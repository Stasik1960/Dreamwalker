"""Safety-focused unit tests for accepted RC1 restoration planning."""
from __future__ import annotations

import sys
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import accepted_objects_restore as restore
from world_io import TAG_COMPOUND, TAG_LIST, TAG_STRING, Tag

DIM = "minecraft:overworld"
OLD = ("bloodborne_blocks:city_old", ())
MID = ("bloodborne_blocks:city_mid", ())
TARGET = ("bloodborne_blocks:o_target", ())
FOREIGN = ("minecraft:diamond_block", ())
PART = (restore.PART, ())
AIR = restore.AIR


class MemoryWorld:
    def __init__(self, cells, ticks=()):
        self.cells = dict(cells)
        self._ticks = set(ticks)

    def get(self, dimension, point):
        return self.cells.get((dimension, point))

    def ticks_at(self, dimension, points):
        return self._ticks & {(dimension, point) for point in points}


class SeedChunk:
    def __init__(self, palette):
        self.x = self.z = 0
        self.dimension = DIM
        self._root = {"sections": Tag(TAG_LIST, [Tag(TAG_COMPOUND, {
            "Y": Tag(3, 4), "block_states": Tag(TAG_COMPOUND, {
                "palette": Tag(TAG_LIST, palette, TAG_COMPOUND)})})], TAG_COMPOUND)}

    def root(self):
        return self._root


class SeedReference(MemoryWorld):
    def __init__(self, cells, palette):
        super().__init__(cells)
        self.chunks = {"seed": SeedChunk(palette)}


def custom_entity():
    return Tag(TAG_COMPOUND, {"id": Tag(TAG_STRING, "minecraft:chest")})


def group(desired=None, helpers=None, outputs=None):
    desired = desired or {(0, 64, 0): TARGET}
    outputs = outputs or (restore.Output(TARGET, (0, 0, 0), frozenset({(0, 0, 0)})),)
    return {"id": "accepted-test", "number": 7, "dimension": DIM, "origin": (0, 64, 0),
            "desired": desired, "helpers": helpers or {}, "outputs": outputs}


