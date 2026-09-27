"""Adversarial tests for the independent verifier, including forged manifests."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from test_composite_retirement import make_source, retire
from retire_missing_composites import approved_rows, file_hash
from verify_composite_retirement import verify, file_manifest
from world_io import (RegionFile, Tag, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG,
                      TAG_LONG_ARRAY, TAG_STRING, compound, pack_palette_indices, section_blocks)


class RetirementVerifierTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.source = make_source(self.base)
        self.out = self.base/'retired'
        self.ledger = self.base/'ledger.json'
        self.sha = file_hash(self.source)
        retire(self.source, self.out, self.ledger, expected_source_sha256=self.sha)

    def check(self):
        # No modded terrain remains in the synthetic source. Real production
        # verification loads frozen/current registries, not this test override.
        with patch('verify_composite_retirement.SOURCE_DIGEST', self.sha), patch('verify_composite_retirement.knowledge',
                   return_value=({'bloodborne_blocks:architecture_part'}, {'bloodborne_blocks:architecture_part'}, [], [])):
            return verify(self.source, self.out, self.ledger, self.sha)

    def rewrite_proof(self, transform=None):
        data = json.loads(self.ledger.read_bytes())
        data['outputManifest'] = file_manifest(self.out)
        if transform:
            transform(data)
        self.ledger.write_text(json.dumps(data), encoding='utf-8')

    def edit_chunk(self, change):
        row = approved_rows()[0]
        x,y,z = row['position']
        name = f'region/r.{x//512}.{z//512}.mca'
        region = RegionFile.open(self.out/name)
        chunk = region.get_chunk(x//16%32,z//16%32)
        nbt = chunk.nbt()
        change(compound(nbt.root), row['position'])
        region.set_chunk(chunk.x,chunk.z,nbt,compression=chunk.compression,timestamp=chunk.timestamp)
        region.save(self.out/name)
        self.rewrite_proof()

    def test_full_33_cell_fixture_passes_and_repeat_verification_is_read_only(self):
        before = file_manifest(self.out)
        for _ in range(2):
            report = self.check()
            self.assertEqual(report['result'],'PASS', report)
            self.assertEqual(report['retiredCells'],33)
            self.assertEqual(report['changedChunks'],21)
            self.assertEqual(report['orphanHelpers'],0)
            self.assertEqual(report['unknownModPaletteIdCount'],0)
        self.assertEqual(before,file_manifest(self.out))

    def test_player_mod_bytes_and_file_membership_are_independently_protected(self):
        (self.out/'othermod/data.dat').write_bytes(b'tampered')
        self.rewrite_proof()
        with self.assertRaisesRegex(ValueError,'Non-terrain'):
            self.check()
        (self.out/'othermod/data.dat').write_bytes(b'untouched-other-mod-data')
        (self.out/'extra.txt').write_text('unexpected')
        self.rewrite_proof()
        with self.assertRaisesRegex(ValueError,'membership'):
            self.check()

    def test_arbitrary_root_nbt_cannot_hide_behind_forged_file_manifest(self):
        self.edit_chunk(lambda root,pos: root.update(OtherModData=Tag(TAG_STRING,'tampered')))
        with self.assertRaisesRegex(ValueError,'typed chunk'):
            self.check()

    def test_lighting_and_scheduled_ticks_are_not_exempted(self):
        self.edit_chunk(lambda root,pos: root.update(isLightOn=Tag(TAG_INT,0)))
        with self.assertRaisesRegex(ValueError,'typed chunk'):
            self.check()

    def test_neighbor_change_is_detected_even_with_updated_chunk_and_file_hashes(self):
        def mutate(root,pos):
            section = next(s for s in root['sections'].value if compound(s)['Y'].value == pos[1]//16)
            state = compound(compound(section)['block_states'])
            palette, values = section_blocks(section)
            values[0] = len(palette)
            palette.append(Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'minecraft:diamond_block')}))
            state['data'] = Tag(TAG_LONG_ARRAY,pack_palette_indices(values,len(palette)))
        self.edit_chunk(mutate)
        with self.assertRaisesRegex(ValueError,'Terrain change'):
            self.check()

    def test_ledger_position_reason_and_chunk_hash_cannot_be_forged(self):
        self.rewrite_proof(lambda data:data['ledger'][0].update(reason='OTHER'))
        with self.assertRaisesRegex(ValueError,'ledger'):
            self.check()
        self.rewrite_proof(lambda data:data['ledger'][0].update(reason='USER_APPROVED_RETIREMENT'))
        self.rewrite_proof(lambda data:data['changedChunks'][0].update(afterSha256='0'*64))
        with self.assertRaisesRegex(ValueError,'Changed-chunk'):
            self.check()

    def test_source_hash_and_missing_target_row_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'SHA mismatch'):
            verify(self.source,self.out,self.ledger,'0'*64)
        self.rewrite_proof(lambda data:data['ledger'].pop())
        with self.assertRaisesRegex(ValueError,'ledger'):
            self.check()

    def test_production_verifier_rejects_another_zip_with_its_own_sha(self):
        with self.assertRaisesRegex(ValueError,'SHA mismatch'):
            verify(self.source,self.out,self.ledger,self.sha)

    def test_valid_root_owner_id_does_not_prove_helper_offset(self):
        from verify_composite_retirement import owns_offset
        definitions={'door':{'default':{'half':'lower'},'kind':'door'}}
        geometry={'blocks':{'door':{'states':{'half=lower':{'cells':{'0,0,0':{},'1,0,0':{},'0,1,0':{}}}}}}}
        schemas=[(definitions,geometry)]
        self.assertTrue(owns_offset(schemas,'bloodborne_blocks:door[half=lower]',(1,0,0)))
        self.assertFalse(owns_offset(schemas,'bloodborne_blocks:door[half=lower]',(9,0,0)))
        self.assertFalse(owns_offset(schemas,'bloodborne_blocks:door[half=lower]',(0,1,0)))
        self.assertFalse(owns_offset(schemas,'bloodborne_blocks:door',(0,1,0)))

    def test_legacy_schema_is_authoritative_before_conversion(self):
        from verify_composite_retirement import owns_offset
        definitions={'shared_id':{'default':{},'kind':'generic'}}
        def schema(offset):
            return definitions, {'blocks':{'shared_id':{'states':{'':{'cells':{offset:{}}}}}}}
        legacy, current = schema('1,0,0'), schema('2,0,0')
        self.assertTrue(owns_offset([legacy,current],'bloodborne_blocks:shared_id',(1,0,0)))
        self.assertFalse(owns_offset([legacy,current],'bloodborne_blocks:shared_id',(2,0,0)))

    def test_orphan_and_unknown_palette_are_actually_scanned(self):
        # Add an untouched region to BOTH source and result: its orphan/unknown
        # cannot be caught solely by comparing mutation effects.
        import zipfile
        from world_io import NbtFile
        part='bloodborne_blocks:architecture_part'
        palette=[Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,part)}),
                 Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,'bloodborne_blocks:not_known')})]
        root={'xPos':Tag(TAG_INT,128),'zPos':Tag(TAG_INT,128),
              'sections':Tag(TAG_LIST,[Tag(TAG_COMPOUND,{'Y':Tag(1,4),'block_states':Tag(TAG_COMPOUND,{
                  'palette':Tag(TAG_LIST,palette,TAG_COMPOUND),'data':Tag(TAG_LONG_ARRAY,pack_palette_indices([0]*4096,2))})})],TAG_COMPOUND),
              'block_entities':Tag(TAG_LIST,[],TAG_COMPOUND)}
        region=RegionFile();region.set_chunk(0,0,NbtFile('',Tag(TAG_COMPOUND,root)),timestamp=5)
        name='region/r.4.4.mca'; payload=region.to_bytes()
        with zipfile.ZipFile(self.source,'a') as archive:archive.writestr('fixture/'+name,payload)
        (self.out/name).write_bytes(payload)
        self.sha=file_hash(self.source)
        import hashlib
        self.rewrite_proof(lambda data:(data.update(sourceZipSha256=self.sha),data['sourceManifest'].update({name:hashlib.sha256(payload).hexdigest()})))
        report=self.check()
        self.assertEqual(report['result'],'FAIL')
        self.assertEqual(report['orphanHelpers'],4096)
        self.assertEqual(report['unknownModPaletteIds'],['bloodborne_blocks:not_known'])
        self.assertEqual(report['additionalTerrainChanges'],0)


if __name__=='__main__':
    unittest.main()
