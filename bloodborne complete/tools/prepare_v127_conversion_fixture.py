"""Prepare a tiny independent converter fixture; never convert/copy original city.

Only level.dat is read from a known flat test world or immutable original ZIP.
The --original-level-template option retains its old level/worldgen schema and
uses source DataVersion for the tiny new chunk, without copying original terrain.
The new fixture
has a fresh air/stone chunk, palette cube, overlapping deferred NEW owners,
an original native wall, a whole original door, and typed RP entity data.
Root runs the separately compiled author and production reopen checks.
"""
import argparse
import base64
import copy
import gzip
import json
import math
from pathlib import Path
import uuid
import zipfile
from package_v127_release import ROOT, REPO, SOURCE_HASHES, sha, read, ordinary
from world_io import *


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def props(registry):
    state = read(ROOT / 'src/architecture/resources/assets/bloodborne_dw/blockstates' / (registry.split(':')[1] + '.json'))
    if 'variants' not in state:
        # Native/manual multipart palettes use the registered default state.
        # Compact mode is explicitly added by the calling fixture row.
        return {}
    for key in state['variants']:
        values = dict(p.split('=', 1) for p in key.split(',') if '=' in p)
        if all(values.get(k, v) == v for k, v in {'rotation': '0', 'variant': '0', 'profile': 'base', 'open': 'false', 'art_bank': '0', 'compact': 'false'}.items()):
            return values
    raise ValueError('No default source fixture state for ' + registry)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    templates = p.add_mutually_exclusive_group(required=True)
    templates.add_argument('--level-template', type=Path)
    templates.add_argument('--original-level-template', action='store_true')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--production-jar', type=Path, required=True)
    a = p.parse_args()
    assert ordinary(a.output), 'Reparsed fixture destination rejected'
    output = a.output.resolve()
    assert output.is_relative_to(ROOT / 'build') and not output.exists()
    if a.original_level_template:
        template = REPO / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v16_map.zip'
        assert ordinary(template) and sha(template) == SOURCE_HASHES['map'], 'Immutable original map ZIP changed'
        with zipfile.ZipFile(template) as archive:
            names = [name for name in archive.namelist() if Path(name).name == 'level.dat']
            assert len(names) == 1, 'Original level.dat root is ambiguous'
            level_bytes = archive.read(names[0])
        level = decode_nbt(gzip.decompress(level_bytes))
        template_sha = sha(template)
    else:
        template = a.level_template
        assert ordinary(template) and template.resolve().is_relative_to(ROOT / 'build') and template.name == 'level.dat'
        template_sha = sha(template)
        level = read_nbt(template)
    output.mkdir(parents=True)
    world, plan = output / 'world', output / 'runtime-plan'
    world.mkdir(); plan.mkdir(); (world / 'region').mkdir()
    data = compound(compound(level.root)['Data'])
    source_version = data['DataVersion'].value
    assert not a.original_level_template or source_version == 2975, 'Unexpected original level schema version'
    preserved_level = copy.deepcopy(data)
    if not a.original_level_template:
        data['LevelName'] = Tag(TAG_STRING, 'V12.7 isolated conversion fixture')
    for key in ['Player', 'CustomBossEvents']:
        data.pop(key, None)
    for key, value in [('SpawnX', 0), ('SpawnY', 80), ('SpawnZ', 0)]:
        data[key] = Tag(TAG_INT, value)
    if not a.original_level_template:
        data['GameType'] = Tag(TAG_INT, 1)
        data['allowCommands'] = Tag(TAG_BYTE, 1)
    data['DataPacks'] = Tag(TAG_COMPOUND, {'Enabled': Tag(TAG_LIST, [Tag(TAG_STRING, 'vanilla'), Tag(TAG_STRING, 'fabric')], TAG_STRING),
                                        'Disabled': Tag(TAG_LIST, [], TAG_STRING)})
    # Deferred source function callbacks are disabled in the isolated fixture.
    data.pop('ScheduledEvents', None)
    controlled_level_fields = {'LevelName', 'Player', 'CustomBossEvents', 'SpawnX', 'SpawnY', 'SpawnZ', 'GameType', 'allowCommands', 'DataPacks', 'ScheduledEvents'}
    assert all(data.get(key) == value for key, value in preserved_level.items() if key not in controlled_level_fields), 'Source level/worldgen tags changed outside fixture controls'
    write_nbt(world / 'level.dat', level)
    root = ROOT / 'src/architecture/resources/bloodborne_dw'
    collision = read(root / 'imported-collision-v125.json')
    candidate = None
    for registry, poses in collision['objects'].items():
        path = root / 'composite' / (registry.split(':')[1] + '.json')
        if not path.is_file():
            continue
        spec = read(path)
        if spec.get('support', {}).get('required') or not registry.startswith('bloodborne_dw:source_'):
            continue
        boxes = collision['shapes'][poses[0]['closedBase']][0]['collision']
        for box in boxes:
            span = [(box['to'][i] - box['from'][i]) / 16 for i in range(3)]
            axis = next((i for i, size in enumerate(span) if 1.05 < size < 5), None)
            if axis is not None:
                candidate = (registry, boxes, axis)
                break
        if candidate:
            break
    assert candidate, 'Need a simple NEW prism that overlaps after one-cell displacement'
    registry, boxes, axis = candidate
    origin = [2, 80, 2]
    second = origin.copy(); second[axis] += 1
    roots = {tuple(origin), tuple(second)}
    cells = set()
    for base in [origin, second]:
        for box in boxes:
            lo = [math.floor(base[i] + box['from'][i] / 16 + 1e-8) for i in range(3)]
            hi = [math.ceil(base[i] + box['to'][i] / 16 - 1e-8) for i in range(3)]
            for x in range(lo[0], hi[0]):
                for y in range(lo[1], hi[1]):
                    for z in range(lo[2], hi[2]):
                        cells.add((x, y, z))
    compact_position = next(pos for pos in sorted(cells - roots) if all(0 <= pos[i] <= 15 for i in [0, 2]))
    compact = next(row for row in read(root / 'imported-compact-v127.json')['types'] if 0 in row['opaquePoses'])
    compact_state = props(compact['registryId']); compact_state['compact'] = 'true'
    uid = lambda key: str(uuid.uuid5(uuid.NAMESPACE_URL, 'dreamwalker-v127-conversion-fixture/' + key))
    owner = lambda key, reg, pos: {'dimension': 'minecraft:overworld', 'position': pos, 'registryId': reg,
                                  'instanceUuid': uid(key), 'properties': props(reg), 'payload': {}}
    normal = [owner('wide-first', registry, origin), owner('wide-second', registry, second)]
    wall = next(row['registryId'] for row in read(root / 'debug_catalogue.json')['entries'] if row['temporaryId'] == '90002')
    normal.append(owner('native-wall', wall, [12, 80, 2]))
    whole = [owner('whole-door', 'bloodborne_dw:prototype_double_door', [8, 80, 8])]
    entity_uuid = uuid.UUID(uid('rp-chair'))
    ints = [(entity_uuid.int >> shift) & 0xffffffff for shift in [96, 64, 32, 0]]
    ints = [v - (1 << 32) if v >= (1 << 31) else v for v in ints]
    nbt = NbtFile('', Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, 'bloodborne_rp:chair'), 'UUID': Tag(TAG_INT_ARRAY, ints),
                  'Pos': Tag(TAG_LIST, [Tag(TAG_DOUBLE, v) for v in [2.5, 80., 2.5]], TAG_DOUBLE),
                  'Rotation': Tag(TAG_LIST, [Tag(TAG_FLOAT, 90.), Tag(TAG_FLOAT, 0.)], TAG_FLOAT),
                  'Open': Tag(TAG_BYTE, 0), 'Scale': Tag(TAG_FLOAT, 1.),
                  'SourceLegacyPayload': Tag(TAG_COMPOUND, {'Schema': Tag(TAG_INT, 1), 'Original': Tag(TAG_COMPOUND,
                      {'id': Tag(TAG_STRING, 'bloodborne:chair'), 'CanUpdate': Tag(TAG_BYTE, 1), 'EntityState': Tag(TAG_INT, 0)})})}))
    entities = [{'dimension': 'minecraft:overworld', 'position': [2, 80, 2], 'registryId': 'bloodborne_rp:chair',
                 'instanceUuid': str(entity_uuid), 'sourceId': 'bloodborne:chair', 'nbtBase64': base64.b64encode(encode_nbt(nbt)).decode('ascii')}]
    for name, rows in [('owners.jsonl.gz', normal), ('whole-objects.jsonl.gz', whole), ('entities.jsonl.gz', entities)]:
        with gzip.open(plan / name, 'wt', encoding='utf8') as stream:
            for row in rows:
                stream.write(json.dumps(row, separators=(',', ':')) + '\n')
    sections = []
    for sy in range(-4, 20):
        palette = [Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, 'minecraft:air')})]
        indices = [0] * 4096
        if sy == 3:
            palette.append(Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, 'minecraft:stone')}))
            indices[15 * 256:16 * 256] = [1] * 256
        if sy == compact_position[1] >> 4:
            compact_tag = Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, compact['registryId']), 'Properties': Tag(TAG_COMPOUND,
                                              {key: Tag(TAG_STRING, value) for key, value in compact_state.items()})})
            palette.append(compact_tag)
            x, y, z = compact_position
            indices[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] = len(palette) - 1
        states = {'palette': Tag(TAG_LIST, palette, TAG_COMPOUND)}
        if len(palette) > 1:
            states['data'] = Tag(TAG_LONG_ARRAY, pack_palette_indices(indices, len(palette)))
        sections.append(Tag(TAG_COMPOUND, {'Y': Tag(TAG_BYTE, sy), 'block_states': Tag(TAG_COMPOUND, states),
                          'biomes': Tag(TAG_COMPOUND, {'palette': Tag(TAG_LIST, [Tag(TAG_STRING, 'minecraft:plains')], TAG_STRING)})}))
    chunk = NbtFile('', Tag(TAG_COMPOUND, {'DataVersion': Tag(TAG_INT, source_version if a.original_level_template else 3465), 'xPos': Tag(TAG_INT, 0), 'zPos': Tag(TAG_INT, 0),
             'yPos': Tag(TAG_INT, -4), 'Status': Tag(TAG_STRING, 'full' if a.original_level_template else 'minecraft:full'), 'LastUpdate': Tag(TAG_LONG, 0),
             'InhabitedTime': Tag(TAG_LONG, 0), 'sections': Tag(TAG_LIST, sections, TAG_COMPOUND),
             'block_entities': Tag(TAG_LIST, [], TAG_COMPOUND), 'Heightmaps': Tag(TAG_COMPOUND, {})}))
    region = RegionFile(); region.set_chunk(0, 0, chunk, timestamp=0); region.save(world / 'region/r.0.0.mca')
    manifest = {'schemaVersion': 1, 'status': 'PASS_COMPLETE_SOURCE_PLAN', 'knownVisibleGaps': 0,
                'sourceHashes': SOURCE_HASHES, 'fixtureOnly': True, 'fullWorldConverted': False,
                'runtimeFiles': {path.name: sha(path) for path in plan.glob('*.jsonl.gz')}}
    dump(plan / 'manifest.json', manifest)
    operation = str(uuid.uuid4())
    dump(world / 'dw-derived-copy.json', {'schemaVersion': 1, 'status': 'PASS_DERIVED_COPY_PREPARED',
         'operationId': operation, 'sourceHashes': SOURCE_HASHES, 'fixtureOnly': True,
         'originalArchivesWritten': False, 'sourceOwnersAndConsumedClearedBeforeMinecraft': True})
    report = {'status': 'PASS_PREPARED_RUNTIME_NOT_RUN', 'productionJarSha256': sha(a.production_jar),
              'world': str(world), 'runtimePlan': str(plan), 'owners': normal, 'wholeObjects': whole,
              'entities': entities, 'compactState': {'registryId': compact['registryId'], 'properties': compact_state, 'position': compact_position},
              'physicalOverlapExpected': True, 'sourceRootsAirBeforeRuntime': True, 'levelTemplateUnchanged': sha(template) == template_sha,
              'levelTemplateSource': 'IMMUTABLE_ORIGINAL_ZIP_LEVEL_ONLY' if a.original_level_template else 'KNOWN_FLAT_TEST_LEVEL_ONLY',
              'sourceLevelDataVersion': source_version, 'sourceLevelWorldgenTagsPreserved': True,
              'fixtureChunkDataVersion': source_version if a.original_level_template else 3465,
              'sourceProgramsExecuted': False, 'sourceScheduledEventsDisabled': True,
              'originalCityConverted': False, 'galleryCreated': False, 'nativeJavaRun': 'NOT_RUN'}
    dump(output / 'fixture-input.json', report)
    print(json.dumps({key: value for key, value in report.items() if key not in ['owners', 'wholeObjects', 'entities']}))


if __name__ == '__main__':
    main()
