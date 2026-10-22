"""Bounded, targeted OOM evidence from saved discovery and clean clone metadata."""
import collections,gzip,importlib.util,json,sqlite3,zlib
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.control_check();db=m.connect(m.DEFAULT_PLAN);copy=m.ROOT/'research'/'pilot'/'candidate';report={'worldScan':False,'copyModified':False}
def bounded_nbt(path,limit=16*1024*1024):
    with gzip.open(path,'rb') as stream:raw=stream.read(limit+1)
    if len(raw)>limit:return {'compressedBytes':path.stat().st_size,'rawBytesGreaterThan':limit},None
    return {'compressedBytes':path.stat().st_size,'rawBytes':len(raw)},m.compound(m.decode_nbt(raw).root)
ledger=copy/'data'/'bloodborne_dw_composite_owners.dat';info,root=bounded_nbt(ledger)
if root:
    data=m.compound(root.get('data',m.Tag(10,root)));info.update({'rootKeys':list(data),'OwnerShardSchema':data.get('OwnerShardSchema',m.Tag(3,0)).value,'indexedShardChunks':len(data.get('OwnerShardChunks',m.Tag(12,[])).value),'globalCells':len(data.get('cells',m.Tag(9,[],10)).value)})
report['globalLedger']=info
shards=sorted((copy/'data'/'bloodborne_dw_owner_shards').glob('*.nbt'),key=lambda p:p.stat().st_size,reverse=True)
report['copiedShards']={'count':len(shards),'compressedBytes':sum(p.stat().st_size for p in shards),'largest':[]}
for path in shards[:3]:
    info,root=bounded_nbt(path)
    if root:info.update({k:len(root.get(k,m.Tag(9,[],10)).value) for k in ('ownerTable','dataTable','shapeTable','cells','ownerMembership')})
    report['copiedShards']['largest'].append({'path':path.name,**info})
versions=collections.Counter();raw_sizes=[];compressed=bes=packed=legacy=0
for dim,cx,cz in db.execute('SELECT dim,cx,cz FROM source_chunks'):
    compression,payload=db.execute('SELECT compression,payload FROM old.chunk_cache WHERE dim=? AND x=? AND z=?',(dim,cx,cz)).fetchone()
    compressed+=len(payload);raw=gzip.decompress(payload) if compression==1 else zlib.decompress(payload) if compression==2 else payload
    raw_sizes.append(len(raw));root=m.compound(m.decode_nbt(raw).root);versions[root.get('DataVersion',m.Tag(3,-1)).value]+=1
    for tag in root.get('block_entities',m.Tag(9,[],10)).value:
        be=m.compound(tag);bes+=1;packed+=int('OwnerDataV1' in be);legacy+=int('owners' in be)
report['plannedSavedChunks']={'count':len(raw_sizes),'dataVersions':dict(versions),'compressedBytes':compressed,'rawNbtBytes':sum(raw_sizes),'maxRawChunkBytes':max(raw_sizes,default=0),'blockEntities':bes,'packedOwnerBEs':packed,'legacyOwnerListBEs':legacy}
level,root=bounded_nbt(copy/'level.dat')
if root:
    data=m.compound(root.get('Data',m.Tag(10,root)));level.update({'DataVersion':data.get('DataVersion',m.Tag(3,-1)).value,'Spawn':[data.get(k,m.Tag(3,0)).value for k in ('SpawnX','SpawnY','SpawnZ')]})
report['level']=level;m.atomic_json(m.ROOT/'research'/'pilot-copy-metadata-audit.json',report);db.close()
