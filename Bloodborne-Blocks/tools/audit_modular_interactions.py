#!/usr/bin/env python3
"""Read-only audit of live legacy door/gate interaction footprints in ether-v2b."""

from __future__ import annotations

import argparse
import collections
import json
from pathlib import Path

import numpy as np

from build_modular_palette import RES, ROOT, statekey
from convert_modular_world import unpack_fast
from relocate_modular_conflicts import AIR_NAMES, HELPER, NS, WorldEditor, props_of, unpack_block_pos
from world_io import RegionFile, Tag, TAG_COMPOUND, TAG_LIST, TAG_LONG, TAG_STRING, block_state_key, compound, read_nbt


KINDS = {"door", "model_door", "trapdoor", "gate"}
REPLACEABLE_NAMES = {
    "minecraft:water", "minecraft:lava", "minecraft:fire", "minecraft:soul_fire", "minecraft:snow",
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:grass", "minecraft:fern", "minecraft:large_fern",
    "minecraft:dead_bush", "minecraft:vine", "minecraft:glow_lichen", "minecraft:seagrass", "minecraft:tall_seagrass",
}
REPLACEABLE_SUFFIXES = ("_flower", "_sapling", "_roots", "_fungus", "_mushroom")


def add(first, second):return tuple(first[i] + second[i] for i in range(3))


def geometry_state(geometry, ident, values):
    key = statekey(values);raw = geometry["blocks"][ident]["states"].get(key)
    if raw is None:raise KeyError(f"missing geometry {ident}[{key}]")
    return geometry.get("profiles", {}).get(raw.get("ref"), raw), key


def offsets(state):return {tuple(map(int, cell.split(","))) for cell in state.get("cells", {})}


def reserved_door_sibling(definition, values, offset):
    if definition["kind"] != "door" or values.get("half") not in {"lower", "upper"}:return False
    return offset == (0, 1 if values["half"] == "lower" else -1, 0)


def helper_entity(world, position):
    return [entity for entity in world.entities_at(position)
            if compound(entity).get("id", Tag(TAG_STRING, "")).value == HELPER]


def helper_root(entity):
    root = compound(entity).get("Root")
    return unpack_block_pos(int(root.value)) if root is not None and root.type == TAG_LONG else None


def helper_owner(entity):
    owner = compound(entity).get("Owner");return owner.value if owner is not None and owner.type == TAG_STRING else None


def is_replaceable(entry):
    if entry is None:return True
    name = compound(entry)["Name"].value
    return name in AIR_NAMES or name in REPLACEABLE_NAMES or name.endswith(REPLACEABLE_SUFFIXES)


def chunk_loaded(world, position):return world.chunk(position) is not None


def state_or_air(world, position):
    state = world.state(position)
    return state if state is not None else Tag(TAG_COMPOUND, {"Name": Tag(TAG_STRING, "minecraft:air")})


def current_helper_issues(world, root, ident, definition, values, authored):
    issues = []
    for offset in sorted(offsets(authored)):
        if offset == (0, 0, 0) or reserved_door_sibling(definition, values, offset):continue
        position = add(root, offset);entry = world.state(position)
        if entry is None:
            issues.append((position, "missing_chunk_or_section", None));continue
        name = compound(entry)["Name"].value
        if name != HELPER:
            issues.append((position, "missing_helper_block", block_state_key(entry)));continue
        entities = helper_entity(world, position)
        if len(entities) != 1:
            issues.append((position, "missing_or_duplicate_helper_entity", name));continue
        if helper_root(entities[0]) != root:
            issues.append((position, "helper_root_mismatch", str(helper_root(entities[0]))));continue
        expected_owner = NS + ident
        if helper_owner(entities[0]) != expected_owner:
            issues.append((position, "helper_owner_mismatch", helper_owner(entities[0])))
    return issues


def conflict(world, root, owned_root, ident, definition, values, authored, bounds):
    blockers = []
    for offset in sorted(offsets(authored)):
        if offset == (0, 0, 0) or reserved_door_sibling(definition, values, offset):continue
        target = add(root, offset)
        if target == owned_root:continue
        if not chunk_loaded(world, target):
            blockers.append((target, "unloaded_chunk", "<unloaded_chunk>"));continue
        min_y, max_y, min_x, max_x, min_z, max_z = bounds
        if target[1] < min_y or target[1] >= max_y:
            blockers.append((target, "outside_build_limit", "<outside_build_limit>"));continue
        if target[0] < min_x or target[0] >= max_x or target[2] < min_z or target[2] >= max_z:
            blockers.append((target, "outside_world_border", "<outside_world_border>"));continue
        there = state_or_air(world, target)
        if is_replaceable(there):continue
        name = compound(there)["Name"].value
        if name == HELPER:
            entities = helper_entity(world, target)
            if len(entities) == 1 and helper_root(entities[0]) == owned_root:continue
        blockers.append((target, "occupied", block_state_key(there)))
    return blockers


