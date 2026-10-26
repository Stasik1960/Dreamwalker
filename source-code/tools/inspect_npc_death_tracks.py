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
