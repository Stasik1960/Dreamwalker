"""Systematic textured-mesh orientation QA for every current/future Contract V2.

No image/model AABB is used for physics. Checks the independent render,
selection, collision and interaction transformations around the authored pivot.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import json
from pathlib import Path

from logical_contract_v2 import load_contracts, rotate_cell, rotate_box, master_origin

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
FACINGS = ('north', 'east', 'south', 'west')


def properties(key):
    return dict(item.split('=',1) for item in key.split(',') if item)


def state_key(props):
    return ','.join(f'{k}={v}' for k,v in sorted(props.items()))


def mesh_tokens(polygons, yaw, transform, offset=(0,0,0)):
    tokens = []
    for polygon in polygons:
        vertices=[]
        for vertex in polygon['vertices']:
            point = [vertex[i]+offset[i]-(.5,0,.5)[i] for i in range(3)]
            point = rotate_cell(point,yaw,transform)
            row = tuple(round(round(point[i]+(.5,0,.5)[i],8),5) for i in range(3)) + tuple(round(round(n,8),5) for n in vertex[3:])
            vertices.append(row)
        # Preserve winding: culling is part of actual visible geometry.
        cycles=[tuple(vertices[i:]+vertices[:i]) for i in range(len(vertices))]
        tokens.append((polygon['texture'],min(cycles)))
    return Counter(tokens)


def rotated_props(props, yaw):
    target=props.copy(); turns=yaw//90
    if 'facing' in props:
        target['facing']=FACINGS[(FACINGS.index(props['facing'])+turns)%4]
    if all(name in props for name in FACINGS):
        for i,name in enumerate(FACINGS):
            target[FACINGS[(i+turns)%4]]=props[name]
    return target


def audit(data, transform, meshes):
    failures=[]; rows=[]; checks=0
    for family in data['families']:
        states=family['states']; family_errors=[]
        symmetric=True; has_facing=False; connected=False
        for key,state in states.items():
            props=properties(key); has_facing |= 'facing' in props
            connected |= all(name in props for name in FACINGS)
            mesh=meshes[state['render_mesh']['id']]['polygons']
            offset=state['render_mesh']['offset']
            tokens=mesh_tokens(mesh,0,transform,offset)
            symmetric &= tokens==mesh_tokens(mesh,90,transform,offset)
            for yaw in (0,90,180,270):
                checks+=1
                dest_key=state_key(rotated_props(props,yaw))
                dest=states.get(dest_key)
                if dest is None:
                    family_errors.append(f'{key}: missing yaw{yaw} state'); continue
                dest_tokens=mesh_tokens(meshes[dest['render_mesh']['id']]['polygons'],0,transform,dest['render_mesh']['offset'])
                if mesh_tokens(mesh,yaw,transform,offset)!=dest_tokens:
                    family_errors.append(f'{key}: render yaw{yaw} is not equivalent at canonical anchor')
                for label in ('collision_footprint','selection_footprint'):
                    expected=sorted(tuple(round(v,5) for v in rotate_box(box,yaw,transform)) for box in state[label]['boxes'])
                    actual=sorted(tuple(round(v,5) for v in box) for box in dest[label]['boxes'])
                    if expected!=actual: family_errors.append(f'{key}: {label} yaw{yaw} drift')
                expected_cells={rotate_cell(c,yaw,transform) for c in state['interaction_footprint']['cells']}
                if expected_cells!={tuple(c) for c in dest['interaction_footprint']['cells']}:
                    family_errors.append(f'{key}: interaction yaw{yaw} drift')
                # Fixed actual placement target must not move master by horizontal yaw.
                origin=master_origin((100,65,100),family['canonical_anchor']['cell'],yaw,transform)
                if origin!=master_origin((100,65,100),family['canonical_anchor']['cell'],0,transform):
                    family_errors.append(f'{key}: master position changes with placement yaw{yaw}')
        if not symmetric and not has_facing and not connected:
            family_errors.append('ASYMMETRIC_WITHOUT_ORIENTATION')
        if symmetric and has_facing:
            family_errors.append('SYMMETRIC_WITH_REDUNDANT_FACING')
        rows.append({'id':family['id'],'rotationally_symmetric':symmetric,'facing':has_facing,
                     'connected':connected,'states':len(states),'result':'FAIL' if family_errors else 'PASS',
                     'errors':list(dict.fromkeys(family_errors))})
        failures.extend(family['id']+': '+e for e in dict.fromkeys(family_errors))
    return {'result':'FAIL' if failures else 'PASS','families':rows,'rotation_checks':checks,
            'symmetric':sum(row['rotationally_symmetric'] for row in rows),
            'facing':sum(row['facing'] for row in rows),'connected':sum(row['connected'] for row in rows),
            'errors':failures}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',type=Path,default=ROOT/'build/test-results/contract-orientation.json')
    args=parser.parse_args()
    data,transform=load_contracts(RES)
    with gzip.open(RES/'meshes.json.gz','rt',encoding='utf8') as stream: meshes=json.load(stream)
    report=audit(data,transform,meshes)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({key:value for key,value in report.items() if key not in ('families','errors')}))
    for error in report['errors'][:12]: print(error)
    if report['errors']: raise SystemExit(1)


if __name__=='__main__': main()
