"""Add a saved upper-root state ONLY for the two approved exception families.

Does not change mesh payloads, default placement, or infer map relocations.
Authored contracts remain in their canonical frame; runtime/converter apply
the explicit technical translation after authored validation.
"""
import copy
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
LOG=RES/'bloodborne_blocks/logical'
FAMILIES={'o_bench','o_high_balustrade'}

def state_key(key,mount):
    props=dict(p.split('=',1) for p in key.split(',') if p)
    props['root_anchor']=mount
    return ','.join(k+'='+v for k,v in sorted(props.items()))

def expand(values, transform=None):
    result={}
    for key,value in values.items():
        if 'root_anchor=upper' in key:continue
        for mount in ('canonical','upper'):
            cloned=copy.deepcopy(value)
            if transform:transform(cloned,mount)
            result[state_key(key,mount)]=cloned
    return result

def write(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')

def main():
    definitions=json.loads((LOG/'definitions.json').read_bytes())
    for d in definitions['blocks']:
        if d['id'] not in FAMILIES:continue
        d['properties']['root_anchor']=['canonical','upper']
        d['default']['root_anchor']='canonical'
        for field in ('states','models','visual_models'):
            if d.get(field) is not None:d[field]=expand(d[field])
    write(LOG/'definitions.json',definitions)
    contracts=json.loads((LOG/'contracts-v2.json').read_bytes())
    def contract(state,mount):
        state.pop('technical_root_offset',None)
        if mount=='upper':
            state['technical_root_offset']=[0,1,0]
            state['migration_source_pattern']=[]
    for f in contracts['families']:
        if f['id'] in FAMILIES:f['states']=expand(f['states'],contract)
    write(LOG/'contracts-v2.json',contracts)
    geometry=json.loads((LOG/'geometry.json').read_bytes())
    physical=json.loads((LOG/'physical-footprints.json').read_bytes())
    for ident in FAMILIES:
        geometry['blocks'][ident]['states']=expand(geometry['blocks'][ident]['states'])
        physical['families'][ident]=expand(physical['families'][ident])
        path=RES/f'assets/bloodborne_blocks/blockstates/{ident}.json'
        models=json.loads(path.read_bytes());models['variants']=expand(models['variants']);write(path,models)
    write(LOG/'geometry.json',geometry);write(LOG/'physical-footprints.json',physical)

if __name__=='__main__':main()
