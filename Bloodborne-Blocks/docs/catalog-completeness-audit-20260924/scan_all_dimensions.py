#!/usr/bin/env python3
"""Read-only all-dimension source-world carrier state census.

The output is evidence for catalogue completeness, not approval or migration
authority.  It intentionally stores only three samples per exact state and
dimension; the source ZIP is never extracted or modified.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import sys
import time
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

TOOLS = Path(__file__).resolve().parents[2] / "tools"
sys.path.insert(0, str(TOOLS))
from manual_review_world import _load_blockstates
from source_assembly_index import _resource_carriers, _state
from source_assembly_pipeline import active_choices
from scan_production_usage import weighted_indices
from world_io import RegionFile, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, child, compound, unpack_palette_indices


ROOT = TOOLS.parent
AUDIT = Path(__file__).resolve().parent
SOURCE = ROOT / "reference-inputs" / "source-world.zip"
PACK = ROOT / "reference-inputs" / "source-resource-pack.zip"
VANILLA = Path(os.environ.get("BLOODBORNE_VANILLA_JAR", ROOT / "build/gradle-home/caches/fabric-loom/1.20.1/minecraft-client.jar"))
EXPECTED_SHA256 = "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51"
OUT = AUDIT / "world-state-usage.json.gz"
SUMMARY = AUDIT / "scan-summary.json"
MAX_EXAMPLES = 3


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for piece in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(piece)
    return digest.hexdigest()


def dimension(name: str) -> str:
    # The supplied archive encloses normal overworld data in ``ether/``.
    if "/dimensions/" in name:
        parts = name.split("/dimensions/", 1)[1].split("/region/", 1)[0].split("/")
        return parts[0] + ":" + "/".join(parts[1:])
    if name.endswith("/region/") or "/region/r." in name:
        return "minecraft:overworld"
    return "unknown"


def archive_regions(archive: zipfile.ZipFile) -> list[str]:
    return sorted(name for name in archive.namelist() if name.endswith(".mca") and "/region/r." in name)


def carrier_universe() -> set[str]:
    # Frozen mapping sources preserve the pre-prune carrier scope; source-pack
    # blockstates/evidence ensure catalogue gaps are not silently omitted.
    carriers = set(_resource_carriers(ROOT))
    with gzip.open(ROOT / "docs/production-source-usage.json.gz", "rt", encoding="utf8") as stream:
        evidence = json.load(stream)
    carriers.update(row["id"] for row in evidence.get("states", []))
    carriers.update(evidence.get("carrier_evidence", {}).keys())
    with zipfile.ZipFile(PACK) as archive:
        marker = "assets/minecraft/blockstates/"
        carriers.update("minecraft:" + name[len(marker):-5] for name in archive.namelist()
                        if name.startswith(marker) and name.endswith(".json"))
    return carriers


def add_examples(record: dict, dim: str, coordinates: np.ndarray) -> None:
    examples = record["dimensions"][dim]["examples"]
    for point in coordinates:
        if len(examples) == MAX_EXAMPLES:
            return
        examples.append([int(value) for value in point])


def add_model_usage(target: dict, model: str, dim: str, count: int, examples: np.ndarray) -> None:
    if not model or count == 0:
        return
    row = target.setdefault(model, {"model": model, "dimensions": {}})
    by_dim = row["dimensions"].setdefault(dim, {"count": 0, "examples": []})
    by_dim["count"] += int(count)
    for point in examples:
        if len(by_dim["examples"]) == MAX_EXAMPLES:
            break
        by_dim["examples"].append([int(value) for value in point])


def model_choices(raw: dict, props: dict[str, str], positions: np.ndarray, dim: str, usage: dict) -> None:
    """Aggregate actually selected applications; weighted choices use source XYZ."""
    groups = active_choices(raw, props)
    multipart = "multipart" in raw
    for group in groups:
        if not group:
            continue
        if len(group) == 1:
            for app in group[0]:
                add_model_usage(usage, app.get("model", ""), dim, len(positions), positions)
            continue
        weights = [option[0].get("weight", 1) for option in group]
        choices = weighted_indices(weights, positions, multipart)
        for index, option in enumerate(group):
            selected = positions[choices == index]
            for app in option:
                add_model_usage(usage, app.get("model", ""), dim, len(selected), selected)


def scan() -> dict:
    started = time.monotonic()
    before = sha256(SOURCE)
    if before != EXPECTED_SHA256:
        raise ValueError(f"unexpected source SHA-256: {before}")
    if not VANILLA.is_file():
        raise FileNotFoundError(f"missing Minecraft client jar: {VANILLA}")
    carriers = carrier_universe()
    blockstates = _load_blockstates(PACK, VANILLA)
    records: dict[tuple[str, tuple[tuple[str, str], ...]], dict] = {}
    model_usage: dict[str, dict] = {}
    regions = Counter()
    chunks = Counter()
    indexed = Counter()
    last_progress = started
    with zipfile.ZipFile(SOURCE) as archive:
        names = archive_regions(archive)
        for number, name in enumerate(names, 1):
            dim = dimension(name)
            regions[dim] += 1
            for stored in RegionFile(archive.read(name)).chunks():
                chunks[dim] += 1
                root = compound(stored.nbt().root)
                cx, cz = int(root.get("xPos").value), int(root.get("zPos").value)
                sections = root.get("sections") or root.get("Sections")
                for section in sections.value if sections and sections.type == TAG_LIST else ():
                    fields = compound(section)
                    state_data = child(section, "block_states", TAG_COMPOUND)
                    palette_tag = child(state_data, "palette", TAG_LIST) if state_data else None
                    if not palette_tag or not palette_tag.value:
                        continue
                    matching = {}
                    for palette_index, entry in enumerate(palette_tag.value):
                        key, descriptor = _state(entry)
                        if descriptor["id"] in carriers:
                            matching[palette_index] = (key, descriptor)
                    if not matching:
                        continue
                    data_tag = child(state_data, "data", TAG_LONG_ARRAY)
                    values = np.asarray(unpack_palette_indices(data_tag.value if data_tag else [], len(palette_tag.value)), dtype=np.int32)
                    sy = int(fields["Y"].value)
                    for palette_index, (key, descriptor) in matching.items():
                        cells = np.flatnonzero(values == palette_index)
                        if not len(cells):
                            continue
                        state_id = (descriptor["id"], tuple(sorted(descriptor["properties"].items())))
                        record = records.setdefault(state_id, {"id": descriptor["id"], "properties": descriptor["properties"], "dimensions": {}})
                        by_dim = record["dimensions"].setdefault(dim, {"count": 0, "examples": []})
                        by_dim["count"] += int(len(cells))
                        indexed[dim] += int(len(cells))
                        xyz = np.column_stack((cx * 16 + (cells & 15), sy * 16 + (cells >> 8), cz * 16 + ((cells >> 4) & 15))).astype(np.int32)
                        add_examples(record, dim, xyz)
                        raw = blockstates.get(descriptor["id"])
                        if raw:
                            model_choices(raw, descriptor["properties"], xyz, dim, model_usage)
            now = time.monotonic()
            if number % 16 == 0 or now - last_progress >= 55:
                print(f"catalog census {number}/{len(names)} regions; {sum(chunks.values())} chunks; {now-started:.1f}s", flush=True)
                last_progress = now
    after = sha256(SOURCE)
    if after != before:
        raise RuntimeError("source ZIP changed during read-only census")
    states = sorted(records.values(), key=lambda row: (row["id"], tuple(sorted(row["properties"].items()))))
    models = sorted(model_usage.values(), key=lambda row: row["model"])
    return {
        "format": "catalog-completeness-world-state-usage-v1",
        "source_sha256": before,
        "source_sha256_after": after,
        "carrier_universe": {"ids": len(carriers), "mapping_and_pack_blockstates": True},
        "regions_by_dimension": dict(sorted(regions.items())),
        "chunks_by_dimension": dict(sorted(chunks.items())),
        "indexed_cells_by_dimension": dict(sorted(indexed.items())),
        "total_regions": sum(regions.values()),
        "total_chunks": sum(chunks.values()),
        "indexed_cells": sum(indexed.values()),
        "states": states,
        "model_usage": models,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "interpretation": "Positive source-world state/model usage evidence only; absence here must be read across all scanned dimensions, not as approval or semantic classification.",
    }


def write(value: dict) -> None:
    OUT.write_bytes(gzip.compress((json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n").encode("utf8"), mtime=0))
    summary = {key: value[key] for key in ("source_sha256", "source_sha256_after", "regions_by_dimension", "chunks_by_dimension", "indexed_cells_by_dimension", "total_regions", "total_chunks", "indexed_cells", "elapsed_seconds")}
    summary.update({"observed_exact_states": len(value["states"]), "observed_models": len(value["model_usage"]), "result": "PASS"})
    SUMMARY.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf8")


if __name__ == "__main__":
    result = scan()
    write(result)
    print(json.dumps({"regions": result["total_regions"], "chunks": result["total_chunks"], "indexed_cells": result["indexed_cells"], "states": len(result["states"]), "models": len(result["model_usage"]), "elapsed": result["elapsed_seconds"]}), flush=True)
