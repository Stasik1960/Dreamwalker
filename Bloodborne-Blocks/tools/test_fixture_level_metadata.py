"""Metadata regressions only. No batch-02 regeneration or existing-world writes."""
import tempfile
import unittest
from pathlib import Path

from fixture_level_metadata import create_level_metadata, validate_level_metadata, TEMPLATE
from reviewed_migration_fixture import write_fixture
from world_io import compound, read_nbt, Tag, TAG_INT, TAG_LONG, TAG_LIST, TAG_STRING


class LevelMetadataTests(unittest.TestCase):
    def test_official_template_and_generated_metadata(self):
        before = TEMPLATE.read_bytes()
        metadata = create_level_metadata((12, 66, -20), level_name='Metadata regression', last_played=1234567890000)
        validate_level_metadata(metadata)
        data = compound(compound(metadata.root)['Data'])
        self.assertEqual(data['version'].value, 19133)
        self.assertEqual(data['DataVersion'].value, 3465)
        self.assertEqual(data['LastPlayed'].value, 1234567890000)
        self.assertEqual(data['LevelName'].value, 'Metadata regression')
        self.assertEqual([data[key].value for key in ('SpawnX', 'SpawnY', 'SpawnZ')], [12, 66, -20])
        self.assertEqual(TEMPLATE.read_bytes(), before)

    def test_missing_required_metadata_rejected(self):
        for key in ('version', 'DataVersion', 'Version', 'LastPlayed', 'DataPacks', 'enabled_features',
                    'LevelName', 'GameType', 'allowCommands', 'SpawnX', 'WorldGenSettings', 'GameRules'):
            with self.subTest(key=key):
                metadata = create_level_metadata((0, 64, 0))
                del compound(compound(metadata.root)['Data'])[key]
                with self.assertRaises(ValueError): validate_level_metadata(metadata)

    def test_storage_and_game_version_are_not_interchangeable(self):
        for tag in (Tag(TAG_INT, 3465), Tag(TAG_LONG, 19133)):
            metadata = create_level_metadata((0, 64, 0))
            compound(compound(metadata.root)['Data'])['version'] = tag
            with self.assertRaises(ValueError): validate_level_metadata(metadata)

    def test_experimental_or_external_pack_dependency_rejected(self):
        for key in ('DataPacks', 'enabled_features'):
            metadata = create_level_metadata((0, 64, 0))
            data = compound(compound(metadata.root)['Data'])
            if key == 'DataPacks':
                compound(data[key])['Enabled'].value.append(Tag(TAG_STRING, 'fabric'))
            else:
                data[key] = Tag(TAG_LIST, [Tag(TAG_STRING, 'minecraft:bundle')], TAG_STRING)
            with self.assertRaises(ValueError): validate_level_metadata(metadata)

    def test_writer_emits_gzip_metadata_and_standard_dimension_set(self):
        with tempfile.TemporaryDirectory(prefix='bloodborne-level-metadata-') as folder:
            path = Path(folder)
            write_fixture(path, {(8, 64, 8): ('minecraft:stone', {})})
            self.assertEqual((path / 'level.dat').read_bytes()[:2], b'\x1f\x8b')
            validate_level_metadata(read_nbt(path / 'level.dat'))
            self.assertTrue((path / 'region/r.0.0.mca').is_file())


if __name__ == '__main__': unittest.main()
