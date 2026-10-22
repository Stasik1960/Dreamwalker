from common import *
from collections import Counter,defaultdict
import copy,re,gzip
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
spec=importlib.util.spec_from_file_location('convert_v1',PROJECT/'tools/block-fixes-20261020/convert_world.py');v1=importlib.util.module_from_spec(spec);spec.loader.exec_module(v1)
state=v1.state;variant=v1.variant;uuid_key=v1.uuid_key;rootdata=v1.rootdata;payload_from_bytes=v1.payload_from_bytes;scalar=v1.scalar;rewrite_section=v1.rewrite_section;put_state=v1.put_state
AIR=v1.AIR;CELL=v1.CELL

def main():
 log_to('convert-pavement');assert read(OUT/'world-copy.json')['status']=='PASS';assert not (OUT/'conversion-stage1.json').exists(),'Conversion must not be applied twice'
 mats=read(OUT/'new-pavement-materials.json');types={(r['source'],r['variant']):r for r in mats};sourceids={r['source'] for r in mats}
 cat=read(RES/'bloodborne_dw/static-architecture-v1274.json');poses={p['key']:p for p in cat['poses']};targetposes={k for k,p in poses.items() if (p['registryId'],variant(p['properties'])) in types};remap={}
 for i,r in enumerate(cat['recipes']):
  j=i
  while j>=0:
   rr=cat['recipes'][j];at=next((n for n,f in enumerate(rr['fragments']) if f['pose'] in targetposes),None)
   if at is None:break
   j=rr['remove'][at]
  if i!=j:remap[i]=j
 markers=[s.encode() for s in sourceids]+[('bloodborne_dw:static_architecture_'+str(i//128)).encode() for i in remap]
 markers=list(set(markers));placements={};removed=set();stats=Counter();components=set();changed_files=[];examples=[]
 def add(dim,pos,id,props,payload,kind):
  material=types[id,variant(props)];yaw=int(props.get('rotation','0'));assert yaw%2==0,('non-cardinal authored pavement',dim,pos,id,props)
  shift=[t.value for t in payload.get('SourceShift',Tag(9,[Tag(6,0)]*3,6)).value];sx,sy,sz=shift;a=math.pi*yaw/4;c=round(math.cos(a));s=round(math.sin(a));sx,sz=c*sx-s*sz,s*sx+c*sz
  assert abs(sx-round(sx))<1e-7 and abs(sz-round(sz))<1e-7,('non-grid pavement shift',pos,shift)
  art_y=0 if props.get('compact')=='true' else sy+scalar(payload,'MountY')+scalar(payload,'VerticalOffset')+material['sourceOffset'][1]/16
  surface=material['canonicalSurface']+art_y
  assert abs(surface*2-round(surface*2))<1e-6,('non-half paving surface',pos,id,surface,payload)
  surface=round(surface*2)/2;dy=math.floor(surface-1e-7);typ='bottom' if abs(surface-dy-.5)<1e-6 else 'top'
  row={'position':[dim,pos[0]+round(sx),pos[1]+dy,pos[2]+round(sz)],'id':material['id'],'type':typ,'facing':['north','east','south','west'][yaw//2%4],'source':id,'variant':variant(props),'oldRoot':list(pos),'artWorldShiftY':art_y,'canonicalSurface':material['canonicalSurface'],'kind':kind}
  key=tuple(row['position']);prior=placements.get(key)
  if prior is not None:
   assert all(prior[k]==row[k] for k in ['id','type','facing']),('overlapping different pavement',prior,row)
   stats['duplicate_component_references']+=1
  else:placements[key]=row
 for path in files(WORLD):
  if path.suffix!='.mca' or path.parent.name!='region' or not path.stat().st_size:continue
  dim=str(path.parent.parent.relative_to(WORLD));region=RegionFile.open(path);dirty_region=False
  for chunk in region.chunks():
   stats['chunks_scanned']+=1;raw=chunk.raw_nbt()
   if not any(m in raw for m in markers):continue
   nbt=decode_nbt(raw);root=compound(nbt.root);cx,cz=root['xPos'].value,root['zPos'].value;secs={compound(s)['Y'].value:s for s in root['sections'].value};dirty=False;kept=[]
   for tag in root.get('block_entities',Tag(9,[],10)).value:
    be=compound(tag);pos=tuple(be[k].value for k in ['x','y','z']);resident=be.get('resident');old=v1.owners(be) if 'OwnerDataV1' in be or 'owners' in be else None;new=[];resident_removed=False
    if old is not None:
     for entry in old:
      d=compound(entry);id,props,payload=rootdata(d['rootData'])
      if (id,variant(props)) in types:
       owner=d['owner'];own=compound(owner);removed.add((dim,uuid_key(owner)));add(dim,tuple(own['root'].value),id,props,payload_from_bytes(payload),'owned');stats['removed_owner_contributions']+=1
       if resident is not None and uuid_key(resident)==uuid_key(owner):resident_removed=True
      else:new.append(entry)
     if len(new)!=len(old):v1.save_owners(be,new);dirty=True
    if resident_removed:be.pop('resident',None);be['payload']=Tag(10,{})
    if old is not None and len(new)!=len(old) and not new and 'resident' not in be:put_state(secs,pos,AIR);stats['removed_empty_owner_entities']+=1;dirty=True
    else:
     kept.append(tag)
     if resident_removed:put_state(secs,pos,CELL)
   if len(kept)!=len(root.get('block_entities',Tag(9,[],10)).value):root['block_entities']=Tag(9,kept,10)
   bes={tuple(compound(t)[k].value for k in ['x','y','z']):compound(t) for t in kept}
   for sy,sec in secs.items():
    bs=section_blocks(sec)
    if not bs:continue
    palette,indices=bs;selected={};static={};changes={}
    for pi,e in enumerate(palette):
     id,props=state(e)
     if (id,variant(props)) in types:selected[pi]=(id,props);changes[pi]=AIR
     elif id.startswith('bloodborne_dw:static_architecture_'):
      ri=v1.static_index(id,props)
      if ri in remap:static[pi]=ri;changes[pi]=v1.static_state(remap[ri])
    if not changes:continue
    for at,pi in enumerate(indices):
     if pi not in changes:continue
     pos=(cx*16+(at&15),sy*16+(at>>8),cz*16+((at>>4)&15))
     if pi in selected:
      id,props=selected[pi];assert pos not in bes,('unremoved native/legacy root BE',dim,pos,id)
      add(dim,pos,id,props,{},'direct');stats['direct_source_cells']+=1
     else:
      ri=static[pi]
      for f in cat['recipes'][ri]['fragments']:
       if f['pose'] not in targetposes:continue
       p=poses[f['pose']];origin=f['origin'];rpos=tuple(pos[k]+origin[k] for k in range(3));key=(dim,*rpos,p['key'])
       if key in components:continue
       components.add(key);payload={'SourceShift':Tag(9,[Tag(6,v) for v in p['shift']],6),'MountY':Tag(6,p.get('mountY',0))};add(dim,rpos,p['registryId'],p['properties'],payload,'static');stats['static_components']+=1
     stats['changed_palette_cells']+=1
    for pi,newstate in changes.items():palette[pi]=newstate
    rewrite_section(sec,palette,indices);dirty=True
   if dirty:
    root['sections']=Tag(9,[secs[k] for k in sorted(secs)],10);invalidate_chunk_lighting(root);root.pop('Heightmaps',None);region.set_chunk(chunk.x,chunk.z,nbt,timestamp=chunk.timestamp);dirty_region=True;stats['changed_chunks']+=1
  if dirty_region:region.save(path);changed_files.append(str(path.relative_to(WORLD)))
  print('REGION',path.relative_to(WORLD),dict(stats),flush=True)
 write(OUT/'pavement-placements.json',list(placements.values()));write(OUT/'static-pavement-remap.json',remap);write(OUT/'removed-owner-uuids.json',[[d,list(u)] for d,u in sorted(removed)])
 write(OUT/'conversion-stage1.json',{'status':'PASS','stats':dict(stats),'placements':len(placements),'removedOwners':len(removed),'changedFiles':changed_files})
 print('PASS STAGE1',len(placements),dict(stats))
 # Rebase the existing owner dictionaries; preserve all other records exactly.
 for path in files(WORLD):
  if path.parent.name!='bloodborne_dw_owner_shards' or path.suffix!='.nbt':continue
  raw=path.read_bytes();raw=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw
  if not any(s.encode() in raw for s in sourceids):continue
  dim=str(path.parent.parent.parent.relative_to(WORLD));nbt=decode_nbt(raw);r=compound(nbt.root);assert scalar(r,'Dictionary')==1
  ot=r['ownerTable'].value;gone={i for i,o in enumerate(ot) if (dim,uuid_key(o)) in removed};cells=[]
  if not gone:continue
  for tag in r['cells'].value:
   d=compound(tag);refs=[ref for ref in d['refs'].value if ref.value[0] not in gone]
   if refs:d['refs']=Tag(9,refs,11);cells.append(tag)
  members=[t for t in r['ownerMembership'].value if compound(t)['ownerRef'].value not in gone]
  oi=sorted({ref.value[0] for t in cells for ref in compound(t)['refs'].value}|{compound(t)['ownerRef'].value for t in members});di=sorted({ref.value[1] for t in cells for ref in compound(t)['refs'].value});si=sorted({ref.value[2] for t in cells for ref in compound(t)['refs'].value}|{compound(m)['shapeRef'].value for t in members for m in compound(t)['members'].value});om={n:i for i,n in enumerate(oi)};dm={n:i for i,n in enumerate(di)};sm={n:i for i,n in enumerate(si)}
  for t in cells:
   for ref in compound(t)['refs'].value:ref.value=[om[ref.value[0]],dm[ref.value[1]],sm[ref.value[2]]]
  for t in members:
   d=compound(t);d['ownerRef']=Tag(3,om[d['ownerRef'].value])
   for m in d['members'].value:compound(m)['shapeRef']=Tag(3,sm[compound(m)['shapeRef'].value])
  r['cells']=Tag(9,cells,10);r['ownerMembership']=Tag(9,members,10);r['ownerTable']=Tag(9,[ot[i] for i in oi],10);r['dataTable']=Tag(9,[r['dataTable'].value[i] for i in di],10);r['shapeTable']=Tag(9,[r['shapeTable'].value[i] for i in si],10);write_nbt(path,nbt);stats['changed_owner_shards']+=1
 write(OUT/'conversion-stage2.json',{'status':'PASS','stats':dict(stats),'placements':len(placements)})
 print('PASS STAGE2',dict(stats))
if __name__=='__main__':main()
