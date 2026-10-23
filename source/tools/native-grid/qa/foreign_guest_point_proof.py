"""Bind two FOREIGN backgrounds and one retained native whole visual neighbour.

Future root execution only, after FULL freeze/import and successful TARGET
audit. Reads no world/cache/ledger/fragments database and performs no writes
except a fresh authoring JSON. It does not discover or choose replacement types.
"""
import argparse
import hashlib
import json
import os
import sqlite3
from pathlib import Path
from zipfile import ZipFile
from prepare_foreign_guest_plan import OWNER, POSE, SOURCE_SHA, POLICY_SHA

POINTS = ((-243, 31, -214), (-243, 31, -213))
WHOLE_POINT = (-100, 114, -643)
WHOLE_SOURCE_SHA = 'de0cf82fb53bb61beea39ebaefba67e911b1622c7525554ddd76b2fc2ec41d28'
CATALOG_ENTRY = 'assets/bloodborne_dw/native_grid/catalog.json'


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(1 << 20), b''):
            result.update(part)
    return result.hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def binding(path):
    path = Path(path).resolve(strict=True)
    return {'path': str(path), 'sha256': sha(path)}


def bound(reference):
    actual = binding(reference['path'])
    require(actual['sha256'] == reference['sha256'], 'Bound input changed: ' + str(reference['path']))
    return read(reference['path'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('plan', 'frozen', 'production', 'target-audit', 'source-proof', 'member-policy', 'out'):
        p.add_argument('--' + name, type=Path, required=True)
    for name in ('frozen-sha256', 'target-audit-sha256'):
        p.add_argument('--' + name, required=True)
    p.add_argument('--legacy-whole-source', type=Path, default=Path(__file__).resolve().parents[4] /
                   'research/native-grid-plan/full/native-overhang-024801-native-statue-recipe-proof.json')
    a = p.parse_args()
    require(not a.out.exists() and not a.out.with_name(a.out.name + '.writing').exists(), 'Fresh output required')
    frozen_ref, audit_ref = binding(a.frozen), binding(a.target_audit)
    require(frozen_ref['sha256'] == a.frozen_sha256 and audit_ref['sha256'] == a.target_audit_sha256,
            'Explicit FULL freeze/TARGET audit SHA differs')
    freeze, audit = read(a.frozen), read(a.target_audit)
    require(freeze['status'] == 'FROZEN_RESOURCES_READY' and
            freeze['kind'] == 'COMPLETED_FULL_CATALOG_AUTHORING_HANDOFF', 'Actual FULL frozen output required')
    append = bound(freeze['inputBinding'])
    require(append['status'] == 'PASS' and append['mode'] == 'EXPLICIT_FULL_APPEND_INPUT_BINDING' and
            append == freeze['inputBindings'], 'FULL input binding differs from completed freeze')
    inputs = append['inputs']
    final = bound(inputs['final_coverage'])
    require(final['status'] == 'PASS' and final['scope'] == 'FULL_CITY' and
            final['sourceBinding'] == append['sourceBinding'], 'Completed source-bound FULL coverage required')
    raw_ref = inputs['compositions']
    require(Path(raw_ref['path']).resolve() == (a.plan / 'compositions.jsonl').resolve() and
            sha(raw_ref['path']) == raw_ref['sha256'] == final['compositionsSha256'], 'CURRENT raw generation changed')
    catalog_ref, mapping_ref = binding(freeze['catalog']['path']), binding(freeze['mapping']['path'])
    require(catalog_ref['sha256'] == freeze['catalog']['sha256'] and
            mapping_ref['sha256'] == freeze['mapping']['sha256'], 'FULL catalog/mapping bytes changed')
    catalog = read(catalog_ref['path'])
    source_ref, policy_ref = binding(a.source_proof), binding(a.member_policy)
    require(source_ref['sha256'] == SOURCE_SHA and policy_ref['sha256'] == POLICY_SHA, 'Original exact source proofs changed')
    source, policy = read(a.source_proof), read(a.member_policy)
    require(source['status'] == policy['status'] == 'PASS' and
            source['sourceBinding'] == policy['sourceBinding'] == final['sourceBinding'], 'Source proof binding differs')
    fact = next(row for row in source['allAddressedOwnerSourceFacts'] if row['uuid'] == OWNER)
    require(not fact['sourceFailures'] and fact['pose'] == POSE and len(fact['runtimeMemberCells']) == 4,
            'Exact representative source owner/finite footprint differs')
    member = next(row for row in policy['owners'] if row['uuid'] == OWNER)
    policies = {tuple(row['position']): row for row in member['memberPolicies']}
    whole_source_ref = binding(a.legacy_whole_source)
    require(whole_source_ref['sha256'] == WHOLE_SOURCE_SHA, 'Exact retained native W source proof changed')
    whole_source = bound(whole_source_ref)
    require(whole_source['status'] == 'READ_ONLY_EXACT_NATIVE_STATUE_MEMBER_AND_VISUAL_PROOF_NOT_ADMISSION' and
            whole_source['sourceBinding'] == source['sourceBinding'] and
            tuple(whole_source['actualTarget']['position']) == WHOLE_POINT and
            whole_source['statue']['legacyPoseKey'] == 'p17ee1b36da539f69c340', 'Different retained native W/visual neighbour')
    whole_owner, whole_pose = whole_source['statue']['uuid'], whole_source['statue']['pose']
    production_ref = binding(a.production)
    with ZipFile(a.production) as archive:
        require(hashlib.sha256(archive.read(CATALOG_ENTRY)).hexdigest() == catalog_ref['sha256'],
                'Actual FULL production does not contain this catalog')
        require(hashlib.sha256(archive.read('assets/bloodborne_dw/native_grid/meshes.bin')).hexdigest() ==
                freeze['meshes']['sha256'], 'Actual FULL production meshes differ')
    launch_ref = binding(a.target_audit.parent / 'launch-receipt.json')
    runtime_ref = binding(a.target_audit.parent / 'binding.json')
    launch, runtime = bound(launch_ref), bound(runtime_ref)
    require(audit['status'] == 'PASS' and audit['nativeRegistryQueries'] is True and audit['pairsChecked'] > 0 and
            audit['runtimeSha256'] == production_ref['sha256'] and audit['catalogSha256'] == catalog_ref['sha256'],
            'Actual successful FULL TARGET bridge audit required')
    require(launch['status'] == 'PASS' and launch['exitCode'] == 0 and
            all(launch.get(key) == value for key, value in audit.items()) and
            launch['productionSha256'] == runtime['productionSha256'] == production_ref['sha256'] and
            runtime['catalogSha256'] == catalog_ref['sha256'] and runtime['requestSha256'] == audit['bridgePlanSha256'],
            'Actual audit launcher/runtime/request binding differs')
    database = (a.plan / 'plan.sqlite').resolve(strict=True)
    db = sqlite3.connect('file:' + database.as_posix() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    overlays = []
    try:
        db.execute('PRAGMA query_only=ON')
        db.execute('PRAGMA cache_size=-2048')
        db.execute('BEGIN')
        def meta(key, optional=False):
            result = db.execute('SELECT value FROM meta WHERE key=?', (key,)).fetchone()
            require(result is not None or optional, 'Required current plan binding missing: ' + key)
            return json.loads(result[0]) if result is not None else None
        require(not meta('scope', optional=True) and meta('fullNativeFunctionGateRequired') is True and
                meta('binding') == final['sourceBinding'] and
                meta('compositionGeneration') == final['compositionGeneration'], 'Not the CURRENT FULL source generation')
        current_catalog = meta('catalog')
        require(current_catalog['sha256'] == catalog_ref['sha256'] and
                current_catalog['meshesSha256'] == freeze['meshes']['sha256'], 'FULL type import has not bound this catalog')
        host_request = meta('bridgeHostStates')
        require(sha(host_request['path']) == host_request['sha256'] == audit['bridgePlanSha256'] and
                host_request['pairs'] == audit['pairsChecked'], 'TARGET audit omitted CURRENT full carrier request')
        require(not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?', ('.', OWNER)).fetchone(),
                'Retained D was incorrectly selected for compilation')
        require(not db.execute('SELECT 1 FROM fragments WHERE dim=? AND uuid=? LIMIT 1', ('.', OWNER)).fetchone(),
                'Retained D still has a selected compiled fragment')
        for position in POINTS:
            require(position in policies and policies[position]['policy'] == 'nonroot_guest_overlay', 'Point is not exact authorized guest member')
            args = ('.', *position)
            placed = db.execute('SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone()
            require(placed is not None and type(placed['type_index']) is int, 'Exact FULL placement/type missing')
            saved = db.execute('SELECT data,type_index FROM compositions WHERE key=?', (placed['composition'],)).fetchone()
            require(saved is not None and saved['type_index'] == placed['type_index'], 'Imported composition/type mismatch')
            composition = json.loads(saved['data'])
            require(not placed['composition'].startswith('whole:') and
                    (composition.get('baseState') or {}).get('registryId') != fact['actualRootState']['registryId'],
                    'Retained owned D state/whole role cannot be the compiled native background')
            refs = composition.get('fragments', []) + composition.get('substrateFragments', [])
            require(all(ref['pose'] != POSE for ref in refs), 'Exact D source pose was merged into the background')
            for table in ('fragments', 'substrate_fragments'):
                require(not db.execute('SELECT 1 FROM ' + table + ' WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?', (*args, OWNER)).fetchone(),
                        'Retained D ownership remains in compiled point demand')
            bridge = db.execute('SELECT * FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone()
            expected_bridge = policies[position].get('existingBeBridge')
            if expected_bridge:
                require(expected_bridge == {'hostState': {'registryId': 'bloodborne_dw:composite_cell', 'properties': {}},
                                           'retainedResident': 0, 'existingCompositeBE': True}, 'Different existing helper policy')
                require(bridge is not None and bridge['type_index'] == placed['type_index'] and
                        bridge['retained_resident'] == 0 and json.loads(bridge['host_state']) == expected_bridge['hostState'] and
                        composition.get('wrapper') == 'NativeGridBase', 'Actual retained helper/background bridge differs')
                require(any(row['status'] == 'PASS' and row['input'] == {'state': expected_bridge['hostState'], 'cell': placed['type_index']}
                            for row in audit['results']), 'Exact helper/type pair absent from actual TARGET audit')
                mode = 'existing_helper_bridge'
            else:
                require(bridge is None and not composition.get('wrapper'), 'Plain FOREIGN member unexpectedly requires a carrier')
                mode = 'plain_foreign'
            index = placed['type_index']
            require(0 <= index < len(catalog['cells']), 'Actual mapped cell outside catalog')
            cell = catalog['cells'][index]
            require(not cell.get('wholeObject') and not cell.get('fluid'), 'Addressed background unexpectedly has whole/fluid semantics')
            overlays.append({'sourcePosition': list(position), 'offset': [v - r for v, r in zip(position, fact['root'])],
                             'cell': index, 'compositionKey': placed['composition'], 'baseExcludesD': True,
                             'carrierMode': mode, 'collision': catalog['shapes'][cell['collision']],
                             'outline': catalog['shapes'][cell['outline']], 'compiledCell': cell,
                             'placement': dict(placed), 'bridge': dict(bridge) if bridge else None,
                             'rawComposition': composition, 'rawCompositionRecordSha256': hashlib.sha256(saved['data'].encode()).hexdigest()})
        for table in ('selected_objects', 'activated_native', 'cleanup_owners', 'fragments'):
            require(not db.execute('SELECT 1 FROM ' + table + ' WHERE dim=? AND uuid=? LIMIT 1', ('.', whole_owner)).fetchone(),
                    'The retained W was selected, activated, compiled or scheduled for cleanup')
        whole_root = whole_source['statue']['root']
        for offset in ((0, -1, 0), (0, 0, 0)):
            position = tuple(v + d for v, d in zip(whole_root, offset))
            require(not db.execute('SELECT 1 FROM placements WHERE dim=? AND x=? AND y=? AND z=?', ('.', *position)).fetchone() and
                    db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                               ('.', *position, whole_owner)).fetchone(), 'A real retained W member is no longer protected original native data')
            require(not db.execute('SELECT 1 FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                                   ('.', *position, whole_owner)).fetchone(), 'A retained W member entered derived native background demand')
        args = ('.', *WHOLE_POINT)
        placed = db.execute('SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone()
        require(placed is not None and type(placed['type_index']) is int, 'Exact final C visual-neighbour placement/type missing')
        saved = db.execute('SELECT data,type_index FROM compositions WHERE key=?', (placed['composition'],)).fetchone()
        require(saved is not None and saved['type_index'] == placed['type_index'], 'W visual-neighbour composition/type mismatch')
        composition = json.loads(saved['data'])
        require(not placed['composition'].startswith('whole:') and not composition.get('wrapper') and
                not db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?', args).fetchone(),
                'W visual neighbour must be the exact plain NativeGrid background')
        refs = composition.get('fragments', []) + composition.get('substrateFragments', [])
        require(all(ref['pose'] not in {whole_pose, whole_source['statue']['legacyPoseKey']} for ref in refs),
                'Retained native W geometry entered its visual-neighbour background')
        require(not db.execute('SELECT 1 FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                               (*args, whole_owner)).fetchone(), 'Retained W derived data remains at its separate visual neighbour')
        require(not db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',
                               (*args, whole_owner)).fetchone(), 'Stale W visual-only protection was not corrected')
        index = placed['type_index']
        require(0 <= index < len(catalog['cells']), 'W visual-neighbour type outside final catalog')
        cell = catalog['cells'][index]
        require(not cell.get('wholeObject') and not cell.get('fluid'), 'W visual-neighbour background has unexpected whole/fluid semantics')
        whole_background = {'sourcePosition': list(WHOLE_POINT), 'offset': [0, 1, 0], 'cell': index,
                            'compositionKey': placed['composition'], 'carrierMode': 'plain_native_visual_neighbour',
                            'baseExcludesW': True, 'sourceOwnerUuid': whole_owner, 'sourcePose': whole_pose,
                            'sourceRecipeProof': whole_source_ref, 'collision': catalog['shapes'][cell['collision']],
                            'outline': catalog['shapes'][cell['outline']], 'compiledCell': cell, 'placement': dict(placed),
                            'rawComposition': composition,
                            'rawCompositionRecordSha256': hashlib.sha256(saved['data'].encode()).hexdigest()}
    finally:
        db.close()
    wanted = {row['compositionKey']: row['cell'] for row in overlays}
    require(whole_background['compositionKey'] not in wanted or
            wanted[whole_background['compositionKey']] == whole_background['cell'], 'One composition maps to different actual types')
    wanted[whole_background['compositionKey']] = whole_background['cell']
    found = {}
    with Path(mapping_ref['path']).open(encoding='utf-8') as stream:
        for line in stream:
            row = json.loads(line)
            if row['key'] in wanted:
                require(row['key'] not in found, 'Duplicate exact mapped composition')
                found[row['key']] = row['typeIndex']
    require(found == wanted and sha(mapping_ref['path']) == mapping_ref['sha256'], 'Actual FULL mapping does not bind all three exact points')
    for reference in (frozen_ref, audit_ref, source_ref, policy_ref, whole_source_ref, production_ref, catalog_ref, launch_ref, runtime_ref):
        require(sha(reference['path']) == reference['sha256'], 'Authoring input changed while binding the three points')
    result = {'schemaVersion': 1, 'status': 'PASS_SOURCE_COMPOSITION_AND_TYPE_BINDING_NOT_GAMEPLAY',
              'kind': 'EXACT_D_E_NONROOT_NATIVE_GRID_OVERLAY', 'scope': 'FULL_CITY',
              'sourceBinding': final['sourceBinding'], 'sourceOwnerUuid': OWNER, 'sourcePose': POSE,
              'sourceProof': source_ref, 'memberPolicyProof': policy_ref,
              'catalog': catalog_ref, 'catalogSha256': catalog_ref['sha256'],
              'mapping': mapping_ref, 'mappingSha256': mapping_ref['sha256'], 'overlays': overlays,
              'retainedNativeVisualBackground': whole_background,
              'targetBridgePlanSha256': audit['bridgePlanSha256'], 'targetBridgeAudit': audit_ref,
              'production': production_ref, 'frozenFullProof': frozen_ref,
              'compositionGeneration': final['compositionGeneration'], 'rawCompositions': raw_ref,
              'sourceDSelected': False, 'sourceDCompiledFragments': 0,
              'compiledSourcePoseReferencesAtBothPoints': 0, 'worldFilesRead': 0, 'worldWrites': 0,
              'persistentDatabaseWrites': 0, 'gameProcessesStarted': 0, 'exactPointCount': 3,
              'generator': binding(__file__), 'qaPlanBuilder': binding(Path(__file__).with_name('prepare_foreign_guest_plan.py'))}
    a.out.parent.mkdir(parents=True, exist_ok=True)
    temporary = a.out.with_name(a.out.name + '.writing')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    require(not a.out.exists(), 'Output appeared during authoring')
    os.replace(temporary, a.out)
    print(canonical({'status': result['status'], 'out': binding(a.out), 'points': 3, 'worldReads': 0, 'databaseWrites': 0}))


if __name__ == '__main__':
    main()
