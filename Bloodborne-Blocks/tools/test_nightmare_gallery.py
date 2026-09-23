"""Synthetic layout invariants for the all-visible NightmareRunning gallery."""
from __future__ import annotations

import unittest

from build_nightmare_gallery import (ALL_VISIBLE_SCOPE, CONTRACT_V2_SCOPE, FLOOR_Y, ROOT_Y, VISUAL_PROOF_IDS,
                                     _horizontal_extent, _load, add_light_pads, definitions_for_scope, geometry_cells,
                                     hidden_compatibility_ids, level_name,
                                     plan_positions, rendered_mesh_bounds, specimen_states, specimens_for_scope,
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
        rows = [{"id": "a", "bounds": (-2.2, -4, -1.2, 3.1, 9, 2.2), "footprint": [(0, 0, 0), (5, 0, 0)]},
                {"id": "b", "bounds": (0, 0, 0, 1, 1, 1), "footprint": [(0, 0, 0)]}]
        plan_positions(rows, gap=7, columns=1)
        self.assertTrue(all(row["position"][1] == ROOT_Y for row in rows))
        self.assertGreater(rows[1]["pad"][1], rows[0]["pad"][3] + 6)
        cells = {}
        add_light_pads(cells, rows, "minecraft:white_concrete")
        self.assertTrue(cells)
        self.assertEqual({point[1] for point in cells}, {FLOOR_Y})
        low_x, low_z, high_x, high_z = _horizontal_extent(rows[0]["bounds"], rows[0]["footprint"])
        self.assertGreaterEqual(rows[0]["position"][0] + low_x, rows[0]["pad"][0])
        self.assertLessEqual(rows[0]["position"][0] + high_x, rows[0]["pad"][2])

    def test_rendered_bounds_apply_contract_offset_once(self):
        mesh = {"polygons": [{"vertices": [[0, -1, 0], [1, 2, 1]]}]}
        self.assertEqual(rendered_mesh_bounds(mesh, {"offset": [2, 1, -3]}), (2, 0, -3, 3, 3, -2))

    def test_contract_scope_includes_every_contract_state_and_hidden_family(self):
        specimens, definitions, contracts = specimens_for_scope(CONTRACT_V2_SCOPE)
        self.assertEqual({row["id"] for row in definitions}, set(contracts))
        self.assertTrue({"o_c001_a", "o_c009_b", "o_dead_tree_planter"} <= {row["id"] for row in definitions})
        self.assertEqual(sum(len(row["states"]) for row in contracts.values()) + len(VISUAL_PROOF_IDS) * 2, len(specimens))
        self.assertTrue(all(row["purpose"] == "contract_state" for row in specimens[:-len(VISUAL_PROOF_IDS) * 2]))
        for specimen in specimens:
            family = contracts.get(specimen["id"])
            if family and family["placement_policy"] == "FLOOR":
                self.assertTrue(all(cell[1] >= 0 for cell in specimen["footprint"]))
                self.assertGreaterEqual(specimen["bounds"][1], -1e-6)
        hidden = set(hidden_compatibility_ids(definitions, _load()[0]))
        self.assertTrue(hidden)
        self.assertTrue(all(row["hidden_compatibility"] == (row["id"] in hidden) for row in specimens))

    def test_scope_selector_preserves_all_visible_default(self):
        visible, _geometry, _meshes, contracts = _load()
        self.assertEqual(definitions_for_scope(ALL_VISIBLE_SCOPE, visible, contracts), visible)
        self.assertEqual(level_name(ALL_VISIBLE_SCOPE), "Bloodborne NightmareRunning all-visible gallery")

    def test_gallery_matches_manual_item_placement_overrides(self):
        row = {"id": "o_shuttered_window", "default": {"embedded": "true"},
               "properties": {"embedded": ["true", "false"]},
               "placement_properties": {"embedded": "false"},
               "states": {"embedded=true": [], "embedded=false": []}}
        self.assertEqual(specimen_states(row), [("embedded=false", {"embedded": "false"}, "default")])


if __name__ == "__main__":
    unittest.main()
