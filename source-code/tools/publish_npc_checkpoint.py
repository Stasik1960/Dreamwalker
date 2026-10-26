import hashlib,json,os,shutil,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';R=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/github-publication');branch='codex/npc-controls-20261026';env={**os.environ,'GIT_TERMINAL_PROMPT':'0','GCM_INTERACTIVE':'never'}
def git(*a):
 r=subprocess.run(['git',*a],cwd=R,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
 if r.returncode:raise RuntimeError('Git '+a[0]+': '+r.stderr[-1000:])
 return r.stdout.strip()
assert not git('status','--porcelain'),'Do not overwrite another checkout change'
if (D/'completion.json').exists():
 release=json.loads((D/'final-release.json').read_text());tag=release['tag'];refs=git('ls-remote','--tags','origin','refs/tags/'+tag,'refs/tags/'+tag+'^{}').splitlines();assert refs
 target=next((row.split()[0] for row in refs if row.split()[1].endswith('^{}')),refs[0].split()[0]);assert target==release['commit'],'Published source tag mismatch'
 (D/'published-tag-verified.json').write_text(json.dumps({'status':'PASS','tag':tag,'sourceCommit':target},indent=2),encoding='utf-8')
if git('branch','--show-current')!=branch:
 if git('branch','--list',branch):git('switch',branch)
 else:git('switch','-c',branch)
owned=['gradle.properties','src/architecture/resources/fabric.mod.json','src/architecture/resources/bloodborne_dw.npc.mixins.json',
'src/architecture/java/dev/dreamwalker/bloodbornedw/compat/OptionalMixinContract.java',
'src/architecture/java/dev/dreamwalker/bloodbornedw/compat/customnpc/CustomNpcMobRendererMixin.java',
'src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolService.java','src/architecture/java/dev/dreamwalker/bloodbornedw/tool/BbToolRotations.java',
'src/rp/java/dev/dreamwalker/bloodbornerp/mob/RpMobEntity.java','src/rp/java/dev/dreamwalker/bloodbornerp/client/NpcMobVisualBridge.java','src/rp/java/dev/dreamwalker/bloodbornerp/client/CatalogMobRenderer.java']
owned += [p.relative_to(P).as_posix() for p in (P/'src/restorationqa').rglob('*') if p.is_file()]
owned += ['src/rp/java/dev/dreamwalker/bloodbornerp/client/CorpseFloor.java']
owned += [p.relative_to(P).as_posix() for p in (P/'tools').glob('*npc*.py')]
paths=[]
readme=R/'README.md'
if readme.exists():
 text=readme.read_text(encoding='utf-8')
 if 'CURRENT-NPC-RU.md' not in text:readme.write_text('NPC и Custom NPC, visual-physics.8: [текущее состояние и восстановление](CURRENT-NPC-RU.md).\n\n'+text,encoding='utf-8');paths.append('README.md')
current=R/'CURRENT-NPC-RU.md';shutil.copy2(D/('HANDOFF-RU.md' if (D/'acceptance.json').exists() else 'NOTES-RU.md'),current);paths.append(current.relative_to(R).as_posix())
(D/'source-files.json').write_text(json.dumps(sorted(set(owned)),ensure_ascii=False,indent=2),encoding='utf-8')
for rel in sorted(set(owned)):
 source=P/rel;assert source.is_file() and source.stat().st_size<99000000;target=R/'source-code'/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);paths.append(target.relative_to(R).as_posix())
for source in D.rglob('*'):
 if source.is_file() and source.suffix in ('.json','.md','.txt','.log','.png') and source.stat().st_size<3000000:
  target=R/'diagnostics/2026-10-26-npc'/source.relative_to(D);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target);paths.append(target.relative_to(R).as_posix())
git('add','--',*paths)
if git('diff','--cached','--name-only'):git('commit','-m','Add passive NPC actors, tool controls, corpse lifecycle and Custom NPC models')
commit=git('rev-parse','HEAD');git('push','origin','HEAD:refs/heads/'+branch);assert git('ls-remote','--heads','origin',branch).split()[0]==commit
(D/'publication.json').write_text(json.dumps({'status':'CHECKPOINT_VERIFIED','branch':branch,'commit':commit,'requiredTestsPending':not (D/'acceptance.json').exists()},indent=2),encoding='utf-8')
