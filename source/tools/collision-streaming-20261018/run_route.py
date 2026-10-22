"""Launch exactly one prepared route and sample process memory; run through Guard."""
import argparse, json, subprocess, time, traceback
from pathlib import Path

PROJECT = Path(__file__).absolute().parents[2]
EVIDENCE = PROJECT.parent / 'deliverables/collision-streaming-20261018/evidence'
PYTHON = r'C:\Users\vakir\miniconda3\python.exe'
POWERSHELL = r'C:\Users\vakir\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--case', required=True)
    ap.add_argument('--expected-frames', type=int, choices=[1, 3], default=3)
    args = ap.parse_args()
    case = args.case
    assert case.replace('-', '').isalnum()
    prepared_path = EVIDENCE / (case + '-prepared.json')
    prep = json.loads(prepared_path.read_text())
    assert prep['status'] == 'PREPARED'
    run = Path(prep['run_directory'])
    launch_path = EVIDENCE / (case + '-launch.json')
    assert not launch_path.exists()
    record = {'status': 'STARTING'}
    receipt = EVIDENCE / (case + '-driver.json')
    assert not receipt.exists()

    def save():
        receipt.write_text(json.dumps(record, indent=2) + '\n')

    try:
        save()
        with (EVIDENCE / (case + '-launcher.log')).open('w') as log:
            child = subprocess.Popen([PYTHON, 'tools/launch_prepared_client.py', '--prepared-report', str(prepared_path),
                                      '--launch-report', str(launch_path), '--wait', '--timeout', '1800'], cwd=PROJECT,
                                     stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            for _ in range(120):
                try:
                    launch = json.loads(launch_path.read_text())
                    break
                except (OSError, ValueError):
                    time.sleep(.5)
            else:
                raise RuntimeError('Native launch receipt missing')
            record.update(status='NATIVE_RUNNING', pid=launch['pid'])
            save()
            monitor = subprocess.Popen([POWERSHELL, '-NoProfile', '-File', 'tools/measure_native_client_memory.ps1',
                                        '-ClientProcessId', str(launch['pid']), '-Output', str(EVIDENCE / (case + '-memory.jsonl')),
                                        '-CaptureOutput', str(run / 'client-capture-output.json'), '-MaximumSeconds', '1800'],
                                       cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            code = child.wait()
            monitor.wait(timeout=30)
            assert code == 0
        result = json.loads(launch_path.read_text())
        assert result.get('exitCode') == 0 and result.get('captureComplete') and result.get('normalSaveMessages'), result
        cap = json.loads((run / 'client-capture-output.json').read_text())
        assert cap['status'] == 'CAPTURE_COMPLETE' and len(cap['frames']) == args.expected_frames
        for frame in cap['frames']:
            assert frame['requiredClientChunkEvidence']['delivered'] == 289
            assert frame['requiredClientBlockEvidence']['ready']
        record['status'] = 'NATIVE_SAVED'
    except Exception:
        record.update(status='FAIL', error=traceback.format_exc())
        raise
    finally:
        save()


if __name__ == '__main__':
    main()
