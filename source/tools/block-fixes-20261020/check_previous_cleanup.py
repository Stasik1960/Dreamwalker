from common import *
from collections import Counter
import gzip
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import decode_nbt,compound

def main():
 log_to('previous-cleanup-check')
 old=ROOT/'deliverables/map-cleanup-20261019'
 plan=read(old/'plan.json');manifest=read(old/'MANIFEST.json')
 world=DELIVERY/'map/bloodborne-blocks-fixed';archive=DELIVERY/'map/bloodborne-blocks-fixed.zip'
 rows=list(files(world));names={p.relative_to(world).as_posix() for p in rows}
 removed={r['path'] for r in plan['removed']}
 with zipfile.ZipFile(archive) as z:archived={n.removeprefix('bloodborne-blocks-fixed/') for n in z.namelist()}
 assert archived==names
 old_present=sorted(names&removed);junk=sorted(n for n in names if ('history' in n.split('/')[0] and n.startswith('dw-')) or n.endswith(('.jfr','.hprof','.log','.jsonl','.zip','.sqlite','.sqlite-shm','.sqlite-wal','.db')))
 empties=[];shards=0;bydir=Counter();counts=Counter()
 for p in rows:
  rel=p.relative_to(world).as_posix();bydir[rel.split('/')[0]]+=p.stat().st_size;counts[rel.split('/')[0]]+=1
  if not rel.startswith('data/bloodborne_dw_owner_shards/'):continue
  shards+=1;data=p.read_bytes();raw=gzip.decompress(data) if data[:2]==b'\x1f\x8b' else data
  if len(raw)>1024:continue
  d=compound(decode_nbt(raw).root);lists={'cells','ownerMembership','ownerTable','dataTable','shapeTable'}
  if set(d)==lists|{'Schema','ChunkX','ChunkZ','Dictionary'} and d['Schema'].value==1 and d['Dictionary'].value==1 and all(d[k].type==9 and not d[k].value for k in lists):empties.append({'path':rel,'bytes':len(data)})
 result={'status':'PASS_READ_ONLY_AUDIT','archive':str(archive),'oldCleanup':{k:manifest[k] for k in ['beforeFiles','afterFiles','beforeBytes','afterBytes','removedEmptyShards','removedAuthoringFiles']},'previouslyRemovedPathsPresent':old_present,'historyLogsCachesOrNestedArchives':junk,'currentFiles':len(rows),'currentBytes':sum(p.stat().st_size for p in rows),'ownerShards':shards,'currentlyProvenEmptyShards':len(empties),'emptyShardBytes':sum(r['bytes'] for r in empties),'emptyShards':empties,'directoryBytes':dict(bydir),'directoryFiles':dict(counts)}
 write(OUT/'previous-cleanup-check.json',result)
 print(json.dumps({k:v for k,v in result.items() if k not in ['emptyShards','directoryBytes','directoryFiles']},ensure_ascii=False))
if __name__=='__main__':main()
