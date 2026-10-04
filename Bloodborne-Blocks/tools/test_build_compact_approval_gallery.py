import tempfile,unittest
from pathlib import Path
from build_compact_approval_gallery import build
from test_build_editable_city_gallery import city_fixture
from reviewed_migration_fixture import write_fixture
from convert_logical_world import World,hash_tree
from inspect_editable_city_gallery import inspect

class CompactGalleryTests(unittest.TestCase):
    def test_actual_nbt_roundtrip_and_editable_baseline(self):
        with tempfile.TemporaryDirectory()as td:
            base=Path(td);source=base/'source';out=base/'gallery'
            write_fixture(source,{(0,70,0):('yuushya:example',{})},floor=False)
            before=hash_tree(source);city=city_fixture(base)
            manifest=build(source,out,city)
            self.assertEqual(before,hash_tree(source));self.assertEqual(3,manifest['editContract']['spacing'])
            world=World(out,{})
            self.assertEqual(('yuushya:example',()),world.get('minecraft:overworld',(0,70,0)))
            for specimen in manifest['specimens']:
                self.assertGreater(specimen['padBounds'][1],70)
                for cell in specimen['expectedCells']:
                    self.assertEqual(cell['state'],world.get('minecraft:overworld',tuple(cell['absolute']))[0])
            result=inspect(out,out/'editable-gallery-manifest.json')
            self.assertTrue(all(row['classification']in {'unchanged','service-unchanged'}for row in result['specimens']))
            pads=[s['padBounds']for s in manifest['specimens']]
            for i,a in enumerate(pads):
                for b in pads[i+1:]:self.assertGreaterEqual(max(b[0]-a[3]-1,a[0]-b[3]-1,b[2]-a[5]-1,a[2]-b[5]-1),3)

    def test_reject_incomplete_resources(self):
        with tempfile.TemporaryDirectory()as td:
            with self.assertRaises(ValueError):build(Path(td)/'source',Path(td)/'out',Path(td))

if __name__=='__main__':unittest.main()
