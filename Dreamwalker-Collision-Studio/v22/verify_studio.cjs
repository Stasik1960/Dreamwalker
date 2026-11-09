const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright-core');

async function clickReference(page, name) {
    const point = await page.evaluate(name => {
        const {camera, canvas} = window.DW_TEST;
        const reference = window.DW_V22.references.getObjectByName(name);
        const {left, top, width, height} = canvas.getBoundingClientRect();
        const p = reference.getWorldPosition(new THREE.Vector3()).project(camera);
        return [left + (1 + p.x) * width / 2, top + (1 - p.y) * height / 2];
    }, name);
    await page.mouse.click(...point);
    assert.equal(await page.getByRole('combobox', {name: 'Выбранный блок-ориентир'}).inputValue(), name);
}

async function dragFace(page, axis, sign, distance, cancel = false) {
    const [start, end] = await page.evaluate(({axis, sign, distance}) => {
        const {camera, canvas} = window.DW_TEST;
        const resize = window.DW_V22.resize;
        resize.update();
        const {left, top, width, height} = canvas.getBoundingClientRect();
        const handle = resize.handles.find(handle => handle.axis === axis && handle.sign === sign);
        const position = handle.picker.getWorldPosition(new THREE.Vector3());
        const direction = handle.normal.clone().applyQuaternion(resize.object.getWorldQuaternion(new THREE.Quaternion()));
        const project = point => {
            point.project(camera);
            return [left + (1 + point.x) * width / 2, top + (1 - point.y) * height / 2];
        };
        return [project(position.clone()), project(position.addScaledVector(direction, distance))];
    }, {axis, sign, distance});
    await page.mouse.move(...start);
    assert.equal(await page.locator('canvas').evaluate(canvas => canvas.classList.contains('resize-face')), true);
    await page.mouse.down();
    await page.mouse.move(...end, {steps: 6});
    if (cancel) await page.keyboard.press('Escape');
    await page.mouse.up();
}

async function dragAxis(page, control, axis, distance, cancel = false) {
    const [start, end] = await page.evaluate(({control, axis, distance}) => {
        const {camera, canvas, scene} = window.DW_TEST;
        const index = 'XYZ'.indexOf(axis);
        scene.updateMatrixWorld(true);
        camera.updateMatrixWorld(true);
        const {left, top, width, height} = canvas.getBoundingClientRect();
        const picker = window.DW_V22[control].children[0].picker.translate.getObjectByName(axis);
        const point = picker.localToWorld(new THREE.Vector3().setComponent(index, .64));
        const project = point => {
            point.project(camera);
            return [left + (1 + point.x) * width / 2, top + (1 - point.y) * height / 2];
        };
        return [project(point.clone()), project(point.add(new THREE.Vector3().setComponent(index, distance)))];
    }, {control, axis, distance});
    await page.mouse.move(...start);
    assert.equal(await page.evaluate(control => window.DW_V22[control].axis, control), axis);
    await page.mouse.down();
    assert.equal(await page.evaluate(control => window.DW_V22[control].dragging, control), true);
    await page.mouse.move(...end, {steps: 8});
    if (cancel) await page.keyboard.press('Escape');
    await page.mouse.up();
}

async function verifyModelControls(browser, url, directory, errors) {
    const page = await browser.newPage({viewport: {width: 1500, height: 960}});
    page.on('pageerror', error => errors.push(error.message));
    try {
        await page.goto(url);
        await page.waitForFunction(() => window.DW_V22);
        const model = 'bloodborne_dw:prototype_wood_window';
        await page.evaluate(id => {
            const editor = window.DW_TEST;
            editor.selectEntry(id, 'variant=0,open=true');
            editor.orbit.target.set(0, 0, 0);
            editor.camera.position.set(55, 43, 63);
            editor.orbit.update();
        }, model);
        await page.locator('#addBox').click();
        const volumeId = await page.evaluate(() => window.DW_TEST.selected.id);
        const volumePresent = () => page.evaluate(id => window.DW_TEST.work().objects.some(box => box.id === id), volumeId);
        await page.getByText('Блоки-ориентиры', {exact: true}).click();
        await page.getByRole('button', {name: '＋ Добавить ориентир'}).click();
        await page.getByRole('button', {name: 'Y ＋', exact: true}).click();
        const guides = () => page.evaluate(() => window.DW_V22.references.children.map(mesh => mesh.position.toArray()));
        const offset = () => page.evaluate(() => window.DW_TEST.work().modelOffset);
        const layout = () => page.evaluate(() => window.DW_TEST.work().objects);
        const initial = await layout();
        await page.locator('#pickModel').click();
        assert.equal(await page.locator('#pickModel').getAttribute('aria-pressed'), 'true');
        assert.equal(await page.evaluate(() => window.DW_V22.moveModel.object === window.DW_TEST.modelRoot), true);
        await dragAxis(page, 'moveModel', 'Y', 8);
        assert.deepEqual(await offset(), [0, 8, 0]);
        assert.deepEqual(await layout(), initial);
        assert.deepEqual(await guides(), [[0, 16, 0]]);
        await dragAxis(page, 'moveModel', 'X', 4, true);
        assert.deepEqual(await offset(), [0, 8, 0]);
        assert.deepEqual(await page.evaluate(() => window.DW_TEST.modelRoot.position.toArray()), [0, 8, 0]);
        await page.keyboard.press('Delete');
        assert.equal(await volumePresent(), true);
        assert.deepEqual(await guides(), [[0, 16, 0]]);
        await page.locator('#showModel').uncheck();
        assert.equal(await page.evaluate(() => !!window.DW_V22.moveModel.object), false);
        await page.locator('#showModel').check();
        assert.equal(await page.evaluate(() => !!window.DW_V22.moveModel.object), true);
        await page.screenshot({path: path.join(directory, 'model-move.png')});

        await page.locator('#pickReferences').click();
        const guide = page.getByRole('combobox', {name: 'Выбранный блок-ориентир'});
        await guide.selectOption('1');
        await guide.focus();
        await page.keyboard.press('Delete');
        assert.deepEqual(await guides(), []);
        await page.keyboard.press('Control+z');
        assert.deepEqual(await guides(), [[0, 16, 0]]);
        await page.locator('canvas').dispatchEvent('keydown', {key: 'я', code: 'KeyZ', ctrlKey: true, bubbles: true});
        assert.deepEqual(await offset(), [0, 0, 0]);
        await page.keyboard.press('Control+y');
        assert.deepEqual(await offset(), [0, 8, 0]);
        await page.keyboard.press('Control+y');
        assert.deepEqual(await guides(), []);

        await page.locator('#pickVolumes').click();
        await page.locator('#objects .objectItem').last().click();
        await page.keyboard.press('Delete');
        assert.equal(await volumePresent(), false);
        await page.keyboard.press('Control+z');
        assert.equal(await volumePresent(), true);
        await page.keyboard.press('Control+y');
        assert.equal(await volumePresent(), false);
        await page.getByText('Смещение модели', {exact: true}).click();
        const y = page.getByRole('spinbutton', {name: 'Смещение модели (1 блок = 16) Y'});
        await y.fill('9');
        await y.press('Control+z');
        assert.deepEqual(await offset(), [0, 8, 0]);
        await page.keyboard.press('Control+y');
        assert.deepEqual(await offset(), [0, 9, 0]);
        await page.evaluate(() => window.DW_TEST.selectEntry('bloodborne_dw:prototype_double_door'));
        await page.keyboard.press('Control+z');
        assert.equal(await page.evaluate(() => window.DW_TEST.entry.id), model);
        assert.deepEqual(await offset(), [0, 8, 0]);
        const project = await page.evaluate(() => window.DW_TEST.exportProject());
        assert.equal(project.models.length, 1);
        assert.deepEqual(project.models[0].states[0].modelOffset, [0, 8, 0]);
    } finally {
        await page.close();
    }
}

