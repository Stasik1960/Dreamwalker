import hashlib,json,os,shutil,stat,subprocess,sys,zipfile
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
rows=list(files(W));worldZip=O/'bloodborne-restored-20261025.zip'
if worldZip.exists():
 manifest=load(D/'world-after.json');assert sha(worldZip)==manifest['archiveSha256'];frozen={r['path']:r for r in manifest['entries']};assert set(frozen)=={f.relative_to(W).as_posix() for f in rows}
 for f in rows:assert sha(f)==frozen[f.relative_to(W).as_posix()]['sha256'],'World changed after acceptance: '+str(f)
else:
 archive(worldZip,((f,'bloodborne/'+f.relative_to(W).as_posix()) for f in rows));manifest={'world':str(W),'files':len(rows),'bytes':sum(f.stat().st_size for f in rows),'archiveSha256':sha(worldZip),'entries':[{'path':f.relative_to(W).as_posix(),'bytes':f.stat().st_size,'sha256':sha(f)} for f in rows]};(D/'world-after.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
if '--install' in sys.argv:subprocess.run([sys.executable,'-X','utf8','-B',str(P/'tools/install_restoration_mod.py')],check=True)
sources=[]
for folder in ('src','gradle','tools'):
 for f in files(P/folder):
  rel=f.relative_to(P)
  if '__pycache__' in rel.parts or '.git' in rel.parts:continue
  if folder=='tools' and (f.stat().st_size>20_000_000 or f.suffix in ('.zip','.jar','.class','.hprof','.log')):continue
  sources.append((f,'DW/'+rel.as_posix()))
for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','README.md'):
 if (P/name).is_file():sources.append((P/name,'DW/'+name))
dependency=P/'fix-profiles/server/mods/worldedit-mod-7.2.15.jar';assert dependency.is_file();sources.append((dependency,'DW/build-dependencies/worldedit-mod-7.2.15.jar'))
for f in files(D):
 if 'final-artifacts' in f.parts or 'installed-backups' in f.parts or f.suffix in ('.zip','.jar','.args') or f.stat().st_size>3_000_000:continue
 sources.append((f,'DW/restoration-evidence/'+f.relative_to(D).as_posix()))
history=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication/TASK')
for f in files(history):sources.append((f,'DW/history/TASK/'+f.relative_to(history).as_posix()))
archive(O/'dreamwalker-restoration-full-sources-20261025.zip',sources);shutil.copy2(J,O/J.name)
for name in ('REPORT-RU.md','TASK-RU.md','DECISIONS-RU.md','HANDOFF-RU.md'):shutil.copy2(D/name,O/name)
artifacts=[{'name':f.name,'bytes':f.stat().st_size,'sha256':sha(f)} for f in O.iterdir() if f.is_file() and f.name!='SHA256SUMS.txt']
(O/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+r['name']+'\n' for r in artifacts),encoding='utf-8');artifacts.append({'name':'SHA256SUMS.txt','bytes':(O/'SHA256SUMS.txt').stat().st_size,'sha256':sha(O/'SHA256SUMS.txt')})
(D/'artifacts.json').write_text(json.dumps({'status':'PREPARED','productionSha256':H,'artifacts':artifacts},indent=2),encoding='utf-8')
