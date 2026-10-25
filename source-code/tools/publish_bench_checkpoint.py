import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');BR='codex/bench-seating-20261025';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 p=subprocess.run(['git',*args],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if p.returncode:raise RuntimeError('Git '+args[0]+' failed: '+p.stderr[-700:])
 return p.stdout.strip()
assert not git('status','--porcelain')
if git('ls-remote','--heads','origin',BR):git('fetch','--depth=1','origin',BR);git('switch','--detach','FETCH_HEAD')
else:git('switch','--detach','740523183e6b544db55aa37ba80657e839511245')
files=list((P/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite').glob('Bench*.java'))
files +=[P/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite'/n for n in ('CompositeArchitecture.java','CompositeClient.java')]
files +=[p for p in (P/'src/benchqa').rglob('*') if p.is_file()]
files +=[P/'build.gradle',P/'gradle.properties']+[p for p in (P/'tools').glob('*bench*.py')]
files +=[p for p in (P/'bench-evidence').rglob('*') if p.is_file() and p.suffix in ('.json','.java','.txt') and p.stat().st_size<10_000_000]
for p in files:
 dst=R/'source-code'/p.relative_to(P);dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
D=R/'diagnostics/2026-10-25-bench-seating';D.mkdir(parents=True,exist_ok=True)
accepted=(P/'bench-evidence/acceptance.json').exists() and json.loads((P/'bench-evidence/acceptance.json').read_text())['status']=='PASS'
status='READY: bench seating tests PASS; see REPORT-RU.txt.' if accepted else 'WORK IN PROGRESS: port old bench seating to visual-physics.3; game acceptance pending.'
(D/'STATUS.txt').write_text(status+'\nBaseline release: native-grid-city-visual-fixes-20261025\nMap is unchanged; geometry/physics resources must remain identical.\n',encoding='utf-8')
for n in ('REPORT-RU.txt','HANDOFF-RU.txt','acceptance.json'):
 if (P/'bench-evidence'/n).exists():shutil.copy2(P/'bench-evidence'/n,D/n)
if (P/'bench-release/ASSETS.json').exists():shutil.copy2(P/'bench-release/ASSETS.json',D/'ASSETS.json')
(D/'checkpoint-files.json').write_text(json.dumps({str(p.relative_to(P)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},indent=2),encoding='utf-8')
git('add','--force','--','source-code','diagnostics/2026-10-25-bench-seating')
if git('diff','--cached','--name-only'):git('commit','-m','Restore authored three-seat benches' if accepted else 'Checkpoint bench seat restoration and targeted QA')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);assert git('ls-remote','--heads','origin',BR).split()[0]==commit
(P/'bench-evidence/publication.json').write_text(json.dumps({'status':'READY' if accepted else 'WORK_IN_PROGRESS','branch':BR,'commit':commit},indent=2),encoding='utf-8');print('Pushed',commit)
