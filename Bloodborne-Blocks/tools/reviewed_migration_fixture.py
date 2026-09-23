"""Small multi-section/multi-chunk Anvil fixtures for reviewed Contract V2 rules."""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path

from convert_logical_world import AIR_NAME, state_tag
from world_io import (NbtFile, RegionFile, TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LONG, TAG_FLOAT,
                      TAG_LIST, TAG_LONG_ARRAY, TAG_STRING, Tag, compound,
                      pack_palette_indices, write_nbt)


def _chunk(point):
    return point[0] // 16, point[2] // 16


def _point(tag):
    data = compound(tag)
    return int(data["x"].value), int(data["y"].value), int(data["z"].value)


def write_fixture(folder: Path, cells: dict[tuple[int, int, int], tuple[str, dict[str, str]]], *,
                  block_entities=(), fluid_ticks=(), floor=True) -> None:
    """Write a 1.20.1 Anvil world with all needed chunks/sections.

    The optional bedrock floor spans the source bounds (including chunk edges),
    so a tall reviewed post is exercised across sections rather than the old
    Y=64-only test writer.
    """
    if not cells:
        raise ValueError("fixture needs source cells")
    folder.mkdir(parents=True, exist_ok=True)
    def string(value): return Tag(TAG_STRING,value)
    def obj(value): return Tag(TAG_COMPOUND,value)
    def dimension(kind,generator): return obj({'type':string(kind),'generator':obj(generator)})
    generator={'type':string('minecraft:flat'),'settings':obj({'biome':string('minecraft:plains'),
        'lakes':Tag(TAG_BYTE,0),'features':Tag(TAG_BYTE,0),'structure_overrides':Tag(TAG_LIST,[],TAG_STRING),
        'layers':Tag(TAG_LIST,[obj({'block':string('minecraft:bedrock'),'height':Tag(TAG_INT,1)})],TAG_COMPOUND)})}
    first=next(iter(cells))
    level={'DataVersion':Tag(TAG_INT,3465),'LevelName':string('Bloodborne batch-02 synthetic review'),
        'Version':obj({'Id':Tag(TAG_INT,3465),'Name':string('1.20.1'),'Snapshot':Tag(TAG_BYTE,0),'Series':string('main')}),
        'GameType':Tag(TAG_INT,1),'allowCommands':Tag(TAG_BYTE,1),'initialized':Tag(TAG_BYTE,1),'hardcore':Tag(TAG_BYTE,0),
        'Difficulty':Tag(TAG_BYTE,0),'Time':Tag(TAG_LONG,6000),'DayTime':Tag(TAG_LONG,6000),'SpawnAngle':Tag(TAG_FLOAT,0.0),
        'SpawnX':Tag(TAG_INT,first[0]),'SpawnY':Tag(TAG_INT,first[1]+2),'SpawnZ':Tag(TAG_INT,first[2]-4),
        'GameRules':obj({'doDaylightCycle':string('false'),'doMobSpawning':string('false'),'doWeatherCycle':string('false')}),
        'WorldGenSettings':obj({'seed':Tag(TAG_LONG,0),'generate_features':Tag(TAG_BYTE,0),'bonus_chest':Tag(TAG_BYTE,0),
            'dimensions':obj({'minecraft:overworld':dimension('minecraft:overworld',generator),
                'minecraft:the_nether':dimension('minecraft:the_nether',{'type':string('minecraft:noise'),'settings':string('minecraft:nether'),
                    'biome_source':obj({'type':string('minecraft:multi_noise'),'preset':string('minecraft:nether')})}),
                'minecraft:the_end':dimension('minecraft:the_end',{'type':string('minecraft:noise'),'settings':string('minecraft:end'),
                    'biome_source':obj({'type':string('minecraft:the_end')})})})})}
    write_nbt(folder / "level.dat", NbtFile("",obj({'Data':obj(level)})))
    values = dict(cells)
    if floor:
        xs, ys, zs = zip(*values)
        floor_y = min(ys) - 1
        for x in range(min(xs) - 1, max(xs) + 2):
            for z in range(min(zs) - 1, max(zs) + 2):
                # Bedrock is deliberately absent from reviewed carrier
                # patterns; ordinary stone would create false candidates.
                values.setdefault((x, floor_y, z), ("minecraft:bedrock", {}))
    per_chunk = defaultdict(dict)
    for point, state in values.items(): per_chunk[_chunk(point)][point] = state
    entities, ticks = defaultdict(list), defaultdict(list)
    for tag in block_entities: entities[_chunk(_point(tag))].append(tag)
    for tag in fluid_ticks: ticks[_chunk(_point(tag))].append(tag)
    for cx, cz in sorted(set(per_chunk) | set(entities) | set(ticks)):
        sections = []
        by_section = defaultdict(dict)
        for point, state in per_chunk[(cx, cz)].items(): by_section[point[1] // 16][point] = state
        for sy, section_cells in sorted(by_section.items()):
            palette = [state_tag(AIR_NAME, {})]; index = {AIR_NAME: 0}; packed = [0] * 4096
            for (x, y, z), (name, properties) in section_cells.items():
                key = name + ("[" + ",".join(f"{key}={value}" for key, value in sorted(properties.items())) + "]" if properties else "")
                if key not in index:
                    index[key] = len(palette); palette.append(state_tag(name, properties))
                packed[(y & 15) * 256 + (z & 15) * 16 + (x & 15)] = index[key]
            states = {"palette": Tag(TAG_LIST, palette, TAG_COMPOUND)}
            if len(palette) > 1: states["data"] = Tag(TAG_LONG_ARRAY, pack_palette_indices(packed, len(palette)))
            sections.append(Tag(TAG_COMPOUND, {"Y": Tag(TAG_BYTE, sy), "block_states": Tag(TAG_COMPOUND, states),
                "biomes":obj({'palette':Tag(TAG_LIST,[string('minecraft:plains')],TAG_STRING)})}))
        root = Tag(TAG_COMPOUND, {"DataVersion": Tag(TAG_INT, 3465), "xPos": Tag(TAG_INT, cx), "zPos": Tag(TAG_INT, cz),
                                  "yPos":Tag(TAG_INT,-4),'Status':string('minecraft:full'),'isLightOn':Tag(TAG_BYTE,0),
                                  'InhabitedTime':Tag(TAG_LONG,0),'LastUpdate':Tag(TAG_LONG,0),
                                  "sections": Tag(TAG_LIST, sections, TAG_COMPOUND),
                                  "block_entities": Tag(TAG_LIST, entities[(cx, cz)], TAG_COMPOUND),
                                  "fluid_ticks": Tag(TAG_LIST, ticks[(cx, cz)], TAG_COMPOUND),
                                  "Heightmaps": Tag(TAG_COMPOUND, {})})
        region_path = folder / "region" / f"r.{cx // 32}.{cz // 32}.mca"
        region = RegionFile.open(region_path) if region_path.exists() else RegionFile()
        region.set_chunk(cx & 31, cz & 31, NbtFile("", root), timestamp=17)
        region_path.parent.mkdir(parents=True, exist_ok=True); region.save(region_path)
