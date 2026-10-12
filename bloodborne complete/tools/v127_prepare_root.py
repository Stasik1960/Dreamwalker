"""Append only fresh utility IDs and generated utility assets; capture the V12.6 baseline."""
from pathlib import Path
import json,subprocess,hashlib,struct,zlib
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
doc=ROOT/'docs/v12.7';doc.mkdir(exist_ok=True)
if not (doc/'BASELINE.json').exists():
 write(doc/'BASELINE.json',{'revision':'V12.7-final','baseCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'originalV12Commit':'821de077917f696a13600e900a244ffc05230c2f','noGallery':True,'fullMapConversion':'PREPARE_ONLY_UNTIL_EXPLICIT_COMMAND','workingStorage':'C','pausedExternalMigration':True})
catalog=RES/'bloodborne_dw/debug_catalogue.json';data=read(catalog)
for number,path,name in [('93230','dw_barrier','DW-барьер'),('93231','dw_light','DW-свет')]:
 if any(e['registryId']=='bloodborne_dw:'+path for e in data['entries']):continue
 assert not any(e['temporaryId']==number for e in data['entries'])
 data['entries'].append({'temporaryId':number,'finalId':None,'name':name,'kind':'architecture','registryId':'bloodborne_dw:'+path,'aliases':['bloodborne_dw:'+path]})
write(catalog,data)
mix=RES/'bloodborne_dw.composite.mixins.json';data=read(mix)
for name in ['NoCollideBlockItemMixin','NoCollideInventoryMixin']:
 if name not in data['mixins']:data['mixins'].append(name)
if 'DwMarkerParticleMixin' not in data['client']:data['client'].append('DwMarkerParticleMixin')
write(mix,data)
assets=RES/'assets/bloodborne_dw'
def png(path,pixels):
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',zlib.crc32(t+b)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(v for pixel in row for v in pixel) for row in pixels)
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
# Repository-native pixel designs. Palette stays yellow for barrier BASE and ALT.
for path,color in [('dw_barrier',(255,218,38,255)),('dw_barrier_alt',(222,170,28,255))]:
 pixels=[]
 for y in range(16):
  row=[]
  for x in range(16):
   dx=x-7.5;dy=y-7.5;r=dx*dx+dy*dy
   row.append(color if 27<=r<=52 or abs(dx+dy)<=1.3 and r<=44 else (0,0,0,0))
  pixels.append(row)
 png(assets/f'textures/item/{path}.png',pixels)
for path,color in [('dw_light',(255,219,76,255)),('dw_light_alt',(231,183,48,255))]:
 pixels=[]
 for y in range(16):
  row=[]
  for x in range(16):
   d=abs(x-7.5)+abs(y-7.5)
   row.append((255,255,219,255) if d<3 else color if d<6 or x in (0,15) or y in (0,15) else (100,72,16,100))
  pixels.append(row)
 png(assets/f'textures/block/{path}.png',pixels)
for path in ['dw_barrier','dw_light']:
 for profile in ['base','alt']:
  suffix='' if profile=='base' else '_alt';texture=f'bloodborne_dw:'+('item/' if path=='dw_barrier' else 'block/')+path+suffix
  model={'parent':'minecraft:block/cube_all','textures':{'all':texture,'particle':texture}}
  write(assets/f'models/block/{path}{suffix}.json',model)
 write(assets/f'blockstates/{path}.json',{'variants':{'profile=base':{'model':f'bloodborne_dw:block/{path}'},'profile=alt':{'model':f'bloodborne_dw:block/{path}_alt'}}})
 write(assets/f'models/item/{path}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'bloodborne_dw:'+('item/' if path=='dw_barrier' else 'block/')+path}})
lang=assets/'lang/ru_ru.json';data=read(lang);data.update({'block.bloodborne_dw.dw_barrier':'DW-барьер','block.bloodborne_dw.dw_light':'DW-свет'});write(lang,data)
props=ROOT/'gradle.properties';text=props.read_text();text='\n'.join('mod_version=0.1.0-v12.7-final' if line.startswith('mod_version=') else line for line in text.splitlines())+'\n';props.write_text(text)
print(json.dumps({'status':'PASS','newIds':['93230','93231'],'baseline':read(doc/'BASELINE.json')['baseCommit']}))
