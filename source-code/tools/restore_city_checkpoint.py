"""Freeze the user's current map; never traverse reparse points or edit input."""
import hashlib, json, os, stat, sys, time, zipfile
from pathlib import Path

P = Path(__file__).resolve().parents[1]
D = P / 'restoration-evidence'
W = Path('C:/Users/vakir/Desktop/bloodborne')
D.mkdir(exist_ok=True)
def files(root):
    for e in os.scandir(root):
        if e.is_symlink() or e.stat(follow_symlinks=False).st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise RuntimeError('Reparse point forbidden: ' + e.path)
        if e.is_dir(follow_symlinks=False):
            yield from files(Path(e.path))
        elif e.is_file(follow_symlinks=False):
            yield Path(e.path)

plan = {'status':'IN_PROGRESS','inputWorld':str(W),'preserve':'All manual changes, v12, independent component blocks and previously approved whole objects', 'stages':[
 {'id':0,'task':'Freeze current world with SHA256 manifest, audit and checkpoint GitHub'},
 {'id':1,'task':'Fix compact block removal and actual WorldEdit //set / undo / redo'},
 {'id':2,'task':'Fix window rendering and v12/tree targeting; audit collision/selection bounds'},
 {'id':3,'task':'Restore lantern impact sound and sway; add standalone lantern types'},
 {'id':4,'task':'Assemble lamp posts bottom to top, separate paired lamps, replace only matched originals in user world'},
 {'id':5,'task':'Remove proven empty unnecessary files and stop runtime regeneration'},
 {'id':6,'task':'Targeted dedicated and client tests, save/reopen, install mod, publish map/full sources/evidence'}]}
(D/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
target=D/'bloodborne-before-restoration.zip'
assert not target.exists(), 'Do not overwrite original backup'
rows=[];start=time.time()
with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
    for p in files(W):
        before=p.stat();h=hashlib.sha256();rel=p.relative_to(W).as_posix()
        with p.open('rb') as src,z.open('bloodborne/'+rel,'w',force_zip64=True) as dst:
            for b in iter(lambda:src.read(1<<20),b''):h.update(b);dst.write(b)
        after=p.stat()
        assert (before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns), 'World changed while copying: '+rel
        rows.append({'path':rel,'bytes':before.st_size,'mtimeNs':before.st_mtime_ns,'sha256':h.hexdigest()})
manifest={'status':'FROZEN_READ_ONLY','world':str(W),'backup':str(target),'files':len(rows),'bytes':sum(r['bytes'] for r in rows),'zeroByteFiles':sum(r['bytes']==0 for r in rows),'seconds':time.time()-start,'entries':rows}
(D/'world-before.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in manifest.items() if k!='entries'}),flush=True)
