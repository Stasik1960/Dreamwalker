from common import *
from collections import Counter
log_to('compact-layout');path=OUT/'all-slab-placements.json';rows=read(path);mats={m['id']:m for m in read(OUT/'selected-materials.json')['materials']};context=read(OUT/'compact-context.json')['types'];counts=Counter();changed=0
for row in rows:
 if row.get('priority')!=0 or 'component' in row:continue
 m=mats[row['id']];case=context[m['source']+'/'+str(m['variant'])];b=case['nativeBounds'];assert case['kind']=='half' and abs(b[1][1]-b[0][1]-8)<.01
 assert abs(b[0][0])<.01 and abs(b[0][2])<.01 and abs(b[1][0]-16)<.01 and abs(b[1][2]-16)<.01
 kind='top' if abs(b[0][1]-8)<.01 else 'bottom';assert abs(b[0][1])<.01 or kind=='top';assert row['type']!='double'
 if row['type']!=kind:changed+=1
 row['type']=kind;row['storageContext']='native_compact';counts[kind]+=1
assert sum(counts.values())==read(OUT/'conversion-stage1.json')['stats']['native_source_slab_cells']
write(path,rows);write(OUT/'compact-layout.json',{'status':'PASS','validatedCompactCells':sum(counts.values()),'correctedTopBottomOrientation':changed,'types':dict(counts),'policy':'Compact states render the original unshifted blockstate model. Owned/static states render normalized descriptors plus SourceShift. Native half placement now matches each actual storage context.'});print('PASS',changed,dict(counts))
