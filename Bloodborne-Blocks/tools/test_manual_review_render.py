"""Small no-JAR contract test for manual_review_render."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path

import numpy as np
from PIL import Image


MODULE = Path(__file__).with_name("manual_review_render.py")


def load_renderer(root: Path):
    catalog = types.ModuleType("catalog_geometry")
    catalog.ROOT, catalog.RES = root, root / "resources"
    catalog.rotation = lambda _axis, _angle: np.eye(3)
    build = types.ModuleType("build_logical_objects")
    build.object_polys = lambda apps: ([{"vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [0, 1, 0, 0, 1]], "texture": "x"}], [])
    raster = types.ModuleType("render_modular_preview")
    raster.CAM = np.eye(3)
    raster.draw_polys = lambda _polys, size: Image.new("RGBA", (size, size), (1, 2, 3, 255))
    old = {name: sys.modules.get(name) for name in ("catalog_geometry", "build_logical_objects", "render_modular_preview")}
    sys.modules.update({"catalog_geometry": catalog, "build_logical_objects": build, "render_modular_preview": raster})
    try:
        spec = importlib.util.spec_from_file_location("manual_review_render_test", MODULE)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader
        spec.loader.exec_module(module)
        # This unit test deliberately stubs geometry/texture loading; real pinned
        # pack/JAR validation is exercised by the actual catalogue generation.
        module._input_hashes = {'pack': 'synthetic-fixture', 'vanilla': 'synthetic-fixture'}
        return module
    finally:
        for name, value in old.items():
            if value is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = value


class ManualReviewRenderTest(unittest.TestCase):
    def test_metadata_numbers_and_resource_cache_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            model = root / "resources/assets/bloodborne_blocks/models/block/example.json"
            model.parent.mkdir(parents=True)
            model.write_text(json.dumps({"textures": {"0": "bloodborne_blocks:block/example"}}), encoding="utf8")
            renderer = load_renderer(root)
            record = {"review_id": "F001", "logical_id": "o_example", "name_ru": "Пример для проверки",
                      "preview_state": "facing=north", "preview_mesh": "mesh", "components": [
                          {"number": 7, "app": {"model": "bloodborne_blocks:block/example"}, "source_blocks": ["stone"]}]}
            meshes = {"mesh": {"polygons": [{"vertices": [[0, 0, 0, 0, 0], [2, 0, 0, 1, 0], [0, 2, 0, 0, 1]], "texture": "x"}]}}
            first = renderer.render_family(record, meshes, root / "out")
            self.assertEqual(first["bounds"], [[0.0, 0.0, 0.0], [2.0, 2.0, 0.0]])
            self.assertEqual(first["reconstructed_bounds"], [[0.0, 0.0, 0.0], [1.0, 1.0, 0.0]])
            self.assertTrue(Path(first["paths"]["exploded"]).is_file())
            model.write_text(json.dumps({"textures": {"0": "bloodborne_blocks:block/changed"}}), encoding="utf8")
            second = renderer.render_family(record, meshes, root / "out")
            self.assertNotEqual(first["input_hash"], second["input_hash"])


if __name__ == "__main__":
    unittest.main()
