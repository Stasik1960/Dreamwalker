'use strict';

const DW_RESIZE = (() => {
    const cellSize = 16;
    const epsilon = 1e-8;
    const y = new THREE.Vector3(0, 1, 0);

    function fitToGrid(volume, gridOrigin) {
        const position = [...volume.position];
        const size = [...volume.size];
        const rotation = new THREE.Euler(...volume.rotation.map(value => value * Math.PI / 180), 'ZYX');
        for (let axis = 0; axis < 3; axis++) {
            const normal = new THREE.Vector3().setComponent(axis, 1).applyEuler(rotation);
            const gridAxis = normal.toArray().findIndex(value => Math.abs(value) > 1 - epsilon);
            if (gridAxis === -1) continue;
            const center = position[gridAxis];
            const origin = gridOrigin[gridAxis];
            const low = center - size[axis] / 2;
            const high = center + size[axis] / 2;
            const innerLow = origin + Math.ceil((low - origin) / cellSize) * cellSize;
            const innerHigh = origin + Math.floor((high - origin) / cellSize) * cellSize;
            const lowOverhang = innerLow - low;
            const highOverhang = high - innerHigh;
            let trimLow = lowOverhang > epsilon && lowOverhang < cellSize / 2 - epsilon
                && high - innerLow > epsilon;
            let trimHigh = highOverhang > epsilon && highOverhang < cellSize / 2 - epsilon
                && innerHigh - low > epsilon;
            if (trimLow && trimHigh && innerLow === innerHigh) {
                // Preserve the larger part. At a tie, the center belongs to
                // the positive cell under Minecraft's floor convention.
                trimLow = lowOverhang <= highOverhang + epsilon;
                trimHigh = !trimLow;
            }
            if (!trimLow && !trimHigh) continue;
            const nextLow = trimLow ? innerLow : low;
            const nextHigh = trimHigh ? innerHigh : high;
            if (nextHigh - nextLow <= epsilon) continue;
            position[gridAxis] = (nextLow + nextHigh) / 2;
            size[axis] = nextHigh - nextLow;
        }
        return {position, size};
    }

    function create(editor) {
        const {canvas, camera, orbit, scene} = editor;
        const rig = new THREE.Group();
        scene.add(rig);
        const colors = [0xf2827e, 0x9cde86, 0x83b9ff];
        const hitGeometry = new THREE.CylinderGeometry(1, 1, 1, 8);
        const hitMaterial = new THREE.MeshBasicMaterial({visible: false});
        const handles = [];
        for (let axis = 0; axis < 3; axis++) {
            for (const sign of [-1, 1]) {
                const normal = new THREE.Vector3().setComponent(axis, sign);
                const arrow = new THREE.ArrowHelper(normal, new THREE.Vector3(), 1, colors[axis]);
                for (const child of [arrow.line, arrow.cone]) {
                    child.material.depthTest = false;
                    child.material.depthWrite = false;
                    child.material.transparent = true;
                    child.renderOrder = 1000;
                }
                const picker = new THREE.Mesh(hitGeometry, hitMaterial);
                picker.quaternion.setFromUnitVectors(y, normal);
                rig.add(arrow, picker);
                handles.push({axis, sign, normal, arrow, picker});
            }
        }

        let target = null;
        let volume = null;
        let dragging = null;
        let hovered = null;
        const raycaster = new THREE.Raycaster();
        const hit = new THREE.Vector3();
        const cameraPosition = new THREE.Vector3();
        const center = new THREE.Vector3();
        const view = new THREE.Vector3();
        const inverse = new THREE.Quaternion();

        function highlight(handle) {
            if (hovered === handle) return;
            if (hovered) hovered.arrow.setColor(colors[hovered.axis]);
            hovered = handle;
            if (hovered) hovered.arrow.setColor(0xffe39b);
            canvas.classList.toggle('resize-face', !!hovered);
        }

        function update() {
            rig.visible = !!target;
            if (!target) return;
            target.getWorldPosition(center);
            target.getWorldQuaternion(rig.quaternion);
            rig.position.copy(center);
            camera.getWorldPosition(cameraPosition);
            view.copy(cameraPosition).sub(center);
            const unitsPerPixel = 2 * view.length() * Math.tan(camera.fov * Math.PI / 360)
                / Math.max(canvas.clientHeight, 1);
            const length = unitsPerPixel * 32;
            view.normalize().applyQuaternion(inverse.copy(rig.quaternion).invert());
            for (const handle of handles) {
                const {axis, normal, arrow, picker} = handle;
                arrow.position.copy(normal).multiplyScalar(volume.size[axis] / 2 + unitsPerPixel * 4);
                arrow.setLength(length, unitsPerPixel * 11, unitsPerPixel * 11);
                picker.position.copy(arrow.position).addScaledVector(normal, length * .55);
                picker.scale.set(unitsPerPixel * 8, length * 1.1, unitsPerPixel * 8);
                arrow.visible = picker.visible = Math.abs(view.getComponent(axis)) < .985 || handle === dragging?.handle;
            }
            rig.updateMatrixWorld(true);
        }

        function pointerRay(event) {
            const {left, top, width, height} = canvas.getBoundingClientRect();
            raycaster.setFromCamera(new THREE.Vector2(
                (event.clientX - left) * 2 / width - 1,
                1 - (event.clientY - top) * 2 / height,
            ), camera);
            return raycaster.ray;
        }

        function pick(event) {
            if (!target) return null;
            update();
            pointerRay(event);
            const pickers = handles.filter(handle => handle.picker.visible).map(handle => handle.picker);
            const picked = raycaster.intersectObjects(pickers, false)[0]?.object;
            return handles.find(handle => handle.picker === picked) || null;
        }

        function preview(position, size) {
            volume.position = position;
            volume.size = size;
            target.position.fromArray(position);
            target.scale.fromArray(size);
            editor.syncFields();
        }

        function consume(event) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }

        function start(event) {
            if (event.button !== 0 || dragging) return;
            const handle = pick(event);
            if (!handle) return;
            consume(event);
            const direction = handle.normal.clone().applyQuaternion(rig.quaternion);
            const face = center.clone().addScaledVector(direction, volume.size[handle.axis] / 2);
            const normal = cameraPosition.clone().sub(face);
            normal.addScaledVector(direction, -normal.dot(direction));
            if (normal.lengthSq() < epsilon) return;
            const plane = new THREE.Plane().setFromNormalAndCoplanarPoint(normal.normalize(), face);
            if (!raycaster.ray.intersectPlane(plane, hit)) return;
            dragging = {
                handle, plane, direction, pointerId: event.pointerId, start: hit.dot(direction),
                position: [...volume.position], size: [...volume.size], before: editor.captureLayout(),
                orbitEnabled: orbit.enabled,
            };
            highlight(handle);
            orbit.enabled = false;
            canvas.setPointerCapture(event.pointerId);
        }

        function move(event) {
            if (!dragging) {
                highlight(pick(event));
                return;
            }
            if (dragging.pointerId !== event.pointerId) return;
            consume(event);
            const {handle, plane, direction, start, position, size} = dragging;
            if (!pointerRay(event).intersectPlane(plane, hit)) return;
            const delta = hit.dot(direction) - start;
            const step = editor.resizeSnap();
            const moved = step > 0 ? Math.round(delta / step) * step : delta;
            const before = size[handle.axis];
            const next = Math.max(Math.min(before, .05), before + moved);
            const lengths = [...size];
            lengths[handle.axis] = next;
            const shift = (next - before) / 2;
            const translated = position.map((value, axis) => value + direction.getComponent(axis) * shift);
            preview(translated, lengths);
        }

        function finish(cancel = false) {
            if (!dragging) return;
            const {handle, size, position, before, pointerId, orbitEnabled} = dragging;
            dragging = null;
            orbit.enabled = orbitEnabled;
            if (canvas.hasPointerCapture(pointerId)) canvas.releasePointerCapture(pointerId);
            if (cancel) preview(position, size);
            else if (volume.size[handle.axis] !== size[handle.axis]) editor.commitResize(before);
            highlight(null);
        }

        function detach() {
            finish(true);
            target = volume = null;
            rig.visible = false;
            highlight(null);
        }

        function attach(mesh, selected) {
            if (mesh !== target) detach();
            target = mesh;
            volume = selected;
            update();
        }

        canvas.addEventListener('pointerdown', start, true);
        canvas.addEventListener('pointermove', move, true);
        canvas.addEventListener('pointerup', event => {
            if (event.pointerId !== dragging?.pointerId) return;
            consume(event);
            finish();
        }, true);
        for (const type of ['pointercancel', 'lostpointercapture']) {
            canvas.addEventListener(type, event => {
                if (event.pointerId === dragging?.pointerId) finish(true);
            });
        }
        canvas.addEventListener('pointerleave', () => { if (!dragging) highlight(null); });
        window.addEventListener('keydown', event => { if (event.key === 'Escape') finish(true); });

        return {attach, detach, update, handles, get object() { return target; }, get dragging() { return !!dragging; }};
    }

    return {create, fitToGrid};
})();
