"""Archive the human review verbatim; never overwrite earlier requirements."""
from pathlib import Path
import hashlib, json, shutil

ROOT = Path(__file__).resolve().parents[1]
REQUEST = Path('C:/Users/vakir/.codex/attachments/6ee1ad07-c332-4d27-8716-c450d3d1c632/Вставленный текст.txt')
IMAGES = [
    ('01-door.png', 'e9025cb4-8422-4c79-a4de-0e6979caf5f3'),
    ('02-wall.png', '196ba9b4-2717-46e7-91dd-8346e5007bb1'),
    ('03-roof.png', '41868286-fc44-4823-a365-47909a23cc06'),
    ('04-glazing.png', '5d7d857d-0070-462d-a404-160bf5aa7503'),
    ('05-flat-tree.png', '96214bf8-c440-4627-b2ac-f23a37db7b27'),
    ('06-ladder.png', '72f2c32a-aee5-4646-8fc3-387f5693cdc5'),
]
DEST = ROOT / 'reports/user-review-v7'
DEST.mkdir(parents=True, exist_ok=True)
data = REQUEST.read_bytes()
text = data.decode('utf-8-sig')
marker = '## Уточнение пользователя по ручной проверке prototype.2 / v7 — 2026-10-07'
for name in ('TASK.md', 'PROGRESS.md'):
    path = ROOT / name
    if marker not in path.read_text(encoding='utf-8-sig'):
        with path.open('a', encoding='utf-8', newline='') as out:
            out.write('\n\n' + marker + '\n\n')
            out.write('Приоритет над противоречащими прежними формулировками. Ниже полный текст запроса без сокращений. Статус v7: PARTIALLY_ACCEPTED; весь набор NOT_ACCEPTED.\n\n')
            out.write(text)
            out.write('\n')
    assert text in path.read_text(encoding='utf-8-sig'), name
(DEST / 'request.txt').write_bytes(data)
manifest = {'schema': 'human-review-v7-evidence-1', 'acceptance': 'PARTIALLY_ACCEPTED_SET_NOT_ACCEPTED',
            'accepted': ['wooden_window_panels', 'volumetric_tree'], 'source': str(REQUEST),
            'request_sha256': hashlib.sha256(data).hexdigest(), 'images': []}
for name, token in IMAGES:
    source = Path('C:/Users/vakir/AppData/Local/Temp') / ('codex-clipboard-' + token + '.png')
    target = DEST / name
    shutil.copyfile(source, target)
    assert target.read_bytes() == source.read_bytes()
    manifest['images'].append({'order': len(manifest['images']) + 1, 'file': name,
                               'source': str(source), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest()})
(DEST / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Full human request appended verbatim to TASK and PROGRESS; six screenshots archived byte-identically.')