def chunks_loaded(world, root, authored):
    return chunk_loaded(world, root) and all(chunk_loaded(world, add(root, offset)) for offset in offsets(authored))


def sibling_position(root, definition, values):
    if definition["kind"] != "door" or values.get("half") not in {"lower", "upper"}:return None
    return add(root, (0, 1 if values["half"] == "lower" else -1, 0))


def scan_roots(target, interactive):
    wanted = {NS + ident: ident for ident in interactive}
    for path in sorted((target / "region").glob("r.*.*.mca")):
        region = RegionFile.open(path)
        for stored in region.chunks():
            chunk = compound(stored.nbt().root);cx, cz = int(chunk["xPos"].value), int(chunk["zPos"].value)
            for section in chunk.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                data = compound(section)
                if "block_states" not in data:continue
                states = compound(data["block_states"]);palette = states["palette"].value
                indices = {index: wanted.get(compound(entry)["Name"].value) for index, entry in enumerate(palette)}
                indices = {index: ident for index, ident in indices.items() if ident is not None}
                if not indices:continue
                array = unpack_fast(states);sy = int(data["Y"].value);matches = np.isin(array, list(indices))
                for raw_index in np.flatnonzero(matches):
                    index = int(raw_index);entry = palette[int(array[index])]
                    pos = (cx * 16 + (index & 15), sy * 16 + (index >> 8), cz * 16 + ((index >> 4) & 15))
                    yield pos, indices[int(array[index])], entry


def world_limits(target):
    data = compound(compound(read_nbt(target / "level.dat").root)["Data"])
    # ether-v2b is the ordinary 1.20.1 overworld; these are its dimension bounds.
    center_x = float(data.get("BorderCenterX", Tag(6, 0.0)).value);center_z = float(data.get("BorderCenterZ", Tag(6, 0.0)).value)
    size = float(data.get("BorderSize", Tag(6, 59999968.0)).value);half = size / 2.0
    return (-64, 320, int(np.floor(center_x - half)), int(np.ceil(center_x + half)),
            int(np.floor(center_z - half)), int(np.ceil(center_z + half)))


def blocker_id(state):
    if state.startswith("<"):return state
    return state.split("[", 1)[0]


