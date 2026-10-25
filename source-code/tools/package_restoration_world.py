import hashlib,json,os,shutil,stat,sys,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');O=D/'final-artifacts';O.mkdir(exist_ok=True);target=O/'bloodborne-restored-20261025.zip'
assert json.loads((D/'final-map-audit.json').read_text())['status']=='PASS'
if target.exists():
 assert '--refresh-after-sweep' in sys.argv and json.loads((D/'cleanup-after-save.json').read_text())['status']=='APPLIED'
 h=hashlib.sha256()
 with target.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 assert h.hexdigest()==json.loads((D/'world-after.json').read_text())['archiveSha256']
 previous=D/'bloodborne-after-apply-before-final-sweep.zip';assert not previous.exists() and target.resolve().is_relative_to(P.resolve()) and previous.resolve().is_relative_to(P.resolve());shutil.move(target,previous)
def files(root):
 for e in os.scandir(root):
  assert not e.is_symlink() and not e.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
  if e.is_dir(follow_symlinks=False):yield from files(Path(e.path))
  elif e.is_file(follow_symlinks=False):yield Path(e.path)
rows=[]
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
 for p in files(W):
  before=p.stat();h=hashlib.sha256();rel=p.relative_to(W).as_posix()
  with p.open('rb') as src,z.open('bloodborne/'+rel,'w',force_zip64=True) as dst:
   for b in iter(lambda:src.read(1<<20),b''):h.update(b);dst.write(b)
  after=p.stat();assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns)
  rows.append({'path':rel,'bytes':before.st_size,'sha256':h.hexdigest()})
h=hashlib.sha256()
with target.open('rb') as f:
 for b in iter(lambda:f.read(1<<20),b''):h.update(b)
(D/'world-after.json').write_text(json.dumps({'world':str(W),'files':len(rows),'bytes':sum(r['bytes'] for r in rows),'archiveSha256':h.hexdigest(),'entries':rows},ensure_ascii=False,indent=2),encoding='utf-8')
