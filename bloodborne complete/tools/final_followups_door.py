"""Repair only user-specified single door90960; retain original model and IDs."""
from pathlib import Path
import copy,json
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/architecture/resources';OUT=ROOT/'build/final-followups-20261014'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
model=RES/'assets/bloodborne_dw/models/remaining/source/minecraft/block/stairs/andesite_stairs_top.json'
spec_path=RES/'bloodborne_dw/composite/source_stairs_andesite_stairs_top.json'
original=read(model);spec=read(spec_path)
assert spec['id']=='bloodborne_dw:source_stairs_andesite_stairs_top' and len(spec['variants'])==1
assert len(original['elements'])==13
leaf=original['elements'][12];assert leaf['from']==[-5,-15,7] and leaf['to']==[21,32,9]
frame=copy.deepcopy(original);frame['elements']=copy.deepcopy(original['elements'][:12])
panel=copy.deepcopy(original);panel['elements']=[copy.deepcopy(leaf)]
# The wooden leaf is a volume, not an intentionally one-sided decal. Reuse its
# own existing wood image for the narrow edges; no PNG or authored face changes.
for side in ('up','down','east','west'):
    panel['elements'][0]['faces'][side]=copy.deepcopy(leaf['faces']['north'])
frame_name='bloodborne_dw:remaining/final_followups/door_90960_frame'
leaf_name='bloodborne_dw:remaining/final_followups/door_90960_leaf'
write(RES/'assets/bloodborne_dw/models/remaining/final_followups/door_90960_frame.json',frame)
write(RES/'assets/bloodborne_dw/models/remaining/final_followups/door_90960_leaf.json',panel)
variant=spec['variants'][0];offset=variant['poses']['closed']['parts'][0]['offset'];assert offset==[0,16.0,0]
fixed={'model':frame_name,'offset':offset};closed_leaf={'model':leaf_name,'offset':offset}
open_leaf={**closed_leaf,'yaw':90,'pivot':[-5,0,8]}
columns=[{'from':[-8,0,1],'to':[-2,48,15]},{'from':[18,0,1],'to':[24,48,15]}]
closed_box={'from':[-5,1,7],'to':[21,48,9]}
open_box={'from':[-6,1,8],'to':[-4,48,34]}
for opened,part,box in [('closed',closed_leaf,closed_box),('open',open_leaf,open_box)]:
    geometry=copy.deepcopy(columns)+[box]
    variant['poses'][opened]={'parts':[fixed,part],'collision':geometry,'selection':copy.deepcopy(geometry)}
spec['openable']=True;spec['functionalDoorPhysics']=True;spec['simplePhysicalCollision']=False
spec['collisionPolicy']='User90960: two fixed ordinary frame columns and one hinged wooden leaf; same simple boxes for physical collision and aim. Closed source geometry/offset preserved; only the leaf opens90 degrees.'
write(spec_path,spec)
catalogue=RES/'bloodborne_dw/remaining-catalogue.json';doc=read(catalogue);hidden=[]
for row in doc['objects']:
    if row['path'] in ('source__wall_dec_top','source_hold_wall_lantern_4'):
        row['conversionOnly']=True;hidden.append(row['path'])
assert len(hidden)==2
write(catalogue,doc)
write(OUT/'door-and-requested-duplicates.json',{'status':'APPLIED_PENDING_NATIVE_VALIDATION','doorNumericId':'90960','originalModelUnmodified':True,'closedExistingFacesAndCoordinatesUnmodified':True,'addedLeafEdgeFaces':['up','down','east','west'],'hingeSourcePivot':[-5,0,8],'openYaw':90,'registryIdsRemoved':[],'hiddenCatalogueOnly':hidden,'oldMapStatesAndModelsPreserved':True})
