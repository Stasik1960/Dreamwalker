"""Remove only proven empty shards/Anvil files from the user's frozen world."""
import hashlib,json,re,sys,stat
from pathlib import Path
import world_io as w
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne').resolve();apply='--apply' in sys.argv
before=json.loads((D/'world-before.json').read_text(encoding='utf-8'));frozen={r['path']:r for r in before['entries']};audit=json.loads((D/'empty-shards-audit.json').read_text());pending=[];retired=set()
def verify(p):
 assert p.resolve().is_relative_to(W) and not p.stat().st_file_attributes&stat.FILE_ATTRIBUTE_REPARSE_POINT
 rel=p.relative_to(W).as_posix();assert hashlib.sha256(p.read_bytes()).hexdigest()==frozen[rel]['sha256'],'Input changed since backup: '+rel
 return rel
for row in audit['empty']:
 p=W/row['path'];n=w.read_nbt(p).root.value
 assert not any(n.get(k,w.Tag(9,[],10)).value for k in ('cells','ownerMembership','ownerTable','dataTable','shapeTable'))
 assert set(n)<=set(('Schema','ChunkX','ChunkZ','Dictionary','cells','ownerMembership','ownerTable','dataTable','shapeTable')),'Unknown shard payload'
 m=re.fullmatch(r'owners-(-?\d+)\.(-?\d+)\.nbt',p.name);cx,cz=map(int,m.groups());assert n['ChunkX'].value==cx and n['ChunkZ'].value==cz
 key=(cx&0xffffffff)|((cz&0xffffffff)<<32);retired.add(key-(1<<64) if key>=1<<63 else key);pending.append((p,verify(p),'empty owner shard'))
for row in before['entries']:
 rel=row['path'];p=W/rel
 if p.suffix=='.mca' and any(part in ('entities','poi','region') for part in p.relative_to(W).parts[:-1]):
  data=p.read_bytes()
  if len(data)==0 or len(data)>=8192 and not any(data[:4096]):pending.append((p,verify(p),'no live Anvil chunk entries'))
index=W/'data/bloodborne_dw_composite_owners.dat';verify(index);nbt=w.read_nbt(index);data=nbt.root.value['data'].value;chunks=data['OwnerShardChunks'];assert chunks.type==w.TAG_LONG_ARRAY
old=list(chunks.value);new=[k for k in old if k not in retired]
if apply:
 chunks.value=new
 temp=index.with_suffix('.dat.restoration-pending');w.write_nbt(temp,nbt);assert w.read_nbt(temp)==nbt;temp.replace(index)
 # Index no longer references any retired file before the files are removed.
 for p,rel,why in pending:p.unlink()
report={'status':'APPLIED' if apply else 'REVIEWED','world':str(W),'removedFiles':len(pending),'removedBytes':sum(frozen[rel]['bytes'] for _,rel,_ in pending),'indexedChunksBefore':len(old),'indexedChunksAfter':len(new),'unrelatedFieldsPreserved':True,'removed':[{'path':rel,'reason':why} for _,rel,why in pending]}
(D/('cleanup-result.json' if apply else 'cleanup-plan.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print({k:v for k,v in report.items() if k!='removed'})
