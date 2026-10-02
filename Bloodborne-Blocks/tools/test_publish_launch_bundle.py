import io
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from publish_launch_bundle import public_world, publish, safe_name
from world_io import NbtFile, Tag, TAG_COMPOUND, TAG_INT, TAG_STRING, compound, decode_nbt, encode_nbt


class PublicationTests(unittest.TestCase):
    def test_private_profiles_removed_and_world_bytes_and_other_metadata_preserved(self):
        nbt = NbtFile('', Tag(TAG_COMPOUND, {'Data': Tag(TAG_COMPOUND, {
            'Player': Tag(TAG_COMPOUND, {'testProfile': Tag(TAG_STRING, 'private')}),
            'SpawnX': Tag(TAG_INT, -501), 'LevelName': Tag(TAG_STRING, 'city')})}))
        memory = io.BytesIO()
        with zipfile.ZipFile(memory, 'w') as archive:
            archive.writestr('Main-City/level.dat', encode_nbt(nbt, compressed='gzip'))
            archive.writestr('Main-City/region/r.-1.-1.mca', b'exact saved chunk bytes')
            archive.writestr('Main-City/customnpcs/dialogs.json', b'NPC dialogue data')
            archive.writestr('Main-City/playerdata/profile.dat', b'private')
            archive.writestr('Main-City/rpchat/logs/session.log', b'private speech')
            archive.writestr('Main-City/customnpcs/playerdata/profile.json', b'private')
            archive.writestr('Main-City/data/cpm.json', b'{"player_scaling":{"test-profile":1}}')
        payload, stats = public_world(memory.getvalue())
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            self.assertEqual(3, len(archive.namelist()))
            self.assertEqual(b'exact saved chunk bytes', archive.read('Main-City/region/r.-1.-1.mca'))
            self.assertEqual(b'NPC dialogue data', archive.read('Main-City/customnpcs/dialogs.json'))
            data = compound(compound(decode_nbt(archive.read('Main-City/level.dat'), compressed='gzip').root)['Data'])
            self.assertNotIn('Player', data)
            self.assertEqual(Tag(TAG_INT, -501), data['SpawnX'])
            self.assertEqual(Tag(TAG_STRING, 'city'), data['LevelName'])
        self.assertEqual(4, stats['removedEntries'])
        self.assertEqual(1, stats['removedPlayerTags'])

    def test_complete_bundle_has_new_manifest_and_keeps_source_unchanged(self):
        world = io.BytesIO()
        with zipfile.ZipFile(world, 'w') as archive:
            archive.writestr('Main-City/region/r.0.0.mca', b'unchanged chunks')
            archive.writestr('Main-City/rpchat/logs/test.log', b'private speech')
        with tempfile.TemporaryDirectory() as folder:
            source, output = Path(folder)/'source.zip', Path(folder)/'public.zip'
            with zipfile.ZipFile(source, 'w') as archive:
                for name in ('Main-City.zip', 'Approval-Gallery.zip'):
                    archive.writestr('Bloodborne-Launch-Base/'+name, world.getvalue())
                archive.writestr('Bloodborne-Launch-Base/README.md', b'Original instructions')
                archive.writestr('Bloodborne-Launch-Base/Files-SHA256.json', b'{}')
                archive.writestr('Bloodborne-Launch-Base/mod.jar', b'unchanged mod')
            before = source.read_bytes()
            result = publish(source, output)
            self.assertEqual(before, source.read_bytes())
            self.assertTrue(result['sourceUnchanged'])
            with zipfile.ZipFile(output) as archive:
                manifest = json.loads(archive.read('Bloodborne-Launch-Base/Files-SHA256.json'))
                for row in manifest['files']:
                    data = archive.read('Bloodborne-Launch-Base/'+row['path'])
                    self.assertEqual(len(data), row['bytes'])
                    self.assertEqual(hashlib.sha256(data).hexdigest(), row['sha256'])
                self.assertEqual(b'unchanged mod', archive.read('Bloodborne-Launch-Base/mod.jar'))
                with zipfile.ZipFile(io.BytesIO(archive.read('Bloodborne-Launch-Base/Main-City.zip'))) as public:
                    self.assertEqual(['Main-City/region/r.0.0.mca'], public.namelist())

    def test_unsafe_paths_and_output_overwrite_rejected(self):
        for value in ('../file', '/file', 'C:/file', '\\\\server\\share\\file', 'Base/./Main-City.zip', 'Base//file'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                safe_name(value)
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / 'source.zip'
            source.write_bytes(b'original unchanged')
            with self.assertRaises(ValueError):
                publish(source, source)
            self.assertEqual(b'original unchanged', source.read_bytes())

    def test_alias_or_case_duplicate_cannot_bypass_private_archive_filter(self):
        for second in ('Bloodborne-Launch-Base/./Main-City.zip', 'Bloodborne-Launch-Base/main-city.ZIP'):
            with tempfile.TemporaryDirectory() as folder:
                source, output = Path(folder)/'source.zip', Path(folder)/'public.zip'
                with zipfile.ZipFile(source, 'w') as archive:
                    archive.writestr('Bloodborne-Launch-Base/Main-City.zip', b'private')
                    archive.writestr(second, b'private alias')
                with self.subTest(second=second), self.assertRaises(ValueError):
                    publish(source, output)
                self.assertFalse(output.exists())
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as archive:
            archive.writestr('Main-City/data/cpm.json', b'private')
            archive.writestr('Main-City/data/CPM.json', b'private alias')
        with self.assertRaises(ValueError):
            public_world(data.getvalue())


if __name__ == '__main__':
    unittest.main()
