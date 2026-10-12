"""Build the yellow utility marker with the exact Minecraft barrier pixel silhouette."""
from pathlib import Path
import zipfile,struct,zlib,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
with zipfile.ZipFile(jar) as archive:original=archive.read('assets/minecraft/textures/item/barrier.png')
position=8;compressed=b''
while position<len(original):
 size=struct.unpack('>I',original[position:position+4])[0];kind=original[position+4:position+8];data=original[position+8:position+8+size];position+=size+12
 if kind==b'IHDR':width,height,bits,color,*_=struct.unpack('>IIBBBBB',data)
 if kind==b'IDAT':compressed+=data
assert (width,height,bits,color)==(16,16,8,6)
raw=zlib.decompress(compressed);rows=[];last=bytes(width*4);offset=0
for y in range(height):
 mode=raw[offset];line=bytearray(raw[offset+1:offset+1+width*4]);offset+=1+width*4
 for x in range(width*4):
  a=line[x-4] if x>=4 else 0;b=last[x];c=last[x-4] if x>=4 else 0
  if mode==1:predict=a
  elif mode==2:predict=b
  elif mode==3:predict=(a+b)//2
  elif mode==4:
   p=a+b-c;da,db,dc=abs(p-a),abs(p-b),abs(p-c);predict=a if da<=db and da<=dc else b if db<=dc else c
  else:assert mode==0;predict=0
  line[x]=(line[x]+predict)&255
 rows.append(bytes(line));last=bytes(line)
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
outputs=[]
for name,palette in [('dw_barrier',(255,218,38)),('dw_barrier_alt',(222,170,28))]:
 new=[]
 for row in rows:
  line=bytearray(row)
  for i in range(0,len(row),4):
   r,g,b,alpha=row[i:i+4]
   if alpha and r>g and r>b:
    line[i:i+3]=bytes(round(r*channel/255) for channel in palette)
  assert line[3::4]==row[3::4];new.append(b'\x00'+bytes(line))
 result=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,bits,color,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(new)))+chunk(b'IEND',b'')
 target=ROOT/f'src/architecture/resources/assets/bloodborne_dw/textures/item/{name}.png';target.write_bytes(result)
 outputs.append({'asset':str(target.relative_to(ROOT)),'sha256':hashlib.sha256(result).hexdigest()})
proof={'status':'PASS','vanillaPixelSourceSha256':hashlib.sha256(original).hexdigest(),'identicalAlphaSilhouette':True,'size':[width,height],'outputs':outputs}
out=ROOT/'docs/v12.7/utility/barrier-pixels.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof))
