"""Record root's explicit observations of the unmodified final gallery PNGs."""
from pathlib import Path
import argparse
import hashlib
import json

OBSERVATIONS = [
    'Both textured closed door leaves are visible on the blue V12 platform.',
    'The full wooden gate and its base are visible; the neighboring tree no longer hides the gate.',
    'The complete native elevator tower is visible from base to top, on its own blue platform.',
    'The lower stone ornament is fully visible on the green platform beside its label.',
    'The thin curved stone arch is fully visible on its green platform; the large right-hand arch is a separate neighbor.',
    'The wide stepped stone platform and its patterned surfaces are visible on the green display platform.',
    'The gray stone cube is fully visible in the orange absent-from-source-map section.',
    'The curved panelled arch is fully visible on its orange platform; the foreground object is a separate neighbor.',
    'The vial icon is visible inside a supported item frame on the blue item platform.',
    'The BB tool spanner icon is visible inside a supported item frame beside its Russian label.',
    'The yellow bamboo mosaic stair has visible textured riser, tread and side faces.',
    'The yellow bamboo stair shows its horizontal plank pattern and complete step faces.',
    'The gray brick stair has complete textured tread, riser and side faces.',
    'The pink cherry stair is clearly visible with complete plank-textured step faces.',
    'The gray cobblestone stair is visible with complete rock-textured step faces.',
    'The patterned gray deepslate brick stair is visible with complete tread, riser and side faces.',
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    path = args.run / 'client-capture-output.json'
    capture = json.loads(path.read_text(encoding='utf-8-sig'))
    assert capture['status'] == 'CAPTURE_COMPLETE' and capture['capturedFrames'] == 48
    frames = []
    for number, observation in enumerate(OBSERVATIONS, 1):
        row = capture['frames'][number - 1]
        assert row['fileName'].startswith(f'gallery-{number:02d}-')
        assert row['status'] == 'PASS' and row['renderedCameraVerified']
        assert sha(args.run / 'screenshots' / row['fileName']) == row['sha256']
        frames.append({'fileName': row['fileName'], 'key': row['key'], 'sha256': row['sha256'],
                       'status': 'PASS_OBSERVED_PIXELS', 'observation': observation})
    result = {'schema': 'dreamwalker-flat-gallery-v124-root-pixel-review-v1',
              'status': 'PASS_ASSIGNED_PIXELS', 'productionJarSha256': capture['productionJarSha256'],
              'captureSha256': sha(path), 'reviewedFrames': len(frames),
              'method': 'Root viewed all sixteen original native screenshots using view_image; no image edits.',
              'frames': frames, 'failures': []}
    args.output.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'status': result['status'], 'reviewedFrames': len(frames)}))


if __name__ == '__main__':
    main()
