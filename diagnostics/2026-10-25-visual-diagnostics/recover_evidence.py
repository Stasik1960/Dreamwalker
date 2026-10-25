import hashlib,json,urllib.request,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent;p=O/'published-evidence.zip';expected='cc81a368fe0361d8d871a179dc6023e1b8220b1e1353493dc16845fb1a99647c'
if not p.exists():
 with urllib.request.urlopen('https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-visual-physics-20261025/dreamwalker-visual-physics-evidence.zip',timeout=120) as src,p.open('xb') as dst:
  for data in iter(lambda:src.read(1<<20),b''):dst.write(data)
assert hashlib.sha256(p.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(p) as z:
 names=z.namelist();(O/'evidence-index.json').write_text(json.dumps(names,indent=2),encoding='utf-8')
 selected=[n for n in names if any(s in n.lower() for s in ('composition','catalog.sqlite','export.sqlite','native-states','native_states','sourceposes','source-poses','plan.sqlite','source.*'))]
 print('Evidence entries',len(names),'candidates',*selected[:70],sep='\n')
