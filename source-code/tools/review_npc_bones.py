import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence/client-5';result={}
for name in ('scourge_beast','large_huntsman'):
 rows=json.loads((D/f'bones-{name}.json').read_text(encoding='utf-8'));result[name]=[r for r in rows if r['name'] in ('body','body_root','torso','torso_root','neck','head','legs','scourgebeast','largehuntsman','uppertorso') or r['hidden'] or r['hideChildren'] or r['scale']=='0.0,0.0,0.0']
(D/'bone-review.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
