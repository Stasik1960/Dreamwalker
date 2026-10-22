from common import *
log_to('texture-references');count=0
for p in (RES/'assets/bloodborne_dw/models/block/restored_slabs').glob('*.json'):
 d=read(p);textures=d.setdefault('textures',{})
 for e in d['elements']:
  for side,face in e['faces'].items():
   texture=face['texture'];seen=set()
   while texture.startswith('#'):
    assert texture not in seen;seen.add(texture);texture=textures[texture[1:]]
   ns,name=texture.split(':',1);assert (RES/'assets'/ns/'textures'/(name+'.png')).is_file()
   key='face_'+side;textures[key]=texture;face['texture']='#'+key;count+=1
 write(p,d)
write(OUT/'texture-references.json',{'status':'PASS','models':count//6,'faces':count,'policy':'Every model face uses a named #texture reference and every named texture points to an existing original PNG.'});print('PASS',count)
