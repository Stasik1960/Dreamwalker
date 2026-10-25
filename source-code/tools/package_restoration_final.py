import hashlib,json,os,shutil,stat,sys,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');O=D/'final-artifacts';O.mkdir(exist_ok=True)
V='0.1.0-v12.7.4-visual-physics.6';J=P/f'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-{V}.jar'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
def load(p):return json.loads(p.read_text(encoding='utf-8'))
assert load(D/'ready-to-release.json')['status']=='READY'
assert load(D/'final-map-audit.json')['status']=='PASS'
assert load(D/'map-apply.json')['status']=='COMPLETE'
H=sha(J);ready=load(D/'ready-to-release.json')
for role in ('server','client'):
 folder=D/ready[role];assert load(folder/'process.json')['productionSha256']==H
 assert load(folder/('checks.json' if role=='server' else 'client-probe.json'))['status']=='PASS'
assert load(D/'visual-review.json')['status']=='PASS'
with zipfile.ZipFile(J) as z:assert json.loads(z.read('fabric.mod.json'))['version']==V
def files(root):
 for e in os.scandir(root):
  assert not e.is_symlink() and not e.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT,e.path
  if e.is_dir(follow_symlinks=False):yield from files(Path(e.path))
  elif e.is_file(follow_symlinks=False):yield Path(e.path)
def archive(path,rows):
 assert not path.exists(),'Do not replace a prepared artifact without inspecting its manifest'
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
  for f,rel in rows:z.write(f,rel)
rows=list(files(W));archive(O/'bloodborne-restored-20261025.zip',((f,'bloodborne/'+f.relative_to(W).as_posix()) for f in rows))
manifest={'world':str(W),'files':len(rows),'bytes':sum(f.stat().st_size for f in rows),'entries':[{'path':f.relative_to(W).as_posix(),'bytes':f.stat().st_size,'sha256':sha(f)} for f in rows]};(D/'world-after.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
sources=[]
for folder in ('src','gradle','tools'):
 for f in files(P/folder):
  rel=f.relative_to(P)
  if '__pycache__' in rel.parts or '.git' in rel.parts:continue
  if folder=='tools' and (f.stat().st_size>20_000_000 or f.suffix in ('.zip','.jar','.class','.hprof','.log')):continue
  sources.append((f,'DW/'+rel.as_posix()))
for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','README.md'):
 if (P/name).is_file():sources.append((P/name,'DW/'+name))
for f in files(D):
 if 'final-artifacts' in f.parts or 'installed-backups' in f.parts or f.suffix in ('.zip','.jar','.args') or f.stat().st_size>3_000_000:continue
 sources.append((f,'DW/restoration-evidence/'+f.relative_to(D).as_posix()))
original=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-21-native-grid-city/START-RU.txt')
if original.is_file():sources.append((original,'DW/history/START-RU.txt'))
archive(O/'dreamwalker-restoration-full-sources-20261025.zip',sources);shutil.copy2(J,O/J.name)
for name in ('REPORT-RU.md','TASK-RU.md','DECISIONS-RU.md','HANDOFF-RU.md'):shutil.copy2(D/name,O/name)
artifacts=[{'name':f.name,'bytes':f.stat().st_size,'sha256':sha(f)} for f in O.iterdir() if f.is_file() and f.name!='SHA256SUMS.txt']
(O/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in artifacts),encoding='utf-8');artifacts.append({'name':'SHA256SUMS.txt','bytes':(O/'SHA256SUMS.txt').stat().st_size,'sha256':sha(O/'SHA256SUMS.txt')})
(D/'artifacts.json').write_text(json.dumps({'status':'PREPARED','productionSha256':H,'artifacts':artifacts},indent=2),encoding='utf-8')
if '--install' in sys.argv:
 installs=[]
 for role,mods in [('client',Path('C:/Users/vakir/Desktop/client_mods')),('server',Path('C:/Users/vakir/Desktop/server_mods')),('limacina',Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods'))]:
  backup=D/'installed-backups'/role;backup.mkdir(parents=True,exist_ok=True);removed=[]
  for f in mods.glob('*.jar'):
   with zipfile.ZipFile(f) as z:info=json.loads(z.read('fabric.mod.json')) if 'fabric.mod.json' in z.namelist() else {}
   if info.get('id')!='bloodborne_dw':continue
   assert f.resolve().parent==mods.resolve() and not f.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
   if f.name==J.name and sha(f)==H:continue
   target=backup/f.name
   if target.exists():assert sha(target)==sha(f)
   else:shutil.copy2(f,target)
   assert sha(f)==sha(target);f.unlink();removed.append(f.name)
  shutil.copy2(J,mods/J.name);assert sha(mods/J.name)==H
  lock=mods/'DREAMWALKER-MODPACK-LOCK.json'
  if lock.exists():
   data=load(lock);shutil.copy2(lock,backup/lock.name);data['dreamwalkerSha256']=H
   data['files']={name:r for name,r in data['files'].items() if r.get('id')!='bloodborne_dw'};data['files'][J.name]={'id':'bloodborne_dw','version':V,'sha256':H,'bytes':J.stat().st_size};lock.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
  installs.append({'role':role,'path':str(mods/J.name),'sha256':H,'replaced':removed})
 (D/'installed.json').write_text(json.dumps(installs,indent=2),encoding='utf-8')
