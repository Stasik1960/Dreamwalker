"""Author the one bound FOREIGN-guest QA input; never opens a world or database.

Actual FULL composition/type evidence and the actual target bridge audit are
required. There is deliberately no pilot-type fallback or discovery step.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
from zipfile import ZipFile


SOURCE_SHA = '6261a58cd23120f0fc5ae6b9210919f18e385482d03464da0b8366e720eee268'
OWNER = '[-1182530740,-1421322098,-1822126450,163509195]'
POSE = 'sp_e70009f84997fbb5b6ede9de'
POLICY_SHA = '79392979f165ef447666f947b49d539c6cb262ce126e6b217cdcaf8bf6556afe'


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            value.update(block)
    return value.hexdigest()


def binding(path):
    path = Path(path).resolve(strict=True)
    return {'path': str(path), 'sha256': sha(path)}


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def bound(value):
    assert binding(value['path']) == value, 'Bound proof bytes/path changed'
    return read(value['path'])


def gson_transport(value):
    """Match report Gson's omitted named nulls; null array entries survive."""
    if isinstance(value, dict):
        return {key: gson_transport(item) for key, item in value.items() if item is not None}
    if isinstance(value, list):
        return [gson_transport(item) for item in value]
    return value


def derived_install_transport_view(original, executed_plan, original_reference, plan_reference):
    """Restore two binding trees from executed input; retain all execution facts."""
    fixture = executed_plan['foreignGuestFixture']
    detail = original['foreignGuestFixture']
    if not (original['status'] == 'PASS' and original['normalServerThreadFinished'] is True and
            original['planSha256'] == plan_reference['sha256'] and
            original['productionSha256'] == executed_plan['productionSha256'] and
            fixture['phase'] == 'install_save' and 'previousReport' not in fixture and
            detail['standRetainedForReopen'] is True and detail['gameplayComplete'] is False and
            'derivedTransportView' not in original and
            gson_transport(detail['binding']) == gson_transport(fixture) and
            gson_transport(detail['retainedNativeWholeItem']['binding']) == gson_transport(fixture['legacyNativeWholeItem'])):
        raise ValueError('Transport view requires the exact actual install report and its executed rich input')
    result = copy.deepcopy(original)
    result['foreignGuestFixture']['binding'] = copy.deepcopy(fixture)
    result['foreignGuestFixture']['retainedNativeWholeItem']['binding'] = copy.deepcopy(fixture['legacyNativeWholeItem'])
    result['derivedTransportView'] = {
        'kind': 'NOT_NEW_GAMEPLAY_EXECUTION', 'transformation': 'DERIVED_GSON_NAMED_NULL_RESTORATION_VIEW',
        'originalReport': copy.deepcopy(original_reference), 'executedInstallPlan': copy.deepcopy(plan_reference),
        'restoredBindings': ['foreignGuestFixture.binding', 'foreignGuestFixture.retainedNativeWholeItem.binding']}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-plan', type=Path, required=True)
    parser.add_argument('--production', type=Path, required=True)
    parser.add_argument('--point-proof', type=Path, required=True)
    parser.add_argument('--target-audit', type=Path, required=True)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--phase', choices=('install_save', 'reopen_finish'), required=True)
    parser.add_argument('--previous-report', type=Path)
    parser.add_argument('--previous-install-plan', type=Path,
                        help='Exact plan whose SHA is recorded by the successful install report')
    parser.add_argument('--retained-whole-resource-proof', type=Path, required=True,
                        help='Final observed old-native whole item resource receipt; never the provisional report')
    parser.add_argument('--legacy-whole-source', type=Path, default=Path(__file__).resolve().parents[4] / 'research/native-grid-plan/full/native-overhang-024801-native-statue-recipe-proof.json')
    parser.add_argument('--isolated-authoring-stand', action='store_true',
                        help='Use only the existing actual bridge-audit empty world, with no migration-marker claim')
    args = parser.parse_args()
    base = read(args.base_plan)
    assert base['mode'] == 'BOUND_NATIVE_GRID_FUNCTION_QA' and base['variant'] == 'candidate'
    assert base.get('authoringReferenceOnly', False) is False and 'referenceScope' not in base
    proof_binding, audit_binding = binding(args.point_proof), binding(args.target_audit)
    proof, audit = bound(proof_binding), bound(audit_binding)
    assert proof['status'] == 'PASS_SOURCE_COMPOSITION_AND_TYPE_BINDING_NOT_GAMEPLAY'
    assert proof['kind'] == 'EXACT_D_E_NONROOT_NATIVE_GRID_OVERLAY' and proof['scope'] == 'FULL_CITY'
    assert proof['sourceOwnerUuid'] == OWNER and proof['sourcePose'] == POSE
    source = bound(proof['sourceProof'])
    assert proof['sourceProof']['sha256'] == SOURCE_SHA and source['sourceBinding'] == proof['sourceBinding']
    fact = next(row for row in source['allAddressedOwnerSourceFacts'] if row['uuid'] == OWNER)
    assert not fact['sourceFailures'] and fact['pose'] == POSE and len(fact['runtimeMemberCells']) == 4
    assert fact['actualRootState'] == {'registryId': 'bloodborne_dw:source_stairs_exposed_cut_copper_stairs_bottom',
                                     'properties': {'open': 'false', 'profile': 'base', 'rotation': '4', 'variant': '0'}}
    assert fact['rootBE']['payload'] == {'ImportedTypeVersion': 1, 'SourceShift': [0, -1, 0]}
    policy = bound(proof['memberPolicyProof'])
    assert proof['memberPolicyProof']['sha256'] == POLICY_SHA and policy['sourceBinding'] == source['sourceBinding']
    policy_owner = next(row for row in policy['owners'] if row['uuid'] == OWNER)
    policies = {tuple(row['position']): row for row in policy_owner['memberPolicies']}
    catalog = bound(proof['catalog'])
    assert proof['catalog']['sha256'] == proof['catalogSha256']
    assert binding(proof['mapping']['path']) == proof['mapping']
    assert proof['mapping']['sha256'] == proof['mappingSha256']
    overlays = proof['overlays']
    assert len(overlays) == 2 and {tuple(row['offset']) for row in overlays} == {(0, -1, -1), (0, -1, 0)}
    for row in overlays:
        assert row['baseExcludesD'] is True
        assert row['sourcePosition'] == [a + b for a, b in zip(fact['root'], row['offset'])]
        member = policies[tuple(row['sourcePosition'])]
        assert member['policy'] == 'nonroot_guest_overlay'
        if row['offset'] == [0, -1, -1]:
            assert row['carrierMode'] == 'plain_foreign' and not member.get('existingBeBridge')
        else:
            assert row['carrierMode'] == 'existing_helper_bridge'
            assert member['existingBeBridge'] == {'hostState': {'registryId': 'bloodborne_dw:composite_cell', 'properties': {}},
                                                  'retainedResident': 0, 'existingCompositeBE': True}
        assert row['compositionKey'] and isinstance(row['cell'], int)
        cell = catalog['cells'][row['cell']]
        assert catalog['shapes'][cell['collision']] == row['collision'] and catalog['shapes'][cell['outline']] == row['outline']
    whole_background = proof['retainedNativeVisualBackground']
    assert whole_background['sourcePosition'] == [-100, 114, -643] and whole_background['offset'] == [0, 1, 0]
    assert whole_background['carrierMode'] == 'plain_native_visual_neighbour' and whole_background['baseExcludesW'] is True
    assert whole_background['sourceOwnerUuid'] == '[1766235759,1738012414,1336587778,1026181031]'
    assert whole_background['sourcePose'] == 'sp_6949081c5ca0a5e1288aa9ce'
    whole_cell = catalog['cells'][whole_background['cell']]
    assert whole_cell == whole_background['compiledCell'] and not whole_cell.get('wholeObject') and not whole_cell.get('fluid')
    assert catalog['shapes'][whole_cell['collision']] == whole_background['collision']
    assert catalog['shapes'][whole_cell['outline']] == whole_background['outline']
    wanted = {row['compositionKey']: row['cell'] for row in overlays}
    assert whole_background['compositionKey'] not in wanted or wanted[whole_background['compositionKey']] == whole_background['cell']
    wanted[whole_background['compositionKey']] = whole_background['cell']
    found = {}
    with Path(proof['mapping']['path']).open(encoding='utf-8') as stream:
        for line in stream:
            row = json.loads(line)
            if row['key'] in wanted:
                assert row['key'] not in found, 'Ambiguous actual composition mapping'
                found[row['key']] = row['typeIndex']
    assert found == wanted and sha(proof['mapping']['path']) == proof['mappingSha256']
    production = binding(args.production)
    legacy_source_binding = binding(args.legacy_whole_source)
    assert legacy_source_binding['sha256'] == 'de0cf82fb53bb61beea39ebaefba67e911b1622c7525554ddd76b2fc2ec41d28'
    legacy_source = bound(legacy_source_binding)
    assert legacy_source['status'] == 'READ_ONLY_EXACT_NATIVE_STATUE_MEMBER_AND_VISUAL_PROOF_NOT_ADMISSION'
    assert legacy_source['sourceBinding'] == source['sourceBinding']
    assert whole_background['sourceRecipeProof'] == legacy_source_binding
    retained_binding = binding(args.retained_whole_resource_proof)
    retained = bound(retained_binding)
    assert retained['status'] == 'PASS_EXACT_RETAINED_NATIVE_WHOLE_ITEM_RESOURCE_NOT_GAMEPLAY'
    assert retained['kind'] == 'FINITE_OBSERVED_RETAINED_NATIVE_WHOLE_ITEMS'
    assert retained['sourceBinding'] == source['sourceBinding'] and retained['originalV12Excluded'] is True
    frozen = bound(proof['frozenFullProof'])
    assert retained['finalCompositions'] == frozen['inputBindings']['inputs']['compositions']
    assert retained['compositionGeneration']['compositionsSha256'] == retained['finalCompositions']['sha256']
    resource = bound(retained['resource'])
    expected_legacy = legacy_source['statue']['legacyPose']
    expected_item_row = {key: expected_legacy[key] for key in ('registryId', 'properties', 'shift', 'mountY')}
    expected_item_row['legacyPose'] = 'p17ee1b36da539f69c340'
    assert resource['schemaVersion'] == 1 and expected_item_row in resource['poses']
    with ZipFile(production['path']) as jar:
        assert hashlib.sha256(jar.read('assets/bloodborne_dw/native_grid/catalog.json')).hexdigest() == proof['catalogSha256']
        assert hashlib.sha256(jar.read('assets/bloodborne_dw/native_grid/retained_whole_items.json')).hexdigest() == retained['resource']['sha256']
    assert audit['status'] == 'PASS' and audit['nativeRegistryQueries'] is True
    assert audit['runtimeSha256'] == production['sha256'] and audit['catalogSha256'] == proof['catalogSha256']
    assert audit['bridgePlanSha256'] == proof['targetBridgePlanSha256'] and audit['pairsChecked'] > 0
    audit_launch = read(args.target_audit.parent / 'launch-receipt.json')
    audit_runtime_binding = read(args.target_audit.parent / 'binding.json')
    assert audit_launch['status'] == 'PASS' and audit_launch['exitCode'] == 0
    assert all(audit_launch.get(key) == value for key, value in audit.items())
    assert audit_launch['productionSha256'] == audit_runtime_binding['productionSha256'] == production['sha256']
    assert audit_runtime_binding['catalogSha256'] == proof['catalogSha256'] and audit_runtime_binding['requestSha256'] == proof['targetBridgePlanSha256']
    assert set(audit_binding) == {'path', 'sha256'} and binding(audit_binding['path']) == audit_binding
    fixture = {'phase': args.phase, 'sourceProof': proof['sourceProof'], 'memberPolicyProof': proof['memberPolicyProof'],
               'pointProof': proof_binding, 'targetBridgeAudit': audit_binding,
               'legacyNativeWholeItem': {'legacyPose': 'p17ee1b36da539f69c340', 'sourceRecipeProof': legacy_source_binding,
                                         'resourceProof': retained_binding, 'resource': retained['resource'],
                                         'visualNeighbourBackground': whole_background}}
    pending_view = None
    if args.phase == 'reopen_finish':
        assert args.previous_report and args.previous_install_plan, 'Real reopen needs the actual install report and its exact executed plan'
        previous_binding = binding(args.previous_report)
        previous = bound(previous_binding)
        executed_binding = binding(args.previous_install_plan)
        executed = bound(executed_binding)
        view = derived_install_transport_view(previous, executed, previous_binding, executed_binding)
        prior = previous['foreignGuestFixture']
        assert previous['status'] == 'PASS' and previous['normalServerThreadFinished'] is True
        assert previous['productionSha256'] == production['sha256']
        assert prior['standRetainedForReopen'] is True and prior['gameplayComplete'] is False
        assert gson_transport({key: value for key, value in executed['foreignGuestFixture'].items()
                               if key not in ('phase', 'previousReport')}) == gson_transport({
            key: value for key, value in fixture.items() if key not in ('phase', 'previousReport')})
        view_path = args.plan.with_name(args.plan.stem + '.previous-install.transport-view.json').resolve()
        view_bytes = (json.dumps(view, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        fixture['previousReport'] = {'path': str(view_path), 'sha256': hashlib.sha256(view_bytes).hexdigest()}
        pending_view = (view_path, view_bytes)
    else:
        assert args.previous_report is None and args.previous_install_plan is None
    # The old windows/door/tree/statue inputs are evidence history, not actions
    # of this addressed case. Do not copy their stale catalog/type bindings.
    plan = {key: base[key] for key in ('schemaVersion', 'mode', 'variant', 'stand')}
    if 'dimension' in base:
        plan['dimension'] = base['dimension']
    plan.update(productionJar=production['path'], productionSha256=production['sha256'],
                targetWorldPath=str(args.world.resolve(strict=True)), output=str(args.output.resolve()),
                authoringReferenceOnly=False, foreignGuestFixture=fixture)
    root = Path(__file__).resolve().parents[4]
    known_empty = (root / 'research/native-bridge-authoring-server/empty-authoring').resolve(strict=True)
    assert args.isolated_authoring_stand and args.world.resolve(strict=True) == known_empty
    assert audit_runtime_binding['emptyAuthoringWorld'] == str(known_empty) and audit_runtime_binding['actualCityOpened'] is False
    plan['isolatedAuthoringStand'] = {'kind': 'EXACT_FOREIGN_GUEST_ISOLATED_AUTHORING_STAND', 'world': str(known_empty)}
    plan.pop('referenceScope', None)
    low, high = plan['stand']['min'], plan['stand']['max']
    plan['sourceChunks'] = [[x, z] for x in range(low[0] >> 4, (high[0] >> 4) + 1)
                            for z in range(low[2] >> 4, (high[2] >> 4) + 1)]
    plan['addressedQaScope'] = {'kind': 'ONE_FOREIGN_GUEST_FIXTURE_ONLY', 'repeatAcceptedGroups': False,
                              'setupRawStateWrites': 4 if args.phase == 'install_save' else 0,
                              'retainedNativeWholeItemInSamePhases': True,
                              'nativeWholeSetup': 'Exact W-only helper7177/root7176 plus actual final C background at visual-only +1Y; canonical item replaces W at the same root',
                              'gameplayCompleteRequiresRealReopenFinish': True,
                              'standDefinitionFrom': binding(args.base_plan)}
    args.plan.parent.mkdir(parents=True, exist_ok=True)
    assert not args.plan.exists(), 'Never overwrite an executed or previously authored plan'
    if pending_view:
        view_path, view_bytes = pending_view
        if view_path.exists():
            assert view_path.read_bytes() == view_bytes, 'Preserve a differing existing derived transport view'
        else:
            with view_path.open('xb') as stream:
                stream.write(view_bytes);stream.flush();os.fsync(stream.fileno())
    args.plan.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'AUTHORING_ONLY_NOT_GAMEPLAY', 'plan': binding(args.plan),
                      'phase': args.phase, 'members': 4, 'overlays': 2, 'worldReads': 0, 'databaseReads': 0}))


if __name__ == '__main__':
    main()
