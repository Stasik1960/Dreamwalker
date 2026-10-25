"""Reuse accepted physics/data checks only when those bytes are unchanged."""
import hashlib,json,shutil,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
old=T/'runtime-history/103f7f584a5f48d1a1aa5d66cf02ab654939f1fcdf187eeb56176c7840914896.jar'
new=T/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.4-visual-physics.1.jar'
allowed=('dev/dreamwalker/bloodbornedw/composite/StaticArchitectureClient','dev/dreamwalker/bloodbornedw/composite/CompositeTerrain','dev/dreamwalker/bloodbornedw/composite/CompositeGpuBatch')
new_prefixes=('dev/dreamwalker/bloodbornedw/composite/NativeVisualSodium','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureFabricEmitter','dev/dreamwalker/bloodbornedw/composite/StaticArchitecturePrelitContext','dev/dreamwalker/bloodbornedw/composite/StaticArchitectureShaderCompat','dev/dreamwalker/bloodbornedw/composite/mixin/NativeVisualSodium','dev/dreamwalker/bloodbornedw/composite/mixin/StaticArchitectureIndiumLighting','dev/dreamwalker/bloodbornedw/composite/mixin/StaticArchitectureSodiumBounds')
changed=[];same=0
with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as b:
 aa={n for n in a.namelist() if not n.endswith('/')};bb={n for n in b.namelist() if not n.endswith('/')};assert not aa-bb
 for name in sorted(aa):
  before=hashlib.sha256(a.read(name)).hexdigest();after=hashlib.sha256(b.read(name)).hexdigest()
  if before==after:same+=1;continue
  assert name=='native-grid.mixins.json' or any(name.startswith(prefix) for prefix in allowed),name
  changed.append({'entry':name,'before':before,'after':after})
 added=sorted(bb-aa);assert all(any(name.startswith(prefix) for prefix in new_prefixes) for name in added)
digest=sha(new);target=T/('runtime-history/'+digest+'.jar')
if not target.exists():shutil.copy2(new,target)
assert sha(target)==digest
report={'status':'CLIENT_RENDERER_EXTENSION_PHYSICS_SERIALIZATION_NETWORK_AND_DATA_BYTE_IDENTICAL','before':{'path':str(old),'sha256':sha(old)},'after':{'path':str(target),'sha256':digest},'identicalExistingEntries':same,'changedExistingEntries':changed,'addedEntries':added,
 'commonException':'CompositeTerrain class changes only its client Sodium feature gate. Server environment preserves the previous enabled setting. Its eligibility/rejection algorithm and all physical/persistence/network/catalog classes are unchanged.','acceptedEvidenceReused':['server-checks.json','integration-checks.json','map-remap.json','region-repack.json'],'newValidationRequired':'Actual renderer/modpack routes, shader on/off and shadow positions.'}
(T/'addons/client-compat-delta-proof.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
