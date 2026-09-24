"""Compile the retired functional families into Contract V2 input.

This module is deliberately pure: callers pass the decompressed frozen
authoring input and decide where the returned definitions, meshes and contract
are written.  It neither scans a world nor performs a conversion itself.
"""
from __future__ import annotations

import copy
import hashlib
import json
import math
from itertools import product
from typing import Any, Mapping


FUNCTIONAL_IDS = (
    "o_acacia_door", "o_birch_door", "o_dark_oak_door", "o_shuttered_window",
    "o_stone_railing", "o_ornate_balustrade", "o_carved_balustrade",
    "o_stepped_balustrade", "o_high_balustrade", "o_stone_curb",
    "o_ladder_01", "o_ladder_02", "o_ladder_03", "o_candles_0",
    "o_lanterns", "o_wall_lantern", "o_lantern", "o_lightning_rod",
    "o_oak_wood", "o_bench", "o_bench_rotate",
)
DOORS = {"o_acacia_door": 2, "o_birch_door": 3, "o_dark_oak_door": 2}
CONNECTED = {"o_stone_railing", "o_ornate_balustrade", "o_carved_balustrade",
             "o_stepped_balustrade", "o_high_balustrade", "o_stone_curb"}
LADDERS = {"o_ladder_01", "o_ladder_02", "o_ladder_03"}
LANTERNS = {"o_candles_0", "o_lanterns", "o_wall_lantern", "o_lantern",
            "o_lightning_rod", "o_oak_wood"}
BENCHES = {"o_bench", "o_bench_rotate"}
FACINGS = ("north", "east", "south", "west")
# These are the original pack application yaws, not an inferred direction map.
OLD_APP_YAW = {"north": 180, "east": 270, "south": 0, "west": 90}


def _props(key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in key.split(",") if part)


def _bounds(polygons: list[dict[str, Any]]) -> list[float]:
    vertices = [vertex for polygon in polygons for vertex in polygon["vertices"]]
    if not vertices:
        raise ValueError("functional mesh has no vertices")
    return [min(float(v[axis]) for v in vertices) for axis in range(3)] + [
        max(float(v[axis]) for v in vertices) for axis in range(3)]


def _mesh_id(ident: str, state: str, polygons: list[dict[str, Any]]) -> str:
    payload = json.dumps(polygons, sort_keys=True, separators=(",", ":"))
    return "functional_v2_" + hashlib.sha256((ident + "\0" + state + "\0" + payload).encode()).hexdigest()[:20]


def _raw_component(name: str, offset: tuple[int, int, int] = (0, 0, 0), **properties: str) -> dict[str, Any]:
    """A direct source rule contains only properties that vanilla exposes."""
    return {"id": "minecraft:" + name, "properties": properties, "offset": list(offset)}


