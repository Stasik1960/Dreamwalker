"""Whole-world current registry and grid ownership census (all chunks)."""
import json,sys
from collections import Counter
from pathlib import Path
from world_io import RegionFile,compound,section_blocks,block_state_key,TAG_LIST,Tag
from convert_logical_world import list_region_files
ROOT=Path(__file__).resolve().parents[1]
def scan(world):
    res=ROOT/'src/main/resources/bloodborne_blocks';defs={}
    for layer in ('logical','city'):
        for d in json.loads((res/layer/'definitions.json').read_bytes())['blocks']:defs['bloodborne_blocks:'+d['id']]=d
    from logical_contract_v2 import direct_rules
    rules,_=direct_rules(res/'logical');raw_sources={p.state for r in rules for p in (r.source,*r.members)}
    unknown=Counter();invalid=Counter();modules=0;raw=Counter();chunks=0;cells=0;registered=Counter()
    for path in list_region_files(world):
        for c in RegionFile.open(path).chunks():
            chunks+=1
            for section in compound(c.nbt().root).get('sections',Tag(TAG_LIST,[])).value:
                result=section_blocks(section)
                if result is None:continue
                palette,indices=result;counts=Counter(indices);cells+=len(indices)
                for i,n in counts.items():
                    d=compound(palette[i]);name=d['Name'].value;props={k:v.value for k,v in compound(d['Properties']).items()} if 'Properties' in d else {}
                    state=(name,tuple(sorted(props.items())))
                    if state in raw_sources:raw[block_state_key(palette[i])]+=n
                    if name.startswith('bloodborne_blocks:m_'):modules+=n
                    if not name.startswith('bloodborne_blocks:') or name=='bloodborne_blocks:architecture_part':continue
                    if name not in defs:unknown[name]+=n;continue
                    key=','.join(k+'='+v for k,v in sorted({**defs[name].get('default',{}),**props}.items()))
                    if key not in defs[name]['states']:invalid[block_state_key(palette[i])]+=n
                    registered[name]+=n
    return {'chunks':chunks,'checked_cells':cells,'unknown_bloodborne_ids':dict(unknown),'old_module_cells':modules,'invalid_current_states':dict(invalid),'possible_raw_vanilla_source_cells':dict(raw),'current_bloodborne_cells':sum(registered.values()),'result':'PASS' if not (unknown or invalid or modules or raw) else 'FAIL'}
if __name__=='__main__':
    r=scan(Path(sys.argv[1]));Path(sys.argv[2]).write_text(json.dumps(r,indent=2)+'\n',encoding='utf8');print(json.dumps(r));sys.exit(r['result']!='PASS')
