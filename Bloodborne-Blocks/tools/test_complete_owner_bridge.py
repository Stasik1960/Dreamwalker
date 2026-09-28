"""Trust-boundary regressions for complete-city source evidence."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import complete_accepted_restore as restore
import complete_owner_bridge as bridge
from atomic_owner_groups import connected_groups, POSITIVE


class EvidenceTests(unittest.TestCase):
    def test_preservation_markers_survive_both_trace_orders(self):
        context = {'result':bridge.CONTEXT_RESULT,'objects':[{'sourceRoot':[0,0,0],'state':'a','cells':[[1,0,0]]}],
            'cells':[{'position':[1,0,0],'actual':'x','preservedOmissions':[{'preserveState':'x'}]}]}
        exact = {'result':next(iter(POSITIVE)),'objects':[{'sourceRoot':[2,0,0],'state':'b','cells':[[1,0,0]]}],
            'cells':[{'position':[1,0,0],'actual':'x'}]}
        for rows in ([context,exact],[exact,context]):
            groups = connected_groups({'closures':rows},{'occurrences':[]},POSITIVE|{bridge.CONTEXT_RESULT})
            self.assertEqual([{'preserveState':'x'}],groups[0]['cells'][(1,0,0)]['preservedOmissions'])

    def test_unapproved_positive_proof_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);p=root/'proof.json';p.write_text('[{"result":"EXACT_HISTORICAL_OWNER_CLOSURE"}]')
            manifest=root/'manifest.json';manifest.write_text('{"proofs":[]}')
            with patch.object(bridge,'PROOF_MANIFEST',manifest), self.assertRaisesRegex(ValueError,'hash_not_approved'):
                bridge.load_additional_proof(p)

    def test_unknown_result_is_refused_even_with_approved_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);p=root/'proof.json';p.write_text('[{"result":"TYPO_PASS"}]')
            manifest=root/'manifest.json';manifest.write_text(json.dumps({'proofs':[{'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}]}))
            with patch.object(bridge,'PROOF_MANIFEST',manifest), self.assertRaisesRegex(ValueError,'unsupported_owner_proof_result'):
                bridge.load_additional_proof(p)

    def test_checked_in_evidence_is_available_and_valid(self):
        document=json.loads(bridge.PROOF_MANIFEST.read_bytes())
        for row in document['proofs']:
            path=restore.ROOT/row['path']
            self.assertTrue(path.is_file())
            self.assertTrue(bridge.load_additional_proof(path))

    def test_self_hashed_arbitrary_directory_is_not_rc2_provenance(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);source=root/'foreign';source.mkdir();(source/'player.dat').write_bytes(b'preserve')
            digest=restore.tree_sha(restore.hash_tree(source))
            with self.assertRaisesRegex(ValueError,'not pinned rc2'):
                restore.run(source,root/'output',expected_source_tree_sha256=digest)
            self.assertFalse((root/'output').exists())


if __name__ == '__main__':
    unittest.main()
