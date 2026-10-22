"""Addressed composition coverage checks before any catalog publication."""
from pathlib import Path
from collections import Counter
import argparse, hashlib, json, sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from export_cells import FragmentStore, read_lines, state_key


def inspect(compositions, fragments):
    store = FragmentStore(fragments)
    problems = []
    counts = Counter()
    source_hash = hashlib.sha256(Path(compositions).read_bytes()).hexdigest()
    for row in read_lines(compositions):
        counts['compositions'] += 1
        key = row.get('key')
        visual = []
        for field in ('fragments', 'substrateFragments'):
            for item in row.get(field, []):
                try:
                    fragment = store.fragment(item['pose'], tuple(item['cell']))[1]
                    if field == 'substrateFragments':
                        visual.extend(fragment['quads'])
                except KeyError as failure:
                    problems.append({'key': key, 'kind': 'missing_source_fragment', 'reference': item, 'error': str(failure)})
        for item in row.get('substrateNativeFragments', []):
            try:
                visual.extend(store.native_fragment(state_key(item['state']), tuple(item['cell']))[1]['quads'])
            except Exception as failure:
                problems.append({'key': key, 'kind': 'missing_native_visual_fragment', 'reference': item, 'error': str(failure)})
        base = row.get('baseState')
        if not base or base.get('registryId', base.get('id')) in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
            continue
        try:
            native = store.native(state_key(base))
        except KeyError as failure:
            problems.append({'key': key, 'kind': 'missing_native_state', 'baseState': base, 'error': str(failure)})
            continue
        mode = row.get('baseVisualMode', 'native')
        counts['baseVisualMode.'+mode] += 1
        if mode not in ('native', 'substrateFragments', 'nativePlusSubstrateFragments'):
            problems.append({'key': key, 'kind': 'unknown_base_visual_mode', 'mode': mode})
        if mode in ('native', 'nativePlusSubstrateFragments'):
            if not native.get('nativeVisualComplete', True):
                problems.append({'key': key, 'kind': 'physics_only_native_used_as_visual', 'baseState': base})
            if any(vertex[axis] < -1e-6 or vertex[axis] > 1.000001 for quad in native.get('quads', []) for vertex in quad['vertices'] for axis in range(3)):
                problems.append({'key': key, 'kind': 'native_visual_outside_single_cell', 'baseState': base})
        elif not native.get('nativeVisualComplete', True) and str(base.get('properties', {}).get('compact', '')).lower() == 'true' and not visual:
            problems.append({'key': key, 'kind': 'explicit_empty_substrate_discards_physics_only_model_visual', 'baseState': base})
        elif native.get('nativeVisualComplete', True) and native.get('quads') and not visual:
            problems.append({'key': key, 'kind': 'explicit_empty_substrate_discards_complete_native_visual', 'baseState': base, 'nativeQuadCount': len(native['quads'])})
    store.close()
    if source_hash != hashlib.sha256(Path(compositions).read_bytes()).hexdigest():
        raise ValueError('Composition source changed during preflight; wait for the final migration gate')
    return {'status': 'PASS' if not problems else 'FAIL', 'compositionsSha256': source_hash, 'counts': dict(counts), 'problemCount': len(problems), 'problems': problems}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compositions', type=Path, required=True)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    report = inspect(args.compositions, args.fragments)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'problemCount': report['problemCount'], 'counts': report['counts']}))
    if report['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
