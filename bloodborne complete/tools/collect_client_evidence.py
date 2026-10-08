"""Collect bounded startup evidence; never promote log markers to visual acceptance."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--process-ended', action='store_true')
    parser.add_argument('--baseline-rp', action='store_true', help='Original standalone RP baseline; architecture is absent by design')
    parser.add_argument('--termination', choices=['forced-after-observation','normal','still-running'], default='still-running')
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding='utf-8'))
    run = Path(report['run_directory'])
    log = run / 'logs/latest.log'
    lines = log.read_text(encoding='utf-8', errors='replace').splitlines()
    installed = run / 'mods' / Path(report['artifact']).name
    assert digest(installed) == report['artifact_sha256'], 'Installed JAR differs from recorded artifact'
    markers = {
        'loader': 'Loading Minecraft 1.20.1 with Fabric Loader ' + report['loader'],
        'architecture_init': 'Dreamwalker BB source-backed prototype checkpoint',
        'rp_init': 'Bloodborne RP initialized:',
        'resource_reload': 'Reloading ResourceManager:',
        'audio_init': 'OpenAL initialized',
        'integrated_server': 'Starting integrated minecraft server version 1.20.1',
        'qa_player_entered': 'DreamwalkerQA[local:',
        'advancements': 'Loaded 2 advancements',
    }
    observations = {}
    for key, text in markers.items():
        hits = [{'line': n + 1, 'text': line[:2000]} for n, line in enumerate(lines) if text in line]
        observations[key] = {'status': 'OBSERVED' if hits else 'NOT_OBSERVED', 'count': len(hits), 'samples': hits[:2]}
    errors = [(n + 1, line) for n, line in enumerate(lines) if re.search(r'/ERROR\]', line)]
    groups = Counter()
    for _, line in errors:
        message = line.split(']: ', 1)[-1]
        if 'Found a broken recipe, failed to setRecipe' in message: key = 'JEI recipe integration error'
        elif 'Failed to load model' in message or 'Unable to load model' in message: key = 'Model load error'
        elif 'Failed to fetch user properties' in message: key = 'Offline QA authentication fetch error'
        else: key = message[:180]
        groups[key] += 1
    archive = ROOT / 'reports' / (args.report.stem + '_LOGS.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as output:
        for path in [log, run/'launch-console.log', run/'launch-command.json']:
            if path.exists(): output.write(path, path.name)
    required = ['loader','rp_init','resource_reload','audio_init'] + ([] if args.baseline_rp else ['architecture_init'])
    core = all(observations[key]['status'] == 'OBSERVED' for key in required)
    world = observations['integrated_server']['status'] == 'OBSERVED' and observations['qa_player_entered']['status'] == 'OBSERVED'
    report.update({
        'status': 'STARTUP_AND_SCENE_ENTRY_OBSERVED' if core and world else 'RESOURCE_STARTUP_OBSERVED' if core else 'INCOMPLETE_STARTUP_EVIDENCE',
        'observations': observations,
        'comparison_role': 'ORIGINAL_STANDALONE_RP_BASELINE' if args.baseline_rp else 'COMBINED_PROTOTYPE',
        'process_ended': args.process_ended,
        'termination': args.termination,
        'normal_world_save_verified': False,
        'gameplay': 'NOT_RUN', 'client_visuals': 'NOT_RUN', 'two_clients': 'NOT_RUN',
        'shader_comparison': 'NOT_RUN', 'city_performance': 'NOT_RUN',
        'full_modpack_compatibility': 'NOT_ACCEPTED_ERRORS_RECORDED' if errors else 'NOT_ACCEPTED_STARTUP_ONLY',
        'log_sha256': digest(log),
        'error_line_count': len(errors), 'error_groups': dict(groups),
        'error_samples': [{'line': n, 'text': line[:2000]} for n,line in errors[:12]],
        'log_archive': {'path': str(archive), 'sha256': digest(archive)},
        'limitations': [
            'Log markers prove initialization and optional local scene entry, not a inspected frame or user action.',
            'The user modset is selected from supplied duplicate versions; original configs and shaders are not copied.',
            'Forced process termination does not prove normal save/restart; a normally stopped dedicated server supplies the review scene.',
            'Windows Graphics Capture twice timed out; manual visual and Creative gameplay remain NOT_RUN.'
        ]
    })
    args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'report': str(args.report), 'status': report['status'], 'errors': len(errors), 'visual': 'NOT_RUN'}))

if __name__ == '__main__': main()
