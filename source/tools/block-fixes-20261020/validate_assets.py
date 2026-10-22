from common import *
import copy
log_to('asset-validation');selected=read(OUT/'selected-materials.json');types={(t['registryId'],t['variant']):t for t in selected['slabs']};errors=[];images={};repaired=[]
from PIL import Image
def image(texture):
 ns,name=texture.split(':',1);p=RES/'assets'/ns/'textures'/(name+'.png')
 if texture not in images:images[texture]=Image.open(p).convert('RGBA')
 return images[texture]
def opaque_sample(face,textures):
 tex=face['texture']
 while tex.startswith('#'):tex=textures[tex[1:]]
 img=image(tex);uv=face.get('uv',[0,0,16,16]);x0,x1=sorted([uv[0]*img.width/16,uv[2]*img.width/16]);y0,y1=sorted([uv[1]*img.height/16,uv[3]*img.height/16])
 if x0<-.01 or y0<-.01 or x1>img.width+.01 or y1>img.height+.01:return False
 box=(max(0,math.floor(x0)),max(0,math.floor(y0)),min(img.width,max(math.ceil(x1),math.floor(x0)+1)),min(img.height,max(math.ceil(y1),math.floor(y0)+1)))
 return img.getchannel('A').crop(box).getextrema()[1]>0
for row in selected['materials']:
 t=types[row['source'],row['variant']];top=t['top'];uv=top['face'].get('uv',[0,0,16,16]);textures=model(top['model']).get('textures',{})
 for part,lo,hi in [('bottom',0,8),('top',8,16),('double',0,16)]:
  p=RES/'assets/bloodborne_dw/models/block/restored_slabs'/(row['id'].split(':')[1]+'_'+part+'.json');d=read(p);faces=d['elements'][0]['faces'];before=copy.deepcopy(d)
  if t['kind']=='half':
   faces['up']['uv']=uv
   if 'down' in top['element']['faces']:
    f=copy.deepcopy(top['element']['faces']['down']);tex=f['texture']
    while tex.startswith('#'):tex=textures[tex[1:]]
    f['texture']=tex;faces['down']=f
  if t['kind']=='plane' and top['face'].get('rotation',0)%360 in [90,180,270]:
   # Crop in the rotated source UV axes. Face rotation is then retained on
   # each tile, so the original long atlas patch remains continuous.
   a=top['element']['from'];b=top['element']['to'];x,z=row['cell'];s0=(x*16-a[0])/(b[0]-a[0]);s1=((x+1)*16-a[0])/(b[0]-a[0]);t0=(z*16-a[2])/(b[2]-a[2]);t1=((z+1)*16-a[2])/(b[2]-a[2]);r=top['face']['rotation']%360
   u0,v0,u1,v1=(t0,1-s1,t1,1-s0) if r==90 else (1-s1,1-t1,1-s0,1-t0) if r==180 else (1-t1,s0,1-t0,s1)
   crop=[uv[0]+(uv[2]-uv[0])*u0,uv[1]+(uv[3]-uv[1])*v0,uv[0]+(uv[2]-uv[0])*u1,uv[1]+(uv[3]-uv[1])*v1]
   for face in faces.values():face['uv']=crop
  if not opaque_sample(faces['up'],d['textures']):
   other=next((copy.deepcopy(f) for f in faces.values() if opaque_sample(f,d['textures'])),None)
   if other is None:errors.append({'model':str(p.relative_to(RES)),'error':'No visible source face material'})
   else:faces['up']=other;repaired.append({'model':str(p.relative_to(RES)),'reason':'Visible material copied from another original face'})
  for side,face in faces.items():
   face.pop('cullface',None)
   if side not in ['up','down'] or side=='up' and hi==16 or side=='down' and lo==0:face['cullface']=side
   if not opaque_sample(face,d['textures']):errors.append({'model':str(p.relative_to(RES)),'face':side,'error':'Invalid UV or entirely transparent material'})
  assert set(faces)=={'down','up','north','south','west','east'}
  if d!=before:write(p,d);repaired.append({'model':str(p.relative_to(RES)),'reason':'Source UV / side / interior culling correction'})
write(OUT/'asset-validation.json',{'status':'PASS' if not errors else 'FAIL','baseMaterials':len(selected['materials']),'models':3*len(selected['materials']),'faces':18*len(selected['materials']),'sourceImages':len(images),'repairs':repaired,'errors':errors})
print('RESULT',len(errors),'errors',len(repaired),'model corrections');print(errors[:20])
if errors:raise RuntimeError('Review source face material errors')
