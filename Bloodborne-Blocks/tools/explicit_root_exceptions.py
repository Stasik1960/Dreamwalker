"""Two user-approved coordinates, never a generic conflict-shift fallback."""
from dataclasses import replace

CASES={
    ('o_bench',(-374,73,-291)):{'diagonal':'false','facing':'south','visual':'base'},
    ('o_high_balustrade',(-560,98,-9)):{'east':'true','facing':'north','north':'false','south':'false','visual':'base','west':'true'},
}
PRESERVED_OTHER={
    'o_bench':('bloodborne_blocks:m_5455a35611c21c80',(('facing','south'),)),
    'o_high_balustrade':('bloodborne_blocks:m_4da10f4d6bb1f41b',(('facing','north'),)),
}

def apply(rules,resources):
    from logical_contract_v2 import load_contracts
    from convert_logical_world import Expected
    data,_=load_contracts(resources)
    families={f['id']:f for f in data['families']}
    result=list(rules);additional=[]
    for index,rule in enumerate(rules):
        for (family,canonical_root),props in CASES.items():
            if rule.target[0]!='bloodborne_blocks:'+family:continue
            if dict(rule.target[1])!={**props,'root_anchor':'canonical'}:continue
            if rule.outputs:raise ValueError('ROOT_EXCEPTION_CANNOT_REWRITE_SPLIT_TRANSACTION')
            origin=tuple(canonical_root[i]-rule.root_offset[i] for i in range(3))
            new_offset=(rule.root_offset[0],rule.root_offset[1]+1,rule.root_offset[2])
            source_offsets={p.offset for p in (rule.source,)+rule.members}
            if new_offset not in source_offsets or rule.root_offset in source_offsets:continue
            guard=(('minecraft:overworld',origin),)
            target_props={**props,'root_anchor':'upper'}
            key=','.join(k+'='+v for k,v in sorted(target_props.items()))
            state=families[family]['states'][key]
            if state.get('technical_root_offset')!=[0,1,0]:raise ValueError('ROOT_EXCEPTION_CONTRACT_MISMATCH')
            shape=frozenset(tuple(c) for c in state['physical_footprint']['cells'])
            # New root must already be in this assembly's old physical mask.
            if (0,1,0) not in rule.shape:raise ValueError('ROOT_EXCEPTION_EXPANDS_PHYSICAL_MASK')
            if not {(x,y+1,z) for x,y,z in shape} <= set(rule.shape):raise ValueError('ROOT_EXCEPTION_EXPANDS_PHYSICAL_MASK')
            result[index]=replace(rule,excluded_origins=guard)
            additional.append(replace(rule,number=len(rules)+len(additional),
                target=(rule.target[0],tuple(sorted(target_props.items()))),
                root_offset=new_offset,
                shape=shape,allowed_origins=guard,
                required_context=(Expected(rule.root_offset,PRESERVED_OTHER[family]),)))
    return result+additional
