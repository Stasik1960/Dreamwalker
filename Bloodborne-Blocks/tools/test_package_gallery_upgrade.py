import hashlib,json,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
import test_upgrade_gallery_city as fixtures
from convert_logical_world import hash_tree
from package_gallery_upgrade import package
from publish_launch_bundle import public_world


class PackageTests(unittest.TestCase):
    def fixture(self,base):
        world,_,_,_=fixtures.UpgradeGalleryTests().fixture(base)
        (world/'Gallery-Index.md').write_text('index',encoding='utf8')
        (world/'City-Upgrade.md').write_text('changes',encoding='utf8')
        manifest=json.loads((world/'gallery-manifest.json').read_bytes());manifest['cityUpgrade']={'counts':{'converted':0,'rejected':0}}
        (world/'gallery-manifest.json').write_text(json.dumps(manifest),encoding='utf8')
        report=base/'migration.json';verification=base/'verification.json'
        report.write_text(json.dumps({'status':'complete','followUpPasses':[0],'converted':[],'rejected':[],
            'overlappingChoices':[],'ownerChoices':[],'documentChoices':[],'counts':{'converted':0,'rejected':0}}),encoding='utf8')
        checked={'passed':True,'targetHashes':hash_tree(world),'migrationReportSha256':hashlib.sha256(report.read_bytes()).hexdigest(),
                 'helpers':{'checked':0,'orphans':0},'stats':{'yuushyaBlocks':0,'foreignBlocks':0,'foreignBlockEntities':0}}
        verification.write_text(json.dumps(checked),encoding='utf8')
        return world,report,verification,base/'release'

    def test_packaged_report_keeps_verified_bytes_even_if_input_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            args=self.fixture(Path(temporary));original=args[1].read_bytes()
            def changed_input(payload):
                args[1].write_text('changed during archive creation',encoding='utf8')
                return public_world(payload)
            with patch('package_gallery_upgrade.public_world',side_effect=changed_input):result=package(*args)
            self.assertEqual(original,(args[3]/'Gallery-Upgrade-Report.json').read_bytes())
            checked=json.loads((args[3]/'World-Verification.json').read_bytes())
            self.assertEqual(checked['migrationReportSha256'],hashlib.sha256((args[3]/'Gallery-Upgrade-Report.json').read_bytes()).hexdigest())
            self.assertEqual(result['sha256'],hashlib.sha256((args[3]/'Approval-Gallery-Updated-City.zip').read_bytes()).hexdigest())

    def test_unverified_world_never_creates_release_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            args=self.fixture(Path(temporary));(args[0]/'Gallery-Index.md').write_text('modified',encoding='utf8')
            with self.assertRaisesRegex(ValueError,'saved world'):package(*args)
            self.assertFalse(args[3].exists())

    def test_corrupt_archive_never_creates_release_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            args=self.fixture(Path(temporary))
            with patch('package_gallery_upgrade.public_world',return_value=(b'invalid zip',{})):
                with self.assertRaises(zipfile.BadZipFile):package(*args)
            self.assertFalse(args[3].exists())


if __name__=='__main__':unittest.main()
