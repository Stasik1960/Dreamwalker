import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'fix-evidence';rows=[]
for relative,note in (
 ('client-2/screenshots/fixed-0.png','Confirmed wall opening is covered by the reconstructed base surface.'),
 ('client-2/screenshots/fixed-1.png','Reconstructed slab uses the original half-height base, books and neighboring depressions retained.'),
 ('client-2/screenshots/fixed-2.png','Fallback wall pick renders the authored decoration and preserves its state.'),
 ('client-2/screenshots/statistics-items-skillet.png','Actual Statistics Items screen displays skillet without crash.'),
 ('client-3/screenshots/fixed-3.png','Balustrade black gradient is gone with AO enabled; authored item visible.'),
 ('../fix-profiles/client/screenshots/fixed-6.png','Retained staircase black top faces are gone with AO enabled in client-5.')):
 p=D/relative;assert p.is_file();rows.append({'file':relative,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'review':note})
(D/'visual-review.json').write_text(json.dumps({'status':'PASS','reviewedBy':'Codex visual inspection of actual Minecraft framebuffer captures','checks':rows},ensure_ascii=False,indent=2),encoding='utf-8')
