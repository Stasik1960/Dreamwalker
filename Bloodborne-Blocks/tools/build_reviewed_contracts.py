"""Compile the manually approved Catalog-B batch-02, and nothing else.

This is intentionally a batch compiler rather than a discovery pipeline.  It
consumes the reviewed snapshot, preserves every selected raw source state in
``migration_source_pattern``, and never changes the review evidence itself.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import itertools
import json
import zipfile
from pathlib import Path

from logical_contract_v2 import MATRICES as TRANSFORM_MATRICES, box_cells, rotate_box
from source_assembly_visuals import archives, resource, source_polys
from source_review_decisions import DECISIONS, persist_decisions
from source_variant_rng import guards_for, resolve_choices

ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
RESOURCES = ROOT / "src/main/resources"
MANIFEST = ROOT / "docs/manual-review/source-assemblies/batch-02/batch-02-manifest.json"
AUTHORING = ROOT / "docs/reviewed-batch-02-authoring.json"
EVIDENCE = ROOT / "docs/reviewed-batch-02-evidence.json"
POC_BASELINE = ROOT / "docs/reviewed-batch-02-poc-baseline.json"
FACINGS = ("north", "east", "south", "west")
MATRICES = {0: ((1, 0), (0, 1)), 90: ((0, -1), (1, 0)), 180: ((-1, 0), (0, -1)), 270: ((0, 1), (-1, 0))}
TRANSFORM = {"rotations": TRANSFORM_MATRICES}
TEXTURE_DIGESTS = None
POC_IDS = ("o_dead_tree_planter", "o_cases_0", "o_wall_deco_1", "o_iron_gate", "o_iron_railing")
RAILING_DIRECTIONS = ("north", "east", "south", "west")
DISPLAY_NAMES = {
    "o_c001_a": ("Dead-tree planter A", "Клумба с сухим деревом A"),
    "o_c001_b": ("Dead-tree planter B", "Клумба с сухим деревом B"),
    "o_c009_a": ("Dead-tree planter C", "Клумба с сухим деревом C"),
    "o_c009_b": ("Dead-tree planter D", "Клумба с сухим деревом D"),
    "o_c002": ("Stone shrine", "Каменное святилище"), "o_c003": ("Gothic ruin", "Готические руины"),
    "o_c008": ("Pedestal statue", "Статуя на пьедестале"), "o_c471": ("Decorated bookshelf", "Декорированный книжный шкаф"),
    "o_c046": ("Stone marker", "Каменный знак"), "o_c1680": ("Wall shelf", "Настенная полка"),
    "o_c474": ("Timber post", "Деревянная стойка"), "o_c1962": ("Bookshelf", "Книжный шкаф"),
    "o_c1979": ("Library composition", "Библиотечная композиция"), "o_c028": ("Wall decoration", "Настенное украшение"),
    "o_c282": ("Dark oak door", "Дверь из тёмного дуба"), "o_c561": ("Wall ornament", "Настенный орнамент"),
    "o_c618": ("Hanging lantern", "Подвесной фонарь"), "o_c654": ("Wall window frame", "Настенная оконная рама"),
    "o_c1319": ("Stone pedestal", "Каменный пьедестал"), "o_c1491": ("Statue composition", "Скульптурная композиция"),
}


def dump(path, value, zipped=False, pretty=False):
    raw = ((json.dumps(value, ensure_ascii=False, indent=2) if pretty else json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))) + "\n").encode()
    if zipped:
        raw = gzip.compress(raw, compresslevel=6, mtime=0); raw = raw[:9] + b"\xff" + raw[10:]
    if not path.exists() or path.read_bytes() != raw:
        path.parent.mkdir(parents=True, exist_ok=True)
        # Windows may reject an in-place overwrite while a resource watcher
        # holds the old file.  A sibling write then atomic replace is safe for
        # deterministic reruns and never exposes a truncated gzip payload.
        temporary = path.with_name(path.name + ".reviewed-batch-02.tmp")
        temporary.write_bytes(raw); temporary.replace(path)


def key(props): return ",".join(f"{name}={value}" for name, value in sorted(props.items()))
def digest(value): return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
def state_values(state): return dict(part.split("=", 1) for part in state.split(",") if part)
def selected(row, numbers): return [part for part in row["components"] if part["number"] in numbers]
def source_components(pattern, numbers, anchor):
    """Raw matcher components plus visual evidence for the migration compiler."""
    origin = pattern.get("example", {}).get("anchor", [0, 0, 0])
    return [{"id": part["source"]["id"], "properties": part["source"]["properties"],
             # This is the sole matcher offset: already relative to the final
             # canonical master, never an intermediate source-pattern origin.
             "offset": [part["relative"][i] - anchor[i] for i in range(3)],
             "source_apps": part.get("apps", []), "model_choices": part.get("model_choices", []),
             "source_relative": part["relative"],
             "source_reference_position": [origin[i] + part["relative"][i] for i in range(3)]}
            for part in pattern["components"] if part["number"] in numbers]


def rotate_polygons(polygons, yaw, offset=(0, 0, 0), pivot=(.5, .5)):
    a, b = MATRICES[yaw]
    result = []
    for polygon in polygons:
        copied = dict(polygon); vertices = []
        for x, y, z, u, v in polygon["vertices"]:
            dx, dz = x - pivot[0], z - pivot[1]
            vertices.append([a[0] * dx + a[1] * dz + pivot[0] + offset[0], y + offset[1],
                             b[0] * dx + b[1] * dz + pivot[1] + offset[2], u, v])
        copied["vertices"] = vertices; result.append(copied)
    return result


def corrected_c282_polygons(polygons):
    """Repair only the documented bad south face UV; source data remains intact."""
    result = [dict(poly, vertices=[list(vertex) for vertex in poly["vertices"]]) for poly in polygons]
    for polygon in result:
        uv = [(vertex[3], vertex[4]) for vertex in polygon["vertices"]]
        if uv == [(6.25, 16.0), (6.625, 16.0), (6.625, 15.375), (6.25, 15.375)]:
            for vertex, replacement in zip(polygon["vertices"], ((6, 8), (0, 8), (0, 2), (6, 2))):
                vertex[3:] = replacement
    return result


def bounds(polygons):
    points = [vertex[:3] for polygon in polygons for vertex in polygon["vertices"]]
    return [min(point[i] for point in points) for i in range(3)] + [max(point[i] for point in points) for i in range(3)]


def policy(ident):
    if ident.startswith(("o_c001", "o_c009")): return "TRUNK", [[.25, 0, .25, .75, 7, .75]], [0, 0, 0, 1, 7, 1]
    if ident == "o_c474": return "POST", [[.375, 0, .375, .625, 16, .625]], [0, 0, 0, 1, 16, 1]
    if ident == "o_c654": return "SIMPLE_BOX", [[0, 0, .90625, 1, 1, .96875]], [0, 0, .90625, 1, 1, .96875]
    if ident == "o_c282": return "DOOR", [[-1, 0, .3125, 2, 3, .5625]], [-1, 0, .3125, 2, 3, .5625]
    if ident == "o_c008": return "TWO_BOX", [[0, 0, 0, 1, 1, 1], [.25, 1, .25, .75, 2, .75]], [0, 0, 0, 1, 2, 1]
    if ident == "o_c1979": return "TWO_BOX", [[.25, 0, .25, .75, 2, .75], [4.25, 0, .25, 4.75, 2, .75]], [0, 0, 0, 5, 2, 3]
    return "SIMPLE_BOX", [[0, 0, 0, 1, 1, 1]], [0, 0, 0, 1, 1, 1]


def definition(ident, properties, behavior="static", luminance=False):
    states = [{name: value for name, value in zip(properties, values)} for values in itertools.product(*properties.values())]
    return {"id": ident, "logical": True, "creative": True, "custom_geometry": True, "kind": "generic",
            "behavior": behavior, "semantic": "reviewed_batch_02", "properties": properties,
            "default": states[0], "states": {key(state): [0, 0, 0] for state in states},
            "models": {}, "extra_facing": "facing" in properties, "full_cube": False, "hardness": 1.5,
            "resistance": 6, "slipperiness": .6, "jump": 1, "velocity": 1, "layer": "cutout",
            "offset": "none", "source": "minecraft:stone", "emissive": luminance}


def copy_texture(texture):
    """Package every texture overridden by the source pack, including vanilla names."""
    global TEXTURE_DIGESTS
    namespace, local = texture.split(":", 1)
    source_pack, _vanilla = archives()
    source_path = f"assets/{namespace}/textures/{local}.png"
    if source_path not in source_pack.namelist():
        return texture
    raw = source_pack.read(source_path); digest = hashlib.sha256(raw).hexdigest()
    if TEXTURE_DIGESTS is None:
        TEXTURE_DIGESTS = {hashlib.sha256(path.read_bytes()).hexdigest(): path
                           for path in (RESOURCES / "assets/bloodborne_blocks/textures").rglob("*.png")}
    target = TEXTURE_DIGESTS.get(digest)
    if target is None:
        target_local = "block/reviewed-batch-02/" + namespace + "-" + local.replace("/", "-")
        target = RESOURCES / "assets/bloodborne_blocks/textures" / (target_local + ".png")
        target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw); TEXTURE_DIGESTS[digest] = target
    target_local = target.relative_to(RESOURCES / "assets/bloodborne_blocks/textures").with_suffix("").as_posix()
    return "bloodborne_blocks:" + target_local


def equivalent_mesh(a, b):
    def token(polygons):
        return sorted((polygon["texture"], tuple(sorted(tuple(round(value, 6) for value in vertex) for vertex in polygon["vertices"]))) for polygon in polygons)
    return token(a) == token(b)


def door_boxes(values, yaw):
    _policy, boxes, selection = policy("o_c282")
    source_height = values.get("placement_height") == "source_height"
    if source_height:
        # Migration keeps the carrier cell as the master.  The pack panel has
        # one artwork block below the manual placement, while its side cells
        # are real glass-pane frame.  Helpers reserve only the central passage.
        boxes = [[0, 0, .3125, 1, 2, .5625]]
        selection = [0, 0, .3125, 1, 2, .5625]
    if values["open"] == "true" and not source_height:
        pivot = (-1, .4375) if values["hinge"] == "left" else (2, .4375)
        leaf_yaw = 90 if values["hinge"] == "left" else 270
        boxes = [rotate_box(box, leaf_yaw, TRANSFORM, pivot=(pivot[0], 0, pivot[1])) for box in boxes]
        selection = rotate_box(selection, leaf_yaw, TRANSFORM, pivot=(pivot[0], 0, pivot[1]))
    elif values["open"] == "true":
        # The visual leaf still opens around its hinge; no migration helper may
        # claim either frame cell.  Retain the original passage as selection so
        # an open source-height door remains targetable to close.
        boxes = []
    return [rotate_box(box, yaw, TRANSFORM) for box in boxes], rotate_box(selection, yaw, TRANSFORM)


def app_token(apps):
    """Stable textured-source choice identity, excluding only RNG weight metadata."""
    return json.dumps([{key: value for key, value in app.items() if key != "weight"} for app in apps], sort_keys=True, separators=(",", ":"))


def source_variants(row, numbers, anchor, canonical_apps):
    """Resolve each reviewed source example with Minecraft's positional RNG."""
    entries, seen, patterns = [("canonical", canonical_apps)], {app_token(canonical_apps): "canonical"}, {}
    for pattern in row.get("source_patterns", []):
        origin = pattern["example"]["anchor"]; apps, guards = [], []
        parts = [part for part in pattern["components"] if part["number"] in numbers]
        weighted = any(any(len(group) > 1 for group in part.get("model_choices", [])) for part in parts)
        if not weighted:
            # Carrier/app yaw is orientation evidence, never an object variant.
            patterns[pattern["exact_source_signature"]] = {"variant": "canonical", "variant_guards": []}
            continue
        for part in parts:
            position = tuple(origin[i] + part["relative"][i] for i in range(3))
            chosen = resolve_choices(part, position)
            apps.extend({**app, "offset": [app.get("offset", part["relative"])[i] - anchor[i] for i in range(3)]} for app in chosen)
            guards.extend(guards_for(part, chosen, [part["relative"][i] - anchor[i] for i in range(3)]))
        token = app_token(apps)
        name = seen.get(token)
        if name is None:
            name = "observed_" + hashlib.sha256(token.encode()).hexdigest()[:10]; seen[token] = name; entries.append((name, apps))
        patterns[pattern["exact_source_signature"]] = {"variant": name, "variant_guards": guards}
    return entries, patterns


