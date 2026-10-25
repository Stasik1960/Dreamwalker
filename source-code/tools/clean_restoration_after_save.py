import hashlib,json,os,stat,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne').resolve();rows=[]
def walk(root):
 for e in os.scandir(root):
  assert not e.is_symlink() and not e.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT,e.path
  if e.is_dir(follow_symlinks=False):yield from walk(Path(e.path))
  elif e.is_file(follow_symlinks=False):yield Path(e.path)
for p in walk(W):
 if p.suffix!='.mca' or p.parent.name not in ('region','entities','poi'):continue
 data=p.read_bytes()
 if not data or len(data)>=8192 and not any(data[:4096]):rows.append({'path':p.relative_to(W).as_posix(),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
if '--apply' in sys.argv:
 for row in rows:
  p=W/row['path'];assert p.resolve().is_relative_to(W) and hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256'];p.unlink()
report={'status':'APPLIED' if '--apply' in sys.argv else 'REVIEWED','reason':'Minecraft-created Anvil files with no live location-table entries after save','files':len(rows),'bytes':sum(r['bytes'] for r in rows),'removed':rows}
(D/'cleanup-after-save.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
