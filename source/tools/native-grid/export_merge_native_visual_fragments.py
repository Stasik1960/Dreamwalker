"""Append actual native visual clips without hydrating existing native rows."""
import argparse
import hashlib
import json
import os
import sqlite3
from pathlib import Path
from export_cells import canonical, canonical_boxes, clipping_fingerprint, clip_pose, digest, read_lines, state_key, unique_quads
from export_merge_physics import BASE_SHA, read, require, sha

HELPER_SHA = '8ebbebae73d69c73ed31e40cce34c0d9535543e946f5359643caf773d919de20'
PROPERTIES_HELPER_SHA = '572a1bf3dc4d929098fa5d7aad798506ea65128f8ad8998e19cf74a3cba911d6'
FIELDS = ('collision', 'outline', 'culling', 'climbing', 'optics')


def reference(path): return {'path': str(path.resolve()), 'sha256': sha(path)}


def normalize_native_visual(visual):
    # The same read normalization as merge_native_rows, without its native DML.
    for field in FIELDS[:-1]:
        visual[field] = canonical_boxes(visual[field])
    for quad in visual.get('quads', []):
        material = quad.get('material', {})
        if material.get('tintSource') and visual['state'].get('properties'):
            material['tintProperties'] = visual['state']['properties']
    visual['quads'] = unique_quads(visual['quads'])
    return visual


def visual_clips(visual):
    normalize_native_visual(visual)
    return clip_pose({'key': 'nv_' + digest(visual['state'])[:24], 'quads': visual['quads'], 'cells': []})


