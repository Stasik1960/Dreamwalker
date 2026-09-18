#!/usr/bin/env python3
"""Relocate whole decorative blockers, then restore modular pieces on a world COPY.

Preview is the default. ``--apply`` performs only fully planned relocations after
revalidating every source and destination, saves the v2 palette once, and emits
a complete ledger. Fluids and objects without a safe destination remain intact.
"""

from __future__ import annotations

import argparse
import collections
import copy
import hashlib
import json
from pathlib import Path

import numpy as np

from build_modular_palette import RES, ROOT, statekey
from convert_modular_world import AIR, load_palette, set_section, tag_state, unpack_fast
from repair_modular_overlaps import compose, state_text
from world_io import *


NS = "bloodborne_blocks:"
AIR_NAMES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
FLUIDS = {"minecraft:water", "minecraft:lava"}
HELPER = NS + "architecture_part"
DIRECTIONS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
FURNITURE_TOKENS = ("chair", "table", "bench", "cabinet", "shelf", "drawer", "counter", "sofa", "desk", "wardrobe", "chest", "barrel")
UNSUPPORTED_TOKENS = ("air", "water", "lava", "fire", "leaves", "glass", "pane", "fence", "wall", "torch", "flower", "grass", "vine", "carpet", "rail", "button", "pressure_plate")


def add(pos, delta): return tuple(pos[i] + delta[i] for i in range(3))
def subtract(pos, delta): return tuple(pos[i] - delta[i] for i in range(3))


def horizontal_offsets(max_radius=12):
    """Chebyshev rings, nearest Manhattan candidates first within each ring."""
    for radius in range(1, max_radius + 1):
        ring = [(x, 0, z) for x in range(-radius, radius + 1) for z in range(-radius, radius + 1)
                if max(abs(x), abs(z)) == radius]
        yield from sorted(ring, key=lambda p: (abs(p[0]) + abs(p[2]), p[2], p[0]))


def nearest_free_offset(valid, max_radius=12):
    return next((delta for delta in horizontal_offsets(max_radius) if valid(delta)), None)


def relocation_offsets(max_radius=12, vertical_range=0):
    """Candidate translations ordered by distance, preferring the source Y on ties."""
    if vertical_range == 0:
        yield from horizontal_offsets(max_radius);return
    candidates = [(x, y, z) for x in range(-max_radius, max_radius + 1)
                  for y in range(-vertical_range, vertical_range + 1)
                  for z in range(-max_radius, max_radius + 1)
                  if (x or y or z) and max(abs(x), abs(z)) <= max_radius]
    yield from sorted(candidates, key=lambda p: (p[0] * p[0] + p[1] * p[1] + p[2] * p[2],
                                                  p[1] != 0, abs(p[1]), p[1], p[2], p[0]))


def nearest_relocation_offset(valid, max_radius=12, vertical_range=0):
    return next((delta for delta in relocation_offsets(max_radius, vertical_range) if valid(delta)), None)


def pack_block_pos(pos):
    x, y, z = pos;value = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return value - (1 << 64) if value >= 1 << 63 else value


def unpack_block_pos(value):
    value &= (1 << 64) - 1
    x, z, y = value >> 38, (value >> 12) & 0x3FFFFFF, value & 0xFFF
    if x >= 1 << 25:x -= 1 << 26
    if z >= 1 << 25:z -= 1 << 26
    if y >= 1 << 11:y -= 1 << 12
    return x, y, z


def parse_state(text):
    if "[" not in text:return text, {}
    name, raw = text.split("[", 1);return name, dict(pair.split("=", 1) for pair in raw.removesuffix("]").split(",") if pair)


