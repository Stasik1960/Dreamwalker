import copy, hashlib, json, tempfile, unittest
from pathlib import Path
import gzip

import build_editable_city_gallery as g
from convert_logical_world import PART, hash_tree
from reviewed_migration_fixture import write_fixture
from world_io import compound
from convert_logical_world import World


def row(ident, width=1):
    return {"id": ident, "debug_id": "10001", "properties": {}, "state": "",
            "role": "editable_variant", "item": None,
            "bounds": [0, 0, 0, width, 1, 1],
            "footprint": [(x, 0, 0) for x in range(width)]}

def city_fixture(base):
    city=base/'resources'/'city'; logical=base/'resources'/'logical'
    city.mkdir(parents=True); logical.mkdir(parents=True)
    defs={'blocks':[{'id':'city_a','default':{},'states':{'':[]},'models':{'':'m_a'}},
                    {'id':'city_b','default':{},'states':{'':[]},'models':{'':'m_b'}}]}
    (city/'definitions.json').write_text(json.dumps(defs)); (logical/'definitions.json').write_text('{"blocks":[]}')
    geo={'blocks':{'city_a':{'states':{'':{'cells':{'0,0,0':{'collision':[],'outline':[]}},'render_offset':[0,0,0]}}},'city_b':{'states':{'':{'cells':{'0,0,0':{'collision':[],'outline':[]},'1,0,0':{'collision':[],'outline':[]}},'render_offset':[0,0,0]}}}}}
    (city/'geometry.json').write_text(json.dumps(geo)); (logical/'geometry.json').write_text('{"blocks":{}}')
    (logical/'contracts-v2.json').write_text('{"families":[]}'); (base/'resources'/'debug-ids.json').write_text(json.dumps({'ids':{'city_a':'10001','city_b':'10002','architecture_part':'10003'}}))
    for n in ('meshes.json.gz','owner-meshes.json.gz'):
        with gzip.open(city/n,'wt') as f:f.write('{}')
    with gzip.open(logical/'meshes.json.gz','wt') as f:f.write('{}')
    return city


class EditableGalleryTests(unittest.TestCase):
    def test_native_city_copy_merges_above_city_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); source = base / "source"; output = base / "output"
            city_cells = {(x, 70, z): ("minecraft:stone", {}) for x in range(-16, 33, 16) for z in range(0, 65, 16)}
            city_cells[(1, 70, 0)] = ("minecraft:chest", {})
            write_fixture(source, city_cells, floor=False)
            before = hash_tree(source)
            city=city_fixture(base)
            # Native definitions may explicitly carry a null model map.
            definitions=json.loads((city/'definitions.json').read_text())
            definitions['blocks'][0]['models']=None
            (city/'definitions.json').write_text(json.dumps(definitions))
            manifest = g.build(source, output, base / "resources" / "city")
            self.assertEqual(before, hash_tree(source))
            world = World(output, {})
            self.assertEqual(("minecraft:stone", ()), world.get("minecraft:overworld", (0, 70, 0)))
            self.assertGreater(manifest["galleryBand"]["minY"], 70)
            self.assertEqual(3, manifest["coverage"]["count"])
            self.assertEqual(3, len(manifest["coverage"]["registeredIds"]))
            from world_io import read_nbt
            meta=compound(compound(read_nbt(output/'level.dat').root)['Data'])
            self.assertEqual('0',compound(meta['GameRules'])['spawnRadius'].value)
            spawn=tuple(meta[k].value for k in ('SpawnX','SpawnY','SpawnZ'))
            self.assertEqual(('minecraft:air',()),world.get('minecraft:overworld',spawn))
            self.assertEqual(('minecraft:smooth_stone',()),world.get('minecraft:overworld',(spawn[0],spawn[1]-1,spawn[2])))
            helpers = []
            for tag in world.block_entities().values():
                d = compound(tag)
                if d["id"].value == PART:
                    helpers.append(d)
                    self.assertIn("Owner", d); self.assertIn("Root", d)
            self.assertTrue(helpers)
            # Every helper is a valid part state and its root is a non-helper.
            for d in helpers:
                p = tuple(int(d[k].value) for k in "xyz")
                self.assertEqual(PART, world.get("minecraft:overworld", p)[0])

    def test_all_registered_variants_are_represented_by_stable_keys(self):
        rows = [row("city_a"), row("city_a_variant"), row("city_functional")]
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); source = base / "source"; output = base / "output"
            write_fixture(source, {(x, 70, z): ("minecraft:stone", {}) for x in range(-16, 33, 16) for z in range(0, 65, 16)}, floor=False)
            city_fixture(base)
            manifest = g.build(source, output, base / "resources" / "city")
            keys = [s["stableKey"] for s in manifest["specimens"]]
            self.assertEqual(len(keys), len(set(keys)))
            self.assertTrue(all(s["sourceBlockId"] for s in manifest["specimens"]))
            self.assertTrue(all(s["baselineSha256"] for s in manifest["specimens"]))
            pads=[s['padBounds'] for s in manifest['specimens']]
            for i,a in enumerate(pads):
                for b in pads[i+1:]:
                    xgap=max(b[0]-a[3]-1,a[0]-b[3]-1)
                    zgap=max(b[2]-a[5]-1,a[2]-b[5]-1)
                    self.assertGreaterEqual(max(xgap,zgap),3)

    def test_empty_vanilla_lists_become_compound_lists(self):
        from world_io import Tag,TAG_LIST,TAG_END,TAG_COMPOUND,RegionFile
        with tempfile.TemporaryDirectory() as td:
            source=Path(td)/'world';write_fixture(source,{(0,70,0):('minecraft:stone',{})},floor=False)
            region_path=next((source/'region').glob('*.mca'));region=RegionFile(region_path.read_bytes())
            nbt=region.get_chunk(0,0).nbt();root=compound(nbt.root)
            root['sections']=Tag(TAG_LIST,[],TAG_END);root['block_entities']=Tag(TAG_LIST,[],TAG_END)
            region.set_chunk(0,0,nbt);region.save(region_path)
            world=g.GalleryWorld(source,{})
            world.set('minecraft:overworld',(0,80,0),('minecraft:stone',()))
            world.save()
            saved=RegionFile(region_path.read_bytes()).get_chunk(0,0).nbt();data=compound(saved.root)
            self.assertEqual(TAG_COMPOUND,data['sections'].list_type)
            self.assertEqual(TAG_COMPOUND,data['block_entities'].list_type)
            self.assertTrue(data['sections'].value)


if __name__ == "__main__":
    unittest.main()
