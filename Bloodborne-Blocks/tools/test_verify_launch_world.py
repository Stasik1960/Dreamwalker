"""Real Anvil regression cases for the read-only launch-world verifier."""
from __future__ import annotations

import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from convert_logical_world import World
from test_logical_world import assembly_source, entity, tree_hash
from verify_launch_world import run
from world_io import TAG_STRING, Tag, compound


class LaunchWorldTests(unittest.TestCase):
    def resources(self,root):
        city=root/'city';logical=root/'logical';city.mkdir(parents=True);logical.mkdir()
        (city/'definitions.json').write_text(json.dumps({'blocks':[{'id':'owner','states':{'mode=ok':{}},'city_compat':True}]}))
        (city/'geometry.json').write_text(json.dumps({'blocks':{'owner':{'states':{'mode=ok':{'cells':{'0,0,0':{}}}}}}}))
        (logical/'definitions.json').write_text(json.dumps({'blocks':[]}));(logical/'geometry.json').write_text(json.dumps({'blocks':{}}))
        return city

    def fixture(self):
        temp=tempfile.TemporaryDirectory();base=Path(temp.name);city=self.resources(base/'registry');source=base/'source';target=base/'target';report=base/'report.json'
        chest=entity((2,64,2),'minecraft:chest',CustomName=Tag(TAG_STRING,'{"text":"native"}'))
        assembly_source(source,{(1,64,1):('yuushya:chair',{}),(2,64,2):('minecraft:chest',{}),(3,64,3):('minecraft:stone',{})},[chest]);shutil.copytree(source,target)
        return temp,city,source,target,report

    @staticmethod
    def save(world):
        for chunk in world.chunks.values():chunk.changed=True
        world.save()

    def test_happy_yuushya_world_preserves_source_and_reports_pass(self):
        temp,city,source,target,report=self.fixture()
        with temp:
            before=tree_hash(source);result=run(source,target,report,city)
            self.assertTrue(result['passed']);self.assertEqual(before,tree_hash(source));self.assertEqual(1,result['stats']['yuushyaBlocks']);self.assertTrue(json.loads(report.read_text())['readOnly'])

    def test_detects_foreign_block_and_typed_foreign_nbt_changes(self):
        for kind in ('block','nbt'):
            with self.subTest(kind=kind):
                temp,city,source,target,report=self.fixture()
                with temp:
                    before=tree_hash(source);world=World(target,{})
                    if kind=='block':world.set('minecraft:overworld',(3,64,3),('minecraft:dirt',{}))
                    else:compound(world.block_entities()[('minecraft:overworld',2,64,2)])['CustomName']=Tag(TAG_STRING,'{"text":"changed"}')
                    self.save(world);result=run(source,target,report,city)
                    self.assertFalse(result['passed']);self.assertEqual(before,tree_hash(source));self.assertIn('foreign_'+('block_changed' if kind=='block' else 'block_entity_changed'),{row['kind'] for row in result['errors']})

    def test_detects_duplicate_block_entities_and_invalid_target_owner_state(self):
        for kind in ('duplicate','owner_state'):
            with self.subTest(kind=kind):
                temp,city,source,target,report=self.fixture()
                with temp:
                    world=World(target,{})
                    if kind=='duplicate':
                        chunk=world.chunks[('minecraft:overworld',0,0)];key,rows=chunk.entities();rows.append(copy.deepcopy(rows[0]));chunk.changed=True
                    else:world.set('minecraft:overworld',(4,64,4),('bloodborne_blocks:owner',(('mode','bad'),)))
                    self.save(world);result=run(source,target,report,city)
                    self.assertFalse(result['passed']);self.assertIn('duplicate_block_entity_coordinate' if kind=='duplicate' else 'invalid_own_state',{row['kind'] for row in result['errors']})

    def test_report_inside_source_or_registry_is_rejected_before_write(self):
        temp,city,source,target,report=self.fixture()
        with temp:
            for unsafe in (source/'report.json',city/'report.json',city.parent/'logical'/'report.json'):
                with self.subTest(unsafe=unsafe),self.assertRaisesRegex(ValueError,'report must be outside'):run(source,target,unsafe,city)
                self.assertFalse(unsafe.exists())


if __name__=='__main__':unittest.main()
