#!/usr/bin/env python3
"""Fail-closed QA2 release checkpoint verifier; never creates a gallery or JAR."""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path
from xml.etree import ElementTree

import build_reviewed_gallery as gallery_builder
from convert_logical_world import PART, World, block_pos_long
from logical_contract_v2 import load_contracts
from world_io import compound, read_nbt

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
LOGICAL = RES / 'bloodborne_blocks/logical'
DEFAULT_JAR = ROOT / 'build/libs/bloodborne-blocks-2.1.0-alpha.1.jar'
DEFAULT_GALLERY = ROOT / 'build/reviewed-batch-02-gallery-qa2'
DEFAULT_REPORT = ROOT / 'docs/reviewed-batch-02-qa2-checkpoint.json'
ORIENTATION_REPORT = ROOT / 'build/test-results/contract-orientation.json'
SOURCE_REPORT = ROOT / 'build/test-results/reviewed-batch-02-source-examples.json'
TREE_REPORT = ROOT / 'build/test-results/qa2-trees.json'
GAMETEST_XML = ROOT / 'build/test-results/gametest/TEST-logical-gametest.xml'
BUILD_PROOF = ROOT / 'build/qa2-build-proof.json'
QA2_GAMETESTS = {
    'logicaltreegametests.completetreehasonetallownedcolumnandsimpleshapes',
    'logicaltreegametests.completetreeplacespicksandbreaksasoneobject',
    'logicaltreegametests.legacytreeitemsplacethecompletehiddentree',
    'logicaldebuggametests.commandtreealiasespermissionandnonplayer',
    'logicaldebuggametests.ordinaryandlogicalmasterhelperandstalehelperaresafe',
    'logicaldebuggametests.serverplayerraycastcommandsarereadonly',
}
DIMENSION = 'minecraft:overworld'
CRITICAL_RESOURCES = (
    'fabric.mod.json', 'pack.mcmeta', 'bloodborne_blocks.mixins.json',
    'bloodborne_blocks/logical/contracts-v2.json',
    'bloodborne_blocks/logical/definitions.json',
    'bloodborne_blocks/logical/geometry.json',
    'bloodborne_blocks/logical/hidden-items.json',
    'bloodborne_blocks/logical/meshes.json.gz',
    'bloodborne_blocks/logical/migration.json',
    'bloodborne_blocks/logical/transform-v2.json',
)


def _sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(part)
    return digest.hexdigest()


def _assert(condition, message):
    if not condition:
        raise AssertionError(message)


def _jar_matches_build(archive):
    compiled = ROOT / 'build/classes/java/main'
    classes = sorted(path for path in compiled.rglob('*.class') if path.is_file())
    _assert(classes, 'current compiled classes are absent')
    names = set(archive.namelist())
    for path in classes:
        member = path.relative_to(compiled).as_posix()
        _assert(member in names, f'JAR lacks compiled class: {member}')
        # Loom remaps named compile output to intermediary names in the release.
        # Presence is checked here; the complete release is hash-compared below
        # with remapJar's output, not falsely compared to unremapped bytecode.
        _assert(archive.read(member)[:4] == b'\xca\xfe\xba\xbe', f'invalid class: {member}')
    return len(classes)


def _critical_resources(archive):
    names = set(archive.namelist())
    for relative in CRITICAL_RESOURCES:
        source = RES / relative
        _assert(source.is_file(), f'critical source resource absent: {relative}')
        _assert(relative in names, f'JAR lacks critical resource: {relative}')
        if relative == 'bloodborne_blocks.mixins.json':
            expected = json.loads(source.read_bytes())
            expected.setdefault('refmap', 'bloodborne-blocks-refmap.json')
            _assert(json.loads(archive.read(relative)) == expected, 'unexpected Loom mixin config change')
        else:
            _assert(archive.read(relative) == source.read_bytes(), f'JAR resource differs from source: {relative}')
    return len(CRITICAL_RESOURCES)


