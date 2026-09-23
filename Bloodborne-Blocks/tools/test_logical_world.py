#!/usr/bin/env python3
"""Synthetic Anvil/NBT tests for the fail-closed logical world converter."""
from __future__ import annotations

import hashlib
import json
import shutil
import tempfile
import zipfile
from copy import deepcopy
from pathlib import Path

from check_logical_world import check, validate_ledger
from convert_logical_world import (AIR_NAME, PART, Candidate, Expected, Rule, World,
                                   block_pos_long, candidates, convert, parse_rules, reject_overlaps,
                                   state_tag)
from world_io import (TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG,
                      TAG_STRING, NbtFile, RegionFile, Tag, compound, write_nbt)


def write_chunk(path: Path, cx: int, cz: int, blocks: dict[tuple[int, int, int], tuple[str, dict]], entities=(), fluid_ticks=(), uniform=None):
    region = RegionFile.open(path) if path.exists() else RegionFile()
    palette = [state_tag(AIR_NAME, {})]
    index = {"minecraft:air": 0}
    values = [0] * 4096
    if uniform is not None:
        name, props = uniform
        palette.append(state_tag(name, props))
        index[name] = 1
        values = [1] * 4096
    for (x, y, z), (name, props) in blocks.items():
        tag = state_tag(name, props)
        key = name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")
        if key not in index:
            index[key] = len(palette)
            palette.append(tag)
        values[(y & 15) * 256 + (z & 15) * 16 + (x & 15)] = index[key]
    # All test data is at y=64; a direct palette avoids testing the helper
    # module itself rather than converter logic.
    from world_io import pack_palette_indices
    section = Tag(TAG_COMPOUND, {"Y": Tag(TAG_BYTE, 4), "block_states": Tag(TAG_COMPOUND, {
        "palette": Tag(TAG_LIST, palette, TAG_COMPOUND), "data": Tag(TAG_LONG, 0)})})
    section.value["block_states"].value["data"] = Tag(12, pack_palette_indices(values, len(palette)))
    root = Tag(TAG_COMPOUND, {"DataVersion": Tag(TAG_INT, 3465), "xPos": Tag(TAG_INT, cx), "zPos": Tag(TAG_INT, cz),
                              "sections": Tag(TAG_LIST, [section], TAG_COMPOUND), "block_entities": Tag(TAG_LIST, list(entities), TAG_COMPOUND),
                              "fluid_ticks": Tag(TAG_LIST, list(fluid_ticks), TAG_COMPOUND),
                              "Heightmaps": Tag(TAG_COMPOUND, {"WORLD_SURFACE": Tag(TAG_LONG, 1)})})
    region.set_chunk(cx & 31, cz & 31, NbtFile("", root), timestamp=17)
    path.parent.mkdir(parents=True, exist_ok=True)
    region.save(path)


def entity(pos, ident, **more):
    fields = {"id": Tag(TAG_STRING, ident), "x": Tag(TAG_INT, pos[0]), "y": Tag(TAG_INT, pos[1]), "z": Tag(TAG_INT, pos[2])}
    fields.update(more)
    return Tag(TAG_COMPOUND, fields)


