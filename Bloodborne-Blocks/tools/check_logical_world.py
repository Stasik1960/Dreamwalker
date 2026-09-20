#!/usr/bin/env python3
"""Independent verifier for ``convert_logical_world.py`` outputs."""
from __future__ import annotations

import argparse
import hashlib
import json
import tempfile
import zipfile
from pathlib import Path

import numpy as np

from convert_logical_world import AIR_NAME, AIR_NAMES, PART, World, as_tag_state, block_pos_long, hash_tree, parse_rules, safe_extract, state_of
from world_io import TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING, Tag, block_state_key, compound, section_blocks


AIR = (AIR_NAME, ())


def section_values(section, defaults):
    if section is None:
        return [AIR] * 4096
    unpacked = section_blocks(section)
    if unpacked is None:
        return [AIR] * 4096
    palette, indices = unpacked
    states = [state_of(entry, defaults) for entry in palette]
    return [states[index] for index in indices]


def section_codes(section, defaults, codes):
    """Compare palettes in bulk; expand coordinates only for actual changes."""
    unpacked = None if section is None else section_blocks(section)
    if unpacked is None:
        return np.full(4096, codes.setdefault(AIR, len(codes)), dtype=np.int32)
    palette, indices = unpacked
    mapping = []
    for entry in palette:
        state = state_of(entry, defaults)
        mapping.append(codes.setdefault(state, len(codes)))
    return np.asarray(mapping, dtype=np.int32)[np.asarray(indices, dtype=np.int32)]


def entities(chunk):
    root = chunk.root()
    values = root.get("block_entities") or root.get("TileEntities")
    result = {}
    if values is None:
        return result
    for entry in values.value:
        data = compound(entry)
        if not all(key in data for key in ("x", "y", "z")):
            raise AssertionError(f"block entity has no position in {chunk.dimension}/{chunk.x},{chunk.z}")
        point = (chunk.dimension, int(data["x"].value), int(data["y"].value), int(data["z"].value))
        if point in result:
            raise AssertionError(f"duplicate block entity at {point}")
        result[point] = entry
    return result


def effect_key(dim, source, touched, writes):
    return dim, frozenset(source), frozenset(touched), frozenset(writes.items())


def proved_helpers(before, dim, origin, root, rule, pieces, owned):
    """Adopt only helpers of matched roots inside their exact old footprints.

    The helper's claimed Owner/Root is not proof by itself. In particular, a
    neighbouring object's parts cannot be taken over merely because they lie
    inside the new object's bounding box.
    """
    result = set(owned.get((dim, rule.target[0], block_pos_long(*root)), ()))
    for piece in pieces:
        source_root = tuple(origin[i] + piece.offset[i] for i in range(3))
        if before.get(dim, source_root) != piece.state:
            continue
        for point in owned.get((dim, piece.state[0], block_pos_long(*source_root)), ()):
            offset = tuple(point[i] - source_root[i] for i in range(3))
            if offset != (0, 0, 0) and offset in piece.shape and before.get(dim, point) == (PART, ()):
                result.add(point)
    return result


def independently_accepted_effects(before, rules, old_entities, owned):
    """Enumerate complete groups, deduplicate effects and reject global conflicts.

    The first member is sufficient to discover every *complete* group. This
    verifier intentionally has a different scanning strategy from conversion.
    """
    starts = {}
    for rule in rules:
        starts.setdefault(rule.source.state, []).append((rule, "legacy", (0, 0, 0)))
        if rule.components:
            first = rule.components[0]
            starts.setdefault(first.state, []).append((rule, "v2", first.offset))
    scheduled = set()
    proposals = {}
    for chunk in before.chunks.values():
        for name in ("block_ticks", "TileTicks", "fluid_ticks", "LiquidTicks"):
            ticks = chunk.root().get(name)
            for tick in ticks.value if ticks is not None else ():
                data = compound(tick)
                if all(axis in data for axis in ("x", "y", "z")):
                    scheduled.add((chunk.dimension, *(data[axis].value for axis in ("x", "y", "z"))))
        for section in chunk.root().get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
            unpacked = section_blocks(section)
            if unpacked is None:
                continue
            palette, indices = unpacked
            array = np.asarray(indices, dtype=np.int32)
            sy = int(compound(section)["Y"].value)
            for index, tag in enumerate(palette):
                choices = starts.get(state_of(tag, before.defaults))
                if not choices:
                    continue
                for cell in np.flatnonzero(array == index):
                    cell = int(cell)
                    pos = (chunk.x * 16 + (cell & 15), sy * 16 + (cell >> 8), chunk.z * 16 + ((cell >> 4) & 15))
                    for rule, mode, offset in choices:
                        origin = tuple(pos[i] - offset[i] for i in range(3))
                        proposals[(rule.number, mode, chunk.dimension, origin)] = rule
    effects = {}
    for (_, mode, dim, origin), rule in proposals.items():
        pieces = (rule.source,) + rule.members if mode == "legacy" else rule.components
        source = {tuple(origin[i] + part.offset[i] for i in range(3)) for part in pieces}
        if len(source) != len(pieces) or any(before.get(dim, tuple(origin[i] + part.offset[i] for i in range(3))) != part.state for part in pieces):
            continue
        root = tuple(origin[i] + rule.root_offset[i] for i in range(3))
        writes = {tuple(root[i] + offset[i] for i in range(3)): (PART, ()) for offset in rule.shape}
        writes[root] = rule.target
        helpers = proved_helpers(before, dim, origin, root, rule, pieces, owned)
        stale = helpers - set(writes)
        touched = source | stale | set(writes)
        valid = True
        for point in touched:
            old = before.get(dim, point)
            is_owned = point in helpers and old == (PART, ())
            if (old is None or (dim, *point) in scheduled or
                    (point in stale and not is_owned) or
                    ((dim, *point) in old_entities and not is_owned) or
                    (point in writes and point not in source and old[0] not in AIR_NAMES and not is_owned)):
                valid = False
                break
        if valid:
            effects[effect_key(dim, source, touched, writes)] = {(dim, *point) for point in touched}
    claimed = {}
    conflicting = set()
    for effect, points in effects.items():
        for point in points:
            previous = claimed.setdefault(point, effect)
            if previous != effect:
                conflicting.update((previous, effect))
    return set(effects) - conflicting


