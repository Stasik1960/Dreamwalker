import json,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];O=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics');s=O/'inspect_map.py';ns={'__file__':str(s)};exec(s.read_text(encoding='utf-8').split('coords=')[0],ns)
rows=[ns['at'](x,y,z) for x in range(74,77) for y in range(26,30) for z in (-851,-850)]
stairs=[ns['at'](x,y,z) for x in range(82,87) for y in range(48,52) for z in range(-1013,-1008)]
(P/'fix-evidence/stairs-neighborhood.json').write_text(json.dumps(stairs,ensure_ascii=False,indent=2),encoding='utf-8')
for r in stairs:
 if r['state'] and r['state']['Name']!='minecraft:air':print('STAIR_CELL',r['pos'],r['state'],r['visual'])
(P/'fix-evidence/shadow-neighborhood.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
for r in rows:print(r['pos'],r['state'],r['visual'],r.get('SkyLight'))
data=[json.loads(s) for s in (P/'fix-evidence/client-2/light-trace.jsonl').read_text(encoding='utf-8').splitlines()];data=[r for r in data if r['phase']=='AO_ON_3' and abs(r['root'][0]-75)<=3]
for r in sorted(data,key=lambda r:min(r['brightness']))[:12]:print('AO_SAMPLE',r['root'],r['model'],r['brightness'],r['lights'],r['vertices'])
