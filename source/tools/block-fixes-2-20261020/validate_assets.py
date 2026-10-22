from common import *
from PIL import Image

def main():
 log_to('validate-pavement-assets');rows=read(OUT/'new-pavement-materials.json');checks=[];opaque=[]
 for r in rows:
  name=r['id'].split(':')[1];source=model(r['sourceModel'])
  for typ,top in [('bottom',8),('top',16),('double',16)]:
   d=read(RES/'assets/bloodborne_dw/models/block/restored_slabs'/(name+'_'+typ+'.json'));assert len(d['elements'])==1+len(source['elements']);shift=top-r['canonicalSurface']*16
   for old,new in zip(source['elements'],d['elements'][1:]):
    for key in ['from','to']:assert all(abs(a+(shift if axis==1 else 0)-b)<1e-7 for axis,(a,b) in enumerate(zip(old[key],new[key]))),(r['source'],key)
    if 'rotation' in old:
     assert old['rotation']['axis']==new['rotation']['axis'] and old['rotation']['angle']==new['rotation']['angle']
     assert all(abs(a+(shift if axis==1 else 0)-b)<1e-7 for axis,(a,b) in enumerate(zip(old['rotation']['origin'],new['rotation']['origin'])))
    for side,face in old['faces'].items():
     copied=new['faces'][side];assert face.get('uv')==copied.get('uv') and face.get('rotation',0)==copied.get('rotation',0)
     tex=face['texture']
     while tex.startswith('#'):tex=source['textures'][tex[1:]]
     assert d['textures'][copied['texture'][1:]]==tex
   for side,face in d['elements'][0]['faces'].items():
    tex=d['textures'][face['texture'][1:]];ns,path=tex.split(':',1);png=RES/'assets'/ns/'textures'/(path+'.png');im=Image.open(png).convert('RGBA');u0,v0,u1,v1=face['uv'];x0,x1=sorted((u0*im.width/16,u1*im.width/16));y0,y1=sorted((v0*im.height/16,v1*im.height/16));a=im.getchannel('A').crop((math.ceil(x0),math.ceil(y0),math.floor(x1),math.floor(y1)));assert a.getextrema()==(255,255),(r['source'],side,a.getextrema());opaque.append([r['id'],typ,side])
   checks.append([r['id'],typ])
 write(OUT/'asset-validation.json',{'status':'PASS','models':len(checks),'originalStoneGeometryUvTexturesPreserved':True,'completeSlabBaseFacesVerifiedOpaque':len(opaque),'materials':len(rows),'policy':'Opaque full paving cuboid plus original decorative elements. Only the intentional slab-type vertical offset is applied to the original elements; PNGs are unchanged.'});print('PASS',len(checks),'models;',len(opaque),'opaque base faces')
if __name__=='__main__':main()