def validate_ledger(before, report, rules):
    """Authorize each edit from the unmodified world, independently of planning.

    The report is an assertion to verify, never permission to change arbitrary
    blocks. Do not call the converter's candidates/apply/overlap routines here.
    """
    if report.get("dryRun") or report.get("format") != "bloodborne-logical-world-conversion-v1":
        raise AssertionError("only a completed conversion report can verify a world")
    old_entities = before.block_entities()
    owned = {}
    for (dim, x, y, z), entity in old_entities.items():
        data = compound(entity)
        if (data.get("id") == Tag(TAG_STRING, PART) and
                data.get("Owner", Tag(TAG_LIST, [])).type == TAG_STRING and
                data.get("Root", Tag(TAG_LIST, [])).type == TAG_LONG and
                before.get(dim, (x, y, z)) == (PART, ())):
            owned.setdefault((dim, data["Owner"].value, data["Root"].value), set()).add((x, y, z))
    reserved = set()
    reported_effects = set()
    for entry in report.get("ledger", []):
        number = entry.get("rule")
        if type(number) is not int or not 0 <= number < len(rules):
            raise AssertionError("ledger has an unknown migration rule")
        rule = rules[number]
        mode = entry.get("mode")
        pieces = (rule.source,) + rule.members if mode == "legacy" else rule.components if mode == "v2" else None
        if not pieces:
            raise AssertionError("ledger mode has no proven source group")
        dim = entry["dimension"]
        origin = entry["origin"]
        if len(origin) != 3 or any(type(value) is not int for value in origin):
            raise AssertionError("ledger origin is not an integer block position")
        source = set()
        for piece in pieces:
            point = tuple(origin[i] + piece.offset[i] for i in range(3))
            if before.get(dim, point) != piece.state:
                raise AssertionError("ledger source group does not match original world")
            source.add(point)
        if len(source) != len(pieces) or sorted(map(tuple, entry["source"])) != sorted(source):
            raise AssertionError("ledger source positions do not match rule")
        root = tuple(origin[i] + rule.root_offset[i] for i in range(3))
        if tuple(entry["targetRoot"]) != root:
            raise AssertionError("ledger moved the root outside its rule")
        writes = {tuple(root[i] + offset[i] for i in range(3)): (PART, ()) for offset in rule.shape}
        writes[root] = rule.target
        helpers = proved_helpers(before, dim, origin, root, rule, pieces, owned)
        stale = helpers - set(writes)
        if sorted(map(tuple, entry["staleRemoved"])) != sorted(stale):
            raise AssertionError("ledger stale-helper set is not provably owned")
        touched = source | stale | set(writes)
        reported_effects.add(effect_key(dim, source, touched, writes))
        expected_changes = {}
        for point in touched:
            old = before.get(dim, point)
            if old is None:
                raise AssertionError("ledger targets a missing chunk")
            entity = old_entities.get((dim, *point))
            is_owned = point in helpers and old == (PART, ())
            if point in stale and not is_owned:
                raise AssertionError("ledger removes a foreign stale helper")
            if entity is not None and not is_owned:
                raise AssertionError("ledger destroys a foreign block entity")
            if point in writes and point not in source and old[0] not in AIR_NAMES and not is_owned:
                raise AssertionError("ledger overwrites a foreign block")
            for name in ("block_ticks", "TileTicks", "fluid_ticks", "LiquidTicks"):
                ticks = before.chunk(dim, point[0], point[2]).root().get(name)
                for tick in ticks.value if ticks is not None else ():
                    data = compound(tick)
                    if all(axis in data for axis in ("x", "y", "z")) and tuple(data[axis].value for axis in ("x", "y", "z")) == point:
                        raise AssertionError("ledger changes a scheduled-tick position")
            location = (dim, *point)
            if location in reserved:
                raise AssertionError("ledger has overlapping object edits")
            reserved.add(location)
            expected_changes[point] = (block_state_key(as_tag_state(old)), block_state_key(as_tag_state(writes.get(point, AIR))))
        actual_changes = {}
        for change in entry["changes"]:
            point = tuple(change["position"])
            if point in actual_changes:
                raise AssertionError("duplicate ledger change")
            actual_changes[point] = (change["before"], change["after"])
        if actual_changes != expected_changes:
            raise AssertionError("ledger before/after changes are not authorized by its rule")
        expected_helpers = {point: {"position": list(point), "id": PART, "Root": block_pos_long(*root), "Owner": rule.target[0]}
                            for point in writes if point != root}
        actual_helpers = {tuple(helper["position"]): helper for helper in entry["helpers"]}
        if len(actual_helpers) != len(entry["helpers"]) or actual_helpers != expected_helpers:
            raise AssertionError("ledger helper data does not match target geometry")
    if report.get("counts", {}).get("converted") != len(report["ledger"]):
        raise AssertionError("conversion count differs from ledger")
    expected_effects = independently_accepted_effects(before, rules, old_entities, owned)
    if reported_effects != expected_effects:
        raise AssertionError(f"ledger differs from independently accepted groups: missing={len(expected_effects - reported_effects)}, unexpected={len(reported_effects - expected_effects)}")
    return {(dim, x // 16, z // 16) for dim, x, y, z in reserved}


def check(source: Path, converted: Path, report_path: Path, resources: Path) -> dict:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    for filename, field in (("migration.json", "migrationSha256"), ("geometry.json", "geometrySha256")):
        if hashlib.sha256((resources / filename).read_bytes()).hexdigest() != report.get("resources", {}).get(field):
            raise AssertionError(f"resource hash changed since conversion: {filename}")
    definitions_hashes = {}
    for name, path in (("legacy", resources.parent / "definitions.json"), ("logical", resources / "definitions.json"),
                       ("legacyGeometry", resources.parent / "geometry.json")):
        definitions_hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
    if definitions_hashes != report.get("resources", {}).get("definitionsSha256"):
        raise AssertionError("resource definitions changed since conversion")
    rules, defaults = parse_rules(resources)
    source = source.resolve()
    declared = report.get("source", {})
    kind, hashes = declared.get("kind"), declared.get("hashes")
    if not isinstance(hashes, dict):
        raise AssertionError("conversion report has no source hashes")
    if source.is_dir():
        if kind != "directory" or hashes != hash_tree(source):
            raise AssertionError("directory source hash does not match conversion report")
        temporary = None
        source_world = source
    elif source.is_file() and zipfile.is_zipfile(source):
        actual = {"archive": hashlib.sha256(source.read_bytes()).hexdigest()}
        if kind != "zip" or hashes != actual:
            raise AssertionError("ZIP source hash does not match conversion report")
        temporary = tempfile.TemporaryDirectory(prefix="logical-world-check-")
        source_world = safe_extract(source, Path(temporary.name))
    else:
        raise AssertionError("source must be a Java world directory or ZIP archive")
    try:
        source_files, output_files = hash_tree(source_world), hash_tree(converted)
        if set(source_files) != set(output_files):
            raise AssertionError("converted world changed the file set")
        region_files = {p.relative_to(source_world).as_posix() for p in source_world.glob("**/region/r.*.*.mca")}
        external_files = set(source_files) - region_files
        if any(source_files[name] != output_files[name] for name in external_files):
            raise AssertionError("non-block-region data changed (level/player/entities/POI or other files)")
        before, after = World(source_world, defaults), World(converted.resolve(), defaults)
        if set(before.chunks) != set(after.chunks):
            raise AssertionError("converted world changed the chunk set")
        touched_chunks = validate_ledger(before, report, rules)
        allowed = {}
        allowed_sections = {}
        expected_helpers = {}
        for entry in report["ledger"]:
            dim = entry["dimension"]
            for change in entry["changes"]:
                x, y, z = change["position"]
                allowed[(dim, x, y, z)] = change["after"]
                allowed_sections.setdefault((dim, x // 16, z // 16, y // 16), {})[(y & 15) * 256 + (z & 15) * 16 + (x & 15)] = change["after"]
            for helper in entry["helpers"]:
                expected_helpers[(dim, *helper["position"])] = helper
        # Non-block fields are compared section-by-section except the explicitly
        # invalidated lighting/height cache; conversion rejects touched ticks.
        cache_root = {"Heightmaps", "isLightOn", "starlight.light_version"}
        cache_section = {"BlockLight", "SkyLight", "starlight.blocklight_state", "starlight.skylight_state"}
        checked_blocks = 0
        seen_helpers = set()
        seen_allowed = set()
        for key in before.chunks:
            left, right = before.chunks[key].root(), after.chunks[key].root()
            touched = key in touched_chunks
            ignored_root = {"sections", "block_entities", "TileEntities"} | (cache_root if touched else set())
            ignored_section = {"block_states"} | (cache_section if touched else set())
            if touched and cache_root & set(right):
                raise AssertionError("changed chunk retains invalid height/light cache")
            if {k: v for k, v in left.items() if k not in ignored_root} != {k: v for k, v in right.items() if k not in ignored_root}:
                raise AssertionError(f"chunk NBT changed outside permitted fields: {key}")
            left_sections = {int(compound(s)["Y"].value): s for s in left.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}
            right_sections = {int(compound(s)["Y"].value): s for s in right.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}
            for sy in set(left_sections) | set(right_sections):
                a = compound(left_sections[sy]) if sy in left_sections else {}
                b = compound(right_sections[sy]) if sy in right_sections else {}
                if touched and cache_section & set(b):
                    raise AssertionError("changed chunk section retains invalid light cache")
                if {k: v for k, v in a.items() if k not in ignored_section} != {k: v for k, v in b.items() if k not in ignored_section}:
                    raise AssertionError(f"section NBT changed outside permitted fields: {key}/{sy}")
                codes = {}
                old_codes = section_codes(left_sections.get(sy), defaults, codes)
                new_codes = section_codes(right_sections.get(sy), defaults, codes)
                states_by_code = {code: state for state, code in codes.items()}
                section_allowed = allowed_sections.get((*key, sy), {})
                inspect = set(int(i) for i in np.flatnonzero(old_codes != new_codes)) | set(section_allowed)
                for index in inspect:
                    old, new = states_by_code[int(old_codes[index])], states_by_code[int(new_codes[index])]
                    point = (key[0], key[1] * 16 + (index & 15), sy * 16 + (index >> 8), key[2] * 16 + ((index >> 4) & 15))
                    expected = allowed.get(point)
                    if expected is None and old != new:
                        raise AssertionError(f"block changed outside ledger at {point}: {old} -> {new}")
                    if expected is not None and block_state_key(as_tag_state(new)) != expected:
                        raise AssertionError(f"ledger mismatch at {point}: expected {expected}, got {block_state_key(as_tag_state(new))}")
                    if expected is not None:
                        seen_allowed.add(point)
                checked_blocks += 4096
            old_entities, new_entities = entities(before.chunks[key]), entities(after.chunks[key])
            for point in set(old_entities) | set(new_entities):
                if point in expected_helpers:
                    seen_helpers.add(point)
                    data = compound(new_entities.get(point, Tag(TAG_COMPOUND, {})))
                    expected = expected_helpers[point]
                    if data != {"id": Tag(TAG_STRING, PART), "x": Tag(TAG_INT, point[1]),
                                "y": Tag(TAG_INT, point[2]), "z": Tag(TAG_INT, point[3]),
                                "Root": Tag(TAG_LONG, expected["Root"]), "Owner": Tag(TAG_STRING, expected["Owner"])}:
                        raise AssertionError(f"bad helper entity at {point}")
                elif point not in allowed and old_entities.get(point) != new_entities.get(point):
                    raise AssertionError(f"block entity changed outside ledger at {point}")
                elif point in allowed and point not in expected_helpers and point in new_entities:
                    raise AssertionError(f"unexpected entity at converted position {point}")
        missing_helpers = set(expected_helpers) - seen_helpers
        if missing_helpers:
            raise AssertionError(f"missing helper entities from ledger: {sorted(missing_helpers)}")
        if set(allowed) != seen_allowed:
            raise AssertionError("ledger includes positions missing from converted sections")
        return {"format": "bloodborne-logical-world-check-v1", "ledgerEntries": len(report["ledger"]), "chunks": len(after.chunks), "checkedBlocks": checked_blocks, "unchangedExternalFiles": len(external_files), "sourceHashVerified": True, "resourceHashesVerified": True, "ok": True}
    finally:
        if temporary is not None:
            temporary.cleanup()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("converted", type=Path)
    parser.add_argument("report", type=Path)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(check(args.source, args.converted, args.report, args.resources), ensure_ascii=False))


if __name__ == "__main__":
    main()
