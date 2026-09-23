"""Project authored contract primitives into legacy per-cell storage, not mesh elements."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
LOGICAL=ROOT/'src/main/resources/bloodborne_blocks/logical'


def profile(family):
    result={}
    for key,state in family['states'].items():
        cells={}
        for cell in state['interaction_footprint']['cells']:
            data={}
            for kind,source in (('outline','selection_footprint'),('collision','collision_footprint')):
                boxes=[]
                for box in state[source]['boxes']:
                    clipped=[max(0,box[i]-cell[i]) for i in range(3)]+[min(1,box[i+3]-cell[i]) for i in range(3)]
                    if all(clipped[i]<clipped[i+3] for i in range(3)):boxes.append(clipped)
                data[kind]=boxes
            cells[','.join(map(str,cell))]=data
        result[key]={'cells':cells,'anchor':family['canonical_anchor']['cell'],
                     'render_offset':state['render_mesh']['offset'],
                     'globalOutline':state['selection_footprint']['boxes'][0]}
    return {'states':result}


def build():
    path=LOGICAL/'geometry.json';data=json.loads(path.read_text())
    for family in json.loads((LOGICAL/'contracts-v2.json').read_text())['families']:
        if family.get('authority')=='user':data['blocks'][family['id']]=profile(family)
    path.write_text(json.dumps(data,separators=(',',':'),sort_keys=True)+'\n',encoding='utf8')


if __name__=='__main__':build()
