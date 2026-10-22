"""Inspect authored model planes and actual door registration before composition.

This reads unique source types, never a world or an owner graph. A tree requires
both botanical identity evidence and wholly planar authored model geometry.
"""
from pathlib import Path
from functools import lru_cache
import argparse, hashlib, json


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def classify(resources):
    resources = Path(resources)

    @lru_cache(maxsize=None)
    def model(identifier):
        namespace, name = identifier.split(':', 1)
        path = resources/'assets'/namespace/'models'/(name+'.json')
        if not path.is_file():
            return {'missing': str(path)}
        row = read(path)
        parent = row.get('parent')
        if parent:
            if ':' not in parent:
                parent = 'minecraft:'+parent
            ancestor = model(parent)
            row = {**ancestor, **row, 'textures': {**ancestor.get('textures', {}), **row.get('textures', {})}}
        return row

    output = []
    for path in sorted((resources/'bloodborne_dw/composite').glob('*.json')):
        spec = read(path)
        rid = spec.get('id')
        if not rid:
            continue
        variants = []
        for index, variant in enumerate(spec.get('variants', [])):
            models = sorted({part['model'] for pose in variant.get('poses', {}).values() for part in pose.get('parts', [])})
            details = []
            for identifier in models:
                document = model(identifier)
                elements = [e for e in document.get('elements', []) if e.get('faces')]
                planar = [e for e in elements if any(abs(float(e['to'][i])-float(e['from'][i])) <= 1e-9 for i in range(3))]
                textures = sorted(set(document.get('textures', {}).values()))
                details.append({'model': identifier, 'visibleElements': len(elements), 'planarElements': len(planar), 'allVisibleElementsPlanar': bool(elements) and len(elements) == len(planar), 'textures': textures, 'missing': document.get('missing')})
            all_planar = bool(details) and all(d['allVisibleElementsPlanar'] for d in details)
            identity = ' '.join([rid, spec.get('displayName', ''), *[t for d in details for t in d['textures']]]).lower()
            tree = all_planar and any(word in identity for word in ('tree', 'дерево', 'дерева'))
            variants.append({'variant': index, 'spriteTree': tree, 'allVisibleElementsPlanar': all_planar, 'models': details})
        output.append({'registryId': rid, 'displayName': spec.get('displayName', ''), 'openable': bool(spec.get('openable') or spec.get('functionalDoorPhysics')), 'spriteTree': any(v['spriteTree'] for v in variants), 'variants': variants, 'descriptorSha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    return {'schemaVersion': 1, 'evidence': 'Actual composite openable/functionalDoorPhysics plus authored source-model element planes and resolved texture identity; unique types only', 'types': output, 'openableIds': sorted(r['registryId'] for r in output if r['openable']), 'spriteTreeIds': sorted(r['registryId'] for r in output if r['spriteTree'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = classify(args.resources)
    except BaseException:
        import traceback
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.with_suffix('.failure.txt').write_text(traceback.format_exc(), encoding='utf-8')
        raise
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'types': len(result['types']), 'openableIds': result['openableIds'], 'spriteTreeIds': result['spriteTreeIds']}))


if __name__ == '__main__':
    main()
