"""Publish diagnostics only, never stage the user's cleaned working tree."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
O=Path(__file__).resolve().parent;R=O/'github-publication';BR='codex/visual-diagnostics-20261025';PREFIX='diagnostics/2026-10-25-visual-diagnostics';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 p=subprocess.run(['git',*args],cwd=R,env=env,text=True,encoding='utf-8',errors='replace',capture_output=True)
 if p.returncode:raise RuntimeError('git '+args[0]+' failed: '+p.stderr[-1600:])
 return p.stdout.strip()
R.mkdir(exist_ok=True)
if not (R/'.git').exists():
 assert not list(R.iterdir()),'Publication path is not empty'
 git('init');git('remote','add','origin','https://github.com/Stasik1960/Dreamwalker.git')
assert not git('status','--porcelain'),'Uncommitted publication state requires review'
git('fetch','--depth=1','origin',BR);git('switch','--detach','FETCH_HEAD');parent=git('rev-parse','HEAD');assert parent=='a337f7e33a6a890f97b0712e7d8be7ff3168b9e1','Unexpected branch head; inspect before retrying'
git('config','user.name','MoondancerDW');git('config','user.email','mrscratch94@gmail.com')
D=R/PREFIX;D.mkdir(parents=True,exist_ok=True)
names=['REPORT-RU.txt','HANDOFF-RU.txt','findings.json','map-targets.json','target-geometry.json','legacy-targets.json','configuration-evidence.json','catalog-structure.json','source-recovery.json','map-recovery.json']
names += [p.name for p in O.glob('*.py')]
for name in names:shutil.copy2(O/name,D/name)
for name in ('screenshots','reference-models'):shutil.copytree(O/name,D/name,dirs_exist_ok=True)
source=O/'recovered/source/src/architecture/java/dev/dreamwalker/bloodbornedw/composite';ex=D/'source-excerpts';ex.mkdir(exist_ok=True)
for name in ['NativePhysicsBlock.java','NativeGridClient.java','SourceFallbackCarrierV1271.java','StaticArchitectureBlock.java','NativeGridRetainedWholeItems.java','CompositeClient.java','StaticArchitectureRenderView.java','StaticArchitectureClient.java']:
 shutil.copy2(source/name,ex/name)
shutil.copy2(O/'recovered/source/tools/native-grid/compiler/catalog.py',ex/'catalog.py')
(D/'README.md').write_text('# Диагностика визуала и выбора блока\n\nЧитайте [REPORT-RU.txt](REPORT-RU.txt), затем [HANDOFF-RU.txt](HANDOFF-RU.txt). Исправления не выполнялись. Неполные базовые меши и отсутствие предметов у fallback-носителей подтверждены; точная причина всех чёрных граней пока не установлена.\n\nЭта ветка содержит диагностические материалы, не новую сборку мода. Полные исходники, JAR и карта находятся в [релизе 25 октября](https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-physics-20261025).\n',encoding='utf-8')
files={str(p.relative_to(D)).replace('\\','/'):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(D.rglob('*')) if p.is_file()}
(D/'MANIFEST.json').write_text(json.dumps({'schemaVersion':1,'releaseCommit':parent,'diagnosticOnly':True,'files':files},ensure_ascii=False,indent=2),encoding='utf-8')
git('add','--force','--',PREFIX)
staged=git('diff','--cached','--name-only').splitlines();assert staged and all(p.startswith(PREFIX+'/') for p in staged),'Unexpected staged path'
git('commit','-m','Document visual mesh, lighting and creative pick diagnostics without fixes')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);remote=git('ls-remote','--heads','origin',BR).split()[0];assert remote==commit,'Remote publication not confirmed'
receipt={'status':'PUSHED_AND_REMOTE_HEAD_VERIFIED','commit':commit,'branch':BR,'files':len(files)+1,'reportUrl':'https://github.com/Stasik1960/Dreamwalker/blob/'+commit+'/'+PREFIX+'/REPORT-RU.txt','directoryUrl':'https://github.com/Stasik1960/Dreamwalker/tree/'+commit+'/'+PREFIX,'modAndWorldChanged':False}
(O/'publication-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False))