def resources(root: Path):
    root.mkdir()
    (root / "definitions.json").write_text(json.dumps({"blocks": [
        {"id": "legacy", "default": {"facing": "north"}}, {"id": "member", "default": {"facing": "north"}},
        {"id": "legacy2", "default": {"facing": "north"}}, {"id": "member2", "default": {"facing": "north"}},
        {"id": "legacy3", "default": {"facing": "north"}}, {"id": "member3", "default": {"facing": "north"}},
        {"id": "legacy4", "default": {"facing": "north"}}, {"id": "member4", "default": {"facing": "north"}},
        {"id": "legacy_alias", "default": {"facing": "north"}},
        {"id": "m_a", "default": {"facing": "north"}}, {"id": "m_b", "default": {"facing": "north"}},
        {"id": "m_c", "default": {"facing": "north"}}, {"id": "m_d", "default": {"facing": "north"}}, {"id": "m_other", "default": {}},
        {"id": "o_arch", "default": {"facing": "north"}}, {"id": "architecture_part", "default": {}},
        {"id": "coal", "default": {}}, {"id": "raw_iron", "default": {}}, {"id": "raw_copper", "default": {}},
        {"id": "o_toothed_stone_rib", "default": {}}, {"id": "o_raw_iron_block", "default": {}},
        {"id": "o_raw_copper_block", "default": {}}, {"id": "o_toothed_spire", "default": {}},
        {"id": "o_toothed_spire_alt", "default": {}},
        {"id": "statue_bottom", "default": {}}, {"id": "lantern_top", "default": {}},
        {"id": "o_body", "default": {"lantern": "false"}}, {"id": "o_attachment", "default": {}},
    ]}), encoding="utf-8")
    migration = {"schemaVersion": 1, "rules": [
        {"source": {"id": "legacy", "properties": {"facing": "north"}}, "target": {"id": "o_arch", "properties": {"facing": "east"}},
         "offset": [-1, 0, 0], "members": [{"offset": [1, 0, 0], "id": "member", "properties": {"facing": "north"}}], "components": None},
        {"source": {"id": "legacy", "properties": {"facing": "south"}}, "target": {"id": "o_arch", "properties": {"facing": "south"}},
         "offset": [0, 0, 0], "components": [{"offset": [0, 0, 0], "id": "m_a", "properties": {"facing": "north"}}, {"offset": [1, 0, 0], "id": "m_b", "properties": {"facing": "north"}}]},
        {"source": {"id": "legacy2", "properties": {"facing": "north"}}, "target": {"id": "o_arch", "properties": {"facing": "west"}},
         "offset": [0, 0, 0], "members": [{"offset": [0, 1, 0], "id": "member2", "properties": {"facing": "north"}}], "components": None},
        {"source": {"id": "legacy3", "properties": {"facing": "north"}}, "target": {"id": "o_arch", "properties": {"facing": "west"}},
         "offset": [-2, 0, 0], "members": [{"offset": [1, 0, 0], "id": "member3", "properties": {"facing": "north"}}], "components": None},
        {"source": {"id": "legacy4", "properties": {"facing": "north"}}, "target": {"id": "o_arch", "properties": {"facing": "east"}},
         "offset": [-4, 0, 0], "members": [{"offset": [1, 0, 0], "id": "member4", "properties": {"facing": "north"}}], "components": None},
        # A legacy alias with the same v2 component representation must not
        # make the existing v2 object an ambiguous double conversion.
        {"source": {"id": "legacy_alias", "properties": {"facing": "north"}}, "target": {"id": "o_arch", "properties": {"facing": "south"}},
         "offset": [0, 0, 0], "components": [{"offset": [0, 0, 0], "id": "m_a", "properties": {"facing": "north"}}, {"offset": [1, 0, 0], "id": "m_b", "properties": {"facing": "north"}}]},
        # Same source components but distinct resulting target state: this
        # must remain fail-closed rather than being deduplicated as an alias.
        {"source": {"id": "legacy_alias", "properties": {"facing": "south"}}, "target": {"id": "o_arch", "properties": {"facing": "east"}},
         "offset": [0, 0, 0], "components": [{"offset": [0, 0, 0], "id": "m_c", "properties": {"facing": "north"}}, {"offset": [1, 0, 0], "id": "m_d", "properties": {"facing": "north"}}]},
        {"source": {"id": "legacy_alias", "properties": {"facing": "west"}}, "target": {"id": "o_arch", "properties": {"facing": "west"}},
         "offset": [0, 0, 0], "components": [{"offset": [0, 0, 0], "id": "m_c", "properties": {"facing": "north"}}, {"offset": [1, 0, 0], "id": "m_d", "properties": {"facing": "north"}}]},
        {"source": {"id": "coal"}, "target": {"id": "o_toothed_stone_rib"}, "offset": [0, 0, 0], "components": None},
        {"source": {"id": "raw_iron"}, "target": {"id": "o_raw_iron_block"}, "offset": [0, 0, 0], "components": None},
        {"source": {"id": "raw_copper"}, "target": {"id": "o_raw_copper_block"}, "offset": [0, 0, 0], "components": None},
        {"source": {"id": "coal"}, "target": {"id": "o_toothed_spire"}, "offset": [0, 0, 0],
         "members": [{"offset": [3, 0, 0], "id": "raw_iron"}, {"offset": [6, 0, 0], "id": "raw_copper"}], "components": None,
         "supersedes_targets": ["o_toothed_stone_rib", "o_raw_iron_block", "o_raw_copper_block"]},
        {"source": {"id": "statue_bottom"}, "target": {"id": "o_body", "properties": {"lantern": "false"}}, "offset": [0, 0, 0], "components": None},
        {"source": {"id": "lantern_top"}, "target": {"id": "o_attachment"}, "offset": [0, 0, 0], "components": None},
        {"source": {"id": "statue_bottom"}, "target": {"id": "o_body", "properties": {"lantern": "true"}}, "offset": [0, 0, 0],
         "members": [{"offset": [2, 0, 0], "id": "lantern_top"}], "components": None,
         "supersedes_targets": ["o_body", "o_attachment"]},
    ]}
    (root / "migration.json").write_text(json.dumps(migration), encoding="utf-8")
    geometry = {"blocks": {"o_arch": {"states": {
        "facing=east": {"cells": {"0,0,0": {}, "1,0,0": {}}}, "facing=south": {"cells": {"0,0,0": {}, "1,0,0": {}}},
        "facing=west": {"cells": {"0,0,0": {}, "1,0,0": {}}},
    }}, "o_toothed_stone_rib": {"states": {"": {"cells": {"0,0,0": {}}}}},
         "o_raw_iron_block": {"states": {"": {"cells": {"0,0,0": {}}}}},
         "o_raw_copper_block": {"states": {"": {"cells": {"0,0,0": {}}}}},
         "o_toothed_spire": {"states": {"": {"cells": {"0,0,0": {}, "3,0,0": {}, "6,0,0": {}}}}},
         "o_toothed_spire_alt": {"states": {"": {"cells": {"0,0,0": {}, "3,0,0": {}, "6,0,0": {}}}}},
         "o_body": {"states": {"lantern=false": {"cells": {"0,0,0": {}}}, "lantern=true": {"cells": {"0,0,0": {}, "2,0,0": {}}}}},
         "o_attachment": {"states": {"": {"cells": {"0,0,0": {}}}}}}}
    (root / "geometry.json").write_text(json.dumps(geometry), encoding="utf-8")
    (root.parent / "geometry.json").write_text(json.dumps({"blocks": {
        "legacy": {"states": {"facing=north": {"cells": {"0,0,0": {}, "0,0,2": {}}}}},
        "member": {"states": {"facing=north": {"cells": {"0,0,0": {}, "0,0,1": {}}}}},
        "legacy2": {"states": {"facing=north": {"cells": {"0,0,0": {}, "2,0,0": {}}}}},
        "member2": {"states": {"facing=north": {"cells": {"0,0,0": {}}}}},
    }}), encoding="utf-8")


