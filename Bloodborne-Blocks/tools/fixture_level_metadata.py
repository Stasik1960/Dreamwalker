"""1.20.1 synthetic-world metadata, based on Minecraft's own LevelProperties writer.

Data.version is the storage format (19133), NOT DataVersion / Version.Id (3465).
The former controls LevelStorage's summary filter. Some other fields have vanilla
fallbacks; this generator deliberately emits an explicit, non-experimental policy
instead of relying on those fallbacks. No player/world metadata is copied in.
"""
from __future__ import annotations

import argparse
import time
from pathlib import Path

from world_io import (NbtFile, Tag, TAG_BYTE, TAG_COMPOUND, TAG_FLOAT, TAG_INT,
                      TAG_LIST, TAG_LONG, TAG_STRING, compound, read_nbt)

TEMPLATE = Path(__file__).with_name('templates') / 'level-1.20.1.dat'
STORAGE_VERSION = 19133
DATA_VERSION = 3465


def _field(parent, key, tag_type):
    tag = parent.get(key)
    if tag is None or tag.type != tag_type:
        raise ValueError(f'level.dat {key}: required NBT tag type {tag_type}')
    return tag.value


def _strings(parent, key):
    values = _field(parent, key, TAG_LIST)
    if any(tag.type != TAG_STRING for tag in values):
        raise ValueError(f'level.dat {key}: expected string list')
    return [tag.value for tag in values]


def validate_level_metadata(nbt: NbtFile) -> None:
    """Validate this generator's explicit 1.20.1 metadata contract, not all save versions."""
    data = _field(compound(nbt.root), 'Data', TAG_COMPOUND)
    if _field(data, 'version', TAG_INT) != STORAGE_VERSION:
        raise ValueError('level.dat version: expected Anvil storage version 19133')
    if _field(data, 'DataVersion', TAG_INT) != DATA_VERSION:
        raise ValueError('level.dat DataVersion: expected Minecraft 1.20.1 (3465)')
    version = _field(data, 'Version', TAG_COMPOUND)
    if (_field(version, 'Id', TAG_INT) != DATA_VERSION or
            _field(version, 'Name', TAG_STRING) != '1.20.1' or
            _field(version, 'Series', TAG_STRING) != 'main' or
            _field(version, 'Snapshot', TAG_BYTE) != 0):
        raise ValueError('level.dat Version: expected stable Minecraft 1.20.1/main')
    if _field(data, 'LastPlayed', TAG_LONG) < 0:
        raise ValueError('level.dat LastPlayed: expected nonnegative epoch milliseconds')
    if not _field(data, 'LevelName', TAG_STRING):
        raise ValueError('level.dat LevelName: empty')
    if _field(data, 'GameType', TAG_INT) != 1 or _field(data, 'allowCommands', TAG_BYTE) != 1:
        raise ValueError('fixture must be creative with commands')
    for key in ('SpawnX', 'SpawnY', 'SpawnZ'):
        _field(data, key, TAG_INT)
    _field(data, 'SpawnAngle', TAG_FLOAT)
    for key in ('Time', 'DayTime'):
        _field(data, key, TAG_LONG)
    for key in ('hardcore', 'initialized', 'Difficulty', 'DifficultyLocked'):
        _field(data, key, TAG_BYTE)
    packs = _field(data, 'DataPacks', TAG_COMPOUND)
    if _strings(packs, 'Enabled') != ['vanilla'] or _strings(packs, 'Disabled'):
        raise ValueError('fixture template must require only the vanilla data pack')
    if _strings(data, 'enabled_features') != ['minecraft:vanilla']:
        raise ValueError('fixture template must enable only stable vanilla features')
    rules = _field(data, 'GameRules', TAG_COMPOUND)
    if any(tag.type != TAG_STRING for tag in rules.values()):
        raise ValueError('level.dat GameRules: expected string values')
    settings = _field(data, 'WorldGenSettings', TAG_COMPOUND)
    _field(settings, 'seed', TAG_LONG)
    for key in ('generate_features', 'bonus_chest'):
        _field(settings, key, TAG_BYTE)
    dimensions = _field(settings, 'dimensions', TAG_COMPOUND)
    if set(dimensions) != {'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'}:
        raise ValueError('fixture must have the three vanilla dimensions')
    for dimension in dimensions.values():
        entry = compound(dimension)
        _field(entry, 'type', TAG_STRING)
        _field(entry, 'generator', TAG_COMPOUND)
    overworld = compound(dimensions['minecraft:overworld'])
    generator = compound(overworld['generator'])
    if _field(generator, 'type', TAG_STRING) != 'minecraft:flat':
        raise ValueError('fixture must use vanilla flat world generation')
    flat = _field(generator, 'settings', TAG_COMPOUND)
    if not _field(flat, 'layers', TAG_LIST):
        raise ValueError('fixture flat generator must contain layers')
    if 'Player' in data:
        raise ValueError('fixture metadata must not contain copied player data')


def create_level_metadata(spawn, *, level_name='Bloodborne batch-02 synthetic review', last_played=None):
    # read_nbt returns a fresh tree; the checked-in template is never mutated.
    nbt = read_nbt(TEMPLATE)
    data = compound(compound(nbt.root)['Data'])
    data['LevelName'] = Tag(TAG_STRING, level_name)
    data['GameType'] = Tag(TAG_INT, 1)
    data['allowCommands'] = Tag(TAG_BYTE, 1)
    data['LastPlayed'] = Tag(TAG_LONG, int(time.time() * 1000) if last_played is None else last_played)
    for key, value in zip(('SpawnX', 'SpawnY', 'SpawnZ'), spawn):
        data[key] = Tag(TAG_INT, int(value))
    data['SpawnAngle'] = Tag(TAG_FLOAT, 0.0)
    data['Time'] = Tag(TAG_LONG, 6000)
    data['DayTime'] = Tag(TAG_LONG, 6000)
    rules = compound(data['GameRules'])
    for rule in ('doDaylightCycle', 'doMobSpawning', 'doWeatherCycle'):
        rules[rule] = Tag(TAG_STRING, 'false')
    validate_level_metadata(nbt)
    return nbt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('level_dat', type=Path)
    args = parser.parse_args()
    validate_level_metadata(read_nbt(args.level_dat))
    print('LEVEL METADATA PASS: Anvil 19133 / Minecraft 1.20.1 / stable vanilla features')
