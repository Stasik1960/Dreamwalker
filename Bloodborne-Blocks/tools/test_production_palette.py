"""Current production gates, independent of historical QA registry counts."""
import copy, gzip, hashlib, json, unittest
from pathlib import Path
import numpy as np
from logical_contract_v2 import direct_rules, load_contracts
from scan_production_usage import weighted_indices, encode
from source_variant_rng import weighted_index

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
LOG=RES/'bloodborne_blocks/logical'
REQUIRED=ROOT/'docs/required-production-families.json'

def read(path):
    return json.loads(gzip.decompress(path.read_bytes()) if path.suffix=='.gz' else path.read_bytes())

class ProductionPaletteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=read(ROOT/'docs/production-logical-palette.json')
        cls.required=read(REQUIRED)
        cls.required_ids=set(cls.required['retained_reviewed_ids'])|{row['id'] for row in cls.required['restorations']}|set(cls.required['c003_production_ids'])
        cls.blocks={b['id']:b for b in read(LOG/'definitions.json')['blocks']}
        cls.contracts,_=load_contracts(LOG)
        cls.families={f['id']:f for f in cls.contracts['families']}
        cls.meshes=read(LOG/'meshes.json.gz')

    def test_exact_manifest_registry_and_no_superseded_members(self):
        ids={o['id'] for o in self.manifest['objects']}
        self.assertEqual(49,self.required['expected_production_count']);self.assertEqual(49,len(self.required_ids));self.assertEqual(ids,self.required_ids);self.assertEqual(ids,set(self.blocks));self.assertEqual(ids,set(self.families))
        self.assertTrue(all(o['status']=='PRODUCTION' for o in self.manifest['objects']))
        self.assertFalse(ids & {x['id'] for x in self.manifest['excluded']})
        self.assertFalse(ids & set(self.required['forbidden_production_ids']))
        self.assertEqual([],read(LOG/'hidden-items.json'))
        self.assertFalse((RES/'bloodborne_blocks/definitions.json').exists())
        self.assertFalse((RES/'bloodborne_blocks/v2/definitions.json').exists())

    def test_tree_and_semantic_supersession(self):
        self.assertIn('o_c001',self.blocks)
        for ident in ('o_c001_a','o_c001_b','o_c009_a','o_c009_b','o_dead_tree_planter','o_c008','o_c008_4','o_c282_a','o_c282_b','o_c654'):
            self.assertNotIn(ident,self.blocks)
            self.assertFalse((RES/f'assets/bloodborne_blocks/models/item/{ident}.json').exists())
        expected={'C003':4,'C008':4,'C1491':3,'C1962':1,'C1979':5,'C471':2,'C282':1,'C654':2}
        for review,count in expected.items():
            self.assertEqual(count,sum(review in o['source_reviews'] for o in self.manifest['objects']))
        self.assertNotIn('o_c003',self.blocks)
        for ident in ('o_barrel','o_books','o_bag','o_cases_0'):self.assertIn(ident,self.blocks)
        bag=next(o for o in self.manifest['objects'] if o['id']=='o_bag')
        self.assertEqual({'C1962','C1962_a','C1962_b','C003','C003 component 4'},set(bag['source_reviews']))
        self.assertEqual(['o_books'],self.required['successors']['o_c1319']['ids'])

    def test_required_family_cannot_be_replaced_by_a_smaller_generated_selection(self):
        selected={o['id'] for o in self.manifest['objects']}
        self.assertFalse(self.required_ids-selected,'production selection silently omitted: '+str(sorted(self.required_ids-selected)))

    def test_base_alt_same_gameplay_and_single_registry(self):
        for ident,block in self.blocks.items():
            self.assertEqual(['base','alt'],block['properties']['visual'])
            family=self.families[ident]
            for key,state in family['states'].items():
                if 'visual=base' not in key:continue
                other=family['states'][key.replace('visual=base','visual=alt')]
                self.assertEqual({k:v for k,v in state.items() if k!='migration_source_pattern'},
                                 {k:v for k,v in other.items() if k!='migration_source_pattern'})
                self.assertEqual([],other['migration_source_pattern'])

    def test_manifest_labels_match_current_player_facing_translations(self):
        english=read(RES/'assets/bloodborne_blocks/lang/en_us.json')
        russian=read(RES/'assets/bloodborne_blocks/lang/ru_ru.json')
        for obj in self.manifest['objects']:
            key='block.bloodborne_blocks.'+obj['id']
            self.assertEqual(obj['semantic_label'],english[key])
            self.assertTrue(russian[key])

    def test_split_retains_second_statue_root_and_rotated_state(self):
        rules,_=direct_rules(LOG)
        statues=[r for r in rules if r.transaction_id and r.transaction_id.startswith('o_c008_')]
        self.assertTrue(statues)
        for rule in statues:
            copies=[o for o in rule.outputs if o.target[0]=='bloodborne_blocks:o_c008_2']
            self.assertEqual(2,len(copies));self.assertNotEqual(copies[0].root_offset,copies[1].root_offset)
            order=('north','east','south','west')
            self.assertEqual(3,(order.index(dict(copies[1].target[1])['facing'])-order.index(dict(copies[0].target[1])['facing']))%4)
            self.assertEqual(6,len(rule.outputs))

    def test_all_migration_targets_and_overrides_are_production(self):
        rules,_=direct_rules(LOG)
        for rule in rules:
            for target in [rule.target]+[o.target for o in rule.outputs]:
                ident=target[0].split(':')[1]
                self.assertIn(ident,self.blocks)
                key=','.join(f'{k}={v}' for k,v in target[1])
                self.assertIn(key,self.blocks[ident]['states'])

    def test_statue_dedup_proves_frozen_mesh_equivalence_not_state_arithmetic(self):
        frozen=read(ROOT/'docs/production-authoring-inputs.json.gz')
        from test_preserved_semantics import texture_alias
        aliases=read(ROOT/'docs/production-resource-pruning.json')['texture_aliases']
        old=next(b for b in frozen['definitions']['blocks'] if b['id']=='o_c008_4')
        facing=('north','east','south','west')
        for index,direction in enumerate(facing):
            source=frozen['meshes'][old['models'][f'facing={direction},visual=base']]['polygons']
            target_key=f'facing={facing[(index-1)%4]},hand_lantern=none,visual=base'
            target=self.meshes[self.blocks['o_c008_2']['models'][target_key]]['polygons']
            remaining=list(target)
            for polygon in source:
                a=np.asarray(polygon['vertices']);matches=[]
                for j,other in enumerate(remaining):
                    if texture_alias(polygon['texture'],aliases)!=texture_alias(other['texture'],aliases) or len(a)!=len(other['vertices']):continue
                    b=np.asarray(other['vertices'])
                    error=min(float(np.max(np.abs(a-np.roll(b,shift,axis=0)))) for shift in range(len(a)))
                    if error<=2e-6:matches.append(j)
                self.assertTrue(matches,'removed statue does not match canonical target geometry: '+direction)
                remaining.pop(matches[0])
            self.assertFalse(remaining)

    def test_emissive_metadata_is_nonnull_and_companions_exist(self):
        data=read(LOG/'definitions.json')
        self.assertIsInstance(data['emissive_textures'],dict)
        for texture in data['emissive_textures'].values():
            ns,path=texture.split(':',1)
            self.assertTrue((RES/f'assets/{ns}/textures/{path}.png').is_file())

    def test_no_duplicate_mesh_content_and_no_orphan_meshes(self):
        used={m for b in self.blocks.values() for m in b['models'].values()}
        self.assertEqual(used,set(self.meshes))
        digests=[hashlib.sha256(json.dumps(m,sort_keys=True,separators=(',',':')).encode()).hexdigest() for m in self.meshes.values()]
        self.assertEqual(len(digests),len(set(digests)))

    def test_tags_do_not_reference_removed_registry_blocks(self):
        for path in (RES/'data').rglob('tags/**/*.json'):
            for entry in read(path).get('values',[]):
                name=entry if isinstance(entry,str) else entry['id']
                if name.startswith('bloodborne_blocks:'):
                    self.assertIn(name.split(':',1)[1],self.blocks,str(path))

    def test_source_inputs_unchanged_and_scan_full_scope(self):
        scan=read(ROOT/'docs/production-source-usage-summary.json')
        self.assertEqual(33,scan['regions']);self.assertEqual(21503,scan['chunks']);self.assertEqual(27119750,scan['carrier_cells'])
        for file,field in [('source-world.zip','source_sha256'),('source-resource-pack.zip','pack_sha256')]:
            self.assertEqual(scan[field],hashlib.sha256((ROOT/'reference-inputs'/file).read_bytes()).hexdigest())

    def test_vectorized_scan_rng_equals_verified_scalar_for_negative_coordinates(self):
        xyz=np.random.default_rng(1201).integers([-800,-64,-900],[600,320,500],size=(3000,3))
        for weights in ([1,1],[1,2,7],[3,2,1,8]):
            for multipart in (False,True):
                actual=weighted_indices(weights,xyz,multipart).tolist()
                self.assertEqual([weighted_index(weights,tuple(map(int,p)),multipart) for p in xyz],actual)
        self.assertEqual(len(xyz),len(set(encode(xyz).tolist())))

    def test_c282_source_pattern_keeps_raw_carrier_above_floor_master(self):
        family=self.families['o_c282']
        self.assertNotIn('migration_disabled',family)
        patterns=[p for s in family['states'].values() for p in s['migration_source_pattern']]
        self.assertEqual(4,len(patterns))
        for pattern in patterns:
            self.assertEqual([0,1,0],pattern['components'][0]['offset'])
            self.assertEqual('minecraft:dark_oak_stairs',pattern['components'][0]['id'])
        self.assertEqual({'facing','open','visual'},set(self.blocks['o_c282']['properties']))

if __name__=='__main__':unittest.main()
