import json,shutil,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import test_upgrade_gallery_city as fixtures
from upgrade_gallery_city import StreamingWorld,apply_rule
from convert_logical_world import World,PART
from convert_document_world import add_owner_binding
from city_palette import audit_helpers,helper_bindings
from retire_gallery_helpers import repair_chunk,frozen_city_view,finish_recorded
from resume_gallery_upgrade import Checkpoints
from convert_logical_world import hash_tree
from world_io import compound


class RetireTests(unittest.TestCase):
    def fixture(self,base):
        source,city,defs,rule=fixtures.UpgradeGalleryTests().fixture(base,'source_guest')
        (source/'region/r.32.0.mca').unlink()
        geo=json.loads((city/'geometry.json').read_bytes())
        geo['blocks']['city_old']['states']['']['cells']={f'{i},0,0':{} for i in (-1,0,1,4)}
        (city/'geometry.json').write_text(json.dumps(geo))
        world=World(source,{})
        world.set(fixtures.DIM,(3,65,4),(PART,()))
        world.add_helper(fixtures.DIM,(3,65,4),fixtures.ORIGIN,fixtures.OLD[0])
        world.set(fixtures.DIM,(8,65,4),fixtures.CARRIER)
        world.add_helper(fixtures.DIM,(8,65,4),fixtures.ORIGIN,fixtures.OLD[0])
        add_owner_binding(world,world.block_entities(),fixtures.DIM,(5,65,4),fixtures.ORIGIN,fixtures.OLD[0])
        world.save();self.assertTrue(audit_helpers(World(source,{}),city)['ok'])
        baseline=base/'baseline';shutil.copytree(source,baseline)
        world=StreamingWorld(source,{})
        self.assertIsNone(apply_rule(world,world.block_entities(),fixtures.DIM,fixtures.ORIGIN,rule,{d['id']:d for d in defs}))
        world.save()
        return source,baseline,city

    def test_only_retired_links_are_removed_guest_and_visual_carrier_survive(self):
        with tempfile.TemporaryDirectory() as directory:
            source,baseline,city=self.fixture(Path(directory));world=StreamingWorld(source,{})
            counts,_=repair_chunk(world,StreamingWorld(baseline,{}),(fixtures.DIM,0,0),{(fixtures.DIM,fixtures.ORIGIN):{fixtures.OLD}})
            world.save();saved=StreamingWorld(source,{})
            self.assertEqual(3,counts['bindingsRemoved'])
            self.assertEqual(('minecraft:air',()),saved.get(fixtures.DIM,(3,65,4)))
            self.assertEqual(fixtures.CARRIER,saved.get(fixtures.DIM,(8,65,4)))
            self.assertNotIn((fixtures.DIM,8,65,4),saved.block_entities())
            bindings=helper_bindings(compound(saved.block_entities()[(fixtures.DIM,5,65,4)]))
            self.assertIn(((7,65,4),fixtures.GUEST[0]),bindings)
            self.assertIn((fixtures.ORIGIN,fixtures.NEW[0]),bindings)
            self.assertNotIn((fixtures.ORIGIN,fixtures.OLD[0]),bindings)
            self.assertTrue(audit_helpers(saved,city)['ok'])
            repeat,_=repair_chunk(saved,StreamingWorld(baseline,{}),(fixtures.DIM,0,0),{(fixtures.DIM,fixtures.ORIGIN):{fixtures.OLD}})
            self.assertEqual({},dict(repeat))

    def test_missing_owner_without_successful_operation_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            source,baseline,_=self.fixture(Path(directory));world=StreamingWorld(source,{})
            with self.assertRaisesRegex(ValueError,'unproven missing'):
                repair_chunk(world,StreamingWorld(baseline,{}),(fixtures.DIM,0,0),{})

    def test_nonoriginal_binding_is_not_silently_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            source,baseline,_=self.fixture(Path(directory));world=StreamingWorld(source,{})
            world.set(fixtures.DIM,(9,65,4),(PART,()));world.add_helper(fixtures.DIM,(9,65,4),fixtures.ORIGIN,fixtures.OLD[0]);world.save()
            with self.assertRaisesRegex(ValueError,'absent from original'):
                repair_chunk(world,StreamingWorld(baseline,{}),(fixtures.DIM,0,0),{(fixtures.DIM,fixtures.ORIGIN):{fixtures.OLD}})

    def test_interrupted_baseline_copy_can_resume_without_partial_final_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);source,_,_,_=fixtures.UpgradeGalleryTests().fixture(base)
            progress=base/'progress';progress.mkdir();paths=[Path('region/r.0.0.mca')]
            expected={p.as_posix():hash_tree(source)[p.as_posix()] for p in paths};expected['level.dat']=hash_tree(source)['level.dat']
            copy=shutil.copy2
            def interrupted(source_file,target_file):
                if Path(source_file).suffix=='.mca':raise InterruptedError('during evidence copy')
                return copy(source_file,target_file)
            with patch('retire_gallery_helpers.shutil.copy2',side_effect=interrupted):
                with self.assertRaises(InterruptedError):frozen_city_view(source,progress,paths,expected)
            self.assertFalse((progress/'original-city').exists())
            result=frozen_city_view(source,progress,paths,expected)
            self.assertEqual(expected,hash_tree(result))

    def test_changed_cleanup_implementation_is_rejected_before_any_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);source,city,_,_=fixtures.UpgradeGalleryTests().fixture(base)
            view=base/'view';(view/'region').mkdir(parents=True)
            shutil.copy2(source/'level.dat',view/'level.dat');shutil.copy2(source/'region/r.0.0.mca',view/'region/r.0.0.mca')
            progress=base/'progress';output=base/'out';report=base/'report.json'
            state={'source':str(source.resolve()),'output':str(output.resolve()),'reportPath':str(report.resolve()),
                   'sourceHashes':hash_tree(source),'phase':'cleanup','cursor':0,
                   'report':{'converted':[],'followUpPasses':[0],'legacyHelperCleanup':{'implementationSha256':'wrong'}}}
            Checkpoints(progress).save(state,StreamingWorld(view,{}))
            with self.assertRaisesRegex(ValueError,'cleanup implementation changed'):
                finish_recorded(source,output,report,progress,city)
            self.assertFalse(output.exists())


if __name__=='__main__':unittest.main()
