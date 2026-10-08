"""Prepare distinct V8 client markers; never rewrite historical V7 inputs."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--revision', default='v8')
    args = parser.parse_args()
    if not re.fullmatch(r'v8(?:-[a-z0-9-]+)?', args.revision):
        raise ValueError('Use a distinct safe V8 marker revision')
    scene_path = ROOT / 'tools/first_set_scene_input.json'
    scene_bytes = scene_path.read_bytes()
    scene = json.loads(scene_bytes)
    assert scene['sceneId'] == 'first-set-review-v8'
    artifact_sha = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    records = []
    for suffix in ['', '_shader', '_alt']:
        old = ROOT / ('tools/first_set_client' + suffix + '_input.json')
        marker = json.loads(old.read_text(encoding='utf8'))
        marker['productionJarSha256'] = artifact_sha
        marker['expectedBlocks'] = [
            {'id': 'bloodborne_dw:' + row['kind'], 'pos': row['root']}
            for row in scene['objects']
        ]
        marker['reviewSceneId'] = scene['sceneId']
        marker['reviewSceneInputSha256'] = hashlib.sha256(scene_bytes).hexdigest()
        marker['historicalMarkerSource'] = {
            'path': str(old.relative_to(ROOT)),
            'sha256': hashlib.sha256(old.read_bytes()).hexdigest(),
            'meaning': 'configuration template only; historical PASS is not reused',
        }
        path = ROOT / ('tools/first_set_client_' + args.revision + suffix + '_input.json')
        data = (json.dumps(marker, ensure_ascii=False, indent=2) + '\n').encode('utf8')
        if path.exists() and path.read_bytes() != data:
            raise ValueError('V8 marker already frozen with different bytes: ' + str(path))
        path.write_bytes(data)
        records.append({'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(data).hexdigest()})
    print(json.dumps({'status': 'PREPARED_CURRENT_V8_MARKERS_NOT_RUNTIME_PASS',
                      'production_jar_sha256': artifact_sha,
                      'expected_roots': len(scene['objects']), 'markers': records}))


if __name__ == '__main__':
    main()
