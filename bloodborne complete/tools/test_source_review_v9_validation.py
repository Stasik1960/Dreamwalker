"""Offline regressions for the V9 source mask and typed live-RP validators."""
import unittest
import tempfile
import zipfile
from pathlib import Path
import world_io as wi
from prepare_source_review_v9_plan import DESCRIPTORS, frozen_descriptor_contract, registered_composite_paths
from verify_source_review_saved import axis_box, collision_yaw, expected_root, rp_v9_role_differences, unhandled_source_differences


class SourceReviewV9Validation(unittest.TestCase):
    def fields(self, identity, **extra):
        return {"id": wi.Tag(wi.TAG_STRING, "bloodborne_rp:" + identity), **extra}

    def test_descriptor_guard_covers_new_registered_glass_before_preparation(self):
        registered = registered_composite_paths()
        self.assertEqual(len(registered), 6)
        self.assertIn("prototype_glass_window_02", registered)
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "descriptor-contract.jar"
            for include_window02 in (False, True):
                with zipfile.ZipFile(jar, "w") as archive:
                    for kind in sorted(registered):
                        if include_window02 or kind != "prototype_glass_window_02":
                            archive.writestr("bloodborne_dw/composite/" + kind + ".json", (DESCRIPTORS / (kind + ".json")).read_bytes())
                if include_window02:
                    self.assertEqual(set(frozen_descriptor_contract(jar)), registered)
                else:
                    with self.assertRaisesRegex(ValueError, "Frozen JAR descriptor set"):
                        frozen_descriptor_contract(jar)

    def test_source_ladder_v9_exact_mount_property_keeps_historical_contract(self):
        row = {"kind": "source_ladder_pair", "physical": [1, 2, 3], "variant": 2, "profile": "base",
               "members": [{"pos": [1, 2, 3], "properties": {"facing": "south", "waterlogged": "false"}}]}
        current = expected_root(row, "V9")
        self.assertEqual(current, "bloodborne_dw:prototype_ladder[diagonal=false,facing=south,freestanding=false,profile=base,source_clone=true,variant=2,waterlogged=false]")
        self.assertNotIn("freestanding", expected_root(row, "V8"))
        self.assertEqual(expected_root(row, "V8"), expected_root(row))

    def test_grid_cube_does_not_follow_visual_eight_yaws(self):
        spec = {"collisionRotation": "GRID_ALIGNED"}
        cube = {"from": [0, 0, 0], "to": [16, 16, 16]}
        for visual in range(8):
            physical = collision_yaw(spec, visual)
            self.assertEqual(physical, 0)
            self.assertEqual(axis_box(cube, physical, [0, -.1875, .3125]),
                             ([0, -.1875, .3125], [1, .8125, 1.3125]))

    def test_authored_outline_cardinal_yaw_is_separate(self):
        box = {"from": [0, 0, 0], "to": [16, 16, 8]}
        self.assertEqual(axis_box(box, collision_yaw({}, 2), [0, 0, 0]), ([.5, 0, 0], [1, 1, 1]))
        with self.assertRaises(ValueError):
            axis_box(box, collision_yaw({}, 1), [0, 0, 0])
        with self.assertRaises(ValueError):
            collision_yaw({"collisionRotation": "unexpected"}, 0)

    def test_dog_instance_false_byte_is_not_defaulted_or_coerced(self):
        original = self.fields("cage_obj_1", DogsVisible=wi.Tag(wi.TAG_BYTE, 0))
        saved = {**original, "RpBehaviourVersion": wi.Tag(wi.TAG_INT, 1)}
        self.assertEqual(rp_v9_role_differences(original, saved), [])
        for invalid in (wi.Tag(wi.TAG_BYTE, 1), wi.Tag(wi.TAG_INT, 0)):
            self.assertTrue(rp_v9_role_differences(original, {**saved, "DogsVisible": invalid}))
        legacy = self.fields("cage_obj_2")
        self.assertEqual(rp_v9_role_differences(legacy, {**legacy, "RpBehaviourVersion": wi.Tag(wi.TAG_INT, 1),
                                                     "DogsVisible": wi.Tag(wi.TAG_BYTE, 1)}), [])

    def test_ladder_saved_countdown_can_finish_but_not_restart(self):
        original = self.fields("ladder", RpBehaviourVersion=wi.Tag(wi.TAG_INT, 1), LadderDeployTicks=wi.Tag(wi.TAG_INT, 24))
        self.assertEqual(rp_v9_role_differences(original, {**original, "LadderDeployTicks": wi.Tag(wi.TAG_INT, 0)}, reopening=True), [])
        self.assertTrue(rp_v9_role_differences(original, {**original, "LadderDeployTicks": wi.Tag(wi.TAG_INT, 48)}, reopening=True))
        self.assertTrue(rp_v9_role_differences(original, {**original, "LadderDeployTicks": wi.Tag(wi.TAG_BYTE, 24)}, reopening=True))

    def test_gate_tracker_does_not_add_a_persisted_pulse_queue(self):
        original = self.fields("wood_gate")
        saved = {**original, "RpBehaviourVersion": wi.Tag(wi.TAG_INT, 1)}
        self.assertEqual(rp_v9_role_differences(original, saved), [])
        self.assertTrue(rp_v9_role_differences(original, {**saved, "WoodGatePulseTicks": wi.Tag(wi.TAG_INT, 32)}))
        opaque = {**original, "GateRequests": wi.Tag(wi.TAG_COMPOUND, {"legacy": wi.Tag(wi.TAG_LONG, 9)})}
        self.assertEqual(rp_v9_role_differences(opaque, {**saved, **opaque}), [])
        self.assertTrue(unhandled_source_differences(opaque, saved))

    def test_new_roles_do_not_hide_lost_or_retyped_original_payload(self):
        original = self.fields("npc_window", CanUpdate=wi.Tag(wi.TAG_BYTE, 0),
                               AnimationId=wi.Tag(wi.TAG_STRING, "legacy"),
                               Unknown=wi.Tag(wi.TAG_LIST, [wi.Tag(wi.TAG_INT, 7), wi.Tag(wi.TAG_INT, 3)], wi.TAG_INT))
        saved = {**original, "RpBehaviourVersion": wi.Tag(wi.TAG_INT, 1)}
        self.assertEqual(unhandled_source_differences(original, saved), [])
        self.assertTrue(unhandled_source_differences(original, {**saved, "CanUpdate": wi.Tag(wi.TAG_INT, 0)}))
        self.assertTrue(unhandled_source_differences(original, {k: v for k, v in saved.items() if k != "AnimationId"}))
        self.assertTrue(unhandled_source_differences(original, {**saved, "Unknown": wi.Tag(wi.TAG_LIST, list(reversed(original["Unknown"].value)), wi.TAG_INT)}))


if __name__ == "__main__":
    unittest.main(verbosity=2)
