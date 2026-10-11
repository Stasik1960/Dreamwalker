"""Append missing v15 artworks using the existing V12 composite runtime.

Input is a reviewed logical-object manifest, not a raw directory of JSON files.
No map/gallery files are read or written. Baseline production files are never
overwritten. New generated resources can be refined before this stage is released.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import itertools
import json
import math
import io
import gzip
import zipfile
from pathlib import Path
from PIL import Image
from simplify_imported_collisions import apply_policy
from gallery_platform_plan import ModelGeometry

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
VANILLA = Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/remaining-transfer'
PREFIX = 'remaining/source/'
SIDES = {'west': (0, 0), 'east': (0, 1), 'down': (1, 0), 'up': (1, 1), 'north': (2, 0), 'south': (2, 1)}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def key(value: str) -> str:
    if ':' not in value:
        value = 'minecraft:' + value
    namespace, path = value.split(':', 1)
    if not namespace or not path or '..' in path.split('/') or '\\' in path:
        raise ValueError('Unsafe resource identifier: ' + value)
    return namespace + ':' + path


def model_path(value: str) -> str:
    namespace, path = key(value).split(':', 1)
    return f'assets/{namespace}/models/{path}.json'


def remap(value: str) -> str:
    namespace, path = key(value).split(':', 1)
    return 'bloodborne_dw:' + PREFIX + namespace + '/' + path


def rotation(axis: int, angle: float) -> list[list[float]]:
    radians = math.radians(angle)
    c, s = math.cos(radians), math.sin(radians)
    result = [[float(row == column) for column in range(3)] for row in range(3)]
    first, second = (axis + 1) % 3, (axis + 2) % 3
    result[first][first] = result[second][second] = c
    result[first][second] = -s
    result[second][first] = s
    return result


def mv(matrix, vector):
    return [sum(matrix[row][column] * vector[column] for column in range(3)) for row in range(3)]


def rotate_point(point, axis, angle, origin):
    transformed = mv(rotation(axis, angle), [point[index] - origin[index] for index in range(3)])
    return [transformed[index] + origin[index] for index in range(3)]


def implicit_uv(element, side):
    x, y, z = element['from']
    X, Y, Z = element['to']
    return {'down': [x, 16 - Z, X, 16 - z], 'up': [x, z, X, Z],
            'north': [16 - X, 16 - Y, 16 - x, 16 - y], 'south': [x, 16 - Y, X, 16 - y],
            'west': [z, 16 - Y, Z, 16 - y], 'east': [16 - Z, 16 - Y, 16 - z, 16 - y]}[side]


def uvlocked_face(face, side, element, application):
    """Bake vanilla UV locking into a private source-model wrapper.

    Geometry still rotates in CompositeClient. This is the existing repository
    modular_mesh.face_uv transform, reduced to vanilla face UV/rotation fields;
    it neither extrudes planes nor adds/removes faces.
    """
    u, v, U, V = face.get('uv', implicit_uv(element, side))
    angle = face.get('rotation', 0)
    normal = [0, 0, 0]
    axis, high = SIDES[side]
    normal[axis] = 1 if high else -1
    normal = rotate_point(normal, 0, -application.get('x', 0), [0, 0, 0])
    normal = rotate_point(normal, 1, -application.get('y', 0), [0, 0, 0])
    target = next(name for name, (axis, high) in SIDES.items() if abs(normal[axis] - (1 if high else -1)) < 1e-6)

    def transformed_uv(uv):
        a, b = uv
        point = {'north': [16 - a, 16 - b, 0], 'south': [a, 16 - b, 16], 'west': [0, 16 - b, a],
                 'east': [16, 16 - b, 16 - a], 'up': [a, 16, b], 'down': [a, 0, 16 - b]}[side]
        point = rotate_point(point, 0, -application.get('x', 0), [8, 8, 8])
        x, y, z = rotate_point(point, 1, -application.get('y', 0), [8, 8, 8])
        return {'north': [16 - x, 16 - y], 'south': [x, 16 - y], 'west': [z, 16 - y],
                'east': [16 - z, 16 - y], 'up': [x, z], 'down': [x, 16 - z]}[target]

    a, b = transformed_uv([u, v]), transformed_uv([U, V])
    origin = transformed_uv([0, 0])
    basis_u, basis_v = transformed_uv([1, 0]), transformed_uv([0, 1])
    def sign(value):
        return 1 if value > 1e-8 else -1 if value < -1e-8 else 0
    if sign(U - u) != sign(b[0] - a[0]):
        a[0], b[0] = b[0], a[0]
    if sign(V - v) != sign(b[1] - a[1]):
        a[1], b[1] = b[1], a[1]
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    direction = [(basis_u[index] - origin[index]) * c + (basis_v[index] - origin[index]) * s for index in range(2)]
    adjusted = copy.deepcopy(face)
    adjusted['uv'] = [round(a[0], 8), round(a[1], 8), round(b[0], 8), round(b[1], 8)]
    adjusted['rotation'] = (-round(math.degrees(math.atan2(direction[1], direction[0])) / 90) * 90) % 360
    return adjusted


def transformed_corners(element, application):
    points = list(itertools.product(*zip(element['from'], element['to'])))
    local_rotation = element.get('rotation')
    if local_rotation:
        axis = 'xyz'.index(local_rotation['axis'])
        origin, angle = local_rotation['origin'], float(local_rotation['angle'])
        if local_rotation.get('rescale'):
            scale = 1 / math.cos(math.radians(angle))
            points = [tuple(origin[index] + (point[index] - origin[index]) * (1 if index == axis else scale)
                            for index in range(3)) for point in points]
        points = [rotate_point(point, axis, angle, origin) for point in points]
    # Minecraft blockstate rotations are clockwise; CompositeClient uses the
    # same negative X/Y convention. Offsets are in model units, applied last.
    points = [rotate_point(point, 0, -float(application.get('x', 0)), [8, 8, 8]) for point in points]
    points = [rotate_point(point, 1, -float(application.get('y', 0)), [8, 8, 8]) for point in points]
    offset = application.get('offset', [0, 0, 0])
    return [[point[index] + offset[index] for index in range(3)] for point in points]


def bounds(points):
    return {'from': [round(min(point[index] for point in points), 8) for index in range(3)],
            'to': [round(max(point[index] for point in points), 8) for index in range(3)]}


def distinct_boxes(boxes):
    result = {}
    for box in boxes:
        result[tuple(box['from'] + box['to'])] = box
    return list(result.values())


def coalesce_boxes(boxes):
    """Merge only rectangular unions whose four other coordinates are equal.

    Every merge joins overlapping or touching intervals along one axis.  It
    cannot fill holes: the other two dimensions must match exactly.  Repeat
    Y/Z/X until stable, preserving finite slabs and existing occupied volume.
    This changes only newly imported defaults, never V12 runtime geometry.
    """
    values = {tuple(box['from'] + box['to']) for box in boxes}
    if any(not all(math.isfinite(v) for v in row) or
           any(row[axis] >= row[axis + 3] for axis in range(3)) for row in values):
        raise ValueError('Invalid default box in exact union coalescer')
    while True:
        before = len(values)
        for axis in (1, 2, 0):
            fixed = tuple(index for index in range(6) if index not in (axis, axis + 3))
            groups = {}
            for row in values:
                groups.setdefault(tuple(row[index] for index in fixed), []).append((row[axis], row[axis + 3]))
            merged = set()
            for coordinates, intervals in groups.items():
                intervals.sort()
                low, high = intervals[0]
                for next_low, next_high in intervals[1:] + [(math.inf, math.inf)]:
                    if next_low <= high:
                        high = max(high, next_high)
                    else:
                        row = [0] * 6
                        for index, coordinate in zip(fixed, coordinates):
                            row[index] = coordinate
                        row[axis], row[axis + 3] = low, high
                        merged.add(tuple(row))
                        low, high = next_low, next_high
            values = merged
        if len(values) == before:
            return [{'from': list(row[:3]), 'to': list(row[3:])} for row in sorted(values)]


def dot(first, second):
    return sum(x * y for x, y in zip(first, second))


def cross(first, second):
    return [first[1] * second[2] - first[2] * second[1],
            first[2] * second[0] - first[0] * second[2],
            first[0] * second[1] - first[1] * second[0]]


def rotated_boxes(points, *, cell_size=1):
    """Raster an oriented authored element into small default collision boxes.

    The grid is one model pixel. SAT rejects voxels outside the actual OBB;
    rows are merged along X. This is a new-object voxel default, not an
    imported author collision. Large source boxes stay exact when aligned.
    """
    outer = bounds(points)
    axes = [[points[index][axis] - points[0][axis] for axis in range(3)] for index in (4, 2, 1)]
    if all(sum(abs(value) > 1e-6 for value in axis) <= 1 for axis in axes):
        return [outer]
    center = [sum(point[index] for point in points) / 8 for index in range(3)]
    half_axes = [[value / 2 for value in axis] for axis in axes]
    normals = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    normals += [cross(axes[0], axes[1]), cross(axes[1], axes[2]), cross(axes[2], axes[0])]
    normals += [cross(axis, native) for axis in axes for native in ([1, 0, 0], [0, 1, 0], [0, 0, 1])]
    normals = [normal for normal in normals if dot(normal, normal) > 1e-12]
    limits = [sum(abs(dot(half, normal)) for half in half_axes) + cell_size / 2 * sum(abs(value) for value in normal)
              for normal in normals]
    low = [math.floor(value / cell_size) for value in outer['from']]
    high = [math.ceil(value / cell_size) for value in outer['to']]
    flat = [index for index in range(3) if outer['to'][index] - outer['from'][index] < 1e-6]
    for index in flat:
        low[index] = math.floor(outer['from'][index] / cell_size - 1e-6)
        high[index] = math.ceil(outer['to'][index] / cell_size + 1e-6)
    count = math.prod(high[index] - low[index] for index in range(3))
    # Bounded default approximation for exceptional huge curved scenery; this
    # remains an OBB voxel raster and never fills the enclosing AABB wholesale.
    if count > 300_000 and cell_size < 4:
        return rotated_boxes(points, cell_size=cell_size * 2)
    result = []
    for y in range(low[1], high[1]):
        for z in range(low[2], high[2]):
            start = None
            for x in range(low[0], high[0] + 1):
                delta = [(x + .5) * cell_size - center[0], (y + .5) * cell_size - center[1], (z + .5) * cell_size - center[2]]
                occupied = x < high[0] and all(abs(dot(delta, normal)) <= limit + 1e-9 for normal, limit in zip(normals, limits))
                if occupied and start is None:
                    start = x
                elif not occupied and start is not None:
                    box = {'from': [start * cell_size, y * cell_size, z * cell_size],
                           'to': [x * cell_size, (y + 1) * cell_size, (z + 1) * cell_size]}
                    for index in flat:
                        box['from'][index] = outer['from'][index] - .0625
                        box['to'][index] = outer['to'][index] + .0625
                    result.append(box)
                    start = None
    return result


class Source:
    def __init__(self, pack: Path, vanilla: Path):
        self.pack = zipfile.ZipFile(pack)
        self.vanilla = zipfile.ZipFile(vanilla)
        names = self.pack.namelist()
        self.root = next(name[:-len('pack.mcmeta')] for name in names if name.endswith('pack.mcmeta'))
        self.pack_names = set(names)
        self.vanilla_names = set(self.vanilla.namelist())
        self.raw_cache = {}
        self.resolved_cache = {}
        self.exported = {}
        self.exporting = set()
        self.alpha_cache = {}
        self.source_warnings = []

    def read(self, path):
        if self.root + path in self.pack_names:
            return self.pack.read(self.root + path), 'v15'
        if path in self.vanilla_names:
            return self.vanilla.read(path), 'vanilla_1.20.1'
        raise FileNotFoundError('Missing source dependency: ' + path)

    def raw(self, model):
        model = key(model)
        if model not in self.raw_cache:
            data, origin = self.read(model_path(model))
            self.raw_cache[model] = (json.loads(data), origin, sha(data))
        return self.raw_cache[model][0]

    def resolved(self, model, chain=()):
        model = key(model)
        if model in chain:
            raise ValueError('Cyclic source model parent: ' + ' -> '.join(chain + (model,)))
        if model in self.resolved_cache:
            return self.resolved_cache[model]
        raw = self.raw(model)
        parent = raw.get('parent', '')
        result = copy.deepcopy(self.resolved(parent, chain + (model,))) if parent and not parent.startswith('builtin/') else {}
        textures = {**result.get('textures', {}), **raw.get('textures', {})}
        result.update(copy.deepcopy(raw))
        result['textures'] = textures
        self.resolved_cache[model] = result
        return result

    def texture(self, value):
        if value.startswith('#'):
            return value
        namespace, path = key(value).split(':', 1)
        original = f'assets/{namespace}/textures/{path}.png'
        if self.root + original not in self.pack_names:
            if original not in self.vanilla_names:
                raise FileNotFoundError('Missing source texture: ' + value)
            return key(value)
        target = RES / 'assets/bloodborne_dw/textures/block' / PREFIX / namespace / (path + '.png')
        self.write(target, self.pack.read(self.root + original), source=original, origin='v15', kind='texture')
        metadata = original + '.mcmeta'
        if self.root + metadata in self.pack_names:
            self.write(Path(str(target) + '.mcmeta'), self.pack.read(self.root + metadata), source=metadata, origin='v15', kind='texture_metadata')
        return 'bloodborne_dw:block/' + PREFIX + namespace + '/' + path

    def grass_palette(self):
        original = 'assets/minecraft/textures/colormap/grass.png'
        data = self.pack.read(self.root + original)
        image = Image.open(io.BytesIO(data))
        if image.size != (256, 256):
            raise ValueError('Authored grass colormap must be 256 by 256')
        self.write(RES / 'assets/bloodborne_dw/textures/remaining/colormap/grass.png', data,
                   source=original, source_sha256=sha(data), origin='v15', kind='texture',
                   role='source_colormap', consumers=['source_grass_block', 'source_bricks_grass_block_0', 'source_potted_fern'])

    def semi_alpha(self, value):
        value = key(value)
        if value not in self.alpha_cache:
            namespace, path = value.split(':', 1)
            data, _ = self.read(f'assets/{namespace}/textures/{path}.png')
            alpha = Image.open(io.BytesIO(data)).convert('RGBA').getchannel('A').getcolors(256)
            self.alpha_cache[value] = alpha is None or any(0 < entry[1] < 255 for entry in alpha)
        return self.alpha_cache[value]

    def resolve_texture(self, textures, name):
        seen = set()
        while name.startswith('#'):
            alias = name[1:]
            if alias in seen or alias not in textures:
                raise ValueError('Unknown or cyclic texture alias: ' + name)
            seen.add(alias)
            name = textures[alias]
        return key(name)

    def translucent(self, applications):
        for application in applications:
            model = self.resolved(application['model'])
            textures = model.get('textures', {})
            for element in model.get('elements', []):
                for face in element.get('faces', {}).values():
                    texture = self.resolve_texture(textures, face['texture'])
                    if self.semi_alpha(texture):
                        return True
        return False

    def export_model(self, model):
        model = key(model)
        if model in self.exported:
            return remap(model)
        if model in self.exporting:
            raise ValueError('Cyclic source model parent: ' + model)
        self.exporting.add(model)
        try:
            raw = copy.deepcopy(self.raw(model))
            for element in raw.get('elements', []):
                if element.get('rotation', {}).get('angle', 0) not in (-45, -22.5, 0, 22.5, 45):
                    raise ValueError('Unsupported vanilla element rotation: ' + model)
                for face in element.get('faces', {}).values():
                    if not face.get('texture', '').startswith('#'):
                        face['texture'] = self.texture(face['texture'])
            if raw.get('parent') and not raw['parent'].startswith('builtin/'):
                raw['parent'] = self.export_model(raw['parent'])
            if 'textures' in raw:
                remapped_textures = {}
                for name, value in raw['textures'].items():
                    try:
                        remapped_textures[name] = self.texture(value)
                    except FileNotFoundError as exc:
                        resolved = self.resolved(model)
                        used = {self.resolve_texture(resolved.get('textures', {}), face['texture'])
                                for element in resolved.get('elements', []) for face in element.get('faces', {}).values()}
                        if name != 'particle' or value.startswith('#') or key(value) in used:
                            raise
                        remapped_textures[name] = key(value)
                        self.source_warnings.append({'model': model, 'status': 'SOURCE_PARTICLE_DEFECT',
                                                     'unusedDeclaredTexture': key(value), 'detail': str(exc),
                                                     'policy': 'Visible faces preserved; missing unused particle reference retained without guessed texture.'})
                raw['textures'] = remapped_textures
            for override in raw.get('overrides', []):
                if 'model' in override:
                    override['model'] = self.export_model(override['model'])
            namespace, path = model.split(':', 1)
            target = RES / 'assets/bloodborne_dw/models' / PREFIX / namespace / (path + '.json')
            _, origin, digest = self.raw_cache[model]
            self.write(target, encode(raw), source=model_path(model), origin=origin, kind='model', source_sha256=digest)
            self.exported[model] = remap(model)
        finally:
            self.exporting.remove(model)
        return remap(model)

    def export_application_model(self, application):
        model = application['model']
        if not application.get('uvlock') or not (application.get('x', 0) or application.get('y', 0)):
            return self.export_model(model)
        if any(float(application.get(axis, 0)) % 90 != 0 for axis in ('x', 'y')):
            raise ValueError('Importer limitation: UV lock requires cardinal source applications: ' + model)
        elements = copy.deepcopy(self.resolved(model).get('elements', []))
        for element in elements:
            element['faces'] = {side: uvlocked_face(face, side, element, application)
                                for side, face in element.get('faces', {}).items()}
            for face in element['faces'].values():
                if not face.get('texture', '').startswith('#'):
                    face['texture'] = self.texture(face['texture'])
        namespace, local = key(model).split(':', 1)
        path = f'remaining/source_uvlock/{namespace}/{local}_x{application.get("x", 0)}_y{application.get("y", 0)}'
        target = RES / 'assets/bloodborne_dw/models' / (path + '.json')
        wrapper = {'parent': self.export_model(model), 'elements': elements,
                   'sourceUvlockApplication': {'x': application.get('x', 0), 'y': application.get('y', 0), 'uvlock': True},
                   'sourceUvlockPolicy': 'Private face UV/rotation wrapper; original source elements and PNG bytes remain preserved in parent.'}
        self.write(target, encode(wrapper), kind='uvlock_model', source=model_path(model))
        return 'bloodborne_dw:' + path

    def export_emissive_application_model(self, application):
        """Keep only source-declared emissive faces in an exact-UV overlay."""
        model = application['model']
        resolved = self.resolved(model)
        textures = resolved.get('textures', {})
        elements, masks, mask_aliases, overlay_textures = [], set(), {}, {}
        for original in resolved.get('elements', []):
            element = copy.deepcopy(original)
            faces = {}
            for side, original_face in original.get('faces', {}).items():
                texture = self.resolve_texture(textures, original_face['texture'])
                namespace, local = texture.split(':', 1)
                mask = namespace + ':' + local + '_e'
                mask_file = f'assets/{namespace}/textures/{local}_e.png'
                if self.root + mask_file not in self.pack_names:
                    continue
                face = uvlocked_face(original_face, side, original, application) if application.get('uvlock') else copy.deepcopy(original_face)
                if mask not in mask_aliases:
                    mask_aliases[mask] = 'remaining_emissive_' + str(len(mask_aliases))
                    overlay_textures[mask_aliases[mask]] = self.texture(mask)
                # Minecraft's baked face loader resolves a texture-variable
                # alias here; direct atlas references yield missing sprites.
                face['texture'] = '#' + mask_aliases[mask]
                faces[side] = face
                masks.add(mask_file)
            if faces:
                element['faces'] = faces
                element['shade'] = False
                elements.append(element)
        if not elements:
            return None
        namespace, local = key(model).split(':', 1)
        path = f'remaining/source_emissive/{namespace}/{local}_x{application.get("x", 0)}_y{application.get("y", 0)}'
        overlay = {'parent': self.export_model(model), 'textures': overlay_textures, 'elements': elements,
                   'sourceEmissiveMasks': sorted(masks), 'sourceEmissivePolicy': 'Only declared _e faces, same geometry/UV; renderer must use source-only fullbright overlay. No emitted world light.'}
        self.write(RES / 'assets/bloodborne_dw/models' / (path + '.json'), encode(overlay),
                   kind='emissive_model', source=model_path(model), masks=sorted(masks))
        return 'bloodborne_dw:' + path

    def write(self, target, data, **evidence):
        relative = target.relative_to(RES).as_posix()
        if target.exists() and target.read_bytes() != data:
            generated_resource = relative.startswith('bloodborne_dw/composite/source_') or relative.startswith('assets/bloodborne_dw/models/remaining/')
            if not generated_resource:
                raise FileExistsError('Refusing to overwrite existing source dependency: ' + relative)
            baseline = json.loads((OUT / 'baseline.json').read_text(encoding='utf-8'))
            if 'src/architecture/resources/' + relative in baseline['implementation']:
                raise FileExistsError('Refusing to overwrite baseline resource: ' + relative)
            target.write_bytes(data)
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            target.write_bytes(data)
        self.exported['file:' + relative] = {'file': relative, 'sha256': sha(data), **evidence}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def pose(source, applications, *, collision=True):
    parts, boxes, visible = [], [], []
    for application in applications:
        model = application['model']
        resolved = source.resolved(model)
        elements = resolved.get('elements', [])
        part = {'model': source.export_application_model(application)}
        for old, new in (('offset', 'offset'), ('y', 'yaw'), ('x', 'pitch')):
            if application.get(old):
                part[new] = application[old]
        parts.append(part)
        emissive = source.export_emissive_application_model(application)
        if emissive is not None:
            parts.append({**part, 'model': emissive, 'emissive': True})
        for element in elements:
            if not element.get('faces'):
                continue
            points = transformed_corners(element, application)
            box = bounds(points)
            physical = all(element['to'][index] - element['from'][index] > 1e-6 for index in range(3))
            raster = rotated_boxes(points)
            if physical and collision:
                boxes.extend(raster)
            for selection in copy.deepcopy(raster):
                # Exact visible planes need a finite picking slab, not a
                # filled bounding volume. Their collision remains empty.
                for index in range(3):
                    if selection['to'][index] - selection['from'][index] <= 1e-6:
                        selection['from'][index] -= 0.0625
                        selection['to'][index] += 0.0625
                visible.append(selection)
    if not visible:
        raise ValueError('No selectable source geometry')
    return {'parts': parts, 'collision': coalesce_boxes(boxes), 'selection': coalesce_boxes(visible)}


def placement_applications(source, applications, anchor):
    """Return a uniform source-to-building translation, preserving raw models."""
    if anchor != 'floor':
        return copy.deepcopy(applications), [0, 0, 0]
    points = [point for application in applications for element in source.resolved(application['model']).get('elements', [])
              if element.get('faces') for point in transformed_corners(element, application)]
    if not points:
        raise ValueError('No authored placement geometry')
    shift = [0, round(-min(point[1] for point in points), 8), 0]
    return translate_applications(applications, shift), shift


def translate_applications(applications, shift):
    translated = copy.deepcopy(applications)
    for application in translated:
        offset = application.get('offset', [0, 0, 0])
        application['offset'] = [round(offset[index] + shift[index], 8) for index in range(3)]
    return translated


def import_objects(manifest, source):
    imported, blocked = [], []
    retired_file = RES / 'bloodborne_dw/retired-source-objects.json'
    retired = {row['path'] for row in json.loads(retired_file.read_text(encoding='utf-8'))['objects']} if retired_file.exists() else set()
    override_file = OUT / 'source-name-overrides.json'
    overrides = {row['path']: row['displayName'] for row in json.loads(override_file.read_text(encoding='utf-8'))['objects']} if override_file.exists() else {}
    for row in manifest['objects']:
        path = row['path']
        if path in retired:
            # A previous authoring manifest is historical evidence, not permission
            # to restore explicitly removed objects or recycle their numbers.
            continue
        if not path.startswith('source_') or any(character not in 'abcdefghijklmnopqrstuvwxyz0123456789_' for character in path):
            raise ValueError('Expected append-only source_ object path: ' + path)
        source_name = row.get('displayName', row.get('name', path))
        name = overrides.get(path, source_name)
        variants = copy.deepcopy(row['variants']) if 'variants' in row else [{'closed': row['parts']}]
        if name != source_name:
            for variant in variants:
                if isinstance(variant.get('name'), str) and variant['name'].startswith(source_name):
                    variant['name'] = name + variant['name'][len(source_name):]
        if not 1 <= len(variants) <= 16:
            raise ValueError('Composite variant limit: ' + path)
        parsed = []
        try:
            translucent = False
            for variant in variants:
                applications = variant.get('closed', variant.get('parts'))
                if applications is None:
                    applications = [{'model': model} if isinstance(model, str) else model for model in variant['sourceModels']]
                source_applications = copy.deepcopy(applications)
                applications, placement_offset = placement_applications(source, applications, row.get('anchor', 'floor'))
                closed = pose(source, applications, collision=row.get('collision', True))
                translucent = translucent or source.translucent(applications)
                poses = {'closed': closed}
                if 'open' in variant:
                    poses['open'] = pose(source, translate_applications(variant['open'], placement_offset), collision=row.get('collision', True))
                points = [box[side] for box in closed['selection'] for side in ('from', 'to')]
                parsed.append({'poses': poses, 'visibleBounds': bounds(points),
                               'sourceVariant': variant.get('name', str(len(parsed))),
                               'sourcePlacementOffset': placement_offset, 'localOrigin': placement_offset,
                               'sourceClosedApplications': source_applications,
                               'sourceOpenApplications': variant.get('open', []),
                               'sourceStateRefs': variant.get('sourceRefs', variant.get('sourceStates', []))})
            descriptor = {'schemaVersion': 1, 'units': 16, 'id': 'bloodborne_dw:' + path,
                          'displayName': name, 'openable': any('open' in variant for variant in variants),
                          'layer': row.get('layer', 'translucent' if translucent else 'cutout'), 'support': row.get('support', {'required': False}),
                          'essentialMask': row.get('essentialMask', [[0, 0, 0]]), 'variants': parsed,
                          'source': row.get('source', row.get('evidence', {})),
                          'anchorPolicy': row.get('anchor', 'floor'),
                          'collisionPolicy': 'New-object default per-element boxes; rotated source elements use SAT-filtered one-model-pixel voxels (bounded coarse raster only for exceptional huge elements), then exact rectangular-union coalescing. Visible zero-thickness planes are selection-only. Not authored Collision Studio data.',
                          'geometryPolicy': 'Source element coordinates, face UV, rotations, display, textures and parent inheritance unchanged; only dependency namespace remapped.'}
            descriptor, _ = apply_policy(descriptor, ModelGeometry())
            source.write(RES / 'bloodborne_dw/composite' / (path + '.json'), encode(descriptor), kind='descriptor')
            source.write(RES / 'assets/bloodborne_dw/blockstates' / (path + '.json'),
                         encode({'variants': {'': {'model': 'bloodborne_dw:block/' + path + '_empty'}}}), kind='blockstate')
            source.write(RES / 'assets/bloodborne_dw/models/block' / (path + '_empty.json'),
                         encode({'textures': {'particle': 'minecraft:block/stone'}, 'elements': []}), kind='empty_carrier_model')
            source.write(RES / 'assets/bloodborne_dw/models/item' / (path + '.json'),
                         encode({'parent': 'builtin/entity'}), kind='item_model')
            imported.append({'path': path, 'registryId': 'bloodborne_dw:' + path, 'name': name,
                             'variantCount': len(variants), 'openable': descriptor['openable'], 'source': row.get('source', row.get('evidence', {})),
                             'variants': [{'variant': index, 'name': variant.get('name', str(index)),
                                           'sourceModels': variant.get('sourceModels', [part['model'] for part in variant.get('closed', variant.get('parts', []))])}
                                          for index, variant in enumerate(variants)],
                             'offeredVariants': list(range(len(variants))), 'resourceStatus': 'IMPORTED',
                             'gameStatus': 'NOT_RUN'})
        except Exception as exc:
            blocked.append({'path': path, 'name': name, 'source': row.get('source', {}),
                            'status': 'IMPORT_LIMITATION' if not isinstance(exc, (FileNotFoundError, json.JSONDecodeError)) else 'SOURCE_DEFECT',
                            'detail': type(exc).__name__ + ': ' + str(exc)})
        if (len(imported) + len(blocked)) % 25 == 0:
            OUT.mkdir(parents=True, exist_ok=True)
            (OUT / 'import-progress.json').write_bytes(encode({'status': 'RUNNING', 'done': len(imported) + len(blocked),
                                                             'total': len(manifest['objects']), 'imported': len(imported),
                                                             'blocked': len(blocked), 'latest': path}))
    return imported, blocked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--vanilla', type=Path, default=VANILLA)
    parser.add_argument('--verify-geometry', action='store_true')
    parser.add_argument('--refresh-emissive-only', action='store_true')
    parser.add_argument('--refresh-tint-only', action='store_true')
    parser.add_argument('--coalesce-boxes-only', action='store_true')
    args = parser.parse_args()
    if args.refresh_tint_only:
        source = Source(PACK, args.vanilla)
        source.grass_palette()
        result_path = OUT / 'imported-resources.json'
        result = json.loads(result_path.read_text(encoding='utf-8'))
        resources = {row['file']: row for row in result['resources']}
        resources.update({value['file']: value for name, value in source.exported.items() if name.startswith('file:')})
        result['resources'] = list(resources.values())
        result['sourceTintFeature'] = {'status': 'RESOURCES_PREPARED', 'objects': ['source_grass_block', 'source_bricks_grass_block_0', 'source_potted_fern'],
            'tintIndices': [0], 'paletteSha256': sha((RES / 'assets/bloodborne_dw/textures/remaining/colormap/grass.png').read_bytes()),
            'worldPolicy': 'Authored vanilla-path v15 grass palette, exact native temperature/downfall lookup, native biome override/modifier and blend radius at root position. No vanilla/global palette change.',
            'itemPolicy': 'Authored palette native default temperature=.5, humidity=1; untinted faces remain white.'}
        result_path.write_bytes(encode(result))
        (OUT / 'source-tint-feature.json').write_bytes(encode(result['sourceTintFeature']))
        return
    if args.coalesce_boxes_only:
        catalogue = json.loads((RES / 'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
        baseline = json.loads((OUT / 'baseline.json').read_text(encoding='utf-8'))
        archive = OUT / 'box-coalescing-before-after.jsonl.gz'
        if archive.exists():
            raise FileExistsError('Preserve the initial box union evidence: ' + str(archive))
        changes, total_before, total_after = [], 0, 0
        resource_hashes = {}
        with gzip.open(archive, 'wt', encoding='utf-8', newline='\n') as history:
            for row in catalogue['objects']:
                if not row['path'].startswith('source_'):
                    raise ValueError('Only new source_ descriptors may coalesce')
                target = RES / 'bloodborne_dw/composite' / (row['path'] + '.json')
                relative = target.relative_to(RES).as_posix()
                if 'src/architecture/resources/' + relative in baseline['implementation']:
                    raise ValueError('Refusing to change a baseline descriptor')
                original = target.read_bytes()
                descriptor = json.loads(original)
                count_before, count_after, changed = 0, 0, False
                for index, variant in enumerate(descriptor['variants']):
                    for pose_name, current in variant['poses'].items():
                        for field in ('collision', 'selection'):
                            before = current[field]
                            after = coalesce_boxes(before)
                            total_before += len(before)
                            total_after += len(after)
                            count_before += len(before)
                            count_after += len(after)
                            if before != after:
                                history.write(json.dumps({'id': descriptor['id'], 'variant': index,
                                    'pose': pose_name, 'field': field, 'descriptor': relative,
                                    'beforeDescriptorSha256': sha(original), 'before': before, 'after': after},
                                    ensure_ascii=False, separators=(',', ':')) + '\n')
                                current[field] = after
                                changed = True
                if changed:
                    descriptor['collisionPolicy'] = descriptor['collisionPolicy'].replace(
                        '). Visible', '), then exact rectangular-union coalescing. Visible')
                    data = encode(descriptor)
                    target.write_bytes(data)
                    resource_hashes[relative] = sha(data)
                    changes.append({'id': descriptor['id'], 'descriptor': relative,
                                    'before': count_before, 'after': count_after,
                                    'beforeSha256': sha(original), 'afterSha256': sha(data)})
        result_path = OUT / 'imported-resources.json'
        result = json.loads(result_path.read_text(encoding='utf-8'))
        for row in result['resources']:
            if row['file'] in resource_hashes:
                row['sha256'] = resource_hashes[row['file']]
        result_path.write_bytes(encode(result))
        report = {'status': 'COALESCED_PENDING_INDEPENDENT_UNION_CHECK',
                  'objectsChanged': len(changes), 'boxesBefore': total_before, 'boxesAfter': total_after,
                  'archive': archive.name, 'archiveSha256': sha(archive.read_bytes()),
                  'policy': 'Only exact equal cross-sections merge touching or overlapping intervals; no enclosing AABB substitution; no art or old runtime changes.',
                  'objects': sorted(changes, key=lambda row: row['before'], reverse=True)}
        (OUT / 'box-coalescing.json').write_bytes(encode(report))
        print(json.dumps({key: value for key, value in report.items() if key != 'objects'}))
        return
    if args.verify_geometry:
        aligned = {'from': [0, 0, 0], 'to': [16, 16, 16]}
        points = transformed_corners(aligned, {'model': 'check'})
        assert rotated_boxes(points) == [aligned]
        clockwise = transformed_corners(aligned, {'model': 'check', 'y': 90})
        assert all(abs(clockwise[0][index] - [16, 0, 0][index]) < 1e-8 for index in range(3))
        thin = {'from': [0, 0, 7], 'to': [64, 16, 9],
                'rotation': {'axis': 'y', 'angle': 45, 'origin': [8, 8, 8]}}
        points = transformed_corners(thin, {'model': 'check'})
        raster = rotated_boxes(points)
        volume = sum(math.prod(box['to'][index] - box['from'][index] for index in range(3)) for box in raster)
        outer = bounds(points)
        enclosing = math.prod(outer['to'][index] - outer['from'][index] for index in range(3))
        assert 0 < volume < enclosing / 4, (volume, enclosing)
        planar = {'from': [0, 0, 8], 'to': [32, 32, 8],
                  'rotation': {'axis': 'y', 'angle': 45, 'origin': [8, 8, 8]}}
        assert rotated_boxes(transformed_corners(planar, {'model': 'check'}))
        horizontal = {'from': [0, 16, 0], 'to': [32, 16, 32],
                      'rotation': {'axis': 'y', 'angle': 45, 'origin': [8, 8, 8]}}
        assert rotated_boxes(transformed_corners(horizontal, {'model': 'check'}))
        face = {'uv': [2, 3, 6, 9], 'texture': '#check'}
        for source_angle, expected in ((0, [[7, 2], [7, 6], [13, 6], [13, 2]]),
                                       (90, [[13, 2], [7, 2], [7, 6], [13, 6]])):
            adjusted = uvlocked_face({**face, 'rotation': source_angle}, 'up', aligned, {'uvlock': True, 'y': 90})
            u, v, U, V = adjusted['uv']
            coordinates = [[u, V], [U, V], [U, v], [u, v]]
            step = adjusted['rotation'] // 90
            actual = coordinates[step:] + coordinates[:step]
            assert actual == expected, (actual, expected)
        result = {'status': 'PASS', 'checks': 7, 'rotatedThinRasterBoxes': len(raster),
                  'rasterVolume': volume, 'enclosingAabbVolume': enclosing}
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / 'geometry-default-check.json').write_bytes(encode(result))
        print(json.dumps(result))
        return
    if args.manifest is None:
        if not args.refresh_emissive_only:
            parser.error('--manifest is required when importing')
    if args.refresh_emissive_only:
        source = Source(PACK, args.vanilla)
        catalogue = json.loads((RES / 'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
        affected = []
        for row in catalogue['objects']:
            target = RES / 'bloodborne_dw/composite' / (row['path'] + '.json')
            descriptor = json.loads(target.read_text(encoding='utf-8'))
            changed, has_emissive = False, False
            for variant in descriptor['variants']:
                for pose_name, current in variant['poses'].items():
                    applications = variant['sourceClosedApplications'] if pose_name == 'closed' else variant['sourceOpenApplications']
                    base = [part for part in current['parts'] if not part['model'].startswith('bloodborne_dw:remaining/source_emissive/')]
                    if len(base) != len(applications):
                        raise ValueError('Emissive refresh source-part mismatch: ' + row['path'])
                    parts = []
                    for application, part in zip(applications, base):
                        parts.append(part)
                        emissive = source.export_emissive_application_model(application)
                        if emissive:
                            parts.append({**part, 'model': emissive, 'emissive': True})
                            has_emissive = True
                    if current['parts'] != parts:
                        current['parts'] = parts
                        changed = True
            if changed:
                source.write(target, encode(descriptor), kind='descriptor')
            if has_emissive:
                affected.append(row['path'])
        result_path = OUT / 'imported-resources.json'
        result = json.loads(result_path.read_text(encoding='utf-8'))
        resources = {row['file']: row for row in result['resources']}
        resources.update({value['file']: value for name, value in source.exported.items() if name.startswith('file:')})
        result['resources'] = list(resources.values())
        result['emissiveFeature'] = {'status': 'RESOURCES_PREPARED', 'affectedObjects': affected,
                                     'consumerContract': 'Only new source_ objects: part.model path remaining/source_emissive/ renders with fullbright lightmap. Existing parts unchanged.'}
        result['summary']['emissiveOverlayModels'] = sum(row.get('kind') == 'emissive_model' for row in result['resources'])
        result['summary']['emissiveMaskTextures'] = sum(row.get('kind') == 'texture' and row.get('source', '').endswith('_e.png') for row in result['resources'])
        result_path.write_bytes(encode(result))
        (OUT / 'emissive-resources.json').write_bytes(encode(result['emissiveFeature']))
        print(json.dumps(result['emissiveFeature']))
        return
    manifest_bytes = args.manifest.read_bytes()
    manifest = json.loads(manifest_bytes.decode('utf-8-sig'))
    source = Source(PACK, args.vanilla)
    imported, blocked = import_objects(manifest, source)
    if any(row['path'] in ('source_grass_block', 'source_bricks_grass_block_0', 'source_potted_fern') for row in imported):
        source.grass_palette()
    baseline = manifest.get('baselineCommit', manifest.get('baseline_commit'))
    result = {'baselineCommit': baseline, 'packSha256': sha(PACK.read_bytes()),
              'inputManifestSha256': sha(manifest_bytes), 'objects': imported, 'blocked': blocked,
              'sourceWarnings': source.source_warnings,
              'resources': [value for name, value in source.exported.items() if name.startswith('file:')],
              'summary': {'imported': len(imported), 'variants': sum(row['variantCount'] for row in imported),
                          'blocked': len(blocked), 'sourceWarnings': len(source.source_warnings),
                          'models': sum(not name.startswith('file:') for name in source.exported)}}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'imported-resources.json').write_bytes(encode(result))
    catalogue = {'schemaVersion': 1, 'baselineCommit': baseline,
                 'objects': [{'path': row['path'], 'displayName': row['name'], 'variants': row['variants']}
                             for row in imported]}
    target = RES / 'bloodborne_dw/remaining-catalogue.json'
    if target.exists() and target.read_bytes() != encode(catalogue):
        baseline_data = json.loads((OUT / 'baseline.json').read_text(encoding='utf-8'))
        if 'src/architecture/resources/bloodborne_dw/remaining-catalogue.json' in baseline_data['implementation']:
            raise ValueError('Generated catalogue cannot replace a baseline resource')
        target.write_bytes(encode(catalogue))
    else:
        source.write(target, encode(catalogue), kind='catalogue')
    (OUT / 'import-progress.json').write_bytes(encode({'status': 'COMPLETE', 'done': len(imported) + len(blocked),
                                                     'total': len(manifest['objects']), **result['summary']}))
    print(json.dumps(result['summary'], ensure_ascii=False))


if __name__ == '__main__':
    main()
