"""Actual source-pack tests for NightmareRunning composition recipes."""
from __future__ import annotations

import json
import os
import unittest
from copy import deepcopy
from pathlib import Path

os.environ.setdefault("BLOODBORNE_VANILLA_JAR", str(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))

from nightmare_composition_recipes import recipe, tagged_polys_for_components


ROOT = Path(__file__).resolve().parents[1]
ROWS = {row["review_id"]: row for row in json.loads((ROOT / "docs/manual-source-assemblies.json").read_text(encoding="utf8"))["candidates"]}


def components(review_id: str):
    return ROWS[review_id]["source_patterns"][0]["components"]


class NightmareCompositionRecipeTests(unittest.TestCase):
    def test_c008_preserves_five_requested_families_and_two_instances_for_number_three(self):
        source = tagged_polys_for_components(components("C008"), range(1, 7))
        output = recipe("o_c008", source)
        self.assertEqual([row["name"] for row in output], [f"o_c008_{number}" for number in range(1, 6)])
        third = output[2]
        self.assertEqual(third["provenance"]["source_components"], [3, 1])
        self.assertEqual(third["source_translation"][1], -third["source_bounds"][1])
        high = output[0]
        self.assertEqual(high["source_translation"][1], 0.0)
        self.assertAlmostEqual(min(vertex[1] for polygon in high["polygons"] for vertex in polygon["vertices"]), 0.0)

    def test_c1979_filters_context_before_generation_and_uses_screenshot_groups(self):
        source = tagged_polys_for_components(components("C1979"), {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13})
        self.assertNotIn(1, {polygon["source_component"] for polygon in source})
        output = recipe("o_c1979", source)
        self.assertEqual([row["provenance"]["source_components"] for row in output],
                         [[2, 12], [6, 13], [3, 4, 5], [7, 9], [8, 10]])
        self.assertTrue(all(row["source_translation"] == (-1.0, 0.0, 0.0) for row in output))
        with self.assertRaisesRegex(ValueError, "context"):
            recipe("o_c1979", source + tagged_polys_for_components(components("C1979"), {1}))

    def test_c002_keeps_all_sixty_six_actual_source_elements_and_floors_them(self):
        source = tagged_polys_for_components(components("C002"), {1, 2})
        output = recipe("o_c002", source)[0]
        elements = {(polygon["source_component"], polygon["source_element"]) for polygon in output["polygons"]}
        self.assertEqual(len({element for component, element in elements if component == 1}), 34)
        self.assertEqual(len({element for component, element in elements if component == 2}), 32)
        self.assertEqual(len(elements), 66)
        self.assertAlmostEqual(min(vertex[1] for polygon in output["polygons"] for vertex in polygon["vertices"]), 0.0)
        self.assertAlmostEqual(output["source_translation"][1], 1.0)

    def test_unknown_input_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "no NightmareRunning"):
            recipe("o_unknown", [])
        with self.assertRaisesRegex(ValueError, "requested source component"):
            tagged_polys_for_components(components("C002"), {3})

    def test_c008_rejects_mismatched_duplicate_statue_instance(self):
        source = tagged_polys_for_components(components("C008"), range(1, 7))
        mismatched = deepcopy(source)
        polygon = next(row for row in mismatched if row["source_component"] == 1)
        polygon["vertices"][0][3] += 0.25  # same placement, no longer same textured geometry
        with self.assertRaisesRegex(ValueError, "#3/#6 no longer share"):
            recipe("o_c008", mismatched)


if __name__ == "__main__":
    unittest.main()
