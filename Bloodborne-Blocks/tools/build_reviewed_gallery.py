"""Create a disposable, playable manual-placement gallery. Never reads a city."""
import argparse
import json
from pathlib import Path
from reviewed_migration_fixture import write_fixture
from logical_contract_v2 import load_contracts
from convert_logical_world import block_pos_long, PART
from world_io import Tag, TAG_COMPOUND, TAG_STRING, TAG_INT, TAG_LONG

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/bloodborne_blocks/logical'
OUTPUT=ROOT/'build/reviewed-batch-02-gallery-ready'


def build(output=OUTPUT):
    if output.exists():raise FileExistsError('Use a fresh gallery output; never overwrite an opened world: '+str(output))
    contracts,_=load_contracts(RES)
    definitions={d['id']:d for d in json.loads((RES/'definitions.json').read_text())['blocks']}
    cells={};entities=[];positions=[]
    for number,family in enumerate(contracts['families']):
        for turn,facing in enumerate(('north','east','south','west')):
            props={**definitions[family['id']]['default'],'facing':facing}
            key=','.join(f'{k}={v}' for k,v in sorted(props.items()))
            shape=family['states'][key]['interaction_footprint']['cells']
            # Every specimen rests on the same Y=63 floor, including POC
            # contracts whose explicitly authored interaction extends below master.
            origin=(16+(number%5)*128+turn*28,64-min(c[1] for c in shape),16+(number//5)*40)
            owner='bloodborne_blocks:'+family['id'];cells[origin]=(owner,props)
            for offset in shape:
                if offset==[0,0,0]:continue
                point=tuple(origin[i]+offset[i] for i in range(3))
                if point in cells:raise ValueError('Gallery footprint overlap')
                cells[point]=(PART,{})
                data={'id':Tag(TAG_STRING,PART),'Owner':Tag(TAG_STRING,owner),'Root':Tag(TAG_LONG,block_pos_long(*origin))}
                data.update({k:Tag(TAG_INT,v) for k,v in zip(('x','y','z'),point)})
                entities.append(Tag(TAG_COMPOUND,data))
            positions.append({'id':family['id'],'facing':facing,'position':origin,'give':'/give @s '+owner})
    # Extend the shared platform underneath the spawn chosen by write_fixture.
    spawn_anchor=positions[0]['position']
    cells[(spawn_anchor[0],64,spawn_anchor[2]-6)]=('minecraft:air',{})
    write_fixture(output,cells,block_entities=entities)
    (output/'gallery-positions.json').write_text(json.dumps(positions,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (output/'README.txt').write_text('Synthetic creative world, 25 families x 4 facing states.\n'
        'Install the checkpoint Fabric 1.20.1 JAR. Copy this folder into saves.\n'
        'Use gallery-positions.json for /tp and /give; compare fresh manual placement with each specimen.\n'
        'No city data, original source world is never modified.\n',encoding='utf8')
    print(f'{output}: {len(positions)} specimens')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=OUTPUT)
    build(parser.parse_args().output)
