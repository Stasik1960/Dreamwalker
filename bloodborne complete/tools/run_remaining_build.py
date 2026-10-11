"""Build combined production/source JARs and run focused plus V12 regressions.

Launch only through CodexControl/Guard.ps1 -Mode Operation. Worlds are disposable
GameTest worlds below this project's build folder; no map/gallery task is used.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path.home() / 'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-name', required=True)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--skip-client-qa', action='store_true')
    parser.add_argument('--qa-only', action='store_true')
    args = parser.parse_args()
    if not args.run_name.replace('-', '').replace('_', '').isalnum():
        raise SystemExit('Unsafe run name')
    output = ROOT / 'build/remaining-transfer' / args.run_name
    output.mkdir(parents=True, exist_ok=False)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(JAVA)
    env['PATH'] = str(JAVA / 'bin') + os.pathsep + env['PATH']
    command = [str(ROOT / 'gradlew.bat'), '--no-daemon', '-Dorg.gradle.java.home=' + str(JAVA),
               '-Pgametest_run_name=' + args.run_name]
    if not args.skip_client_qa:
        command.extend(['-I', 'tools/remaining-qa.init.gradle'])
    if args.qa_only and args.skip_client_qa:
        raise ValueError('QA-only requires the explicit QA source set')
    if not args.qa_only:
        command.extend(['build', 'compileGametestJava'])
    if not args.skip_client_qa:
        command.append('remapRemainingQaJar')
    if not args.build_only and not args.qa_only:
        command.append('runGametest')
    with (output / 'gradle.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    data = {'command': command, 'exitCode': result.returncode,
            'status': 'PASS' if result.returncode == 0 else 'FAIL',
            'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'log': str(output / 'gradle.log'), 'javaHome': str(JAVA),
            'gameTests': 'NOT_RUN' if args.build_only or args.qa_only else 'SEE_XML',
            'worldScope': 'Fresh GameTest world only; source map and gallery untouched'}
    (output / 'result.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