async function verifyPlacementAnchors(browser, url, directory, errors) {
    const page = await browser.newPage({viewport: {width: 1500, height: 960}, acceptDownloads: true});
    const peer = await browser.newPage({viewport: {width: 1500, height: 960}});
    for (const tab of [page, peer]) tab.on('pageerror', error => errors.push(error.message));
    const model = 'bloodborne_dw:prototype_wood_window';
    try {
        await page.goto(url);
        await page.waitForFunction(() => window.DW_V22);
        await page.evaluate(id => {
            const studio = window.DW_TEST;
            studio.selectEntry(id);
            studio.orbit.target.set(8, 8, 8);
            studio.camera.position.set(58, 52, 70);
            studio.orbit.update();
        }, model);
        await page.locator('#pickAnchor').click();
        const cell = tab => tab.evaluate(() => window.DW_TEST.anchorCell());
        const marker = () => page.evaluate(() => window.DW_V22.moveAnchor.object
            .getWorldPosition(new THREE.Vector3()).toArray());
        const layout = () => page.evaluate(() => ({
            objects: window.DW_TEST.work().objects,
            art: window.DW_TEST.modelRoot.position.toArray(),
        }));
        const before = await layout();
        assert.deepEqual(await cell(page), [0, 0, 0]);
        assert.deepEqual(await marker(), [8, 8, 8]);
        await dragAxis(page, 'moveAnchor', 'X', 16);
        await dragAxis(page, 'moveAnchor', 'Y', -16);
        assert.deepEqual(await cell(page), [1, -1, 0]);
        assert.deepEqual(await marker(), [24, -8, 8]);
        assert.deepEqual(await layout(), before);
        await page.keyboard.press('Control+z');
        assert.deepEqual(await cell(page), [1, 0, 0]);
        await page.keyboard.press('Control+y');
        assert.deepEqual(await cell(page), [1, -1, 0]);
        await page.locator('#state').selectOption('variant=0,open=true');
        assert.deepEqual(await cell(page), [1, -1, 0]);
        assert.deepEqual(await marker(), [24, -8, 8]);
        const draft = await page.evaluate(() => window.DW_TEST.exportProject());
        assert.equal(draft.models.length, 1);
        assert.equal(draft.models[0].registryId, model);
        assert.deepEqual(draft.models[0].anchorCell, [1, -1, 0]);
        assert.deepEqual(draft.models[0].states, []);
        await page.locator('#excludeModel').check();
        assert.equal(await page.evaluate(() => window.DW_TEST.exportProject(true).models.length), 0);
        await page.locator('#excludeModel').uncheck();
        const bundle = page.waitForEvent('download');
        await page.locator('#finish').click();
        await (await bundle).saveAs(path.join(directory, 'test-anchor-export.zip'));
        await page.screenshot({path: path.join(directory, 'placement-anchor.png')});

        await page.reload();
        await page.waitForFunction(() => window.DW_V22);
        assert.deepEqual(await cell(page), [1, -1, 0]);
        await page.evaluate(() => {
            const studio = window.DW_TEST;
            studio.selectEntry(studio.listEntries().find(entry => entry.kind === 'rp').id);
        });
        await page.locator('#pickAnchor').click();
        assert.deepEqual(await cell(page), [0, 0, 0]);
        assert.deepEqual(await marker(), [0, 8, 0]);
        const input = page.getByRole('spinbutton', {name: 'Смещение опорной клетки (в блоках) Z'});
        await input.fill('1');
        await input.press('Tab');
        assert.deepEqual(await marker(), [0, 8, 16]);

        await peer.goto(url);
        await peer.waitForFunction(() => window.DW_V22);
        await peer.evaluate(id => {
            const studio = window.DW_TEST;
            studio.selectEntry(id);
            studio.setAnchorCell([-2, 0, 0]);
        }, model);
        await peer.locator('#projectFile').setInputFiles({
            name: 'anchor.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(draft)),
        });
        const conflict = peer.getByRole('dialog', {name: 'Конфликты при загрузке'});
        assert.match(await conflict.textContent(), /опорная клетка/);
        assert.deepEqual(await cell(peer), [-2, 0, 0]);
        await conflict.getByRole('combobox').selectOption('replace');
        await conflict.getByRole('button', {name: 'Применить выбранное'}).click();
        await peer.waitForFunction(() => !document.getElementById('importProject').disabled);
        assert.deepEqual(await cell(peer), [1, -1, 0]);
    } finally {
        await page.close();
        await peer.close();
    }
}

