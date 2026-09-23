#!/usr/bin/env python3
"""Temporary-world checks for the reviewed placement gallery."""
import json
import sys
import tempfile
import unittest
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_reviewed_gallery as gallery
from logical_contract_v2 import load_contracts
from reviewed_migration_fixture import write_fixture
from world_io import (RegionFile, block_state_key, compound, read_nbt, section_blocks)


def block_at(world, point):
    x, y, z = point
    region_path = Path(world) / 'region' / f'r.{x // 512}.{z // 512}.mca'
    region = RegionFile.open(region_path)
    chunk = region.get_chunk((x // 16) & 31, (z // 16) & 31)
    if chunk is None:
        return 'minecraft:air'
    root = compound(chunk.nbt().root)
    for section in root['sections'].value:
        data = compound(section)
        if data['Y'].value != y // 16:
            continue
        palette_data = section_blocks(section)
        if palette_data is None:
            return 'minecraft:air'
        palette, values = palette_data
        index = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
        return block_state_key(palette[values[index]])
    return 'minecraft:air'


class ReviewedGalleryTests(unittest.TestCase):
    def test_general_fixture_keeps_bedrock_default_and_accepts_floor_override(self):
        with tempfile.TemporaryDirectory() as temp:
            world = Path(temp) / 'bedrock'
            write_fixture(world, {(0, 64, 0): ('minecraft:stone', {})})
            self.assertEqual(block_at(world, (0, 63, 0)), 'minecraft:bedrock')
            custom = Path(temp) / 'custom'
            write_fixture(custom, {(0, 64, 0): ('minecraft:stone', {})}, floor_block='minecraft:white_concrete')
            self.assertEqual(block_at(custom, (0, 63, 0)), 'minecraft:white_concrete')

    def test_gallery_is_fresh_only(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'already-opened'
            output.mkdir()
            with self.assertRaises(FileExistsError):
                gallery.build(output)

    def test_gallery_platform_spawn_and_positions(self):
        contracts, _ = load_contracts(gallery.RES)
        definitions = {row['id']: row for row in json.loads((gallery.RES / 'definitions.json').read_text(encoding='utf8'))['blocks']}
        families = gallery._ordered_active_families(contracts, definitions)
        expected = {(family['id'], facing) for family in families
                    for facing in gallery._facings(definitions[family['id']])}
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'gallery'
            gallery.build(output)
            positions = json.loads((output / 'gallery-positions.json').read_text(encoding='utf8'))
            self.assertIsInstance(positions, list)  # retain the old consumer-facing list format
            self.assertEqual({(entry['id'], entry.get('facing')) for entry in positions}, expected)
            self.assertEqual(len(positions), sum(
                len(gallery._facings(definitions[family['id']]))
                for family, _ in gallery._specimen_groups(families, definitions)))
            self.assertTrue(all(
                count == len(gallery._facings(definitions[ident]))
                for (ident, _), count in Counter(
                    (entry['id'], entry.get('variant')) for entry in positions).items()))

            trees = [entry for entry in positions if gallery._tree_group(entry['id'])]
            self.assertTrue(trees)
            self.assertTrue(all(entry['gallery_section'] == 'complete_tree_families'
                                and entry['tree_family'] == gallery._tree_group(entry['id']) for entry in trees))
            tree_indexes = [index for index, entry in enumerate(positions) if entry in trees]
            self.assertEqual(tree_indexes, list(range(len(trees))))

            platform = gallery.platform_block()
            self.assertEqual(platform, 'minecraft:white_concrete')
            # The explicit plane extends beyond the interaction footprint, not
            # merely beneath the master cells used by the positions list.
            bounds = []
            by_id = {family['id']: family for family in families}
            for entry in positions:
                key, _ = gallery._state_key(definitions[entry['id']], entry.get('facing'), entry.get('variant'))
                for offset in by_id[entry['id']]['states'][key]['interaction_footprint']['cells']:
                    bounds.append(tuple(entry['position'][index] + offset[index] for index in range(3)))
            min_x, min_y, min_z = (min(point[index] for point in bounds) for index in range(3))
            max_x, max_z = max(point[0] for point in bounds), max(point[2] for point in bounds)
            floor_y = min(64, min_y) - 1  # first metadata anchor fixes the gallery floor source minimum at Y=64
            for x, z in ((min_x - gallery.PLATFORM_MARGIN, min_z - gallery.PLATFORM_MARGIN),
                         (max_x + gallery.PLATFORM_MARGIN, max_z + gallery.PLATFORM_MARGIN)):
                self.assertEqual(block_at(output, (x, floor_y, z)), platform)

            level = compound(read_nbt(output / 'level.dat').root)['Data'].value
            spawn = (level['SpawnX'].value, level['SpawnY'].value, level['SpawnZ'].value)
            self.assertEqual(block_at(output, (spawn[0], spawn[1] - 1, spawn[2])), platform)

    def test_faceless_states_are_not_given_an_invented_facing(self):
        asymmetric = {'id': 'o_asymmetric', 'default': {'facing': 'north'},
                      'properties': {'facing': list(gallery.FACING)}}
        faceless = {'id': 'o_c471_a', 'default': {'visual': 'base'},
                    'properties': {'visual': ['base', 'alt']}}
        self.assertEqual(gallery.FACING, gallery._facings(asymmetric))
        self.assertEqual((None,), gallery._facings(faceless))
        self.assertEqual(('visual=base', {'visual': 'base'}), gallery._state_key(faceless))
        with self.assertRaises(ValueError):
            gallery._state_key(faceless, 'north')


if __name__ == '__main__':
    unittest.main()
