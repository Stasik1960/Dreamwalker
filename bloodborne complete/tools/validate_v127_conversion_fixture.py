"""Read-only exact save/reopen proof for the small opt-in converter fixture.

The full original city is never written or opened by this validator. It checks
the native author result, the ordinary production-only server lifecycle, actual
Anvil states, typed owner/guest NBT, typed RP payload, shard dictionaries and the
restored original game rules. It cannot turn an absent native run into PASS.
"""
from pathlib import Path
import argparse, base64, hashlib, json, uuid
from package_v127_release import ROOT, read, sha, ordinary
from world_io import RegionFile, compound, decode_nbt, read_nbt, section_blocks, Tag

# These engine counters can legitimately change during a normal server reopen.
# UUID/position/rotation/motion, all BB fields and opaque original NBT stay exact.
VOLATILE_ENTITY_FIELDS = {'Air', 'Fire', 'PortalCooldown', 'FallDistance', 'OnGround', 'TicksFrozen'}


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
        data = region.read_bytes()
        # Native RegionFile can create an empty file before its first save.
        # Only exact EOF is empty; any nonempty short/corrupt header still fails.
        if not data:
            continue
        for chunk in RegionFile(data).chunks():
            root = compound(chunk.nbt().root)
            for entity in root.get('Entities', Tag(9, [], 10)).value:
                values = compound(entity)
                uid = identifier(values['UUID']) if 'UUID' in values else None
                if uid not in wanted:
                    continue
                expected = wanted[uid]
                assert values['id'].value == expected['registryId']
                original = compound(decode_nbt(base64.b64decode(expected['nbtBase64'])).root)
                assert typed(values['Pos']) == typed(original['Pos'])
                assert typed(values['Rotation']) == typed(original['Rotation'])
                for key in ('SourceLegacyPayload', 'Scale', 'Open', 'DogsVisible', 'Locked', 'Links', 'VerticalOffset'):
                    if key in original:
                        assert key in values and typed(values[key]) == typed(original[key]), ('changed typed source entity field', uid, key)
                assert 'SourceLegacyPayload' in values and 'NoCollide' not in values
                functional = Tag(10, {key: value for key, value in values.items() if key not in VOLATILE_ENTITY_FIELDS})
                result['entities'].append({'uuid': uid, 'typedFunctionalEntity': typed(functional)})
    assert {row['uuid'] for row in result['entities']} == set(wanted), 'Deferred RP entity missing after native save'
    result['entities'].sort(key=lambda row: row['uuid'])
    metadata = compound(compound(read_nbt(world / 'data/bloodborne_dw_composite_owners.dat').root)['data'])
    assert metadata['OwnerShardSchema'].value >= 1 and not metadata['cells'].value
    shards = []
    collision_by_owner = {}
    compact_contacts = []
    for path in sorted((world / 'data').rglob('*.nbt')):
        content = read_nbt(path)
        values = compound(content.root)
        if 'ownerTable' in values and 'ownerMembership' in values:
            assert values['Dictionary'].value == 1
            owners = [identifier(compound(owner)['uuid']) for owner in values['ownerTable'].value]
            shapes = [compound(shape) for shape in values['shapeTable'].value]
            for row in values['cells'].value:
                row = compound(row)
                pos = row['pos'].value
                for reference in row['refs'].value:
                    owner_index, _, shape_index = reference.value
                    uid = owners[owner_index]
                    boxes = [[number.value for number in box.value] for box in shapes[shape_index]['collision'].value]
                    absolute = [[box[i] + pos[i % 3] for i in range(6)] for box in boxes]
                    collision_by_owner.setdefault(uid, []).extend(absolute)
                    if pos == list(compact['position']):
                        compact_contacts.extend(boxes)
            shards.append({'path': path.relative_to(world).as_posix(), 'typedNbt': typed(content.root)})
    assert shards, 'No dictionary owner shards were saved'
    assert len(fixture['owners']) >= 2
    first, second = (collision_by_owner[row['instanceUuid']] for row in fixture['owners'][:2])
    intersects = lambda a, b: all(min(a[i + 3], b[i + 3]) - max(a[i], b[i]) > 1e-5 for i in range(3))
    assert any(intersects(a, b) for a in first for b in second), 'Installed owners do not actually overlap physically'
    assert any(intersects(box, [0, 0, 0, 1, 1, 1]) for box in compact_contacts), 'No saved owner actually overlaps the retained compact cube'
    result['physicalOverlap'] = {'ownedPrisms': True, 'ownedPrismAndCompactCube': True}
    result['shards'] = shards
    level = compound(compound(read_nbt(world / 'level.dat').root)['Data'])
    result['levelDataVersion'] = level['DataVersion'].value
    result['rules'] = {key: value.value for key, value in compound(level['GameRules']).items()}
    result['typedRules'] = {key: typed(value) for key, value in compound(level['GameRules']).items()}
    return result