def audit(target, final_report, output, example_limit=200, blocker_limit=50):
    target = Path(target).resolve();final_report = Path(final_report).resolve();output = Path(output).resolve()
    if not target.is_dir() or not (target / "level.dat").is_file():raise ValueError("target must be an existing world copy")
    completed = json.loads(final_report.read_text(encoding="utf-8"))
    if not completed.get("applied"):raise ValueError("final relocation report is not applied")
    reported_target = completed.get("target")
    if reported_target and Path(reported_target).resolve() != target:raise ValueError("final relocation report targets a different world")
    definitions = {row["id"]: row for row in json.loads((RES / "bloodborne_blocks/definitions.json").read_text(encoding="utf-8-sig"))["blocks"]}
    interactive = {ident: row for ident, row in definitions.items() if row.get("kind") in KINDS and "open" in row.get("properties", {})}
    geometry = json.loads((RES / "bloodborne_blocks/geometry.json").read_text(encoding="utf-8-sig"));world = WorldEditor(target)
    bounds = world_limits(target);min_y, max_y = bounds[:2];counts = collections.Counter();kinds = collections.Counter();blockers = collections.Counter();issues = []
    for root, ident, entry in scan_roots(target, interactive):
        definition = interactive[ident];values = {**definition["default"], **props_of(entry)}
        if definition["kind"] == "model_door" and values.get("shape") != "straight":
            counts["excludedModelDoorNonStraight"] += 1;continue
        counts["roots"] += 1;kinds[definition["kind"]] += 1;action = "close" if values.get("open") == "true" else "open";counts[action + "Checks"] += 1
        problem = {"position": list(root), "id": ident, "state": block_state_key(entry), "kind": definition["kind"], "action": action}
        try:current, _ = geometry_state(geometry, ident, values)
        except KeyError as error:
            problem["geometryError"] = str(error);counts["geometryErrors"] += 1;issues.append(problem);continue
        helper_issues = current_helper_issues(world, root, ident, definition, values, current)
        if helper_issues:
            counts["rootsWithHelperIssues"] += 1
            for _, reason, _ in helper_issues:blockers["<" + reason + ">"] += 1
            problem["helperIssues"] = [{"position": list(pos), "reason": reason, "actual": detail} for pos, reason, detail in helper_issues]
        next_values = dict(values);next_values["open"] = "false" if values.get("open") == "true" else "true"
        try:next_geometry, _ = geometry_state(geometry, ident, next_values)
        except KeyError as error:
            problem["geometryError"] = str(error);counts["geometryErrors"] += 1;issues.append(problem);continue
        transition = []
        if not chunks_loaded(world, root, current):transition.append((root, "current_footprint_unloaded", "<unloaded_chunk>"))
        transition.extend(conflict(world, root, root, ident, definition, next_values, next_geometry, bounds))
        sibling = sibling_position(root, definition, values)
        if sibling is not None:
            sibling_entry = world.state(sibling);expected_half = "upper" if values["half"] == "lower" else "lower"
            if sibling_entry is None or compound(sibling_entry)["Name"].value != NS + ident or props_of(sibling_entry).get("half") != expected_half:
                actual = None if sibling_entry is None else block_state_key(sibling_entry)
                problem["doorSiblingIssue"] = {"position": list(sibling), "expectedHalf": expected_half, "actual": actual};counts["doorSiblingIssues"] += 1
            else:
                sibling_values = {**definition["default"], **props_of(sibling_entry)};sibling_values["open"] = next_values["open"]
                sibling_current, _ = geometry_state(geometry, ident, {**definition["default"], **props_of(sibling_entry)})
                sibling_next, _ = geometry_state(geometry, ident, sibling_values)
                if not chunks_loaded(world, sibling, sibling_current):transition.append((sibling, "sibling_current_footprint_unloaded", "<unloaded_chunk>"))
                transition.extend(conflict(world, sibling, sibling, ident, definition, sibling_values, sibling_next, bounds))
        if transition:
            counts["blockedTransitions"] += 1
            for _, reason, state in transition:blockers[blocker_id(state) if reason == "occupied" else "<" + reason + ">"] += 1
            problem["transitionBlockers"] = [{"position": list(pos), "reason": reason, "state": state} for pos, reason, state in transition]
        if not helper_issues and not transition and "doorSiblingIssue" not in problem:
            counts["workingRoots"] += 1
        else:issues.append(problem)
    # Independent second pass over every retained legacy state whose authored
    # geometry occupies more than its root cell. Active doors were checked above;
    # the remaining roots still require a complete current helper footprint.
    for root, ident, entry in scan_roots(target, definitions):
        definition = definitions[ident];values = {**definition["default"], **props_of(entry)}
        try:authored, _ = geometry_state(geometry, ident, values)
        except KeyError as error:
            counts["retainedGeometryErrors"] += 1;issues.append({"position": list(root), "id": ident, "state": block_state_key(entry), "check": "retainedHelpers", "geometryError": str(error)});continue
        if len(offsets(authored) | {(0, 0, 0)}) <= 1:continue
        counts["retainedMultiCellRoots"] += 1
        if active_door := (definition.get("kind") in KINDS and "open" in definition.get("properties", {}) and not (definition["kind"] == "model_door" and values.get("shape") != "straight")):
            continue
        helper_issues = current_helper_issues(world, root, ident, definition, values, authored)
        if helper_issues:
            counts["retainedRootsWithHelperIssues"] += 1
            for _, reason, _ in helper_issues:blockers["<" + reason + ">"] += 1
            issues.append({"position": list(root), "id": ident, "state": block_state_key(entry), "kind": definition.get("kind"), "check": "retainedHelpers",
                           "helperIssues": [{"position": list(pos), "reason": reason, "actual": detail} for pos, reason, detail in helper_issues]})
    result = {"format": "bloodborne-modular-interactions-audit-v2", "readOnly": True, "target": str(target),
              "finalRelocationReport": str(final_report), "aggregate": dict(sorted(counts.items())), "rootsByKind": dict(sorted(kinds.items())),
              "blockerCounts": [{"id": key, "count": value} for key, value in blockers.most_common(blocker_limit)],
              "blockerIdsTruncated": max(0, len(blockers) - blocker_limit),
              "issueCount": len(issues), "issues": issues[:example_limit], "issuesTruncated": max(0, len(issues) - example_limit),
              "rules": {"kinds": sorted(KINDS), "modelDoorNonStraightExcluded": True, "ownedHelpersAllowed": True,
                        "reservedTraditionalDoorSibling": True, "buildLimits": [min_y, max_y], "worldBorder": list(bounds[2:]),
                        "exampleLimit": example_limit, "blockerLimit": blocker_limit}}
    output.parent.mkdir(parents=True, exist_ok=True);output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument("target", type=Path)
    parser.add_argument("--final-report", type=Path, default=ROOT / "docs/world-relocation-final-v2.json")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/world-interactions-v2.json")
    parser.add_argument("--example-limit", type=int, default=200);parser.add_argument("--blocker-limit", type=int, default=50);args = parser.parse_args()
    if args.example_limit < 0 or args.blocker_limit < 0:parser.error("limits must be non-negative")
    result = audit(args.target, args.final_report, args.output, args.example_limit, args.blocker_limit);print(json.dumps(result["aggregate"], ensure_ascii=False))


if __name__ == "__main__":main()
