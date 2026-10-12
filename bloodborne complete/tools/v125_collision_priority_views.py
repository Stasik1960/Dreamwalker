"""Offline physics figures and coordinates for native QA; never game screenshots."""
import hashlib
import json
import html
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from simplify_imported_collisions import rotate_clip_boxes
from v125_collision_geometry import oriented,solid_parts
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.5-collisions'
table=json.loads((RES/'bloodborne_dw/imported-collision-v125.json').read_text());models=ModelGeometry()
cat=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text());names={row['registryId']:row.get('temporaryId') for row in cat['entries'] if 'registryId' in row}
aliases=json.loads((RES/'bloodborne_dw/imported-type-aliases.json').read_text());retired={row['oldRegistryId'] for row in aliases['mappings'] if row['oldRegistryId']!=row['newRegistryId']}
fixtures=[('thick-half-arch','source_stairs_axed_exposed_cut_copper_stairs_outer_3',0,(0,1)),('thin-half-arch','source_stairs_waxed_oxidized_cut_copper_stairs_outer_right',0,(0,1)),('native-rescaled-panel','source_allium',0,(0,1))]
for registry in table['objects']:
 if registry in retired:continue
 spec=json.loads((RES/'bloodborne_dw/composite'/(registry.split(':')[1]+'.json')).read_text());p=spec['variants'][0]['poses']['closed']
 if len(p['collision'])!=1:continue
 b=p['collision'][0];span=np.asarray(b['to'])-b['from']
 if span[0]>=32 and span[2]>=32 and span[1]<=24:
  fixtures.append(('diagonal-large-platform',registry.split(':')[1],1,(0,2)));break
def hull(points):
 points=sorted(set(map(tuple,points)))
 def cross(o,a,b):return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0])
 lower=[]
 for p in points:
  while len(lower)>=2 and cross(lower[-2],lower[-1],p)<=0:lower.pop()
  lower.append(p)
 upper=[]
 for p in reversed(points):
  while len(upper)>=2 and cross(upper[-2],upper[-1],p)<=0:upper.pop()
  upper.append(p)
 return lower[:-1]+upper[:-1]
proof=[]
for key,path,yaw,axes in fixtures:
 registry='bloodborne_dw:'+path;spec=json.loads((RES/'bloodborne_dw/composite'/(path+'.json')).read_text());pose=spec['variants'][0]['poses']['closed'];source=oriented(solid_parts(pose,models,'base',1.),yaw)
 row=table['shapes'][table['objects'][registry][0]['closedBase']][yaw];boxes=rotate_clip_boxes(row['collision'],0,crop=True)
 allpoints=np.concatenate(source)[:,axes]/16;lo,hi=np.min(allpoints,axis=0),np.max(allpoints,axis=0);padding=(hi-lo)*.08+.2;lo-=padding;hi+=padding
 scale=min(1000/(hi[0]-lo[0]),950/(hi[1]-lo[1]));origin=(90+(1000-(hi[0]-lo[0])*scale)/2,145+(950-(hi[1]-lo[1])*scale)/2)
 def point(p):return (origin[0]+(p[0]-lo[0])*scale,origin[1]+(hi[1]-p[1])*scale)
 svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1200" viewBox="0 0 1200 1200"><rect width="1200" height="1200" fill="white"/>',f'<text x="40" y="40" font-family="sans-serif" font-size="26">Offline default collision diagram: {html.escape(key)}</text>',f'<text x="40" y="78" font-family="sans-serif" font-size="21">ID {names.get(registry)} / yaw {yaw*45} degrees / {len(boxes)} large cuboids</text>','<text x="40" y="113" font-family="sans-serif" font-size="18">Grey = source volumes; green = physics; blue = origin cell. Native checks NOT_RUN.</text>']
 for axis in (0,1):
  for tick in range(int(np.floor(lo[axis])),int(np.ceil(hi[axis]))+1):
   a,b=lo.copy(),hi.copy();a[axis]=b[axis]=tick;pa,pb=point(a),point(b);svg.append(f'<line x1="{pa[0]}" y1="{pa[1]}" x2="{pb[0]}" y2="{pb[1]}" stroke="#dddddd"/>')
 def polygon(points,fill,stroke,opacity):
  coordinates=' '.join(f'{x:.4f},{y:.4f}' for x,y in map(point,points));svg.append(f'<polygon points="{coordinates}" fill="{fill}" fill-opacity="{opacity}" stroke="{stroke}" stroke-width="1.5"/>')
 for primitive in source:
  projected=hull(primitive[:,axes]/16)
  if len(projected)>=3:polygon(projected,'#aaaaaa','#666666',.28)
 for index,box in enumerate(boxes):
  low=np.asarray(box['from'])[list(axes)]/16;high=np.asarray(box['to'])[list(axes)]/16
  polygon([low,[high[0],low[1]],high,[low[0],high[1]]],'#15945c','#086439',.4);x,y=point((low+high)/2);svg.append(f'<text x="{x}" y="{y}" font-family="sans-serif" font-size="16">{index+1}</text>')
 polygon([(0,0),(1,0),(1,1),(0,1)],'none','#206ac7',0)
 svg.append(f'<text x="40" y="1170" font-family="sans-serif" font-size="20">Coordinates: {"XYZ"[axes[0]]} / {"XYZ"[axes[1]]} in blocks. Models, UV and IDs are unchanged.</text></svg>')
 file=OUT/(key+'-collision-plan.svg');file.write_bytes(('\n'.join(svg)+'\n').encode('utf8'))
 proof.append({'key':key,'registryId':registry,'temporaryId':names.get(registry),'variant':0,'profile':'base','open':False,'rotation':yaw,'majorPhysicalCuboids':boxes,'file':str(file.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'status':'PASS_OFFLINE_GEOMETRY_ONLY','nativePlayerAndCubeChecks':'NOT_RUN'})
(OUT/'priority-cases.json').write_bytes((json.dumps({'schemaVersion':1,'metadataSha256':hashlib.sha256((RES/'bloodborne_dw/imported-collision-v125.json').read_bytes()).hexdigest(),'cases':proof},ensure_ascii=False,indent=2)+'\n').encode('utf8'))
print(json.dumps({'status':'OFFLINE_QA_FIGURES_CREATED','figures':len(proof),'priorityRegistries':[row['registryId'] for row in proof]}))
