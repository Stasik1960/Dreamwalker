"""Scoped cumulative-patch invariants; consumes saved evidence, never scans a world."""
import copy, json, unittest
from apply_agony_patch import DOC, LOGICAL, MERGES, CONTEXT, STATUES, FACINGS, read, props, key, rotate, bounds
from production_fingerprints import verify
from logical_contract_v2 import direct_rules

class AgonyPatchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ds={x['id']:x for x in read(LOGICAL/'definitions.json')['blocks']}
        cls.fs={x['id']:x for x in read(LOGICAL/'contracts-v2.json')['families']}
        cls.ms=read(LOGICAL/'meshes.json.gz')
        cls.baseline=read(DOC/'baseline-fingerprints.json.gz')['fingerprints']['families']

    def test_untouched_families(self):
        result=verify(allowlist_path=DOC/'allowlist.json')
        self.assertEqual('PASS',result['result'])
        self.assertEqual(sorted(set(MERGES)|CONTEXT),result['approved_retired_removals'])

    def test_retired_rules_resolve_or_are_unconsumed_context(self):
        self.assertEqual(49,len(self.ds))
        rules,_=direct_rules(LOGICAL)
        targets={t[0].split(':')[1] for r in rules for t in [r.target]+[o.target for o in r.outputs]}
        self.assertFalse(targets&(set(MERGES)|CONTEXT|{'o_c003'}))
        decisions={x['id']:x for x in read(DOC/'decisions.json')['decisions']}
        for ident in CONTEXT:
            self.assertEqual('CONTEXT_ONLY',decisions[ident]['status'])
            self.assertFalse(decisions[ident]['ids'])
            self.assertTrue(decisions[ident]['source_patterns'])
        for ident in ('o_c561','o_bench_rotate'):
            new=self.fs[MERGES[ident]]
            live=[p for s in new['states'].values() for p in s['migration_source_pattern']]
            for state in self.baseline[ident]['core']['contract']['states'].values():
                for pattern in state['migration_source_pattern']:self.assertIn(pattern,live,ident)
        self.assertIn('o_cases_0',self.ds);self.assertIn('o_c046',self.ds)
        self.assertNotEqual(set(self.ds['o_cases_0']['models'].values()),set(self.ds['o_c046']['models'].values()))
        self.assertEqual('o_cases_0',self.fs['o_c046']['migration_redirect']['successor'])
        self.assertEqual(5,len(self.fs['o_c046']['review_source_patterns']))
        for ident in ('o_c046','o_c561'):
            for p in self.baseline[ident]['core']['contract']['review_source_patterns']:
                self.assertIn(p,self.fs['o_c046']['review_source_patterns'])

    def test_candlestick_support_preserves_xyz_source_patterns(self):
        f=self.fs['o_candles_0'];old=self.baseline['o_candles_0']['core']['contract']
        for k,s in f['states'].items():
            self.assertEqual(old['states'][k]['migration_source_pattern'],s['migration_source_pattern'])
            self.assertEqual(old['canonical_anchor'],f['canonical_anchor'])
            self.assertFalse(s['collision_footprint']['boxes'])
        pol=self.ms[self.ds['o_candles_0']['models']['facing=north,lit=false,visual=base']]['polygons']
        for group in f['support_groups']:
            vertices=[v for e in group['source_elements'] for i in (2*e,2*e+1) for v in pol[i]['vertices']]
            self.assertAlmostEqual(0,min(v[1] for v in vertices),places=6)

    def test_landing_and_ladder_are_separate_simple_shapes(self):
        self.assertEqual('static',self.ds['o_ladder_01']['behavior'])
        for ident in ('o_ladder_01','o_ladder_03'):
            for k,s in self.fs[ident]['states'].items():
                self.assertEqual(9 if ident=='o_ladder_01' else 2,len(s['collision_footprint']['boxes']),ident)
                if ident=='o_ladder_03':
                    self.assertEqual([[0,-1,0],[0,0,0]],s['interaction_footprint']['cells'])
                    for b in s['collision_footprint']['boxes']:
                        self.assertAlmostEqual(.125,min(b[3]-b[0],b[5]-b[2]))
        pol=self.ms[self.ds['o_ladder_01']['models']['facing=north,visual=base']]['polygons']
        for target,origin in ((9,8),(10,7)):
            self.assertEqual(pol[origin]['texture'],pol[target]['texture'])
            self.assertEqual([[1-v[0],*v[1:]] for v in reversed(pol[origin]['vertices'])],pol[target]['vertices'])

    def test_attachments_preserve_statue_geometry_shapes_and_raw_matchers(self):
        for ident in STATUES:
            f=self.fs[ident];old=self.baseline[ident]['core']['contract'];d=self.ds[ident]
            self.assertEqual(['none','unlit','lit'],d['properties']['hand_lantern'])
            self.assertEqual('none',d['placement_properties']['hand_lantern'])
            self.assertTrue(f['hand_lantern']['facing_bound'])
            for k,s in f['states'].items():
                p=props(k);mode=p.pop('hand_lantern');oldk=key(p);base=old['states'][oldk]
                for field in ('canonical_anchor','master_from_source','collision_footprint','selection_footprint','interaction_footprint'):
                    self.assertEqual(base.get(field),s.get(field),f'{ident} {field}')
                self.assertEqual(15 if mode=='lit' else 0,d['states'][k][2])
                self.assertEqual(base['migration_source_pattern'] if mode=='none' else [],s['migration_source_pattern'])
                vertices=self.ms[d['models'][k]]['polygons']
                self.assertGreaterEqual(bounds(rotate(vertices,offset=s['render_mesh']['offset']))[1],-1e-6)

    def test_window_has_no_opaque_embedded_carrier(self):
        d=self.ds['o_shuttered_window'];f=self.fs['o_shuttered_window']
        self.assertNotIn('embedded',d['properties'])
        for opened in ('false','true'):
            k=key({'facing':'north','open':opened,'visual':'base'})
            pol=self.ms[d['models'][k]]['polygons'];self.assertEqual(15,len(pol))
            self.assertEqual(pol[10]['texture'],pol[14]['texture'])
            self.assertEqual([v[3:] for v in reversed(pol[10]['vertices'])],[v[3:] for v in pol[14]['vertices']])
        # Geometry and owned root transform remain stationary; only authored shutters change.
        a=f['states'][key({'facing':'north','open':'false','visual':'base'})]
        b=f['states'][key({'facing':'north','open':'true','visual':'base'})]
        self.assertEqual(a['rotation'],b['rotation'])
        self.assertEqual(a['collision_footprint'],b['collision_footprint'])
        self.assertEqual([[0,0,0],[0,1,0]],a['interaction_footprint']['cells'])

if __name__=='__main__':unittest.main()
