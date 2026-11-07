const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright-core');

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
        await page.goto(pathToFileURL(path.join(directory, 'index.html')).href);
        await page.waitForFunction(() => window.DW_V22 && window.DW_TEST);
        const results = await page.evaluate(() => {
            const studio = window.DW_TEST;
            const failed = [];
            let states = 0;
            for (const model of studio.listEntries()) {
                for (const state of model.states) {
                    studio.selectEntry(model.id, state.key);
                    states++;
                    if (!studio.modelRoot.children.length || !Array.isArray(studio.work().objects)) {
                        failed.push(model.id + ' ' + state.key);
                    }
                }
            }
            studio.selectEntry('bloodborne_dw:source_shutter_window');
            return {failed, states, models: studio.listEntries().length,
                    untouched: studio.exportProject().models.length};
        });
        assert.deepEqual(results.failed, []);
        assert.equal(results.untouched, 0);
        await page.locator('#addOutline').click();
        await page.locator('#addSeat').click();
        assert.equal(await page.locator('#size0').isDisabled(), true);
        assert.equal(await page.locator('#scale').isDisabled(), true);
        await page.getByText('Модель и ориентиры', {exact: true}).click();
        const modelY = page.getByRole('spinbutton', {name: 'Смещение модели (1 блок = 16) Y', exact: true});
        await modelY.fill('8');
        await modelY.press('Tab');
        await page.getByRole('button', {name: '＋ Фантомный блок'}).click();
        assert.deepEqual(await page.evaluate(() => window.DW_V22.references.children[0].position.toArray()),
                         [24, 24, 8]);
        const exported = await page.evaluate(() => window.DW_TEST.exportProject());
        assert.equal(exported.schemaVersion, 4);
        assert.equal(exported.models.length, 1);
        const state = exported.models[0].states[0];
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
        await page.evaluate(() => window.DW_TEST.frameScene());
        await page.screenshot({path: path.join(directory, 'studio.png')});
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
