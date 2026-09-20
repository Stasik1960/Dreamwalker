"""Build the additive, lossless logical-object resource palette.

This is deliberately separate from ``build_modular_palette.py``.  It reads the
authored legacy models and v3 curation, but writes only ``logical/`` and the
``o_*`` client assets.  In particular, it never rewrites v2 or legacy data.
"""
from __future__ import annotations

import argparse
import functools
import gzip
import hashlib
import itertools
import json
import re
from collections import defaultdict
from pathlib import Path

import numpy as np

from catalog_geometry import ASSETS, DATA, RES, ROOT, applications, corners, model, rotation
from modular_mesh import FACE_INDEX, alpha_patch, clip, face_uv, implicit_uv, polygon, split_element, texname, tile_uv

FACING = ("north", "east", "south", "west")
MOUNT_FACES = {0: "floor", 90: "wall", 180: "ceiling"}
LOGICAL = RES / "bloodborne_blocks" / "logical"
ORIGINAL_PACK = Path.home() / "Downloads" / "bloodborne.zip"
AUTO_CATEGORIES = {"bush", "plant", "container", "statue", "ornament", "ladder", "lamp"}
EXCLUDED_WORDS = ("door", "window_bottom", "window_top", "tree", "dead_fire_coral_block", "dead_tube_coral_block")
TREE_PIECES = {"acacia_log", "jungle_log", "dark_oak_log", "birch_log", "spruce_log", "oak_log"}
# The importer turns an authored zero-width face into this exact half-unit slab.
# Never collapse a wider (possibly deliberate) element back to a face.
IMPORTER_SLIVER_THICKNESS = .5


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def dump(path: Path, value):
    """Stable JSON writer which does not disturb equal generated files."""
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    encoded = text.encode("utf-8")
    if not path.exists() or path.read_bytes() != encoded:
        path.write_bytes(encoded)


