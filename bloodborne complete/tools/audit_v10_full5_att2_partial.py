"""Validate the completed ordinary FULL5ATT2 subset, preserving its terminal GUI FAIL."""
import argparse
import hashlib
import json
from pathlib import Path
from verify_v10_middle_pick import check_pick, check_inventory_readback, compound, valid_uuid


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(ok, reason):
    if not ok:
        raise RuntimeError(reason)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--wrapper', type=Path, required=True)
    p.add_argument('--catalogue-audit', type=Path, required=True)
    p.add_argument('--production-manifest', type=Path, required=True)
    p.add_argument('--qa-manifest', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    require(not a.output.exists(), 'Never overwrite historical evidence')
    wrapper, audit, prod, qa = map(read, [a.wrapper, a.catalogue_audit, a.production_manifest, a.qa_manifest])
    raw = Path(wrapper['client_review_output']['path'])
    actual = read(raw)
    require(wrapper['status'] == 'FAIL_CLIENT_REVIEW' and wrapper['exit_code'] == 0 and actual['phase'] == 8,
            'This partial audit requires actual normally exited phase8 failure')
    require(actual == wrapper['client_review_output']['result'] and sha(raw) == wrapper['client_review_output']['sha256'],
            'Actual raw bytes differ from the wrapper')
    require(actual['productionJarSha256'] == wrapper['artifact_sha256'] == prod['sha256'] ==
            audit['productionJarSha256'] == qa['productionJarSha256'], 'Exact current artifact binding differs')
    require(any(x['sha256'] == qa['sha256'] for x in wrapper['extra_mods']), 'Actual QA26 binding differs')
    require(any(x['sha256'] == prod['sha256'] for x in actual['productionArtifactOrigins']),
            'Actual loaded production code origin differs')
    canonical = {r['asset']: r for r in audit['rows'] if r['offeredCanonical']}
    require(len(canonical) == 69, 'Current offered canonical roster differs')
    primary = [r for r in actual['ordinaryRpCases'] if r['part'] == 'authored-first-part']
    extras = [r for r in actual['ordinaryRpCases'] if r['part'] != 'authored-first-part']
    require(len(primary) == 69 and {r['asset'] for r in primary} == set(canonical) and len(extras) == 4,
            'Completed canonical/working-part rows differ')
    completed = []
    for row in actual['ordinaryRpCases']:
        require(row['status'] == 'PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK', 'Unfinished ordinary RP row')
        removed = row['serverThreadRemovalObservation']
        require(removed['readThread'] == 'ACTUAL_SERVER_THREAD' and removed['present'] is False and
                removed['uuid'] == row['uuid'], 'RP removal witness missing')
        if row['part'] == 'authored-first-part':
            require(row['nativeBlockSurvivesRpAttack'] is True and row['nativeBlockOrdinaryCleanup'] is True,
                    'Primary RP native overlap/cleanup witness missing')
            check_pick(row['actualMiddlePick'], row['uuid'], row['asset'], row['asset'],
                       canonical[row['asset']]['source']['model']['path'])
        completed.append({'asset': row['asset'], 'part': row['part'], 'uuid': row['uuid'],
            'ordinaryCreativeRemovalActual': True,
            'nativeBlockRetainedThroughRpRemoval': row.get('nativeBlockSurvivesRpAttack', 'NOT_PROBED_IN_ADDITIONAL_PART_ROW'),
            'nativeBlockOrdinaryCleanup': row.get('nativeBlockOrdinaryCleanup', 'NOT_PROBED_IN_ADDITIONAL_PART_ROW'),
            'dropCount': 'NOT_RECORDED_BY_THIS_ROW'})
    architecture = []
    for row in actual['ordinaryArchitectureCases']:
        require(row['status'] == 'PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK' and
                row['serverThreadRemovalObservation']['air'] is True and
                row['serverThreadLedgerCleanup']['remainingOwnerContributions'] == 0, 'Architecture cleanup incomplete')
        architecture.append({'registry': row['registry'], 'uuid': row['uuid'], 'remainingOwnerContributions': 0})
    require(len(architecture) == 18, 'Architecture roster differs')
    old, mapping = actual['ordinaryAliasPickCases'], audit['existingAliasImplementation']['mapping']
    require(len(old) == 7 and {r['alias'] for r in old} == set(mapping) and len({r['uuid'] for r in old}) == 7,
            'Seven distinct real old-registry middle inputs missing')
    aliases = []
    for row in old:
        source, canonical_key = row['alias'], mapping[row['alias']]
        require(row['canonical'] == canonical_key and
                row['status'] == 'PASS_ACTUAL_OLD_REGISTRY_CLIENT_MIDDLE_KEY_CANONICAL_SETTINGS_AND_CREATIVE_CLEANUP',
                'Old-registry client input/cleanup failed')
        check_pick(row['actualMiddlePick'], row['uuid'], source, canonical_key, canonical[canonical_key]['source']['model']['path'])
        setup, cleanup = row['technicalSetupServerThread'], row['actualCleanupServerThread']
        require(setup['readThread'] == cleanup['readThread'] == 'ACTUAL_SERVER_THREAD' and
                setup['uuid'] == row['uuid'] and setup['stable'] == row['actualMiddlePick']['sourceStableTypedBefore'] and
                cleanup['nativeAfter'] == setup['nativeBefore'] and cleanup['present'] is False and
                cleanup['creativeDropCount'] == 0, 'Old-registry exact source/cleanup/native/drop witness differs')
        aliases.append({'alias': source, 'canonical': canonical_key, 'uuid': row['uuid'],
                        'actualMiddleKey': 'PASS', 'actualCreativeCleanup': True, 'creativeDrops': 0})
    placed = actual['actualPickedItemReinstallation']
    require(placed['status'] == 'PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP',
            'Actual picked-item ordinary reinstallation did not complete')
    source = next((r for r in old if r['uuid'] == placed['sourceOldUuid']), None)
    require(source is not None and placed['canonicalAsset'] == source['canonical'] and
            placed['actualPickedItem'] == source['actualMiddlePick']['pickedItem'] and
            placed['actualPickedTypedNbt'] == source['actualMiddlePick']['pickedTypedNbt'], 'Reinstallation did not use actual picked stack')
    new = placed['newUuid']
    require(valid_uuid(new) and new not in {r['uuid'] for r in old + primary}, 'Picked object copied a source UUID')
    server, cleanup = placed['actualServerThreadPlacement'], placed['actualCleanupServerThread']
    settings = compound(compound(placed['actualPickedTypedNbt'], 'picked stack')['bloodborne_rp_object'], 'picked settings')
    require(server['uuid'] == new and server['registry'] == 'bloodborne_rp:' + source['canonical'] and
            server['open'] == (settings['Open']['value'] == '1b') and
            server['locked'] == (settings['Locked']['value'] == '1b') and
            server['scale'] == float(str(settings['Scale']['value']).rstrip('fF')) and
            server['name'] == source['actualMiddlePick']['sourceStableTypedBefore'].get('CustomName', '') and
            server['linksEmpty'] is True and server['offset'] == 0, 'Actual new object copied identity/links/height or lost settings/name')
    require(cleanup['readThread'] == 'ACTUAL_SERVER_THREAD' and cleanup['present'] is False and
            cleanup['creativeDropCount'] == 0 and cleanup['nativeAfter'] == placed['nativeBefore'], 'Picked reinstallation cleanup differs')
    restored = actual['originalActorRestorationBeforeMenus']
    check_inventory_readback(restored, 'actual restored inventory before failed menu stage')
    restore_flags = [k for k in restored if k.endswith('Restored')]
    require(restore_flags and all(restored[k] is True for k in restore_flags) and
            actual['originalActorInventoryCameraRestoredBeforeMenus'] is True, 'Actor/inventory restoration did not complete')
    require(actual['preConstructionFixtureState'] == actual['postConstructionFixtureState'] and
            actual['temporaryConstructionFixtureImmutable'] is True, 'Permanent fixture state/graphs changed during temporary cases')
    gate = next(r for r in primary if r['asset'] == 'main_gate')
    cam = gate['actualSourcePartCameraCandidate']
    require(cam['eye'][1] > 110 and cam['sourceHitUuid'] == gate['uuid'] and
            gate['attackEyeToSourceOriginSquared'] > 64 and
            gate['serverThreadRemovalObservation']['present'] is False, 'Actual upper gate regression was not exercised')
    menus = actual['menusActual']
    require(menus['status'] == 'FAIL_ACTUAL_CLIENT_MENUS' and
            menus['failedStep'] == 'ordinary item packet opens BuilderScreen for lamp_A' and
            actual['screen'] == 'dev.dreamwalker.bloodbornedw.tool.BuilderScreen', 'Actual GUI failure differs')
    require(actual['loadingScreenWasClosedByQa'] is False and actual['nativeScreenNoneBeforeFirstEntrySnapshot'] is True,
            'Loading screen was forced closed or first-entry snapshot preceded native screen readiness')
    report = {'schema': 'dw-v10-full5att2-qualified-completed-subset-v1',
        'status': 'VERIFIED_ALL_ORDINARY_AND_MIDDLE_SUBSETS_WHOLE_CLIENT_FAILED_FIRST_MENU_CHECK',
        'productionJarSha256': prod['sha256'], 'qaJarSha256': qa['sha256'],
        'productionFreeze': {'path': str(a.production_manifest.resolve()), 'sha256': sha(a.production_manifest)},
        'qaFreeze': {'path': str(a.qa_manifest.resolve()), 'sha256': sha(a.qa_manifest)},
        'wrapper': str(a.wrapper.resolve()), 'wrapperSha256': sha(a.wrapper),
        'raw': str(raw), 'rawSha256': sha(raw), 'wholeWrapperStatus': wrapper['status'],
        'exitCode': wrapper['exit_code'], 'elapsedSeconds': wrapper['elapsed_seconds'],
        'actualIntegratedSaveLogPresent': wrapper['integrated_save_messages_present'],
        'completedOrdinaryRpCount': len(completed), 'canonicalOrdinaryCount': len(primary),
        'additionalWorkingPartCount': len(extras), 'completedOrdinaryRp': completed,
        'completedOrdinaryArchitectureCount': len(architecture), 'completedOrdinaryArchitecture': architecture,
        'actualCanonicalMiddleKeyCount': len(primary), 'actualOldRegistryMiddleKeyCount': len(aliases),
        'actualOldRegistryMiddleKeys': aliases,
        'actualPickedReinstallation': {'sourceOldUuid': source['uuid'], 'newUuid': new,
            'canonical': source['canonical'], 'settingsAndCustomNamePreserved': True,
            'sourceUuidLinksHeightNotCloned': True, 'ordinaryCleanupDrops': 0},
        'actualUpperMainGateRemoval': {'uuid': gate['uuid'], 'actorEye': cam['eye'],
            'actualHit': cam['actualRayHit'], 'eyeToOriginSquared': gate['attackEyeToSourceOriginSquared'],
            'actualServerUuidRemoved': True, 'nativeBlockRetainedAndOrdinaryCleanupCompleted': True,
            'scope': 'Actual ordinary attack-key, original upperpart pose, full mod profile. Live internal callback trace is not separately captured.'},
        'actorRestoredBeforeMenus': {'typedInventorySlots': restored['originalInventorySnapshot']['size'],
            'serverAndClientInventoryExact': True, 'flags': {k: restored[k] for k in restore_flags}},
        'permanentFixturesAndGraphsUnchangedThroughTemporaryCases': True,
        'loadedArchitectureProbe': actual['architectureV10'], 'initialIrisRuntime': actual['irisRuntime']['initial'],
        'nativeLoadingScreenReadiness': {'waitTicks': actual['loadingScreenWaitTicks'],
            'observedClasses': actual['observedLoadingScreenClasses'], 'forcedClose': False,
            'firstEntrySnapshotBeforeAnySetup': actual['firstEntrySnapshot']},
        'menuFailure': {'screen': actual['screen'], 'failedStep': menus['failedStep'],
            'completedPrepAndAimSteps': menus['steps'], 'actualGesture': menus['gestures'],
            'failure': menus['failure'], 'actualFullGuiFlow': 'FAIL_FIRST_OPEN_PREDICATE',
            'causalClassification': 'PENDING_PRECISE_READONLY_PREDICATE_AUDIT; do not assume paused GUI or production failure'},
        'finalDiagnosticOffOnOff': 'NOT_RUN', 'finalStrictShader42AndFrameCounter': 'NOT_RUN',
        'manualVisualAcceptance': 'NOT_RUN', 'wholeTaskAcceptance': 'PENDING',
        'limits': ['All claims here concern completed subsets only. Overall client result remains FAIL.',
            'First menu screen exists, but the expected predicate failed; a complete menu workflow is not proved.',
            'The true first-entry Creative/OP snapshot precedes any temporary preparation.',
            'Initial active Iris pipeline is not the interrupted final shader/options proof.',
            'No historical candidate8 runtime proof substitutes for actual candidate9 evidence.',
            'No Minecraft/build or production/QA edit was performed by this compact JSON audit.']}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'rp': len(completed), 'architecture': len(architecture),
                      'middleCanonical': len(primary), 'middleOld': len(aliases), 'output': str(a.output)}))


if __name__ == '__main__':
    main()
