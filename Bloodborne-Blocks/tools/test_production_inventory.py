import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from audit_production_palette import STATUSES, status_for, write_inventory


class ProductionInventoryTests(unittest.TestCase):
    def test_statuses_are_bounded_and_reviewed_authority_keeps(self):
        self.assertEqual(status_for("reviewed", set(), {"reviewed": {"authority": "user"}}, {}), "KEEP")
        self.assertEqual(status_for("old", {"old"}, {}, {"old": ["new"]}), "SUPERSEDED")
        self.assertEqual(status_for("old", {"old"}, {}, {}), "COMPATIBILITY_ONLY")
        self.assertIn(status_for("unknown", set(), {}, {}), STATUSES)

    def test_baseline_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            output, baseline = Path(temp) / "audit.json", Path(temp) / "baseline.json"
            baseline.write_text("{}\n", encoding="utf8")
            with patch("audit_production_palette.build_inventory", return_value={"fixture": True}):
                write_inventory(output, baseline)
            self.assertEqual(baseline.read_text(encoding="utf8"), "{}\n")


if __name__ == "__main__":
    unittest.main()
