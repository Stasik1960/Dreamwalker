import tempfile,unittest,json,zipfile
from unittest.mock import patch
from pathlib import Path
import numpy as np
from collections import Counter
from convert_compact_city import (clean_chunk, place_owner, place_single_cell_batch, require_chunk_coverage,
    audit_saved_world, AIR, DIM)
from build_editable_city_gallery import GalleryWorld
from reviewed_migration_fixture import write_fixture
from convert_logical_world import World, PART, block_pos_long
from world_io import compound,Tag,TAG_COMPOUND,TAG_STRING,TAG_INT,TAG_LONG
from test_build_editable_city_gallery import city_fixture
from convert_compact_city import convert

class CompactConversionTests(unittest.TestCase):
    def test_gallery_cleanup_keeps_high_foreign_blocks_outside_gallery_bounds(self):
        with tempfile.TemporaryDirectory()as td:
            path=Path(td)/'world';write_fixture(path,{(0,240,0):('minecraft:smooth_stone',{}),
                (8,240,8):('yuushya:example',{})},floor=False)
            w=GalleryWorld(path,{});clean_chunk(w.chunk(DIM,0,0),set(),228,[-1,228,-1,2,319,2]);w.save()
            loaded=World(path,{})
            self.assertEqual(AIR,loaded.get(DIM,(0,240,0)))
            self.assertEqual(('yuushya:example',()),loaded.get(DIM,(8,240,8)))

    def test_full_converter_checkpoint_and_input_identity(self):
        with tempfile.TemporaryDirectory()as td:
            base=Path(td);source=base/'source';original=base/'original';out=base/'output'
            write_fixture(source,{(0,70,0):('bloodborne_blocks:city_a',{}),(1,70,0):('yuushya:example',{})},floor=False)
            write_fixture(original,{(0,70,0):('minecraft:stone',{})},floor=False)
            (source/'entities').mkdir();(source/'entities'/'r.0.0.mca').write_bytes(b'entity')
            (source/'poi').mkdir();(source/'poi'/'r.0.0.mca').write_bytes(b'poi')
            from convert_logical_world import hash_tree
            manifest={'sourceHashes':hash_tree(source),'galleryBand':{'minY':228,'bounds':[-10,228,-10,10,319,10]},'cityBounds':[-10,0,-10,10,70,10]}
            latest=base/'latest.zip';rawzip=base/'raw.zip'
            with zipfile.ZipFile(latest,'w')as z:
                for p in source.rglob('*'):
                    if p.is_file():z.write(p,'world/'+p.relative_to(source).as_posix())
                z.writestr('world/editable-gallery-manifest.json',json.dumps(manifest))
            with zipfile.ZipFile(rawzip,'w')as z:
                for p in (original/'region').glob('*.mca'):z.write(p,'ether/dimensions/eh_s2/yharnam/region/'+p.name)
            catalog=city_fixture(base)
            old=base/'old-resources'/'city';old.parent.mkdir(parents=True)
            import shutil
            shutil.copytree(catalog,old);shutil.copytree(catalog.parent/'logical',old.parent/'logical')
            data=json.loads((old/'definitions.json').read_bytes());data['blocks'][0]['unified']=True
            (old/'definitions.json').write_text(json.dumps(data))
            (catalog/'raw-source-mapping.json').write_text(json.dumps({'states':{'minecraft:stone':{'id':'bloodborne_blocks:city_b','properties':{}}}}))
            oracle=base/'oracle.json';oracle.write_text('{"occurrences":[]}')
            proof=convert(latest,rawzip,out,catalog,old,oracle)
            self.assertTrue(proof['complete']);self.assertEqual(1,proof['counts']['wholeOwners'])
            loaded=World(out,{})
            self.assertEqual(('bloodborne_blocks:city_b',()),loaded.get(DIM,(0,70,0)))
            self.assertEqual(('yuushya:example',()),loaded.get(DIM,(1,70,0)))
            (catalog/'definitions.json').write_text((catalog/'definitions.json').read_text()+' ')
            with self.assertRaisesRegex(ValueError,'contents changed'):convert(latest,rawzip,out,catalog,old,oracle,resume=True)

    def test_resume_rejects_entity_mutation_and_poi_deletion(self):
        with tempfile.TemporaryDirectory()as td:
            base=Path(td);source=base/'source';original=base/'original';out=base/'output'
            write_fixture(source,{(0,70,0):('bloodborne_blocks:city_a',{})},floor=False);write_fixture(original,{(0,70,0):('minecraft:stone',{})},floor=False)
            (source/'entities').mkdir();(source/'entities'/'r.0.0.mca').write_bytes(b'entity');(source/'poi').mkdir();(source/'poi'/'r.0.0.mca').write_bytes(b'poi')
            from convert_logical_world import hash_tree
            manifest={'sourceHashes':hash_tree(source),'galleryBand':{'minY':228,'bounds':[-10,228,-10,10,319,10]},'cityBounds':[-10,0,-10,10,70,10]}
            latest=base/'latest.zip';rawzip=base/'raw.zip'
            with zipfile.ZipFile(latest,'w')as z:
                for p in source.rglob('*'):
                    if p.is_file():z.write(p,'world/'+p.relative_to(source).as_posix())
                z.writestr('world/editable-gallery-manifest.json',json.dumps(manifest))
            with zipfile.ZipFile(rawzip,'w')as z:
                for p in (original/'region').glob('*.mca'):z.write(p,'ether/dimensions/eh_s2/yharnam/region/'+p.name)
            catalog=city_fixture(base);old=base/'old'/'city';old.parent.mkdir(parents=True)
            import shutil;shutil.copytree(catalog,old);shutil.copytree(catalog.parent/'logical',old.parent/'logical')
            data=json.loads((old/'definitions.json').read_bytes());data['blocks'][0]['unified']=True;(old/'definitions.json').write_text(json.dumps(data))
            (catalog/'raw-source-mapping.json').write_text(json.dumps({'states':{'minecraft:stone':{'id':'bloodborne_blocks:city_b','properties':{}}}}));oracle=base/'oracle.json';oracle.write_text('{"occurrences":[]}')
            convert(latest,rawzip,out,catalog,old,oracle)
            (out/'entities'/'r.0.0.mca').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'output contents changed'):convert(latest,rawzip,out,catalog,old,oracle,resume=True)
            (out/'entities'/'r.0.0.mca').write_bytes(b'entity');(out/'poi'/'r.0.0.mca').unlink()
            with self.assertRaisesRegex(ValueError,'output contents changed'):convert(latest,rawzip,out,catalog,old,oracle,resume=True)

    def test_chunk_coverage_and_malformed_helper_audit_fail_closed(self):
        with tempfile.TemporaryDirectory()as td:
            base=Path(td);source=base/'source';target=base/'target'
            write_fixture(source,{(0,70,0):('minecraft:stone',{})},floor=False);write_fixture(target,{(16,70,0):('minecraft:stone',{})},floor=False)
            from upgrade_gallery_city import StreamingWorld
            with self.assertRaisesRegex(ValueError,'coverage mismatch'):require_chunk_coverage(StreamingWorld(source,{},cache_limit=32),StreamingWorld(target,{},cache_limit=32))
            with patch('convert_compact_city.audit_helpers',return_value={'checked':1,'orphans':[{'reason':'malformed_owner_bindings'}],'ok':False}):
                with self.assertRaisesRegex(ValueError,'helper audit failed'):audit_saved_world(source,{},base)

    def test_cleanup_removes_fragments_but_preserves_foreign_and_manual(self):
        with tempfile.TemporaryDirectory()as td:
            path=Path(td)/'world';old='bloodborne_blocks:old';keep='bloodborne_blocks:o_tree'
            write_fixture(path,{(0,70,0):(old,{}),(1,70,0):('yuushya:example',{}),
                (2,70,0):(keep,{}),(3,70,0):(PART,{}),(4,70,0):(PART,{}),
                (5,228,0):('minecraft:smooth_stone',{})},floor=False)
            w=GalleryWorld(path,{})
            w.add_helper(DIM,(3,70,0),(2,70,0),keep)
            w.add_helper(DIM,(4,70,0),(0,70,0),old)
            clean_chunk(w.chunk(DIM,0,0),{keep},228);w.save();w=World(path,{})
            self.assertEqual(AIR,w.get(DIM,(0,70,0)))
            self.assertEqual(('yuushya:example',()),w.get(DIM,(1,70,0)))
            self.assertEqual((keep,()),w.get(DIM,(2,70,0)))
            self.assertEqual((PART,()),w.get(DIM,(3,70,0)))
            self.assertEqual(AIR,w.get(DIM,(4,70,0)));self.assertEqual(AIR,w.get(DIM,(5,228,0)))
            self.assertEqual(1,len(w.block_entities()))

    def test_force_overlap_preserves_both_roots_and_foreign_collision_cell(self):
        with tempfile.TemporaryDirectory()as td:
            path=Path(td)/'world';write_fixture(path,{(0,70,0):('minecraft:air',{}),
                (3,70,0):('yuushya:example',{})},floor=False)
            w=GalleryWorld(path,{});entities=w.block_entities();counts=Counter();cases=[]
            a=('bloodborne_blocks:owner_a',());b=('bloodborne_blocks:owner_b',())
            self.assertTrue(place_owner(w,entities,(0,70,0),a,[(0,0,0),(1,0,0),(3,0,0)],counts,cases))
            self.assertTrue(place_owner(w,entities,(2,70,0),b,[(0,0,0),(-1,0,0)],counts,cases))
            w.save();loaded=World(path,{})
            self.assertEqual(a,loaded.get(DIM,(0,70,0)));self.assertEqual(b,loaded.get(DIM,(2,70,0)))
            self.assertEqual(('yuushya:example',()),loaded.get(DIM,(3,70,0)))
            helper=compound(loaded.block_entities()[(DIM,1,70,0)])
            self.assertEqual(2,len(helper['Owners'].value));self.assertEqual(1,counts['foreignCollisionCells'])

    def test_batch_skips_foreign_existing_roots_and_protected_source_members(self):
        with tempfile.TemporaryDirectory()as td:
            path=Path(td)/'world';write_fixture(path,{(0,70,0):('minecraft:air',{}),
                (1,70,0):('yuushya:example',{}),(2,70,0):('bloodborne_blocks:o_tree',{}),
                (3,70,0):('minecraft:air',{})},floor=False)
            w=GalleryWorld(path,{});chunk=w.chunk(DIM,0,0);counts=Counter()
            indices=np.array([chunk._index(x,70,0)for x in range(4)])
            target=('bloodborne_blocks:owner_new',())
            place_single_cell_batch(chunk,70//16,indices,target,{int(indices[3])},counts);w.save()
            loaded=World(path,{})
            self.assertEqual(target,loaded.get(DIM,(0,70,0)))
            self.assertEqual(('yuushya:example',()),loaded.get(DIM,(1,70,0)))
            self.assertEqual(('bloodborne_blocks:o_tree',()),loaded.get(DIM,(2,70,0)))
            self.assertEqual(AIR,loaded.get(DIM,(3,70,0)));self.assertEqual(1,counts['wholeOwners'])

if __name__=='__main__':unittest.main()
