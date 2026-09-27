"""Exact authorized-cell mutation, preservation and fail-closed regression tests."""
from collections import defaultdict
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import retire_missing_composites as retirement
from retire_missing_composites import approved_rows, file_hash, tree_manifest
from world_io import (NbtFile, RegionFile, Tag, TAG_BYTE, TAG_BYTE_ARRAY,
                      TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING,
                      block_state_key, compound, pack_palette_indices, section_blocks,
                      write_nbt)


def retire(source, *args, **kwargs):
    # Only test fixtures replace the hard production source authorization.
    with patch.object(retirement, 'SOURCE_DIGEST', file_hash(source)):
        return retirement.retire(source, *args, **kwargs)


def make_source(base, *, mismatch=False, target_entity=False):
    """Small real Anvil encoding of all 33 fixed authorization coordinates."""
    base = Path(base)
    world = base / 'fixture-world'
    world.mkdir(parents=True)
    write_nbt(world / 'level.dat', NbtFile('', Tag(TAG_COMPOUND, {'Data': Tag(TAG_COMPOUND, {'DataVersion': Tag(TAG_INT, 3465)})})))
    (world / 'othermod').mkdir()
    (world / 'othermod/data.dat').write_bytes(b'untouched-other-mod-data')
    (world / 'playerdata').mkdir()
    (world / 'playerdata/synthetic.dat').write_bytes(b'untouched-synthetic-player-data')
    regions, by_chunk = {}, defaultdict(list)
    rows = approved_rows()
    for row in rows:
        x, y, z = row['position']
        by_chunk[(x//16, z//16)].append(row)
    for (cx, cz), chosen in by_chunk.items():
        by_section, sections = defaultdict(list), []
        for row in chosen:
            by_section[row['position'][1]//16].append(row)
        for sy, entries in by_section.items():
            palette = [Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, 'minecraft:air')})]
            indices = [0]*4096
            for row in entries:
                name, _, properties = row['before'].partition('[')
                state = {'Name': Tag(TAG_STRING, 'minecraft:stone' if mismatch and row == rows[-1] else name)}
                if properties and not (mismatch and row == rows[-1]):
                    state['Properties'] = Tag(TAG_COMPOUND, {k: Tag(TAG_STRING, v) for k, v in (p.split('=') for p in properties[:-1].split(','))})
                tag = Tag(TAG_COMPOUND, state)
                found = next((i for i, p in enumerate(palette) if p == tag), None)
                if found is None:
                    found = len(palette); palette.append(tag)
                x, y, z = row['position']
                indices[(y&15)*256+(z&15)*16+(x&15)] = found
            sections.append(Tag(TAG_COMPOUND, {'Y': Tag(TAG_BYTE, sy),
                'SkyLight': Tag(TAG_BYTE_ARRAY, bytes(2048)),
                'block_states': Tag(TAG_COMPOUND, {'palette': Tag(TAG_LIST, palette, TAG_COMPOUND),
                'data': Tag(TAG_LONG_ARRAY, pack_palette_indices(indices, len(palette)))})}))
        entities = []
        if target_entity and rows[-1] in chosen:
            x, y, z = rows[-1]['position']
            entities.append(Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, 'othermod:protected'),
                'x': Tag(TAG_INT, x), 'y': Tag(TAG_INT, y), 'z': Tag(TAG_INT, z), 'private': Tag(TAG_STRING, 'synthetic')}))
        nbt = NbtFile('', Tag(TAG_COMPOUND, {'DataVersion': Tag(TAG_INT, 3465),
            'xPos': Tag(TAG_INT, cx), 'zPos': Tag(TAG_INT, cz),
            'OtherModData': Tag(TAG_COMPOUND, {'sentinel': Tag(TAG_STRING, 'unchanged')}),
            'Heightmaps': Tag(TAG_COMPOUND, {'WORLD_SURFACE': Tag(TAG_LONG_ARRAY, [0]*36)}),
            'isLightOn': Tag(TAG_BYTE, 1), 'sections': Tag(TAG_LIST, sections, TAG_COMPOUND),
            'block_entities': Tag(TAG_LIST, entities, TAG_COMPOUND)}))
        key = (cx//32, cz//32)
        region = regions.setdefault(key, RegionFile())
        region.set_chunk(cx%32, cz%32, nbt, compression=2, timestamp=17)
    (world / 'region').mkdir()
    for (rx, rz), region in regions.items():
        region.save(world / 'region' / f'r.{rx}.{rz}.mca')
    source = base / 'source.zip'
    with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(world.rglob('*')):
            if path.is_file():
                archive.write(path, 'fixture/' + path.relative_to(world).as_posix())
    return source


class CompositeRetirementTests(unittest.TestCase):
    def test_production_api_rejects_other_zip_even_with_its_correct_sha(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base)
            with self.assertRaisesRegex(ValueError,'authorized immutable'):
                retirement.retire(source,base/'output',base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertFalse((base/'output').exists())

    def test_exact_33_cells_and_second_run_is_byte_identical(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); source = make_source(base)
            before = file_hash(source); output = base/'retired'; ledger = base/'ledger.json'
            first = retire(source, output, ledger, expected_source_sha256=before)
            snapshot = tree_manifest(output); ledger_bytes = ledger.read_bytes()
            second = retire(source, output, ledger, expected_source_sha256=before)
            self.assertEqual(first['retiredCells'], 33)
            self.assertEqual(len(first['retiredIds']), 23)
            self.assertEqual(len(first['changedChunks']), 21)
            self.assertEqual(second['changedCellsThisRun'], 0)
            self.assertEqual(snapshot, tree_manifest(output))
            self.assertEqual(ledger_bytes, ledger.read_bytes())
            self.assertEqual(before, file_hash(source))
            for row in approved_rows():
                x, y, z = row['position']
                chunk = RegionFile.open(output/'region'/f'r.{x//512}.{z//512}.mca').get_chunk(x//16%32,z//16%32)
                section = next(s for s in compound(chunk.nbt().root)['sections'].value if compound(s)['Y'].value == y//16)
                palette, indices = section_blocks(section)
                self.assertEqual(block_state_key(palette[indices[(y&15)*256+(z&15)*16+(x&15)]]), 'minecraft:air')
            self.assertEqual((output/'othermod/data.dat').read_bytes(), b'untouched-other-mod-data')
            (output/'othermod/data.dat').write_bytes(b'tampered')
            with self.assertRaises(ValueError):
                retire(source, output, ledger, expected_source_sha256=before)

    def test_last_mismatched_target_refuses_before_any_copy_write(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base,mismatch=True)
            with self.assertRaisesRegex(ValueError,'state mismatch'):
                retire(source,base/'output',base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertFalse((base/'output').exists())
            self.assertFalse((base/'ledger.json').exists())

    def test_foreign_target_entity_refuses_without_deleting_anything(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base,target_entity=True)
            with self.assertRaisesRegex(ValueError,'block entity'):
                retire(source,base/'output',base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertFalse((base/'output').exists())

    def test_source_hash_changed_list_and_source_overlap_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base)
            with self.assertRaisesRegex(ValueError,'checksum'):
                retire(source,base/'out',base/'ledger.json',expected_source_sha256='0'*64)
            from retire_missing_composites import APPROVED_LIST
            changed=json.loads(APPROVED_LIST.read_bytes()); next(iter(changed.values()))[0]['pos'][0]+=1
            manifest=base/'unauthorized.json';manifest.write_text(json.dumps(changed))
            with self.assertRaisesRegex(ValueError,'authorization'):
                retire(source,base/'out',base/'ledger.json',expected_source_sha256=file_hash(source),list_path=manifest)
            with self.assertRaisesRegex(ValueError,'separate'):
                retire(source,base,base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertFalse((base/'out').exists())

    def test_malformed_zip_and_unproven_existing_output_are_not_written(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base)
            with zipfile.ZipFile(source,'a') as archive:
                archive.writestr('../escape','forbidden')
            with self.assertRaisesRegex(ValueError,'Unsafe'):
                retire(source,base/'out',base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertFalse((base/'out').exists())
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); source=make_source(base);(base/'out').mkdir()
            with self.assertRaisesRegex(ValueError,'Existing'):
                retire(source,base/'out',base/'ledger.json',expected_source_sha256=file_hash(source))
            self.assertEqual(list((base/'out').iterdir()),[])


if __name__ == '__main__':
    unittest.main()
