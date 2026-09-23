"""Build a fresh, sparse NightmareRunning manual-placement gallery.

This reads current logical resources only and writes a disposable fixture.  It
never reads or modifies a city, and deliberately does not reuse the narrow
batch-02 QA2 gallery.
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
OUTPUT = ROOT / "build/nightmare-running-gallery"
FACINGS = ("north", "east", "south", "west")
TREE_PREFIXES = ("o_c001", "o_c009")
COMPATIBILITY_TREES = {"o_c001_a", "o_c001_b", "o_c009_a", "o_c009_b", "o_dead_tree_planter"}
VISUAL_PROOF_IDS = ("o_c282_a", "o_c654_a", "o_c618")
FLOOR_Y = 63
ROOT_Y = 64
VOID_GAP = 12


def state_key(properties: dict[str, str]) -> str:
    return ",".join(f"{name}={value}" for name, value in sorted(properties.items()))


def visible_definitions(definitions: Iterable[dict[str, Any]], hidden: Iterable[str], aliases: Iterable[str] = ()) -> list[dict[str, Any]]:
    """Return every current creative logical definition except explicit hides."""
    hidden_ids = set(hidden) | set(aliases)
    return sorted((row for row in definitions if row.get("logical", True) and row.get("creative", True)
                   and row["id"] not in hidden_ids and row["id"] not in COMPATIBILITY_TREES), key=lambda row: row["id"])


def is_tree(ident: str) -> bool:
    return any(ident == prefix or ident.startswith(prefix + "_") for prefix in TREE_PREFIXES)


def specimen_states(definition: dict[str, Any]) -> list[tuple[str, dict[str, str], str]]:
    """Select reviewable defaults plus bounded functional representatives.

    All facing states are included where the property exists.  Tree visual
    variants get all facings; non-tree variants remain on their explicit base
    default to keep the gallery readable.  Open and lit states add one north
    representative without multiplying every functional cross-product.
    """
    properties = definition.get("properties", {})
    base = dict(definition["default"], **definition.get("placement_properties", {}))
    variants = properties.get("variant", [base.get("variant")]) if is_tree(definition["id"]) else [base.get("variant")]
    facings = FACINGS if "facing" in properties else (base.get("facing"),)
    selected: dict[str, tuple[dict[str, str], str]] = {}

    def add(values: dict[str, str], purpose: str) -> None:
        values = {name: value for name, value in values.items() if value is not None}
        key = state_key(values)
        if key not in definition.get("states", {}):
            raise ValueError(f'{definition["id"]} lacks declared state {key}')
        selected.setdefault(key, (values, purpose))

    for variant in variants:
        for facing in facings:
            values = dict(base)
            if variant is not None:
                values["variant"] = variant
            if facing is not None:
                values["facing"] = facing
            add(values, "tree_variant" if is_tree(definition["id"]) and variant is not None else "default")
    for functional in ("open", "lit"):
        if functional in properties:
            values = dict(base)
            values[functional] = next(value for value in properties[functional] if value != base.get(functional))
            if "facing" in properties:
                values["facing"] = "north"
            add(values, functional + "_representative")
    return [(key, values, purpose) for key, (values, purpose) in selected.items()]


def geometry_cells(ident: str, state: str, contracts: dict[str, dict], geometry: dict[str, Any]) -> list[tuple[int, int, int]]:
    """Resolve Contract V2 footprints first, then legacy geometry profiles."""
    if ident in contracts:
        try:
            return [tuple(cell) for cell in contracts[ident]["states"][state]["interaction_footprint"]["cells"]]
        except KeyError as error:
            raise ValueError(f"contract footprint missing for {ident} {state}") from error
    try:
        row = geometry["blocks"][ident]["states"][state]
        if "ref" in row:
            row = geometry["profiles"][row["ref"]]
        return [tuple(map(int, cell.split(","))) for cell in row["cells"]]
    except KeyError as error:
        raise ValueError(f"geometry footprint missing for {ident} {state}") from error


def mesh_bounds(mesh: dict[str, Any]) -> tuple[float, float, float, float, float, float]:
    vertices = [vertex for polygon in mesh.get("polygons", ()) for vertex in polygon.get("vertices", ())]
    if not vertices:
        raise ValueError("model mesh has no polygons")
    return tuple(min(vertex[index] for vertex in vertices) for index in range(3)) + tuple(
        max(vertex[index] for vertex in vertices) for index in range(3))


def visual_proof_states(definitions: Iterable[dict[str, Any]]) -> list[tuple[dict[str, Any], str, dict[str, str], str]]:
    """Return three physical BASE/ALT pairs, distinct from ordinary specimens."""
    by_id = {row["id"]: row for row in definitions}
    result = []
    for ident in VISUAL_PROOF_IDS:
        definition = by_id.get(ident)
        if definition is None or definition.get("properties", {}).get("visual") != ["base", "alt"]:
            raise ValueError(f"visual proof requires {ident} with base/alt states")
        for visual in ("base", "alt"):
            properties = dict(definition["default"], **definition.get("placement_properties", {}))
            properties["visual"] = visual
            state = state_key(properties)
            if state not in definition.get("states", {}):
                raise ValueError(f"visual proof state missing: {ident} {state}")
            result.append((definition, state, properties, "visual_proof_" + visual))
    return result


def plan_positions(specimens: list[dict[str, Any]], *, gap: int = VOID_GAP, columns: int = 4) -> list[dict[str, Any]]:
    """Lay out sparse pads from actual visual bounds, retaining void gaps."""
    if columns < 1:
        raise ValueError("columns must be positive")
    cursor_x = 32
    cursor_z = 32
    row_depth = 0
    for number, specimen in enumerate(specimens):
        low_x, _low_y, low_z, high_x, _high_y, high_z = specimen["bounds"]
        width = max(1, math.ceil(high_x) - math.floor(low_x) + 1)
        depth = max(1, math.ceil(high_z) - math.floor(low_z) + 1)
        if number and number % columns == 0:
            cursor_x = 32
            # Each pad extends two cells on both sides of its visual bounds.
            # Account for both margins so `gap` remains real empty space.
            cursor_z += row_depth + gap + 4
            row_depth = 0
        # The root stays at Y=64.  Do not compensate low visual/helper bounds.
        origin = (cursor_x - math.floor(low_x), ROOT_Y, cursor_z - math.floor(low_z))
        specimen["position"] = origin
        specimen["pad"] = (cursor_x - 2, cursor_z - 2, cursor_x + width + 1, cursor_z + depth + 1)
        cursor_x += width + gap + 4
        row_depth = max(row_depth, depth)
    return specimens


def add_light_pads(cells: dict[tuple[int, int, int], tuple[str, dict[str, str]]], specimens: Iterable[dict[str, Any]], block: str) -> None:
    """Add independent pads only; specimens are separated by documented void."""
    for specimen in specimens:
        min_x, min_z, max_x, max_z = specimen["pad"]
        for x in range(min_x, max_x + 1):
            for z in range(min_z, max_z + 1):
                cells.setdefault((x, FLOOR_Y, z), (block, {}))


def _review_id(ident: str, contracts: dict[str, dict]) -> str:
    return contracts.get(ident, {}).get("review_id", "noCatalogID" if ident == "o_wall_deco_1" else "unreviewed")


def _load() -> tuple[list[dict], dict[str, Any], dict[str, Any], dict[str, Any]]:
    definitions = json.loads((LOGICAL / "definitions.json").read_text(encoding="utf8"))["blocks"]
    hidden = json.loads((LOGICAL / "hidden-items.json").read_text(encoding="utf8"))
    aliases = json.loads((ROOT / "src/main/resources/bloodborne_blocks/aliases.json").read_text(encoding="utf8")).get("aliases", {})
    geometry = json.loads((LOGICAL / "geometry.json").read_text(encoding="utf8"))
    with gzip.open(LOGICAL / "meshes.json.gz", "rt", encoding="utf8") as stream:
        meshes = json.load(stream)
    from logical_contract_v2 import load_contracts
    contract_doc, _transform = load_contracts(LOGICAL)
    contracts = {row["id"]: row for row in contract_doc["families"]}
    return visible_definitions(definitions, hidden, aliases), geometry, meshes, contracts


def build(output: Path = OUTPUT) -> None:
    """Write a new world only to a nonexistent output directory."""
    if output.exists():
        raise FileExistsError("Nightmare gallery output must be fresh: " + str(output))
    definitions, geometry, meshes, contracts = _load()
    specimens: list[dict[str, Any]] = []
    for definition in definitions:
        for state, properties, purpose in specimen_states(definition):
            mesh_id = definition["models"].get(state)
            if mesh_id is None:
                raise ValueError(f'{definition["id"]} lacks model for {state}')
            specimens.append({"id": definition["id"], "review_id": _review_id(definition["id"], contracts),
                              "state": state, "properties": properties, "purpose": purpose,
                              "bounds": mesh_bounds(meshes[mesh_id]),
                              "footprint": geometry_cells(definition["id"], state, contracts, geometry)})
    for definition, state, properties, purpose in visual_proof_states(definitions):
        mesh_id = definition["models"][state]
        specimens.append({"id": definition["id"], "review_id": _review_id(definition["id"], contracts),
                          "state": state, "properties": properties, "purpose": purpose,
                          "bounds": mesh_bounds(meshes[mesh_id]),
                          "footprint": geometry_cells(definition["id"], state, contracts, geometry)})
    plan_positions(specimens)
    # Delayed imports keep synthetic layout tests independent of world I/O.
    from build_reviewed_gallery import platform_block
    from convert_logical_world import PART, block_pos_long
    from reviewed_migration_fixture import write_fixture
    from world_io import TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING, Tag

    floor = platform_block()
    cells: dict[tuple[int, int, int], tuple[str, dict[str, str]]] = {(16, ROOT_Y, 10): ("minecraft:air", {})}
    # write_fixture spawns at (first.x, first.y+2, first.z-4).
    # Provide a separate safe arrival pad; never spawn above the void.
    for x in range(12,21):
        for z in range(2,15):cells[(x,FLOOR_Y,z)]=(floor,{})
    entities = []
    positions = []
    for specimen in specimens:
        origin = specimen["position"]
        owner = "bloodborne_blocks:" + specimen["id"]
        if origin in cells:
            raise ValueError("gallery root overlap")
        cells[origin] = (owner, specimen["properties"])
        for offset in specimen["footprint"]:
            if offset == (0, 0, 0):
                continue
            point = tuple(origin[index] + offset[index] for index in range(3))
            if point in cells:
                raise ValueError("gallery helper overlap")
            cells[point] = (PART, {})
            data = {"id": Tag(TAG_STRING, PART), "Owner": Tag(TAG_STRING, owner),
                    "Root": Tag(TAG_LONG, block_pos_long(*origin))}
            data.update({axis: Tag(TAG_INT, value) for axis, value in zip(("x", "y", "z"), point)})
            entities.append(Tag(TAG_COMPOUND, data))
        positions.append({key: specimen[key] for key in ("id", "review_id", "state", "properties", "purpose", "bounds", "position")}
                         | {"tp": f"/tp @s {origin[0]} {origin[1] + 2} {origin[2]}", "give": f"/give @s {owner}"})
    add_light_pads(cells, specimens, floor)
    # One readable marker per visible family.  It is deliberately outside the
    # contract footprint on its own pad, so signs cannot disguise helper bugs.
    markers = []
    for ident in sorted({entry["id"] for entry in positions}):
        specimen = next(item for item in specimens if item["id"] == ident)
        min_x, min_z, max_x, max_z = specimen["pad"]
        marker = next(((x, ROOT_Y, z) for x, z in ((min_x, min_z), (max_x, min_z), (min_x, max_z), (max_x, max_z))
                       if (x, ROOT_Y, z) not in cells), None)
        if marker is None:
            raise ValueError("gallery marker overlaps logical footprint")
        cells[marker] = ("minecraft:oak_sign", {"rotation": "8", "waterlogged": "false"})
        messages = [json.dumps({"text": text}, ensure_ascii=False, separators=(",", ":"))
                    for text in (ident, "review " + specimen["review_id"], "debug map + /tp", "")]
        front = Tag(TAG_COMPOUND, {"messages": Tag(TAG_LIST, [Tag(TAG_STRING, text) for text in messages], TAG_STRING),
                                   "color": Tag(TAG_STRING, "black"), "has_glowing_text": Tag(TAG_BYTE, 0)})
        data = {"id": Tag(TAG_STRING, "minecraft:sign"), "front_text": front,
                "back_text": front, "is_waxed": Tag(TAG_BYTE, 0)}
        data.update({axis: Tag(TAG_INT, value) for axis, value in zip(("x", "y", "z"), marker)})
        entities.append(Tag(TAG_COMPOUND, data))
        markers.append({"id": ident, "position": marker, "text": [ident, "review " + specimen["review_id"]]})
    write_fixture(output, cells, block_entities=entities, floor=False, level_name="Bloodborne NightmareRunning all-visible gallery")
    (output / "gallery-positions.json").write_text(json.dumps(positions, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    debug_map = {row["id"]: [entry for entry in positions if entry["id"] == row["id"]] for row in definitions}
    (output / "debug-family-map.json").write_text(json.dumps(debug_map, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    alt_proof = [entry for entry in positions if entry["purpose"].startswith("visual_proof_")]
    if len(alt_proof) != len(VISUAL_PROOF_IDS) * 2:
        raise ValueError("gallery must contain all visual BASE/ALT proof specimens")
    (output / "alt-proof-positions.json").write_text(json.dumps(alt_proof, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    (output / "gallery-markers.json").write_text(json.dumps(markers, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    (output / "README.txt").write_text(
        f"Synthetic NightmareRunning gallery: {len(definitions)} visible logical families, {len(positions)} specimens.\n"
        f"Each root is fixed at Y={ROOT_Y}; independent {floor} light pads sit at Y={FLOOR_Y}.\n"
        f"Pads are separated by at least {VOID_GAP} blocks of void, not a continuous floor; use gallery-positions.json /tp commands.\n"
        "Oak-sign family markers name each visible family; gallery-markers.json records their readable text.\n"
        "Use debug-family-map.json with /bloodborne debug target. alt-proof-positions.json contains three physical BASE/ALT pairs.\n"
        "No city/original world data was read or changed.\n", encoding="utf8")
    print(f"{output}: {len(definitions)} visible families, {len(positions)} specimens")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    build(parser.parse_args().output)
