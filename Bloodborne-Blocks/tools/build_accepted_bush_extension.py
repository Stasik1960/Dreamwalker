"""Build the single explicitly accepted grass-0 repair bundle.

The bundle is an additive hand-off for the release integrator.  It deliberately
does not edit logical resources or approve the seven other weighted grass
alternatives.
"""
from __future__ import annotations

import copy
import gzip
import json
from pathlib import Path

from source_assembly_visuals import resource, source_polys
from source_variant_rng import guards_for, resolve_choices

ROOT = Path(__file__).resolve().parents[1]
FROZEN = ROOT / "docs/production-authoring-inputs.json.gz"
OUTPUT = ROOT / "build/complete-accepted-repair/bush-extension.json"
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
ASSETS = ROOT / "src/main/resources/assets/bloodborne_blocks"
IDENT = "o_grass_0"
PROFILE = "g_897dd4c17017104d1037"
SOURCE = "minecraft:dead_fire_coral_fan"
FACINGS = ("north", "east", "south", "west")
VISUALS = ("base", "alt")


def read_frozen():
    with gzip.open(FROZEN, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def state_key(facing, visual):
    return f"facing={facing},visual={visual}"


def source_pattern():
    """Return only the real weighted source choice that renders grass_0."""
    blockstate = json.loads(resource(SOURCE, "blockstates", "json"))
    choices = blockstate.get("variants", {}).get("")
    expected = [f"minecraft:block/addon/grass_{number}" for number in range(8)]
    if not isinstance(choices, list) or [choice.get("model") for choice in choices] != expected:
        raise ValueError("dead_fire_coral_fan weighted source changed")
    if [choice.get("weight") for choice in choices] != [100] * 8:
        raise ValueError("dead_fire_coral_fan weights changed")
    component = {"id": SOURCE, "model_choices": [[[copy.deepcopy(choice)] for choice in choices]]}
    # This fixed sample is solely evidence that index zero is grass_0.  The
    # emitted guard evaluates at each actual source position during migration.
    selected = resolve_choices(component, (-32, -2, -30))
    if selected != [choices[0]]:
        raise ValueError("dead_fire_coral_fan no longer selects grass_0 at index zero")
    return {"components": [{"id": SOURCE, "properties": {"waterlogged": "false"}, "offset": [0, 0, 0],
                              "source_apps": selected, "model_choices": component["model_choices"]}],
            "variant_guards": guards_for(component, selected, [0, 0, 0])}


def texture_local(polygons):
    """Compare source art across the historical namespace relocation."""
    return [{**polygon, "texture": polygon["texture"].split(":", 1)[-1]} for polygon in polygons]


def bounds(polygons):
    points = [vertex[:3] for polygon in polygons for vertex in polygon["vertices"]]
    return [min(point[axis] for point in points) for axis in range(3)] + [max(point[axis] for point in points) for axis in range(3)]


def current_contract(definition, profile, pattern, mesh_bounds):
    frozen_cells = [[int(value) for value in key.split(",")] for key in profile["cells"]]
    physical = {state_key(facing, visual): {"cells": copy.deepcopy(frozen_cells), "boxes": []}
                for facing in FACINGS for visual in VISUALS}
    selection = []
    for cell_key, cell in profile["cells"].items():
        x, y, z = (int(value) for value in cell_key.split(","))
        for box in cell["outline"]:
            selection.append([box[0] + x, box[1] + y, box[2] + z,
                              box[3] + x, box[4] + y, box[5] + z])
    if not selection:
        raise ValueError("frozen grass must retain selection outlines")
    states = {}
    for facing in FACINGS:
        for visual in VISUALS:
            key = state_key(facing, visual)
            states[key] = {
                "rotation": {"north": 0, "east": 90, "south": 180, "west": 270}[facing],
                "render_mesh": {"id": definition["models"][key], "bounds": mesh_bounds, "offset": [0, 0, 0]},
                "selection_footprint": {"boxes": copy.deepcopy(selection)},
                "collision_footprint": {"boxes": []},
                "interaction_footprint": {"cells": copy.deepcopy(frozen_cells)},
                "migration_source_pattern": [copy.deepcopy(pattern)] if key == state_key("north", "base") else [],
            }
    return {"id": IDENT, "canonical_anchor": {"cell": [0, 0, 0], "pivot": [0.5, 0, 0.5]},
            "collision_policy": "NONE", "placement_policy": "FLOOR", "mirror_policy": "ROTATE_ONLY",
            "rotations": [0, 90, 180, 270], "states": states}, physical


def build(output=OUTPUT):
    frozen = read_frozen()
    definitions = [copy.deepcopy(block) for block in frozen["definitions"]["blocks"] if block["id"] == IDENT]
    if len(definitions) != 1:
        raise ValueError("expected one frozen o_grass_0 definition")
    definition = definitions[0]
    states = {state_key(facing, visual) for facing in FACINGS for visual in VISUALS}
    if set(definition["states"]) != states or set(definition["models"]) != states:
        raise ValueError("frozen grass states changed")
    mesh_ids = sorted(set(definition["models"].values()))
    meshes = {mesh: copy.deepcopy(frozen["meshes"][mesh]) for mesh in mesh_ids}
    geometry = copy.deepcopy(frozen["geometry"]["blocks"][IDENT])
    if set(geometry["states"]) != states or {row.get("ref") for row in geometry["states"].values()} != {PROFILE}:
        raise ValueError("frozen grass geometry no longer uses its one approved profile")
    profile = copy.deepcopy(frozen["geometry"]["profiles"][PROFILE])
    if set(profile.get("cells", ())) != {"0,0,0", "0,1,0"} or any(cell["collision"] for cell in profile["cells"].values()):
        raise ValueError("frozen grass collision changed")

    pattern = source_pattern()
    source_polygons, _ = source_polys(pattern["components"][0]["source_apps"])
    north_mesh = meshes[definition["models"][state_key("north", "base")]]["polygons"]
    if texture_local(source_polygons) != texture_local(north_mesh):
        raise ValueError("grass_0 source application no longer matches frozen authored mesh")
    contract, physical = current_contract(definition, profile, pattern, bounds(source_polygons))
    # Production geometry deliberately has no legacy profile references.
    # Inline the same frozen cells without changing their art/selection shape.
    geometry = {"states": {key: copy.deepcopy(profile) for key in sorted(states)}}
    bundle = {
        "format": "bloodborne-accepted-bush-extension-v1",
        "scope": "Only the accepted weighted dead_fire_coral_fan grass_0 art; other grass alternatives remain unapproved.",
        "definitions": {"blocks": definitions},
        "meshes": meshes,
        "geometry": {"blocks": {IDENT: geometry}, "profiles": {}},
        "physical": {"schemaVersion": 1, "basis": "frozen empty-collision two-cell profile", "families": {IDENT: physical}},
        "currentContracts": {"schemaVersion": 2, "transform_contract": "transform-v2.json", "families": [contract]},
        "productionPaletteEntry": {"id": IDENT, "semantic_label": "Dry dense growth", "source_reviews": ["F004"]},
    }
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(bundle, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
    return bundle


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


def append_once(rows, row, ident=IDENT):
    present = [value for value in rows if value.get("id") == ident]
    if present:
        if present != [row]:
            raise ValueError("existing grass resource differs")
        return
    rows.append(row)
    rows.sort(key=lambda value: value["id"])


def apply(bundle):
    """Merge this one accepted family without rewriting any other family."""
    definition = bundle["definitions"]["blocks"][0]
    contract = bundle["currentContracts"]["families"][0]
    physical = bundle["physical"]["families"][IDENT]
    for name, row in (("definitions.json", definition), ("contracts-v2.json", contract)):
        path = LOGICAL / name
        data = json.loads(path.read_text(encoding="utf-8")); append_once(data["blocks" if name == "definitions.json" else "families"], row); write_json(path, data)
    geometry_path = LOGICAL / "geometry.json"; geometry = json.loads(geometry_path.read_text(encoding="utf-8"))
    if IDENT in geometry["blocks"] and geometry["blocks"][IDENT] != bundle["geometry"]["blocks"][IDENT]: raise ValueError("existing grass geometry differs")
    geometry["blocks"][IDENT] = bundle["geometry"]["blocks"][IDENT]
    write_json(geometry_path, geometry)
    physical_path = LOGICAL / "physical-footprints.json"; physics = json.loads(physical_path.read_text(encoding="utf-8"))
    if IDENT in physics["families"] and physics["families"][IDENT] != physical: raise ValueError("existing grass physical footprint differs")
    physics["families"][IDENT] = physical; write_json(physical_path, physics)
    slots_path = LOGICAL / "visual-slots.json"; slots = json.loads(slots_path.read_text(encoding="utf-8"))
    append_once(slots["families"], {"id": IDENT, "states": 8, "base_states": 4}); write_json(slots_path, slots)
    logical_palette_path = LOGICAL / "production-palette.json"; logical_palette = json.loads(logical_palette_path.read_text(encoding="utf-8"))
    append_once(logical_palette["objects"], bundle["productionPaletteEntry"]); write_json(logical_palette_path, logical_palette)
    meshes_path = LOGICAL / "meshes.json.gz"
    with gzip.open(meshes_path, "rt", encoding="utf-8") as stream: meshes = json.load(stream)
    for mesh, value in bundle["meshes"].items():
        if mesh in meshes and meshes[mesh] != value: raise ValueError("existing grass mesh differs")
        meshes[mesh] = value
    raw = json.dumps(meshes, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    meshes_path.write_bytes(gzip.compress(raw, mtime=0))
    texture = "bloodborne_blocks:block/addon/spirelamp_0046"
    for key, model in definition["visual_models"].items():
        mesh = definition["models"][key]
        path = ASSETS / "models" / (model.split(":", 1)[1] + ".json")
        value = {"parent": "minecraft:block/block", "bloodborne_mesh": mesh,
                 "bloodborne_texture_slots": {texture: "#t0"}, "textures": {"particle": texture, "t0": texture}}
        if 'visual=alt' in key.split(','):
            value = {"parent": model.replace('/alt/','/base/')}
        if path.exists() and json.loads(path.read_text(encoding="utf-8")) != value: raise ValueError("existing grass model differs")
        write_json(path, value)
    variants = {key: {"model": model} for key, model in definition["visual_models"].items()}
    write_json(ASSETS / "blockstates/o_grass_0.json", {"variants": variants})
    frozen = read_frozen(); item = frozen["items"][IDENT]
    write_json(ASSETS / "models/item/o_grass_0.json", item)
    write_json(ROOT / "src/main/resources/data/bloodborne_blocks/loot_tables/blocks/o_grass_0.json",
               {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bloodborne_blocks:o_grass_0"}]}]})
    language_key = "block.bloodborne_blocks.o_grass_0"
    for locale in ("en_us", "ru_ru"):
        path = ASSETS / f"lang/{locale}.json"; language = json.loads(path.read_text(encoding="utf-8")); language[language_key] = frozen["languages"][locale][language_key]; write_json(path, language)
    required_path = ROOT / "docs/required-production-families.json"; required = json.loads(required_path.read_text(encoding="utf-8"))
    if IDENT not in required["retained_reviewed_ids"]: required["retained_reviewed_ids"].append(IDENT); required["retained_reviewed_ids"].sort()
    required["expected_production_count"] = 50; write_json(required_path, required)
    palette_path = ROOT / "docs/production-logical-palette.json"; palette = json.loads(palette_path.read_text(encoding="utf-8"))
    entry = {"id": IDENT, "status": "PRODUCTION", "semantic_label": frozen["languages"]["en_us"][language_key],
             "source_reviews": ["current explicit Complete Accepted request", "frozen authored grass_0"],
             "provenance": ["current explicit Complete Accepted request", "frozen authored grass_0"],
             "migration_redirect": None, "placement_policy": "FLOOR", "collision_policy": "NONE", "source_patterns": [], "source_occurrences": 0,
             "compiled_source_patterns": [{"target_state": "facing=north,visual=base", "pattern": contract["states"]["facing=north,visual=base"]["migration_source_pattern"][0]}],
             "occurrence_scope": "only weighted dead_fire_coral_fan selections resolving to grass_0", "model_variant_relationship": {"facing": list(FACINGS), "visual": list(VISUALS)},
             "visual": {"base": "frozen authored grass_0", "alt": "BASE fallback; same gameplay"}, "migration_target": IDENT,
             "migration_policy": "exact raw source + index-zero RNG guard; unmatched grass variants remain unapproved"}
    palette["excluded"] = [row for row in palette["excluded"] if row.get("id") != IDENT]
    append_once(palette["objects"], entry); write_json(palette_path, palette)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--apply", action="store_true")
    args = parser.parse_args(); result = build()
    if args.apply: apply(result)
