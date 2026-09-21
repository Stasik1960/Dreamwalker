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

BUDGETS = {"NONE": 0, "SIMPLE_BOX": 1, "TWO_BOX": 2, "TRUNK": 2,
           "POST": 1, "FENCE": 5, "WALL": 5, "DOOR": 2, "GATE": 2, "STAIRS": 3}
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
    return set(itertools.product(*(range(math.floor(box[i]), math.ceil(box[i+3])) for i in range(3))))


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
    if {f["id"] for f in data["families"]} != POC_FAMILIES:
        raise ValueError("schema-v2 POC is restricted to the approved five families")
    definitions = {d["id"]: d for d in json.loads((resources / "definitions.json").read_text(encoding="utf-8"))["blocks"]}
    seen = set()
    for family in data["families"]:
        ident = family["id"]
        if ident in seen or ident not in definitions or not definitions[ident].get("logical"):
            raise ValueError("duplicate/unknown family")
        seen.add(ident)
        cell(family["canonical_anchor"]["cell"])
        if family["canonical_anchor"]["pivot"] != [.5,0,.5] or family["rotations"] != [0,90,180,270] or family["mirror_policy"] != "ROTATE_ONLY":
            raise ValueError("unsupported transform policy")
        if family["placement_policy"] not in ("FLOOR", "WALL_ADJACENT"):
            raise ValueError("unsupported placement policy")
        policy = family["collision_policy"]
        budget = BUDGETS[policy]
        if policy in ("TRUNK", "STAIRS", "TWO_BOX") and not family.get("collision_justification"):
            raise ValueError("multiple primitive policy needs justification")
        if set(family["states"]) != set(definitions[ident]["states"]):
            raise ValueError("contract must describe all existing family states")
        for key, state in family["states"].items():
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
            if any(not box_cells(b) <= set(cells) for b in collision):
                raise ValueError("collision outside explicit interaction footprint")
            for pattern in state["migration_source_pattern"]:
                components = pattern["components"]
                positions = [cell(c["offset"]) for c in components]
                if not components or len(positions) != len(set(positions)):
                    raise ValueError("empty or overlapping source pattern")
                for component in components:
                    if not component["id"].startswith("minecraft:") or not isinstance(component["properties"], dict):
                        raise ValueError("direct pattern must describe raw minecraft states")
    return data, transform


def direct_rules(resources):
    """Compile only explicit vanilla patterns into the existing safe transaction engine."""
    from convert_logical_world import Expected, Rule, load_defaults, make_state
    data, transform = load_contracts(resources)
    defaults = load_defaults(resources)
    rules = []
    for family in data["families"]:
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
                rules.append(Rule(len(rules), first, target, shift, tuple(pieces[1:]), None,
                                  frozenset(cell(c) for c in state["interaction_footprint"]["cells"])))
    return rules, defaults
