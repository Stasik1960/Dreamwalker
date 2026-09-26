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

    def test_physical_separation_preserves_all_authored_masks(self):
        data,_=load_contracts(DEFAULT_RESOURCES)
        for f in data['families']:
            for s in f['states'].values():
                self.assertEqual(s['interaction_footprint']['cells'],s['physical_footprint']['cells'])
                self.assertEqual(s['collision_footprint']['boxes'],s['physical_footprint']['boxes'])

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

    def test_superseded_railings_are_not_independent_c474_objects(self):
        d=json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
        rows=[r for r in d['occurrences'] if any(o['family']=='o_c474' and o['canonical_root']==[-566,85,-165] for o in r['outputs'])]
        self.assertEqual(1,len(rows))
        cells={tuple(p['position']) for p in rows[0]['source_cells']}
        for r in d['occurrences']:
            if any(o['family']=='o_stone_railing' for o in r['outputs']):
                self.assertFalse({tuple(p['position']) for p in r['source_cells']}<cells)

if __name__=='__main__':unittest.main()
