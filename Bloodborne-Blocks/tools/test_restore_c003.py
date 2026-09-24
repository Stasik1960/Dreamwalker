import gzip
import json
import unittest
from restore_c003 import ROOT, CENSUS, compile_c003
from source_variant_rng import guards_match, resolve_choices
from source_assembly_pipeline import active_choices
from source_assembly_visuals import resource


class C003RestorationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=compile_c003()
        cls.blocks={b['id']:b for b in cls.data['blocks']}
        cls.families={f['id']:f for f in cls.data['families']}

    def test_four_semantic_ids_and_thirteen_variants(self):
        self.assertEqual({'o_barrel','o_books','o_bag','o_cases_0'},set(self.blocks))
        self.assertEqual({'o_barrel':6,'o_books':2,'o_bag':4,'o_cases_0':1},
                         {i:len(b['properties'].get('variant', ['cases_0'])) for i,b in self.blocks.items()})
        self.assertNotIn('o_c003',self.blocks)
        for b in self.blocks.values():
            self.assertNotIn('base',b['properties'].get('variant', []))
            self.assertNotIn('alt',b['properties'].get('variant', []))
            self.assertTrue(all(len(values) > 1 for values in b['properties'].values()))

    def test_each_occurrence_uses_only_itself_and_same_source_root(self):
        for f in self.families.values():
            self.assertFalse(f.get('migration_disabled',False))
            for s in f['states'].values():
                for p in s['migration_source_pattern']:
                    self.assertEqual(1,len(p['components']))
                    self.assertEqual([0,0,0],p['components'][0]['offset'])
                    self.assertNotIn('split_transaction',p)
                    self.assertTrue(all(g['offset']==[0,0,0] for g in p['variant_guards']))

    def test_all_cached_samples_choose_exact_source_variant_once(self):
        census=json.loads(gzip.decompress(CENSUS.read_bytes()))
        for record in self.data['evidence']['families']:
            f=self.families[record['id']]
            patterns=[(key,p) for key,s in f['states'].items() for p in s['migration_source_pattern']]
            checked=0
            for source in census['states']:
                if source['id']!=record['source_id']:continue
                same=[(k,p) for k,p in patterns if p['components'][0]['properties']==source['properties']]
                if not same:continue
                raw=json.loads(resource(source['id'],'blockstates','json'))
                component={'id':source['id'],'model_choices':active_choices(raw,source['properties'])}
                for dim in source['dimensions'].values():
                    for pos in dim['examples']:
                        selected=[(k,p) for k,p in same if guards_match(p['variant_guards'],pos)]
                        self.assertEqual(1,len(selected),(record['id'],pos))
                        actual=resolve_choices(component,pos)[0]['model'].rsplit('/',1)[-1]
                        self.assertEqual(actual,dict(s.split('=') for s in selected[0][0].split(',')).get('variant', 'cases_0'))
                        checked+=1
            self.assertGreater(checked,0)

    def test_actual_c003_barrels_are_independent_occurrences(self):
        batch=json.loads((ROOT/'docs/manual-review/source-assemblies/batch-02/batch-02-manifest.json').read_bytes())
        row=next(r for r in batch['candidates'] if r['review_id']=='C003')
        origin=row['example']['anchor']
        patterns=[(k,p) for k,s in self.families['o_barrel']['states'].items() for p in s['migration_source_pattern']]
        roots=[]
        for component in row['components']:
            if component['number'] not in (1,3,6):continue
            root=tuple(origin[i]+component['relative'][i] for i in range(3));roots.append(root)
            selected=[(k,p) for k,p in patterns if p['components'][0]['properties']==component['source']['properties']
                      and guards_match(p['variant_guards'],root)]
            self.assertEqual(1,len(selected))
            expected=resolve_choices(component,root)[0]['model'].rsplit('/',1)[-1]
            self.assertIn('variant='+expected,selected[0][0])
        self.assertEqual(3,len(set(roots)))

if __name__=='__main__':unittest.main()
