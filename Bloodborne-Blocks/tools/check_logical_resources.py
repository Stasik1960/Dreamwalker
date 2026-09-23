"""Independent static contract/resource checks for additive logical objects.

Does not import the generator, touch a world, or start the Minecraft client.
"""
import argparse
import gzip
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
NS = 'bloodborne_blocks:'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def key(properties):
    return ','.join(k + '=' + v for k, v in sorted(properties.items()))


def short(ident):
    return ident.removeprefix(NS)


def validate(resources=RES):
    root = resources / 'bloodborne_blocks'
    logical = root / 'logical'
    definitions = {}
    for relative in ('definitions.json', 'v2/definitions.json', 'logical/definitions.json'):
        for block in read(root / relative)['blocks']:
            assert block['id'] not in definitions, 'duplicate registry ' + block['id']
            definitions[block['id']] = block
    new = read(logical / 'definitions.json')['blocks']
    geometry = read(logical / 'geometry.json')
    with gzip.open(logical / 'meshes.json.gz', 'rt', encoding='utf-8') as stream:
        meshes = json.load(stream)
    texture_count = set()
    state_count = 0
    maximum_cells = 0
    for block in new:
        ident = block['id']
        assert ident.startswith('o_') and block['logical'] and not block.get('modular', False)
        assert block.get('creative', False), 'logical object absent from creative inventory: ' + ident
        assert not block.get('extra_facing', False) or 'facing' in block['properties'], 'extra_facing without facing: ' + ident
        if block['behavior'] == 'connected':
            assert block.get('connection_family'), 'connected family must be explicit: ' + ident
        expected = {key(dict(zip(block['properties'], values))) for values in itertools.product(*block['properties'].values())}
        assert set(block['states']) == expected, 'incomplete state cartesian product: ' + ident
        assert set(block['models']) == expected, 'model-state mismatch: ' + ident
        assert key(block['default']) in expected, 'default not registered: ' + ident
        actual = read(resources / 'assets/bloodborne_blocks/blockstates' / (ident + '.json'))
        assert set(actual.get('variants', {})) == expected, 'blockstate asset mismatch: ' + ident
        item = read(resources / 'assets/bloodborne_blocks/models/item' / (ident + '.json'))
        placement = block.get('placement_properties', {})
        assert all(name in block['properties'] and value in block['properties'][name]
                   for name, value in placement.items()), 'invalid placement properties: ' + ident
        item_state = {**block['default'], **placement}
        assert item['parent'] == NS + 'block/logical/' + block['models'][key(item_state)], 'item selects wrong mesh: ' + ident
        assert 'gui' in item.get('display', {}), 'missing fitted inventory transform: ' + ident
        loot = read(resources / 'data/bloodborne_blocks/loot_tables/blocks' / (ident + '.json'))
        drops = [entry.get('name') for pool in loot['pools'] for entry in pool['entries']]
        expected_drops = [NS + ident]
        if block.get('attachment_item'):
            attached = block['attachment_item']
            assert attached in definitions and definitions[attached]['logical'], 'unknown attachment item'
            assert block['placement_properties'].get('lantern') == 'false', 'attachment cannot be cloned by placement'
            assert block['properties'].get('lantern') == ['false', 'true'], 'invalid attachment states'
            expected_drops.append(NS + attached)
            assert {'condition': 'minecraft:block_state_property', 'block': NS + ident,
                    'properties': {'lantern': 'true'}} in loot['pools'][1]['conditions'], 'unconditional attachment drop'
        assert drops == expected_drops, 'logical object must drop one own item and only its installed attachment: ' + ident
        for state in expected:
            mesh = block['models'][state]
            assert mesh in meshes, 'missing mesh ' + mesh
            application = actual['variants'][state]
            assert application['model'] == NS + 'block/logical/' + mesh
            assert not application.get('x', 0) and not application.get('y', 0), 'logical mesh rotated twice'
            model = read(resources / 'assets/bloodborne_blocks/models/block/logical' / (mesh + '.json'))
            declared = set(model['textures'].values())
            profile = geometry['blocks'][ident]['states'][state]
            if 'ref' in profile:
                profile = geometry['profiles'][profile['ref']]
            maximum_cells = max(maximum_cells, len(profile['cells']))
            assert len(profile['anchor']) == 3
            for position, cell in profile['cells'].items():
                assert len(position.split(',')) == 3
                for kind in ('outline', 'collision'):
                    for box in cell.get(kind, []):
                        assert len(box) == 6 and all(math.isfinite(v) and 0 <= v <= 1 for v in box)
                        assert all(box[i] < box[i + 3] for i in range(3)), 'empty shape: ' + ident
            for polygon in meshes[mesh]['polygons']:
                texture = polygon['texture']
                assert texture in declared, 'texture not stitched: ' + texture
                texture_count.add(texture)
                namespace, path = texture.split(':', 1)
                if namespace != 'minecraft':
                    assert (resources / 'assets' / namespace / 'textures' / (path + '.png')).is_file(), 'missing texture ' + texture
                assert len(polygon['vertices']) >= 3
                for vertex in polygon['vertices']:
                    assert len(vertex) == 5 and all(math.isfinite(v) for v in vertex)
                    assert all(-64 <= v <= 64 for v in vertex[:3]) and all(0 <= v <= 16 for v in vertex[3:])
            state_count += 1
    migration = read(logical / 'migration.json')
    assert migration['schemaVersion'] == 1
    rules = {}
    for rule in migration['rules']:
        for part in [rule['source'], rule['target'], *rule.get('members', []), *(rule.get('components') or [])]:
            ident = short(part['id'])
            assert ident in definitions, 'unknown migration registry ' + ident
            definition = definitions[ident]
            state = key({**definition['default'], **part.get('properties', {})})
            assert state in definition['states'], 'unknown migration state ' + ident + '[' + state + ']'
        source = (short(rule['source']['id']), key(rule['source']['properties']))
        target = (short(rule['target']['id']), key(rule['target']['properties']), tuple(rule.get('offset', (0, 0, 0))))
        for previous_target, previous_rule in rules.get(source, []):
            if previous_target != target:
                # Only explicit complete-member assemblies may override a
                # fallback. World conversion separately proves all members,
                # affected-cell containment and absence of competing winners.
                def dominates(large, small):
                    return bool(large.get('members')) and short(small['target']['id']) in large.get('supersedes_targets', [])
                assert dominates(rule, previous_rule) or dominates(previous_rule, rule), 'ambiguous source: ' + str(source)
        rules.setdefault(source, []).append((target, rule))
        for superseded in rule.get('supersedes_targets', []):
            assert superseded in definitions and definitions[superseded].get('logical'), 'unknown superseded target'
        assert short(rule['target']['id']).startswith('o_')
        assert len(rule.get('offset', [])) == 3
        components = rule.get('components')
        if components is not None:
            assert components, 'empty components is not evidence for inverse conversion'
            positions = [tuple(c['offset']) for c in components]
            assert len(positions) == len(set(positions)), 'two components occupy same cell'
    hidden = read(logical / 'hidden-items.json')
    assert len(hidden) == len(set(hidden))
    inventory_aliases = migration.get('item_aliases', {})
    for ident, target in inventory_aliases.items():
        assert ident in definitions and definitions[ident]['logical'] and ident in hidden
        assert target['id'] in definitions and definitions[target['id']]['logical']
        assert target['id'] not in inventory_aliases, 'inventory alias chain/cycle'
        assert key(target['properties']) in definitions[target['id']]['states']
    assert all(short(ident) in definitions and
               (not short(ident).startswith('o_') or short(ident) in inventory_aliases) for ident in hidden)
    return {'ok': True, 'logicalObjects': len(new), 'states': state_count, 'meshes': len(meshes),
            'textures': len(texture_count), 'migrationRules': len(migration['rules']),
            'hiddenLegacyItems': len(hidden), 'maximumFootprintCells': maximum_cells}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=RES)
    args = parser.parse_args()
    print(json.dumps(validate(args.resources), ensure_ascii=False))
