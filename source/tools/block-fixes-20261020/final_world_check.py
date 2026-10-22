from common import *
from collections import Counter
import time
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

def main():
 log_to('final-world-check');start=time.time();world=DELIVERY/'map/bloodborne-blocks-fixed'
 manifest=read(OUT/'world-before-manifest.json');expected={r['path'] for r in manifest}
 actual={p.relative_to(SOURCE_WORLD).as_posix() for p in files(SOURCE_WORLD) if p.name!='session.lock'}
 assert actual==expected,'Original map file inventory changed'
 for row in manifest:
  p=SOURCE_WORLD/row['path'];assert p.stat().st_size==row['bytes'] and sha(p)==row['sha256'],row['path']
 assert sha(BASE)=='3edcabcdb40beefa7b885bd0e9076c64bdf67ef8be96bfdcfde046aa4a41b89d'
 backup=DELIVERY/'backup/bloodborne-before-block-fixes.zip';assert sha(backup)==read(OUT/'world-copy.json')['backupSha256']
 selected={(r['registryId'],r['variant']) for r in read(OUT/'selected-materials.json')['slabs']}
 removed_recipes={int(k) for k in read(OUT/'static-slab-remap.json')};chunks=0;all_region_payloads=0;nbt_files=0;empty_regions=0;slabs=Counter();kinds=Counter();forbidden=[];legacy_temps=[]
 for p in files(world):
  if p.suffix=='.mca':
   if p.stat().st_size==0:empty_regions+=1;continue
   region=RegionFile.open(p)
   for chunk in region.chunks():
    raw=chunk.raw_nbt();all_region_payloads+=1
    if b'source_invisible' in raw:forbidden.append(str(p.relative_to(world))+':'+str((chunk.x,chunk.z)))
    if p.parent.name!='region':continue
    chunks+=1
    if b'bloodborne_dw:' not in raw:continue
    root=compound(decode_nbt(raw).root)
    for sec in root.get('sections',Tag(9,[],10)).value:
     data=compound(sec).get('block_states')
     if data is None:continue
     palette=compound(data)['palette'].value;native={}
     for pi,entry in enumerate(palette):
      d=compound(entry);id=d['Name'].value;props={k:v.value for k,v in d.get('Properties',Tag(10,{})).value.items()}
      assert 'source_invisible' not in id,id
      assert (id,int(props.get('variant','0'))+16*int(props.get('art_bank','0'))) not in selected,(id,props)
      if id.startswith('bloodborne_dw:static_architecture_'):
       index=int(id.rsplit('_',1)[1])*128+int(props.get('part','0'));assert index not in removed_recipes,index
      if id.startswith('bloodborne_dw:restored_slab_'):
       assert props.get('waterlogged')=='false',(id,props);native[pi]=(id,props['type'])
     if native:
      _,indices=section_blocks(sec);hist=Counter(indices)
      for pi,(id,kind) in native.items():slabs[id]+=hist[pi];kinds[kind]+=hist[pi]
   print('REGION',p.relative_to(world),'chunks',chunks,flush=True)
  elif p.suffix in ['.dat','.nbt','.dat_old'] and p.name!='session.lock':
   raw=p.read_bytes()
   try:raw=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw
   except EOFError:
    # An interrupted old level save is not an active Minecraft level file.
    # Preserve it exactly and inspect every byte emitted by its stored stream.
    assert p.parent==world and p.name.startswith('level') and p.stem[5:].isdigit(),str(p)
    assert sha(p)==sha(SOURCE_WORLD/p.relative_to(world)),str(p)
    partial=zlib.decompressobj(16+zlib.MAX_WBITS);raw=partial.decompress(raw)
    legacy_temps.append({'path':p.name,'unchangedFromInput':True,'availableNbtBytesScanned':len(raw),'gzipEndReached':partial.eof})
   except Exception as failure:raise RuntimeError('NBT scan '+str(p)) from failure
   nbt_files+=1
   if b'source_invisible' in raw:forbidden.append(str(p.relative_to(world)))
 assert not forbidden,forbidden
 conversion=read(OUT/'conversion.json');assert chunks==read(OUT/'world-audit.json')['chunks'],chunks
 assert sum(slabs.values())==conversion['stats']['placements'],sum(slabs.values())
 assert kinds['bottom']==conversion['stats']['type_bottom'] and kinds['top']==conversion['stats']['type_top'],dict(kinds)
 write(OUT/'final-world-check.json',{'status':'PASS','seconds':time.time()-start,'sourceMapFilesHashVerified':len(manifest),'sourceMapUnchanged':True,'originalJarUnchanged':True,'backupSha256':sha(backup),'chunks':chunks,'allRegionPayloadsScanned':all_region_payloads,'otherNbtFilesScanned':nbt_files,'emptyRegionFiles':empty_regions,'unchangedIncompleteTemporaryLevelFiles':legacy_temps,'remainingInvisibleReferences':0,'remainingSelectedLegacySlabStates':0,'remainingSelectedStaticSlabRecipes':0,'nativeMaterialSlabCells':sum(slabs.values()),'nativeMaterialSlabTypes':dict(kinds),'allNewMaterialSlabsDry':True,'materialCells':dict(slabs)})
 print('PASS',chunks,'chunks;',sum(slabs.values()),'slab cells; original',len(manifest),'files unchanged')
if __name__=='__main__':main()
