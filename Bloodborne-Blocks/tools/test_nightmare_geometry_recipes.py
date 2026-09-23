"""Tests for source-provenance NightmareRunning geometry recipes."""
from __future__ import annotations

import copy
import json
import unittest
import zipfile
from pathlib import Path

from nightmare_geometry_recipes import recipe, transform


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "reference-inputs/source-resource-pack.zip"


def source_elements(model: str) -> list[dict]:
    """Read the actual source model (all reviewed models are parent-free)."""
    name = "assets/minecraft/models/" + model + ".json"
    with zipfile.ZipFile(PACK) as archive:
        return json.loads(archive.read(name))["elements"]


def canonical_fixture(model: str, component: int, offset=(0.0, 0.0, 0.0)) -> list[dict]:
    """One textured canonical polygon per real source element.

    Source-element identity is the material fact tested here; the production
    compiler may supply more than one face/tile polygon for an element.
    """
    polygons = []
    for number, element in enumerate(source_elements(model)):
        lo = element["from"]
        hi = element["to"]
        polygons.append({
            "source_component": component,
            "source_element": number,
            "texture": "minecraft:block/addon/spirelamp_test",
            "vertices": [[lo[0] / 16 + offset[0], lo[1] / 16 + offset[1], lo[2] / 16 + offset[2], 1.25, 2.5],
                         [hi[0] / 16 + offset[0], hi[1] / 16 + offset[1], hi[2] / 16 + offset[2], 3.75, 4.5],
                         [lo[0] / 16 + offset[0], hi[1] / 16 + offset[1], lo[2] / 16 + offset[2], 5.25, 6.5]],
        })
    return polygons


class NightmareGeometryRecipeTests(unittest.TestCase):
    def assert_floor_and_artwork(self, result: dict, original: list[dict]) -> None:
        vertices = [vertex for polygon in result["polygons"] for vertex in polygon["vertices"]]
        self.assertAlmostEqual(min(vertex[1] for vertex in vertices), 0.0)
        self.assertEqual(result["source_translation"][0], int(result["source_translation"][0]))
        self.assertEqual(result["source_translation"][2], int(result["source_translation"][2]))
        self.assertEqual(result["recommended_source_root_offset"][1], 0)
        self.assertEqual([polygon["texture"] for polygon in result["polygons"]], [polygon["texture"] for polygon in original])
        self.assertEqual([[vertex[3:] for vertex in polygon["vertices"]] for polygon in result["polygons"]],
                         [[vertex[3:] for vertex in polygon["vertices"]] for polygon in original])

    def test_c1491_splits_actual_source_elements_without_losing_artwork(self):
        mesh = canonical_fixture("block/addon/tombstone_gathered_1", 1)
        before = copy.deepcopy(mesh)
        results = recipe("o_c1491", mesh)
        self.assertEqual([result["name"] for result in results], ["o_c1491_a", "o_c1491_b", "o_c1491_c"])
        self.assertEqual([len(result["polygons"]) for result in results], [7, 13, 2])
        self.assertEqual(mesh, before)
        for result, elements in zip(results, (range(0, 7), range(7, 20), range(20, 22))):
            self.assertEqual([polygon["source_element"] for polygon in result["polygons"]], list(elements))
            self.assert_floor_and_artwork(result, [mesh[number] for number in elements])

    def test_component_splits_keep_each_actual_sack_and_spire_complete(self):
        sacks = canonical_fixture("block/addon/bag_0", 1) + canonical_fixture("block/addon/bag_0", 2, (1.0, 0.0, 1.0))
        spires = canonical_fixture("block/cracked_nether_bricks", 1) + canonical_fixture("block/cracked_nether_bricks", 2, (-1.0, 0.0, 1.0))
        for old_id, mesh, names in (("o_c1962", sacks, ["o_c1962_a", "o_c1962_b"]),
                                    ("o_c471", spires, ["o_c471_a", "o_c471_b"])):
            results = recipe(old_id, mesh)
            self.assertEqual([result["name"] for result in results], names)
            self.assertEqual([len(result["polygons"]) for result in results], [len(mesh) // 2, len(mesh) // 2])
            self.assert_floor_and_artwork(results[0], mesh[:len(mesh) // 2])
            self.assert_floor_and_artwork(results[1], mesh[len(mesh) // 2:])

    def test_cases_remove_only_documented_source_elements(self):
        c028 = canonical_fixture("block/addon/dark_oak_fence_gate_open", 1)
        c1680 = c028 + canonical_fixture("block/addon/crimson_fence_gate_open", 2, (-1.0, 0.0, 1.0))
        for old_id, mesh, expected in (("o_c028", c028, {1: set(range(12, 25))}),
                                       ("o_c1680", c1680, {1: set(range(12, 25)), 2: set(range(8, 16))})):
            result = recipe(old_id, mesh)[0]
            kept = {(polygon["source_component"], polygon["source_element"]) for polygon in result["polygons"]}
            self.assertEqual(kept, {(component, element) for component, elements in expected.items() for element in elements})
            self.assert_floor_and_artwork(result, [polygon for polygon in mesh if (polygon["source_component"], polygon["source_element"]) in kept])

    def test_unknown_or_unprovenanced_input_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "no NightmareRunning"):
            recipe("o_unknown", [{"source_component": 1, "source_element": 0, "vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "requires source_component"):
            recipe("o_c1491", [{"vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "unknown source provenance"):
            recipe("o_c1491", [{"source_component": 1, "source_element": 22, "vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "unknown source element"):
            recipe("o_c028", [{"source_component": 1, "source_element": 25, "vertices": [[0, 0, 0, 0, 0]]}])
        with self.assertRaisesRegex(ValueError, "exactly x, y, z"):
            transform([], (0, 0))


if __name__ == "__main__":
    unittest.main()
