"""Compile only source rotations actually representable by original pack states.

Never infer artwork yaw from a vanilla facing property. Enumerate resource-pack
applications and prove textured-polygon equivalence instead. No world scan.
"""
import copy
import functools
import itertools
import json

from check_contract_orientation import mesh_tokens
from logical_contract_v2 import MATRICES, rotate_cell
from source_assembly_visuals import resource, source_polys

TRANSFORM={'rotations':MATRICES}


def condition(rule,props):
    if 'OR' in rule: return any(condition(item,props) for item in rule['OR'])
    if 'AND' in rule: return all(condition(item,props) for item in rule['AND'])
    return all(props.get(name) in str(value).split('|') for name,value in rule.items())


@functools.lru_cache(None)
def state_document(ident):
    return json.loads(resource(ident,'blockstates','json'))


def applications(ident,props):
    data=state_document(ident); groups=[]
    for selector,apps in data.get('variants',{}).items():
        if condition(dict(part.split('=',1) for part in selector.split(',') if part),props):
            groups.append([[copy.deepcopy(app)] for app in (apps if isinstance(apps,list) else [apps])])
    for part in data.get('multipart',[]):
        if condition(part.get('when',{}),props):
            apps=part['apply']; groups.append([[copy.deepcopy(app)] for app in (apps if isinstance(apps,list) else [apps])])
    return groups


def candidate_properties(ident,props):
    yield props.copy()
    seen={json.dumps(props,sort_keys=True)}
    data=state_document(ident)
    for selector in data.get('variants',{}):
        values=dict(part.split('=',1) for part in selector.split(',') if part)
        for choices in itertools.product(*(value.split('|') for value in values.values())):
            candidate={**props,**dict(zip(values,choices))}; token=json.dumps(candidate,sort_keys=True)
            if token not in seen: seen.add(token); yield candidate
    if 'multipart' in data:
        directions=('north','east','south','west')
        for turn in (1,2,3):
            candidate=props.copy()
            if props.get('facing') in directions: candidate['facing']=directions[(directions.index(props['facing'])+turn)%4]
            for i,name in enumerate(directions):
                if name in props: candidate[directions[(i+turn)%4]]=props[name]
            if 'axis' in props and props['axis'] in ('x','z') and turn%2: candidate['axis']='z' if props['axis']=='x' else 'x'
            yield candidate


@functools.lru_cache(2048)
def application_tokens(serialized,yaw):
    apps=json.loads(serialized)
    return tuple(sorted(mesh_tokens(source_polys(apps)[0],yaw,TRANSFORM).items()))


def group_tokens(groups,yaw):
    result=[]
    for group in groups:
        entries=[]
        for option in group:
            apps=[{k:v for k,v in app.items() if k not in ('offset','weight')} for app in option]
            entries.append((sum(app.get('weight',1) for app in option),application_tokens(json.dumps(apps,sort_keys=True),yaw)))
        result.append(tuple(sorted(entries)))
    return tuple(sorted(result))


def rotate_source_component(component,yaw):
    """Return rotated raw source + visual evidence, or None if unrepresentable."""
    if yaw==0: return copy.deepcopy(component)
    source=component.get('source',component); ident=source['id']; props=source['properties']
    groups=component.get('model_choices') or applications(ident,props)
    expected=group_tokens(groups,yaw)
    for candidate in candidate_properties(ident,props):
        candidate_groups=applications(ident,candidate)
        if group_tokens(candidate_groups,0)!=expected: continue
        copied=copy.deepcopy(component)
        copied['id']=ident; copied['properties']=candidate
        if 'source' in copied: copied['source']={'id':ident,'properties':candidate}
        copied['offset']=list(rotate_cell(component['offset'],yaw,TRANSFORM))
        copied['model_choices']=candidate_groups
        copied['source_apps']=[{**app,'offset':copied['offset']} for group in candidate_groups for app in group[0]]
        copied['rotation_evidence']={'yaw':yaw,'method':'exact_textured_pack_applications'}
        return copied
    return None
