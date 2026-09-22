import json, sqlite3, sys, tempfile, unittest, zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from source_assembly_index import build_index, positions
from world_io import NbtFile, RegionFile, TAG_BYTE, TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG_ARRAY, TAG_STRING, Tag, encode_nbt, pack_palette_indices

def state(name, **props):
    value = {"Name": Tag(TAG_STRING, name)}
    if props: value["Properties"] = Tag(TAG_COMPOUND, {k: Tag(TAG_STRING, v) for k, v in props.items()})
    return Tag(TAG_COMPOUND, value)

def source_zip(path):
    palette = [state("minecraft:stone"), state("minecraft:oak_log", axis="x"), state("minecraft:air")]
    values = [0] * 4096; values[1] = 1; values[17] = 2
    section = Tag(TAG_COMPOUND, {"Y": Tag(TAG_BYTE, 0), "block_states": Tag(TAG_COMPOUND, {"palette": Tag(TAG_LIST, palette, TAG_COMPOUND), "data": Tag(TAG_LONG_ARRAY, pack_palette_indices(values, 3))})})
    root = NbtFile("", Tag(TAG_COMPOUND, {"xPos": Tag(TAG_INT, 2), "zPos": Tag(TAG_INT, -1), "sections": Tag(TAG_LIST, [section], TAG_COMPOUND)}))
    region = RegionFile(); region.set_chunk(2, 31, encode_nbt(root))
    with zipfile.ZipFile(path, "w") as z: z.writestr("region/r.0.-1.mca", region.to_bytes()); z.writestr("poi/r.0.-1.mca", region.to_bytes())

class IndexTest(unittest.TestCase):
    def test_index_positions_and_tamper(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "world.zip"; source_zip(source); cache = root / "index.sqlite"
            # Mapping files are intentionally current project resources; stone and oak_log are present.
            index = build_index(source, cache, dimension="", expected_sha256=None)
            found = {item["source"]["id"]: item for item in index["states"]}
            self.assertEqual(index["chunks"], 1); self.assertEqual(index["carrier_cells"], 4095)
            self.assertEqual(found["minecraft:oak_log"]["source"]["properties"], {"axis": "x"})
            self.assertEqual(positions(index, [found["minecraft:oak_log"]["state_id"]]), {(33, 0, -16): found["minecraft:oak_log"]["state_id"]})
            with cache.open("ab") as f: f.write(b"tamper")
            rebuilt = build_index(source, cache, dimension="", expected_sha256=None)
            self.assertEqual(rebuilt["carrier_cells"], 4095)
            different_dimension = build_index(source, cache, dimension="other", expected_sha256=None)
            self.assertEqual(different_dimension["chunks"], 0)

    def test_pack_model_and_texture_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "world.zip"; source_zip(source); pack = root / "pack.zip"; vanilla = root / "vanilla.zip"
            with zipfile.ZipFile(vanilla, "w") as z:
                z.writestr("assets/minecraft/blockstates/stone.json", json.dumps({"variants": {"": {"model": "block/stone"}}}))
            with zipfile.ZipFile(pack, "w") as z:
                z.writestr("assets/minecraft/models/block/stone.json", json.dumps({"textures": {"all": "minecraft:block/stone"}}))
                z.writestr("assets/minecraft/textures/block/stone.png", b"changed")
                z.writestr("assets/minecraft/textures/block/stone.png.mcmeta", b"meta")
            index = build_index(source, root / "index.sqlite", pack=pack, vanilla=vanilla, dimension="", expected_sha256=None)
            stone = next(s for s in index["states"] if s["source"]["id"] == "minecraft:stone")
            self.assertIn("assets/minecraft/models/block/stone.json", stone["carrier_evidence"])
            self.assertIn("assets/minecraft/textures/block/stone.png.mcmeta", stone["carrier_evidence"])

if __name__ == "__main__": unittest.main()
