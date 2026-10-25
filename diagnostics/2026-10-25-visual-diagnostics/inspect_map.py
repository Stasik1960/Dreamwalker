import hashlib,json,sys,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent;P=O/'recovered/source';sys.path.insert(0,str(P/'tools'))
from world_io import RegionFile,compound,section_blocks,_plain
jar=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar');W=O/'published-map/bloodborne-native-grid-city'
with zipfile.ZipFile(jar) as z:
 catalog=json.loads(z.read('assets/bloodborne_dw/native_grid/catalog.json'));layout=json.loads(z.read('assets/bloodborne_dw/native_grid/visual_physics.json'))
(O/'catalog-structure.json').write_text(json.dumps({'catalogKeys':list(catalog),'layoutKeys':list(layout),'cell0':catalog['cells'][0],'layoutSample':{k:(v[:2] if isinstance(v,list) else str(v)[:400]) for k,v in layout.items()}},ensure_ascii=False,indent=2),encoding='utf-8')
cache={}
def chunk(x,z):
 cx,cz=x//16,z//16;k=cx,cz
 if k not in cache:
  f=W/'region'/f'r.{cx//32}.{cz//32}.mca';r=RegionFile(f.read_bytes());c=r.get_chunk(cx%32,cz%32);cache[k]=compound(c.nbt().root) if c else {}
 return cache[k]
def at(x,y,z):
 root=chunk(x,z);sections=root.get('sections');result={'pos':[x,y,z],'state':None,'visual':None}
 if not sections:return result
 for section in sections.value:
  s=compound(section)
  if s['Y'].value!=y//16:continue
  b=section_blocks(section)
  if b:result['state']=_plain(b[0][b[1][((y&15)<<8)|((z&15)<<4)|(x&15)]])
  i=((y&15)<<8)|((z&15)<<4)|(x&15)
  for name in ('SkyLight','BlockLight'):
   light=s.get(name);a=light.value if light else None;result[name]=((a[i//2]>>(4*(i%2)))&15) if a is not None else None
 meta=root.get('DreamwalkerVisuals')
 if meta:
  m=compound(meta);result['catalog']=m['Catalog'].value
  for section in m['Sections'].value:
   s=compound(section)
   if s['Y'].value!=y//16:continue
   for packed in s['Entries'].value:
    if packed&4095==i:
     vi=packed>>12;result['visual']=vi;result['cell']=catalog['cells'][vi]
 for be in root.get('block_entities',type('T',(),{'value':[]})()).value:
  n=compound(be)
  if [n[k].value for k in ('x','y','z')]==[x,y,z]:result['blockEntity']=_plain(be)
 return result
coords=[(166,65,-829),(142,74,-939),(84,48,-1011),(24,48,-1011),(-25,26,-1070),(30,0,-961),(75,26,-851),(62,66,-660),(143,171,-481)]
records=[]
for p in coords:
 item=at(*p);item['neighborhood']=[at(p[0]+dx,p[1]+dy,p[2]+dz) for dx,dy,dz in ((0,1,0),(0,-1,0),(-1,0,0),(1,0,0),(0,0,-1),(0,0,1))];records.append(item)
(O/'map-targets.json').write_text(json.dumps({'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'world':str(W),'records':records},ensure_ascii=False,indent=2),encoding='utf-8')
