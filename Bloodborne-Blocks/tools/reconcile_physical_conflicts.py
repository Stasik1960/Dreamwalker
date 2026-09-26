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

def plan(oracle,contracts):
    by_id={f['id']:f for f in contracts['families']};remove=defaultdict(set)
    shared=[]
    for conflict in oracle['physical_conflicts']:
        roots=[o for o in conflict['owners'] if o['root']==conflict['position']]
        if len(roots)>1:shared.append(conflict)
        for owner in conflict['owners']:
            p=tuple(conflict['position'][i]-owner['root'][i] for i in range(3))
            if p==(0,0,0):continue
            family=owner['family'];key=owner['state'].partition('[')[2].rstrip(']')
            remove[(family,key)].add(p)
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
    masks={};changes=[]
    for family in contracts['families']:
        states={};masks[family['id']]=states
        for key,state in family['states'].items():
            before={tuple(c) for c in state['interaction_footprint']['cells']}
            removed=before&remove[(family['id'],key)]
            after=before-removed
            if (0,0,0) not in after:raise ValueError('ROOT_REMOVAL_FORBIDDEN')
            boxes=state['collision_footprint']['boxes']
            if removed:
                boxes=merge([part for b in boxes for c in sorted(after) if (part:=clipped(b,c)) is not None])
                changes.append({'family':family['id'],'state':key,'removed_cells':[list(c) for c in sorted(removed)],
                                'before_boxes':state['collision_footprint']['boxes'],'after_boxes':boxes,
                                'basis':'Exact source occurrence physical overlap, plus cardinal closure; user authorizes smaller collision with unchanged visuals.'})
            states[key]={'cells':[list(c) for c in sorted(after)],'boxes':boxes}
    return {'schemaVersion':1,'basis':'User-approved reductions for proven physical overlaps only','families':masks}, {
        'changed_states':len(changes),'changes':changes,'unresolved_shared_roots':shared,
        'models_changed':0,'root_positions_changed':0,'city_geometry_changed':0}

if __name__=='__main__':
    directory=ROOT/'docs/composite-grid-repair'
    masks,report=plan(json.loads((directory/'protected-world-oracle.json').read_bytes()),
                      json.loads((ROOT/'src/main/resources/bloodborne_blocks/logical/contracts-v2.json').read_bytes()))
    # Reviewable proposal only: do not change production until shared roots
    # and all source membership conflicts have been reconciled.
    (ROOT/'build/composite-proposed-physical-footprints.json').write_text(json.dumps(masks,separators=(',',':'))+'\n',encoding='utf8')
    (directory/'physical-conflict-plan.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('proposed state changes',len(report['changes']),'shared roots',len(report['unresolved_shared_roots']))
