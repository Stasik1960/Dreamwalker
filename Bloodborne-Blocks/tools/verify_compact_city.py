"""Fail-closed streaming proof that a compact gallery preserves its city input."""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

from convert_logical_world import NS
from upgrade_gallery_city import StreamingWorld
from world_io import TAG_COMPOUND, TAG_LIST, Tag, block_state_key, compound, section_blocks

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
MAX_EXAMPLES = 200


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def in_gallery(point: tuple[int, int, int], bounds: tuple[int, int, int, int, int, int]) -> bool:
    return all(bounds[index] <= point[index] <= bounds[index + 3] for index in range(3))


def sections(chunk):
    return {int(compound(row)["Y"].value): row
            for row in chunk.root().get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value
            if section_blocks(row) is not None}


def entities(chunk, bounds, all_states):
    result = {}
    for row in chunk.root().get("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        data = compound(row)
        if not all(axis in data for axis in ("x", "y", "z")):
            continue
        point = tuple(int(data[axis].value) for axis in ("x", "y", "z"))
        if not in_gallery(point, bounds) and (all_states or not data.get("id", Tag(8, "")).value.startswith(NS)):
            result[point] = row
    return result


def file_hashes(world: Path, group: str) -> dict[str, str]:
    directory = world / group
    return {path.relative_to(world).as_posix(): sha256(path)
            for path in sorted(directory.glob("*.mca"))} if directory.is_dir() else {}


def world_hashes(world: Path) -> dict[str, str]:
    return {path.relative_to(world).as_posix(): sha256(path) for path in sorted(world.rglob("*"))
            if path.is_file() and (path.name == "level.dat" or path.suffix == ".mca")}


def verify(source: Path, target: Path, report: Path, exclude_bounds, archive: Path | None = None,
           verify_manifest_source: bool = False, all_states: bool = False) -> dict:
    source, target, report = (path.resolve() for path in (source, target, report))
    manifest = json.loads((target / "editable-gallery-manifest.json").read_text(encoding="utf8"))
    bounds = [tuple(int(value) for value in row) for row in exclude_bounds]
    if not bounds:
        bounds = [tuple(int(value) for value in manifest["galleryBand"]["bounds"])]
    if any(len(row) != 6 or row[1] != 228 or row[4] != 319 for row in bounds):
        raise ValueError("expected explicit gallery bounds with Y=228..319")
    if report == source or report == target or source in report.parents or target in report.parents:
        raise ValueError("report must be outside both worlds")

    before, after = StreamingWorld(source, {}, cache_limit=32), StreamingWorld(target, {}, cache_limit=32)
    errors, counts = [], Counter()
    state_codes = {}
    definitions={NS+row['id']:row for row in json.loads(
        (Path(__file__).resolve().parents[1]/'src/main/resources/bloodborne_blocks/city/definitions.json').read_bytes())['blocks']}

    def error(kind: str, **details) -> None:
        counts["errors"] += 1
        if len(errors) < MAX_EXAMPLES:
            errors.append({"kind": kind, **details})

    def foreign_code(state: str) -> int:
        if state not in state_codes:
            state_codes[state] = len(state_codes) + 1
        return state_codes[state]

    def canonical_state(entry):
        data=compound(entry);name=data['Name'].value
        values={key:value.value for key,value in compound(data.get('Properties',Tag(TAG_COMPOUND,{}))).items()}
        if all_states:
            definition=definitions.get(name,{})
            for key,choices in definition.get('properties',{}).items():
                if len(choices)==1 and key not in values:values[key]=definition.get('default',{}).get(key,choices[0])
        return name+('['+','.join(key+'='+values[key] for key in sorted(values))+']' if values else '')

    source_keys, target_keys = set(before.chunks.rows), set(after.chunks.rows)
    if source_keys != target_keys:
        error("chunk_set_mismatch", sourceOnly=len(source_keys - target_keys), targetOnly=len(target_keys - source_keys))
    for key in sorted(source_keys & target_keys):
        source_chunk, target_chunk = before.chunks[key], after.chunks[key]
        counts["chunks"] += 1
        def excluded(point): return any(in_gallery(point, row) for row in bounds)
        source_entities = {point: row for point, row in entities(source_chunk, bounds[0],all_states).items() if not excluded(point)}
        target_entities = {point: row for point, row in entities(target_chunk, bounds[0],all_states).items() if not excluded(point)}
        for point in sorted(set(source_entities) | set(target_entities)):
            if source_entities.get(point) != target_entities.get(point):
                error("foreign_block_entity_changed", position=list(point))
        counts["foreignBlockEntities"] += len(source_entities)
        source_sections, target_sections = sections(source_chunk), sections(target_chunk)
        for sy in sorted(set(source_sections) | set(target_sections)):
            def codes(section, side: str):
                if section is None:
                    return np.zeros(4096, dtype=np.int32)
                palette, indices = section_blocks(section)
                indices = np.asarray(indices, dtype=np.int32)
                remap = np.zeros(len(palette), dtype=np.int32)
                for index, entry in enumerate(palette):
                    state = canonical_state(entry) if all_states else block_state_key(entry)
                    name = compound(entry)["Name"].value
                    if not all_states and (name.startswith(NS) or name in AIR):
                        continue
                    remap[index] = foreign_code(state)
                    seen = int(np.count_nonzero(indices == index))
                    if side == "source":
                        counts["foreignBlocks"] += seen
                        if name.startswith("yuushya:"):
                            counts["yuushyaBlocks"] += seen
                return remap[indices]

            expected, actual = codes(source_sections.get(sy), "source"), codes(target_sections.get(sy), "target")
            indices = np.flatnonzero(expected != actual)
            if not len(indices):
                continue
            raw = indices.astype(np.int64)
            positions = np.column_stack((
                source_chunk.x * 16 + (raw & 15),
                sy * 16 + (raw >> 8),
                source_chunk.z * 16 + ((raw >> 4) & 15),
            ))
            exempt = np.zeros(len(positions), dtype=bool)
            for lower in bounds:
                exempt |= ((positions[:, 0] >= lower[0]) & (positions[:, 0] <= lower[3]) &
                           (positions[:, 1] >= lower[1]) & (positions[:, 1] <= lower[4]) &
                           (positions[:, 2] >= lower[2]) & (positions[:, 2] <= lower[5]))
            for index in raw[~exempt]:
                point = (source_chunk.x * 16 + int(index & 15), sy * 16 + int(index >> 8),
                         source_chunk.z * 16 + int((index >> 4) & 15))
                reverse = {value: state for state, value in state_codes.items()}
                error("foreign_block_changed", position=list(point),
                      source=reverse.get(int(expected[index]), "air-or-bloodborne"),target=reverse.get(int(actual[index]), "air-or-bloodborne"))
        if counts["chunks"] % 100 == 0:
            print("verified", counts["chunks"], "chunks", flush=True)

    hashes = {group: {"source": file_hashes(source, group), "target": file_hashes(target, group)}
              for group in ("entities", "poi")}
    for group, sides in hashes.items():
        if sides["source"] != sides["target"]:
            error("preserved_file_hash_mismatch", group=group)
    manifest_mismatches = ([path for path, digest in manifest.get("sourceHashes", {}).items()
                            if (source / path).is_file() and sha256(source / path) != digest]
                           if verify_manifest_source else [])
    for path in manifest_mismatches:
        error("baseline_does_not_match_gallery_manifest", path=path)
    result = {"schemaVersion": 1, "readOnly": True, "passed": not errors,
              "baseline": str(source), "target": str(target), "excludedGalleryBounds": [list(row) for row in bounds],
              "allStates":all_states,
              "counts": dict(counts), "errors": errors,
              "fileHashes": hashes, "targetWorldHashes": world_hashes(target),
              "archiveSha256": sha256(archive) if archive else None,
              "manifestSourceHashVerified": verify_manifest_source,
              "manifestBaselineMismatches": manifest_mismatches}
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    return result


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--target", type=Path, required=True)
    parser.add_argument("--report", type=Path, default=root / "build/compact-city-preservation.json")
    parser.add_argument("--exclude-bounds", type=int, nargs=6, action="append", default=[])
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--verify-manifest-source", action="store_true")
    parser.add_argument("--all-states", action="store_true")
    args = parser.parse_args()
    result = verify(args.source, args.target, args.report, args.exclude_bounds, args.archive,
                    args.verify_manifest_source,args.all_states)
    print(json.dumps({"passed": result["passed"], **result["counts"]}))
    raise SystemExit(0 if result["passed"] else 1)
