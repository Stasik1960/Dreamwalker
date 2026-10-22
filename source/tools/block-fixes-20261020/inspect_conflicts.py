from common import *
from collections import Counter
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import *
from world_io import _plain
log_to('conflicts');issues=read(OUT/'native-slab-preflight.json')['issues'];print('ISSUES',len(issues),Counter((x['reason'],x.get('state','')) for x in issues))
rows={tuple(r['position']):r for r in read(OUT/'all-slab-placements.json')};materials={m['id']:m for m in read(OUT/'selected-materials.json')['materials']};seen=set()
for issue in issues[:12]:
 dim,x,y,z=issue['position'];row=rows[tuple(issue['position'])];print('DEST',issue,'PLAN',row,'MATERIAL',materials[row['id']]);path=SOURCE_WORLD/dim/'region'/f'r.{x>>9}.{z>>9}.mca';region=RegionFile.open(path)
 chunk=region.get_chunk((x>>4)&31,(z>>4)&31);root=compound(chunk.nbt().root);secs={compound(s)['Y'].value:section_blocks(s) for s in root['sections'].value}
 for Y in range(y-1,y+3):
  bs=secs.get(Y>>4);e=bs[0][bs[1][((Y&15)<<8)|((z&15)<<4)|(x&15)]];print('ORIGINAL COLUMN',Y,block_state_key(e))
 for be in root.get('block_entities',Tag(9,[],10)).value:
  d=compound(be)
  if abs(d['x'].value-x)<=2 and abs(d['z'].value-z)<=2 and y-1<=d['y'].value<=y+2 and 'resident' in d:
   print('BE',_plain(Tag(10,{k:v for k,v in d.items() if k not in ['OwnerDataV1','owners']})))
print('PASS')
