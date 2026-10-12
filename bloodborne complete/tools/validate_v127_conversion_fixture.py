"""Read-only exact save/reopen proof for the small opt-in converter fixture.

The full original city is never written or opened by this validator. It checks
the native author result, the ordinary production-only server lifecycle, actual
Anvil states, typed owner/guest NBT, typed RP payload, shard dictionaries and the
restored original game rules. It cannot turn an absent native run into PASS.
"""
from pathlib import Path
import argparse, hashlib, json, uuid
from package_v127_release import ROOT, read, sha, ordinary
from world_io import RegionFile, compound, read_nbt, section_blocks, Tag


def typed(tag):
    if tag.type == 10:
        value = {key: typed(value) for key, value in sorted(tag.value.items())}
    elif tag.type == 9:
        value = [typed(value) for value in tag.value]
    elif tag.type == 7:
        value = bytes(tag.value).hex()
    else:
        value = tag.value
    return {'type': tag.type, 'listType': tag.list_type, 'value': value}


def identifier(tag):
    assert tag.type == 11 and len(tag.value) == 4
    return str(uuid.UUID(bytes=b''.join((value & 0xffffffff).to_bytes(4, 'big') for value in tag.value)))


def cell(world, position):
    x, y, z = position
    region = world / 'region' / f'r.{x >> 9}.{z >> 9}.mca'
    chunk = RegionFile(region.read_bytes()).get_chunk((x >> 4) & 31, (z >> 4) & 31)
    assert chunk is not None, position
    root = compound(chunk.nbt().root)
    sections = root.get('sections', root.get('Sections')).value
    section = next(value for value in sections if compound(value)['Y'].value == y >> 4)
    palette, indices = section_blocks(section)
    state = compound(palette[indices[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]])
    properties = {key: value.value for key, value in compound(state.get('Properties', Tag(10, {}))).items()}
    entity = next((value for value in root.get('block_entities', Tag(9, [], 10)).value
                   if [compound(value)[axis].value for axis in ('x', 'y', 'z')] == position), None)
    return {'registryId': state['Name'].value, 'properties': properties}, entity


