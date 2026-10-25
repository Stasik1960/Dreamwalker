"""Offline preservation audit against the immutable user map; no world writes."""
import collections, json, sys, zipfile
from pathlib import Path
import world_io as n
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence'
role=sys.argv[1];runDir=D/role
W=P/'interaction-profiles'/role/'world' if role.startswith('preview-') else Path('C:/Users/vakir/Desktop/bloodborne')
run=json.loads((runDir/'map-apply.json').read_text());assert run['status']=='COMPLETE'
assert all(r['status']=='COMMITTED' for r in run['results']), 'Migration contains rejected groups'
assert sum(r['windows'] for r in run['results'])==203
allowed={tuple(p) for r in run['results'] for p in r['touchedCells']}
legacy={tuple(r['pos']) for r in json.loads((D/'legacy-window-data.json').read_text())['windows']}
report={'status':'RUNNING','windows':203,'groups':run['groups'],'allowedCells':len(allowed),'changedCells':0,'protectedBlockEntities':0,'checkedRpEntities':0,'runtimeProtectedRecords':sum(r['protectedRecords'] for r in run['results']),'errors':[]}
def issue(kind,**data):report['errors'].append({'kind':kind,**data})
def sections(r):return {s.value['Y'].value:s for s in r.get('sections',n.Tag(9,[],10)).value}
def bes(r):return {tuple(b.value[k].value for k in ('x','y','z')):b for b in r.get('block_entities',n.Tag(9,[],10)).value}
def visuals(r):
 out={}
 for s in r.get('DreamwalkerVisuals',n.Tag(10,{})).value.get('Sections',n.Tag(9,[],10)).value:
  for raw in s.value['Entries'].value:
   v=raw&0xffffffff;out[(v&15,s.value['Y'].value*16+((v>>8)&15),(v>>4)&15)]=v>>12
 return out
def entities(r):return {tuple(e.value['UUID'].value):e for e in r.get('Entities',n.Tag(9,[],10)).value if e.value.get('id',n.Tag(8,'')).value.startswith(('bloodborne_rp:','bloodborne_dw:'))}
runtime={'Air','Fire','FallDistance','HurtTime','DeathTime','PortalCooldown','OnGround','TicksFrozen','Motion'}
with zipfile.ZipFile(D/'bloodborne-before-interactions.zip') as archive:
 for member in archive.namelist():
  rel=Path(*Path(member).parts[1:])
  if len(rel.parts)!=2 or rel.parts[0] not in ('region','entities') or rel.suffix!='.mca':continue
  ab=archive.read(member);target=W/rel
  if not target.is_file():issue('missing-region',path=str(rel));continue
  bb=target.read_bytes()
  if ab==bb:continue
  a=n.RegionFile(ab);b=n.RegionFile(bb);rx,rz=map(int,rel.stem.split('.')[1:])
  for cz in range(32):
   for cx in range(32):
    ac=a.get_chunk(cx,cz);bc=b.get_chunk(cx,cz)
    if ac is None:continue
    if bc is None:issue('missing-chunk',region=str(rel),x=cx,z=cz);continue
    ar=ac.nbt().root.value;br=bc.nbt().root.value
    if rel.parts[0]=='entities':
     be=entities(br)
     for uuid,e in entities(ar).items():
      report['checkedRpEntities']+=1;v=be.get(uuid)
      if v is None or {k:t for k,t in e.value.items() if k not in runtime}!={k:t for k,t in v.value.items() if k not in runtime}:issue('rp-data',uuid=uuid,id=e.value['id'].value,changedKeys=[] if v is None else [k for k,t in e.value.items() if k not in runtime and v.value.get(k)!=t])
     continue
    nb=bes(br)
    for pos,e in bes(ar).items():
     if pos in legacy:continue  # The 67 explicitly replaced whole window residents.
     report['protectedBlockEntities']+=1;v=nb.get(pos)
     if e==v:continue
     # Runtime transaction checks preserve every existing contribution and all
     # resident/payload data; only adding/removing authorized guests changes this envelope.
     skip={'OwnerDataV1','owners'} if pos in allowed else set()
     if v is None or {k:t for k,t in e.value.items() if k not in skip}!={k:t for k,t in v.value.items() if k not in skip}:issue('block-entity',pos=pos,id=e.value.get('id',n.Tag(8,'')).value,changedKeys=[] if v is None else [k for k,t in e.value.items() if v.value.get(k)!=t])
    av=visuals(ar);bv=visuals(br)
    for key in set(av)|set(bv):
     pos=((rx*32+cx)*16+key[0],key[1],(rz*32+cz)*16+key[2])
     if av.get(key)!=bv.get(key) and pos not in allowed:issue('native-visual',pos=pos,before=av.get(key),after=bv.get(key))
    aa=sections(ar);bs=sections(br)
    for sy in set(aa)|set(bs):
     sa=aa.get(sy);sb=bs.get(sy);ta=sa.value.get('block_states') if sa else None;tb=sb.value.get('block_states') if sb else None
     if ta==tb:continue
     air=([n.Tag(10,{'Name':n.Tag(8,'minecraft:air')})],[0]*4096)
     pa,ia=n.section_blocks(sa) if ta else air;pb,ib=n.section_blocks(sb) if tb else air
     ka=list(map(n.block_state_key,pa));kb=list(map(n.block_state_key,pb))
     for i,(x,y) in enumerate(zip(ia,ib)):
      if ka[x]==kb[y]:continue
      pos=((rx*32+cx)*16+i%16,sy*16+i//256,(rz*32+cz)*16+i//16%16);report['changedCells']+=1
      if pos not in allowed:issue('block-state',pos=pos,before=ka[x],after=kb[y])
data=W/'data/bloodborne_dw_composite_owners.dat'
index=n.read_nbt(data).root.value['data'].value['OwnerShardChunks'].value
known={};empty=[]
for path in (W/'data').rglob('owners-*.nbt'):
 root=n.read_nbt(path).root.value;cx=root['ChunkX'].value;cz=root['ChunkZ'].value;key=(cx&0xffffffff)|((cz&0xffffffff)<<32);known[key-(1<<64) if key>=1<<63 else key]=path
 if not any(root.get(k,n.Tag(9,[],10)).value for k in ('cells','ownerMembership','ownerTable','dataTable','shapeTable')):empty.append(str(path.relative_to(W)))
for key in index:
 if key not in known:issue('dangling-owner-shard',chunk=key)
report['emptyOwnerShards']=empty
if empty:issue('empty-owner-shards',count=len(empty))
report['status']='PASS' if not report['errors'] else 'REVIEW_REQUIRED'
(runDir/'map-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
