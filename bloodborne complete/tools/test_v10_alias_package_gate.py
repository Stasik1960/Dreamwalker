"""Evidence-binding checks; synthetic inputs are never runtime proof.

The separate nine typed-region tests exercise instance/item data loss. These
checks ensure the package adapter cannot accept a stale, incomplete or changed
independent proof instead of rechecking its actual three worlds.
"""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import package_review_v10 as package
import verify_review_v10_alias_saved as verifier


class AliasPackageGateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.sha='7'*64
        self.proof={'schema':'dw-v10-alias-saved-independent-v1',
            'status':'PASS_14_OLD_ALIAS_INSTANCES_SAVED_REENTERED_AND_PRODUCTION_REOPENED_TYPED_EXACT',
            'productionJarSha256':self.sha,'oldTypes':7,'permanentInstances':14,
            'instancesPerAlias':2,'savedOldInventoryStacks':7,'temporaryOldItemConstructors':7,
            'temporaryOldRegistryCreativeAttacks':7,'temporaryCanonicalCreativeAttacks':7,
            'savedTemporaryRpInstancesOrItemDrops':0,'forcedChunksRestoredAndAbsentOnDisk':True,
            'copySourcesStillByteExact':True,'reports':[],'worlds':['one','two','three']}
        self.phases=[]
        for mode in ('AUTHOR','REENTER','PRODUCTION_REOPEN'):
            source=self.root/(mode+'.json');source.write_text('{}',encoding='utf8')
            console=self.root/(mode+'.log');console.write_text('synthetic',encoding='utf8')
            self.proof['reports'].append({'path':str(source),'bytes':source.stat().st_size,'sha256':package.digest(source)})
            self.phases.append({'raw':None,'consoleRecord':{'path':str(console)}})
        self.path=self.root/'independent.json'

    def gate(self,declared=None,actual=None):
        self.path.write_text(json.dumps(declared or self.proof),encoding='utf8')
        with patch.object(verifier,'checked_phase',side_effect=self.phases) as phases,patch.object(verifier,'verify',return_value=actual or self.proof) as typed:
            result=package.gate_alias_disk(self.path,self.sha)
            self.assertEqual([call.args[2] for call in phases.call_args_list],['AUTHOR','REENTER','PRODUCTION_REOPEN'])
            typed.assert_called_once()
            return result

    def test_adapter_rechecks_three_actual_phase_roles(self):
        result=self.gate()
        self.assertEqual(len(result['reports']),3)
        self.assertEqual(len(result['entries']),3)

    def test_previous_artifact_proof_rejected(self):
        proof=copy.deepcopy(self.proof);proof['productionJarSha256']='8'*64
        with self.assertRaisesRegex(ValueError,'artifact binding'):self.gate(proof)

    def test_missing_production_only_third_process_rejected(self):
        proof=copy.deepcopy(self.proof);proof['reports'].pop()
        with self.assertRaisesRegex(ValueError,'Three actual alias'):self.gate(proof)

    def test_claimed_worlds_cannot_replace_rechecked_typed_results(self):
        proof=copy.deepcopy(self.proof);proof['worlds'][2]='different-saved-world'
        with self.assertRaisesRegex(ValueError,'differs from actual three saved worlds'):self.gate(proof,copy.deepcopy(self.proof))

    def test_changed_report_bytes_rejected_before_typed_recheck(self):
        Path(self.proof['reports'][1]['path']).write_text('{"changed":true}',encoding='utf8')
        with self.assertRaisesRegex(ValueError,'wrapper bytes changed'):self.gate()


if __name__=='__main__':unittest.main()
