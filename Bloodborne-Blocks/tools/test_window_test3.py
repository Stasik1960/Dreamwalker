"""Contract-level regression evidence for the bounded TEST3 shutter correction."""
from __future__ import annotations

import gzip
import hashlib
import json
import subprocess
import shutil
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
WINDOW = "o_shuttered_window"
FACING = {"north": (0.0, -1.0), "east": (1.0, 0.0),
          "south": (0.0, 1.0), "west": (-1.0, 0.0)}


def props(key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in key.split(","))


class WindowTest3Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.definitions = {row["id"]: row for row in json.loads((LOGICAL / "definitions.json").read_text())["blocks"]}
        cls.contracts = {row["id"]: row for row in json.loads((LOGICAL / "contracts-v2.json").read_text())["families"]}
        cls.geometry = json.loads((LOGICAL / "geometry.json").read_text())["blocks"]
        cls.physical = json.loads((LOGICAL / "physical-footprints.json").read_text())["families"]
        cls.meshes = json.loads(gzip.decompress((LOGICAL / "meshes.json.gz").read_bytes()))
        cls.window = cls.contracts[WINDOW]

    def test_reproduced_test2_baseline_is_measured_not_inferred_from_screenshots(self) -> None:
        raw = subprocess.check_output([
            "git", "show", "f80a7fe3bd87bd47ba649997fee5ebee95ab8ba2:Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/contracts-v2.json"
        ], cwd=ROOT.parent)
        baseline = next(row for row in json.loads(raw)["families"] if row["id"] == WINDOW)
        state = baseline["states"]["facing=north,open=false,visual=base"]
        self.assertEqual([0, 0, 0], state["render_mesh"]["offset"])
        self.assertEqual(-.875, state["render_mesh"]["bounds"][1])
        self.assertEqual([[0, 0, 0], [0, 1, 0]], state["interaction_footprint"]["cells"])
        self.assertEqual([0.0, 0, 0.0, 1.0, 1, .125], state["collision_footprint"]["boxes"][0])

    def test_fixed_sill_plane_and_rear_frame_mount_for_every_visual_state(self) -> None:
        definition = self.definitions[WINDOW]
        for key, state in self.window["states"].items():
            facing = props(key)["facing"]
            vector_x, vector_z = FACING[facing]
            self.assertEqual([round(-.75 * vector_x, 6), .875, round(-.75 * vector_z, 6)], state["render_mesh"]["offset"], key)
            vertices = [vertex[:3] for polygon in self.meshes[definition["models"][key]]["polygons"] for vertex in polygon["vertices"]]
            # Polygon 8 is the closed/open invariant underside of the sill;
            # its source y is -.875, so the corrected support contact is zero.
            sill_min_y = min(vertex[1] for vertex in vertices)
            self.assertEqual(0.0, round(sill_min_y + state["render_mesh"]["offset"][1], 6), key)
            bounds = state["render_mesh"]["bounds"]
            expected_outline = [round(bounds[0] + state["render_mesh"]["offset"][0], 6),
                                round(bounds[1] + state["render_mesh"]["offset"][1], 6),
                                round(bounds[2] + state["render_mesh"]["offset"][2], 6),
                                round(bounds[3] + state["render_mesh"]["offset"][0], 6),
                                round(bounds[4] + state["render_mesh"]["offset"][1], 6),
                                round(bounds[5] + state["render_mesh"]["offset"][2], 6)]
            self.assertEqual(expected_outline, state["selection_footprint"]["boxes"][0], key)

    def test_authored_stationary_frame_and_sill_reach_wall_and_floor(self) -> None:
        # Authored elements 0/1 compile into leaf polygons 0..3; elements 2/3
        # compile into sill/frame polygons 4..14. No geometric leaf guessing.
        for key, state in self.window['states'].items():
            polygons = self.meshes[self.definitions[WINDOW]['models'][key]]['polygons']
            vertices = [v[:3] for polygon in polygons[4:] for v in polygon['vertices']]
            offset = state['render_mesh']['offset']
            positions = [[v[i]+offset[i] for i in range(3)] for v in vertices]
            self.assertAlmostEqual(0, min(v[1] for v in positions), places=5)
            facing = props(key)['facing']
            axis = 2 if facing in ('north', 'south') else 0
            rear = max(v[axis] for v in positions) if facing in ('north', 'west') else min(v[axis] for v in positions)
            self.assertAlmostEqual(1 if facing in ('north', 'west') else 0, rear, places=5)

    def test_collision_and_physical_masks_are_thin_rear_cell_local_column(self) -> None:
        expectations = {
            "north": (2, .875, 1.0), "east": (0, 0.0, .125),
            "south": (2, 0.0, .125), "west": (0, .875, 1.0),
        }
        for key, state in self.window["states"].items():
            facing = props(key)["facing"]
            axis, low, high = expectations[facing]
            boxes = state["collision_footprint"]["boxes"]
            self.assertEqual([[0, 0, 0], [0, 1, 0]], state["interaction_footprint"]["cells"], key)
            self.assertEqual(boxes, self.physical[WINDOW][key]["boxes"], key)
            self.assertEqual([[0, 0, 0], [0, 1, 0]], self.physical[WINDOW][key]["cells"], key)
            self.assertEqual(2, len(boxes), key)
            for index, box in enumerate(boxes):
                self.assertEqual(low, box[axis], key)
                self.assertEqual(high, box[axis + 3], key)
                self.assertEqual(index, int(box[1]), key)
                self.assertEqual(index + 1, int(box[4]), key)
                self.assertLess(high - low, .2, key)

    def test_legacy_per_cell_profile_matches_contract(self) -> None:
        states = self.geometry[WINDOW]["states"]
        self.assertEqual(set(self.window["states"]), set(states))
        for key, state in self.window["states"].items():
            generated = states[key]
            self.assertEqual(state["render_mesh"]["offset"], generated["render_offset"], key)
            self.assertEqual(state["selection_footprint"]["boxes"][0], generated["globalOutline"], key)
            self.assertEqual({"0,0,0", "0,1,0"}, set(generated["cells"]), key)

    def test_physical_file_changes_only_the_exact_test3_window_boxes(self) -> None:
        # This mask file did not exist at the older root-state checkpoint.
        # Use the immediate TEST2 checkpoint and compare the entire document.
        from apply_window_test3_patch import rear_collision_boxes
        raw = subprocess.check_output([
            "git", "show", "f80a7fe3bd87bd47ba649997fee5ebee95ab8ba2:Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/physical-footprints.json"
        ], cwd=ROOT.parent)
        expected = json.loads(raw)
        for key, mask in expected['families'][WINDOW].items():
            mask['boxes'] = rear_collision_boxes(props(key)['facing'])
        self.assertEqual(expected, json.loads((LOGICAL / 'physical-footprints.json').read_bytes()))

    def test_generator_is_byte_identical_when_reapplied(self) -> None:
        import apply_window_test3_patch as compiler
        names = ("contracts-v2.json", "geometry.json", "physical-footprints.json")
        with tempfile.TemporaryDirectory(prefix='bloodborne-window-test3-') as directory:
            temporary = Path(directory)
            for name in names:
                shutil.copyfile(LOGICAL / name, temporary / name)
            before = {name: hashlib.sha256((temporary / name).read_bytes()).hexdigest() for name in names}
            with patch.object(compiler, 'LOGICAL', temporary):
                for _ in range(2):
                    self.assertEqual(16, compiler.patch()['states'])
            self.assertEqual(before, {name: hashlib.sha256((temporary / name).read_bytes()).hexdigest() for name in names})


if __name__ == "__main__":
    unittest.main()
