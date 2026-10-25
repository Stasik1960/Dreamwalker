import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');BR='codex/faerie-veil-20261025';D=P/'faerie-evidence';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*args):
 r=subprocess.run(['git',*args],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if r.returncode:raise RuntimeError('Git '+args[0]+': '+r.stderr[-500:])
 return r.stdout.rstrip('\r\n')
git('config','--local','core.longpaths','true')
dirty=git('status','--porcelain','--untracked-files=all')
owned={'README.md','build.gradle','gradle.properties','tools/publish_utility_checkpoint.py'}
owned.update('src/architecture/java/dev/dreamwalker/bloodbornedw/'+n for n in ['architecture/DwUtilityBlocks.java','composite/CompositeArchitecture.java','composite/CompositeClient.java','composite/NativePlayerBreak.java','debug/UnifiedCreativeCatalogue.java']+['composite/mixin/'+n+'.java' for n in ('CompositeChunkPublicationMixin','CompositeShapeMixin','DwMarkerParticleMixin','NativeVisualChunkMixin','NativeVisualChunkPacketMixin','NativeVisualSerializationMixin','NativeVisualWorldChunkMixin','NoCollideBlockItemMixin')])
if dirty:
 for row in dirty.splitlines():
  path=row[3:]
  if path.startswith('diagnostics/2026-10-25-faerie-veil/'):continue
  assert path.startswith('source-code/'),path
  src=P/path[len('source-code/'):];dst=R/path
  relative=path[len('source-code/'):]
  assert src.is_file() and dst.is_file() and (src.read_bytes()==dst.read_bytes() or relative in owned or '/FauxWater' in relative or relative.startswith('src/utilityqa/') or relative.startswith('faerie-evidence/')),'Unrelated checkout change: '+path
 remote=git('ls-remote','--heads','origin',BR)
 assert git('rev-parse','HEAD')==(remote.split()[0] if remote else '5249d169fd8e83994dadbca330681886b086a347')
elif git('ls-remote','--heads','origin',BR):git('fetch','--depth=1','origin',BR);git('switch','--detach','FETCH_HEAD')
else:
 if git('rev-parse','HEAD')!='5249d169fd8e83994dadbca330681886b086a347' and not git('branch','--list','codex/faerie-full-source-local-20261025'):git('branch','codex/faerie-full-source-local-20261025','HEAD')
 git('switch','--detach','5249d169fd8e83994dadbca330681886b086a347')
# Large generated native meshes belong to the complete source ZIP release,
# never to an ordinary Git blob. Publish only this task's code/resource delta.
files=[P/n for n in owned if n!='tools/publish_utility_checkpoint.py']
files +=list((P/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture').glob('FauxWater*.java'))
files +=list((P/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin').glob('FauxWater*.java'))
files +=[p for p in (P/'src/utilityqa').rglob('*') if p.is_file()]
files +=list((P/'tools').glob('*utility*.py'))
assets=P/'src/architecture/resources/assets/bloodborne_dw'
files +=[P/'src/architecture/resources/bloodborne_dw.composite.mixins.json']
files +=[assets/'textures/block/dw_darkness.png']
for name in ('dw_darkness','dw_fake_water'):
 files +=[assets/'blockstates'/(name+'.json'),assets/'models/block'/(name+'.json'),assets/'models/item'/(name+'.json')]
files +=[assets/'lang'/n for n in ('ru_ru.json','en_us.json')]
files +=list((P/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture').glob('FaerieVeil*.java'))
files +=[p for p in (P/'src/faerieqa').rglob('*') if p.is_file()]
files +=list((P/'tools').glob('*faerie*.py'))
files +=[assets/'blockstates/dw_faerie_veil.json',assets/'models/block/dw_faerie_veil.json',assets/'models/item/dw_faerie_veil.json']
files +=[assets/'textures/block/faerie_veil.png',assets/'textures/block/faerie_veil.png.mcmeta']
for tile in range(64):
 for folder,ext in [('textures/block/faerie_veil','.png'),('textures/block/faerie_veil','.png.mcmeta'),('models/block/faerie_veil','.json')]:
  obsolete=R/'source-code/src/architecture/resources/assets/bloodborne_dw'/folder/(f'tile_{tile:02d}'+ext)
  if obsolete.exists():
   assert obsolete.resolve().is_relative_to((R/'source-code/src/architecture/resources/assets/bloodborne_dw').resolve()) and not obsolete.is_symlink()
   obsolete.unlink()
files +=[P/n for n in ('README.md','build.gradle','gradle.properties')]
files +=[p for p in D.rglob('*') if p.is_file() and p.suffix in ('.txt','.json') and p.stat().st_size<10000000]
for p in files:
 target=R/'source-code'/p.relative_to(P);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
out=R/'diagnostics/2026-10-25-faerie-veil';out.mkdir(parents=True,exist_ok=True)
for name in ('TASK-RU.txt','REPORT-RU.txt','HANDOFF-RU.txt','acceptance.json'):
 if (D/name).exists():shutil.copy2(D/name,out/name)
accepted=(D/'acceptance.json').exists() and json.loads((D/'acceptance.json').read_text())['status']=='PASS'
(out/'STATUS.txt').write_text(('READY: selected faerie veil tested.' if accepted else 'WORK IN PROGRESS: faerie veil .5; game acceptance pending.')+'\nChosen variant 3: Faerie mist; no block entity or per-block ticker.\n',encoding='utf-8')
if (P/'faerie-release/ASSETS.json').exists():shutil.copy2(P/'faerie-release/ASSETS.json',out/'ASSETS.json')
git('add','--force','--','source-code','diagnostics/2026-10-25-faerie-veil')
if git('diff','--cached','--name-only'):git('commit','-m','Add approved faerie veil with shared wall animation' if accepted else 'Checkpoint selected faerie veil and targeted QA')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+BR);assert git('ls-remote','--heads','origin',BR).split()[0]==commit
(D/'publication.json').write_text(json.dumps({'status':'READY' if accepted else 'WORK_IN_PROGRESS','branch':BR,'commit':commit},indent=2),encoding='utf-8')
