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
STATUE_HAND_SOCKETS = {
    "o_c008_1": ([0.814422, 2.24161, 0.300141], "minecraft:block/hold/statue", 6, 5, "south"),
    "o_c008_2": ([1.001923, 1.122392, 0.153886], "minecraft:block/hold/statue_3", 5, 10, "down"),
    "o_c008_3": ([0.631753, 0.792948, -0.281602], "minecraft:block/hold/statue_4", 3, 7, "west"),
    "o_c008_5": ([1.092947, 0.57794, 0.123782], "minecraft:block/hold/statue_5", 4, 5, "north"),
}
STATUE_SOURCE_FACE_POLYGONS = {
    "o_c008_1": 31,
    "o_c008_2": 48,
    "o_c008_3": 40,
    "o_c008_5": 25,
}


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


def rotate_vertex(vertex: list[float], yaw: int) -> list[float]:
    x, z = vertex[0] - .5, vertex[2] - .5
    turns = (yaw // 90) % 4
    if yaw % 90:
        raise ValueError("statue facing yaw must be cardinal")
    if turns == 1:
        x, z = -z, x
    elif turns == 2:
        x, z = -x, -z
    elif turns == 3:
        x, z = z, -x
    return [round(.5 + x, 5), round(vertex[1], 5), round(.5 + z, 5), *vertex[3:]]


class BetaClientQaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definitions = {row["id"]: row for row in read(LOGICAL / "definitions.json")["blocks"]}
        cls.contracts = {row["id"]: row for row in read(LOGICAL / "contracts-v2.json")["families"]}
        cls.meshes = read(LOGICAL / "meshes.json.gz")
        cls.baseline = read(ROOT / "docs/beta-client-qa/baseline-fingerprints.json.gz")["fingerprints"]["families"]

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

    def test_statue_hand_sockets_match_frozen_source_faces_for_every_facing(self):
        lantern = self.definitions["o_lantern"]
        for ident, (socket, model, component, element, face) in STATUE_HAND_SOCKETS.items():
            source_state = self.baseline[ident]["core"]["state_evidence"][
                "facing=north,hand_lantern=none,visual=base"]
            source_face = source_state["mesh"]["polygons"][STATUE_SOURCE_FACE_POLYGONS[ident]]
            source_center = [round(sum(vertex[axis] for vertex in source_face["vertices"])
                                   / len(source_face["vertices"]), 6) for axis in range(3)]
            self.assertEqual(socket, source_center, f"{ident} source distal-face center")
            hand = self.contracts[ident]["hand_lantern"]
            self.assertEqual(socket, hand["position"], ident)
            self.assertEqual([0, 0, 0], hand["rotation"], ident)
            self.assertEqual({"kind": "source_distal_face_center", "model": model,
                              "component": component, "element": element, "face": face},
                             hand["position_authority"], ident)
            self.assertIn("no mounted single-lantern source composition", hand["scale_authority"], ident)
            for facing_index, facing in enumerate(FACINGS):
                yaw = facing_index * 90
                for mode, lit in (("unlit", "false"), ("lit", "true")):
                    lamp_key = f"facing=north,lit={lit},visual=base"
                    lamp = self.meshes[lantern["models"][lamp_key]]["polygons"]
                    expected = []
                    for polygon in lamp:
                        expected.append({**polygon, "vertices": [rotate_vertex(
                            [socket[index] + (vertex[index] - hand["source_pivot"][index]) * hand["scale"]
                             for index in range(3)] + vertex[3:], yaw) for vertex in polygon["vertices"]]})
                    for visual in ("base", "alt"):
                        state_key = f"facing={facing},hand_lantern={mode},visual={visual}"
                        actual = self.meshes[self.definitions[ident]["models"][state_key]]["polygons"][-len(expected):]
                        self.assertEqual(expected, actual, (ident, facing, mode, visual))


if __name__ == "__main__":
    unittest.main()
