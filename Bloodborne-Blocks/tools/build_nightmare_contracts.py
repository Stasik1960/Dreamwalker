"""Idempotent NightmareRunning contract compiler.

This compiler freezes the reviewed pre-Nightmare definitions and contracts on
its first invocation.  Later invocations deliberately use that snapshot, not
the generated output, so a rerun cannot turn a generated split into new input.
Meshes are never frozen or rewritten under their old identifiers.
"""
from __future__ import annotations

import copy
import gzip
import json
import math
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
LOGICAL = RESOURCES / "bloodborne_blocks/logical"
BASELINE = ROOT / "docs/nightmare-qa-baseline.json.gz"
BASELINE_COMMIT = "9e60c5"
OLD_SPLITS = ("o_c008", "o_c1979", "o_c1491", "o_c1962", "o_c471", "o_c654", "o_c282")
RAW_DISABLED = set(OLD_SPLITS)


def read(path: Path):
    return json.loads(path.read_text(encoding="utf8"))


def write(path: Path, value, *, zipped=False):
    raw = (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf8")
    if zipped:
        raw = gzip.compress(raw, mtime=0)
        raw = raw[:9] + b"\xff" + raw[10:]
    if not path.exists() or path.read_bytes() != raw:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)


def freeze_baseline(logical: Path = LOGICAL, baseline: Path = BASELINE) -> dict:
    """Capture only relevant old definitions/contracts, once and deterministically."""
    if baseline.exists():
        with gzip.open(baseline, "rt", encoding="utf8") as stream:
            data = json.load(stream)
        if data.get("baseline_commit") != BASELINE_COMMIT:
            raise ValueError("Nightmare baseline belongs to another source revision")
        return data
    definitions, contracts = read(logical / "definitions.json"), read(logical / "contracts-v2.json")
    old = set(OLD_SPLITS) | {"o_c002", "o_c028", "o_c1680", "o_c618", "o_wall_deco_1"}
    data = {"schemaVersion": 1, "baseline_commit": BASELINE_COMMIT,
            "definitions": [row for row in definitions["blocks"] if row["id"] in old],
            "families": [row for row in contracts["families"] if row["id"] in old]}
    write(baseline, data, zipped=True)
    return data


def strip_provenance(polygons):
    """Runtime meshes retain only renderable data; review provenance stays in reports."""
    return [{"texture": polygon["texture"], "vertices": copy.deepcopy(polygon["vertices"])} for polygon in polygons]


def mark_old_splits(contracts: dict, hidden: set[str]) -> None:
    for family in contracts["families"]:
        if family["id"] not in OLD_SPLITS:
            continue
        hidden.add(family["id"])
        if family["id"] in RAW_DISABLED:
            family["migration_disabled"] = True
        # C654's raw matcher is intentionally replaced by C654-A/source_depth.
        if family["id"] == "o_c654":
            family["migration_disabled"] = True


def c654_properties():
    """The source-depth state is migration-only; surface is the manual default."""
    return ({"facing": ["north", "east", "south", "west"], "variant": ["surface", "source_depth"]},
            {"facing": ["north", "east", "south", "west"]})


