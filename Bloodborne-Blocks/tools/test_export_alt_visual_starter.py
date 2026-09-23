from __future__ import annotations

import gzip
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from export_alt_visual_starter import export


class ExportAltVisualStarterTests(unittest.TestCase):
 def fixture(self, root: Path) -> Path:
  resources = root / "resources"; logical = resources / "bloodborne_blocks" / "logical"; logical.mkdir(parents=True)
  definition = {"blocks": [{"id": "o_test", "models": {"facing=north,visual=base": "mesh", "facing=north,visual=alt": "mesh"},
                 "visual_models": {"facing=north,visual=base": "bloodborne_blocks:block/logical/o_test/base/facing_north",
                 "facing=north,visual=alt": "bloodborne_blocks:block/logical/o_test/alt/facing_north"}}]}
  contract = {"families": [{"id": "o_test", "canonical_anchor": {"cell": [0, 0, 0], "pivot": [.5, .5, .5]},
               "collision_policy": "SIMPLE_BOX", "placement_policy": "FLOOR", "mirror_policy": "ROTATE_ONLY"}]}
  geometry = {"blocks": {"o_test": {"states": {"facing=north,visual=base": {"cells": {"0,0,0": {}}},
                "facing=north,visual=alt": {"cells": {"0,0,0": {}}}}}}}
  (logical / "definitions.json").write_text(json.dumps(definition), encoding="utf8")
  (logical / "contracts-v2.json").write_text(json.dumps(contract), encoding="utf8")
  (logical / "geometry.json").write_text(json.dumps(geometry), encoding="utf8")
  with gzip.open(logical / "meshes.json.gz", "wt", encoding="utf8") as stream:
   json.dump({"mesh": {"polygons": [{"texture": "bloodborne_blocks:block/test", "vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 16, 0], [0, 1, 0, 0, 16]]}, {"texture": "minecraft:block/stone", "vertices": [[0, 0, 1, 0, 0], [1, 0, 1, 16, 0], [0, 1, 1, 0, 16]]}]}}, stream)
  texture = resources / "assets/bloodborne_blocks/textures/block/test.png"; texture.parent.mkdir(parents=True); texture.write_bytes(b"png")
  texture.with_suffix('.png.mcmeta').write_text('{"animation":{}}',encoding='utf8')
  alt = resources / "assets/bloodborne_blocks/models/block/logical/o_test/alt/facing_north.json"; alt.parent.mkdir(parents=True)
  alt.write_text(json.dumps({"parent": "bloodborne_blocks:block/logical/o_test/base/facing_north", "display": {"gui": {"rotation": [1, 2, 3]}}}), encoding="utf8")
  return resources

 def test_alt_only_pack_is_deterministic_editable_and_refuses_overwrite(self):
  with tempfile.TemporaryDirectory() as folder:
   root = Path(folder); resources = self.fixture(root); first, second = root / "first.zip", root / "second.zip"
   manifest = export(resources, first)
   export(resources, second)
   self.assertEqual(first.read_bytes(), second.read_bytes())
   self.assertIn("o_test", manifest["families"])
   with zipfile.ZipFile(first) as archive:
    names = archive.namelist(); self.assertFalse(any("/base/" in name for name in names))
    model = json.loads(archive.read("assets/bloodborne_blocks/models/block/logical/o_test/alt/facing_north.json"))
    self.assertEqual("minecraft:block/block", model["parent"])
    self.assertEqual({"gui": {"rotation": [1, 2, 3]}}, model["display"])
    self.assertEqual([0.0, 0.0, 0.0, 0.0, 0.0], model["bloodborne_polygons"][0]["vertices"][0])
    self.assertEqual([1.0, 0.0, 0.0, 16.0, 0.0], model["bloodborne_polygons"][0]["vertices"][1])
    self.assertIn("assets/bloodborne_blocks/textures/block/logical_alt/o_test/facing_north__t0.png", names)
    sidecar="assets/bloodborne_blocks/textures/block/logical_alt/o_test/facing_north__t0.png.mcmeta"
    self.assertIn(sidecar,names)
    self.assertIn(sidecar,manifest['families']['o_test']['states']['facing=north,visual=alt']['files'])
    self.assertEqual("minecraft:block/stone", model["textures"]["t1"])
    self.assertNotIn("assets/bloodborne_blocks/textures/block/logical_alt/o_test/facing_north__t1.png", names)
    self.assertEqual(["minecraft:block/stone"], json.loads(archive.read("manifest.json"))["vanilla_textures"])
    self.assertIn("pack_format: 15", archive.read("README.md").decode("utf8"))
   with self.assertRaises(ValueError): export(resources, first)
   export(resources, first, force=True)

 def test_legacy_logical_without_v2_contract_is_exported(self):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);resources=self.fixture(root)
   (resources/'bloodborne_blocks/logical/contracts-v2.json').write_text('{"families":[]}',encoding='utf8')
   result=export(resources,root/'legacy.zip')
   self.assertIn('legacy_placement_anchors_by_state',result['families']['o_test']['canonical_anchor'])


if __name__ == "__main__":
 unittest.main()
