"""Reuse the existing authored poses and cached map inventory, never reconvert."""
import collections,copy,json,math,re,sys
from pathlib import Path
import world_io as w
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');R=P/'src/architecture/resources';B=R/'bloodborne_dw';read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
audit=read(D/'world-audit.json');static=read(B/'static-architecture-v1274.json');poses={p['key']:p for p in static['poses']};compact={p['registryId']:p for p in read(B/'imported-compact-v127.json')['types']};specs={};regions=collections.OrderedDict();chunks=collections.OrderedDict();newAliases=set()
native=read(R/'assets/bloodborne_dw/native_grid/catalog.json')
def spec(id):
 if id not in specs:specs[id]=read(B/'composite'/(id.split(':')[1]+'.json'))
 return specs[id]
def chunk(x,z):
 key=x//16,z//16
 if key not in chunks:
  regkey=x//512,z//512
  if regkey not in regions:
   regions[regkey]=w.RegionFile((W/f'region/r.{regkey[0]}.{regkey[1]}.mca').read_bytes())
   if len(regions)>4:regions.popitem(last=False)
  c=regions[regkey].get_chunk(key[0]%32,key[1]%32);chunks[key]=c.nbt().root.value if c else {}
  if len(chunks)>64:chunks.popitem(last=False)
 return chunks[key]
