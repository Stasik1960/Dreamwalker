"""Emit separate accepted and known-pending authored grass source censuses."""
from __future__ import annotations

import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path

from source_assembly_index import positions
from source_variant_rng import weighted_index

ROOT = Path(__file__).resolve().parents[1]
INDEX_META = ROOT / "build/complete-accepted-repair/bush-yharnam.sqlite.json.gz"
OUTPUT = ROOT / "docs/accepted-restore/bush-source-census.json.gz"
PENDING_OUTPUT = ROOT / "docs/accepted-restore/known-pending-vegetation.json.gz"
STATE = "minecraft:dead_fire_coral_fan[waterlogged=false]"
SOURCE = ROOT / "reference-inputs/source-world.zip"
PENDING_REASON = "available_authored_variant_without_accepted_whole_object_rule"


def read_index():
    with gzip.open(INDEX_META, "rt", encoding="utf-8") as stream:
        body = json.load(stream)
    if body["database_sha256"] != hashlib.file_digest((ROOT / body["index"]["database"]).open("rb"), "sha256").hexdigest():
        raise ValueError("completed bush index hash mismatch")
    index = body["index"]
    if index["source_sha256"] != hashlib.file_digest(SOURCE.open("rb"), "sha256").hexdigest():
        raise ValueError("source world hash mismatch")
    if index["source_sha256"] != "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51":
        raise ValueError("unexpected source world")
    if index["regions"] != sorted(index["regions"]) or len(index["regions"]) != 33:
        raise ValueError("incomplete yharnam index")
    return index


def build(output=OUTPUT):
    index = read_index()
    found = positions(index, [STATE])
    variants = Counter(weighted_index([100] * 8, point) for point in found)
    rows = []
    for point, source_state in sorted(found.items()):
        if weighted_index([100] * 8, point) != 0:
            continue
        rows.append({"source_dimension": "eh_s2:yharnam", "dimension": "minecraft:overworld", "origin": list(point),
                     "source_cells": [{"position": list(point), "state": source_state}],
                     "outputs": [{"family": "o_grass_0", "canonical_root": list(point),
                                  "expected_logical_state": "bloodborne_blocks:o_grass_0[facing=north,visual=base]"}]})
    data = {"format": "bloodborne-accepted-bush-source-census-v1", "scope": "original immutable source only; no success-ledger selection",
            "source_sha256": index["source_sha256"], "source_dimension": "eh_s2:yharnam", "dimension": "minecraft:overworld",
            "source_state": STATE, "weighted_variants": {"models": [f"minecraft:block/addon/grass_{i}" for i in range(8)],
                                  "weights": [100] * 8, "counts": {str(i): variants[i] for i in range(8)},
                                  "known_pending_indices": [i for i in range(1, 8)]},
            "counts": {"all_dead_fire_coral_fan": len(found), "o_grass_0_candidates": len(rows), "known_pending_other_variants": len(found) - len(rows)},
            "occurrences": rows}
    raw = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(gzip.compress(raw, mtime=0))
    return data


def build_pending(output=PENDING_OUTPUT):
    """Record the other source-selected grass alternatives without approving them."""
    index = read_index()
    found = positions(index, [STATE])
    variants = Counter(weighted_index([100] * 8, point) for point in found)
    rows = []
    for point, source_state in sorted(found.items()):
        index_number = weighted_index([100] * 8, point)
        if index_number == 0:
            continue
        rows.append({"source_dimension": "eh_s2:yharnam", "dimension": "minecraft:overworld", "origin": list(point),
                     "source_cells": [{"position": list(point), "state": source_state}],
                     "family": f"authored_grass_{index_number}", "canonical_root": list(point),
                     "weighted_index": index_number, "reason": PENDING_REASON})
    if variants[0] != 517 or len(rows) != 3339:
        raise ValueError("unexpected authored grass denominator")
    data = {
        "format": "bloodborne-known-pending-vegetation-v1",
        "scope": "original immutable source only; known pending authored grass alternatives, not accepted restoration outputs",
        "source_sha256": index["source_sha256"],
        "source_dimension": "eh_s2:yharnam",
        "dimension": "minecraft:overworld",
        "source_state": STATE,
        "metadata": {
            "classification": "known_pending_not_unknown_or_absent",
            "accepted_ready_forms": ["o_grass_0"],
            "unapproved_art": "indices 1..7 are recorded for denominator completeness only; no whole-object rule is asserted",
            "runtime_current_target": "family missing -> unresolved",
        },
        "weighted_variants": {"models": [f"minecraft:block/addon/grass_{i}" for i in range(8)],
                              "weights": [100] * 8, "counts": {str(i): variants[i] for i in range(8)},
                              "pending_indices": list(range(1, 8))},
        "counts": {"source_carriers": len(found), "accepted_grass_0": variants[0], "known_pending_occurrences": len(rows),
                   "known_pending_by_weighted_index": {str(i): variants[i] for i in range(1, 8)}},
        "occurrences": rows,
    }
    raw = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(gzip.compress(raw, mtime=0))
    return data


if __name__ == "__main__":
    build_pending()
