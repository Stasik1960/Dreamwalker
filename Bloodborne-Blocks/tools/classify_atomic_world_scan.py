"""Classify a new scan, and verify current ownership of ledger omission witnesses.

No writes, no replay, and no membership approval from a ledger alone.
"""
import gzip
import json
from collections import Counter, defaultdict
from pathlib import Path
from atomic_owner_groups import ROOT, state, compile_groups
from convert_logical_world import DEFAULT_RESOURCES, add, PART
from modded_world_adapter import compile_modded_rules
from check_composite_world import WorldReader
from source_mapping_archive import archive


def classify(scan_path,gate_path):
    scan=json.loads(scan_path.read_bytes())
    gate=json.loads(gate_path.read_bytes())
    rules,_,_=compile_modded_rules(DEFAULT_RESOURCES);rules,diagnostic=compile_groups(rules,DEFAULT_RESOURCES)
    by_id={r.transaction_id:r for r in rules if r.atomic_owner_group}
    accepted={r['transaction'] for r in scan['ledger'] if r.get('transaction') in by_id}
    rejected={r['rule']:r for r in scan['rejected']}
    groups=[]
    for g in diagnostic['groups']:
        r=by_id[g['transaction']]
        if r.transaction_id in accepted:continue
        dim,origin=r.allowed_origins[0]
        occupants=defaultdict(list)
        for o in r.outputs:
            root=add(origin,o.root_offset)
            for offset in o.shape:occupants[add(root,offset)].append({'root':root,'target':o.target[0],'isRoot':offset==(0,0,0)})
        shared=[{'position':p,'owners':v,'rootConflict':sum(o['isRoot'] for o in v)>1} for p,v in occupants.items() if len(v)>1]
        reason=rejected.get(r.number,{}).get('reason','not_instantiated')
        groups.append({'transaction':r.transaction_id,'origin':origin,'owners':g['owners'],
            'category':'shared-cell / root conflict' if shared or reason in {'target_would_overwrite_foreign_block','aggressive_blocker_has_owned_helpers','ambiguous_overlap_or_double_consumption'}
                       else 'exact owner closure; converter/runtime group support incomplete',
            'preflightReason':reason,'sharedCells':shared,'mappingErrors':g['errors'],'occurrences':g['occurrences']})
    # A ledger's protectedBlock string is insufficient. Check actual helper NBT,
    # root identity and the entire current owner's geometry membership instead.
    witnesses=json.loads(gzip.decompress((ROOT/'docs/composite-grid-repair/historical-omission-witnesses.json.gz').read_bytes()))
    frozen=archive(); definitions={b['id']:b for b in frozen['definitions.json']['blocks']}
    # Geometry is the immutable old runtime, not the source-world appearance.
    import zipfile
    from build_city_compat import JAR, JAR_SHA
    import hashlib
    if hashlib.sha256(JAR.read_bytes()).hexdigest()!=JAR_SHA:raise ValueError('OLD_RUNTIME_HASH_MISMATCH')
    with zipfile.ZipFile(JAR) as jar: geometry=json.loads(jar.read('bloodborne_blocks/geometry.json'))
    reader=WorldReader(ROOT/'reference-inputs/latest-modded-world.zip');omissions=[]
    try:
        for w in witnesses['approved']:
            point=tuple(w['position']);owner=reader.helper_owner('minecraft:overworld',point)
            reason=None;current_root=None
            if reader.state('minecraft:overworld',point)[1]!=w['preserveState']:
                reason='historical_context_changed';owner=None
            elif owner is None:reason='no_current_helper_owner'
            else:
                root,ident=owner;current_root=reader.state('minecraft:overworld',root)[1]
                name,props=state(current_root or '')
                definition=definitions.get(ident.split(':')[-1])
                if name!=ident or definition is None:reason='current_owner_root_not_proven'
                else:
                    values={**definition['default'],**dict(props)};key=','.join(k+'='+v for k,v in sorted(values.items()))
                    value=geometry['blocks'].get(definition['id'],{}).get('states',{}).get(key)
                    value=geometry.get('profiles',{}).get(value.get('ref'),value) if value else None
                    expected={add(root,tuple(map(int,k.split(',')))) for k in value['cells']} if value else set()
                    if point not in expected:reason='helper_outside_current_owner_geometry'
                    elif any(p!=root and (reader.state('minecraft:overworld',p)[1]!=PART or reader.helper_owner('minecraft:overworld',p)!=owner) for p in expected):reason='current_owner_closure_incomplete'
            omissions.append({'root':w['root'],'position':w['position'],'currentOwner':owner,
                'currentOwnerState':current_root,'currentOwnerProven':reason is None,'reason':reason})
    finally:reader.close()
    oracle=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
    by_occ={(r['rule'],tuple(r['origin'])):r for r in oracle['occurrences']}
    proved_roots={tuple(r['root']) for r in omissions if r['currentOwnerProven']}
    unresolved=[]
    for r in gate['unresolved_technical_membership']:
        row=by_occ[(r['rule'],tuple(r['origin']))]
        current=any(tuple(c['position']) in proved_roots for c in row['source_cells'])
        unresolved.append({**r,'category':'historical omission with proven current owner' if current else 'insufficient evidence',
                           'sourceRoots':[c['position'] for c in row['source_cells']]})
    result={'scope':'Post-integration scan; no gate bypass or ledger replay',
        'convertedAtomicGroups':len(accepted),'remainingAtomicGroups':len(groups),
        'atomicGroupCategories':dict(Counter(r['category'] for r in groups)),
        'membershipCategories':dict(Counter(r['category'] for r in unresolved)),
        'fragmentedProtectedObjects':gate['fragmented'],
        'omissionCurrentOwnerProof':dict(Counter('proven' if r['currentOwnerProven'] else r['reason'] for r in omissions)),
        'groups':groups,'unresolvedMemberships':unresolved,'omissions':omissions}
    destination=ROOT/'docs/composite-grid-repair/atomic-world-residuals.json.gz'
    destination.write_bytes(gzip.compress((json.dumps(result,separators=(',',':'))+'\n').encode(),mtime=0))
    print(json.dumps({k:v for k,v in result.items() if k not in {'groups','unresolvedMemberships','omissions'}},indent=2))


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('scan',type=Path);parser.add_argument('gate',type=Path)
    args=parser.parse_args();classify(args.scan,args.gate)
