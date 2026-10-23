"""Freeze only artifact paths before the still-unstarted functional continuation."""
import json
from pathlib import Path
from runtime_patch_receipt import read, require, sha, validate

ROOT = Path(__file__).resolve().parents[3]
HERE = ROOT / 'research/function-qa'
prior_path = HERE / 'candidate-terminal-bridge-plan.json'
require(sha(prior_path) == 'ba64f73374727ae3ba04e3455189eeab019e9d2ee78251b4737b26d63812716d', 'Frozen authoring plan changed')
plan = read(prior_path)
require(not Path(plan['output']).exists(), 'An executed plan must never be silently rebound')
patch_path = HERE / 'bridge-terminal-runtime-patch-immutable.json'
patch = read(patch_path)
validate(patch_path, patch['after']['path'])
require(patch['after']['sha256'] == plan['productionSha256'], 'Packaging rebind may not change runtime bytes')
previous = {'path': str(prior_path), 'sha256': sha(prior_path)}
plan['productionJar'] = patch['after']['path']
plan['runtimePatch'] = {'path': str(patch_path), 'sha256': sha(patch_path)}
plan['continuation']['executionArtifactRebind'] = {'previousPlan': previous,
    'reason': 'Freeze the identical 99f4 JAR at an immutable archive path before the future FULL build replaces build/libs; no gameplay run has started'}
output = HERE / 'candidate-terminal-bridge-frozen-plan.json'
require(not output.exists(), 'Preserve existing execution plan')
output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
receipt = {'status': 'ARTIFACT_PATHS_FROZEN_NOT_GAMEPLAY', 'previousPlan': previous,
    'executionPlan': {'path': str(output), 'sha256': sha(output)},
    'production': patch['after'], 'runtimePatch': plan['runtimePatch'],
    'sameRuntimeBytes': True, 'sameFreshOutput': plan['output'], 'mapWrites': 0, 'jvmStarts': 0}
receipt_path = HERE / 'candidate-terminal-bridge-artifact-freeze.json'
require(not receipt_path.exists(), 'Preserve existing artifact freeze receipt')
receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(receipt))