def observed_properties(a):
    require(len(a.state_properties) == len(a.state_properties_sha256) == len(a.state_properties_request), 'Pair every actual property result with exact SHA and request')
    known, proofs = {}, []
    for path, expected, request_path in zip(a.state_properties, a.state_properties_sha256, a.state_properties_request):
        require(sha(path) == expected and a.state_properties_helper and sha(a.state_properties_helper) == PROPERTIES_HELPER_SHA, 'Actual properties/helper binding changed')
        data, launch, binding = (read(item) for item in (path, path.parent/'launch-receipt.json', path.parent/'binding.json'))
        require(data['status'] == 'PASS' and data['kind'] == 'EXACT_STATE_PROPERTIES' and data['runtimeSha256'] == BASE_SHA and data['worldReads'] == data['worldWrites'] == data['blockEntitiesInstantiated'] == 0 and data['sourceMapOpened'] is False, 'Only actual original worldless properties may prove an absent legacy BE flag')
        require(launch['status'] == 'PASS' and launch['exitCode'] == 0 and launch['actualSourceMapOpened'] is False and launch['worldStatesWritten'] == 0 and binding['productionSha256'] == launch['productionSha256'] == BASE_SHA and binding['helperSha256'] == PROPERTIES_HELPER_SHA and binding['requestSha256'] == launch['requestSha256'] == data['requestSha256'] == sha(request_path), 'Actual properties launch/request did not pass its source binding')
        query = read(request_path); wanted = {state_key(row) for row in query['stateProperties']}
        require(len(wanted) == len(query['stateProperties']) == len(data['states']) and {state_key(row['requestedState']) for row in data['states']} == wanted, 'Actual property state coverage changed')
        proof = {'result': reference(path), 'request': reference(request_path), 'launch': reference(path.parent/'launch-receipt.json'), 'binding': reference(path.parent/'binding.json'), 'helper': reference(a.state_properties_helper)}
        for row in data['states']:
            key = state_key(row['requestedState']); require(type(row['hasBlockEntity']) is bool and (key not in known or known[key]['hasBlockEntity'] == row['hasBlockEntity']), 'Missing/conflicting actual BE getter')
            known[key] = row
        proofs.append(proof)
    return known, proofs


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('export', 'request', 'production', 'helper', 'fragments'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--request-sha256', required=True)
    p.add_argument('--state-properties', type=Path, action='append', default=[])
    p.add_argument('--state-properties-sha256', action='append', default=[])
    p.add_argument('--state-properties-request', type=Path, action='append', default=[])
    p.add_argument('--state-properties-helper', type=Path)
    p.add_argument('--out', type=Path)
    a = p.parse_args(); out = a.out or a.export / 'merge-receipt.json'
    require(not out.exists(), 'A fresh fragment-only merge receipt is required')
    binding, launch, result, summary, request = (read(path) for path in (a.export/'binding.json', a.export/'launch-receipt.json', a.export/'export-result.json', a.export/'geometry-summary.json', a.request))
    require(launch.get('status') == 'PASS' and launch.get('exitCode') == 0 and launch.get('worldOpened') is False and result.get('status') == 'PASS' and result.get('worldOpened') is False and summary.get('worldOpened') is False, 'Require clean completed world-free actual export')
    console = (a.export/'client.log').read_text(encoding='utf-8', errors='replace')
    require(not any(marker in console for marker in ('OutOfMemoryError', 'Java heap space', 'GC overhead limit exceeded', 'unable to create native thread')), 'A callback cannot override actual OOM')
    require(binding['productionSha256'] == launch['productionSha256'] == BASE_SHA == sha(a.production), 'Immutable source JAR changed')
    require(binding['exporterSha256'] == launch['exporterSha256'] == HELPER_SHA == sha(a.helper), 'Exact strict export helper changed')
    require(binding['requestSha256'] == launch['requestSha256'] == a.request_sha256 == sha(a.request), 'Exact finite request changed')
    require(not request.get('poses') and not request.get('doorFootprints') and summary['poses'] == 0 and not list(read_lines(a.export/'source-geometry.jsonl')), 'Native visual-only merge must not contain source poses/doors')
    requested = request['nativeStates']; wanted = {state_key(row) for row in requested}
    actual = list(read_lines(a.export/'native-geometry.jsonl'))
    require(all(row.get('physicsOnly') is False for row in requested) and len(wanted) == len(requested) == len(actual) and {state_key(row['state']) for row in actual} == wanted, 'Actual complete native rows differ from exact requested states')
    require(all(row.get('nativeVisualComplete') is True and row.get('hasBlockEntity', False) is False and (row.get('renderType') != 'MODEL' or row.get('quads')) for row in actual), 'Incomplete/empty MODEL or BE native export is not a visual fragment input')
    property_states, property_proofs = observed_properties(a)
    expected_binding = request['inputBindings']['fragmentBinding']
    before_sha = sha(a.fragments)
    require(Path(expected_binding['path']).resolve() == a.fragments.resolve() and expected_binding['sha256'] == before_sha, 'Freeze the exact existing native/physics prefix before this fragment append')
    db = sqlite3.connect(a.fragments)
    try:
        db.execute('PRAGMA synchronous=FULL'); db.execute('BEGIN IMMEDIATE')
        algorithm = canonical(clipping_fingerprint())
        saved_algorithm = db.execute("SELECT data FROM metadata WHERE name='nativeVisualClippingAlgorithmSha256'").fetchone()
        require(saved_algorithm and saved_algorithm[0] == algorithm, 'Existing native clipping algorithm must remain byte-exact')
        before = {table: db.execute('SELECT count(*) FROM ' + table).fetchone()[0] for table in ('poses', 'fragments', 'native', 'native_fragments')}
        prepared, proofs, old_native = [], [], {}
        for visual in actual:
            normalize_native_visual(visual)
            state = state_key(visual['state']); saved = db.execute('SELECT data FROM native WHERE state=?', (state,)).fetchone()
            require(saved is not None, 'A fragment-only export cannot add/hydrate a missing native physics row: ' + state)
            old = json.loads(saved[0]); old_native[state] = saved[0]
            observed = property_states.get(state)
            no_be = old.get('hasBlockEntity') is False or ('hasBlockEntity' not in old and observed is not None and observed['hasBlockEntity'] is False)
            require(old.get('nativeVisualComplete') is False and no_be and (observed is None or observed['hasBlockEntity'] is False), 'Expected exact previously physics-only no-BE native row or actual bound false getter: ' + canonical({'state': state, 'nativeVisualComplete': old.get('nativeVisualComplete'), 'hasBlockEntity': old.get('hasBlockEntity')}))
            differences = {field: {'cachedDigest': digest(old.get(field)), 'actualDigest': digest(visual.get(field))} for field in (*FIELDS, 'fluid') if old.get(field) != visual.get(field) or (field in FIELDS and (field not in old or field not in visual))}
            require(not differences, 'Actual visual export changed normalized physical/optical fields: ' + canonical({'state': state, 'differences': differences}))
            cells, proof = visual_clips(visual)
            coordinates = set()
            for cell in cells:
                coordinate = tuple(cell.pop('cell')); require(coordinate not in coordinates, 'Duplicate actual native clipped coordinate'); coordinates.add(coordinate)
                encoded = canonical(cell); cell_digest = digest(cell)
                previous = db.execute('SELECT digest,data FROM native_fragments WHERE state=? AND x=? AND y=? AND z=?', (state, *coordinate)).fetchone()
                require(previous is None or previous == (cell_digest, encoded), 'Actual clipping would overwrite an existing native fragment')
                prepared.append((state, *coordinate, cell_digest, encoded))
            proofs.append({'state': visual['state'], 'existingNativeRowSha256': hashlib.sha256(saved[0].encode()).hexdigest(), 'hasBlockEntityEvidence': 'cached_false' if old.get('hasBlockEntity') is False else 'actual_bound_property_getter_false', 'physicalAndOpticalFieldsEqual': list(FIELDS) + ['fluid'], 'cells': [list(value) for value in sorted(coordinates)], **proof})
        changed = db.total_changes
        db.executemany('INSERT OR IGNORE INTO native_fragments VALUES(?,?,?,?,?,?)', prepared)
        added = db.total_changes - changed
        after = {table: db.execute('SELECT count(*) FROM ' + table).fetchone()[0] for table in before}
        require(all(after[table] == before[table] for table in ('poses', 'fragments', 'native')) and after['native_fragments'] == before['native_fragments'] + added, 'Only additive native visual fragments may change')
        require(all(db.execute('SELECT data FROM native WHERE state=?', (key,)).fetchone()[0] == encoded for key, encoded in old_native.items()), 'A cached native row changed')
        db.commit(); db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
    except BaseException:
        db.rollback(); raise
    finally:
        db.close()
    report = {'schemaVersion': 1, 'status': 'PASS', 'mode': 'EXACT_NATIVE_VISUAL_FRAGMENTS_ONLY', 'beforeFragmentsSha256': before_sha, 'afterFragmentsSha256': sha(a.fragments), 'binding': binding, 'launch': launch, 'request': reference(a.request), 'nativeGeometry': reference(a.export/'native-geometry.jsonl'), 'statePropertiesEvidence': property_proofs, 'nativeStates': len(actual), 'newNativeRows': 0, 'newNativeVisualFragments': added, 'beforeCounts': before, 'afterCounts': after, 'posesUnchanged': True, 'fragmentsUnchanged': True, 'existingNativeRowsUnchanged': True, 'existingNativeVisualFragmentsUnchanged': True, 'physicalAndOpticalFieldsEqual': True, 'clippingAlgorithmSha256': clipping_fingerprint(), 'proofs': proofs, 'mapReads': 0, 'mapWrites': 0, 'producer': reference(Path(__file__))}
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(report, ensure_ascii=False, indent=2) + '\n'); stream.flush(); os.fsync(stream.fileno())
    print(canonical({'status': 'PASS', 'receipt': reference(out), 'nativeStates': len(actual), 'newNativeVisualFragments': added, 'nativeRowsHydrated': 0}))


if __name__ == '__main__': main()
