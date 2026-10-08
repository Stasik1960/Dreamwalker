"""Prepare FULL4ATT3 only: same QA24/main8, explicit isolated focus-pause flag."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = 'tools/review_v10_candidate8_runtime_bound24.json'
OUTPUT = 'tools/review_v10_candidate8_runtime_bound24_full_retry3.json'


def main():
    output = ROOT / OUTPUT
    if output.exists(): raise ValueError('Refusing historical retry preparation overwrite')
    raw = (ROOT / BASE).read_bytes()
    value = json.loads(raw.decode('utf-8'))
    if not value['fullClientQaJarSha256'] or value['executionPerformed']:
        raise ValueError('Actual QA24/main8 freeze bindings required')
    before = value['commands']
    commands = {}
    for key, argv in before.items():
        key = key.replace('att2', 'att3')
        argv = [arg.replace('FULL_RELEASE4_ATTEMPT_2', 'FULL_RELEASE4_ATTEMPT_3')
                   .replace('full-release4-attempt2', 'full-release4-attempt3')
                   .replace('FULL4_ATT2', 'FULL4_ATT3') for arg in argv]
        if len(argv) > 1 and argv[1] == 'tools/run_final_client.py' and 'full_client' in argv:
            argv.append('--unattended-no-focus-pause')
        commands[key] = argv
    value['commands'] = commands
    value['schema'] = 'dw-v10-main8-server22-min23-full24-unattended-retry3-v1'
    value['status'] = 'PREPARED_CURRENT_MAIN8_SERVER22_MIN23_FULL24_ATT3_UNATTENDED_OPTION_ONLY_NOT_RUN'
    value['previousFull24Plan'] = {'path': BASE, 'sha256': hashlib.sha256(raw).hexdigest(), 'preservedExact': True}
    value['historicalFullAttempt2Failure'] = {'wrapper': 'reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_2.json', 'status': 'FAIL_CLIENT_REVIEW', 'phase': 0,
                                              'reason': 'Actor setup requires prior actual Esc; actual GameMenuScreen before any object construction', 'ordinaryRpOrGuiProof': 'NOT_RUN', 'substitutionForRetry': 'FORBIDDEN'}
    value['runEnvironmentOnlyChange'] = {'flag': '--unattended-no-focus-pause', 'default': 'OFF', 'scope': 'One generated isolated options key pauseOnLostFocus:false; original generated options snapshot retained/hashverified. No main/QA/mod/graphics/shader option edit.',
                                        'wrapperEvidenceField': 'unattended_focus_pause', 'generatedOptionsSnapshot': 'qa-inputs/options-before-unattended.txt',
                                        'syntheticEvidence': 'reports/V10_UNATTENDED_OPTIONS_SYNTHETIC_CHECK.json', 'actualNewRuntime': 'NOT_RUN'}
    value['qaArtifactRoleBindings'].pop('onlyNewFull4Att2', None)
    value['qaArtifactRoleBindings']['onlyNewFull4Att3'] = 'Same frozen QA24; only runner isolated unattended flag changes'
    value['requiredPriorRootFreeze'] = [line.replace('FULL4ATT2', 'FULL4ATT3') for line in value['requiredPriorRootFreeze']]
    value['sequence'] = ['Actual MIN16/REENTER4ATT1 main8/QA23 and three alias2 main8/QA22 normal PASS are preserved; never rerun from this full-only retry',
                         'Root separately launches only FULL4ATT3 with same frozen main8/QA24/strict shader marker4 and explicit isolated no-focus-pause flag',
                         'After actual full normal PASS/save, strict middle/roster/diagnostics and shader validators use fresh ATT3 output',
                         'Dedicated diagnostics4 still requires separate root GO after every client cost window/process closes',
                         'FULL4ATT1/ATT2 FAIL wrappers and original prepared plans remain unchanged history']
    output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': value['status'], 'path': str(output), 'fullQA': value['fullClientQaJarSha256'], 'executionPerformed': False}))


if __name__ == '__main__': main()
