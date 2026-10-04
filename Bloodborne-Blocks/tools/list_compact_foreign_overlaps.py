"""Read-only complete inventory of foreign cells inside compact owner footprints."""
from __future__ import annotations
import json
from pathlib import Path
import numpy as np
from convert_logical_world import NS, state_of
from upgrade_gallery_city import StreamingWorld
from world_io import TAG_COMPOUND, TAG_LIST, Tag, compound, section_blocks

ROOT=Path(__file__).resolve().parents[1]; AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air'}

def run(world_path=ROOT/'build/compact-city', city=ROOT/'src/main/resources/bloodborne_blocks/city', output=ROOT/'build/compact-foreign-overlap-cases.json'):
    raw=json.loads((city/'definitions.json').read_bytes());defs={NS+d['id']:d for d in raw['blocks'] if d.get('whole_owner')}
    geometry=json.loads((city/'geometry.json').read_bytes());cells={}
    for name,d in defs.items():
        cells[name]={key:geometry.get('profiles',{}).get(value.get('ref'),value).get('cells',[]) for key,value in geometry['blocks'][d['id']]['states'].items()}
    world=StreamingWorld(world_path,{},cache_limit=32);cases=[];seen=set();roots=0
    for chunk in world.chunks.values():
        for section in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            values=section_blocks(section)
            if not values:continue
            palette,indices=values;indices=np.asarray(indices);sy=int(compound(section)['Y'].value)*16
            for palette_index,entry in enumerate(palette):
                name=compound(entry)['Name'].value
                if name not in defs:continue
                state=name, state_of(entry,{name:defs[name].get('default',{})})[1]
                key=','.join(a+'='+b for a,b in state[1]);footprint=[tuple(map(int,row.split(','))) for row in cells[name].get(key,[])]
                if len(footprint)<=1:continue
                for index in np.flatnonzero(indices==palette_index):
                    index=int(index);root=(chunk.x*16+(index&15),sy+(index>>8),chunk.z*16+((index>>4)&15));roots+=1
                    for offset in footprint:
                        if offset==(0,0,0):continue
                        cell=tuple(root[i]+offset[i] for i in range(3));foreign=world.get(chunk.dimension,cell)
                        if not foreign or foreign[0].startswith(NS) or foreign[0] in AIR:continue
                        marker=(root,cell)
                        if marker in seen:continue
                        seen.add(marker);cases.append({'root':list(root),'id':name,'properties':dict(state[1]),'cell':list(cell),'offset':list(offset),'foreign':foreign[0],'foreignProperties':dict(foreign[1]),'rootCategory':'generated_owner' if name.startswith(NS+'owner_unified_') else 'retained_native_or_manual'})
    result={'schemaVersion':1,'readOnly':True,'multiCellRoots':roots,'foreignOverlapCount':len(cases),'cases':cases}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8');return result

if __name__=='__main__':
    result=run();print(json.dumps({'multiCellRoots':result['multiCellRoots'],'foreignOverlapCount':result['foreignOverlapCount']}))
