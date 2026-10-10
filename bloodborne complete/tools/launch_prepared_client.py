"""Launch one already prepared isolated ordinary client after explicit user approval."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepared-report', required=True, type=Path)
    parser.add_argument('--launch-report', required=True, type=Path)
    args = parser.parse_args()
    report = json.loads(args.prepared_report.read_text(encoding='utf8'))
    run = Path(report['run_directory']).resolve()
    if run.parent != ROOT / 'build' or not run.name.startswith('runtime-client-') or report['status'] != 'PREPARED':
        raise ValueError('Only a prepared isolated client is accepted')
    if args.launch_report.exists() or (run / 'launch-console.log').exists():
        raise ValueError('Do not overwrite a prior launch record')
    jar = Path(report['artifact'])
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != report['artifact_sha256'] or report.get('extra_mods') or report.get('qa_input'):
        raise ValueError('Exact ordinary JAR without QA is required')
    command = report['command']
    if Path(command[0]).name.lower() != 'java.exe' or '--gameDir' not in command or Path(command[command.index('--gameDir')+1]).resolve() != run:
        raise ValueError('Prepared command must launch Java in its own isolated game directory')
    version = subprocess.run([command[0], '-version'], capture_output=True, text=True, check=True).stderr
    if 'version "17.' not in version:
        raise ValueError('Actual JDK17 is required')
    with (run / 'launch-console.log').open('w', encoding='utf8') as console:
        process = subprocess.Popen(command, cwd=run, stdin=subprocess.DEVNULL, stdout=console, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    result = {'schemaVersion':1, 'status':'LAUNCHED_NOT_CLIENT_VALIDATED', 'pid':process.pid,
              'run_directory':str(run), 'prepared_report':str(args.prepared_report.resolve()),
              'artifact_sha256':digest, 'clientVisuals':'REQUIRES_REAL_OBSERVATION', 'qaRequired':False}
    args.launch_report.parent.mkdir(parents=True, exist_ok=True)
    args.launch_report.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
