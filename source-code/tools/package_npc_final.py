import hashlib,json,os,shutil,stat,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';O=D/'final-artifacts';O.mkdir(exist_ok=True)
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1<<20),b''):h.update(chunk)
 return h.hexdigest()
ready=json.loads((D/'acceptance.json').read_text(encoding='utf-8'));assert ready['status']=='PASS'
J=P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.8.jar';assert sha(J)==ready['productionSha256']
def files(root):
 for e in os.scandir(root):
  assert not e.is_symlink() and not e.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT,e.path
  if e.is_dir(follow_symlinks=False):yield from files(Path(e.path))
  elif e.is_file(follow_symlinks=False):yield Path(e.path)
sources=[]
for folder in ('src','gradle','tools'):
 for f in files(P/folder):
  rel=f.relative_to(P)
  if '__pycache__' in rel.parts or '.git' in rel.parts:continue
  if folder=='tools' and (f.stat().st_size>20_000_000 or f.suffix in ('.zip','.jar','.class','.hprof','.log')):continue
  sources.append((f,'DW/'+rel.as_posix()))
for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','README.md'):
 if (P/name).is_file():sources.append((P/name,'DW/'+name))
sources.append((P/'build-tools/worldedit-mod-7.2.15.jar','DW/build-dependencies/worldedit-mod-7.2.15.jar'))
for evidence in (D,P/'interaction-evidence',P/'restoration-evidence'):
 for f in files(evidence):
  if 'final-artifacts' in f.parts or 'installed-backups' in f.parts or f.suffix in ('.zip','.jar','.args') or f.stat().st_size>3_000_000:continue
  sources.append((f,'DW/'+evidence.name+'/'+f.relative_to(evidence).as_posix()))
archive=O/'dreamwalker-npc-full-sources-20261026.zip';assert not archive.exists()
history=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication/TASK')
for f in files(history):sources.append((f,'DW/history/TASK/'+f.relative_to(history).as_posix()))
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
 for f,rel in sources:z.write(f,rel)
shutil.copy2(J,O/J.name)
for name in ('REPORT-RU.md','TASK-RU.md','DECISIONS-RU.md','HANDOFF-RU.md'):shutil.copy2(D/name,O/name)
artifacts=[{'name':f.name,'bytes':f.stat().st_size,'sha256':sha(f)} for f in O.iterdir() if f.is_file() and f.name!='SHA256SUMS.txt']
(O/'SHA256SUMS.txt').write_text(''.join(row['sha256']+'  '+row['name']+'\n' for row in artifacts),encoding='utf-8');artifacts.append({'name':'SHA256SUMS.txt','bytes':(O/'SHA256SUMS.txt').stat().st_size,'sha256':sha(O/'SHA256SUMS.txt')})
integrity=[]
for f in (archive,J):
 with zipfile.ZipFile(f) as z:assert z.testzip() is None;integrity.append({'name':f.name,'entries':len(z.infolist()),'sha256':sha(f)})
(D/'archive-integrity.json').write_text(json.dumps({'status':'PASS','archives':integrity},indent=2),encoding='utf-8');(D/'artifacts.json').write_text(json.dumps({'status':'PREPARED','productionSha256':sha(J),'artifacts':artifacts},indent=2),encoding='utf-8')
