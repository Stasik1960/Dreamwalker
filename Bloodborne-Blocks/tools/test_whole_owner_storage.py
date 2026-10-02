import copy
import unittest

from build_whole_owner_runtime import rotate_geometry, storage_root_geometry


class StorageRootGeometryTests(unittest.TestCase):
    def test_missing_root_is_added_without_changing_rotated_physics(self):
        source = {'anchor': [0, 0, 0], 'render_offset': [0, 0, 0], 'metadata': {'keep': True}, 'cells': {
            '1,0,0': {'collision': [[0, 0, 0, 1, 1, 1]], 'outline': [[0, 0, 0, 1, 1, 1]]}}}
        before = copy.deepcopy(source)
        result = storage_root_geometry(source)
        self.assertEqual(source, before)
        self.assertEqual(result['cells']['0,0,0'], {'collision': [], 'outline': []})
        self.assertEqual(result['metadata'], {'keep': True})
        for rotation in range(4):
            rotated = rotate_geometry(result, rotation)
            original_rotated = rotate_geometry(before, rotation)
            nonempty = lambda cells: {key:cell for key,cell in cells.items() if cell['collision'] or cell['outline']}
            self.assertEqual(nonempty(rotated['cells']), nonempty(original_rotated['cells']))

    def test_existing_empty_root_is_preserved(self):
        source = {'render_offset': [0, 0, 0], 'cells': {'0,0,0': {'collision': [], 'outline': []}}}
        self.assertEqual(storage_root_geometry(source), source)

    def test_rejects_existing_guards(self):
        with self.assertRaisesRegex(ValueError, 'nonzero'):
            storage_root_geometry({'render_offset': [1, 0, 0], 'cells': {}})
        with self.assertRaisesRegex(ValueError, 'simplification'):
            storage_root_geometry({'render_offset': [0, 0, 0], 'cells': {'1,0,0': {'collision': [[0,0,0,1,1,1]] * 5, 'outline': []}}})


if __name__ == '__main__':
    unittest.main()
