import json,tempfile,unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch
import resume_gallery_upgrade as migration
from test_upgrade_gallery_city import UpgradeGalleryTests,NEW,DIM,ORIGIN
from upgrade_gallery_city import StreamingWorld
from convert_logical_world import hash_tree


class ResumeTests(unittest.TestCase):
    def fixture(self,base):return UpgradeGalleryTests().fixture(base)

    def test_resume_uses_committed_snapshot_and_does_not_repeat_operation(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);source,city,_,rule=self.fixture(base);before=hash_tree(source)
            args=(source,base/'out',base/'report.json',base/'progress',city)
            save=migration.Checkpoints.save
            def interrupted(checkpoints,state,world=None):
                save(checkpoints,state,world)
                if state['phase']=='apply' and state['cursor']==1:raise InterruptedError('after commit')
            with patch('resume_gallery_upgrade.compile_city_rules',return_value=([rule],{})):
                with patch.object(migration.Checkpoints,'save',interrupted):
                    with self.assertRaises(InterruptedError):migration.upgrade_resumable(*args,interval=1)
                for working in (base/'progress').glob('working-*'):
                    (working/'region/r.0.0.mca').write_bytes(b'interrupted working copy')
                report=migration.upgrade_resumable(*args,interval=1)
                self.assertEqual(1,report['counts']['converted'])
                self.assertEqual(0,report['helpers']['orphans'])
                again=migration.upgrade_resumable(*args,interval=1)
                self.assertEqual(report,again)
            self.assertEqual(before,hash_tree(source))
            self.assertEqual(b'exhibit sentinel',(base/'out/region/r.32.0.mca').read_bytes())
            city_view=base/'view';(city_view/'region').mkdir(parents=True)
            (city_view/'region/r.0.0.mca').write_bytes((base/'out/region/r.0.0.mca').read_bytes())
            self.assertEqual(NEW,StreamingWorld(city_view,{}).get(DIM,ORIGIN))

    def test_interruption_before_pointer_commit_rolls_back_whole_operation(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);source,city,_,rule=self.fixture(base)
            args=(source,base/'out',base/'report.json',base/'progress',city)
            write=migration.write_json
            def interrupted(path,value):
                if Path(path).name=='checkpoint-head.json':
                    state=migration.read_json(base/'progress'/value['state'])
                    if state['phase']=='apply' and state['cursor']==1:raise InterruptedError('before pointer')
                write(path,value)
            with patch('resume_gallery_upgrade.compile_city_rules',return_value=([rule],{})):
                with patch('resume_gallery_upgrade.write_json',side_effect=interrupted):
                    with self.assertRaises(InterruptedError):migration.upgrade_resumable(*args,interval=1)
                report=migration.upgrade_resumable(*args,interval=1)
            self.assertEqual(1,report['counts']['converted'])
            self.assertEqual([],report['overlappingChoices'])

    def test_ineligible_atomic_groups_remain_in_final_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);source,city,_,rule=self.fixture(base)
            atomic=replace(rule,number=0,atomic_owner_group=True,allowed_origins=((DIM,ORIGIN),),
                           transaction_id='frozen-owner',preflight_error='missing_runtime_owner')
            with patch('resume_gallery_upgrade.compile_city_rules',return_value=([atomic,rule],{})):
                report=migration.upgrade_resumable(source,base/'out',base/'report.json',base/'progress',city,interval=1)
            self.assertEqual(0,report['counts']['converted'])
            self.assertIn('missing_runtime_owner',report['counts']['reasons'])
            self.assertIn('reserved_by_atomic_owner_group',report['counts']['reasons'])


if __name__=='__main__':unittest.main()
