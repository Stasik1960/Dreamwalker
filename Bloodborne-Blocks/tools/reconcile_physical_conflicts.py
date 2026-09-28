"""Bounded mask reductions justified by exact source-oracle intersections.

Never moves a model/root or flattens city geometry. Shared roots remain a hard
unresolved condition; only non-root reservations can be removed.
"""
import json
from collections import defaultdict
from pathlib import Path
from composite_world_oracle import ROOT

def rotate(p,turns):
    x,y,z=p
    return [(x,y,z),(-z,y,x),(-x,y,-z),(z,y,-x)][turns%4]

def clipped(box,c):
    b=[max(box[i],c[i]) for i in range(3)]+[min(box[i+3],c[i]+1) for i in range(3)]
    return b if all(b[i]<b[i+3] for i in range(3)) else None

def merge(boxes):
    boxes=[list(b) for b in sorted(set(tuple(b) for b in boxes))]
    changed=True
    while changed:
        changed=False
        for i,a in enumerate(boxes):
            for j in range(i+1,len(boxes)):
                b=boxes[j]
                axes=[k for k in range(3) if a[k]!=b[k] or a[k+3]!=b[k+3]]
                if len(axes)==1:
                    k=axes[0]
                    if max(a[k],b[k])<=min(a[k+3],b[k+3]):
                        a[k]=min(a[k],b[k]);a[k+3]=max(a[k+3],b[k+3]);boxes.pop(j);changed=True;break
            if changed:break
    return boxes

def plan(oracle,contracts,external_evidence=()):
    by_id={f['id']:f for f in contracts['families']};remove=defaultdict(set)
    def canonical_key(family,key):
        if family in {'o_bench','o_high_balustrade'} and any('root_anchor=' in k for k in by_id[family]['states']):
            return ','.join(sorted([p for p in key.split(',') if not p.startswith('root_anchor=')]+['root_anchor=canonical']))
        return key
    shared=[]
    for conflict in oracle['physical_conflicts']:
        roots=[o for o in conflict['owners'] if o['root']==conflict['position']]
        if len(roots)>1:shared.append(conflict)
        for owner in conflict['owners']:
            p=tuple(conflict['position'][i]-owner['root'][i] for i in range(3))
            if p==(0,0,0):continue
            family=owner['family'];key=canonical_key(family,owner['state'].partition('[')[2].rstrip(']'))
            remove[(family,key)].add(p)
    external=[]
    for evidence in external_evidence:
        # Only the separately proved panel beside this reviewed C618 fixture.
        # Never infer ownership merely because the existing ID is Bloodborne.
        if evidence['position']!=[-540,42,-33]:continue
        sources=evidence['matching_forward_sources']
        if len(sources)!=1 or sources[0]['mismatches'] or sources[0]['parts']!=1 or sources[0]['origin']!=evidence['position']:
            raise ValueError('UNPROVEN_EXTERNAL_PANEL')
        if sources[0]['source_state']!='minecraft:magenta_stained_glass_pane[east=false,north=false,south=false,waterlogged=false,west=true]':
            raise ValueError('UNEXPECTED_EXTERNAL_PANEL_SOURCE')
        outputs=[o for row in oracle['occurrences'] for o in row['outputs']
                 if o['family']=='o_c618' and o['canonical_root']==[-540,41,-33]]
        if len(outputs)!=1:raise ValueError('C618_FIXTURE_NOT_UNIQUE')
        output=outputs[0]
        state_key=output['expected_logical_state'].partition('[')[2].rstrip(']')
        remove[('o_c618',state_key)].add((0,1,0))
        external.append({'family':'o_c618','root':output['canonical_root'],'position':evidence['position'],
                         'preserved_source':sources[0]['source_state'],'basis':'Exact frozen forward mapping, all component states match MODDED input.'})
    # Keep cardinally related masks equivalent: one map occurrence must not
    # accidentally make the same object's east-facing item physically different.
    for (family,key),cells in list(remove.items()):
        props=dict(v.split('=',1) for v in key.split(',') if v)
        facing=props.get('facing')
        if facing not in ('north','east','south','west'):continue
        source_turn=('north','east','south','west').index(facing)
        for turn,target in enumerate(('north','east','south','west')):
            target_props={**props,'facing':target}
            target_key=','.join(k+'='+v for k,v in sorted(target_props.items()))
            if target_key in by_id[family]['states']:
                remove[(family,target_key)].update(rotate(c,turn-source_turn) for c in cells)
    # Appearance slots and lamp lighting must not restore a removed reservation.
    # Do not generalize this to open doors: those genuinely change geometry.
    for (family,key),cells in list(remove.items()):
        props=dict(v.split('=',1) for v in key.split(',') if v)
        for visual in ('base','alt') if 'visual' in props else (None,):
            for lit in ('false','true') if 'lit' in props else (None,):
                target_props=dict(props)
                if visual is not None:target_props['visual']=visual
                if lit is not None:target_props['lit']=lit
                target_key=','.join(k+'='+v for k,v in sorted(target_props.items()))
                if target_key in by_id[family]['states']:
                    original=by_id[family]['states'][key]
                    other=by_id[family]['states'][target_key]
                    if (original['interaction_footprint']!=other['interaction_footprint'] or
                            original['collision_footprint']!=other['collision_footprint']):
                        raise ValueError('APPEARANCE_TRANSITION_CHANGES_AUTHORED_PHYSICS')
                    remove[(family,target_key)].update(cells)
    masks={};changes=[]
    for family in contracts['families']:
        states={};masks[family['id']]=states
        for key,state in family['states'].items():
            before={tuple(c) for c in state['interaction_footprint']['cells']}
            removed=before&remove[(family['id'],canonical_key(family['id'],key))]
            after=before-removed
            if (0,0,0) not in after:raise ValueError('ROOT_REMOVAL_FORBIDDEN')
            boxes=state['collision_footprint']['boxes']
            if removed:
                boxes=merge([part for b in boxes for c in sorted(after) if (part:=clipped(b,c)) is not None])
                changes.append({'family':family['id'],'state':key,'removed_cells':[list(c) for c in sorted(removed)],
                                'before_boxes':state['collision_footprint']['boxes'],'after_boxes':boxes,
                                'basis':'Exact source occurrence physical overlap, plus cardinal and appearance-slot closure; user authorizes smaller collision with unchanged visuals.'})
            states[key]={'cells':[list(c) for c in sorted(after)],'boxes':boxes}
    return {'schemaVersion':1,'basis':'User-approved reductions for proven physical overlaps only','families':masks}, {
        'changed_states':len(changes),'changes':changes,'unresolved_shared_roots':shared,'external_source_conflicts':external,
        'models_changed':0,'root_positions_changed':0,'city_geometry_changed':0}

if __name__=='__main__':
    directory=ROOT/'docs/composite-grid-repair'
    masks,report=plan(json.loads((directory/'protected-world-oracle.json').read_bytes()),
                      json.loads((ROOT/'src/main/resources/bloodborne_blocks/logical/contracts-v2.json').read_bytes()),
                      json.loads((directory/'module-provenance.json').read_bytes()))
    # Reviewable proposal only: do not change production until shared roots
    # and all source membership conflicts have been reconciled.
    (ROOT/'build/composite-proposed-physical-footprints.json').write_text(json.dumps(masks,separators=(',',':'))+'\n',encoding='utf8')
    (directory/'physical-conflict-plan.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('proposed state changes',len(report['changes']),'shared roots',len(report['unresolved_shared_roots']))
