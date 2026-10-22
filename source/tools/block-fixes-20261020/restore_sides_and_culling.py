from common import *
import copy
log_to('side-textures')
selected=read(OUT/'selected-materials.json');types={(t['registryId'],t['variant']):t for t in selected['slabs']};changed=[]
for row in selected['materials']:
 t=types[row['source'],row['variant']];top=t['top'];m=model(top['model']);textures=m.get('textures',{});original={}
 for e in m.get('elements',[]):
  for side,face in e.get('faces',{}).items():
   if side not in original:original[side]=face
 for part,lo,hi in [('bottom',0,8),('top',8,16),('double',0,16)]:
  p=RES/'assets/bloodborne_dw/models/block/restored_slabs'/(row['id'].split(':')[1]+'_'+part+'.json');d=read(p)
  for side,face in d['elements'][0]['faces'].items():
   if side!='up' and side not in top['element']['faces'] and side in original:
    face=copy.deepcopy(original[side]);tex=face['texture']
    while tex.startswith('#'):tex=textures[tex[1:]]
    face['texture']=tex;d['elements'][0]['faces'][side]=face;changed.append({'model':str(p.relative_to(RES)),'face':side,'source':top['model']})
   face.pop('cullface',None)
   if side not in ['up','down'] or side=='up' and hi==16 or side=='down' and lo==0:face['cullface']=side
  write(p,d)
write(OUT/'original-side-textures-restored.json',changed)
print('PASS',len(changed),'original side/bottom face assignments; inner half-height faces do not use boundary culling')
