import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];O=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics');R=O/'github-publication';BR='codex/visual-compat-fixes-20261025';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 r=subprocess.run(['git',*args],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
 if r.returncode:raise RuntimeError('git '+args[0]+': '+r.stderr[-1000:])
 return r.stdout.strip()
assert not git('status','--porcelain'),'Review uncommitted publication state before retrying'
git('fetch','--depth=1','origin',BR);git('switch','--detach','FETCH_HEAD')
files=[p for d in ('src/architecture/java','src/rp/java','src/nativegridexport','src/nativegridqa') for p in (P/d).rglob('*') if p.is_file()]
files += [P/'build.gradle',P/'gradle.properties',P/'src/architecture/resources/native-grid.mixins.json']
files += [p for p in (P/'tools').glob('*fix*.py')]+[P/'tools/bootstrap_fix_runtime.py']
for p in files:
 dst=R/'source-code'/p.relative_to(P);dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
D=R/'diagnostics/2026-10-25-fixes';D.mkdir(parents=True,exist_ok=True)
shutil.copy2(O.parent/'2026-10-25-compatibility-audit/REPORT-RU.txt',D/'COMPATIBILITY-AUDIT-RU.txt')
(D/'STATUS-RU.txt').write_text('НЕЗАВЕРШЁННЫЙ ЧЕКПОИНТ. Сборка и игровые проверки ещё не завершены. Не устанавливать как готовый релиз.\nРабочий проект: '+str(P)+'\nТекущая работа: визуальные дополнения без перезаписи карты; средняя кнопка; nullable NBT; WorldEdit; BEFORE/AFTER; preview BlockView; reach; no-collide finally; диагностика теней.\nИсходная версия/ассеты: релиз native-grid-city-visual-physics-20261025.\nПункт Sodium без Indium исключён пользователем. Проверки функциональные, без FPS-бенчмарка, ограничение памяти.\n',encoding='utf-8')
manifest={str(p.relative_to(P)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in files};(D/'checkpoint-files.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
git('add','--force','--','source-code','diagnostics/2026-10-25-fixes')
if not git('diff','--cached','--name-only'):print('Checkpoint unchanged');raise SystemExit(0)
git('commit','-m','Checkpoint visual and compatibility fixes in progress');commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);assert git('ls-remote','--heads','origin',BR).split()[0]==commit
(P/'build-tools/checkpoint-publication.json').write_text(json.dumps({'commit':commit,'branch':BR,'status':'PUSHED_VERIFIED_WORK_IN_PROGRESS'},indent=2),encoding='utf-8');print('Checkpoint pushed',commit)
