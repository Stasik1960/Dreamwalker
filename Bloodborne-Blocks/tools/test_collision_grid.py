"""Lossless cell storage, refusal paths, and complete bounded resource coverage."""
import copy
import unittest

from normalize_support_contracts import audit_grid, union_volume
from sync_reviewed_geometry import normalize_collision_cells, split_collision


def fixture(boxes):
    return {'anchor':[0,0,0], 'render_offset':[4,-3,2], 'identity':'unchanged',
            'cells':{'0,0,0':{'collision':boxes,'outline':[[0,0,0,1,1,1]]},
                     '1,0,0':{'collision':[],'outline':[]},
                     '0,1,0':{'collision':[],'outline':[]}}}


MASK=[[0,0,0],[1,0,0],[0,1,0]]


class CollisionGridTests(unittest.TestCase):
    def test_cross_cell_split_preserves_union_identity_and_empty_helper(self):
        original=fixture([[.25,0,0,1.75,1,1],[.75,.25,0,1.25,.75,1]])
        snapshot=copy.deepcopy(original)
        result,reason=normalize_collision_cells(original,MASK)
        self.assertEqual('NORMALIZED',reason)
        self.assertEqual(snapshot,original)
        self.assertEqual(set(original['cells']),set(result['cells']))
        self.assertEqual([],result['cells']['0,1,0']['collision'])
        self.assertEqual(original['anchor'],result['anchor'])
        self.assertEqual(original['render_offset'],result['render_offset'])
        self.assertEqual(original['identity'],result['identity'])
        after=sum(union_volume(data['collision']) for data in result['cells'].values())
        self.assertAlmostEqual(union_volume(original['cells']['0,0,0']['collision']),after)
        for name,data in result['cells'].items():
            self.assertEqual(original['cells'][name]['outline'],data['outline'])
            for box in data['collision']:self.assertTrue(all(0<=v<=1 for v in box))
        again,status=normalize_collision_cells(result,MASK)
        self.assertEqual('ALREADY_CELL_LOCAL',status)
        self.assertEqual(result,again)

    def test_outside_mask_never_trims_or_expands_even_positive_sliver(self):
        for high in (2.0,1.00000001):
            original=fixture([[0,0,0,high,1,1]])
            del original['cells']['1,0,0']
            result,reason=normalize_collision_cells(original,[[0,0,0],[0,1,0]])
            self.assertIs(original,result)
            self.assertIn('COLLISION_OUTSIDE_PHYSICAL_MASK',reason)

    def test_protected_unproven_and_mismatched_masks_remain_exact(self):
        state=fixture([[0,0,0,2,1,1]])
        for mask,protected,expected in ((MASK,True,'PROTECTED'),(None,False,'UNPROVEN_PHYSICAL_MASK'),
                                        ([[0,0,0]],False,'APPROVED_MASK_DIFFERS_FROM_EXISTING_CELLS')):
            result,reason=normalize_collision_cells(state,mask,protected=protected)
            self.assertIs(state,result)
            self.assertEqual(expected,reason)

    def test_invalid_or_nonpositive_primitive_is_refused_unchanged(self):
        for box in ([0,0,0,0,1,1],[0,0,0,float('nan'),1,1],[0,0,0,1,1]):
            state=fixture([box]);result,reason=normalize_collision_cells(state,MASK)
            self.assertIs(state,result)
            self.assertNotEqual('NORMALIZED',reason)

    def test_empty_collision_keeps_all_declared_cells(self):
        self.assertEqual({'0,0,0':[],'1,0,0':[],'0,1,0':[]},split_collision([],MASK))

    def test_every_resource_state_is_audited_without_world_or_art_mutation(self):
        report=audit_grid()
        self.assertEqual('PASS',report['status'],report['errors'][:5])
        self.assertEqual([],report['needs_normalization'])
        self.assertEqual(0,report['summary']['resource_states_changed'])
        self.assertEqual(49,report['summary']['protected_logical_families'])
        self.assertTrue(all(row['union_preserved'] for row in report['logical_states']))
        self.assertEqual(report['summary']['city_states'],sum(len(row['states']) for row in report['city_normalization_skips']))
        self.assertTrue(all(row['unchanged'] and row['reason'] for row in report['city_normalization_skips']))


if __name__=='__main__':unittest.main()