async function verifyEditing(page, directory) {
    const controls = page.locator('#volumeSettings');
    assert.equal(await controls.getAttribute('open'), null);
    assert.equal(await page.locator('#objects').evaluate(el => getComputedStyle(el).overflow), 'visible');
    await page.locator('#addBox').click();
    assert.equal(await page.locator('#pickVolumes').getAttribute('aria-pressed'), 'true');
    await controls.locator('summary').click();
    assert.equal(await page.locator('#fitGrid').isEnabled(), true);
    await page.evaluate(() => {
        const editor = window.DW_TEST;
        editor.fitSelection([8, 8, 8], [16, 16, 16]);
        editor.orbit.target.set(8, 8, 8);
        editor.camera.position.set(48, 39, 52);
        editor.orbit.update();
    });
    await page.locator('#scale').click();
    const geometry = () => page.evaluate(() => {
        const {position, size} = window.DW_TEST.selected;
        return {position: position.map(n => Math.round(n * 1e6) / 1e6), size};
    });
    assert.equal(await page.evaluate(() => window.DW_V22.resize.handles.length), 6);
    await dragFace(page, 0, 1, 4);
    assert.deepEqual(await geometry(), {position: [10, 8, 8], size: [20, 16, 16]});
    await dragFace(page, 0, -1, 3);
    assert.deepEqual(await geometry(), {position: [8.5, 8, 8], size: [23, 16, 16]});
    await page.locator('#search').fill('93241');
    await page.locator('#inspector').evaluate(inspector => { inspector.scrollTop = 0; });
    await page.screenshot({path: path.join(directory, 'resize-grips.png')});
    await page.locator('#search').fill('');
    await dragFace(page, 1, -1, -2);
    assert.deepEqual(await geometry(), {position: [8.5, 9, 8], size: [23, 14, 16]});
    await dragFace(page, 2, 1, 3, true);
    assert.deepEqual(await geometry(), {position: [8.5, 9, 8], size: [23, 14, 16]});
    await dragFace(page, 2, 1, 3);
    assert.deepEqual(await geometry(), {position: [8.5, 9, 9.5], size: [23, 14, 19]});
    await page.locator('#undo').click();
    await page.locator('#objects .objectItem').last().click();
    assert.deepEqual(await geometry(), {position: [8.5, 9, 8], size: [23, 14, 16]});
    await page.locator('#redo').click();
    await page.locator('#objects .objectItem').last().click();
    assert.deepEqual(await geometry(), {position: [8.5, 9, 9.5], size: [23, 14, 19]});
    const rotationY = page.locator('#rotation1');
    await rotationY.fill('90');
    await rotationY.press('Tab');
    await dragFace(page, 0, 1, 2);
    assert.deepEqual(await geometry(), {position: [8.5, 9, 8.5], size: [25, 14, 19]});
    await rotationY.fill('0');
    await rotationY.press('Tab');
    await page.evaluate(() => window.DW_TEST.fitSelection([-8, 8, 8], [22, 24, 32]));
    await page.locator('#fitGrid').click();
    assert.deepEqual(await geometry(), {position: [-8, 8, 8], size: [16, 16, 32]});
    assert.deepEqual(await page.evaluate(() => window.DW_TEST.modelRoot.position.toArray()), [0, 0, 0]);
    const gridCases = await page.evaluate(() => {
        const zero = [0, 0, 0];
        const fit = (position, size, rotation = zero, origin = zero) => DW_RESIZE.fitToGrid({position, size, rotation}, origin);
        return [
            fit([16, 8, 8], [4, 36, 16]),
            fit([15.5, 8, 8], [4, 16, 16]),
            fit([16.5, 8, 8], [4, 16, 16]),
            fit([.5, 8, 8], [1, 16, 16]),
            fit([0, 8, 0], [22, 22, 22], zero, [-8, 0, -8]),
            fit([8, 8, 8], [32, 22, 24], [0, 90, 0]),
            fit([8, 8, 8], [22, 22, 22], [0, 45, 0]),
        ];
    });
    assert.deepEqual(gridCases, [
        {position: [17, 8, 8], size: [2, 36, 16]},
        {position: [14.75, 8, 8], size: [2.5, 16, 16]},
        {position: [17.25, 8, 8], size: [2.5, 16, 16]},
        {position: [.5, 8, 8], size: [1, 16, 16]},
        {position: [0, 8, 0], size: [16, 16, 16]},
        {position: [8, 8, 8], size: [32, 16, 16]},
        {position: [8, 8, 8], size: [22, 16, 22]},
    ]);
    await page.locator('#delete').click();
    assert.equal(await page.locator('.modelItem.selected .model-star').count(), 0);
    await controls.locator('summary').click();
    assert.equal(await controls.getAttribute('open'), null);
}

async function verifyWindowTrim(page) {
    const previous = await page.evaluate(() => {
        const editor = window.DW_TEST;
        const previous = [editor.entry.id, editor.state.key];
        editor.selectEntry('bloodborne_dw:prototype_wood_window', 'variant=0,open=true');
        return previous;
    });
    const settings = page.locator('#volumeSettings summary');
    await page.getByRole('button', {name: '□ Игроки 5', exact: true}).click();
    await settings.click();
    const box = () => page.evaluate(() => {
        const {position, size} = window.DW_TEST.selected;
        return {position, size};
    });
    const before = await box();
    await page.locator('#fitGrid').click();
    const after = await box();
    assert.deepEqual(after.position.slice(0, 2), before.position.slice(0, 2));
    assert.deepEqual(after.size.slice(0, 2), before.size.slice(0, 2));
    assert.ok(Math.abs(after.position[2] + after.size[2] / 2) < 1e-8);
    assert.ok(Math.abs(after.size[2] - 2.4148448741) < 1e-8);
    await page.locator('#fitGrid').click();
    assert.deepEqual(await box(), after);
    await page.keyboard.press('Control+z');
    assert.deepEqual(await box(), before);
    const centered = {position: [...before.position.slice(0, 2), 0], size: before.size};
    await page.evaluate(({position, size}) => window.DW_TEST.fitSelection(position, size), centered);
    await page.locator('#fitGrid').click();
    const tied = await box();
    assert.equal(tied.position[2] - tied.size[2] / 2, 0);
    assert.equal(tied.size[2], before.size[2] / 2);
    assert.match(await page.locator('#status').textContent(), /Z 4,787 → 2,394/);
    await page.locator('#fitGrid').click();
    assert.deepEqual(await box(), tied);
    await page.keyboard.press('Control+z');
    assert.deepEqual(await box(), centered);
    await page.keyboard.press('Control+z');
    assert.deepEqual(await box(), before);
    await settings.click();
    await page.evaluate(([id, key]) => window.DW_TEST.selectEntry(id, key), previous);
}