def _rotate_box(box: list[float], yaw: int) -> list[float]:
    """Rotate an authored primitive about the Contract V2 half-cell pivot."""
    box=[round(v,5) for v in box]
    turns = (yaw // 90) % 4
    points = []
    for x, y, z in product((box[0], box[3]), (box[1], box[4]), (box[2], box[5])):
        x, z = x - .5, z - .5
        for _ in range(turns): x, z = -z, x
        points.append((x + .5, y, z + .5))
    return [round(min(point[axis] for point in points),5) for axis in range(3)] + [round(max(point[axis] for point in points),5) for axis in range(3)]


def _raw_rules(frozen: Mapping[str, Any], ident: str, values: Mapping[str, str]) -> list[dict[str, Any]]:
    """Compile archived direct carrier rules, never hand-select a replacement."""
    if values.get('visual','base')!='base' or (ident in DOORS and values.get('open')!='false'):
        return []
    if ident in LANTERNS:
        original=next(b for b in frozen['definitions']['blocks'] if b['id']==ident)
        source_key=','.join(f'{k}={v}' for k,v in sorted(values.items()) if k!='lit')
        if values['lit'] != ('true' if original['states'][source_key][2]>0 else 'false'):return []
    result, seen = [], set()
    for old in frozen["migration"]["rules"]:
        target = old.get("target", {})
        if target.get("id") != ident or any(values.get(k) != v for k, v in target.get("properties", {}).items()):
            continue
        members = old.get("members") or []
        if any(str(member.get("id", "")).startswith("m_") for member in members):
            continue
        cooked = []
        for component in [old["source"], *members]:
            block_id = component["id"]
            props = dict(component.get("properties", {}))
            allowed = raw_properties(block_id)
            # Old importer invented yaw on vanilla symmetric carriers. Only
            # its canonical north representation can describe the raw source.
            if 'facing' not in allowed and props.get('facing','north')!='north':
                cooked=[];break
            props = {k:v for k,v in props.items() if k in allowed}
            cooked.append(_raw_component(block_id, tuple(component.get("offset", (0, 0, 0))), **props))
        if not cooked:continue
        signature = json.dumps(cooked, sort_keys=True)
        if signature not in seen:
            seen.add(signature); result.append({"components": cooked})
    return result


def raw_properties(ident):
    """Vanilla 1.20.1 property schemas for the explicit restoration carriers."""
    if ident.endswith('_stairs'):return {'facing','half','shape','waterlogged'}
    if ident.endswith('_wall'):return {'north','east','south','west','up','waterlogged'}
    if ident.endswith('_trapdoor'):return {'facing','half','open','powered','waterlogged'}
    if ident in ('lantern','soul_lantern'):return {'hanging','waterlogged'}
    if ident=='lightning_rod':return {'facing','powered','waterlogged'}
    if ident=='oak_wood':return {'axis'}
    if ident=='rail':return {'shape','waterlogged'}
    if ident=='dead_brain_coral':return {'waterlogged'}
    raise ValueError('unreviewed raw carrier schema '+ident)


def _legacy_simple(frozen: Mapping[str, Any], ident: str, key: str) -> tuple[list[float], list[tuple[int, int, int]]]:
    """One conservative primitive, bounded by archived collision evidence."""
    state = frozen["geometry"]["blocks"][ident]["states"][key]
    profile = frozen["geometry"]["profiles"][state["ref"]]
    boxes, cells = [], []
    for text, cell in profile["cells"].items():
        offset = tuple(int(value) for value in text.split(","))
        if cell["collision"]: cells.append(offset)
        for box in cell["collision"]:
            boxes.append([box[i] + offset[i] for i in range(3)] + [box[i + 3] + offset[i] for i in range(3)])
    if not boxes: return [0, 0, 0, 1 / 16, 1 / 16, 1 / 16], [(0, 0, 0)]
    return [min(box[i] for box in boxes) for i in range(3)] + [max(box[i + 3] for box in boxes) for i in range(3)], sorted(set(cells))


def _state_contract(frozen: Mapping[str, Any], ident: str, key: str, geometry_key: str, mesh_id: str, polygons: list[dict[str, Any]]) -> dict[str, Any]:
    values = _props(key)
    bounds = _bounds(polygons)
    wall = ident in LADDERS or ident == 'o_shuttered_window'
    lift = 0 if wall else max(0, math.ceil(-bounds[1]))
    if ident=='o_stone_curb':lift=.125
    render_offset = [0, lift, 0]
    selection=[v+(lift if i in (1,4) else 0) for i,v in enumerate(bounds)]
    envelope,_=_legacy_simple(frozen,ident,geometry_key)
    collision=[[v+(lift if i in (1,4) else 0) for i,v in enumerate(envelope)]]
    policy='SIMPLE_BOX'
    justification='Authored simple gameplay core; raw geometry profile coordinates are mesh-relative, not anchor-relative.'
    rotation=FACINGS.index(values['facing'])*90 if 'facing' in values else 0
    if ident in DOORS:
        policy='DOOR'
        # Explicit two lower leaf/jamb primitives, in north-facing local
        # source coordinates. Fixed upper headers remain render/selection only.
        ranges={'o_acacia_door':(-.0625,1.1875,.5,3),
                'o_birch_door':(-.4375,.9375,.5,3),
                'o_dark_oak_door':(-.8125,.6875,.25,3)}
        lo,hi,width,height=ranges[ident]
        if values['open']=='true':
            collision=[[-1,0,lo,-1+width,height,hi],[2-width,0,lo,2,height,hi]]
        else:
            # Closed lower panels cover the passage; header cannot become an
            # invisible fourth/fifth block-high wall when opened.
            canonical,_=_legacy_simple(frozen,ident,geometry_key.replace('facing='+values['facing'],'facing=north'))
            collision=[[-1,0,canonical[2],.5,height,canonical[5]], [.5,0,canonical[2],2,height,canonical[5]]]
        collision=[_rotate_box(b,rotation) for b in collision]
    elif ident== 'o_shuttered_window':
        policy='DOOR'
        # Separate wing envelopes; never bridge the opening with a union AABB.
        north_key=geometry_key.replace('facing='+values['facing'],'facing=north')
        canonical,_=_legacy_simple(frozen,ident,north_key)
        if values['open']=='true':
            collision=[[-.378188,-.5,-.649570,.148002,1.4375,.620765],
                       [.851998,-.5,-.649570,1.378188,1.4375,.620765]]
        else:
            collision=[[canonical[0],canonical[1],canonical[2],.5,canonical[4],canonical[5]],
                       [.5,canonical[1],canonical[2],canonical[3],canonical[4],canonical[5]]]
        collision=[_rotate_box(b,rotation) for b in collision]
    elif ident in LADDERS:
        # Climbable region is explicitly owned but traversable, as a ladder.
        policy='NONE';collision=[]
    elif ident in BENCHES:
        policy='THREE_BOX'
        # Reviewed seat and backrest cores; supports are decorative. The thin
        # seat is also the runtime seating-height authority.
        canonical=[[-.9375,.875,-.100298,1.9375,1,.774702],
                   [-.9375,.3125,-.045610,1.9375,.875,-.029985],
                   [-.9375,.947015,.726867,1.9375,2.448319,1.348728]]
        if ident=='o_bench_rotate':
            # Authored diagonal bench: conservative horizontal seat rectangle
            # still has plank thickness, never a floor-to-backrest solid cube.
            canonical=[[-.940940,.875,-.940940,1.710710,1,1.710710],
                       [-.896746,.3125,-.896746,1.136186,.875,1.136186],
                       [-.314492,.923958,-.314492,1.718440,2.548958,1.718440]]
        collision=[_rotate_box(b,rotation) for b in canonical]
    cells={(0,0,0)}
    if ident in BENCHES:
        _, sparse=_legacy_simple(frozen,ident,geometry_key)
        cells.update((x,y+lift,z) for x,y,z in sparse)
    owned=collision if collision else [selection]
    for box in owned:
        cells.update(product(*(range(math.floor(box[i]+1e-8),math.ceil(box[i+3]-1e-8)) for i in range(3))))
    patterns=_raw_rules(frozen,ident,values)
    if lift and int(lift)==lift:
        for p in patterns:
            for c in p['components']:c['offset'][1]+=lift
            p['master_from_source']=[0,-lift,0]
            p['matching_evidence']='Integer floor-root shift; source world visual position retained exactly'
    elif lift:
        for p in patterns:p['source_support_lift']=lift
    row = {
        "rotation": rotation,
        "render_mesh": {"id": mesh_id, "bounds": bounds, "offset": render_offset},
        "selection_footprint": {"boxes": [selection]},
        "collision_footprint": {"boxes": collision},
        "interaction_footprint": {"cells": [list(cell) for cell in sorted(cells)]},
        "migration_source_pattern": patterns,
    }
    return policy, justification, row


def compile_functional(frozen: Mapping[str, Any]) -> dict[str, Any]:
    """Return new definitions, Contract V2 families, re-keyed meshes and evidence.

    The legacy state mesh is used only as preserved authored artwork.  Runtime
    identity, collision, interaction cells and direct source rules are freshly
    authored here; execution of the returned migration rules is caller-owned.
    """
    blocks = {block["id"]: block for block in frozen["definitions"]["blocks"]}
    missing = set(FUNCTIONAL_IDS) - set(blocks)
    if missing:
        raise ValueError("frozen input misses functional families: " + ", ".join(sorted(missing)))
    source_meshes = frozen["meshes"]
    result_blocks: list[dict[str, Any]] = []
    families: list[dict[str, Any]] = []
    meshes: dict[str, Any] = {}
    evidence: dict[str, Any] = {"schemaVersion": 2, "scope": "21 functional family Contract V2 restoration; compiler performs no city conversion", "families": []}
    for ident in FUNCTIONAL_IDS:
        original = blocks[ident]
        block = copy.deepcopy(original)
        block["models"] = {}
        block["states"] = copy.deepcopy(original["states"])
        if ident in CONNECTED:
            block["connection_family"] = original.get("connection_family") or ident.removeprefix("o_")
        # Existing server behavior dispatches from these legacy behavior names.
        if ident in LADDERS: block["behavior"] = "ladder"
        if ident in LANTERNS: block["behavior"] = "lantern"
        if ident in BENCHES: block["behavior"] = "bench"
        model_rows = [(key, mesh_id, key) for key, mesh_id in sorted(original["models"].items())]
        if ident in LANTERNS:
            # State slot 2 is ArchitectureBlock's server luminance value.
            block["properties"]["lit"] = ["false", "true"]
            block['default']['lit']='true' if original['states'][next(iter(original['states']))][2]>0 else 'false'
            block["states"] = {}
            model_rows = []
            for source_key, mesh_id in sorted(original["models"].items()):
                for lit in ("false", "true"):
                    key = ",".join(f"{name}={value}" for name, value in sorted({**_props(source_key), "lit": lit}.items()))
                    state = copy.deepcopy(original["states"][source_key]); state[2] = 15 if lit == "true" else 0
                    block["states"][key] = state; model_rows.append((key, mesh_id, source_key))
        family = {"id": ident,
                  "canonical_anchor": {"cell": [0, 0, 0], "pivot": [.5, 0, .5]},
                  "placement_policy": "WALL_ADJACENT" if ident in LADDERS or ident == "o_shuttered_window" else "FLOOR",
                  "rotations": [0, 90, 180, 270], "mirror_policy": "ROTATE_ONLY", "states": {}}
        policies, justifications, raw = set(), set(), []
        for key, old_mesh_id, source_key in model_rows:
            if old_mesh_id not in source_meshes:
                raise ValueError("missing frozen mesh " + old_mesh_id)
            polygons = copy.deepcopy(source_meshes[old_mesh_id]["polygons"])
            values = _props(key)
            # Frozen open meshes were generated from the source's explicit
            # two-leaf hinge elements; preserving them avoids selecting faces
            # by height and accidentally tearing a vertical leaf or its frame.
            new_mesh_id = _mesh_id(ident, key, polygons)
            meshes[new_mesh_id] = {"polygons": polygons}
            block["models"][key] = new_mesh_id
            policy, justification, state = _state_contract(frozen, ident, key, source_key, new_mesh_id, polygons)
            policies.add(policy)
            if justification: justifications.add(justification)
            raw.extend(state["migration_source_pattern"])
            family["states"][key] = state
        if ident in CONNECTED and 'facing' not in block['properties']:
            # A source post/slab can have direction-dependent textured faces
            # even with a symmetric connection mask. Preserve them explicitly
            # instead of pretending the visual is rotationally symmetric.
            from build_reviewed_contracts import rotate_polygons, key as state_key
            old_states=block['states'];old_family=family['states'];old_models=block['models']
            block['properties']['facing']=list(FACINGS);block['default']['facing']='north'
            block['states']={};block['models']={};family['states']={}
            for oldkey,oldstate in old_family.items():
                values=_props(oldkey)
                for turn,facing in enumerate(FACINGS):
                    props={**values,'facing':facing}
                    for i,direction in enumerate(FACINGS):props[FACINGS[(i+turn)%4]]=values[direction]
                    newkey=state_key(props);state=copy.deepcopy(oldstate);state['rotation']=turn*90
                    polys=rotate_polygons(meshes[old_models[oldkey]]['polygons'],turn*90)
                    mid=_mesh_id(ident,newkey,polys);meshes[mid]={'polygons':polys}
                    state['render_mesh'].update(id=mid,bounds=_bounds(polys))
                    for field in ('selection_footprint','collision_footprint'):
                        state[field]['boxes']=[_rotate_box(b,turn*90) for b in oldstate[field]['boxes']]
                    from logical_contract_v2 import rotate_cell,MATRICES
                    state['interaction_footprint']['cells']=[list(rotate_cell(c,turn*90,{'rotations':MATRICES})) for c in oldstate['interaction_footprint']['cells']]
                    if turn:state['migration_source_pattern']=[]
                    block['states'][newkey]=copy.deepcopy(old_states[oldkey]);block['models'][newkey]=mid;family['states'][newkey]=state
        if len(policies) != 1: raise ValueError("inconsistent collision policy for " + ident)
        family["collision_policy"] = policies.pop()
        if justifications: family["collision_justification"] = " ".join(sorted(justifications))
        result_blocks.append(block); families.append(family)
        evidence["families"].append({"id": ident, "behavior": block.get("behavior"), "states": len(family["states"]),
                                     "mesh_recompiled": True, "migration_enabled": True,
                                     "raw_source_patterns": len(raw),
                                     "old_application_yaw": OLD_APP_YAW if "facing" in block.get("properties", {}) else {}})
    return {"blocks": result_blocks, "families": families, "meshes": meshes, "evidence": evidence}
