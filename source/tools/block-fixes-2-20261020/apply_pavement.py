from common import *
from collections import Counter,defaultdict
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
from convert_pavement import state,v1,rewrite_section

def main():
 log_to('apply-pavement');assert read(OUT/'conversion-stage2.json')['status']=='PASS';assert not (OUT/'conversion.json').exists()
 rows=read(OUT/'pavement-placements.json');recipes=read(RES/'bloodborne_dw/static-architecture-v1274.json')['recipes'];byregion=defaultdict(list);stats=Counter();issues=[];adjusted=[];needed=set();manifest=read(RES/'bloodborne_dw/restored-material-slabs.json');known={m['id']:m for m in manifest['materials']};added=[]
 def overlay(base,recipe):
  assert recipes[recipe]['resident']<0,('Visible root at new paving cell',base,recipe)
  name='restored_slab_overlay_'+hashlib.sha256((base+'/'+str(recipe)).encode()).hexdigest()[:16];id='bloodborne_dw:'+name
  if id in known:return id
  row=dict(known[base],id=id,base=base,overlayRecipe=recipe,primary=False);known[id]=row;added.append(row)
  base_name=base.split(':')[1];write(RES/'assets/bloodborne_dw/blockstates'/(name+'.json'),read(RES/'assets/bloodborne_dw/blockstates'/(base_name+'.json')));write(RES/'assets/bloodborne_dw/models/item'/(name+'.json'),{'parent':'bloodborne_dw:block/restored_slabs/'+base_name+'_bottom'})
  for nextrecipe in recipes[recipe]['remove']:
   if nextrecipe>=0:overlay(base,nextrecipe)
  return id
 for row in rows:dim,x,y,z=row['position'];byregion[dim,x>>9,z>>9].append(row)
 for (dim,rx,rz),group in sorted(byregion.items()):
  region=RegionFile.open(WORLD/dim/'region'/f'r.{rx}.{rz}.mca');chunks=defaultdict(list)
  for row in group:_,x,y,z=row['position'];chunks[x>>4,z>>4].append(row)
  for (cx,cz),pp in chunks.items():
   stored=region.get_chunk(cx&31,cz&31);root=compound(stored.nbt().root);secs={compound(s)['Y'].value:section_blocks(s) for s in root['sections'].value};bes={tuple(compound(b)[k].value for k in ['x','y','z']):compound(b) for b in root.get('block_entities',Tag(9,[],10)).value}
   for row in pp:
    _,x,y,z=row['position'];bs=secs.get(y>>4);id,props=('minecraft:air',{}) if bs is None else state(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
    nextrow=dict(row);be=bes.get((x,y,z))
    if be and 'resident' in be:issues.append({'reason':'occupied foreign root','position':row['position'],'state':id});continue
    if id.startswith('bloodborne_dw:static_architecture_'):
     ri=v1.static_index(id,props)
     if recipes[ri]['resident']>=0:issues.append({'reason':'visible static root','position':row['position'],'state':id,'recipe':ri});continue
     nextrow['id']=overlay(row['id'],ri);stats['preserved_helper_cells']+=1
    elif id=='bloodborne_dw:composite_cell':stats['preserved_guest_cells']+=1
    elif id not in ['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:water']:issues.append({'reason':'occupied structural cell','position':row['position'],'state':id,'props':props});continue
    adjusted.append(nextrow);stats['placements']+=1;stats['type_'+row['type']]+=1
  print('PREFLIGHT',dim,rx,rz,'issues',len(issues),flush=True)
 write(OUT/'native-pavement-preflight.json',{'status':'PASS' if not issues else 'NEEDS_REVIEW','stats':dict(stats),'issues':issues,'newHelperVariants':len(added)})
 assert not issues,('Unresolved paving destinations',issues[:8])
 manifest['materials']+=added;write(RES/'bloodborne_dw/restored-material-slabs.json',manifest)
 for locale in ['ru_ru','en_us']:
  p=RES/'assets/bloodborne_dw/lang'/(locale+'.json');d=read(p)
  for r in added:d['block.bloodborne_dw.'+r['id'].split(':')[1]]=r['name']
  write(p,d)
 byregion=defaultdict(list)
 for row in adjusted:dim,x,y,z=row['position'];byregion[dim,x>>9,z>>9].append(row)
 changed=[]
 for (dim,rx,rz),group in sorted(byregion.items()):
  path=WORLD/dim/'region'/f'r.{rx}.{rz}.mca';region=RegionFile.open(path);chunks=defaultdict(list)
  for row in group:_,x,y,z=row['position'];chunks[x>>4,z>>4].append(row)
  for (cx,cz),pp in chunks.items():
   stored=region.get_chunk(cx&31,cz&31);nbt=stored.nbt();root=compound(nbt.root);secs={compound(s)['Y'].value:s for s in root['sections'].value};bysec=defaultdict(list);positions=set()
   for row in pp:_,x,y,z=row['position'];bysec[y>>4].append(row);positions.add((x,y,z))
   for sy,ss in bysec.items():
    if sy not in secs:secs[sy]=Tag(10,{'Y':Tag(1,sy),'block_states':Tag(10,{'palette':Tag(9,[v1.AIR],10)})})
    sec=secs[sy];palette,indices=section_blocks(sec);keys={block_state_key(p):i for i,p in enumerate(palette)}
    for row in ss:
     entry=v1.native_state(row);key=block_state_key(entry);pi=keys.get(key)
     if pi is None:pi=len(palette);palette.append(entry);keys[key]=pi
     _,x,y,z=row['position'];indices[((y&15)<<8)|((z&15)<<4)|(x&15)]=pi
    rewrite_section(sec,palette,indices)
   root['sections']=Tag(9,[secs[k] for k in sorted(secs)],10);root['block_entities']=Tag(9,[b for b in root.get('block_entities',Tag(9,[],10)).value if tuple(compound(b)[k].value for k in ['x','y','z']) not in positions],10);invalidate_chunk_lighting(root);root.pop('Heightmaps',None);region.set_chunk(stored.x,stored.z,nbt,timestamp=stored.timestamp)
  region.save(path);changed.append(str(path.relative_to(WORLD)));print('WRITE',path.relative_to(WORLD),flush=True)
 level=read_nbt(WORLD/'level.dat');compound(compound(level.root)['Data'])['LevelName']=Tag(8,'Bloodborne — исправленная мостовая 2');write_nbt(WORLD/'level.dat',level)
 write(OUT/'installed-slab-states.json',adjusted);write(OUT/'conversion.json',{'status':'PASS','stats':dict(stats),'newMaterials':len(read(OUT/'new-pavement-materials.json')),'newHelperVariants':len(added),'regions':changed,'stage1':read(OUT/'conversion-stage1.json'),'stage2':read(OUT/'conversion-stage2.json')});print('PASS',dict(stats),len(added),'new helper variants')
if __name__=='__main__':main()
