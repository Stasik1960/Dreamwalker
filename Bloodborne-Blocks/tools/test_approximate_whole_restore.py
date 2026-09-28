"""The opt-in mode must not weaken baseline NBT/owner guards."""
import json
import unittest
from pathlib import Path
from approximate_whole_restore import RELOCATIONS
from complete_accepted_restore import close_group
from test_complete_accepted_restore import World, OLD, TARGET, P, UP, DIM
from world_io import Tag, TAG_COMPOUND, TAG_STRING


class ApproximateTests(unittest.TestCase):
    def test_relocations_match_explicit_decision_log(self):
        path=Path(__file__).resolve().parents[1]/'docs/whole-models-handoff/decisions/APPROXIMATION.json'
        rows=json.loads(path.read_bytes())['relocations']
        self.assertEqual(RELOCATIONS,{(r['family'],tuple(r['sourceRoot'])):tuple(r['offset']) for r in rows})

    def test_opt_in_preserves_single_cell_neighbor(self):
        shapes={OLD:{(0,0,0)},TARGET:{(0,0,0),(0,1,0)}}
        defs={value[0]:{'default':{}} for value in shapes}
        seed={'ids':['qa'],'occurrences':[],'dimension':DIM,'consume':{P},'outputs':{P:TARGET}}
        world=World({P:OLD,UP:OLD})
        strict=close_group(seed,world,{}, {},shapes,defs)
        self.assertTrue(strict['errors'])
        opted=close_group(seed,world,{}, {},shapes,defs,preserve_single_cells=True)
        self.assertFalse(opted['errors'])
        self.assertEqual(OLD,opted['desired'][UP])
        entity=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'minecraft:chest')})
        blocked=close_group(seed,world,{(DIM,*UP):entity},{},shapes,defs,preserve_single_cells=True)
        self.assertIn('foreign_or_custom_block_entity',blocked['errors'])


if __name__=='__main__':unittest.main()
