"""Small, checked changes to the pinned V4 editor instead of copying its vendor assets."""

import json


def replace_once(text, source, replacement):
    if text.count(source) != 1:
        raise ValueError(f"V4 editor anchor changed: {source[:100]}")
    return text.replace(source, replacement, 1)


def replace_section(text, start, end, replacement):
    first = text.index(start)
    last = text.index(end, first)
    return replace_once(text, text[first:last], replacement + "\n")


def patch_app(app):
    app = app.replace("transform.detach()", "detachVolumeHandles()")
    app = replace_section(app, "function snapshot(){", "function changed()", """
function syncHistoryButtons() {
    $('undo').disabled = !history.length;
    $('redo').disabled = !future.length;
}
function recordEdit(swap, target) {
    history.push({swap, target});
    if (history.length > 100) history.shift();
    future = [];
    syncHistoryButtons();
}
function rememberLayout(before, modelId = entry.id, key = state.key) {
    let saved = before;
    recordEdit(() => {
        const layouts = projects[modelId] ||= {};
        const current = layouts[key];
        if (saved) layouts[key] = saved;
        else delete layouts[key];
        saved = current;
        if (entry.id !== modelId || state.key !== key) {
            selectEntry(modelId, key);
        } else {
            const selectedId = selected?.id;
            selected = work().objects.find(object => object.id === selectedId) || null;
            modelRoot.position.fromArray(work().modelOffset || [0, 0, 0]);
            renderBoxes();
            renderObjects();
            syncFields();
            window.DW_V22?.refresh();
            updateStats();
        }
        saveLocal();
    }, JSON.stringify([modelId, key]));
}
function snapshot() {
    rememberLayout(clone(work()));
}
function commitLayout(before) {
    if (JSON.stringify(before) === JSON.stringify(work())) return;
    rememberLayout(before);
    changed();
}
function forgetImportedLayouts(loaded) {
    const replaced = new Set(loaded.map(target => JSON.stringify(target)));
    history = history.filter(action => !replaced.has(action.target));
    future = future.filter(action => !replaced.has(action.target));
    syncHistoryButtons();
}""".lstrip())
    app = replace_section(app, "function attach(){", "function renderObjects()", """
function detachVolumeHandles() {
    transform.detach();
    window.DW_V22?.resize.detach();
}
function attach() {
    if (selected) window.DW_V22?.pickVolumes();
    const mesh = boxRoot.children.find(group => group.name === selected?.id);
    if (!mesh || !mesh.visible || !boxRoot.visible || !poseMatches()) {
        detachVolumeHandles();
        syncFields();
        return;
    }
    if (selected.type === 'seat' && transform.mode === 'scale') {
        setMode('translate');
        return;
    }
    if (transform.mode === 'scale') {
        transform.detach();
        window.DW_V22?.resize.attach(mesh, selected);
    } else {
        window.DW_V22?.resize.detach();
        transform.attach(mesh);
    }
    syncFields();
}""".lstrip())
    app = replace_section(app, "function undo(redo){", "$('undo').onclick", """
function editingDrag() {
    return transform.dragging || window.DW_V22?.dragging;
}
function undo(redo) {
    if (editingDrag()) return;
    const source = redo ? future : history;
    const destination = redo ? history : future;
    const action = source.pop();
    if (!action) return;
    action.swap();
    destination.push(action);
    syncHistoryButtons();
}""".lstrip())
    app = replace_section(app, "function fillStates(){", "function loadState(frame=false)", """
function hasLayoutEdits(model, variant) {
    return DW_PROJECT.stateChanged(variant, projects[model.id]?.[variant.key]);
}
function fillStates() {
    const filter = $('stateSearch').value.toLowerCase();
    const options = $('state');
    options.replaceChildren();
    for (const variant of entry.states) {
        if (variant !== state && !variant.label.toLowerCase().includes(filter)
                && !variant.key.toLowerCase().includes(filter)) continue;
        options.add(new Option(variant.label + (hasLayoutEdits(entry, variant) ? ' ★' : ''), variant.key));
    }
    options.value = state.key;
}
function selectEntry(id, key) {
    if (entry && dirty) saveLocal();
    entry = listEntries().find(model => model.id === id) || D.entries[0];
    state = entry.states.find(variant => variant.key === key)
        || entry.states.find(variant => hasLayoutEdits(entry, variant)) || entry.states[0];
    $('modelName').textContent = entry.name;
    $('modelId').textContent = entry.id + (entry.number ? ' · № ' + entry.number : '');
    $('stateSearch').value = '';
    loadState(true);
}
""".lstrip())
    app = replace_section(app, "function renderLibrary(){", "function hasLayoutEdits(", """
const libraryButtons = new Map();
let visibleLibraryIds = [];
function renderLibrary() {
    const query = $('search').value.toLowerCase();
    const root = $('modelList');
    const matches = listEntries().filter(model =>
        [model.name, model.id, model.number || ''].join(' ').toLowerCase().includes(query));
    const buttons = matches.map(model => {
        let button = libraryButtons.get(model.id);
        if (!button) {
            button = document.createElement('button');
            button.className = 'modelItem';
            button.dataset.modelId = model.id;
            button.append(document.createTextNode(''), document.createElement('span'), document.createElement('small'));
            button.onclick = () => selectEntry(model.id);
            libraryButtons.set(model.id, button);
        }
        button.classList.toggle('selected', entry?.id === model.id);
        if (button.firstChild.data !== model.name) button.firstChild.data = model.name;
        const star = button.children[0];
        const excluded = excludedModels.has(model.id);
        const changed = DW_PROJECT.modelChanged(model, projects, anchors);
        const status = excluded ? 'excluded' : changed ? 'changed' : model.previouslyEdited ? 'applied' : '';
        if (status && star.dataset.status !== status) {
            star.className = 'model-star ' + status;
            star.title = {excluded: 'Не применять изменения этой модели', changed: 'Есть новые правки',
                applied: 'Разметка применена в предыдущей сборке'}[status];
            star.textContent = ' ★';
        } else if (!status && star.dataset.status) {
            star.className = '';
            star.title = '';
            star.textContent = '';
        }
        star.dataset.status = status;
        const label = (model.number ? model.number + ' · ' : '') + model.id;
        if (button.lastChild.textContent !== label) button.lastChild.textContent = label;
        return button;
    });
    if (buttons.length !== visibleLibraryIds.length
            || buttons.some((button, index) => button.dataset.modelId !== visibleLibraryIds[index])) {
        root.replaceChildren(...buttons);
        visibleLibraryIds = buttons.map(button => button.dataset.modelId);
    }
    $('count').textContent = buttons.length;
}
""".lstrip())
    app = replace_section(app, "function updateStats(){", "for(const key of ['position','size','rotation'])", """
function updateStats() {
    let states = 0;
    let objects = 0;
    for (const model of listEntries()) {
        for (const state of model.states) {
            const layout = projects[model.id]?.[state.key];
            if (!DW_PROJECT.stateChanged(state, layout)) continue;
            states++;
            objects += layout.objects.length;
        }
    }
    const current = work().objects;
    const seats = current.filter(object => object.type === 'seat').length;
    $('stats').textContent = 'Изменено вариантов: ' + states + ' · опорных клеток: ' + anchors.size + ' · элементов: ' + objects
        + '. В текущем: ' + (current.length - seats) + ' коллизий, ' + seats + ' сидений.';
    fillStates();
    renderLibrary();
}""".lstrip())
    app = replace_section(app, "$('copyState').onclick=", "function animData()", """
function copyModelLabel(model) {
    return model.number ? model.number + ' · ' + model.name : model.name;
}
function copyDestination() {
    return listEntries().find(model => model.id === $('copyModel').value);
}
function updateCopyVariants() {
    const destination = copyDestination();
    const variants = $('copyTarget');
    variants.replaceChildren();
    for (const variant of destination?.states || []) {
        if (destination.id !== entry.id || variant.key !== state.key) {
            variants.add(new Option(variant.label, variant.key));
        }
    }
    variants.disabled = $('copyConfirm').disabled = !variants.options.length;
}
$('copyState').onclick = () => {
    const models = $('copyModel');
    const candidates = listEntries().filter(model => model.id !== entry.id || model.states.length > 1);
    models.replaceChildren(new Option('Выберите модель…', ''),
        ...candidates.map(model => new Option(copyModelLabel(model), model.id)));
    models.value = entry.states.length > 1 ? entry.id : '';
    updateCopyVariants();
    $('copyDialog').showModal();
    models.focus();
};
$('copyModel').onchange = updateCopyVariants;
$('copyCancel').onclick = () => $('copyDialog').close();
$('copyConfirm').onclick = () => {
    const destination = copyDestination();
    const variant = destination?.states.find(variant => variant.key === $('copyTarget').value);
    if (!variant || (destination.id === entry.id && variant.key === state.key)) return;
    const sameModel = destination.id === entry.id;
    const previous = projects[destination.id]?.[variant.key];
    const replaced = sameModel ? previous : previous && {
        objects: previous.objects, modelOffset: variant.baselineModelOffset,
    };
    if (DW_PROJECT.stateChanged(variant, replaced) && !confirm('Заменить правки разметки выбранного варианта?')) return;
    const copied = clone(work());
    if (!sameModel) {
        copied.modelOffset = [...(previous?.modelOffset || variant.baselineModelOffset || [0, 0, 0])];
        copied.reviewed = false;
    }
    rememberLayout(previous ? clone(previous) : undefined, destination.id, variant.key);
    const layouts = projects[destination.id] ||= {};
    layouts[variant.key] = {...copied, edited: true};
    $('copyDialog').close();
    if (sameModel) updateStats();
    else selectEntry(destination.id, variant.key);
    saveLocal();
    $('status').textContent = 'Разметка скопирована в ' + copyModelLabel(destination) + ' · ' + variant.label;
};
""".lstrip())
    app = replace_section(app, "function exportProject(){", "function download(", """
function exportProject(forCodex = false) {
    return DW_PROJECT.exportProject(CONTRACT, listEntries(), projects, custom, excludedModels, anchors, forCodex);
}""".lstrip())
    app = replace_section(app, "async function importProject(file){", "function restoreCustom", """
async function importProject(file) {
    const raw = JSON.parse(await DW_PROJECT.projectText(file));
    const project = validateProject(window.DW_V22.upgradeProject(raw));
    const migrated = DW_PROJECT.migrateShutterLayouts(project);
    const skipped = DW_PROJECT.skipRetiredLayouts(project);
    const merge = choices => DW_PROJECT.mergeProject(project, listEntries(), projects, custom, excludedModels, anchors, SEAT, choices);
    let merged = merge();
    let replaced = 0;
    if (!merged.projects) {
        const choices = await window.DW_V22.chooseConflicts(merged.conflicts);
        if (!choices) {
            $('status').textContent = 'Загрузка проекта отменена';
            return 0;
        }
        replaced = choices.size;
        merged = merge(choices);
    }
    projects = merged.projects;
    custom = merged.custom;
    const exclusions = [...merged.excluded].filter(id => !excludedModels.has(id));
    excludedModels = merged.excluded;
    anchors = merged.anchors;
    forgetImportedLayouts([...merged.loaded, ...merged.replacedStates, ...exclusions.map(id => [id, null]),
        ...merged.loadedAnchors.map(id => [id, null, 'anchorCell'])]);
    const loadedVariant = merged.loaded.find(([id]) => id === entry.id);
    const shownKey = loadedVariant && !hasLayoutEdits(entry, state) ? loadedVariant[1] : state.key;
    if (merged.updatedModels.has(entry.id) || shownKey !== state.key) {
        selectEntry(entry.id, shownKey);
    } else if (merged.loaded.some(([id, key]) => id === entry.id && key === state.key)) {
        loadState(false);
    } else {
        $('excludeModel').checked = excludedModels.has(entry.id);
        updateStats();
    }
    if (merged.loadedAnchors.includes(entry.id)) window.DW_V22.refreshAnchor();
    saveLocal();
    $('status').textContent = 'Объединено вариантов: ' + merged.loaded.length
        + (migrated ? ' · перенесено на 93241: ' + migrated : '')
        + (skipped ? ' · убрано из каталога: ' + skipped : '')
        + (merged.modelsAdded ? ' · добавлено моделей: ' + merged.modelsAdded : '')
        + (merged.loadedAnchors.length ? ' · опорных клеток: ' + merged.loadedAnchors.length : '')
        + (replaced ? ' · заменено по выбору: ' + replaced : '')
        + (merged.conflicts.length > replaced ? ' · оставлено текущих: ' + (merged.conflicts.length - replaced) : '');
    return merged.loaded.length;
}""".lstrip())
    app = replace_section(app, "window.addEventListener('keydown',e=>", "$('help').onclick", """
window.addEventListener('keydown', event => {
    if (event.defaultPrevented || event.isComposing || event.altKey
            || document.querySelector('dialog[open]') || editingDrag()) return;
    const key = event.key.toLowerCase();
    const typing = event.target.closest?.('textarea, [contenteditable="true"], input:not([type]), input[type="text"], input[type="number"]');
    if (event.ctrlKey || event.metaKey) {
        const isZ = event.code === 'KeyZ' || key === 'z';
        const isY = event.code === 'KeyY' || key === 'y';
        if ((!isZ && !isY) || (typing && typing.type !== 'number')) return;
        event.preventDefault();
        // Commit a numeric edit first so the shortcut can undo that edit.
        if (typing) typing.blur();
        undo(isY || event.shiftKey);
        return;
    }
    if (typing || event.target.tagName === 'SELECT' && key !== 'delete') return;
    if (key === 'delete') {
        event.preventDefault();
        if (!event.repeat && !window.DW_V22?.removePickedReference()) $('delete').click();
        return;
    }
    const shortcuts = {KeyW: 'translate', KeyE: 'rotate', KeyR: 'scale'};
    const mode = shortcuts[event.code];
    if (mode) setMode(mode);
    if (event.code === 'KeyF' || key === 'f') frameScene();
});""".lstrip())
    edits = {
        "let projects={},custom=[],entry,state,":
            "let projects={},custom=[],excludedModels=new Set(),anchors=new Map(),entry,state,",
        "{objects:[],reviewed:false,edited:false}":
            "{objects:clone(state.baseline||[]),modelOffset:clone(state.baselineModelOffset||[0,0,0]),reviewed:false,edited:false}",
        "selected=null;history=[];future=[];playing=false;":
            "window.DW_V22?.cancelTranslation();selected=null;playing=false;",
        "else if(dragStart){history.push(dragStart);future=[];dragStart=null;changed();}":
            "else if(dragStart){const before=dragStart;dragStart=null;commitLayout(before);}",
        "modelRoot.scale.set(1,1,1);for":
            "modelRoot.scale.set(1,1,1);modelRoot.position.fromArray(work().modelOffset||[0,0,0]);for",
        "if(frame)frameScene();$('loading').hidden=true;":
            "window.DW_V22?.refresh();if(frame)frameScene();$('loading').hidden=true;",
        "function add(type){":
            "function add(type,mode='players'){",
        "const v=Number(input.value);if(!Number.isFinite(v)":
            "const v=input.valueAsNumber;if(!Number.isFinite(v)",
        "type,mode:'both',name:(type==='seat'?'Сидение ':'Коллизия ')":
            "type,mode,name:(type==='seat'?'Сидение ':mode==='outline'?'Обводка ':'Коллизия ')",
        "o.mode==='players'?0xc294f9:0x5fdda4":
            "o.mode==='players'?0xc294f9:o.mode==='outline'?0xfa785f:0x5fdda4",
        "o.mode==='players'?0xc294f9:0x78f7bf":
            "o.mode==='players'?0xc294f9:o.mode==='outline'?0xfa785f:0x78f7bf",
        "mesh.userData.objectId=o.id;group.add(mesh);":
            "mesh.userData.objectId=o.id;group.userData.volumeMode=seat?'seat':o.mode;group.add(mesh);",
        "boxRoot.visible=$('showBoxes').checked;if(selected)":
            "boxRoot.visible=$('showBoxes').checked;window.DW_V22?.refreshVolumes();if(selected)",
        "if(e.button!==0||transform.axis)return;":
            "if(e.button!==0||window.DW_V22?.routeScenePointer(eventPointer(e))||transform.axis)return;",
        "const r=renderer.domElement.getBoundingClientRect(),ray=new THREE.Raycaster();ray.setFromCamera(new THREE.Vector2((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1),camera);":
            "const ray=new THREE.Raycaster();ray.setFromCamera(eventPointer(e),camera);",
        ".filter(x=>x.object.userData.objectId)":
            ".filter(x=>boxRoot.visible&&x.object.parent.visible&&x.object.userData.objectId)",
        "schemaVersion:3,unitsPerBlock":
            "schemaVersion:4,unitsPerBlock",
        "emptyReviewedState:'remove_player_and_block_colliders_and_seats'":
            "emptyReviewedState:'remove_player_and_block_colliders_outlines_and_seats',instanceScope:'new_placements_only',profileScope:'all'",
        "'block_support','block_selection','non_player_physics','render_geometry'":
            "'block_support','non_player_physics'",
        "![1,2,3].includes(p.schemaVersion)":
            "![1,2,3,4].includes(p.schemaVersion)",
        "(p.schemaVersion===3&&!['both','blocks','players'].includes(b.mode))":
            "(p.schemaVersion>=3&&!(p.schemaVersion===4?['both','blocks','players','outline']:['both','blocks','players']).includes(b.mode))",
        "if(!Array.isArray(s.colliders)||!Array.isArray(s.seats))":
            "if(p.schemaVersion===4&&!vec(s.modelOffset))throw Error('Неверное смещение модели');if(!Array.isArray(s.colliders)||!Array.isArray(s.seats))",
        "if(typeof m.registryId!=='string'||!Array.isArray(m.states))throw Error('Неверная модель');":
            "if(typeof m.registryId!=='string'||!Array.isArray(m.states))throw Error('Неверная модель');"
            "if(Object.hasOwn(m,'anchorCell')&&!DW_PROJECT.validAnchorCell(m.anchorCell))throw Error('Неверная опорная клетка');",
        " BASE/ALT и диагональные состояния редактируются отдельно.":
            " Диагональные варианты и позы редактируются отдельно. Разметка применяется к Original и ALT.",
        "$('projectFile').onchange=async e=>{try{await importProject(e.target.files[0]);}catch(err){alert(err.message);}e.target.value='';};":
            """$('projectFile').onchange = async event => {
    const file = event.target.files[0];
    if (!file) return;
    $('importProject').disabled = true;
    try {
        await importProject(file);
    } catch (error) {
        alert(error.message);
    } finally {
        event.target.value = '';
        $('importProject').disabled = false;
    }
};""",
        "p.policy?.operation!=='replace_per_exact_state'||p.coordinates":
            "p.policy?.operation!=='replace_per_exact_state'||p.policy?.profileScope!=='all'||p.coordinates",
        "$('reviewed').checked=work().reviewed;":
            "$('excludeModel').checked=excludedModels.has(entry.id);",
        "$('reviewed').onchange=()=>{snapshot();work().reviewed=$('reviewed').checked;changed();};":
            """$('excludeModel').onchange = () => {
    const id = entry.id;
    const apply = value => {
        if (value) excludedModels.add(id);
        else excludedModels.delete(id);
        if (entry.id === id) $('excludeModel').checked = value;
        renderLibrary();
        saveLocal();
    };
    let saved = excludedModels.has(id);
    const value = $('excludeModel').checked;
    if (value === saved) return;
    recordEdit(() => {
        const current = excludedModels.has(id);
        apply(saved);
        saved = current;
    }, JSON.stringify([id, null]));
    apply(value);
};""",
        "$(m).classList.toggle('active',m===mode);}":
            "$(m).classList.toggle('active',m===mode);if(selected)attach();}",
        "function setMode(mode){if(mode==='scale'":
            "function setMode(mode){if(window.DW_V22&&!window.DW_V22.editingVolumes())return;if(mode==='scale'",
        "$('scale').disabled=disabled||selected.type==='seat';":
            "const volumeTools=window.DW_V22?.editingVolumes()??true;"
            "$('translate').disabled=$('rotate').disabled=$('space').disabled=!volumeTools;"
            "$('fitGrid').disabled=$('scale').disabled=!volumeTools||disabled||selected.type==='seat';",
        "$('showModel').onchange=()=>modelRoot.visible=$('showModel').checked;":
            "$('showModel').onchange=()=>{modelRoot.visible=$('showModel').checked;window.DW_V22?.refreshModelControl();};",
        "const project=exportProject();if(!project.models.length){alert('Сначала измените или отметьте завершённым хотя бы один вариант');":
            "const project=exportProject(true);if(!project.models.length){alert('Нет изменений для пакета. "
            "Отредактируйте модель или снимите «Не применять изменения».');",
        "JSON — полный проект, его можно снова открыть в редакторе.":
            "ZIP содержит изменения для применения. Полный рабочий файл сохраняется кнопкой «Сохранить JSON».",
        "zip.file('APPLY_IN_CODEX_RU.md',window.DW_INSTRUCTIONS)":
            "zip.file('APPLY_IN_CODEX_RU.md',window.DW_V22.instructions)",
        "JSON.stringify(window.DW_SCHEMA,null,2)":
            "JSON.stringify(window.DW_V22.schema,null,2)",
        "renderer.render(scene,camera);":
            "window.DW_V22?.resize.update();renderer.render(scene,camera);",
        "projects=saved.projects||{};restoreCustom":
            "projects=saved.projects||{};excludedModels=new Set(saved.excludedModels||[]);anchors=new Map(saved.anchorCells||[]);restoreCustom",
        "const p=exportProject();localStorage.setItem(STORAGE,JSON.stringify({projects,custom,assets:p.importedGeometry,":
            "localStorage.setItem(STORAGE,JSON.stringify({projects:DW_PROJECT.changedLayouts(listEntries(),projects),custom,excludedModels:[...excludedModels],"
            "anchorCells:[...anchors],assets:DW_PROJECT.embeddedAssets(custom),",
        "renderer.domElement.addEventListener('pointerdown',e=>":
            """function eventPointer(event) {
    const r = renderer.domElement.getBoundingClientRect();
    return new THREE.Vector2((event.clientX-r.left)/r.width*2-1, 1-(event.clientY-r.top)/r.height*2);
}
renderer.domElement.addEventListener('pointerdown',e=>""",
        "window.DW_TEST={exportProject":
            """syncHistoryButtons();
window.DW_TEST={
    anchorCell(){return DW_PROJECT.anchorCell(entry, anchors);},
    setAnchorCell(value){
        if (!DW_PROJECT.validAnchorCell(value)) return;
        const id = entry.id;
        const model = entry;
        const baseline = model.baselineAnchorCell || [0, 0, 0];
        let saved = DW_PROJECT.anchorCell(model, anchors);
        if (value.every((cell, axis) => cell === saved[axis])) return;
        const apply = cell => {
            if (cell.some((value, axis) => value !== baseline[axis])) anchors.set(id, [...cell]);
            else anchors.delete(id);
            if (entry.id !== id) selectEntry(id);
            else {
                window.DW_V22?.refreshAnchor();
                updateStats();
            }
            saveLocal();
        };
        recordEdit(() => {
            const current = DW_PROJECT.anchorCell(model, anchors);
            apply(saved);
            saved = current;
        }, JSON.stringify([id, null, 'anchorCell']));
        apply(value);
    },
    setModelOffset(value){
        const current = work().modelOffset || [0, 0, 0];
        if (value.length !== 3 || !value.every(n => Number.isFinite(n) && Math.abs(n) < 4096)) return;
        if (value.every((n, axis) => n === current[axis])) return;
        snapshot();
        work().modelOffset = [...value];
        modelRoot.position.fromArray(value);
        window.DW_V22?.refreshModelOffset();
        changed();
    },
    clearVolumeSelection(){detachVolumeHandles();selected=null;renderObjects();syncFields();},
    fitSelection(position,size){snapshot();selected.position=position;selected.size=size;renderBoxes();changed();},
    captureLayout(){playing=false;return clone(work());},
    commitResize:commitLayout,recordEdit,
    resizeSnap(){return Number($('snap').value);},
    grid,boxRoot,canvas:renderer.domElement,syncFields,attachVolume:attach,exportProject""",
    }
    for before, after in edits.items():
        app = replace_once(app, before, after)
    return app