def _registry_resource_manifest(archive, definitions, contracts):
    names = set(archive.namelist())
    manifest = json.loads(archive.read('fabric.mod.json'))
    _assert(manifest.get('id') == 'bloodborne_blocks', 'fabric.mod.json has unexpected mod id')
    _assert(manifest.get('entrypoints', {}).get('main'), 'fabric.mod.json lacks main entrypoint')
    ids = {row['id'] for row in definitions}
    contract_ids = {family['id'] for family in contracts['families']}
    _assert(contract_ids <= ids, 'Contract V2 references unknown logical registry IDs')
    for ident in ids:
        _assert(f'assets/bloodborne_blocks/blockstates/{ident}.json' in names,
                f'JAR lacks registry blockstate for {ident}')
    for family in contracts['families']:
        for state in family['states'].values():
            mesh = state['render_mesh']['id']
            _assert(f'assets/bloodborne_blocks/models/block/logical/{mesh}.json' in names,
                    f'JAR lacks logical mesh model {mesh}')
    return {'logical_registry_ids': len(ids), 'contract_v2_families': len(contract_ids), 'logical_blockstates': len(ids),
            'logical_mesh_models': len({state['render_mesh']['id'] for family in contracts['families']
                                        for state in family['states'].values()})}


def _report_all_passed(path):
    data = json.loads(Path(path).read_text(encoding='utf8'))
    _assert(data.get('result', 'PASS').upper() == 'PASS', f'{path.name} result is not PASS')
    rows = next((data[key] for key in ('cases', 'checks', 'trees', 'variants') if isinstance(data.get(key), list)), None)
    _assert(rows, f'{path.name} has no check rows')
    _assert(all(str(row.get('status', 'passed')).lower() == 'passed' for row in rows if isinstance(row, dict)),
            f'{path.name} contains a non-passing row')
    return len(rows)


def _test_reports():
    orientation = json.loads(ORIENTATION_REPORT.read_text(encoding='utf8'))
    _assert(orientation.get('result') == 'PASS', 'orientation report is not PASS')
    orientation_checks = int(orientation.get('rotation_checks', 0))
    _assert(orientation_checks > 0, 'orientation report has no checks')
    source = json.loads(SOURCE_REPORT.read_text(encoding='utf8'))
    cases = source.get('cases', [])
    _assert(cases and all(case.get('status') == 'passed' for case in cases), 'source-example report has failures')
    trees = _report_all_passed(TREE_REPORT)
    suite = ElementTree.parse(GAMETEST_XML)
    testcases = suite.findall('.//testcase')
    _assert(testcases, 'GameTest XML contains no testcases')
    _assert(not suite.findall('.//failure') and not suite.findall('.//error'), 'GameTest XML reports failure/error')
    _assert(QA2_GAMETESTS <= {case.get('name') for case in testcases}, 'GameTest XML lacks required QA2 tree/debug cases')
    _assert(len(testcases) >= 17, 'GameTest XML lacks baseline regression coverage')
    return {'orientation_checks': orientation_checks, 'source_examples': len(cases),
            'qa2_tree_checks': trees, 'gametests': len(testcases)}


def _proof_payload():
    # Bind the execution proof to Java, tests, tooling, authoring and critical
    # resources. The separate packaged-resource check compares every art file.
    files = set()
    for relative in ('src/main/java', 'src/gametest', 'tools'):
        files.update(path for path in (ROOT / relative).rglob('*') if path.is_file()
                     and '__pycache__' not in path.parts and path.suffix != '.pyc')
    for relative in ('build.gradle', 'settings.gradle', 'gradle.properties',
                     'docs/reviewed-batch-02-qa2-trees.json', 'docs/manual-source-assemblies.json'):
        files.add(ROOT / relative)
    files.update(RES / relative for relative in CRITICAL_RESOURCES)
    files.add(RES / 'assets/bloodborne_blocks/models/item/o_c001.json')
    files.add(RES / 'assets/bloodborne_blocks/blockstates/o_c001.json')
    return {'jar_sha256': _sha256(DEFAULT_JAR),
            'inputs': {path.relative_to(ROOT).as_posix(): _sha256(path) for path in sorted(files)},
            'reports': {path.relative_to(ROOT).as_posix(): _sha256(path)
                        for path in (ORIENTATION_REPORT, SOURCE_REPORT, TREE_REPORT, GAMETEST_XML)}}


