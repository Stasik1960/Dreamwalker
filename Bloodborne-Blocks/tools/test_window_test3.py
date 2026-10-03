"""Contract-level regression evidence for the approved TEST3 shutter physics."""
from __future__ import annotations

import copy
import gzip
import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest
import sys
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
LOGICAL = ROOT / "src/main/resources/bloodborne_blocks/logical"
WINDOW = "o_shuttered_window"
FACING = {"north": (0.0, -1.0), "east": (1.0, 0.0), "south": (0.0, 1.0), "west": (-1.0, 0.0)}
CHECKPOINT = "a43c1131f"
sys.path.insert(0, str(ROOT / "tools"))


def props(key: str) -> dict[str, str]:
    return dict(part.split("=", 1) for part in key.split(","))


class WindowTest3Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.definitions = {row["id"]: row for row in json.loads((LOGICAL / "definitions.json").read_text())["blocks"]}
        cls.contract_doc = json.loads((LOGICAL / "contracts-v2.json").read_text())
        cls.contracts = {row["id"]: row for row in cls.contract_doc["families"]}
        cls.geometry_doc = json.loads((LOGICAL / "geometry.json").read_text())
        cls.physical_doc = json.loads((LOGICAL / "physical-footprints.json").read_text())
        cls.meshes = json.loads(gzip.decompress((LOGICAL / "meshes.json.gz").read_bytes()))
        cls.window = cls.contracts[WINDOW]

    def test_test2_measurement_proves_current_vertical_normalization(self) -> None:
        raw = subprocess.check_output(["git", "show", "f80a7fe3bd87bd47ba649997fee5ebee95ab8ba2:Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/contracts-v2.json"], cwd=ROOT.parent)
        baseline = next(row for row in json.loads(raw)["families"] if row["id"] == WINDOW)
        self.assertEqual([0, 0, 0], baseline["states"]["facing=north,open=false,visual=base"]["render_mesh"]["offset"])
        self.assertEqual(-.875, baseline["states"]["facing=north,open=false,visual=base"]["render_mesh"]["bounds"][1])
        for key, state in self.window["states"].items():
            self.assertEqual(.875, state["render_mesh"]["offset"][1], key)
            vertices = [v for polygon in self.meshes[self.definitions[WINDOW]["models"][key]]["polygons"] for v in polygon["vertices"]]
            self.assertEqual(0.0, round(min(v[1] for v in vertices) + state["render_mesh"]["offset"][1], 6), key)

    def test_all_states_use_fixed_two_cell_selection_and_collision(self) -> None:
        expected = [[0.0, 0.0, 0.0, 1.0, 1.0, 1.0], [0.0, 1.0, 0.0, 1.0, 2.0, 1.0]]
        for key, state in self.window["states"].items():
            facing = props(key)["facing"]
            vector_x, vector_z = FACING[facing]
            self.assertEqual([round(-.75 * vector_x, 6), .875, round(-.75 * vector_z, 6)], state["render_mesh"]["offset"], key)
            self.assertEqual([[0, 0, 0], [0, 1, 0]], state["interaction_footprint"]["cells"], key)
            self.assertEqual([[0.0, 0.0, 0.0, 1.0, 2.0, 1.0]], state["selection_footprint"]["boxes"], key)
            self.assertEqual(expected, state["collision_footprint"]["boxes"], key)
            self.assertEqual(expected, self.physical_doc["families"][WINDOW][key]["boxes"], key)
            self.assertEqual([[0, 0, 0], [0, 1, 0]], self.physical_doc["families"][WINDOW][key]["cells"], key)

    def test_legacy_profile_has_only_the_two_simple_cells(self) -> None:
        states = self.geometry_doc["blocks"][WINDOW]["states"]
        self.assertEqual(set(self.window["states"]), set(states))
        for key, generated in states.items():
            self.assertEqual([0.0, 0.0, 0.0, 1.0, 2.0, 1.0], generated["globalOutline"], key)
            self.assertEqual({"0,0,0", "0,1,0"}, set(generated["cells"]), key)
            for cell in generated["cells"].values():
                self.assertEqual([[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]], cell["collision"], key)
                self.assertEqual([[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]], cell["outline"], key)

    def test_patch_family_normalizes_old_and_new_window_equally(self) -> None:
        import apply_window_test3_patch as compiler
        old = json.loads(subprocess.check_output(["git", "show", f"{CHECKPOINT}:Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/contracts-v2.json"], cwd=ROOT.parent))
        old_window = next(row for row in old["families"] if row["id"] == WINDOW)
        new_window = copy.deepcopy(self.window)
        compiler.patch_family(old_window); compiler.patch_family(new_window)
        self.assertEqual(old_window, new_window)

    def test_non_window_generated_families_match_checkpoint(self) -> None:
        for name, key in (("contracts-v2.json", "families"), ("geometry.json", "blocks"), ("physical-footprints.json", "families")):
            before = json.loads(subprocess.check_output(["git", "show", f"{CHECKPOINT}:Bloodborne-Blocks/src/main/resources/bloodborne_blocks/logical/{name}"], cwd=ROOT.parent))
            after = json.loads((LOGICAL / name).read_text())
            if name == "contracts-v2.json":
                before = {row["id"]: row for row in before[key] if row["id"] != WINDOW}
                after = {row["id"]: row for row in after[key] if row["id"] != WINDOW}
            else:
                before = before[key]; after = after[key]
                before.pop(WINDOW, None); after.pop(WINDOW, None)
            # The user explicitly approved appending the historical dry bush
            # to this ID. All older tree states and all other families remain exact.
            if name=='physical-footprints.json':
                after['o_c001']={k:v for k,v in after['o_c001'].items()if 'variant=bush_asset_e,'not in k}
            else:
                after['o_c001']['states']={k:v for k,v in after['o_c001']['states'].items()if 'variant=bush_asset_e,'not in k}
            self.assertEqual(before, after, name)

    def test_generator_is_byte_identical_when_reapplied(self) -> None:
        import apply_window_test3_patch as compiler
        names = ("contracts-v2.json", "geometry.json", "physical-footprints.json")
        with tempfile.TemporaryDirectory(prefix="bloodborne-window-test3-") as directory:
            temporary = Path(directory)
            for name in names: shutil.copyfile(LOGICAL / name, temporary / name)
            before = {name: hashlib.sha256((temporary / name).read_bytes()).hexdigest() for name in names}
            with patch.object(compiler, "LOGICAL", temporary):
                for _ in range(2): self.assertEqual(16, compiler.patch()["states"])
            self.assertEqual(before, {name: hashlib.sha256((temporary / name).read_bytes()).hexdigest() for name in names})


if __name__ == "__main__": unittest.main()
