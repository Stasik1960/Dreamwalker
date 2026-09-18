#!/usr/bin/env python3
"""Repair legacy multi-cell helpers and clear live door movement in ether-v2b."""

from __future__ import annotations

import argparse
import copy
import functools
import hashlib
import json
from pathlib import Path

from audit_modular_interactions import KINDS, geometry_state, offsets, reserved_door_sibling, scan_roots
from build_modular_palette import RES, ROOT
from convert_modular_world import AIR, load_palette, tag_state
from relocate_modular_conflicts import (HELPER, NS, SupportClassifier, WorldEditor, add, apply_plan,
    bloodborne_group, destination_valid, discover_group, moved_entity, normalized_entity_hash, pack_block_pos,
    plain_tag, props_of, relocation_offsets, state_text, support_requirements, unpack_block_pos)
from world_io import Tag, TAG_COMPOUND, TAG_INT, TAG_LONG, TAG_STRING, block_state_key, compound, read_nbt


def entity_hash(entity):return hashlib.sha256(json.dumps(plain_tag(entity), sort_keys=True, separators=(",", ":")).encode()).hexdigest()


class Ledger:
    def __init__(self, world):self.world = world;self.states = {};self.entity_moves = [];self.entity_changes = [];self.moved = []

    def put(self, pos, state):
        old = self.world.state(pos);old_key = None if old is None else block_state_key(old);new_key = block_state_key(state)
        if old_key == new_key:return
        self.states.setdefault(pos, old_key);self.world.set_state(pos, state)

    def replace_entities(self, pos, entities, kind):
        old = self.world.remove_entities(pos);old_hashes = sorted(entity_hash(value) for value in old)
        for entity in entities:self.world.add_entity(pos, entity)
        new_hashes = sorted(entity_hash(value) for value in entities)
        if old_hashes != new_hashes:self.entity_changes.append({"position": list(pos), "kind": kind, "oldHashes": old_hashes, "newHashes": new_hashes})

    def changes(self):
        result = []
        for pos, old in self.states.items():
            current = self.world.state(pos);new = None if current is None else block_state_key(current)
            if old != new:result.append({"position": list(pos), "oldState": old, "newState": new})
        return sorted(result, key=lambda row: tuple(row["position"]))


def helper_entity(pos, root, owner):
    return Tag(TAG_COMPOUND, {"id": Tag(TAG_STRING, HELPER), "x": Tag(TAG_INT, pos[0]), "y": Tag(TAG_INT, pos[1]), "z": Tag(TAG_INT, pos[2]),
                              "Root": Tag(TAG_LONG, pack_block_pos(root)), "Owner": Tag(TAG_STRING, owner)})


def owned_helper(world, pos, root):
    state = world.state(pos)
    if state is None or compound(state)["Name"].value != HELPER:return False
    entities = [value for value in world.entities_at(pos) if compound(value).get("id", Tag(TAG_STRING, "")).value == HELPER]
    return len(entities) == 1 and "Root" in compound(entities[0]) and unpack_block_pos(int(compound(entities[0])["Root"].value)) == root


def ensure_helper(ledger, pos, root, owner):
    state = ledger.world.state(pos)
    if state is None:raise ValueError("missing helper section")
    name = compound(state)["Name"].value
    if name not in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", HELPER}:raise ValueError("occupied helper cell: " + block_state_key(state))
    if any(compound(entity).get("id", Tag(TAG_STRING, "")).value != HELPER for entity in ledger.world.entities_at(pos)):
        raise ValueError("foreign block entity in helper cell")
    ledger.put(pos, tag_state("architecture_part"));ledger.replace_entities(pos, [helper_entity(pos, root, owner)], "bind_helper")


def clear_cell(ledger, pos, reason):
    if any(compound(entity).get("id", Tag(TAG_STRING, "")).value != HELPER for entity in ledger.world.entities_at(pos)):
        raise ValueError("refusing to remove foreign block entity")
    ledger.put(pos, AIR);ledger.replace_entities(pos, [], reason)


def active_door(definition, values):
    return definition.get("kind") in KINDS and "open" in definition.get("properties", {}) and not (definition["kind"] == "model_door" and values.get("shape") != "straight")


