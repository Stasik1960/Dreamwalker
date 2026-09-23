"""Actual-source tests for NightmareRunning functional recipes."""
from __future__ import annotations

import copy
import os
import unittest
from pathlib import Path

os.environ.setdefault("BLOODBORNE_VANILLA_JAR", str(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))

from nightmare_functional_recipes import recipe
from nightmare_geometry_recipes import tagged_polys


PYRAMID = [{"model": "minecraft:block/lantern", "weight": 100, "offset": [0, 0, 0]}]


class NightmareFunctionalRecipeTests(unittest.TestCase):
    def test_c282_uses_actual_single_source_element_and_human_seam(self):
        source = tagged_polys([{"model": "minecraft:block/dark_door_1", "y": 270, "offset": [0, 0, 0]}])
        before = copy.deepcopy(source)
        outputs = recipe("o_c282", source)
        self.assertEqual([row["name"] for row in outputs], ["o_c282_a", "o_c282_b"])
        self.assertEqual(source, before)
        self.assertTrue(all(poly["source_element"] == 0 for row in outputs for poly in row["polygons"]))
        self.assertEqual(outputs[0]["provenance"]["semantic_boundary"], "image8 vertical seam x=0.5")
        self.assertAlmostEqual(max(v[0] for p in outputs[0]["polygons"] for v in p["vertices"]), 1.5)
        self.assertAlmostEqual(min(v[0] for p in outputs[1]["polygons"] for v in p["vertices"]), 0.5)
        self.assertEqual(outputs[0]["door_pivots"]["left"], (0.0, 0.4375))

    def test_c654_real_source_faces_become_two_consistent_two_sided_designs(self):
        source = tagged_polys([{"model": "minecraft:block/hold/window_02", "y": 90, "offset": [0, 0, 0]}])
        outputs = recipe("o_c654", source)
        self.assertEqual([row["name"] for row in outputs], ["o_c654_a", "o_c654_b"])
        self.assertEqual([row["provenance"]["source_face"] for row in outputs], ["north", "south"])
        for output in outputs:
            self.assertEqual(len(output["polygons"]), 2)
            self.assertEqual(output["collision_box"], (0.0, 0.0, 0.90625, 1.0, 2.75, 0.96875))
            self.assertEqual(output["polygons"][0]["texture"], output["polygons"][1]["texture"])
            self.assertEqual([vertex[3:] for vertex in output["polygons"][0]["vertices"]],
                             list(reversed([vertex[3:] for vertex in output["polygons"][1]["vertices"]])))

    def test_c618_and_wall_raise_actual_meshes_to_floor_without_changing_artwork(self):
        for ident, apps in (("o_c618", PYRAMID),
                            ("o_wall_deco_1", [{"model": "minecraft:block/addon/wall_deco_1", "offset": [0, 0, 0]}])):
            source = tagged_polys(apps)
            output = recipe(ident, source)[0]
            self.assertAlmostEqual(min(v[1] for p in output["polygons"] for v in p["vertices"]), 0.0)
            self.assertEqual([p["texture"] for p in output["polygons"]], [p["texture"] for p in source])
            self.assertEqual([[v[3:] for v in p["vertices"]] for p in output["polygons"]],
                             [[v[3:] for v in p["vertices"]] for p in source])
        self.assertAlmostEqual(recipe("o_c618", tagged_polys(PYRAMID))[0]["source_translation"][1], 0.8125)
        self.assertAlmostEqual(recipe("o_wall_deco_1", tagged_polys([{"model": "minecraft:block/addon/wall_deco_1", "offset": [0, 0, 0]}]))[0]["source_translation"][1], 1.0)

    def test_fail_closed_on_unknown_and_unprovenanced_inputs(self):
        with self.assertRaisesRegex(ValueError, "no NightmareRunning"):
            recipe("o_unknown", [{"source_component": 1, "source_element": 0, "vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "source component"):
            recipe("o_c618", [{"source_element": 0, "vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "two authored source faces"):
            recipe("o_c654", [{"source_component": 1, "source_element": 0, "vertices": [[0, 0, 0, 0, 0]]}])


if __name__ == "__main__":
    unittest.main()
