import json
from pathlib import Path
import world_io as w
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';W=Path('C:/Users/vakir/Desktop/bloodborne');p=D/'window-map-plan.json';plan=json.loads(p.read_text(encoding='utf-8'));regions={};chunks={};actual=[]
for r in plan['directLegacyPaletteReferences']:
 x,z=r['chunk'];key=(x,z)
 if key not in chunks:
  regionkey=x//32,z//32
  if regionkey not in regions:regions[regionkey]=w.RegionFile((W/f'region/r.{regionkey[0]}.{regionkey[1]}.mca').read_bytes())
  chunks[key]=regions[regionkey].get_chunk(x%32,z%32).nbt().root.value
 sec=next(s for s in chunks[key]['sections'].value if s.value['Y'].value==r['section']);pal,indices=w.section_blocks(sec)
 for i,n in enumerate(indices):
  if n!=r['palette']:continue
  row=pal[n].value;actual.append({'pos':[x*16+(i&15),r['section']*16+(i>>8),z*16+((i>>4)&15)],'registryId':row['Name'].value,'properties':{k:v.value for k,v in row.get('Properties',w.Tag(10,{})).value.items()}})
plan['directUsedLegacyBlocks']=actual;plan['counts']['directUsedLegacyBlocks']=len(actual);p.write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
