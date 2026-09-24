from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path
from production_coverage import DEFAULT_MANIFEST, DEFAULT_REQUIRED, DEFAULT_RESOURCES, required_ids, validate


def read(path: Path):
    return json.loads(path.read_text(encoding="utf8"))


def write(path: Path, value) -> None:
    path.write_text(json.dumps(value), encoding="utf8")


class ProductionCoverageTests(unittest.TestCase):
    def copied_inputs(self, folder: str) -> tuple[Path, Path]:
        base = Path(folder)
        resources = base / "logical"; shutil.copytree(DEFAULT_RESOURCES, resources)
        manifest = base / "manifest.json"; shutil.copy2(DEFAULT_MANIFEST, manifest)
        return resources, manifest

    def test_handwritten_union_is_fixed(self):
        self.assertEqual(len(required_ids(read(DEFAULT_REQUIRED))), 56)
        for old, new in [('o_barrel_0','o_barrel'),('o_books_0','o_books'),('o_bag_0','o_bag')]:
            self.assertEqual(read(DEFAULT_REQUIRED)['successors'][old], {'status':'MERGED_WITH_SUCCESSOR','ids':[new]})

    def test_live_complete_palette_passes(self):
        report = validate(required_path=DEFAULT_REQUIRED, resources=DEFAULT_RESOURCES, manifest_path=DEFAULT_MANIFEST)
        self.assertEqual(report["result"], "PASS")
        self.assertEqual(report["required_count"], 56)

    def test_required_spec_catches_id_removed_from_every_production_input(self):
        with tempfile.TemporaryDirectory() as folder:
            resources, manifest_path = self.copied_inputs(folder)
            ident = "o_bench"
            for name, key in (("contracts-v2.json", "families"), ("definitions.json", "blocks")):
                path = resources / name; data = read(path)
                data[key] = [row for row in data[key] if row["id"] != ident]; write(path, data)
            manifest = read(manifest_path)
            manifest["objects"] = [row for row in manifest["objects"] if row["id"] != ident]
            write(manifest_path, manifest)
            with self.assertRaisesRegex(ValueError, "missing Contract V2 family: o_bench"):
                validate(required_path=DEFAULT_REQUIRED, resources=resources, manifest_path=manifest_path)

    def test_missing_gallery_is_rejected_after_production_inputs_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            gallery = Path(folder) / "gallery"; gallery.mkdir()
            write(gallery / "production-gallery.json", {"positions": []})
            with self.assertRaisesRegex(ValueError, "gallery missing required IDs"):
                validate(required_path=DEFAULT_REQUIRED, resources=DEFAULT_RESOURCES, manifest_path=DEFAULT_MANIFEST, gallery=gallery)

    def test_unused_carrier_needs_its_own_explicit_reason(self):
        required = read(DEFAULT_REQUIRED)
        row = next(r for r in required["restorations"] if r["id"] == "o_lantern")
        row.pop("unused_carriers")
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "required.json"; write(path, required)
            with self.assertRaisesRegex(ValueError, "cached census lacks restoration carrier: o_lantern minecraft:soul_lantern"):
                validate(required_path=path, resources=DEFAULT_RESOURCES, manifest_path=DEFAULT_MANIFEST)

    def test_broken_successor_is_rejected(self):
        required = read(DEFAULT_REQUIRED)
        required["successors"]["o_c003"]["ids"] = ["o_not_a_successor"]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "required.json"; write(path, required)
            with self.assertRaisesRegex(ValueError, "incomplete successor: o_c003"):
                validate(required_path=path, resources=DEFAULT_RESOURCES, manifest_path=DEFAULT_MANIFEST)

    def test_restoration_without_raw_source_pattern_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            resources, manifest_path = self.copied_inputs(folder)
            path = resources / "contracts-v2.json"; contracts = read(path)
            family = next(row for row in contracts["families"] if row["id"] == "o_bench")
            for state in family["states"].values(): state["migration_source_pattern"] = []
            write(path, contracts)
            with self.assertRaisesRegex(ValueError, "restoration lacks raw source patterns: o_bench"):
                validate(required_path=DEFAULT_REQUIRED, resources=resources, manifest_path=manifest_path)

    def test_historical_exclusion_without_reason_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            resources, manifest_path = self.copied_inputs(folder)
            manifest = read(manifest_path); manifest["excluded"][0]["reason"] = ""; write(manifest_path, manifest)
            with self.assertRaisesRegex(ValueError, "historical family silently excluded or unapproved"):
                validate(required_path=DEFAULT_REQUIRED, resources=resources, manifest_path=manifest_path)

    def test_mismatched_handwritten_carrier_is_reported_from_frozen_evidence(self):
        required = read(DEFAULT_REQUIRED)
        required["restorations"][0]["source_carriers"] = ["minecraft:not_a_carrier"]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "required.json"; write(path, required)
            with self.assertRaisesRegex(ValueError, "frozen mapping lacks restoration carrier"):
                validate(required_path=path, resources=DEFAULT_RESOURCES, manifest_path=DEFAULT_MANIFEST)


if __name__ == "__main__": unittest.main()
