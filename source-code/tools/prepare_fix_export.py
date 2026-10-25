import hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[1];A=P/'src/architecture/resources';D=P/'fix-evidence/export';D.mkdir(parents=True,exist_ok=True)
cat=json.loads((A/'assets/bloodborne_dw/native_grid/catalog.json').read_text(encoding='utf-8'));states={};cells=[]
confirmed={'bloodborne_dw:source_cobblestone','bloodborne_dw:restored_slab_9c3f5c1f30a2b9bb'}
for i,c in enumerate(cat['cells']):
 state=c.get('baseState')
 if state and state.get('registryId') in confirmed:
  key=json.dumps(state,sort_keys=True,separators=(',',':'));states[key]=state;cells.append(i)
legacy=json.loads((A/'bloodborne_dw/static-architecture-v1274.json').read_text(encoding='utf-8'));poses=[p for p in legacy['poses'] if p['key'] in ('pfee03837eabca021f77c','pc64bf6c73e536febb2c7')]
request={'poses':poses,'nativeStates':list(states.values())};(D/'request.json').write_text(json.dumps(request,ensure_ascii=False,indent=2),encoding='utf-8')
(D/'candidate-cells.json').write_text(json.dumps({'selection':'All catalog occurrences of the two confirmed source families, no placement coordinates or physical-state replacement','families':sorted(confirmed),'cells':cells,'nativeStates':len(states),'catalogSha256':hashlib.sha256((A/'assets/bloodborne_dw/native_grid/catalog.json').read_bytes()).hexdigest()},indent=2),encoding='utf-8')
print('Native states',len(states),'candidate cells',len(cells))
