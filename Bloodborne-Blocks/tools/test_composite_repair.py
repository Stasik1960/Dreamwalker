"""Regression tests for exact identities, independently of registry validity."""
import json
import tempfile
import unittest
from pathlib import Path
from composite_world_oracle import EvidenceReader, reconcile, key
from convert_logical_world import DEFAULT_RESOURCES, World, add, convert, PART
from logical_contract_v2 import direct_rules, load_contracts
from modded_world_adapter import compile_modded_rules
from source_variant_rng import guards_match
from test_logical_world import assembly_source, tree_hash

ROOT=Path(__file__).resolve().parents[1]

class CompositeRepairTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw,_=direct_rules(DEFAULT_RESOURCES)
        cls.rules,cls.defaults,_=compile_modded_rules(DEFAULT_RESOURCES)

    def test_physical_changes_are_exact_documented_nonexpanding_reductions(self):
        data,_=load_contracts(DEFAULT_RESOURCES)
        plan=json.loads((ROOT/'docs/composite-grid-repair/physical-conflict-plan.json').read_bytes())
        approved={(c['family'],c['state']):c for c in plan['changes']}
        for f in data['families']:
            for state_key,s in f['states'].items():
                before={tuple(c) for c in s['interaction_footprint']['cells']}
                after={tuple(c) for c in s['physical_footprint']['cells']}
                change=approved.get((f['id'],state_key))
                self.assertEqual(before-({tuple(c) for c in change['removed_cells']} if change else set()),after)
                self.assertEqual(change['after_boxes'] if change else s['collision_footprint']['boxes'],s['physical_footprint']['boxes'])
                for box in s['physical_footprint']['boxes']:
                    # Partition at every original boundary to test containment
                    # in the UNION, including merged adjacent primitives.
                    from itertools import product
                    cuts=[sorted({box[i],box[i+3]}|{v for old in s['collision_footprint']['boxes']
                          for v in (old[i],old[i+3]) if box[i]<v<box[i+3]}) for i in range(3)]
                    centers=[[(a+b)/2 for a,b in zip(axis,axis[1:])] for axis in cuts]
                    for point in product(*centers):
                        self.assertTrue(any(all(old[i]<=point[i]<=old[i+3] for i in range(3))
                                            for old in s['collision_footprint']['boxes']), (f['id'],state_key,point))

    def test_real_tree_mixed_representation_exact(self):
        reader=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try:
            rules=[r for r in self.rules if (r.source_reference or '').startswith('frozen mixed kept carriers')
                   and r.target[0]=='bloodborne_blocks:o_c001']
            matches=[r for r in rules if guards_match(r.variant_guards,(-284,42,-71)) and all(
                reader.state('minecraft:overworld',add((-284,42,-71),p.offset))[1]==key(p.state)
                for p in (r.source,)+r.members)]
            self.assertEqual(1,len(matches))
            self.assertEqual('tree_cfd3d71f521b',dict(matches[0].target[1])['variant'])
            self.assertGreater(len(matches[0].members)+1,len(matches[0].shape))
        finally:reader.close()

    def test_reduced_masks_leave_only_two_explicit_shared_root_cases(self):
        from collections import defaultdict
        data,_=load_contracts(DEFAULT_RESOURCES)
        families={f['id']:f for f in data['families']}
        oracle=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        owners=defaultdict(list)
        for row in oracle['occurrences']:
            for output in row['outputs']:
                state_key=output['expected_logical_state'].partition('[')[2].rstrip(']')
                state=families[output['family']]['states'][state_key]
                for delta in state['physical_footprint']['cells']:
                    owners[add(output['canonical_root'],delta)].append(output['family'])
        conflicts={p for p,values in owners.items() if len(values)>1}
        self.assertEqual({(-374,73,-291),(-560,98,-9)},conflicts)

    def test_lighting_toggle_does_not_restore_removed_physical_cells(self):
        data,_=load_contracts(DEFAULT_RESOURCES)
        for family in data['families']:
            for state_key,state in family['states'].items():
                if 'lit=true' not in state_key:continue
                other=family['states'].get(state_key.replace('lit=true','lit=false'))
                if other and state['collision_footprint']==other['collision_footprint'] and state['interaction_footprint']==other['interaction_footprint']:
                    self.assertEqual(state['physical_footprint'],other['physical_footprint'])

    def test_mixed_tree_atomic_consumption_and_second_pass(self):
        origin=(8,64,8)
        rule=next(r for r in self.rules if (r.source_reference or '').startswith('frozen mixed kept carriers')
                  and r.target[0]=='bloodborne_blocks:o_c001' and guards_match(r.variant_guards,origin))
        blocks={add(origin,p.offset):(p.state[0],dict(p.state[1])) for p in (rule.source,)+rule.members}
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary);source=base/'source';output=base/'first';second=base/'second'
            assembly_source(source,blocks,[])
            report=convert(source,output,source_mode='modded',report_root=base,report_path=base/'first.json')
            world=World(output,self.defaults)
            self.assertEqual(rule.target,world.get('minecraft:overworld',origin),report['rejected'])
            for p in blocks:
                state=world.get('minecraft:overworld',p)
                self.assertIn(state[0],{rule.target[0],PART,'minecraft:air'})
            again=convert(output,second,source_mode='modded',report_root=base,report_path=base/'second.json')
            self.assertEqual(0,again['counts']['converted'])
            self.assertEqual(tree_hash(output),tree_hash(second))

    def test_c474_oracle_uses_source_root_not_playtest_neighbor(self):
        d=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        rows=[o for r in d['occurrences'] for o in r['outputs'] if o['family']=='o_c474']
        self.assertIn([-566,85,-165],[o['canonical_root'] for o in rows])
        self.assertNotIn([-563,85,-165],[o['canonical_root'] for o in rows])

    def test_c474_merged_modules_convert_atomically(self):
        rule=next(r for r in self.rules if r.target[0]=='bloodborne_blocks:o_c474' and
                  (r.source_reference or '').startswith('proven textured module composition'))
        reader=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try:
            for p in (rule.source,)+rule.members:
                self.assertEqual(key(p.state),reader.state('minecraft:overworld',add((-566,85,-165),p.offset))[1])
        finally:reader.close()
        self.assertEqual(32,1+len(rule.members))
        origin=(8,64,8)
        blocks={add(origin,p.offset):(p.state[0],dict(p.state[1])) for p in (rule.source,)+rule.members}
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary);source=base/'source';output=base/'first'
            assembly_source(source,blocks,[])
            report=convert(source,output,source_mode='modded',report_root=base,report_path=base/'report.json')
            world=World(output,self.defaults)
            self.assertEqual(rule.target,world.get('minecraft:overworld',origin),report['rejected'])
            for p in blocks:self.assertIn(world.get('minecraft:overworld',p)[0],{PART,rule.target[0],'minecraft:air'})

    def test_lantern_identity_is_reviewed_c618(self):
        d=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        outputs=[o for r in d['occurrences'] for o in r['outputs'] if o['canonical_root']==[-540,41,-33]]
        self.assertEqual(['o_c618'],[o['family'] for o in outputs])
        self.assertEqual('north',outputs[0]['orientation'])

    def test_c618_preserves_independent_panel_and_second_pass(self):
        origin=(-540,41,-33);panel=add(origin,(0,1,0))
        rule=next(r for r in self.rules if r.target[0]=='bloodborne_blocks:o_c618'
                  and 'legacy carriers' in (r.source_reference or '')
                  and dict(r.target[1]).get('facing')=='north' and dict(r.target[1]).get('lit')=='true')
        reader=EvidenceReader(ROOT/'reference-inputs/latest-modded-world.zip')
        try:
            self.assertEqual(key(rule.source.state),reader.state('minecraft:overworld',add(origin,rule.source.offset))[1])
            pane_text=reader.state('minecraft:overworld',panel)[1]
        finally:reader.close()
        pane_id,_,suffix=pane_text.partition('[')
        pane_props=dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)
        # The existing fixture writer emits section Y=4 in chunk (0,0).
        # Preserve the exact source variant by selecting a matching local seed.
        origin=next((x,68,z) for x in range(2,14) for z in range(2,14)
                    if guards_match(rule.variant_guards,(x,68,z)))
        panel=add(origin,(0,1,0))
        blocks={add(origin,p.offset):(p.state[0],dict(p.state[1])) for p in (rule.source,)+rule.members}
        blocks[panel]=(pane_id,pane_props)
        with tempfile.TemporaryDirectory() as temporary:
            base=Path(temporary);source=base/'source';output=base/'first';second=base/'second'
            assembly_source(source,blocks,[])
            report=convert(source,output,source_mode='modded',report_root=base,report_path=base/'first.json')
            world=World(output,self.defaults)
            self.assertEqual(rule.target,world.get('minecraft:overworld',origin),report['rejected'])
            self.assertEqual(pane_text,key(world.get('minecraft:overworld',panel)))
            again=convert(output,second,source_mode='modded',report_root=base,report_path=base/'second.json')
            self.assertEqual(0,again['counts']['converted'])
            self.assertEqual(tree_hash(output),tree_hash(second))

    def test_superseded_railings_are_not_independent_c474_objects(self):
        d=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        rows=[r for r in d['occurrences'] if any(o['family']=='o_c474' and o['canonical_root']==[-566,85,-165] for o in r['outputs'])]
        self.assertEqual(1,len(rows))
        cells={tuple(p['position']) for p in rows[0]['source_cells']}
        for r in d['occurrences']:
            if any(o['family']=='o_stone_railing' for o in r['outputs']):
                self.assertFalse({tuple(p['position']) for p in r['source_cells']}<cells)

if __name__=='__main__':unittest.main()
