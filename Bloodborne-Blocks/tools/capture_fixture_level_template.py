"""Capture a privacy-safe metadata template from an engine-written 1.20.1 test save.

This is an explicit maintainer operation, not a dependency on a user's saves.
Only the allowlisted vanilla metadata fields are retained. Player, UUID, boss,
dragon, scheduled-event and wandering-trader state never enter the template.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

from fixture_level_metadata import validate_level_metadata
from world_io import (NbtFile, Tag, TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LONG,
                      TAG_LIST, TAG_STRING, compound, read_nbt, write_nbt)

FIELDS = '''version DataVersion Version LastPlayed LevelName GameType allowCommands
SpawnX SpawnY SpawnZ SpawnAngle Time DayTime Difficulty DifficultyLocked hardcore
initialized clearWeatherTime rainTime raining thunderTime thundering
BorderCenterX BorderCenterZ BorderDamagePerBlock BorderSafeZone BorderSize
BorderSizeLerpTarget BorderSizeLerpTime BorderWarningBlocks BorderWarningTime
WorldGenSettings GameRules DataPacks enabled_features'''.split()


def capture(source, output):
    if output.exists():
        raise FileExistsError('Refusing to overwrite a template: ' + str(output))
    original = compound(compound(read_nbt(source).root)['Data'])
    if original['version'].value != 19133 or original['DataVersion'].value != 3465:
        raise ValueError('Need an engine-written Anvil Minecraft 1.20.1 save')
    if compound(original['Version'])['Name'].value != '1.20.1':
        raise ValueError('Not a stable 1.20.1 reference')
    # All template tags originate in the real serializer output. In particular,
    # the lowercase format version must not be inferred from Version.Id.
    data = {name: copy.deepcopy(original[name]) for name in FIELDS}
    data['LevelName'] = Tag(TAG_STRING, 'Minecraft 1.20.1 synthetic fixture template')
    data['LastPlayed'] = Tag(TAG_LONG, 0)  # Generator supplies current epoch millis.
    data['Time'] = data['DayTime'] = Tag(TAG_LONG, 6000)
    data['GameType'] = Tag(TAG_INT, 1)
    for name, value in (('allowCommands', 1), ('initialized', 1), ('hardcore', 0),
                        ('Difficulty', 0), ('DifficultyLocked', 0), ('raining', 0), ('thundering', 0)):
        data[name] = Tag(TAG_BYTE, value)
    for name in ('SpawnX', 'SpawnZ', 'rainTime', 'thunderTime', 'clearWeatherTime'):
        data[name] = Tag(TAG_INT, 0)
    data['SpawnY'] = Tag(TAG_INT, 64)
    data['DataPacks'] = Tag(TAG_COMPOUND, {'Enabled': Tag(TAG_LIST, [Tag(TAG_STRING, 'vanilla')], TAG_STRING),
                                        'Disabled': Tag(TAG_LIST, [], TAG_STRING)})
    data['enabled_features'] = Tag(TAG_LIST, [Tag(TAG_STRING, 'minecraft:vanilla')], TAG_STRING)
    worldgen = compound(data['WorldGenSettings'])
    worldgen['seed'] = Tag(TAG_LONG, 0)
    worldgen['generate_features'] = worldgen['bonus_chest'] = Tag(TAG_BYTE, 0)
    dims = compound(worldgen['dimensions'])
    flat = compound(compound(compound(dims['minecraft:overworld'])['generator'])['settings'])
    # Retain the actual engine-written bedrock layer and vanilla dimension refs.
    bedrock = next(layer for layer in flat['layers'].value if compound(layer)['block'].value == 'minecraft:bedrock')
    if compound(bedrock)['height'].value != 1:
        raise ValueError('Expected a one-block bedrock layer in the reference')
    flat['layers'] = Tag(TAG_LIST, [bedrock], TAG_COMPOUND)
    flat['structure_overrides'] = Tag(TAG_LIST, [], TAG_STRING)
    flat['features'] = flat['lakes'] = Tag(TAG_BYTE, 0)
    nbt = NbtFile('', Tag(TAG_COMPOUND, {'Data': Tag(TAG_COMPOUND, data)}))
    validate_level_metadata(nbt)
    output.parent.mkdir(parents=True, exist_ok=True)
    write_nbt(output, nbt)
    provenance = {'format': 'Minecraft Java 1.20.1 / gzip NBT / Anvil 19133',
                  'source_kind': 'Minecraft LevelProperties writer, disposable Fabric GameTest world',
                  'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                  'template_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                  'retained_fields': FIELDS,
                  'privacy': 'allowlist only; no Player, UUID, entities, command/boss/event state',
                  'normalization': 'stable vanilla packs/features; creative; zero seed; bedrock-only flat world; neutral time/spawn/weather'}
    output.with_suffix('.provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf8')
    print(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    capture(args.source, args.output)
