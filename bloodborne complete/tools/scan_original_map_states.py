#!/usr/bin/env python3
"""Read-only, actual-cell block-state inventory of the original v16 ZIP world.

No archive members are extracted or changed. Palette entries are counted only
when a decoded 1.20.1 padded storage index actually uses them. A first occupied
coordinate is retained for every state, and dimensions stay separate.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import time
import zipfile

import numpy as np

from world_io import (
    NbtError, RegionFile, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING,
    compound, pack_palette_indices, unpack_palette_indices,
)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAP = ROOT.parent / "Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v16_map.zip"
DEFAULT_OUT = ROOT / "docs/map-pack-absence"
EXPECTED_MAP_SHA = "39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08"
REGION = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(4 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, data: dict) -> None:
    # Only these requested report files are written; original ZIP stays closed
    # for writing throughout. Truncate-and-write is sufficient for a report.
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def state_details(entry):
    value = compound(entry)
    name = value.get("Name")
    if name is None or name.type != TAG_STRING or ":" not in name.value:
        raise NbtError("palette entry has no namespaced string Name")
    props = value.get("Properties")
    properties = {}
    if props is not None:
        for key, child in compound(props).items():
            if child.type != TAG_STRING:
                raise NbtError(f"block property {key} is not TAG_String")
            properties[key] = child.value
    properties = dict(sorted(properties.items()))
    canonical = name.value
    if properties:
        canonical += "[" + ",".join(f"{key}={item}" for key, item in properties.items()) + "]"
    return canonical, name.value, properties


def decode_indices(longs: list[int], palette_size: int) -> np.ndarray:
    if palette_size < 1:
        raise NbtError("empty block-state palette")
    if palette_size == 1 and not longs:
        return np.zeros(4096, dtype=np.uint16)
    bits = max(4, (palette_size - 1).bit_length())
    if bits > 32:
        raise NbtError(f"impossible block-state palette size {palette_size}")
    per_long = 64 // bits
    expected = math.ceil(4096 / per_long)
    if len(longs) != expected:
        raise NbtError(f"palette data has {len(longs)} longs; expected {expected}")
    words = np.asarray(longs, dtype=np.int64).view(np.uint64)
    shifts = np.arange(per_long, dtype=np.uint64) * np.uint64(bits)
    indices = ((words[:, None] >> shifts) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096]
    if indices.size != 4096 or int(indices.max()) >= palette_size:
        raise NbtError("decoded section has an out-of-range palette index or cell count")
    return indices.astype(np.uint16 if palette_size <= 65535 else np.uint32, copy=False)


def validate_decoder() -> dict:
    """Cross-check against the pre-existing scalar world_io implementation."""
    cases = []
    for palette_size in (1, 2, 16, 17, 32, 33, 64, 65, 128, 129, 256, 257, 512, 513, 1024, 1025, 2048, 2049, 4096):
        expected = [((index * 73) + index // 7) % palette_size for index in range(4096)]
        packed = pack_palette_indices(expected, palette_size)
        scalar = unpack_palette_indices(packed, palette_size)
        vectorized = decode_indices(packed, palette_size).tolist()
        if vectorized != scalar or scalar != expected:
            raise AssertionError(f"decoder mismatch at palette size {palette_size}")
        if Counter(vectorized) != Counter(expected):
            raise AssertionError(f"histogram mismatch at palette size {palette_size}")
        cases.append({"palette_size": palette_size, "cells": 4096, "signed_negative_words": sum(value < 0 for value in packed), "status": "PASS"})
    return {"status": "PASS", "basis": "generated 4096-cell arrays compared element-for-element with existing world_io scalar unpacker and original input", "cases": cases, "total_checked_cells": len(cases) * 4096}


def dimension_for(entry: str, world_prefix: str | None) -> str:
    parent = PurePosixPath(entry).parent.parent.as_posix()
    if parent == ".":
        parent = ""
    if world_prefix and (parent == world_prefix or parent.startswith(world_prefix + "/")):
        parent = parent[len(world_prefix):].lstrip("/")
    parts = [part for part in parent.split("/") if part]
    if parts and parts[-1] == "DIM-1":
        return "minecraft:the_nether"
    if parts and parts[-1] == "DIM1":
        return "minecraft:the_end"
    if "dimensions" in parts:
        at = parts.index("dimensions")
        relative = parts[at + 1:]
        if len(relative) < 2:
            raise ValueError("custom dimension region path lacks namespace/path")
        return relative[0] + ":" + "/".join(relative[1:])
    if any(part.startswith("DIM") for part in parts):
        return "archive:" + "/".join(parts)
    return "minecraft:overworld"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--map", type=Path, default=DEFAULT_MAP)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--validate-decoder-only", action="store_true")
    args = parser.parse_args()
    source = args.map.resolve()
    output = args.output.resolve()
    if output == source or source in output.parents:
        raise ValueError("reports must not be written inside the source archive")
    output.mkdir(parents=True, exist_ok=True)
    report_path = output / "world-states.json"
    progress_path = output / "scan-progress.json"
    validation = validate_decoder()
    if args.validate_decoder_only:
        progress = json.loads(progress_path.read_text(encoding="utf-8")) if progress_path.exists() else {}
        progress["decoder_validation"] = validation
        write_json(progress_path, progress)
        return
    start_time = time.monotonic()
    started = utc()
    source_sha = sha256(source)
    errors = []
    if source_sha != EXPECTED_MAP_SHA:
        errors.append({"phase": "input", "error": "original v16 ZIP SHA differs from fixed supplied source", "expected_sha256": EXPECTED_MAP_SHA, "actual_sha256": source_sha})
    counts = Counter()
    state_dimensions = defaultdict(Counter)
    details = {}
    first = {}
    dimensions = defaultdict(lambda: {"region_files": 0, "chunks": 0, "sections": 0, "count": 0})
    data_versions = Counter()
    chunk_statuses = Counter()
    regions = []
    total_chunks = 0
    total_sections = 0
    no_block_state_sections = 0
    seen_chunks = set()
    region_entries = []
    excluded_mca = []
    world_prefix = None
    level_entries = []
    with zipfile.ZipFile(source, "r") as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            errors.append({"phase": "archive", "error": "duplicate ZIP entry names"})
        level_entries = sorted(name for name in names if PurePosixPath(name).name == "level.dat")
        if len(level_entries) == 1:
            prefix = PurePosixPath(level_entries[0]).parent.as_posix()
            world_prefix = "" if prefix == "." else prefix
        else:
            errors.append({"phase": "archive", "error": "expected exactly one level.dat", "level_entries": level_entries})
        for name in sorted(names):
            path = PurePosixPath(name)
            if not name.endswith(".mca"):
                continue
            if path.parent.name == "region":
                region_entries.append(name)
            else:
                excluded_mca.append(name)
        if not region_entries:
            errors.append({"phase": "archive", "error": "no terrain region MCA files"})
        for number, entry in enumerate(region_entries, 1):
            row = {"zip_entry": entry, "dimension": None, "chunks": 0, "sections": 0, "status": "PASS"}
            regions.append(row)
            match = REGION.fullmatch(PurePosixPath(entry).name)
            try:
                if match is None:
                    raise ValueError("invalid terrain region filename")
                rx, rz = int(match.group(1)), int(match.group(2))
                dimension = dimension_for(entry, world_prefix)
                row["dimension"] = dimension
                dimensions[dimension]["region_files"] += 1
                payload = archive.read(entry)  # ZIP CRC is checked by ZipFile.
                row["uncompressed_bytes"] = len(payload)
                region = RegionFile(payload)
                for stored in region.chunks():
                    total_chunks += 1
                    row["chunks"] += 1
                    dimensions[dimension]["chunks"] += 1
                    expected_cx = rx * 32 + stored.x
                    expected_cz = rz * 32 + stored.z
                    chunk_context = {"zip_entry": entry, "chunk_x": expected_cx, "chunk_z": expected_cz}
                    try:
                        raw = compound(stored.nbt().root)
                        version = raw.get("DataVersion")
                        version_number = int(version.value) if version is not None else None
                        data_versions[str(version_number)] += 1
                        if version_number is None or version_number < 2529:
                            raise NbtError(f"unsupported pre-padded chunk DataVersion {version_number}")
                        root = compound(raw["Level"]) if "Level" in raw else raw
                        cx, cz = root.get("xPos"), root.get("zPos")
                        if cx is None or cz is None:
                            raise NbtError("chunk lacks xPos/zPos")
                        if int(cx.value) != expected_cx or int(cz.value) != expected_cz:
                            raise NbtError(f"chunk coordinates {cx.value},{cz.value} disagree with region location")
                        key = dimension, expected_cx, expected_cz
                        if key in seen_chunks:
                            raise NbtError("duplicate dimension chunk coordinates")
                        seen_chunks.add(key)
                        status = root.get("Status")
                        chunk_statuses[str(status.value) if status is not None else "<missing>"] += 1
                        sections = root.get("sections") or root.get("Sections")
                        if sections is None:
                            continue
                        if sections.type != TAG_LIST or (sections.value and sections.list_type != TAG_COMPOUND):
                            raise NbtError("sections are not a compound TAG_List")
                        seen_y = set()
                        for section in sections.value:
                            values = compound(section)
                            sy_tag = values.get("Y")
                            if sy_tag is None:
                                raise NbtError("section lacks Y")
                            sy = int(sy_tag.value)
                            if sy in seen_y:
                                raise NbtError(f"duplicate section Y={sy}")
                            seen_y.add(sy)
                            states = values.get("block_states")
                            if states is None:
                                no_block_state_sections += 1
                                continue
                            fields = compound(states)
                            palette = fields.get("palette")
                            if palette is None or palette.type != TAG_LIST or palette.list_type != TAG_COMPOUND or not palette.value:
                                raise NbtError(f"section Y={sy} has invalid/empty palette")
                            packed = fields.get("data")
                            if packed is not None and packed.type != TAG_LONG_ARRAY:
                                raise NbtError(f"section Y={sy} data is not TAG_Long_Array")
                            indices = decode_indices(packed.value if packed is not None else [], len(palette.value))
                            local_counts = np.bincount(indices.astype(np.int64), minlength=len(palette.value))
                            if int(local_counts.sum()) != 4096:
                                raise NbtError(f"section Y={sy} counts do not sum to 4096")
                            # Validate every palette entry, including unused ones;
                            # only positive counts contribute to membership.
                            local_details = [state_details(item) for item in palette.value]
                            total_sections += 1
                            row["sections"] += 1
                            dimensions[dimension]["sections"] += 1
                            dimensions[dimension]["count"] += 4096
                            for palette_index in np.flatnonzero(local_counts):
                                canonical, name, properties = local_details[int(palette_index)]
                                amount = int(local_counts[palette_index])
                                counts[canonical] += amount
                                state_dimensions[canonical][dimension] += amount
                                details.setdefault(canonical, (name, properties))
                                if canonical not in first:
                                    index = int(np.flatnonzero(indices == palette_index)[0])
                                    first[canonical] = {"dimension": dimension, "x": expected_cx * 16 + index % 16, "y": sy * 16 + index // 256, "z": expected_cz * 16 + (index // 16) % 16}
                    except Exception as error:
                        row["status"] = "FAIL"
                        errors.append({**chunk_context, "phase": "chunk", "error": f"{type(error).__name__}: {error}"})
            except Exception as error:
                row["status"] = "FAIL"
                errors.append({"zip_entry": entry, "phase": "region", "error": f"{type(error).__name__}: {error}"})
            write_json(progress_path, {"status": "RUNNING", "started_utc": started, "updated_utc": utc(), "regions_scanned": number, "regions_total": len(region_entries), "chunks_scanned": total_chunks, "sections_scanned": total_sections, "unique_states": len(counts), "errors": errors, "last_region": entry, "elapsed_seconds": round(time.monotonic() - start_time, 2)})
    final_sha = sha256(source)
    if final_sha != source_sha:
        errors.append({"phase": "input", "error": "source ZIP changed during read-only scan", "before": source_sha, "after": final_sha})
    if sum(counts.values()) != total_sections * 4096:
        errors.append({"phase": "totals", "error": "global counts do not equal sections * 4096"})
    for dimension, row in dimensions.items():
        row["unique_states"] = sum(1 for state in state_dimensions.values() if dimension in state)
    states = []
    for canonical in sorted(counts):
        name, properties = details[canonical]
        states.append({"state": canonical, "name": name, "properties": properties, "count": counts[canonical], "dimensions": dict(sorted(state_dimensions[canonical].items())), "first": first[canonical]})
    result = {
        "format": "original-v16-actual-world-states-v1", "status": "PASS" if not errors else "FAIL",
        "started_utc": started, "completed_utc": utc(), "elapsed_seconds": round(time.monotonic() - start_time, 2),
        "source": str(source), "map_sha256_before": source_sha, "map_sha256_after": final_sha,
        "expected_map_sha256": EXPECTED_MAP_SHA, "source_unchanged": source_sha == final_sha,
        "read_only": True, "no_archive_extraction": True, "membership_basis": "positive counts of decoded used palette indices; not palette membership",
        "storage_layout": "Minecraft post-1.16 padded non-spanning 64-bit block state indices; 4096 cells per encoded section",
        "decoder_validation": validation,
        "level_entries": level_entries, "world_prefix": world_prefix,
        "regions_total": len(region_entries), "regions": regions, "excluded_non_terrain_mca_entries": excluded_mca,
        "chunks_scanned": total_chunks, "sections_scanned": total_sections, "sections_without_block_states": no_block_state_sections,
        "chunk_data_versions": dict(sorted(data_versions.items())), "chunk_statuses": dict(sorted(chunk_statuses.items())),
        "dimensions": dict(sorted(dimensions.items())), "total_stored_cells": sum(counts.values()),
        "non_air_cells": sum(value for key, value in counts.items() if key.split("[", 1)[0] not in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}),
        "unique_states": len(counts), "unique_block_ids": len({item[0] for item in details.values()}),
        "state_counts": dict(sorted(counts.items())), "states": states, "errors": errors,
    }
    write_json(report_path, result)
    write_json(progress_path, {"status": result["status"], "started_utc": started, "completed_utc": result["completed_utc"], "regions_scanned": len(regions), "regions_total": len(region_entries), "chunks_scanned": total_chunks, "sections_scanned": total_sections, "unique_states": len(counts), "errors": errors, "elapsed_seconds": result["elapsed_seconds"], "report": str(report_path)})
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
