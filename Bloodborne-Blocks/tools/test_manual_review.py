"""Manual authority and stable IDs; no Gradle, renderer, or world writes."""
import copy
import unittest

from manual_review import (apply_decisions, refresh_manifest, validate, read, MANIFEST,
                           reviewed_contract_skip_reason, existing_row_has_source_drift, ROOT)
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
        self.assertEqual([row['review_id'] for row in generated['families']], [row['review_id'] for row in edited['families']])
        for before, after in zip(edited['families'], generated['families']):
            for field in ('review_id', 'status', 'user_decision', 'decision_history', 'relationships', 'notes', 'canonical_anchor', 'components'):
                self.assertEqual(after[field], before[field])
        self.assertFalse(any(row['logical_id'] == 'o_c282' for row in generated['families']))
        again = refresh_manifest(generated)
        self.assertEqual(generated, again)

    def test_reviewed_v2_provenance_stays_in_catalog_b(self):
        contracts = read(LOGICAL / 'contracts-v2.json')['families']
        c282 = next(contract for contract in contracts if contract['id'] == 'o_c282')
        self.assertEqual(reviewed_contract_skip_reason(c282), 'covered_by_catalog_b_review_source_patterns')
        self.assertTrue(c282['review_source_patterns'])

    def test_source_drift_covers_rules_members_contract_patterns_and_railing_expansion(self):
        definitions = {row['id']: row for row in read(LOGICAL / 'definitions.json')['blocks']}
        rules = read(LOGICAL / 'migration.json')['rules']
        curated = {row['id']: row for row in read(ROOT / 'docs/logical-families-v3.json')['objects']}
        contracts = {row['id']: row for row in read(LOGICAL / 'contracts-v2.json')['families']}
        geometry = read(LOGICAL / 'geometry.json')
        names = read(LOGICAL.parents[1] / 'assets/bloodborne_blocks/lang/ru_ru.json')
        planter = next(row for row in self.manifest['families'] if row['logical_id'] == 'o_dead_tree_planter')
        planter_rules = [row for row in rules if row['target']['id'] == planter['logical_id']]
        self.assertFalse(existing_row_has_source_drift(copy.deepcopy(planter), definitions[planter['logical_id']], planter_rules,
                                                        curated, contracts, geometry, names))
        changed_rules = copy.deepcopy(planter_rules)
        changed_rules[0]['members'][0]['properties']['facing'] = 'south'
        self.assertTrue(existing_row_has_source_drift(copy.deepcopy(planter), definitions[planter['logical_id']], changed_rules,
                                                       curated, contracts, geometry, names))
        changed_contracts = copy.deepcopy(contracts)
        state = next(state for state in changed_contracts[planter['logical_id']]['states'].values()
                     if state['migration_source_pattern'])
        state['migration_source_pattern'][0]['exact_source_signature'] = 'changed-by-test'
        self.assertTrue(existing_row_has_source_drift(copy.deepcopy(planter), definitions[planter['logical_id']], planter_rules,
                                                       curated, changed_contracts, geometry, names))
        railing = next(row for row in self.manifest['families'] if row['logical_id'] == 'o_iron_railing')
        railing_rules = [row for row in rules if row['target']['id'] == railing['logical_id']]
        unprimed_rules = copy.deepcopy(railing_rules)
        unprimed_rules[0]['source']['properties']['facing'] = 'east'
        with self.assertRaises(ValueError):
            existing_row_has_source_drift(copy.deepcopy(railing), definitions[railing['logical_id']], unprimed_rules,
                                           curated, contracts, geometry, names)
        unprimed_contracts = copy.deepcopy(contracts)
        north_pattern = next(state for key, state in unprimed_contracts[railing['logical_id']]['states'].items()
                             if 'facing=north' in key and state['migration_source_pattern'])
        north_pattern['migration_source_pattern'][0]['exact_source_signature'] = 'changed-before-baseline'
        with self.assertRaises(ValueError):
            existing_row_has_source_drift(copy.deepcopy(railing), definitions[railing['logical_id']], railing_rules,
                                           curated, unprimed_contracts, geometry, names)
        oriented = copy.deepcopy(railing)
        self.assertFalse(existing_row_has_source_drift(oriented, definitions[railing['logical_id']], railing_rules,
                                                        curated, contracts, geometry, names))
        self.assertEqual(oriented['source_projection_version'], 'oriented-contract-provenance-v1')
        self.assertIn('source_projection_baseline_fingerprint', oriented)
        changed_railing_rules = copy.deepcopy(railing_rules)
        changed_railing_rules[0]['source']['properties']['facing'] = 'east'
        self.assertTrue(existing_row_has_source_drift(oriented, definitions[railing['logical_id']], changed_railing_rules,
                                                       curated, contracts, geometry, names))

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
