import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');branch='codex/interaction-fixes-20261025';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*a):
 r=subprocess.run(['git',*a],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if r.returncode:raise RuntimeError('Git '+a[0]+': '+r.stderr[-1000:])
 return r.stdout.strip()
assert not git('status','--porcelain'),'Do not overwrite another checkout change'
if git('branch','--show-current')!=branch:
 if git('branch','--list',branch):git('switch',branch)
 else:git('switch','-c',branch)
owned=[
'gradle.properties','src/architecture/resources/fabric.mod.json',
'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/DwUtilityBlocks.java','src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/FaerieVeilBlock.java','src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/NoCollidePlacement.java',
'src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolItem.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolService.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolRotations.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolClient.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/NoCollideKeyClient.java',
'src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java','src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogObjectRenderer.java','src/architecture/java/dev/dreamwalker/bloodbornedw/composite/StaticArchitectureCatalog.java',
'src/architecture/resources/assets/bloodborne_dw/lang/ru_ru.json','src/architecture/resources/assets/bloodborne_dw/lang/en_us.json','src/architecture/resources/bloodborne_dw/remaining-catalogue.json','src/architecture/resources/bloodborne_dw/static-architecture-v1274.json']
owned += [str(p.relative_to(P)).replace('\\','/') for p in (P/'src/restorationqa').rglob('*') if p.is_file()]
owned += [str(p.relative_to(P)).replace('\\','/') for p in (P/'tools').glob('*interaction*.py')]
for pattern in ('bloodborne_dw/composite/source_shutter*.json','assets/bloodborne_dw/models/interactions/*.json','assets/bloodborne_dw/models/block/source_shutter*.json','assets/bloodborne_dw/models/item/source_shutter*.json','assets/bloodborne_dw/blockstates/source_shutter*.json','assets/bloodborne_dw/textures/interactions/*.png'):
 owned += [str(p.relative_to(P)).replace('\\','/') for p in (P/'src/architecture/resources').glob(pattern)]
paths=[]
(D/'source-files.json').write_text(json.dumps(sorted(set(owned)),ensure_ascii=False,indent=2),encoding='utf-8')
for rel in sorted(set(owned)):
 source=P/rel;assert source.is_file() and source.stat().st_size<99000000;target=R/'source-code'/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);paths.append(target.relative_to(R).as_posix())
for source in D.rglob('*'):
 if source.is_file() and source.suffix in ('.json','.md','.txt','.log','.png') and source.stat().st_size<3000000:
  target=R/'diagnostics/2026-10-25-interactions'/source.relative_to(D);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);paths.append(target.relative_to(R).as_posix())
git('add','--',*paths)
if git('diff','--cached','--name-only'):git('commit','-m','Fix utility block interactions, shutter windows and RP physical bounds')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+branch);assert git('ls-remote','--heads','origin',branch).split()[0]==commit
(D/'publication.json').write_text(json.dumps({'status':'CHECKPOINT_VERIFIED','branch':branch,'commit':commit,'requiredTestsPending':not (D/'acceptance.json').exists()},indent=2),encoding='utf-8')
