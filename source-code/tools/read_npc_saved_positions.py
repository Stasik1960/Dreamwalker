import json,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];sys.path.insert(0,str(P/'tools'));from world_io import RegionFile,compound
rows=[]
for f in (P/'npc-profiles'/sys.argv[1]/'world/entities').glob('*.mca'):
 if f.stat().st_size<8192:continue
 r=RegionFile.open(f)
 for chunk in r.chunks():
  root=compound(chunk.nbt().root)
  for tag in root.get('Entities',[]).value:
   e=compound(tag);row={k:v.value for k,v in e.items() if k in ('id','Pos','Motion','OnGround','NoAI','bloodborne_rp_frozen','bloodborne_rp_corpse','bloodborne_rp_destination')};row={k:([x.value for x in v] if isinstance(v,list) else v) for k,v in row.items()};rows.append(row)
(P/'npc-evidence'/sys.argv[1]/'saved-positions.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
