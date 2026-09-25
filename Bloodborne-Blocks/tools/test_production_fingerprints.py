from __future__ import annotations

import copy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import production_fingerprints as fingerprints
from production_fingerprints import ROOT, capture, read, verify, write


class ProductionFingerprintTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="production-fingerprints-")
        cls.base = Path(cls.temp.name) / "baseline.json"
        capture(root=ROOT, output=cls.base)
        cls.snapshot = read(cls.base)["fingerprints"]

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def verify_mutated(self, ident: str, mutate, allow: dict | None = None):
        current = dict(self.snapshot)
        current["families"] = dict(self.snapshot["families"])
        current["families"][ident] = copy.deepcopy(self.snapshot["families"][ident])
        mutate({"fingerprints": current})
        allow_path = None
        if allow is not None:
            allow_path = Path(self.temp.name) / f"allow-{len(list(Path(self.temp.name).glob('allow-*.json')))}.json"
            write(allow_path, allow)
        with patch.object(fingerprints, "collect", return_value=current):
            return verify(root=ROOT, baseline_path=self.base, allowlist_path=allow_path)

    def test_capture_refuses_to_replace_immutable_baseline(self):
        with self.assertRaises(FileExistsError):
            capture(root=ROOT, output=self.base)

    def test_checkout_line_endings_do_not_change_text_fingerprints(self):
        path = Path(self.temp.name) / "checkout.json"
        path.write_bytes(b'{"value": 1}\n')
        expected = fingerprints.file_hash(path)
        path.write_bytes(b'{"value": 1}\r\n')
        self.assertEqual(expected, fingerprints.file_hash(path))
        path.write_bytes(b'{"value": 2}\r\n')
        self.assertNotEqual(expected, fingerprints.file_hash(path))
        binary = path.with_suffix('.png')
        binary.write_bytes(b'\r\n')
        self.assertEqual(fingerprints.digest_bytes(b'\r\n'), fingerprints.file_hash(binary))

    def test_geometry_uv_helpers_and_patterns_require_explicit_family_allowlist(self):
        def geometry(data):
            state = next(iter(data["fingerprints"]["families"]["o_c002"]["core"]["state_evidence"].values()))
            state["mesh"]["polygons"][0]["vertices"][0][0] += 0.25

        def uv(data):
            state = next(iter(data["fingerprints"]["families"]["o_c002"]["core"]["state_evidence"].values()))
            state["mesh"]["polygons"][0]["vertices"][0][3] += 0.25

        def helpers(data):
            state = next(iter(data["fingerprints"]["families"]["o_c002"]["core"]["contract"]["states"].values()))
            state["interaction_footprint"]["cells"].append([99, 0, 0])

        def patterns(data):
            state = next(value for value in data["fingerprints"]["families"]["o_acacia_door"]["core"]["contract"]["states"].values()
                         if value["migration_source_pattern"])
            state["migration_source_pattern"][0]["components"][0]["offset"] = [9, 9, 9]

        for ident, mutate in (("o_c002", geometry), ("o_c002", uv), ("o_c002", helpers), ("o_acacia_door", patterns)):
            with self.subTest(change=mutate.__name__):
                with self.assertRaisesRegex(ValueError, "unexpected fingerprint changes"):
                    self.verify_mutated(ident, mutate)
                self.assertEqual("PASS", self.verify_mutated(ident, mutate, {"families": [ident]})["result"])

    def test_display_name_allowlist_does_not_exempt_geometry(self):
        def labels_only(data):
            data["fingerprints"]["families"]["o_c002"]["display_names"]["en_us"] = "changed label"

        allow = {"display_names": ["o_c002"]}
        self.assertEqual("PASS", self.verify_mutated("o_c002", labels_only, allow)["result"])

        def labels_and_geometry(data):
            labels_only(data)
            state = next(iter(data["fingerprints"]["families"]["o_c002"]["core"]["state_evidence"].values()))
            state["mesh"]["polygons"][0]["vertices"][0][0] += 0.25

        with self.assertRaisesRegex(ValueError, r"families=\['o_c002'\]"):
            self.verify_mutated("o_c002", labels_and_geometry, allow)

    def test_schema_change_fails_even_with_an_allowlist(self):
        baseline = Path(self.temp.name) / "schema.json"
        data = read(self.base); data["schema_version"] = 99; write(baseline, data)
        allow = Path(self.temp.name) / "schema.allow.json"; write(allow, {"families": ["o_c002"]})
        with self.assertRaisesRegex(ValueError, "unsupported fingerprint baseline schema"):
            verify(root=ROOT, baseline_path=baseline, allowlist_path=allow)

    def test_retirements_need_an_explicit_allowlist_and_additions_still_fail(self):
        # Keep all surviving records identical so this isolates removal policy
        # from the separately protected production-data changes.
        baseline = Path(self.temp.name) / "retirement-baseline.json"
        data = read(self.base)
        expected = set(read(fingerprints.DEFAULT_BASELINE)["fingerprints"]["family_ids"])
        actual = fingerprints.collect(ROOT)
        removed = sorted(expected - set(actual["family_ids"]))
        self.assertTrue(removed)
        data["fingerprints"]["family_ids"] = sorted(set(data["fingerprints"]["family_ids"]) | set(removed))
        data["fingerprints"]["families"].update({ident: {"core": {}, "display_names": {}} for ident in removed})
        payload = {key: value for key, value in data.items() if key != "sha256"}
        data["sha256"] = fingerprints.digest_bytes(fingerprints.canonical(payload)); write(baseline, data)
        with self.assertRaisesRegex(ValueError, "family IDs changed"):
            verify(root=ROOT, baseline_path=baseline)
        allow = Path(self.temp.name) / "retired.allow.json"; write(allow, {"families": removed})
        report = verify(root=ROOT, baseline_path=baseline, allowlist_path=allow)
        self.assertEqual(removed, report["approved_retired_removals"])
        with self.assertRaisesRegex(ValueError, "family IDs changed"):
            partial = Path(self.temp.name) / "partial-retired.allow.json"; write(partial, {"families": removed[:-1]})
            verify(root=ROOT, baseline_path=baseline, allowlist_path=partial)
        changed = dict(actual); changed["family_ids"] = [*actual["family_ids"], "o_unapproved_new"]
        with patch.object(fingerprints, "collect", return_value=changed):
            with self.assertRaisesRegex(ValueError, r"added=\['o_unapproved_new'\]"):
                verify(root=ROOT, baseline_path=baseline, allowlist_path=allow)

    def test_runtime_java_hashes_are_reported_without_exempting_family_changes(self):
        current = dict(self.snapshot); current["runtime_java_sha256"] = dict(self.snapshot["runtime_java_sha256"])
        current["runtime_java_sha256"]["src/main/java/example/Changed.java"] = "changed"
        with patch.object(fingerprints, "collect", return_value=current):
            report = verify(root=ROOT, baseline_path=self.base)
        self.assertIn("src/main/java/example/Changed.java", report["runtime_java_metadata"]["changed"])


if __name__ == "__main__":
    unittest.main()
