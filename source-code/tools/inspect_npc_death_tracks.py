import json
from pathlib import Path
P=Path(__file__).resolve().parents[1];R=P/'src/rp/resources';cat=json.loads((R/'assets/bloodborne_rp/catalog.json').read_text());result={}
for name in ('scourge_beast','large_huntsman','huntsman_a'):
 spec=cat[name];value=spec['animation'];ns,rel=value.split(':',1) if ':' in value else ('bloodborne_rp',value);doc=json.loads((R/'assets'/ns/rel).read_text());result[name]={key:clip for key,clip in doc['animations'].items() if 'death' in key}
(P/'npc-evidence/death-tracks.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
summary={}
for name,clips in result.items():
 for key,clip in clips.items():
  summary[name]={b:{k:(list(v.items())[-1] if isinstance(v,dict) else v) for k,v in tracks.items() if k in ('position','scale')} for b,tracks in clip['bones'].items() if any(k in tracks for k in ('position','scale'))}
(P/'npc-evidence/death-position-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
geometry={}
for name in result:
 value=cat[name]['model'];ns,rel=value.split(':',1) if ':' in value else ('bloodborne_rp',value);doc=json.loads((R/'assets'/ns/rel).read_text());bones=doc['minecraft:geometry'][0]['bones'];geometry[name]=[{k:b[k] for k in ('name','parent','pivot') if k in b}|{'cubes':[c['size'] for c in b.get('cubes',[])]} for b in bones]
(P/'npc-evidence/death-model-geometry.json').write_text(json.dumps(geometry,indent=2),encoding='utf-8')
sample={}
for name in ('scourge_beast','large_huntsman'):
 value=cat[name]['model'];ns,rel=value.split(':',1) if ':' in value else ('bloodborne_rp',value);bones=json.loads((R/'assets'/ns/rel).read_text())['minecraft:geometry'][0]['bones'];sample[name]=[b for b in bones if b['name']=='body']
(P/'npc-evidence/death-body-cubes.json').write_text(json.dumps(sample,indent=2),encoding='utf-8')
