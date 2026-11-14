"""Transplant the matching Ether interior and grove into the prepared Bloodborne save."""
import argparse
from collections import Counter
from copy import deepcopy
from functools import lru_cache
from hashlib import sha256
import json
import os
from pathlib import Path
import shutil
import sys
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from map_io.studio_save import AIR, OwnerShard, SHARD_DIR, WorldEdit, chunk_key, list_tag, name, properties, state_tag, static_index
from map_io.world_io import NbtFile, RegionFile, Tag, block_state_key, compound, encode_nbt, section_blocks
from update_city_followup_map import single_grass_recipes

DONOR_DIR = "Ether-Bloodborne-2.0.2/"
DONOR_SHA = "c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9"
DONOR_URL = "https://github.com/Stasik1960/Dreamwalker/blob/codex/bloodborne-beta-world-20260925/Bloodborne-Blocks/reference-inputs/latest-modded-world.zip"
OFFSET = -554, 8, 793
ROOM = range(165, 191), range(57, 73), range(-999, -964)
GROVE = range(7, 45), range(-7, 6), range(-1114, -1090)
WOOD = {"biomesoplenty:dead_planks", "biomesoplenty:maple_planks"}
ROOM_VANILLA = {
    "minecraft:bookshelf", "minecraft:mangrove_stairs", "minecraft:mangrove_leaves",
    "minecraft:mangrove_planks", "minecraft:spruce_door", "minecraft:oak_pressure_plate",
    "minecraft:brown_wool",
}
GROVE_PLANTS = {
    "minecraft:oak_leaves", "minecraft:oak_log", "minecraft:dark_oak_wood", "minecraft:vine",
    "minecraft:grass", "minecraft:tall_grass", "minecraft:large_fern", "minecraft:fern",
}
LEGACY_FIXTURES = {"source_addon_bag", "source_addon_barrel", "source_addon_books"}
GRASS_SOIL = {"minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
              "minecraft:podzol", "minecraft:mycelium", "minecraft:moss_block", "minecraft:farmland"}
DIRECTIONS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
UPPER_FLOOR = 66
CEILING = 72


def state_parts(key):
    block, _, suffix = key.partition("[")
    values = dict(pair.split("=", 1) for pair in suffix[:-1].split(",")) if suffix else {}
    return block, values


def state_key(block, props):
    return block + ("[" + ",".join(f"{key}={value}" for key, value in sorted(props.items())) + "]" if props else "")


def shifted(pos, offset=OFFSET):
    return tuple(a + b for a, b in zip(pos, offset, strict=True))


def unpack_position(value):
    x = value >> 38
    y = value & 4095
    z = (value >> 12) & ((1 << 26) - 1)
    return x, y - (1 << 12) if y >= (1 << 11) else y, z - (1 << 26) if z >= (1 << 25) else z


def remap(block, props):
    facing = props.get("facing", "north")
    dry = {"waterlogged": "false"}
    match block:
        case "biomesoplenty:dead_planks":
            return "minecraft:dark_oak_planks", {}
        case "biomesoplenty:dead_stairs":
            return "minecraft:dark_oak_stairs", props
        case "biomesoplenty:maple_planks":
            return "minecraft:jungle_planks", {}
        case "mcwpaths:mud_brick_diamond_paving":
            return "minecraft:mud_brick_slab", {"type": "bottom", **dry}
        case "amendments:wall_lantern":
            return "yuushya:goth_lantern", {"facing": facing, "waterlogged": props.get("waterlogged", "false")}
        case "mcwlights:iron_triple_candle_holder":
            return "minecraft:candle", {"candles": "3", "lit": "true", **dry}
        case "mcwlights:dark_oak_tiki_torch":
            return "minecraft:torch", {}
        case "supplementaries:daub_frame" | "supplementaries:daub_cross_brace":
            return "yuushya:jungle_framed_planks", {"axis": props.get("axis", "y")}
        case "supplementaries:candle_holder_light_gray":
            return "minecraft:light_gray_candle", {"candles": props.get("candles", "1"), "lit": props.get("lit", "false"), "waterlogged": props.get("waterlogged", "false")}
        case "supplementaries:item_shelf":
            return "handcrafted:spruce_shelf", {"facing": facing, "shape": "single", "type": "1"}
        case "magic_vibe_decorations:bkdarkoak":
            return "minecraft:bookshelf", {}
        case "yuushya:vending_machine_blue_v":
            return "yuushya:refrigerator", {"facing": facing, "pos": props.get("pos", "middle"), **dry}
        case "diagonalfences:biomesoplenty/palm_fence":
            block = "diagonalfences:minecraft/jungle_fence"
    prefix = "diagonalfences:minecraft/"
    if block.startswith(prefix):
        return "minecraft:" + block.removeprefix(prefix), {
            key: props.get(key, "false") for key in (*DIRECTIONS, "waterlogged")
        }
    return block, props


