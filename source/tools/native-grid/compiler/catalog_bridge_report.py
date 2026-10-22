"""Verify actual retained-BE background types satisfy the finite bridge gate."""
from pathlib import Path
import argparse, hashlib, json, sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from export_cells import read_lines


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--compositions', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--carrier-audit', type=Path, help='Completed native query output; its prior comparison may have failed')
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding='utf-8'))
    mapping = {row['key']: row['typeIndex'] for row in read_lines(args.mapping)}
    count, indices, issues = 0, set(), []
    carrier_flags = None
    if args.carrier_audit:
        audit = json.loads(args.carrier_audit.read_text(encoding='utf-8-sig'))
        observed = {(row['opaque'], row['sidedTransparency'], row['luminance'], row['fluidEmpty']) for row in audit['results']}
        if len(observed) != 1:
            raise ValueError('Actual carrier flags differ by host; require an explicit finite host-state correspondence')
        carrier_flags = next(iter(observed))
    for row in read_lines(args.compositions):
        if row.get('wrapper') != 'NativeGridBase':
            continue
        count += 1
        index = mapping[row['key']]
        indices.add(index)
        cell = catalog['cells'][index]
        optics = catalog['optics'][cell['optics']]
        if cell.get('fluid') is not None or optics['luminance'] != 0:
            issues.append({'key': row['key'], 'typeIndex': index, 'luminance': optics['luminance'], 'fluid': cell.get('fluid')})
        if carrier_flags is not None and (optics['opaque'], optics['sidedTransparency'], optics['luminance'], cell.get('fluid') is None) != carrier_flags:
            issues.append({'key': row['key'], 'typeIndex': index, 'kind': 'compiled_background_differs_from_observed_carrier_flags', 'opaque': optics['opaque'], 'sidedTransparency': optics['sidedTransparency'], 'luminance': optics['luminance'], 'fluidEmpty': cell.get('fluid') is None})
    report = {'status': 'PASS' if not issues else 'FAIL', 'wrapperCompositions': count, 'uniqueBackgroundCellTypes': len(indices), 'allCompiledBackgroundsFluidEmpty': all(catalog['cells'][index].get('fluid') is None for index in indices), 'allCompiledBackgroundsLuminanceZero': all(catalog['optics'][catalog['cells'][index]['optics']]['luminance'] == 0 for index in indices), 'observedCarrierFlags': dict(zip(('opaque', 'sidedTransparency', 'luminance', 'fluidEmpty'), carrier_flags)) if carrier_flags is not None else None, 'allCompiledBackgroundsMatchObservedCarrierFlags': not issues if carrier_flags is not None else None, 'catalogSha256': hashlib.sha256(args.catalog.read_bytes()).hexdigest(), 'compositionsSha256': hashlib.sha256(args.compositions.read_bytes()).hexdigest(), 'issues': issues, 'scope': 'Compiled background check against provided prior native carrier observations; new-JAR runtime validate audit remains required'}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
    if issues:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
