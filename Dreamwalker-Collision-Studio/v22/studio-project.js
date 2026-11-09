'use strict';

const DW_PROJECT = (() => {
    const data = window.DW_DATA;
    const origin = [0, 0, 0];
    const anchorLimit = 255;

    function validAnchorCell(value) {
        return Array.isArray(value) && value.length === 3
            && value.every(cell => Number.isInteger(cell) && Math.abs(cell) <= anchorLimit);
    }

    function layoutKey(layout) {
        const vector = values => values.map(value => Math.round(value * 1e8) / 1e8);
        const volumes = layout.objects.map(volume => [
            volume.id, volume.type, volume.name, volume.type === 'box' ? volume.mode || 'both' : null,
            vector(volume.position), vector(volume.size), vector(volume.rotation),
        ]).sort((a, b) => a[0].localeCompare(b[0]));
        return JSON.stringify([vector(layout.modelOffset || origin), volumes]);
    }

    function stateChanged(state, layout) {
        return !!layout && layoutKey(layout) !== layoutKey({objects: state.baseline || []});
    }

    function modelChanged(model, projects, anchors) {
        return anchors.has(model.id) || model.kind === 'import' || model.states.some(state =>
            state.selector?.authoringPose || stateChanged(state, projects[model.id]?.[state.key]));
    }

    function writeState(state, layout, seatSize, forCodex) {
        return {
            key: state.key, selector: state.selector, replace: true,
            reviewed: forCodex || !!layout.reviewed, modelOffset: layout.modelOffset || origin,
            pose: {
                clip: state.clip || null, time: state.time || 0,
                hiddenBones: state.hiddenBones || [], boneOffsets: state.boneOffsets || {},
            },
            colliders: layout.objects.filter(volume => volume.type === 'box').map(volume => ({
                id: volume.id, name: volume.name, center: volume.position, size: volume.size,
                rotation: volume.rotation, mode: volume.mode || 'both',
            })),
            seats: layout.objects.filter(volume => volume.type === 'seat').map(volume => ({
                id: volume.id, name: volume.name, surfaceCenter: volume.position, rotation: volume.rotation,
                size: seatSize, capacity: 1, front: [0, 0, -1], trigger: 'right_click_surface',
            })),
        };
    }

    function embeddedAssets(entries) {
        const assets = {models: {}, textures: {}, animations: {}};
        for (const entry of entries) {
            const animation = entry.asset?.animation;
            if (animation && data.animations?.[animation]) assets.animations[animation] = data.animations[animation];
            for (const state of entry.states) {
                for (const part of state.parts) {
                    if (assets.models[part.model]) continue;
                    const model = data.models[part.model];
                    assets.models[part.model] = model;
                    for (const cube of model?.cubes || []) {
                        for (const face of Object.values(cube.faces)) {
                            if (face.texture) assets.textures[face.texture] = data.textures[face.texture];
                        }
                    }
                }
            }
        }
        return assets;
    }

    function exportProject(contract, entries, projects, custom, excluded, anchors, forCodex) {
        const include = entry => !forCodex || !excluded.has(entry.id);
        const models = [];
        for (const entry of entries.filter(include)) {
            const states = [];
            for (const state of entry.states) {
                const layout = projects[entry.id]?.[state.key];
                if (stateChanged(state, layout)) states.push(writeState(state, layout, contract.seatSize, forCodex));
            }
            const anchorCell = anchors.get(entry.id);
            if (states.length || anchorCell) models.push({
                registryId: entry.id, displayName: entry.name, source: entry.source,
                aliases: entry.aliases || [], kind: entry.kind, states,
                ...(anchorCell ? {anchorCell} : {}),
            });
        }
        const customAssets = custom.filter(include);
        const result = {
            ...contract, createdAt: new Date().toISOString(), models,
            customAssets, importedGeometry: embeddedAssets(customAssets),
        };
        if (!forCodex && excluded.size) result.editor = {excludedModels: [...excluded]};
        return structuredClone(result);
    }

    function readState(state, version, seatSize) {
        const colliders = state.colliders.map(volume => ({
            id: volume.id, type: 'box', name: volume.name, position: volume.center, size: volume.size,
            rotation: volume.rotation, mode: volume.mode || (version === 1 ? 'players' : 'both'),
        }));
        const seats = state.seats.map(volume => ({
            id: volume.id, type: 'seat', name: volume.name, position: volume.surfaceCenter,
            size: seatSize, rotation: volume.rotation,
        }));
        return {
            edited: true, reviewed: !!state.reviewed, modelOffset: state.modelOffset || origin,
            objects: [...colliders, ...seats],
        };
    }

    function migrateShutterLayouts(project) {
        const {target, sources} = data.shutterLayoutMigration;
        const oldModels = Object.keys(sources).map(id => project.models.find(model => model.registryId === id));
        const exclusions = project.editor?.excludedModels;
        const entry = data.entries.find(model => model.id === target);
        let windowModel = project.models.find(model => model.registryId === target);
        const directEdit = !!windowModel;
        let migrated = 0;
        for (const model of oldModels.filter(Boolean)) {
            const redirects = sources[model.registryId];
            const legacyStates = [];
            for (const saved of model.states) {
                const state = entry.states.find(pose => pose.key === redirects[saved.key]);
                if (state) legacyStates.push([saved, state]);
            }
            if (!legacyStates.length && !Object.hasOwn(model, 'anchorCell')) continue;
            if (!windowModel) {
                windowModel = {registryId: target, displayName: entry.name, kind: entry.kind, states: []};
                project.models.push(windowModel);
            }
            if (!Object.hasOwn(windowModel, 'anchorCell') && Object.hasOwn(model, 'anchorCell')) {
                windowModel.anchorCell = model.anchorCell;
            }
            for (const [saved, state] of legacyStates) {
                if (windowModel.states.some(current => current.key === state.key)) continue;
                windowModel.states.push({...saved, key: state.key, selector: state.selector});
                migrated++;
            }
            model.states = model.states.filter(saved => !Object.hasOwn(redirects, saved.key));
        }
        // Keep a direct 93241 entry's own exclusion setting when both IDs were edited.
        if (!directEdit && Array.isArray(exclusions) && !exclusions.includes(target)
                && exclusions.some(id => Object.hasOwn(sources, id))) {
            exclusions.push(target);
        }
        return migrated;
    }

    function skipRetiredLayouts(project) {
        const retired = data.retiredLayouts;
        if (!retired) return 0;
        const removed = new Set(retired.models);
        let skipped = 0;
        project.models = project.models.filter(model => {
            if (removed.has(model.registryId)) {
                const migrated = Object.hasOwn(data.shutterLayoutMigration.sources, model.registryId);
                skipped += migrated ? model.states.length : Math.max(1, model.states.length);
                return false;
            }
            if (!Object.hasOwn(retired.states, model.registryId)) return true;
            const oldKeys = retired.states[model.registryId];
            const before = model.states.length;
            model.states = model.states.filter(state => !oldKeys.includes(state.key.replace(/,profile=(base|alt)(?=,|$)/, '')));
            skipped += before - model.states.length;
            return before === model.states.length || model.states.length > 0 || Object.hasOwn(model, 'anchorCell');
        });
        if (Array.isArray(project.customAssets)) {
            project.customAssets = project.customAssets.filter(entry => !removed.has(entry?.id));
        }
        if (Array.isArray(project.editor?.excludedModels)) {
            project.editor.excludedModels = project.editor.excludedModels.filter(id => !removed.has(id));
        }
        return skipped;
    }

    const targetKey = (id, key) => JSON.stringify([id, key]);

    function mergeCustom(incoming, entries, custom, choose) {
        if (!Array.isArray(incoming)) throw Error('Неверный список дополнительных моделей');
        const assets = new Map(custom.map(entry => [entry.id, entry]));
        const extensions = new Map();
        const updatedModels = new Set();
        const geometry = new Map();
        const replacedStates = [];
        for (const saved of incoming) {
            if (!saved || typeof saved.id !== 'string' || !Array.isArray(saved.states)
                    || !saved.states.length || saved.states.some(state => typeof state?.key !== 'string'
                    || ['__proto__', 'constructor'].includes(state.key)
                    || !Array.isArray(state.parts))) throw Error('Неверная модель в проекте');
            const local = entries.get(saved.id);
            if (!saved.extension && !saved.id.startsWith('import:')) throw Error('Неверный ID дополнительной модели');
            if (!local) {
                if (saved.extension) throw Error('Неизвестная модель ' + saved.id);
                entries.set(saved.id, saved);
                assets.set(saved.id, saved);
                continue;
            }
            const existing = new Map(local.states.map(state => [state.key, state]));
            const candidates = saved.extension ? saved.states.filter(state => state.selector?.authoringPose) : saved.states;
            let updated = false;
            for (const state of candidates) {
                const previous = existing.get(state.key);
                if (previous) {
                    if (JSON.stringify(previous) === JSON.stringify(state)) continue;
                    const replace = choose(local, state, 'geometry');
                    geometry.set(targetKey(saved.id, state.key), replace);
                    if (!replace) continue;
                    replacedStates.push([saved.id, state.key]);
                }
                existing.set(state.key, state);
                updated = true;
            }
            if (updated) {
                const entry = {...local, states: [...existing.values()]};
                entries.set(saved.id, entry);
                updatedModels.add(saved.id);
                if (saved.extension) {
                    extensions.set(saved.id, entry.states);
                    assets.set(saved.id, {...entry, extension: true});
                } else {
                    assets.set(saved.id, entry);
                }
            }
        }
        return {custom: [...assets.values()], extensions, geometry, updatedModels, replacedStates};
    }

    function mergeAssets(entries, incoming) {
        const staged = {models: new Map(), textures: new Map(), animations: new Map()};
        function need(kind, id) {
            const current = data[kind] || {};
            const offered = incoming[kind] || {};
            if (Object.hasOwn(current, id)) return current[id];
            if (!Object.hasOwn(offered, id) || !offered[id]) throw Error('В проекте нет ресурса ' + id);
            staged[kind].set(id, offered[id]);
            return offered[id];
        }
        for (const entry of entries) {
            if (entry.asset?.animation) need('animations', entry.asset.animation);
            for (const state of entry.states) {
                for (const part of state.parts) {
                    const model = need('models', part.model);
                    for (const cube of model.cubes) {
                        for (const face of Object.values(cube.faces)) {
                            if (face.texture) need('textures', face.texture);
                        }
                    }
                }
            }
        }
        // Validate all newly needed resources before changing the current catalogue.
        for (const [kind, files] of Object.entries(staged)) {
            const target = data[kind] ||= {};
            for (const [id, file] of files) {
                Object.defineProperty(target, id, {value: file, writable: true, enumerable: true, configurable: true});
            }
        }
    }

    function mergeProject(project, entries, projects, custom, excluded, anchors, seatSize, choices) {
        const known = new Map(entries.map(entry => [entry.id, entry]));
        const local = new Map(known);
        const conflicts = new Map();
        function choose(entry, state, kind) {
            const id = JSON.stringify([entry.id, state.key, kind]);
            const model = entry.name + (entry.number ? ' · № ' + entry.number : '');
            const label = model + ' / ' + state.label + (kind === 'geometry' ? ' · геометрия' : '');
            conflicts.set(id, {id, label});
            return choices?.has(id) ?? false;
        }
        const merged = mergeCustom(project.customAssets || [], known, custom, choose);
        const next = {...projects};
        const loaded = [];
        const nextAnchors = new Map(anchors);
        const loadedAnchors = [];
        for (const model of project.models) {
            const entry = known.get(model.registryId);
            if (!entry) throw Error('Неизвестная модель ' + model.registryId);
            if (Object.hasOwn(model, 'anchorCell')) {
                const cell = model.anchorCell;
                const current = anchors.get(entry.id) || origin;
                const same = cell.every((value, axis) => value === current[axis]);
                const target = {key: 'anchorCell', label: 'опорная клетка'};
                if (!same && (!anchors.has(entry.id) || choose(entry, target, 'anchor'))) {
                    if (cell.some(value => value !== 0)) nextAnchors.set(entry.id, cell);
                    else nextAnchors.delete(entry.id);
                    loadedAnchors.push(entry.id);
                }
            }
            for (const saved of model.states) {
                const geometry = merged.geometry.get(targetKey(entry.id, saved.key));
                if (geometry === false) continue;
                const state = entry.states.find(state => state.key === saved.key);
                if (!state) throw Error('Неизвестное состояние ' + saved.key);
                const incoming = readState(saved, project.schemaVersion, seatSize);
                if (!stateChanged(state, incoming)) continue;
                const current = projects[entry.id]?.[state.key];
                if (stateChanged(state, current)) {
                    if (layoutKey(current) === layoutKey(incoming)) continue;
                    if (!geometry && !choose(entry, state, 'layout')) continue;
                }
                if (next[entry.id] === projects[entry.id]) next[entry.id] = {...projects[entry.id]};
                next[entry.id][state.key] = incoming;
                loaded.push([entry.id, state.key]);
            }
        }
        const savedExclusions = project.editor?.excludedModels ?? [];
        if (!Array.isArray(savedExclusions) || savedExclusions.some(id => typeof id !== 'string' || !known.has(id))) {
            throw Error('Неверный список исключённых моделей');
        }
        const exclusions = new Set(excluded);
        for (const id of savedExclusions) {
            const entry = local.get(id);
            if (!entry || !modelChanged(entry, projects, anchors)) exclusions.add(id);
        }

        if (conflicts.size && !choices) return {conflicts: [...conflicts.values()]};
        mergeAssets(merged.custom.filter(entry => !custom.includes(entry)), project.importedGeometry || {});
        for (const [id, states] of merged.extensions) {
            const entry = local.get(id);
            entry.states = states;
        }
        return {
            projects: next, custom: merged.custom, excluded: exclusions, anchors: nextAnchors,
            loaded, loadedAnchors, updatedModels: merged.updatedModels, replacedStates: merged.replacedStates,
            modelsAdded: known.size - local.size, conflicts: [...conflicts.values()],
        };
    }

    async function projectText(file) {
        if (!file.name.toLowerCase().endsWith('.zip')) return file.text();
        const zip = await JSZip.loadAsync(file);
        const member = zip.file('dreamwalker-collisions.json') || zip.file('studio.json');
        if (!member) throw Error('В ZIP нет dreamwalker-collisions.json или studio.json');
        return member.async('string');
    }

    return {stateChanged, modelChanged, embeddedAssets, exportProject, mergeProject, projectText,
        migrateShutterLayouts, skipRetiredLayouts,
        validAnchorCell, anchorLimit};
})();
