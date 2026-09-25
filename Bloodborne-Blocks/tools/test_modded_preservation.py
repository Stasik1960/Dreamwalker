"""Synthetic checks for the independent MODDED preservation verifier."""
from __future__ import annotations

import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from convert_logical_world import World, convert, parse_rules
from test_logical_world import assembly_source, resources
from verify_modded_preservation import verify


class ModdedPreservationTests(unittest.TestCase):
    def test_actual_ledger_states_metadata_and_outside_ledger_mutation(self):
        with tempfile.TemporaryDirectory(prefix="modded-preservation-test-") as temporary:
            base = Path(temporary)
            logical = base / "logical"
            resources(logical)
            source = base / "source"
            assembly_source(source, {
                (5, 64, 5): ("bloodborne_blocks:legacy", {"facing": "north"}),
                (6, 64, 5): ("bloodborne_blocks:member", {"facing": "north"}),
                (10, 64, 10): ("minecraft:stone", {}),
            })
            (source / "data").mkdir()
            (source / "data/unrelated.dat").write_bytes(b"must remain byte-identical")
            report_root = base / "reports"
            report_root.mkdir()
            output = base / "output"
            report = convert(source, output, resources=logical,
                             report_path=report_root / "conversion.json", report_root=report_root)

            passed = verify(source, output, report)
            self.assertEqual("PASS", passed["result"], passed)
            self.assertEqual(len({(row["dimension"], tuple(change["position"]))
                                  for row in report["ledger"] for change in row["changes"]}),
                             passed["ledger_cells"])
            self.assertGreaterEqual(passed["preserved_nonterrain_files"], 2)

            tampered_report = copy.deepcopy(report)
            tampered_report["ledger"][0]["changes"][0]["after"] = "minecraft:diamond_block"
            wrong_ledger = verify(source, output, tampered_report)
            self.assertEqual("FAIL", wrong_ledger["result"])
            self.assertTrue(any("does not match ledger" in error for error in wrong_ledger["errors"]))

            mutated = base / "mutated"
            shutil.copytree(output, mutated)
            _rules, defaults = parse_rules(logical)
            world = World(mutated, defaults)
            world.set("minecraft:overworld", (10, 64, 10), ("minecraft:diamond_block", ()))
            world.save()
            outside = verify(source, mutated, report)
            self.assertEqual("FAIL", outside["result"])
            self.assertTrue(any("cell changed outside ledger" in error for error in outside["errors"]), outside)

            duplicate = copy.deepcopy(report)
            duplicate["ledger"][0]["changes"].append(copy.deepcopy(duplicate["ledger"][0]["changes"][0]))
            duplicate_result = verify(source, output, duplicate)
            self.assertEqual("FAIL", duplicate_result["result"])
            self.assertTrue(any("duplicate ledger position" in error for error in duplicate_result["errors"]))


if __name__ == "__main__":
    unittest.main()
