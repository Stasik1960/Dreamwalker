"""Verify an addressed server export binding and merge its exact native rows."""
from pathlib import Path
import argparse, hashlib, json, shutil, sqlite3
from export_cells import canonical, prepare_database, read_lines, state_key


BASE_SHA = '40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):
            value.update(chunk)
    return value.hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export', type=Path, required=True)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--mods', type=Path, required=True)
    parser.add_argument('--fragments', type=Path, required=True)
    parser.add_argument('--invalidate-reason')
    args = parser.parse_args()
    if args.invalidate_reason:
        receipt_path = args.export/'merge-receipt.json'
        original = read(receipt_path) if receipt_path.exists() else {}
        preserved = args.export/'merge-receipt.callback-only.json'
        if receipt_path.exists() and not preserved.exists():
            shutil.copyfile(receipt_path, preserved)
        keys = sorted(state_key(row['state']) for row in read_lines(args.export/'native-physics.jsonl'))
        database = sqlite3.connect(args.fragments)
        database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('nativePhysicsQuarantine', canonical({'states': keys, 'reason': args.invalidate_reason, 'firstAttempt': str(args.export)})))
        database.commit()
        database.close()
        receipt_path.write_text(json.dumps({**original, 'status': 'INVALIDATED_AUTHORING_OOM', 'reason': args.invalidate_reason, 'compilerBlocked': True, 'originalCallbackReceipt': str(preserved)}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print('INVALIDATED_AUTHORING_OOM: compiler blocked until a clean completed driver confirms all affected exact states')
        return
    binding = read(args.export/'binding.json')
    result = read(args.export/'physics-result.json')
    summary = read(args.export/'physics-summary.json')
    launch = read(args.export/'launch-receipt.json')
    require(launch.get('status') == 'PASS' and launch.get('exitCode') == 0, 'Physics driver has not completed a successful normal shutdown')
    console = (args.export/'server-console.log').read_text(encoding='utf-8', errors='replace')
    require(not any(marker in console for marker in ('OutOfMemoryError', 'Java heap space', 'GC overhead limit exceeded', 'unable to create native thread')), 'Authoring driver log contains a real OOM; callback PASS does not confirm the export')
    require(result['status'] == 'PASS' and result['worldStatesWritten'] == 0 and not result['actualSourceMapOpened'], 'Server physics export did not pass its nonmutation gate')
    require(binding['productionSha256'] == BASE_SHA == sha(args.mods/'dreamwalker-production.jar'), 'Physics export production differs from the immutable blocks.2 checkpoint')
    require(binding['helperSha256'] == sha(args.mods/'native-grid-export.jar'), 'Physics helper binding differs from actual launch JAR')
    require(binding['requestSha256'] == sha(args.request), 'Physics request changed after launch')
    document = read(args.request)
    requested = document if isinstance(document, list) else document.get('nativeStates', document.get('states', []))
    wanted = {state_key(row) for row in requested}
    rows = list(read_lines(args.export/'native-physics.jsonl'))
    actual = {state_key(row['state']) for row in rows}
    require(actual == wanted and len(rows) == len(actual) == summary['states'], 'Physics export state coverage differs from exact requested states')
    require(all(not row['nativeVisualComplete'] and not row['quads'] and not row['hasBlockEntity'] for row in rows), 'Physics-only state output silently contains visual/BE semantics')
    before = args.fragments.with_suffix('.receipt.json')
    preserved = args.fragments.with_suffix('.geometry.receipt.json')
    if before.is_file() and not preserved.exists():
        shutil.copyfile(before, preserved)
    merged = prepare_database(None, args.fragments, args.export/'native-physics.jsonl')
    database = sqlite3.connect(args.fragments)
    pending = database.execute("SELECT data FROM metadata WHERE name='nativePhysicsQuarantine'").fetchone()
    if pending:
        quarantine = json.loads(pending[0])
        require(set(quarantine['states']) <= actual, 'Fresh driver did not re-confirm every quarantined exact state')
        database.execute("DELETE FROM metadata WHERE name='nativePhysicsQuarantine'")
    database.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', ('nativePhysicsConfirmed', canonical({'requestSha256': binding['requestSha256'], 'nativePhysicsSha256': sha(args.export/'native-physics.jsonl'), 'export': str(args.export), 'successfulDriverExitCode': 0, 'oomMarkers': 0})))
    database.commit()
    database.close()
    receipt = {'status': 'PASS', 'requestedAndExportedStates': len(rows), 'binding': binding, 'launch': launch, 'oomMarkers': 0, 'nativePhysicsSha256': sha(args.export/'native-physics.jsonl'), 'merge': merged, 'initialGeometryReceipt': str(preserved)}
    (args.export/'merge-receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': 'PASS', 'requestedAndExportedStates': len(rows), 'nativeTotal': merged['totals']['native']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
