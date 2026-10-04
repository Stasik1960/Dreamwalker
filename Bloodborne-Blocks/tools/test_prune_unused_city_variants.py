import gzip,json,tempfile,unittest
from pathlib import Path
from prune_unused_city_variants import build_plan,stage_plan,apply_stage,load,sha,key,full,write

class PruningChecks(unittest.TestCase):
    def fixture(self,root,same_art=False,same_physics=False,manual=False):
        city=root/'src/main/resources/bloodborne_blocks/city';city.mkdir(parents=True)
        ident='owner_unified_1234';states={};models={};physical={};meshes={}
        for v in ('0','1','2'):
            meshes['mesh'+v]={'polygons':[{'texture':'texture'+('0'if same_art and v=='1'else v)}]}
            for facing in ('north','east','south','west'):
                s=key({'facing':facing,'root_anchor':'canonical','variant':v})
                states[s]=[0,0,0];models[s]='mesh'+v
                physical[s]={'cells':{'0,0,0':{'collision':[[0,0,0,1,1,1 if same_physics or v!='1'else .5]]}}}
        d={'id':ident,'unified':True,'properties':{'facing':['north','east','south','west'],'root_anchor':['canonical'],'variant':['0','1','2']},
           'default':{'facing':'north','root_anchor':'canonical','variant':'0'},'placement_properties':{'variant':'0'},'states':states,'models':models}
        write(city/'definitions.json',{'blocks':[d]});write(city/'geometry.json',{'blocks':{ident:{'states':physical}},'profiles':{}})
        write(city/'document-final.json',{'objects':[{'components':[{'id':ident,'properties':{'variant':'2'}}]}]if manual else[]})
        write(city/'unified-migration.json',{'states':{full(ident,s):{'id':'bloodborne_blocks:'+ident,'properties':dict(part.split('=')for part in s.split(','))}for s in states}})
        for name,data in [('meshes.json.gz',{}),('owner-meshes.json.gz',meshes)]:
            with gzip.open(city/name,'wt')as stream:json.dump(data,stream)
        asset=root/f'src/main/resources/assets/bloodborne_blocks/blockstates/{ident}.json'
        write(asset,{'variants':{s:{'model':'bloodborne_blocks:block/city/'+ident}for s in states}})
        world=root/'world.dat';world.write_bytes(b'world fixture')
        write(root/'persisted.json',{'complete':True,'hashInputs':{'world.dat':sha(world)},'variantReferences':{}})
        usage=root/'usage.json';write(usage,{'persistedAudit':'persisted.json','variantAudit':{'definitionsSha256':sha(city/'definitions.json')},'hashInputMap':{'world.dat':sha(world)},'inventory':{'blocks':{'stateCounts':{full(ident,s):1 for s in states if 'variant=2'not in s}}}})
        return usage,ident

    def test_same_offsets_are_not_proof_of_identical_art(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root,same_physics=True)
            plan=build_plan(root,usage);row=plan['blocks'][0]
            self.assertEqual({'2':'0'},row['removed']);self.assertEqual(0,plan['counts']['usedVariantRemaps'])

    def test_geometry_difference_prevents_merge(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root,same_art=True)
            self.assertNotIn('1',build_plan(root,usage)['blocks'][0]['removed'])

    def test_exact_used_duplicate_preserves_identity_target_and_closure(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root,same_art=True,same_physics=True)
            plan=build_plan(root,usage);self.assertEqual(1,plan['counts']['usedVariantRemaps'])
            stage=stage_plan(root,plan);city=stage/'resources/bloodborne_blocks/city'
            d=load(city/'definitions.json')['blocks'][0]
            self.assertEqual(['0'],d['properties']['variant']);self.assertEqual(4,len(d['states']))
            self.assertEqual(set(d['states']),set(load(city/'geometry.json')['blocks'][ident]['states']))
            m=load(city/'variant-pruning-migration.json')['states'];self.assertEqual(8,len(m))
            self.assertTrue(all(v['properties']['variant']=='0'for v in m.values()))
            with gzip.open(city/'owner-meshes.json.gz','rt')as stream:self.assertEqual({'mesh0'},set(json.load(stream)))

    def test_manual_unused_variant_survives(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root,manual=True)
            self.assertEqual(0,build_plan(root,usage)['counts']['removedVariants'])

    def test_missing_complete_usage_and_stale_hash_refuse(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root)
            data=load(usage);del data['inventory']['blocks']['stateCounts'];write(usage,data)
            with self.assertRaises(ValueError):build_plan(root,usage)
            data['inventory']['blocks']['stateCounts']={'bloodborne_blocks:x':1};data['variantAudit']['definitionsSha256']='wrong';write(usage,data)
            with self.assertRaises(ValueError):build_plan(root,usage)

    def test_persisted_item_variant_survives(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root)
            data=load(root/'persisted.json');data['variantReferences']={'bloodborne_blocks:'+ident:['2']};write(root/'persisted.json',data)
            self.assertEqual(0,build_plan(root,usage)['counts']['removedVariants'])

    def test_historical_owner_geometry_variant_survives(self):
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);usage,ident=self.fixture(root)
            write(root/'src/main/resources/bloodborne_blocks/city/owner-runtime-mappings.json',
                  {'states':{'old-state':{'id':'bloodborne_blocks:'+ident,'properties':{'variant':'2'},'shape':[[0,0,0]]}}})
            self.assertEqual(0,build_plan(root,usage)['counts']['removedVariants'])

    def test_inputs_changed_after_plan_or_stage_refuse(self):
        for input_kind in('usage','world','asset','model'):
            for stage_first in(False,True):
                with self.subTest(input_kind=input_kind,stage_first=stage_first),tempfile.TemporaryDirectory()as folder:
                    root=Path(folder);usage,ident=self.fixture(root);plan=build_plan(root,usage)
                    if stage_first:stage=stage_plan(root,plan)
                    path={'usage':usage,'world':root/'world.dat','asset':root/f'src/main/resources/assets/bloodborne_blocks/blockstates/{ident}.json',
                          'model':root/f'src/main/resources/assets/bloodborne_blocks/models/block/city/{ident}.json'}[input_kind]
                    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'intervening edit')
                    with self.assertRaises(ValueError):
                        if stage_first:apply_stage(root,stage)
                        else:stage_plan(root,plan)

if __name__=='__main__':unittest.main()
