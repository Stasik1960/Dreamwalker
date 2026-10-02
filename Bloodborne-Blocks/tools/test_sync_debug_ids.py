"""Persisted debug aliases must survive catalog changes without reassignment."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sync_debug_ids as ids


class StableDebugIdTests(unittest.TestCase):
    def test_append_retire_and_rerun(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resource = root / 'src/main/resources/bloodborne_blocks/debug-ids.json'
            logical = resource.parent / 'logical/definitions.json'
            city = resource.parent / 'city/definitions.json'
            logical.parent.mkdir(parents=True)
            city.parent.mkdir(parents=True)
            logical.write_text(json.dumps({'blocks': [{'id': 'tree'}]}))
            city.write_text(json.dumps({'blocks': [{'id': 'wall'}]}))
            with patch.object(ids, 'ROOT', root), patch.object(ids, 'RESOURCE', resource):
                ids.main()
                first = json.loads(resource.read_text())['ids']
                ids.main()
                self.assertEqual(first, json.loads(resource.read_text())['ids'])
                logical.write_text(json.dumps({'blocks': [{'id': 'tree'}, {'id': 'a_new'}]}))
                city.write_text(json.dumps({'blocks': []}))
                ids.main()
                second = json.loads(resource.read_text())
                for name in ('tree', 'architecture_part'):
                    self.assertEqual(first[name], second['ids'][name])
                self.assertIn(first['wall'], second['retiredIds'])
                self.assertNotIn(second['ids']['a_new'], first.values())
                city.write_text(json.dumps({'blocks': [{'id': 'wall'}]}))
                ids.main()
                third = json.loads(resource.read_text())
                self.assertNotEqual(first['wall'], third['ids']['wall'])
                self.assertEqual(second['ids']['a_new'], third['ids']['a_new'])


if __name__ == '__main__':
    unittest.main()
