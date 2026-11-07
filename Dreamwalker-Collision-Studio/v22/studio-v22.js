'use strict';

(() => {
    const editor = window.DW_TEST;
    const references = new THREE.Group();
    references.userData.reference = true;
    editor.scene.add(references);
    editor.grid.position.set(0, -0.02, 0);
    const geometry = new THREE.BoxGeometry(16, 16, 16);
    const edgeGeometry = new THREE.EdgesGeometry(geometry);
    const phantom = new THREE.MeshBasicMaterial({
        color: 0x8daaba, opacity: 0.16, transparent: true, depthWrite: false,
    });
    const edge = new THREE.LineBasicMaterial({color: 0xa4c6d7});
    const inspector = document.getElementById('inspector');
    const filters = document.createElement('fieldset');
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
        label.style.marginRight = '8px';
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = true;
        checkbox.onchange = refreshVolumes;
        visible[mode] = checkbox;
        label.append(checkbox, name);
        filters.append(label);
    }
    inspector.insertBefore(filters, document.getElementById('objects'));

    function refreshVolumes() {
        for (const group of editor.boxRoot.children) {
            group.visible = visible[group.userData.volumeMode]?.checked ?? true;
        }
        if (editor.transform.object?.visible === false) editor.transform.detach();
    }
    const layout = document.createElement('details');
    layout.style.margin = '12px 0';
    inspector.insertBefore(layout, document.getElementById('stats'));
    const summary = document.createElement('summary');
    summary.textContent = 'Модель и ориентиры';
    layout.append(summary);

    function fields(label, step, values, change) {
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
        layout.append(group);
        return inputs;
    }

    const offsets = fields('Смещение модели (1 блок = 16)', 1, [0, 0, 0], () => {
        const value = offsets.map(field => field.valueAsNumber);
        if (value.every(number => Number.isFinite(number) && Math.abs(number) < 4096)) {
            editor.setModelOffset(value);
        }
        refresh();
    });
    const cell = fields('Ячейка ориентира (в блоках)', 1, [1, 1, 0], () => {});
    const hint = document.createElement('p');
    hint.className = 'hint';
    hint.textContent = 'Смещение действует только на модель выбранного варианта. Разметку можно двигать отдельно. Ориентиры служат только для просмотра.';
    layout.append(hint);

    function anchor() {
        return editor.entry.kind === 'rp' ? 0 : 8;
    }

    function button(label, action) {
        const element = document.createElement('button');
        element.textContent = label;
        element.onclick = action;
        layout.append(element);
    }

    button('＋ Фантомный блок', () => {
        const coordinates = cell.map(input => input.valueAsNumber);
        if (!coordinates.every(value => Number.isInteger(value) && Math.abs(value) <= 64)) return;
        const key = coordinates.join(',');
        if (references.getObjectByName(key)) return;
        const [x, y, z] = coordinates;
        const mesh = new THREE.Mesh(geometry, phantom);
        mesh.name = key;
        mesh.userData.cell = coordinates;
        mesh.position.set(x * 16 + anchor(), y * 16 + 8, z * 16 + anchor());
        mesh.add(new THREE.LineSegments(edgeGeometry, edge));
        references.add(mesh);
    });
    button('Убрать ориентиры', () => references.clear());
    document.getElementById('addOutline').onclick = () => editor.add('box', 'outline');
    function refresh() {
        const value = editor.work().modelOffset || [0, 0, 0];
        offsets.forEach((field, index) => { field.value = value[index]; });
        editor.grid.position.set(anchor() - 8, -0.02, anchor() - 8);
        for (const mesh of references.children) {
            const [x, y, z] = mesh.userData.cell;
            mesh.position.set(x * 16 + anchor(), y * 16 + 8, z * 16 + anchor());
        }
    }

    const help = document.createElement('p');
    help.textContent = 'Фиолетовый объём останавливает игрока, синий резервирует место для блоков, красный задаёт обводку, жёлтая стрелка показывает направление сидения. Пакет для Codex содержит изменённые состояния.';
    document.getElementById('helpDialog').append(help);
    window.DW_V22 = {
        refresh, refreshVolumes, references, instructions: window.DW_V22_INSTRUCTIONS, schema: window.DW_V22_SCHEMA,
    };
    refresh();
})();
