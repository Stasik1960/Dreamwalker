"""Focused regression tests for the additive logical-object generator."""
from __future__ import annotations

import gzip
import itertools
import json
import tempfile
import unittest
from collections import defaultdict
from pathlib import Path

import build_logical_objects as logical


class LogicalObjectTests(unittest.TestCase):
    def test_missing_pack_fails_before_any_output(self):
        previous = logical.ORIGINAL_PACK
        try:
            with tempfile.TemporaryDirectory() as temporary:
                logical.ORIGINAL_PACK = Path(temporary) / "missing.zip"
                with self.assertRaisesRegex(ValueError, "No output was changed"):
                    logical.build()
        finally:
            logical.ORIGINAL_PACK = previous

    def test_state_key_is_canonical(self):
        self.assertEqual(logical.key({"facing": "north", "open": "false"}), "facing=north,open=false")
        self.assertEqual(logical.parsed("open=false,facing=north"), {"open": "false", "facing": "north"})

    def test_curated_family_cannot_silently_omit_all_migration_rules(self):
        curation = [{'id': 'o_window'}]
        with self.assertRaisesRegex(ValueError, 'o_window'):
            logical.require_curated_rules(curation, [])
        with self.assertRaisesRegex(ValueError, 'o_window'):
            logical.require_curated_rules(curation, [{'target': {'id': 'o_other'}}])
        logical.require_curated_rules(curation, [{'target': {'id': 'o_window'}}])

    def test_auto_groups_merge_only_proven_mount_rotations(self):
        def record(model, x, uvlock=False):
            return ("lamp", f"source_{model}_{x}_{uvlock}", {"assembled": "false"},
                    {"model": model, "x": x, "uvlock": uvlock, "y": x}, {})
        groups = logical.group_auto_sources([
            record("minecraft:block/shared", 0), record("minecraft:block/shared", 90), record("minecraft:block/shared", 180),
            record("minecraft:block/other", 0), record("minecraft:block/shared", 270), record("minecraft:block/shared", 0, True),
        ])
        merged = [group for group in groups if group["mount"]]
        self.assertEqual(len(merged), 1)
        self.assertEqual(merged[0]["model"], "minecraft:block/shared")
        self.assertFalse(merged[0]["uvlock"])
        self.assertEqual({app["x"] for _ident, _state, app, _evidence in merged[0]["sources"]}, {0, 90, 180})
        self.assertEqual({(app["x"], app["y"], app["uvlock"]) for _ident, _state, app, _evidence in merged[0]["sources"]},
                         {(0, 0, False), (90, 90, False), (180, 180, False)})
        self.assertTrue(any(not group["mount"] and group["model"] == "minecraft:block/other" for group in groups))
        self.assertTrue(any(not group["mount"] and group["x"] == 270 for group in groups))
        self.assertTrue(any(not group["mount"] and group["uvlock"] for group in groups))

    def test_mount_group_preserves_source_orientation_and_generates_full_states(self):
        group = {"semantic": "lamp", "model": "minecraft:block/shared", "uvlock": False, "mount": True, "sources": []}
        self.assertEqual(len(logical.auto_group_states(group)), 12)
        self.assertEqual(logical.auto_group_target({"x": 90, "y": 180}, group), {"facing": "south", "face": "wall"})
        self.assertEqual(logical.auto_group_target({"x": 180, "y": 270}, group), {"facing": "west", "face": "ceiling"})

    def test_unmerged_collision_id_uses_legacy_signature(self):
        occupied = {"o_brown_terracotta"}
        signature = ("bloodborne_blocks:block/brown_terracotta", 0, False)
        self.assertEqual(logical.unique_id("brown_terracotta", occupied, json.dumps(signature)), "o_brown_terracotta_0cc6fca1")

    def test_degenerate_polygon_is_rejected(self):
        self.assertFalse(logical.nondegenerate({"vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [2, 0, 0, 2, 0]]}))

    def test_gzip_is_reproducible(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "mesh.json.gz"
            logical.dump_gzip(path, {"x": 1})
            first = path.read_bytes()
            logical.dump_gzip(path, {"x": 1})
            self.assertEqual(first, path.read_bytes())
            self.assertEqual(json.load(gzip.open(path, "rt", encoding="utf-8")), {"x": 1})

    def test_import_sliver_requires_exact_bounded_expansion(self):
        original = {"from": [0, 0, 0], "to": [16, 0, 16]}
        self.assertTrue(logical.comparable_import_sliver(
            {"from": [0, -.25, 0], "to": [16, .25, 16]}, original))
        self.assertFalse(logical.comparable_import_sliver(
            {"from": [0, -.25001, 0], "to": [16, .25001, 16]}, original))

    def test_auto_requires_authored_source_artwork(self):
        overrides = {"bloodborne_blocks:block/reviewed": {"category": "ladder"}}
        original_model = logical.original_model
        try:
            logical.original_model = lambda name: None
            self.assertFalse(logical.proven_authored_artwork("minecraft:block/ladder", overrides))
            logical.original_model = lambda name: {"elements": [{"from": [0, 0, 0], "to": [1, 1, 1]}]}
            self.assertTrue(logical.proven_authored_artwork("bloodborne_blocks:block/reviewed", overrides))
        finally:
            logical.original_model = original_model

    def test_auto_categories_accept_only_reviewed_whole_architecture_semantics(self):
        catalog, _overrides = logical.semantic_catalog()
        self.assertTrue({"column", "trim", "roof", "floor", "floor_decoration", "bench"} <= logical.AUTO_CATEGORIES)
        self.assertFalse({"masonry", "slab", "stairs"} & logical.AUTO_CATEGORIES)
        self.assertEqual({ident: catalog[ident]["category"] for ident in
                          ("gold_block", "gilded_blackstone", "blackstone", "cracked_nether_bricks")},
                         {"gold_block": "trim", "gilded_blackstone": "column",
                          "blackstone": "roof", "cracked_nether_bricks": "roof"})

    def test_new_categories_follow_existing_groups_without_changing_their_order(self):
        self.assertFalse((logical.AUTO_CATEGORIES - logical.PREVIOUS_AUTO_CATEGORIES) & logical.PREVIOUS_AUTO_CATEGORIES)
        self.assertEqual(logical.PREVIOUS_AUTO_CATEGORIES,
                         {"bush", "plant", "container", "statue", "ornament", "ladder", "lamp"})

    def test_palette_report_compacts_component_occurrences(self):
        full = {"schemaVersion": 1, "modules": {"m_one": {
            "classification": "standalone", "provenance": ["old"], "occurrences": [{"components": ["large"]}],
            "replacement_logical_ids": ["o_one"],
            "coverage": {"reachable": 2, "covered": 1, "remaining": [{"components": ["large"]}],
                         "uncovered_reason": ["incomplete_logical_coverage"]}, "safe_hidden": False}},
            "legacy": {"safe_hidden_ids": []}, "safe_hidden_ids": []}
        report = logical.compact_palette_classification(full)
        self.assertNotIn("occurrences", report["modules"]["m_one"])
        self.assertEqual(report["modules"]["m_one"]["coverage"]["remaining"], 1)

    def test_final_hidden_items_honors_creative_module_vetoes(self):
        previous = ["old_legacy", "m_safe", "m_unreferenced", "m_noncreative"]
        classification = {"safe_hidden_ids": ["m_safe", "old_classifier"]}
        definitions = {"blocks": [{"id": "m_safe", "creative": True},
                                    {"id": "m_unreferenced", "creative": True},
                                    {"id": "m_noncreative", "creative": False}]}
        self.assertEqual(logical.final_hidden_items(previous, classification, definitions),
                         ["m_noncreative", "m_safe", "old_classifier", "old_legacy"])

    def test_curated_subset_does_not_suppress_other_auto_states(self):
        original_assets, original_catalog = logical.ASSETS, logical.semantic_catalog
        original_apps, original_proven = logical.apps_for_state, logical.proven_authored_artwork
        original_polys, original_read = logical.object_polys, logical.read
        try:
            with tempfile.TemporaryDirectory() as temporary:
                logical.ASSETS = Path(temporary)
                (logical.ASSETS / "blockstates").mkdir()
                (logical.ASSETS / "blockstates" / "source.json").touch()
                logical.semantic_catalog = lambda: ({"source": {"category": "column", "ru": "Источник"}}, {})
                logical.read = lambda _path: {"variants": {}}
                logical.apps_for_state = lambda _blockstate, _state: [{"model": "minecraft:block/proven"}]
                logical.proven_authored_artwork = lambda _model, _overrides: True
                logical.object_polys = lambda _apps: ([], [])
                legacy = {"blocks": [{"id": "source", "states": {
                    "assembled=false,facing=north": [0, 0, 0], "assembled=false,facing=south": [0, 0, 0]}}]}
                auto, _meshes, _geometry, rules, _names = logical.auto_objects(
                    legacy, {}, set(), {("source", "assembled=false,facing=north")}, {"column"})
        finally:
            logical.ASSETS, logical.semantic_catalog = original_assets, original_catalog
            logical.apps_for_state, logical.proven_authored_artwork = original_apps, original_proven
            logical.object_polys, logical.read = original_polys, original_read
        self.assertEqual(len(auto), 1)
        self.assertEqual([rule["source"]["properties"]["facing"] for rule in rules], ["south"])

    def test_authored_clip_x_preserves_uv_clips_boxes_before_yaw_and_leaves_default_path(self):
        original_model, original_corners = logical.model, logical.corners
        try:
            logical.model = lambda _name: {"textures": {"all": "minecraft:block/stone"}, "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"north": {"texture": "#all"}}}]}
            logical.corners = lambda element, _app: logical.np.array(
                list(itertools.product(*zip(element["from"], element["to"]))), float) / 16
            plain = {"model": "test:model", "y": 90, "offset": [2, 0, 3]}
            plain_polygons, plain_boxes = logical.authored_polys(plain)
            repeated_polygons, repeated_boxes = logical.authored_polys(dict(plain))
            self.assertEqual(plain_polygons, repeated_polygons)
            self.assertTrue(all(logical.np.array_equal(left, other_left)
                                and logical.np.array_equal(upper, other_upper) and planar == other_planar
                                for (left, upper, planar), (other_left, other_upper, other_planar)
                                in zip(plain_boxes, repeated_boxes)))
            polygons, boxes = logical.authored_polys({**plain, "clip_x": [.25, .75]})
        finally:
            logical.model, logical.corners = original_model, original_corners
        self.assertEqual(len(polygons), 1)  # The clip does not invent a cap face.
        self.assertEqual([vertex[3] for vertex in polygons[0]["vertices"]], [4.0, 12.0, 12.0, 4.0])
        self.assertEqual(len(boxes), 1)
        self.assertTrue(logical.np.array_equal(boxes[0][0], [2, 0, 3.25]))
        self.assertTrue(logical.np.array_equal(boxes[0][1], [3, 1, 3.75]))
        self.assertFalse(boxes[0][2])

    def test_owned_boxes_clamp_only_ownership_and_reject_invalid_bounds(self):
        boxes = [(logical.np.array([-.1, -.2, -.1]), logical.np.array([1.1, 1.2, 1.1]), False),
                 (logical.np.array([1, 0, 0]), logical.np.array([2, 1, 1]), False)]
        self.assertIs(logical.owned_boxes(boxes), boxes)
        clamped = logical.owned_boxes(boxes, [[0, 0, 0], [1, 1, 1]])
        self.assertEqual(len(clamped), 1)
        self.assertTrue(logical.np.array_equal(clamped[0][0], [0, 0, 0]))
        self.assertTrue(logical.np.array_equal(clamped[0][1], [1, 1, 1]))
        self.assertFalse(clamped[0][2])
        for bounds in ([[0, 0], [1, 1]], [[0, 0, 0], [1, float("nan"), 1]],
                       [[0, 0, 0], [0, 1, 1]]):
            with self.assertRaises(ValueError):
                logical.owned_boxes(boxes, bounds)

    def test_geometry_owns_only_visible_element_volume_cells(self):
        cube = logical.geometry_for([], [(logical.np.array([0, 0, 0]), logical.np.array([1, 1, 1]), False)], "container")
        self.assertEqual(set(cube["cells"]), {"0,0,0"})

        face = {"texture": "test", "vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 16, 0],
                                                       [1, 0, 1, 16, 16], [0, 0, 1, 0, 16]]}
        alpha_patch = logical.alpha_patch
        try:
            logical.alpha_patch = lambda _face: logical.np.array([[255]])
            planar = logical.geometry_for([face], [(logical.np.array([0, 0, 0]), logical.np.array([1, 0, 1]), True)], "ornament")
            self.assertEqual(set(planar["cells"]), {"0,0,0"})
            logical.alpha_patch = lambda _face: logical.np.array([[0]])
            self.assertEqual(logical.geometry_for([face], [(logical.np.array([0, 0, 0]), logical.np.array([1, 0, 1]), True)], "ornament")["cells"], {})
        finally:
            logical.alpha_patch = alpha_patch

        rotated_solid = logical.geometry_for([], [(logical.np.array([.2, 0, .2]), logical.np.array([1.8, 1, .8]), False)], "container")
        self.assertEqual(set(rotated_solid["cells"]), {"0,0,0", "1,0,0"})

    def test_floor_contact_lip_anchors_small_containers_at_zero(self):
        profile = logical.geometry_for([], [(logical.np.array([.25, -1 / 16, .25]), logical.np.array([.75, 1, .75]), False)], "container")
        self.assertEqual(set(profile["cells"]), {"0,0,0"})
        self.assertEqual(profile["anchor"], [0, 0, 0])

    def test_architectural_sprite_flange_has_selection_but_no_collision(self):
        face = {"texture": "test", "vertices": [[0, 0, .5, 0, 0], [1, 0, .5, 16, 0],
                                                   [1, 1, .5, 16, 16], [0, 1, .5, 0, 16]]}
        alpha_patch = logical.alpha_patch
        try:
            logical.alpha_patch = lambda _face: logical.np.array([[255]])
            for semantic in ("column", "trim", "roof"):
                profile = logical.geometry_for([face], [(logical.np.array([0, 0, .5]), logical.np.array([1, 1, .5]), True)], semantic)
                self.assertTrue(profile["cells"]["0,0,0"]["outline"])
                self.assertFalse(profile["cells"]["0,0,0"]["collision"])
            fence = logical.geometry_for([face], [(logical.np.array([0, 0, .5]), logical.np.array([1, 1, .5]), True)], "fence")
            self.assertTrue(fence["cells"]["0,0,0"]["collision"])
        finally:
            logical.alpha_patch = alpha_patch

    def test_floor_contact_lip_does_not_change_render_polygons(self):
        polygon = {"texture": "test", "vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 16, 0],
                                                        [1, 0, 1, 16, 16], [0, 0, 1, 0, 16]]}
        original = json.loads(json.dumps(polygon))
        alpha_patch = logical.alpha_patch
        try:
            logical.alpha_patch = lambda _face: logical.np.array([[255]])
            logical.geometry_for([polygon], [(logical.np.array([0, -1 / 16, 0]), logical.np.array([1, 1, 1]), False)], "ornament")
        finally:
            logical.alpha_patch = alpha_patch
        self.assertEqual(polygon, original)

    def test_floor_contact_lip_keeps_substantial_negative_geometry(self):
        profile = logical.geometry_for([], [(logical.np.array([.25, -.2, .25]), logical.np.array([.75, 1, .75]), False)], "container")
        self.assertEqual(profile["anchor"][1], -1)

    def test_floor_contact_lip_excludes_lanterns(self):
        profile = logical.geometry_for([], [(logical.np.array([.25, -1 / 16, .25]), logical.np.array([.75, 1, .75]), False)], "lamp")
        self.assertEqual(profile["anchor"][1], -1)

    def test_generated_contract(self):
        # The root validator is independent of this module and checks UV range,
        # geometry, complete state mapping and no runtime double rotations.
        from check_logical_resources import validate
        result = validate()
        self.assertTrue(result["ok"])
        self.assertGreater(result["logicalObjects"], 0)
        definitions = logical.read(logical.LOGICAL / "definitions.json")
        blocks = {block["id"]: block for block in definitions["blocks"]}
        ids = set(blocks)
        self.assertTrue({"o_ladder_03", "o_books_test", "o_wooden_box", "o_tombstone_gathered_8"} <= ids)
        self.assertFalse({"o_cut_copper_stairs", "o_fence_gate_wall"} & ids)
        self.assertEqual([blocks[ident]["semantic"] for ident in
                          ("o_exposed_cut_copper_stairs_bottom", "o_exposed_cut_copper_stairs_top")], ["trim", "trim"])
        self.assertNotIn("o_ladder", ids)

        rules = logical.read(logical.LOGICAL / "migration.json")["rules"]
        shutter = blocks["o_shuttered_window"]
        self.assertEqual(shutter["default"]["embedded"], "true")
        self.assertEqual(shutter["placement_properties"], {"embedded": "false"})
        shutter_rules = [rule for rule in rules if rule["target"]["id"] == shutter["id"]]
        self.assertTrue(shutter_rules)
        self.assertTrue(all(rule["target"]["properties"]["embedded"] == "true" for rule in shutter_rules))
        meshes = json.load(gzip.open(logical.LOGICAL / "meshes.json.gz", "rt", encoding="utf-8"))
        for facing in logical.FACING:
            for opened in ("false", "true"):
                carrier = shutter["models"][f"embedded=true,facing={facing},open={opened}"]
                standalone = shutter["models"][f"embedded=false,facing={facing},open={opened}"]
                self.assertGreater(len(meshes[carrier]["polygons"]), len(meshes[standalone]["polygons"]))

        geometry = logical.read(logical.LOGICAL / "geometry.json")
        for ident in ("o_ornate_balustrade", "o_carved_balustrade", "o_stepped_balustrade"):
            rows = [rule for rule in rules if rule["target"]["id"] == ident]
            self.assertTrue(rows)
            targets_by_source = defaultdict(set)
            for rule in rows:
                targets_by_source[json.dumps(rule["source"], sort_keys=True)].add(
                    json.dumps(rule["target"]["properties"], sort_keys=True))
            self.assertTrue(all(len(targets) == 1 for targets in targets_by_source.values()))
            for state in geometry["blocks"][ident]["states"].values():
                profile = geometry["profiles"][state["ref"]]
                self.assertTrue(all(int(cell.split(",")[0]) == 0 and int(cell.split(",")[2]) == 0
                                    for cell in profile["cells"]))

        legacy = logical.read(logical.RES / "bloodborne_blocks/definitions.json")
        v2_definitions = logical.read(logical.RES / "bloodborne_blocks/v2/definitions.json")
        classification = logical.classify_palette(legacy, v2_definitions,
                                                   logical.read(logical.RES / "bloodborne_blocks/v2/migration.json"),
                                                   logical.read(logical.RES / "bloodborne_blocks/v2/sources.json"), {"rules": rules})
        expected_hidden = logical.final_hidden_items(logical.safe_hidden_items(legacy, rules), classification, v2_definitions)
        self.assertEqual(logical.read(logical.LOGICAL / "hidden-items.json"), expected_hidden)


if __name__ == "__main__":
    unittest.main()
