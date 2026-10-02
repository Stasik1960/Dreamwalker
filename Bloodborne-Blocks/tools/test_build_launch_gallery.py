import json
import tempfile
import unittest
import copy
from pathlib import Path

import build_launch_gallery as g
from convert_logical_world import World, PART, unpack_pos_long
from reviewed_migration_fixture import write_fixture
from test_logical_world import entity, tree_hash
from world_io import TAG_STRING, Tag, compound


class GalleryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = g.load_data()
        cls.rows = g.specimens(cls.data)

    def test_all_old_objects_and_document_variants_have_distinct_pods(self):
        import copy
        rows = copy.deepcopy(self.rows)
        cells, entities = g.build_cells(rows)
        ids = {r['id'] for r in rows}
        expected = {d['id'] for d in self.data[1].values() if d.get('whole_owner')} | set(self.data[6])
        self.assertTrue(expected <= ids)
        self.assertEqual(len(rows), len({tuple(r['root']) for r in rows}))
        for r in rows:
            self.assertEqual(5, len(r['debug_id']))
            self.assertTrue(r['debug_id'].isdigit())
            if not r.get('_decision'):
                self.assertEqual(self.data[5][r['id']], r['debug_id'])
            self.assertEqual('minecraft:oak_sign', cells[tuple(r['marker'])][0])
        root_positions = {tuple(r['root']) for r in rows}
        for tag in entities:
            d = compound(tag)
            if d['id'].value == PART:
                self.assertIn(unpack_pos_long(d['Root'].value), root_positions)
                p = tuple(d[a].value for a in ('x','y','z'))
                self.assertEqual(PART, cells[p][0])

    def test_variable_pads_have_exact_three_block_gaps_and_contain_specimens(self):
        rows = copy.deepcopy(self.rows)
        cells, entities = g.build_cells(rows)
        for row in rows:
            x0, _, z0, x1, _, z1 = row['pad_bounds']
            self.assertTrue(x0 <= row['root'][0] <= x1 and z0 <= row['root'][2] <= z1)
            self.assertTrue(x0 <= row['marker'][0] <= x1 and z0 <= row['marker'][2] <= z1)
            for point in row['footprint']:
                self.assertTrue(x0 <= row['root'][0] + point[0] <= x1)
                self.assertTrue(z0 <= row['root'][2] + point[2] <= z1)
        for start in range(0, len(rows), 16):
            shelf = rows[start:start + 16]
            for left, right in zip(shelf, shelf[1:]):
                self.assertEqual(right['pad_bounds'][0] - left['pad_bounds'][3] - 1, 3)
        for start in range(0, len(rows) - 16, 16):
            lower = rows[start + 16]['pad_bounds'][2]
            for upper in rows[start:start + 16]:
                self.assertEqual(lower - upper['pad_bounds'][5] - 1, 3)
        self.assertTrue(any(r['pad_bounds'][3] - r['pad_bounds'][0] != rows[0]['pad_bounds'][3] - rows[0]['pad_bounds'][0] for r in rows[1:]))

    def test_saved_world_has_valid_helpers_outside_signs_and_keeps_sources(self):
        before = (g.CITY/'document-final.json').read_bytes()
        with tempfile.TemporaryDirectory() as t:
            out = Path(t)/'gallery'
            m = g.build(out, source=None, selected={'owner_final_03','owner_final_05','o_lantern'})
            self.assertTrue(m['helperAudit']['ok'])
            self.assertGreater(m['helperAudit']['checked'], 0)
            world = World(out,{})
            for r in m['specimens']:
                if not r.get('_decision'):
                    self.assertEqual('bloodborne_blocks:'+r['id'], world.get('minecraft:overworld',tuple(r['root']))[0])
                self.assertEqual('minecraft:oak_sign', world.get('minecraft:overworld',tuple(r['marker']))[0])
            be = world.block_entities()
            self.assertEqual(len(be), sum(len(c.entities()[1]) for c in world.chunks.values()))
            with self.assertRaisesRegex(ValueError, 'must be new'):
                g.build(out, source=None, selected={'owner_final_03'})
        self.assertEqual(before, (g.CITY/'document-final.json').read_bytes())

    def test_full_city_context_copy_preserves_native_nbt_and_source_bytes(self):
        with tempfile.TemporaryDirectory() as t:
            base=Path(t);source=base/'city';out=base/'gallery';chest=entity((2,64,2),'minecraft:chest',CustomName=Tag(TAG_STRING,'{"text":"native city chest"}'))
            write_fixture(source,{(2,64,2):('minecraft:chest',{}),(3,64,2):('minecraft:stone',{})},block_entities=[chest],floor=False);before=tree_hash(source);region=source/'region/r.0.0.mca';region_bytes=region.read_bytes()
            manifest=g.build(out,source=source,selected={'owner_final_05'})
            self.assertEqual(before,tree_hash(source));self.assertEqual(region_bytes,(out/'region/r.0.0.mca').read_bytes());self.assertIn({'path':'region/r.0.0.mca','sha256':__import__('hashlib').sha256(region_bytes).hexdigest()},manifest['cityContext']);self.assertEqual([],manifest['helperAudit']['orphans'])
            copied=compound(World(out,{}).block_entities()[('minecraft:overworld',2,64,2)])
            self.assertEqual('minecraft:chest',copied['id'].value);self.assertEqual('{"text":"native city chest"}',copied['CustomName'].value)

    def test_rejects_source_region_that_would_overwrite_a_gallery_pod(self):
        with tempfile.TemporaryDirectory() as t:
            base=Path(t);source=base/'city';out=base/'gallery';rows=[copy.deepcopy(row) for row in self.rows if row['id']=='owner_final_05'];cells,_=g.build_cells(rows);point=tuple(rows[0]['root'])
            write_fixture(source,{point:('minecraft:stone',{})},floor=False);before=tree_hash(source)
            with self.assertRaisesRegex(ValueError,'refusing to overwrite a gallery file'):g.build(out,source=source,selected={'owner_final_05'})
            self.assertFalse(out.exists());self.assertEqual(before,tree_hash(source))

    def test_output_under_city_or_logical_resources_is_rejected_before_writing(self):
        for unsafe in (g.CITY/'__gallery_test_output__',g.CITY.parent/'logical'/'__gallery_test_output__'):
            with self.subTest(unsafe=unsafe):
                self.assertFalse(unsafe.exists())
                with self.assertRaisesRegex(ValueError,'outside city'):g.build(unsafe,source=None,selected={'owner_final_05'})
                self.assertFalse(unsafe.exists())

    def test_decisions_have_physical_markers_and_source_links(self):
        decisions = [r for r in self.rows if r.get('_decision')]
        self.assertTrue(decisions)
        source = next(r for r in decisions if r['decision'].get('_source_components'))
        self.assertTrue(source['source_rows'])
        self.assertTrue(all(r['properties'] for r in source['source_rows']))
        self.assertTrue(source['debug_id'].isdigit() and len(source['debug_id']) == 5)
        with tempfile.TemporaryDirectory() as t:
            manifest = g.build(Path(t) / 'gallery', source=None, selected={source['id'].split('__decision_')[0]})
            choice = next(c for c in manifest['choices'] if c['document'] == source['decision']['_document_id'])
            self.assertTrue(choice['decision_ref'])
            self.assertTrue(choice['sourceTps'])
            self.assertIn('candidates', choice)

    def test_select_document_keeps_actual_historical_candidate_references(self):
        rows = g.select_rows(copy.deepcopy(self.rows), {'owner_final_13'})
        decisions = [r for r in rows if r.get('_decision')]
        candidate = next(r for r in decisions if r['candidate_rows'])
        self.assertTrue(any(r['id'] == 'o_shuttered_window' for r in rows))
        g.build_cells(rows)
        row_ids = {id(r) for r in rows}
        for r in candidate['candidate_rows']:
            self.assertIn(id(r), row_ids)
            self.assertTrue(r['tp'])


if __name__ == '__main__':
    unittest.main()
