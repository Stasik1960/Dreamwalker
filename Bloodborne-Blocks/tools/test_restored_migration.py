"""Synthetic Contract V2 conversion checks for restored production families.

These fixtures deliberately use only direct rules and tiny temporary Anvil
worlds; they never open, scan, or modify the historical source world.
"""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from convert_logical_world import World, convert
from logical_contract_v2 import direct_rules
from reviewed_migration_fixture import write_fixture

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/bloodborne_blocks/logical"
DIM = "minecraft:overworld"
DOORS = ("o_acacia_door", "o_birch_door", "o_dark_oak_door")


def pieces(rule, root):
    return {tuple(root[axis] + piece.offset[axis] for axis in range(3)): (piece.state[0], dict(piece.state[1]))
            for piece in (rule.source,) + rule.members}


class RestoredMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.defaults = direct_rules(RES)

    def matching(self, ident, **properties):
        return next(rule for rule in self.rules if rule.target[0] == "bloodborne_blocks:" + ident and
                    all(dict(rule.target[1]).get(key) == value for key, value in properties.items()))

    def convert_rules(self, selected):
        with tempfile.TemporaryDirectory(prefix="restored-v2-") as folder:
            base = Path(folder); source, output, reports = base / "source", base / "output", base / "reports"
            cells = {(96, 64, 11): ("minecraft:stone", {})}; roots = []
            for index, rule in enumerate(selected):
                # Keep every source/target helper in a materialized chunk.  A
                # root at chunk edge is not a converter behavior test.
                root = (8 + index * 32, 64, 8); roots.append(root); cells.update(pieces(rule, root))
                for offset in rule.shape:
                    cells.setdefault(tuple(root[axis] + offset[axis] for axis in range(3)), ("minecraft:air", {}))
            write_fixture(source, cells, floor=False)
            report = convert(source, output, resources=RES, report_path=reports / "conversion.json", report_root=reports, source_mode="original-v2")
            world = World(output, self.defaults)
            states = {point: world.get(DIM, point) for point in [*roots, (96, 64, 11)]}
            return report, states, roots

    def test_three_restored_door_assemblies_convert_once_and_preserve_foreign_cells(self):
        selected = [self.matching(ident, open="false") for ident in DOORS]
        self.assertTrue(all(len(rule.members) == 1 for rule in selected), "door must retain its two-carrier source assembly")
        report, states, roots = self.convert_rules(selected)
        self.assertEqual(report["counts"]["converted"], 3, report["rejected"])
        for ident, root in zip(DOORS, roots): self.assertEqual(states[root][0], "bloodborne_blocks:" + ident)
        self.assertEqual(states[(96, 64, 11)][0], "minecraft:stone")

    def test_shutter_open_and_closed_carriers_remain_distinct(self):
        selected = [self.matching("o_shuttered_window", open=value, embedded="true") for value in ("false", "true")]
        report, states, roots = self.convert_rules(selected)
        self.assertEqual(report["counts"]["converted"], 2, report["rejected"])
        self.assertEqual([dict(states[root][1])["open"] for root in roots], ["false", "true"])

    def test_c003_successors_convert_as_independent_single_cells(self):
        selected = [self.matching(ident) for ident in ("o_barrel", "o_books", "o_bag", "o_cases_0")]
        self.assertTrue(all(not rule.members for rule in selected), "C003 successors must not be one composite assembly")
        report, states, roots = self.convert_rules(selected)
        self.assertEqual(report["counts"]["converted"], 4, report["rejected"])
        written = {change["after"].split("[", 1)[0] for entry in report["ledger"] for change in entry["changes"] if change["after"]}
        self.assertNotIn("bloodborne_blocks:o_c003", written)
        self.assertEqual({states[root][0] for root in roots}, {"bloodborne_blocks:o_barrel", "bloodborne_blocks:o_books", "bloodborne_blocks:o_bag", "bloodborne_blocks:o_cases_0"})

    def test_three_barrel_occurrences_do_not_require_the_other_c003_components(self):
        rule = self.matching("o_barrel")
        report, states, roots = self.convert_rules([rule, rule, rule])
        self.assertEqual(report["counts"]["converted"], 3, report["rejected"])
        self.assertTrue(all(states[root][0] == "bloodborne_blocks:o_barrel" for root in roots))

    def test_empty_legacy_rules_fail_before_copying_a_v2_world(self):
        with tempfile.TemporaryDirectory(prefix="empty-legacy-") as folder:
            base = Path(folder); source, output, reports = base / "source", base / "output", base / "reports"
            write_fixture(source, {(8, 64, 8): ("minecraft:stone", {})}, floor=False)
            with self.assertRaisesRegex(ValueError, "use --source-mode original-v2"):
                convert(source, output, resources=RES, report_path=reports / "conversion.json", report_root=reports)
            self.assertFalse(output.exists(), "legacy refusal must happen before an output copy is created")


if __name__ == "__main__":
    unittest.main()