def record_build_proof():
    """Called by qa2BuildProof only after check, logicalGameTest and remapJar."""
    _test_reports()
    BUILD_PROOF.write_text(json.dumps(_proof_payload(), sort_keys=True, indent=2) + '\n', encoding='utf8')
    print('QA2 build proof recorded after Gradle prerequisites')


def _verified_build_proof():
    _assert(BUILD_PROOF.is_file(), 'run Gradle qa2Checkpoint first; no current build/test proof')
    recorded = json.loads(BUILD_PROOF.read_text(encoding='utf8'))
    _assert(recorded == _proof_payload(), 'stale build/test proof: rerun Gradle qa2Checkpoint')
    return _sha256(BUILD_PROOF)


def _expected_gallery_rows(contracts, definitions):
    families = gallery_builder._ordered_active_families(contracts, definitions)
    expected = []
    for number, (family, variant) in enumerate(gallery_builder._specimen_groups(families, definitions)):
        row, column = divmod(number, 4)
        for turn, facing in enumerate(gallery_builder.FACING):
            key, _ = gallery_builder._state_key(definitions[family['id']], facing, variant)
            shape = family['states'][key]['interaction_footprint']['cells']
            expected.append({'id': family['id'], 'facing': facing, 'variant': variant,
                             'position': (64 + column * 192 + turn * 40,
                                          64 - min(cell[1] for cell in shape), 32 + row * 64)})
    return expected


def _verify_gallery(path, contracts, definitions):
    positions = json.loads((path / 'gallery-positions.json').read_text(encoding='utf8'))
    _assert(isinstance(positions, list), 'gallery positions must remain a list')
    expected = _expected_gallery_rows(contracts, definitions)
    actual_rows = [(row['id'], row['facing'], row.get('variant'), tuple(row['position'])) for row in positions]
    expected_rows = [(row['id'], row['facing'], row['variant'], row['position']) for row in expected]
    _assert(actual_rows == expected_rows, 'gallery positions do not match active contracts/coordinate helpers')
    tree_definition = definitions.get('o_c001')
    _assert(tree_definition is not None, 'QA2 complete tree registry is absent')
    variants = tree_definition['properties']['variant']
    tree_rows = [row for row in positions if row['id'] == 'o_c001']
    _assert(len(tree_rows) == len(variants) * len(gallery_builder.FACING), 'gallery does not include each tree variant/facing')
    _assert({row.get('variant') for row in tree_rows} == set(variants), 'gallery omits a QA2 tree variant')
    _assert(all(row.get('tree_family') == 'o_c001' and row.get('gallery_section') == 'complete_tree_families'
                for row in tree_rows), 'QA2 tree rows lack complete-tree metadata')

    defaults = {'bloodborne_blocks:' + row['id']: row['default'] for row in definitions.values()}
    world = World(path, defaults)
    entities = world.block_entities()
    families = {family['id']: family for family in contracts['families']}
    for row in positions:
        root = tuple(row['position'])
        name, properties = world.get(DIMENSION, root)
        props = dict(properties)
        _assert(name == 'bloodborne_blocks:' + row['id'], f'gallery master mismatch at {root}')
        _assert(props['facing'] == row['facing'], f'gallery facing mismatch at {root}')
        if 'variant' in row:
            _assert(props.get('variant') == row['variant'], f'gallery variant mismatch at {root}')
        key = ','.join(f'{key}={value}' for key, value in sorted(props.items()))
        for cell in families[row['id']]['states'][key]['interaction_footprint']['cells']:
            if cell == [0, 0, 0]:
                continue
            point = tuple(root[index] + cell[index] for index in range(3))
            _assert(world.get(DIMENSION, point)[0] == PART, f'gallery helper missing at {point}')
            data = compound(entities[(DIMENSION, *point)])
            _assert(data['Owner'].value == name and data['Root'].value == block_pos_long(*root),
                    f'gallery helper ownership mismatch at {point}')
    level = compound(compound(read_nbt(path / 'level.dat').root)['Data'])
    _assert(level['LevelName'].value == 'Bloodborne batch-02 QA2: whole trees + debug', 'gallery level name differs')
    spawn = (level['SpawnX'].value, level['SpawnY'].value, level['SpawnZ'].value)
    _assert(world.get(DIMENSION, (spawn[0], spawn[1] - 1, spawn[2]))[0] == 'minecraft:white_concrete',
            'gallery spawn has no white-concrete floor')
    return {'active_families': len(gallery_builder._ordered_active_families(contracts, definitions)),
            'specimens': len(positions), 'tree_variants': len(variants), 'tree_specimens': len(tree_rows),
            'floor': 'minecraft:white_concrete', 'level_metadata': 'PASS', 'helper_ownership': 'PASS'}


