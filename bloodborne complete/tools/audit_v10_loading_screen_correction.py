"""Append a source-grounded correction; preserve prior failed wrappers and metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--mapping-jar', type=Path, required=True)
    ap.add_argument('--wrapper', type=Path, action='append', required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    if args.output.exists():
        raise RuntimeError('Refusing to overwrite previous correction/evidence')
    with zipfile.ZipFile(args.mapping_jar) as z:
        data = z.read('mappings/mappings.tiny')
    mapping = {}
    source_rows = []
    for line in data.decode('utf-8').splitlines():
        fields = line.split('\t')
        if len(fields) == 3 and fields[0] == 'c' and fields[1] in {'net/minecraft/class_433', 'net/minecraft/class_434'}:
            mapping[fields[1]] = fields[2]
            source_rows.append(line)
    if mapping != {'net/minecraft/class_433': 'net/minecraft/client/gui/screen/GameMenuScreen',
                   'net/minecraft/class_434': 'net/minecraft/client/gui/screen/DownloadingTerrainScreen'}:
        raise RuntimeError('Exact current 1.20.1 mapping differs')
    attempts = []
    for path in args.wrapper:
        wrapper = json.loads(path.read_text(encoding='utf-8'))
        raw_path = Path(wrapper['client_review_output']['path'])
        raw = json.loads(raw_path.read_text(encoding='utf-8'))
        if not (wrapper['status'] == 'FAIL_CLIENT_REVIEW' and wrapper['exit_code'] == 0 and
                raw['phase'] == 0 and raw['failure'] == 'Actor setup requires prior actual Esc' and
                raw['screen'] == 'net.minecraft.class_434'):
            raise RuntimeError('Correction input must be the actual phase0 loading-screen failure')
        if raw != wrapper['client_review_output']['result'] or sha(raw_path) != wrapper['client_review_output']['sha256']:
            raise RuntimeError('Actual wrapper/raw binding differs')
        options = Path(wrapper['run_directory']) / 'options.txt'
        focus_rows = [line for line in options.read_text(encoding='utf-8').splitlines() if line.startswith('pauseOnLostFocus:')]
        if len(focus_rows) > 1:
            raise RuntimeError('Ambiguous actual focus option')
        focus = wrapper.get('unattended_focus_pause')
        attempts.append({'wrapper': str(path.resolve()), 'wrapperSha256': sha(path),
            'productionJarSha256': wrapper['artifact_sha256'], 'status': wrapper['status'],
            'normalExitCode': wrapper['exit_code'], 'actualIntegratedSaveLogPresent': wrapper['integrated_save_messages_present'],
            'raw': str(raw_path), 'rawSha256': sha(raw_path), 'failedPhase': raw['phase'], 'reason': raw['failure'],
            'observedScreenIntermediary': raw['screen'], 'correctMappedScreen': mapping[raw['screen'].replace('.', '/')],
            'priorGameMenuScreenInterpretation': 'INCORRECT',
            'actualDerivedOptionsPath': str(options), 'actualDerivedOptionsSha256': sha(options),
            'actualPauseOnLostFocus': focus_rows[0].split(':', 1)[1] if focus_rows else 'ABSENT_IN_SAVED_OPTIONS',
            'unattendedFlagApplied': bool(focus and focus['enabled']),
            'focusOptionProvenance': focus if focus else 'No unattended flag applied; generated isolated options only.',
            'firstEntryBeforeAnySetup': raw['firstEntrySnapshot'],
            'completedOrdinaryRpCount': len(raw.get('ordinaryRpCases', [])),
            'completedOrdinaryArchitectureCount': len(raw.get('ordinaryArchitectureCases', [])),
            'architectureProbe': 'NOT_RUN_AFTER_PHASE0_FAILURE', 'menus': 'NOT_RUN', 'diagnosticsOffOnOff': 'NOT_RUN',
            'mainMixinBootFailure': 'NOT_OBSERVED: scene/player loaded and failure was QA screen readiness before object operations'})
    out = {'schema': 'dw-v10-loading-screen-causal-correction-v1',
        'status': 'CORRECTED_PRIMARY_CLASS_MAPPING_PRIOR_FOCUS_CAUSAL_INFERENCE_UNSUPPORTED',
        'mappingJar': str(args.mapping_jar.resolve()), 'mappingJarSha256': sha(args.mapping_jar),
        'mappingEntry': 'mappings/mappings.tiny', 'mappingEntrySha256': hashlib.sha256(data).hexdigest(),
        'exactMappingRows': source_rows, 'attempts': attempts,
        'correction': 'class_434 is DownloadingTerrainScreen. class_433 is GameMenuScreen. The earlier interpretation of class_434 as focus-pause GameMenuScreen was wrong.',
        'causalScope': 'A later attempt with pauseOnLostFocus=false still failed on class_434. Therefore the focus flag cannot be credited as the cause of earlier phase0 progress. It remains an explicit isolated unattended option with source settings preserved.',
        'nextQaOnlyFix': 'Wait for DownloadingTerrainScreen to close through native readiness before actor setup; keep initial Creative/OP snapshot before ANY setup.',
        'historicalBytesOverwritten': False, 'productionOrQaEdited': False, 'minecraftOrBuildStarted': False,
        'manualVisualAcceptance': 'NOT_RUN', 'wholeTaskAcceptance': 'PENDING'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': out['status'], 'attempts': len(attempts), 'output': str(args.output)}))


if __name__ == '__main__':
    main()
