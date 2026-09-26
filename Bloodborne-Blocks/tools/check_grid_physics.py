"""Fail-closed physical mask and frozen production validator."""
from pathlib import Path
import subprocess,gzip
from production_fingerprints import read, verify
ROOT=Path(__file__).resolve().parents[1]

def validate_state(mask, cells):
    physical={tuple(c) for c in mask}
    if len(physical)!=len(mask) or (0,0,0) not in physical: raise ValueError('INVALID_PHYSICAL_FOOTPRINT')
    for text,cell in cells.items():
        point=tuple(map(int,text.split(',')))
        if point not in physical: raise ValueError('HELPER_OUTSIDE_PHYSICAL_FOOTPRINT')
        for b in cell['collision']:
            if len(b)!=6 or any(not 0<=n<=1 for n in b) or any(b[i]>=b[i+3] for i in range(3)):
                raise ValueError('COLLISION_OUTSIDE_LOCAL_CELL')
    if set(cells)!={','.join(map(str,c)) for c in physical}: raise ValueError('PHYSICAL_CELL_MISSING')

def validate(root=ROOT):
    verify(root=root,baseline_path=root/'docs/grid-physics/baseline-fingerprints.json.gz')
    res=root/'src/main/resources/bloodborne_blocks'; masks=read(res/'logical/physical-footprints.json')['families']
    contracts=read(res/'logical/contracts-v2.json')['families'];total=0
    for f in contracts:
        if set(masks[f['id']])!=set(f['states']):raise ValueError('PHYSICAL_STATE_MISSING')
        for key,s in f['states'].items():
            p=masks[f['id']][key]
            if p['cells']!=s['interaction_footprint']['cells']:raise ValueError('PROTECTED_FAMILY_CHANGED')
            validate_state(p['cells'],{k:{'collision':v} for k,v in p['collision_by_cell'].items()});total+=1
            from logical_contract_v2 import box_cells
            if any(not box_cells(b)<=set(map(tuple,p['cells'])) for b in s['collision_footprint']['boxes']):raise ValueError('COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT')
    changed=subprocess.check_output(['git','diff','--name-only','419b85eeab56180f0e26272ffc2a2136f6a05a18','--','Bloodborne-Blocks/src/main/resources'],cwd=root.parent,text=True).splitlines()
    allowed={'Bloodborne-Blocks/src/main/resources/bloodborne_blocks/city/geometry.json','Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/physical-footprints.json'}
    if set(changed)-allowed:raise ValueError('RENDER_OR_SOURCE_MAPPING_CHANGED_UNEXPECTEDLY: '+str(set(changed)-allowed))
    city=read(res/'city/geometry.json')
    old=read(root/'docs/grid-physics/legacy-city-geometry.json.gz')
    for key,p in city['profiles'].items():
        before=old['profiles'][key]
        if {k:v for k,v in p.items() if k not in ('cells','anchor','physical_footprint')}!={k:v for k,v in before.items() if k not in ('cells','anchor','physical_footprint')}:raise ValueError('RENDER_HASH_CHANGED_UNEXPECTEDLY')
    for p in city['profiles'].values():
        if p['physical_footprint']!=[[0,0,0]] or p['anchor']!=[0,0,0]:raise ValueError('CITY_NOT_CELL_LOCAL')
        validate_state(p['physical_footprint'],p['cells'])
    return {'result':'PASS','protected_states':total,'city_profiles':len(city['profiles'])}
if __name__=='__main__':print(validate())
