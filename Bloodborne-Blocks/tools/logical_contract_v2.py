"""Small authored schema-v2 adapter. No model elements participate in gameplay.

Java LogicalTransform and this module execute the same matrix contract/vectors.
Cells rotate around the master cell; points rotate around the explicit pivot.
Already oriented mesh/state data must never be rotated for a second time.
"""
from __future__ import annotations

import itertools
import json
import math
from pathlib import Path

BUDGETS = {"NONE": 0, "SIMPLE_BOX": 1, "TWO_BOX": 2, "THREE_BOX": 3, "TRUNK": 2,
           "POST": 1, "FENCE": 5, "WALL": 5, "DOOR": 2, "GATE": 2, "STAIRS": 3,
           "DECK": 9}
COLLISION_EPSILON = 1e-6
MATRICES = {"0": [[1,0,0],[0,1,0],[0,0,1]], "90": [[0,0,-1],[0,1,0],[1,0,0]],
            "180": [[-1,0,0],[0,1,0],[0,0,-1]], "270": [[0,0,1],[0,1,0],[-1,0,0]]}
POC_FAMILIES = {"o_dead_tree_planter", "o_cases_0", "o_wall_deco_1", "o_iron_gate", "o_iron_railing"}


def cell(value):
    if not isinstance(value, (list, tuple)) or len(value) != 3 or any(type(v) is not int or abs(v) > 64 for v in value):
        raise ValueError("expected bounded integer cell")
    return tuple(value)


def rotate_cell(value, rotation, transform):
    matrix = transform["rotations"][str(rotation)]
    return tuple(sum(row[i] * value[i] for i in range(3)) for row in matrix)


def master_origin(placement_cell, anchor_cell, rotation, transform):
    offset = rotate_cell(anchor_cell, rotation, transform)
    return tuple(placement_cell[i] - offset[i] for i in range(3))


def rotate_box(box, rotation, transform, pivot=(.5, 0, .5)):
    points = [tuple(rotate_cell(tuple(p[i]-pivot[i] for i in range(3)), rotation, transform)[j]+pivot[j]
                    for j in range(3)) for p in itertools.product(*zip(box[:3], box[3:]))]
    return [min(p[i] for p in points) for i in range(3)] + [max(p[i] for p in points) for i in range(3)]


def box_cells(box):
    return set(itertools.product(*(range(math.floor(box[i]+COLLISION_EPSILON),
                                         math.ceil(box[i+3]-COLLISION_EPSILON)) for i in range(3))))


def check_box(box):
    if not isinstance(box, list) or len(box) != 6 or any(type(n) not in (int, float) or not math.isfinite(n) or abs(n)>64 for n in box):
        raise ValueError("invalid bounded box")
    if any(box[i] >= box[i+3] for i in range(3)):
        raise ValueError("empty gameplay box")


def load_transform(path):
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if value.get("schemaVersion") != 2 or value.get("rotations") != MATRICES or len(value.get("vectors", [])) < 4:
        raise ValueError("invalid transform contract")
    for vector in value["vectors"]:
        if master_origin(vector["placement_cell"], cell(vector["anchor_cell"]), vector["rotation"], value) != tuple(vector["expected_master"]):
            raise ValueError("transform vector mismatch")
    return value


