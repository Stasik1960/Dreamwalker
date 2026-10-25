import json,sys,collections
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'bench-evidence';sys.path.insert(0,str(P/'tools'));from world_io import RegionFile,compound,section_blocks,_plain
R=P/'src/architecture/resources';legacy=json.loads((R/'bloodborne_dw/static-architecture-v1274.json').read_text());keys={v['key'] for v in legacy['poses'] if 'bench' in v['registryId']};print('recipe sample',str(legacy['recipes'][0])[:700]);types=set()
for index,recipe in enumerate(legacy['recipes']):
 for f in recipe.get('fragments',[]):
  if f.get('pose') in keys and f.get('origin')==[0,0,0]:types.add(index)
print('root recipe indexes',types)
W=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-25-visual-diagnostics/published-map/bloodborne-native-grid-city');out=[]
for path in (W/'region').glob('*.mca'):
 if path.stat().st_size<=8192:continue
 region=RegionFile(path.read_bytes());rx,rz=map(int,path.stem.split('.')[1:]);
 for cz in range(32):
  for cx in range(32):
   chunk=region.get_chunk(cx,cz)
   if chunk is None:continue
   root=compound(chunk.nbt().root)
   for sec in root.get('sections',type('T',(),{'value':[]})()).value:
    s=compound(sec);container=s.get('block_states')
    if container is None:continue
    palette=compound(container).get('palette')
    if palette is None:continue
    palette=palette.value;accepted={}
    for n,tag in enumerate(palette):
     state=_plain(tag);name=state['Name'];props=state.get('Properties',{})
     direct='bench' in name
     if name.startswith('bloodborne_dw:static_architecture_'):
      index=int(name.rsplit('_',1)[1])*128+int(props['part']);direct=index in types
     if direct:accepted[n]=state
    if not accepted:continue
    blocks=section_blocks(sec)
    if not blocks:continue
    indices=blocks[1]
    for i,n in enumerate(indices):
     if n in accepted:out.append({'pos':[(rx*32+cx)*16+(i&15),s['Y'].value*16+(i>>8),(rz*32+cz)*16+((i>>4)&15)],'state':accepted[n]})
 print('scanned',path.name,'roots',len(out),flush=True)
(D/'map-benches.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print('bench roots',len(out),'states',collections.Counter(str(v['state']) for v in out));print('examples',str(out[:5])[:1200])
cat=json.loads((R/'assets/bloodborne_dw/native_grid/catalog.json').read_text())
def decode(s):
 for _ in range(3):
  try:s=s.encode('cp1251').decode('utf-8')
  except (UnicodeEncodeError,UnicodeDecodeError):break
 return s
native=[v for v in cat['wholeObjects'] if any(k in decode(v['displayName']).lower() for k in ('скам','bench','ажур'))];(D/'native-bench-templates.json').write_text(json.dumps(native,ensure_ascii=False,indent=2),encoding='utf-8');print('native bench templates',len(native),[(v['key'],decode(v['displayName'])) for v in native])
for name in ('source_hold_bench','source_hold_bench_1','source_hold_bench_rotate'):
 data=json.loads((R/'bloodborne_dw/composite'/(name+'.json')).read_text());print(name,[(i,v['poses']['closed']['parts']) for i,v in enumerate(data['variants'])])
