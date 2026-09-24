from __future__ import annotations

import unittest
import json

from build_production_gallery import ALT_PROOF_IDS, MANIFEST, LOGICAL, load_specimens


class ProductionGallerySpecimenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.specimens, cls.objects = load_specimens(MANIFEST)

    def test_every_production_id_has_one_canonical_base_specimen(self):
        canonical = [row for row in self.specimens if row["role"] == "canonical_base"]
        self.assertEqual({row["id"] for row in canonical}, {row["id"] for row in self.objects})
        self.assertEqual(len(canonical), len(self.objects))
        self.assertTrue(all(row["properties"].get("visual") == "base" for row in canonical if "visual" in row["properties"]))

    def test_interactive_production_ids_have_closed_and_open_specimens(self):
        definitions=json.loads((LOGICAL/'definitions.json').read_text(encoding='utf8'))['blocks']
        interactive = {row['id'] for row in definitions if row.get('behavior') in ('door','gate','shutter')}
        self.assertTrue({'o_acacia_door','o_birch_door','o_dark_oak_door','o_shuttered_window'}<=interactive)
        for ident in interactive:
            rows = [row for row in self.specimens if row["id"] == ident]
            self.assertEqual({row["properties"].get("open") for row in rows}, {"false", "true"})
            self.assertIn(ident + ":open", {row["specimen_id"] for row in rows})

    def test_alt_proofs_are_extra_and_metadata_has_no_historical_ids(self):
        proof = [row for row in self.specimens if row["role"] == "alt_proof"]
        self.assertEqual({row["id"] for row in proof}, set(ALT_PROOF_IDS))
        self.assertTrue(all(row["properties"].get("visual") == "alt" for row in proof))
        manifest_ids = {row["id"] for row in self.objects}
        self.assertTrue(all(row["id"] in manifest_ids and row["id"].startswith("o_") for row in self.specimens))
        self.assertEqual(len({row["specimen_id"] for row in self.specimens}), len(self.specimens))


if __name__ == "__main__":
    unittest.main()