def dump_gzip(path: Path, value):
    raw = (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()
    data = gzip.compress(raw, compresslevel=6, mtime=0)
    data = data[:9] + b"\xff" + data[10:]  # gzip OS byte must be cross-platform stable
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_bytes() != data:
        path.write_bytes(data)


def key(values: dict) -> str:
    return ",".join(f"{k}={v}" for k, v in sorted(values.items()))


def parsed(state: str) -> dict:
    return dict(piece.split("=", 1) for piece in state.split(",") if piece)


def flatten_apps(found):
    return [app for group in found for app in group if isinstance(app, dict) and "model" in app]


def semantic_catalog():
    raw = read(ROOT / "docs" / "semantic-catalog-v2.json")
    # Overrides are model-keyed evidence, not legacy block categories.
    return raw.get("blocks", raw), read(ROOT / "docs" / "semantic-model-overrides-v2.json")


def group_auto_sources(records):
    """Group exact auto-model applications, merging proven mount rotations only.

    ``records`` contain ``(semantic, source_id, state, app, evidence)``. A
    merged group requires at least two distinct x rotations from the explicit
    floor/wall/ceiling set; all other rotations retain the legacy grouping.
    """
    buckets = defaultdict(list)
    for semantic, source_id, state, app, evidence in records:
        buckets[(semantic, app["model"], bool(app.get("uvlock", False)))].append((source_id, state, app, evidence))
    grouped = []
    for (semantic, model_name, uvlock), sources in sorted(buckets.items()):
        by_x = defaultdict(list)
        for source in sources:
            by_x[int(source[2].get("x", 0))].append(source)
        mount_x = sorted(set(by_x) & set(MOUNT_FACES))
        if len(mount_x) > 1:
            grouped.append({"semantic": semantic, "model": model_name, "uvlock": uvlock,
                            "mount": True, "sources": [source for x in mount_x for source in by_x[x]]})
        for x, entries in sorted(by_x.items()):
            if x in mount_x and len(mount_x) > 1:
                continue
            grouped.append({"semantic": semantic, "model": model_name, "uvlock": uvlock,
                            "x": x, "mount": False, "sources": entries})
    return grouped


def auto_group_states(group):
    if group["mount"]:
        return [{"face": face, "facing": facing} for face in MOUNT_FACES.values() for facing in FACING]
    return [{"facing": facing} for facing in FACING]


def auto_group_target(app, group):
    target = {"facing": FACING[(int(app.get("y", 0)) // 90) % 4]}
    if group["mount"]:
        target["face"] = MOUNT_FACES[int(app.get("x", 0))]
    return target


def safe_stem(model_name: str) -> str:
    stem = model_name.split(":", 1)[-1].split("/")[-1].lower()
    stem = re.sub(r"[^a-z0-9_]+", "_", stem).strip("_")
    return stem[:46] or "object"


def unique_id(base: str, occupied: set[str], salt: str) -> str:
    candidate = "o_" + base
    if candidate not in occupied:
        occupied.add(candidate)
        return candidate
    candidate += "_" + hashlib.sha256(salt.encode()).hexdigest()[:8]
    occupied.add(candidate)
    return candidate


def source_states(definition: dict):
    for state in definition["states"]:
        yield parsed(state)


def matches(state: dict, subset: dict) -> bool:
    return all(state.get(name) == value for name, value in subset.items())


@functools.lru_cache(maxsize=None)
def original_model(model_name: str):
    """Return the authored source-pack model when it is available.

    This is evidence only; a malformed/missing pack can never alter output.
    """
    if not ORIGINAL_PACK.is_file():
        return ()
    import zipfile
    namespace, local = (model_name.split(":", 1) if ":" in model_name else ("minecraft", model_name))
    choices = [f"assets/{namespace}/models/{local}.json"]
    if namespace == "bloodborne_blocks":
        choices.append(f"assets/minecraft/models/{local}.json")
    try:
        with zipfile.ZipFile(ORIGINAL_PACK) as pack:
            name = next((choice for choice in choices if choice in pack.namelist()), None)
            if name is None:
                return None
            return json.loads(pack.read(name).decode("utf-8-sig"))
    except (OSError, KeyError, ValueError, json.JSONDecodeError):
        return None


def original_elements(model_name: str):
    return tuple((original_model(model_name) or {}).get("elements", []))


def proven_authored_artwork(model_name: str, overrides: dict) -> bool:
    """Require source-pack evidence before automatic logical conversion.

    Vanilla fallback models have no authored entry in the original pack.  A
    reviewed model override is sufficient once that entry exists; otherwise an
    authored element list must differ from the vanilla model of the same name.
    """
    authored = original_model(model_name)
    if not authored:
        return False
    if model_name in overrides:
        return True
    elements = authored.get("elements")
    if not elements:
        return False
    namespace, local = (model_name.split(":", 1) if ":" in model_name else ("minecraft", model_name))
    if namespace != "minecraft":
        return True
    try:
        from catalog_geometry import ZIP
        vanilla = json.loads(ZIP.read(f"assets/minecraft/models/{local}.json").decode("utf-8-sig"))
    except (KeyError, OSError, ValueError, json.JSONDecodeError):
        return True
    return elements != vanilla.get("elements")


def original_element(model_name: str, index: int):
    elements = original_elements(model_name)
    return elements[index] if index < len(elements) else None


def comparable_import_sliver(current: dict, original: dict | None) -> bool:
    if not original:
        return False
    if current.get("rotation") != original.get("rotation"):
        return False
    low, high = current.get("from"), current.get("to")
    old_low, old_high = original.get("from"), original.get("to")
    if not all(isinstance(x, list) and len(x) == 3 for x in (low, high, old_low, old_high)):
        return False
    zero = [i for i in range(3) if abs(float(old_high[i]) - float(old_low[i])) < 1e-8]
    if len(zero) != 1:
        return False
    for axis in range(3):
        if axis in zero:
            if abs(float(high[axis]) - float(low[axis])) > IMPORTER_SLIVER_THICKNESS + 1e-8:
                return False
            if abs((float(low[axis]) + float(high[axis])) / 2 - float(old_low[axis])) > 1e-8:
                return False
        elif abs(float(low[axis]) - float(old_low[axis])) > 1e-8 or abs(float(high[axis]) - float(old_high[axis])) > 1e-8:
            return False
    return True


def authored_polys(app: dict):
    """Use only faces actually authored by the source model (no sealing/caps)."""
    md = model(app["model"])
    # Gate curation uses an explicit, reviewed transform.  Build local authored
    # surfaces first, hinge them, then apply ordinary application yaw/offset.
    local_gate = bool(app.get("open_double"))
    geometry_app = {k: v for k, v in app.items() if k not in {"offset", "open_double", "y"}}
    result, boxes = [], []
    offset = np.array(app.get("offset", (0, 0, 0)), float)
    selected = app.get("elements")
    if selected is not None and (not isinstance(selected, list) or not all(isinstance(value, int) and value >= 0 for value in selected)):
        raise ValueError("app.elements must be a non-negative element-index whitelist")
    for index, current in enumerate(md.get("elements", [])):
        if selected is not None and index not in selected:
            continue
        original = original_element(app["model"], index)
        element = dict(current)
        # Reverse only an importer expansion which has exact geometrical proof.
        if len(md.get("elements", [])) == len(original_elements(app["model"])) and comparable_import_sliver(current, original):
            element["from"] = list(original["from"])
            element["to"] = list(original["to"])
            element["faces"] = {side: face for side, face in current.get("faces", {}).items()
                                if side in original.get("faces", {})}
        points = corners(element, geometry_app)
        planar = any(abs(float(element["to"][a]) - float(element["from"][a])) < 1e-8 for a in range(3))
        def world_points(value, pivot=None, angle=0):
            value = np.asarray(value, float)
            if pivot is not None:
                value = (value - pivot) @ rotation(1, angle).T + pivot
            yaw = int(app.get("y", 0))
            if yaw:
                value = (value - .5) @ rotation(1, -yaw).T + .5
            return value + offset
        if local_gate:
            # The two halves have independent hinges; use their transformed
            # element AABBs for collision instead of one giant closed-gate box.
            for high_side, pivot, angle in ((False, np.array([-1., 0., .5]), -90), (True, np.array([2., 0., .5]), 90)):
                low, high = points.min(axis=0), points.max(axis=0)
                if high_side: low[0] = max(low[0], .5)
                else: high[0] = min(high[0], .5)
                if low[0] <= high[0] + 1e-8:
                    cuboid = np.array(list(itertools.product(*zip(low, high))))
                    moved = world_points(cuboid, pivot, angle)
                    boxes.append((moved.min(axis=0), moved.max(axis=0), planar))
        else:
            moved = world_points(points)
            boxes.append((moved.min(axis=0), moved.max(axis=0), planar))
        for side, face in element.get("faces", {}).items():
            texture = texname(md, face["texture"])
            result.append(polygon(points[FACE_INDEX[side]], face_uv(face, side, element, app), texture))
    if local_gate:
        opened = []
        for poly in result:
            for piece, pivot, angle in ((clip(poly, 0, .5, False)[0], np.array([-1., 0., .5]), -90),
                                        (clip(poly, 0, .5, True)[0], np.array([2., 0., .5]), 90)):
                if piece is None:
                    continue
                vertices = np.asarray(piece["vertices"], float)
                vertices[:, :3] = (vertices[:, :3] - pivot) @ rotation(1, angle).T + pivot
                opened.append({**piece, "vertices": vertices.tolist()})
        result = opened
    # Variant yaw is a world transform around the block centre; it happens only
    # after the reviewed local gate hinges above.
    yaw = int(app.get("y", 0))
    if yaw:
        turn = rotation(1, -yaw)
        for poly in result:
            vertices = np.asarray(poly["vertices"], float)
            vertices[:, :3] = (vertices[:, :3] - .5) @ turn.T + .5
            poly["vertices"] = vertices.tolist()
    for poly in result:
        vertices = np.asarray(poly["vertices"], float)
        vertices[:, :3] += offset
        poly["vertices"] = vertices.round(7).tolist()
    return result, boxes


def nondegenerate(poly):
    vertices = np.asarray(poly["vertices"], dtype=float)
    return len(vertices) >= 3 and any(np.linalg.norm(np.cross(vertices[i, :3] - vertices[0, :3], vertices[i + 1, :3] - vertices[0, :3])) > 1e-9
                                        for i in range(1, len(vertices) - 1))


def object_polys(apps: list[dict]):
    polygons, boxes = [], []
    for app in apps:
        part, part_boxes = authored_polys(app)
        polygons.extend(tile for poly in part if nondegenerate(poly) for tile in tile_uv(poly) if nondegenerate(tile))
        boxes.extend(part_boxes)
    return polygons, boxes


def mesh_id(ident: str, state: str, polygons: list[dict]) -> str:
    serial = json.dumps(polygons, sort_keys=True, separators=(",", ":"))
    return "o_" + hashlib.sha256((ident + "\0" + state + "\0" + serial).encode()).hexdigest()[:20]


def bounds_box(low, high):
    low, high = np.asarray(low, float), np.asarray(high, float)
    for axis in range(3):
        if high[axis] - low[axis] < 1 / 64:
            centre = (low[axis] + high[axis]) / 2
            low[axis], high[axis] = max(0.0, centre - 1 / 128), min(1.0, centre + 1 / 128)
            if high[axis] - low[axis] < 1 / 128:
                low[axis], high[axis] = (0.0, 1 / 64) if centre <= .5 else (1 - 1 / 64, 1.0)
    return np.r_[low, high]


def floor_owned_boxes(boxes, semantic):
    """Clip only a small authored floor-contact lip from ownership geometry."""
    if semantic not in {"container", "ornament"} or not boxes:
        return boxes
    minimum = min(float(low[1]) for low, _high, _planar in boxes)
    maximum = max(float(high[1]) for _low, high, _planar in boxes)
    if not (-1 / 8 <= minimum < 0 < maximum):
        return boxes
    result = []
    for low, high, planar in boxes:
        if high[1] <= 0:
            continue
        clipped_low = np.array(low, dtype=float, copy=True)
        clipped_low[1] = max(clipped_low[1], 0)
        result.append((clipped_low, high, planar))
    return result


def geometry_for(polygons, boxes, semantic):
    """Derive ownership from element volumes, not render-face boundaries."""
    cells = defaultdict(lambda: {"outline": [], "collision": []})
    soft = semantic in {"bush", "plant"}
    visible_face_cells = set()
    for poly in polygons:
        # Faces are visual only: a face exactly on x=1 belongs to the rendered
        # neighbour cell after clipping, but must not claim that cell's block
        # volume.  Keep visibility evidence solely for planar alpha artwork.
        for cell, clipped in split_element([poly], closed=False).items():
            if any(np.any(alpha_patch(face) > 0) for face in clipped):
                visible_face_cells.add(cell)
    # Visibility indices intentionally come from the original rendered faces.
    # The optional floor trim is ownership-only and must not move the mesh.
    boxes = floor_owned_boxes(boxes, semantic)
    for low, high, planar in boxes:
        lo = np.floor(low + 1e-8).astype(int)
        hi = np.ceil(high - 1e-8).astype(int)
        hi = np.maximum(hi, lo + 1)
        for cell in itertools.product(*(range(lo[a], hi[a]) for a in range(3))):
            # A transparent part of a planar sprite has no outline footprint.
            # Non-planar boxes keep every AABB cell, including rotated solids.
            if planar and cell not in visible_face_cells:
                continue
            clipped_low, clipped_high = np.maximum(low, cell), np.minimum(high, np.array(cell) + 1)
            if np.any(clipped_high < clipped_low - 1e-8):
                continue
            box = bounds_box(clipped_low - cell, clipped_high - cell).round(6).tolist()
            entry = cells[",".join(map(str, cell))]
            entry["outline"].append(box)
            # Planar decorative artwork and tree foliage must never turn into a
            # full invisible wall.  A tree's authored non-planar stone base is
            # still solid, unlike soft bushes/plants.
            if not soft and not (planar and semantic in {"ornament", "tree"}):
                entry["collision"].append(box)
    output = {}
    for cell, item in cells.items():
        output[cell] = {name: sorted({tuple(value) for value in values}) for name, values in item.items()}
        output[cell] = {name: [list(value) for value in values] for name, values in output[cell].items()}
    positions = [tuple(map(int, cell.split(","))) for cell in output]
    anchor = tuple(min(position[axis] for position in positions) for axis in range(3)) if positions else (0, 0, 0)
    return {"cells": output, "anchor": list(anchor), "render_offset": [0, 0, 0]}


def layer(polygons):
    if any(np.any((a := alpha_patch(poly)) > 0) and np.any((a > 0) & (a < 250)) for poly in polygons):
        return "translucent"
    if any(np.any(alpha_patch(poly) < 250) for poly in polygons):
        return "cutout"
    return "solid"


def apps_for_state(blockstate: dict, state: dict):
    return flatten_apps(applications(blockstate, state))


def v2_components(v2, source_id, state):
    record = v2.get(source_id, {})
    value = record.get("states", {}).get(key(state))
    if not isinstance(value, list) or not value:
        return None
    answer = []
    for part in value:
        if not isinstance(part, dict) or "id" not in part or "offset" not in part:
            return None
        answer.append({"id": part["id"], "properties": part.get("properties", {}), "offset": part["offset"]})
    return answer or None


def source_rule(source_id, source_state, target_id, target_state, members, v2):
    rule = {"source": {"id": source_id, "properties": source_state},
            "target": {"id": target_id, "properties": target_state}, "offset": [0, 0, 0]}
    if members:
        rule["members"] = members
    all_components = [v2_components(v2, source_id, source_state)]
    for member in members:
        all_components.append(v2_components(v2, member["id"], member["properties"]))
    rule["components"] = all_components[0] if len(all_components) == 1 else None
    # A multi-member inverse is useful only if every exact expansion is proven;
    # v2 parts cannot share locations after translating member offsets.
    if len(all_components) > 1 and all(all_components):
        combined = []
        for member, components in zip([{"offset": [0, 0, 0]}] + members, all_components):
            for component in components:
                offset = [component["offset"][i] + member["offset"][i] for i in range(3)]
                combined.append({**component, "offset": offset})
        if len({tuple(item["offset"]) for item in combined}) == len(combined):
            rule["components"] = combined
    return rule


def make_definition(ident, semantic, behavior, properties, models, default, polygons_by_mesh, luminance=None, connection_family=None):
    states = {state: [0, 0, int((luminance or {}).get(state, 0))] for state in models}
    return {"id": ident, "source": "minecraft:stone", "layer": layer(next(iter(polygons_by_mesh.values()), [])),
            "kind": "generic", "offset": "none", "hardness": 1.5, "resistance": 6, "slipperiness": .6,
            "velocity": 1, "jump": 1, "extra_facing": "facing" in properties, "custom_geometry": True, "full_cube": False,
            "emissive": False, "animated": False, "orphan": False, "creative": True, "logical": True, "behavior": behavior,
            "semantic": semantic, "properties": properties, "default": default, "states": states, "models": models,
            **({"connection_family": connection_family} if connection_family else {})}


def curated_objects(legacy, v2):
    catalog, _overrides = semantic_catalog()
    curation = read(ROOT / "docs" / "logical-families-v3.json")["objects"]
    blocks = {block["id"]: block for block in legacy["blocks"]}
    result, meshes, geometry, rules, names, covered = [], {}, {}, [], {}, set()
    for item in curation:
        state_rows = item["states"]
        properties = item.get("properties") or {name: sorted({row["properties"][name] for row in state_rows})
                                                   for name in sorted({name for row in state_rows for name in row["properties"]})}
        models, polygons, luminance = {}, {}, defaultdict(int)
        for row in state_rows:
            state = key(row["properties"])
            polys, boxes = object_polys(row["apps"])
            mesh = mesh_id(item["id"], state, polys)
            meshes[mesh] = {"polygons": polys}; polygons[mesh] = polys; models[state] = mesh
            geometry.setdefault(item["id"], {"states": {}})["states"][state] = geometry_for(polys, boxes, item["semantic"])
            for source in row.get("sources", []):
                block = blocks.get(source["id"])
                if not block:
                    continue
                for source_state in source_states(block):
                    if source_state.get("waterlogged") == "true" or not matches(source_state, source["match"]):
                        continue
                    members = []
                    complete = True
                    for member in source.get("members", []):
                        member_block = blocks.get(member["id"])
                        shared = {name: value for name, value in source_state.items()
                                  if name in (member_block or {}).get("properties", {}) and name not in member["match"]}
                        expected = {**shared, **member["match"]}
                        exact = [candidate for candidate in source_states(member_block or {}) if matches(candidate, expected)]
                        if len(exact) != 1:
                            complete = False; break
                        members.append({"id": member["id"], "properties": exact[0], "offset": member.get("offset", [0, 0, 0])})
                    if complete:
                        rules.append(source_rule(source["id"], source_state, item["id"], row["properties"], members, v2))
                        covered.add((source["id"], key(source_state)))
                        luminance[state] = max(luminance[state], int(block["states"].get(key(source_state), [0, 0, 0])[2]))
        result.append(make_definition(item["id"], item["semantic"], item["behavior"], properties, models,
                                      state_rows[0]["properties"], polygons, luminance, item.get("connection_family")))
        names[item["id"]] = item.get("name", item["id"])
    return result, meshes, geometry, rules, names, covered


def auto_objects(legacy, v2, occupied, covered):
    catalog, overrides = semantic_catalog()
    records, blocks = [], {block["id"]: block for block in legacy["blocks"]}
    curated_ids = {source for source, _state in covered}
    for block in legacy["blocks"]:
        ident = block["id"]
        state_asset = ASSETS / "blockstates" / f"{ident}.json"
        if ident in curated_ids or ident in TREE_PIECES or not state_asset.exists():
            continue
        blockstate = read(state_asset)
        for state in source_states(block):
            if state.get("assembled") != "false" or state.get("waterlogged") == "true" or (ident, key(state)) in covered:
                continue
            found = apps_for_state(blockstate, state)
            if len(found) != 1:
                continue
            app = found[0]
            source_model = app["model"].lower()
            evidence = overrides.get(app["model"], catalog.get(ident, {}))
            semantic = evidence.get("category", "")
            if semantic not in AUTO_CATEGORIES:
                continue
            if not proven_authored_artwork(app["model"], overrides):
                continue
            name_for_filter = source_model
            if any(word in name_for_filter or word in ident.lower() for word in EXCLUDED_WORDS):
                continue
            records.append((semantic, ident, state, app, evidence))
    result, meshes, geometry, rules, names = [], {}, {}, [], {}
    for group in group_auto_sources(records):
        semantic, model_name, uvlock, sources = group["semantic"], group["model"], group["uvlock"], group["sources"]
        signature = (model_name, 0, uvlock) if group["mount"] else (model_name, group["x"], uvlock)
        ident = unique_id(safe_stem(model_name), occupied, json.dumps(signature))
        # Each mesh is pre-rotated.  Runtime blockstate variants never rotate it.
        models, polygons, luminance = {}, {}, defaultdict(int)
        for state_values in auto_group_states(group):
            face = state_values.get("face")
            x = next((rotation for rotation, name in MOUNT_FACES.items() if name == face), group.get("x", 0))
            index = FACING.index(state_values["facing"])
            app = {"model": model_name, "x": x, "uvlock": uvlock, "y": index * 90}
            polys, boxes = object_polys([app])
            state = key(state_values)
            mesh = mesh_id(ident, state, polys)
            meshes[mesh] = {"polygons": polys}; polygons[mesh] = polys; models[state] = mesh
            geometry.setdefault(ident, {"states": {}})["states"][state] = geometry_for(polys, boxes, semantic)
        behavior = "ladder" if semantic == "ladder" else "lantern" if semantic == "lamp" else "static"
        properties = {"facing": list(FACING), **({"face": list(MOUNT_FACES.values())} if group["mount"] else {})}
        default = {"facing": "north", **({"face": "floor"} if group["mount"] else {})}
        result.append(make_definition(ident, semantic, behavior, properties, models, default, polygons))
        names[ident] = next((record.get("ru") for *_x, record in sources if record.get("ru")), ident)
        for source_id, state, app, _record in sources:
            # The visual orientation follows the original application rather than
            # trusting mislabeled source-facing data.
            target = auto_group_target(app, group)
            rules.append(source_rule(source_id, state, ident, target, [], v2))
            luminance[key(target)] = max(luminance[key(target)], int(blocks[source_id]["states"].get(key(state), [0, 0, 0])[2]))
        # The source ID lookup is intentionally after the full group is known;
        # every rule retains its exact source state.
        result[-1] = make_definition(ident, semantic, behavior, properties, models, default, polygons, luminance)
    return result, meshes, geometry, rules, names


def write_assets(blocks, meshes, names):
    expected_ids = {block["id"] for block in blocks}
    expected_meshes = {mesh for block in blocks for mesh in block["models"].values()}
    removed = {"blockstates": 0, "items": 0, "models": 0, "loot": 0, "lang": 0}
    for block in blocks:
        ident = block["id"]
        variants = {state: {"model": "bloodborne_blocks:block/logical/" + mesh}
                    for state, mesh in block["models"].items()}
        dump(ASSETS / "blockstates" / f"{ident}.json", {"variants": variants})
        for mesh in set(block["models"].values()):
            textures = list(dict.fromkeys(poly["texture"] for poly in meshes[mesh]["polygons"]))
            dump(ASSETS / "models/block/logical" / f"{mesh}.json",
                 {"parent": "minecraft:block/block", "textures": {"particle": textures[0] if textures else "minecraft:block/stone",
                                                                    **{str(i): texture for i, texture in enumerate(textures)}}, "elements": []})
        default_mesh = block["models"][key(block["default"])]
        item = {"parent": "bloodborne_blocks:block/logical/" + default_mesh,
                "display": {"gui": {"scale": [1, 1, 1], "translation": [0, 0, 0]}}}
        vertices = np.asarray([vertex[:3] for poly in meshes[default_mesh]["polygons"] for vertex in poly["vertices"]], float)
        if len(vertices):
            span = float((vertices.max(axis=0) - vertices.min(axis=0)).max())
            if span > 1.5:
                scale = round(min(1.0, 1.35 / span), 5)
                item["display"] = {"gui": {"scale": [scale, scale, scale], "translation": [0, 0, 0]}}
        dump(ASSETS / "models/item" / f"{ident}.json", item)
        dump(RES / "data/bloodborne_blocks/loot_tables/blocks" / f"{ident}.json",
             {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bloodborne_blocks:" + ident}],
                                                        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for language in ("ru_ru", "en_us"):
        path = ASSETS / "lang" / f"{language}.json"
        data = read(path)
        for entry in [entry for entry in data if entry.startswith("block.bloodborne_blocks.o_") and entry.rsplit(".", 1)[-1] not in expected_ids]:
            del data[entry]
            removed["lang"] += 1
        for ident, name in names.items():
            data["block.bloodborne_blocks." + ident] = name if language == "ru_ru" else "Bloodborne " + ident[2:].replace("_", " ").title()
        dump(path, data)
    # These four exact directories contain only this generator's o_* output.
    # Do not glob legacy/v2 directories and never touch m_* resources.
    for folder, allowed, label in (
        (ASSETS / "blockstates", expected_ids, "blockstates"),
        (ASSETS / "models/item", expected_ids, "items"),
        (ASSETS / "models/block/logical", expected_meshes, "models"),
        (RES / "data/bloodborne_blocks/loot_tables/blocks", expected_ids, "loot"),
    ):
        if not folder.exists():
            continue
        for path in folder.glob("o_*.json"):
            if path.stem not in allowed:
                path.unlink()
                removed[label] += 1
    return removed


def safe_hidden_items(legacy, rules):
    """Hide only fully normalised dry source IDs; legacy IDs remain readable."""
    mapped = {(rule["source"]["id"], key(rule["source"]["properties"])) for rule in rules}
    hidden = set()
    for block in legacy["blocks"]:
        dry = [state for state in source_states(block)
               if state.get("assembled") != "true" and state.get("waterlogged") != "true"]
        if dry and all((block["id"], key(state)) in mapped for state in dry):
            hidden.add(block["id"])
    # A v2 module can disappear from creative inventory only when every stated
    # provenance source is one of the fully normalised legacy IDs.  Composite
    # modules deliberately stay visible because their inverse is not a proof.
    sources = read(RES / "bloodborne_blocks/v2/sources.json")
    for module, provenance in sources.items():
        if provenance and "world_composite" not in provenance and all(source in hidden for source in provenance):
            hidden.add(module)
    return sorted(hidden)


def build():
    # Absence of source evidence must never silently shrink the checked-in
    # palette and delete its generated assets. Gradle uses committed output;
    # regeneration explicitly requires the original pack.
    import zipfile
    if not ORIGINAL_PACK.is_file() or not zipfile.is_zipfile(ORIGINAL_PACK):
        raise ValueError("A valid original resource pack is required; use --original-pack PATH. No output was changed.")
    legacy = read(RES / "bloodborne_blocks/definitions.json")
    v2 = read(RES / "bloodborne_blocks/v2/migration.json")
    occupied = {block["id"] for block in legacy["blocks"]} | {block["id"] for block in read(RES / "bloodborne_blocks/v2/definitions.json")["blocks"]}
    curated, meshes, geometry, rules, names, covered = curated_objects(legacy, v2)
    occupied.update(block["id"] for block in curated)
    auto, auto_meshes, auto_geometry, auto_rules, auto_names = auto_objects(legacy, v2, occupied, covered)
    blocks = curated + auto; meshes.update(auto_meshes); geometry.update(auto_geometry); rules.extend(auto_rules); names.update(auto_names)
    # Profile dedup keeps large generated resources bounded while preserving state data.
    profiles = {}
    for block in geometry.values():
        for state, profile in list(block["states"].items()):
            token = "g_" + hashlib.sha256(json.dumps(profile, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:20]
            profiles.setdefault(token, profile)
            block["states"][state] = {"ref": token}
    dump(LOGICAL / "definitions.json", {"blocks": blocks})
    dump(LOGICAL / "geometry.json", {"profiles": profiles, "blocks": geometry})
    dump_gzip(LOGICAL / "meshes.json.gz", meshes)
    dump(LOGICAL / "migration.json", {"schemaVersion": 1, "rules": rules})
    hidden = safe_hidden_items(legacy, rules)
    dump(LOGICAL / "hidden-items.json", hidden)
    removed = write_assets(blocks, meshes, names)
    report = {"schemaVersion": 1, "logicalObjects": len(blocks), "states": sum(len(block["states"]) for block in blocks),
              "meshes": len(meshes), "migrationRules": len(rules), "curatedObjects": len(curated), "autoObjects": len(auto),
              "hiddenLegacyItems": len([ident for ident in hidden if not ident.startswith("m_")]),
              "hiddenV2Items": len([ident for ident in hidden if ident.startswith("m_")]), "staleAssetsRemoved": removed}
    dump(ROOT / "docs/logical-generation-v3.json", report)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original-pack", type=Path, default=ORIGINAL_PACK)
    args = parser.parse_args()
    ORIGINAL_PACK = args.original_pack.resolve()
    print(json.dumps(build(), ensure_ascii=False, sort_keys=True))
