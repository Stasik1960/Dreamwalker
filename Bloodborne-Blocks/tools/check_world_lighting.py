"""Regression checks for the spawn-load Starlight cache corruption (no game)."""
import copy
import unittest
from world_io import *


def starlight_reads_saved_light(root):
    # SaveUtil.java in Starlight 1.1.2: existence, NOT getBoolean(isLightOn).
    return 'isLightOn' in root and root.get('starlight.light_version', Tag(TAG_INT, 0)).value == 9


class LightingChecks(unittest.TestCase):
    def fixture(self):
        return {
            'Status': Tag(TAG_STRING, 'minecraft:full'),
            'isLightOn': Tag(TAG_BYTE, 0),
            'starlight.light_version': Tag(TAG_INT, 9),
            'Heightmaps': Tag(TAG_COMPOUND, {'WORLD_SURFACE': Tag(TAG_LONG_ARRAY, [123])}),
            'block_entities': Tag(TAG_LIST, [Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, 'other:chest')})], TAG_COMPOUND),
            'sections': Tag(TAG_LIST, [Tag(TAG_COMPOUND, {
                'Y': Tag(TAG_BYTE, -5),  # Boundary light sections must also be reset.
                'starlight.skylight_state': Tag(TAG_INT, 2),
                'starlight.blocklight_state': Tag(TAG_INT, 1),
                'biomes': Tag(TAG_COMPOUND, {'palette': Tag(TAG_LIST, [Tag(TAG_STRING, 'minecraft:plains')], TAG_STRING)})
            })], TAG_COMPOUND)
        }

    def test_false_flag_still_loads_missing_initialized_starlight_array(self):
        root = self.fixture()
        self.assertTrue(starlight_reads_saved_light(root))
        self.assertEqual([(-5, 'SkyLight', 'initialized_array_missing')], lighting_cache_errors(root))
        self.assertNotIn('SkyLight', compound(root['sections'].value[0]))
        self.assertTrue(invalidate_chunk_lighting(root))
        self.assertFalse(starlight_reads_saved_light(root))
        self.assertEqual([], lighting_cache_errors(root))
        self.assertFalse(root.get('isLightOn', Tag(TAG_BYTE, 0)).value)
        self.assertNotIn('starlight.skylight_state', compound(root['sections'].value[0]))

    def test_valid_arrays_removed_without_changing_world_content(self):
        root = self.fixture()
        section = compound(root['sections'].value[0])
        section['SkyLight'] = Tag(TAG_BYTE_ARRAY, bytes([255]) * 2048)
        section['BlockLight'] = Tag(TAG_BYTE_ARRAY, bytes(2048))
        preserved = copy.deepcopy({k:v for k,v in root.items() if k not in ('isLightOn','starlight.light_version','sections')})
        biomes = copy.deepcopy(section['biomes'])
        invalidate_chunk_lighting(root)
        self.assertEqual(preserved, {k:v for k,v in root.items() if k != 'sections'})
        self.assertEqual({'Y','biomes'}, set(section))
        self.assertEqual(biomes, section['biomes'])

    def test_idempotent_and_vanilla_safe(self):
        root = self.fixture()
        del root['starlight.light_version']
        invalidate_chunk_lighting(root)
        encoded = encode_nbt(NbtFile('', Tag(TAG_COMPOUND, root)))
        self.assertFalse(invalidate_chunk_lighting(root))
        self.assertEqual(encoded, encode_nbt(NbtFile('', Tag(TAG_COMPOUND, root))))


if __name__ == '__main__':
    unittest.main()
