#!/usr/bin/env python3
import hashlib
import gzip
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import manual_review_world as review
from world_io import TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING, NbtFile, RegionFile, Tag, pack_palette_indices


def state(name, props=None):
    value = {"Name": Tag(TAG_STRING, name)}
    if props: value["Properties"] = Tag(TAG_COMPOUND, {k: Tag(TAG_STRING, v) for k, v in props.items()})
    return Tag(TAG_COMPOUND, value)


def chunk(cx, cz, cells):
    palette = [state("minecraft:air")]
    indexes = [0] * 4096
    for (x, y, z), value in cells.items():
        if value not in palette: palette.append(value)
        indexes[(y << 8) | (z << 4) | x] = palette.index(value)
    section = Tag(TAG_COMPOUND, {"Y": Tag(TAG_BYTE, 0), "block_states": Tag(TAG_COMPOUND, {
        "palette": Tag(TAG_LIST, palette, TAG_COMPOUND), "data": Tag(TAG_LONG_ARRAY, pack_palette_indices(indexes, len(palette)))})})
    return NbtFile("", Tag(TAG_COMPOUND, {"xPos": Tag(TAG_INT, cx), "zPos": Tag(TAG_INT, cz), "sections": Tag(TAG_LIST, [section], TAG_COMPOUND)}))


def source_zip(path, chunks):
    region = RegionFile()
    for cx, cz, cells in chunks:
        region.set_chunk(cx & 31, cz & 31, chunk(cx, cz, cells))
    with zipfile.ZipFile(path, "w") as archive: archive.writestr("world/region/r.0.0.mca", region.to_bytes())


class ManualReviewWorldTests(unittest.TestCase):
    def test_active_visual_variants_weight_and_boolean_conditions(self):
        raw = {'variants': {'facing=north': [{'model': 'block/books_a'}, {'model': 'block/books_b'}]}}
        refs = review._active_models(raw, {'facing': 'north'})
        self.assertEqual(refs, [('books_a', True), ('books_b', True)])
        self.assertEqual(review._active_models(raw, {'facing': 'south'}), [])
        part = {'multipart': [{'when': {'OR': [{'north': 'true'}, {'east': 'true'}]}, 'apply': {'model': 'block/rail'}}]}
        self.assertEqual(review._active_models(part, {'north': 'false', 'east': 'true'}), [('rail', False)])

    def test_missing_visual_input_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                review.config_hash([], Path(temp)/'missing.zip', Path(temp)/'missing.jar')

    def record(self, review_id, logical_id, blocks):
        return {"review_id": review_id, "logical_id": logical_id, "legacy_rules": [{
            "source": blocks[0], "members": blocks[1:], "target": {"id": logical_id}}]}

    def run_scan(self, chunks, records):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); source, output, pack, vanilla = root/"source.zip", root/"out.json", root/"pack.zip", root/"vanilla.jar"
            source_zip(source, chunks)
            with zipfile.ZipFile(pack, "w"), zipfile.ZipFile(vanilla, "w"): pass
            old, old_pack = review.EXPECTED_SHA256, review.EXPECTED_PACK_SHA256; review.EXPECTED_SHA256 = hashlib.sha256(source.read_bytes()).hexdigest(); review.EXPECTED_PACK_SHA256 = hashlib.sha256(pack.read_bytes()).hexdigest()
            try: result = review.scan(records, output, source=source, sourcepack=pack, vanilla_jar=vanilla)
            finally: review.EXPECTED_SHA256, review.EXPECTED_PACK_SHA256 = old, old_pack
            self.assertEqual(json.loads(output.read_text())["source"]["sha256"], hashlib.sha256(source.read_bytes()).hexdigest())
            return result

    def test_exact_and_ambiguous_candidates(self):
        rec = self.record("r", "o_r", [{"id":"minecraft:stone", "offset":[0,0,0]}])
        result = self.run_scan([(0, 0, {(1, 0, 1): state("minecraft:stone"), (2,0,1): state("minecraft:stone")})], [rec])
        self.assertEqual(result["r"]["candidate_count"], 2)
        self.assertEqual(result["r"]["status"], "matched")
        rec["ambiguous"] = True
        result = self.run_scan([(0, 0, {(1, 0, 1): state("minecraft:stone")})], [rec])
        self.assertEqual(result["r"]["status"], "ambiguous_candidate_count")

    def test_multiblock_crosses_chunk_boundary(self):
        rec = self.record("boundary", "o_boundary", [{"id":"minecraft:stone", "offset":[0,0,0]}, {"id":"minecraft:dirt", "offset":[1,0,0]}])
        result = self.run_scan([(0, 0, {(15,0,0): state("minecraft:stone")}), (1, 0, {(0,0,0): state("minecraft:dirt")})], [rec])
        self.assertEqual(result["boundary"]["candidate_count"], 1)
        self.assertEqual(result["boundary"]["examples"][0]["anchor"], [15,0,0])

    def test_no_source_carrier_is_not_zero(self):
        result = self.run_scan([(0, 0, {})], [{"review_id":"none", "logical_id":"o_none", "components":[]}])
        self.assertIsNone(result["none"]["candidate_count"])
        self.assertEqual(result["none"]["status"], "not_mapped")

    def test_single_visual_model_preserves_multiblock_pattern(self):
        rec = self.record('multi', 'o_multi', [{'id': 'minecraft:stone'}, {'id': 'minecraft:dirt', 'offset': [1, 0, 0]}])
        rec['components'] = [{'app': {'model': 'block/object'}}]
        visuals = {'minecraft:stone': {'variants': {'': {'model': 'block/object'}}}}
        cells = {(1, 0, 1): state('minecraft:stone'), (2, 0, 1): state('minecraft:dirt'), (5, 0, 1): state('minecraft:stone')}
        with patch.object(review, '_load_blockstates', return_value=visuals):
            result = self.run_scan([(0, 0, cells)], [rec])
        self.assertEqual(result['multi']['candidate_count'], 1)
        self.assertEqual(len(result['multi']['examples'][0]['source_blocks']), 2)

    def test_tampered_sparse_cache_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source, output, pack, vanilla = (root/name for name in ('source.zip', 'out.json', 'pack.zip', 'vanilla.jar'))
            source_zip(source, [(0, 0, {(1, 0, 1): state('minecraft:stone')})])
            with zipfile.ZipFile(pack, 'w'), zipfile.ZipFile(vanilla, 'w'): pass
            rec = self.record('r', 'o_r', [{'id': 'minecraft:stone'}])
            with patch.object(review, 'EXPECTED_SHA256', review._sha256(source)), patch.object(review, 'EXPECTED_PACK_SHA256', review._sha256(pack)):
                self.assertEqual(review.scan([rec], output, source=source, sourcepack=pack, vanilla_jar=vanilla)['r']['candidate_count'], 1)
                # A changed report config exercises the checksum-checked sparse reuse path.
                rec['notes'] = 'same source, fresh report'
                self.assertEqual(review.scan([rec], output, source=source, sourcepack=pack, vanilla_jar=vanilla)['r']['candidate_count'], 1)
                sparse = root/'source-candidates.json.gz'
                with gzip.open(sparse, 'rt', encoding='utf-8') as stream: data = json.load(stream)
                data['candidates'][0][3].append([5, 0, 1])
                with gzip.open(sparse, 'wt', encoding='utf-8') as stream: json.dump(data, stream)
                rec['notes'] = 'force another report'
                with self.assertRaisesRegex(ValueError, 'integrity check failed'):
                    review.scan([rec], output, source=source, sourcepack=pack, vanilla_jar=vanilla)


if __name__ == "__main__": unittest.main()