def tree_hash(root: Path):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob("*") if p.is_file()}


def converted(source, output, res, report, report_root, **kwargs):
    return convert(source, output, resources=res, report_path=report, report_root=report_root, **kwargs)


def rejected_conversion(source, output, res, report, report_root):
    try:
        converted(source, output, res, report, report_root)
    except ValueError:
        return
    raise AssertionError("unsafe report path was accepted")


def rejected_check(source, output, report, res):
    try:
        check(source, output, report, res)
    except AssertionError:
        return
    raise AssertionError("checker accepted tampered conversion evidence")


def rejected_ledger(before, report, rules):
    try:
        validate_ledger(before, report, rules)
    except AssertionError:
        return
    raise AssertionError("checker accepted an incomplete or ambiguous ledger")


def assembly_source(root, blocks, entities=()):
    root.mkdir()
    write_nbt(root / "level.dat", NbtFile("", Tag(TAG_COMPOUND, {"Data": Tag(TAG_COMPOUND, {})})))
    write_chunk(root / "region/r.0.0.mca", 0, 0, blocks, entities)


def assembly_cases(base, res):
    """Exercise the one explicit assembly exception without broad preference."""
    complete = {(0, 64, 0): ("bloodborne_blocks:coal", {}),
                (3, 64, 0): ("bloodborne_blocks:raw_iron", {}),
                (6, 64, 0): ("bloodborne_blocks:raw_copper", {})}
    reports = base / "assembly-reports"
    reports.mkdir()
    source = base / "assembly-complete"
    assembly_source(source, complete)
    output = base / "assembly-output"
    report = converted(source, output, res, reports / "complete.json", reports)
    assert report["counts"]["converted"] == 1, report
    assert sum(item["reason"] == "superseded_by_complete_assembly" for item in report["rejected"]) == 3, report
    assert World(output, {}).get("minecraft:overworld", (0, 64, 0))[0] == "bloodborne_blocks:o_toothed_spire"
    check(source, output, reports / "complete.json", res)
    omitted = deepcopy(report)
    omitted["ledger"].pop()
    omitted["counts"]["converted"] = 0
    rules, defaults = parse_rules(res)
    rejected_ledger(World(source, defaults), omitted, rules)
    second = base / "assembly-second"
    converted(output, second, res, reports / "second.json", reports)
    assert tree_hash(output) == tree_hash(second), "assembly conversion was not idempotent"

    partial = base / "assembly-partial"
    assembly_source(partial, {(0, 64, 0): ("bloodborne_blocks:coal", {})})
    partial_output = base / "assembly-partial-output"
    partial_report = converted(partial, partial_output, res, reports / "partial.json", reports)
    assert partial_report["counts"]["converted"] == 1
    assert World(partial_output, {}).get("minecraft:overworld", (0, 64, 0))[0] == "bloodborne_blocks:o_toothed_stone_rib"

    obstructed = base / "assembly-obstructed"
    assembly_source(obstructed, complete, [entity((6, 64, 0), "minecraft:chest")])
    obstructed_output = base / "assembly-obstructed-output"
    obstructed_report = converted(obstructed, obstructed_output, res, reports / "obstructed.json", reports)
    assert obstructed_report["counts"]["converted"] == 2, obstructed_report
    assert World(obstructed_output, {}).get("minecraft:overworld", (0, 64, 0))[0] == "bloodborne_blocks:o_toothed_stone_rib"

    no_allowlist = base / "assembly-no-allowlist"
    shutil.copytree(res, no_allowlist)
    migration = json.loads((no_allowlist / "migration.json").read_text(encoding="utf-8"))
    next(rule for rule in migration["rules"] if rule["target"]["id"] == "o_toothed_spire").pop("supersedes_targets")
    (no_allowlist / "migration.json").write_text(json.dumps(migration), encoding="utf-8")
    no_allowlist_source = base / "assembly-no-allowlist-source"
    assembly_source(no_allowlist_source, complete)
    no_allowlist_output = base / "assembly-no-allowlist-output"
    no_allowlist_report = converted(no_allowlist_source, no_allowlist_output, no_allowlist, reports / "no-allowlist.json", reports)
    assert no_allowlist_report["counts"]["converted"] == 0, no_allowlist_report

    conflict_resources = base / "assembly-conflict-resources"
    shutil.copytree(res, conflict_resources)
    migration = json.loads((conflict_resources / "migration.json").read_text(encoding="utf-8"))
    conflicting = deepcopy(next(rule for rule in migration["rules"] if rule["target"]["id"] == "o_toothed_spire"))
    conflicting["target"] = {"id": "o_toothed_spire_alt"}
    migration["rules"].append(conflicting)
    (conflict_resources / "migration.json").write_text(json.dumps(migration), encoding="utf-8")
    conflict_source = base / "assembly-conflict-source"
    assembly_source(conflict_source, complete)
    conflict_output = base / "assembly-conflict-output"
    conflict_report = converted(conflict_source, conflict_output, conflict_resources, reports / "conflict.json", reports)
    assert conflict_report["counts"]["converted"] == 3, conflict_report
    assert World(conflict_output, {}).get("minecraft:overworld", (0, 64, 0))[0] == "bloodborne_blocks:o_toothed_stone_rib"
    assert all(entry["rule"] not in (11, 15) for entry in conflict_report["ledger"])

    malformed = json.loads((res / "migration.json").read_text(encoding="utf-8"))
    for index, targets in enumerate(([], ["o_toothed_stone_rib", "o_toothed_stone_rib"], ["minecraft:stone"], ["o_unknown_target"])):
        next(rule for rule in malformed["rules"] if rule["target"]["id"] == "o_toothed_spire")["supersedes_targets"] = targets
        bad = base / ("assembly-invalid-" + str(index))
        shutil.copytree(res, bad)
        (bad / "migration.json").write_text(json.dumps(malformed), encoding="utf-8")
        try:
            parse_rules(bad)
        except ValueError:
            continue
        raise AssertionError("invalid supersedes_targets was accepted")