class AcceptedObjectsRestoreTests(unittest.TestCase):
    def test_existing_output_is_rejected_before_reading_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);out=base/'existing';out.mkdir()
            with self.assertRaisesRegex(ValueError,'output must be a new directory'):
                restore.restore(base/'source.zip',out,resources=base/'resources',
                    report_path=base/'reports/result.json',report_root=base/'reports')

    def test_already_correct_with_scheduled_tick_is_not_accepted(self):
        world=MemoryWorld({(DIM,(0,64,0)):TARGET},{(DIM,(0,64,0))})
        self.assertEqual((None,'scheduled_tick_at_target'),restore.plan_group(group(),world,world,{},{},{},set()))

    def test_compile_group_replays_modded_chain_but_keeps_rc1_original(self):
        original = MemoryWorld({(DIM, (0, 64, 0)): OLD})
        entries = [
            (3, {"dimension": DIM, "decision": "converted", "changes": [
                {"position": [0, 64, 0], "before": "bloodborne_blocks:city_old", "after": "bloodborne_blocks:city_mid"}], "helpers": []}),
            (8, {"dimension": DIM, "decision": "converted", "changes": [
                {"position": [0, 64, 0], "before": "bloodborne_blocks:city_mid", "after": "bloodborne_blocks:o_target"}], "helpers": []}),
        ]
        compiled = restore.compile_group(0, entries, original, {TARGET: frozenset({(0, 0, 0)})})
        self.assertEqual(OLD, compiled["original"][(0, 64, 0)])
        self.assertEqual(TARGET, compiled["desired"][(0, 64, 0)])
        self.assertEqual([3, 8], compiled["entries"])

    def test_compile_group_rejects_chain_with_wrong_modded_source(self):
        original = MemoryWorld({(DIM, (0, 64, 0)): OLD})
        entries = [(1, {"dimension": DIM, "decision": "converted", "changes": [
            {"position": [0, 64, 0], "before": "bloodborne_blocks:city_mid", "after": "bloodborne_blocks:o_target"}], "helpers": []})]
        with self.assertRaisesRegex(ValueError, "accepted_source_mismatch"):
            restore.compile_group(0, entries, original, {TARGET: frozenset({(0, 0, 0)})})

    def test_plan_group_rejects_any_changed_rc1_state_nbt_or_tick(self):
        base = group()
        reference = MemoryWorld({(DIM, (0, 64, 0)): OLD})
        cases = [
            (MemoryWorld({(DIM, (0, 64, 0)): FOREIGN}), {}, "rc1_cells_or_nbt_changed"),
            (MemoryWorld({(DIM, (0, 64, 0)): OLD}), {(DIM, 0, 64, 0): custom_entity()}, "rc1_cells_or_nbt_changed"),
            (MemoryWorld({(DIM, (0, 64, 0)): OLD}, {(DIM, (0, 64, 0))}), {}, "scheduled_tick_at_target"),
        ]
        for world, entities, expected in cases:
            with self.subTest(expected=expected):
                _, reason = restore.plan_group(base, reference, world, {}, entities, {}, set())
                self.assertEqual(expected, reason)

    def test_plan_group_marks_correct_world_and_restore_candidate_atomic(self):
        base = group()
        reference = MemoryWorld({(DIM, (0, 64, 0)): OLD})
        correct = MemoryWorld({(DIM, (0, 64, 0)): TARGET})
        self.assertEqual((None, "already_correct"), restore.plan_group(base, reference, correct, {}, {}, {}, set()))
        candidate, reason = restore.plan_group(base, reference, MemoryWorld({(DIM, (0, 64, 0)): OLD}), {}, {}, {}, set())
        self.assertEqual("restore", reason)
        self.assertTrue(candidate.rule.atomic_owner_group)
        self.assertTrue(candidate.rule.shared_physics)
        self.assertEqual(((0, 64, 0), TARGET), candidate.output_roots[0])

    def test_plan_group_rejects_retired_cell_with_different_state_or_nbt(self):
        point = (0, 64, 0)
        base = group()
        reference = MemoryWorld({(DIM, point): OLD})
        retired = {(DIM, *point)}
        _, reason = restore.plan_group(base, reference, MemoryWorld({(DIM, point): OLD}), {}, {}, {}, retired)
        self.assertEqual("retired_cell_intersection", reason)
        retained = (1, 64, 0)
        helper = custom_entity()
        base = group({point: TARGET, retained: OLD}, {retained: helper})
        reference = MemoryWorld({(DIM, point): OLD, (DIM, retained): OLD})
        _, reason = restore.plan_group(base, reference, reference, {}, {}, {}, {(DIM, *retained)})
        self.assertEqual("retired_cell_intersection", reason)

    def test_dependency_requires_complete_outside_base_rc1_mask(self):
        root, helper, missing = (1, 64, 0), (0, 64, 0), (2, 64, 0)
        owner = "bloodborne_blocks:o_neighbour"
        bridge = restore.helper_tag(helper, {"Root": restore.block_pos_long(*root), "Owner": owner})
        result = restore.expand_rc1_dependencies(
            group({helper: PART}), MemoryWorld({(DIM, helper): PART, (DIM, root): (owner, ()), (DIM, missing): AIR}),
            {(DIM, *helper): bridge}, {(DIM, owner, root): {helper}},
            {(owner, ()): frozenset({(0, 0, 0), (-1, 0, 0), (1, 0, 0)})}, {TARGET: frozenset({(0, 0, 0)})}, {}, set())
        self.assertIn("incomplete_rc1_dependency_owner", result["preflightErrors"])

    def test_missing_target_helper_may_be_air_but_foreign_or_orphan_helper_fails(self):
        root, helper = (0, 64, 0), (1, 64, 0)
        owner = "bloodborne_blocks:o_target"
        source_group = group({root: TARGET})
        old_shapes = {TARGET: frozenset({(0, 0, 0), (1, 0, 0)})}
        shapes = {TARGET: frozenset({(0, 0, 0)})}
        allowed = restore.expand_rc1_dependencies(source_group.copy(), MemoryWorld({(DIM, root): TARGET, (DIM, helper): AIR}), {}, {(DIM, owner, root): set()}, old_shapes, shapes, {}, set())
        self.assertEqual([], allowed["preflightErrors"])
        foreign = restore.expand_rc1_dependencies(source_group.copy(), MemoryWorld({(DIM, root): TARGET, (DIM, helper): FOREIGN}), {}, {(DIM, owner, root): set()}, old_shapes, shapes, {}, set())
        self.assertIn("foreign_cell_in_incomplete_rc1_target_mask", foreign["preflightErrors"])
        orphan = restore.expand_rc1_dependencies(source_group.copy(), MemoryWorld({(DIM, root): TARGET, (DIM, helper): AIR}), {(DIM, *helper): custom_entity()}, {(DIM, owner, root): set()}, old_shapes, shapes, {}, set())
        self.assertIn("foreign_or_custom_block_entity", orphan["preflightErrors"])

    def test_dependency_closure_does_not_capture_unrelated_o_prefix(self):
        root, unrelated = (0, 64, 0), (5, 64, 0)
        result = restore.expand_rc1_dependencies(group({root: TARGET}), MemoryWorld({(DIM, root): TARGET, (DIM, unrelated): ("bloodborne_blocks:o_unrelated", ())}), {}, {(DIM, "bloodborne_blocks:o_target", root): set()}, {TARGET: frozenset({(0, 0, 0)})}, {TARGET: frozenset({(0, 0, 0)})}, {}, set())
        self.assertEqual([], result["preflightErrors"])
        self.assertNotIn(unrelated, result["desired"])

    def seed_entry(self, root, before="bloodborne_blocks:o_seed[variant=old]", target="bloodborne_blocks:o_seed[variant=old]"):
        return {"dimension": DIM, "decision": "converted", "conflictingCells": [],
                "rc1ExistingOwner": {"root": list(root), "before": before, "state": target},
                "changes": [{"position": list(root), "before": before, "after": target}], "helpers": []}

    def test_seed_uses_rc1_proof_not_modded_before_and_conflicts_fail_closed(self):
        seed_root, regular_root = (0, 64, 0), (2, 64, 0)
        old_seed = ("bloodborne_blocks:o_seed", (("variant", "old"),))
        target_seed = ("bloodborne_blocks:o_seed", (("variant", "new"),))
        entry = self.seed_entry(seed_root, "bloodborne_blocks:o_seed[variant=old]", "bloodborne_blocks:o_seed[variant=new]")
        regular = {"dimension": DIM, "decision": "converted", "changes": [{"position": list(regular_root),
            "before": "bloodborne_blocks:city_old", "after": "bloodborne_blocks:o_target"}], "helpers": []}
        compiled = restore.compile_group(3, [(0, regular), (1, entry)],
            MemoryWorld({(DIM, seed_root): FOREIGN, (DIM, regular_root): OLD}),
            {TARGET: frozenset({(0, 0, 0)}), target_seed: frozenset({(0, 0, 0)})},
            MemoryWorld({(DIM, seed_root): old_seed}))
        self.assertEqual(old_seed, compiled["original"][seed_root])
        self.assertEqual(target_seed, compiled["desired"][seed_root])
        self.assertEqual({seed_root}, compiled["seedRoots"])
        conflict = dict(regular); conflict["changes"] = [{"position": list(seed_root),
            "before": "bloodborne_blocks:city_old", "after": "bloodborne_blocks:o_target"}]
        with self.assertRaisesRegex(ValueError, "existing_rc1_owner_source_mismatch"):
            restore.compile_group(3, [(0, conflict), (1, entry)], MemoryWorld({(DIM, seed_root): OLD}),
                {TARGET: frozenset({(0, 0, 0)}), target_seed: frozenset({(0, 0, 0)})},
                MemoryWorld({(DIM, seed_root): old_seed}))

    def test_seed_requires_whole_old_mask_even_when_root_is_in_base(self):
        root, regular_root, helper = (0, 64, 0), (3, 64, 0), (1, 64, 0)
        old = ("bloodborne_blocks:o_seed", (("variant", "old"),))
        target = ("bloodborne_blocks:o_seed", (("variant", "new"),))
        seed = self.seed_entry(root, "bloodborne_blocks:o_seed[variant=old]", "bloodborne_blocks:o_seed[variant=new]")
        regular = {"dimension": DIM, "decision": "converted", "changes": [{"position": list(regular_root),
            "before": "bloodborne_blocks:city_old", "after": "bloodborne_blocks:o_target"}], "helpers": []}
        shapes = {target: frozenset({(0, 0, 0)}), TARGET: frozenset({(0, 0, 0)})}
        compiled = restore.compile_group(0, [(0, regular), (1, seed)], MemoryWorld({(DIM, regular_root): OLD}), shapes,
            MemoryWorld({(DIM, root): old, (DIM, helper): AIR}))
        expanded = restore.expand_rc1_dependencies(compiled, MemoryWorld({(DIM, root): old, (DIM, helper): AIR,
            (DIM, regular_root): OLD}), {}, {(DIM, old[0], root): set()},
            {old: frozenset({(0, 0, 0), (1, 0, 0)})}, shapes, {}, set())
        self.assertIn("incomplete_rc1_dependency_owner", expanded["preflightErrors"])

    def test_seed_shrink_keeps_shared_co_owner_binding_and_root(self):
        left, helper, right = (0, 64, 0), (1, 64, 0), (2, 64, 0)
        left_old = ("bloodborne_blocks:o_left", (("v", "old"),)); left_new = ("bloodborne_blocks:o_left", (("v", "new"),))
        right_state = ("bloodborne_blocks:o_right", ())
        seed = self.seed_entry(left, "bloodborne_blocks:o_left[v=old]", "bloodborne_blocks:o_left[v=new]")
        shapes = {left_new: frozenset({(0, 0, 0)}), right_state: frozenset({(0, 0, 0), (-1, 0, 0)})}
        compiled = restore.compile_group(0, [(0, seed)], MemoryWorld({}), shapes,
            MemoryWorld({(DIM, left): left_old}))
        binding = restore.helper_tag(helper, {"Root": restore.block_pos_long(*left), "Owner": left_old[0],
            "Owners": [{"Root": restore.block_pos_long(*left), "Owner": left_old[0]},
                       {"Root": restore.block_pos_long(*right), "Owner": right_state[0]}]})
        expanded = restore.expand_rc1_dependencies(compiled, MemoryWorld({(DIM, left): left_old, (DIM, helper): PART,
            (DIM, right): right_state}), {(DIM, *helper): binding},
            {(DIM, left_old[0], left): {helper}, (DIM, right_state[0], right): {helper}},
            {left_old: frozenset({(0, 0, 0), (1, 0, 0)}), right_state: frozenset({(0, 0, 0), (-1, 0, 0)})},
            shapes, {right_state[0]: {"default": {}}}, set())
        self.assertEqual([], expanded["preflightErrors"])
        self.assertEqual(right_state, expanded["desired"][right])
        self.assertEqual(PART, expanded["desired"][helper])
        self.assertEqual([(right_state[0], right)], restore.bindings(expanded["helpers"][helper]))

    def test_seed_new_footprint_cannot_expand_into_ordinary_foreign_block(self):
        root, foreign = (0, 64, 0), (1, 64, 0)
        old = ("bloodborne_blocks:o_seed", (("v", "old"),)); target = ("bloodborne_blocks:o_seed", (("v", "new"),))
        seed = self.seed_entry(root, "bloodborne_blocks:o_seed[v=old]", "bloodborne_blocks:o_seed[v=new]")
        shapes = {target: frozenset({(0, 0, 0), (1, 0, 0)})}
        compiled = restore.compile_group(0, [(0, seed)], MemoryWorld({}), shapes, MemoryWorld({(DIM, root): old}))
        expanded = restore.expand_rc1_dependencies(compiled, MemoryWorld({(DIM, root): old, (DIM, foreign): FOREIGN}), {},
            {(DIM, old[0], root): set()}, {old: frozenset({(0, 0, 0)})}, shapes, {}, set())
        self.assertIn("foreign_block_in_dependency_footprint", expanded["preflightErrors"])

    def test_rc1_owner_entries_seed_only_unprotected_roots_and_merge_omitted_defaults(self):
        before = ("bloodborne_blocks:o_seed", (("facing", "south"), ("variant", "old")))
        tag = restore.as_tag_state(before)
        reference = SeedReference({}, [tag])
        definitions = {before[0]: {"default": {"fixed": "default", "variant": "new"}}}
        target = (before[0], (("facing", "south"), ("fixed", "default"), ("variant", "old")))
        with patch.object(restore, "section_blocks", return_value=([tag], [0, 0])):
            rows = restore.rc1_owner_entries(reference, {target: frozenset({(0, 0, 0)})}, definitions,
                {before: frozenset({(0, 0, 0), (1, 0, 0)})},
                [{"dimension": DIM, "changes": [{"position": [0, 64, 0]}]}])
        self.assertEqual(1, len(rows))
        self.assertEqual([1, 64, 0], rows[0]["rc1ExistingOwner"]["root"])
        self.assertEqual("bloodborne_blocks:o_seed[facing=south,fixed=default,variant=old]", rows[0]["rc1ExistingOwner"]["state"])


if __name__ == "__main__":
    unittest.main()
