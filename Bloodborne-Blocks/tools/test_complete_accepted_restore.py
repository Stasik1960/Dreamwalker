"""Negative whole-owner preflight cases, without a successful-ledger fixture."""
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
from collections import defaultdict
from complete_accepted_restore import AIR, PART, close_group, closed_groups, complete_current_owner
from accepted_objects_restore import bindings, helper_tag
from convert_logical_world import block_pos_long
from world_io import TAG_COMPOUND, TAG_INT, TAG_STRING, Tag

DIM = 'minecraft:overworld'
OLD = ('bloodborne_blocks:city_old', ())
TARGET = ('bloodborne_blocks:o_target', ())
GUEST = ('bloodborne_blocks:o_guest', ())
P = (0,64,0)
UP = (0,65,0)
SIDE = (1,64,0)


class World:
    def __init__(self, cells, ticks=(), height=(-64,320)):
        self.cells = cells
        self.ticks = set(ticks)
        self.height = height
    def get(self, dim, point):
        return self.cells.get(point, AIR)
    def ticks_at(self, dim, points):
        return self.ticks & set(points)
    def build_height(self, dim):
        return self.height


def helper(point, root, name=TARGET[0]):
    return helper_tag(point, {'Root':block_pos_long(*root), 'Owner':name})


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.shapes = {OLD: {(0,0,0)}, TARGET: {(0,0,0),(0,1,0)}, GUEST: {(0,0,0),(-1,0,0)}}
        self.defs = {v[0]: {'default':{}} for v in self.shapes}
        self.seed = {'ids':['test'], 'occurrences':[], 'dimension':DIM, 'consume':{P}, 'outputs':{P:TARGET}}

    def close(self, world=None, entities=None, owners=None, seed=None):
        return close_group(seed or self.seed, world or World({P:OLD}), entities or {}, owners or {}, self.shapes, self.defs)

    def test_complete_new_owner(self):
        g = self.close()
        self.assertEqual([], g['errors'])
        self.assertEqual(TARGET, g['desired'][P])
        self.assertEqual((PART,()), g['desired'][UP])
        self.assertEqual([(TARGET[0],P)], bindings(g['helpers'][UP]))

    def test_foreign_block_entity_refuses_even_when_source_matches(self):
        entity = Tag(TAG_COMPOUND, {'id':Tag(TAG_STRING,'minecraft:chest'), 'LootTable':Tag(TAG_STRING,'test:keep')})
        self.assertIn('foreign_or_custom_block_entity', self.close(entities={(DIM,*P):entity})['errors'])

    def test_scheduled_tick_refuses_whole_group(self):
        self.assertIn('scheduled_tick_at_target', self.close(World({P:OLD},ticks={UP}))['errors'])

    def test_build_height_refuses_expanded_upper_helper(self):
        self.assertIn('target_outside_dimension_build_height', self.close(World({P:OLD},height=(-64,65)))['errors'])

    def test_unrelated_terrain_is_never_consumed(self):
        w = World({P:OLD,UP:('minecraft:diamond_block',())})
        self.assertTrue(any(e.startswith('foreign_block_in_target_footprint:') for e in self.close(w)['errors']))
        self.assertEqual(('minecraft:diamond_block',()), w.get(DIM,UP))

    def test_current_root_does_not_prove_missing_helper(self):
        self.assertFalse(complete_current_owner(World({P:TARGET}), {}, {}, DIM,P,TARGET,self.shapes,self.defs))
        self.assertTrue(complete_current_owner(World({P:TARGET,UP:(PART,())}), {(DIM,*UP):helper(UP,P)},
            {(DIM,TARGET[0],P):{UP}}, DIM,P,TARGET,self.shapes,self.defs))

    def test_incomplete_guest_refuses(self):
        entity = helper(P,SIDE,GUEST[0])
        result = self.close(World({P:OLD,SIDE:GUEST}), {(DIM,*P):entity})
        self.assertIn('incomplete_current_dependency_owner',result['errors'])

    def test_complete_guest_survives_root_replacement(self):
        entity = helper(P,SIDE,GUEST[0])
        result = self.close(World({P:OLD,SIDE:GUEST}), {(DIM,*P):entity}, {(DIM,GUEST[0],SIDE):{P}})
        self.assertEqual([],result['errors'])
        self.assertEqual(GUEST,result['desired'][SIDE])
        self.assertEqual([(GUEST[0],SIDE)],bindings(result['helpers'][P]))

    def test_consumed_helper_cell_does_not_authorize_foreign_root_removal(self):
        self.shapes[GUEST] = {(0,0,0)}
        seed = {**self.seed,'consume':{P,UP}}
        entity = helper(UP,P)
        result = self.close(World({P:TARGET,UP:GUEST}),{(DIM,*UP):entity},
            {(DIM,TARGET[0],P):{UP}},seed=seed)
        self.assertEqual([],result['errors'])
        self.assertEqual(GUEST,result['desired'][UP])
        self.assertEqual([(TARGET[0],P)],bindings(result['helpers'][UP]))

    def test_preserved_context_cannot_gain_a_helper(self):
        seed = {**self.seed,'preserve':{UP:(AIR,None)}}
        self.assertIn('preserved_context_would_change: '+str(UP),self.close(seed=seed)['errors'])

    def test_independently_complete_context_root_can_host_new_guest(self):
        self.shapes[GUEST] = {(0,0,0)}
        seed = {**self.seed, 'preserve':{UP:(GUEST,None)}}
        result = self.close(World({P:OLD,UP:GUEST}),seed=seed)
        self.assertEqual([],result['errors'])
        self.assertEqual(GUEST,result['desired'][UP])
        self.assertEqual([(TARGET[0],P)],bindings(result['helpers'][UP]))
        self.assertEqual([UP],result['sharedPreservedContexts'])

    def test_malformed_owners_list_refuses(self):
        entity = helper(UP,P)
        entity.value['Owners'] = Tag(TAG_INT,4)
        with self.assertRaisesRegex(ValueError,'invalid_helper_owners_list'):
            bindings(entity)

    def test_custom_helper_payload_cannot_be_lost(self):
        entity = helper(UP,P)
        entity.value['Custom'] = Tag(TAG_STRING,'keep')
        self.assertIn('foreign_or_custom_block_entity',self.close(entities={(DIM,*UP):entity})['errors'])

    def test_conflicting_roots_block_only_connected_group(self):
        other = {**self.seed, 'ids':['conflict'], 'outputs':{P:GUEST}}
        far = (8,64,0)
        independent = {**self.seed, 'ids':['independent'], 'consume':{far}, 'outputs':{far:TARGET}}
        groups = closed_groups([self.seed,other,independent],World({P:OLD,far:OLD}),{}, {}, self.shapes,self.defs)
        self.assertEqual(2,len(groups))
        self.assertEqual(1,sum(bool(g['errors']) for g in groups))
        blocked = next(g for g in groups if g['errors'])
        self.assertTrue(any(e.startswith('conflicting_exact_recipe_roots:') for e in blocked['errors']))

    def test_real_anvil_apply_repeat_and_nonterrain_preservation(self):
        # Mock only input authorization/candidate discovery for a tiny fixture.
        # Exercise real NBT, owner preflight, Candidate/apply/save and repeated
        # run_with_repeat, including an unrelated chest and arbitrary mod data.
        import complete_accepted_restore as writer
        from convert_logical_world import World as AnvilWorld, hash_tree
        from accepted_objects_restore import tree_sha
        from test_logical_world import write_chunk, entity
        from verify_modded_preservation import verify
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root/'original'
            chest = (7,64,7)
            chest_tag = entity(chest,'minecraft:chest',Secret=Tag(TAG_STRING,'retain'))
            write_chunk(source/'region/r.0.0.mca',0,0,{P:(OLD[0],{}),chest:('minecraft:chest',{})},entities=[chest_tag])
            from world_io import write_nbt, NbtFile
            write_nbt(source/'level.dat',NbtFile('',Tag(TAG_COMPOUND,{'Data':Tag(TAG_COMPOUND,{})})))
            (source/'data').mkdir()
            (source/'data/foreign-mod.dat').write_bytes(b'\x00preserve\xff')
            (source/'playerdata').mkdir()
            (source/'playerdata/player.dat').write_bytes(b'player-bytes')
            before = hash_tree(source)
            resources = root/'resources'; resources.mkdir()
            def discover(world,*_args):
                return ([{**self.seed,'evidence':'independent tiny fixture'}],[],[]) if world.get(DIM,P)==OLD else ([],[],[{'result':'already_correct'}])
            with patch.multiple(writer,ROOT=root,RC2_TREE_SHA=tree_sha(before),
                    load_shapes=lambda _p:self.shapes,definitions=lambda _p:self.defs,
                    definition_hashes=lambda _p:{},recipe_candidates=discover,
                    alias_candidates=lambda *_args:([],[])), \
                    patch('complete_owner_bridge.proven_candidates',return_value=([],[],{})):
                result = writer.run_with_repeat(source,root/'build/new-city',resources=resources,
                    report_path=root/'build/report.json',dry_run=False,repeat_check=True)
            self.assertEqual('PASS',result['secondPass']['result'])
            self.assertEqual(1,len(result['ledger']))
            self.assertEqual(before,hash_tree(source))
            after = AnvilWorld(root/'build/new-city',{})
            self.assertEqual(TARGET,after.get(DIM,P))
            self.assertEqual((PART,()),after.get(DIM,UP))
            self.assertEqual(chest_tag,after.block_entities()[(DIM,*chest)])
            self.assertEqual('PASS',verify(source,root/'build/new-city',result)['result'])


if __name__ == '__main__':
    unittest.main()
