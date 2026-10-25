import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];R=P/'src/architecture/resources';d=json.loads((R/'assets/bloodborne_dw/native_grid/catalog.json').read_text())
def fix(s):
 for _ in range(4):
  try:s=s.encode('cp1251').decode('utf-8')
  except UnicodeError:break
 return s
print('whole count',len(d['wholeObjects']));print([(x['key'],fix(x['displayName']),x['baseCell']) for x in d['wholeObjects'] if 'скам' in fix(x['displayName']).lower() or 'ажур' in fix(x['displayName']).lower()])
for name in ('source_hold_bench','source_hold_bench_1','source_hold_bench_rotate'):
 data=json.loads((R/'bloodborne_dw/composite'/(name+'.json')).read_text());print(name,[(i,v['poses']['closed']['parts']) for i,v in enumerate(data['variants'])])