async function verifyMerge(browser, url, errors) {
    const open = async () => {
        const page = await browser.newPage({viewport: {width: 1500, height: 960}, acceptDownloads: true});
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(url);
        await page.waitForFunction(() => window.DW_V22);
        return page;
    };
    const a = await open();
    const b = await open();
    try {
        const door = 'bloodborne_dw:prototype_double_door';
        const skipped = 'bloodborne_dw:source_stairs_waxed_oxidized_cut_copper_stairs_inner_left_top2';
        const localOnly = 'bloodborne_dw:source_shutter_window';
        const customSent = 'import:fixture-shared';
        const customLocal = 'import:fixture-local';
        const createModel = (page, id) => page.evaluate(id => {
            const editor = window.DW_TEST;
            const part = {...editor.state.parts[0]};
            const geometry = structuredClone(window.DW_DATA.models[part.model]);
            part.model = id + ':geometry';
            editor.registerAssembly({
                id, name: id, kind: 'import', states: [{key: 'default', label: 'Исходная модель',
                    selector: {}, parts: [part], clip: null, time: 0}],
            }, {[part.model]: geometry});
        }, id);
        const edit = (page, id, index, y) => page.evaluate(({id, index, y}) => {
            const editor = window.DW_TEST;
            const entry = editor.listEntries().find(entry => entry.id === id);
            editor.selectEntry(id, entry.states[index].key);
            editor.setModelOffset([0, y, 0]);
        }, {id, index, y});
        const star = (page, id) => page.locator('.modelItem[data-model-id="' + id + '"] .model-star');
        await edit(a, door, 0, 3);
        await edit(a, door, 1, 4);
        await edit(a, skipped, 0, 1);
        await edit(a, skipped, 1, 2);
        assert.equal(await star(a, skipped).getAttribute('class'), 'model-star');
        await a.locator('#excludeModel').check();
        assert.equal(await star(a, skipped).getAttribute('class'), 'model-star excluded');
        await createModel(a, customSent);
        await a.locator('#excludeModel').check();
        const saved = a.waitForEvent('download');
        await a.locator('#exportJson').click();
        const json = await fs.readFile(await (await saved).path());
        const project = JSON.parse(json);
        assert.deepEqual(project.editor.excludedModels, [skipped, customSent]);
        assert.equal(project.models.find(model => model.registryId === skipped).states.length, 2);
        const packed = a.waitForEvent('download');
        await a.locator('#finish').click();
        const zip = await fs.readFile(await (await packed).path());
        const bundled = await a.evaluate(async bytes => {
            const zip = await JSZip.loadAsync(Uint8Array.from(bytes));
            return JSON.parse(await zip.file('dreamwalker-collisions.json').async('string'));
        }, [...zip]);
        assert.deepEqual(bundled.models.map(model => model.registryId), [door]);
        assert.equal(bundled.editor, undefined);
        assert.equal(bundled.customAssets.length, 0);
        assert.deepEqual(bundled.importedGeometry.models, {});
        await createModel(b, customLocal);
        await edit(b, door, 0, 9);
        await edit(b, localOnly, 0, 7);
        const dialog = b.getByRole('dialog', {name: 'Конфликты при загрузке'});
        const openMerge = async buffer => {
            await b.locator('#projectFile').setInputFiles({name: 'partner.json', mimeType: 'application/json', buffer});
            await dialog.waitFor({state: 'visible'});
        };
        const apply = async () => {
            await dialog.getByRole('button', {name: 'Применить выбранное'}).click();
            await b.waitForFunction(() => document.getElementById('status').textContent.includes('Объединено'));
            await b.waitForFunction(() => !document.getElementById('importProject').disabled);
        };
        const before = await b.evaluate(() => window.DW_TEST.exportProject().models);
        await openMerge(json);
        assert.ok((await dialog.textContent()).includes('№ 90001'));
        assert.equal(await dialog.getByRole('combobox').inputValue(), 'keep');
        assert.deepEqual(await b.evaluate(() => window.DW_TEST.exportProject().models), before);
        await dialog.getByRole('button', {name: 'Отмена'}).click();
        await b.waitForFunction(() => document.getElementById('status').textContent.includes('отменена'));
        assert.deepEqual(await b.evaluate(() => window.DW_TEST.exportProject().models), before);
        await openMerge(json);
        await apply();
        const restored = await b.evaluate(() => window.DW_TEST.exportProject());
        const offsets = id => restored.models.find(model => model.registryId === id).states.map(state => state.modelOffset[1]);
        assert.deepEqual(offsets(door), [9, 4]);
        assert.deepEqual(offsets(skipped), [1, 2]);
        assert.deepEqual(offsets(localOnly), [7]);
        assert.deepEqual(restored.editor.excludedModels, [skipped, customSent]);
        assert.deepEqual(restored.customAssets.map(asset => asset.id), [customLocal, customSent]);
        assert.ok(await b.evaluate(id => window.DW_DATA.models[id + ':geometry'], customSent));
        assert.equal(await b.evaluate(() => window.DW_TEST.entry.id), localOnly);
        await b.reload();
        await b.waitForFunction(() => window.DW_V22);
        assert.equal(await star(b, skipped).getAttribute('class'), 'model-star excluded');
        assert.deepEqual(await b.evaluate(() => window.DW_TEST.exportProject().models), restored.models);
        await openMerge(json);
        await dialog.getByRole('combobox').selectOption('replace');
        await apply();
        const replaced = await b.evaluate(() => window.DW_TEST.exportProject());
        assert.deepEqual(replaced.models.find(model => model.registryId === door).states
            .map(state => state.modelOffset[1]), [3, 4]);
        assert.deepEqual(replaced.models.filter(model => model.registryId !== door),
            restored.models.filter(model => model.registryId !== door));
        const geometry = structuredClone(project);
        geometry.models = [];
        geometry.customAssets[0].states[0].parts[0].offset = [0, 7, 0];
        await openMerge(Buffer.from(JSON.stringify(geometry)));
        assert.ok((await dialog.textContent()).includes('геометрия'));
        await dialog.getByRole('combobox').selectOption('replace');
        await apply();
        assert.deepEqual(await b.evaluate(id => window.DW_TEST.listEntries()
            .find(entry => entry.id === id).states[0].parts[0].offset, customSent), [0, 7, 0]);
    } finally {
        await a.close();
        await b.close();
    }
}

