from __future__ import annotations
import gzip
import json
import tempfile
import unittest
from pathlib import Path
from build_nightmare_contracts import BASELINE_COMMIT, c654_properties, freeze_baseline, mark_old_splits, strip_provenance

class NightmareCompilerTests(unittest.TestCase):
 def test_freeze_is_small_idempotent_and_old_flags_are_fail_closed(self):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder); logical=root/'logical'; logical.mkdir()
   (logical/'definitions.json').write_text(json.dumps({'blocks':[{'id':'o_c654'},{'id':'unrelated'}]}),encoding='utf8')
   (logical/'contracts-v2.json').write_text(json.dumps({'families':[{'id':'o_c654'},{'id':'unrelated'}]}),encoding='utf8')
   baseline=root/'baseline.json.gz'; first=freeze_baseline(logical,baseline); self.assertEqual(BASELINE_COMMIT,first['baseline_commit'])
   (logical/'definitions.json').write_text(json.dumps({'blocks':[{'id':'changed'}]}),encoding='utf8')
   self.assertEqual(first,freeze_baseline(logical,baseline))
   contracts={'families':[{'id':'o_c654'},{'id':'o_c1491'},{'id':'unrelated'}]}; hidden=set(); mark_old_splits(contracts,hidden)
   self.assertTrue(all(row.get('migration_disabled') for row in contracts['families'] if row['id']!='unrelated'))
   self.assertEqual(({'facing':['north','east','south','west'],'variant':['surface','source_depth']},{'facing':['north','east','south','west']}),c654_properties())
   self.assertEqual([{'texture':'x','vertices':[[0,0,0,0,0]]}],strip_provenance([{'texture':'x','vertices':[[0,0,0,0,0]],'source_element':1}]))

if __name__=='__main__': unittest.main()
