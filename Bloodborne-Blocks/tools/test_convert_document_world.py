"""Small saved-world proofs for final-document imports and carrier retention."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from convert_document_world import convert
from convert_logical_world import PART, World
from city_palette import helper_bindings
from test_logical_world import assembly_source, entity, tree_hash
from world_io import TAG_COMPOUND, TAG_LIST, Tag, compound


DIMENSION='minecraft:overworld'
SOURCE=(4,65,4)
STORAGE=(4,64,4)
CARRIER=(4,66,4)
GUEST=(6,65,4)


class ConvertDocumentWorldTests(unittest.TestCase):
    def resources(self,root:Path):
        city=root/'city';logical=root/'logical';city.mkdir(parents=True);logical.mkdir()
        (city/'document-final.json').write_text(json.dumps({'schemaVersion':1,'objects':[{
            'id':'owner_final_01','item':1,'choices':[],'components':[{
                'id':'bloodborne_blocks:city_source','properties':{'variant':'0'},'offset':[0,1,0],
                'source_state':'bloodborne_blocks:m_source[facing=north]','verified':True}],
        }]}),encoding='utf-8')
        (city/'definitions.json').write_text(json.dumps({'blocks':[
            {'id':'owner_final_01','default':{'facing':'north','root_anchor':'canonical'},'city_compat':True,'whole_owner':True},
            {'id':'city_carrier','default':{'variant':'0'},'city_compat':True},
        ]}),encoding='utf-8')
        (city/'geometry.json').write_text(json.dumps({'blocks':{
            'owner_final_01':{'states':{
                'facing=north,root_anchor=canonical':{'cells':{'0,0,0':{},'0,1,0':{}}},
                'facing=north,root_anchor=upper_1':{'render_offset':[0,-1,0],'cells':{'0,0,0':{},'0,1,0':{}}},
            }},
            'city_carrier':{'states':{'variant=0':{'cells':{'0,0,0':{}}}}},
        }}),encoding='utf-8')
        (city/'migration.json').write_text(json.dumps({'states':{}}),encoding='utf-8')
        (logical/'definitions.json').write_text(json.dumps({'blocks':[{'id':'logical_guest','default':{},'logical':True}]}),encoding='utf-8')
        (logical/'geometry.json').write_text(json.dumps({'blocks':{'logical_guest':{'states':{'':{'cells':{'-2,1,0':{}}}}}}}),encoding='utf-8')
        return city

    def source(self,root:Path,kind='valid',wrong_variant=False):
        blocks={SOURCE:('bloodborne_blocks:city_source',{'variant':'0'}),STORAGE:('minecraft:stone',{}),GUEST:('bloodborne_blocks:logical_guest',{})}
        entities=[]
        if kind=='missing_helper':blocks[CARRIER]=(PART,{})
        else:blocks[CARRIER]=('bloodborne_blocks:city_carrier',{'variant':'0'})
        if kind=='foreign_nbt':entities=[entity(CARRIER,'minecraft:chest')]
        if kind=='foreign_storage':blocks[STORAGE]=('minecraft:air',{});entities=[entity(STORAGE,'minecraft:chest')]
        if wrong_variant:blocks[(10,65,4)]=('bloodborne_blocks:city_source',{'variant':'wrong'})
        assembly_source(root,blocks,entities)
        world=World(root,{})
        if kind=='valid':world.add_shared_helper(DIMENSION,CARRIER,[('bloodborne_blocks:logical_guest',GUEST)])
        if kind=='ticks':
            chunk=world.chunks[(DIMENSION,0,0)];chunk.root()['fluid_ticks']=Tag(TAG_LIST,[entity(SOURCE,'minecraft:water')],TAG_COMPOUND);chunk.changed=True
        if kind=='target_ticks':
            chunk=world.chunks[(DIMENSION,0,0)];chunk.root()['fluid_ticks']=Tag(TAG_LIST,[entity(CARRIER,'minecraft:water')],TAG_COMPOUND);chunk.changed=True
        world.save()

    def test_rebased_document_owner_merges_city_guest_without_mutating_source(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);city=self.resources(base/'resources');source=base/'source';output=base/'output';report=base/'report.json';self.source(source,wrong_variant=True);before=tree_hash(source)
            result=convert(source,output,report,city);self.assertEqual('complete',result['status']);self.assertEqual(before,tree_hash(source));self.assertEqual(1,result['counts']['converted'])
            world=World(output,{})
            self.assertEqual(('bloodborne_blocks:owner_final_01',(('facing','north'),('root_anchor','upper_1'))),world.get(DIMENSION,SOURCE))
            self.assertEqual(('bloodborne_blocks:city_carrier',(('variant','0'),)),world.get(DIMENSION,CARRIER))
            self.assertEqual(('bloodborne_blocks:city_source',(('variant','wrong'),)),world.get(DIMENSION,(10,65,4)))
            bindings=helper_bindings(compound(world.block_entities()[(DIMENSION,*CARRIER)]))
            self.assertEqual([(SOURCE,'bloodborne_blocks:owner_final_01'),(GUEST,'bloodborne_blocks:logical_guest')],bindings)
            self.assertNotIn((CARRIER,'bloodborne_blocks:owner_final_01'),bindings);self.assertTrue(result['helpers']['ok'])
            self.assertEqual([0,-1,0],json.loads((city/'geometry.json').read_text())['blocks']['owner_final_01']['states']['facing=north,root_anchor=upper_1']['render_offset'])

    def test_refuses_missing_helper_ticks_and_foreign_carrier_nbt(self):
        expected={'missing_helper':'helper_without_binding','ticks':'source_has_scheduled_ticks','target_ticks':'target_has_scheduled_ticks','foreign_nbt':'foreign_block_entity','foreign_storage':'foreign_block_entity'}
        for kind,reason in expected.items():
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as temp:
                base=Path(temp);city=self.resources(base/'resources');source=base/'source';output=base/'output';report=base/'report.json';self.source(source,kind);before=tree_hash(source)
                if kind=='missing_helper':
                    with self.assertRaisesRegex(ValueError,'part_without_entity'):convert(source,output,report,city)
                    self.assertEqual(before,tree_hash(source));self.assertFalse(output.exists());continue
                result=convert(source,output,report,city)
                self.assertEqual(before,tree_hash(source));self.assertEqual(0,result['counts']['converted']);self.assertEqual(reason,result['unresolved'][0]['reason'])
                copied=World(output,{});self.assertEqual(('bloodborne_blocks:city_source',(('variant','0'),)),copied.get(DIMENSION,SOURCE))
                if kind=='foreign_storage':self.assertEqual('minecraft:chest',compound(copied.block_entities()[(DIMENSION,*STORAGE)])['id'].value)

    def test_output_and_report_path_guards(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);city=self.resources(base/'resources');source=base/'source';self.source(source);report=base/'report.json'
            for output,unsafe_report in ((source,report),(source/'nested',report),(base/'output',source/'report.json')):
                with self.subTest(output=output,report=unsafe_report),self.assertRaises(ValueError):convert(source,output,unsafe_report,city)

    def test_bound_existing_visual_root_is_not_used_as_new_storage(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);city=self.resources(base/'resources');source=base/'source';output=base/'output';report=base/'report.json'
            geometry=json.loads((city.parent/'logical/geometry.json').read_text())
            geometry['blocks']['logical_guest']['states']['']['cells']['-2,-1,0']={}
            (city.parent/'logical/geometry.json').write_text(json.dumps(geometry))
            self.source(source)
            world=World(source,{})
            world.set(DIMENSION,STORAGE,('bloodborne_blocks:city_carrier',(('variant','0'),)))
            world.add_shared_helper(DIMENSION,STORAGE,[('bloodborne_blocks:logical_guest',GUEST)])
            world.save()
            before=World(source,{}).block_entities()[(DIMENSION,*STORAGE)]
            result=convert(source,output,report,city)
            saved=World(output,{})
            self.assertEqual(1,result['counts']['converted'])
            self.assertEqual(('bloodborne_blocks:city_carrier',(('variant','0'),)),saved.get(DIMENSION,STORAGE))
            self.assertEqual(before,saved.block_entities()[(DIMENSION,*STORAGE)])
            self.assertEqual(list(SOURCE),result['converted'][0]['roots'][0]['position'])

    def test_doc7_plans_shared_capacity_before_mutating_any_output(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);city=self.resources(base/'resources');logical=city.parent/'logical';source=base/'source';output=base/'output';report=base/'report.json'
            document=json.loads((city/'document-final.json').read_text());document['objects'][0].update({'id':'owner_final_07','item':7});(city/'document-final.json').write_text(json.dumps(document))
            definitions=json.loads((city/'definitions.json').read_text());definitions['blocks'] += [
                {'id':'owner_final_07','default':{'facing':'north','root_anchor':'canonical'},'city_compat':True,'whole_owner':True},
                {'id':'owner_final_07_cap','default':{'facing':'north','root_anchor':'canonical'},'city_compat':True,'whole_owner':True}]
            (city/'definitions.json').write_text(json.dumps(definitions))
            geometry=json.loads((city/'geometry.json').read_text());geometry['blocks']['owner_final_07']={'states':{'facing=north,root_anchor=upper_1':{'cells':{'0,0,0':{},'0,2,0':{}}}}};geometry['blocks']['owner_final_07_cap']={'states':{'facing=north,root_anchor=upper_4':{'cells':{'0,0,0':{},'0,-1,0':{}}}}};(city/'geometry.json').write_text(json.dumps(geometry))
            logical_defs=json.loads((logical/'definitions.json').read_text());logical_geo=json.loads((logical/'geometry.json').read_text())
            owners=[]
            for index in range(15):
                ident=f'logical_guest_{index}';root=(index,65,10);owners.append(('bloodborne_blocks:'+ident,root));logical_defs['blocks'].append({'id':ident,'default':{},'logical':True});logical_geo['blocks'][ident]={'states':{'':{'cells':{f'{4-index},1,-6':{},f'{4-index},2,-6':{}}}}}
            (logical/'definitions.json').write_text(json.dumps(logical_defs));(logical/'geometry.json').write_text(json.dumps(logical_geo))
            self.source(source);world=World(source,{})
            for owner,root in owners:world.set(DIMENSION,root,(owner,()))
            chunk=world.chunks[(DIMENSION,0,0)];key,rows=chunk.entities();rows.clear();chunk.changed=True;world.set(DIMENSION,(4,67,4),('bloodborne_blocks:city_carrier',(('variant','0'),)));world.add_shared_helper(DIMENSION,CARRIER,owners);world.add_shared_helper(DIMENSION,(4,67,4),owners);world.save();before=tree_hash(source)
            result=convert(source,output,report,city)
            self.assertEqual(before,tree_hash(source));self.assertEqual(0,result['counts']['converted']);self.assertEqual('shared_owner_limit',result['unresolved'][0]['reason']);self.assertEqual(('bloodborne_blocks:city_source',(('variant','0'),)),World(output,{}).get(DIMENSION,SOURCE))


if __name__=='__main__':unittest.main()
