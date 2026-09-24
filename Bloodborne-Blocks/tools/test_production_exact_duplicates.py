from __future__ import annotations

import copy
import hashlib
import unittest
from pathlib import Path
from unittest.mock import patch

import production_exact_duplicates as duplicates
from production_exact_duplicates import analyse, analyse_current, exact_recommendations, mesh_signature, rotated
from production_fingerprints import DEFAULT_BASELINE, read


def evidence(*, uv_shift: float = 0, texture_hash: str = "pixels"):
    return {"textures": [{"raw": "bloodborne_blocks:block/test", "content": {"sha256": texture_hash}}], "mesh": {"polygons": [{"texture": "bloodborne_blocks:block/test", "vertices": [
        [0, 0, 0, 0 + uv_shift, 0], [1, 0, 0, 1 + uv_shift, 0], [1, 1, 0, 1 + uv_shift, 1], [0, 1, 0, 0 + uv_shift, 1]
    ]}]}}


class ProductionExactDuplicateTests(unittest.TestCase):
    def test_changed_uv_or_texture_content_is_not_exact_duplicate(self):
        source = evidence()
        self.assertNotEqual(mesh_signature(source)["signature"], mesh_signature(evidence(uv_shift=.25))["signature"])
        self.assertNotEqual(mesh_signature(source)["signature"], mesh_signature(evidence(texture_hash="other-pixels"))["signature"])

    def test_cardinal_orientation_uses_one_exact_canonical_signature(self):
        source = evidence(); turned = copy.deepcopy(source)
        for vertex in turned["mesh"]["polygons"][0]["vertices"]:
            vertex[0], vertex[2] = rotated(vertex[0], vertex[2], 1)
        self.assertEqual(mesh_signature(source)["signature"], mesh_signature(turned)["signature"])

    def test_same_geometry_with_different_behavior_is_not_an_auto_recommendation(self):
        signature = mesh_signature(evidence())
        rows = [
            {"id": "o_left", "state": "visual=base", "visual_slot": "base", "evidence": signature, "behavior_schema": {"behavior": "bench"}},
            {"id": "o_right", "state": "visual=base", "visual_slot": "base", "evidence": signature, "behavior_schema": {"behavior": "lantern"}},
        ]
        self.assertEqual([], exact_recommendations(rows))

    def test_current_analysis_reads_collect_without_touching_immutable_baseline(self):
        before = hashlib.sha256(DEFAULT_BASELINE.read_bytes()).hexdigest()
        baseline = read(DEFAULT_BASELINE)
        with patch.object(duplicates, "collect", return_value=baseline["fingerprints"]) as current:
            report = analyse_current(Path("current-root"))
        self.assertEqual("current_production_palette", report["input"])
        self.assertIsNone(report["baseline_sha256"])
        current.assert_called_once_with(Path("current-root"))
        self.assertEqual(before, hashlib.sha256(DEFAULT_BASELINE.read_bytes()).hexdigest())

    def test_default_baseline_analysis_remains_immutable_baseline(self):
        baseline = read(DEFAULT_BASELINE)
        report = analyse(baseline)
        self.assertEqual("immutable_baseline", report["input"])
        self.assertEqual(baseline["sha256"], report["baseline_sha256"])


if __name__ == "__main__":
    unittest.main()
