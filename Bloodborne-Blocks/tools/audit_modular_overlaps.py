#!/usr/bin/env python3
"""Audit visual-risk overlaps in the offline modular world conversion.

This reconstructs complex expansion cells from the original world and v2
migration table.  It never writes either world.  A temporary SQLite database
keeps memory bounded while joining roughly a million pending cells back to the
original protected blocks.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import sqlite3
import tempfile
from pathlib import Path

import numpy as np

from world_io import *


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
V2 = RES / "bloodborne_blocks/v2"
NS = "bloodborne_blocks:"
AIR_NAMES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def properties(entry: Tag) -> dict[str, str]:
    tag = compound(entry).get("Properties")
    return {} if tag is None else {key: value.value for key, value in compound(tag).items()}


def state_key(values: dict[str, str]) -> str:
    return ",".join(f"{key}={value}" for key, value in sorted(values.items()))


def unpack_fast(states: dict[str, Tag]) -> np.ndarray:
    palette = states["palette"].value
    if len(palette) == 1:
        return np.zeros(4096, dtype=np.int32)
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    longs = np.array([value & ((1 << 64) - 1) for value in states["data"].value], dtype=np.uint64)
    shifts = np.arange(0, per_long * bits, bits, dtype=np.uint64)
    result = ((longs[:, None] >> shifts) & ((1 << bits) - 1)).ravel()[:4096].astype(np.int32)
    if result.max(initial=0) >= len(palette):
        raise ValueError("invalid packed palette index")
    return result


def replacement(entry: Tag, migration: dict) -> tuple[str, object | None]:
    data = compound(entry)
    name = data["Name"].value
    if not name.startswith(NS):
        return "foreign", None
    ident = name[len(NS):]
    if ident == "architecture_part":
        return "helper", None
    spec = migration.get(ident)
    if spec is None:
        raise ValueError(f"unknown legacy architecture ID {ident}")
    values = {**spec["default"], **properties(entry)}
    key = state_key(values)
    if key not in spec["states"]:
        raise ValueError(f"unmapped state {ident}[{key}]")
    value = spec["states"][key]
    if isinstance(value, dict):
        return "retained_legacy", value
    if len(value) == 1 and value[0]["offset"] == [0, 0, 0]:
        return "simple", value
    return "complex", value


def helper_positions(root: dict[str, Tag], kept: set[str], migration: dict) -> tuple[dict, dict, dict]:
    retained = {}
    phase1_removed = {}
    orphan_spruce_buttons = {}
    for entity in root.get("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        data = compound(entity)
        if data.get("id", Tag(TAG_STRING, "")).value != NS + "architecture_part":
            continue
        owner = data.get("Owner", Tag(TAG_STRING, "")).value.removeprefix(NS)
        position = (data["x"].value, data["y"].value, data["z"].value)
        owner_has_retained_state = owner in migration and any(isinstance(value, dict) for value in migration[owner]["states"].values())
        if owner in kept and owner_has_retained_state:
            retained[position] = owner
        else:
            phase1_removed[position] = owner
            # This owner was accidentally allowed by the conversion run even
            # though its migration emits no root. The verifier found exactly
            # these helpers orphaned in that output.
            if owner == "spruce_button":
                orphan_spruce_buttons[position] = owner
    return retained, phase1_removed, orphan_spruce_buttons


NONFULL_TOKENS = (
    "glass", "pane", "fence", "wall", "stairs", "slab", "door", "trapdoor",
    "leaves", "torch", "lantern", "chain", "rail", "carpet", "vine", "flower",
    "grass", "sapling", "mushroom", "coral", "button", "pressure_plate", "sign",
    "banner", "bed", "chest", "barrel", "cauldron", "hopper", "cobweb", "ladder",
    "scaffolding", "farmland", "path", "candle", "pot", "head", "skull", "anvil",
)


def foreign_visual_class(block: str) -> tuple[str, str]:
    name = block.split("[", 1)[0]
    if name in {"minecraft:barrier", "minecraft:structure_void", "minecraft:light"}:
        return "visible_risk_invisible_protector", "high"
    if name in {"minecraft:water", "minecraft:lava"} or "waterlogged=true" in block:
        return "visible_risk_translucent_or_fluid", "high"
    if not name.startswith("minecraft:"):
        return "visible_risk_foreign_mod_shape_unknown", "high"
    if any(token in name for token in NONFULL_TOKENS):
        return "visible_risk_nonfull_or_translucent_vanilla", "high"
    return "probably_occluded_by_opaque_vanilla_cube", "low"


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def external_hash_audit(source: Path, target: Path) -> dict:
    excluded = {"level.dat", "level.dat_old", "session.lock"}

    def included(root: Path, path: Path) -> bool:
        relative = path.relative_to(root).as_posix()
        return not relative.startswith("region/") and relative not in excluded

    source_files = {path.relative_to(source).as_posix(): path for path in source.rglob("*") if path.is_file() and included(source, path)}
    target_files = {path.relative_to(target).as_posix(): path for path in target.rglob("*") if path.is_file() and included(target, path)}
    missing = sorted(source_files.keys() - target_files.keys())
    extra = sorted(target_files.keys() - source_files.keys())
    changed = []
    matched = 0
    for name in sorted(source_files.keys() & target_files.keys()):
        if file_hash(source_files[name]) == file_hash(target_files[name]):
            matched += 1
        else:
            changed.append(name)
    return {
        "pathNormalization": "Path.relative_to(...).as_posix()",
        "excluded": sorted(excluded | {"region/**"}),
        "matchedFiles": matched,
        "changedFiles": changed,
        "missingFiles": missing,
        "extraFiles": extra,
        "passed": not changed and not missing and not extra,
    }


def _bucket() -> dict:
    return {
        "positions": 0,
        "pieces": 0,
        "roots": set(),
        "pieceTypes": collections.Counter(),
        "rootTypes": collections.Counter(),
        "protectedStates": collections.Counter(),
        "examples": [],
    }


def audit(source: Path, target: Path, output: Path, conflicts_output: Path | None = None) -> dict:
    source, target = source.resolve(), target.resolve()
    if not source.is_dir() or not target.is_dir():
        raise ValueError("source and completed target worlds must both exist")
    migration = json.loads((V2 / "migration.json").read_text(encoding="utf-8"))
    kept = set(json.loads((V2 / "legacy-creative.json").read_text(encoding="utf-8")))
    definitions = {block["id"]: block for block in json.loads((V2 / "definitions.json").read_text(encoding="utf-8"))["blocks"]}
    counters = collections.Counter()
    root_type_counts = collections.Counter()
    removable_helper_owners = collections.Counter()
    conflicts_output = conflicts_output or output.with_name("world-overlap-conflicts-v2.json")
    conflicts_output.parent.mkdir(parents=True, exist_ok=True)
    conflict_temporary = conflicts_output.with_suffix(conflicts_output.suffix + ".tmp")
    conflict_stream = conflict_temporary.open("w", encoding="utf-8")
    conflict_stream.write('{"format":"bloodborne-world-overlap-conflicts-v2","localCoordinates":true,"conflicts":[\n')
    first_conflict = True

    with tempfile.TemporaryDirectory(prefix="bloodborne-overlap-audit-") as temporary:
        database = sqlite3.connect(Path(temporary) / "pending.sqlite")
        database.execute("PRAGMA journal_mode=OFF")
        database.execute("PRAGMA synchronous=OFF")
        database.execute("PRAGMA temp_store=MEMORY")
        database.execute("""CREATE TABLE pending(
            cx INTEGER, cz INTEGER, px INTEGER, py INTEGER, pz INTEGER,
            rx INTEGER, ry INTEGER, rz INTEGER, owner TEXT, owner_state TEXT,
            piece TEXT, facing TEXT)""")
        batch = []
        for path in sorted(source.glob("region/r.*.*.mca")):
            region = RegionFile.open(path)
            for stored in region.chunks():
                root = compound(stored.nbt().root)
                cx, cz = root["xPos"].value, root["zPos"].value
                for section in root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                    section_data = compound(section)
                    if "block_states" not in section_data:
                        continue
                    states = compound(section_data["block_states"])
                    palette = states["palette"].value
                    complex_indices = {}
                    for index, entry in enumerate(palette):
                        kind, value = replacement(entry, migration)
                        if kind == "complex":
                            complex_indices[index] = value
                    if not complex_indices:
                        continue
                    array = unpack_fast(states)
                    sy = section_data["Y"].value
                    for index in np.flatnonzero(np.isin(array, list(complex_indices))):
                        integer = int(index)
                        entry = palette[int(array[integer])]
                        owner = compound(entry)["Name"].value.removeprefix(NS)
                        owner_state = block_state_key(entry)
                        root_position = (cx * 16 + (integer & 15), sy * 16 + (integer >> 8), cz * 16 + ((integer >> 4) & 15))
                        components = complex_indices[int(array[integer])]
                        counters["expandedRoots"] += 1
                        root_type_counts[owner_state] += 1
                        for component in components:
                            position = tuple(root_position[axis] + component["offset"][axis] for axis in range(3))
                            batch.append((position[0] // 16, position[2] // 16, *position, *root_position,
                                          owner, owner_state, component["id"], component["properties"]["facing"]))
                            counters["pendingPieces"] += 1
                        if len(batch) >= 50000:
                            database.executemany("INSERT INTO pending VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", batch)
                            batch.clear()
            print("Reconstructed", path.name, counters["expandedRoots"], counters["pendingPieces"], flush=True)
        if batch:
            database.executemany("INSERT INTO pending VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", batch)
        database.execute("CREATE INDEX pending_chunk_position ON pending(cx,cz,px,py,pz)")
        database.commit()
        counters["pendingPositions"] = database.execute("SELECT COUNT(*) FROM (SELECT 1 FROM pending GROUP BY px,py,pz)").fetchone()[0]

        categories: dict[str, dict] = collections.defaultdict(_bucket)
        target_mismatches = []
        for path in sorted(source.glob("region/r.*.*.mca")):
            before = RegionFile.open(path)
            after_path = target / path.relative_to(source)
            after = RegionFile.open(after_path) if after_path.exists() else None
            after_chunks = {(chunk.x, chunk.z): chunk for chunk in after.chunks()} if after else {}
            for stored in before.chunks():
                root = compound(stored.nbt().root)
                cx, cz = root["xPos"].value, root["zPos"].value
                retained_helpers, phase1_removed_helpers, removable_helpers = helper_positions(root, kept, migration)
                counters["retainedHelperEntities"] += len(retained_helpers)
                counters["normalPhase1RemovedHelperEntities"] += len(phase1_removed_helpers) - len(removable_helpers)
                counters["removableOrphanHelperEntities"] += len(removable_helpers)
                removable_helper_owners.update(removable_helpers.values())
                pending_positions = database.execute(
                    "SELECT px,py,pz,COUNT(*) FROM pending WHERE cx=? AND cz=? GROUP BY px,py,pz", (cx, cz)).fetchall()
                if not pending_positions:
                    continue
                sections = {compound(section)["Y"].value: compound(section) for section in root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}
                section_cache = {}
                target_root = compound(after_chunks[(stored.x, stored.z)].nbt().root) if (stored.x, stored.z) in after_chunks else None
                target_sections = ({compound(section)["Y"].value: compound(section) for section in target_root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}
                                   if target_root else {})
                target_cache = {}

                def state_at(section_map, cache, x, y, z):
                    sy = y // 16
                    section = section_map.get(sy)
                    if section is None or "block_states" not in section:
                        return None
                    if sy not in cache:
                        states = compound(section["block_states"])
                        cache[sy] = (states["palette"].value, unpack_fast(states))
                    palette, array = cache[sy]
                    index = (y % 16) * 256 + (z % 16) * 16 + x % 16
                    return palette[int(array[index])]

                for x, y, z, piece_count in pending_positions:
                    entry = state_at(sections, section_cache, x, y, z)
                    if entry is None:
                        counters["missingSectionPositions"] += 1
                        continue
                    block = block_state_key(entry)
                    name = compound(entry)["Name"].value
                    category = severity = None
                    if not name.startswith(NS) and name not in AIR_NAMES:
                        category, severity = foreign_visual_class(block)
                    elif name == NS + "architecture_part" and (x, y, z) in retained_helpers:
                        category, severity = "visible_risk_retained_helper_cell", "high"
                    elif name == NS + "architecture_part" and (x, y, z) in removable_helpers:
                        category, severity = "removable_orphan_helper_overlap", "repair"
                    elif name.startswith(NS) and name != NS + "architecture_part":
                        kind, _ = replacement(entry, migration)
                        if kind == "retained_legacy":
                            category, severity = "composition_risk_retained_legacy", "medium"
                    if category is None:
                        continue
                    rows = database.execute(
                        "SELECT rx,ry,rz,owner,owner_state,piece,facing FROM pending WHERE cx=? AND cz=? AND px=? AND py=? AND pz=?",
                        (cx, cz, x, y, z)).fetchall()
                    full_conflict = {
                        "position": [x, y, z],
                        "category": category,
                        "severity": severity,
                        "protectedBlock": block,
                        "incomingPieces": [{
                            "id": row[5], "facing": row[6], "root": list(row[:3]),
                            "owner": row[3], "rootState": row[4],
                        } for row in rows],
                    }
                    if not first_conflict:
                        conflict_stream.write(",\n")
                    conflict_stream.write(json.dumps(full_conflict, ensure_ascii=False, separators=(",", ":")))
                    first_conflict = False
                    bucket = categories[category]
                    bucket["positions"] += 1
                    bucket["pieces"] += len(rows)
                    bucket["protectedStates"][block] += 1
                    for rx, ry, rz, owner, owner_state, piece, facing in rows:
                        bucket["roots"].add((rx, ry, rz))
                        bucket["pieceTypes"][piece] += 1
                        bucket["rootTypes"][owner_state] += 1
                    example_limit = 1000 if category == "removable_orphan_helper_overlap" else 12
                    if len(bucket["examples"]) < example_limit:
                        bucket["examples"].append({
                            "position": [x, y, z],
                            "protectedBlock": block,
                            "severity": severity,
                            "incomingPieces": [{"id": row[5], "facing": row[6], "root": list(row[:3]), "owner": row[3]} for row in rows[:8]],
                            "additionalPieceCount": max(0, len(rows) - 8),
                        })
                    protected = category != "removable_orphan_helper_overlap"
                    if protected:
                        counters["protectedOverlapPositions"] += 1
                        counters["protectedOverlapPieces"] += len(rows)
                    else:
                        counters["orphanHelperOverlapPositions"] += 1
                        counters["orphanHelperIncomingPiecesToRestore"] += len(rows)
                    counters["severity:" + severity + ":positions"] += 1
                    counters["severity:" + severity + ":pieces"] += len(rows)
                    target_entry = state_at(target_sections, target_cache, x, y, z) if target_root else None
                    if protected and (target_entry is None or block_state_key(target_entry) != block):
                        if len(target_mismatches) < 50:
                            target_mismatches.append({"position": [x, y, z], "expected": block,
                                                      "actual": block_state_key(target_entry) if target_entry else None})

        serial_categories = {}
        for name, bucket in sorted(categories.items()):
            serial_categories[name] = {
                "positions": bucket["positions"],
                "pieces": bucket["pieces"],
                "distinctRoots": len(bucket["roots"]),
                "distinctPieceTypes": len(bucket["pieceTypes"]),
                "topPieceTypes": dict(bucket["pieceTypes"].most_common(25)),
                "topRootStates": dict(bucket["rootTypes"].most_common(25)),
                "topProtectedStates": dict(bucket["protectedStates"].most_common(25)),
                "examples": bucket["examples"],
            }
            if name == "removable_orphan_helper_overlap":
                serial_categories[name]["positionsWithIncomingPieces"] = serial_categories[name].pop("examples")
        database.close()

    conflict_stream.write("\n]}\n")
    conflict_stream.close()
    os.replace(conflict_temporary, conflicts_output)

    high_pieces = counters["severity:high:pieces"]
    medium_pieces = counters["severity:medium:pieces"]
    meaningful = high_pieces > 100 or (high_pieces + medium_pieces) / max(1, counters["pendingPieces"]) >= 0.001
    suggestions = []
    if counters["severity:high:pieces"]:
        suggestions.append("Inspect high-risk examples in game or a renderer; invisible helpers, fluids, transparent/non-full and unknown mod blocks do not prove geometric occlusion.")
    if "composition_risk_retained_legacy" in serial_categories:
        suggestions.append("For retained legacy cells, preserve the old model and merge incoming modular pieces into a dedicated composite when their meshes do not overlap; one-block storage cannot render both independently.")
    if "probably_occluded_by_opaque_vanilla_cube" in serial_categories:
        suggestions.append("Opaque vanilla full-cell overlaps are probably intentional hidden intersections and can remain skipped unless visual inspection shows exposed faces.")
    report = {
        "format": "bloodborne-world-overlap-audit-v2",
        "method": {
            "sourceOfTruth": "original copied world plus v2 migration.json",
            "protectedDefinition": ["foreign non-air block", "retained legacy migration state", "architecture_part whose BlockEntity Owner is retained"],
            "visualAssessment": "Conservative state-name classification. High-risk means occlusion is not established; it does not prove visible damage.",
            "worldsModified": False,
        },
        "counts": dict(sorted(counters.items())),
        "rootTypes": {"distinctStates": len(root_type_counts), "topStates": dict(root_type_counts.most_common(30))},
        "removableOrphanHelpers": {
            "entities": counters["removableOrphanHelperEntities"],
            "owners": dict(removable_helper_owners.most_common()),
            "overlapPositions": counters["orphanHelperOverlapPositions"],
            "incomingPiecesToRestore": counters["orphanHelperIncomingPiecesToRestore"],
            "action": "Remove the orphan helper block/entity, then allow reconstructed incoming modular pieces to occupy the cell.",
        },
        "categories": serial_categories,
        "fullConflictFile": conflicts_output.name,
        "visualLoss": {
            "meaningfulRisk": meaningful,
            "highRiskPieces": high_pieces,
            "mediumRiskPieces": medium_pieces,
            "highRiskFractionOfPendingPieces": high_pieces / max(1, counters["pendingPieces"]),
            "mediumOrHighFractionOfPendingPieces": (high_pieces + medium_pieces) / max(1, counters["pendingPieces"]),
            "targetProtectedStateMismatchCount": len(target_mismatches),
            "targetProtectedStateMismatchExamples": target_mismatches,
            "suggestions": suggestions,
        },
        "externalFiles": external_hash_audit(source, target),
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("output", type=Path, nargs="?", default=ROOT / "docs/world-overlap-audit-v2.json")
    parser.add_argument("--conflicts-output", type=Path)
    args = parser.parse_args()
    report = audit(args.source, args.target, args.output, args.conflicts_output)
    print(json.dumps({"counts": report["counts"], "visualLoss": report["visualLoss"], "externalFiles": report["externalFiles"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
