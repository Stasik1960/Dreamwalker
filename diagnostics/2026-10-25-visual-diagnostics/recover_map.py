import hashlib,json,urllib.request,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent;p=O/'published-map.zip';h='da6d02e3e86e1780576359033f908a0a5cb22a255348939eae1637a44423c636'
if not p.exists():
 with urllib.request.urlopen('https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-visual-physics-20261025/bloodborne-native-grid-city.zip',timeout=120) as r,p.open('xb') as f:
  for b in iter(lambda:r.read(1<<20),b''):f.write(b)
assert hashlib.sha256(p.read_bytes()).hexdigest()==h
with zipfile.ZipFile(p) as z:
 for n in z.namelist():
  if '/region/' in n or n.endswith('/level.dat') or n.endswith('/NATIVE-VISUAL-READY.json'):
   target=O/'published-map'/n;assert target.resolve().is_relative_to((O/'published-map').resolve());target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(n))
(O/'map-recovery.json').write_text(json.dumps({'status':'PUBLISHED_MAP_SHA_VERIFIED_DIAGNOSTIC_COPY','sha256':h},indent=2))