def patch_assembly(script):
    script = replace_once(script, "DW_TEST.transform.detach();partControl.setMode(mode);",
                          "DW_TEST.clearVolumeSelection();window.DW_V22.pickVolumes();partControl.setMode(mode);")
    return replace_once(script, "DW_TEST.transform.object){",
                        "DW_TEST.transform.object||window.DW_V22?.resize.object||!window.DW_V22?.editingVolumes()){")


def patch_index(html):
    edits = {
        "COLLISION STUDIO · V10": "COLLISION STUDIO · V22",
        '<button id="redo">↷</button>':
            '<button id="redo" title="Повторить (Ctrl+Y)" aria-label="Повторить (Ctrl+Y)">↷</button>'
            '<div class="pick-mode" role="group" aria-label="Выбор в сцене">'
            '<button id="pickVolumes" class="active" aria-pressed="true" title="Выбирать коллизии, обводку и сидения">'
            'Разметка</button><button id="pickReferences" aria-pressed="false" title="Выбирать блоки-ориентиры">'
            'Ориентиры</button><button id="pickModel" aria-pressed="false" title="Двигать модель выбранного варианта">'
            'Модель</button><button id="pickAnchor" aria-pressed="false" title="Сдвигать клетку установки модели">'
            'Опорная клетка</button></div>',
        '<button id="undo">↶</button>':
            '<button id="undo" title="Отменить (Ctrl+Z)" aria-label="Отменить (Ctrl+Z)">↶</button>',
        'Ctrl+Z / Ctrl+Y — отмена и повтор. Delete удаляет выбранную разметку.':
            'Ctrl+Z / Ctrl+Y отменяют и повторяют правки. Delete удаляет выбранную разметку или ориентир.',
        '<input type="file" id="modelFiles"':
            '<dialog id="mergeDialog" aria-labelledby="mergeTitle"><form method="dialog">'
            '<h2 id="mergeTitle">Конфликты при загрузке</h2>'
            '<p>Выберите версию для каждой строки. Правки без конфликтов будут загружены вместе с выбранными.</p>'
            '<div id="mergeChoices"></div><div class="two"><button value="cancel">Отмена</button>'
            '<button class="primary" value="apply">Применить выбранное</button></div></form></dialog>'
            '<input type="file" id="modelFiles"',
        '<button id="scale">Размер · R</button>':
            '<button id="scale" title="Перетащите стрелку нужной грани">Размер · R</button>',
        '<option value="players">Только игроки</option>':
            '<option value="players">Только игроки</option><option value="outline">Только обводка</option>',
        '<div class="section">Коллизии и сидения</div>':
            '<div class="section">Коллизии, обводка и сидения</div>',
        '<button id="addSeat" class="seat">＋ Сидение</button></div>':
            '<button id="addSeat" class="seat">＋ Сидение</button></div><button id="addOutline">＋ Обводка</button>',
        '<div id="objects"></div><div id="properties">':
            '<div class="review"><label><input type="checkbox" id="excludeModel"> Не применять изменения</label>'
            '<p class="hint">Для всех вариантов этой модели. Правки сохраняются в рабочем JSON.</p>'
            '<button id="copyState">Копировать разметку…</button></div>'
            '<details id="volumeSettings"><summary>Настройки выбранной разметки</summary><div id="properties">',
        '<dialog id="copyDialog"><h2>Копировать разметку</h2>'
        '<p>Выберите вариант того же объекта. Его коллизии и сидения будут заменены копией текущих.</p>'
        '<select id="copyTarget" style="width:100%;margin-bottom:15px"></select>':
            '<dialog id="copyDialog" aria-labelledby="copyTitle"><h2 id="copyTitle">Копировать разметку</h2>'
            '<p>Разметка выбранного варианта будет заменена целиком, включая обводку и сиденья.</p>'
            '<label for="copyModel">Модель-получатель</label>'
            '<select id="copyModel" style="width:100%;margin-bottom:15px"></select>'
            '<label for="copyTarget">Вариант</label>'
            '<select id="copyTarget" style="width:100%;margin-bottom:15px"></select>',
        '<div id="objectTitle" class="section">Выберите разметку</div>':
            '<button id="fitGrid" type="button" '
            'title="Срезать выступы меньше половины клетки. При равных выступах на одной границе остаётся часть по положительной оси.">'
            'Подогнать под сетку Minecraft</button><div id="objectTitle" class="section">Выберите разметку</div>',
        '<div class="review"><label><input type="checkbox" id="reviewed"> Вариант завершён</label>'
        '<p class="hint">Экспорт содержит только изменённые или завершённые варианты. Пустой завершённый вариант явно '
        'удаляет его коллизии для игроков и блоков, а также сидения.</p>'
        '<button id="copyState">Копировать разметку в другой вариант</button></div>':
            '</details><div id="objects"></div>',
        '<script src="data.js"></script><script src="app.js"></script>':
            '<script src="data.js"></script><script src="studio-project.js"></script><script src="app.js"></script>',
        '<script src="pack-export.js"></script><script src="assembly.js"></script>':
            '<script src="assembly.js"></script><script src="studio-resize.js"></script><script src="studio-v22.js"></script>',
    }
    for before, after in edits.items():
        html = replace_once(html, before, after)
    return html


def patch_schema(raw):
    schema = json.loads(raw)
    schema["title"] = "Dreamwalker V22 Studio project"
    schema["properties"]["schemaVersion"] = {"const": 4}
    schema["properties"]["editor"] = {
        "type": "object",
        "properties": {"excludedModels": {"type": "array", "uniqueItems": True, "items": {"type": "string"}}},
    }
    schema["properties"]["models"]["items"]["properties"]["anchorCell"] = {
        "type": "array", "minItems": 3, "maxItems": 3,
        "items": {"type": "integer", "minimum": -255, "maximum": 255},
    }
    policy = schema["properties"]["policy"]
    policy["required"].append("profileScope")
    policy["properties"]["profileScope"] = {"const": "all"}
    def visit(value):
        if isinstance(value, dict):
            if "colliders" in value.get("properties", {}):
                value["required"].append("modelOffset")
                value["properties"]["modelOffset"] = {"$ref": "#/$defs/vector"}
            if value.get("enum") == ["both", "blocks", "players"]:
                value["enum"].append("outline")
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    visit(schema)
    return json.dumps(schema, ensure_ascii=False, indent=2) + "\n"
