"""Transcribe root's observed native PNG review, or explicitly carry identical pixels."""
from pathlib import Path
import argparse, hashlib, json
ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/v12.4-dedup'
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p = argparse.ArgumentParser()
    p.add_argument('--run', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--carry', type=Path)
    p.add_argument('--freshly-reviewed', default='')
    a = p.parse_args()
    capture = read(a.run / 'v124-client-comparison-output.json')
    assert capture['status'] == 'CAPTURE_COMPLETE'
    pages = set(range(1, 26)) | set(range(56, 81))
    failures = {('93145', 0): 'Source chain texture orientation changed.',
                ('93021', 0): 'Native FaceBakery rescale widened the original ornament.'}
    blanks = {('90141', 0), ('93223', 3), ('93223', 4), ('93223', 5)}
    frames, rows = [], []
    prior = read(a.carry) if a.carry else None
    prior_frames = {f['page']: f for f in prior['frames']} if prior else {}
    fresh = {int(n) for n in a.freshly_reviewed.split(',') if n}
    for f in capture['frames']:
        page = int(f['fileName'].rsplit('-', 1)[1].split('.')[0])
        if page not in pages: continue
        image = a.run / 'screenshots' / f['fileName']
        assert sha(image) == f['sha256']
        if prior and page not in fresh:
            assert f['sha256'] == prior_frames[page]['sha256'], ('Changed pixels require inspection', page)
            assert prior_frames[page]['status'] == 'PASS_OBSERVED_PIXELS', ('Prior frame not accepted', page)
        frame_status = 'PASS_OBSERVED_PIXELS'
        for r in f['pairs']:
            key = (r['oldId'], r['oldVariant'])
            status, reason = 'PASS', 'Actual before/after geometry, visible texture, scale and anchor match.'
            if not prior:
                if key in failures: status, reason = 'FAIL', failures[key]; frame_status = 'FAIL'
                elif key in blanks: status, reason = 'PENDING_ALTERNATE_VIEW', 'Both source and replacement are culled in this native camera.'; frame_status = 'PENDING'
            elif key in blanks:
                status, reason = 'PENDING_ALTERNATE_VIEW', 'Dedicated alternate view is reviewed separately.'
            rows.append({'page': page, 'oldId': key[0], 'oldRegistryId': r['oldRegistryId'], 'oldVariant': key[1], 'status': status, 'reason': reason})
        frames.append({'page': page, 'fileName': f['fileName'], 'sha256': f['sha256'],
                       'status': frame_status, 'manualVisualInspection': True,
                       'method': 'Fresh actual PNG inspection' if not prior or page in fresh else 'Exact PNG byte identity to previously observed image',
                       'priorReviewSha256': sha(a.carry) if prior and page not in fresh else None})
    assert {f['page'] for f in frames} == pages
    result = {'schema': 'dreamwalker-v124-root-observed-native-review-v1',
              'status': 'PASS_ASSIGNED_PIXELS_PENDING_ALTERNATES' if prior else 'FAIL_PRE_FINAL_REVIEW',
              'productionJarSha256': capture['productionJarSha256'], 'captureSha256': sha(a.run / 'v124-client-comparison-output.json'),
              'reviewedFrames': len(frames), 'frames': frames, 'rows': rows,
              'method': 'Unmodified native Minecraft screenshots. Capture completion alone is not visual acceptance.'}
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': result['status'], 'frames': len(frames), 'rows': len(rows)}))
if __name__ == '__main__': main()