def snapshot(world, fixture):
    result = {'owners': [], 'entities': [], 'compact': None}
    for expected in fixture['owners'] + fixture['wholeObjects']:
        state, block_entity = cell(world, expected['position'])
        assert state['registryId'] == expected['registryId'], (expected, state)
        assert all(state['properties'].get(key) == value for key, value in expected['properties'].items())
        assert block_entity is not None, expected
        values = compound(block_entity)
        resident = compound(values['resident'])
        assert identifier(resident['uuid']) == expected['instanceUuid']
        assert resident['id'].value == expected['registryId'] and resident['root'].value == expected['position']
        assert 'NoCollide' not in compound(values['payload'])
        result['owners'].append({'state': state, 'typedBlockEntity': typed(block_entity)})
    compact = fixture['compactState']
    state, block_entity = cell(world, compact['position'])
    assert state['registryId'] == compact['registryId'], (compact, state)
    assert all(state['properties'].get(key) == value for key, value in compact['properties'].items())
    assert block_entity is None, 'Native compact palette must not acquire an owner block entity'
    result['compact'] = state
    wanted = {row['instanceUuid']: row for row in fixture['entities']}
    for region in sorted((world / 'entities').glob('*.mca')):
        for chunk in RegionFile(region.read_bytes()).chunks():
            root = compound(chunk.nbt().root)
            for entity in root.get('Entities', Tag(9, [], 10)).value:
                values = compound(entity)
                uid = identifier(values['UUID']) if 'UUID' in values else None
                if uid not in wanted:
                    continue
                expected = wanted[uid]
                assert values['id'].value == expected['registryId']
                assert [value.value for value in values['Pos'].value] == [2.5, 80.0, 2.5]
                assert 'SourceLegacyPayload' in values and 'NoCollide' not in values
                result['entities'].append({'uuid': uid, 'typedEntity': typed(entity)})
    assert {row['uuid'] for row in result['entities']} == set(wanted), 'Deferred RP entity missing after native save'
    result['entities'].sort(key=lambda row: row['uuid'])
    metadata = compound(compound(read_nbt(world / 'data/bloodborne_dw_composite_owners.dat').root)['data'])
    assert metadata['OwnerShardSchema'].value >= 1 and not metadata['cells'].value
    shards = []
    for path in sorted((world / 'data').rglob('*.nbt')):
        content = read_nbt(path)
        values = compound(content.root)
        if 'ownerTable' in values and 'ownerMembership' in values:
            assert values['Dictionary'].value == 1
            shards.append({'path': path.relative_to(world).as_posix(), 'typedNbt': typed(content.root)})
    assert shards, 'No dictionary owner shards were saved'
    result['shards'] = shards
    level = compound(compound(read_nbt(world / 'level.dat').root)['Data'])
    result['rules'] = {key: value.value for key, value in compound(level['GameRules']).items()}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture-input', type=Path, required=True)
    parser.add_argument('--conversion-launch', type=Path, required=True)
    parser.add_argument('--reopen-launch', type=Path, required=True)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.fixture_input, args.conversion_launch, args.reopen_launch, args.production_jar):
        assert path.resolve().is_relative_to(ROOT / 'build') and ordinary(path)
    fixture, author, reopened = read(args.fixture_input), read(args.conversion_launch), read(args.reopen_launch)
    digest = sha(args.production_jar)
    assert author['status'] == 'PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED' and author['productionJarSha256'] == digest
    assert author['mode'] == 'FIXTURE_NATIVE_TEST'
    assert reopened['status'] == 'PASS' and reopened['artifact_sha256'] == digest and reopened['world_startup'] == 'PASS'
    assert not reopened['extraMods'], 'Reopen must use production without the converter or QA add-on'
    source = Path(author['derivedWorld'])
    target = Path(reopened['run_directory']) / 'isolated-smoke-world'
    assert source.resolve().is_relative_to(ROOT / 'build') and target.resolve().is_relative_to(ROOT / 'build')
    first, second = snapshot(source, fixture), snapshot(target, fixture)
    assert first == second, 'Native production reopen changed exact states, typed objects, guest geometry or shards'
    runtime = author['runtimeResult']
    assert runtime['committed'] == len(fixture['owners']) + len(fixture['wholeObjects']) + len(fixture['entities'])
    assert runtime['phase'] == 3 and runtime['initialOverlapPrivilegeSerialized'] is False
    expected_rules = {'doDaylightCycle': 'daylight', 'doMobSpawning': 'spawning', 'doFireTick': 'fire',
                      'doWeatherCycle': 'weather', 'randomTickSpeed': 'randomTicks'}
    original = runtime['originalRules']['minecraft:overworld']
    for key, field in expected_rules.items():
        assert first['rules'][key] == str(original[field]).lower()
    value = {'status': 'PASS_NATIVE_FIXTURE_CONVERTED_AND_PRODUCTION_REOPENED', 'productionJarSha256': digest,
             'ownerRoots': len(first['owners']), 'deferredTypedRpEntities': len(first['entities']),
             'dictionaryShards': len(first['shards']), 'nativeCompactPaletteWithoutBlockEntity': 'PASS',
             'initialOverlappingOwnersNativeInstall': 'PASS', 'exactUuidStateTypedPayloadGuestGeometryReopen': 'PASS',
             'originalGameRulesRestored': 'PASS', 'savedOverlapPrivilege': False,
             'ordinaryReplacementAfterBreak': 'SEPARATE_NATIVE_UTILITY_GAMETEST',
             'fullOriginalWorldConverted': False, 'galleryCreated': False,
             'snapshotSha256': hashlib.sha256(json.dumps(first, sort_keys=True).encode()).hexdigest(),
             'evidence': {str(path): sha(path) for path in (args.fixture_input, args.conversion_launch, args.reopen_launch)}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(value))


if __name__ == '__main__':
    main()
