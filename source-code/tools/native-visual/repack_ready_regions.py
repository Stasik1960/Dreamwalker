"""Reclaim superseded sectors in the NEW map only, preserving compressed chunks."""
import hashlib,json,os,stat,struct
from pathlib import Path
import sys
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024';WORLD=T/'world'
sys.path.insert(0,str(P/'tools'))
from world_io import RegionFile
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
receipt=T/'region-repack.json';assert not receipt.exists()
remap=json.loads((T/'map-remap.json').read_text(encoding='utf-8'));rows=[]
for name,info in remap['regions'].items():
    if not info['convertedChunks']:continue
    path=WORLD/name;assert path.resolve().is_relative_to(WORLD.resolve()) and sha(path)==info['sha256']
    for part in (path,*path.parents):assert not getattr(part.lstat(),'st_file_attributes',0)&1024
    before=path.stat().st_size;region=RegionFile.open(path);data=bytearray(8192);records=list(region.chunks())
    for chunk in records:
        assert not chunk.compression&128
        payload=bytes([chunk.compression])+chunk.compressed_payload;record=struct.pack('>I',len(payload))+payload
        size=(len(record)+4095)//4096;sector=len(data)//4096;assert size<=255
        slot=chunk.x+32*chunk.z;struct.pack_into('>I',data,slot*4,(sector<<8)|size);struct.pack_into('>I',data,4096+slot*4,chunk.timestamp)
        data.extend(record);data.extend(bytes(size*4096-len(record)))
    packed=RegionFile(bytes(data))
    for old in records:
        new=packed.get_chunk(old.x,old.z);assert new.timestamp==old.timestamp and new.compression==old.compression and new.compressed_payload==old.compressed_payload
    temporary=path.with_name(path.name+'.packed');temporary.write_bytes(data);os.replace(temporary,path)
    check=RegionFile.open(path)
    for old in records:assert check.get_chunk(old.x,old.z).compressed_payload==old.compressed_payload
    rows.append({'path':name,'beforeBytes':before,'afterBytes':path.stat().st_size,'beforeSha256':info['sha256'],'afterSha256':sha(path),'chunks':len(records),'compressedChunkPayloadsExact':True})
result={'status':'NEW_MAP_REGION_REPACK_PAYLOAD_EXACT','world':str(WORLD),'files':rows,'beforeBytes':sum(r['beforeBytes'] for r in rows),'afterBytes':sum(r['afterBytes'] for r in rows),'sourceWorldWrites':0,'chunkNBTChanged':False}
receipt.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='files'}))
