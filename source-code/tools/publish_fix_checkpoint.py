import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];O=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics');R=O/'github-publication';BR='codex/visual-compat-fixes-20261025';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 r=subprocess.run(['git',*args],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
 if r.returncode:raise RuntimeError('git '+args[0]+': '+r.stderr[-1000:])
 return r.stdout.strip()
assert not git('status','--porcelain'),'Review uncommitted publication state before retrying'
git('fetch','--depth=1','origin',BR);git('switch','--detach','FETCH_HEAD')
obsolete=R/'source-code/src/fixqa/java/dev/dreamwalker/bloodbornedw/fixqa/FixClientAccess.java'
if obsolete.exists():
 assert not obsolete.is_symlink() and obsolete.resolve().is_relative_to(R.resolve())
 assert obsolete.read_text(encoding='utf-8').startswith('package dev.dreamwalker.bloodbornedw.fixqa;')
 obsolete.unlink()
obsolete=R/'source-code/src/architecture/java/dev/dreamwalker/bloodbornedw/composite/NativeAoDiagnostics.java'
if obsolete.exists():
 assert not obsolete.is_symlink() and obsolete.resolve().is_relative_to(R.resolve()) and obsolete.read_text(encoding='utf-8').startswith('package dev.dreamwalker.bloodbornedw.composite;')
 obsolete.unlink()
files=[p for d in ('src/architecture/java','src/rp/java','src/nativegridexport','src/nativegridqa','src/fixqa') for p in (P/d).rglob('*') if p.is_file()]
files += [P/'build.gradle',P/'gradle.properties',P/'src/architecture/resources/native-grid.mixins.json']
files += [P/'README.md',P/'settings.gradle',P/'gradlew',P/'gradlew.bat',*list((P/'gradle/wrapper').glob('*'))]
files += [P/'src/architecture/resources/bloodborne_dw.composite.mixins.json']
files += list((P/'src/architecture/resources/assets/bloodborne_dw/models/item').glob('source_fallback*carrier.json'))
repair=P/'src/architecture/resources/assets/bloodborne_dw/native_grid/visual-repairs.json'
if repair.exists():files.append(repair)
files += [p for p in (P/'tools').glob('*fix*.py')]+[P/'tools/bootstrap_fix_runtime.py']
files += [p for p in (P/'fix-evidence').rglob('*') if p.is_file() and p.suffix in ('.json','.jsonl','.txt') and p.stat().st_size<10_000_000]
for p in files:
 dst=R/'source-code'/p.relative_to(P);dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
D=R/'diagnostics/2026-10-25-fixes';D.mkdir(parents=True,exist_ok=True)
shutil.copy2(O.parent/'2026-10-25-compatibility-audit/REPORT-RU.txt',D/'COMPATIBILITY-AUDIT-RU.txt')
for name in ('REPORT-RU.txt','HANDOFF-RU.txt'):
 if (P/'fix-evidence'/name).exists():shutil.copy2(P/'fix-evidence'/name,D/name)
for stage,scene,label in (('client-2',0,'fixed-wall'),('client-2',1,'fixed-books'),('client-2',2,'fixed-pick-wall'),('client-2',3,'before-balustrade'),('client-2',6,'before-stairs'),('client-2',None,'statistics-items-skillet'),('client-3',3,'after-balustrade'),('client-5',6,'after-stairs')):
 name='statistics-items-skillet.png' if scene is None else 'fixed-'+str(scene)+'.png';source=P/'fix-evidence'/stage/'screenshots'/name
 if source.exists():(D/'frames').mkdir(exist_ok=True);shutil.copy2(source,D/'frames'/(label+'.png'))
(D/'STATUS-RU.txt').write_text('НЕЗАВЕРШЁННЫЙ ЧЕКПОИНТ. Сборка и игровые проверки ещё не завершены. Не устанавливать как готовый релиз.\nРабочий проект: '+str(P)+'\nТекущая работа: визуальные дополнения без перезаписи карты; средняя кнопка; nullable NBT; WorldEdit; BEFORE/AFTER; preview BlockView; reach; no-collide finally; диагностика теней.\nИсходная версия/ассеты: релиз native-grid-city-visual-physics-20261025.\nПункт Sodium без Indium исключён пользователем. Проверки функциональные, без FPS-бенчмарка, ограничение памяти.\n',encoding='utf-8')
manifest={str(p.relative_to(P)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in files};(D/'checkpoint-files.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
accept=P/'fix-evidence/acceptance.json';accepted=accept.exists() and json.loads(accept.read_text(encoding='utf-8'))['status']=='PASS'
if accepted:
 (D/'STATUS-RU.txt').write_text('ГОТОВО: visual-physics.2. Функциональные проверки PASS.\nКарта прежняя, повторная конвертация не нужна.\nСм. REPORT-RU.txt, HANDOFF-RU.txt и acceptance.json.\nРелиз: https://github.com/Stasik1960/Dreamwalker/releases/tag/native-grid-city-visual-fixes-20261025\n',encoding='utf-8')
 if (P/'fix-release/ASSETS.json').exists():shutil.copy2(P/'fix-release/ASSETS.json',D/'ASSETS.json')
 shutil.copy2(accept,D/'acceptance.json')
git('add','--force','--','source-code','diagnostics/2026-10-25-fixes')
if not git('diff','--cached','--name-only'):print('Checkpoint unchanged');raise SystemExit(0)
git('commit','-m','Deliver verified visual-physics.2 hotfix and test evidence' if accepted else 'Checkpoint visual and compatibility fixes in progress');commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);assert git('ls-remote','--heads','origin',BR).split()[0]==commit
(P/'build-tools/checkpoint-publication.json').write_text(json.dumps({'commit':commit,'branch':BR,'status':'PUSHED_VERIFIED_READY_RELEASE' if accepted else 'PUSHED_VERIFIED_WORK_IN_PROGRESS'},indent=2),encoding='utf-8');print('Checkpoint pushed',commit)
