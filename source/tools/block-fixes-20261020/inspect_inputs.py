import json, sys, zipfile
from pathlib import Path
from collections import Counter

PROJECT=Path(__file__).resolve().parents[2]
ROOT=PROJECT.parent
RES=PROJECT/'src/architecture/resources'
OUT=ROOT/'research/block-fixes-20261020'
OUT.mkdir(parents=True,exist_ok=True)
sys.stdout=(OUT/'inspection.txt').open('w',encoding='utf8',buffering=1)
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
catalog=read(RES/'bloodborne_dw/debug_catalogue.json')
targets=[r for r in catalog['entries'] if str(r['temporaryId']) in ['90190','90115','90566']]
static=read(RES/'bloodborne_dw/static-architecture-v1274.json')
print('STATIC',static.keys(),len(static['poses']),len(static['recipes']))
for r in targets:
 print('TARGET',json.dumps(r,ensure_ascii=False))
 d=read(RES/'bloodborne_dw/composite'/(r['registryId'].split(':')[1]+'.json'))
 print('DESCRIPTOR',json.dumps(d,ensure_ascii=False)[:14000])
for bank in [27,145]:
 rows=[dict(index=i,**r) for i,r in enumerate(static['recipes']) if i//128==bank]
 (OUT/f'bank-{bank}.json').write_text(json.dumps(rows,indent=2),encoding='utf8')
 counts=Counter(f['pose'] for r in rows for f in r['fragments'])
 print('BANK',bank,'POSES',json.dumps([p for p in static['poses'] if p['key'] in counts],ensure_ascii=False))
 print('SAMPLES',json.dumps(rows[:2],ensure_ascii=False))
names=[]
for p in (RES/'bloodborne_dw/composite').glob('*.json'):
 d=read(p)
 if any(word in (p.stem+' '+d.get('displayName','')).lower() for word in ['slab','плит','дощ','wood','plank','пол ']):
  names.append({'id':d['id'],'name':d.get('displayName'),'variants':len(d['variants']),'parts':d['variants'][0]['poses']['closed']['parts'],'bounds':d['variants'][0].get('visibleBounds')})
(OUT/'candidate-materials.json').write_text(json.dumps(names,ensure_ascii=False,indent=2),encoding='utf8')
print('CANDIDATE MATERIALS',len(names))
for x in names:print(json.dumps(x,ensure_ascii=False))
print('WORLD', ROOT/'deliverables/collision-streaming-20261018/map/bloodborne')
for p in ROOT.glob('**/prepared.json'):
 if 'graphical-streaming-20261019/normal-save' in p.as_posix():
  d=read(p);print('PREPARED',p,{k:v for k,v in d.items() if k in ['world','sourceWorld','java','serverArgs','clientArgs']})
