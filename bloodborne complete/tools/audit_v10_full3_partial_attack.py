"""Qualify completed FULL4ATT3 rows without treating the failed run as acceptance."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from verify_v10_middle_pick import check_pick


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def require(value, reason):
    if not value:
        raise RuntimeError(reason)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--wrapper', required=True, type=Path)
    ap.add_argument('--catalogue-audit', required=True, type=Path)
    ap.add_argument('--bytecode-manifest', required=True, type=Path)
    ap.add_argument('--output', required=True, type=Path)
    a = ap.parse_args()
    require(not a.output.exists(), 'Never overwrite previous evidence')
    wrapper = read(a.wrapper)
    raw = Path(wrapper['client_review_output']['path'])
    actual = read(raw)
    catalogue = read(a.catalogue_audit)
    require(wrapper['status'] == 'FAIL_CLIENT_REVIEW' and wrapper['exit_code'] == 0,
            'This audit is scoped to the normally exited failed FULL3 run')
    require(sha(raw) == wrapper['client_review_output']['sha256'] and actual == wrapper['client_review_output']['result'],
            'Wrapper/raw bytes disagree')
    require(wrapper['artifact_sha256'] == actual['productionJarSha256'] == catalogue['productionJarSha256'],
            'Actual current artifact differs')
    require(actual['phase'] == 2 and actual['failedStep'] == 'ordinary Creative attack packet main_gate',
            'Actual failed phase/step differs')
    models = {r['asset']: r['source']['model']['path'] for r in catalogue['rows'] if r['offeredCanonical']}
    completed = []
    for r in actual['ordinaryRpCases']:
        require(r['status'] == 'PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK', 'Unfinished RP row in completed list')
        removed = r['serverThreadRemovalObservation']
        require(removed['present'] is False and removed['uuid'] == r['uuid'] and removed['readThread'] == 'ACTUAL_SERVER_THREAD',
                'Completed RP row lacks actual exact-UUID removal')
        require(r['nativeBlockSurvivesRpAttack'] is True and r['nativeBlockOrdinaryCleanup'] is True,
                'Completed RP row lacks native retention/cleanup')
        check_pick(r['actualMiddlePick'], r['uuid'], r['asset'], r['asset'], models[r['asset']])
        completed.append({'asset': r['asset'], 'uuid': r['uuid'], 'actualMiddlePick': 'PASS',
                          'actualServerUuidRemoved': True, 'nativeBlockSurvivesRpAttack': True,
                          'nativeBlockOrdinaryCleanup': True, 'itemDropCount': 'NOT_RECORDED_BY_THIS_ROW'})
    architecture = []
    for r in actual['ordinaryArchitectureCases']:
        require(r['status'] == 'PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK', 'Unfinished architecture row')
        require(r['serverThreadRemovalObservation']['air'] is True and
                r['serverThreadLedgerCleanup']['remainingOwnerContributions'] == 0, 'Residual architecture owner')
        architecture.append({'registry': r['registry'], 'uuid': r['uuid'], 'actualOrdinaryRemoval': True,
                             'remainingOwnerContributions': 0})
    gate = actual['currentCaseAttempt']
    require(gate['asset'] == 'main_gate' and 'status' not in gate and 'serverThreadRemovalObservation' not in gate,
            'Do not count failed main_gate as completed cleanup')
    check_pick(gate['actualMiddlePick'], gate['uuid'], 'main_gate', 'main_gate', models['main_gate'])
    hit = actual['actualCrosshair']
    require(hit['type'] == 'ENTITY' and hit['entityUuid'] == gate['uuid'], 'Failure ray selected another target')
    latest = actual['lastActualServerThreadReadback']
    require(latest['readThread'] == 'ACTUAL_SERVER_THREAD' and latest['present'] is True and latest['uuid'] == gate['uuid'],
            'Expected unchanged failed target absent from actual server readback')
    require(latest['stable'] == gate['nativeStableRpBefore'] == gate['nativeStableRpAfter'],
            'Failed target identity/settings changed')
    camera = gate['actualSourcePartCameraCandidate']
    origin = [latest['x'], latest['y'], latest['z']]
    eye_origin_sq = sum((x-y)**2 for x,y in zip(camera['eye'], origin))
    feet_origin_sq = sum((x-y)**2 for x,y in zip(camera['feet'], origin))
    require(math.isclose(eye_origin_sq, gate['attackEyeToSourceOriginSquared'], abs_tol=1e-9), 'Distance witness differs')
    iris = actual['irisRuntime']['initial']
    report = {
        'schema': 'dw-v10-full3-qualified-partial-attack-audit-v1',
        'status': 'VERIFIED_COMPLETED_SUBSET_ONLY_WHOLE_FULL_CLIENT_FAILED_AT_MAIN_GATE_ATTACK',
        'productionJarSha256': actual['productionJarSha256'],
        'wrapper': str(a.wrapper.resolve()), 'wrapperSha256': sha(a.wrapper),
        'raw': str(raw), 'rawSha256': sha(raw), 'wholeWrapperStatus': wrapper['status'],
        'exitCode': wrapper['exit_code'], 'elapsedSeconds': wrapper['elapsed_seconds'],
        'actualIntegratedSaveLogPresent': wrapper['integrated_save_messages_present'],
        'completedOrdinaryRpCount': len(completed), 'completedOrdinaryRp': completed,
        'completedOrdinaryArchitectureCount': len(architecture), 'completedOrdinaryArchitecture': architecture,
        'completedCanonicalMiddleKeyCount': len(completed) + 1,
        'failedMainGate': {'asset': 'main_gate', 'uuid': gate['uuid'], 'actualMiddlePick': 'PASS',
            'actualRay': hit, 'actorEye': camera['eye'], 'actorFeet': camera['feet'], 'sourceOrigin': origin,
            'eyeToOriginSquared': eye_origin_sq, 'feetToOriginSquared': feet_origin_sq,
            'actualTargetServerPresent': True, 'stableTypedSourceSettingsUnchanged': True,
            'actualNativeBlockUnchanged': latest['nativeRegistry'] == 'minecraft:stone',
            'ordinaryAttackKeyRequested': True, 'specificOutgoingPacketDelivery': 'NOT_OBSERVED',
            'actualRemovalOrDropProof': 'FAIL_TIMEOUT_NOT_COMPLETED'},
        'loadedArchitectureProbe': actual['architectureV10'],
        'initialIrisObservation': iris,
        'finalStrictShader42AndFrameCounter': 'NOT_RUN_AFTER_PHASE2_FAILURE',
        'actualMenus237': 'NOT_RUN', 'finalDiagnosticOffOnOff': 'NOT_RUN',
        'bytecodeManifest': str(a.bytecode_manifest.resolve()), 'bytecodeManifestSha256': sha(a.bytecode_manifest),
        'staticClientRouting': [
            'Actual runtime class_310.method_1536 ENTITY branch invokes class_636.method_2918.',
            'Actual runtime class_636.method_2918 sends class_2824.attack C2S before local player.attack.',
            'Immersive Portals wrapStartAttack uses original operation outside portal pointing; its inspected game-mode packet rewriting targets block action/use, not entity attack.',
            'BetterCombat pre_doAttack cancels only for nonnull WeaponAttributes with attacks; held placer live registry attributes were not captured.',
            'Inspected Pehkui and reach-entity-attributes client game-mode hooks modify block reach getter, not entity attack packet send.',
            'Own BuilderInput/BuilderAttackGuard cancel only BuildingTool; actual held main_gate_placer is not BuildingTool.'
        ],
        'limits': ['This is not whole-client acceptance or manual visual review.',
                   'No live packet delivery/callback-entry probe was present; static bytecode conditions are distinguished from actual execution.',
                   'Rows lack explicit creative drop counts, so zero ItemEntity drops are not inferred.',
                   'Initial Iris pipeline observation does not replace the interrupted final strict shader/settings check.',
                   'No production/QA mutation, Minecraft launch, shader/mod removal or camera workaround was performed by this audit.']}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'completedRp': len(completed), 'middle': len(completed)+1,
                      'architecture': len(architecture), 'output': str(a.output)}))


if __name__ == '__main__':
    main()
