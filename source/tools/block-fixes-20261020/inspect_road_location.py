from common import *
from collections import Counter
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *

def main():
 log_to('road-location');fixed=DELIVERY/'map/bloodborne-blocks-fixed';catalog=read(RES/'bloodborne_dw/static-architecture-v1274.json');poses={p['key']:p for p in catalog['poses']};materials={m['id']:m for m in read(RES/'bloodborne_dw/restored-material-slabs.json')['materials']};invisible=read(RES/'bloodborne_dw/invisible-source-carriers-v1271.json')['sourceStates'];cache={}
 def cell(world,x,y,z):
  key=(str(world),x>>4,z>>4)
  if key not in cache:
   r=RegionFile.open(world/'region'/f'r.{x>>9}.{z>>9}.mca');c=r.get_chunk((x>>4)&31,(z>>4)&31);root=compound(c.nbt().root);secs={compound(s)['Y'].value:section_blocks(s) for s in root['sections'].value};cache[key]=secs
  bs=cache[key].get(y>>4)
  if not bs:return {'Name':'minecraft:air','Properties':{}}
  d=compound(bs[0][bs[1][((y&15)<<8)|((z&15)<<4)|(x&15)]]);return {'Name':d['Name'].value,'Properties':{k:v.value for k,v in d.get('Properties',Tag(10,{})).value.items()}}
 rows=[]
 for z in range(-1010,-959):
  line=''
  for x in range(125,161):
   now=cell(fixed,x,74,z);old=cell(SOURCE_WORLD,x,74,z);id=now['Name'];p=now['Properties'];ch='?';extra={}
   if id=='minecraft:air':ch='.'
   elif id.startswith('bloodborne_dw:restored_slab_'):ch='S';extra['material']=materials[id]
   elif id.startswith('bloodborne_dw:static_architecture_'):
    ch='D';ri=int(id.rsplit('_',1)[1])*128+int(p['part']);r=catalog['recipes'][ri];extra={'recipe':ri,'resident':r['resident'],'collision':r['collision'],'fragments':[{'source':poses[f['pose']]['registryId'],'variant':poses[f['pose']]['properties'].get('variant'),'pose':f['pose'],'origin':f['origin'],'poseShift':poses[f['pose']].get('shift'),'mount':poses[f['pose']].get('mountY')} for f in r['fragments']]}
   elif id=='bloodborne_dw:composite_cell':ch='c'
   elif id.startswith('bloodborne_dw:source_'):ch='R'
   elif id.startswith('minecraft:'):ch='#'
   if 'source_invisible' in old['Name']:extra['originalCarrier']=invisible[int(old['Properties']['source'])]
   if ch in ['.','D','R','c']:rows.append({'position':[x,74,z],'current':now,'original':old,**extra})
   line+=ch
  print(z,line)
 write(OUT/'road-location.json',rows)
 for row in rows:
  x,y,z=row['position']
  if 137<=x<=150 and -997<=z<=-975:print(json.dumps(row,ensure_ascii=False))
 print('TARGET',materials.get('bloodborne_dw:restored_slab_c66444efa072e824'))
if __name__=='__main__':main()
