"""Synthetic lazy-region checks for the original reference oracle."""
from __future__ import annotations

import hashlib
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

from compare_modded_reference import compare
from convert_logical_world import DEFAULT_RESOURCES, add
from logical_contract_v2 import direct_rules
from modded_world_adapter import compile_modded_rules
from test_logical_world import assembly_source


class CompareModdedReferenceTests(unittest.TestCase):
    def test_match_mismatch_missing_and_embedded_not_applicable(self):
        with tempfile.TemporaryDirectory(prefix="reference-compare-test-") as temporary:
            base = Path(temporary)
            inventory = base / "inventory.json"
            inventory.write_text(json.dumps({"blocks": {"stateCounts": {}}}), encoding="utf-8")
            compiled, _defaults, diagnostics = compile_modded_rules(DEFAULT_RESOURCES, inventory)
            raw_rules, _ = direct_rules(DEFAULT_RESOURCES)

            chosen = None
            for index, rule in enumerate(compiled):
                match = re.search(r"raw rule (\d+)$", rule.source_reference or "")
                if match is None:
                    continue
                raw = raw_rules[int(match.group(1))]
                offsets = [piece.offset for piece in (raw.source,) + raw.members]
                spans = [max(point[axis] for point in offsets) - min(point[axis] for point in offsets)
                         for axis in range(3)]
                if spans[0] <= 2 and spans[1] <= 4 and spans[2] <= 2:
                    chosen = index, rule, raw, offsets
                    break
            self.assertIsNotNone(chosen)
            compiled_index, compiled_rule, raw_rule, offsets = chosen
            minimum = [min(point[axis] for point in offsets) for axis in range(3)]
            match_origin = (2 - minimum[0], 64 - minimum[1], 2 - minimum[2])
            mismatch_origin = (10 - minimum[0], 64 - minimum[1], 10 - minimum[2])
            blocks = {}
            pieces = (raw_rule.source,) + raw_rule.members
            for piece in pieces:
                blocks[add(match_origin, piece.offset)] = (piece.state[0], dict(piece.state[1]))
            for piece in pieces[1:]:
                blocks[add(mismatch_origin, piece.offset)] = (piece.state[0], dict(piece.state[1]))
            # The omitted first component resolves to air in an existing
            # section, producing MISMATCH rather than missing-region evidence.
            world = base / "reference-world"
            assembly_source(world, blocks)
            archive = base / "reference.zip"
            with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as output:
                for path in world.rglob("*"):
                    if path.is_file():
                        output.write(path, Path("world") / path.relative_to(world))
            sha = hashlib.sha256(archive.read_bytes()).hexdigest()

            embedded_index = next(index for index, rule in enumerate(compiled)
                                  if rule.source_reference == "frozen beta embedded-window Contract V2")
            embedded_rule = compiled[embedded_index]
            def ledger(index, rule, origin):
                return {"rule": index, "dimension": "minecraft:overworld", "origin": list(origin),
                        "targetRoot": list(origin), "delta": [0, 0, 0],
                        "originalReference": rule.source_reference}
            report = {
                "source": {"path": "synthetic-modded"},
                "mappingDiagnostics": {"inventorySha256": diagnostics["inventorySha256"]},
                "ledger": [
                    ledger(compiled_index, compiled_rule, match_origin),
                    ledger(compiled_index, compiled_rule, mismatch_origin),
                    ledger(compiled_index, compiled_rule, (600, 64, 600)),
                    ledger(embedded_index, embedded_rule, (4, 64, 4)),
                ],
            }
            result = compare(report, archive, DEFAULT_RESOURCES, inventory, sha)
            self.assertEqual(["MATCH", "MISMATCH", "MISSING", "NOT_APPLICABLE"],
                             [row["status"] for row in result["entries"]])
            self.assertEqual(1, result["summary"]["regionsLoaded"])
            self.assertEqual(1, result["summary"]["missingRegions"])
            self.assertEqual(0, result["summary"]["coordinateCorrectionsApplied"])
            self.assertTrue(all(row["coordinateDecision"] == "UNCHANGED_REFERENCE_ONLY"
                                for row in result["entries"]))
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                compare(report, archive, DEFAULT_RESOURCES, inventory, "0" * 64)


if __name__ == "__main__":
    unittest.main()
