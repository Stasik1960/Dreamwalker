"""Read-only model envelopes and exact two-block grid planning for a fresh gallery.

This module never edits a mod resource or an existing Minecraft world. Its model
transform follows CompositeClient (element, extra yaw, pitch, yaw, offset).
Envelopes reserve the closed and open pose and both BASE and ALT profiles, so an
exhibit can be opened or have its profile changed without reaching a neighbour.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
import itertools
import json
import math
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EPSILON = 1e-8


def rotate(point, axis, degrees, origin):
    """Right-handed Java RotationAxis transform in Minecraft model units."""
    radians = math.radians(degrees)
    cosine, sine = math.cos(radians), math.sin(radians)
    result = [float(point[i]) - origin[i] for i in range(3)]
    first, second = (axis + 1) % 3, (axis + 2) % 3
    a, b = result[first], result[second]
    result[first] = cosine * a - sine * b
    result[second] = sine * a + cosine * b
    return [result[i] + origin[i] for i in range(3)]


def corners(box):
    return itertools.product(*zip(box['from'], box['to']))


class ModelGeometry:
    def __init__(self, resource_roots=None, vanilla_jar=None):
        self.roots = tuple(Path(p) for p in (resource_roots or [
            ROOT / 'src/architecture/resources', ROOT / 'src/rp/resources']))
        self.vanilla = zipfile.ZipFile(vanilla_jar) if vanilla_jar else None

    @lru_cache(maxsize=None)
    def document(self, identifier):
        namespace, path = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
        relative = 'assets/' + namespace + '/models/' + path + '.json'
        for root in self.roots:
            source = root / relative
            if source.is_file():
                return json.loads(source.read_text(encoding='utf-8-sig'))
        if self.vanilla and relative in self.vanilla.namelist():
            return json.loads(self.vanilla.read(relative))
        raise FileNotFoundError('Missing gallery visual dependency: ' + identifier)

    @lru_cache(maxsize=None)
    def elements(self, identifier, chain=()):
        if identifier in chain:
            raise ValueError('Model inheritance cycle: ' + identifier)
        document = self.document(identifier)
        if 'elements' in document:
            return document['elements']
        parent = document.get('parent', '')
        if not parent or parent.startswith(('builtin/', 'minecraft:builtin/')):
            return []
        return self.elements(parent, chain + (identifier,))

    @lru_cache(maxsize=None)
    def points(self, identifier):
        points = []
        for element in self.elements(identifier):
            if not element.get('faces'):
                continue
            values = list(corners(element))
            rotation = element.get('rotation')
            if rotation:
                axis = 'xyz'.index(rotation['axis'])
                angle, origin = float(rotation['angle']), rotation['origin']
                if rotation.get('rescale'):
                    scale = 1 / math.cos(math.radians(angle))
                    values = [[origin[i] + (point[i] - origin[i]) * (1 if i == axis else scale)
                               for i in range(3)] for point in values]
                values = [rotate(point, axis, angle, origin) for point in values]
            points.extend(values)
        return tuple(tuple(point) for point in points)


def composite_envelope(spec, variant, models, *, include_open=True, include_alt=True):
    """Six local block coordinates covering visuals, shapes and reservations."""
    row = spec['variants'][variant]
    points = list(corners({'from': [0, 0, 0], 'to': [16, 16, 16]}))
    for cell in spec.get('essentialMask', []):
        points.extend(corners({'from': [value * 16 for value in cell],
                               'to': [(value + 1) * 16 for value in cell]}))
    if 'visibleBounds' in row:
        points.extend(corners(row['visibleBounds']))
    poses = row['poses']
    names = ['closed'] + (['open'] if include_open and 'open' in poses else [])
    for name in names:
        pose = poses[name]
        for field in ['collision', 'selection']:
            for box in pose.get(field, []):
                points.extend(corners(box))
        for part in pose['parts']:
            identifiers = {part['model']}
            if include_alt:
                identifiers.add(part.get('altModel', part['model']))
            pivot, extra_pivot = part.get('pivot', [8, 8, 8]), part.get('extraPivot', [8, 8, 8])
            offset = part.get('offset', [0, 0, 0])
            for identifier in sorted(identifiers):
                for point in models.points(identifier):
                    # MatrixStack operations act on vertices in reverse order.
                    point = rotate(point, 1, part.get('extraYaw', 0), extra_pivot)
                    point = rotate(point, 0, -part.get('pitch', 0), pivot)
                    point = rotate(point, 1, -part.get('yaw', 0), pivot)
                    points.append([point[i] + offset[i] for i in range(3)])
    bounds = [min(point[i] for point in points) / 16 for i in range(3)]
    bounds += [max(point[i] for point in points) / 16 for i in range(3)]
    if not all(math.isfinite(value) for value in bounds):
        raise ValueError('Non-finite envelope: ' + spec['id'])
    return [round(value, 10) for value in bounds]


def bounds_of_points(points):
    return [round(min(point[i] for point in points) / 16, 10) for i in range(3)] + [
        round(max(point[i] for point in points) / 16, 10) for i in range(3)]


def native_envelopes(models):
    """Native wall/ladder visuals are transformed custom models, not colliders.

    Walls cover their valid authored arm choices, quarter turns and profiles;
    material 1 is the deliberately restricted diagonal post. Ladder envelopes
    include the actual SourceLadderBakedModel outward mount and optional45 turn.
    No previously retired registry ID is restored or added to the catalogue.
    """
    envelopes, evidence = {}, {}
    blockstates = ROOT / 'src/architecture/resources/assets/bloodborne_dw/blockstates'
    for material in range(8):
        identifier = 'bloodborne_dw:prototype_wall' if material == 6 else 'bloodborne_dw:prototype_wall_skin_' + str(material)
        points = list(corners({'from':[0,0,0], 'to':[16,24,16]}))
        used = set()
        kinds = ['post'] if material == 1 else ['post','low','tall_' + str(material)]
        diagonal_values = [1] if material == 1 else [0,1]
        for profile in ['base','alt']:
            for diagonal in diagonal_values:
                for kind in kinds:
                    model = 'bloodborne_dw:block/wall/' + profile + '/' + kind + ('_diagonal' if diagonal else '')
                    used.add(model)
                    for quarter in range(4):
                        points.extend(rotate(point,1,-quarter*90,[8,8,8]) for point in models.points(model))
        key = identifier + '|0'
        envelopes[key] = bounds_of_points(points)
        evidence[key] = {'kind':'native_wall', 'material':material, 'models':sorted(used),
                         'geometrySource':'PrototypeWallModels primitive bank and ModelRotation quarter turns',
                         'poses':'Valid LOW/TALL/post choices, BASE/ALT; material1 is canonical diagonal post only'}
    facing_vectors = {'north':[0,-1], 'east':[1,0], 'south':[0,1], 'west':[-1,0]}
    for path in ['prototype_ladder','prototype_ladder_art_1','prototype_ladder_art_2']:
        document = json.loads((blockstates / (path + '.json')).read_text(encoding='utf-8-sig'))
        # Fixed-art registered aliases keep variant0 on normal new placement.
        # They are envelope metadata only; current catalogue still excludes them.
        points = list(corners({'from':[0,0,0],'to':[16,16,16]}))
        used = set()
        for condition, applications in document['variants'].items():
            state = dict(pair.split('=',1) for pair in condition.split(',') if pair)
            if state.get('variant') != '0' or state.get('source_clone') != 'false' or state.get('waterlogged') != 'false' or state.get('freestanding') != 'true':
                continue
            applications = applications if isinstance(applications,list) else [applications]
            for application in applications:
                model = application['model']
                used.add(model)
                dx,dz = facing_vectors[state['facing']]
                for point in models.points(model):
                    point = rotate(point,0,-application.get('x',0),[8,8,8])
                    point = rotate(point,1,-application.get('y',0),[8,8,8])
                    # SourceLadderBakedModel: opposite-facing mirror + one-cell shift.
                    point = [16-point[0]-16*dx, point[1],16-point[2]-16*dz]
                    if state.get('diagonal') == 'true':
                        point = rotate(point,1,-45,[8,8,8])
                    points.append(point)
        key = 'bloodborne_dw:' + path + '|0'
        envelopes[key] = bounds_of_points(points)
        evidence[key] = {'kind':'native_ladder', 'models':sorted(used),
                         'geometrySource':'Private blockstate model, SourceLadderBakedModel reflection/shift, DiagonalBakedModel rotation',
                         'poses':'variant0, normal freestanding placement, all facings and BASE/ALT'}
    return envelopes, evidence


def cell_bounds(bounds):
    return [math.floor(bounds[i] + EPSILON) for i in range(3)] + [
        math.ceil(bounds[i] - EPSILON) for i in range(3, 6)]


def grid_plan(exhibits, *, columns=16, gap=2, margin=2, floor_y=63,
              start_x=0, start_z=0, minimum_width=5, minimum_depth=5):
    """Full grid-cell platforms give exactly `gap` ground cells between edges.

    Input rows need a unique ``key`` and six root-relative block ``bounds``.
    A column is as wide as its widest exhibit, a row as deep as its deepest.
    Platforms fill those shared widths/depths. This retains aligned rows and
    columns without the wider-than-requested gaps of a sparse rectangle packer.
    """
    if columns <= 0 or gap < 0 or margin < 0:
        raise ValueError('Invalid grid parameters')
    exhibits = list(exhibits)
    if len({row['key'] for row in exhibits}) != len(exhibits):
        raise ValueError('Duplicate exhibit key')
    count = len(exhibits)
    if not count:
        return {'platforms': [], 'columns': 0, 'rows': 0, 'width': 0, 'depth': 0,
                'start': [start_x, start_z], 'gap': gap, 'margin': margin, 'floorY': floor_y}
    columns = min(columns, count)
    rows = (count + columns - 1) // columns
    widths, depths, discrete = [minimum_width] * columns, [minimum_depth] * rows, []
    for index, exhibit in enumerate(exhibits):
        bounds = cell_bounds(exhibit['bounds'])
        discrete.append(bounds)
        widths[index % columns] = max(widths[index % columns], bounds[3] - bounds[0] + 2 * margin)
        depths[index // columns] = max(depths[index // columns], bounds[5] - bounds[2] + 2 * margin)
    x_starts, z_starts = [], []
    cursor = start_x
    for width in widths:
        x_starts.append(cursor)
        cursor += width + gap
    cursor = start_z
    for depth in depths:
        z_starts.append(cursor)
        cursor += depth + gap
    planned = []
    for index, (exhibit, bounds) in enumerate(zip(exhibits, discrete)):
        column, row = index % columns, index // columns
        x, z = x_starts[column], z_starts[row]
        # Integer raising keeps all negative source art above the floor.
        root = [x + margin - bounds[0], floor_y + 1 - min(0, bounds[1]), z + margin - bounds[2]]
        actual = [exhibit['bounds'][i] + root[i] for i in range(3)] + [
            exhibit['bounds'][i + 3] + root[i] for i in range(3)]
        planned.append({**exhibit, 'row': row, 'column': column,
                        'platform': [x, floor_y, z, widths[column], depths[row]],
                        'root': root, 'worldBounds': actual})
    plan = {'platforms': planned, 'columns': columns, 'rows': rows,
            'width': sum(widths) + (columns - 1) * gap,
            'depth': sum(depths) + (rows - 1) * gap,
            'columnWidths': widths, 'rowDepths': depths, 'start': [start_x, start_z],
            'gap': gap, 'margin': margin, 'floorY': floor_y}
    verify_plan(plan)
    return plan


def verify_plan(plan):
    """Reject clipping, crossed envelopes, misaligned grids and incorrect gaps."""
    positions = {(row['row'], row['column']): row for row in plan['platforms']}
    gap, margin = plan['gap'], plan['margin']
    for row in plan['platforms']:
        x, y, z, width, depth = row['platform']
        a, b, c, d, e, f = row['worldBounds']
        if min(a - x, c - z, x + width - d, z + depth - f) < margin - EPSILON:
            raise ValueError('Visual/shape envelope leaves platform margin: ' + row['key'])
        if b < y + 1 - EPSILON:
            raise ValueError('Visual/shape envelope buried in floor: ' + row['key'])
        neighbour = positions.get((row['row'], row['column'] + 1))
        if neighbour and neighbour['platform'][0] - (x + width) != gap:
            raise ValueError('Horizontal platform gap differs: ' + row['key'])
        neighbour = positions.get((row['row'] + 1, row['column']))
        if neighbour and neighbour['platform'][2] - (z + depth) != gap:
            raise ValueError('Vertical platform gap differs: ' + row['key'])
    for index, row in enumerate(plan['platforms']):
        x, _, z, width, depth = row['platform']
        for other in plan['platforms'][index + 1:]:
            X, _, Z, W, D = other['platform']
            if not (x + width + gap <= X or X + W + gap <= x or z + depth + gap <= Z or Z + D + gap <= z):
                raise ValueError('Platforms overlap or lose gap: ' + row['key'] + '/' + other['key'])
    return {'status': 'PASS', 'platforms': len(positions), 'gap': gap,
            'visualAndShapeEnvelopesContained': True, 'envelopesAboveFloor': True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--vanilla-jar', type=Path, default=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    args = parser.parse_args()
    models = ModelGeometry(vanilla_jar=args.vanilla_jar)
    envelopes = {}
    for path in sorted((ROOT / 'src/architecture/resources/bloodborne_dw/composite').glob('*.json')):
        spec = json.loads(path.read_text(encoding='utf-8-sig'))
        for variant in range(len(spec['variants'])):
            envelopes[spec['id'] + '|' + str(variant)] = composite_envelope(spec, variant, models)
    native, native_evidence = native_envelopes(models)
    if set(native) & set(envelopes):
        raise ValueError('Native/composite envelope key collision')
    envelopes.update(native)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'schemaVersion': 1, 'units': 'blocks',
        'policy': 'closed/open + BASE/ALT visuals + collision + selection + root + essentialMask',
        'nativePolicy':'Native private models use actual custom renderer transforms; registry aliases in metadata are not offered again.',
        'envelopes': envelopes, 'nativeEvidence':native_evidence}, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
