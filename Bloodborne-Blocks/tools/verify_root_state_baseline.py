"""Prove canonical root states stay exact, allowing only named additions."""
import copy
import json
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASELINE='419b85eeab56180f0e26272ffc2a2136f6a05a18'
FAMILIES={'o_bench','o_high_balustrade'}
ALLOW_FAMILY='o_c001'
ADDED_FAMILY='o_dry_bush'

def collapse(states):
    return {','.join(p for p in key.split(',') if not p.startswith('root_anchor=')):value
            for key,value in states.items() if 'root_anchor=upper' not in key}

def verify():
    from apply_window_test3_patch import WINDOW, patch_family
    from sync_reviewed_geometry import profile
    baseline_contracts=json.loads(subprocess.run(['git','show',BASELINE+':Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/contracts-v2.json'],cwd=ROOT.parent,check=True,capture_output=True).stdout)
    mounted_window=copy.deepcopy(next(f for f in baseline_contracts['families'] if f['id']==WINDOW))
    patch_family(mounted_window)
    paths=['bloodborne_blocks/logical/'+name for name in ('definitions.json','contracts-v2.json','geometry.json')]
    paths += [f'assets/bloodborne_blocks/blockstates/{family}.json' for family in sorted(FAMILIES)]
    checked=[]
    for suffix in paths:
        relative='Bloodborne-Blocks/src/main/resources/'+suffix
        original=json.loads(subprocess.run(['git','show',BASELINE+':'+relative],cwd=ROOT.parent,
                                          check=True,capture_output=True).stdout)
        current=json.loads((ROOT/'src/main/resources'/suffix).read_bytes())
        if suffix.endswith('/definitions.json'):
            original_tree=next(block for block in original['blocks'] if block['id']==ALLOW_FAMILY)
            current['blocks']=[block for block in current['blocks'] if block['id']!=ADDED_FAMILY]
            for block in current['blocks']:
                if block['id']==ALLOW_FAMILY:
                    placement=block.get('placement_properties')
                    assert placement is not None and placement.pop('variant')==original_tree['default']['variant']
                    if not placement:block.pop('placement_properties')
                if block['id'] not in FAMILIES:continue
                assert block['properties'].pop('root_anchor')==['canonical','upper']
                assert block['default'].pop('root_anchor')=='canonical'
                for field in ('states','models','visual_models'):
                    if block.get(field) is not None:
                        block[field]=collapse(block[field])
        elif suffix.endswith('/contracts-v2.json'):
            original['families']=[mounted_window if f['id']==WINDOW else f for f in original['families']]
            current['families']=[family for family in current['families'] if family['id']!=ADDED_FAMILY]
            for family in current['families']:
                if family['id'] in FAMILIES:family['states']=collapse(family['states'])
        elif suffix.endswith('/geometry.json'):
            original['blocks'][WINDOW]=profile(mounted_window)
            current['blocks'].pop(ADDED_FAMILY,None)
            for family in FAMILIES:current['blocks'][family]['states']=collapse(current['blocks'][family]['states'])
        else:
            current['variants']=collapse(current['variants'])
        if current!=original:raise AssertionError('CANONICAL_BASELINE_CHANGED: '+suffix)
        checked.append(suffix)
    return {'result':'PASS','baseline':BASELINE,'files':checked,'canonical_root_states_changed':0,
            'additional_root_families':sorted(FAMILIES),'separate_mount_delta':WINDOW}

if __name__=='__main__':print(json.dumps(verify()))