async function verifyCoordinateRoundTrip(browser, url, directory, errors) {
    const registryId = 'bloodborne_dw:source_blackstone_stairs_top';
    const editedKey = 'variant=0,open=false';
    const defaultKey = 'variant=1,open=false';
    const modelOffset = [-2.375, 6.125, -11.75];
    const anchorCell = [-2, 1, 3];
    const edits = [
        {mode: 'players', position: [-12.375, 7.125, 19.75], size: [15.5, 19, 3.25], rotation: [0, 37.5, 0]},
        {mode: 'blocks', position: [11.5, -8.25, 23.625], size: [16, 7.5, 12.25], rotation: [7, 0, -15]},
        {mode: 'outline', position: [1.125, 17.25, -6.75], size: [12, 22, 2.5], rotation: [5, -45, 0]},
        {mode: 'seat', position: [7.25, 21.875, -13.5], rotation: [0, 135, 15]},
    ];
    const modelButton = page => page.locator(`[data-model-id="${registryId}"]`);
    const sceneState = page => page.evaluate(() => {
        const studio = window.DW_TEST;
        return {
            key: studio.state.key,
            modelOffset: studio.modelRoot.position.toArray(),
            anchorCell: studio.anchorCell(),
            objects: studio.work().objects.map(object => {
                const group = studio.boxRoot.getObjectByName(object.id);
                return {id: object.id, mode: object.type === 'seat' ? 'seat' : object.mode,
                    position: object.position, size: object.size, rotation: object.rotation,
                    scenePosition: group.getWorldPosition(new THREE.Vector3()).toArray()};
            }),
        };
    });
    async function inputValues(inputs, values) {
        for (const [index, value] of values.entries()) {
            await inputs(index).fill(String(value));
            await inputs(index).press('Tab');
        }
    }
    const source = await browser.newPage({viewport: {width: 1500, height: 960}, acceptDownloads: true});
    source.on('pageerror', error => errors.push(error.message));
    try {
        await source.goto(url);
        await source.waitForFunction(() => window.DW_V22);
        await modelButton(source).click();
        assert.equal(await source.locator('#state').inputValue(), defaultKey);
        await source.locator('#state').selectOption(editedKey);
        await source.locator('#volumeSettings summary').click();
        for (const edit of edits) {
            if (edit.mode === 'seat') {
                await source.locator('#addSeat').click();
            } else {
                const index = await source.evaluate(mode => window.DW_TEST.work().objects
                    .findIndex(object => object.mode === mode), edit.mode);
                assert.ok(index >= 0, edit.mode);
                await source.locator('#objects .objectItem').nth(index).click();
                await inputValues(axis => source.locator('#size' + axis), edit.size);
            }
            await inputValues(axis => source.locator('#position' + axis), edit.position);
            await inputValues(axis => source.locator('#rotation' + axis), edit.rotation);
            const x = source.locator('#position0');
            await x.fill('');
            await x.press('Tab');
            assert.equal(Number(await x.inputValue()), edit.position[0]);
        }
        await source.getByText('Смещение модели', {exact: true}).click();
        await inputValues(axis => source.getByRole('spinbutton', {name: 'Смещение модели (1 блок = 16) ' + 'XYZ'[axis]}), modelOffset);
        await source.locator('#pickAnchor').click();
        await inputValues(axis => source.getByRole('spinbutton', {name: 'Смещение опорной клетки (в блоках) ' + 'XYZ'[axis]}), anchorCell);
        const authored = await sceneState(source);
        assert.equal(authored.key, editedKey);
        assert.deepEqual(authored.modelOffset, modelOffset);
        assert.deepEqual(authored.anchorCell, anchorCell);
        for (const edit of edits) {
            const object = authored.objects.find(object => object.mode === edit.mode);
            assert.deepEqual(object.position, edit.position);
            assert.deepEqual(object.scenePosition, edit.position);
            assert.deepEqual(object.rotation, edit.rotation);
            assert.deepEqual(object.size, edit.size || [9.6, 1.6, 9.6]);
        }
        const JSZip = require(path.resolve(directory, 'vendor/jszip.min.js'));
        for (const [button, file] of [['#exportJson', 'test-coordinates.json'], ['#finish', 'test-coordinates.zip']]) {
            const filePath = path.join(directory, file);
            const download = source.waitForEvent('download');
            await source.locator(button).click();
            await (await download).saveAs(filePath);
            const raw = await fs.readFile(filePath);
            const zip = file.endsWith('.zip') ? await JSZip.loadAsync(raw) : null;
            const project = JSON.parse(zip ? await zip.file('dreamwalker-collisions.json').async('string') : raw.toString('utf8'));
            assert.equal(project.models.length, 1);
            const [model] = project.models;
            assert.equal(model.registryId, registryId);
            assert.deepEqual(model.anchorCell, anchorCell);
            assert.equal(model.states.length, 1);
            const [state] = model.states;
            assert.equal(state.key, editedKey);
            assert.deepEqual(state.modelOffset, modelOffset);
            assert.equal(state.colliders.length + state.seats.length, authored.objects.length);
            for (const {id, mode, position, size, rotation} of authored.objects) {
                if (mode === 'seat') {
                    const seat = state.seats.find(seat => seat.id === id);
                    assert.deepEqual([seat.surfaceCenter, seat.size, seat.rotation, seat.front], [position, size, rotation, [0, 0, -1]]);
                } else {
                    const box = state.colliders.find(box => box.id === id);
                    assert.deepEqual([box.mode, box.center, box.size, box.rotation], [mode, position, size, rotation]);
                }
            }
            const reader = await browser.newPage({viewport: {width: 1500, height: 960}});
            reader.on('pageerror', error => errors.push(error.message));
            try {
                await reader.goto(url);
                await reader.waitForFunction(() => window.DW_V22);
                await modelButton(reader).click();
                assert.equal(await reader.locator('#state').inputValue(), defaultKey);
                await reader.locator('#projectFile').setInputFiles(filePath);
                await reader.waitForFunction(() => document.getElementById('status').textContent.includes('Объединено вариантов: 1'));
                assert.deepEqual(await sceneState(reader), authored);
                assert.match(await reader.locator('#state option:checked').textContent(), /★$/);
                await reader.reload();
                await reader.waitForFunction(() => window.DW_V22);
                assert.deepEqual(await sceneState(reader), authored);
                await reader.locator('#pickAnchor').click();
                assert.deepEqual(await reader.evaluate(() => window.DW_V22.moveAnchor.object
                    .getWorldPosition(new THREE.Vector3()).toArray()), [-24, 24, 56]);
                await reader.locator('#state').selectOption(defaultKey);
                assert.deepEqual((await sceneState(reader)).modelOffset, [0, 0, 0]);
                await modelButton(reader).click();
                assert.deepEqual(await sceneState(reader), authored);
            } finally {
                await reader.close();
            }
        }
    } finally {
        await source.close();
    }
}