def c618_variant_rules(row, numbers, anchor):
    """Emit every source lantern alternative with a positional RNG guard.

    The state model intentionally has only the two lit art choices.  The
    third resource (`lantern_0`) is the unique unlit visual, not a lit model.
    """
    rules = {}
    for pattern in row.get("source_patterns", []):
        for part in (part for part in pattern["components"] if part["number"] in numbers):
            offset = [part["relative"][i] - anchor[i] for i in range(3)]
            for group in part.get("model_choices", []):
                for option in group:
                    if len(option) != 1: raise ValueError("C618 choice is not a single model")
                    model = option[0]["model"]
                    state = ("false", "canonical") if model.endswith("lantern_0") else (
                        ("true", "lantern_1") if model.endswith("lantern_1") else ("true", "canonical"))
                    key_value = (pattern["exact_source_signature"], *state)
                    rules.setdefault(key_value, []).extend(guards_for(part, option, offset))
    return rules


def rotate_connections(values, yaw):
    """Rotate a railing's world-connection mask with the same X/Z matrix as its mesh."""
    clockwise = {"north": "east", "east": "south", "south": "west", "west": "north"}
    result = {direction: "false" for direction in RAILING_DIRECTIONS}
    turns = (yaw // 90) % 4
    for direction in RAILING_DIRECTIONS:
        target = direction
        for _ in range(turns): target = clockwise[target]
        result[target] = values[direction]
    return result


def strip_north_facing(state):
    values = state_values(state)
    if values.pop("facing", "north") != "north": raise ValueError("POC baseline requested non-north facing")
    return key(values)


def normalize_iron_railing(definitions_doc, contracts, geometry, meshes):
    """Give the non-90-symmetric POC railing explicit orientation without changing north."""
    block = next(row for row in definitions_doc["blocks"] if row["id"] == "o_iron_railing")
    family = next(row for row in contracts["families"] if row["id"] == "o_iron_railing")
    old_models = {strip_north_facing(state) if "facing=" in state else state: model
                  for state, model in block["models"].items() if "facing=" not in state or "facing=north" in state}
    old_luminance = {strip_north_facing(state) if "facing=" in state else state: value
                     for state, value in block["states"].items() if "facing=" not in state or "facing=north" in state}
    old_contracts = {strip_north_facing(state) if "facing=" in state else state: value
                     for state, value in family["states"].items() if "facing=" not in state or "facing=north" in state}
    if len(old_models) != 16 or set(old_models) != set(old_contracts): raise ValueError("unexpected POC railing state set")
    old_geometry = geometry["blocks"]["o_iron_railing"]["states"]
    old_geometry = {strip_north_facing(state) if "facing=" in state else state: value
                    for state, value in old_geometry.items() if "facing=" not in state or "facing=north" in state}
    if set(old_models) != set(old_geometry): raise ValueError("unexpected POC railing geometry state set")
    for mesh in [name for name in meshes if name.startswith("poc_railing_rot_")]: del meshes[mesh]
    for profile in [name for name in geometry["profiles"] if name.startswith("g_poc_railing_rot_")]: del geometry["profiles"][profile]
    connections = {direction: block["properties"][direction] for direction in RAILING_DIRECTIONS}
    block["properties"] = {**connections, "facing": list(FACINGS)}
    block["default"] = {**{direction: block["default"][direction] for direction in RAILING_DIRECTIONS}, "facing": "north"}
    block["extra_facing"] = True; block["models"] = {}; block["states"] = {}; family["states"] = {}
    geometry["blocks"]["o_iron_railing"]["states"] = {}
    for target_key in sorted(old_models):
        target_mask = state_values(target_key)
        for facing, yaw in zip(FACINGS, (0, 90, 180, 270)):
            base_key = key(rotate_connections(target_mask, (-yaw) % 360))
            old_mesh = old_models[base_key]; base_contract = copy.deepcopy(old_contracts[base_key])
            state = key({**target_mask, "facing": facing})
            mesh = old_mesh
            if yaw:
                mesh = "poc_railing_rot_" + hashlib.sha256((old_mesh + ":" + str(yaw)).encode()).hexdigest()[:20]
                meshes[mesh] = {"polygons": rotate_polygons(meshes[old_mesh]["polygons"], yaw)}
            block["models"][state] = mesh; block["states"][state] = copy.deepcopy(old_luminance[base_key])
            base_contract["rotation"] = yaw
            base_contract["render_mesh"] = {"id": mesh, "bounds": bounds(meshes[mesh]["polygons"]), "offset": [0, 0, 0]}
            base_contract["selection_footprint"] = {"boxes": [rotate_box(box, yaw, TRANSFORM) for box in base_contract["selection_footprint"]["boxes"]]}
            state_boxes = [rotate_box(box, yaw, TRANSFORM) for box in base_contract["collision_footprint"]["boxes"]]
            base_contract["collision_footprint"] = {"boxes": state_boxes}
            base_contract["interaction_footprint"] = {"cells": [list(cell) for cell in sorted({(0, 0, 0), *(cell for box in state_boxes for cell in box_cells(box))})]}
            if yaw: base_contract["migration_source_pattern"] = []
            family["states"][state] = base_contract
            profile = old_geometry[base_key]
            if yaw:
                source_profile = copy.deepcopy(geometry["profiles"][profile["ref"]])
                for cell in source_profile["cells"].values():
                    for kind in ("outline", "collision"):
                        cell[kind] = [rotate_box(box, yaw, TRANSFORM) for box in cell.get(kind, [])]
                profile = {"ref": "g_poc_railing_rot_" + hashlib.sha256((profile["ref"] + ":" + str(yaw)).encode()).hexdigest()[:20]}
                geometry["profiles"][profile["ref"]] = source_profile
            geometry["blocks"]["o_iron_railing"]["states"][state] = profile


def poc_baseline_view(ident, definition, contract):
    """Return the frozen POC projection; railing facing=north is its compatible view."""
    if ident != "o_iron_railing": return definition, contract
    definition = copy.deepcopy(definition); contract = copy.deepcopy(contract)
    definition["properties"].pop("facing"); definition["default"].pop("facing"); definition["extra_facing"] = False
    definition["models"] = {strip_north_facing(state): mesh for state, mesh in definition["models"].items() if "facing=north" in state}
    definition["states"] = {strip_north_facing(state): value for state, value in definition["states"].items() if "facing=north" in state}
    contract["states"] = {strip_north_facing(state): value for state, value in contract["states"].items() if "facing=north" in state}
    return definition, contract


def write_display_names():
    for locale, index in (("en_us", 0), ("ru_ru", 1)):
        path = RESOURCES / "assets/bloodborne_blocks/lang" / (locale + ".json")
        names = json.loads(path.read_text(encoding="utf8")); changed = False
        for ident, pair in DISPLAY_NAMES.items():
            name_key = "block.bloodborne_blocks." + ident
            if names.get(name_key) != pair[index]: names[name_key] = pair[index]; changed = True
        if changed: path.write_text(json.dumps(names, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf8")


def write_assets(ident, block):
    variants = {state: {"model": "bloodborne_blocks:block/logical/" + mesh} for state, mesh in block["models"].items()}
    dump(RESOURCES / "assets/bloodborne_blocks/blockstates" / (ident + ".json"), {"variants": variants})
    for mesh in sorted(set(block["models"].values())):
        # Textures are inferred from the mesh at call site and supplied later.
        pass
    default = key(block["default"])
    dump(RESOURCES / "assets/bloodborne_blocks/models/item" / (ident + ".json"),
         {"parent": "bloodborne_blocks:block/logical/" + block["models"][default], "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.625, .625, .625]}}})
    dump(RESOURCES / "data/bloodborne_blocks/loot_tables/blocks" / (ident + ".json"),
         {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bloodborne_blocks:" + ident}]}]})


def write_railing_assets(block, meshes):
    """Only the new oriented railing output is compiler-owned.

    The historic item model and loot table carry hand-authored particle/GUI
    details, so generation must not replace them while adding facings.
    """
    variants = {state: {"model": "bloodborne_blocks:block/logical/" + mesh} for state, mesh in block["models"].items()}
    dump(RESOURCES / "assets/bloodborne_blocks/blockstates/o_iron_railing.json", {"variants": variants})
    for mesh in sorted(set(block["models"].values())):
        if not mesh.startswith("poc_railing_rot_"):
            continue
        textures = sorted({poly["texture"] for poly in meshes[mesh]["polygons"]})
        dump(RESOURCES / "assets/bloodborne_blocks/models/block/logical" / (mesh + ".json"),
             {"parent": "minecraft:block/block", "textures": {"particle": textures[0] if textures else "minecraft:block/stone",
              **{str(i): texture for i, texture in enumerate(textures)}}, "elements": []})


def build():
    persist_decisions(AUTHORING, source_manifest=MANIFEST)
    authoring = json.loads(AUTHORING.read_text(encoding="utf8"))
    manifest = json.loads(MANIFEST.read_text(encoding="utf8")); rows = {row["review_id"]: row for row in manifest["candidates"]}
    batch_ids = {family["id"] for family in authoring["families"]}
    definitions_doc = json.loads((LOGICAL / "definitions.json").read_text(encoding="utf8"))
    definitions_doc["blocks"] = [block for block in definitions_doc["blocks"] if block["id"] not in batch_ids]
    with gzip.open(LOGICAL / "meshes.json.gz", "rt", encoding="utf8") as stream: meshes = json.load(stream)
    for mesh in [name for name in meshes if name.startswith("reviewed_batch_02_")]: del meshes[mesh]
    # Mesh JSON names are owned exclusively by this compiler.  Removing stale
    # state variants prevents prior generation runs from becoming dead assets.
    for path in (RESOURCES / "assets/bloodborne_blocks/models/block/logical").glob("reviewed_batch_02_*.json"):
        path.unlink()
    contracts = json.loads((LOGICAL / "contracts-v2.json").read_text(encoding="utf8"))
    contracts["families"] = [family for family in contracts["families"] if family["id"] not in batch_ids]
    geometry = json.loads((LOGICAL / "geometry.json").read_text(encoding="utf8"))
    geometry["blocks"] = {ident: value for ident, value in geometry["blocks"].items() if ident not in batch_ids}
    baseline = json.loads(POC_BASELINE.read_text(encoding="utf8"))
    normalize_iron_railing(definitions_doc, contracts, geometry, meshes)
    evidence = {"schemaVersion": 1, "batch": "batch-02", "source_manifest_sha256": hashlib.sha256(MANIFEST.read_bytes()).hexdigest(), "pocPreservedHashes": baseline["poc"],
                "pocExceptions": {"o_iron_railing": "Explicit facing states; each facing=north state and mesh is byte-for-byte equivalent to the frozen POC baseline after stripping facing=north."}, "families": []}
    for authored in authoring["families"]:
        ident, row, numbers = authored["id"], rows[authored["review_id"]], authored["component_numbers"]
        parts = selected(row, numbers); anchor = min((part["relative"] for part in parts), key=lambda p: (p[1], p[2], p[0]))
        apps = [{**app, "offset": [app.get("offset", part["relative"])[i] - anchor[i] for i in range(3)]}
                for part in parts for app in part["apps"]]
        # C618 selects exactly one reviewed art alternative per lit state.  RNG
        # variants are deliberately left to the dedicated source-RNG resolver.
        choices = parts[0].get("model_choices", []) if ident == "o_c618" else []
        lit_apps = apps; unlit_apps = apps; lit_alt_apps = None
        if choices:
            alternatives = [app for group in choices for option in group for app in option]
            unlit = [app for app in alternatives if app["model"].endswith("lantern_0")]
            lit = [app for app in alternatives if app["model"].endswith("/lantern")]
            lit_alt = [app for app in alternatives if app["model"].endswith("lantern_1")]
            if unlit: unlit_apps = [{**unlit[0], "offset": [0, 0, 0]}]
            if lit: lit_apps = [{**lit[0], "offset": [0, 0, 0]}]
            if lit_alt: lit_alt_apps = [{**lit_alt[0], "offset": [0, 0, 0]}]
        variants, source_pattern_variants = source_variants(row, numbers, anchor, lit_apps)
        c618_rules = None
        if ident == "o_c618":
            if lit_alt_apps is None or not unlit_apps or not lit_apps: raise ValueError("C618 missing reviewed lantern art")
            # Compile all three source resources, whether or not the one
            # representative RNG position happened to select each of them.
            variants = [("canonical", lit_apps), ("lantern_1", lit_alt_apps)]
            c618_rules = c618_variant_rules(row, numbers, anchor)
            source_pattern_variants = {signature: {"variant": "canonical", "variant_guards": []}
                                       for signature in source_pattern_variants}
        if ident == "o_c282":
            # The four dark-door carrier applications are rotations of one
            # panel, not random art alternatives or separate C/D variants.
            variants = [("canonical", lit_apps)]
            source_pattern_variants = {signature: {"variant": "canonical", "variant_guards": []}
                                       for signature in source_pattern_variants}
        if ident == "o_c654":
            # The manual surface is opaque-wall safe; source_depth keeps the
            # original pack fraction exclusively for lossless migration.
            variants = [("surface", lit_apps), ("source_depth", lit_apps)]
            source_pattern_variants = {signature: {"variant": "source_depth", "variant_guards": []}
                                       for signature in source_pattern_variants}
        symmetry_source = corrected_c282_polygons(source_polys(lit_apps)[0]) if ident == "o_c282" else source_polys(lit_apps)[0]
        needs_facing = ident in ("o_c282", "o_c654") or not equivalent_mesh(symmetry_source, rotate_polygons(symmetry_source, 90))
        props = {"facing": list(FACINGS)} if needs_facing else {}
        if len(variants) > 1: props["variant"] = [name for name, _apps in variants]
        behavior = "static"; light = False
        if ident == "o_c282":
            props.update({"placement_height": ["manual", "source_height"], "hinge": ["left", "right"], "open": ["false", "true"]})
            behavior = "door"
        if ident == "o_c618": props["lit"] = ["false", "true"]; behavior = "lantern"; light = True
        block = definition(ident, props, behavior, light)
        if ident == "o_c618": block["default"]["lit"] = "true"
        if ident == "o_c654":
            block["default"]["variant"] = "surface"
            block['placement_properties']={'variant':'surface'}
        if ident == 'o_c282': block['placement_properties']={'placement_height':'manual'}
        collision_policy, collision_boxes, selection = policy(ident)
        family = {"id": ident, "review_id": authored["review_id"], "authority": "user", "canonical_anchor": {"cell": [0, 0, 0], "pivot": [.5, 0, .5]},
                  "placement_policy": "WALL_ADJACENT" if ident == "o_c654" else "FLOOR", "rotations": [0, 90, 180, 270],
                  "mirror_policy": "ROTATE_ONLY", "collision_policy": collision_policy,
                  "collision_justification": "Explicit authored gameplay primitive; source polygons never derive helpers or collision.", "states": {}}
        if collision_policy == "SIMPLE_BOX": family.pop("collision_justification")
        for state in list(block["states"]):
            values = dict(part.split("=", 1) for part in state.split(",") if part); yaw = FACINGS.index(values["facing"]) * 90 if "facing" in values else 0
            variant_apps = dict(variants).get(values.get("variant", "canonical"), lit_apps)
            visual_apps = unlit_apps if values.get("lit") == "false" else variant_apps
            source_polygons = source_polys(visual_apps)[0]
            if ident == "o_c282":
                source_polygons = corrected_c282_polygons(source_polygons)
                if values["placement_height"] == "manual":
                    source_polygons = rotate_polygons(source_polygons, 0, offset=(0, 1, 0))
                # Representative application is y=270; this exact +90 pack
                # transform establishes a yaw=0 north panel before hinge work.
                source_polygons = rotate_polygons(source_polygons, 90)
                if values["open"] == "true":
                    pivot = (-1, .4375) if values["hinge"] == "left" else (2, .4375)
                    source_polygons = rotate_polygons(source_polygons, 90 if values["hinge"] == "left" else 270, pivot=pivot)
            elif ident == "o_c654":
                # Integer bake before yaw keeps every horizontal wall placement
                # tied to the same master; no state-dependent render offset.
                source_polygons = rotate_polygons(source_polygons, 0, offset=(0, 1, 0))
                # The source app's thin axis is X.  Its canonical north wall
                # face is the south Z edge; this is pack-application rotation,
                # not a carrier-facing assumption.
                source_polygons = rotate_polygons(source_polygons, 270)
                if values["variant"] == "surface":
                    # Authored correction: a 3/16-block inset prevents opaque
                    # vanilla wall occlusion while retaining zero render offset.
                    source_polygons = rotate_polygons(source_polygons, 0, offset=(0, 0, -.1875))
            polygons = rotate_polygons(source_polygons, yaw)
            for polygon in polygons: polygon["texture"] = copy_texture(polygon["texture"])
            mesh = "reviewed_batch_02_" + ident[2:] + "_" + hashlib.sha256(state.encode()).hexdigest()[:12]
            meshes[mesh] = {"polygons": polygons}; block["models"][state] = mesh
            render_offset = [0, 0, 0]
            patterns = []
            if (values.get("open", "false") == "false" and values.get("hinge", "left") == "left" and yaw == 0
                    and (ident == "o_c618" or values.get("lit", "true") == "true")
                    and (ident != "o_c282" or values["placement_height"] == "source_height")):
                patterns = [{"components": source_components(pattern, numbers, anchor),
                             "canonical_master_source_anchor": anchor,
                             # Review geometry compares the original pack application
                             # (not carrier-facing) to the canonical visual yaw.
                             "source_rotation": pattern.get("review_equivalence", {}).get("yaw", 0),
                             "source_master_offset": ([0, -1, 0] if ident == "o_c654" else anchor), "exact_source_signature": pattern["exact_source_signature"],
                             "review_id": authored["review_id"], "authority": "user",
                             **({"source_visual": {"translation": [0, 0, 0], "placement_height": "source_height",
                                                    "pack_application_y": pattern["components"][0]["apps"][0].get("y", 0),
                                                    "canonical_yaw": (-pattern["components"][0]["apps"][0].get("y", 0)) % 360}} if ident == "o_c282" else
                                {"source_visual": {"translation": [0, 1, 0]}} if ident == "o_c654" else {}),
                             **source_pattern_variants[pattern["exact_source_signature"]]}
                            for pattern in row.get("source_patterns", [])
                            if (c618_rules is None and source_pattern_variants[pattern["exact_source_signature"]]["variant"] == values.get("variant", "canonical")) or
                            (c618_rules is not None and (pattern["exact_source_signature"], values["lit"], values.get("variant", "canonical")) in c618_rules)]
                if c618_rules is not None:
                    for pattern in patterns:
                        pattern["variant"] = values.get("variant", "canonical")
                        pattern["variant_guards"] = c618_rules[(pattern["exact_source_signature"], values["lit"], values.get("variant", "canonical"))]
            state_boxes, state_selection = door_boxes(values, yaw) if ident == "o_c282" else (
                [rotate_box(box, yaw, TRANSFORM) for box in collision_boxes], rotate_box(selection, yaw, TRANSFORM))
            interaction = sorted({(0, 0, 0), *(cell for box in state_boxes for cell in box_cells(box))})
            family["states"][state] = {"rotation": yaw, "render_mesh": {"id": mesh, "bounds": bounds(polygons), "offset": render_offset},
                                         "selection_footprint": {"boxes": [state_selection]}, "collision_footprint": {"boxes": state_boxes},
                                         "interaction_footprint": {"cells": [list(cell) for cell in interaction]}, "migration_source_pattern": patterns}
            if ident == "o_c618" and values["lit"] == "true": block["states"][state][2] = 15
        definitions_doc["blocks"].append(block); contracts["families"].append(family)
        geometry_states = {}
        for state in block["states"]:
            values = dict(part.split("=", 1) for part in state.split(",") if part)
            if ident == "o_c282" and values["placement_height"] == "source_height":
                contract_state = family["states"][state]
                outline = contract_state["selection_footprint"]["boxes"]
                collision = contract_state["collision_footprint"]["boxes"]
            else:
                outline = [[0, 0, 0, 1, 1, 1]] if collision_policy != "NONE" else []
                collision = [[0, 0, 0, 1, 1, 1]] if collision_policy != "NONE" else []
            geometry_states[state] = {"cells": {"0,0,0": {"outline": outline, "collision": collision}}, "anchor": [0, 0, 0]}
        from sync_reviewed_geometry import profile
        geometry["blocks"][ident] = profile(family)
        for mesh in set(block["models"].values()):
            textures = sorted({poly["texture"] for poly in meshes[mesh]["polygons"]})
            dump(RESOURCES / "assets/bloodborne_blocks/models/block/logical" / (mesh + ".json"), {"parent": "minecraft:block/block", "textures": {"particle": textures[0] if textures else "minecraft:block/stone", **{str(i): texture for i, texture in enumerate(textures)}}, "elements": []})
        write_assets(ident, block)
        evidence["families"].append({**authored, "canonical_anchor": anchor, "visual_component_count": len(parts), "source_pattern_count": len(row.get("source_patterns", [])), "context_excluded": authored["context_numbers"],
                                     **({"derivedUVfix": "C282 south face [6.25,15.375,6.625,16] replaced by mirrored north door UV; source pack untouched"} if ident == "o_c282" else {}),
                                     **({"authoredDepthCorrection": "surface variant shifts the canonical face by -0.1875Z before state yaw; source_depth retains the source-pack fractional depth for migration only"} if ident == "o_c654" else {})})
    definitions = {row["id"]: row for row in definitions_doc["blocks"]}; contract_rows = {row["id"]: row for row in contracts["families"]}
    for ident, expected in baseline["poc"].items():
        definition_view, contract_view = poc_baseline_view(ident, definitions[ident], contract_rows[ident])
        if digest(definition_view) != expected["definition"] or digest(contract_view) != expected["contract"]:
            raise ValueError("POC contract/definition changed: " + ident)
        if {state: digest(meshes[mesh]) for state, mesh in definition_view["models"].items()} != expected["meshes"]:
            raise ValueError("POC mesh changed: " + ident)
    railing = definitions["o_iron_railing"]
    write_railing_assets(railing, meshes)
    write_display_names()
    dump(LOGICAL / "definitions.json", definitions_doc)
    dump(LOGICAL / "contracts-v2.json", contracts, pretty=True)
    dump(LOGICAL / "geometry.json", geometry)
    dump(LOGICAL / "meshes.json.gz", meshes, zipped=True)
    dump(EVIDENCE, evidence)
    print("reviewed batch-02:", len(authoring["families"]), "families")


if __name__ == "__main__":
    build()
    from compile_reviewed_migration import build as compile_migration
    compile_migration()
