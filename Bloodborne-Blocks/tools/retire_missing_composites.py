#!/usr/bin/env python3
"""Retire user-approved unresolved Bloodborne composite cells from a copied world.

This is deliberately narrower than a world converter.  It refuses every input
other than the frozen source ZIP, checks each recorded state before changing it,
and writes a fresh ZIP plus an auditable ledger.  The source archive is never
opened for writing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import zipfile
from pathlib import Path
from typing import Any

from convert_logical_world import AIR_NAME, World, as_tag_state, block_state_key, copy_source, write_report

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_SOURCE_SHA256 = "c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_state(value: str) -> tuple[str, tuple[tuple[str, str], ...]]:
    name, separator, raw_properties = value.partition("[")
    if not separator:
        return name, ()
    if not raw_properties.endswith("]"):
        raise ValueError(f"invalid saved state: {value!r}")
    pairs = [] if raw_properties == "]" else [item.split("=", 1) for item in raw_properties[:-1].split(",")]
    if any(len(item) != 2 or not item[0] for item in pairs):
        raise ValueError(f"invalid saved state properties: {value!r}")
    return name, tuple(sorted((key, item_value) for key, item_value in pairs))


def display_state(state: tuple[str, tuple[tuple[str, str], ...]]) -> str:
    return block_state_key(as_tag_state(state))


def load_plan(path: Path) -> list[dict[str, Any]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("retirement plan must be an object")
    rows: list[dict[str, Any]] = []
    for short_id, entries in raw.items():
        full_id = "bloodborne_blocks:" + str(short_id)
        if not short_id.startswith("m_") or not isinstance(entries, list):
            raise ValueError(f"invalid plan entry for {short_id!r}")
        for entry in entries:
            if not isinstance(entry, dict) or entry.get("dimension") != "minecraft:overworld":
                raise ValueError(f"invalid dimension for {short_id!r}")
            position = entry.get("pos")
            state = entry.get("state")
            if not isinstance(position, list) or len(position) != 3 or not all(type(v) is int for v in position):
                raise ValueError(f"invalid position for {short_id!r}")
            if not isinstance(state, str) or not state.startswith(full_id + "["):
                raise ValueError(f"state does not belong to {short_id!r}")
            rows.append({"id": full_id, "dimension": entry["dimension"], "position": tuple(position), "before": parse_state(state)})
    keys = [(row["dimension"], row["position"]) for row in rows]
    if len(rows) != 33 or len({row["id"] for row in rows}) != 23 or len(set(keys)) != len(keys):
        raise ValueError("plan must contain exactly 23 IDs and 33 unique cells")
    return sorted(rows, key=lambda row: (row["id"], row["position"]))


def write_zip(world: Path, output: Path, root_name: str) -> None:
    temporary = output.with_name(output.name + ".tmp")
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in sorted(world.rglob("*")):
                if path.is_file():
                    archive.write(path, (Path(root_name) / path.relative_to(world)).as_posix())
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            temporary.unlink()


def retire(source: Path, output: Path, plan_path: Path, report_path: Path) -> dict[str, Any]:
    source, output, plan_path, report_path = (path.resolve() for path in (source, output, plan_path, report_path))
    if output.exists() or report_path.exists():
        raise ValueError("output ZIP and report must be new files")
    if sha256(source) != EXPECTED_SOURCE_SHA256:
        raise ValueError("source ZIP SHA-256 does not match the frozen MODDED input")
    plan = load_plan(plan_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="retire-missing-", dir=str(output.parent)) as temporary:
        copied, source_hashes, source_kind = copy_source(source, Path(temporary))
        world = World(copied, {})
        positions = {row["position"] for row in plan}
        entities = world.block_entities()
        entity_positions = [(row["dimension"], *row["position"]) for row in plan if (row["dimension"], *row["position"]) in entities]
        if entity_positions:
            raise ValueError(f"refusing to remove cells with block entities: {entity_positions}")
        if world.ticks_at("minecraft:overworld", positions):
            raise ValueError("refusing to remove cells with scheduled ticks")
        for row in plan:
            actual = world.get(row["dimension"], row["position"])
            if actual != row["before"]:
                raise ValueError(f"source state mismatch at {row['position']}: expected {display_state(row['before'])}, got {display_state(actual) if actual else None}")
        for row in plan:
            world.set(row["dimension"], row["position"], (AIR_NAME, ()))
        for row in plan:
            if world.get(row["dimension"], row["position"]) != (AIR_NAME, ()):
                raise ValueError(f"post-write verification failed at {row['position']}")
        world.save()
        write_zip(copied, output, "Bloodborne-City-2.1.0-rc.1")
    report = {
        "schema": 1,
        "operation": "user-approved-retirement-of-unresolved-composites",
        "reason": "IDs are unrelated to the release mod and are intentionally discarded by user instruction",
        "source": {"kind": source_kind, "sha256": source_hashes.get("archive")},
        "plan": {"path": str(plan_path.relative_to(ROOT)), "sha256": sha256(plan_path), "ids": 23, "cells": 33},
        "output": {"path": str(output.relative_to(ROOT)), "sha256": sha256(output)},
        "changes": [{"dimension": row["dimension"], "position": list(row["position"]), "before": display_state(row["before"]), "after": AIR_NAME} for row in plan],
        "result": "PASS",
    }
    write_report(report_path, report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="immutable MODDED source ZIP")
    parser.add_argument("output", type=Path, help="new release ZIP")
    parser.add_argument("--plan", type=Path, default=ROOT / "docs/city-compat/missing-model-positions.json")
    parser.add_argument("--report", type=Path, required=True, help="new JSON ledger")
    args = parser.parse_args()
    print(json.dumps(retire(args.source, args.output, args.plan, args.report), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