async function verifyCrossModelCopy(browser, url, errors) {
    const page = await browser.newPage({viewport: {width: 1500, height: 960}});
    page.on('pageerror', error => errors.push(error.message));
    const sourceId = 'bloodborne_rp:cabriolet_closed';
    const targetId = 'bloodborne_rp:cabriolet_open';
    const read = () => page.evaluate(() => {
        const studio = window.DW_TEST;
        return {id: studio.entry.id, ...studio.work(), anchor: studio.anchorCell()};
    });
    try {
        await page.goto(url);
        await page.waitForFunction(() => window.DW_V22);
        const [source, target] = await page.evaluate(([sourceId, targetId]) => {
            const studio = window.DW_TEST;
            const read = () => structuredClone({id: studio.entry.id, ...studio.work(), anchor: studio.anchorCell()});
            studio.selectEntry(targetId);
            studio.setModelOffset([-2, 0, 5]);
            studio.setAnchorCell([1, 0, 0]);
            studio.add('box', 'outline');
            const target = read();
            studio.selectEntry(sourceId);
            studio.setModelOffset([2, 3, 4]);
            studio.setAnchorCell([0, 1, 0]);
            studio.add('box', 'blocks');
            studio.fitSelection([-4.25, 5, 9.5], [2, 4, 6]);
            studio.add('seat');
            studio.fitSelection([3, 7.25, -11], [9.6, 1.6, 9.6]);
            return [read(), target];
        }, [sourceId, targetId]);
        await page.locator('#copyState').click();
        assert.equal(await page.locator('#copyConfirm').isDisabled(), true);
        assert.equal(await page.locator('#copyModel option[value="' + sourceId + '"]').count(), 0);
        await page.locator('#copyModel').selectOption(targetId);
        assert.equal(await page.locator('#copyTarget').inputValue(), 'default');
        page.once('dialog', dialog => dialog.dismiss());
        await page.locator('#copyConfirm').click();
        assert.deepEqual(await read(), source);
        page.once('dialog', dialog => dialog.accept());
        await page.locator('#copyConfirm').click();
        const copied = await read();
        assert.equal(copied.id, targetId);
        assert.deepEqual(copied.objects, source.objects);
        assert.deepEqual(copied.modelOffset, target.modelOffset);
        assert.deepEqual(copied.anchor, target.anchor);
        await page.locator('#undo').click();
        assert.deepEqual(await read(), target);
        await page.locator('#redo').click();
        assert.deepEqual(await read(), copied);
        await page.evaluate(id => window.DW_TEST.selectEntry(id), sourceId);
        assert.deepEqual(await read(), source);
    } finally {
        await page.close();
    }
}

async function verifyShutterMigration(browser, url, errors) {
    const page = await browser.newPage({viewport: {width: 1500, height: 960}, acceptDownloads: true});
    page.on('pageerror', error => errors.push(error.message));
    try {
        await page.goto(url);
        await page.waitForFunction(() => window.DW_TEST?.exportProject);
        const fixture = await page.evaluate(() => {
            const editor = window.DW_TEST;
            const project = editor.exportProject();
            const source = 'bloodborne_dw:source_spruce_trapdoor_bottom';
            const target = 'bloodborne_dw:source_shutter_window';
            const stateKeys = ['variant=0,open=false', 'variant=1,open=false'];
            const states = stateKeys.map((key, variant) => ({
                key, selector: {variant, open: false}, replace: true, reviewed: true,
                modelOffset: [0, -1, 9 + variant],
                colliders: ['players', 'blocks', 'outline'].map((mode, i) => ({
                    id: mode, name: mode, mode, center: [i, 12, 9], size: [2, 10, 1], rotation: [0, 0, 0],
                })),
                seats: [{id: 'seat', name: 'seat', surfaceCenter: [0, 5, 3], size: [9.6, 1.6, 9.6],
                    rotation: [0, 90, 0], capacity: 1, front: [0, 0, -1], trigger: 'right_click_surface'}],
            }));
            project.source.commit = 'v22:' + project.source.jarSha256
                + ':shared-profiles:d5c592dcb429c8dde72ef838548ea7a08942bca9eb8b9924e1a12b943df7e0b1';
            project.models = [{registryId: source, states, anchorCell: [1, 0, -1]}];
            editor.selectEntry(target, 'variant=0,open=false');
            editor.setModelOffset([7, 0, 0]);
            return {project, source, target};
        });
        assert.equal(await page.locator('.modelItem[data-model-id="' + fixture.source + '"]').count(), 0);
        await page.locator('#projectFile').setInputFiles({
            name: 'old-window.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(fixture.project)),
        });
        const dialog = page.getByRole('dialog', {name: 'Конфликты при загрузке'});
        await dialog.waitFor({state: 'visible'});
        await dialog.getByRole('combobox').selectOption('replace');
        await dialog.getByRole('button', {name: 'Применить выбранное'}).click();
        await page.waitForFunction(() => document.getElementById('status').textContent.includes('перенесено на 93241: 2'));
        const exported = await page.evaluate(() => window.DW_TEST.exportProject(true));
        assert.deepEqual(exported.models.map(model => model.registryId), [fixture.target]);
        assert.deepEqual(exported.models[0].anchorCell, [1, 0, -1]);
        assert.deepEqual(exported.models[0].states.map(state => state.key), ['variant=0,open=false', 'variant=0,open=true']);
        for (const [i, saved] of fixture.project.models[0].states.entries()) {
            const actual = exported.models[0].states[i];
            for (const key of ['modelOffset', 'colliders', 'seats']) assert.deepEqual(actual[key], saved[key]);
        }
    } finally {
        await page.close();
    }
}

