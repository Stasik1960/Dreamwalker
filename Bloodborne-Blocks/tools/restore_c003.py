"""Compile the explicit C003 correction, using cached census and source art only.

No discovery, world scan, old-composite mesh copying or runtime file writes.
Each source cell owns its own RNG guard and target root; there is no six-cell
transaction or multiblock comprising the three barrels.
"""
from __future__ import annotations
import copy
import gzip
import hashlib
import json
from pathlib import Path

from build_reviewed_contracts import FACINGS, TRANSFORM, bounds, definition, key, rotate_polygons
from logical_contract_v2 import box_cells, rotate_box
from source_assembly_pipeline import active_choices
from source_assembly_visuals import resource, source_polys
from source_variant_rng import guards_for

ROOT = Path(__file__).resolve().parents[1]
DECISION = ROOT / 'docs/c003-semantic-correction.json'
CENSUS = ROOT / 'docs/catalog-completeness-audit-20260924/world-state-usage.json.gz'

# Explicit simple gameplay cores, in each authored model's local source frame.
# Decorative paper has no collision. These are not mesh-element voxel unions.
CORES = {
    'barrel_0': [.125,0,.125,.875,1.3125,.875],
    'barrel_1': [.125,0,.125,.875,1.3125,.875],
    'barrel_2': [.125,0,.125,.875,1.3125,.875],
    'barrel_3': [.08,0,-.18,.83,1.3125,.57],
    'barrel_4': [-.55,0,.13,.20,1.3125,.88],
    'barrel_5': [.59,0,-.36,1.34,1.3125,.39],
    'bag_0': [.25,0,.25,.75,.9375,.75],
    'bag_1': [0,0,-.125,.5,.9375,.375],
    'bag_2': [.4,0,.5,.9,.9375,1],
    'bag_3': [.25,0,.25,.75,.9375,.75],
    'cases_0': [0,0,0,1,1,1],
}


def compile_c003(frozen=None):
    decision = json.loads(DECISION.read_bytes())
    census = json.loads(gzip.decompress(CENSUS.read_bytes()))
    blocks, families, meshes, evidence = [], [], {}, []
    for spec in decision['families']:
        ident, variants = spec['id'], spec['variants']
        properties = {'facing': list(FACINGS)}
        if len(variants) > 1:
            properties['variant'] = variants
        block = definition(ident, properties)
        block['semantic'] = spec['semantic'].lower()
        family = {'id': ident, 'review_id': 'C003', 'authority': 'user',
            'compiler_owner': 'independent_c003', 'canonical_anchor': {'cell': [0,0,0], 'pivot': [.5,0,.5]},
            'placement_policy': 'FLOOR', 'rotations': [0,90,180,270], 'mirror_policy': 'ROTATE_ONLY',
            'collision_policy': 'NONE' if spec['semantic']=='BOOKS' else 'SIMPLE_BOX', 'states': {}}
        lifts = {}
        for variant in variants:
            original, _ = source_polys([{'model': 'minecraft:block/addon/' + variant}])
            # Source meshes penetrate the floor by <=1 source pixel. Keep the
            # source root unchanged; support-normalize artwork, not world cells.
            lift = max(0.0, -bounds(original)[1]); lifts[variant] = lift
            for facing, yaw in zip(FACINGS, (0,90,180,270)):
                state = key(dict(facing=facing, **({'variant': variant} if len(variants) > 1 else {})))
                polygons = rotate_polygons(original, yaw, offset=(0,lift,0))
                collision = [rotate_box(CORES[variant],yaw,TRANSFORM)] if variant in CORES else []
                footprint = {(0,0,0)}
                for box in collision: footprint.update(box_cells(box))
                mesh = 'restored_c003_' + hashlib.sha256((ident+state).encode()).hexdigest()[:16]
                meshes[mesh] = {'polygons': polygons}
                block['models'][state] = mesh
                family['states'][state] = {'rotation': yaw,
                    'render_mesh': {'id': mesh, 'bounds': bounds(polygons), 'offset': [0,0,0]},
                    'selection_footprint': {'boxes': [bounds(polygons)]},
                    'collision_footprint': {'boxes': collision},
                    'interaction_footprint': {'cells': [list(c) for c in sorted(footprint)]},
                    'migration_source_pattern': []}
        raw = json.loads(resource(spec['source_id'], 'blockstates', 'json'))
        source_states = [r for r in census['states'] if r['id']==spec['source_id']]
        used_states = []
        for source in source_states:
            choices = active_choices(raw,source['properties'])
            if not any(app.get('model','').rsplit('/',1)[-1] in variants
                       for group in choices for option in group for app in option):
                continue
            # These four explicit categories use one weighted-or-single group.
            if len(choices)!=1: raise ValueError('unexpected C003 multipart source: '+str(source))
            for option in choices[0]:
                if len(option)!=1: raise ValueError('unexpected C003 multi-application')
                app = copy.deepcopy(option[0]); variant = app['model'].rsplit('/',1)[-1]
                if variant not in variants:
                    continue  # Same carrier's other source states are not this object.
                if app.get('x',0) or app.get('y',0)%90: raise ValueError('unsupported C003 source rotation')
                facing = FACINGS[(-app.get('y',0)//90)%4]
                state = key(dict(facing=facing, **({'variant': variant} if len(variants) > 1 else {})))
                component = {'id':source['id'], 'properties':copy.deepcopy(source['properties']),
                             'offset':[0,0,0], 'source_apps':[dict(app,offset=[0,0,0])]}
                descriptor = {'id':source['id'], 'model_choices':choices}
                pattern = {'components':[component], 'variant_guards':guards_for(descriptor,[app],[0,0,0]),
                    'matching_evidence':'C003 user correction: independent source cell; original XYZ selects model variant',
                    'source_support_lift':lifts[variant]}
                family['states'][state]['migration_source_pattern'].append(pattern)
            used_states.append({'properties':source['properties'], 'counts': {d:v['count'] for d,v in source['dimensions'].items()}})
        if not any(s['migration_source_pattern'] for s in family['states'].values()):
            raise ValueError('no cached raw source evidence for '+ident)
        blocks.append(block); families.append(family)
        evidence.append({'id':ident,'components':spec['components'],'variants':variants,
            'source_id':spec['source_id'],'source_states':used_states,'source_support_lift':lifts,
            'semantic':'independent occurrences, not joined parts','master_offset_from_source':[0,0,0]})
    return {'blocks':blocks,'families':families,'meshes':meshes,'evidence':{'decision':decision,'families':evidence},
            'removed_ids':['o_c003']}


def record_decision():
    """Append only the new semantic decision; keep signatures and discovery intact."""
    decision=json.loads(DECISION.read_bytes())
    path=ROOT/'docs/manual-source-assemblies.json'
    doc=json.loads(path.read_bytes())
    row=next(r for r in doc['candidates'] if r['review_id']=='C003')
    event={'event':'authoritative_semantic_correction','revision':decision['revision'],
           'decision':decision,'supersedes':decision['supersedes']}
    if event not in row.setdefault('history',[]): row['history'].append(event)
    row['user_decision']={'kind':'SPLIT_TO_FAMILIES','families':decision['families'],
                          'occurrence_rule':decision['occurrence_rule']}
    row['status']='REVIEWED'
    from build_reviewed_contracts import dump
    dump(path,doc,pretty=True)


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--record-decision',action='store_true');args=parser.parse_args()
    if args.record_decision: record_decision()
    else:
        result=compile_c003();print(json.dumps({'families':[b['id'] for b in result['blocks']],'meshes':len(result['meshes'])}))
