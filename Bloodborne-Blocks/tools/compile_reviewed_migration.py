"""Prove reviewed raw-source patterns against baked meshes; no world scan.

Only integer translations and resource-pack-representable horizontal rotations
are admitted. Weighted alternatives receive Minecraft positional RNG guards.
Unrepresentable/unauthored art is reported, never silently substituted.
"""
import copy
import functools
import gzip
import hashlib
import itertools
import json
from pathlib import Path

from check_contract_orientation import mesh_tokens
from logical_contract_v2 import MATRICES
from source_assembly_visuals import source_polys, resource
from source_contract_rotations import rotate_source_component
from source_variant_rng import guards_for

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
LOGICAL=RES/'bloodborne_blocks/logical'
TRANSFORM={'rotations':MATRICES}


@functools.lru_cache(None)
def texture_identity(name):
    ns,path=name.split(':',1)
    raw=(RES/f'assets/{ns}/textures/{path}.png').read_bytes() if ns=='bloodborne_blocks' else resource(name,'textures','png')
    return hashlib.sha256(raw).hexdigest()


def identified(polys):
    return [{**p,'texture':texture_identity(p['texture'])} for p in polys]


def minimum(polys):
    return tuple(min(v[i] for p in polys for v in p['vertices']) for i in range(3))


def normalized(polys):
    low=minimum(polys)
    return tuple(sorted(mesh_tokens(polys,0,TRANSFORM,offset=tuple(-n for n in low)).items())),low


@functools.lru_cache(4096)
def component_polys(serialized):
    return source_polys(json.loads(serialized))[0]


def choices(parts,limit=4096):
    groups=[(i,group) for i,p in enumerate(parts) for group in p['model_choices']]
    count=1
    for _,group in groups: count*=len(group)
    if count>limit: raise ValueError(f'Bounded reviewed pattern exceeds {limit} visual alternatives: {count}')
    for options in itertools.product(*(group for _,group in groups)):
        selected=[[] for _ in parts]
        for (i,_),apps in zip(groups,options):
            selected[i].extend({**app,'offset':parts[i]['offset']} for app in apps)
        yield selected


