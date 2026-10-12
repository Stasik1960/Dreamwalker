"""Offline physics figures and coordinates for native QA; never game screenshots."""
import hashlib
import json
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon,Rectangle
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
 fig,ax=plt.subplots(figsize=(9,8),layout='constrained')
 for primitive in source:
  polygon=hull(primitive[:,axes]/16)
  if len(polygon)>=3:ax.add_patch(Polygon(polygon,facecolor='#aaaaaa',edgecolor='#555555',alpha=.28,lw=.5))
 for index,box in enumerate(boxes):
  low=np.asarray(box['from'])[list(axes)]/16;high=np.asarray(box['to'])[list(axes)]/16
  ax.add_patch(Rectangle(low,*(high-low),facecolor='#15945c',edgecolor='#086439',alpha=.4,lw=1.4));ax.text(*(low+(high-low)/2),str(index+1),ha='center',va='center',fontsize=9)
 ax.add_patch(Rectangle((0,0),1,1,fill=False,edgecolor='#206ac7',lw=1.5,linestyle='--'))
 allpoints=np.concatenate(source)[:,axes]/16;lo,hi=np.min(allpoints,axis=0),np.max(allpoints,axis=0);padding=(hi-lo)*.08+.2;ax.set_xlim(lo[0]-padding[0],hi[0]+padding[0]);ax.set_ylim(lo[1]-padding[1],hi[1]+padding[1]);ax.set_aspect('equal');ax.grid(alpha=.2)
 ax.set_xlabel('XYZ'[axes[0]]+' / blocks');ax.set_ylabel('XYZ'[axes[1]]+' / blocks');ax.set_title(f'Offline collision plan: {key}\nID {names.get(registry)}; yaw={yaw*45}°; {len(boxes)} large cuboids\nGrey: source geometry; green: default physics; blue: origin cell')
 file=OUT/(key+'-collision-plan.png');fig.savefig(file,dpi=150);plt.close(fig)
 proof.append({'key':key,'registryId':registry,'temporaryId':names.get(registry),'variant':0,'profile':'base','open':False,'rotation':yaw,'majorPhysicalCuboids':boxes,'file':str(file.relative_to(ROOT)).replace('\\','/'),'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'status':'PASS_OFFLINE_GEOMETRY_ONLY','nativePlayerAndCubeChecks':'NOT_RUN'})
(OUT/'priority-cases.json').write_bytes((json.dumps({'schemaVersion':1,'metadataSha256':hashlib.sha256((RES/'bloodborne_dw/imported-collision-v125.json').read_bytes()).hexdigest(),'cases':proof},ensure_ascii=False,indent=2)+'\n').encode('utf8'))
print(json.dumps({'status':'OFFLINE_QA_FIGURES_CREATED','figures':len(proof),'priorityRegistries':[row['registryId'] for row in proof]}))
