import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];rows=[json.loads(s) for s in (P/'fix-evidence/client-3/light-trace.jsonl').read_text().splitlines()]
selected=[]
for r in rows:
 if r['phase']!='AO_ON_6':continue
 xs=[p[0] for p in r['vertices']];ys=[p[1] for p in r['vertices']];zs=[p[2] for p in r['vertices']]
 if max(xs)<83.5 or min(xs)>86 or max(ys)<48 or min(ys)>51 or max(zs)<-1012.5 or min(zs)>-1009:continue
 selected.append(r)
(P/'fix-evidence/stairs-lighting-focus.json').write_text(json.dumps(selected,indent=2),encoding='utf-8')
for r in selected:print(r['root'],r['model'],r['face'],'B',r['brightness'],'L',r['lights'],'S',r['surfaceSky'],'V',r['vertices'])
print('quad count',len(selected));print('invalid packed lights',sum(any((v&65535)>240 or ((v>>16)&65535)>240 for v in r['lights']) for r in rows))
