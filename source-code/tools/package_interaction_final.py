"""Package only accepted, frozen files; keep the preceding release intact."""
import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');O=D/'final-artifacts';O.mkdir(exist_ok=True)
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1<<20),b''):h.update(chunk)
 return h.hexdigest()
ready=load(D/'acceptance.json');assert ready['status']=='PASS'
J=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.7.jar';assert sha(J)==ready['productionSha256']
assert load(D/'apply-1/map-audit.json')['status']=='PASS';assert load(D/'visual-review.json')['status']=='PASS'
def files(root):
 for entry in os.scandir(root):
  assert not entry.is_symlink() and not entry.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT,entry.path
  if entry.is_dir(follow_symlinks=False):yield from files(Path(entry.path))
  elif entry.is_file(follow_symlinks=False):yield Path(entry.path)
def archive(path,rows):
 assert not path.exists(), 'Inspect an existing artifact before replacing it'
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
  for file,rel in rows:z.write(file,rel)
rows=list(files(W));worldZip=O/'bloodborne-interactions-20261026.zip'
archive(worldZip,((f,'bloodborne/'+f.relative_to(W).as_posix()) for f in rows))
manifest={'world':str(W),'files':len(rows),'bytes':sum(f.stat().st_size for f in rows),'archiveSha256':sha(worldZip),'entries':[{'path':f.relative_to(W).as_posix(),'bytes':f.stat().st_size,'sha256':sha(f)} for f in rows]};(D/'world-after.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
sources=[]
for folder in ('src','gradle','tools'):
 for f in files(P/folder):
  rel=f.relative_to(P)
  if '__pycache__' in rel.parts or '.git' in rel.parts:continue
  if folder=='tools' and (f.stat().st_size>20_000_000 or f.suffix in ('.zip','.jar','.class','.hprof','.log')):continue
  sources.append((f,'DW/'+rel.as_posix()))
for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','README.md'):
 if (P/name).is_file():sources.append((P/name,'DW/'+name))
dependency=P/'build-tools/worldedit-mod-7.2.15.jar';assert dependency.is_file();sources.append((dependency,'DW/build-dependencies/worldedit-mod-7.2.15.jar'))
for evidence in (D,P/'restoration-evidence'):
 for f in files(evidence):
  if 'final-artifacts' in f.parts or 'installed-backups' in f.parts or f.suffix in ('.zip','.jar','.args') or f.stat().st_size>3_000_000:continue
  sources.append((f,'DW/'+evidence.name+'/'+f.relative_to(evidence).as_posix()))
history=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication/TASK')
for f in files(history):sources.append((f,'DW/history/TASK/'+f.relative_to(history).as_posix()))
archive(O/'dreamwalker-interactions-full-sources-20261026.zip',sources);shutil.copy2(J,O/J.name)
for name in ('REPORT-RU.md','TASK-RU.md','DECISIONS-RU.md','HANDOFF-RU.md'):shutil.copy2(D/name,O/name)
artifacts=[{'name':f.name,'bytes':f.stat().st_size,'sha256':sha(f)} for f in O.iterdir() if f.is_file() and f.name!='SHA256SUMS.txt']
(O/'SHA256SUMS.txt').write_text(''.join(row['sha256']+'  '+row['name']+'\n' for row in artifacts),encoding='utf-8');artifacts.append({'name':'SHA256SUMS.txt','bytes':(O/'SHA256SUMS.txt').stat().st_size,'sha256':sha(O/'SHA256SUMS.txt')})
integrity=[]
for path in (J,worldZip,O/'dreamwalker-interactions-full-sources-20261026.zip'):
 with zipfile.ZipFile(path) as z:assert z.testzip() is None;integrity.append({'file':path.name,'entries':len(z.infolist()),'sha256':sha(path)})
(D/'archive-integrity.json').write_text(json.dumps({'status':'PASS','archives':integrity},indent=2),encoding='utf-8')
(D/'artifacts.json').write_text(json.dumps({'status':'PREPARED','productionSha256':ready['productionSha256'],'artifacts':artifacts},indent=2),encoding='utf-8')
