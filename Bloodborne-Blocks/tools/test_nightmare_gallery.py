"""Synthetic layout invariants for the all-visible NightmareRunning gallery."""
from __future__ import annotations

import unittest

from build_nightmare_gallery import (FLOOR_Y, ROOT_Y, VISUAL_PROOF_IDS, add_light_pads,
                                     geometry_cells, plan_positions, specimen_states,
                                     visible_definitions, visual_proof_states)


class NightmareGalleryTests(unittest.TestCase):
    def test_visible_definitions_respect_hidden_and_creative(self):
        rows = [{"id": "shown", "logical": True, "creative": True}, {"id": "o_c001_a", "logical": True, "creative": True}, {"id": "hidden", "logical": True, "creative": True},
                {"id": "not_creative", "logical": True, "creative": False}, {"id": "vanilla", "logical": False, "creative": True}]
        self.assertEqual([row["id"] for row in visible_definitions(rows, ["hidden"], ["shown"])], [])

    def test_visual_proof_creates_real_base_alt_pairs(self):
        rows = []
        for ident in VISUAL_PROOF_IDS:
            base = {"facing": "north", "visual": "base"}
            rows.append({"id": ident, "default": base, "properties": {"facing": ["north"], "visual": ["base", "alt"]},
                         "states": {"facing=north,visual=base": [], "facing=north,visual=alt": []}})
        proof = visual_proof_states(rows)
        self.assertEqual(len(VISUAL_PROOF_IDS) * 2, len(proof))
        self.assertEqual({(definition["id"], properties["visual"], purpose)
                          for definition, _state, properties, purpose in proof},
                         {(ident, visual, "visual_proof_" + visual)
                          for ident in VISUAL_PROOF_IDS for visual in ("base", "alt")})

    def test_state_selection_has_four_facings_and_one_functional_representative(self):
        door = {"id": "o_door", "properties": {"facing": ["north", "east", "south", "west"], "open": ["false", "true"]},
                "default": {"facing": "north", "open": "false"},
                "states": {f"facing={facing},open={open_}": [0, 0, 0] for facing in ("north", "east", "south", "west") for open_ in ("false", "true")}}
        selected = specimen_states(door)
        self.assertEqual(len(selected), 5)
        self.assertEqual({properties["facing"] for _key, properties, purpose in selected if purpose == "default"}, set(("north", "east", "south", "west")))
        self.assertEqual([properties["open"] for _key, properties, purpose in selected if purpose == "open_representative"], ["true"])

    def test_contract_footprint_precedes_legacy_profile_and_profiles_resolve(self):
        contracts = {"v2": {"states": {"": {"interaction_footprint": {"cells": [[0, 0, 0], [1, 0, 0]]}}}}}
        geometry = {"blocks": {"old": {"states": {"": {"ref": "p"}}}}, "profiles": {"p": {"cells": {"0,-1,0": {}}}}}
        self.assertEqual(geometry_cells("v2", "", contracts, geometry), [(0, 0, 0), (1, 0, 0)])
        self.assertEqual(geometry_cells("old", "", contracts, geometry), [(0, -1, 0)])

    def test_sparse_layout_keeps_fixed_root_height_and_void_gap(self):
        rows = [{"id": "a", "bounds": (-2.2, -4, -1.2, 3.1, 9, 2.2)}, {"id": "b", "bounds": (0, 0, 0, 1, 1, 1)}]
        plan_positions(rows, gap=7, columns=1)
        self.assertTrue(all(row["position"][1] == ROOT_Y for row in rows))
        self.assertGreater(rows[1]["pad"][1], rows[0]["pad"][3] + 6)
        cells = {}
        add_light_pads(cells, rows, "minecraft:white_concrete")
        self.assertTrue(cells)
        self.assertEqual({point[1] for point in cells}, {FLOOR_Y})

    def test_gallery_matches_manual_item_placement_overrides(self):
        row = {"id": "o_shuttered_window", "default": {"embedded": "true"},
               "properties": {"embedded": ["true", "false"]},
               "placement_properties": {"embedded": "false"},
               "states": {"embedded=true": [], "embedded=false": []}}
        self.assertEqual(specimen_states(row), [("embedded=false", {"embedded": "false"}, "default")])


if __name__ == "__main__":
    unittest.main()