def build(resources: Path = RESOURCES, baseline: Path = BASELINE) -> dict:
    """Compile explicit recipes and raw-source transactions, then publish together."""
    from build_reviewed_contracts import (FACINGS, TRANSFORM, bounds, copy_texture, definition,
        key, rotate_polygons, source_variants, write_assets, dump)
    from source_assembly_visuals import source_polys
    from nightmare_geometry_recipes import recipe as static_recipe, tagged_polys
    from nightmare_functional_recipes import recipe as functional_recipe
    from nightmare_composition_recipes import recipe as composition_recipe, tagged_polys_for_components
    from logical_contract_v2 import box_cells, rotate_box, rotate_cell
    from sync_reviewed_geometry import profile
    logical = resources / "bloodborne_blocks/logical"
    frozen = freeze_baseline(logical, baseline)
    definitions, contracts, geometry = (read(logical / name) for name in ("definitions.json", "contracts-v2.json", "geometry.json"))
    hidden_path = logical / "hidden-items.json"; hidden = set(read(hidden_path))
    with gzip.open(logical / 'meshes.json.gz', 'rt', encoding='utf8') as stream: meshes = json.load(stream)
    original_defs = {d['id']: d for d in frozen['definitions']}
    original_families = {f['id']: f for f in frozen['families']}
    rows = {r['review_id']: r for r in read(ROOT / 'docs/manual-review/source-assemblies/batch-02/batch-02-manifest.json')['candidates']}
    authoring = {a['id']: a for a in read(ROOT / 'docs/reviewed-batch-02-authoring.json')['families']}
    report = {"schemaVersion": 1, "compiler_owner": "nightmare_qa", "baseline_commit": BASELINE_COMMIT,
              "families": [],
              "pending_migration": [{"review_id": cid, "patterns": patterns,
                                     "status": "SAFE_BLOCKED", "reason": "target_would_overwrite_foreign_block",
                                     "evidence": "nightmare-checks/reviewed-batch-02-source-examples.json"}
                                    for cid, patterns in (("C282", ["A", "B", "C", "D"]),
                                                          ("C654", ["A", "B", "C"]), ("C1491", ["A"]))],
              "c654": {"replacement": "o_c654_a", "migration_variant": "source_depth", "manual_variant": "surface", "b_manual_only": True}}
    emitted_defs, emitted_families = {}, {}
    def values(state): return dict(p.split('=', 1) for p in state.split(',') if p)
    def clean(poly):
        return [{'texture': copy_texture(p['texture']) if not p['texture'].startswith('bloodborne_blocks:') else p['texture'],
                 'vertices': p['vertices']} for p in poly]
    def recipe_set(old_id, old_values):
        if old_id in ('o_c618', 'o_wall_deco_1'):
            north = key({**old_values, 'facing': 'north'})
            poly = copy.deepcopy(meshes[original_defs[old_id]['models'][north]]['polygons'])
            box = bounds(poly); dy = -box[1]
            return [{'name': old_id, 'polygons': rotate_polygons(poly, 0, offset=(0,dy,0)),
                     'source_translation': (0,dy,0), 'collision_box': ([.25,0,.25,.75,2,.75] if old_id=='o_c618' else None),
                     'provenance': {'baseline_mesh': original_defs[old_id]['models'][north], 'lift': dy}}]
        row = rows[original_families[old_id]['review_id']]
        numbers = authoring[old_id]['component_numbers']
        parts = [p for p in row['components'] if p['number'] in numbers]
        anchor = min((p['relative'] for p in parts), key=lambda p:(p[1],p[2],p[0]))
        apps = [{**a, 'offset': [a.get('offset',p['relative'])[i]-anchor[i] for i in range(3)]} for p in parts for a in p['apps']]
        variants, _ = source_variants(row, numbers, anchor, apps)
        chosen = dict(variants).get(old_values.get('variant','canonical'), apps)
        if old_id in ('o_c008','o_c002','o_c1979'):
            # Composition recipes explicitly retain original component numbers.
            full = copy.deepcopy(row['components']); by_pos = {}
            for app in chosen: by_pos.setdefault(tuple(app['offset']), []).append(app)
            for p in full:
                pos = tuple(p['relative'][i]-anchor[i] for i in range(3))
                if p['number'] in numbers:
                    p['apps'] = [{**a,'offset':[a['offset'][i]+anchor[i] for i in range(3)]} for a in by_pos[pos]]
            result = composition_recipe(old_id, tagged_polys_for_components(full,numbers))
        elif old_id in ('o_c282','o_c654'):
            result = functional_recipe(old_id, tagged_polys(chosen))
        else:
            result = static_recipe(old_id, tagged_polys(chosen))
        for out in result:
            name = out['name']
            if old_id=='o_c1979':
                # Recipe supplies old canonical frame; move each semantic object
                # to its own integer source-cell anchor, not a global five-part anchor.
                number = out['provenance']['source_components'][0]
                rel = next(p['relative'] for p in parts if p['number']==number)
                root = [rel[i]-anchor[i] for i in range(3)]
                poly = rotate_polygons(out['polygons'],0,offset=tuple(-n for n in root))
                dy = -bounds(poly)[1]
                out['polygons']=rotate_polygons(poly,0,offset=(0,dy,0))
                out['source_translation']=(-root[0],dy,-root[2])
                if name.endswith('_3'): out['collision_box']=[0,0,.25,3,1,.75]
                elif name.endswith(('_4','_5')): out['collision_box']=[.1,0,0,.9,1,2]
                else: out['collision_box']=[.1,0,.1,.9,2,.9]
            if name=='o_c1491_a':
                # Main foot lies in z=0. A loose neighboring base detail made
                # the raw AABB centre cross z=1 (also occupied by grave C).
                out['polygons']=rotate_polygons(out['polygons'],0,offset=(0,0,1))
                out['source_translation']=(out['source_translation'][0],out['source_translation'][1],0)
                out['recommended_source_root_offset']=[-1,0,0]
                out['canonical_bounds']=bounds(out['polygons'])
            if old_id=='o_c1491': out['collision_box']=[.22,0,.22,.78,1.25 if name.endswith('_c') else 3,.78]
            if old_id=='o_c654': out['collision_box']=[-.875,.1875,.90625,1.875,2.9375,.96875]
            if old_id=='o_c1680':
                out['collision_boxes']=[[.1,0,-.9,.9,1.3,-.1],[-.9,0,.1,-.1,1.3,.9]]
        return result

    for old_id, original in original_families.items():
        old_def = original_defs[old_id]
        north_states = [values(k) for k in old_def['states'] if values(k).get('facing','north')=='north']
        north_states = [v for v in north_states if v.get('open','false')=='false' and v.get('hinge','left')=='left'
                        and v.get('placement_height','manual')=='manual' and (old_id!='o_c654' or v.get('variant')=='surface')]
        outputs_by_input = [(v, recipe_set(old_id,v)) for v in north_states]
        names = [o['name'] for o in outputs_by_input[0][1]]
        fractional = any(abs(o['source_translation'][1]-round(o['source_translation'][1]))>1e-6 for _,os in outputs_by_input for o in os)
        output_recipes = {name:{} for name in names}
        for old_values, outputs in outputs_by_input:
            signature = key({k:v for k,v in old_values.items() if k not in ('facing','open','hinge','placement_height')})
            for out in outputs: output_recipes[out['name']][signature] = out
        for name in names:
            props = {k:copy.deepcopy(v) for k,v in old_def['properties'].items() if k not in ('visual','placement_height')}
            props['facing']=list(FACINGS)
            if old_id=='o_c471':props.pop('facing') # Each isolated spire is textured-yaw symmetric.
            if old_id=='o_c282': props['placement_height']=['manual','source_height']
            if old_id=='o_c654': props=c654_properties()[0 if name.endswith('_a') else 1]
            block=definition(name,props,old_def.get('behavior','static'),old_def.get('emissive',False))
            if 'placement_height' in props: block['placement_properties']={'placement_height':'manual'}
            if old_id=='o_c654' and 'variant' in props: block['placement_properties']={'variant':'surface'}
            if old_id=='o_c282' and name.endswith('_b'): block['default']['hinge']='right'
            if old_id=='o_c618': block['default']['lit']='true'
            family={'id':name,'review_id':original.get('review_id'),'authority':original.get('authority'),
                    'compiler_owner':'nightmare_qa','canonical_anchor':{'cell':[0,0,0],'pivot':[.5,0,.5]},
                    'placement_policy':original['placement_policy'],'rotations':[0,90,180,270], 'mirror_policy':'ROTATE_ONLY',
                    'collision_policy':'DOOR' if old_id=='o_c282' else 'NONE' if old_id=='o_wall_deco_1' else 'SIMPLE_BOX',
                    'states':{}}
            if family['review_id'] is None: family.pop('review_id');family.pop('authority')
            if old_id=='o_c1680':
                family['collision_policy']='TWO_BOX'
                family['collision_justification']='Two compact suitcase stacks; no collision on removed side decorations.'
            for state in block['states']:
                val=values(state); yaw=FACINGS.index(val.get('facing','north'))*90
                basevals={k:v for k,v in val.items() if k not in ('facing','open','hinge','placement_height')}
                if old_id=='o_c654': basevals['variant']='surface'
                out=output_recipes[name][key(basevals)]
                poly=copy.deepcopy(out['polygons']); box=copy.deepcopy(out['collision_box'])
                if val.get('placement_height')=='source_height':
                    dy=math.ceil(out['source_translation'][1]-1e-6)-out['source_translation'][1]
                    poly=rotate_polygons(poly,0,offset=(0,dy,0))
                    if old_id=='o_c282':
                        # Source leaf seam is half-cell. Helpers have one owner:
                        # partition the closed passage at integer x=0, reserving
                        # A's one column and B's two columns, never sharing a helper.
                        box=[0,0,.3125,1 if name.endswith('_a') else 2,3,.5625]
                if old_id=='o_c654' and val.get('variant')=='source_depth':
                    poly=rotate_polygons(poly,0,offset=(0,0,.1875)); box=[*box];box[2]+=.1875;box[5]+=.1875
                if old_id=='o_c282' and val['open']=='true':
                    pivot=out['door_pivots'][val['hinge']];turn=90 if val['hinge']=='left' else 270
                    poly=rotate_polygons(poly,turn,pivot=pivot)
                    box=rotate_box(box,turn,TRANSFORM,pivot=(pivot[0],0,pivot[1]))
                poly=clean(rotate_polygons(poly,yaw)); boxes=[rotate_box(b,yaw,TRANSFORM) for b in out.get('collision_boxes',[] if box is None else [box])]
                selection=bounds(poly)
                for axis in range(3):
                    if selection[axis+3]-selection[axis]<1e-5:selection[axis]-=.001;selection[axis+3]+=.001
                cells=sorted({(0,0,0),*(c for b in boxes for c in box_cells(b))})
                mesh='nightmare_'+name+'_'+hashlib.sha256(state.encode()).hexdigest()[:12]
                meshes[mesh]={'polygons':poly};block['models'][state]=mesh
                if val.get('lit')=='true':block['states'][state][2]=15
                family['states'][state]={'rotation':yaw,'render_mesh':{'id':mesh,'bounds':bounds(poly),'offset':[0,0,0]},
                    'selection_footprint':{'boxes':[selection]},'collision_footprint':{'boxes':boxes},
                    'interaction_footprint':{'cells':[list(c) for c in cells]},'migration_source_pattern':[]}
            emitted_defs[name]=block;emitted_families[name]=family
        # Preserve every previously proven exact raw pattern, including weighted
        # guards and contextual exclusions. Only the authored output changes.
        pattern_count=0
        for old_state, data in original['states'].items():
            old_values=values(old_state);yaw=FACINGS.index(old_values.get('facing','north'))*90
            canon={k:v for k,v in old_values.items() if k not in ('facing','open','hinge','placement_height')}
            if old_id=='o_c654':canon['variant']='surface'
            if key(canon) not in output_recipes[names[0]]:continue
            for pattern in data['migration_source_pattern']:
                declarations=[];targets={}
                for name in names:
                    if old_id=='o_c654' and name.endswith('_b'):continue
                    out=output_recipes[name][key(canon)];tx,ty,tz=out['source_translation']
                    root=(-round(tx),-math.ceil(ty-1e-6),-round(tz))
                    # QA explicitly corrects sunk artwork: retain the old
                    # carrier-level Y for raised objects, not its buried base.
                    # Otherwise a floor/context block would become the master.
                    if old_id!='o_c282':root=(root[0],0,root[2])
                    if old_id=='o_c654':root=(0,0,0)
                    roots=[root]
                    if name=='o_c008_3':roots.append((0,root[1],0))
                    for pos in roots:
                        declarations.append({'family':name,'root_offset':list(rotate_cell(pos,yaw,TRANSFORM))})
                    props={**emitted_defs[name]['default'],**{k:v for k,v in old_values.items() if k in emitted_defs[name]['properties']}}
                    if 'placement_height' in props:props['placement_height']='source_height'
                    if old_id=='o_c654':props['variant']='source_depth'
                    if old_id=='o_c282':props['hinge']='left' if name.endswith('_a') else 'right'
                    targets[name]=key(props)
                for name,target in targets.items():
                    rule=copy.deepcopy(pattern)
                    rule['matching_evidence']='NightmareRunning reviewed geometry correction; raw exact states and RNG guards retained from QA2'
                    if len(declarations)>1:
                        token=hashlib.sha256(json.dumps([old_state,pattern],sort_keys=True).encode()).hexdigest()[:16]
                        rule['split_transaction']={'id':old_id+'_'+token,'outputs':declarations}
                    else:
                        delta=declarations[0]['root_offset']
                        for c in rule['components']:c['offset']=[c['offset'][i]-delta[i] for i in range(3)]
                        for g in rule.get('variant_guards',[]):g['offset']=[g['offset'][i]-delta[i] for i in range(3)]
                    emitted_families[name]['states'][target]['migration_source_pattern'].append(rule)
                    pattern_count+=1
        report['families'].append({'old_id':old_id,'outputs':names,'pattern_members':pattern_count,
            'recipes':[{k:v for k,v in o.items() if k not in ('polygons',)} for o in outputs_by_input[0][1]]})
    # Do not hide any composite until all its replacement contracts are emitted.
    for name in emitted_families:
        if not emitted_families[name]['states']:raise ValueError('empty replacement family '+name)
    existing_defs={d['id'] for d in definitions['blocks']};existing_families={f['id'] for f in contracts['families']}
    definitions['blocks']=[emitted_defs.get(d['id'],d) for d in definitions['blocks']]+[d for n,d in emitted_defs.items() if n not in existing_defs]
    contracts['families']=[emitted_families.get(f['id'],f) for f in contracts['families']]+[f for n,f in emitted_families.items() if n not in existing_families]
    mark_old_splits(contracts,hidden)
    for name,family in emitted_families.items():
        geometry['blocks'][name]=profile(family)
        write_assets(name,emitted_defs[name])
        for mesh in set(emitted_defs[name]['models'].values()):
            textures=sorted({p['texture'] for p in meshes[mesh]['polygons']})
            dump(resources/f'assets/bloodborne_blocks/models/block/logical/{mesh}.json',
                 {'parent':'minecraft:block/block','textures':{'particle':textures[0],**{str(i):t for i,t in enumerate(textures)}},'elements':[]})
    for file,data in (('definitions.json',definitions),('contracts-v2.json',contracts),('geometry.json',geometry)):
        dump(logical/file,data,pretty=file=='contracts-v2.json')
    dump(logical/'meshes.json.gz',meshes,zipped=True)
    write(hidden_path, sorted(hidden))
    write(logical/'old-logical-migrations.json',{'schemaVersion':1,'rules':[
        {'kind':'c654_to_a','source':{'id':'o_c654','properties':values(state)},
         'target':{'id':'o_c654_a','properties':{**values(state),'visual':'base'}},
         'root_offset':[0,0,0], 'source_shape':data['interaction_footprint']['cells']}
        for state,data in original_families['o_c654']['states'].items()]})
    for locale in ('en_us','ru_ru'):
        path=resources/f'assets/bloodborne_blocks/lang/{locale}.json'; language=read(path)
        for old_id in original_families:
            old_label=language.get('block.bloodborne_blocks.'+old_id,old_id)
            for ident in emitted_defs:
                if ident.startswith(old_id+'_'):
                    language['block.bloodborne_blocks.'+ident]=old_label+' — '+ident[len(old_id)+1:].upper()
        dump(path,language)
    write(ROOT / "docs/nightmare-qa-report.json", report)
    print('Nightmare:',len(emitted_families),'emitted families')
    return report


if __name__ == "__main__":
    build()
