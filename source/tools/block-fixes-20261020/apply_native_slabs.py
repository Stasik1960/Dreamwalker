from common import *
from collections import Counter,defaultdict
sys.path.insert(0,str(Path(__file__).parent))
from convert_world import WORLD,state,static_index,native_state,rewrite_section
from world_io import *

def main():
 log_to('apply-native-slabs');assert read(OUT/'conversion-stage2.json')['status']=='PASS'
 rows=read(OUT/'all-slab-placements.json');static=read(RES/'bloodborne_dw/static-architecture-v1274.json')['recipes'];overlays=read(OUT/'overlay-index.json');byregion=defaultdict(list)
 for row in rows:
  dim,x,y,z=row['position'];byregion[dim,x>>9,z>>9].append(row)
 stats=Counter();issues=[];adjustments={};changes=[];clipped=[];materials={m['id']:m for m in read(OUT/'selected-materials.json')['materials']}
 # Preflight every destination before writing any native slab.
 for (dim,rx,rz),group in sorted(byregion.items()):
  path=WORLD/dim/'region'/f'r.{rx}.{rz}.mca';region=RegionFile.open(path);bychunk=defaultdict(list)
  for row in group:_,x,y,z=row['position'];bychunk[x>>4,z>>4].append(row)
  for (cx,cz),pp in bychunk.items():
   chunk=region.get_chunk(cx&31,cz&31);root=compound(chunk.nbt().root);secs={compound(s)['Y'].value:section_blocks(s) for s in root['sections'].value};bes={tuple(compound(b)[k].value for k in ['x','y','z']):compound(b) for b in root.get('block_entities',Tag(9,[],10)).value}
   for row in pp:
    _,x,y,z=row['position'];bs=secs.get(y>>4);old=('minecraft:air',{}) if bs is None else state(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
    id,props=old;nextrow=dict(row);key=tuple(row['position'])
    if id.startswith('bloodborne_dw:static_architecture_'):
     i=static_index(id,props);r=static[i]
     if r['resident']>=0:issues.append({'position':row['position'],'reason':'visible_static_root','recipe':i});continue
     dest=overlays.get(row['id']+'/'+str(i))
     if dest is None:issues.append({'position':row['position'],'reason':'missing_overlay','recipe':i,'base':row['id']});continue
     nextrow['id']=dest;stats['preserved_static_helper_cells']+=1
    elif id=='bloodborne_dw:composite_cell':
     be=bes.get((x,y,z))
     if be and 'resident' in be:issues.append({'position':row['position'],'reason':'foreign_owned_root','state':id});continue
     stats['native_slab_with_saved_guests']+=1
    elif id.startswith('minecraft:') and id.endswith('_slab'):
     before=props.get('type','bottom')
     if before=='double' or before!=row['type']:nextrow['type']='double'
     stats['combined_native_slab_cells']+=1
    elif id in ['minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:water']:pass
    else:
     if materials[row['id']]['cell']!=[0,0]:
      clipped.append({'position':row['position'],'slab':row['id'],'existingBlock':id,'sourceTileOffset':materials[row['id']]['cell']});stats['cropped_occupied_outer_tiles']+=1;continue
     issues.append({'position':row['position'],'reason':'foreign_block','state':id,'properties':props});continue
    adjustments[key]=nextrow;stats['placements']+=1;stats['type_'+nextrow['type']]+=1
  print('PREFLIGHT',dim,rx,rz,'issues',len(issues),flush=True)
 write(OUT/'native-slab-preflight.json',{'status':'PASS' if not issues else 'NEEDS_REVIEW','stats':dict(stats),'issues':issues,'clippedOccupiedOuterTiles':clipped,'cropPolicy':'The original 3-cell texture overhang is clipped at an occupied neighbouring block. Every original slab root is converted; existing structural blocks and their models remain in place.'})
 if issues:raise RuntimeError('Review '+str(len(issues))+' native slab destinations before writing')
 for (dim,rx,rz),group in sorted(byregion.items()):
  path=WORLD/dim/'region'/f'r.{rx}.{rz}.mca';region=RegionFile.open(path);bychunk=defaultdict(list)
  for row in group:
   if tuple(row['position']) not in adjustments:continue
   _,x,y,z=row['position'];bychunk[x>>4,z>>4].append(adjustments[tuple(row['position'])])
  for (cx,cz),pp in bychunk.items():
   chunk=region.get_chunk(cx&31,cz&31);nbt=chunk.nbt();root=compound(nbt.root);secs={compound(s)['Y'].value:s for s in root['sections'].value};bysec=defaultdict(list);positions=set()
   for row in pp:_,x,y,z=row['position'];bysec[y>>4].append(row);positions.add((x,y,z))
   for sy,ss in bysec.items():
    if sy not in secs:secs[sy]=Tag(10,{'Y':Tag(1,sy),'block_states':Tag(10,{'palette':Tag(9,[Tag(10,{'Name':Tag(8,'minecraft:air')})],10)})})
    sec=secs[sy];palette,indices=section_blocks(sec);keys={block_state_key(p):i for i,p in enumerate(palette)}
    for row in ss:
     entry=native_state(row);key=block_state_key(entry);pi=keys.get(key)
     if pi is None:pi=len(palette);palette.append(entry);keys[key]=pi
     _,x,y,z=row['position'];indices[((y&15)<<8)|((z&15)<<4)|(x&15)]=pi
    rewrite_section(sec,palette,indices)
   root['sections']=Tag(9,[secs[sy] for sy in sorted(secs)],10)
   root['block_entities']=Tag(9,[b for b in root.get('block_entities',Tag(9,[],10)).value if tuple(compound(b)[k].value for k in ['x','y','z']) not in positions],10)
   invalidate_chunk_lighting(root);root.pop('Heightmaps',None);region.set_chunk(chunk.x,chunk.z,nbt,timestamp=chunk.timestamp)
  region.save(path);changes.append(str(path.relative_to(WORLD)));print('WRITE',dim,rx,rz,flush=True)
 level=read_nbt(WORLD/'level.dat');compound(compound(level.root)['Data'])['LevelName']=Tag(8,'Bloodborne — исправленные плиты');write_nbt(WORLD/'level.dat',level)
 write(OUT/'installed-slab-states.json',list(adjustments.values()))
 write(OUT/'conversion.json',{'status':'PASS','stats':dict(stats),'nativePlacementRegions':changes,'originalWorldUnchanged':True,'stage1':read(OUT/'conversion-stage1.json'),'stage2':read(OUT/'conversion-stage2.json'),'baseJar':str(BASE),'baseJarSha256':sha(BASE)})
 print('PASS',dict(stats))
if __name__=='__main__':main()