def statue_like_cases(base, res):
    """A same-ID state change may supersede only with the attached source."""
    reports = base / "statue-reports"
    reports.mkdir()
    complete = base / "statue-complete"
    assembly_source(complete, {(0, 64, 0): ("bloodborne_blocks:statue_bottom", {}),
                               (2, 64, 0): ("bloodborne_blocks:lantern_top", {})})
    output = base / "statue-output"
    report = converted(complete, output, res, reports / "complete.json", reports)
    assert report["counts"]["converted"] == 1, report
    assert sum(item["reason"] == "superseded_by_complete_assembly" for item in report["rejected"]) == 2, report
    assert World(output, {}).get("minecraft:overworld", (0, 64, 0)) == ("bloodborne_blocks:o_body", (("lantern", "true"),))
    check(complete, output, reports / "complete.json", res)
    second = base / "statue-second"
    converted(output, second, res, reports / "second.json", reports)
    assert tree_hash(output) == tree_hash(second), "same-ID assembly conversion was not idempotent"

    partial = base / "statue-partial"
    assembly_source(partial, {(0, 64, 0): ("bloodborne_blocks:statue_bottom", {})})
    partial_output = base / "statue-partial-output"
    converted(partial, partial_output, res, reports / "partial.json", reports)
    assert World(partial_output, {}).get("minecraft:overworld", (0, 64, 0)) == ("bloodborne_blocks:o_body", (("lantern", "false"),))
    check(partial, partial_output, reports / "partial.json", res)

    attachment = base / "statue-attachment"
    assembly_source(attachment, {(2, 64, 0): ("bloodborne_blocks:lantern_top", {})})
    attachment_output = base / "statue-attachment-output"
    converted(attachment, attachment_output, res, reports / "attachment.json", reports)
    assert World(attachment_output, {}).get("minecraft:overworld", (2, 64, 0))[0] == "bloodborne_blocks:o_attachment"
    check(attachment, attachment_output, reports / "attachment.json", res)


def overlap_scalability_case():
    """Disjoint candidates must use the position index, not a global pair scan."""
    state = ("bloodborne_blocks:o_scale", ())
    rule = Rule(0, Expected((0, 0, 0), state), state, (0, 0, 0), (), None, frozenset({(0, 0, 0)}))
    items = []
    for index in range(10_000):
        point = (index * 2, 64, 0)
        items.append(Candidate(rule, "legacy", "minecraft:overworld", point, {point}, point, {point: state}))
    stats = {}
    reject_overlaps(items, stats)
    assert stats == {"eligible": 10_000, "touchedPositions": 10_000, "pairChecks": 0}, stats
    assert all(item.reason is None for item in items)


