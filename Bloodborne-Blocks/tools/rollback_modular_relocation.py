#!/usr/bin/env python3
"""Safely reverse exactly one applied relocation ledger on ether-v2b."""

from __future__ import annotations

import argparse
import collections
import json
from pathlib import Path

from build_modular_palette import ROOT
from convert_modular_world import tag_state
from relocate_modular_conflicts import WorldEditor, moved_entity, normalized_entity_hash, parse_state
from world_io import Tag, TAG_STRING, block_state_key, compound, read_nbt


def entry(text):
    name, properties = parse_state(text);return tag_state(name, properties)


def position(row, key):return tuple(int(value) for value in row[key])


def rollback(target, source_report, archived_report, output):
    target = Path(target).resolve();source_report = Path(source_report).resolve();archived_report = Path(archived_report).resolve();output = Path(output).resolve()
    if not source_report.is_file():raise ValueError("source relocation report is missing")
    if archived_report.exists():raise ValueError("archived relocation report already exists")
    report = json.loads(source_report.read_text(encoding="utf-8"))
    if not report.get("applied"):raise ValueError("relocation report is not applied")
    if Path(report.get("target", "")).resolve() != target:raise ValueError("relocation report targets a different world")
    level = compound(compound(read_nbt(target / "level.dat").root)["Data"])
    if target.name != "ether-v2b" or not level.get("LevelName", Tag(TAG_STRING, "")).value.startswith("Ether - Bloodborne 2.0"):
        raise ValueError("rollback is restricted to generated ether-v2b")
    world = WorldEditor(target);changes = report.get("changes", []);objects = report.get("movedObjects", [])
    seen = set();state_plan = []
    for change in changes:
        pos = position(change, "position")
        if pos in seen:raise ValueError(f"duplicate change position {pos}")
        seen.add(pos);actual = world.state(pos)
        if actual is None or block_state_key(actual) != change["newState"]:
            raise ValueError(f"current state mismatch at {pos}: {None if actual is None else block_state_key(actual)} != {change['newState']}")
        state_plan.append((pos, entry(change["oldState"]), change["newState"], change["oldState"]))

    moves = [];synthetic = [];claimed_destinations = set();claimed_sources = set()
    for obj in objects:
        old_anchor, new_anchor = position(obj, "oldAnchor"), position(obj, "newAnchor")
        delta = tuple(old_anchor[i] - new_anchor[i] for i in range(3))
        by_destination = collections.defaultdict(list)
        for item in obj.get("blockEntities", []):by_destination[position(item, "newPosition")].append(item)
        for dest, expected in by_destination.items():
            if dest in claimed_destinations:raise ValueError(f"duplicate block entity destination {dest}")
            claimed_destinations.add(dest);actual = world.entities_at(dest)
            actual_by_hash = {normalized_entity_hash(entity, new_anchor): entity for entity in actual}
            if len(actual_by_hash) != len(actual):raise ValueError(f"duplicate block entity hashes at {dest}")
            if set(actual_by_hash) != {item["newNormalizedHash"] for item in expected}:raise ValueError(f"block entity mismatch at {dest}")
            for item in expected:
                source = position(item, "oldPosition")
                if source in claimed_sources:raise ValueError(f"duplicate block entity source {source}")
                claimed_sources.add(source)
                if world.entities_at(source):raise ValueError(f"block entity source is not empty at {source}")
                entity = actual_by_hash[item["newNormalizedHash"]]
                restored = moved_entity(entity, delta)
                if normalized_entity_hash(restored, old_anchor) != item["oldNormalizedHash"]:raise ValueError(f"reverse block entity hash mismatch at {source}")
                moves.append((dest, source, restored, item))
        for item in obj.get("syntheticBlockEntities", []):
            dest = position(item, "position")
            if dest in claimed_destinations:raise ValueError(f"synthetic/real block entity overlap at {dest}")
            claimed_destinations.add(dest);actual = world.entities_at(dest)
            if len(actual) != 1 or normalized_entity_hash(actual[0], new_anchor) != item["normalizedHash"]:
                raise ValueError(f"synthetic helper mismatch at {dest}")
            synthetic.append((dest, item))

    # Full preflight succeeded. From here all mutations stay in memory until one commit.
    for pos, restored, _, _ in state_plan:world.set_state(pos, restored)
    for dest, _ in synthetic:world.remove_entities(dest)
    for dest in {item[0] for item in moves}:world.remove_entities(dest)
    entity_moves = []
    for dest, source, restored, item in moves:
        world.add_entity(source, restored);entity_moves.append({"from": list(dest), "to": list(source), "id": item["id"],
                                                                 "oldNormalizedHash": item["newNormalizedHash"], "newNormalizedHash": item["oldNormalizedHash"]})
    regions, chunks = world.commit()
    reverse_changes = [{"position": list(pos), "oldState": current, "newState": restored} for pos, _, current, restored in state_plan]
    result = {"format": "bloodborne-world-static-relocation-rollback-v2", "applied": True, "target": str(target),
              "sourceReport": str(archived_report), "aggregate": {"changes": len(reverse_changes), "restoredBlockEntities": len(moves),
              "removedSyntheticHelpers": len(synthetic), "changedRegions": regions, "changedChunks": chunks},
              "changes": reverse_changes, "entityMoves": entity_moves,
              "removedSyntheticBlockEntities": [{"position": list(pos), "normalizedHash": item["normalizedHash"]} for pos, item in synthetic]}
    output.parent.mkdir(parents=True, exist_ok=True);output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    source_report.replace(archived_report)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument("target", type=Path)
    parser.add_argument("--source", type=Path, default=ROOT / "docs/world-static-relocation-v2.json")
    parser.add_argument("--archive", type=Path, default=ROOT / "docs/world-static-relocation-rolled-back-v2.json")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/world-static-relocation-reverted-v2.json")
    args = parser.parse_args();print(json.dumps(rollback(args.target, args.source, args.archive, args.output)["aggregate"], ensure_ascii=False))


if __name__ == "__main__":main()
