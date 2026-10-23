"""Only new retained-P correction and ancestor-head admission boundaries."""
import copy
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c
from test_native_certificates import CertificateTests
from test_native_source_upgrade_chains import SourceUpgradeChainTests


class RetainedFunctionalVisualTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown
    install_upgrade=CertificateTests.install_upgrade
    failure_with_current_archives=SourceUpgradeChainTests.failure_with_current_archives
    install_visual_correction=SourceUpgradeChainTests.install_visual_correction

    def setUp(self):
        CertificateTests.setUp(self)
        self.p=self.seed('P_original',30,'static',(),selected=False);self.c=self.seed('functional_C',33,'substrate',(),selected=False)
        for x in (30,31,32,34):self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',x,0,0,'P_original','functional_guest'))
        self.trigger={'reason':'native_functional_base_retained','registryId':'fixture:protected_v12','properties':{},'cell':[34,0,0],'dimension':'.','directlyConflictingOwners':['P_original']}
        self.preflight=self.plan/'functional-preflight.json';m.atomic_json(self.preflight,{'conflicts':[self.trigger]});m.meta(self.db,'functionalGuestPreflight',n.ref(m,self.preflight))
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',33,0,0))
        receipt=m.read(self.old_receipt);receipt['semanticBindings']=n.snapshot_bindings(m,self.db);m.atomic_json(self.old_receipt,receipt);head=n.ref(m,self.old_receipt)
        m.meta(self.db,'nativeLocalRetention',head);cursor=m.meta(self.db,'nativeComposeCursor');cursor['retention']=head;m.meta(self.db,'nativeComposeCursor',cursor);self.db.commit();self.install_upgrade()

    def certificate(self,trigger_inside=False):
        binding=m.meta(self.db,'binding');native={key:self.c[key] for key in ('uuid','object','pose','poseDataSha256','root','runtimeMemberCells')};native.update(dimension='.',visualCells=[[32,0,0],[33,0,0]],physicalMembers=[{'position':[33,0,0],'sourceState':m.AIR,'pointDemand':c.point(m,self.db,'.',[33,0,0])}],visualPointDemands=[c.point(m,self.db,'.',[x,0,0]) for x in (32,33)])
        owner={key:self.p[key] for key in ('uuid','object','pose','poseDataSha256','root')};owner.update(dimension='.',runtimeMemberCells=[[30,0,0],[31,0,0]])
        root_be={'exists':True,'id':'bloodborne_dw:composite_cell','privateOuterFields':[],'resident':{'owner':'P_original'},'payload':{'ImportedTypeVersion':1}}
        owner['physicalMembers']=[{'position':[x,0,0],'sourceState':{'registryId':'fixture:P_original' if x==30 else 'fixture:native_support','properties':{}},'sourceBE':root_be if x==30 else {'exists':False},'storedContributions':[{'uuid':'P_original'}],'pointDemand':c.point(m,self.db,'.',[x,0,0])} for x in (30,31)]
        owner.update(originalRootState=owner['physicalMembers'][0]['sourceState'],rootBE=root_be,sourceMechanism={'className':'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock','wholeModelAndMemberLifecyclePreserved':True,'rootResidentPayloadAndLedgerPreserved':True,'nonV12':True,'exactManifestPoseMatches':[],'exactComponentGroups':[]})
        row={'dim':'.','x':32,'y':0,'z':0,'uuid':'P_original','reason':'functional_guest'};point=c.point(m,self.db,'.',[32,0,0]);target={'position':[32,0,0],'pointDemand':point,'sourceState':{'registryId':'fixture:ordinary_target','properties':{}},'sourceBE':{'exists':False},'PStoredContributions':[],'POldMembers':[],'isPPhysical':False}
        trigger=copy.deepcopy(self.trigger)
        if trigger_inside:trigger['cell']=[32,0,0]
        original={'object':owner['object'],'poseDataSha256':owner['poseDataSha256'],'root':owner['root'],'runtimeMemberCells':owner['runtimeMemberCells'],'runtimeMembers':owner['physicalMembers'],'rootBE':root_be,
            'rootResidentUuidMatches':True,'rootResidentRootMatches':True,'rootResidentRegistryMatches':True,'payloadShiftMatches':True,'payloadMountMatches':True,'payloadImportedTypeVersion':1}
        pfacts=self.plan/'P-source.json';m.atomic_json(pfacts,{'sourceBinding':binding,'protectedObject':original,'target':target,'summary':{'targetNativeRecipeHasPOrigin':False},'sourceClassCapability':{'rows':[{'className':owner['sourceMechanism']['className']}]},'classification':{'protectedOriginalV12':False},'manifestPriority':{'exactPoseMatches':[],'exactComponentGroups':[]},'functionReason':{'preflightBinding':n.ref(m,self.preflight),'exactSavedConflicts':[trigger]}})
        owner['preflightRetention']={'proof':n.ref(m,pfacts),'triggerState':{'registryId':trigger['registryId'],'properties':trigger['properties']},'position':trigger['cell'],'protectedRow':row,'sourcePreflightBinding':n.ref(m,self.preflight)}
        cfacts=self.plan/'C-source.json';m.atomic_json(cfacts,{'sourceBinding':binding,'nativeObject':native['object'],'nativePoseDataSha256':native['poseDataSha256'],'runtimeMemberCells':native['runtimeMemberCells'],'physicalMembers':native['physicalMembers'],'visualCells':native['visualCells'],'visualPointDemands':native['visualPointDemands']})
        diag=self.plan/'diagnostic.json';m.atomic_json(diag,{'fixture':'exact actual source C/P'})
        semantics=self.plan/'P-semantics.json';m.atomic_json(semantics,{'status':'PASS_SOURCE_REVIEW_NOT_EXECUTION','sourceBinding':binding,'retainedFunctionalUuid':'P_original','pose':owner['pose'],'runtimeMemberCells':owner['runtimeMemberCells'],'originalRootResidentPayloadLedgerPreserved':True,'wholeModelAndMemberLifecyclePreserved':True,'targetOutsideRuntimeMembers':True,'protectedV12Unchanged':True,'gameplayPass':False})
        source=self.plan/'functional-source-proof.json';m.atomic_json(source,{'status':'PASS_SOURCE_CORRECTION_ONLY','sourceBinding':binding,'native':native,'retainedFunctional':owner,'protectionRow':row,'pointSnapshot':point,'retainedFunctionalSemantics':n.ref(m,semantics),'nativeAndOwnerSourceProofs':{'nativeSource':n.ref(m,cfacts),'retainedFunctionalSource':n.ref(m,pfacts),'diagnostic':n.ref(m,diag)}})
        history=self.plan/'functional-strict-history.json';m.atomic_json(history,{'sourceBinding':binding,'targetPosition':[32,0,0],'strictOriginalTargetMatches':[],'latestCommittedReceipt':m.meta(self.db,'nativeLocalRetention')})
        failure,_=self.failure_with_current_archives('functional-failure','Required native visual overhang meets a protected local object functional_C')
        doc={'schemaVersion':1,'status':'PASS_SOURCE_CORRECTION_ONLY','kind':'FINITE_RETAINED_FUNCTIONAL_VISUAL_PROTECTION_CORRECTION_V1','sourceBinding':binding,'failedRun':n.ref(m,failure),'native':native,'retainedFunctional':owner,'protectionRow':row,'pointSnapshot':point,'sourceProof':n.ref(m,source),'historicalStrictOriginalCheck':n.ref(m,history),'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},'ownerGraphTraversal':False,'applyAuthorization':False,'gameplayPass':False}
        path=self.plan/'functional-certificate.json';m.atomic_json(path,doc)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'functional-preview.json',execute=False,expected_preview_sha256=None)

    def execute(self,args):
        c.correct_retained_functional_visual(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.correct_retained_functional_visual(args,m)

    def test_exact_row_positive_and_idempotent_preserves_all_work_and_heads(self):
        args=self.certificate();before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');head=m.meta(self.db,'nativeLocalRetention');self.execute(args);n.validate_resume(m,self.db,self.plan)
        self.assertEqual(n.counts(self.db),before);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor);self.assertEqual(m.meta(self.db,'nativeLocalRetention'),head)
        self.assertIsNone(self.db.execute('SELECT 1 FROM protected_cells WHERE x=32').fetchone())
        self.assertEqual(self.db.execute("SELECT x FROM protected_cells WHERE uuid='P_original' ORDER BY x").fetchall(),[(30,),(31,),(34,)])
        c.correct_retained_functional_visual(args,m);self.assertEqual(n.counts(self.db),before)

    def test_physical_protection_work_and_extra_target_owner_block(self):
        args=self.certificate();self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',31,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'physical member has replacement work'):c.correct_retained_functional_visual(args,m)
        self.db.execute('DELETE FROM cell_queue WHERE x=31');self.db.execute('DELETE FROM protected_cells WHERE x=30');self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'physical protection disappeared'):c.correct_retained_functional_visual(args,m)
        self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',30,0,0,'P_original','functional_guest'));self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',32,0,0,'other_original','unknown'));self.db.commit()
        with self.assertRaises(RuntimeError):c.correct_retained_functional_visual(args,m)

    def test_v12_trigger_inside_c_and_historical_strict_target_block(self):
        args=self.certificate(trigger_inside=True)
        with self.assertRaisesRegex(RuntimeError,'trigger must remain outside'):c.correct_retained_functional_visual(args,m)
        args=self.certificate();head=m.meta(self.db,'nativeLocalRetention');receipt=c.bound(m,head);preview=c.bound(m,receipt['preview']);preview['cases'][0]['retainedNative']['mustRemainOriginalCells'].append([32,0,0])
        path=self.plan/'strict-preview.json';m.atomic_json(path,preview);receipt.update(preview=n.ref(m,path),previousRetention=head);path=self.plan/'strict-receipt.json';m.atomic_json(path,receipt);m.meta(self.db,'nativeLocalRetention',n.ref(m,path));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'Historical strict original native'):c.correction_strict_history(m,self.db,'.',[32,0,0],head)

    def test_receipt_before_commit_rollback_retry_and_final_gate(self):
        args=self.certificate();c.correct_retained_functional_visual(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);before=n.counts(self.db);original=m.atomic_json
        def crash(path,value):
            original(path,value)
            if Path(path).parent.name=='retained-functional-visual-corrections':raise RuntimeError('functional receipt before commit')
        with patch.object(m,'atomic_json',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.correct_retained_functional_visual(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertIsNotNone(self.db.execute('SELECT 1 FROM protected_cells WHERE x=32').fetchone());c.correct_retained_functional_visual(args,m)
        self.db.execute('UPDATE cell_queue SET processed=1');self.db.commit();self.db.execute('BEGIN');n.checkpoint(m,self.db,'FINALIZING');self.db.rollback()
        self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',32,0,0,0,0,'actual_C',1,m.canonical({'registryId':'fixture:ordinary_target','properties':{}}),0));self.db.commit();c.validate_retained_functional_visual_corrections(m,self.db,finalized=True)
        self.db.execute('DELETE FROM protected_cells WHERE x=34');self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'trigger protection disappeared'):c.validate_retained_functional_visual_corrections(m,self.db,finalized=True)

    def test_ancestor_retention_source_upgrades_with_optional_chains_without_fake_retention(self):
        self.execute(self.certificate());self.install_visual_correction();admitted=c.source_files(m);head=m.meta(self.db,'nativeLocalRetention')
        self.assertNotEqual(c.bound(m,head)['sourceFiles'],admitted)
        failure,_=self.failure_with_current_archives('next-functional-resume','Actual next native guard')
        newer={**admitted,'nativeCertificates':m.hashlib.sha256((admitted['nativeCertificates']+'next').encode()).hexdigest()};proof=self.plan/'next-functional-upgrade.json'
        m.atomic_json(proof,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','oldSourceFiles':admitted,'sourceFiles':newer})
        args=SimpleNamespace(plan=self.plan,failed_run=failure,source_proof=proof,source_proof_sha256=m.sha(proof));before=n.counts(self.db);changes=self.db.total_changes
        with patch.object(c,'source_files',return_value=newer),patch.object(c,'retained_functional_visual_state',wraps=c.retained_functional_visual_state) as pcheck,patch.object(c,'check_correction_whole',wraps=c.check_correction_whole) as vcheck:
            result=c.upgrade_preview(args,m,self.db);self.assertEqual(result['parentRetention'],head);self.assertTrue(pcheck.called);self.assertTrue(vcheck.called)
            self.assertEqual(n.counts(self.db),before);self.assertEqual(self.db.total_changes,changes);self.assertEqual(m.meta(self.db,'nativeLocalRetention'),head)
            with self.assertRaisesRegex(RuntimeError,'explicit native consumer source upgrade'):c.active_binding(m,self.db)
            bad={**c.bound(m,head),'sourceFiles':{'nativeCertificates':'unreviewed'},'previousRetention':head};path=self.plan/'unreviewed-head.json';m.atomic_json(path,bad);m.meta(self.db,'nativeLocalRetention',n.ref(m,path));self.db.commit()
            with self.assertRaisesRegex(RuntimeError,'not a reviewed source-lineage ancestor'):c.upgrade_preview(args,m,self.db)


if __name__=='__main__':unittest.main()