def door_footprints(geometry, ident, definition, values):
    current, _ = geometry_state(geometry, ident, values);other = dict(values);other["open"] = "false" if values.get("open") == "true" else "true"
    toggled, _ = geometry_state(geometry, ident, other)
    current_offsets = {value for value in offsets(current) if value == (0, 0, 0) or not reserved_door_sibling(definition, values, value)}
    next_offsets = {value for value in offsets(toggled) if value == (0, 0, 0) or not reserved_door_sibling(definition, other, value)}
    return current_offsets | {(0, 0, 0)}, next_offsets | {(0, 0, 0)}


def inferred_support(group):
    cell = min(group["footprint"], key=lambda pos: (pos[1], abs(pos[0] - group["anchor"][0]) + abs(pos[2] - group["anchor"][2]), pos[2], pos[0]))
    return [(cell, (0, -1, 0), "inferred_floor")]


@functools.lru_cache(maxsize=None)
def cached_offsets(max_radius, vertical_range):return tuple(relocation_offsets(max_radius, vertical_range))


def move_group(ledger, group, classifier, forbidden, max_radius, vertical_range, clearance=None):
    group["supportRequirements"] = support_requirements(ledger.world, group, classifier) or inferred_support(group)
    check = dict(group);check["footprint"] = set(group["footprint"] if clearance is None else clearance)
    delta = next((value for value in cached_offsets(max_radius, vertical_range)
                  if destination_valid(ledger.world, check, value, forbidden, classifier)), None)
    if delta is None:return None
    affected = set(group["states"]) | {add(pos, delta) for pos in group["footprint"]}
    before = {pos: ledger.world.state(pos) for pos in affected}
    before_entities = {pos: [copy.deepcopy(entity) for entity in ledger.world.entities_at(pos)] for pos in affected}
    moved_entities = []
    for old_pos, entities in group["entities"].items():
        for entity in entities:moved_entities.append({"from": list(old_pos), "to": list(add(old_pos, delta)), "oldHash": entity_hash(entity), "newHash": entity_hash(moved_entity(entity, delta))})
    apply_plan(ledger.world, {"group": group, "delta": delta, "fills": {}})
    for pos, state in before.items():
        old_key = None if state is None else block_state_key(state);new_state = ledger.world.state(pos);new_key = None if new_state is None else block_state_key(new_state)
        if old_key != new_key:ledger.states.setdefault(pos, old_key)
        old_hashes = sorted(entity_hash(value) for value in before_entities[pos]);new_hashes = sorted(entity_hash(value) for value in ledger.world.entities_at(pos))
        if old_hashes != new_hashes:ledger.entity_changes.append({"position": list(pos), "kind": "relocate_object", "oldHashes": old_hashes, "newHashes": new_hashes})
    ledger.entity_moves.extend(moved_entities);new_anchor = add(group["anchor"], delta)
    ledger.moved.append({"kind": group["kind"], "oldAnchor": list(group["anchor"]), "newAnchor": list(new_anchor), "delta": list(delta),
                         "supportRequirements": [{"source": list(pos), "offset": list(offset), "kind": kind} for pos, offset, kind in group["supportRequirements"]]})
    return new_anchor


def blocker_root(world, pos):
    state = world.state(pos)
    if state is None:return pos
    if compound(state)["Name"].value != HELPER:return pos
    entities = [value for value in world.entities_at(pos) if compound(value).get("id", Tag(TAG_STRING, "")).value == HELPER]
    if len(entities) == 1 and "Root" in compound(entities[0]):return unpack_block_pos(int(compound(entities[0])["Root"].value))
    return None


def root_record(world, pos, definitions, geometry):
    entry = world.state(pos)
    if entry is None:return None
    name = compound(entry)["Name"].value
    if not name.startswith(NS) or name == HELPER or name.removeprefix(NS) not in definitions:return None
    ident = name.removeprefix(NS);definition = definitions[ident];values = {**definition["default"], **props_of(entry)}
    authored, _ = geometry_state(geometry, ident, values)
    return {"position": pos, "id": ident, "definition": definition, "values": values, "authored": authored, "entry": entry}


