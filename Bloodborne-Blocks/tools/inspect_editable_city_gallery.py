"""Read the entire editing volume, including newly built adjacent blocks."""
import argparse,json
from collections import defaultdict
from pathlib import Path
import numpy as np
from upgrade_gallery_city import StreamingWorld
from convert_logical_world import PART,state_of
from world_io import Tag,TAG_LIST,TAG_COMPOUND,compound,section_blocks

def inspect(world_path,manifest_path):
    manifest=json.loads(Path(manifest_path).read_bytes())
    if manifest.get('schemaVersion')!=2:raise ValueError('unsupported editable gallery baseline')
    world=StreamingWorld(Path(world_path),{},cache_limit=32);dimension=manifest['dimension'];rows=manifest['specimens'];index=defaultdict(list);actual=defaultdict(dict)
    constants=manifest.get('registryConstants',{})
    def normalize(state):return state[0],tuple(sorted({**dict(state[1]),**constants.get(state[0],{})}.items()))
    for i,row in enumerate(rows):
        x0,y0,z0,x1,y1,z1=row['inspectionBounds']
        for cx in range(x0//16,x1//16+1):
            for cz in range(z0//16,z1//16+1):index[(dimension,cx,cz)].append(i)
    for ck,candidates in index.items():
        if ck not in world.chunks:continue
        chunk=world.chunks[ck];miny=min(rows[i]['inspectionBounds'][1]for i in candidates)
        for sec in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            sy=int(compound(sec)['Y'].value)*16
            if sy+15<miny or not compound(sec).get('block_states'):continue
            pal,indices=section_blocks(sec);states=[normalize(state_of(t,{}))for t in pal]
            selected=[i for i,s in enumerate(states)if s[0]not in {PART,'minecraft:air','minecraft:cave_air','minecraft:void_air'}]
            if not selected:continue
            array=np.asarray(indices);points=np.flatnonzero(np.isin(array,selected))
            for raw in points:
                n=int(raw);p=(chunk.x*16+(n&15),sy+(n>>8),chunk.z*16+((n>>4)&15))
                for i in candidates:
                    r=rows[i];b=r['inspectionBounds']
                    if b[0]<=p[0]<=b[3]and b[1]<=p[1]<=b[4]and b[2]<=p[2]<=b[5]and list(p)!=r['marker']:
                        actual[i][p]=states[int(array[n])];break
    result=[]
    for i,row in enumerate(rows):
        expected={tuple(c['absolute']):normalize((c['state'],tuple(sorted(c.get('properties',{}).items()))))for c in row['expectedCells']if c['state']!=PART}
        got=actual[i]
        kind='unchanged'if got==expected else'deleted'if not got else'replaced'if len(got)==1 else'multi-block-construction'
        if not row['editable']:kind='service-unchanged'if got==expected else'service-edited'
        cells=[{'position':list(p),'id':s[0],'properties':dict(s[1])}for p,s in sorted(got.items())]
        result.append({'stableKey':row['stableKey'],'numericId':row['numericId'],'sourceBlockId':row['sourceBlockId'],
            'classification':kind,'actualConstruction':cells,'changedCells':[list(p)for p in sorted(set(expected)|set(got))if expected.get(p)!=got.get(p)]})
    return {'schemaVersion':2,'specimens':result}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--world',required=True);p.add_argument('--manifest',required=True);p.add_argument('--output',required=True);a=p.parse_args()
    Path(a.output).write_text(json.dumps(inspect(a.world,a.manifest),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
