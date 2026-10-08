"""Source-versus-generated checks for the reviewed window prototype resources."""
import hashlib
import io
import json
from pathlib import Path
import unittest
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/architecture/resources"
PACK = Path("C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip")


def load(path):
    return json.loads((RES / path).read_text(encoding="utf8"))


def descriptor(key):
    return load("bloodborne_dw/composite/" + key + ".json")


def contains(boxes, point):
    return any(all(box["from"][i] <= point[i] <= box["to"][i] for i in range(3)) for box in boxes)


class WindowSourceTests(unittest.TestCase):
    def test_imported_geometry_uv_and_authored_pivots_equal_source(self):
        with zipfile.ZipFile(PACK) as archive:
            for path in ("block/wood_window", "block/hold/window_01", "block/hold/window_02", "block/hold/window_03"):
                source = json.loads(archive.read("assets/minecraft/models/" + path + ".json"))
                imported = load("assets/bloodborne_dw/models/base/source/minecraft/" + path + ".json")
                self.assertEqual(source["elements"], imported["elements"])
                self.assertEqual(source.get("display"), imported.get("display"))
                self.assertEqual(source.get("texture_size"), imported.get("texture_size"))

    def test_three_source_elements_recombine_without_redrawing(self):
        imported = load("assets/bloodborne_dw/models/base/source/minecraft/block/wood_window.json")
        parts = [load("assets/bloodborne_dw/models/base/prototype_windows/wood/" + name + ".json") for name in ("frame", "left", "right")]
        self.assertEqual(imported["elements"], [part["elements"][0] for part in parts])
        for part in parts:
            self.assertEqual(imported["textures"], part["textures"])

    def test_texture_animation_and_emissive_bytes_are_exact(self):
        report = json.loads((ROOT / "reports/WINDOW_ASSET_PROPOSAL.json").read_text(encoding="utf8"))
        with zipfile.ZipFile(PACK) as archive:
            for entry in report["textures"]:
                actual = (RES / entry["target"]).read_bytes()
                self.assertEqual(archive.read(entry["source"]), actual)
                self.assertEqual(entry["sha256"], hashlib.sha256(actual).hexdigest())

    def test_only_side_parts_move_at_their_authored_pivots(self):
        poses = descriptor("prototype_wood_window")["variants"][0]["poses"]
        self.assertEqual(poses["closed"]["parts"][0], poses["open"]["parts"][0])
        for index, pivot, yaw in ((1, [-8, 0, 7], -45), (2, [24, 0, 7], 45)):
            before, after = poses["closed"]["parts"][index], poses["open"]["parts"][index]
            self.assertEqual(before["extraYaw"], 0)
            self.assertEqual(after["extraYaw"], yaw)
            self.assertEqual(after["extraPivot"], pivot)
            self.assertEqual({k: v for k, v in before.items() if k != "extraYaw"}, {k: v for k, v in after.items() if k != "extraYaw"})

    def test_fixed_opaque_center_blocks_passage_in_both_poses(self):
        data = descriptor("prototype_wood_window")
        poses = data["variants"][0]["poses"]
        for pose in poses.values():
            self.assertTrue(contains(pose["collision"], [8, 8, 8]))
            self.assertFalse(contains(pose["collision"], [8, 8, -10]), "empty gap between side panels must not become the whole object's solid AABB")
        self.assertFalse(data["proposal"]["centralPanelTraversable"])
        self.assertNotEqual(poses["closed"]["collision"], poses["open"]["collision"])

    def test_thin_forms_keep_both_faces_and_real_uv_difference(self):
        forms = [load(f"assets/bloodborne_dw/models/base/source/minecraft/block/hold/window_{number:02}.json")["elements"][0] for number in range(1, 4)]
        for form in forms:
            self.assertEqual(set(form["faces"]), {"north", "south"})
            self.assertTrue(all("cullface" not in face for face in form["faces"].values()))
        self.assertNotEqual(forms[0]["faces"], forms[1]["faces"])
        self.assertEqual(forms[1]["faces"], forms[2]["faces"])
        self.assertEqual(forms[2]["rotation"]["angle"], -45)
        self.assertEqual(forms[2]["from"][2], forms[1]["from"][2] - 4)

    def test_intrinsic_45_is_not_baked_a_second_time(self):
        data = descriptor("prototype_thin_window")
        self.assertEqual(data["globalOrientations"], 8)
        self.assertEqual(data["orientationStepDegrees"], 45)
        for form in data["variants"]:
            for part in form["poses"]["closed"]["parts"]:
                self.assertEqual(part["extraYaw"], 0)
        self.assertEqual(data["variants"][2]["sourceIntrinsicYaw"], -45)
        self.assertEqual(data["id"], "bloodborne_dw:prototype_thin_window")

    def test_diagonal_thin_form_does_not_fill_its_aabb_corner(self):
        boxes = descriptor("prototype_thin_window")["variants"][2]["poses"]["closed"]["collision"]
        low = [min(box["from"][axis] for box in boxes) for axis in range(3)]
        high = [max(box["to"][axis] for box in boxes) for axis in range(3)]
        self.assertGreater(len(boxes), 1)
        corners = [[x, (low[1] + high[1]) / 2, z] for x in (low[0] + .1, high[0] - .1) for z in (low[2] + .1, high[2] - .1)]
        self.assertGreaterEqual(sum(not contains(boxes, corner) for corner in corners), 2, "both empty diagonal AABB corners stay empty")
        self.assertTrue(all(box["to"][0] - box["from"][0] <= 1 + 1e-8 for box in boxes))

    def test_physical_and_selection_geometry_do_not_reserve_placement_volume(self):
        for name in ("prototype_wood_window", "prototype_thin_window"):
            data = descriptor(name)
            self.assertEqual(data["essentialMask"], [[0, 0, 0]])
            self.assertEqual(data["placementPolicy"], "SOFT_OVERLAP")
            for form in data["variants"]:
                for pose in form["poses"].values():
                    self.assertIn("collision", pose)
                    self.assertIn("selection", pose)
                    self.assertTrue(any(any(number < 0 or number > 16 for number in box["from"] + box["to"]) for box in pose["selection"]))


if __name__ == "__main__":
    output = io.StringIO()
    result = unittest.TextTestRunner(stream=output, verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(WindowSourceTests))
    report = {"schema": "dreamwalker-window-offline-regression-v1", "status": "PASS" if result.wasSuccessful() else "FAIL",
              "command": [sys.executable, str(Path(__file__).resolve())], "tests": result.testsRun,
              "failures": len(result.failures), "errors": len(result.errors), "output": output.getvalue(),
              "source_pack_sha256": hashlib.sha256(PACK.read_bytes()).hexdigest(),
              "game_runtime": "NOT_RUN", "client_render": "NOT_RUN", "numeric_ids_frozen": False}
    destination = ROOT / "reports/WINDOW_ASSET_TESTS.json"
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(output.getvalue(), end="")
    raise SystemExit(0 if result.wasSuccessful() else 1)
