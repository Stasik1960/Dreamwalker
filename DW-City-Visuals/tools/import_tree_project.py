#!/usr/bin/env python3
"""Import the user's editor project without changing any geometry or atlas pixels."""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import math
import struct
from pathlib import Path, PurePosixPath

WORK = Path(__file__).resolve().parents[1]
PROJECT = WORK / 'inputs/trees/DreamWalker-trees.dwtree.json'
ALT = WORK / 'src/main/resources/dw_city_visuals/alt'


def validate(project: dict) -> list[dict]:
    if (project.get('format'), project.get('version'), project.get('unitsPerBlock')) != ('dreamwalker-tree-project', 1, 16):
        raise ValueError('Unsupported editor project format/version/units')
    if len(project.get('models', [])) != 3 or {m['id'] for m in project['models']} != {'tree_1', 'tree_2', 'tree_3'}:
        raise ValueError('Exactly the three named tree models are required')
    for model in project['models']:
        if model['assetPath'] != f"assets/bloodborne_rp/geo/{model['id']}.geo.json":
            raise ValueError('Unexpected geometry target')
        geometries = model['geometry'].get('minecraft:geometry', [])
        if len(geometries) != 1:
            raise ValueError('Each model must contain one geometry')
        geo = geometries[0]
        bones = geo['bones']
        names = {b['name'] for b in bones}
        if len(names) != len(bones):
            raise ValueError('Duplicate bone name')
        lookup = {b['name']: b for b in bones}
        for bone in bones:
            seen = set()
            current = bone
            while current:
                if current['name'] in seen:
                    raise ValueError('Cyclic bone hierarchy')
                seen.add(current['name'])
                parent = current.get('parent')
                if parent is not None and parent not in lookup:
                    raise ValueError('Unknown parent bone')
                current = lookup.get(parent)
            for cube in bone.get('cubes', []):
                for key in ('origin', 'size', 'pivot', 'rotation'):
                    if key not in cube:
                        continue
                    if len(cube[key]) != 3 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in cube[key]):
                        raise ValueError('Invalid cube vector')
                if any(v < 0 for v in cube['size']):
                    raise ValueError('Negative cube size')
        texture = project['textures'][model['texture']]
        if texture['assetPath'] != 'assets/bloodborne_rp/textures/entities/decorations/tree_3.png':
            raise ValueError('Unexpected atlas target')
        if not texture['dataURL'].startswith('data:image/png;base64,'):
            raise ValueError('Expected embedded PNG atlas')
        png = base64.b64decode(texture['dataURL'].split(',', 1)[1], validate=True)
        if png[:8] != b'\x89PNG\r\n\x1a\n':
            raise ValueError('Invalid PNG signature')
        width, height = struct.unpack('>II', png[16:24])
        description = geo['description']
        if (width, height) != (description['texture_width'], description['texture_height']):
            raise ValueError('Geometry UV canvas differs from embedded PNG')
        if description['visible_bounds_width'] != 32 or description['visible_bounds_height'] != 30 or description['visible_bounds_offset'] != [0, 12, 0]:
            raise ValueError('Update the ALT tree render envelope for this new project')
    return project['models']


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=PROJECT)
    parser.add_argument('--report', type=Path, default=WORK / 'validation/tree-import-v7.json')
    args = parser.parse_args()
    raw = args.project.read_bytes()
    project = json.loads(raw.decode('utf-8-sig'))
    models = validate(project)
    report = {'status': 'PASS', 'projectSha256': hashlib.sha256(raw).hexdigest(), 'sourceSavedAt': project.get('savedAt'), 'models': []}
    for model in models:
        target = ALT / PurePosixPath(model['assetPath'])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(model['geometry'], ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        assert json.loads(target.read_text(encoding='utf-8')) == model['geometry']
        report['models'].append({'asset': model['assetPath'], 'boneCount': len(model['geometry']['minecraft:geometry'][0]['bones']),
                                 'cubeCount': sum(len(b.get('cubes', [])) for b in model['geometry']['minecraft:geometry'][0]['bones']), 'exactGeometryEquality': True})
    for texture in project['textures'].values():
        png = base64.b64decode(texture['dataURL'].split(',', 1)[1], validate=True)
        target = ALT / PurePosixPath(texture['assetPath'])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(png)
        report['atlas'] = {'asset': texture['assetPath'], 'sha256': hashlib.sha256(png).hexdigest(), 'exactByteEquality': target.read_bytes() == png}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
