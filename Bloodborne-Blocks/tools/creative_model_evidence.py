"""Content evidence for the installed visual-model resolver, with per-build caches.

Mirrors LogicalVisualModels' nearest geometry and inherited texture-slot rules.
No rotation, translation, UV normalization, or visual-similarity matching occurs.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode('utf8')


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def identifier(value):
    return value if ':' in value else 'minecraft:' + value


class ModelEvidence:
    def __init__(self, resources: Path, meshes: dict, aliases: dict, emissive_textures=None):
        self.resources = resources
        self.meshes = meshes
        self.aliases = aliases
        self.emissive_textures = emissive_textures or {}
        self.source_hashes = {}
        self._models = {}
        self._textures = {}
        self._meshes = {}
        self._visuals = {}

    def _file(self, path):
        if not path.is_file():
            return None
        data = path.read_bytes()
        hashed = data.replace(b'\r\n', b'\n') if path.suffix in {'.json', '.mcmeta'} else data
        self.source_hashes[path.relative_to(self.resources).as_posix()] = hashlib.sha256(hashed).hexdigest()
        return data

    def _model(self, name):
        name = identifier(name)
        if name not in self._models:
            namespace, path = name.split(':', 1)
            data = self._file(self.resources / 'assets' / namespace / 'models' / (path + '.json'))
            self._models[name] = json.loads(data) if data is not None else None
        return self._models[name]

    def texture(self, name, emissive=False):
        name = identifier(name)
        raw_name = name
        seen = set()
        while name in self.aliases:
            if name in seen:
                raise ValueError('Cyclic texture alias: ' + name)
            seen.add(name)
            name = self.aliases[name]
        cache_key = (raw_name, emissive)
        if cache_key not in self._textures:
            namespace, path = name.split(':', 1)
            file = self.resources / 'assets' / namespace / 'textures' / (path + '.png')
            png = self._file(file)
            metadata = self._file(file.with_suffix('.png.mcmeta'))
            # Unbundled vanilla textures remain distinct opaque identifiers.
            self._textures[cache_key] = {
                'png': hashlib.sha256(png).hexdigest() if png is not None else None,
                'mcmeta': hashlib.sha256(metadata.replace(b'\r\n', b'\n')).hexdigest() if metadata is not None else None,
                'external': name if png is None else None,
            }
            if emissive:
                glow = self.emissive_textures.get(raw_name)
                self._textures[cache_key]['glow'] = self.texture(glow) if glow is not None else None
        return self._textures[cache_key]

    @staticmethod
    def _slot(value, slots):
        seen = set()
        while value.startswith('#'):
            if value in seen:
                raise ValueError('Cyclic texture slot: ' + value)
            seen.add(value)
            value = slots[value[1:]]
        return identifier(value)

    @staticmethod
    def _independent_faces(faces):
        """Allow reordering only for disjoint axis-aligned, opaque/cutout faces.

        Different overlapping coplanar faces retain their original draw order.
        Vertex ordering is never changed (it controls runtime triangulation).
        """
        planes = {}
        for face in faces:
            vertices = face['vertices']
            axes = [axis for axis in range(3) if len({v[axis] for v in vertices}) == 1]
            if len(axes) != 1:
                return False
            axis = axes[0]
            others = [i for i in range(3) if i != axis]
            rectangle = tuple((min(v[i] for v in vertices), max(v[i] for v in vertices)) for i in others)
            for old, bounds in planes.setdefault((axis, vertices[0][axis]), []):
                if all(min(a[1], b[1]) > max(a[0], b[0]) for a, b in zip(rectangle, bounds)) and old != face:
                    return False
            planes[(axis, vertices[0][axis])].append((face, rectangle))
        return True

    def _polygons(self, polygons, slots=None, mapping=None, emissive=False, allow_face_reorder=False):
        result = []
        for polygon in polygons:
            texture = polygon['texture']
            texture = (mapping or {}).get(texture, texture)
            if slots is not None:
                texture = self._slot(texture, slots)
            vertices = []
            for vertex in polygon['vertices']:
                if len(vertex) != 5 or not all(math.isfinite(value) for value in vertex):
                    raise ValueError('Invalid model vertex')
                vertices.append(tuple(0.0 if value == 0 else float(value) for value in vertex))
            # Preserve start/winding and draw order: the runtime triangulates
            # from vertex zero, and translucent faces can be order-sensitive.
            flags = {key: value for key, value in polygon.items() if key not in {'texture', 'vertices'}}
            result.append({'vertices': vertices, 'texture': self.texture(texture, emissive), 'flags': flags})
        return sorted(result, key=canonical) if allow_face_reorder and self._independent_faces(result) else result

    def mesh(self, name, emissive=False, allow_face_reorder=False):
        key = (name, emissive, allow_face_reorder)
        if key not in self._meshes:
            self._meshes[key] = digest(self._polygons(self.meshes[name]['polygons'], emissive=emissive, allow_face_reorder=allow_face_reorder))
        return self._meshes[key]

    def visual(self, name, fallback_mesh, emissive=False, allow_face_reorder=False):
        cache_key = (name, fallback_mesh, emissive, allow_face_reorder)
        if cache_key in self._visuals:
            return self._visuals[cache_key]
        if name is None:
            result = {'kind': 'mesh', 'geometry': self.mesh(fallback_mesh, emissive, allow_face_reorder)}
            self._visuals[cache_key] = result
            return result
        chain, seen, geometry, external = [], set(), None, None
        current = identifier(name)
        while current is not None:
            if len(chain) >= 32 or current in seen:
                raise ValueError('Cyclic/deep visual model: ' + name)
            seen.add(current)
            data = self._model(current)
            if data is None:
                # The bundled vanilla parent is external to this repository.
                # Its identity is kept, never equated to another missing asset.
                external = current
                break
            chain.append(data)
            if geometry is None and any(key in data for key in ('elements', 'bloodborne_mesh', 'bloodborne_polygons')):
                geometry = data
            current = identifier(data['parent']) if 'parent' in data else None
        if geometry is None:
            result = {'kind': 'opaque', 'model': name}
        else:
            slots = {}
            attributes = {}
            for data in reversed(chain):
                slots.update(data.get('textures', {}))
                for key in ('ambientocclusion', 'gui_light'):
                    if key in data:
                        attributes[key] = data[key]
            attributes['external_parent'] = external
            if 'particle' in slots:
                attributes['particle'] = self.texture(self._slot('#particle', slots))
            if 'elements' in geometry:
                elements = json.loads(json.dumps(geometry['elements']))
                for element in elements:
                    for face in element.get('faces', {}).values():
                        face['texture'] = self.texture(self._slot(face['texture'], slots), emissive)
                result = {'kind': 'elements', 'geometry': elements, 'attributes': attributes}
            elif 'bloodborne_polygons' in geometry:
                polygons = self._polygons(geometry['bloodborne_polygons'], slots, emissive=emissive, allow_face_reorder=allow_face_reorder)
                result = {'kind': 'mesh', 'geometry': digest(polygons), 'attributes': attributes}
            else:
                mesh_id = geometry['bloodborne_mesh']
                mapping = geometry.get('bloodborne_texture_slots', {})
                if mapping:
                    geometry_hash = digest(self._polygons(self.meshes[mesh_id]['polygons'], slots, mapping, emissive, allow_face_reorder))
                else:
                    geometry_hash = self.mesh(mesh_id, emissive, allow_face_reorder)
                result = {'kind': 'mesh', 'geometry': geometry_hash, 'attributes': attributes}
        self._visuals[cache_key] = result
        return result