def normalized_fallback_case():
    """A discarded fallback's future helper is not part of the existing source."""
    large_target, small_target = ("bloodborne_blocks:o_whole", ()), ("bloodborne_blocks:o_fragment", ())
    root, member, future = (0,64,0), (3,64,0), (3,65,0)
    source = Expected((0,0,0), ("minecraft:stone", ()))
    def fixtures(declared=True, stale=False):
        large_rule = Rule(0, source, large_target, (0,0,0), (), None, frozenset({(0,0,0)}),
                          frozenset({small_target[0]}) if declared else frozenset())
        small_rule = Rule(1, source, small_target, (0,0,0), (), None, frozenset({(0,0,0),(0,1,0)}))
        large = Candidate(large_rule,"legacy","minecraft:overworld",root,{root,member},root,{root:large_target})
        small = Candidate(small_rule,"legacy","minecraft:overworld",member,{member},member,
                          {member:small_target,future:(PART,())},stale={future} if stale else set())
        return large, small
    large, small = fixtures()
    reject_overlaps([large,small])
    assert large.reason is None and small.reason == 'superseded_by_complete_assembly'
    assert future not in large.touched and future not in large.writes, 'discarded helper must not be emitted'
    for declared, stale in ((False,False),(True,True)):
        large, small = fixtures(declared,stale)
        reject_overlaps([large,small])
        assert large.reason == small.reason == 'ambiguous_overlap_or_double_consumption'
    large, small = fixtures()
    small.existing_helpers.add(future)  # Existing helper reused by fallback, not classified as stale.
    reject_overlaps([large,small])
    assert large.reason == small.reason == 'ambiguous_overlap_or_double_consumption'
    large, small = fixtures()
    large.reason = 'target_would_overwrite_foreign_block'
    reject_overlaps([large,small])
    assert small.reason is None, 'rejected assembly must not suppress a valid fallback'
    large, small = fixtures()
    helper_only_rule = Rule(2, source, ("bloodborne_blocks:o_blocker", ()), (0,0,0), (), None,
                            frozenset({(0,0,0)}))
    helper_only = Candidate(helper_only_rule, "legacy", "minecraft:overworld", future, {future}, future,
                            {future: helper_only_rule.target})
    reject_overlaps([large, small, helper_only])
    assert large.reason is None
    assert small.reason == helper_only.reason == 'ambiguous_overlap_or_double_consumption'


def existing_fallback_helper_case(base, res):
    """An owned fallback helper outside an assembly blocks supersession."""
    reports = base / "existing-helper-reports"
    reports.mkdir()
    fixture_resources = base / "existing-helper-resources"
    shutil.copytree(res, fixture_resources)
    geometry = json.loads((fixture_resources / "geometry.json").read_text(encoding="utf-8"))
    geometry["blocks"]["o_raw_iron_block"]["states"][""]["cells"]["0,1,0"] = {}
    (fixture_resources / "geometry.json").write_text(json.dumps(geometry), encoding="utf-8")
    source = base / "existing-helper-source"
    helper = (3, 65, 0)
    assembly_source(source, {
        (0, 64, 0): ("bloodborne_blocks:coal", {}),
        (3, 64, 0): ("bloodborne_blocks:raw_iron", {}),
        (6, 64, 0): ("bloodborne_blocks:raw_copper", {}),
        helper: (PART, {}),
    }, [entity(helper, PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:o_raw_iron_block"),
               Root=Tag(TAG_LONG, block_pos_long(3, 64, 0)))])
    before = tree_hash(source)
    rules, defaults = parse_rules(fixture_resources)
    items, _, _ = candidates(World(source, defaults), rules)
    iron = next(item for item in items if item.rule.target[0] == "bloodborne_blocks:o_raw_iron_block")
    assert iron.existing_helpers == {helper}, iron
    output = base / "existing-helper-output"
    report = converted(source, output, fixture_resources, reports / "result.json", reports)
    assert report["counts"]["converted"] == 2, report
    assert any(item["reason"] == "ambiguous_overlap_or_double_consumption" and item["origin"] == [3, 64, 0]
               for item in report["rejected"]), report
    converted_world = World(output, defaults)
    assert converted_world.get("minecraft:overworld", (3, 64, 0)) == ("bloodborne_blocks:raw_iron", ())
    assert converted_world.get("minecraft:overworld", (0, 64, 0))[0] != "bloodborne_blocks:o_toothed_spire"
    assert converted_world.get("minecraft:overworld", helper) == (PART, ())
    assert converted_world.block_entities()[("minecraft:overworld", *helper)] == World(source, defaults).block_entities()[("minecraft:overworld", *helper)]
    assert tree_hash(source) == before, "conversion modified its original world"
    check(source, output, reports / "result.json", fixture_resources)


def third_candidate_conflict_case():
    """A conflicting third candidate prevents an assembly from suppressing its fallback."""
    source = Expected((0, 0, 0), ("minecraft:stone", ()))
    large_target, small_target, blocker_target = (("bloodborne_blocks:o_whole", ()),
                                                   ("bloodborne_blocks:o_fragment", ()),
                                                   ("bloodborne_blocks:o_blocker", ()))
    root, member = (0, 64, 0), (3, 64, 0)
    large_rule = Rule(0, source, large_target, (0, 0, 0), (), None, frozenset({(0, 0, 0)}),
                      frozenset({small_target[0]}))
    small_rule = Rule(1, source, small_target, (0, 0, 0), (), None, frozenset({(0, 0, 0)}))
    blocker_rule = Rule(2, source, blocker_target, (0, 0, 0), (), None, frozenset({(0, 0, 0)}))
    large = Candidate(large_rule, "legacy", "minecraft:overworld", root, {root, member}, root, {root: large_target})
    small = Candidate(small_rule, "legacy", "minecraft:overworld", member, {member}, member, {member: small_target})
    blocker = Candidate(blocker_rule, "legacy", "minecraft:overworld", root, {root}, root, {root: blocker_target})
    reject_overlaps([large, small, blocker])
    assert large.reason == blocker.reason == "ambiguous_overlap_or_double_consumption"
    assert small.reason is None, "a rejected assembly must not suppress its fallback"


