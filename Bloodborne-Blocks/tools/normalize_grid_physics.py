"""Publish explicit grid masks without rewriting protected logical assets."""
import copy
from pathlib import Path
from production_fingerprints import read,write,verify,file_hash
ROOT=Path(__file__).resolve().parents[1]

def build(root=ROOT):
    res=root/'src/main/resources/bloodborne_blocks';out=root/'docs/grid-physics'
    verify(root=root,baseline_path=out/'baseline-fingerprints.json.gz')
    families={}
    for f in read(res/'logical/contracts-v2.json')['families']:
        states={}
        for key,s in f['states'].items():
            cells=copy.deepcopy(s['interaction_footprint']['cells']);collision={}
            for cell in cells:
                boxes=[]
                for b in s['collision_footprint']['boxes']:
                    c=[max(0,b[i]-cell[i]) for i in range(3)]+[min(1,b[i+3]-cell[i]) for i in range(3)]
                    if all(c[i]<c[i+3] for i in range(3)):boxes.append(c)
                collision[','.join(map(str,cell))]=boxes
            states[key]={'cells':cells,'collision_by_cell':collision}
        families[f['id']]=states
    write(res/'logical/physical-footprints.json',{'schemaVersion':1,'families':families})
    path=res/'city/geometry.json';data=read(path)
    changes=0
    for p in data['profiles'].values():
        before=copy.deepcopy(p)
        p['cells']={'0,0,0':copy.deepcopy(p['cells'].get('0,0,0',{'collision':[],'outline':[]}))}
        p['anchor']=[0,0,0];p['physical_footprint']=[[0,0,0]]
        changes+=before!=p
    write(path,data)
    return {'normalized_profiles':changes,'protected_families':len(families)}
if __name__=='__main__':print(build())
