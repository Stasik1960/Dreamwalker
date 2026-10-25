"""Download only the exact public assets recorded in ASSETS.json; no extraction."""
import argparse,hashlib,json,os,urllib.request
from pathlib import Path
a=argparse.ArgumentParser();a.add_argument('--out',type=Path,required=True);a=a.parse_args()
manifest=json.loads(Path(__file__).with_name('ASSETS.json').read_text(encoding='utf-8'))
for p in (a.out,*a.out.parents):
 if p.exists():assert not p.is_symlink() and not getattr(p.lstat(),'st_file_attributes',0)&0x400
a.out.mkdir(parents=True,exist_ok=True)
for row in manifest['assets']:
 name=row['name'];assert Path(name).name==name and '/' not in name and '\\' not in name
 path=a.out/name
 def digest(p):
  h=hashlib.sha256()
  with p.open('rb') as f:
   for b in iter(lambda:f.read(1<<20),b''):h.update(b)
  return h.hexdigest()
 if path.exists():
  assert path.is_file() and path.stat().st_size==row['bytes'] and digest(path)==row['sha256'];continue
 temporary=a.out/(name+'.part')
 with urllib.request.urlopen('https://github.com/Stasik1960/Dreamwalker/releases/download/native-grid-city-visual-physics-20261025/'+name,timeout=120) as incoming,temporary.open('xb') as output:
  while True:
   data=incoming.read(1<<20)
   if not data:break
   output.write(data)
 assert temporary.stat().st_size==row['bytes'] and digest(temporary)==row['sha256']
 assert not path.exists();os.replace(temporary,path)
 print(name+' SHA256 verified')
