import json
import tempfile
import unittest
from pathlib import Path

from source_review_decisions import DECISIONS, apply_history, reviewed_authoring


class DecisionsTest(unittest.TestCase):
    def test_all_review_rows_are_families_and_context_is_not_selected(self):
        rows = [{"review_id": key, "candidates": []} for key in DECISIONS]
        authoring = reviewed_authoring({"candidates": rows})
        self.assertEqual(20, len(authoring["families"]))
        c1979 = next(row for row in authoring["families"] if row["id"] == "o_c1979")
        self.assertNotIn(1, c1979["component_numbers"])
        self.assertNotIn(11, c1979["component_numbers"])

    def test_history_is_idempotent_and_preserves_signature(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps({"candidates": [{"review_id": "C001", "exact_source_signature": "keep", "history": []}]}), encoding="utf8")
            self.assertTrue(apply_history(path)); self.assertFalse(apply_history(path))
            row = json.loads(path.read_text(encoding="utf8"))["candidates"][0]
            self.assertEqual("keep", row["history"][0]["preserved_exact_source_signature"])


if __name__ == "__main__":
    unittest.main()
