"""Closure evidence must fail on absent components or unproven occluders."""
import unittest
from unittest.mock import patch
from composite_world_oracle import EvidenceReader
from trace_composite_owner_closure import trace


class OwnerClosureTests(unittest.TestCase):
    def test_high_conflict_requires_eight_whole_source_objects(self):
        result = trace((-560,98,-9))
        self.assertEqual('HISTORICAL_OWNER_CLOSURE_WITH_OCCLUSION', result['result'])
        self.assertEqual(8, len(result['objects']))
        self.assertEqual(27, len(result['cells']))
        self.assertFalse(result['errors'])
        self.assertTrue(any(c['preservedOccluder'] for c in result['cells']))

    def test_missing_peripheral_component_prevents_closure(self):
        original = EvidenceReader.state
        def changed(reader, dimension, point):
            if dimension == 'minecraft:overworld' and tuple(point) == (-558,100,-9):
                return 'FOUND', 'minecraft:air'
            return original(reader, dimension, point)
        with patch.object(EvidenceReader, 'state', changed):
            result = trace((-560,98,-9))
        self.assertEqual('UNRESOLVED', result['result'])
        self.assertTrue(result['errors'])

    def test_opaque_cube_requires_its_own_source_attribution(self):
        original = EvidenceReader.state
        def changed(reader, dimension, point):
            if dimension == 'eh_s2:yharnam' and tuple(point) == (-559,97,-10):
                return 'FOUND', 'minecraft:air'
            return original(reader, dimension, point)
        with patch.object(EvidenceReader, 'state', changed):
            result = trace((-560,98,-9))
        self.assertEqual('UNRESOLVED', result['result'])
        self.assertTrue(result['errors'])


if __name__ == '__main__': unittest.main()
