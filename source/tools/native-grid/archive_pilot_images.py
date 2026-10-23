"""Preserve accepted image bytes before reusing the normal candidate profile."""
import hashlib, json, shutil
from pathlib import Path

research = Path(__file__).resolve().parents[3] / 'research'
review_path = research / 'pilot/visual-review.json'
review = json.loads(review_path.read_text(encoding='utf-8-sig'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
gate = json.loads((research / 'pilot-gate.json').read_text(encoding='utf-8-sig'))
assert gate['status'] == 'PASS'
out = research / 'pilot/accepted-rendered-images'
out.mkdir(exist_ok=True)
rows = []
for view in review['views']:
    source = Path(view['path'])
    assert sha(source) == view['sha256']
    target = out / source.name
    if target.exists():
        assert sha(target) == view['sha256']
    else:
        shutil.copy2(source, target)
    assert sha(target) == view['sha256']
    rows.append({'originalPath': str(source), 'archivePath': str(target), 'sha256': view['sha256']})
receipt = {'status': 'ACCEPTED_BYTES_ARCHIVED_NO_RETEST',
           'reviewSha256': sha(review_path), 'pilotGateSha256': sha(research / 'pilot-gate.json'),
           'images': rows, 'worldWrites': 0, 'acceptanceReportsChanged': False}
(out / 'manifest.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': receipt['status'], 'images': len(rows)}))
