import collections, json, math, sys
from pathlib import Path
from world_io import *
P=Path(__file__).resolve().parents[1];W=Path('C:/Users/vakir/Desktop/bloodborne');D=P/'restoration-evidence';R=P/'src/architecture/resources'
catalog=json.loads((R/'assets/bloodborne_dw/native_grid/catalog.json').read_text(encoding='utf-8'))
def visual_at(root,pos):
    data=root.get('DreamwalkerVisuals')
    if data:
        for sec in data.value['Sections'].value:
            if sec.value['Y'].value==pos[1]//16:
                key=(pos[1]%16)*256+(pos[2]%16)*16+pos[0]%16
                for raw in sec.value['Entries'].value:
                    v=raw&0xffffffff
                    if v&4095==key:return v>>12
    return None
def read_cell(pos):
    x,y,z=pos;region=RegionFile((W/f'region/r.{x//512}.{z//512}.mca').read_bytes());c=region.get_chunk(x//16%32,z//16%32);root=c.nbt().root.value
    sec=next(s for s in root['sections'].value if s.value['Y'].value==y//16);pal,idx=section_blocks(sec)
    row={'pos':pos,'state':block_state_key(pal[idx[(y%16)*256+(z%16)*16+x%16]]),'visual':visual_at(root,pos)}
    if row['visual'] is not None:row['descriptor']=catalog['cells'][row['visual']]
    row['blockEntities']=[_plain(b) for b in root.get('block_entities',Tag(9,[],10)).value if [b.value[k].value for k in ('x','y','z')]==list(pos)]
    return row
# Bound inspection is separate from the all-map state/application inventory.
targets=[read_cell(p) for p in [(21,-21,-1015),(-25,-56,-920)]]
counts=collections.Counter();lamps=[];emptyRegions=[]
for path in sorted((W/'region').glob('r.*.mca')):
    reg=RegionFile(path.read_bytes());rx,rz=map(int,path.stem.split('.')[1:])
    for lz in range(32):
        for lx in range(32):
            chunk=reg.get_chunk(lx,lz)
            if chunk is None:continue
            root=chunk.nbt().root.value
            for sec in root.get('sections',Tag(9,[],10)).value:
                blocks=section_blocks(sec)
                if blocks is None:continue
                pal,indices=blocks;sy=sec.value['Y'].value
                for i,n in collections.Counter(indices).items():counts[block_state_key(pal[i])]+=n
                matches={i for i,p in enumerate(pal) if any(t in block_state_key(p) for t in ('orange_stained_glass_pane','cobblestone_wall','source_andesite_slab','source_deepslate_tile'))}
                if matches:
                    for local,i in enumerate(indices):
                        if i in matches:lamps.append({'pos':[(rx*32+lx)*16+local%16,sy*16+local//256,(rz*32+lz)*16+local//16%16],'state':block_state_key(pal[i])})
result={'targets':targets,'matchingSourceComponents':lamps,'sourceComponentCounts':dict(collections.Counter(r['state'] for r in lamps)),'allStateCounts':dict(counts)}
(D/'world-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'targetStates':[{k:v for k,v in r.items() if k!='blockEntities'} for r in targets],'sourceComponents':len(lamps),'uniqueStates':len(counts)},ensure_ascii=False)[:2200])
