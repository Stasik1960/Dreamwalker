"""Rebind only pilot metadata after real affected QA and an exact adapter delta."""
import json, os, shutil
from pathlib import Path
from runtime_patch_receipt import read, require, sha, validate

ROOT = Path(__file__).resolve().parents[3]
FOLDER = ROOT / 'research/function-qa/candidate-terminal-bridge'
report_path = FOLDER / 'functions.json'
launch_path = FOLDER / 'launch-receipt.json'
binding = read(FOLDER / 'binding.json')
report, launch = read(report_path), read(launch_path)
patch_path = Path(binding['runtimePatch']['path'])
require(sha(patch_path) == binding['runtimePatch']['sha256'], 'Runtime patch evidence changed')
patch = read(patch_path)
validate(patch_path, patch['after']['path'])
require(report['status'] == launch['status'] == 'PASS' and launch['exitCode'] == 0,
        'Actual affected functional run must pass with normal process exit')
require(report['standRestored'] and report['normalServerThreadFinished'], 'Stand restoration/native server completion unproved')
require(report['productionSha256'] == launch['productionSha256'] == binding['productionSha256'] == patch['after']['sha256'], 'Functional runtime binding differs')
require(report['planSha256'] == binding['planSha256'] and launch['helperSha256'] == binding['helperSha256'], 'Functional input/helper binding differs')
require(binding['targetProfileUsed'] and not binding['authoringReferenceOnly'] and binding['heap'] == '4G', 'Use the actual pinned target server profile')
require(all(row['pass'] for row in report['checks']), 'An actual current check failed')
for key, count in [('inheritedSuccessfulChecks', 83), ('inheritedAirDoorChecks', 5), ('inheritedBridgeDoorChecks', 17)]:
    require(len(report[key]) == count and all(row['pass'] for row in report[key]), 'An exact inherited functional group is missing: ' + key)
require(report['ownedFixtureRecovery']['preconditionsExact'] and report['ownedFixtureRecovery']['standRestored'] and report['ownedFixtureRecovery']['removalResult']['outcome'] == 'COMMITTED', 'Owned fixture recovery did not complete')
require(report['terminalBridgeRollbackProbe']['restoredExactly'] and report['terminalBridgeRollbackProbe']['result']['outcome'] == 'ROLLED_BACK', 'Actual terminal-write rollback was not proved')
require({row['kind'] for row in report['actions'] if row['status'] == 'PASS'} >= {'door', 'tree', 'statue'}, 'Remaining whole-object gameplay actions are incomplete')
parts = report['representativeAssemblyParts']
require(parts and all(row['pickedSameMainItem'] for row in parts) and report['representativeAccessiblePartsBroken'] > 0, 'Representative one-item/per-part checks are incomplete')
require(all(not row['accessible'] or row.get('wholeRemoved') is True for row in parts), 'An accessible assembly part did not remove the whole object')
console = (FOLDER / 'server-console.log').read_text(encoding='utf-8', errors='replace')
require('All dimensions are saved' in console and not any(value in console for value in ('OutOfMemoryError', 'Failed to save', 'Exception stopping the server')), 'Native save or process health failed')

world = Path(binding['world']).resolve()
require(world == (ROOT / 'research/pilot/candidate').resolve(), 'Only the one disposable pilot metadata may be rebound')
marker_path = world / 'NATIVE-GRID-MIGRATION.json'
marker = read(marker_path)
require(not (world / 'NATIVE-GRID-INCOMPLETE.json').exists() and marker['status'] == 'READY_FOR_GAMEPLAY_QA', 'Pilot migration/light state is incomplete')
require(marker['runtime']['sha256'] == patch['before']['sha256'] and marker['catalog']['sha256'] == patch['catalogSha256'], 'Pilot marker belongs to another runtime/catalogue')
light_path = Path(marker['lightReceipt'])
require(sha(light_path) == marker['lightReceiptSha256'], 'Completed native-light evidence changed')
light = read(light_path)
require(light['status'] == 'PASS' and light['pending'] == 0 and light['saveFlushed'] and light['catalogSha256'] == patch['catalogSha256'] and light['runtimeSha256'] == patch['before']['sha256'], 'Unchanged-light compatibility is not proved')
archive = FOLDER / 'migration-marker-before-runtime-patch.json'
require(not archive.exists(), 'Preserve the original migration marker archive')
shutil.copy2(marker_path, archive)
evidence = {'runtimePatch': binding['runtimePatch'], 'affectedFunctions': {'path': str(report_path), 'sha256': sha(report_path)},
    'launchReceipt': {'path': str(launch_path), 'sha256': sha(launch_path)},
    'previousMarker': {'path': str(archive), 'sha256': sha(archive)},
    'resourcesAndOpticsUnchanged': True, 'nativeLightReusedWithoutRecalculation': True}
marker['runtime'] = patch['after']
marker['runtimeCompatibility'] = evidence
temporary = marker_path.with_name(marker_path.name + '.writing')
with temporary.open('w', encoding='utf-8') as stream:
    stream.write(json.dumps(marker, ensure_ascii=False, indent=2) + '\n'); stream.flush(); os.fsync(stream.fileno())
os.replace(temporary, marker_path)
receipt = {'status': 'AFFECTED_QA_PASSED_RUNTIME_REBOUND_NOT_FINAL_READY', 'runtime': patch['after'],
    'evidence': evidence, 'marker': {'path': str(marker_path), 'sha256': sha(marker_path)},
    'worldTerrainWrites': 0, 'metadataFilesWritten': 1, 'benchmarkPairStillRequired': True}
out = FOLDER / 'runtime-compatibility-accepted.json'
require(not out.exists(), 'Preserve existing compatibility receipt')
out.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(receipt))
