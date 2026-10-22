from common import *
from collections import Counter
sys.path.insert(0,str(PROJECT/'tools'))
from world_io import RegionFile,compound,section_blocks,block_state_key,read_nbt,_plain

def main():
 log_to('audit')
 catalog=read(RES/'bloodborne_dw/debug_catalogue.json')
 names={r['registryId']:r for r in catalog['entries']}
 targets=[];floors=[]
 for path in (RES/'bloodborne_dw/composite').glob('source_*.json'):
  d=read(path);name=d.get('displayName','')
  floor=any(s in name.lower() for s in ['настил','дощатая плитка','дощатая напольная','разбитые деревянные доски'])
  if floor:floors.append(d['id'])
  for i,v in enumerate(d['variants']):
   b,top=bounds(v['poses']['closed']['parts'])
   if b is None or top is None:continue
   size=[b[1][j]-b[0][j] for j in range(3)]
   flat='slab' in path.stem and 'stair' not in path.stem and size[1]<1.1 and size[0]>=15.9 and size[2]>=15.9
   half=all(abs(b[0][j])<.1 and abs(b[1][j]-16)<.1 for j in [0,2]) and abs(size[1]-8)<.1 and (abs(b[0][1])<.1 or abs(b[0][1]-8)<.1)
   if flat or half:
    targets.append({'registryId':d['id'],'variant':i,'name':name,'temporaryId':names.get(d['id'],{}).get('temporaryId'),'kind':'plane' if flat else 'half','bounds':b,'top':top,'sourceStateRefs':v.get('sourceStateRefs',[])})
 write(OUT/'materials.json',{'slabs':targets,'woodenFloors':floors})
 print('SLAB_VARIANTS',len(targets),'FLOORS',floors)
 for t in targets:print(t['registryId'],t['variant'],t['kind'],t['bounds'],t['name'])
 static=read(RES/'bloodborne_dw/static-architecture-v1274.json');poses={p['key']:p for p in static['poses']}
 target_keys={(t['registryId'],t['variant']) for t in targets}
 affected={p['key'] for p in poses.values() if (p['registryId'],int(p['properties'].get('variant','0'))+16*int(p['properties'].get('art_bank','0'))) in target_keys}
 print('STATIC TARGET POSES',len(affected))
 pal=Counter();counts=Counter();source=Counter();components=set();floor_components=set();examples=[];chunkcount=0
 for path in sorted(SOURCE_WORLD.rglob('*.mca')):
  if path.parent.name!='region':continue
  ordinary(path);region=RegionFile(path.read_bytes())
  for c in region.chunks():
   root=compound(c.nbt().root);root=root.get('Level',None).value if 'Level' in root else root;chunkcount+=1
   cx,cz=root['xPos'].value,root['zPos'].value;dim=str(path.parent.parent.relative_to(SOURCE_WORLD))
   for sec in root.get('sections',root.get('Sections',[])).value:
    bs=section_blocks(sec)
    if not bs:continue
    palette,indices=bs;hist=Counter(indices);sy=compound(sec)['Y'].value
    for pi,num in hist.items():
     state=compound(palette[pi]);id=state['Name'].value;props={k:v.value for k,v in state.get('Properties',type('T',(),{'value':{}})()).value.items()};pal[id]+=num
     if 'invisible' in id or 'fallback' in id:counts[block_state_key(palette[pi])]+=num
     if id.startswith('bloodborne_dw:static_architecture_'):
      ri=int(id.rsplit('_',1)[1])*128+int(props.get('part',0));recipe=static['recipes'][ri]
      chosen=[f for f in recipe['fragments'] if f['pose'] in affected or poses[f['pose']]['registryId'] in floors]
      if chosen:
       for at,j in enumerate(indices):
        if j!=pi:continue
        pos=(cx*16+(at&15),sy*16+(at>>8),cz*16+((at>>4)&15))
        for f in chosen:
         origin=f['origin'];rootpos=tuple(pos[k]+origin[k] for k in range(3));entry=(dim,*rootpos,f['pose'])
         (components if f['pose'] in affected else floor_components).add(entry)
     elif (id,int(props.get('variant','0'))+16*int(props.get('art_bank','0'))) in target_keys:source[block_state_key(palette[pi])]+=num
  print('REGION',path.relative_to(SOURCE_WORLD),flush=True)
 write(OUT/'world-audit.json',{'chunks':chunkcount,'invisibleAndFallbackStates':dict(counts),'targetSourceBlocks':dict(source),'slabComponents':sorted(components),'floorComponents':sorted(floor_components),'blockCounts':dict(pal)})
 print('WORLD',chunkcount,'SLABS',len(components),'FLOORS',len(floor_components),'SOURCE',dict(source),'INVISIBLE',sum(n for k,n in counts.items() if 'invisible' in k))
 ledger=[]
 for path in SOURCE_WORLD.rglob('bloodborne_dw_composite_owners.dat'):
  d=_plain(read_nbt(path).root);write(OUT/('ledger-'+str(len(ledger))+'.json'),d);ledger.append(str(path.relative_to(SOURCE_WORLD)))
 print('LEDGERS',ledger)
 print('PASS')
if __name__=='__main__':main()
