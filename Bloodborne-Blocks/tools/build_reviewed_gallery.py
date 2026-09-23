"""Create a disposable, playable manual-placement gallery. Never reads a city."""
import argparse
import gzip
import json
from pathlib import Path

from reviewed_migration_fixture import write_fixture
from logical_contract_v2 import load_contracts
from convert_logical_world import block_pos_long, PART
from world_io import Tag, TAG_COMPOUND, TAG_STRING, TAG_INT, TAG_LONG

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
OUTPUT = ROOT / 'build/reviewed-batch-02-gallery-qa2'
FACING = ('north', 'east', 'south', 'west')
TREE_FAMILIES = ('o_c001', 'o_c009')
PLATFORM_MARGIN = 12


def _source_carriers():
    """Return the conservative source/mapping carrier universe, if recorded."""
    carriers = set()
    index = ROOT / 'docs/source-assembly-carrier-index.json.gz'
    if index.exists():
        with gzip.open(index, 'rt', encoding='utf-8') as stream:
            carriers.update(row['source']['id'] for row in json.load(stream).get('states', ()))
    # The source index says what occurs in the captured map; mapping sources
    # additionally protect a block which could become a carrier on future maps.
    from source_assembly_index import _resource_carriers
    carriers.update(_resource_carriers(ROOT))
    return carriers


def platform_block():
    carriers = _source_carriers()
    if 'minecraft:smooth_sandstone' not in carriers:
        return 'minecraft:smooth_sandstone'
    # Current source index and mapping both contain smooth_sandstone, so keep
    # the gallery's full-cube floor visually neutral and outside that universe.
    if 'minecraft:white_concrete' not in carriers:
        return 'minecraft:white_concrete'
    raise ValueError('No approved non-carrier gallery platform block is available')


def _tree_group(ident):
    for family in TREE_FAMILIES:
        if ident == family or ident.startswith(family + '_'):
            return family
    return None


def _ordered_active_families(contracts, definitions):
    hidden = set(json.loads((RES / 'hidden-items.json').read_text(encoding='utf8')))
    active = [family for family in contracts['families']
              if definitions[family['id']].get('creative', True) and family['id'] not in hidden]
    # Complete trees first; historical compatibility-only fragments are hidden.
    def key(family):
        group = _tree_group(family['id'])
        return (0, TREE_FAMILIES.index(group), family['id']) if group else (1, family['id'])
    return sorted(active, key=key)


def _state_key(definition, facing, variant=None):
    props = {**definition['default'], 'facing': facing}
    if variant is not None:
        props['variant'] = variant
    return ','.join(f'{key}={value}' for key, value in sorted(props.items())), props


def _specimen_groups(families, definitions):
    return [(family, variant) for family in families
            for variant in (definitions[family['id']]['properties'].get('variant', [None])
                            if _tree_group(family['id']) else [None])]


def _add_platform(cells, block):
    xs, ys, zs = zip(*cells)
    floor_y = min(ys) - 1
    for x in range(min(xs) - PLATFORM_MARGIN, max(xs) + PLATFORM_MARGIN + 1):
        for z in range(min(zs) - PLATFORM_MARGIN, max(zs) + PLATFORM_MARGIN + 1):
            cells.setdefault((x, floor_y, z), (block, {}))


def build(output=OUTPUT):
    if output.exists():
        raise FileExistsError('Use a fresh gallery output; never overwrite an opened world: ' + str(output))
    contracts, _ = load_contracts(RES)
    definitions = {definition['id']: definition
                   for definition in json.loads((RES / 'definitions.json').read_text(encoding='utf-8'))['blocks']}
    families = _ordered_active_families(contracts, definitions)
    cells = {(16, 64, 10): ('minecraft:air', {})}  # write_fixture derives the safe creative spawn from first cell.
    entities = []
    positions = []
    for number, (family, variant) in enumerate(_specimen_groups(families, definitions)):
        row, column = divmod(number, 4)
        tree_group = _tree_group(family['id'])
        for turn, facing in enumerate(FACING):
            key, props = _state_key(definitions[family['id']], facing, variant)
            try:
                shape = family['states'][key]['interaction_footprint']['cells']
            except KeyError as error:
                raise ValueError(f'Active family {family["id"]} lacks its {facing} default state') from error
            # 40 blocks between facings and 192 between family columns leave
            # comfortable clearance for all current visual/interaction bounds.
            origin = (64 + column * 192 + turn * 40,
                      64 - min(cell[1] for cell in shape),
                      32 + row * 64)
            owner = 'bloodborne_blocks:' + family['id']
            cells[origin] = (owner, props)
            for offset in shape:
                if offset == [0, 0, 0]:
                    continue
                point = tuple(origin[index] + offset[index] for index in range(3))
                if point in cells:
                    raise ValueError('Gallery footprint overlap')
                cells[point] = (PART, {})
                data = {'id': Tag(TAG_STRING, PART), 'Owner': Tag(TAG_STRING, owner),
                        'Root': Tag(TAG_LONG, block_pos_long(*origin))}
                data.update({name: Tag(TAG_INT, value) for name, value in zip(('x', 'y', 'z'), point)})
                entities.append(Tag(TAG_COMPOUND, data))
            entry = {'id': family['id'], 'facing': facing, 'position': origin,
                     'give': '/give @s ' + owner}
            if variant is not None:
                entry['variant'] = variant
                entry['give'] += '{BlockStateTag:{variant:"' + variant + '"}}'
            if tree_group:
                # Additive metadata preserves the existing top-level list and
                # per-specimen fields used by review scripts.
                entry.update({'gallery_section': 'complete_tree_families', 'tree_family': tree_group})
            positions.append(entry)

    floor = platform_block()
    _add_platform(cells, floor)
    # The metadata spawn is (16, 66, 6); this pad puts its surface one block
    # below the player while the broad lower platform remains below all visuals.
    for x in range(15, 18):
        for z in range(5, 8):
            cells[(x, 65, z)] = (floor, {})
    write_fixture(output, cells, block_entities=entities, floor=False,
                  level_name='Bloodborne batch-02 QA2: whole trees + debug')
    (output / 'gallery-positions.json').write_text(json.dumps(positions, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    (output / 'README.txt').write_text(
        f'Synthetic creative world, {len(families)} active families, {len(positions)} specimens; all tree variants x four facings.\n'
        f'Gallery platform: {floor}; source carriers are deliberately excluded.\n'
        'Install the checkpoint Fabric 1.20.1 JAR. Copy this folder into saves.\n'
        'Use gallery-positions.json for /tp and /give; compare fresh manual placement with each specimen.\n'
        'Aim at an object and run /bloodborne debug target (alias: /bloodborne debug). Click the report to copy.\n'
        'No city data, original source world is never modified.\n', encoding='utf8')
    print(f'{output}: {len(positions)} specimens')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    build(parser.parse_args().output)
