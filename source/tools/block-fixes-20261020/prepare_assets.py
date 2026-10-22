from common import *
import copy

def backup(p):
 dest=OUT/'source-before'/p.relative_to(PROJECT)
 if not dest.exists() and p.exists():dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(p.read_bytes())
def put(p,d):backup(p);write(p,d)
def textures_for(top):return model(top['model']).get('textures',{})
def resolve_texture(value,textures):
 while value.startswith('#'):value=textures[value[1:]]
 return value
def main():
 log_to('prepare-assets')
 audit=read(OUT/'materials.json');slabs=[];materials=[];floorrows=[];patches=[];model_evidence=[]
 for id in ['source_slabs_stone_slab_manwhole','source_slabs_stone_slab_manwhole_top']:
  d=read(RES/'bloodborne_dw/composite'/(id+'.json'))
  for i,v in enumerate(d['variants']):
   b,top=bounds(v['poses']['closed']['parts'])
   audit['slabs'].append({'registryId':d['id'],'variant':i,'name':d['displayName'],'temporaryId':'90915' if id.endswith('manwhole') else '90916','kind':'pavement','bounds':b,'top':top,'sourceStateRefs':v.get('sourceStateRefs',[])})
 for t in audit['slabs']:
  # Loose stones, broken rubble and floor decals with raised debris are not
  # half-block pavement. Keep that artwork out of this conversion.
  if t['kind']=='plane':
   m=model(t['top']['model']);e=t['top']['element'];r=e.get('rotation',{})
   if len(m.get('elements',[]))!=1 or abs(t['bounds'][1][1]-t['bounds'][0][1])>.02 or r.get('axis','y')!='y' or r.get('angle',0)%90:continue
  slabs.append(t)
  b=t['bounds'];xs=range(round(b[0][0]/16),round(b[1][0]/16));zs=range(round(b[0][2]/16),round(b[1][2]/16))
  cells=sorted(((x,z) for x in xs for z in zs),key=lambda c:(abs(c[0])+abs(c[1]),c))
  assert cells and len(cells)<=16,(t['registryId'],cells)
  top=t['top'];sourcefaces=top['element']['faces'];textures=textures_for(top)
  for n,(x,z) in enumerate(cells):
   key=t['registryId']+'/'+str(t['variant'])+'/'+str(x)+'/'+str(z)
   name='restored_slab_'+hashlib.sha256(key.encode()).hexdigest()[:16]
   id='bloodborne_dw:'+name
   uv=top['face'].get('uv',[0,0,16,16]);a=top['element']['from'];c=top['element']['to']
   # The retained horizontal source planes use cardinal, axis-aligned UVs.
   # Crop their atlas region by the tile instead of repeating a three-block
   # texture once on every native slab.
   def uv_at(X,Z):
    p=[X,top['element']['to'][1],Z];offset=top['part'].get('offset',[0,0,0]);p=[p[i]-offset[i] for i in range(3)]
    if top['part'].get('yaw'):p=turn(p,top['part']['yaw'],'y',top['part'].get('pivot',[8,8,8]))
    r=top['element'].get('rotation',{})
    if r.get('angle'):p=turn(p,-r['angle'],r['axis'],r['origin'])
    return ((p[0]-a[0])/(c[0]-a[0]),(p[2]-a[2])/(c[2]-a[2]))
   corners=[uv_at(X,Z) for X in [x*16,(x+1)*16] for Z in [z*16,(z+1)*16]]
   u0=min(p[0] for p in corners);u1=max(p[0] for p in corners);v0=min(p[1] for p in corners);v1=max(p[1] for p in corners)
   cropped=[uv[0]+(uv[2]-uv[0])*u0,uv[1]+(uv[3]-uv[1])*v0,uv[0]+(uv[2]-uv[0])*u1,uv[1]+(uv[3]-uv[1])*v1]
   if t['kind']=='half':cropped=uv
   for part,lo,hi in [('bottom',0,8),('top',8,16),('double',0,16)]:
    faces={}
    for side in ['down','up','north','south','west','east']:
     f=copy.deepcopy(sourcefaces.get(side,top['face']));f['texture']=resolve_texture(f['texture'],textures)
     f.pop('cullface',None)
     if t['kind']=='plane' or side=='up' or side=='down' and side not in sourcefaces:f['uv']=cropped
     if side not in ['up','down'] or side=='up' and hi==16 or side=='down' and lo==0:f['cullface']=side
     faces[side]=f
    d={'ambientocclusion':True,'textures':{'particle':top['texture']},'elements':[{'from':[0,lo,0],'to':[16,hi,16],'faces':faces}]}
    if part=='bottom':d['parent']='minecraft:block/block'
    put(RES/'assets/bloodborne_dw/models/block/restored_slabs'/(name+'_'+part+'.json'),d)
   variants={}
   for typ in ['bottom','top','double']:
    for direction,yaw in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+direction+',type='+typ]={'model':'bloodborne_dw:block/restored_slabs/'+name+'_'+typ,**({'y':yaw} if yaw else {})}
   put(RES/'assets/bloodborne_dw/blockstates'/(name+'.json'),{'variants':variants})
   put(RES/'assets/bloodborne_dw/models/item'/(name+'.json'),{'parent':'bloodborne_dw:block/restored_slabs/'+name+'_bottom'})
   display=t['name']+' · плита'+(' ['+str(t['temporaryId'])+']' if t['temporaryId'] else '')
   row={'id':id,'source':t['registryId'],'variant':t['variant'],'cell':[x,z],'name':display,'primary':n==0}
   materials.append(row)
  model_evidence.append({'source':t['registryId'],'variant':t['variant'],'model':top['model'],'sourceFaces':list(sourcefaces),'sidePolicy':'Original face when available; otherwise the same cropped top material','tiles':len(cells)})
 for id in audit['woodenFloors']:
  d=read(RES/'bloodborne_dw/composite'/(id.split(':')[1]+'.json'))
  for i,v in enumerate(d['variants']):
   b,top=bounds(v['poses']['closed']['parts']);b=[[value/16 for value in p] for p in b]
   if b[1][1]-b[0][1]<1/256:
    y=b[1][1];y=round(y*16)/16 if abs(y-round(y*16)/16)<1/256 else y
    b[0][1]=y-1/16;b[1][1]=y
   floorrows.append({'source':id,'variant':i,'bounds':b})
 put(RES/'bloodborne_dw/restored-material-slabs.json',{'schemaVersion':1,'materials':materials})
 put(RES/'bloodborne_dw/wood-floor-collision.json',floorrows)
 for locale in ['ru_ru','en_us']:
  p=RES/'assets/bloodborne_dw/lang'/(locale+'.json');d=read(p) if p.exists() else {}
  for r in materials:d['block.bloodborne_dw.'+r['id'].split(':')[1]]=r['name']
  put(p,d)
 write(OUT/'selected-materials.json',{'slabs':slabs,'woodenFloors':audit['woodenFloors'],'materials':materials,'floorBounds':floorrows})
 write(OUT/'source-model-evidence.json',model_evidence)
 print('SLAB ART VARIANTS',len(slabs),'NATIVE MATERIALS',len(materials),'WOODEN FLOOR VARIANTS',len(floorrows))
 print('SOURCES',[(t['registryId'],t['variant']) for t in slabs])
 # Check the immutable original resource pack, not only the remapped copy.
 pack=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\Bloodborne-Blocks\reference-inputs\bbmc-v15-v16\bbmc_v15_resource.zip')
 with zipfile.ZipFile(pack) as z:
  evidence=[]
  for path in ['addon/slabs_0','addon/slabs_1','slabs/stone_slab_manwhole','gray_wool']:
   suffix='assets/minecraft/models/block/'+path+'.json';matches=[n for n in z.namelist() if n.endswith(suffix)];assert len(matches)==1,(path,matches)
   raw=z.read(matches[0]);d=json.loads(raw);evidence.append({'entry':matches[0],'sha256':hashlib.sha256(raw).hexdigest(),'faces':[list(e.get('faces',{})) for e in d.get('elements',[])],'elements':len(d.get('elements',[]))})
 write(OUT/'original-resource-pack-check.json',{'pack':str(pack),'sha256':sha(pack),'models':evidence})
 print('PASS')
if __name__=='__main__':main()
