"""Bind separately observed gallery pixels to the original native screenshots.

This only combines explicit observations; a successful capture does not imply
visual acceptance. All 48 final frames must have an observed PASS record.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/v12.4-dedup'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--reviews', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    profile = read(args.profile)
    run = Path(profile['run_directory']).resolve()
    assert run.is_relative_to(ROOT / 'build')
    capture_path = run / 'client-capture-output.json'
    capture = read(capture_path)
    capture_sha = sha(capture_path)
    digest = sha(Path(profile['artifact']))
    assert capture['status'] == 'CAPTURE_COMPLETE' and capture['captureComplete']
    assert capture['productionJarSha256'] == profile['artifact_sha256'] == digest
    assert len(capture['frames']) == capture['capturedFrames'] == 48
    actual = {row['fileName']: row for row in capture['frames']}
    assert len(actual) == 48
    observed = {}
    reports = []
    for path in args.reviews:
        review = read(path)
        assert review['status'] == 'PASS_ASSIGNED_PIXELS'
        assert review['productionJarSha256'] == digest
        assert review['captureSha256'] == capture_sha
        assert not review.get('failures')
        for row in review['frames']:
            frame = actual[row['fileName']]
            image = run / 'screenshots' / row['fileName']
            assert row['status'] == 'PASS_OBSERVED_PIXELS' and row['observation'].strip()
            assert row['key'] == frame['key'] and row['sha256'] == frame['sha256'] == sha(image)
            assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
            assert frame['status'] == 'PASS' and frame['renderedCameraVerified']
            assert frame['width'] == 1280 and frame['height'] == 720
            assert row['fileName'] not in observed
            observed[row['fileName']] = row
        reports.append({'path': str(path.resolve()), 'sha256': sha(path), 'observedFrames': len(review['frames'])})
    assert set(observed) == set(actual), 'Unreviewed native gallery frames'
    destination = DOC / 'gallery-native-final'
    assert not destination.exists(), 'Never overwrite a previous visual attempt'
    destination.mkdir()
    for frame in capture['frames']:
        shutil.copyfile(run / 'screenshots' / frame['fileName'], destination / frame['fileName'])
    for name in ('client-capture-output.json', 'client-capture-input.json', 'launch-console.log'):
        shutil.copyfile(run / name, destination / name)
    result = {'schema': 'dreamwalker-flat-gallery-v124-observed-pixel-review-v1',
              'status': 'PASS', 'productionJarSha256': digest,
              'captureSha256': capture_sha, 'reviewedFrames': len(observed),
              'reviewMethod': 'Separate manual inspection of all original native Minecraft PNGs, no image editing.',
              'frames': [observed[row['fileName']] for row in capture['frames']],
              'reviews': reports, 'physicalMouseKeyboard': 'NOT_RUN', 'frameRateBenchmark': 'NOT_RUN'}
    args.output.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'status': 'PASS', 'productionJarSha256': digest, 'reviewedFrames': len(observed)}))


if __name__ == '__main__':
    main()
