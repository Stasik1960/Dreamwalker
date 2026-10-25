import json,subprocess,collections
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'bench-evidence';D.mkdir(exist_ok=True);W=Path('C:/Users/vakir/.codex/worktrees/3f07/DW')
def git(*args):return subprocess.run(['git',*args],cwd=W,capture_output=True,check=True).stdout
commit=git('rev-parse','HEAD').decode().strip();files=git('grep','-l','seat_anchors','HEAD','--','Bloodborne-Blocks/src/main/resources').decode().splitlines();old=[]
for name in files:
 path=name.split(':',1)[1];doc=json.loads(git('show','HEAD:'+path))
 def scan(v):
  if isinstance(v,dict):
   if 'seat_anchors' in v:old.append({k:v[k] for k in ('id','behavior','semantic','seat_anchors','default') if k in v})
   else:
    for value in v.values():scan(value)
  elif isinstance(v,list):
   for value in v:scan(value)
 scan(doc)
for name in ('ArchitectureSeatEntity.java','FunctionalFurniture.java'):
 raw=git('show','HEAD:Bloodborne-Blocks/src/main/java/dev/dreamwalker/bloodborneblocks/'+name);(D/('legacy-'+name)).write_bytes(raw)
specs=[]
for p in (P/'src/architecture/resources/bloodborne_dw/composite').glob('*bench*.json'):
 d=json.loads(p.read_text());models=[]
 for i,v in enumerate(d['variants']):
  pose=v['poses']['closed'];models.append({'variant':i,'parts':pose['parts'],'collision':pose.get('collision',[])})
 specs.append({'id':d['id'],'name':d['displayName'],'variants':models})
cat=json.loads((P/'src/architecture/resources/assets/bloodborne_dw/native_grid/catalog.json').read_text());matches=[(i,c) for i,c in enumerate(cat['cells']) if 'bench' in json.dumps(c).lower()]
whole=[v for v in cat.get('wholeObjects',[]) if 'bench' in json.dumps(v).lower()]
legacy=json.loads((P/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json').read_text());summary={'oldCommit':commit,'oldDefinitions':old,'currentSpecs':specs,'catalogKeys':list(cat),'cellSample':cat['cells'][0],'nativeMatches':matches,'nativeWhole':whole,'legacyKeys':list(legacy)}
(D/'history-and-layout.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8');print('old definitions',old);print('current spec count',len(specs),'native cells',len(matches),'native whole',len(whole));print('catalog keys',list(cat),'legacy keys',list(legacy));print('first native matches',str(matches[:2])[:1600]);print('legacy structure sample',str(legacy)[:700])
benchposes=[r for r in legacy['poses'] if 'bench' in r['registryId']];print('legacy bench poses',len(benchposes),collections.Counter(r['registryId'] for r in benchposes));print('whole samples',str(cat['wholeObjects'][:1])[:800]);print('cell sample',cat['cells'][0]);print('native bench names',sum('скам' in json.dumps(c,ensure_ascii=False).lower() for c in cat['cells']))
for spec in specs:
 print('SPEC',spec['id'],spec['name'],'variants',len(spec['variants']))
 for v in spec['variants'][:1]:
  for part in v['parts']:
   namespace,path=part['model'].split(':');model=json.loads((P/'src/architecture/resources/assets'/namespace/'models'/(path+'.json')).read_text());flat=[]
   for e in model.get('elements',[]):
    f,t=e['from'],e['to'];dx,dy,dz=[t[i]-f[i] for i in range(3)]
    if dy<=3 and dx*dz>100:flat.append({k:e[k] for k in ('from','to','rotation') if k in e})
   print('PART',part,'thin planks',flat[:6])
(D/'legacy-bench-poses.json').write_text(json.dumps(benchposes,ensure_ascii=False,indent=2),encoding='utf-8')
