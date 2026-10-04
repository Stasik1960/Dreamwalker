import json, tempfile, unittest, sys,zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from audit_persisted_variants import audit, migrate_nbt
from world_io import NbtFile, Tag, TAG_COMPOUND, TAG_STRING, RegionFile

def C(**kw): return Tag(TAG_COMPOUND, {k: (v if isinstance(v, Tag) else Tag(TAG_STRING, v)) for k,v in kw.items()})

class AuditTests(unittest.TestCase):
    def test_empty_poi_requires_exact_original_archive_path(self):
        with tempfile.TemporaryDirectory()as d:
            root=Path(d)/'world';(root/'poi').mkdir(parents=True)
            (root/'poi/r.0.0.mca').write_bytes(b'');reference=Path(d)/'reference.zip'
            with zipfile.ZipFile(reference,'w')as archive:archive.writestr('Original/poi/r.0.0.mca',b'')
            report=audit(root,empty_poi_reference=reference)
            self.assertEqual(['poi/r.0.0.mca'],report['emptyPoiReference']['allowedPaths'])
            self.assertIn(str(reference.resolve()),report['hashInputs'])
            (root/'poi/r.1.0.mca').write_bytes(b'')
            with self.assertRaises(ValueError):audit(root,empty_poi_reference=reference)
    def test_partial_saved_item_uses_unique_variant_target(self):
        original=C(id='bloodborne_blocks:lamp',tag=C(BlockStateTag=C(variant='2')))
        migration={'states':{'bloodborne_blocks:lamp[facing=east,root_anchor=canonical,variant=2]':
                            {'id':'bloodborne_blocks:lamp','properties':{'variant':'0'}}}}
        out,changes=migrate_nbt(NbtFile('',original),migration)
        self.assertEqual('0',out.root.value['tag'].value['BlockStateTag'].value['variant'].value)
        self.assertEqual('2',original.value['tag'].value['BlockStateTag'].value['variant'].value)
        self.assertEqual(1,len(changes))

    def test_empty_entity_region_is_valid_empty_audit_is_not(self):
        with tempfile.TemporaryDirectory()as d:
            with self.assertRaises(ValueError):audit(d)
            (Path(d)/'entities').mkdir();(Path(d)/'entities/r.0.0.mca').write_bytes(b'')
            self.assertEqual(1,audit(d)['counts']['files'])
            for folder in('region','poi'):
                (Path(d)/folder).mkdir();p=Path(d)/folder/'r.0.0.mca';p.write_bytes(b'')
                with self.assertRaises(ValueError):audit(d)
                p.unlink()

    def test_command_and_jigsaw_strings_protect_variants(self):
        root=C(Command='setblock ~ ~ ~ bloodborne_blocks:wall[facing=east,variant=72]',
               final_state='bloodborne_blocks:wall[variant=8]',give='give @p bloodborne_blocks:wall{BlockStateTag:{variant:"19"}}')
        with tempfile.TemporaryDirectory()as d:
            from world_io import write_nbt
            write_nbt(Path(d)/'x.nbt',NbtFile('',root));report=audit(d)
        self.assertEqual(['19','72','8'],report['variantReferences']['bloodborne_blocks:wall'])
    def test_structure_and_schem_palette(self):
        root=C(Palette=Tag(TAG_COMPOUND, {'bloodborne_blocks:wall[variant=3]': Tag(3, 0)}), Name='bloodborne_blocks:wall', Properties=C(variant='2'))
        with tempfile.TemporaryDirectory() as d:
            from world_io import write_nbt
            write_nbt(Path(d)/'x.schem', NbtFile('', root), compressed=None)
            report=audit(d)
        self.assertEqual(report['variantReferences']['bloodborne_blocks:wall'], ['2', '3'])

    def test_region_roundtrip(self):
        root=C(Name='bloodborne_blocks:wall', Properties=C(variant='4'))
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'r.0.0.mca'; region=RegionFile(); region.set_chunk(0,0,NbtFile('',root)); p.write_bytes(region.to_bytes())
            report=audit(d)
        self.assertEqual(report['variantReferences']['bloodborne_blocks:wall'], ['4'])

    def test_nested_item_and_native_are_reported(self):
        root=C(id='minecraft:chest', Items=Tag(9,[C(id='bloodborne_blocks:lamp', tag=C(BlockStateTag=C(variant='removed'))), C(id='yuushya:chair', tag=C(BlockStateTag=C(variant='native')))], TAG_COMPOUND))
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x.nbt'; from world_io import write_nbt; write_nbt(p,NbtFile('',root),compressed=None)
            report=audit(d)
        self.assertEqual(report['variantReferences']['bloodborne_blocks:lamp'], ['removed'])
        self.assertEqual(report['variantReferences']['yuushya:chair'], ['native'])

    def test_migration_clones_and_preserves_tags(self):
        original=C(id='bloodborne_blocks:lamp', Count='1', BlockStateTag=C(variant='removed', facing='east'), Custom='keep')
        out, changes=migrate_nbt(NbtFile('', original), {'states': {'bloodborne_blocks:lamp[variant=removed,facing=east]': {'id':'bloodborne_blocks:lamp','properties':{'variant':'default','facing':'east'}}}})
        self.assertEqual(original.value['BlockStateTag'].value['variant'].value, 'removed')
        self.assertEqual(out.root.value['BlockStateTag'].value['variant'].value, 'default')
        self.assertEqual(out.root.value['Custom'].value, 'keep'); self.assertEqual(len(changes), 1)

    def test_malformed_input_fails(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'bad.nbt'; p.write_bytes(b'bad')
            with self.assertRaises(ValueError): audit(d)

if __name__ == '__main__': unittest.main()
