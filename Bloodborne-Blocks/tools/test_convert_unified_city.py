import json, tempfile, unittest
from pathlib import Path
from collections import Counter
from convert_unified_city import convert
from reviewed_migration_fixture import write_fixture
from convert_logical_world import World, PART, block_pos_long, hash_tree
from world_io import Tag, TAG_COMPOUND, TAG_LONG, TAG_STRING, TAG_INT, compound


class UnifiedCityTests(unittest.TestCase):
    def test_default_split_mapping_rewrites_all_withdrawn_bush_states_and_helpers(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td);source=base/'source';out=base/'output';old=base/'old/city';new=base/'new/city'
            facings=('north','east','south','west');visuals=('base','alt')
            old_states=[(facing,visual) for facing in facings for visual in visuals]
            cells={(index*3,70,0):('bloodborne_blocks:o_c001',{'facing':facing,'variant':'bush_asset_e','visual':visual})
                   for index,(facing,visual) in enumerate(old_states)}
            cells[(1,70,0)]=(PART,{})
            cells[(30,70,0)]=('minecraft:chest',{})
            helper=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,PART),'x':Tag(TAG_INT,1),'y':Tag(TAG_INT,70),'z':Tag(TAG_INT,0),
                'Root':Tag(TAG_LONG,block_pos_long(0,70,0)),'Owner':Tag(TAG_STRING,'bloodborne_blocks:o_c001')})
            write_fixture(source,cells,block_entities=[helper],floor=False)
            for city in (old,new):
                city.mkdir(parents=True);(city.parent/'logical').mkdir()
                (city/'definitions.json').write_text('{"blocks":[]}')
            (old.parent/'logical/definitions.json').write_text(json.dumps({'blocks':[{'id':'o_c001','default':{},'states':{
                f'facing={facing},variant=bush_asset_e,visual={visual}':[] for facing,visual in old_states}}]}))
            (new.parent/'logical/definitions.json').write_text(json.dumps({'blocks':[{'id':'o_dry_bush','default':{},'states':{
                f'facing={facing},visual={visual}':[] for facing,visual in old_states}}]}))
            split={'states':{f'bloodborne_blocks:o_c001[facing={facing},variant=bush_asset_e,visual={visual}]':{
                'id':'bloodborne_blocks:o_dry_bush','properties':{'facing':facing,'visual':visual},'rootOffset':[0,0,0]}
                for facing,visual in old_states}}
            (new.parent/'logical/tree-bush-split-migration.json').write_text(json.dumps(split))
            mapping=base/'mapping.json';mapping.write_text('{"states":{}}')
            result=convert(source,out,mapping,old,new,base/'progress.json')
            self.assertEqual(8,result['counts']['replacedCells'])
            world=World(out,{})
            for index,(facing,visual) in enumerate(old_states):
                self.assertEqual(('bloodborne_blocks:o_dry_bush',(('facing',facing),('visual',visual))),world.get('minecraft:overworld',(index*3,70,0)))
            self.assertEqual(('minecraft:chest',()),world.get('minecraft:overworld',(30,70,0)))
            self.assertEqual('bloodborne_blocks:o_dry_bush',compound(world.block_entities()[('minecraft:overworld',1,70,0)])['Owner'].value)
            self.assertEqual(result,convert(source,out,mapping,old,new,base/'progress.json'))

    def test_native_all_cells_shared_owners_foreign_and_resume(self):
        with tempfile.TemporaryDirectory() as td:
            base=Path(td);source=base/'source';out=base/'output'
            cells={(0,70,0):('bloodborne_blocks:old_a',{'variant':'0'}),
                   (2,70,0):('bloodborne_blocks:old_b',{'variant':'1'}),
                   (1,70,0):(PART,{}),(3,70,0):('yuushya:example',{}),
                   (4,70,0):('minecraft:chest',{})}
            owners=[Tag(TAG_COMPOUND,{'Root':Tag(TAG_LONG,block_pos_long(*p)),'Owner':Tag(TAG_STRING,i)})
                    for p,i in [((0,70,0),'bloodborne_blocks:old_a'),((2,70,0),'bloodborne_blocks:old_b')]]
            helper=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,PART),'x':Tag(TAG_INT,1),'y':Tag(TAG_INT,70),'z':Tag(TAG_INT,0),
                'Root':owners[0].value['Root'],'Owner':owners[0].value['Owner'],'Owners':Tag(9,owners,TAG_COMPOUND)})
            chest=Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'minecraft:chest'),'x':Tag(TAG_INT,4),'y':Tag(TAG_INT,70),'z':Tag(TAG_INT,0),'custom':Tag(TAG_STRING,'foreign unchanged')})
            write_fixture(source,cells,block_entities=[helper,chest],floor=False)
            old=base/'old/city';new=base/'new/city'
            for p in (old,new):
                p.mkdir(parents=True);(p.parent/'logical').mkdir();(p.parent/'logical/definitions.json').write_text('{"blocks":[]}')
            (old/'definitions.json').write_text(json.dumps({'blocks':[{'id':'old_a','default':{'variant':'0'},'states':{'variant=0':[]}}, {'id':'old_b','default':{'variant':'1'},'states':{'variant=1':[]}}]}))
            (new/'definitions.json').write_text(json.dumps({'blocks':[{'id':'unified','default':{'facing':'north','variant':'0'},'states':{'facing=north,variant=0':[], 'facing=east,variant=1':[]}}]}))
            mapping=base/'mapping.json';mapping.write_text(json.dumps({'states':{
                'bloodborne_blocks:old_a[variant=0]':{'id':'bloodborne_blocks:unified','properties':{'facing':'north','variant':'0'}},
                'bloodborne_blocks:old_b[variant=1]':{'id':'bloodborne_blocks:unified','properties':{'facing':'east','variant':'1'}}}}))
            before=hash_tree(source);result=convert(source,out,mapping,old,new,base/'progress.json')
            self.assertEqual(2,result['counts']['replacedCells']);self.assertEqual(before,hash_tree(source))
            w=World(out,{})
            self.assertEqual('bloodborne_blocks:unified',w.get('minecraft:overworld',(0,70,0))[0])
            self.assertEqual(('yuushya:example',()),w.get('minecraft:overworld',(3,70,0)))
            entities=w.block_entities()
            self.assertEqual(chest,entities[('minecraft:overworld',4,70,0)])
            changed=compound(entities[('minecraft:overworld',1,70,0)])
            self.assertTrue(all(compound(o)['Owner'].value=='bloodborne_blocks:unified' for o in changed['Owners'].value))
            self.assertEqual(result,convert(source,out,mapping,old,new,base/'progress.json'))

    def test_rejects_unmapped_retired_cell(self):
        with tempfile.TemporaryDirectory() as td:
            b=Path(td);s=b/'source';write_fixture(s,{(0,70,0):('bloodborne_blocks:retired',{})},floor=False)
            scopes=[]
            for label in ('old','new'):
                p=b/label/'city';p.mkdir(parents=True);(p.parent/'logical').mkdir()
                for d in (p,p.parent/'logical'):(d/'definitions.json').write_text('{"blocks":[]}')
                scopes.append(p)
            m=b/'m.json';m.write_text('{"states":{}}')
            with self.assertRaisesRegex(ValueError,'unmapped obsolete'):
                convert(s,b/'out',m,*scopes,b/'progress.json')

if __name__=='__main__':unittest.main()
