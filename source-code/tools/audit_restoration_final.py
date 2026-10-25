"""Compare repaired world to the immutable user input, permitting only logged cells."""
import collections,hashlib,json,sys,zipfile
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne')
run=json.loads((D/'map-apply.json').read_text());assert run['status']=='COMPLETE'
allowed={tuple(p) for r in run['objects'] if r['status']=='COMMITTED' for p in r['touchedCells']}
report={'status':'RUNNING','objects':dict(collections.Counter((r['kind']+':'+r['status']) for r in run['objects'])),'allowedCells':len(allowed),'changedCells':0,'unauthorizedCells':[],'protectedBlockEntities':0,'blockEntityDifferences':[],'nativeVisualDifferences':[],'missingChunks':[],'rpEntities':0,'rpEntityDifferences':[],'newChunks':0}
def dump(): (D/'final-map-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
def visuals(root):
 out={}
 for s in root.get('DreamwalkerVisuals',n.Tag(10,{})).value.get('Sections',n.Tag(9,[],10)).value:
  for raw in s.value['Entries'].value:
   v=raw&0xffffffff;out[(v&15,s.value['Y'].value*16+((v>>8)&15),(v>>4)&15)]=v>>12
 return out
def bes(root):return {tuple(b.value[k].value for k in ('x','y','z')):b for b in root.get('block_entities',n.Tag(9,[],10)).value}
def sections(root):return {s.value['Y'].value:s for s in root.get('sections',n.Tag(9,[],10)).value}
def entities(root):return {tuple(e.value['UUID'].value):e for e in root.get('Entities',n.Tag(9,[],10)).value if e.value.get('id',n.Tag(8,'')).value.startswith(('bloodborne_rp:','bloodborne_dw:'))}
runtime={'Air','Fire','FallDistance','HurtTime','DeathTime','PortalCooldown','OnGround','TicksFrozen','Motion'}
with zipfile.ZipFile(D/'bloodborne-before-restoration.zip') as z:
 for member in z.namelist():
  rel=Path(*Path(member).parts[1:]);parts=rel.parts
  if len(parts)!=2 or parts[0] not in ('region','entities') or rel.suffix!='.mca':continue
  oldBytes=z.read(member)
  if not oldBytes or not any(oldBytes[:4096]):continue
  target=W/rel
  if not target.exists():report['missingChunks'].append(str(rel));continue
  newBytes=target.read_bytes()
  if oldBytes==newBytes:continue
  old=n.RegionFile(oldBytes);new=n.RegionFile(newBytes);rx,rz=map(int,rel.stem.split('.')[1:])
  for cz in range(32):
   for cx in range(32):
    a=old.get_chunk(cx,cz);b=new.get_chunk(cx,cz)
    if a is None:
     if b is not None:report['newChunks']+=1
     continue
    if b is None:report['missingChunks'].append([str(rel),cx,cz]);continue
    ar=a.nbt().root.value;br=b.nbt().root.value
    if parts[0]=='entities':
     be=entities(br)
     for uuid,e in entities(ar).items():
      report['rpEntities']+=1;v=be.get(uuid)
      if v is None or {k:t for k,t in e.value.items() if k not in runtime}!={k:t for k,t in v.value.items() if k not in runtime}:report['rpEntityDifferences'].append({'uuid':uuid,'id':e.value['id'].value,'missing':v is None,'changedKeys':[] if v is None else [k for k,t in e.value.items() if k not in runtime and v.value.get(k)!=t]})
     continue
    bv=bes(br)
    for pos,e in bes(ar).items():
     report['protectedBlockEntities']+=1
     if e!=bv.get(pos):report['blockEntityDifferences'].append({'pos':pos,'id':e.value.get('id',n.Tag(8,'')).value,'missing':pos not in bv,'changedKeys':[] if pos not in bv else [k for k,t in e.value.items() if bv[pos].value.get(k)!=t]})
    av=visuals(ar);nv=visuals(br)
    for key in set(av)|set(nv):
     pos=((rx*32+cx)*16+key[0],key[1],(rz*32+cz)*16+key[2])
     if av.get(key)!=nv.get(key) and pos not in allowed:report['nativeVisualDifferences'].append({'pos':pos,'before':av.get(key),'after':nv.get(key)})
    aa=sections(ar);bb=sections(br)
    for sy in set(aa)|set(bb):
     sa=aa.get(sy);sb=bb.get(sy);ta=None if sa is None else sa.value.get('block_states');tb=None if sb is None else sb.value.get('block_states')
     if ta==tb:continue
     pa,ia=n.section_blocks(sa) if ta else ([n.Tag(10,{'Name':n.Tag(8,'minecraft:air')})],[0]*4096)
     pb,ib=n.section_blocks(sb) if tb else ([n.Tag(10,{'Name':n.Tag(8,'minecraft:air')})],[0]*4096)
     ka=list(map(n.block_state_key,pa));kb=list(map(n.block_state_key,pb))
     for i,(x,y) in enumerate(zip(ia,ib)):
      if ka[x]==kb[y]:continue
      pos=((rx*32+cx)*16+i%16,sy*16+i//256,(rz*32+cz)*16+i//16%16);report['changedCells']+=1
      if pos not in allowed:report['unauthorizedCells'].append({'pos':pos,'before':ka[x],'after':kb[y]})
  dump()
index=n.read_nbt(W/'data/bloodborne_dw_composite_owners.dat').root.value['data'].value['OwnerShardChunks'].value
shards=list((W/'data').rglob('owners-*.nbt'));known={}
for p in shards:
 root=n.read_nbt(p).root.value;cx=root['ChunkX'].value;cz=root['ChunkZ'].value;key=(cx&0xffffffff)|((cz&0xffffffff)<<32);known[key-(1<<64) if key>=1<<63 else key]=p
report['danglingShardReferences']=[key for key in index if key not in known]
report['remainingEmptyOwnerShards']=[str(p.relative_to(W)) for p in shards if not any(n.read_nbt(p).root.value.get(k,n.Tag(9,[],10)).value for k in ('cells','ownerMembership','ownerTable','dataTable','shapeTable'))]
report['status']='PASS' if not any(report[k] for k in ('unauthorizedCells','blockEntityDifferences','nativeVisualDifferences','missingChunks','rpEntityDifferences','danglingShardReferences','remainingEmptyOwnerShards')) else 'REVIEW_REQUIRED';dump()
print(json.dumps({k:v for k,v in report.items() if not isinstance(v,list)},ensure_ascii=False))