def verify(jar=DEFAULT_JAR, gallery=DEFAULT_GALLERY, report=DEFAULT_REPORT):
    jar, gallery, report = Path(jar), Path(gallery), Path(report)
    _assert(jar.is_file(), f'JAR not found: {jar}')
    _assert(DEFAULT_JAR.is_file() and _sha256(jar) == _sha256(DEFAULT_JAR),
            'release differs from the current remapJar output')
    _assert(gallery.is_dir(), f'gallery not found: {gallery}')
    _assert(not report.exists(), f'refusing to overwrite checkpoint report: {report}')
    proof_sha = _verified_build_proof()
    contracts, _ = load_contracts(LOGICAL)
    definitions = {row['id']: row for row in json.loads((LOGICAL / 'definitions.json').read_text(encoding='utf8'))['blocks']}
    with zipfile.ZipFile(jar) as archive:
        _assert(archive.testzip() is None, 'JAR CRC error')
        classes = _jar_matches_build(archive)
        resources = _critical_resources(archive)
        registry = _registry_resource_manifest(archive, definitions.values(), contracts)
    _assert(jar.stat().st_size < 100 * 1024 * 1024, 'JAR exceeds 100 MiB release limit')
    tests = _test_reports()
    gallery_checks = _verify_gallery(gallery, contracts, definitions)
    result = {'result': 'PASS', 'jar': str(jar), 'jar_bytes': jar.stat().st_size, 'jar_sha256': _sha256(jar),
              'jar_crc': 'PASS', 'compiled_classes_present': classes, 'remap_jar_equal': True, 'critical_resources_equal': resources,
              'registry_resource_manifest': registry, 'tests': tests, 'gallery': gallery_checks,
              'build_proof_sha256': proof_sha, 'build_proof': 'check + logicalGameTest + remapJar; current input/report/JAR hashes',
              'client_visual_acceptance': 'NOT_PERFORMED', 'full_city_conversion': False}
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, ensure_ascii=False))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=DEFAULT_JAR)
    parser.add_argument('--gallery', type=Path, default=DEFAULT_GALLERY)
    parser.add_argument('--report', type=Path, default=DEFAULT_REPORT)
    parser.add_argument('--record-build-proof', action='store_true', help='Internal Gradle qa2BuildProof step, not a standalone test command')
    args = parser.parse_args()
    if args.record_build_proof:
        record_build_proof()
    else:
        verify(args.jar, args.gallery, args.report)
