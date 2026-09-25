"""Regression gates for the four client-QA defects fixed for 2.1.0-beta.1.

The checks intentionally consume only the current production payload.  They do
not scan a world or rebuild the production palette.
"""
from __future__ import annotations

import gzip
import json
import math
import unittest
from pathlib import Path

from production_fingerprints import verify


ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
FACINGS = ("north", "east", "south", "west")
EPSILON = 1.0e-6


def read(path: Path):
    raw = path.read_bytes()
    return json.loads(gzip.decompress(raw) if path.suffix == ".gz" else raw)


def properties(state_key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in state_key.split(","))


def occupied_cells(box: list[float]) -> set[tuple[int, int, int]]:
    ranges = []
    for minimum, maximum in zip(box[:3], box[3:]):
        start = math.floor(minimum + EPSILON)
        stop = math.ceil(maximum - EPSILON)
        ranges.append(range(start, stop))
    return {(x, y, z) for x in ranges[0] for y in ranges[1] for z in ranges[2]}


def contains(box: list[float], x: float, y: float, z: float) -> bool:
    return all(box[index] - EPSILON <= value <= box[index + 3] + EPSILON
               for index, value in enumerate((x, y, z)))


class BetaClientQaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definitions = {row["id"]: row for row in read(LOGICAL / "definitions.json")["blocks"]}
        cls.contracts = {row["id"]: row for row in read(LOGICAL / "contracts-v2.json")["families"]}
        cls.meshes = read(LOGICAL / "meshes.json.gz")

    def test_only_explicit_beta_families_changed(self):
        result = verify(baseline_path=ROOT / "docs/beta-client-qa/baseline-fingerprints.json.gz",
                        allowlist_path=ROOT / "docs/beta-client-qa/allowlist.json")
        self.assertEqual("PASS", result["result"])

    def test_collision_never_leaves_explicit_owned_cells(self):
        for family in self.contracts.values():
            for state_key, state in family["states"].items():
                owned = {tuple(int(value) for value in cell)
                         for cell in state["interaction_footprint"]["cells"]}
                for box in state["collision_footprint"]["boxes"]:
                    self.assertLessEqual(
                        occupied_cells(box), owned,
                        f"COLLISION_OUTSIDE_OWNED_CELLS: {family['id']}[{state_key}] {box}",
                    )

    def test_ladder03_is_inverted_thin_and_cell_local_for_every_facing(self):
        family = self.contracts["o_ladder_03"]
        expected_edges = {
            "north": (2, 0.0, 0.125),
            "east": (0, 0.875, 1.0),
            "south": (2, 0.875, 1.0),
            "west": (0, 0.0, 0.125),
        }
        for state_key, state in family["states"].items():
            facing = properties(state_key)["facing"]
            self.assertEqual({(0, -1, 0), (0, 0, 0)},
                             {tuple(cell) for cell in state["interaction_footprint"]["cells"]})
            boxes = state["collision_footprint"]["boxes"]
            self.assertEqual(2, len(boxes), state_key)
            axis, minimum, maximum = expected_edges[facing]
            for box in boxes:
                self.assertEqual(1, len(occupied_cells(box)), (state_key, box))
                self.assertAlmostEqual(minimum, box[axis], places=6)
                self.assertAlmostEqual(maximum, box[axis + 3], places=6)
            mesh = self.meshes[self.definitions["o_ladder_03"]["models"][state_key]]
            vertices = [vertex for polygon in mesh["polygons"] for vertex in polygon["vertices"]]
            self.assertLessEqual(min(vertex[axis] for vertex in vertices), minimum + 0.0625)

    def test_ladder01_deck_is_split_per_owned_cell_and_fully_supported(self):
        family = self.contracts["o_ladder_01"]
        state = family["states"]["facing=north,visual=base"]
        boxes = state["collision_footprint"]["boxes"]
        self.assertEqual(9, len(boxes))
        for box in boxes:
            self.assertEqual(1, len(occupied_cells(box)), box)
        for x in (-0.1, 0.5, 1.1):
            for z in (-0.9, 0.5, 1.9):
                self.assertTrue(any(contains(box, x, 0.8, z) for box in boxes), (x, z))

    def test_window_is_an_ordinary_two_cell_column(self):
        definition = self.definitions["o_shuttered_window"]
        family = self.contracts["o_shuttered_window"]
        self.assertNotIn("embedded", definition["properties"])
        self.assertNotIn("embedded", definition.get("placement_properties", {}))
        self.assertEqual(16, len(definition["states"]))
        for state_key, state in family["states"].items():
            self.assertEqual({(0, 0, 0), (0, 1, 0)},
                             {tuple(cell) for cell in state["interaction_footprint"]["cells"]}, state_key)
            boxes = state["collision_footprint"]["boxes"]
            self.assertEqual(2, len(boxes), state_key)
            for box in boxes:
                self.assertEqual(1, len(occupied_cells(box)), (state_key, box))
                self.assertGreaterEqual(min(box[0], box[2]), -EPSILON)
                self.assertLessEqual(max(box[3], box[5]), 1.0 + EPSILON)

    def test_lantern_and_statues_use_distinct_source_lit_artwork(self):
        lantern = self.definitions["o_lantern"]
        unlit_key = "facing=north,lit=false,visual=base"
        lit_key = "facing=north,lit=true,visual=base"
        self.assertNotEqual(lantern["models"][unlit_key], lantern["models"][lit_key])
        unlit = self.meshes[lantern["models"][unlit_key]]["polygons"]
        lit = self.meshes[lantern["models"][lit_key]]["polygons"]
        self.assertFalse(any(polygon["texture"].endswith("/flower_pot") for polygon in unlit))
        self.assertTrue(any(polygon["texture"].endswith("/flower_pot") for polygon in lit))
        for ident in ("o_c008_1", "o_c008_2", "o_c008_3", "o_c008_5"):
            definition = self.definitions[ident]
            unlit_key = "facing=north,hand_lantern=unlit,visual=base"
            lit_key = "facing=north,hand_lantern=lit,visual=base"
            self.assertNotEqual(definition["models"][unlit_key], definition["models"][lit_key], ident)
            unlit = self.meshes[definition["models"][unlit_key]]["polygons"]
            lit = self.meshes[definition["models"][lit_key]]["polygons"]
            self.assertFalse(any(polygon["texture"].endswith("/flower_pot") for polygon in unlit), ident)
            self.assertTrue(any(polygon["texture"].endswith("/flower_pot") for polygon in lit), ident)


if __name__ == "__main__":
    unittest.main()
