"""Read-only grid baseline audit. Never derives semantic groups from meshes."""
import json
from pathlib import Path
from production_fingerprints import read, write, source_commit
ROOT=Path(__file__).resolve().parents[1]

def audit(root=ROOT):
    res=root/'src/main/resources/bloodborne_blocks'
    protected={v['id']:v for v in read(root/'docs/grid-physics/protected-families.json')['families']}
    rows=[]
    for f in read(res/'logical/contracts-v2.json')['families']:
        states=list(f['states'].values())
        counts=[len(s['interaction_footprint']['cells']) for s in states]
        rows.append({'id':f['id'],'classification':'PROTECTED' if f['id'] in protected else 'AMBIGUOUS',
            'state_count':len(states),'current_occupied_counts':sorted(set(counts)),
            'current_helper_counts':sorted(set(n-1 for n in counts)),
            'derived_physical_counts':sorted(set(counts)),
            'source_pattern_count':sum(len(s['migration_source_pattern']) for s in states),
            'source_occurrence_evidence':'Existing reviewed decisions take precedence; no new source-mask derivation for protected families.',
            'collision_cell_counts':sorted(set(sum(bool(c['collision']) for c in g['cells'].values()) for g in read(res/'logical/geometry.json')['blocks'][f['id']]['states'].values())),
            'reason':protected.get(f['id'],{}).get('evidence',['Incomplete provenance']),
            'proposed_change':'NONE: preserve authored footprint, expose explicitly at runtime.'})
    geom=read(res/'city/geometry.json')
    for d in read(res/'city/definitions.json')['blocks']:
        states=[geom.get('profiles',{}).get(v.get('ref'),v) for v in geom['blocks'][d['id']]['states'].values()]
        counts=sorted(set(len(s['cells']) for s in states))
        rows.append({'id':d['id'],'classification':'AUTO_SAFE','layer':'city_compatibility',
            'state_count':len(states),'current_occupied_counts':counts,'current_helper_counts':[max(0,n-1) for n in counts],
            'derived_physical_counts':[1],'source_pattern_count':None,
            'source_occurrence_evidence':'Exact frozen city/migration.json and MODDED inventory; user mandated one compatibility cell = one physical cell.',
            'collision_cell_counts':sorted(set(sum(bool(c['collision']) for c in s['cells'].values()) for s in states)),
            'reason':'Explicit compatibility rule, no semantic inference.',
            'proposed_change':'Keep origin collision only; no helpers; anchor at origin. Preserve render offset, meshes, source mapping.'})
    return {'base_head':source_commit(root),'families':rows,'logical_unprotected_count':sum(r['classification']!='PROTECTED' for r in rows if r.get('layer')!='city_compatibility'),
            'status':'BASELINE_AUDIT_ONLY_NOT_RELEASED'}

if __name__=='__main__':
    result=audit();out=ROOT/'docs/grid-physics';write(out/'audit.json',result)
    lines=['# Source-grid baseline audit','', '49 logical families are protected by existing manual/authored decisions. No automatic source-mask reinterpretation applies to them.', '', 'Compatibility families follow the explicit one-cell rule. This baseline is not a completed runtime or world conversion.', '', '| ID | Class | States | Existing cells | Physical cells |','|---|---|---:|---|---|']
    lines += [f"| {r['id']} | {r['classification']} | {r['state_count']} | {r['current_occupied_counts']} | {r['derived_physical_counts']} |" for r in result['families']]
    (out/'audit.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
    print('Audited',len(result['families']),'families')