async function verify(directory, executablePath) {
    const browser = await chromium.launch({
        executablePath, headless: true,
        args: ['--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--disable-background-networking'],
    });
    try {
        const page = await browser.newPage({viewport: {width: 1500, height: 960}, acceptDownloads: true});
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('dialog', dialog => dialog.accept());
        const url = pathToFileURL(path.join(directory, 'index.html')).href;
        await page.goto(url);
        await page.waitForFunction(() => window.DW_V22 && window.DW_TEST);
        const results = await page.evaluate(() => {
            const studio = window.DW_TEST;
            const failed = [];
            let states = 0;
            for (const model of studio.listEntries()) {
                for (const state of model.states) {
                    studio.selectEntry(model.id, state.key);
                    states++;
                    if (!studio.modelRoot.children.length || !Array.isArray(studio.work().objects)
                            || state.selector?.profile || state.parts.some(part => part.model.startsWith('v22:alt:'))) {
                        failed.push(model.id + ' ' + state.key);
                    }
                }
            }
            studio.selectEntry('bloodborne_dw:source_shutter_window');
            return {failed, states, models: studio.listEntries().length,
                    has90841: studio.listEntries().some(entry => entry.number === '90841'),
                    untouched: studio.exportProject().models.length};
        });
        assert.deepEqual(results.failed, []);
        assert.equal(results.untouched, 0);
        assert.equal(results.has90841, true);
        const woodWindow = await page.evaluate(() => {
            const editor = window.DW_TEST;
            const wood = editor.listEntries().find(model => model.number === '90007');
            const bounds = open => {
                const state = wood.states.find(state => state.selector.open === open);
                editor.selectEntry(wood.id, state.key);
                return new THREE.Box3().setFromObject(editor.modelRoot).getSize(new THREE.Vector3()).x;
            };
            const widths = [bounds(false), bounds(true)];
            editor.selectEntry('bloodborne_dw:source_shutter_window');
            return widths;
        });
        assert.ok(woodWindow[1] > woodWindow[0], '90007 must swing outwards');
        assert.ok(Math.abs(woodWindow[1] - (32 + 50 * Math.sin(3 * Math.PI / 8))) < .001);
        const gate = await page.evaluate(() => {
            const studio = window.DW_TEST;
            const gate = studio.listEntries().find(model => model.number === '90266');
            const measure = open => {
                studio.selectEntry(gate.id, gate.states.find(state => state.selector.open === open).key);
                const box = new THREE.Box3().setFromObject(studio.modelRoot);
                return {width: box.max.x - box.min.x, height: box.max.y - box.min.y, depth: box.max.z - box.min.z};
            };
            const result = {closed: measure(false), open: measure(true)};
            studio.selectEntry('bloodborne_dw:source_shutter_window');
            return result;
        });
        assert.ok(gate.closed.width > 130 && gate.closed.height > 150 && gate.closed.depth < 4);
        assert.ok(gate.open.depth > 40 && Math.abs(gate.open.height - gate.closed.height) < .001);
        const previews = await page.evaluate(() => {
            const studio = window.DW_TEST;
            studio.selectEntry('bloodborne_dw:source_oak_wood', 'variant=1,open=false');
            const bounds = new THREE.Box3().setFromObject(studio.modelRoot);
            const stainedGlass = [...bounds.min.toArray(), ...bounds.max.toArray()];
            const assemblies = ['source_acacia_pressure_plate', 'source_blackstone_stairs_top',
                'source_stairs_waxed_oxidized_cut_copper_stairs_inner_left_top2'].map(id => {
                studio.selectEntry('bloodborne_dw:' + id);
                return studio.state.key;
            });
            const windowId = 'bloodborne_dw:source_pressure_plate_window_bottom_4';
            studio.selectEntry(windowId);
            const whole = [studio.state.key, studio.modelRoot.children.length, studio.entry.states.length];
            const visibleEntries = studio.listEntries();
            const windows = visibleEntries.filter(model => ['90798', '90799', '90952', '90953'].includes(model.number)
                || model.id.endsWith('source_shutter_window_surround'));
            const twoPoses = ['93152', '93153', '93241'].map(number => visibleEntries.find(model => model.number === number)
                .states.map(state => state.key));
            const rp = visibleEntries.filter(model => model.kind === 'rp');
            const prop = rp.find(model => model.id.endsWith(':babycart')).states[0].baseline;
            const gate = rp.find(model => model.id.endsWith(':main_gate')).states;
            const entityPhysics = {
                prop: prop.map(box => box.mode),
                gateClosed: gate[0].baseline.find(box => box.mode === 'players').size,
                gateOpenPlayers: gate[1].baseline.filter(box => box.mode === 'players').length,
                gateOpenOutline: gate[1].baseline.filter(box => box.mode === 'outline').length,
                noPlacement: rp.every(model => model.states.every(state => state.baseline.every(box => box.mode !== 'blocks'))),
            };
            studio.selectEntry('bloodborne_dw:source_shutter_window');
            return {stainedGlass, assemblies, whole, windows, twoPoses, entityPhysics};
        });
        const stainedGlassBounds = [-10, 0, 0, 26, 36, 32];
        assert.ok(previews.stainedGlass.every((value, i) => Math.abs(value - stainedGlassBounds[i]) < 1e-6),
            `90725 bounds ${previews.stainedGlass} must match the installed pose`);
        assert.deepEqual(previews.assemblies, Array(3).fill('variant=1,open=false'));
        assert.deepEqual(previews.whole, ['variant=2,open=false', 3, 1]);
        assert.deepEqual(previews.windows, []);
        for (const poses of previews.twoPoses) assert.deepEqual(poses, ['variant=0,open=false', 'variant=0,open=true']);
        assert.deepEqual(previews.entityPhysics.prop, ['players', 'outline']);
        assert.deepEqual(previews.entityPhysics.gateClosed, [76, 200, 76]);
        assert.equal(previews.entityPhysics.gateOpenPlayers, 0);
        assert.ok(previews.entityPhysics.gateOpenOutline > 1 && previews.entityPhysics.noPlacement);
        assert.equal(await page.evaluate(() => window.DW_V22.references.children.length), 0);
        assert.equal(await page.locator('#cobbleReference').count(), 0);
        await page.getByText('Блоки-ориентиры', {exact: true}).click();
        const add = page.getByRole('button', {name: '＋ Добавить ориентир'});
        await add.click();
        await add.click();
        await page.getByRole('button', {name: 'Y ＋', exact: true}).click();
        await page.getByRole('button', {name: 'Z −', exact: true}).click();
        const worldPositions = () => page.evaluate(() => window.DW_V22.references.children.map(
            mesh => mesh.getWorldPosition(new THREE.Vector3()).toArray()));
        assert.deepEqual(await worldPositions(), [[8, 8, 8], [24, 24, -8]]);
        await page.locator('#pickVolumes').click();
        assert.equal(await page.evaluate(() => !!window.DW_V22.moveReference.object), false);
        await page.locator('#pickReferences').click();
        await clickReference(page, '1');
        await page.getByRole('button', {name: 'X −', exact: true}).click();
        await page.getByRole('button', {name: 'Y −', exact: true}).click();
        await page.getByRole('button', {name: 'Z ＋', exact: true}).click();
        assert.deepEqual(await worldPositions(), [[-8, -8, 24], [24, 24, -8]]);
        assert.deepEqual(await page.evaluate(() => {
            const tool = window.DW_V22.moveReference;
            const axes = [...new Set(tool.children[0].picker.translate.children.map(handle => handle.name))].sort();
            return [tool.translationSnap, tool.space, axes];
        }), [16, 'local', ['X', 'Y', 'Z']]);
        await page.getByRole('button', {name: 'Удалить выбранный ориентир'}).click();
        assert.deepEqual(await worldPositions(), [[24, 24, -8]]);
        const drag = await page.evaluate(() => {
            const {camera, canvas, scene} = window.DW_TEST;
            scene.updateMatrixWorld(true);
            camera.updateMatrixWorld(true);
            const {left, top, width, height} = canvas.getBoundingClientRect();
            const picker = window.DW_V22.moveReference.children[0].picker.translate.getObjectByName('Y');
            const start = picker.localToWorld(new THREE.Vector3(0, .64, 0));
            const project = point => {
                point.project(camera);
                return [left + (1 + point.x) * width / 2, top + (1 - point.y) * height / 2];
            };
            return [project(start.clone()), project(start.add(new THREE.Vector3(0, -16, 0)))];
        });
        await page.mouse.move(...drag[0]);
        assert.equal(await page.evaluate(() => window.DW_V22.moveReference.axis), 'Y');
        await page.mouse.down();
        assert.equal(await page.evaluate(() => window.DW_V22.moveReference.dragging), true);
        await page.mouse.move(...drag[1], {steps: 8});
        await page.mouse.up();
        assert.deepEqual(await worldPositions(), [[24, 8, -8]]);
        await page.getByRole('button', {name: 'Y ＋', exact: true}).click();
        assert.deepEqual(await worldPositions(), [[24, 24, -8]]);
        assert.equal(await page.evaluate(() => window.DW_TEST.exportProject().models.length), 0);
        await verifyEditing(page, directory);
        await verifyWindowTrim(page);
        await page.locator('#addOutline').click();
        await page.locator('#addSeat').click();
        assert.equal(await page.locator('#size0').isDisabled(), true);
        assert.equal(await page.locator('#scale').isDisabled(), true);
        await page.getByText('Смещение модели', {exact: true}).click();
        const modelY = page.getByRole('spinbutton', {name: 'Смещение модели (1 блок = 16) Y', exact: true});
        await modelY.fill('8');
        await modelY.press('Tab');
        assert.deepEqual(await worldPositions(), [[24, 24, -8]]);
        const exported = await page.evaluate(() => window.DW_TEST.exportProject());
        assert.equal(exported.schemaVersion, 4);
        assert.equal(exported.policy.profileScope, 'all');
        assert.equal(exported.models.length, 1);
        const state = exported.models[0].states[0];
        assert.equal(state.selector.profile, undefined);
        assert.deepEqual(state.modelOffset, [0, 8, 0]);
        assert.ok(state.colliders.some(box => box.mode === 'outline'));
        assert.ok(state.colliders.some(box => box.mode === 'players'));
        assert.ok(state.colliders.some(box => box.mode === 'blocks'));
        assert.deepEqual(state.seats[0].front, [0, 0, -1]);
        const bundle = page.waitForEvent('download');
        await page.locator('#finish').click();
        await (await bundle).saveAs(path.join(directory, 'test-export.zip'));
        await page.evaluate(project => window.DW_TEST.importProject(
            new File([JSON.stringify(project)], 'test.json')), exported);
        const reimported = await page.evaluate(() => window.DW_TEST.exportProject());
        assert.deepEqual(reimported.models, exported.models);
        const migrated = await page.evaluate(project => {
            const legacy = structuredClone(project);
            const state = legacy.models[0].states[0];
            state.key = state.key.replace(',open=', ',profile=base,open=');
            state.selector.profile = 'base';
            legacy.source.commit = 'v22:' + legacy.source.jarSha256;
            delete legacy.policy.profileScope;
            return window.DW_TEST.validateProject(window.DW_V22.upgradeProject(legacy));
        }, exported);
        assert.deepEqual(migrated.models, exported.models);
        await page.evaluate(() => window.DW_TEST.frameScene());
        await page.screenshot({path: path.join(directory, 'studio.png')});
        await verifyMerge(browser, url, errors);
        await verifyModelControls(browser, url, directory, errors);
        await verifyPlacementAnchors(browser, url, directory, errors);
        await verifyCoordinateRoundTrip(browser, url, directory, errors);
        await verifyCrossModelCopy(browser, url, errors);
        await verifyShutterMigration(browser, url, errors);
        assert.deepEqual(errors, []);
        await fs.writeFile(path.join(directory, 'verification.json'),
            JSON.stringify({status: 'PASS', ...results, exportVersion: exported.schemaVersion}, null, 2) + '\n');
    } finally {
        await browser.close();
    }
}

verify(process.argv[2], process.argv[3]).catch(error => {
    console.error(error);
    process.exitCode = 1;
});
