from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import build_accepted_bush_extension as extension
from logical_contract_v2 import load_contracts


class AcceptedBushExtensionTests(unittest.TestCase):
    def test_bundle_is_one_family_and_preserves_the_frozen_form(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "bush-extension.json"
            bundle = extension.build(output)
            self.assertEqual(bundle, json.loads(output.read_text(encoding="utf-8")))
        self.assertEqual([extension.IDENT], [row["id"] for row in bundle["definitions"]["blocks"]])
        self.assertEqual({extension.IDENT}, set(bundle["geometry"]["blocks"]))
        self.assertEqual({}, bundle["geometry"]["profiles"])
        self.assertNotIn("oldContracts", bundle)
        self.assertEqual([extension.IDENT], [row["id"] for row in bundle["currentContracts"]["families"]])
        self.assertEqual(extension.IDENT, bundle["productionPaletteEntry"]["id"])

    def test_all_facing_visual_states_keep_two_empty_collision_cells_and_outlines(self):
        bundle = extension.build(Path(tempfile.gettempdir()) / "accepted-bush-extension-test.json")
        profile = bundle["geometry"]["blocks"][extension.IDENT]['states']['facing=north,visual=base']
        self.assertEqual(extension.read_frozen()['geometry']['profiles'][extension.PROFILE],profile)
        self.assertEqual({"0,0,0", "0,1,0"}, set(profile["cells"]))
        for cell in profile["cells"].values():
            self.assertEqual([], cell["collision"])
            self.assertEqual(2, len(cell["outline"]))
        expected = {extension.state_key(facing, visual) for facing in extension.FACINGS for visual in extension.VISUALS}
        self.assertEqual(expected, set(bundle["definitions"]["blocks"][0]["states"]))
        self.assertEqual(expected, set(bundle["currentContracts"]["families"][0]["states"]))
        for footprint in bundle["physical"]["families"][extension.IDENT].values():
            self.assertEqual([[0, 0, 0], [0, 1, 0]], footprint["cells"])
            self.assertEqual([], footprint["boxes"])

    def test_only_grass_zero_weighted_choice_has_a_migration_pattern_and_mesh_proof(self):
        bundle = extension.build(Path(tempfile.gettempdir()) / "accepted-bush-extension-test.json")
        states = bundle["currentContracts"]["families"][0]["states"]
        patterned = [key for key, state in states.items() if state["migration_source_pattern"]]
        self.assertEqual(["facing=north,visual=base"], patterned)
        pattern = states[patterned[0]]["migration_source_pattern"][0]
        component = pattern["components"][0]
        self.assertEqual(extension.SOURCE, component["id"])
        self.assertEqual([0], pattern["variant_guards"][0]["indices"])
        self.assertEqual([f"minecraft:block/addon/grass_{i}" for i in range(8)],
                         [option[0]["model"] for option in component["model_choices"][0]])

    def test_current_contract_uses_the_existing_schema_helper(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = extension.build(root / "bush-extension.json")
            (root / "definitions.json").write_text(json.dumps(bundle["definitions"]), encoding="utf-8")
            (root / "contracts-v2.json").write_text(json.dumps(bundle["currentContracts"]), encoding="utf-8")
            (root / "physical-footprints.json").write_text(json.dumps(bundle["physical"]), encoding="utf-8")
            (root / "production-palette.json").write_text(
                json.dumps({"schemaVersion": 1, "objects": [bundle["productionPaletteEntry"]]}), encoding="utf-8")
            (root / "transform-v2.json").write_bytes(
                (extension.ROOT / "src/main/resources/bloodborne_blocks/logical/transform-v2.json").read_bytes())
            contracts, _transform = load_contracts(root)
        self.assertEqual([extension.IDENT], [family["id"] for family in contracts["families"]])


if __name__ == "__main__":
    unittest.main()