def repair(target, output, max_radius=32, vertical_range=16, apply=False):
    target = Path(target).resolve();output = Path(output).resolve()
    level = compound(compound(read_nbt(target / "level.dat").root)["Data"]);level_name = level.get("LevelName", Tag(TAG_STRING, "")).value
    if not level_name.startswith("Ether - Bloodborne 2.0") or target.name != "ether-v2b":raise ValueError("functional repair is restricted to the generated ether-v2b copy")
    definitions = {row["id"]: row for row in json.loads((RES / "bloodborne_blocks/definitions.json").read_text(encoding="utf-8-sig"))["blocks"]}
    geometry = json.loads((RES / "bloodborne_blocks/geometry.json").read_text(encoding="utf-8-sig"));palette = load_palette()
    world = WorldEditor(target);ledger = Ledger(world);classifier = SupportClassifier(palette, definitions, geometry);unresolved = []
    records = []
    print("Scanning retained legacy roots...", flush=True)
    for pos, ident, entry in scan_roots(target, definitions):
        definition = definitions[ident];values = {**definition["default"], **props_of(entry)}
        authored, _ = geometry_state(geometry, ident, values)
        if len(offsets(authored) | {(0, 0, 0)}) > 1:records.append({"position": pos, "id": ident, "definition": definition, "values": values})
    doors = [row for row in records if active_door(row["definition"], row["values"])]
    print("Found", len(records), "multi-cell roots and", len(doors), "active doors", flush=True)
    door_clearance = set();door_specs = {}
    for row in doors:
        current, toggled = door_footprints(geometry, row["id"], row["definition"], row["values"]);root = row["position"]
        door_specs[root] = current, toggled;door_clearance.update(add(root, value) for value in current | toggled)
    active_roots = set(door_specs);moved_roots = {};removed_modules = 0

    # A handful of historical decorative doors occupy another live door's root.
    # Keep the lower/original doorway and move the higher overlapping object with
    # clearance for both of its states before repairing any helpers.
    print("Separating overlapping active doors...", flush=True)
    for row in sorted(doors, key=lambda item: (item["position"][1], item["position"][2], item["position"][0]), reverse=True):
        root = row["position"];current, toggled = door_specs[root]
        current_cells = {add(root, value) for value in current};toggled_cells = {add(root, value) for value in toggled};overlapping = []
        for other in doors:
            other_root = other["position"]
            if other is row or other_root == root:continue
            other_current, other_toggled = door_specs[other_root]
            other_current_cells = {add(other_root, value) for value in other_current};other_toggled_cells = {add(other_root, value) for value in other_toggled}
            # Opening either door is checked against the other door's closed
            # helpers. Swing/swing-only overlap is harmless for individual use.
            if current_cells & (other_current_cells | other_toggled_cells) or toggled_cells & other_current_cells:overlapping.append(other_root)
        if not overlapping:continue
        try:group = bloodborne_group(world, root, definitions, geometry)
        except (KeyError, ValueError) as error:
            unresolved.append({"position": list(root), "id": row["id"], "reason": "overlapping_door_group_failed", "error": str(error)});continue
        clearance = {add(root, value) for value in current | toggled}
        new_root = move_group(ledger, group, classifier, door_clearance, max_radius, vertical_range, clearance)
        if new_root is None:
            unresolved.append({"position": list(root), "id": row["id"], "reason": "overlapping_door_no_supported_destination", "otherRoots": [list(pos) for pos in sorted(overlapping)]});continue
        moved_roots[root] = new_root;active_roots.remove(root);active_roots.add(new_root);door_specs[new_root] = current, toggled
        door_clearance.update(add(new_root, value) for value in current | toggled);row["position"] = new_root

    # Preserve every doorway. Modules are the only visible states that may be erased;
    # every other blocker is moved as a whole object with its block entities.
    print("Clearing authored door movement footprints...", flush=True)
    for door_index, row in enumerate(doors, 1):
        if door_index % 50 == 0:print("  doors", door_index, "/", len(doors), "moved", len(ledger.moved), "unresolved", len(unresolved), flush=True)
        root = row["position"];current, toggled = door_specs[root]
        for pos in sorted({add(root, value) for value in current | toggled} - {root}):
            for attempt in range(3):
                state = world.state(pos)
                if state is None:unresolved.append({"position": list(root), "id": row["id"], "reason": "door_footprint_missing_section", "cell": list(pos)});break
                name = compound(state)["Name"].value
                if name in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"} or owned_helper(world, pos, root):break
                if name.startswith(NS + "m_"):
                    try:clear_cell(ledger, pos, "remove_module_from_door_clearance");removed_modules += 1
                    except ValueError as error:unresolved.append({"position": list(root), "id": row["id"], "reason": "module_cell_has_foreign_block_entity", "cell": list(pos), "error": str(error)})
                    break
                other_root = blocker_root(world, pos)
                other_record = None if other_root is None else root_record(world, other_root, definitions, geometry)
                if name == HELPER and (other_root is None or other_record is None):
                    try:clear_cell(ledger, pos, "remove_stale_helper_from_door_clearance")
                    except ValueError as error:unresolved.append({"position": list(root), "id": row["id"], "reason": "invalid_helper_contains_foreign_block_entity", "cell": list(pos), "error": str(error)})
                    break
                if other_root is None:
                    unresolved.append({"position": list(root), "id": row["id"], "reason": "unidentified_door_blocker", "cell": list(pos), "state": block_state_key(state)});break
                if other_root in active_roots:
                    unresolved.append({"position": list(root), "id": row["id"], "reason": "overlapping_active_door", "cell": list(pos), "otherRoot": list(other_root)});break
                try:group = discover_group(world, other_root, definitions, geometry)
                except (KeyError, ValueError) as error:
                    unresolved.append({"position": list(root), "id": row["id"], "reason": "door_blocker_group_failed", "cell": list(pos), "error": str(error)});break
                new_root = move_group(ledger, group, classifier, door_clearance, max_radius, vertical_range)
                if new_root is None:
                    unresolved.append({"position": list(root), "id": row["id"], "reason": "no_supported_destination_for_door_blocker", "cell": list(pos), "blocker": group["kind"]});break
                if group["kind"].startswith("bloodborne:"):moved_roots[group["anchor"]] = new_root
            else:unresolved.append({"position": list(root), "id": row["id"], "reason": "door_blocker_did_not_clear", "cell": list(pos)})

    # Complete closed/current helpers and leave swing-only cells empty.
    for row in doors:
        root = row["position"];current, toggled = door_specs[root];owner = NS + row["id"]
        for offset in sorted(current - {(0, 0, 0)}):
            pos = add(root, offset)
            try:ensure_helper(ledger, pos, root, owner)
            except ValueError as error:unresolved.append({"position": list(root), "id": row["id"], "reason": "cannot_bind_current_door_helper", "cell": list(pos), "error": str(error)})
        for offset in sorted(toggled - current):
            pos = add(root, offset);state = world.state(pos)
            if state is not None and (compound(state)["Name"].value in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"} or owned_helper(world, pos, root)):
                try:clear_cell(ledger, pos, "clear_door_swing_cell")
                except ValueError as error:unresolved.append({"position": list(root), "id": row["id"], "reason": "door_swing_cell_has_foreign_block_entity", "cell": list(pos), "error": str(error)})
            elif state is not None:unresolved.append({"position": list(root), "id": row["id"], "reason": "door_swing_cell_still_occupied", "cell": list(pos), "state": block_state_key(state)})

    # Repair every remaining retained multi-cell root. Prefer relocation; if the
    # only overlap is generated construction, keep the root and free its footprint.
    print("Repairing retained multi-cell helpers...", flush=True)
    for record_index, initial in enumerate(records, 1):
        if record_index % 500 == 0:print("  roots", record_index, "/", len(records), "moved", len(ledger.moved), "unresolved", len(unresolved), flush=True)
        if initial["position"] in active_roots:continue
        root = moved_roots.get(initial["position"], initial["position"]);record = root_record(world, root, definitions, geometry)
        if record is None:continue
        definition, values, ident, authored = record["definition"], record["values"], record["id"], record["authored"]
        expected = {value for value in offsets(authored) if value != (0, 0, 0) and not reserved_door_sibling(definition, values, value)}
        blocked = []
        for offset in expected:
            pos = add(root, offset);state = world.state(pos)
            if state is None or (compound(state)["Name"].value not in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"} and not owned_helper(world, pos, root)):blocked.append((pos, state))
        if blocked:
            try:group = bloodborne_group(world, root, definitions, geometry)
            except (KeyError, ValueError) as error:
                unresolved.append({"position": list(root), "id": ident, "reason": "retained_group_failed", "error": str(error)});continue
            new_root = move_group(ledger, group, classifier, door_clearance, max_radius, vertical_range)
            if new_root is None:
                if not all(state is not None and compound(state)["Name"].value.startswith(NS + "m_") for _, state in blocked):
                    unresolved.append({"position": list(root), "id": ident, "reason": "retained_overlap_no_supported_destination",
                                       "blockers": [{"position": list(pos), "state": None if state is None else block_state_key(state)} for pos, state in blocked]});continue
                for pos, state in blocked:
                    try:clear_cell(ledger, pos, "remove_module_from_retained_footprint");removed_modules += 1
                    except ValueError as error:unresolved.append({"position": list(root), "id": ident, "reason": "retained_module_cell_has_foreign_block_entity", "cell": list(pos), "error": str(error)})
                new_root = root
            moved_roots[root] = new_root;root = new_root;record = root_record(world, root, definitions, geometry);authored = record["authored"]
            expected = {value for value in offsets(authored) if value != (0, 0, 0) and not reserved_door_sibling(definition, values, value)}
        for offset in sorted(expected):
            pos = add(root, offset)
            try:ensure_helper(ledger, pos, root, NS + ident)
            except ValueError as error:unresolved.append({"position": list(root), "id": ident, "reason": "cannot_bind_retained_helper", "cell": list(pos), "error": str(error)})

    if unresolved:apply = False
    if apply:
        regions, chunks = world.commit()
    else:regions = chunks = 0
    changes = ledger.changes()
    result = {"format": "bloodborne-world-functional-repair-v2", "applied": bool(apply), "target": str(target),
              "aggregate": {"legacyMultiCellRoots": len(records), "activeDoors": len(doors), "movedObjects": len(ledger.moved),
                            "removedDoorOrFootprintModules": removed_modules, "changedCells": len(changes), "entityMoves": len(ledger.entity_moves),
                            "blockEntityChanges": len(ledger.entity_changes), "changedRegions": regions, "changedChunks": chunks, "unresolved": len(unresolved)},
              "changes": changes, "entityMoves": ledger.entity_moves, "blockEntityChanges": ledger.entity_changes,
              "movedObjects": ledger.moved, "unresolved": unresolved}
    output.parent.mkdir(parents=True, exist_ok=True);output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return result


def self_test():
    model = {"kind": "model_door", "properties": {"open": ["false", "true"]}}
    geometry = {"blocks": {"test": {"states": {
        "facing=north,open=false,shape=straight": {"cells": {"0,0,0": {}, "0,1,0": {}}},
        "facing=north,open=true,shape=straight": {"cells": {"0,0,0": {}, "1,0,0": {}}},
    }}}}
    values = {"facing": "north", "open": "false", "shape": "straight"};current, toggled = door_footprints(geometry, "test", model, values)
    assert current == {(0, 0, 0), (0, 1, 0)} and toggled == {(0, 0, 0), (1, 0, 0)}
    traditional = {"kind": "door", "properties": {"open": ["false", "true"], "half": ["lower", "upper"]}}
    assert reserved_door_sibling(traditional, {"half": "lower"}, (0, 1, 0))
    pos, root, owner = (5, 70, 6), (-2, 64, 3), NS + "test";entity = helper_entity(pos, root, owner);data = compound(entity)
    assert unpack_block_pos(data["Root"].value) == root and data["Owner"].value == owner and tuple(data[key].value for key in ("x", "y", "z")) == pos
    print("FUNCTIONAL REPAIR TESTS PASSED: door union/reserved sibling/helper binding")


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument("target", type=Path, nargs="?")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/world-functional-repair-v2.json")
    parser.add_argument("--max-radius", type=int, default=32);parser.add_argument("--vertical-range", type=int, default=16);parser.add_argument("--apply", action="store_true");parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:self_test();return
    if args.target is None:parser.error("target is required unless --self-test is used")
    result = repair(args.target, args.output, args.max_radius, args.vertical_range, args.apply);print(json.dumps(result["aggregate"], ensure_ascii=False))


if __name__ == "__main__":main()
