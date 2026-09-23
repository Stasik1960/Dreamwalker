"""Verify the packaged checkpoint against sources and the synthetic gallery."""
import hashlib
import json
import zipfile
from pathlib import Path
from xml.etree import ElementTree

from convert_logical_world import World, PART, block_pos_long
from logical_contract_v2 import load_contracts
from world_io import read_nbt, compound

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
LOGICAL = RES / 'bloodborne_blocks/logical'
JAR = ROOT.parent / 'releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-batch-02-20260922-mc1.20.1.jar'
GALLERY = ROOT / 'build/reviewed-batch-02-gallery-ready'
DIM = 'minecraft:overworld'


def verify():
    contracts, _ = load_contracts(LOGICAL)
    definitions = json.loads((LOGICAL / 'definitions.json').read_text())['blocks']
    families = {f['id']: f for f in contracts['families']}
    defaults = {'bloodborne_blocks:' + d['id']: d['default'] for d in definitions}
    members = ['fabric.mod.json'] + ['bloodborne_blocks/logical/' + f for f in
        ('contracts-v2.json', 'definitions.json', 'geometry.json', 'meshes.json.gz', 'migration.json')]
    with zipfile.ZipFile(JAR) as archive:
        assert archive.testzip() is None, 'JAR CRC error'
        for member in members:
            assert archive.read(member) == (RES / member).read_bytes(), member
        assert 'dev/dreamwalker/bloodborneblocks/LogicalVariantProperty.class' in archive.namelist()
    assert JAR.stat().st_size < 100 * 1024 * 1024, 'ordinary GitHub file limit'
    orientation = json.loads((ROOT / 'build/test-results/contract-orientation.json').read_text())
    assert orientation['result'] == 'PASS' and orientation['rotation_checks'] == 912
    source = json.loads((ROOT / 'build/test-results/reviewed-batch-02-source-examples.json').read_text())
    assert len(source['cases']) == 36 and all(c['status'] == 'passed' for c in source['cases'])
    tests = ElementTree.parse(ROOT / 'build/test-results/gametest/TEST-logical-gametest.xml')
    assert len(tests.findall('.//testcase')) == 11
    assert not tests.findall('.//failure') and not tests.findall('.//error')
    positions = json.loads((GALLERY / 'gallery-positions.json').read_text())
    assert len(positions) == 100
    world = World(GALLERY, defaults)
    entities = world.block_entities()
    for row in positions:
        root = tuple(row['position']); name, properties = world.get(DIM, root)
        props = dict(properties)
        assert name == 'bloodborne_blocks:' + row['id'] and props['facing'] == row['facing']
        key = ','.join(f'{k}={v}' for k, v in sorted(props.items()))
        for cell in families[row['id']]['states'][key]['interaction_footprint']['cells']:
            if cell == [0, 0, 0]: continue
            point = tuple(root[i] + cell[i] for i in range(3))
            assert world.get(DIM, point)[0] == PART
            # Exact ownership/root is essential for pick/break in the playable fixture.
            entity = entities[(DIM, *point)]
            data = compound(entity)
            assert data['Owner'].value == name and data['Root'].value == block_pos_long(*root)
    level = compound(compound(read_nbt(GALLERY / 'level.dat').root)['Data'])
    spawn = (level['SpawnX'].value, 63, level['SpawnZ'].value)
    assert world.get(DIM, spawn)[0] == 'minecraft:bedrock'
    report = {'result': 'PASS', 'jar': str(JAR.relative_to(ROOT.parent)),
              'jar_bytes': JAR.stat().st_size, 'jar_sha256': hashlib.sha256(JAR.read_bytes()).hexdigest(),
              'jar_crc': 'PASS', 'packaged_contract_resources_equal_sources': True,
              'contract_families': len(families), 'orientation_checks': 912,
              'source_examples': 36, 'gametests': 11, 'gallery_specimens': len(positions),
              'client_visual_acceptance': 'NOT_PERFORMED', 'full_city_conversion': False}
    (ROOT / 'docs/reviewed-batch-02-checkpoint-qa.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report))


if __name__ == '__main__': verify()