def build():
    from build_reviewed_contracts import corrected_c282_polygons
    path=LOGICAL/'contracts-v2.json'; data=json.loads(path.read_text(encoding='utf8'))
    with gzip.open(LOGICAL/'meshes.json.gz','rt',encoding='utf8') as stream: meshes=json.load(stream)
    report={'schemaVersion':1,'method':'raw resource applications + textured geometry equivalence + integer master translation',
            'world_scan':False,'families':[],'pattern_aliases':[]}
    for family in data['families']:
        owner=family.get('compiler_owner')
        if owner is not None:
            if (family['id'],owner) != ('o_c001','qa2_trees'):
                raise ValueError('unsupported compiler_owner: '+repr((family['id'],owner)))
        if owner == 'qa2_trees':
            # Complete-tree patterns/weighted meshes have their own bounded,
            # independently checked compiler; never re-author them as old SPLIT.
            continue
        if family.get('authority')!='user': continue
        print('Proving '+family['id'],flush=True)
        row={'id':family['id'],'retained':0,'unrepresentable_source_rotations':[], 'unauthored_weighted_art':0}
        seeds={p['exact_source_signature']:copy.deepcopy(p) for p in family.get('review_source_patterns',[])}
        for state in family['states'].values():
            for pattern in state['migration_source_pattern']:
                # Original, uncompiled definitions are retained to make this pass idempotent.
                original=pattern.get('review_source_pattern',pattern)
                seeds.setdefault(original['exact_source_signature'],copy.deepcopy(original))
            state['migration_source_pattern']=[]
        family['review_source_patterns']=list(seeds.values())
        targets={}
        for key,state in family['states'].items():
            props=dict(part.split('=',1) for part in key.split(',') if part)
            if props.get('open','false')!='false' or props.get('hinge','left')!='left':continue
            if family['id']=='o_c654' and props.get('variant')!='source_depth':continue
            if family['id']=='o_c282' and props.get('placement_height')!='source_height':continue
            polys=identified(meshes[state['render_mesh']['id']]['polygons'])
            token,low=normalized(polys)
            targets.setdefault(token,[]).append((key,low))
        seen=set()
        for signature,seed in seeds.items():
            for yaw in (0,90,180,270):
                parts=[rotate_source_component(part,yaw) for part in seed['components']]
                if any(part is None for part in parts):
                    row['unrepresentable_source_rotations'].append({'signature':signature,'yaw':yaw});continue
                for selected in choices(parts):
                    polys=[p for apps in selected for p in component_polys(json.dumps(apps,sort_keys=True))]
                    if family['id']=='o_c282':polys=corrected_c282_polygons(polys)
                    token,low=normalized(identified(polys))
                    matches=targets.get(token,[])
                    matches=[(key,tuple(low[i]-target[i] for i in range(3))) for key,target in matches]
                    matches=[(key,tuple(round(v) for v in shift)) for key,shift in matches if all(abs(v-round(v))<1e-5 for v in shift)]
                    if not matches:
                        row['unauthored_weighted_art']+=1;continue
                    key,shift=sorted(matches)[0]
                    components=[];guards=[]
                    for part,apps in zip(parts,selected):
                        offset=[part['offset'][i]-shift[i] for i in range(3)]
                        components.append({'id':part['id'],'properties':part['properties'],'offset':offset})
                        guards.extend(guards_for(part,apps,offset))
                    fingerprint=json.dumps([key,components,guards],sort_keys=True)
                    if fingerprint in seen:continue
                    seen.add(fingerprint)
                    pattern={'components':components,'variant_guards':guards,'authority':'user','review_id':family['review_id'],
                             'exact_source_signature':signature,'source_rotation':yaw,'master_from_review_origin':list(shift),
                             'matching_evidence':'exact textured mesh after integer translation (documented C282 UV correction applied)',
                             'review_source_pattern':seed}
                    family['states'][key]['migration_source_pattern'].append(pattern);row['retained']+=1
        if row['retained']==0:raise ValueError('No proven migration for '+family['id'])
        report['families'].append(row)
    # Equal raw patterns must not produce ambiguous objects after a reviewed SPLIT.
    # Dedup only after proving the SAME world mesh, physics and helper footprint.
    all_patterns={}
    for family in data['families']:
        for key,state in family['states'].items():
            kept=[]
            for pattern in state['migration_source_pattern']:
                components=pattern['components'];base=min(tuple(c['offset']) for c in components)
                raw=sorted((c['id'],tuple(sorted(c['properties'].items())),tuple(c['offset'][i]-base[i] for i in range(3))) for c in components)
                guards=[{**g,'offset':[g['offset'][i]-base[i] for i in range(3)]} for g in pattern.get('variant_guards',[])]
                source_key=json.dumps([raw,guards],sort_keys=True)
                mesh=tuple(sorted(mesh_tokens(identified(meshes[state['render_mesh']['id']]['polygons']),0,TRANSFORM,offset=tuple(-n for n in base)).items()))
                physics=tuple(tuple(round(v-base[i%3],6) for i,v in enumerate(b)) for b in state['collision_footprint']['boxes'])
                helpers=tuple(sorted(tuple(c[i]-base[i] for i in range(3)) for c in state['interaction_footprint']['cells']))
                effect=(mesh,physics,helpers)
                previous=next((entry for entry in all_patterns.get(source_key,[]) if entry[2][0]==mesh and entry[2][2]==helpers),None)
                if previous and previous[2][0]==mesh and previous[2][2]==helpers and family.get('authority')=='user':
                    report['pattern_aliases'].append({'from':family['id'],'state':key,'to':previous[0],'target_state':previous[1],
                                                      'source_signature':pattern.get('exact_source_signature'),'reason':'same raw pattern, world mesh and helper footprint; retain earlier existing primitive policy',
                                                      'identical_collision':previous[2][1]==physics})
                else:
                    all_patterns.setdefault(source_key,[]).append((family['id'],key,effect));kept.append(pattern)
            state['migration_source_pattern']=kept
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    report['rules_after_aliases']=sum(len(s['migration_source_pattern']) for f in data['families'] for s in f['states'].values())
    (ROOT/'docs/reviewed-batch-02-migration-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'rules':report['rules_after_aliases'],'aliases':len(report['pattern_aliases']), 'families':report['families']}))


if __name__=='__main__':build()