def assert_original_game_rules(fixture, runtime, first, second, fixture_directory):
    """Prepared typed source baseline, independent of the native author's claims."""
    assert fixture['preparedGameRulesCapturedBeforeNativeAuthor'] is True
    baseline = Path(fixture['preparedGameRulesPath']).resolve()
    assert baseline == fixture_directory.resolve() / 'prepared-game-rules.nbt' and ordinary(baseline)
    assert sha(baseline) == fixture['preparedGameRulesSha256'], 'Prepared independent GameRules baseline changed'
    original = compound(read_nbt(baseline).root)
    assert original, 'Prepared source GameRules baseline is empty'
    for key, value in original.items():
        expected = typed(value)
        assert first['typedRules'].get(key) == expected, ('Native author changed prepared source GameRule', key, expected, first['typedRules'].get(key))
        assert second['typedRules'].get(key) == expected, ('Production reopen changed prepared source GameRule', key, expected, second['typedRules'].get(key))
    fields = {'doDaylightCycle': 'daylight', 'doMobSpawning': 'spawning', 'doFireTick': 'fire',
              'doWeatherCycle': 'weather', 'randomTickSpeed': 'randomTicks'}
    originals = runtime['originalRules']
    assert all(dimension in originals for dimension in ('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'))
    for dimension, claimed in originals.items():
        for key, field in fields.items():
            assert key in original and original[key].type == 8
            assert str(claimed[field]).lower() == original[key].value, ('Author originalRules disagrees with independent prepared baseline', dimension, key, claimed[field], original[key].value)
    return {'source': 'INDEPENDENT_TYPED_PREPARED_NBT_BEFORE_NATIVE_AUTHOR', 'sha256': sha(baseline),
            'preparedRuleCount': len(original), 'checkedOriginalRuleDimensions': sorted(originals),
            'nativeAddedRuleKeysAllowed': sorted(set(first['typedRules']) - set(original))}


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
    assert fixture['productionJarSha256'] == digest, 'Reprepare the small fixture against the final accepted combined JAR'
    assert author['status'] == 'PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED' and author['productionJarSha256'] == digest
    assert author['mode'] == 'FIXTURE_NATIVE_TEST'
    assert reopened['status'] == 'PASS' and reopened['artifact_sha256'] == digest and reopened['world_startup'] == 'PASS'
    assert not reopened['extraMods'], 'Reopen must use production without the converter or QA add-on'
    source = Path(author['derivedWorld'])
    target = Path(reopened['run_directory']) / 'isolated-smoke-world'
    assert source.resolve().is_relative_to(ROOT / 'build') and target.resolve().is_relative_to(ROOT / 'build')
    first, second = snapshot(source, fixture), snapshot(target, fixture)
    assert first == second, 'Native production reopen changed exact states, typed objects, guest geometry or shards'
    if fixture.get('levelTemplateSource') == 'IMMUTABLE_ORIGINAL_ZIP_LEVEL_ONLY':
        assert fixture['sourceLevelDataVersion'] == fixture['fixtureChunkDataVersion'] == 2975
        assert first['levelDataVersion'] >= 3465 and second['levelDataVersion'] >= 3465, 'Original level DataFixer did not upgrade saved level metadata'
    runtime = author['runtimeResult']
    assert runtime['committed'] == len(fixture['owners']) + len(fixture['wholeObjects']) + len(fixture['entities'])
    assert runtime['phase'] == 3 and runtime['initialOverlapPrivilegeSerialized'] is False
    rule_proof = assert_original_game_rules(fixture, runtime, first, second, args.fixture_input.parent)
    value = {'status': 'PASS_NATIVE_FIXTURE_CONVERTED_AND_PRODUCTION_REOPENED', 'productionJarSha256': digest,
             'levelTemplateSource': fixture.get('levelTemplateSource', 'KNOWN_FLAT_TEST_LEVEL_ONLY'),
             'sourceLevelDataVersion': fixture.get('sourceLevelDataVersion'),
             'fixtureChunkDataVersionBeforeDataFixer': fixture.get('fixtureChunkDataVersion'),
             'sourceLevelDataFixerReopen': 'PASS' if fixture.get('levelTemplateSource') == 'IMMUTABLE_ORIGINAL_ZIP_LEVEL_ONLY' else 'NOT_RUN_ORIGINAL_SCHEMA',
             'ownerRoots': len(first['owners']), 'deferredTypedRpEntities': len(first['entities']),
             'dictionaryShards': len(first['shards']), 'nativeCompactPaletteWithoutBlockEntity': 'PASS',
             'initialOverlappingOwnersNativeInstall': 'PASS', 'exactUuidStateTypedPayloadGuestGeometryReopen': 'PASS',
             'originalGameRulesRestored': 'PASS', 'savedOverlapPrivilege': False,
             'originalGameRulesIndependentBaseline': rule_proof,
             'volatileVanillaEntityCountersExcludedFromFunctionalComparison': sorted(VOLATILE_ENTITY_FIELDS),
             'ordinaryReplacementAfterBreak': 'SEPARATE_NATIVE_UTILITY_GAMETEST',
             'fullOriginalWorldConverted': False, 'galleryCreated': False,
             'snapshotSha256': hashlib.sha256(json.dumps(first, sort_keys=True).encode()).hexdigest(),
             'evidence': {str(path): sha(path) for path in (args.fixture_input, args.conversion_launch, args.reopen_launch, Path(fixture['preparedGameRulesPath']))}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(value))


if __name__ == '__main__':
    main()
