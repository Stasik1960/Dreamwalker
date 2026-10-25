"""Byte proof for reusing shader-off FPS evidence after the on-only GPU guards."""
import hashlib,json,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';H=T/'runtime-history'
old='eb32b536e6f746bcdc3d3c64ed326eebf61efa1efb79ffd5116f5ab1a243e679';new='337ad3113ea02fd9e59b0646167d1ed51c769f048277e3a0858b4c89d2cfceb5'
paths=[H/(v+'.jar') for v in (old,new)]
for path,value in zip(paths,(old,new)):assert hashlib.sha256(path.read_bytes()).hexdigest()==value
with zipfile.ZipFile(paths[0]) as a,zipfile.ZipFile(paths[1]) as b:
 assert set(a.namelist())==set(b.namelist());changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
 assert changed==['dev/dreamwalker/bloodbornedw/composite/CompositeGpuBatch.class'],changed
 result={'status':'ONLY_ACTIVE_SHADER_GPU_GUARDS_CHANGED','before':old,'after':new,'changed':changed,'unchanged':len(a.namelist())-len(changed),'sourceReason':'Only enqueueRetained and retainedSignature gained the existing active-Iris guard. When Iris shaders are off, the conditions are false and previous behavior is retained. Byte proof does not substitute for review of those two conditions.','reuseScope':'Shader-off14-view and Sodium0.5.13 centre9-view evidence; physical/server/serialization data unchanged as proven separately.'}
(T/'addons/toggle-delta-proof.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
