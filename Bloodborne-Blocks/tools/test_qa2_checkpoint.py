"""Reject stale/non-QA2 evidence without starting Minecraft or building a JAR."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import verify_qa2_checkpoint as checkpoint


class CheckpointTests(unittest.TestCase):
    def test_required_game_tests_and_failures(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = {name: root / name for name in ('ORIENTATION_REPORT', 'SOURCE_REPORT', 'TREE_REPORT', 'GAMETEST_XML')}
            paths['ORIENTATION_REPORT'].write_text(json.dumps({'result': 'PASS', 'rotation_checks': 1008}))
            for name in ('SOURCE_REPORT', 'TREE_REPORT'):
                paths[name].write_text(json.dumps({'cases': [{'status': 'passed'}]}))
            names = sorted(checkpoint.QA2_GAMETESTS) + [f'baseline_{i}' for i in range(11)]
            with patch.multiple(checkpoint, **paths):
                def write(names, failure=''):
                    paths['GAMETEST_XML'].write_text('<testsuite>' + ''.join(
                        f'<testcase name="{name}">{failure}</testcase>' for name in names) + '</testsuite>')
                write(names)
                self.assertEqual(checkpoint._test_reports()['gametests'], 17)
                write([f'old_{i}' for i in range(17)])
                with self.assertRaisesRegex(AssertionError, 'QA2'):
                    checkpoint._test_reports()
                write(names, '<failure message="regression"/>')
                with self.assertRaisesRegex(AssertionError, 'failure/error'):
                    checkpoint._test_reports()

    def test_build_proof_is_bound_to_inputs_reports_and_jar(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'proof.json'
            payload = {'inputs': {'source.java': 'a'}, 'reports': {'tests.xml': 'b'}, 'jar_sha256': 'c'}
            path.write_text(json.dumps(payload))
            with patch.object(checkpoint, 'BUILD_PROOF', path), patch.object(checkpoint, '_proof_payload', return_value=payload):
                self.assertEqual(len(checkpoint._verified_build_proof()), 64)
            for field in payload:
                changed = {**payload, field: 'changed'}
                with patch.object(checkpoint, 'BUILD_PROOF', path), patch.object(checkpoint, '_proof_payload', return_value=changed):
                    with self.assertRaisesRegex(AssertionError, 'stale'):
                        checkpoint._verified_build_proof()


if __name__ == '__main__':
    unittest.main()
