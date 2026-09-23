"""Review-only contract tests; no writes to source worlds or runtime assets."""
import copy
import gzip
import json
import unittest
from pathlib import Path

from source_assembly_pipeline import active_choices, exact_signature, choose_batch, assert_merge_authority
from source_review_decisions import DECISIONS
from verify_source_assembly_catalog import assert_snapshot_registry, assert_selected_package_decisions


class PipelineTests(unittest.TestCase):
    def test_exact_signature_preserves_layout_and_state(self):
        cells = {(0,0,0):('minecraft:oak_log',(('axis','x'),)),(1,3,0):('minecraft:birch_log',(('axis','y'),))}
        shifted = {(x+99,y+20,z-77):s for (x,y,z),s in cells.items()}
        self.assertEqual(exact_signature(cells),exact_signature(shifted))
        changed = dict(cells); changed[(1,3,0)] = ('minecraft:birch_log',(('axis','z'),))
        self.assertNotEqual(exact_signature(cells),exact_signature(changed))
        changed = dict(cells); changed[(2,3,0)] = changed.pop((1,3,0))
        self.assertNotEqual(exact_signature(cells),exact_signature(changed))

    def test_all_weighted_and_multipart_choices_survive(self):
        raw = {'variants':{'facing=north':[{'model':'a','weight':2},{'model':'b','weight':7}]},
               'multipart':[{'when':{'OR':[{'open':'true'},{'powered':'true'}]},'apply':[{'model':'c'},{'model':'d'}]}]}
        before = copy.deepcopy(raw); groups = active_choices(raw,{'facing':'north','open':'true'})
        self.assertEqual([[option[0]['model'] for option in group] for group in groups],[['a','b'],['c','d']])
        self.assertEqual(groups[0][1][0]['weight'],7); self.assertEqual(raw,before)

    def test_human_decision_cannot_be_hidden_by_alias(self):
        rows = {'C001':{'status':'UNREVIEWED'},'C005':{'status':'UNREVIEWED'}}
        assert_merge_authority(list(rows),rows)
        rows['C005']['user_decision'] = {'action':'SPLIT','components':[1,2]}
        with self.assertRaisesRegex(ValueError,'authoritative'): assert_merge_authority(list(rows),rows)
        self.assertEqual(rows['C005']['user_decision']['action'],'SPLIT')
        decided = {'C001':{'status':'APPROVED','user_decision':{'action':'OBJECT'}},
                   'C005':{'status':'VARIANT_OF','variant_of':'C001','user_decision':None}}
        before = copy.deepcopy(decided)
        assert_merge_authority(list(decided),decided)
        assert_merge_authority(list(decided),decided)
        self.assertEqual(decided,before)

    def test_batch_ignores_alias_reviewed_and_backfills(self):
        def row(i):
            return {'review_id':f'C{i:03}','status':'UNREVIEWED','source_patterns':[{'boundary_methods':['spatial_cluster']}],
                    'components':[{'source':{'id':'minecraft:oak_log'},'apps':[{'model':'minecraft:block/oak_log'}],'model_choices':[[[{'model':'minecraft:block/oak_log'}]]]}],
                    'category':'tree','similar_count':1}
        rows = [row(i) for i in range(1,21)]
        rows[0]['status']='VARIANT_OF'; rows[1]['user_decision']={'action':'OBJECT'}
        picked = choose_batch(rows,18)
        self.assertEqual(len(picked),18); self.assertNotIn('C001',picked); self.assertNotIn('C002',picked)
        self.assertEqual(picked,choose_batch(list(reversed(rows)),18))

    def test_snapshot_overlay_allows_later_nonselected_batch(self):
        def row(review_id, signature):
            return {'review_id': review_id, 'exact_source_signature': signature, 'immutable': review_id,
                    'status': 'UNREVIEWED', 'user_decision': None, 'history': []}
        snapshot = {'candidates': [row('C001', 'sig-1'), row('C099', 'sig-99')]}
        current = copy.deepcopy(snapshot)
        first, later = current['candidates']
        first.update(status='REVIEWED', user_decision={'kind': 'OBJECT'})
        first['history'].append({'event': 'manual_review', 'batch': 'batch-02', 'decision': first['user_decision'],
                                 'preserved_exact_source_signature': 'sig-1'})
        later.update(status='REVIEWED', user_decision={'kind': 'SPLIT'})
        later['history'].append({'event': 'manual_review', 'batch': 'batch-03', 'decision': later['user_decision'],
                                 'preserved_exact_source_signature': 'sig-99'})
        manifest = {'candidates': current['candidates']}
        assert_snapshot_registry(snapshot, manifest)
        assert_selected_package_decisions(snapshot, manifest, ['C001'], {'C001': {'kind': 'OBJECT'}}, 'batch-02')

    def test_published_inventory_partitions_all_exact_patterns(self):
        root = Path(__file__).resolve().parents[1]
        manifest = json.loads((root/'docs/manual-source-assemblies.json').read_text(encoding='utf8'))
        with gzip.open(root/'docs/source-assembly-exact-patterns.json.gz','rt',encoding='utf8') as f: exact = json.load(f)
        if not manifest.get('format','').startswith('source-assembly-pipeline-v2'): self.skipTest('v2 not generated yet')
        signatures = [p['exact_source_signature'] for r in manifest['candidates'] if r.get('status')!='VARIANT_OF' for p in r.get('source_patterns',[])]
        self.assertEqual(len(signatures),len(set(signatures)))
        self.assertEqual(set(signatures),{p['exact_source_signature'] for p in exact['patterns']})
        rows={r['review_id']:r for r in manifest['candidates']}
        canonical=[r['canonical_visual_family_signature'] for r in rows.values() if r['status'] not in ('VARIANT_OF','UNRESOLVED_VISUAL')]
        self.assertEqual(len(canonical),len(set(canonical)))
        self.assertEqual(rows['C005']['variant_of'],'C001')
        self.assertEqual(len(manifest['batch_review_ids']),18)
        self.assertEqual(set(manifest['batch_review_ids']),set(DECISIONS))
        self.assertTrue(all(rows[r]['status']=='REVIEWED' for r in manifest['batch_review_ids']))
        self.assertTrue(all(rows[r]['user_decision']==DECISIONS[r] for r in manifest['batch_review_ids']))
        snapshot=json.loads((root/'docs/manual-review/source-assemblies/batch-02/batch-02-manifest.json').read_text(encoding='utf8'))
        assert_snapshot_registry(snapshot,manifest)
        assert_selected_package_decisions(snapshot, manifest, manifest['batch_review_ids'], DECISIONS, 'batch-02')
        self.assertEqual(len({rows[r]['canonical_visual_family_signature'] for r in manifest['batch_review_ids']}),18)
        self.assertLessEqual(sum(rows[r]['category']=='tree' for r in manifest['batch_review_ids']),3)


if __name__ == '__main__': unittest.main()
