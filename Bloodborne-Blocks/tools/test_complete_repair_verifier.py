"""Focused negative tests for the independent accepted-repair verifier."""
import sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import verify_complete_accepted_repair as verifier

AIR = ('minecraft:air', ())
PART = ('bloodborne_blocks:architecture_part', ())
TARGET = ('bloodborne_blocks:o_arch', ())

class Runtime:
    definitions = {'bloodborne_blocks:o_arch': {'models': {'': 'model'}}}
    def normalized(self, value): return verifier.state(value) if isinstance(value,str) else value

class World:
    def __init__(self, values, entities=None):
        self.values = values; self.entities = entities or {}
    def get(self, dim, pos): return self.values.get((dim, tuple(pos)), AIR)

def report(change, output=TARGET):
    return {'transactions': [{'transaction':'t', 'result':'ready', 'changes':[{'position':list(change)}],
                              'outputs':[{'targetRoot':list(change), 'target':'bloodborne_blocks:o_arch'}]}],
            'ledger':[{'transaction':'t','dimension':'minecraft:overworld','changes':[{'position':list(change)}],
                       'outputs':[{'targetRoot':list(change),'target':'bloodborne_blocks:o_arch'}]}]}

class VerifierTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); root=Path(self.tmp.name)
        (root/'city').mkdir(); (root/'city'/'geometry.json').write_text('{"blocks":{}}')
        self.resources=root/'resources'; self.resources.mkdir()
        self.pos=(0,64,0); self.dim='minecraft:overworld'
        self.before=World({(self.dim,self.pos):AIR}); self.after=World({(self.dim,self.pos):TARGET})
        self.targets={(self.dim,self.pos):{TARGET}}; self.sources={}; self.objects={}; self.preserved=set()
    def tearDown(self): self.tmp.cleanup()
    def check(self, rep, before=None, after=None, targets=None, sources=None, objects=None, preserved=None):
        with patch.object(verifier, 'wall_equal', return_value=False):
            return verifier.transaction_checks(rep, before or self.before, after or self.after, Runtime(),
                targets if targets is not None else self.targets, sources if sources is not None else self.sources, objects if objects is not None else self.objects,
                preserved if preserved is not None else self.preserved, self.resources)
    def test_forged_output_without_independent_source_authority_rejected(self):
        with self.assertRaisesRegex(ValueError,'unproved new output roots'): self.check(report(self.pos), targets={})
    def test_legitimate_independently_authorized_output_passes(self):
        self.assertEqual('PASS',self.check(report(self.pos))['result'])
    def test_foreign_block_entity_loss_rejected(self):
        from world_io import TAG_COMPOUND
        foreign=verifier.Tag(TAG_COMPOUND,{'id': verifier.Tag(verifier.TAG_STRING,'foreign:be')})
        before=World({(self.dim,self.pos):AIR},{(self.dim,*self.pos):foreign})
        after=World({(self.dim,self.pos):TARGET},{})
        with self.assertRaisesRegex(ValueError,'foreign block entity changed'): self.check(report(self.pos),before,after)
    def test_preserved_root_loss_rejected(self):
        preserved={(self.dim,self.pos)}
        before=World({(self.dim,self.pos):TARGET}); after=World({(self.dim,self.pos):AIR})
        rep={'transactions':[{'transaction':'t','result':'ready','changes':[{'position':list(self.pos)}], 'outputs':[]}],
             'ledger':[{'transaction':'t','dimension':self.dim,'changes':[{'position':list(self.pos)}], 'outputs':[]}]}
        with self.assertRaises(ValueError): self.check(rep,before,after,targets={},preserved=preserved)
    def test_non_target_change_outside_ledger_rejected(self):
        from test_logical_world import write_chunk
        source=Path(self.tmp.name)/'before';out=Path(self.tmp.name)/'after'
        write_chunk(source/'region/r.0.0.mca',0,0,{self.pos:('minecraft:stone',{})})
        write_chunk(out/'region/r.0.0.mca',0,0,{self.pos:('minecraft:diamond_block',{})})
        result=verifier.preservation(source,out,{'ledger':[]})
        self.assertEqual('FAIL',result['result'])
        self.assertTrue(any('outside ledger' in e for e in result['errors']))
    def test_zero_candidate_coverage_cannot_be_pass(self):
        from accepted_repair_coverage import audit_coverage,SOURCE_SHA
        self.after.binding_errors=[]
        result=audit_coverage({'source_sha256':SOURCE_SHA,'occurrences':[],'families':[]},self.after,Runtime())
        self.assertEqual('FAIL',result['knownCensusCoverage'])
        self.assertEqual('FAIL',result['coverageCompleteness'])

if __name__=='__main__': unittest.main()
