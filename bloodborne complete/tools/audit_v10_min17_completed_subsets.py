"""Qualify MIN17's completed ordinary/menu/diagnostic stages without passing terminal FAIL."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
from verify_v10_middle_pick import check_pick, check_inventory_readback, valid_uuid, compound
from verify_v10_actual_client import check_diagnostics


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def require(value, message):
    if not value:
        raise RuntimeError(message)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--wrapper', type=Path, required=True)
    p.add_argument('--catalogue-audit', type=Path, required=True)
    p.add_argument('--mapping-jar', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    require(not a.output.exists(), 'Never overwrite prior evidence')
    w, audit = read(a.wrapper), read(a.catalogue_audit)
    raw = Path(w['client_review_output']['path'])
    r = read(raw)
    require(w['status'] == 'FAIL_CLIENT_REVIEW' and w['exit_code'] == 0 and w['integrated_save_messages_present'] is True,
            'Actual failed MIN17 normal-save-exit required')
    require(r == w['client_review_output']['result'] and sha(raw) == w['client_review_output']['sha256'], 'Actual wrapper/raw binding differs')
    require(r['phase'] == 4 and r['screen'] == 'net.minecraft.class_408' and r['failure'] == 'Actor setup requires prior actual Esc',
            'Actual late MIN17 failure differs')
    require(w['artifact_sha256'] == r['productionJarSha256'] == audit['productionJarSha256'], 'Exact current artifact differs')
    require(any(x['sha256'] == w['artifact_sha256'] for x in r['productionArtifactOrigins']), 'Actual loaded production origin differs')
    canonical = {x['asset']: x for x in audit['rows'] if x['offeredCanonical']}
    primary = [x for x in r['ordinaryRpCases'] if x['part'] == 'authored-first-part']
    require(len(canonical) == len(primary) == 69 and {x['asset'] for x in primary} == set(canonical) and
            len(r['ordinaryRpCases']) == 73 and len({x['uuid'] for x in r['ordinaryRpCases']}) == 73,
            'Exact canonical and additional source-part population differs')
    for x in r['ordinaryRpCases']:
        removal = x['serverThreadRemovalObservation']
        require(x['status'] == 'PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK' and
                removal['readThread'] == 'ACTUAL_SERVER_THREAD' and removal['present'] is False and removal['uuid'] == x['uuid'],
                'Unfinished ordinary source-part attack row')
        if x['part'] == 'authored-first-part':
            require(x['nativeBlockSurvivesRpAttack'] is True and x['nativeBlockOrdinaryCleanup'] is True and
                    x['nativeStableRpBefore'] == x['nativeStableRpAfter'], 'Primary native placement/source preservation missing')
            check_pick(x['actualMiddlePick'], x['uuid'], x['asset'], x['asset'], canonical[x['asset']]['source']['model']['path'])
    architecture = r['ordinaryArchitectureCases']
    require(len(architecture) == 18 and len({x['registry'] for x in architecture}) == 18, 'Exact architecture population differs')
    for x in architecture:
        require(x['status'] == 'PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK' and
                x['serverThreadRemovalObservation']['air'] is True and x['serverThreadLedgerCleanup']['remainingOwnerContributions'] == 0,
                'Architecture ordinary cleanup/residual-owner proof missing')
    old, aliases = r['ordinaryAliasPickCases'], audit['existingAliasImplementation']['mapping']
    require(len(old) == 7 and {x['alias'] for x in old} == set(aliases), 'Old registry source population differs')
    for x in old:
        target = aliases[x['alias']]
        require(x['canonical'] == target and x['status'] == 'PASS_ACTUAL_OLD_REGISTRY_CLIENT_MIDDLE_KEY_CANONICAL_SETTINGS_AND_CREATIVE_CLEANUP',
                'Old middle-key canonicalization or cleanup failed')
        check_pick(x['actualMiddlePick'], x['uuid'], x['alias'], target, canonical[target]['source']['model']['path'])
        before, after = x['technicalSetupServerThread'], x['actualCleanupServerThread']
        require(before['stable'] == x['actualMiddlePick']['sourceStableTypedBefore'] and before['nativeBefore'] == after['nativeAfter'] and
                before['readThread'] == after['readThread'] == 'ACTUAL_SERVER_THREAD' and after['present'] is False and after['creativeDropCount'] == 0,
                'Old source/cleanup/native preservation proof differs')
    reinstall = r['actualPickedItemReinstallation']
    source = next((x for x in old if x['uuid'] == reinstall['sourceOldUuid']), None)
    require(source is not None and reinstall['status'] == 'PASS_ACTUAL_MIDDLE_PICK_CANONICAL_STACK_ORDINARY_REPLACE_NEW_UUID_SETTINGS_AND_CLEANUP' and
            reinstall['actualPickedTypedNbt'] == source['actualMiddlePick']['pickedTypedNbt'] and
            reinstall['canonicalAsset'] == source['canonical'], 'Actual picked-stack ordinary reinstallation differs')
    new, cleanup = reinstall['actualServerThreadPlacement'], reinstall['actualCleanupServerThread']
    settings = compound(compound(reinstall['actualPickedTypedNbt'], 'actual picked stack')['bloodborne_rp_object'], 'actual picked settings')
    require(valid_uuid(reinstall['newUuid']) and reinstall['newUuid'] not in {x['uuid'] for x in old + primary} and
            new['uuid'] == reinstall['newUuid'] and new['registry'] == 'bloodborne_rp:' + source['canonical'] and
            new['locked'] == (settings['Locked']['value'] == '1b') and new['open'] == (settings['Open']['value'] == '1b') and
            new['scale'] == float(str(settings['Scale']['value']).rstrip('fF')) and
            new['name'] == source['actualMiddlePick']['sourceStableTypedBefore'].get('CustomName', '') and
            new['linksEmpty'] is True and new['offset'] == 0 and cleanup['present'] is False and cleanup['creativeDropCount'] == 0 and
            cleanup['readThread'] == 'ACTUAL_SERVER_THREAD' and cleanup['nativeAfter'] == reinstall['nativeBefore'], 'Picked object identity/settings/cleanup differs')
    restored = r['originalActorRestorationBeforeMenus']
    check_inventory_readback(restored, 'actual before-menu inventory')
    flags = {k:v for k,v in restored.items() if k.endswith('Restored')}
    require(flags and all(v is True for v in flags.values()) and r['originalActorInventoryCameraRestoredBeforeMenus'] is True and
            r['preConstructionFixtureState'] == r['postConstructionFixtureState'] and r['temporaryConstructionFixtureImmutable'] is True,
            'Pre-menu actor/inventory or permanent fixture preservation differs')
    menu = r['menusActual']
    require(menu['status'] == 'PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS' and menu['completedSteps'] == 237 and
            len(menu['steps']) == 237 and all(x['status'] == 'PASS' for x in menu['steps']) and
            menu['directMutationEndpointsUsedAsGuiProof'] is False, 'Actual full menu stage incomplete')
    diagnostics = check_diagnostics(w, r)
    with zipfile.ZipFile(a.mapping_jar) as z:
        mapping = z.read('mappings/mappings.tiny')
    mapping_row = next(x for x in mapping.decode('utf-8').splitlines() if x.startswith('c\tnet/minecraft/class_408\t'))
    require(mapping_row == 'c\tnet/minecraft/class_408\tnet/minecraft/client/gui/screen/ChatScreen', 'Actual current ChatScreen mapping differs')
    out = {'schema': 'dw-v10-min17-qualified-completed-subsets-v1',
        'status': 'VERIFIED_ORDINARY_MIDDLE_GUI_AND_DIAGNOSTIC_SUBSETS_WHOLE_MIN17_FAILED_LATE_CHATSCREEN_TEARDOWN',
        'productionJarSha256': w['artifact_sha256'], 'qaArtifacts': w['extra_mods'],
        'wrapper': str(a.wrapper.resolve()), 'wrapperSha256': sha(a.wrapper), 'raw': str(raw), 'rawSha256': sha(raw),
        'wholeWrapperStatus': w['status'], 'exitCode': w['exit_code'], 'elapsedSeconds': w['elapsed_seconds'],
        'actualIntegratedSaveLogPresent': True,
        'completed': {'canonicalRp': 69, 'additionalWorkingParts': 4, 'ordinaryArchitecture': 18,
            'canonicalActualMiddleKeys': 69, 'oldRegistryActualMiddleKeys': 7,
            'pickedItemOrdinaryReinstallationNewUuid': reinstall['newUuid'], 'pickedItemCleanupDrops': 0,
            'typedInventorySlotsRestoredBeforeMenus': restored['originalInventorySnapshot']['size'],
            'preMenuActorRestoration': flags, 'permanentFixtureStateAndGraphsUnchangedThroughTemporaryCases': True,
            'actualMenusSteps': 237},
        'diagnosticsActualCompletedSubset': diagnostics,
        'failure': {'phase': r['phase'], 'reason': r['failure'], 'actualScreen': r['screen'],
            'mappedScreen': 'net.minecraft.client.gui.screen.ChatScreen', 'primaryMappingRow': mapping_row,
            'mappingJar': str(a.mapping_jar.resolve()), 'mappingJarSha256': sha(a.mapping_jar),
            'nextActorSetupOrFinalCleanup': 'FAIL_NOT_COMPLETED',
            'causalClassification': 'READONLY_SOURCE_DIAGNOSIS_OWNED_RP_AGENT; no GUI-pause or production-bug inference'},
        'finalInventoryAtExit': 'NOT_OBSERVED: before-menu typed restoration passed, but final teardown did not finish.',
        'distinctReenter': 'NOT_RUN', 'strictShader': 'NOT_APPLICABLE_MINIMAL_PROFILE',
        'manualReview': 'PENDING', 'wholeTaskAcceptance': 'PENDING',
        'limits': ['Completed diagnostics include actual matched OFF/ON/OFF, ACK bindings, sent/delivered batch equality and bounded retained/drop reconciliation.',
            'Successful subsets do not change the whole MIN17 FAIL or satisfy a normal terminal client acceptance gate.',
            'No historical candidate8 runtime proof is substituted for this exact candidate9 run.',
            'No Minecraft/build/production/QA mutation occurred in this audit.']}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': out['status'], 'output': str(a.output)}))


if __name__ == '__main__':
    main()