def main():
    with tempfile.TemporaryDirectory(prefix="logical-test-") as temporary:
        base = Path(temporary)
        res, source, output, direct, second = base / "logical", base / "source", base / "output", base / "direct", base / "second"
        resources(res)
        source.mkdir()
        write_nbt(source / "level.dat", NbtFile("", Tag(TAG_COMPOUND, {"Data": Tag(TAG_COMPOUND, {})})))
        # Successful legacy group crosses chunk boundary: root 14, components
        # at 15/16; geometry creates its helper on a consumed source cell.
        write_chunk(source / "region/r.0.0.mca", 0, 0, {(15, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"})})
        write_chunk(source / "region/r.0.0.mca", 1, 0, {(16, 64, 0): ("bloodborne_blocks:member", {"facing": "north"})})
        # v2 group is in another vanilla dimension and tests target orientation.
        write_chunk(source / "DIM-1/region/r.0.0.mca", 0, 0, {(0, 64, 2): ("bloodborne_blocks:m_a", {"facing": "north"}), (1, 64, 2): ("bloodborne_blocks:m_b", {"facing": "north"}),
                                                             (4, 64, 2): ("bloodborne_blocks:m_c", {"facing": "north"}), (5, 64, 2): ("bloodborne_blocks:m_d", {"facing": "north"}),
                                                             (8, 64, 2): ("bloodborne_blocks:m_a", {"facing": "north"})})
        write_chunk(source / "region/r.0.0.mca", 7, 0, {}, uniform=("bloodborne_blocks:m_other", {}))
        # PART-looking blocks with malformed ownership are foreign and must
        # never be claimed as converter helpers.
        write_chunk(source / "region/r.0.0.mca", 8, 0, {
            (128, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (128, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (129, 64, 0): (PART, {}),
            (136, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (136, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (137, 64, 0): (PART, {}),
        }, [entity((129, 64, 0), PART, Owner=Tag(TAG_LONG, 1), Root=Tag(TAG_LONG, block_pos_long(128, 64, 0))),
            entity((137, 64, 0), "minecraft:chest", Owner=Tag(TAG_STRING, "bloodborne_blocks:o_arch"), Root=Tag(TAG_LONG, block_pos_long(136, 64, 0)))])
        write_chunk(source / "region/r.0.0.mca", 9, 0, {
            (144, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (144, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (145, 64, 0): (PART, {}),
        }, [entity((145, 64, 0), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:o_arch"), Root=Tag(TAG_STRING, "not-a-long"))])
        # Proven legacy helper profiles migrate with their source group.  The
        # single legacy2 helper and both legacy/member helpers are outside the
        # new footprint, so they must be removed as stale source helpers.
        write_chunk(source / "region/r.0.0.mca", 10, 0, {
            (160, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (160, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (162, 64, 0): (PART, {}),
        }, [entity((162, 64, 0), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:legacy2"), Root=Tag(TAG_LONG, block_pos_long(160, 64, 0)))])
        write_chunk(source / "region/r.0.0.mca", 11, 0, {
            (176, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"}), (177, 64, 0): ("bloodborne_blocks:member", {"facing": "north"}),
            (176, 64, 2): (PART, {}), (177, 64, 1): (PART, {}),
        }, [entity((176, 64, 2), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:legacy"), Root=Tag(TAG_LONG, block_pos_long(176, 64, 0))),
            entity((177, 64, 1), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:member"), Root=Tag(TAG_LONG, block_pos_long(177, 64, 0)))])
        # A source-owner helper only qualifies when its root is the matching
        # source state and its offset belongs to that source profile.
        write_chunk(source / "region/r.0.0.mca", 12, 0, {
            (200, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (200, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (201, 64, 0): (PART, {}),
        }, [entity((201, 64, 0), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:legacy2"), Root=Tag(TAG_LONG, block_pos_long(199, 64, 0)))])
        write_chunk(source / "region/r.0.0.mca", 13, 0, {
            (208, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (208, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}), (209, 64, 0): (PART, {}),
        }, [entity((209, 64, 0), PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:legacy2"), Root=Tag(TAG_LONG, block_pos_long(208, 64, 0)))])
        # Partial, foreign-BE, target-foreign-block, and overlapping candidates
        # must all remain byte/logically unchanged.
        write_chunk(source / "region/r.0.0.mca", 2, 0, {(32, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"})})
        write_chunk(source / "region/r.0.0.mca", 3, 0, {
            (48, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"}), (49, 64, 0): ("bloodborne_blocks:member", {"facing": "north"}),
            (54, 64, 0): ("minecraft:dirt", {}), (55, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"}), (56, 64, 0): ("bloodborne_blocks:member", {"facing": "north"}),
        }, [entity((48, 64, 0), "minecraft:chest")])
        # Complete group plus a stale helper with the exact Root/Owner is safe
        # to remove; no other helper may be opportunistically removed.
        write_chunk(source / "region/r.0.0.mca", 4, 0, {(70, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"}), (71, 64, 0): ("bloodborne_blocks:member", {"facing": "north"}), (72, 64, 0): (PART, {}),
        }, [entity((72, 64, 0), PART, Root=Tag(TAG_LONG, block_pos_long(69, 64, 0)), Owner=Tag(TAG_STRING, "bloodborne_blocks:o_arch"))])
        write_chunk(source / "region/r.0.0.mca", 5, 0, {
            (80, 64, 0): ("bloodborne_blocks:legacy2", {"facing": "north"}), (80, 65, 0): ("bloodborne_blocks:member2", {"facing": "north"}),
            (90, 64, 0): ("bloodborne_blocks:legacy3", {"facing": "north"}), (91, 64, 0): ("bloodborne_blocks:member3", {"facing": "north"}),
            (92, 64, 0): ("bloodborne_blocks:legacy4", {"facing": "north"}), (93, 64, 0): ("bloodborne_blocks:member4", {"facing": "north"}),
        })
        write_chunk(source / "region/r.0.0.mca", 6, 0, {
            (96, 64, 0): ("bloodborne_blocks:legacy", {"facing": "north"}), (97, 64, 0): ("bloodborne_blocks:member", {"facing": "north"}),
        }, fluid_ticks=[entity((96, 64, 0), "minecraft:water")])
        before = tree_hash(source)
        readonly = World(source, {})
        readonly_chunk = readonly.chunk("minecraft:overworld", 0, 0)
        assert readonly_chunk is not None
        sections_before = deepcopy(readonly_chunk.root()["sections"])
        assert readonly.get("minecraft:overworld", (0, 80, 0))[0] == AIR_NAME
        assert readonly_chunk.root()["sections"] == sections_before, "read created a missing section"
        archive = base / "source.zip"
        with zipfile.ZipFile(archive, "w") as zip_out:
            for file in source.rglob("*"):
                if file.is_file(): zip_out.write(file, Path("world") / file.relative_to(source))
        report_root = base / "build"
        rejected_conversion(source, base / "bad-source-report", res, source / "report.json", report_root)
        rejected_conversion(archive, base / "bad-zip-report", res, archive, report_root)
        rejected_conversion(source, report_root / "nested-output", res, report_root / "nested-output/report.json", report_root)
        uppercase_source = base / "uppercase-sections"
        shutil.copytree(source, uppercase_source)
        uppercase_world = World(uppercase_source, {})
        uppercase_chunk = uppercase_world.chunk("minecraft:overworld", 0, 0)
        assert uppercase_chunk is not None
        uppercase_chunk.root()["Sections"] = uppercase_chunk.root().pop("sections")
        uppercase_chunk.changed = True
        uppercase_world.save()
        rejected_conversion(uppercase_source, base / "uppercase-output", res, report_root / "uppercase.json", report_root)
        report = converted(archive, output, res, report_root / "report.json", report_root)
        assert report["resources"]["definitionsSha256"] == {"legacy": None, "logical": hashlib.sha256((res / "definitions.json").read_bytes()).hexdigest(),
                                                           "legacyGeometry": hashlib.sha256((res.parent / "geometry.json").read_bytes()).hexdigest()}
        assert tree_hash(source) == before, "directory source was modified"
        assert report["counts"]["converted"] == 6, report
        assert report["counts"]["rejected"] >= 6, report
        assert {(item["dimension"], tuple(item["origin"])) for item in report["rejected"]} >= {
            ("minecraft:overworld", (128, 64, 0)), ("minecraft:overworld", (136, 64, 0)), ("minecraft:overworld", (144, 64, 0)),
            ("minecraft:overworld", (200, 64, 0)), ("minecraft:overworld", (208, 64, 0))}, report
        assert sum(item["reason"] == "ambiguous_overlap_or_double_consumption" for item in report["rejected"]) == 4, report
        assert sum(item["mode"] == "v2" for item in report["ledger"]) == 1, report
        assert report["counts"]["unresolvedV2"] == 3, report
        assert report["counts"]["unmatchedModules"] == 4096, report
        assert report["unmatchedModules"] == [{"dimension": "minecraft:overworld", "state": "bloodborne_blocks:m_other", "count": 4096,
                                                "samples": [[112, 64, 0], [113, 64, 0], [114, 64, 0], [115, 64, 0]]}], report
        check(archive, output, report_root / "report.json", res)
        assert World(output, {}).get("minecraft:overworld", (162, 64, 0))[0] == AIR_NAME, "single legacy helper was not removed"
        assert World(output, {}).get("minecraft:overworld", (176, 64, 2))[0] == AIR_NAME, "source helper was not removed"
        assert World(output, {}).get("minecraft:overworld", (177, 64, 1))[0] == AIR_NAME, "member helper was not removed"
        recorded = json.loads((report_root / "report.json").read_text(encoding="utf-8"))
        rules, defaults = parse_rules(res)
        before_world = World(source, defaults)
        omitted = deepcopy(recorded)
        omitted["ledger"].pop()
        omitted["counts"]["converted"] -= 1
        rejected_ledger(before_world, omitted, rules)
        chosen_ambiguous = deepcopy(recorded)
        chosen_ambiguous["ledger"].append({
            "rule": 6, "mode": "v2", "dimension": "minecraft:the_nether", "origin": [4, 64, 2], "targetRoot": [4, 64, 2],
            "source": [[4, 64, 2], [5, 64, 2]], "staleRemoved": [],
            "changes": [{"position": [4, 64, 2], "before": "bloodborne_blocks:m_c[facing=north]", "after": "bloodborne_blocks:o_arch[facing=east]"},
                        {"position": [5, 64, 2], "before": "bloodborne_blocks:m_d[facing=north]", "after": PART}],
            "helpers": [{"position": [5, 64, 2], "id": PART, "Root": block_pos_long(4, 64, 2), "Owner": "bloodborne_blocks:o_arch"}],
        })
        chosen_ambiguous["counts"]["converted"] += 1
        rejected_ledger(before_world, chosen_ambiguous, rules)
        fake_ledger = deepcopy(recorded)
        fake_ledger["ledger"][0]["changes"][0]["after"] = "minecraft:diamond_block"
        fake_ledger_path = report_root / "fake-ledger.json"
        fake_ledger_path.write_text(json.dumps(fake_ledger), encoding="utf-8")
        rejected_check(archive, output, fake_ledger_path, res)
        fake_definitions = deepcopy(recorded)
        fake_definitions["resources"]["definitionsSha256"]["logical"] = "0" * 64
        fake_definitions_path = report_root / "fake-definitions.json"
        fake_definitions_path.write_text(json.dumps(fake_definitions), encoding="utf-8")
        rejected_check(archive, output, fake_definitions_path, res)
        root_type_damaged = base / "root-type-damaged"
        shutil.copytree(output, root_type_damaged)
        root_type_world = World(root_type_damaged, {})
        root_type_chunk = root_type_world.chunk("minecraft:the_nether", 1, 2)
        assert root_type_chunk is not None
        _, helper_entries = root_type_chunk.entities()
        helper_data = compound(helper_entries[0])
        helper_data["Root"] = Tag(TAG_INT, int(helper_data["Root"].value))
        root_type_chunk.changed = True
        root_type_world.save()
        rejected_check(archive, root_type_damaged, report_root / "report.json", res)
        coordinate_type_damaged = base / "coordinate-type-damaged"
        shutil.copytree(output, coordinate_type_damaged)
        coordinate_type_world = World(coordinate_type_damaged, {})
        coordinate_type_chunk = coordinate_type_world.chunk("minecraft:the_nether", 1, 2)
        assert coordinate_type_chunk is not None
        _, helper_entries = coordinate_type_chunk.entities()
        helper_data = compound(helper_entries[0])
        helper_data["x"] = Tag(TAG_LONG, int(helper_data["x"].value))
        coordinate_type_chunk.changed = True
        coordinate_type_world.save()
        rejected_check(archive, coordinate_type_damaged, report_root / "report.json", res)
        # Save through the loaded world so only this untouched chunk cache is
        # removed; the verifier must not accept cache loss outside ledger rows.
        cache_damaged = base / "cache-damaged"
        shutil.copytree(output, cache_damaged)
        damaged_output = World(cache_damaged, {})
        untouched = damaged_output.chunk("minecraft:overworld", 112, 0)
        assert untouched is not None and "Heightmaps" in untouched.root()
        untouched.root().pop("Heightmaps")
        untouched.changed = True
        damaged_output.save()
        rejected_check(archive, cache_damaged, report_root / "report.json", res)
        direct_report = converted(source, direct, res, report_root / "direct.json", report_root)
        check(source, direct, report_root / "direct.json", res)
        assert tree_hash(output) == tree_hash(direct), "ZIP and directory inputs differ"
        # The verifier requires every helper listed in the ledger, not merely
        # that helpers which happen to remain are valid.
        damaged = World(direct, {})
        _, helper_entities = damaged.chunk("minecraft:overworld", 15, 0).entities()
        damaged.chunk("minecraft:overworld", 15, 0).root()["block_entities"].value = []
        damaged.chunk("minecraft:overworld", 15, 0).changed = True
        damaged.save()
        try:
            check(source, direct, base / "build/direct.json", res)
            raise AssertionError("checker accepted a missing helper")
        except AssertionError as error:
            assert "missing helper" in str(error)
        assert World(output, {}).get("minecraft:overworld", (72, 64, 0))[0] == AIR_NAME, "stale owned part was not removed"
        converted(output, second, res, report_root / "second.json", report_root)
        assert tree_hash(output) == tree_hash(second), "second conversion was not idempotent"
        failure_report = report_root / "failed.json"
        original_save = World.save
        try:
            World.save = lambda self: (_ for _ in ()).throw(RuntimeError("synthetic save failure"))
            try:
                converted(source, base / "failed", res, failure_report, report_root)
                raise AssertionError("synthetic save failure was swallowed")
            except RuntimeError as error:
                assert str(error) == "synthetic save failure"
        finally:
            World.save = original_save
        assert not failure_report.exists(), "failed conversion published a report"
        assembly_cases(base, res)
        statue_like_cases(base, res)
        overlap_scalability_case()
        normalized_fallback_case()
        existing_fallback_helper_case(base, res)
        third_candidate_conflict_case()
        print(json.dumps({"ok": True, "converted": report["counts"]["converted"], "rejected": report["counts"]["rejected"], "dimensions": 2}))


if __name__ == "__main__":
    main()
