"""Manual authority and stable IDs; no Gradle, renderer, or world writes."""
import copy
import unittest

from manual_review import apply_decisions, refresh_manifest, validate, read, MANIFEST
from manual_review import LOGICAL


class ManualReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = read(MANIFEST)

    def test_inventory_and_poc(self):
        validate(self.manifest)
        self.assertEqual(len(self.manifest['families']), 256)
        poc = [r for r in self.manifest['families'] if r['architecture_status'] == 'POC_ACCEPTED']
        self.assertEqual(len(poc), 5)
        self.assertTrue(all(r['status'] == 'APPROVED' for r in poc))
        self.assertEqual(sum(r['batch'] == 1 for r in self.manifest['families']), 25)

    def test_regeneration_preserves_ids_numbers_and_authority(self):
        edited = apply_decisions(self.manifest, 'F001 NEEDS_REVIEW: граница куста\nF003 OK')
        row = next(r for r in edited['families'] if r['review_id'] == 'F001')
        row['notes'] = ['user note']; row['canonical_anchor'] = {'cell': [2, 3, 4]}
        generated = refresh_manifest(edited)
        self.assertEqual(edited, generated)
        again = refresh_manifest(generated)
        self.assertEqual(generated, again)

    def test_refuses_silent_overwrite_atomic(self):
        before = copy.deepcopy(self.manifest)
        with self.assertRaises(ValueError):
            apply_decisions(self.manifest, 'F001 OK\nF002 DELETE')  # POC accepted
        self.assertEqual(self.manifest, before)
        approved = apply_decisions(self.manifest, 'F001 OK')
        with self.assertRaises(ValueError): apply_decisions(approved, 'F001 DELETE')
        replaced = apply_decisions(approved, 'F001 DELETE', replace=True)
        first = replaced['families'][0]
        self.assertEqual(first['decision_history'][-1]['action'], 'OK')
        self.assertEqual(first['status'], 'DELETE')

    def test_split_partition_and_merge_links(self):
        # Tree has 3 numbered source applications; test ONLY a copied manifest.
        split = apply_decisions(self.manifest, 'F002 SPLIT: 1+2 / 3', replace=True)
        tree = next(r for r in split['families'] if r['review_id'] == 'F002')
        self.assertEqual(tree['user_decision']['component_groups'], [[1, 2], [3]])
        for line in ('F002 SPLIT: 1 / 1+2+3', 'F002 SPLIT: 1 / 2', 'F002 SPLIT: 1+2+3'):
            with self.assertRaises(ValueError): apply_decisions(self.manifest, line, replace=True)
        merged = apply_decisions(self.manifest, 'F001 MERGE: F003+F004')
        for row in merged['families']:
            if row['review_id'] in ('F001', 'F003', 'F004'):
                self.assertEqual(row['status'], 'MERGE')
                self.assertEqual(len(row['relationships']), 2)
        with self.assertRaises(ValueError): apply_decisions(self.manifest, 'F001 MERGE: F999')

    def test_duplicate_decision_is_idempotent(self):
        once = apply_decisions(self.manifest, 'F001 CONNECTED')
        self.assertEqual(once, apply_decisions(once, 'F001 CONNECTED'))

    def test_state_variants_within_one_family(self):
        once = apply_decisions(self.manifest, 'F001 STATE_VARIANTS')
        row = next(row for row in once['families'] if row['review_id'] == 'F001')
        self.assertEqual(row['status'], 'STATE_VARIANTS')
        self.assertEqual(row['relationships'], [])
        self.assertEqual(once, apply_decisions(once, 'F001 STATE_VARIANTS'))

    def test_relationship_replacement_rejects_unilateral_and_self_merge(self):
        merged = apply_decisions(self.manifest, 'F001 MERGE: F003')
        with self.assertRaises(ValueError): apply_decisions(merged, 'F001 OK', replace=True)
        with self.assertRaises(ValueError): apply_decisions(self.manifest, 'F001 MERGE: F001')
        changed = apply_decisions(merged, 'F001 STATE_VARIANTS: F003', replace=True)
        for row in changed['families']:
            if row['review_id'] in ('F001', 'F003'):
                self.assertEqual(row['relationships'][0]['type'], 'STATE_VARIANTS')

    def test_replace_can_dissolve_a_complete_existing_group(self):
        merged = apply_decisions(self.manifest, 'F001 MERGE: F003')
        dissolved = apply_decisions(merged, 'F001 OK\nF003 OK', replace=True)
        for ident in ('F001', 'F003'):
            row = next(row for row in dissolved['families'] if row['review_id'] == ident)
            self.assertEqual(row['status'], 'APPROVED')
            self.assertEqual(row['relationships'], [])
            self.assertEqual(row['decision_history'][-1]['action'], 'MERGE')

    def test_replace_can_regroup_only_when_old_group_is_fully_covered(self):
        merged = apply_decisions(self.manifest, 'F001 MERGE: F003')
        with self.assertRaises(ValueError):
            apply_decisions(merged, 'F001 MERGE: F004', replace=True)
        regrouped = apply_decisions(merged, 'F001 MERGE: F004\nF003 OK', replace=True)
        first = next(row for row in regrouped['families'] if row['review_id'] == 'F001')
        fourth = next(row for row in regrouped['families'] if row['review_id'] == 'F004')
        third = next(row for row in regrouped['families'] if row['review_id'] == 'F003')
        self.assertEqual(first['user_decision']['related_families'], ['F001', 'F004'])
        self.assertEqual(fourth['user_decision']['related_families'], ['F001', 'F004'])
        self.assertEqual(third['relationships'], [])

    def test_contradictory_group_transaction_is_atomic(self):
        before = copy.deepcopy(self.manifest)
        with self.assertRaises(ValueError):
            apply_decisions(self.manifest, 'F001 MERGE: F003\nF003 MERGE: F004', replace=True)
        self.assertEqual(self.manifest, before)

    def test_source_drift_does_not_erase_authority(self):
        definitions = read(LOGICAL / 'definitions.json')['blocks']
        edited = apply_decisions(self.manifest, 'F001 OK')
        changed = copy.deepcopy(definitions)
        first_id = edited['families'][0]['logical_id']
        next(d for d in changed if d['id'] == first_id)['semantic'] = 'ornament'
        updated = refresh_manifest(edited, definitions=changed)
        first = next(r for r in updated['families'] if r['review_id'] == 'F001')
        self.assertTrue(first['source_drift']); self.assertEqual(first['status'], 'APPROVED')
        removed = refresh_manifest(edited, definitions=[d for d in definitions if d['id'] != first_id])
        first = next(r for r in removed['families'] if r['review_id'] == 'F001')
        self.assertTrue(first['source_drift']); self.assertIsNone(first['current_source_fingerprint'])


if __name__ == '__main__': unittest.main()
