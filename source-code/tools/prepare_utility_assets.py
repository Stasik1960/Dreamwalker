"""Small deterministic utility textures; no generated world/model conversion."""
import json,struct,zlib
from pathlib import Path
P=Path(__file__).resolve().parents[1];A=P/'src/architecture/resources/assets/bloodborne_dw'
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def chunk(name,data):return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data))
pixels=b''.join(b'\x00'+b'\x00\x00\x00\xff'*16 for _ in range(16))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(pixels,9))+chunk(b'IEND',b'')
(A/'textures/block/dw_darkness.png').write_bytes(png)
write(A/'models/block/dw_darkness.json',{'parent':'minecraft:block/cube_all','textures':{'all':'bloodborne_dw:block/dw_darkness'}})
write(A/'models/block/dw_fake_water.json',{'textures':{'particle':'minecraft:block/water_still'},'elements':[]})
for name in ('dw_darkness','dw_fake_water'):
 write(A/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'bloodborne_dw:block/{name}'}}})
 write(A/'models/item'/f'{name}.json',{'parent':f'bloodborne_dw:block/{name}'} if name=='dw_darkness' else {'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:block/water_still'}})
for lang,names in [('ru_ru',['Тьма','Фальшивая вода']),('en_us',['Darkness','Faux Water'])]:
 path=A/'lang'/f'{lang}.json';data=json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
 for name,title in zip(('dw_darkness','dw_fake_water'),names):data['block.bloodborne_dw.'+name]=title
 write(path,data)
(P/'utility-evidence/assets.json').write_text(json.dumps({'blackTextureBytes':len(png),'dimensions':[16,16],'uniqueRGBA':[[0,0,0,255]],'waterTexture':'minecraft:block/water_still','extraAnimatedTextures':0},indent=2),encoding='utf-8')
