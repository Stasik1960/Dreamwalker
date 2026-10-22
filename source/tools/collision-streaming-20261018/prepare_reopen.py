"""Copy the normally saved after-world for a single brick-view process reopen."""
import argparse, hashlib, json, subprocess, sys
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
EVIDENCE = PROJECT.parent / 'deliverables/collision-streaming-20261018/evidence'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--source-case', default='shapes-after')
    ap.add_argument('--case', default='shapes-reopen')
    args = ap.parse_args()
    assert all(x.replace('-', '').isalnum() for x in [args.source_case, args.case])
    previous_path = EVIDENCE / (args.source_case + '-prepared.json')
    launch_path = EVIDENCE / (args.source_case + '-launch.json')
    previous = json.loads(previous_path.read_text())
    launch = json.loads(launch_path.read_text())
    assert launch.get('exitCode') == 0 and launch.get('captureComplete') and launch.get('normalSaveMessages'), 'Source must have saved and exited normally'
    run = Path(previous['run_directory'])
    cap = json.loads((run / 'client-capture-output.json').read_text())
    assert cap['status'] == 'CAPTURE_COMPLETE' and len(cap['frames']) == 3
    command = [sys.executable, str(Path(__file__).with_name('prepare_route.py')), '--case', args.case,
               '--jar', previous['artifact'], '--world-source', str(run / 'saves/prototype-fixture'),
               '--gallery-addon', str(run / 'mods' / previous['extra_mods'][0]['copied_name']), '--brick-only']
    subprocess.run(command, check=True, cwd=PROJECT)
    report_path = EVIDENCE / (args.case + '-prepared.json')
    report = json.loads(report_path.read_text())
    assert report['artifact_sha256'] == previous['artifact_sha256']
    report['reopenProof'] = {'normallySavedSourceCase': args.source_case, 'priorLaunchReceipt': str(launch_path),
                             'priorLaunchReceiptSha256': hashlib.sha256(launch_path.read_bytes()).hexdigest(),
                             'sourcePid': launch['pid'], 'sourceExitedNormally': True,
                             'scope': 'Fresh process opens an independent byte copy of the normally saved final route world. One brick endpoint, no repeated performance route.'}
    report_path.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