def at(pos):
 x,y,z=pos;root=chunk(x,z);sec=next((s for s in root.get('sections',w.Tag(9,[],10)).value if s.value['Y'].value==y//16),None)
 if sec is None:return {'registryId':'minecraft:air','properties':{}}
 pal,indices=w.section_blocks(sec);p=pal[indices[y%16*256+z%16*16+x%16]].value
 return {'registryId':p['Name'].value,'properties':{k:v.value for k,v in p.get('Properties',w.Tag(10,{})).value.items()}}
def parts_for(id,props,offset,shift=(0,0,0),mount=0):
 s=spec(id);v=int(props.get('variant',0))+16*int(props.get('art_bank',0));rot=int(props.get('rotation',0));angle=-rot*math.pi/4;c=math.cos(angle);sn=math.sin(angle);result=[]
 # MatrixStack.rotate uses -yaw; rotate the source translation with it too.
 for original in s['variants'][v]['poses']['closed']['parts']:
  p=copy.deepcopy(original);o=p.get('offset',[0,0,0]);sx=o[0]+shift[0]*16;sz=o[2]+shift[2]*16
  p['offset']=[round(sx*c+sz*sn+offset[0]*16,8),round(o[1]+shift[1]*16+mount*16+offset[1]*16,8),round(-sx*sn+sz*c+offset[2]*16,8)]
  p['yaw']=p.get('yaw',0)+rot*45;result.append(p)
 return result
def cell_data(pos,offset):
 a=at(pos);id=a['registryId'];props=a['properties'];parts=[];collision=[];outline=[];opacity=0
 if id in compact and props.get('compact')=='true':
  v=int(props.get('variant',0))+16*int(props.get('art_bank',0));rot=int(props.get('rotation',0));shape=compact[id]['shapes'][str(v)][rot];collision=shape['collision'];outline=shape['aim'];opacity=15 if v in compact[id]['opaquePoses'] else 0;parts=parts_for(id,props,offset)
 elif id.startswith('bloodborne_dw:static_architecture_'):
  index=int(id.rsplit('_',1)[1])*128+int(props['part']);recipe=static['recipes'][index];collision=[{'from':[n*16 for n in b[:3]],'to':[n*16 for n in b[3:]]} for b in recipe['collision']];outline=[{'from':[n*16 for n in b[:3]],'to':[n*16 for n in b[3:]]} for b in recipe['selection']];opacity=recipe['opacity']
  for f in recipe['fragments']:
   p=poses[f['pose']]
   if p['registryId']=='bloodborne_dw:source_lantern':raise ValueError('Lantern must remain separate')
   if f['origin']==[0,0,0]:parts+=parts_for(p['registryId'],p['properties'],offset,p['shift'],p['mountY'])
 elif id.startswith('bloodborne_dw:native_physics_'):
  root=chunk(pos[0],pos[2]);key=pos[1]%16*256+pos[2]%16*16+pos[0]%16
  values=next(s.value['Entries'].value for s in root['DreamwalkerVisuals'].value['Sections'].value if s.value['Y'].value==pos[1]//16)
  visual=next((n&0xffffffff)>>12 for n in values if n&4095==key);a['nativeVisual']=visual;cell=native['cells'][visual];newAliases.add(visual)
  parts=[{'model':'bloodborne_dw:restoration/native_'+str(visual),'offset':[n*16 for n in offset]}]
  collision=[{'from':[v*16 for v in b[:3]],'to':[v*16 for v in b[3:]]} for b in native['shapes'][cell['collision']]];outline=[{'from':[v*16 for v in b[:3]],'to':[v*16 for v in b[3:]]} for b in native['shapes'][cell['outline']]];opacity=native['optics'][cell['optics']]['opacity']
 elif id=='minecraft:air':return a,[],[],[],0
 else:raise ValueError('Unsupported pole carrier '+id)
 def shifted(boxes):return [{'from':[round(b['from'][i]+offset[i]*16,8) for i in range(3)],'to':[round(b['to'][i]+offset[i]*16,8) for i in range(3)]} for b in boxes]
 return a,parts,shifted(collision),shifted(outline),opacity
posts=[];variants=[];templates={};skipped=[];shadow=[]
bases=[r['pos'] for r in audit['matchingSourceComponents'] if r['state'].startswith('bloodborne_dw:source_deepslate_tile_wall_side[')]
for base in bases:
 source=[];parts=[];collision=[];outline=[];opt=[]
 try:
  # Confirm the ordered base, shaft and top. Similar parts elsewhere stay alone.
  if at((base[0],base[1]+1,base[2]))['registryId']!='bloodborne_dw:source_andesite_slab_top':continue
  if not at((base[0],base[1]+7,base[2]))['registryId'].endswith('source_assembly_orange_stained_glass_pane'):continue
  offsets=[[0,y,0] for y in range(9)]
  for dx,dz in [(-1,0),(1,0),(0,-1),(0,1)]:
   q=(base[0]+dx,base[1]+7,base[2]+dz)
   if at(q)['registryId']=='bloodborne_dw:source_assembly_light_blue_stained_glass_pane':offsets.append([dx,7,dz])
  for off in offsets:
   pos=tuple(base[i]+off[i] for i in range(3));state,p,c,o,opacity=cell_data(pos,off)
   if state['registryId']=='minecraft:air':continue
   source.append({'offset':off,'state':state});parts+=p;collision+=c;outline+=o;opt.append({'offset':off,'opacity':opacity})
  key=json.dumps(source,sort_keys=True);v=templates.get(key)
  if v is None:
   v=len(variants);templates[key]=v;variants.append({'poses':{'closed':{'parts':parts,'collision':collision,'selection':outline}}});shadow.append(opt)
  posts.append({'root':base,'variant':v,'before':source})
 except (KeyError,ValueError) as e:skipped.append({'root':base,'reason':str(e)})
assert 0<len(variants)<=128
document={'schemaVersion':1,'units':16,'id':'bloodborne_dw:source_street_lamp_post','displayName':'Фонарный столб','openable':False,'layer':'translucent','simplePhysicalCollision':True,'support':{'required':False},'essentialMask':[[0,0,0]],'variants':variants}
(B/'composite/source_street_lamp_post.json').write_text(json.dumps(document,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
(B/'street-lamp-post-optics.json').write_text(json.dumps({'schemaVersion':1,'variants':shadow},separators=(',',':')),encoding='utf-8')
catalog=read(B/'remaining-catalogue.json');catalog['objects']=[r for r in catalog['objects'] if r['path']!='source_street_lamp_post'];catalog['objects'].append({'path':'source_street_lamp_post','displayName':'Фонарный столб','variants':[{'variant':i,'name':'Фонарный столб · вид '+str(i+1)} for i in range(len(variants))]});(B/'remaining-catalogue.json').write_text(json.dumps(catalog,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
assets=R/'assets/bloodborne_dw'
(assets/'models/block/source_street_lamp_post_empty.json').write_text('{"textures":{"particle":"minecraft:block/stone"},"elements":[]}',encoding='utf-8');(assets/'models/item/source_street_lamp_post.json').write_text('{"parent":"builtin/entity"}',encoding='utf-8');(assets/'blockstates/source_street_lamp_post.json').write_text('{"variants":{"":{"model":"bloodborne_dw:block/source_street_lamp_post_empty"}}}',encoding='utf-8')
for visual in newAliases:
 p=assets/'models/restoration'/('native_'+str(visual)+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('{"textures":{"particle":"minecraft:block/stone"},"elements":[]}',encoding='utf-8')
(D/'post-replacement-plan.json').write_text(json.dumps({'schemaVersion':1,'posts':posts,'variantCount':len(variants),'preservedUnmatched':skipped,'lampsExcluded':True},ensure_ascii=False,indent=2),encoding='utf-8');print('POSTS',len(posts),'VARIANTS',len(variants),'LOCAL EXCEPTIONS',len(skipped),flush=True)
# Find lantern roots in existing static recipes, without decoding unrelated palettes.
if (D/'lantern-replacement-plan.json').exists():sys.exit(0)
lampRecipes={i:[f for f in row['fragments'] if f['origin']==[0,0,0] and poses[f['pose']]['registryId']=='bloodborne_dw:source_lantern'] for i,row in enumerate(static['recipes'])};lampRecipes={i:v for i,v in lampRecipes.items() if v};lamps=[]
for path in sorted((W/'region').glob('r.*.mca')):
 reg=w.RegionFile(path.read_bytes());rx,rz=map(int,path.stem.split('.')[1:])
 for lz in range(32):
  for lx in range(32):
   ch=reg.get_chunk(lx,lz)
   if ch is None:continue
   root=ch.nbt().root.value
   for sec in root.get('sections',w.Tag(9,[],10)).value:
    bs=sec.value.get('block_states')
    if bs is None:continue
    candidates={}
    for i,p in enumerate(bs.value['palette'].value):
     id=p.value['Name'].value
     if not id.startswith('bloodborne_dw:static_architecture_'):continue
     index=int(id.rsplit('_',1)[1])*128+int(p.value['Properties'].value['part'].value)
     if index in lampRecipes:candidates[i]=index
    if not candidates:continue
    pal,indices=w.section_blocks(sec);sy=sec.value['Y'].value
    for local,i in enumerate(indices):
     if i not in candidates:continue
     for f in lampRecipes[candidates[i]]:lamps.append({'root':[(rx*32+lx)*16+local%16,sy*16+local//256,(rz*32+lz)*16+local//16%16],'pose':f['pose']})
 print(path.name,len(lamps),flush=True)
(D/'lantern-replacement-plan.json').write_text(json.dumps({'schemaVersion':1,'lamps':lamps},indent=2),encoding='utf-8');print('LANTERNS',len(lamps),flush=True)
