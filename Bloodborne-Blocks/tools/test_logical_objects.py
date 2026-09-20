"""Focused regression tests for the additive logical-object generator."""
from __future__ import annotations

import gzip
import json
import tempfile
import unittest
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
        ids = {block["id"] for block in definitions["blocks"]}
        self.assertTrue({"o_ladder_03", "o_books_test", "o_wooden_box", "o_tombstone_gathered_8"} <= ids)
        self.assertFalse(any("cut_copper_stairs" in ident or "fence_gate_wall" in ident for ident in ids))
        self.assertNotIn("o_ladder", ids)


if __name__ == "__main__":
    unittest.main()
