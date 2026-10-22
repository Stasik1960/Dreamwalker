from common import *
from collections import Counter,defaultdict
import copy,time
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

WORLD=DELIVERY/'map/bloodborne-blocks-fixed'
AIR=Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'minecraft:air')})
CELL=Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'bloodborne_dw:composite_cell')})
def variant(props):return int(props.get('variant','0'))+16*int(props.get('art_bank','0'))
def state(entry):
 d=compound(entry);return d['Name'].value,{k:v.value for k,v in d.get('Properties',Tag(10,{})).value.items()}
def static_index(id,props):return int(id.rsplit('_',1)[1])*128+int(props.get('part','0'))
def static_state(i):return AIR if i<0 else Tag(10,{'Name':Tag(8,'bloodborne_dw:static_architecture_'+str(i//128)),'Properties':Tag(10,{'part':Tag(8,str(i%128))})})
def native_state(row):return Tag(10,{'Name':Tag(8,row['id']),'Properties':Tag(10,{'type':Tag(8,row['type']),'facing':Tag(8,row.get('facing','north')),'waterlogged':Tag(8,'false')})})
def rewrite_section(sec,palette,indices):
 # Remove unused old IDs from the palette as well as from actual positions.
 used=sorted(set(indices));new=[];old_to_new={};by_state={}
 for old in used:
  key=block_state_key(palette[old]);at=by_state.get(key)
  if at is None:at=len(new);by_state[key]=at;new.append(palette[old])
  old_to_new[old]=at
 values=[old_to_new[i] for i in indices];data=Tag(10,{'palette':Tag(9,new,10)})
 if len(new)>1:data.value['data']=Tag(12,pack_palette_indices(values,len(new)))
 compound(sec)['block_states']=data
def put_state(secs,pos,entry):
 x,y,z=pos;sy=y>>4
 if sy not in secs:secs[sy]=Tag(10,{'Y':Tag(1,sy),'block_states':Tag(10,{'palette':Tag(9,[AIR],10)})})
 sec=secs[sy];palette,indices=section_blocks(sec);key=block_state_key(entry);pi=next((i for i,e in enumerate(palette) if block_state_key(e)==key),None)
 if pi is None:pi=len(palette);palette.append(entry)
 indices[((y&15)<<8)|((z&15)<<4)|(x&15)]=pi;rewrite_section(sec,palette,indices)
def uuid_key(owner):return tuple(compound(owner)['uuid'].value)
def owners(be):
 if 'OwnerDataV1' in be:return compound(decode_nbt(bytes(be['OwnerDataV1'].value)).root)['owners'].value
 return be.get('owners',Tag(9,[],10)).value
def save_owners(be,values):
 be.pop('owners',None);be['OwnerDataV1']=Tag(7,encode_nbt(NbtFile('',Tag(10,{'owners':Tag(9,values,10)}))))
def rootdata(data):
 d=compound(data);props={k:v.value for k,v in d['properties'].value.items()};return d['id'].value,props,d.get('payload',Tag(7,b'')).value
def payload_from_bytes(data):return compound(decode_nbt(bytes(data)).root) if data else {}
def scalar(d,key,default=0):return d.get(key,Tag(6,default)).value
def main():
 log_to('convert-world');assert read(OUT/'world-copy.json')['status']=='PASS'
 assert not (OUT/'conversion.json').exists(),'A completed conversion must not be applied twice'
 selected=read(OUT/'selected-materials.json');types={(t['registryId'],t['variant']):t for t in selected['slabs']};native=defaultdict(list)
 for m in selected['materials']:native[m['source'],m['variant']].append(m)
 remap={int(k):v for k,v in read(OUT/'static-slab-remap.json').items()};placements={};stats=Counter();removed_owners=set();changed_files=[];owned_roots={}
 def add(row):
  pos=tuple(row['position']);old=placements.get(pos)
  row=dict(row);row.setdefault('priority',0);row.setdefault('facing','north')
  if old:
   stats['overlapping_slab_candidates']+=1
   double=old['type']!=row['type'] or old['type']=='double' or row['type']=='double'
   if row['priority']>=old['priority']:row=old
   if double:row=dict(row,type='double')
  placements[pos]=row
 for row in read(OUT/'slab-placements.json'):add(row)
 def source_placements(dim,pos,id,props,payload,priority=0):
  typ=types[id,variant(props)];yaw=int(props.get('rotation','0'));a=math.pi*yaw/4;c=math.cos(a);s=math.sin(a)
  shift=[v.value for v in payload.get('SourceShift',Tag(9,[Tag(6,0),Tag(6,0),Tag(6,0)],6)).value];sx,sy,sz=shift;sx,sz=c*sx-s*sz,s*sx+c*sz
  local_top=typ['bounds'][1][1]/16
  if props.get('compact')=='true':
   from compact_context_audit import native_bounds
   b,_,_=native_bounds(id,variant(props),yaw);local_top=b[1][1]/16
  top=round((local_top+sy+scalar(payload,'MountY')+scalar(payload,'VerticalOffset'))*2)/2;yy=math.floor(top-1e-6);kind='bottom' if abs(top-yy-.5)<1e-6 else 'top'
  for m in native[id,variant(props)]:
   tx,tz=m['cell'];X=pos[0]+round(c*tx-s*tz+sx);Z=pos[2]+round(s*tx+c*tz+sz)
   add({'position':[dim,X,pos[1]+yy,Z],'id':m['id'],'type':kind,'facing':['north','east','south','west'][round(yaw/2)%4],'priority':priority+abs(tx)+abs(tz)})
 fallback=read(RES/'bloodborne_dw/source-fallback-carriers-v1271.json')['sourceStates']
 for path in sorted(WORLD.rglob('*.mca')):
  if path.parent.name!='region':continue
  ordinary(path);region=RegionFile.open(path);dim=str(path.parent.parent.relative_to(WORLD));dirty_region=False
  for chunk in region.chunks():
   nbt=chunk.nbt();root=compound(nbt.root);cx,cz=root['xPos'].value,root['zPos'].value
   secs={compound(s)['Y'].value:s for s in root.get('sections',Tag(9,[],10)).value};oldstates={};dirty=False
   # Filter selected owners from every chunk attachment, including helper BEs.
   kept_bes=[]
   for tag in root.get('block_entities',Tag(9,[],10)).value:
    be=compound(tag);pos=tuple(be[k].value for k in ['x','y','z']);resident=be.get('resident');oldlist=None;newlist=None;remove_resident=False
    blob=be.get('OwnerDataV1',Tag(7,b'')).value
    if ('owners' in be or b'slab' in bytes(blob) or b'snow_height8' in bytes(blob)):
     oldlist=owners(be);newlist=[]
     for entry in oldlist:
      fields=compound(entry);id,props,raw=rootdata(fields['rootData'])
      if (id,variant(props)) in types:
       owner=fields['owner'];key=(dim,uuid_key(owner));removed_owners.add(key);own=compound(owner);rpos=tuple(own['root'].value)
       if key not in owned_roots:owned_roots[key]={'id':id,'position':rpos};source_placements(dim,rpos,id,props,payload_from_bytes(raw),-2)
       if resident is not None and uuid_key(resident)==uuid_key(owner):remove_resident=True
       stats['removed_owner_contributions']+=1
      else:newlist.append(entry)
     if len(newlist)!=len(oldlist):save_owners(be,newlist);dirty=True
    if remove_resident:be.pop('resident',None);be['payload']=Tag(10,{})
    if oldlist is not None and len(newlist)!=len(oldlist) and not newlist and 'resident' not in be:
     put_state(secs,pos,AIR);stats['removed_empty_owner_entities']+=1;dirty=True
    else:
     kept_bes.append(tag)
     if remove_resident:put_state(secs,pos,CELL)
   if len(kept_bes)!=len(root.get('block_entities',Tag(9,[],10)).value):root['block_entities']=Tag(9,kept_bes,10)
   be_positions={tuple(compound(b)[k].value for k in ['x','y','z']) for b in kept_bes}
   for sy,sec in secs.items():
    bs=section_blocks(sec)
    if not bs:continue
    palette,indices=bs;changes={};sources={}
    for pi,entry in enumerate(palette):
     id,props=state(entry)
     if 'bloodborne_dw:source_invisible' in id:changes[pi]=AIR
     elif id.startswith('bloodborne_dw:static_architecture_'):
      ri=static_index(id,props)
      if ri in remap:changes[pi]=static_state(remap[ri])
     elif (id,variant(props)) in types:sources[pi]=(id,props);changes[pi]=AIR
     elif id.startswith('bloodborne_dw:source_fallback_'):
      j=int(props.get('source','0'))+128*int(props.get('source_bank','0'));source=fallback[j]
      if '_slab[' in source:
       n,props0=source.split('[',1);properties=dict(p.split('=') for p in props0[:-1].split(','));properties['waterlogged']='false';changes[pi]=Tag(10,{'Name':Tag(8,n),'Properties':Tag(10,{k:Tag(8,v) for k,v in properties.items()})})
    if not changes:continue
    hist=Counter(indices)
    if not any(pi in hist for pi in changes):continue
    for pi,count in hist.items():
     if pi not in changes:continue
     oldid=state(palette[pi])[0];stats['palette_changed_blocks']+=count
     if 'source_invisible' in oldid:stats['removed_invisible_blocks']+=count
     if oldid.startswith('bloodborne_dw:source_fallback_'):stats['native_fallback_slabs']+=count
     if pi in sources:
      id,props=sources[pi]
      for at,p in enumerate(indices):
       if p!=pi:continue
       pos=(cx*16+(at&15),sy*16+(at>>8),cz*16+((at>>4)&15))
       # Owned roots have already supplied the exact saved source shift above.
       if pos in be_positions:raise RuntimeError('Unremoved or mixed owner at slab root '+str((dim,pos,id)))
       source_placements(dim,pos,id,props,{},0);stats['native_source_slab_cells']+=1
    for pi,entry in changes.items():palette[pi]=entry
    rewrite_section(sec,palette,indices);dirty=True
   if dirty:
    root['sections']=Tag(9,[secs[sy] for sy in sorted(secs)],10);invalidate_chunk_lighting(root);root.pop('Heightmaps',None)
    region.set_chunk(chunk.x,chunk.z,nbt,timestamp=chunk.timestamp);dirty_region=True;stats['changed_chunks']+=1
  if dirty_region:region.save(path);changed_files.append(str(path.relative_to(WORLD)))
  print('CLEAN',path.relative_to(WORLD),dict(stats),flush=True)
 write(OUT/'conversion-stage1.json',{'status':'PASS','stats':dict(stats),'changedFiles':changed_files,'removedOwners':len(removed_owners),'ownedRoots':len(owned_roots)})
 write(OUT/'all-slab-placements.json',list(placements.values()))
 write(OUT/'removed-owner-uuids.json',[[d,list(u)] for d,u in sorted(removed_owners)])
 print('STAGE1 PASS',dict(stats),flush=True)
 # Prune saved owner tables and their exact membership masks. Unrelated
 # dictionary values are copied intact and only integer references are rebased.
 for path in sorted(WORLD.rglob('owners-*.nbt')):
  if path.parent.name!='bloodborne_dw_owner_shards':continue
  dim=str(path.parent.parent.parent.relative_to(WORLD));nbt=read_nbt(path);r=compound(nbt.root)
  if not scalar(r,'Dictionary'):
   raise RuntimeError('Unsupported legacy shard in prepared map: '+str(path))
  ot=r['ownerTable'].value;removed={i for i,o in enumerate(ot) if (dim,uuid_key(o)) in removed_owners}
  if not removed:continue
  cells=[]
  for tag in r['cells'].value:
   row=compound(tag);refs=[ref for ref in row['refs'].value if ref.value[0] not in removed]
   if refs:row['refs']=Tag(9,refs,11);cells.append(tag)
  members=[tag for tag in r['ownerMembership'].value if compound(tag)['ownerRef'].value not in removed]
  oi=sorted({ref.value[0] for tag in cells for ref in compound(tag)['refs'].value}|{compound(tag)['ownerRef'].value for tag in members})
  di=sorted({ref.value[1] for tag in cells for ref in compound(tag)['refs'].value})
  si=sorted({ref.value[2] for tag in cells for ref in compound(tag)['refs'].value}|{compound(m)['shapeRef'].value for tag in members for m in compound(tag)['members'].value})
  om={old:i for i,old in enumerate(oi)};dm={old:i for i,old in enumerate(di)};sm={old:i for i,old in enumerate(si)}
  for tag in cells:
   for ref in compound(tag)['refs'].value:ref.value=[om[ref.value[0]],dm[ref.value[1]],sm[ref.value[2]]]
  for tag in members:
   row=compound(tag);row['ownerRef']=Tag(3,om[row['ownerRef'].value])
   for m in row['members'].value:fields=compound(m);fields['shapeRef']=Tag(3,sm[fields['shapeRef'].value])
  r['cells']=Tag(9,cells,10);r['ownerMembership']=Tag(9,members,10);r['ownerTable']=Tag(9,[ot[i] for i in oi],10);r['dataTable']=Tag(9,[r['dataTable'].value[i] for i in di],10);r['shapeTable']=Tag(9,[r['shapeTable'].value[i] for i in si],10)
  write_nbt(path,nbt);changed_files.append(str(path.relative_to(WORLD)));stats['changed_owner_shards']+=1
 print('SHARDS PASS',stats['changed_owner_shards'],flush=True)
 write(OUT/'conversion-stage2.json',{'status':'PASS','stats':dict(stats),'changedFiles':changed_files})
 print('NEXT: apply_native_slabs.py')
if __name__=='__main__':main()
