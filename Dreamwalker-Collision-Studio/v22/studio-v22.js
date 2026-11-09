'use strict';

(() => {
    const editor = window.DW_TEST;
    const units = 16;
    const resize = DW_RESIZE.create(editor);
    const references = new THREE.Group();
    editor.scene.add(references);
    const geometry = new THREE.BoxGeometry(units, units, units);
    const edgeGeometry = new THREE.EdgesGeometry(geometry);
    const phantom = new THREE.MeshBasicMaterial({
        color: 0x8daaba, opacity: 0.16, transparent: true, depthWrite: false, side: THREE.DoubleSide,
    });
    const edge = new THREE.LineBasicMaterial({color: 0xa4c6d7});
    const selectedEdge = new THREE.LineBasicMaterial({color: 0xffcf68});
    function axisMove(space, step) {
        const control = new THREE.TransformControls(editor.camera, editor.canvas);
        control.setMode('translate');
        control.setSpace(space);
        control.setTranslationSnap(step);
        control.enabled = false;
        control.visible = false;
        // Only the three axis arrows are pickable. The other handles move in a plane.
        const gizmo = control.children.find(child => child.type === 'TransformControlsGizmo');
        for (const handles of [gizmo.gizmo.translate, gizmo.picker.translate]) {
            handles.remove(...handles.children.filter(handle => !['X', 'Y', 'Z'].includes(handle.name)));
        }
        editor.scene.add(control);
        return control;
    }
    const moveReference = axisMove('local', units);
    const moveModel = axisMove('world', null);
    const moveAnchor = axisMove('local', units);
    const anchorGroup = new THREE.Group();
    anchorGroup.userData.reference = true;
    anchorGroup.visible = false;
    const anchorMarker = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial({
        color: 0xffc86b, transparent: true, opacity: .09, depthWrite: false,
    }));
    const anchorEdges = new THREE.LineSegments(edgeGeometry, new THREE.LineBasicMaterial({
        color: 0xffc86b, transparent: true, depthTest: false, depthWrite: false,
    }));
    anchorEdges.renderOrder = 1000;
    anchorMarker.add(anchorEdges);
    anchorGroup.add(anchorMarker);
    editor.scene.add(anchorGroup);
    let selectedReference = null;
    let referenceNumber = 0;
    let referenceStart = null;
    let modelStart = null;
    let anchorStart = null;
    let pickMode = 'volumes';
    const pickButtons = {
        volumes: document.getElementById('pickVolumes'),
        references: document.getElementById('pickReferences'),
        model: document.getElementById('pickModel'),
        anchor: document.getElementById('pickAnchor'),
    };
    const ray = new THREE.Raycaster();
    for (const [mode, button] of Object.entries(pickButtons)) button.onclick = () => setPickMode(mode);

    function setPickMode(mode) {
        if (pickMode === mode) return;
        if (pickMode === 'references') detachReference();
        pickMode = mode;
        for (const [name, button] of Object.entries(pickButtons)) {
            button.classList.toggle('active', name === mode);
            button.setAttribute('aria-pressed', String(name === mode));
        }
        if (mode === 'volumes') editor.syncFields();
        else editor.clearVolumeSelection();
        refreshModelControl();
        refreshAnchor();
    }

    function routeScenePointer(pointer) {
        if (pickMode !== 'references') return pickMode !== 'volumes';
        if (moveReference.object) {
            if (moveReference.dragging) return true;
            moveReference.pointerHover(pointer);
            if (moveReference.axis) return true;
        }
        references.updateWorldMatrix(true, true);
        ray.setFromCamera(pointer, editor.camera);
        const picked = ray.intersectObjects(references.children, false)[0]?.object;
        if (picked && picked !== selectedReference) selectReference(picked);
        return true;
    }

    const inspector = document.getElementById('inspector');
    const filters = document.createElement('fieldset');
    filters.className = 'volume-filters';
    filters.style.margin = '10px 0';
    const legend = document.createElement('legend');
    legend.textContent = 'Показывать в сцене';
    filters.append(legend);
    const modes = [
        ['players', 'Игроки', '#c294f9'], ['blocks', 'Блоки', '#68aafa'],
        ['outline', 'Обводка', '#fa785f'], ['seat', 'Сидения', '#ffcf68'],
    ];
    const visible = {};
    for (const [mode, name, color] of modes) {
        const label = document.createElement('label');
        label.style.color = color;
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = true;
        checkbox.onchange = () => {
            refreshVolumes();
            if (editor.selected) editor.attachVolume();
        };
        visible[mode] = checkbox;
        label.append(checkbox, name);
        filters.append(label);
    }
    inspector.insertBefore(filters, inspector.querySelector('.review'));

    function refreshVolumes() {
        for (const group of editor.boxRoot.children) {
            group.visible = visible[group.userData.volumeMode]?.checked ?? true;
        }
    }
    const layout = document.createElement('details');
    layout.style.margin = '12px 0';
    inspector.insertBefore(layout, document.getElementById('stats'));
    const summary = document.createElement('summary');
    summary.textContent = 'Смещение модели';
    layout.append(summary);

    function fields(container, label, step, values, change) {
        const group = document.createElement('div');
        const title = document.createElement('p');
        title.textContent = label + ' · X / Y / Z';
        group.append(title);
        const row = document.createElement('div');
        row.className = 'triple';
        group.append(row);
        const inputs = values.map((value, axis) => {
            const field = document.createElement('input');
            field.type = 'number';
            field.ariaLabel = label + ' ' + 'XYZ'[axis];
            field.step = step;
            field.value = value;
            field.onchange = change;
            row.append(field);
            return field;
        });
        container.append(group);
        return inputs;
    }

    const offsets = fields(layout, 'Смещение модели (1 блок = 16)', 1, [0, 0, 0], () => {
        const value = offsets.map(field => field.valueAsNumber);
        if (value.every(number => Number.isFinite(number) && Math.abs(number) < 4096)) {
            editor.setModelOffset(value);
        }
        refresh();
    });
    const hint = document.createElement('p');
    hint.className = 'hint';
    hint.textContent = 'Режим «Модель» над сценой перемещает её стрелками. Смещение действует на выбранный вариант.';
    layout.append(hint);

    function refreshModelOffset() {
        const value = editor.modelRoot.position.toArray();
        offsets.forEach((field, index) => { field.value = value[index]; });
    }

    function syncModelSnap() {
        const step = editor.resizeSnap();
        moveModel.setTranslationSnap(step > 0 ? step : null);
    }

    function refreshModelControl() {
        if (pickMode === 'model' && editor.modelRoot.visible) {
            syncModelSnap();
            if (!moveModel.object) moveModel.attach(editor.modelRoot);
            moveModel.enabled = true;
        } else {
            cancelModelMove();
            moveModel.detach();
            moveModel.enabled = false;
        }
    }

    function cancelModelMove() {
        if (!modelStart) return;
        editor.modelRoot.position.fromArray(modelStart);
        modelStart = null;
        moveModel.dragging = false;
        moveModel.axis = null;
        refreshModelOffset();
    }

    moveModel.addEventListener('dragging-changed', event => {
        editor.orbit.enabled = !event.value;
        if (event.value) {
            modelStart = editor.modelRoot.position.toArray();
        } else if (modelStart) {
            modelStart = null;
            editor.setModelOffset(editor.modelRoot.position.toArray());
        }
    });
    moveModel.addEventListener('objectChange', () => {
        const position = editor.modelRoot.position;
        position.clampScalar(-4095.999, 4095.999);
        refreshModelOffset();
    });
    document.getElementById('snap').addEventListener('change', syncModelSnap);

    const anchorPanel = document.createElement('fieldset');
    anchorPanel.className = 'anchor-controls';
    const anchorLegend = document.createElement('legend');
    anchorLegend.textContent = 'Опорная клетка';
    anchorPanel.append(anchorLegend);
    inspector.insertBefore(anchorPanel, filters);
    const anchorInputs = fields(anchorPanel, 'Смещение опорной клетки (в блоках)', 1, [0, 0, 0], () => {
        editor.setAnchorCell(anchorInputs.map(field => field.valueAsNumber));
        refreshAnchor();
    });
    const anchorHint = document.createElement('p');
    anchorHint.className = 'hint';
    anchorHint.textContent = 'Золотой куб показывает клетку установки. Сдвиг на одну клетку равен одному блоку и действует на все варианты модели.';
    anchorPanel.append(anchorHint);

    function markerCell() {
        return anchorMarker.position.toArray().map(value => Math.round(value / units));
    }

    function refreshAnchorFields() {
        const cell = markerCell();
        anchorInputs.forEach((field, axis) => { field.value = cell[axis]; });
    }

    function refreshAnchor() {
        const active = pickMode === 'anchor';
        if (!active) cancelAnchorMove();
        anchorPanel.hidden = !active;
        anchorGroup.visible = active;
        anchorGroup.position.set(gridCenter(), units / 2, gridCenter());
        anchorMarker.position.fromArray(editor.anchorCell().map(cell => cell * units));
        if (active) {
            if (!moveAnchor.object) moveAnchor.attach(anchorMarker);
        } else moveAnchor.detach();
        moveAnchor.enabled = active;
        refreshAnchorFields();
    }

    function cancelAnchorMove() {
        if (!anchorStart) return;
        anchorMarker.position.fromArray(anchorStart);
        anchorStart = null;
        moveAnchor.dragging = false;
        moveAnchor.axis = null;
        refreshAnchorFields();
    }

    moveAnchor.addEventListener('dragging-changed', event => {
        editor.orbit.enabled = !event.value;
        if (event.value) {
            anchorStart = anchorMarker.position.toArray();
        } else if (anchorStart) {
            anchorStart = null;
            editor.setAnchorCell(markerCell());
            refreshAnchor();
        }
    });
    moveAnchor.addEventListener('objectChange', () => {
        const limit = DW_PROJECT.anchorLimit * units;
        anchorMarker.position.clampScalar(-limit, limit);
        refreshAnchorFields();
    });

    const panel = document.createElement('details');
    panel.style.margin = '12px 0';
    inspector.insertBefore(panel, filters);
    const panelTitle = document.createElement('summary');
    panelTitle.textContent = 'Блоки-ориентиры';
    panel.append(panelTitle);

    const referenceList = document.createElement('select');
    referenceList.ariaLabel = 'Выбранный блок-ориентир';
    referenceList.style.width = '100%';
    referenceList.onchange = () => selectReference(references.getObjectByName(referenceList.value));
    const controls = document.createElement('div');
    controls.className = 'triple';
    controls.style.margin = '8px 0';
    const referenceHint = document.createElement('p');
    referenceHint.className = 'hint';
    referenceHint.textContent = 'В режиме «Ориентиры» куб можно выбрать в сцене. Стрелки двигают его на одну ячейку за шаг.';

    function gridCenter() {
        return editor.entry.kind === 'rp' ? 0 : units / 2;
    }

    function button(parent, label, action) {
        const element = document.createElement('button');
        element.type = 'button';
        element.textContent = label;
        element.onclick = action;
        parent.append(element);
        return element;
    }

    button(panel, '＋ Добавить ориентир', () => {
        const occupied = new Set(references.children.filter(mesh => mesh.position.y === 0 && mesh.position.z === 0)
            .map(mesh => mesh.position.x));
        let x = 0;
        while (occupied.has(x)) x += units;
        const mesh = new THREE.Mesh(geometry, phantom);
        mesh.name = String(++referenceNumber);
        mesh.position.set(x, 0, 0);
        mesh.add(new THREE.LineSegments(edgeGeometry, edge));
        references.add(mesh);
        selectReference(mesh);
        editor.recordEdit(() => toggleReference(mesh));
    });
    panel.append(referenceList, controls);
    const arrows = [];
    for (const axis of ['x', 'y', 'z']) {
        for (const step of [-1, 1]) {
            arrows.push(button(controls, axis.toUpperCase() + (step < 0 ? ' −' : ' ＋'), () => {
                if (!selectedReference) return;
                const previous = selectedReference.position.toArray();
                selectedReference.position[axis] += step * units;
                rememberReferenceMove(selectedReference, previous);
                updateReferenceList();
            }));
        }
    }
    const remove = button(panel, 'Удалить выбранный ориентир', removePickedReference);
    panel.append(referenceHint);

    function removePickedReference() {
        if (!selectedReference) return false;
        const removed = selectedReference;
        toggleReference(removed);
        editor.recordEdit(() => toggleReference(removed));
        return true;
    }

    function toggleReference(mesh) {
        if (mesh.parent === references) {
            const wasSelected = mesh === selectedReference;
            if (wasSelected) detachReference();
            references.remove(mesh);
            if (wasSelected) selectReference(references.children[0]);
        } else {
            references.add(mesh);
            if (pickMode === 'references') selectReference(mesh);
        }
        updateReferenceList();
    }

    function rememberReferenceMove(mesh, before) {
        let position = before;
        editor.recordEdit(() => {
            const current = mesh.position.toArray();
            mesh.position.fromArray(position);
            position = current;
            updateReferenceList();
        });
    }

    function updateReferenceList() {
        const options = document.createDocumentFragment();
        options.append(new Option('Выбрать ориентир…', ''));
        for (const mesh of [...references.children].sort((a, b) => Number(a.name) - Number(b.name))) {
            const cell = mesh.position.toArray().map(value => value / units);
            options.append(new Option(`Ориентир ${mesh.name} · ${cell.join(', ')}`, mesh.name));
        }
        referenceList.replaceChildren(options);
        referenceList.value = selectedReference?.name ?? '';
        referenceList.disabled = !references.children.length;
        for (const button of [...arrows, remove]) button.disabled = selectedReference === null;
    }

    function detachReference() {
        cancelReferenceMove();
        if (selectedReference) selectedReference.children[0].material = edge;
        selectedReference = null;
        moveReference.detach();
        moveReference.enabled = false;
        updateReferenceList();
    }

    function selectReference(mesh) {
        setPickMode('references');
        detachReference();
        if (!mesh) return;
        selectedReference = mesh;
        mesh.children[0].material = selectedEdge;
        moveReference.enabled = true;
        moveReference.attach(mesh);
        updateReferenceList();
    }

    moveReference.addEventListener('dragging-changed', event => {
        editor.orbit.enabled = !event.value;
        if (event.value) {
            referenceStart = selectedReference.position.toArray();
        } else if (referenceStart) {
            if (selectedReference.position.toArray().some((n, axis) => n !== referenceStart[axis])) {
                rememberReferenceMove(selectedReference, referenceStart);
            }
            referenceStart = null;
            updateReferenceList();
        }
    });

    function cancelReferenceMove() {
        if (!referenceStart) return;
        selectedReference.position.fromArray(referenceStart);
        referenceStart = null;
        moveReference.dragging = false;
        moveReference.axis = null;
        updateReferenceList();
    }

    function cancelTranslation() {
        cancelModelMove();
        cancelReferenceMove();
        cancelAnchorMove();
    }
    editor.canvas.addEventListener('pointercancel', cancelTranslation);
    window.addEventListener('blur', cancelTranslation);
    window.addEventListener('keydown', event => { if (event.key === 'Escape') cancelTranslation(); });

    document.getElementById('addOutline').onclick = () => editor.add('box', 'outline');
    const fitSize = new Intl.NumberFormat('ru-RU', {maximumFractionDigits: 3});
    document.getElementById('fitGrid').onclick = () => {
        const volume = editor.selected;
        if (volume?.type !== 'box') return;
        const origin = gridCenter() - units / 2;
        const fitted = DW_RESIZE.fitToGrid(volume, [origin, 0, origin]);
        const changes = fitted.size.flatMap((length, axis) => {
            const before = volume.size[axis];
            return length === before ? [] : [`${'XYZ'[axis]} ${fitSize.format(before)} → ${fitSize.format(length)}`];
        });
        const status = document.getElementById('status');
        if (!changes.length) {
            status.textContent = 'Подходящих для среза граней нет';
            return;
        }
        editor.fitSelection(fitted.position, fitted.size);
        status.textContent = 'Размер после среза · ' + changes.join(' · ') + ' ед.';
    };
    function refresh() {
        refreshModelOffset();
        refreshModelControl();
        editor.grid.position.set(gridCenter() - units / 2, -0.02, gridCenter() - units / 2);
        references.position.set(gridCenter(), units / 2, gridCenter());
        refreshAnchor();
        updateReferenceList();
    }

    function chooseConflicts(conflicts) {
        const dialog = document.getElementById('mergeDialog');
        const rows = document.getElementById('mergeChoices');
        rows.replaceChildren();
        const selectors = conflicts.map(conflict => {
            const row = document.createElement('label');
            const name = document.createElement('span');
            name.textContent = conflict.label;
            const select = document.createElement('select');
            select.add(new Option('Оставить текущую', 'keep'));
            select.add(new Option('Заменить из JSON', 'replace'));
            row.append(name, select);
            rows.append(row);
            return {id: conflict.id, select};
        });
        dialog.returnValue = 'cancel';
        return new Promise(resolve => {
            dialog.addEventListener('close', () => {
                const picked = selectors.filter(({select}) => select.value === 'replace').map(({id}) => id);
                resolve(dialog.returnValue === 'apply' ? new Set(picked) : null);
            }, {once: true});
            dialog.showModal();
        });
    }

    function upgradeProject(project) {
        const {source, previousProjectSource} = window.DW_DATA;
        if (project.schemaVersion !== 4) return project;
        const previousVisualPatches = [
            '6ead4394329586250dfd16cc0778e79ba1b094aab8aaf25defd8a7937b4a40b6',
            '502b18d246f1a1f4ab7b193ec4272c7c913fa64ab3688c135016cc6815d764c5',
            'd5c592dcb429c8dde72ef838548ea7a08942bca9eb8b9924e1a12b943df7e0b1',
        ];
        const supportedJars = [source.jarSha256, previousProjectSource?.jarSha256].filter(Boolean);
        const commit = project.source?.commit;
        if (commit && (commit === previousProjectSource?.commit || supportedJars.some(jar => {
            const shared = `v22:${jar}:shared-profiles`;
            return commit === shared || previousVisualPatches.some(hash => commit === `${shared}:${hash}`);
        }))) {
            project.source = source;
            return project;
        }
        if (!supportedJars.some(jar => commit === 'v22:' + jar)) return project;
        for (const model of project.models) {
            const entry = window.DW_DATA.entries.find(candidate => candidate.id === model.registryId);
            const retiredShutter = Object.hasOwn(window.DW_DATA.shutterLayoutMigration.sources, model.registryId);
            if (!entry && !retiredShutter) continue;
            const merged = new Map();
            for (const state of model.states) {
                const key = state.key.replace(/,profile=(base|alt)(?=,|$)/, '');
                const pose = entry?.states.find(candidate => candidate.key === key);
                if (pose || retiredShutter) {
                    state.key = key;
                    if (pose) state.selector = pose.selector;
                }
                const previous = merged.get(state.key);
                if (previous) {
                    const data = state => JSON.stringify([state.modelOffset, state.colliders, state.seats]);
                    if (data(previous) !== data(state)) {
                        throw new Error(`Разная разметка Original и ALT у ${model.registryId}, ${key}. Сохрани нужный вариант отдельно в старом редакторе.`);
                    }
                    previous.reviewed ||= state.reviewed;
                } else merged.set(state.key, state);
            }
            model.states = [...merged.values()];
        }
        for (const extension of project.customAssets || []) {
            if (!extension.extension) continue;
            const entry = window.DW_DATA.entries.find(candidate => candidate.id === extension.id);
            if (entry) extension.states = [...entry.states,
                ...extension.states.filter(state => state.selector?.authoringPose === true)];
        }
        project.source = source;
        project.policy.profileScope = 'all';
        return project;
    }

    const help = document.createElement('p');
    help.textContent = 'Фиолетовый объём останавливает игрока, синий резервирует место для блоков, красный задаёт обводку, жёлтая стрелка показывает направление сидения. В режиме размера каждая стрелка двигает одну грань. Подгонка к сетке срезает выступы меньше половины клетки и сохраняет ненулевой размер. Синяя звёздочка отмечает уже применённую разметку, красная — новые правки, жёлтая — исключение из пакета для Codex.';
    document.getElementById('helpDialog').append(help);
    window.DW_V22 = {
        refresh, refreshVolumes, refreshModelOffset, refreshModelControl, refreshAnchor, cancelTranslation,
        references, moveReference, moveModel, moveAnchor, resize, upgradeProject, chooseConflicts,
        pickVolumes: () => setPickMode('volumes'), editingVolumes: () => pickMode === 'volumes',
        routeScenePointer, removePickedReference,
        get dragging() { return resize.dragging || moveReference.dragging || moveModel.dragging || moveAnchor.dragging; },
        instructions: window.DW_V22_INSTRUCTIONS, schema: window.DW_V22_SCHEMA,
    };
    refresh();
})();
