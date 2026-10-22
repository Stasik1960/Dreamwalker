from common import *
from collections import Counter
import gzip,zlib,time
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
from convert_pavement import v1,state,variant

def main():
 log_to('verify-world');start=time.time();before=read(OUT/'world-before-manifest.json');now_source={p.relative_to(SOURCE_WORLD).as_posix() for p in files(SOURCE_WORLD) if p.name!='session.lock'};assert now_source=={r['path'] for r in before}
 for r in before:assert sha(SOURCE_WORLD/r['path'])==r['sha256'],r['path']
 assert sha(BASE)=='9bc881591b35eb702c637025cee2c4f2df18cb8ad80af95356aaabecfdd27bfe';assert sha(DELIVERY/'backup/bloodborne-before-block-fixes.zip')==read(OUT/'world-copy.json')['backupSha256']
 mats=read(OUT/'new-pavement-materials.json');selected={(m['source'],m['variant']) for m in mats};ids={m['id'] for m in mats};sources={m['source'] for m in mats};removed={int(i) for i in read(OUT/'static-pavement-remap.json')};markers=[s.encode() for s in sources]+[('bloodborne_dw:static_architecture_'+str(i//128)).encode() for i in removed]+[b'bloodborne_dw:restored_slab_decor_'];markers=list(set(markers));chunks=0;payloads=0;counts=Counter();kinds=Counter();empty_shards=[];names=set();nbt_files=0
 for p in files(WORLD):
  rel=p.relative_to(WORLD).as_posix();names.add(rel)
  if p.suffix=='.mca' and p.stat().st_size:
   region=RegionFile.open(p)
   for c in region.chunks():
    raw=c.raw_nbt();payloads+=1;assert b'source_invisible' not in raw,(rel,c.x,c.z)
    if p.parent.name!='region':continue
    chunks+=1
    if not any(m in raw for m in markers):continue
    r=compound(decode_nbt(raw).root)
    for sec in r.get('sections',Tag(9,[],10)).value:
     bs=compound(sec).get('block_states')
     if bs is None:continue
     palette=compound(bs)['palette'].value;wanted={}
     for pi,e in enumerate(palette):
      id,props=state(e);assert (id,variant(props)) not in selected,(rel,id,props)
      if id.startswith('bloodborne_dw:static_architecture_'):assert v1.static_index(id,props) not in removed,(rel,id,props)
      if id in ids:assert props['waterlogged']=='false';wanted[pi]=(id,props['type'])
     if wanted:
      _,indices=section_blocks(sec);hist=Counter(indices)
      for pi,(id,typ) in wanted.items():counts[id]+=hist[pi];kinds[typ]+=hist[pi]
    for tag in r.get('block_entities',Tag(9,[],10)).value:
     be=compound(tag)
     if 'OwnerDataV1' not in be and 'owners' not in be:continue
     for e in v1.owners(be):
      id,props,_=v1.rootdata(compound(e)['rootData']);assert (id,variant(props)) not in selected,('legacy floor owner remained',rel,id)
   print('REGION',rel,chunks,flush=True)
  elif p.suffix in ['.dat','.dat_old','.nbt']:
   raw=p.read_bytes()
   if raw[:2]==b'\x1f\x8b':
    try:raw=gzip.decompress(raw)
    except EOFError:
     assert p.parent==WORLD and p.stem.startswith('level') and p.stem[5:].isdigit();assert sha(p)==sha(SOURCE_WORLD/rel);raw=zlib.decompressobj(16+zlib.MAX_WBITS).decompress(raw)
   nbt_files+=1;assert b'source_invisible' not in raw,rel
   if p.parent.name=='bloodborne_dw_owner_shards' and len(raw)<1024:
    r=compound(decode_nbt(raw).root);lists=['cells','ownerMembership','ownerTable','dataTable','shapeTable']
    if all(k in r and not r[k].value for k in lists):empty_shards.append(rel)
   if p.parent.name=='bloodborne_dw_owner_shards' and any(s.encode() in raw for s in sources):
    r=compound(decode_nbt(raw).root)
    for data in r['dataTable'].value:
     id,props,_=v1.rootdata(data);assert (id,variant(props)) not in selected,('legacy floor shard data remained',rel,id)
 assert chunks==39485 and sum(counts.values())==1151,(chunks,sum(counts.values()));assert not empty_shards,empty_shards
 oldclean=read(ROOT/'deliverables/map-cleanup-20261019/plan.json');assert not names&{r['path'] for r in oldclean['removed']}
 junk=[n for n in names if ('history' in n.split('/')[0] and n.startswith('dw-')) or n.endswith(('.log','.jfr','.hprof','.jsonl','.zip','.sqlite','.sqlite-wal','.sqlite-shm','.db'))];assert not junk,junk
 maximum=0
 for row in read(OUT/'installed-slab-states.json'):
  shift=row['position'][2]+(.5 if row['type']=='bottom' else 1)-row['canonicalSurface']-row['oldRoot'][1]-row['artWorldShiftY'];maximum=max(maximum,abs(shift));assert abs(shift)<1e-7,row
 write(OUT/'world-verification.json',{'status':'PASS','seconds':time.time()-start,'chunks':chunks,'regionPayloadsScanned':payloads,'otherNbtFilesScanned':nbt_files,'newPavementCells':sum(counts.values()),'types':dict(kinds),'remainingLegacyPavementStates':0,'remainingInvisibleReferences':0,'emptyOwnerShards':0,'previousCleanupReintroducedFiles':0,'historyLogsCachesOrNestedArchives':0,'previousMapFilesHashVerifiedUnchanged':len(before),'previousJarUnchanged':True,'maxDecorVertexWorldVerticalShift':maximum,'currentMapFiles':len(names)});print('PASS',chunks,sum(counts.values()),'new pavement cells;',len(before),'previous map files unchanged')
if __name__=='__main__':main()
