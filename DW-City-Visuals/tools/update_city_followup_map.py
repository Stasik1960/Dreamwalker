"""Prepare a new Bloodborne world with a bounded water layer and verified grass pairs."""
from collections import Counter
from functools import lru_cache
from itertools import product
import argparse
import json
from hashlib import sha256
import os
from pathlib import Path
import shutil
import stat
import struct
from zipfile import ZipFile

from map_io.world_io import (
    RegionFile, Tag, TAG_COMPOUND, TAG_INT, TAG_INT_ARRAY, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING,
    block_state_key, compound, invalidate_chunk_lighting, pack_palette_indices, section_blocks,
)


AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
WATER = "bloodborne_dw:dw_fake_water"
VISUALS = "DreamwalkerVisuals"
FAUX = "DreamwalkerFauxWater"
FORCED = "ForcedWater"
WATER_Y = -32
SECTION_Y = WATER_Y // 16


def state(name, **properties):
    values = {"Name": Tag(TAG_STRING, name)}
    if properties:
        values["Properties"] = Tag(TAG_COMPOUND, {k: Tag(TAG_STRING, v) for k, v in properties.items()})
    return Tag(TAG_COMPOUND, values)


def static_state(index):
    return state(f"bloodborne_dw:static_architecture_{index // 128}",
                 part=str(index % 128), waterlogged="false")


def recipe_index(name, properties):
    return int(name.rsplit("_", 1)[1]) * 128 + int(properties["part"].value)


def copy_world(source, target):
    def ignore(folder, names):
        for name in names:
            if (Path(folder) / name).lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
                raise ValueError(f"World contains a reparse point: {name}")
        return {"session.lock"} & set(names)

    if source.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
        raise ValueError("World root is a reparse point")
    shutil.copytree(source, target, ignore=ignore)


def cross_meshes(catalog, raw):
    """Recognize the two centered catalogue bushes by their four crossed faces."""
    result = {}
    offset = 8
    for mesh, entry in enumerate(catalog["meshes"]):
        quads = entry["quadCount"]
        offset += 4
        if quads == 4:
            faces = Counter()
            v_values = []
            for index in range(quads):
                at = offset + index * 139
                material = catalog["materials"][struct.unpack_from(">I", raw, at)[0]]
                if (material["layer"] != "cutout" or
                        not material["sprite"].endswith("/addon/spirelamp_0046")):
                    break
                vertices = [struct.unpack_from(">5f", raw, at + 11 + 32*v) for v in range(4)]
                if any(not (14 <= vertex[3] <= 16 and 13.375 <= vertex[4] <= 16)
                       for vertex in vertices):
                    break
                v_values.extend(vertex[4] for vertex in vertices)
                faces[tuple(round(fn(v[a] for v in vertices) * 32)
                            for fn in (min, max) for a in range(3))] += 1
            else:
                for variant, (start, end) in enumerate(((0, 32), (7, 25))):
                    for top in (10, 32):
                        shape = Counter({(start, 0, 16, end, top, 16): 2,
                                         (16, 0, start, 16, top, end): 2})
                        if faces == shape and (min(v_values) < 13.5) == (top == 10):
                            result[mesh] = variant, top == 10
        offset += quads * 139
    if offset != len(raw) or not result:
        raise ValueError("Unexpected mesh catalogue")
    return result


def single_grass_recipes(static):
    result = {}
    for variant in range(2):
        pose, = [p for p in static["poses"]
                 if p["registryId"] == f"bloodborne_dw:source_addon_grass_{variant}"
                 and p["shift"] == [0, 0, 0] and p["mountY"] == 0
                 and p["properties"]["rotation"] == "0"]
        for upper, origin in ((False, [0, 0, 0]), (True, [0, -1, 0])):
            index, = [i for i, r in enumerate(static["recipes"])
                      if r["resident"] == (-1 if upper else 0)
                      and r["fragments"] == [{"pose": pose["key"], "origin": origin, "member": True}]]
            result[variant, upper] = static_state(index)
    return result


