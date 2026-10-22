"""Launch one prepared isolated client, optionally with the existing camera capture add-on."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepared-report', required=True, type=Path)
    parser.add_argument('--launch-report', required=True, type=Path)
    parser.add_argument('--wait', action='store_true', help='Retain the launched native process exit/save receipt')
    parser.add_argument('--timeout', type=int, default=1800)
    args = parser.parse_args()
    if not 1<=args.timeout<=7200:
        raise ValueError('Bounded native wait required')
    report = json.loads(args.prepared_report.read_text(encoding='utf8'))
    run = Path(report['run_directory']).resolve()
    if run.parent != ROOT / 'build' or not run.name.startswith('runtime-client-') or report['status'] != 'PREPARED':
        raise ValueError('Only a prepared isolated client is accepted')
    if args.launch_report.exists() or (run / 'launch-console.log').exists():
        raise ValueError('Do not overwrite a prior launch record')
    jar = Path(report['artifact'])
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != report['artifact_sha256'] or report.get('qa_input'):
        raise ValueError('Exact prepared production JAR is required')
    gallery=report.get('gallery_capture')
    extras=report.get('extra_mods',[])
    if extras:
        if not gallery or len(extras)!=1:
            raise ValueError('Only the single existing gallery capture add-on is supported')
        extra=extras[0];installed=run/'mods'/extra['copied_name']
        if hashlib.sha256(installed.read_bytes()).hexdigest()!=extra['sha256']:
            raise ValueError('Prepared gallery add-on changed')
        with zipfile.ZipFile(installed) as archive:
            metadata=json.loads(archive.read('fabric.mod.json'))
        if metadata.get('id')!='dreamwalker_gallery_author':
            raise ValueError('Unexpected capture add-on')
    if gallery:
        marker=run/'client-capture-input.json'
        if not extras or hashlib.sha256(marker.read_bytes()).hexdigest()!=gallery['sha256']:
            raise ValueError('Prepared capture marker changed')
    installed_game=run/'mods'/jar.name
    if hashlib.sha256(installed_game.read_bytes()).hexdigest()!=digest:
        raise ValueError('Installed production JAR changed')
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
              'artifact_sha256':digest, 'clientVisuals':'REQUIRES_REAL_OBSERVATION', 'qaRequired':bool(gallery)}
    args.launch_report.parent.mkdir(parents=True, exist_ok=True)
    args.launch_report.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps(result))
    if args.wait:
        try:
            code=process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            result['status']='NATIVE_TIMEOUT_REQUIRES_REVIEW'
            args.launch_report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
            raise
        result['exitCode']=code
        capture_path=run/'client-capture-output.json'
        if gallery and capture_path.is_file():
            capture=json.loads(capture_path.read_text(encoding='utf8'))
            result['captureStatus']=capture.get('status')
            result['captureComplete']=capture.get('captureComplete',False)
        console=(run/'launch-console.log').read_text(encoding='utf8',errors='replace')
        result['normalSaveMessages']='Saving worlds' in console and 'All dimensions are saved' in console
        result['status']='CAPTURE_SAVED_NORMAL_EXIT_REQUIRES_PIXEL_REVIEW' if code==0 and result.get('captureComplete') and result['normalSaveMessages'] else 'NATIVE_EXIT_REQUIRES_REVIEW'
        args.launch_report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        print(json.dumps(result))


if __name__ == '__main__':
    main()
