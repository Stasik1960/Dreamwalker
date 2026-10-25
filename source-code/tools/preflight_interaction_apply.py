import hashlib,json,os,stat
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne')
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1<<20),b''):h.update(chunk)
 return h.hexdigest()
for file in ('preview-2/map-audit.json','preview-2/global-data-audit.json','client-3/client-checks.json','visual-review.json'):assert load(D/file)['status']=='PASS',file
baseline=load(D/'world-before.json')['files'];seen=[]
def files(root):
 for e in os.scandir(root):
  assert not e.is_symlink() and not e.stat(follow_symlinks=False).st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
  if e.is_dir(follow_symlinks=False):yield from files(Path(e.path))
  elif e.is_file(follow_symlinks=False):yield Path(e.path)
current={p.relative_to(W).as_posix():p for p in files(W)};assert set(current)=={r['path'] for r in baseline},'Input map file set changed'
for row in baseline:assert sha(current[row['path']])==row['sha256'],'Input map changed: '+row['path']
backup=load(D/'backup.json');assert sha(Path(backup['path']))==backup['sha256']
H=sha(P/'build-native-visual/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.7.jar')
for role in ('preview-2','client-3'):assert load(D/role/'process.json')['productionSha256']==H
(D/'real-apply-preflight.json').write_text(json.dumps({'status':'PASS','productionSha256':H,'inputFiles':len(current),'unchangedInput':True,'backupSha256':backup['sha256']},indent=2),encoding='utf-8')
