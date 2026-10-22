from common import *
import gzip
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

def main():
 log_to('prune-empty-shards');assert read(OUT/'conversion.json')['status']=='PASS';removed=[]
 for index_path in files(WORLD):
  if index_path.name!='bloodborne_dw_composite_owners.dat':continue
  directory=index_path.parent/'bloodborne_dw_owner_shards'
  if not directory.exists():continue
  empty=[]
  for p in files(directory):
   raw=p.read_bytes();raw=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw
   if len(raw)>1024:continue
   r=compound(decode_nbt(raw).root);names={'cells','ownerMembership','ownerTable','dataTable','shapeTable'}
   if set(r)==names|{'Schema','ChunkX','ChunkZ','Dictionary'} and all(r[k].type==9 and not r[k].value for k in names):
    x,z=r['ChunkX'].value,r['ChunkZ'].value;key=(x&0xffffffff)|((z&0xffffffff)<<32);key=key-(1<<64) if key>=1<<63 else key;empty.append((p,key))
  if not empty:continue
  nbt=read_nbt(index_path);r=compound(nbt.root);d=compound(r['data']) if 'data' in r else r;gone={key for _,key in empty};before=list(d['OwnerShardChunks'].value);assert gone<=set(before)
  d['OwnerShardChunks']=Tag(12,[n for n in before if n not in gone]);write_nbt(index_path,nbt)
  for p,key in empty:
   assert p.resolve().is_relative_to(WORLD.resolve());ordinary(p);removed.append({'path':p.relative_to(WORLD).as_posix(),'bytes':p.stat().st_size,'key':key});p.unlink()
 write(OUT/'empty-shards.json',{'status':'PASS','removedEmptyShards':len(removed),'removed':removed,'fullBeforeMapBackup':str(DELIVERY/'backup/bloodborne-before-block-fixes.zip')});print('PASS',len(removed),'newly emptied shards pruned with index entries')
if __name__=='__main__':main()
