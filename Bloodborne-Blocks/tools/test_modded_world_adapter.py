"""Synthetic regression checks for the MODDED offline conversion mode."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from convert_logical_world import DEFAULT_RESOURCES, PART, World, add, block_pos_long, convert
from check_logical_world import check, same_conflict_rows
from modded_world_adapter import compile_modded_rules
from test_logical_world import assembly_source, entity, tree_hash
from verify_modded_preservation import verify
from world_io import TAG_LONG, TAG_STRING, Tag


class ModdedWorldAdapterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.defaults, cls.diagnostics = compile_modded_rules(DEFAULT_RESOURCES)

    def test_frozen_mapping_composes_only_exact_modded_anchors_and_reports_gaps(self):
        self.assertGreater(len(self.rules), 32)
        self.assertGreater(self.diagnostics["compiledRawRules"], 0)
        self.assertTrue(self.diagnostics["mappingGaps"])
        for rule in self.rules:
            if rule.source_reference and "v2/migration.json" in rule.source_reference:
                self.assertTrue(rule.source.state[0].startswith("bloodborne_blocks:m_"), rule)
                self.assertEqual("modded", rule.source_mode)
        inventory = Path(__file__).resolve().parents[1] / "build/modded-input-inspection/inventory.json"
        if inventory.is_file():
            _rules, _defaults, diagnostics = compile_modded_rules(DEFAULT_RESOURCES, inventory)
            self.assertLess(diagnostics["maximumAnchorHits"], 10_000)
            self.assertLess(diagnostics["estimatedAnchorHits"], 100_000)

    def test_modded_archive_refuses_wrong_expected_hash(self):
        with tempfile.TemporaryDirectory(prefix="modded-hash-test-") as temporary:
            base = Path(temporary)
            source = base / "world.zip"
            source.write_bytes(b"not the approved archive")
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                convert(source, base / "output", resources=DEFAULT_RESOURCES,
                        report_path=base / "report.json", report_root=base,
                        source_mode="modded", expected_source_sha256="0" * 64)

    def test_checker_conflicts_are_order_independent_but_duplicate_exact(self):
        first = {"position": [1, 2, 3], "before": "bloodborne_blocks:m_a", "reason": "forced"}
        second = {"position": [4, 5, 6], "before": "bloodborne_blocks:m_b", "reason": "forced"}
        self.assertTrue(same_conflict_rows([second, first], [first, second]))
        self.assertFalse(same_conflict_rows([first, first, second], [first, second]))

    def test_legacy_carrier_full_assembly_is_atomic_and_foreign_safe(self):
        selected = None
        for rule in self.rules:
            if not (rule.source_reference or "").startswith("frozen definitions.json legacy carriers"):
                continue
            source_offsets = {piece.offset for piece in (rule.source,) + rule.members}
            write_offsets = {add(rule.root_offset, offset) for offset in rule.shape}
            if write_offsets - source_offsets:
                selected = rule
                break
        self.assertIsNotNone(selected)
        rule = selected
        source_offsets = {piece.offset for piece in (rule.source,) + rule.members}
        conflict_offset = sorted({add(rule.root_offset, offset) for offset in rule.shape} - source_offsets)[0]

        def put(blocks, origin):
            for piece in (rule.source,) + rule.members:
                blocks[add(origin, piece.offset)] = (piece.state[0], dict(piece.state[1]))

        safe_origin = (3, 64, 3)
        blocked_origin = (10, 64, 10)
        blocks = {}
        put(blocks, safe_origin)
        put(blocks, blocked_origin)
        chest = add(blocked_origin, conflict_offset)
        blocks[chest] = ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})
        unknown = (14, 64, 3)
        blocks[unknown] = ("bloodborne_blocks:retired_probe", {})

        with tempfile.TemporaryDirectory(prefix="legacy-carrier-test-") as temporary:
            base = Path(temporary)
            source = base / "source"
            assembly_source(source, blocks, [entity(chest, "minecraft:chest")])
            report_root = base / "reports"
            report_root.mkdir()
            output = base / "output"
            report = convert(source, output, resources=DEFAULT_RESOURCES,
                             report_path=report_root / "report.json", report_root=report_root,
                             source_mode="modded", conflict_policy="conservative")
            world = World(output, self.defaults)
            self.assertEqual(rule.target[0], world.get("minecraft:overworld", add(safe_origin, rule.root_offset))[0])
            self.assertEqual(rule.source.state, world.get("minecraft:overworld", add(blocked_origin, rule.source.offset)))
            self.assertEqual("minecraft:chest", world.get("minecraft:overworld", chest)[0])
            self.assertEqual("bloodborne_blocks:retired_probe", world.get("minecraft:overworld", unknown)[0])
            self.assertTrue(any(row["origin"] == list(blocked_origin) and row["reason"] == "target_would_overwrite_foreign_block"
                                for row in report["rejected"]), report["rejected"])
            self.assertEqual("FAIL", report["registryCompatibility"]["result"])
            self.assertGreater(report["counts"]["registryIncompatibleBlocks"], 1)
            self.assertGreater(report["counts"]["legacyCarrierRules"], 0)
            self.assertTrue(check(source, output, report_root / "report.json", DEFAULT_RESOURCES)["ok"])

    def test_owned_helper_dependency_reaches_fixed_point_in_one_atomic_output(self):
        dependent = self.rules[86]
        remover = self.rules[120]
        self.assertIn("legacy carriers", dependent.source_reference)
        self.assertIn("legacy carriers", remover.source_reference)
        dependent_origin = (8, 64, 8)
        remover_origin = (9, 64, 6)
        blocker = (8, 64, 7)
        blocks = {
            add(dependent_origin, dependent.source.offset): (dependent.source.state[0], dict(dependent.source.state[1])),
            add(remover_origin, remover.source.offset): (remover.source.state[0], dict(remover.source.state[1])),
            blocker: (PART, {}),
        }
        helper = entity(blocker, PART, Owner=Tag(TAG_STRING, remover.source.state[0]),
                        Root=Tag(TAG_LONG, block_pos_long(*remover_origin)))
        with tempfile.TemporaryDirectory(prefix="modded-fixed-point-test-") as temporary:
            base = Path(temporary)
            source = base / "source"
            assembly_source(source, blocks, [helper])
            reports = base / "reports"
            reports.mkdir()
            output = base / "output"
            report = convert(source, output, resources=DEFAULT_RESOURCES,
                             report_path=reports / "first.json", report_root=reports,
                             source_mode="modded", conflict_policy="conservative")
            self.assertEqual([1, 1, 0], [row["converted"] for row in report["passes"]], report["passes"])
            self.assertEqual([1, 2], [row["pass"] for row in report["ledger"]])
            self.assertTrue(check(source, output, reports / "first.json", DEFAULT_RESOURCES)["ok"])
            self.assertEqual("PASS", verify(source, output, report)["result"])
            final_world = World(output, self.defaults)
            self.assertEqual(dependent.target[0], final_world.get("minecraft:overworld", dependent_origin)[0])

            second = convert(output, base / "second", resources=DEFAULT_RESOURCES,
                             report_path=reports / "second.json", report_root=reports,
                             source_mode="modded", conflict_policy="conservative")
            self.assertEqual(0, second["counts"]["converted"], second)
            self.assertEqual([0], [row["converted"] for row in second["passes"]])

    def test_conservative_aggressive_embedded_preservation_and_idempotence(self):
        # A compact rule with a new owned master cell exercises both policies.
        selected = None
        for rule in self.rules:
            if not rule.source_reference or "v2/migration.json" not in rule.source_reference or rule.outputs:
                continue
            source_offsets = {piece.offset for piece in (rule.source,) + rule.members}
            write_offsets = {add(rule.root_offset, offset) for offset in rule.shape}
            points = source_offsets | write_offsets
            spans = [max(point[axis] for point in points) - min(point[axis] for point in points) for axis in range(3)]
            if write_offsets - source_offsets and max(spans) <= 3:
                selected = rule
                break
        self.assertIsNotNone(selected)
        rule = selected
        source_offsets = {piece.offset for piece in (rule.source,) + rule.members}
        write_offsets = {add(rule.root_offset, offset) for offset in rule.shape}
        conflict_offset = sorted(write_offsets - source_offsets)[0]

        def put_assembly(blocks, origin):
            for piece in (rule.source,) + rule.members:
                point = add(origin, piece.offset)
                blocks[point] = (piece.state[0], dict(piece.state[1]))

        safe_origin = (3, 64, 3)
        bloodborne_conflict_origin = (8, 64, 8)
        foreign_conflict_origin = (13, 64, 13)
        blocks = {}
        for origin in (safe_origin, bloodborne_conflict_origin, foreign_conflict_origin):
            put_assembly(blocks, origin)
        bloodborne_conflict = add(bloodborne_conflict_origin, conflict_offset)
        foreign_conflict = add(foreign_conflict_origin, conflict_offset)
        blocks[bloodborne_conflict] = ("bloodborne_blocks:m_unregistered_test", {"facing": "north"})
        blocks[foreign_conflict] = ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"})

        embedded_rule = next(rule for rule in self.rules
                             if rule.source_reference == "frozen beta embedded-window Contract V2"
                             and dict(rule.source.state[1]) == {"embedded": "true", "facing": "north",
                                                                "open": "false", "visual": "alt"})
        embedded_origin = (3, 75, 10)
        blocks[embedded_origin] = (embedded_rule.source.state[0], dict(embedded_rule.source.state[1]))
        current_cells = {add(embedded_origin, offset) for offset in embedded_rule.shape}
        stale_offset = next(offset for offset in embedded_rule.source.shape
                            if add(embedded_origin, offset) not in current_cells and offset != (0, 0, 0))
        stale_helper = add(embedded_origin, stale_offset)
        blocks[stale_helper] = (PART, {})
        entities = [
            entity(stale_helper, PART, Owner=Tag(TAG_STRING, "bloodborne_blocks:o_shuttered_window"),
                   Root=Tag(TAG_LONG, block_pos_long(*embedded_origin))),
            entity(foreign_conflict, "minecraft:chest"),
        ]

        with tempfile.TemporaryDirectory(prefix="modded-world-test-") as temporary:
            base = Path(temporary)
            source = base / "source"
            assembly_source(source, blocks, entities)
            before = tree_hash(source)
            report_root = base / "reports"
            report_root.mkdir()

            conservative_output = base / "conservative"
            conservative = convert(source, conservative_output, resources=DEFAULT_RESOURCES,
                                   report_path=report_root / "conservative.json", report_root=report_root,
                                   source_mode="modded", conflict_policy="conservative")
            self.assertEqual(before, tree_hash(source), "source world was modified")
            self.assertTrue((report_root / "conservative.md").is_file())
            self.assertGreaterEqual(conservative["counts"]["converted"], 2, conservative)
            self.assertEqual(0, conservative["counts"]["forced"])
            self.assertEqual("FAIL", conservative["registryCompatibility"]["result"])
            self.assertTrue(all({"delta", "decision", "reason", "tp", "originalReference"} <= set(row)
                                for row in conservative["ledger"]))
            conservative_world = World(conservative_output, self.defaults)
            self.assertEqual("bloodborne_blocks:m_unregistered_test",
                             conservative_world.get("minecraft:overworld", bloodborne_conflict)[0])
            self.assertEqual("minecraft:chest", conservative_world.get("minecraft:overworld", foreign_conflict)[0])
            embedded = conservative_world.get("minecraft:overworld", embedded_origin)
            self.assertEqual("bloodborne_blocks:o_shuttered_window", embedded[0])
            self.assertEqual("alt", dict(embedded[1])["visual"])
            self.assertNotIn("embedded", dict(embedded[1]))
            self.assertEqual("minecraft:air", conservative_world.get("minecraft:overworld", stale_helper)[0])
            self.assertTrue(check(source, conservative_output, report_root / "conservative.json", DEFAULT_RESOURCES)["ok"])

            aggressive_output = base / "aggressive"
            aggressive = convert(source, aggressive_output, resources=DEFAULT_RESOURCES,
                                 report_path=report_root / "aggressive.json", report_root=report_root,
                                 source_mode="modded", conflict_policy="aggressive")
            self.assertGreaterEqual(aggressive["counts"]["forced"], 1)
            aggressive_world = World(aggressive_output, self.defaults)
            self.assertEqual(rule.target[0], aggressive_world.get("minecraft:overworld", add(bloodborne_conflict_origin, rule.root_offset))[0])
            self.assertEqual("minecraft:chest", aggressive_world.get("minecraft:overworld", foreign_conflict)[0])
            self.assertTrue(check(source, aggressive_output, report_root / "aggressive.json", DEFAULT_RESOURCES)["ok"])

            second_output = base / "second"
            second = convert(aggressive_output, second_output, resources=DEFAULT_RESOURCES,
                             report_path=report_root / "second.json", report_root=report_root,
                             source_mode="modded", conflict_policy="aggressive")
            self.assertEqual(0, second["counts"]["converted"], second)


if __name__ == "__main__":
    unittest.main()