class ChunkData:
    def __init__(self, stored):
        self.stored = stored;self.nbt = stored.nbt();self.root = compound(self.nbt.root);self.loaded = {};self.dirty = False
        self.sections = {int(compound(section)["Y"].value): compound(section)
                         for section in self.root.get("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value}

    def section(self, sy, create=False):
        section = self.sections.get(sy)
        if section is None and create:
            section = {"Y": Tag(TAG_BYTE, sy), "block_states": Tag(TAG_COMPOUND, {"palette": Tag(TAG_LIST, [copy.deepcopy(AIR)], TAG_COMPOUND)})}
            tag = Tag(TAG_COMPOUND, section);self.root.setdefault("sections", Tag(TAG_LIST, [], TAG_COMPOUND)).value.append(tag);self.sections[sy] = section
        return section

    def blocks(self, sy, create=False):
        if sy in self.loaded:return self.loaded[sy]
        section = self.section(sy, create)
        if section is None or "block_states" not in section:
            if not create:return None
            section["block_states"] = Tag(TAG_COMPOUND, {"palette": Tag(TAG_LIST, [copy.deepcopy(AIR)], TAG_COMPOUND)})
        states = compound(section["block_states"]);palette = list(states["palette"].value);array = unpack_fast(states)
        result = [states, palette, array, {block_state_key(entry): index for index, entry in enumerate(palette)}]
        self.loaded[sy] = result;return result

    def entities(self):return self.root.setdefault("block_entities", Tag(TAG_LIST, [], TAG_COMPOUND)).value

    def flush(self):
        if not self.dirty:return
        for sy, (states, palette, array, _) in self.loaded.items():
            set_section(states, palette, array)
        invalidate_chunk_lighting(self.root);self.root.pop("Heightmaps", None)


class WorldEditor:
    def __init__(self, target):self.target = Path(target);self.regions = {};self.chunks = {}

    @staticmethod
    def chunk_key(pos):return pos[0] // 16, pos[2] // 16

    def chunk(self, pos):
        cx, cz = self.chunk_key(pos);key = cx, cz
        if key in self.chunks:return self.chunks[key]
        rkey = cx // 32, cz // 32;path = self.target / "region" / f"r.{rkey[0]}.{rkey[1]}.mca"
        if not path.is_file():self.chunks[key] = None;return None
        if rkey not in self.regions:self.regions[rkey] = [path, RegionFile.open(path), False]
        region = self.regions[rkey][1]
        stored = region.get_chunk(cx % 32, cz % 32)
        if stored is None:self.chunks[key] = None;return None
        chunk = ChunkData(stored)
        if int(chunk.root["xPos"].value) != cx or int(chunk.root["zPos"].value) != cz:raise ValueError(f"chunk coordinate mismatch {cx},{cz}")
        self.chunks[key] = chunk;return chunk

    def state(self, pos):
        chunk = self.chunk(pos)
        if chunk is None:return None
        if chunk.section(pos[1] // 16) is None:return None
        block = chunk.blocks(pos[1] // 16)
        if block is None:return copy.deepcopy(AIR)
        _, palette, array, _ = block;index = (pos[1] % 16) * 256 + (pos[2] % 16) * 16 + pos[0] % 16
        return palette[int(array[index])]

    def entities_at(self, pos):
        chunk = self.chunk(pos)
        if chunk is None:return []
        return [entity for entity in chunk.entities() if entity_position(entity) == pos]

    def set_state(self, pos, entry):
        chunk = self.chunk(pos)
        if chunk is None:raise ValueError(f"cannot write missing chunk at {pos}")
        states, palette, array, indices = chunk.blocks(pos[1] // 16, True);key = block_state_key(entry);index = indices.get(key)
        if index is None:index = len(palette);indices[key] = index;palette.append(copy.deepcopy(entry))
        local = (pos[1] % 16) * 256 + (pos[2] % 16) * 16 + pos[0] % 16;array[local] = index;chunk.dirty = True

    def remove_entities(self, pos):
        chunk = self.chunk(pos);removed = []
        if chunk is None:return removed
        kept = []
        for entity in chunk.entities():
            (removed if entity_position(entity) == pos else kept).append(entity)
        if removed:chunk.root["block_entities"].value = kept;chunk.dirty = True
        return removed

    def add_entity(self, pos, entity):
        chunk = self.chunk(pos)
        if chunk is None:raise ValueError(f"cannot place block entity in missing chunk at {pos}")
        # Empty vanilla lists may be encoded with TAG_End. Once populated they
        # must declare the compound element type, including newly created helpers.
        chunk.entities().append(entity);chunk.root["block_entities"].list_type = TAG_COMPOUND;chunk.dirty = True

    def commit(self):
        changed_chunks = 0
        for (cx, cz), chunk in self.chunks.items():
            if chunk is None or not chunk.dirty:continue
            chunk.flush();rkey = cx // 32, cz // 32;record = self.regions[rkey];record[1].set_chunk(chunk.stored.x, chunk.stored.z, chunk.nbt, timestamp=chunk.stored.timestamp);record[2] = True;changed_chunks += 1
        changed_regions = 0
        for path, region, dirty in self.regions.values():
            if dirty:region.save(path);changed_regions += 1
        return changed_regions, changed_chunks


def entity_position(entity):
    data = compound(entity)
    if not all(axis in data for axis in ("x", "y", "z")):return None
    return tuple(int(data[axis].value) for axis in ("x", "y", "z"))


def plain_tag(tag):
    if tag.type == TAG_COMPOUND:return {key: plain_tag(value) for key, value in sorted(tag.value.items())}
    if tag.type == TAG_LIST:return [plain_tag(value) for value in tag.value]
    if tag.type == TAG_BYTE_ARRAY:return list(tag.value)
    return tag.value


def normalized_entity_hash(entity, anchor):
    value = copy.deepcopy(entity);data = compound(value)
    for axis, base in zip(("x", "y", "z"), anchor):
        if axis in data:data[axis].value = int(data[axis].value) - base
    if "Root" in data:
        root = unpack_block_pos(int(data["Root"].value));data["Root"].value = list(subtract(root, anchor))
    return hashlib.sha256(json.dumps(plain_tag(value), sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def moved_entity(entity, delta):
    result = copy.deepcopy(entity);data = compound(result)
    for axis, amount in zip(("x", "y", "z"), delta):
        if axis in data:data[axis].value = int(data[axis].value) + amount
    if "Root" in data:data["Root"].value = pack_block_pos(add(unpack_block_pos(int(data["Root"].value)), delta))
    return result


def geometry_state(geometry, ident, key):
    value = geometry["blocks"][ident]["states"][key]
    return geometry.get("profiles", {}).get(value.get("ref"), value)


def bloodborne_group(world, start, definitions, geometry):
    entry = world.state(start);name, _ = parse_state(block_state_key(entry));root = start
    if name == HELPER:
        helpers = [entity for entity in world.entities_at(start) if compound(entity).get("id", Tag(TAG_STRING, "")).value == HELPER]
        if len(helpers) != 1 or "Root" not in compound(helpers[0]):raise ValueError("helper_without_unique_root")
        root = unpack_block_pos(int(compound(helpers[0])["Root"].value));entry = world.state(root)
        if entry is None:raise ValueError("missing_helper_root_chunk")
        name, _ = parse_state(block_state_key(entry))
    if not name.startswith(NS) or name == HELPER:raise ValueError("not_bloodborne_root")
    ident = name.removeprefix(NS);definition = definitions.get(ident)
    if definition is None:raise ValueError("unknown_bloodborne_root")
    values = {**definition["default"], **props_of(entry)};key = statekey(values)
    authored = geometry_state(geometry, ident, key);footprint = {root}
    for cell in authored.get("cells", {}):footprint.add(add(root, tuple(map(int, cell.split(",")))))
    states = {root: copy.deepcopy(entry)};entities = {root: [copy.deepcopy(entity) for entity in world.entities_at(root)]};synthetic = set()
    for pos in footprint - {root}:
        state = world.state(pos)
        present = [] if state is None else world.entities_at(pos)
        linked = [entity for entity in present if compound(entity).get("id", Tag(TAG_STRING, "")).value == HELPER
                  and "Root" in compound(entity) and unpack_block_pos(int(compound(entity)["Root"].value)) == root]
        if state is not None and compound(state)["Name"].value == HELPER and len(linked) == 1:
            states[pos] = copy.deepcopy(state);entities[pos] = [copy.deepcopy(entity) for entity in present]
        else:
            # Historical incomplete helpers and foreign occupants remain untouched
            # at the source. Destination gets a fresh owned helper instead.
            synthetic.add(pos)
    return make_group(root, states, entities, "bloodborne:" + ident, footprint, synthetic, NS + ident)


def props_of(entry):
    tag = compound(entry).get("Properties");return {} if tag is None else {key: value.value for key, value in compound(tag).items()}


def neighbors(pos):
    for delta in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):yield add(pos, delta)


def foreign_group(world, start):
    first = world.state(start);name = compound(first)["Name"].value;properties = props_of(first);positions = {start}
    path = name.split(":", 1)[-1]
    if "half" in properties and properties["half"] in {"upper", "lower"}:
        other = add(start, (0, -1 if properties["half"] == "upper" else 1, 0))
        if same_name(world, other, name):positions.add(other)
    if "part" in properties and properties["part"] in {"head", "foot"}:
        for other in (add(start, delta) for delta in DIRECTIONS.values()):
            if same_name(world, other, name) and props_of(world.state(other)).get("part") != properties["part"]:positions.add(other);break
    has_entity = bool(world.entities_at(start));furniture = has_entity or any(token in path for token in FURNITURE_TOKENS)
    if furniture:
        queue = list(positions)
        while queue:
            current = queue.pop()
            for other in neighbors(current):
                if other in positions or not same_name(world, other, name):continue
                positions.add(other);queue.append(other)
                if len(positions) > 64:raise ValueError("related_furniture_group_too_large")
    states = {pos: copy.deepcopy(world.state(pos)) for pos in positions}
    entities = {pos: [copy.deepcopy(entity) for entity in world.entities_at(pos)] for pos in positions}
    anchor = min(positions, key=lambda pos: (pos[1], pos[2], pos[0]));return make_group(anchor, states, entities, "foreign:" + name)


def same_name(world, pos, name):
    state = world.state(pos);return state is not None and compound(state)["Name"].value == name


def make_group(anchor, states, entities, kind, footprint=None, synthetic=None, owner=None):
    return {"anchor": anchor, "states": states, "entities": entities, "kind": kind,
            "footprint": set(states) if footprint is None else set(footprint),
            "synthetic": set() if synthetic is None else set(synthetic), "owner": owner,
            "sourceKeys": {pos: block_state_key(state) for pos, state in states.items()}}


def is_liquid(state):return state is not None and compound(state)["Name"].value in FLUIDS


def known_opaque_cube(state):
    name = compound(state)["Name"].value
    if name.startswith("biomesoplenty:"):return name.endswith("_planks")
    if not name.startswith("minecraft:"):return False
    path = name.removeprefix("minecraft:")
    if any(token in path for token in ("glass", "leaves", "fire", "ice", "slab", "stairs", "wall", "fence", "door", "trapdoor", "light", "barrier")):return False
    return path.endswith(("_concrete", "_terracotta", "_wool", "_planks", "_log", "_wood", "_bricks", "_ore")) or path in {
        "stone", "smooth_stone", "cobblestone", "mossy_cobblestone", "dirt", "grass_block", "coarse_dirt", "podzol", "sand", "red_sand", "gravel",
        "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
        "sandstone", "red_sandstone", "netherrack", "nether_bricks", "end_stone", "end_stone_bricks", "blackstone", "polished_blackstone",
        "deepslate", "cobbled_deepslate", "polished_deepslate", "calcite", "tuff", "clay", "bricks", "mud", "packed_mud",
    }


class SupportClassifier:
    def __init__(self, palette, definitions, geometry):self.palette = palette;self.definitions = definitions;self.geometry = geometry

    @staticmethod
    def boxes_touch_face(boxes, offset):
        if offset == (0, -1, 0):return any(abs(box[4] - 1) < 1e-6 for box in boxes)
        if offset == (0, 1, 0):return any(abs(box[1]) < 1e-6 for box in boxes)
        if offset == (-1, 0, 0):return any(abs(box[3] - 1) < 1e-6 for box in boxes)
        if offset == (1, 0, 0):return any(abs(box[0]) < 1e-6 for box in boxes)
        if offset == (0, 0, -1):return any(abs(box[5] - 1) < 1e-6 for box in boxes)
        if offset == (0, 0, 1):return any(abs(box[2]) < 1e-6 for box in boxes)
        return False

    def supports(self, state, offset):
        if state is None or is_liquid(state):return False
        name = compound(state)["Name"].value
        if name in AIR_NAMES or name == HELPER:return False
        if name.startswith(NS + "m_"):
            ident = name.removeprefix(NS);facing = props_of(state).get("facing", "north")
            try:
                states = self.palette.geometry[ident]["states"];key = "facing=" + facing
                if key not in states:key += ",waterlogged=false"
                boxes = states[key]["cells"]["0,0,0"]["collision"]
            except KeyError:return False
            return self.boxes_touch_face(boxes, offset)
        if name.startswith(NS):
            ident = name.removeprefix(NS);definition = self.definitions.get(ident)
            if definition is None:return False
            key = statekey({**definition["default"], **props_of(state)})
            try:cell = geometry_state(self.geometry, ident, key).get("cells", {}).get("0,0,0", {})
            except KeyError:return False
            return self.boxes_touch_face(cell.get("collision", []), offset)
        return not any(token in name for token in UNSUPPORTED_TOKENS)


def wall_supports(group):
    result = []
    for pos, state in group["states"].items():
        name = compound(state)["Name"].value;properties = props_of(state);facing = properties.get("facing")
        wall = properties.get("face") == "wall" or properties.get("attachment") == "wall" or any(token in name for token in ("wall_torch", "wall_sign", "wall_banner"))
        if wall and facing in DIRECTIONS:result.append((pos, DIRECTIONS[facing]))
    return result


def support_requirements(world, group, classifier):
    """Preserve attachment relations proven around the original object."""
    sources = set(group["states"]);footprint = set(group["footprint"]);walls = []
    for source, outward in wall_supports(group):
        offset = tuple(-value for value in outward);support = add(source, offset)
        if support not in footprint and classifier.supports(world.state(support), offset):walls.append((source, offset, "wall"))
    if walls:return walls
    # Authored cells without historical helpers are still part of the object at
    # its destination. Preserve support proven below/above those cells as well.
    min_y = min(pos[1] for pos in footprint)
    floor = [(source, (0, -1, 0), "floor") for source in footprint if source[1] == min_y
             and add(source, (0, -1, 0)) not in footprint
             and classifier.supports(world.state(add(source, (0, -1, 0))), (0, -1, 0))]
    if floor:return floor
    max_y = max(pos[1] for pos in footprint)
    hanging = [(source, (0, 1, 0), "hanging") for source in footprint if source[1] == max_y
               and add(source, (0, 1, 0)) not in footprint
               and classifier.supports(world.state(add(source, (0, 1, 0))), (0, 1, 0))]
    if hanging:return hanging
    side = []
    for source in sorted(footprint):
        for offset in DIRECTIONS.values():
            support = add(source, offset)
            if support not in footprint and classifier.supports(world.state(support), offset):side.append((source, offset, "side"))
    return side[:1]


def destination_valid(world, group, delta, forbidden, classifier):
    destinations = {add(pos, delta) for pos in group["footprint"]}
    if destinations & forbidden:return False
    # Support is much cheaper to test than a large authored footprint. Testing
    # it first avoids scanning hundreds of empty destination cells for candidates
    # that could never preserve the original attachment.
    for source, offset, _ in group["supportRequirements"]:
        support = add(add(source, delta), offset)
        if support in forbidden or support in destinations or not classifier.supports(world.state(support), offset):return False
    for dest in destinations:
        state = world.state(dest)
        if state is None or compound(state)["Name"].value not in AIR_NAMES or world.entities_at(dest):return False
    return True


def find_destination(world, group, forbidden, classifier, max_radius=12, vertical_range=0):
    return nearest_relocation_offset(lambda delta: destination_valid(world, group, delta, forbidden, classifier), max_radius, vertical_range)


def compose_incoming(palette, rows, cache):
    pieces = [piece for row in rows for piece in row["incomingPieces"]]
    key = tuple(sorted((piece["id"], piece["facing"]) for piece in pieces))
    if key not in cache:cache[key] = compose(palette, pieces, source="world_overlap_relocation")[0]
    return cache[key]


def discover_group(world, pos, definitions, geometry):
    state = world.state(pos)
    if state is None:raise ValueError("missing_source_chunk")
    name = compound(state)["Name"].value
    return bloodborne_group(world, pos, definitions, geometry) if name.startswith(NS) else foreign_group(world, pos)


def validate_plan(world, plan):
    group, delta = plan["group"], plan["delta"]
    for pos, expected in group["sourceKeys"].items():
        state = world.state(pos)
        if state is None or block_state_key(state) != expected:return False, "source_changed_before_commit"
        expected_entities = sorted(normalized_entity_hash(entity, group["anchor"]) for entity in group["entities"][pos])
        actual_entities = sorted(normalized_entity_hash(entity, group["anchor"]) for entity in world.entities_at(pos))
        if actual_entities != expected_entities:return False, "source_block_entities_changed_before_commit"
    for source in group["footprint"]:
        dest = add(source, delta);state = world.state(dest)
        if state is None or compound(state)["Name"].value not in AIR_NAMES or world.entities_at(dest):return False, "destination_changed_before_commit"
    destinations = {add(source, delta) for source in group["footprint"]}
    for source, offset, _ in group["supportRequirements"]:
        support = add(add(source, delta), offset)
        if support in destinations or not plan["classifier"].supports(world.state(support), offset):return False, "support_changed_before_commit"
    return True, None


def relocation_ledger(group, delta):
    new_anchor = add(group["anchor"], delta);cells = [];entities = [];synthetic_entities = []
    for old_pos, state in sorted(group["states"].items()):
        new_pos = add(old_pos, delta);cells.append({"oldPosition": old_pos, "newPosition": new_pos, "state": block_state_key(state)})
        for entity in group["entities"][old_pos]:
            moved = moved_entity(entity, delta);old_hash = normalized_entity_hash(entity, group["anchor"]);new_hash = normalized_entity_hash(moved, new_anchor)
            if old_hash != new_hash:raise AssertionError("block entity changed beyond normalized coordinates/Root")
            entities.append({"id": compound(entity).get("id", Tag(TAG_STRING, "<missing>")).value,
                             "oldPosition": old_pos, "newPosition": new_pos,
                             "oldNormalizedHash": old_hash, "newNormalizedHash": new_hash})
    for old_pos in sorted(group["synthetic"]):
        new_pos = add(old_pos, delta);entity = synthetic_helper_entity(new_pos, new_anchor, group["owner"])
        synthetic_entities.append({"position": new_pos, "root": new_anchor, "owner": group["owner"],
                                   "normalizedHash": normalized_entity_hash(entity, new_anchor)})
    return cells, entities, synthetic_entities


def synthetic_helper_entity(pos, root, owner):
    return Tag(TAG_COMPOUND, {"id": Tag(TAG_STRING, HELPER), "x": Tag(TAG_INT, pos[0]), "y": Tag(TAG_INT, pos[1]), "z": Tag(TAG_INT, pos[2]),
                              "Root": Tag(TAG_LONG, pack_block_pos(root)), "Owner": Tag(TAG_STRING, owner)})


def destination_state(group, source_pos):
    return group["states"].get(source_pos, tag_state("architecture_part"))


def apply_plan(world, plan):
    group, delta = plan["group"], plan["delta"]
    removed = {}
    for pos in group["states"]:removed[pos] = world.remove_entities(pos);world.set_state(pos, AIR)
    for old_pos in group["footprint"]:
        new_pos = add(old_pos, delta);world.set_state(new_pos, destination_state(group, old_pos))
        for entity in removed.get(old_pos, []):world.add_entity(new_pos, moved_entity(entity, delta))
        if old_pos in group["synthetic"]:world.add_entity(new_pos, synthetic_helper_entity(new_pos, add(group["anchor"], delta), group["owner"]))
    for pos, result in plan["fills"].items():world.set_state(pos, tag_state(result["id"], result["properties"]))


def relocate(target, input_report, output_report, apply, max_radius=12, vertical_range=0):
    if max_radius < 1:raise ValueError("max_radius must be at least 1")
    if vertical_range < 0:raise ValueError("vertical_range must be non-negative")
    target = Path(target).resolve();input_report = Path(input_report).resolve();output_report = Path(output_report).resolve()
    if not target.is_dir() or not (target / "level.dat").is_file() or not (target / "region").is_dir():raise ValueError("target must be an existing world copy")
    report = json.loads(input_report.read_text(encoding="utf-8"));rows = report.get("unresolved")
    if not isinstance(rows, list):raise ValueError("repair report has no unresolved list")
    definitions = {definition["id"]: definition for definition in json.loads((RES / "bloodborne_blocks/definitions.json").read_text(encoding="utf-8-sig"))["blocks"]}
    geometry = json.loads((RES / "bloodborne_blocks/geometry.json").read_text(encoding="utf-8-sig"))
    palette = load_palette();classifier = SupportClassifier(palette, definitions, geometry);world = WorldEditor(target);by_position = {tuple(row["position"]): row for row in rows}
    reserved_unresolved = set(by_position);reserved_destinations = set();reserved_sources = set();processed = set();plans = [];unresolved = [];occluded = [];fill_cache = {}
    for row in rows:
        pos = tuple(row["position"])
        if pos in processed:continue
        actual = world.state(pos)
        if actual is None:
            unresolved.append({**row, "relocationReason": "missing_target_chunk"});processed.add(pos);continue
        actual_text = block_state_key(actual)
        if actual_text != row["protectedBlock"]:
            unresolved.append({**row, "relocationReason": "target_state_mismatch", "actualBlock": actual_text});processed.add(pos);continue
        if is_liquid(actual):
            unresolved.append({**row, "relocationReason": "fluid_deferred"});processed.add(pos);continue
        if known_opaque_cube(actual):
            occluded.append({"position": row["position"], "state": actual_text, "incomingPieces": row["incomingPieces"], "reason": "known_opaque_full_cube_occludes_incoming"});processed.add(pos);continue
        try:group = discover_group(world, pos, definitions, geometry)
        except (KeyError, ValueError) as error:
            unresolved.append({**row, "relocationReason": "group_discovery_failed", "error": str(error)});processed.add(pos);continue
        group["supportRequirements"] = support_requirements(world, group, classifier)
        sources = set(group["states"]);group_rows = [by_position[source] for source in sources if source in by_position]
        processed.update(tuple(item["position"]) for item in group_rows)
        if group["synthetic"] and not group["supportRequirements"]:
            for item in group_rows:unresolved.append({**item, "relocationReason": "incomplete_footprint_has_no_proven_support"})
            continue
        if sources & reserved_sources:
            for item in group_rows:unresolved.append({**item, "relocationReason": "source_group_overlap"})
            continue
        forbidden = reserved_unresolved | reserved_destinations | reserved_sources
        delta = find_destination(world, group, forbidden, classifier, max_radius, vertical_range)
        if delta is None:
            reason = f"no_supported_free_destination_within_radius_{max_radius}_vertical_{vertical_range}"
            for item in group_rows:unresolved.append({**item, "relocationReason": reason,
                                                       "searchBounds": {"maxRadius": max_radius, "verticalRange": vertical_range}})
            continue
        destinations = {add(source, delta) for source in group["footprint"]};fills = {}
        try:
            for item in group_rows:fills[tuple(item["position"])] = compose_incoming(palette, [item], fill_cache)
        except (KeyError, ValueError) as error:
            for item in group_rows:unresolved.append({**item, "relocationReason": "incoming_composition_failed", "error": str(error)})
            continue
        plan = {"group": group, "delta": delta, "fills": fills, "rows": group_rows, "classifier": classifier};plans.append(plan);reserved_sources.update(sources);reserved_destinations.update(destinations)

    for plan in plans:
        valid, reason = validate_plan(world, plan)
        if not valid:raise ValueError(reason + ": " + plan["group"]["kind"])
    moved_objects = [];changes = [];entity_moves = []
    for plan in plans:
        cells, entities, synthetic_entities = relocation_ledger(plan["group"], plan["delta"])
        moved_objects.append({"kind": plan["group"]["kind"], "oldAnchor": plan["group"]["anchor"], "newAnchor": add(plan["group"]["anchor"], plan["delta"]),
                              "cells": cells, "blockEntities": entities, "syntheticBlockEntities": synthetic_entities,
                              "supportRequirements": [{"source": source, "offset": offset, "kind": kind} for source, offset, kind in plan["group"]["supportRequirements"]],
                              "fills": [{"position": pos, "oldState": plan["group"]["sourceKeys"][pos], "newState": state_text(result)} for pos, result in sorted(plan["fills"].items())]})
        for old_pos, old_state in plan["group"]["sourceKeys"].items():
            result = plan["fills"].get(old_pos);changes.append({"position": old_pos, "oldState": old_state,
                                                                 "newState": state_text(result) if result else "minecraft:air"})
        for old_pos in plan["group"]["footprint"]:
            changes.append({"position": add(old_pos, plan["delta"]), "oldState": "minecraft:air", "newState": block_state_key(destination_state(plan["group"], old_pos))})
        if plan["group"]["kind"].startswith("foreign:"):
            entity_moves.extend({"from": entry["oldPosition"], "to": entry["newPosition"]} for entry in entities)
    change_positions = [tuple(change["position"]) for change in changes]
    if len(change_positions) != len(set(change_positions)):raise AssertionError("final changes contain duplicate positions")
    changes.sort(key=lambda change: tuple(change["position"]));entity_moves.sort(key=lambda move: tuple(move["from"]))
    if apply:
        for plan in plans:apply_plan(world, plan)
        # Extra unused palette entries are harmless if a later world write fails;
        # the inverse would leave placed blocks without registered resources.
        print("Saving", len(plans), "relocations; unresolved", len(unresolved), flush=True)
        palette.save();changed_regions, changed_chunks = world.commit()
    else:changed_regions = changed_chunks = 0
    accounted = sum(len(plan["rows"]) for plan in plans) + len(unresolved) + len(occluded)
    if accounted != len(rows):raise AssertionError(f"unaccounted conflicts: {accounted} != {len(rows)}")
    result = {"format": "bloodborne-world-relocation-v2", "applied": apply, "target": str(target), "inputReport": str(input_report),
              "counts": {"input": len(rows), "relocatedObjects": len(plans), "relocatedConflictCells": sum(len(plan["rows"]) for plan in plans),
                         "occluded": len(occluded), "unresolved": len(unresolved), "changedRegions": changed_regions, "changedChunks": changed_chunks},
              "safety": {"horizontalOnly": vertical_range == 0, "maxRadius": max_radius, "verticalRange": vertical_range,
                         "candidateOrder": "euclidean_distance_then_same_y" if vertical_range else "chebyshev_rings_then_manhattan",
                         "existingChunksAndSectionsOnly": True, "destinationsWereAir": True, "reservedUnresolvedAvoided": True,
                         "sourceAndDestinationValidatedBeforeCommit": True, "foreignBlocksOverwritten": 0, "fluidsChanged": 0,
                         "paletteSavedOnce": bool(apply)},
              "changes": changes, "entityMoves": entity_moves, "movedObjects": moved_objects,
              "occluded": occluded, "unresolved": unresolved}
    output_report.parent.mkdir(parents=True, exist_ok=True);output_report.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return result


def self_test():
    offsets = list(horizontal_offsets(2));assert len(offsets) == 24 and len(set(offsets)) == 24
    assert all(max(abs(x), abs(z)) == 1 for x, _, z in offsets[:8]);assert all(max(abs(x), abs(z)) == 2 for x, _, z in offsets[8:])
    blocked = {(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1)}
    nearest = nearest_free_offset(lambda delta: (delta[0], delta[2]) not in blocked, 2);assert nearest == (1, 0, 1)
    ordered = list(relocation_offsets(2, 1));assert ordered.index((1, 0, 0)) < ordered.index((0, 1, 0))
    nearest_3d = nearest_relocation_offset(lambda delta: delta in {(1, 0, 1), (0, 1, 0)}, 2, 1);assert nearest_3d == (0, 1, 0)
    for pos in ((0, 0, 0), (-362, 66, -186), (33554431, 2047, -33554432), (-33554432, -2048, 33554431)):
        assert unpack_block_pos(pack_block_pos(pos)) == pos
    for pos in ((0, 64, 0), (-362, 66, -186)):
        delta = (7, 0, -11);assert unpack_block_pos(pack_block_pos(add(pos, delta))) == add(pos, delta)
    class FakeWorld:
        def __init__(self, states, entities=None):self.states = states;self.entities = entities or {}
        def state(self, pos):return self.states.get(pos, copy.deepcopy(AIR))
        def entities_at(self, pos):return self.entities.get(pos, [])
    class FakeClassifier:
        def supports(self, state, offset):return compound(state)["Name"].value == "minecraft:stone"
    wall_pos = (0, 10, 0);wall_group = make_group(wall_pos, {wall_pos: tag_state("minecraft:wall_torch", {"facing": "north"})}, {wall_pos: []}, "test")
    wall_world = FakeWorld({add(wall_pos, (0, 0, 1)): tag_state("minecraft:stone"), add(wall_pos, (0, -1, 0)): tag_state("minecraft:stone")})
    assert support_requirements(wall_world, wall_group, FakeClassifier()) == [(wall_pos, (0, 0, 1), "wall")]
    floor_cells = {(0, 20, 0): tag_state("minecraft:flower_pot"), (1, 20, 0): tag_state("minecraft:flower_pot")}
    floor_world = FakeWorld({(0, 19, 0): tag_state("minecraft:stone"), (1, 19, 0): tag_state("minecraft:stone")})
    floor_group = make_group((0, 20, 0), floor_cells, {pos: [] for pos in floor_cells}, "test")
    assert {item[0] for item in support_requirements(floor_world, floor_group, FakeClassifier())} == set(floor_cells)
    root = (4, 30, 4);helper = add(root, (1, 0, 0));foreign = add(root, (2, 0, 0))
    helper_entity = Tag(TAG_COMPOUND, {"id": Tag(TAG_STRING, HELPER), "x": Tag(TAG_INT, helper[0]), "y": Tag(TAG_INT, helper[1]), "z": Tag(TAG_INT, helper[2]),
                                           "Root": Tag(TAG_LONG, pack_block_pos(root)), "Owner": Tag(TAG_STRING, NS + "test_prop")})
    source_world = FakeWorld({root: tag_state("test_prop", {"facing": "north"}), helper: tag_state("architecture_part"), foreign: tag_state("minecraft:stone")}, {helper: [helper_entity]})
    definitions = {"test_prop": {"id": "test_prop", "default": {"facing": "north"}}}
    geometry = {"blocks": {"test_prop": {"states": {"facing=north": {"cells": {"0,0,0": {}, "1,0,0": {}, "2,0,0": {}}}}}}}
    group = bloodborne_group(source_world, root, definitions, geometry)
    assert set(group["states"]) == {root, helper} and group["footprint"] == {root, helper, foreign} and group["synthetic"] == {foreign}
    assert block_state_key(source_world.state(foreign)) == "minecraft:stone"
    source_world.states[add(foreign, (0, -1, 0))] = tag_state("minecraft:stone")
    requirements = support_requirements(source_world, group, FakeClassifier())
    assert requirements == [(foreign, (0, -1, 0), "floor")]
    group["supportRequirements"] = requirements
    vertical_delta = (0, 2, 0);source_world.states[add(add(foreign, vertical_delta), (0, -1, 0))] = tag_state("minecraft:stone")
    assert destination_valid(source_world, group, vertical_delta, set(), FakeClassifier())
    source_world.states.pop(add(add(foreign, vertical_delta), (0, -1, 0)))
    assert not destination_valid(source_world, group, vertical_delta, set(), FakeClassifier())
    new_root = add(root, (5, 0, 0));synthetic = synthetic_helper_entity(add(foreign, (5, 0, 0)), new_root, group["owner"]);synthetic_data = compound(synthetic)
    assert synthetic_data["id"].value == HELPER and unpack_block_pos(synthetic_data["Root"].value) == new_root and synthetic_data["Owner"].value == NS + "test_prop"
    print("RELOCATION HELPER TESTS PASSED: 3D nearest search, packed BlockPos, wall/floor/vertical support, incomplete helper footprint")


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument("target", type=Path, nargs="?")
    parser.add_argument("--conflicts", "--report", dest="conflicts", type=Path, default=ROOT / "docs/world-overlap-repair-v2.json",
                        help="JSON object containing the unresolved conflict list")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/world-relocation-v2.json")
    parser.add_argument("--max-radius", type=int, default=12)
    parser.add_argument("--vertical-range", type=int, default=0)
    parser.add_argument("--apply", action="store_true");parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:self_test();return
    if args.target is None:parser.error("target is required unless --self-test is used")
    result = relocate(args.target, args.conflicts, args.output, args.apply, args.max_radius, args.vertical_range);print(json.dumps(result["counts"], ensure_ascii=False))


if __name__ == "__main__":main()
