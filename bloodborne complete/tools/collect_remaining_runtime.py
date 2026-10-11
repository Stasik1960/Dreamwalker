"""Archive actual isolated client/server evidence, without inferring physical input."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/remaining-transfer'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--client-dir', type=Path, required=True)
    parser.add_argument('--reopen-dir', type=Path, required=True)
    parser.add_argument('--rejoin-dir', type=Path, required=True)
    parser.add_argument('--snapshot-before', type=Path, required=True)
    parser.add_argument('--snapshot-after', type=Path, required=True)
    parser.add_argument('--visual-review', type=Path, required=True)
    parser.add_argument('--reopen-server-report', type=Path)
    args = parser.parse_args()
    jar = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar'
    digest = sha(jar)
    proof = DOC / 'evidence/runtime'
    proof.mkdir(parents=True, exist_ok=True)
    inputs = {
        'server-primary': args.native_dir / 'server.json',
        'client-primary-profile': args.native_dir / 'client.json',
        'client-baked': args.client_dir / 'remaining-transfer-client-qa.json',
        'client-network': args.client_dir / 'remaining-transfer-client-network.json',
        'client-capture': args.client_dir / 'remaining-transfer-capture.json',
        'server-reopen': args.reopen_server_report or args.reopen_dir / 'server.json',
        'client-rejoin-profile': args.reopen_dir / 'client.json',
        'client-rejoin': args.rejoin_dir / 'remaining-transfer-client-rejoin.json',
        'saved-world-before': args.snapshot_before,
        'saved-world-after': args.snapshot_after,
        'visual-review': args.visual_review,
        'native-plan': args.native_dir / 'native-plan.json',
    }
    evidence = {}
    for name, source in inputs.items():
        data = read(source)
        bound = data.get('productionJarSha256', data.get('artifact_sha256', data.get('artifact', {}).get('sha256') if isinstance(data.get('artifact'), dict) else None))
        assert bound == digest, (name, bound)
        if name not in ('client-capture', 'native-plan') and not name.endswith('-profile'):
            assert data['status'] == 'PASS', name
        target = proof / (name + '.json')
        shutil.copyfile(source, target)
        evidence[name] = {'path': str(target.relative_to(ROOT)), 'sha256': sha(target), 'status': data.get('status')}
    capture = read(inputs['client-capture'])
    assert capture['status'] == 'CAPTURE_COMPLETE' and capture['capturedFrames'] == capture['expectedFrames']
    visual = read(inputs['visual-review'])
    assert all(row['status'] == 'PASS' for row in visual['frames'])
    png_dir = proof / 'screenshots'
    png_dir.mkdir(exist_ok=True)
    for frame in capture['frames']:
        source = args.client_dir / 'screenshots' / frame['fileName']
        assert sha(source) == frame['sha256']
        reviewed = next(row for row in visual['frames'] if row['fileName'] == frame['fileName'])
        assert reviewed['sha256'] == frame['sha256']
        shutil.copyfile(source, png_dir / frame['fileName'])
    for label, folder in (('server-primary', Path(read(inputs['server-primary'])['run_directory'])),
                          ('server-reopen', Path(read(inputs['server-reopen'])['run_directory'])),
                          ('client-primary', args.client_dir), ('client-rejoin', args.rejoin_dir)):
        shutil.copyfile(folder / 'launch-console.log', proof / (label + '.log'))
    reopen = read(inputs['server-reopen'])
    if 'originalStrictReport' in reopen:
        original = reopen['originalStrictReport']
        original_path = Path(original['path'])
        assert sha(original_path) == original['sha256'] and read(original_path)['status'] == 'FAIL'
        shutil.copyfile(original_path, proof / 'server-reopen-original-strict-fail.json')
    report = {
        'schema': 'dreamwalker-remaining-transfer-runtime-verification-v1',
        'productionJarSha256': digest, 'status': 'PASS',
        'bakedPartModels': read(inputs['client-baked'])['partModels'],
        'bakedOfferedVariants': read(inputs['client-baked'])['offeredVariants'],
        'networkCases': len(read(inputs['client-network'])['cases']),
        'rejoinCases': len(read(inputs['client-rejoin'])['cases']),
        'reviewedFramebufferImages': len(visual['frames']),
        'physicalMouseKeyboard': 'NOT_RUN',
        'physicalInputObstacle': 'Windows window capture failed twice: FrameArrived timeout and window capture timeout. No physical input was inferred from engine/network actions.',
        'authorCollisionExport': 'NOT_PROVIDED_NOT_APPLIED',
        'reopenStrictLauncherStatus': reopen.get('originalStrictReport', {}).get('status', reopen['status']),
        'externalAuthentication': reopen.get('externalAuthentication', {'status': 'NOT_RUN'}),
        'scope': 'Actual new isolated localhost worlds only. Client engine packets, vanilla item pick, server acknowledgement, real save/reopen and client rejoin are separate from physical UI acceptance. This is a sampled visual/network check, not all1805 variants visually inspected.',
        'evidence': evidence,
    }
    (DOC / 'runtime-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
