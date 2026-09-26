import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from convert_logical_world import Expected, Output, Rule, World, candidates, reject_overlaps, apply, DEFAULT_RESOURCES
from test_logical_world import assembly_source
from atomic_owner_groups import compile_groups
from modded_world_adapter import compile_modded_rules

DIM='minecraft:overworld'
OLD=('bloodborne_blocks:m_test',())
A=('bloodborne_blocks:o_test_a',())
B=('bloodborne_blocks:o_test_b',())


class AtomicOwnerGroupsTests(unittest.TestCase):
    def fixture(self, missing=False, obstruction=False, overlap=False, foreign_source=False):
        tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup)
        path=Path(tmp.name)/'world'
        blocks={(0,64,0):(OLD[0],{}),(1,64,0):(OLD[0],{}),(2,64,0):(OLD[0],{})}
        if missing:blocks.pop((1,64,0))
        if obstruction:blocks[(3,64,0)]=('minecraft:diamond_block',{})
        if foreign_source:blocks[(1,64,0)]=('minecraft:diamond_block',{})
        assembly_source(path,blocks)
        shape=frozenset({(0,0,0)})
        group=Rule(0,Expected((0,0,0),OLD),A,(0,0,0),
            (Expected((1,0,0),OLD),Expected((2,0,0),OLD)),None,shape,
            transaction_id='owner-test',outputs=(Output(A,(0,0,0),shape),Output(B,(0 if overlap else 3,0,0),shape)),
            source_mode='modded',allowed_origins=((DIM,(0,64,0)),),atomic_owner_group=True)
        fallback=Rule(1,Expected((0,0,0),OLD),A,(0,0,0),(),None,shape,source_mode='modded')
        return World(path,{}),[group,fallback]

    def test_shared_group_is_one_transaction_and_second_pass_zero(self):
        world,rules=self.fixture();items,_,_=candidates(world,rules);reject_overlaps(items)
        ledger=apply(world,items)
        self.assertEqual(1,len(ledger));self.assertEqual(2,len(ledger[0]['outputs']))
        from check_logical_world import validate_ledger
        # Disk still contains the original fixture: independent replay checker
        # authorizes the complete transaction from untouched input.
        validate_ledger(World(world.root,{}),{'ledger':ledger,'sourceMode':'modded',
            'format':'bloodborne-logical-world-conversion-v2','counts':{'converted':1}},rules)
        self.assertEqual(A,world.get(DIM,(0,64,0)));self.assertEqual(B,world.get(DIM,(3,64,0)))
        again,_,_=candidates(world,rules);reject_overlaps(again);self.assertEqual([],apply(world,again))

    def test_missing_member_foreign_destination_or_shared_root_changes_nothing(self):
        for options in ({'missing':True},{'obstruction':True},{'overlap':True},{'foreign_source':True}):
            with self.subTest(options=options):
                world,rules=self.fixture(**options)
                before=[world.get(DIM,(x,64,0)) for x in range(4)]
                items,_,_=candidates(world,rules,conflict_policy='aggressive');reject_overlaps(items)
                self.assertEqual([],apply(world,items))
                self.assertEqual(before,[world.get(DIM,(x,64,0)) for x in range(4)])
                from check_logical_world import independently_accepted_effects
                self.assertEqual(set(),independently_accepted_effects(world,rules,{}, {},'aggressive'))

    def test_missing_runtime_owner_reserves_all_cells(self):
        world,rules=self.fixture();rules[0]=replace(rules[0],preflight_error='whole_owner_runtime_mapping_missing')
        items,_,_=candidates(world,rules);reject_overlaps(items)
        self.assertEqual([],apply(world,items))
        self.assertTrue(all(i.reason for i in items))

    def test_real_graphs_are_disjoint_and_high_root_exception_is_exact(self):
        rules,_,_=compile_modded_rules(DEFAULT_RESOURCES)
        rules,diagnostic=compile_groups(rules,DEFAULT_RESOURCES)
        from atomic_owner_groups import reservations
        reserved=reservations(rules)
        self.assertGreater(len(reserved),5315)
        high=[]
        for r in rules:
            if not r.atomic_owner_group:continue
            origin=r.allowed_origins[0][1]
            for out in r.outputs:
                root=tuple(origin[i]+out.root_offset[i] for i in range(3))
                if out.target[0]=='bloodborne_blocks:o_high_balustrade' and root==(-560,99,-9):high.append(out)
        self.assertEqual(1,len(high));self.assertEqual('upper',dict(high[0].target[1])['root_anchor'])
        self.assertEqual(5315,diagnostic['historicalMembershipsProven'])


if __name__=='__main__':unittest.main()