@lru_cache(maxsize=8)
def donor_region(archive, rx, rz):
    with ZipFile(archive) as source:
        return RegionFile(source.read(f"{DONOR_DIR}region/r.{rx}.{rz}.mca"))


def original_donor_chunk(archive, cx, cz):
    return donor_region(archive, cx // 32, cz // 32).get_chunk(cx % 32, cz % 32)


def section_clip(axis, section):
    base = section * 16
    return range(max(axis.start, base), min(axis.stop, base + 16))


def donor_cells(donor, limits):
    inverse = tuple(-value for value in OFFSET)
    xs, ys, zs = (range(axis.start + delta, axis.stop + delta)
                  for axis, delta in zip(limits, OFFSET, strict=True))
    result = {}
    for cx in range(xs.start // 16, (xs.stop - 1) // 16 + 1):
        for cz in range(zs.start // 16, (zs.stop - 1) // 16 + 1):
            chunk = original_donor_chunk(donor, cx, cz)
            fields = compound(chunk.nbt().root)
            for section in fields["sections"].value:
                sy = compound(section)["Y"].value
                height = section_clip(ys, sy)
                decoded = section_blocks(section)
                if not height or decoded is None:
                    continue
                palette, indices = decoded
                entries = [(name(tag), properties(tag)) for tag in palette]
                for y in height:
                    for z in section_clip(zs, cz):
                        for x in section_clip(xs, cx):
                            index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
                            result[shifted((x, y, z), inverse)] = entries[indices[index]]
    return result


@lru_cache(maxsize=24)
def donor_chunk(donor, cx, cz):
    chunk = original_donor_chunk(donor, cx, cz)
    fields = compound(chunk.nbt().root)
    return {tuple(compound(be)[axis].value for axis in "xyz"): be
            for be in fields.get("block_entities", list_tag()).value}


def chunk_metadata(chunk):
    visual_data = compound(chunk.fields.get("DreamwalkerVisuals", Tag(10, {})))
    editor = visual_data.get("CityEditorCells", list_tag())
    if editor.type == 10:
        editor_positions = compound(editor)["Positions"].value
    else:
        editor_positions = [compound(record)["Pos"].value for record in editor.value]
    protected = {unpack_position(pos) for pos in editor_positions}
    protected.update(unpack_position(compound(row)["Pos"].value)
                     for row in visual_data.get("Source63Frames", list_tag()).value)
    overlay = {}
    for section in visual_data.get("Sections", list_tag()).value:
        rows = compound(section)
        height = rows["Y"].value
        overlay.update({(height, entry & 4095): ((entry & 0xffffffff) >> 12) for entry in rows["Entries"].value})
    return protected, overlay


def keep_leaves_persistent(block, props):
    if block == "minecraft:oak_leaves":
        return block, {**props, "persistent": "true"}
    return block, props


def validated_target(block, props, by_name, active):
    incoming = state_key(block, props)
    if incoming in active:
        return block, props
    if block.startswith("yuushya:"):
        dry_props = {**props, "waterlogged": "false"}
        if state_key(block, dry_props) in active:
            return block, dry_props
    if block.startswith("yuushya:") and props.get("form") == "0":
        current_props = {key: value for key, value in props.items() if key != "form"}
        current_props["waterlogged"] = "false"
        if state_key(block, current_props) in active:
            return block, current_props
    target, props = remap(block, props)
    if state_key(target, props) in active:
        return target, props
    candidates = [(candidate, values) for candidate, values in by_name.get(target, [])
                  if all(values.get(k) == v for k, v in props.items() if k in values)]
    if len(candidates) == 1 and target != block:
        return candidates[0]
    return None


def relocate_entity(tag, pos, target):
    if tag is None:
        return None
    src = compound(tag)
    identifier = src["id"].value
    if identifier.startswith("handcrafted:") and target.startswith("handcrafted:"):
        result = deepcopy(tag)
        fields = compound(result)
    elif identifier == "supplementaries:item_shelf" and target == "handcrafted:spruce_shelf":
        # Handcrafted containers use the same standard Items list keyed by Slot.
        items = src.get("Items")
        if items is None or not items.value:
            return None
        result = Tag(10, {"id": Tag(8, "handcrafted:container"), "Items": deepcopy(items)})
        fields = compound(result)
    else:
        return None
    fields.update({axis: Tag(3, value) for axis, value in zip("xyz", pos, strict=True)})
    return result


def load_catalog(jar):
    with ZipFile(jar) as source:
        static = json.loads(source.read("bloodborne_dw/static-architecture-v1274.json"))
        raw = source.read("assets/bloodborne_dw/native_grid/catalog.json")
    native = json.loads(raw)
    whole_cells = {item["cell"] for model in native["wholeObjects"] for item in model["cells"]}
    return static, native, whole_cells, sha256(raw).hexdigest()


def configure_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=Path)
    parser.add_argument("donor", type=Path)
    parser.add_argument("registry", type=Path)
    parser.add_argument("jar", type=Path)
    parser.add_argument("report", type=Path)
    parser.add_argument("--apply", action="store_true")
    return parser


def main():
    args = configure_parser().parse_args()
    if sha256(args.donor.read_bytes()).hexdigest() != DONOR_SHA:
        raise ValueError("Donor differs from the historical Git LFS object")
    active = set()
    by_name = {}
    with args.registry.open(encoding="utf-8") as registry:
        for line in registry:
            block, props = state_parts(line.split("\t")[0].strip())
            if block.startswith(("minecraft:", "yuushya:", "handcrafted:", "bloodborne_dw:static_architecture_")):
                active.add(state_key(block, props))
                if block.startswith(("minecraft:", "yuushya:", "handcrafted:")):
                    by_name.setdefault(block, []).append((block, props))
    world = WorldEdit(args.world)
    static, native, whole_cells, catalog_hash = load_catalog(args.jar)
    grass_states = single_grass_recipes(static)
    for state in grass_states.values():
        assert block_state_key(state) in active

    @lru_cache(maxsize=None)
    def metadata(cx, cz):
        chunk = world.at((cx * 16, 0, cz * 16))
        protected, overlay = chunk_metadata(chunk)
        visual_data = compound(chunk.fields.get("DreamwalkerVisuals", Tag(10, {})))
        if overlay and visual_data["Catalog"].value != catalog_hash:
            raise ValueError(f"Wrong native-visual catalog at chunk {cx}, {cz}")
        shards = args.world / SHARD_DIR
        shard_file = shards / f"owners-{cx}.{cz}.nbt"
        if shard_file.exists():
            shard = OwnerShard(shard_file, cx, cz)
            protected |= {pos for pos, refs in shard.cells.items() if refs}
        return protected, overlay

    updates, replacements = Counter(), Counter()
    conflicts, unsupported = Counter(), Counter()
    foliage, removed_art, transferred_nbt = Counter(), Counter(), Counter()
    overwritten = Counter()
    samples = {}
    planned = {}
    overlay_removed = {}
    entities = {}
    old_room = donor_cells(args.donor, ROOM)
    old_grove = donor_cells(args.donor, GROVE)
    # These two historical remappings are oak log in the preserved pre-remap dimension.
    historic_wood = {"bloodborne_blocks:m_17073b3f79a0aeed", "bloodborne_blocks:m_20fcb8389fb88a67"}
    wood_positions = {(20, 1, -1103), (20, 2, -1103), (39, 2, -1097)}
    for pos in wood_positions:
        if old_grove[pos][0] not in historic_wood:
            raise ValueError(f"Historical tree fingerprint differs at {pos}")
        old_grove[pos] = "minecraft:oak_log", {"axis": "y"}

    def collision_with_owner(pos, plants=False):
        chunk = world.at(pos)
        protected, overlay = metadata(*chunk_key(pos))
        state = chunk.state(pos)
        current = name(state)
        local = chunk.cell(pos)
        if pos in protected or pos in chunk.entities or current == "bloodborne_dw:composite_cell":
            return "owner"
        native_id = overlay.get(local)
        if native_id is not None and (plants or native_id in whole_cells):
            return "native model"
        recipe = static_index(state)
        if recipe >= 0:
            model = static["recipes"][recipe]
            if any(model["remove"]) or any(any(row["origin"]) for row in model["fragments"]):
                return "multi-block model"
        if plants and current not in AIR:
            return "occupied"
        return None

    def plan(pos, block, props, group, origin, entity=None):
        chunk = world.at(pos)
        state = state_tag(block, props)
        if chunk.state(pos) == state and entity is None:
            return
        if group == "grove":
            assert name(chunk.state(pos)) in AIR
        if group == "interior":
            overwritten[name(chunk.state(pos))] += 1
        planned[pos] = state
        updates[group] += 1
        if origin != block:
            replacements[(origin, block)] += 1
        samples.setdefault(f"{group}:{block}", pos)
        if entity is not None:
            entities[pos] = entity
            transferred_nbt[compound(entity)["id"].value] += 1
        protected, overlay = metadata(*chunk_key(pos))
        if chunk.cell(pos) in overlay:
            overlay_removed.setdefault(chunk_key(pos), set()).add(chunk.cell(pos))

    for pos, (block, props) in old_room.items():
        y = pos[1]
        if block.startswith("bloodborne_blocks:") or block == "minecraft:barrier":
            continue
        current = name(world.at(pos).state(pos))
        if block in AIR:
            path = current.removeprefix("bloodborne_dw:")
            if y in range(58, 66) or y in range(67, 72):
                if any(path.startswith(prefix) for prefix in LEGACY_FIXTURES):
                    removed_art["old fixtures preserved (owner)"] += 1
            continue
        if block.startswith("minecraft:") and block not in ROOM_VANILLA:
            continue
        owner = collision_with_owner(pos)
        if owner:
            conflicts[("interior", owner, block)] += 1
            continue
        candidate = validated_target(block, props, by_name, active)
        if candidate is None:
            unsupported[(block, state_key(block, props))] += 1
            continue
        target, target_props = candidate
        # Ceiling material is interior where building floor exists directly below.
        if y == CEILING and block in WOOD and (pos[0], UPPER_FLOOR, pos[2]) not in old_room:
            continue
        historic_pos = shifted(pos)
        be = donor_chunk(args.donor, *chunk_key(historic_pos)).get(historic_pos)
        incoming = relocate_entity(be, pos, target)
        if be is not None and incoming is None and compound(be).get("Items", list_tag()).value:
            conflicts[("interior", "unsupported block entity inventory", block)] += 1
            continue
        plan(pos, target, target_props, "interior", block, incoming)

    for pos, (block, props) in old_grove.items():
        if block not in GROVE_PLANTS:
            continue
        if block in {"minecraft:grass", "minecraft:tall_grass", "minecraft:large_fern", "minecraft:fern"}:
            if props.get("half") == "upper":
                continue
            upper = shifted(pos, (0, 1, 0))
            below = shifted(pos, (0, -1, 0))
            tall = props.get("half") == "lower"
            positions = (pos, upper) if tall else (pos,)
            if tall and old_grove.get(upper) != (block, {"half": "upper"}):
                raise ValueError(f"Incomplete donor plant {pos}")
            blocked = next((reason for point in positions if (reason := collision_with_owner(point, True))), None)
            if blocked:
                conflicts[("grove", blocked, block)] += len(positions)
                continue
            soil = name(world.at(below).state(below))
            for index, point in enumerate(positions):
                target = (block, old_grove[point][1])
                if soil not in GRASS_SOIL:
                    variant = 1 if block in {"minecraft:large_fern", "minecraft:fern"} else 0
                    state = grass_states[variant, index > 0]
                    target = name(state), properties(state)
                plan(point, *target, "grove", block)
                foliage[block] += 1
            continue
        if owner := collision_with_owner(pos, True):
            conflicts[("grove", owner, block)] += 1
            continue
        target = keep_leaves_persistent(block, props)
        assert state_key(*target) in active
        plan(pos, *target, "grove", block)
        foliage[block] += 1

    # Restore paired vanilla doors only if both parts can be transferred.
    for pos, state in list(planned.items()):
        if name(state) != "minecraft:spruce_door":
            continue
        half = properties(state)["half"]
        peer = shifted(pos, (0, 1 if half == "lower" else -1, 0))
        existing = planned.get(peer, world.at(peer).state(peer))
        if name(existing) != name(state) or properties(existing)["half"] == half:
            del planned[pos]
            updates["interior"] -= 1
            overwritten[name(world.at(pos).state(pos))] -= 1
            overlay_removed.get(chunk_key(pos), set()).discard(world.at(pos).cell(pos))
            conflicts[("interior", "door pair protected", name(state))] += 1

    report = {
        "donor": DONOR_URL, "donor_sha256": DONOR_SHA, "offset_target_to_donor": OFFSET,
        "changed_cells": dict(updates), "grove_by_donor_id": dict(foliage),
        "substitutions": {f"{a} -> {b}": count for (a, b), count in replacements.items()},
        "protected_conflicts": {" / ".join(key): val for key, val in conflicts.items()},
        "unmapped_states": {" / ".join(key): val for key, val in unsupported.items()},
        "block_entities": dict(transferred_nbt), "current_fixtures": dict(removed_art),
        "overwritten_interior_by_prev_id": dict(overwritten),
        "removed_native_overlay_cells": sum(map(len, overlay_removed.values())),
        "sample_positions": samples, "chunks": sorted({chunk_key(pos) for pos in planned}),
    }
    print(json.dumps({k: v for k, v in report.items() if k not in {"sample_positions", "protected_conflicts", "overwritten_interior_by_prev_id"}}, ensure_ascii=True, indent=2))
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not args.apply:
        return

    for pos, state in planned.items():
        chunk = world.at(pos)
        chunk.set_state(pos, state)
        if pos in entities:
            chunk.set_entity(pos, entities[pos])
    for key, cells_to_remove in overlay_removed.items():
        if not cells_to_remove:
            continue
        chunk = world.at((key[0] * 16, 0, key[1] * 16))
        visual_data = compound(chunk.fields["DreamwalkerVisuals"])
        sections = []
        for section in visual_data["Sections"].value:
            row = compound(section)
            entries = [entry for entry in row["Entries"].value if (row["Y"].value, entry & 4095) not in cells_to_remove]
            if entries:
                row["Entries"] = Tag(11, entries)
                sections.append(section)
        visual_data["Sections"] = list_tag(sections)
    for (cx, cz), chunk in world.chunks.items():
        chunk.flush(world.region(cx, cz))

    dirty_chunks = {chunk_key(pos) for pos in planned}
    backup_dir = args.report.parent / (args.report.stem + "-region-backups")
    backup_dir.mkdir(exist_ok=False)
    prepared = []
    for path, region in world.regions.items():
        if not region._dirty:
            continue
        backup = backup_dir / path.name
        shutil.copy2(path, backup)
        tmp = backup_dir / (path.name + ".checked")
        region.save(tmp)
        saved = RegionFile.open(tmp)
        original = RegionFile.open(backup)
        _, rx, rz, _ = path.name.split(".")
        for old in original.chunks():
            key = (int(rx) * 32 + old.x, int(rz) * 32 + old.z)
            if key not in dirty_chunks:
                assert saved.get_chunk(old.x, old.z) == old, (path, key)
        prepared.append((tmp, path))
    for temp, target in prepared:
        os.replace(temp, target)

    checked = WorldEdit(args.world)
    for pos, expected in planned.items():
        chunk = checked.at(pos)
        assert block_state_key(chunk.state(pos)) == block_state_key(expected), pos
        if pos in entities:
            assert encode_nbt(NbtFile("", chunk.entities[pos])) == encode_nbt(NbtFile("", entities[pos])), pos
        if chunk.cell(pos) in overlay_removed.get(chunk_key(pos), ()):
            assert chunk.cell(pos) not in chunk_metadata(chunk)[1], pos
    report["applied_and_verified"] = True
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
