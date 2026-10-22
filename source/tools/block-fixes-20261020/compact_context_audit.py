from common import *
from collections import Counter
from functools import lru_cache
import common
common.model=lru_cache(None)(common.model)
def condition(c,p):
 return all(any(condition(x,p) for x in v) if k=='OR' else all(condition(x,p) for x in v) if k=='AND' else p.get(k,'false') in str(v).split('|') for k,v in c.items())
@lru_cache(None)
def native_bounds(source,variant,yaw):
 props={'variant':str(variant%16),'art_bank':str(variant//16),'rotation':str(yaw),'compact':'true','open':'false','profile':'base'};d=read(RES/'assets/bloodborne_dw/blockstates'/(source.split(':')[1]+'.json'));applications=[]
 if 'multipart' in d:
  for row in d['multipart']:
   if 'when' not in row or condition(row['when'],props):applications.extend(row['apply'] if isinstance(row['apply'],list) else [row['apply']])
 else:
  for k,app in d['variants'].items():
   if condition(dict(v.split('=') for v in k.split(',') if v),props):applications.extend(app if isinstance(app,list) else [app])
 parts=[{'model':a['model'],'yaw':a.get('y',0),'pitch':a.get('x',0),'offset':[0,0,0]} for a in applications]
 b,_=bounds(parts);return b,props,applications
def main():
 log_to('compact-context');rows=read(OUT/'all-slab-placements.json');mats={m['id']:m for m in read(OUT/'selected-materials.json')['materials']};stats=Counter();cases={}
 for row in rows:
  if row.get('priority')!=0 or 'component' in row:continue
  m=mats[row['id']];b,props,apps=native_bounds(m['source'],m['variant'],['north','east','south','west'].index(row['facing'])*2);assert b is not None
  dy=b[1][1]-b[0][1];kind='half' if abs(dy-8)<.01 else 'full' if abs(dy-16)<.01 else 'plane' if dy<.02 else 'other'
  stats[kind]+=1;cases[m['source']+'/'+str(m['variant'])]={'kind':kind,'nativeBounds':b,'applications':apps,'properties':props}
 write(OUT/'compact-context.json',{'stats':dict(stats),'types':cases});print('STATS',dict(stats));print('TYPES',cases)
 issues=read(OUT/'native-slab-preflight.json')['issues'];foreign=Counter()
 for issue in issues:
  row=next((r for r in rows if r['position']==issue['position']),None);m=mats[row['id']];foreign[tuple(m['cell'])]+=1
 print('CONFLICT TILE OFFSETS',dict(foreign));print('PASS')
if __name__=='__main__':main()
