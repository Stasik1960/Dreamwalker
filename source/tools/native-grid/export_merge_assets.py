"""Verify a completed asset-only driver and enrich exact native visual rows."""
from pathlib import Path
import argparse, json, sqlite3
from export_cells import canonical, merge_additional_sources, prepare_database, read_lines, state_key
from export_merge_physics import BASE_SHA, read, require, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export', type=Path, required=True)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--production', type=Path, required=True)
    parser.add_argument('--helper', type=Path, required=True)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--textures', type=Path, help='Actual unchanged source resources; required when poses are requested')
    args = parser.parse_args()
    binding = read(args.export/'binding.json')
    launch = read(args.export/'launch-receipt.json')
    result = read(args.export/'export-result.json')
    summary = read(args.export/'geometry-summary.json')
    request = read(args.request)
    require(launch.get('status') == 'PASS' and launch.get('exitCode') == 0 and not launch.get('worldOpened', True), 'Asset driver has not completed a successful world-free normal shutdown')
    console = (args.export/'client.log').read_text(encoding='utf-8', errors='replace')
    require(not any(marker in console for marker in ('OutOfMemoryError', 'Java heap space', 'GC overhead limit exceeded', 'unable to create native thread')), 'Asset driver has a real OOM; callback PASS is insufficient')
    require(binding['productionSha256'] == launch['productionSha256'] == BASE_SHA == sha(args.production), 'Asset production differs from the immutable blocks.2 checkpoint')
    require(binding['exporterSha256'] == launch['exporterSha256'] == sha(args.helper), 'Asset helper binding differs from the actual launch JAR')
    require(binding['requestSha256'] == launch['requestSha256'] == sha(args.request), 'Asset request changed after launch')
    require(result.get('status') == 'PASS' and not result.get('worldOpened', True) and not summary.get('worldOpened', True), 'Asset export did not pass its world-free gate')
    wanted_poses = {row['key'] for row in request.get('poses', [])}
    source_rows = list(read_lines(args.export/'source-geometry.jsonl'))
    actual_poses = {row['key'] for row in source_rows}
    require(actual_poses == wanted_poses and len(source_rows) == len(actual_poses) == summary['poses'], 'Supplemental pose coverage differs from the exact finite request')
    require(not wanted_poses or args.textures, 'Supplemental source clipping requires the actual unchanged texture resources')
    wanted = {state_key(row) for row in request.get('nativeStates', [])}
    rows = list(read_lines(args.export/'native-geometry.jsonl'))
    actual = {state_key(row['state']) for row in rows}
    require(actual == wanted and len(rows) == len(actual), 'Native asset coverage differs from exact requested states')
    require(all(row.get('nativeVisualComplete', True) and (row.get('renderType') != 'MODEL' or row.get('quads')) for row in rows), 'A requested native visual is incomplete or unexpectedly empty')
    door_proof = None
    if request.get('doorFootprints'):
        doors = request['doorFootprints']
        door_proof = read(args.export/'door-footprints.json')
        require(door_proof.get('status') == 'PASS' and door_proof.get('nativeDescriptor') and not door_proof.get('worldOpened', True), 'Protected door descriptor export did not pass its world-free gate')
        require(door_proof['sourceRequestSha256'] == doors['sha256'] == sha(doors['requestPath']), 'Protected door request changed after freeze')
        require(door_proof['baselineModJarSha256'] == BASE_SHA, 'Protected door descriptor export used a different actual JAR')
        require({row['key'] for row in door_proof['poses']} == {row['key'] for row in doors['poses']} and len(door_proof['poses']) == len(doors['poses']), 'Protected door pose coverage differs from the exact request')
        for row in door_proof['poses']:
            require({state['open'] for state in row['states']} == {False, True}, 'Protected door lacks both functional states')
            require({tuple(cell) for cell in row['cells']} == {tuple(cell) for state in row['states'] for cell in state['cells']}, 'Protected door union omitted a future helper cell')
    source_proof = merge_additional_sources(args.export/'source-geometry.jsonl', args.fragments, args.textures) if wanted_poses else None
    merged = prepare_database(None, args.fragments, args.export/'native-geometry.jsonl')
    proof = {'status': 'PASS', 'nativeStates': len(rows), 'nativeQuads': sum(len(row['quads']) for row in rows), 'supplementalPoses': len(source_rows), 'doorFootprintPoses': len(door_proof['poses']) if door_proof else 0, 'sourceMerge': source_proof, 'binding': binding, 'launch': launch, 'oomMarkers': 0, 'nativeGeometrySha256': sha(args.export/'native-geometry.jsonl'), 'merge': merged}
    database = sqlite3.connect(args.fragments)
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('nativeVisualConfirmed.'+binding['requestSha256'], canonical({'export': str(args.export), 'nativeGeometrySha256': proof['nativeGeometrySha256'], 'successfulDriverExitCode': 0, 'oomMarkers': 0})))
    database.commit()
    database.close()
    (args.export/'merge-receipt.json').write_text(json.dumps(proof, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': 'PASS', 'nativeStates': proof['nativeStates'], 'nativeQuads': proof['nativeQuads'], 'nativeTotal': merged['totals']['native']}))


if __name__ == '__main__':
    main()
