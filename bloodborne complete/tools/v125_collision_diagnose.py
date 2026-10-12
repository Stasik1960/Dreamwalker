import json
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from v125_collision_geometry import *
from v125_collision_verify import samples
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources'
table=json.loads((ROOT/'build/v125-collision-preview.json').read_text());proof=json.loads((ROOT/'docs/v12.5-collisions/offline-proof.json').read_text());models=ModelGeometry()
for fail in proof['failures'][:12]:
 c=fail['case'];spec=json.loads((RES/'bloodborne_dw/composite'/(c['registryId'].split(':')[1]+'.json')).read_text());pose=spec['variants'][c['variant']]['poses'].get(c['pose'],spec['variants'][c['variant']]['poses']['closed']);solids=oriented(solid_parts(pose,models,c['profile'],1.),c['rotation']);boxes=table['shapes'][table['objects'][c['registryId']][c['variant']][c['pose']+c['profile'].title()]][c['rotation']]['collision'];violations=[]
 for b in boxes:
  p=samples(b);bad=p[~inside(p,solids)]
  for point in bad:
   distances=[]
   for s in solids:
    o=s[0];basis=np.column_stack((s[4]-o,s[2]-o,s[1]-o));co=np.linalg.solve(basis,point-o);cl=np.clip(co,0,1);distances.append(float(np.linalg.norm(basis@(co-cl))))
   violations.append(min(distances))
 print(json.dumps({'registry':c['registryId'],'rotation':c['rotation'],'bad':len(violations),'maxDistanceModelUnits':max(violations),'minDistanceModelUnits':min(violations)}))
spec=json.loads((RES/'bloodborne_dw/composite/source_allium.json').read_text());pose=spec['variants'][0]['poses']['closed'];s=solid_parts(pose,models,'base',1.);print(json.dumps({'allium':{'finiteSolids':len(s),'sourceEnvelope':dict(zip(('from','to'),[v.tolist() for v in envelope(s)])),'old':pose['collision']}}))
