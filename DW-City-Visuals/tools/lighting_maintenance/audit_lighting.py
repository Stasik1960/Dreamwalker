"""Audit Minecraft 1.20.1 saved skylight and block light without editing the map."""
from argparse import ArgumentParser
from collections import Counter, defaultdict
import json
from pathlib import Path
import sys

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "map_io"))
from world_io import RegionFile, block_state_key, compound, _dimension_for_region, palette_bits, section_blocks

ZERO = np.zeros((16, 16, 16), dtype=np.uint8)
MAX_SKY = np.full((16, 16, 16), 15, dtype=np.uint8)
FULL = np.ones((16, 16, 16), dtype=bool)
EMPTY = ~FULL
AIR_IDS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
AXES = (1, 0, 2)  # numpy coordinates are Y, Z, X. Each adjacent pair is visited once.


def nibble(data):
    raw = np.frombuffer(data.value, np.uint8)
    if raw.size != 2048:
        raise ValueError("Expected a 2048-byte light array")
    output = np.empty(4096, dtype=np.uint8)
    output[::2] = raw & 15
    output[1::2] = raw >> 4
    return output.reshape((16, 16, 16))


def air_mask(section):
    states = compound(section).get("block_states")
    if states is None:
        return FULL
    states = compound(states)
    palette = states["palette"].value
    mask = np.array([block_state_key(entry) in AIR_IDS for entry in palette])
    if mask.all():
        return FULL
    if not mask.any():
        return EMPTY
    packed = np.asarray(states["data"].value, dtype=np.int64).view(np.uint64)
    bits = palette_bits(len(palette))
    shifts = np.arange(64//bits, dtype=np.uint64) * bits
    data = ((packed[:,None] >> shifts[None,:]) & ((1 << bits) - 1)).ravel()[:4096]
    return mask[data.reshape((16, 16, 16))]


def loc(x, y, z, index):
    dy, dz, dx = (int(v) for v in index)
    return x * 16 + dx, y * 16 + dy, z * 16 + dz


def edge(array, axis, side):
    idx = [slice(None)] * 3
    idx[axis] = side
    return array[tuple(idx)]


def opposing(axis, side):
    idx = [slice(None)] * 3
    idx[axis] = slice(1, None) if side else slice(None, -1)
    return tuple(idx)


def point_on_edge(axis, side, inner):
    result = list(inner)
    result.insert(axis, side)
    return result


def load_emissions(path):
    # Optional exact runtime registry snapshot. File rows: canonical state, luminance.
    if path is None:
        return {}
    with path.open(encoding="utf-8") as rows:
        return {state: int(amount) for state, amount in
                (line.rsplit("\t", 1) for line in rows)}


def scan(files, emissions, has_skylight, point_rows=()):
    cache = {}
    lit_columns = set()
    invalidated = set()
    versions = Counter()
    chunk_sources = {}
    bad_arrays = []
    statuses = Counter()
    full_columns = set()
    point_light = {}
    for path in files:
        region = RegionFile.open(path)
        rx, rz = map(int, path.stem.split(".")[1:])
        for chunk in region.chunks():
            root = compound(chunk.nbt().root)
            x, z = rx * 32 + chunk.x, rz * 32 + chunk.z
            statuses[root["Status"].value] += 1
            if root["Status"].value not in {"minecraft:full", "full"}:
                continue
            full_columns.add((x, z))
            versions[root["DataVersion"].value] += 1
            chunk_sources[x, z] = path
            marker = root.get("isLightOn")
            if not marker or not marker.value:
                invalidated.add((x, z))
                continue
            lit_columns.add((x, z))
            sections = {compound(s)["Y"].value: s for s in root["sections"].value}
            above = MAX_SKY
            for y in range(20, -5, -1):
                section = sections.get(y)
                if section is None:
                    if has_skylight and y < 20:
                        cache[x, y, z] = (above, None, FULL)
                    continue
                tags = compound(section)
                sky = tags.get("SkyLight")
                block = tags.get("BlockLight")
                try:
                    sky = nibble(sky) if sky else above if has_skylight else None
                    if tags.get("SkyLight") is not None:
                        above = np.broadcast_to(sky[0], (16,16,16))
                    block = nibble(block) if block and y < 20 else None
                except ValueError as exc:
                    bad_arrays.append([x, y, z, str(exc)])
                    continue
                if y == 20:
                    continue
                if sky is not None or block is not None:
                    cache[x, y, z] = (
                        sky,
                        None if block is None or not block.any() else block,
                        None if sky is None else air_mask(section),
                    )
    # The saved arrays cannot establish a boundary gap when one side is unlit.
    interfaces = []
    for x, z in sorted(invalidated):
        for other in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if other in lit_columns:
                interfaces.append(((x, z), other))

    violations = defaultdict(int)
    examples = defaultdict(list)
    all_maxima = defaultdict(list)
    saved_values = Counter()
    for (x, y, z), (sky, block, air) in cache.items():
        neighbors = ((x, y, z+1), (x, y+1, z), (x+1, y, z))
        if sky is not None:
            for axis, other_pos in zip(AXES, neighbors):
                other_sky, _, other_air = cache.get(other_pos, (None, None, None))
                a = opposing(axis, 0)
                b = opposing(axis, 1)
                jump = (np.maximum(sky[a], sky[b]) > np.minimum(sky[a], sky[b]) + 1)
                jump &= air[a] & air[b]
                n = np.count_nonzero(jump)
                if n:
                    key = ((x, z), (x, z))
                    violations[key] += int(n)
                    for pos in np.argwhere(jump)[:max(0, 6 - len(examples[key]))]:
                        pos_b = pos.copy()
                        pos_b[axis] += 1
                        examples[key].append([loc(x,y,z,pos), int(sky[tuple(pos)]),
                                              loc(x,y,z,pos_b), int(sky[tuple(pos_b)])])
                if other_sky is None:
                    continue
                sa, sb = edge(sky, axis, 15), edge(other_sky, axis, 0)
                jump = (np.maximum(sa, sb) > np.minimum(sa, sb) + 1)
                jump &= edge(air, axis, 15) & edge(other_air, axis, 0)
                n = np.count_nonzero(jump)
                if n:
                    key = tuple(sorted(((x,z), (other_pos[0], other_pos[2]))))
                    violations[key] += int(n)
                    for pos in np.argwhere(jump)[:max(0, 6-len(examples[key]))]:
                        a = point_on_edge(axis, 15, pos)
                        b = point_on_edge(axis, 0, pos)
                        examples[key].append([loc(x,y,z,a), int(sky[tuple(a)]),
                                              loc(*other_pos,b), int(other_sky[tuple(b)])])
        if block is None:
            continue
        saved_values.update({i: int(n) for i, n in enumerate(np.bincount(block.reshape(-1), minlength=16)) if i and n})
        maximum = block > 0
        for axis, positive in zip(AXES, neighbors):
            a, b = opposing(axis, 0), opposing(axis, 1)
            maximum[a] &= block[a] >= block[b]
            maximum[b] &= block[b] >= block[a]
            negative = tuple(2*old-new for old, new in zip((x, y, z), positive))
            for side, shift, neighbor_side in ((0, negative, 15), (15, positive, 0)):
                ncolumn = (shift[0], shift[2])
                if ncolumn not in lit_columns:
                    edge(maximum, axis, side)[:] = False
                    continue
                _, other_block, _ = cache.get(shift, (None, None, None))
                if other_block is not None:
                    edge(maximum, axis, side)[:] &= edge(block, axis, side) >= edge(other_block, axis, neighbor_side)
        if maximum.any():
            all_maxima[x,z].append((y, np.argwhere(maximum), block))

    maxima_counts = Counter()
    unsupported = []
    unsupported_columns = set()
    ambiguous = Counter()
    sources = Counter()
    by_region = defaultdict(list)
    for coords in all_maxima:
        by_region[chunk_sources[coords]].append(coords)
    for path, coords_list in by_region.items():
        region = RegionFile.open(path)
        for x,z in coords_list:
            root = compound(region.get_chunk(x%32,z%32).nbt().root)
            sections = {compound(s)["Y"].value: s for s in root["sections"].value}
            for y, positions, block in all_maxima[x,z]:
                read = section_blocks(sections[y]) if y in sections else None
                if read is None:
                    palette, data = ["minecraft:air"], None
                else:
                    palette, data = read
                    palette = [block_state_key(p) for p in palette]
                for pos in positions:
                    dy, dz, dx = map(int, pos)
                    state = "minecraft:air" if data is None else palette[data[dy * 256 + dz * 16 + dx]]
                    value = int(block[tuple(pos)])
                    maxima_counts[state] += 1
                    emissive = emissions.get(state, 0 if state in AIR_IDS else None)
                    if emissive is None:
                        ambiguous[state] += 1
                    elif emissive < value:
                        unsupported.append([loc(x,y,z,pos), state, value, emissive])
                        unsupported_columns.add((x,z))
                    else:
                        sources[state] += 1

    for x,y,z in point_rows:
        key = (x//16,y//16,z//16)
        sky, block, _ = cache.get(key, (None, None, None))
        index = (y%16,z%16,x%16)
        point_light[f"{x} {y} {z}"] = [
            None if sky is None else int(sky[index]),
            None if (key[0],key[2]) not in lit_columns else (0 if block is None else int(block[index])),
        ]
    return {
        "versions": dict(versions),
        "statuses": dict(statuses),
        "full_chunks": len(full_columns),
        "full_chunk_positions": sorted(full_columns),
        "saved_light_chunks": len(lit_columns),
        "invalidated_light_chunks": sorted(invalidated),
        "mixed_cardinal_interfaces": interfaces,
        "invalid_arrays": bad_arrays,
        "air_skylight_jumps": [
            {"chunks": key, "count": n, "examples": examples[key]}
            for key,n in sorted(violations.items(), key=lambda item: (-item[1],item[0]))
        ],
        "saved_block_light_cells_by_level": dict(sorted(saved_values.items())),
        "local_block_light_maxima_states": dict(maxima_counts.most_common()),
        "unsupported_block_light_maxima": unsupported,
        "unsupported_block_light_maxima_chunks": sorted(unsupported_columns),
        "unclassified_block_light_maxima": dict(ambiguous.most_common()),
        "supported_block_light_maxima": dict(sources.most_common()),
        "points_sky_block": point_light,
    }


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--emissions", type=Path)
    parser.add_argument("--patch", type=Path, help="Overlay verified replacement region files")
    args = parser.parse_args()
    dirs = defaultdict(list)
    for path in sorted(args.world.glob("**/region/r.*.*.mca")):
        patched = args.patch/path.relative_to(args.world) if args.patch else None
        dirs[_dimension_for_region(args.world, path)].append(patched if patched and patched.is_file() else path)
    results = {}
    emissions = load_emissions(args.emissions)
    for dimension, files in dirs.items():
        sample = [(96,100,-769),(96,100,-768),(97,100,-768),(97,100,-767),
                  (171,60,-970),(27,-2,-1097)] if dimension == "minecraft:overworld" else []
        has_skylight = dimension in {"minecraft:overworld", "bloodborne_dw:bloodborne_old"}
        result = scan(files, emissions, has_skylight, sample)
        results[dimension] = result
        print(dimension, {
            "chunks": sum(result["statuses"].values()),
            "invalidated": len(result["invalidated_light_chunks"]),
            "mixed": len(result["mixed_cardinal_interfaces"]),
            "air_skylight_jumps": sum(x["count"] for x in result["air_skylight_jumps"]),
            "block_lit_cells": sum(result["saved_block_light_cells_by_level"].values()),
            "unsupported_block_light_maxima": len(result["unsupported_block_light_maxima"]),
            "unclassified_block_light_maxima": sum(result["unclassified_block_light_maxima"].values()),
            "points": result["points_sky_block"],
        }, flush=True)
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
