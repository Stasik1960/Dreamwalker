"""ONE new fixture: reviewed bootstrap tuples and per-invocation validation.

All files and SQL are isolated temporary fixture data. No accepted test or
project database is imported or replayed.
"""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class ReviewedSourceTransitionTests(unittest.TestCase):
    def test_bound_intermediate_and_full_validator_once_without_persistent_skip(self):
        with tempfile.TemporaryDirectory(prefix='reviewed-source-transition-') as directory:
            plan=Path(directory);db=sqlite3.connect(':memory:');db.executescript(m.SCHEMA)
            try:
                keys=('migrate','nativeResume','nativeCertificates');old={key:char*64 for key,char in zip(keys,'123')};intermediate={key:char*64 for key,char in zip(keys,'456')};current=c.source_files(m);unknown={key:'f'*64 for key in keys}
                failed_path=plan/'actual-native-failure.json';m.atomic_json(failed_path,{'fixtureOnly':True,'status':'FAIL'});failed=n.ref(m,failed_path)
                intermediate_doc={'fixtureOnly':True,'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','oldSourceFiles':old,'sourceFiles':intermediate,'actualFailedRun':failed,'reviewedIntermediateSourceProofs':[{'path':'not-followed-recursively','sha256':'0'*64}]}
                intermediate_path=plan/'reviewed-intermediate.json';m.atomic_json(intermediate_path,intermediate_doc);intermediate_ref=n.ref(m,intermediate_path)
                proof={'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':current,'oldSourceFiles':old,'actualFailedRun':failed,'reviewedIntermediateSourceProofs':[intermediate_ref]}
                self.assertEqual(c.reviewed_upgrade_source_files(m,proof,old,failed),[old,current,intermediate])
                self.assertEqual(c.reviewed_upgrade_source_files(m,{'sourceFiles':current},old,failed),[old,current])
                # A direct ref is checked by bytes, tuple and exact anchors;
                # nested lists are never traversed or inferred as authority.
                for changed in ({'status':'FAIL'},{'kind':'not-a-source-proof'},{'oldSourceFiles':unknown},{'actualFailedRun':{**failed,'sha256':'b'*64}},{'sourceFiles':{'migrate':'not-a-sha'}}):
                    m.atomic_json(intermediate_path,{**intermediate_doc,**changed});candidate={**proof,'reviewedIntermediateSourceProofs':[n.ref(m,intermediate_path)]}
                    with self.subTest(changed=changed),self.assertRaises(RuntimeError):c.reviewed_upgrade_source_files(m,candidate,old,failed)
                m.atomic_json(intermediate_path,intermediate_doc);intermediate_ref=n.ref(m,intermediate_path);proof['reviewedIntermediateSourceProofs']=[intermediate_ref]
                for refs in ([{**intermediate_ref,'sha256':'f'*64}],{'not':'a-list'},[intermediate_ref]*17):
                    with self.subTest(refs=type(refs).__name__),self.assertRaises((RuntimeError,OSError,KeyError)):c.reviewed_upgrade_source_files(m,{**proof,'reviewedIntermediateSourceProofs':refs},old,failed)
                # Historical no-intermediate transitions retain their exact
                # two-key tuples; current reviewed tuples have all three.
                legacy={key:old[key] for key in keys[:2]};legacy_before={key:'e'*64 for key in keys[:2]}
                self.assertEqual(c.reviewed_upgrade_source_files(m,{'sourceFiles':legacy},legacy_before,failed),[legacy_before,legacy])
                self.assertEqual(c.reviewed_upgrade_source_files(m,{'sourceFiles':current},legacy,failed),[legacy,current])
                self.assertEqual(c.reviewed_upgrade_source_files(m,{'sourceFiles':legacy},old,failed),[old,legacy])
                source_binding={'fixtureOnly':True,'immutableSource':'fixture'};m.meta(db,'binding',source_binding)
                current_proof=plan/'current-proof.json';m.atomic_json(current_proof,proof)
                old_proof=plan/'old-three-key-proof.json';m.atomic_json(old_proof,{'sourceFiles':old})
                legacy_proof=plan/'old-two-key-proof.json';m.atomic_json(legacy_proof,{'sourceFiles':legacy})
                transitions=[{'fromSourceFiles':old,'toSourceFiles':current,'sourceProof':n.ref(m,current_proof),'failedRun':failed},
                             {'fromSourceFiles':legacy,'toSourceFiles':old,'sourceProof':n.ref(m,old_proof),'failedRun':failed},
                             {'fromSourceFiles':legacy_before,'toSourceFiles':legacy,'sourceProof':n.ref(m,legacy_proof),'failedRun':failed}]
                m.meta(db,'nativeResumeSourceBinding',{'sourceFiles':current,'transition':{'path':'fixture-verified-transition','sha256':'c'*64}})
                with patch.object(c,'active_binding',return_value=transitions[0]),patch.object(c,'source_lineage',return_value=transitions):
                    for files in (current,intermediate,old,legacy,legacy_before):c.require_source_files(m,db,files)
                    with self.assertRaises(RuntimeError):c.require_source_files(m,db,unknown)
                    # Exercise the actual n.validate_resume dispatch, with a
                    # minimal bound old receipt and empty finite cases.
                    m.meta(db,'successfulPilotGate',{'fixtureOnly':True});m.meta(db,'fullNativeFunctionGateRequired',True);m.meta(db,'nativeRetainedComponents',[])
                    artifact=plan/'actual-fixture-source.txt';artifact.write_text('fixture only',encoding='utf-8')
                    scope=plan/'scope.json';m.atomic_json(scope,{'fixtureOnly':True})
                    preview=plan/'preview.json';m.atomic_json(preview,{'sourceBinding':source_binding,'cases':[]})
                    receipt_path=plan/'receipt.json'
                    def receipt_for(files):
                        receipt={'status':'PASS','kind':'EXPLICIT_FINITE_NATIVE_RETENTION','sourceBinding':source_binding,'semanticBindings':n.snapshot_bindings(m,db),'sourceFiles':files,'evidence':{'failedSource':{'archive':str(artifact),'sha256':m.sha(artifact)}},'scope':n.ref(m,scope),'preview':n.ref(m,preview),'previousRetention':None}
                        m.atomic_json(receipt_path,receipt);reference=n.ref(m,receipt_path);m.meta(db,'nativeLocalRetention',reference)
                        m.meta(db,'nativeComposeCursor',{'phase':'NATIVE_TRANSFER','batchesCommitted':1,'rowsProcessedSinceAdmission':1024,'retention':reference})
                    # A later upgrade must carry direct intermediate authority
                    # from the already-admitted historical transition too.
                    receipt_for(intermediate);future={key:char*64 for key,char in zip(keys,'789')};future_path=plan/'future-proof.json'
                    m.atomic_json(future_path,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':future,'oldSourceFiles':current,'actualFailedRun':failed})
                    args=SimpleNamespace(plan=plan,source_proof=future_path,source_proof_sha256=m.sha(future_path),failed_run=failed_path)
                    def validate_future(module,handle,target,source_files_validator):
                        source_files_validator(intermediate);source_files_validator(future)
                        with self.assertRaises(RuntimeError):source_files_validator(unknown)
                    with patch.object(c,'source_files',return_value=future),patch.object(c,'failed_evidence',return_value=({'fixtureOnly':True},{},current)),patch.object(n,'validate_resume',side_effect=validate_future) as full:
                        previewed=c.upgrade_preview(args,m,db);full.assert_called_once();self.assertEqual(previewed['fromSourceFiles'],current);self.assertEqual(previewed['toSourceFiles'],future)
                    at=(1,1,1);uid='retained-fixture-P';row={'dim':'.','x':1,'y':1,'z':1,'uuid':uid,'reason':'functional_guest'}
                    db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',tuple(row.values()));domain={'fixtureOnly':True,'object':{'dim':'.','uuid':uid,'pose':'fixture-pose'}}
                    db.execute('INSERT INTO source_visual_domains VALUES(?,?,?,?)',('.',uid,m.canonical(domain),m.digest(domain)))
                    chunk={'fixtureOnly':True,'cachedPayloadSha256':'source-chunk'}
                    ordinary={'decision':'VISUAL_RESERVATION_ONLY','sourceState':m.AIR}
                    data={**ordinary,'dimension':'.','position':list(at),'protectedRow':row,'allProtectedRows':[row],'incoming':{'kind':'legacy_native','uuid':'incoming','pose':'incoming-pose','root':[10,1,1]},'sourceFiles':intermediate,'domainDigest':m.digest(domain),'sourceBE':None,'sourceChunk':chunk,'nativeOrigins':[]}
                    digest=m.digest(data);db.execute('INSERT INTO source_visual_contacts VALUES(?,?,?,?,?,?,?,?)',('.',*at,uid,m.canonical(data['incoming']),m.canonical(data),digest))
                    journal={'schemaVersion':1,'contacts':1,'head':m.digest({'previous':None,'contact':digest}),'sourceBinding':source_binding};m.meta(db,'sourceVisualReservationJournal',journal)
                    mode={'superseded':True,'badSource':False}
                    context=SimpleNamespace(owner_domain=lambda dim,owner:copy.deepcopy(domain),local_native_domains=lambda:{},local_native_cells=lambda:{},original_zones=lambda:{('.',0,0,0):{}} if mode['superseded'] else {},source_point=lambda dim,pos:({'registryId':'fixture:changed','properties':{}} if mode['badSource'] else copy.deepcopy(m.AIR),None,chunk),native_origins=lambda dim,pos,state:[],contact=lambda dim,pos,protected,saved:copy.deepcopy(ordinary))
                    with patch.object(c,'SourceVisualReservations',return_value=context):
                        for files in (current,intermediate):
                            receipt_for(files)
                            with patch.object(c,'validate_source_local_retentions',return_value=None) as full:
                                n.validate_resume(m,db,plan);full.assert_called_once()
                        receipt_for(unknown)
                        with patch.object(c,'validate_source_local_retentions') as full,self.assertRaises(RuntimeError):n.validate_resume(m,db,plan)
                        full.assert_not_called();receipt_for(current)
                        # Every invocation must validate again, and every guard
                        # after that validation must still run before True.
                        for superseded in (True,False):
                            mode['superseded']=superseded
                            with patch.object(c,'validate_source_local_retentions',return_value=None) as full:
                                n.validate_resume(m,db,plan);full.assert_called_once()
                            with patch.object(c,'validate_source_local_retentions',side_effect=RuntimeError('full original journal rejected')) as full,self.assertRaisesRegex(RuntimeError,'full original journal rejected'):n.validate_resume(m,db,plan)
                            full.assert_called_once()
                        mode['superseded']=True;mode['badSource']=True
                        with patch.object(c,'validate_source_local_retentions',return_value=None) as full,self.assertRaisesRegex(RuntimeError,'Superseded positive contact'):n.validate_resume(m,db,plan)
                        full.assert_called_once();mode['badSource']=False
                        m.meta(db,'sourceVisualReservationJournal',{**journal,'head':'bad-head'})
                        with patch.object(c,'validate_source_local_retentions',return_value=None) as full,self.assertRaisesRegex(RuntimeError,'journal is incomplete'):n.validate_resume(m,db,plan)
                        full.assert_called_once();db.execute("DELETE FROM meta WHERE key='sourceVisualReservationJournal'")
                        with patch.object(c,'validate_source_local_retentions',return_value=None) as full:
                            n.validate_resume(m,db,plan);full.assert_called_once()
                        with patch.object(c,'validate_source_local_retentions',side_effect=RuntimeError('no-visual original journal rejected')) as full,self.assertRaisesRegex(RuntimeError,'no-visual original journal rejected'):n.validate_resume(m,db,plan)
                        full.assert_called_once()
            finally:db.close()


if __name__=='__main__':unittest.main()
