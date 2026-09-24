"""Build and verify the manifest-authoritative production palette gallery."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import zipfile
from pathlib import Path
from typing import Any, Iterable

from build_nightmare_gallery import FLOOR_Y, ROOT_Y, _horizontal_extent, geometry_cells, rendered_mesh_bounds, state_key

ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
MANIFEST = ROOT / "docs/production-logical-palette.json"
OUTPUT = ROOT / "build/production-gallery-saves/production-palette-gallery-20260924"
PART = "bloodborne_blocks:logical_part"
FLOOR = "minecraft:white_concrete"
GAP = 10
ALT_PROOF_IDS = ("o_c001", "o_c002")


def require(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def canonical_properties(definition: dict[str, Any]) -> tuple[str, dict[str, str]]:
    values = dict(definition["default"], **definition.get("placement_properties", {}))
    if "visual" in definition.get("properties", {}):
        values["visual"] = "base"
    if "open" in definition.get("properties", {}):
        values["open"] = "false"
    key = state_key(values)
    require(key in definition["states"], f"canonical state missing for {definition['id']}: {key}")
    return key, values


def specimen_properties(definition: dict[str, Any], **overrides: str) -> tuple[str, dict[str, str]]:
    _key, values = canonical_properties(definition)
    values.update(overrides)
    key = state_key(values)
    require(key in definition["states"], f"specimen state missing for {definition['id']}: {key}")
    return key, values


def plan(specimens: list[dict[str, Any]], columns: int = 4) -> None:
    require(columns > 0, "columns must be positive")
    x = z = 32
    row_depth = 0
    for number, specimen in enumerate(specimens):
        low_x, low_z, high_x, high_z = _horizontal_extent(specimen["bounds"], specimen["footprint"])
        width, depth = high_x - low_x + 1, high_z - low_z + 1
        if number and number % columns == 0:
            x, z, row_depth = 32, z + row_depth + GAP + 4, 0
        # Wall-mounted contracts may own cells below their placement origin.
        # Lift the specimen, not its contract, so helpers cannot replace the pad.
        lowest_y = min(0, math.floor(specimen["bounds"][1]),
                       min(cell[1] for cell in specimen["footprint"]))
        root = (x - low_x, ROOT_Y - lowest_y, z - low_z)
        specimen["position"] = root
        specimen["pad"] = (x - 2, z - 2, x + width + 1, z + depth + 1)
        x += width + GAP + 4
        row_depth = max(row_depth, depth)


def load_specimens(manifest_path: Path = MANIFEST) -> tuple[list[dict], list[dict]]:
    manifest = json.loads(manifest_path.read_text(encoding="utf8"))
    objects = manifest.get("objects")
    require(isinstance(objects, list) and objects, "production manifest needs nonempty objects")
    selected = [row for row in objects if row.get("status") == "PRODUCTION"]
    require(len(selected) == len(objects), "production gallery refuses non-PRODUCTION manifest members")
    ids = [row.get("id") for row in selected]
    require(all(isinstance(ident, str) and ident for ident in ids) and len(ids) == len(set(ids)), "invalid production IDs")
    definitions = {row["id"]: row for row in json.loads((LOGICAL / "definitions.json").read_text(encoding="utf8"))["blocks"]}
    geometry = json.loads((LOGICAL / "geometry.json").read_text(encoding="utf8"))
    contracts = {row["id"]: row for row in json.loads((LOGICAL / "contracts-v2.json").read_text(encoding="utf8"))["families"]}
    with gzip.open(LOGICAL / "meshes.json.gz", "rt", encoding="utf8") as stream:
        meshes = json.load(stream)
    specimens = []
    def add(item: dict, definition: dict, contract: dict, state: str, properties: dict[str, str], role: str) -> None:
        ident = item["id"]
        render = contract["states"].get(state, {}).get("render_mesh")
        require(render is not None, f"contract render missing: {ident} {state}")
        footprint = geometry_cells(ident, state, contracts, geometry)
        require((0, 0, 0) in footprint, f"contract root missing: {ident} {state}")
        source_reviews = item.get("source_reviews", [item.get("source_review", contract.get("review_id", "unreviewed"))])
        require(isinstance(source_reviews, list) and all(isinstance(review, str) and review for review in source_reviews),
                f"invalid SourceReview marker for {ident}")
        specimens.append({"id": ident, "specimen_id": ident + ":" + role, "role": role, "state": state,
                          "properties": properties, "semantic_label": item.get("semantic_label", ident),
                          "source_review": ", ".join(source_reviews),
                          "bounds": rendered_mesh_bounds(meshes[render["id"]], render), "footprint": footprint})

    for item in selected:
        ident = item["id"]
        definition, contract = definitions.get(ident), contracts.get(ident)
        require(definition is not None and contract is not None, f"production member must be current Contract V2: {ident}")
        state, properties = canonical_properties(definition)
        add(item, definition, contract, state, properties, "canonical_base")
        if definition.get("behavior") in {"door", "shutter", "gate"}:
            require("open" in definition.get("properties", {}), f"interactive production object lacks open state: {ident}")
            state, properties = specimen_properties(definition, open="true")
            add(item, definition, contract, state, properties, "open")
    for ident in ALT_PROOF_IDS:
        item = next((row for row in selected if row["id"] == ident), None)
        if item is None:
            continue
        definition, contract = definitions[ident], contracts[ident]
        require(definition.get("properties", {}).get("visual") == ["base", "alt"], f"ALT proof unavailable: {ident}")
        state, properties = specimen_properties(definition, visual="alt")
        add(item, definition, contract, state, properties, "alt_proof")
    plan(specimens)
    return specimens, selected


def build(output: Path = OUTPUT, manifest_path: Path = MANIFEST) -> None:
    require(not output.exists(), "production gallery output must be fresh: " + str(output))
    specimens, manifest_objects = load_specimens(manifest_path)
    from convert_logical_world import block_pos_long
    from reviewed_migration_fixture import write_fixture
    from world_io import TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING, Tag
    cells: dict[tuple[int, int, int], tuple[str, dict[str, str]]] = {(16, ROOT_Y, 10): ("minecraft:air", {})}
    for x in range(12, 21):
        for z in range(2, 15):
            cells[(x, FLOOR_Y, z)] = (FLOOR, {})
    entities, positions, markers = [], [], []
    for specimen in specimens:
        root = specimen["position"]
        owner = "bloodborne_blocks:" + specimen["id"]
        require(root not in cells, "overlapping root")
        cells[root] = (owner, specimen["properties"])
        for offset in specimen["footprint"]:
            if offset == (0, 0, 0):
                continue
            point = tuple(root[index] + offset[index] for index in range(3))
            require(point not in cells, "overlapping helper")
            cells[point] = (PART, {})
            data = {"id": Tag(TAG_STRING, PART), "Owner": Tag(TAG_STRING, owner), "Root": Tag(TAG_LONG, block_pos_long(*root))}
            data.update({axis: Tag(TAG_INT, value) for axis, value in zip(("x", "y", "z"), point)})
            entities.append(Tag(TAG_COMPOUND, data))
        min_x, min_z, max_x, max_z = specimen["pad"]
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                cells.setdefault((x, FLOOR_Y, z), (FLOOR, {}))
        marker = (min_x, ROOT_Y, min_z)
        require(marker not in cells, "marker overlaps production object")
        cells[marker] = ("minecraft:oak_sign", {"rotation": "8", "waterlogged": "false"})
        text = [specimen["specimen_id"], specimen["semantic_label"], specimen["state"], "SourceReview " + specimen["source_review"]]
        front = Tag(TAG_COMPOUND, {"messages": Tag(TAG_LIST, [Tag(TAG_STRING, json.dumps({"text": line}, separators=(",", ":"))) for line in text], TAG_STRING),
                                   "color": Tag(TAG_STRING, "black")})
        data = {"id": Tag(TAG_STRING, "minecraft:sign"), "front_text": front, "back_text": front}
        data.update({axis: Tag(TAG_INT, value) for axis, value in zip(("x", "y", "z"), marker)})
        entities.append(Tag(TAG_COMPOUND, data)); markers.append({"specimen_id": specimen["specimen_id"], "role": specimen["role"], "position": marker, "text": text})
        positions.append({key: specimen[key] for key in ("id", "specimen_id", "role", "state", "properties", "semantic_label", "source_review", "bounds", "position")})
    write_fixture(output, cells, block_entities=entities, floor=False, level_name="Bloodborne production palette gallery")
    (output / "production-gallery.json").write_text(json.dumps({"manifest_ids": [item["id"] for item in manifest_objects],
        "unique_logical_count": len(manifest_objects), "specimen_count": len(positions), "positions": positions}, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    (output / "gallery-markers.json").write_text(json.dumps(markers, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


def verify(world_path: Path, manifest_path: Path = MANIFEST) -> dict:
    from convert_logical_world import World, block_pos_long
    from fixture_level_metadata import validate_level_metadata
    from world_io import compound, read_nbt
    expected, objects = load_specimens(manifest_path)
    metadata = json.loads((world_path / "production-gallery.json").read_text(encoding="utf8"))
    positions = metadata.get("positions", [])
    require(metadata.get("manifest_ids") == [row["id"] for row in objects] and
            metadata.get("unique_logical_count") == len(objects) and metadata.get("specimen_count") == len(expected) and
            len(positions) == len(expected), "manifest membership mismatch")
    defaults = {"bloodborne_blocks:" + row["id"]: row["properties"] for row in expected}
    world, entities = World(world_path, defaults), World(world_path, defaults).block_entities()
    platform_cells = helpers = 0
    for actual, specimen in zip(positions, expected):
        require(all(actual.get(key) == specimen[key] for key in ("id", "specimen_id", "role", "state", "properties", "semantic_label", "source_review")) and
                tuple(actual.get("bounds", ())) == tuple(specimen["bounds"]) and
                tuple(actual.get("position", ())) == tuple(specimen["position"]), "stale production metadata")
        root = tuple(actual["position"]); name, props = world.get("minecraft:overworld", root) or (None, ())
        require(name == "bloodborne_blocks:" + specimen["id"] and dict(props) == specimen["properties"], "root mismatch")
        for offset in specimen["footprint"]:
            point = tuple(root[index] + offset[index] for index in range(3))
            if offset != (0, 0, 0):
                require((world.get("minecraft:overworld", point) or (None, ()))[0] == PART, "missing helper")
                data = compound(entities[("minecraft:overworld", *point)])
                require(data["Owner"].value == name and data["Root"].value == block_pos_long(*root), "helper ownership mismatch")
                helpers += 1
        low_x, low_z, high_x, high_z = _horizontal_extent(specimen["bounds"], specimen["footprint"])
        for x in range(root[0] + low_x, root[0] + high_x + 1):
            for z in range(root[2] + low_z, root[2] + high_z + 1):
                require((world.get("minecraft:overworld", (x, FLOOR_Y, z)) or (None, ()))[0] == FLOOR, "platform coverage mismatch")
                platform_cells += 1
    nbt = read_nbt(world_path / "level.dat"); validate_level_metadata(nbt)
    require(compound(compound(nbt.root)["Data"])["LevelName"].value == "Bloodborne production palette gallery", "level name mismatch")
    return {"unique_logical_count": len(objects), "specimen_count": len(expected), "helpers": helpers, "platform_cells": platform_cells, "result": "PASS"}


def package(world_path: Path, output_zip: Path, manifest_path: Path = MANIFEST) -> dict:
    report = verify(world_path, manifest_path)
    require(not output_zip.exists(), "refusing to overwrite production ZIP")
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(item for item in world_path.rglob("*") if item.is_file()):
            archive.write(path, world_path.name + "/" + path.relative_to(world_path).as_posix())
    report["zip"] = str(output_zip); report["sha256"] = hashlib.file_digest(output_zip.open("rb"), "sha256").hexdigest()
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--manifest", type=Path, default=MANIFEST); parser.add_argument("--verify", type=Path); parser.add_argument("--zip", type=Path)
    args = parser.parse_args()
    if args.verify: print(json.dumps(verify(args.verify, args.manifest), ensure_ascii=False))
    elif args.zip: print(json.dumps(package(args.output, args.zip, args.manifest), ensure_ascii=False))
    else: build(args.output, args.manifest)
