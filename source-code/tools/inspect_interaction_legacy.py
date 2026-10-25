import json
from pathlib import Path
import world_io as w
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');plan=json.loads((D/'window-map-plan.json').read_text(encoding='utf-8'));regions={};chunks={};out=[]
for row in plan['directUsedLegacyBlocks']:
 x,y,z=row['pos'];key=x//16,z//16
 if key not in chunks:
  rk=x//512,z//512
  if rk not in regions:regions[rk]=w.RegionFile((W/f'region/r.{rk[0]}.{rk[1]}.mca').read_bytes())
  chunks[key]=regions[rk].get_chunk(key[0]%32,key[1]%32).nbt().root.value
 root=chunks[key];be=next((n.value for n in root.get('block_entities',w.Tag(9,[],10)).value if [n.value[k].value for k in ('x','y','z')]==row['pos']),None)
 out.append({'pos':row['pos'],'hasBlockEntity':be is not None,'hasResident':be is not None and 'resident' in be,'keys':sorted(be) if be else [],'payloadKeys':sorted(be.get('payload',w.Tag(10,{})).value) if be else []})
(D/'legacy-window-data.json').write_text(json.dumps({'count':len(out),'withBE':sum(r['hasBlockEntity'] for r in out),'withResident':sum(r['hasResident'] for r in out),'windows':out},ensure_ascii=False,indent=2),encoding='utf-8')