def load_contracts(resources):
    resources = Path(resources)
    data = json.loads((resources / "contracts-v2.json").read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 2 or data.get("transform_contract") != "transform-v2.json":
        raise ValueError("invalid logical contract schema")
    transform = load_transform(resources / data["transform_contract"])
    production_path = resources / 'production-palette.json'
    if production_path.exists():
        production_ids = {o['id'] for o in json.loads(production_path.read_text(encoding='utf8'))['objects']}
        if production_ids != {f['id'] for f in data['families']}:
            raise ValueError('production manifest/contract mismatch')
    elif not POC_FAMILIES <= {f["id"] for f in data["families"]}:
        raise ValueError("schema-v2 must preserve the approved POC families")
    definitions = {d["id"]: d for d in json.loads((resources / "definitions.json").read_text(encoding="utf-8"))["blocks"]}
    physical_path = resources / 'physical-footprints.json'
    physical = json.loads(physical_path.read_text(encoding='utf-8')) if physical_path.exists() else None
    if physical is not None and (physical.get('schemaVersion') != 1 or
            set(physical.get('families', {})) != {f['id'] for f in data['families']}):
        raise ValueError('physical footprint family coverage')
    seen = set()
    split_patterns = {}
    for family in data["families"]:
        ident = family["id"]
        if ident in seen or ident not in definitions or not definitions[ident].get("logical"):
            raise ValueError("duplicate/unknown family")
        seen.add(ident)
        if not isinstance(family.get("migration_disabled", False), bool):
            raise ValueError("migration_disabled must be boolean")
        if family.get('authority') not in (None,'user') or bool(family.get('review_id')) != bool(family.get('authority')):
            raise ValueError('invalid authoritative review metadata')
        cell(family["canonical_anchor"]["cell"])
        if family["canonical_anchor"]["pivot"] != [.5,0,.5] or family["rotations"] != [0,90,180,270] or family["mirror_policy"] != "ROTATE_ONLY":
            raise ValueError("unsupported transform policy")
        if family["placement_policy"] not in ("FLOOR", "WALL_ADJACENT"):
            raise ValueError("unsupported placement policy")
        policy = family["collision_policy"]
        budget = BUDGETS[policy]
        if policy in ("TRUNK", "STAIRS", "TWO_BOX", "THREE_BOX", "DECK") and not family.get("collision_justification"):
            raise ValueError("multiple primitive policy needs justification")
        if set(family["states"]) != set(definitions[ident]["states"]):
            raise ValueError("contract must describe all existing family states")
        if physical is not None and set(physical['families'][ident]) != set(family['states']):
            raise ValueError('physical footprint state coverage')
        for key, state in family["states"].items():
            mask = physical['families'][ident][key] if physical is not None else {
                'cells': state['interaction_footprint']['cells'], 'boxes': state['collision_footprint']['boxes']}
            physical_cells = [cell(c) for c in mask['cells']]
            if len(physical_cells) != len(set(physical_cells)) or (0,0,0) not in physical_cells or len(physical_cells)>512:
                raise ValueError('invalid physical footprint')
            if len(mask['boxes']) > budget:
                raise ValueError('physical collision budget exceeded')
            for box in mask['boxes']:
                check_box(box)
                if not box_cells(box) <= set(physical_cells):
                    raise ValueError('COLLISION_OUTSIDE_PHYSICAL_FOOTPRINT')
            if family['placement_policy']=='FLOOR' and any(c[1]<0 for c in physical_cells):
                raise ValueError('physical helper below floor anchor')
            state['physical_footprint'] = mask
            if state["rotation"] not in family["rotations"] or state["render_mesh"]["id"] != definitions[ident]["models"][key]:
                raise ValueError("state/mesh mismatch")
            render = state["render_mesh"]
            if len(render["bounds"]) != 6 or len(render["offset"]) != 3 or any(not math.isfinite(n) or abs(n)>64 for n in render["bounds"]+render["offset"]):
                raise ValueError("invalid render metadata")
            selection, collision = state["selection_footprint"]["boxes"], state["collision_footprint"]["boxes"]
            if len(selection) != 1 or len(collision) > budget:
                raise ValueError("primitive budget exceeded")
            for box in selection+collision:
                check_box(box)
            cells = [cell(c) for c in state["interaction_footprint"]["cells"]]
            if len(cells) != len(set(cells)) or (0,0,0) not in cells or len(cells)>512:
                raise ValueError("invalid explicit interaction footprint")
            if family['placement_policy']=='FLOOR':
                if any(c[1]<0 for c in cells):
                    raise ValueError('GROUND_OBJECT_HAS_HELPER_BELOW_ANCHOR: '+ident)
                if render['bounds'][1]+render['offset'][1] < -1e-6:
                    raise ValueError('RENDER_BELOW_SUPPORT_PLANE: '+ident)
            if any(not box_cells(b) <= set(cells) for b in collision):
                raise ValueError("COLLISION_OUTSIDE_OWNED_CELLS: "+ident+"["+key+"]")
            for pattern in state["migration_source_pattern"]:
                components = pattern["components"]
                positions = [cell(c["offset"]) for c in components]
                if not components or len(positions) != len(set(positions)):
                    raise ValueError("empty or overlapping source pattern")
                for component in components:
                    if not component["id"].startswith("minecraft:") or not isinstance(component["properties"], dict):
                        raise ValueError("direct pattern must describe raw minecraft states")
                for guard in pattern.get('variant_guards',[]):
                    if cell(guard['offset']) not in positions or type(guard.get('multipart')) is not bool:
                        raise ValueError('RNG guard must reference a matched source cell')
                    weights,indices=guard['weights'],guard['indices']
                    if not weights or any(type(w) is not int or w<=0 for w in weights) or sum(weights)>2147483647:
                        raise ValueError('invalid RNG weights')
                    if not indices or len(set(indices))!=len(indices) or any(type(i) is not int or not 0<=i<len(weights) for i in indices):
                        raise ValueError('invalid RNG choices')
                transaction = pattern.get("split_transaction")
                if transaction is not None:
                    if (not isinstance(transaction, dict) or set(transaction) != {"id", "outputs"} or
                            not isinstance(transaction["id"], str) or not transaction["id"] or len(transaction["id"]) > 128 or
                            not isinstance(transaction["outputs"], list) or len(transaction["outputs"]) < 2 or
                            any(not isinstance(output, dict) or not {"family", "root_offset"} <= set(output) or set(output)-{"family", "root_offset", "properties"} or
                                not isinstance(output["family"], str) or not output["family"] for output in transaction["outputs"])):
                        raise ValueError("invalid split transaction")
                    outputs = tuple((output["family"], cell(output["root_offset"]), tuple(sorted(output.get('properties',{}).items()))) for output in transaction["outputs"])
                    for output in transaction['outputs']:
                        target_def=definitions.get(output['family'])
                        override=output.get('properties',{})
                        if not target_def or any(k not in target_def['properties'] or v not in target_def['properties'][k] for k,v in override.items()):
                            raise ValueError('invalid split output state override')
                    # One rendered family may legitimately be placed more than once
                    # (for example two identical statues at different roots).  The
                    # declaration identity is therefore family + root, while the
                    # shared raw pattern still has exactly one member per family.
                    if len(set(outputs)) != len(outputs) or ident not in {output[0] for output in outputs}:
                        raise ValueError("split transaction outputs must have unique family/root pairs and include family")
                    signature = (tuple(sorted((cell(component["offset"]), component["id"],
                                               tuple(sorted(component["properties"].items()))) for component in components)),
                                 json.dumps(pattern.get("variant_guards", []), sort_keys=True, separators=(",", ":")))
                    split_patterns.setdefault((transaction["id"], signature), []).append((ident, outputs))
    known = {family["id"] for family in data["families"]}
    for (_, _), members in split_patterns.items():
        declared = members[0][1]
        declared_ids = {output[0] for output in declared}
        member_ids = [ident for ident, _ in members]
        if (any(outputs != declared for _, outputs in members) or
                len(member_ids) != len(declared_ids) or len(set(member_ids)) != len(member_ids) or
                declared_ids != set(member_ids) or declared_ids - known):
            raise ValueError("split transaction must declare exactly one shared source pattern per output")
    return data, transform


def direct_rules(resources, *, poc_only=False):
    """Compile only explicit vanilla patterns into the existing safe transaction engine."""
    from convert_logical_world import Expected, Output, Rule, add, load_defaults, make_state
    data, transform = load_contracts(resources)
    defaults = load_defaults(resources)
    rules = []
    split = {}
    for family in data["families"]:
        if poc_only and family['id'] not in POC_FAMILIES:
            continue
        if family.get("migration_disabled", False) and not poc_only:
            continue
        # QA2 explicitly retires the incomplete tree construction patterns.
        # Keep frozen POC mode for historical regression fixtures only. In the
        # current converter an incomplete tree must never fall back to a wing
        # or a trunk-only object when its full sixteen-cell pattern is absent.
        if not poc_only and any(f['id'] == 'o_c001' for f in data['families']) and family['id'] in {
                'o_c001_a', 'o_c001_b', 'o_c009_a', 'o_c009_b', 'o_dead_tree_planter'}:
            continue
        for key, state in family["states"].items():
            properties = dict(part.split("=", 1) for part in key.split(",") if part)
            target = make_state({"id": family["id"], "properties": properties}, defaults)
            for pattern in state["migration_source_pattern"]:
                # Source offsets are already rotated and relative to the canonical master.
                # The seed need not be at the master; no min-cell heuristics are used.
                pieces = [Expected(cell(c["offset"]), make_state(c, {})) for c in pattern["components"]]
                first = pieces[0]
                # candidates subtracts first.offset to obtain master. Evaluate the same
                # explicit-anchor transform used by item placement (zero relative shift).
                anchor = rotate_cell(family["canonical_anchor"]["cell"], state["rotation"], transform)
                shift = master_origin(anchor, family["canonical_anchor"]["cell"], state["rotation"], transform)
                shape = frozenset(cell(c) for c in state["physical_footprint"]["cells"])
                transaction = pattern.get("split_transaction")
                if transaction is None:
                    rules.append(Rule(len(rules), first, target, shift, tuple(pieces[1:]), None, shape,
                                      supersedes_targets=(frozenset('bloodborne_blocks:'+f['id'] for f in data['families'] if f['id']!=family['id']) if family.get('authority')=='user' else frozenset('bloodborne_blocks:'+i for i in family.get('supersedes_targets',[]))),
                                      variant_guards=tuple(pattern.get('variant_guards', []))))
                    continue
                signature = (tuple(sorted((piece.offset, piece.state) for piece in pieces)),
                             json.dumps(pattern.get("variant_guards", []), sort_keys=True, separators=(",", ":")))
                split.setdefault((transaction["id"], signature), []).append((family["id"], target, shift, shape, pieces, tuple(pattern.get("variant_guards", [])),
                                                                               tuple((output["family"], cell(output["root_offset"]), tuple(sorted(output.get('properties',{}).items()))) for output in transaction["outputs"])))
    for (transaction_id, _), members in split.items():
        members.sort(key=lambda member: member[0])
        first_family, target, shift, shape, pieces, guards, declarations = members[0]
        by_family = {member[0]: member for member in members}
        declared_families = {family for family, _, _ in declarations}
        member_families = [member[0] for member in members]
        if len(by_family) != len(member_families):
            raise ValueError("split transaction must declare exactly one shared source pattern per output")
        if declared_families != set(by_family):
            # A disabled historical composite makes its declared split incomplete.
            # Do not silently degrade to the remaining outputs.
            continue
        outputs_list=[]
        family_map={f['id']:f for f in data['families']}
        for family,root_offset,override in declarations:
            base=by_family[family]
            props={**dict(base[1][1]),**dict(override)}
            target_state=make_state({'id':family,'properties':props},defaults)
            state_key=','.join(f'{k}={v}' for k,v in sorted(props.items()))
            target_shape=frozenset(cell(c) for c in family_map[family]['states'][state_key]['physical_footprint']['cells'])
            outputs_list.append(Output(target_state,add(base[2],root_offset),target_shape))
        outputs=tuple(outputs_list)
        occupied = set()
        for output in outputs:
            cells = {tuple(output.root_offset[i] + offset[i] for i in range(3)) for offset in output.shape}
            if occupied & cells:
                raise ValueError("split transaction output footprints overlap")
            occupied.update(cells)
        rules.append(Rule(len(rules), pieces[0], target, shift, tuple(pieces[1:]), None, shape,
                          variant_guards=guards, transaction_id=transaction_id, outputs=outputs))
    if not poc_only:
        # A newer user-reviewed exact raw pattern is authoritative over the old
        # POC matcher. Existing POC blocks/assets remain untouched. In particular
        # C046 is proven against 65 original polygons, not the POC's 71 polygons.
        from dataclasses import replace
        def raw_signature(rule):
            return (tuple(sorted((p.offset,p.state) for p in (rule.source,)+rule.members)),
                    json.dumps(rule.variant_guards,sort_keys=True))
        reviewed_ids={f['id'] for f in data['families'] if f.get('authority')=='user'}
        reviewed={raw_signature(r) for r in rules if r.target[0].split(':',1)[1] in reviewed_ids}
        rules=[r for r in rules if r.target[0].split(':',1)[1] not in POC_FAMILIES-reviewed_ids or raw_signature(r) not in reviewed]
        rules=[replace(rule,number=i) for i,rule in enumerate(rules)]
    return rules, defaults
