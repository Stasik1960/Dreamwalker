import collections,json,struct,zipfile
from pathlib import Path
O=Path(__file__).resolve().parent;jar=Path('C:/Users/vakir/AppData/Local/Limacina/project/DreamWalker/mods/dreamwalker-bb-0.1.0-v12.7.4-visual-physics.1.jar')
doc=json.loads((O/'map-targets.json').read_text(encoding='utf-8'));points=[p for r in doc['records'] for p in [r,*r['neighborhood']]]
with zipfile.ZipFile(jar) as z:
 cat=json.loads(z.read('assets/bloodborne_dw/native_grid/catalog.json'));layout=json.loads(z.read('assets/bloodborne_dw/native_grid/visual_physics.json'));needed={p['cell']['mesh'] for p in points if 'cell' in p};needed.add(cat['cells'][layout['representatives'][0]]['mesh']);meshes={}
 with z.open('assets/bloodborne_dw/native_grid/meshes.bin') as f:
  magic,count=struct.unpack('>II',f.read(8))
  for m in range(count):
   n=struct.unpack('>I',f.read(4))[0]
   if m not in needed:
    left=n*139
    while left:q=f.read(min(left,1<<20));left-=len(q)
    continue
   raw=f.read(n*139);quads=[]
   for q in range(n):
    data=raw[q*139:(q+1)*139];mat,cull,face,tint,flags=struct.unpack_from('>ibbib',data);vs=[struct.unpack_from('>5f3I',data,11+32*v) for v in range(4)];quads.append({'cull':cull,'face':face,'flags':flags,'material':cat['materials'][mat],'xyz':[v[:3] for v in vs],'colors':[hex(v[5]) for v in vs],'lights':[hex(v[6]) for v in vs]})
   meshes[m]={'quadCount':n,'quads':quads}
 for p in points:
  if 'cell' not in p:continue
  c=p['cell'];p['geometry']=meshes[c['mesh']];p['opticsData']=cat['optics'][c['optics']];p['shapeData']={key:cat['shapes'][c[key]] for key in ('collision','outline','culling')}
 default=cat['cells'][layout['representatives'][0]]
(O/'target-geometry.json').write_text(json.dumps({'records':doc['records'],'defaultPhysics0':{'cell':default,'geometry':meshes[default['mesh']],'optics':cat['optics'][default['optics']]}},ensure_ascii=False,indent=2),encoding='utf-8')
for p in doc['records']:
 print(p['pos'],p['state'],'vi',p.get('visual'),'quads',p.get('geometry',{}).get('quadCount'),'light',p.get('SkyLight'),'optics',p.get('opticsData'))