def section_data(section):
    blocks = section_blocks(section)
    return blocks if blocks else ([state("minecraft:air")], [0]*4096)


def write_section(section, palette, indices):
    unique = sorted(set(indices))
    remap = {old: new for new, old in enumerate(unique)}
    block_data = {"palette": Tag(TAG_LIST, [palette[i] for i in unique], TAG_COMPOUND)}
    if len(unique) > 1:
        block_data["data"] = Tag(TAG_LONG_ARRAY, pack_palette_indices(
            [remap[i] for i in indices], len(unique)))
    compound(section)["block_states"] = Tag(TAG_COMPOUND, block_data)


def packed_pos(x, y, z):
    value = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 4095)
    return value if value < 2**63 else value - 2**64


def grass_pairs(rows, cells, cross):
    matches = {}
    for sy, entries in rows.items():
        for local, visual in entries.items():
            kind = cross.get(cells[visual]["mesh"])
            if kind is not None:
                matches[(sy, local)] = kind
    for (sy, index), (variant, upper) in matches.items():
        if upper:
            continue
        other = (sy, index + 256) if index < 3840 else (sy + 1, index & 255)
        if matches.get(other) == (variant, True):
            yield (sy, index), other, variant


def patch_world(target, baseline):
    counts = Counter()
    unsupported = Counter()
    with ZipFile(baseline) as jar:
        static = json.loads(jar.read("bloodborne_dw/static-architecture-v1274.json"))
        raw_catalog = jar.read("assets/bloodborne_dw/native_grid/catalog.json")
        native = json.loads(raw_catalog)
        catalog_hash = sha256(raw_catalog).hexdigest()
        crosses = cross_meshes(native, jar.read("assets/bloodborne_dw/native_grid/meshes.bin"))
        fallback = json.loads(jar.read("bloodborne_dw/source-fallback-carriers-v1271.json"))
        specs = {Path(name).stem: json.loads(jar.read(name))
                 for name in jar.namelist()
                 if name.startswith("bloodborne_dw/composite/") and name.endswith(".json")}
    replacement = single_grass_recipes(static)

    def full_shape(boxes, limit):
        if any(all(b[i] <= 0 and b[i+3] >= limit for i in range(3)) for b in boxes):
            return True
        edges = [sorted({0, limit, *(v for b in boxes for v in (b[i], b[i+3]) if 0 < v < limit)})
                 for i in range(3)]
        probes = [[(a+b)/2 for a, b in zip(axis, axis[1:])] for axis in edges]
        return all(any(all(box[i] <= point[i] <= box[i+3] for i in range(3)) for box in boxes)
                   for point in product(*probes))

    @lru_cache(maxsize=None)
    def full_recipe(index):
        return full_shape(static["recipes"][index]["collision"], 1)

    @lru_cache(maxsize=None)
    def partial_native(index):
        cell = native["cells"][index]
        return not cell.get("fluid") and not full_shape(native["shapes"][cell["collision"]], 1)

    @lru_cache(maxsize=None)
    def partial_source(name, encoded_properties):
        path = name.split(":", 1)[1]
        props = dict(encoded_properties)
        if props.get("waterlogged") == "true":
            return False
        if path == "source_fallback_carrier":
            index = int(props["source"]) + fallback["sourceBankSize"]*int(props.get("source_bank", "0"))
            physical = fallback["physicalOverrides"][index]
            if physical is not None:
                return not full_shape([box["from"] + box["to"] for box in physical["collision"]],
                                      physical["units"])
            return None
        if path == "source_fallback_translucent_carrier":
            index = int(props["source"]) + fallback["sourceBankSize"]*int(props.get("source_bank", "0"))
            source = fallback["sourceStates"][index]
            return not source.split("[", 1)[0].endswith("_glass")
        if path.startswith("restored_slab_"):
            return props.get("type") in {"top", "bottom"}
        spec = specs.get(path)
        if not spec or not spec.get("variants"):
            return None
        variant = int(props.get("variant", "0")) + 16*int(props.get("art_bank", "0"))
        if props.get("trim") == "true":
            variant = variant % 10 + 10
        if not 0 <= variant < len(spec["variants"]):
            return None
        value = spec["variants"][variant]
        poses = value.get("poses", {})
        pose = poses.get("open" if props.get("open") == "true" else "closed")
        if not pose:
            return None
        shapes = pose.get("simpleCollisionByRotation")
        boxes = shapes[int(props.get("rotation", "0")) % len(shapes)] if shapes else pose.get("collision", [])
        limit = spec.get("units", 16)
        return not full_shape([box["from"] + box["to"] for box in boxes], limit)

    for path in sorted((target/"region").glob("r.*.*.mca")):
        region = RegionFile(path.read_bytes())
        changed = False
        for chunk in region.chunks():
            nbt = chunk.nbt()
            root = compound(nbt.root)
            if "sections" not in root or root.get("Status", Tag(TAG_STRING, "")).value != "minecraft:full":
                continue
            cx, cz = root["xPos"].value, root["zPos"].value
            sections = {compound(t)["Y"].value: t for t in root["sections"].value}
            staged = {}

            def edit(sy, index, value):
                if sy not in staged:
                    palette, indices = section_data(sections[sy])
                    staged[sy] = list(palette), indices, {block_state_key(v): i for i, v in enumerate(palette)}
                palette, indices, lookup = staged[sy]
                key = block_state_key(value)
                if key not in lookup:
                    lookup[key] = len(palette)
                    palette.append(value)
                indices[index] = lookup[key]

            visual = compound(root.get(VISUALS, Tag(TAG_COMPOUND, {})))
            rows = {}
            row_tags = {}
            for row in visual.get("Sections", Tag(TAG_LIST, [])).value:
                fields = compound(row)
                sy = fields["Y"].value
                rows[sy] = {entry & 4095: (entry & 0xffffffff) >> 12 for entry in fields["Entries"].value}
                row_tags[sy] = row
            old_forced = set(visual.get(FORCED, Tag(TAG_LONG_ARRAY, [])).value)
            forced = set(old_forced)
            supported = set()
            for sy, section in sections.items():
                if sy >= SECTION_Y:
                    continue
                palette, indices = section_data(section)
                solid = [compound(v)["Name"].value not in AIR for v in palette]
                if all(solid):
                    supported = set(range(256))
                    break
                if any(solid):
                    supported.update(i & 255 for i, entry in enumerate(indices) if solid[entry])

            if supported and SECTION_Y in sections:
                palette, indices = section_data(sections[SECTION_Y])
                faux = compound(root.get(FAUX, Tag(TAG_COMPOUND, {})))
                positions = set(faux.get("Cells", Tag(TAG_LONG_ARRAY, [])).value)
                for index in sorted(supported):
                    value = compound(palette[indices[index]])
                    name = value["Name"].value
                    properties = compound(value.get("Properties", Tag(TAG_COMPOUND, {})))
                    if name in AIR:
                        edit(SECTION_Y, index, state(WATER))
                        counts["air_to_faux_water"] += 1
                    elif name.startswith("bloodborne_dw:static_architecture_"):
                        if properties["waterlogged"].value == "false" and not full_recipe(recipe_index(name, properties)):
                            values = {k: v.value for k, v in properties.items()}
                            edit(SECTION_Y, index, state(name, **(values | {"waterlogged": "true"})))
                            positions.add(packed_pos(cx*16+(index & 15), WATER_Y, cz*16+(index >> 4)))
                            counts["waterlogged_static_parts"] += 1
                    elif name.startswith("bloodborne_dw:") and name != WATER:
                        if name.startswith("bloodborne_dw:native_physics_"):
                            partial = partial_native(rows[SECTION_Y][index])
                        elif name.startswith("bloodborne_dw:native_grid_"):
                            partial = partial_native(recipe_index(name, properties))
                        else:
                            partial = partial_source(name, tuple(sorted((k, v.value) for k, v in properties.items())))
                        if partial:
                            pos = packed_pos(cx*16+(index & 15), WATER_Y, cz*16+(index >> 4))
                            if pos not in forced:
                                forced.add(pos)
                                counts["internal_faux_water"] += 1
                        elif partial is None:
                            unsupported[block_state_key(palette[indices[index]])] += 1
                if positions:
                    root[FAUX] = Tag(TAG_COMPOUND, {"Schema": Tag(TAG_INT, 1),
                                                    "Cells": Tag(TAG_LONG_ARRAY, sorted(positions))})
            elif supported:
                counts["supported_columns_with_missing_section"] += len(supported)

            if forced != old_forced:
                if visual.get("Catalog", Tag(TAG_STRING, catalog_hash)).value != catalog_hash:
                    raise ValueError("Native visuals do not match the pinned catalogue")
                visual.setdefault("Catalog", Tag(TAG_STRING, catalog_hash))
                visual.setdefault("Schema", Tag(TAG_INT, 1))
                visual.setdefault("Sections", Tag(TAG_LIST, [], TAG_COMPOUND))
                visual[FORCED] = Tag(TAG_LONG_ARRAY, sorted(forced))
                root[VISUALS] = Tag(TAG_COMPOUND, visual)

            pairs = list(grass_pairs(rows, native["cells"], crosses))
            if pairs:
                entities = {(be["y"].value // 16,
                             ((be["y"].value & 15) << 8) | ((be["z"].value & 15) << 4) | (be["x"].value & 15))
                            for be in (compound(tag) for tag in root.get("block_entities", Tag(TAG_LIST, [])).value)}
                dirty_visual_rows = set()
                for low, high, variant in pairs:
                    if low in entities or high in entities or low[0] == SECTION_Y or high[0] == SECTION_Y:
                        continue
                    for key, upper in ((low, False), (high, True)):
                        sy, index = key
                        edit(sy, index, replacement[variant, upper])
                        del rows[sy][index]
                        dirty_visual_rows.add(sy)
                    counts[f"grass_9014{4+variant}"] += 1
                for sy in dirty_visual_rows:
                    compound(row_tags[sy])["Entries"] = Tag(TAG_INT_ARRAY, [
                        (v << 12) | i for i, v in sorted(rows[sy].items())])
                if dirty_visual_rows:
                    visual["Sections"].value = [row_tags[sy] for sy, values in rows.items() if values]
                    if not visual["Sections"].value and not forced:
                        del root[VISUALS]

            if not staged and forced == old_forced:
                continue
            for sy, (palette, indices, _) in staged.items():
                write_section(sections[sy], palette, indices)
            if staged:
                root.pop("Heightmaps", None)
                invalidate_chunk_lighting(root)
            region.set_chunk(chunk.x, chunk.z, nbt, timestamp=chunk.timestamp)
            assert region.get_chunk(chunk.x, chunk.z).nbt() == nbt
            counts["modified_chunks"] += 1
            changed = True

        if changed:
            temporary = path.with_suffix(".mca.tmp")
            region.save(temporary)
            os.replace(temporary, path)
            counts["modified_regions"] += 1
        print(path.name, dict(counts), flush=True)

    return {"counts": counts, "unclassified_occupied_layer_states": unsupported,
            "grass_meshes": crosses}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--baseline", type=Path, default=Path(__file__).resolve().parents[1]/"baseline/mod.jar")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    copy_world(args.source, args.target)
    result = patch_world(args.target, args.baseline)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["counts"]), flush=True)


if __name__ == "__main__":
    main()
