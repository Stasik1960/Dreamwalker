"""One admission regression: both accepted optional chains survive a code upgrade."""
import copy
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c
from test_native_whole_template_fallback import WholeTemplateFallbackTests


class SourceUpgradeChainTests(unittest.TestCase):
    seed=WholeTemplateFallbackTests.seed
    setUp=WholeTemplateFallbackTests.setUp
    tearDown=WholeTemplateFallbackTests.tearDown
    install_upgrade=WholeTemplateFallbackTests.install_upgrade
    certificate=WholeTemplateFallbackTests.certificate
    execute=WholeTemplateFallbackTests.execute

    def failure_with_current_archives(self,name,log):
        directory=self.plan/(name+'.inputs');directory.mkdir();archives={}
        for filename in ('migrate.py','native_resume.py','native_certificates.py','context_stage.py'):
            path=directory/filename;path.write_bytes(Path(m.__file__).with_name(filename).read_bytes())
            archives[filename]={'archive':str(path),'sha256':m.sha(path)}
        log_path=self.plan/(name+'.log');log_path.write_text(log,encoding='utf-8')
        path=self.plan/(name+'.json');m.atomic_json(path,{'status':'FAIL','exitCode':1,'args':['--plan',str(self.plan),'resume-native-compose'],'inputSources':archives,'log':str(log_path)})
        return path,archives

    def install_visual_correction(self):
        native=self.seed('visual_failed_C',23,'substrate',(),selected=False)
        whole=self.seed('visual_W',20,'whole',(),selected=False)
        whole={key:whole[key] for key in ('object','pose','poseDataSha256','root')};whole['runtimeMemberCells']=[[20,0,0],[21,0,0]]
        for x in (20,21,22):self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',x,0,0,'visual_W','whole_local_overlap'))
        self.db.commit();binding=m.meta(self.db,'binding')
        whole['physicalMembers']=[{'position':[x,0,0],'sourceState':{'registryId':'fixture:visual_native','properties':{}},'sourceBE':{'exists':False},'pointDemand':c.point(m,self.db,'.',[x,0,0])} for x in (20,21)]
        native={key:native[key] for key in ('uuid','pose','poseDataSha256','root')};native['dimension']='.'
        row={'dim':'.','x':22,'y':0,'z':0,'uuid':'visual_W','reason':'whole_local_overlap'};point=c.point(m,self.db,'.',[22,0,0])
        recipe=self.plan/'visual-member-source.json';m.atomic_json(recipe,{'sourceBinding':binding,'statue':{**whole,'members':whole['physicalMembers']},
            'actualTarget':{'position':[22,0,0],'sourceBE':{'exists':False},'sourceState':{'registryId':'fixture:ordinary_visual_target','properties':{}}},'targetStatueRecipeOrigins':[]})
        semantics=self.plan/'visual-semantics.json';m.atomic_json(semantics,{'status':'PASS_SOURCE_REVIEW_NOT_EXECUTION','sourceBinding':binding,'statueUuid':'visual_W','pose':whole['pose'],
            'runtimeMemberCells':whole['runtimeMemberCells'],'wholeModelAndMemberLifecyclePreserved':True,'itemPickDropGapUnresolved':True,'gameplayPass':False})
        history=self.plan/'visual-history.json';m.atomic_json(history,{'sourceBinding':binding,'strictOriginalTargetMatches':[],'targetPosition':[22,0,0],'latestCommittedReceipt':m.meta(self.db,'nativeLocalRetention')})
        source=self.plan/'visual-source.json';m.atomic_json(source,{'status':'PASS_SOURCE_CORRECTION_ONLY','sourceBinding':binding,'native':native,'retainedWhole':whole,'protectionRow':row,'pointSnapshot':point,'sourceRecipeProof':n.ref(m,recipe)})
        failure,_=self.failure_with_current_archives('visual-failure','Required native visual overhang meets a protected local object visual_failed_C')
        doc={'schemaVersion':1,'status':'PASS_SOURCE_CORRECTION_ONLY','kind':'FINITE_NATIVE_VISUAL_PROTECTION_CORRECTION_V1','sourceBinding':binding,'failedRun':n.ref(m,failure),
            'native':native,'retainedWhole':whole,'protectionRow':row,'pointSnapshot':point,'sourceProof':n.ref(m,source),'nativeWholeSemantics':n.ref(m,semantics),'historicalStrictOriginalCheck':n.ref(m,history),
            'ownerGraphTraversal':False,'applyAuthorization':False,'itemPickDropGapUnresolved':True,'gameplayPass':False}
        path=self.plan/'visual-certificate.json';m.atomic_json(path,doc)
        args=SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'visual-preview.json',execute=False,expected_preview_sha256=None)
        c.correct_visual_protection(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.correct_visual_protection(args,m)

    def test_reviewed_upgrade_validates_both_optional_chains_without_activating_new_code(self):
        self.execute(self.certificate());self.install_visual_correction()
        admitted=c.source_files(m);failure,archives=self.failure_with_current_archives('next-resume','A subsequent real native guard failed')
        previous=m.meta(self.db,'nativeLocalRetention');parent=c.bound(m,previous)
        scope=self.plan/'current-generation-scope.json';m.atomic_json(scope,{'fixture':'accepted-no-additional-native-identities'})
        preview=self.plan/'current-generation-preview.json';m.atomic_json(preview,{'sourceBinding':m.meta(self.db,'binding'),'cases':[]})
        receipt={**parent,'sourceFiles':admitted,'scope':n.ref(m,scope),'preview':n.ref(m,preview),'previousRetention':previous,
                 'evidence':{'failedSource':archives['migrate.py'],'failedRun':n.ref(m,failure)}}
        path=self.plan/'current-generation-receipt.json';m.atomic_json(path,receipt);head=n.ref(m,path);m.meta(self.db,'nativeLocalRetention',head)
        cursor=m.meta(self.db,'nativeComposeCursor');cursor['retention']=head;m.meta(self.db,'nativeComposeCursor',cursor);self.db.commit();n.validate_resume(m,self.db,self.plan)
        next_files={**admitted,'nativeCertificates':m.hashlib.sha256((admitted['nativeCertificates']+'next-reviewed-code').encode()).hexdigest()}
        proof=self.plan/'next-source-proof.json';m.atomic_json(proof,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':next_files,'oldSourceFiles':admitted})
        args=SimpleNamespace(plan=self.plan,failed_run=failure,source_proof=proof,source_proof_sha256=m.sha(proof),preview=self.plan/'next-upgrade-preview.json',execute=False,expected_preview_sha256=None)
        before=n.counts(self.db);changes=self.db.total_changes;bindings=n.snapshot_bindings(m,self.db);visual=m.meta(self.db,'nativeVisualProtectionCorrection');whole=m.meta(self.db,'nativeWholeTemplateFallback')
        with patch.object(c,'source_files',return_value=next_files),patch.object(c,'check_correction_whole',wraps=c.check_correction_whole) as visual_check,patch.object(c,'whole_template_fallback_state',wraps=c.whole_template_fallback_state) as whole_check:
            result=c.upgrade_preview(args,m,self.db)
            self.assertEqual(result['fromSourceFiles'],admitted);self.assertEqual(result['toSourceFiles'],next_files)
            self.assertTrue(visual_check.called);self.assertTrue(whole_check.called)
            self.assertEqual(self.db.total_changes,changes);self.assertEqual(n.counts(self.db),before);self.assertEqual(n.snapshot_bindings(m,self.db),bindings)
            self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor);self.assertEqual(m.meta(self.db,'nativeLocalRetention'),head)
            with self.assertRaisesRegex(RuntimeError,'explicit native consumer source upgrade'):c.active_binding(m,self.db)
            for key,reference in (('nativeVisualProtectionCorrection',visual),('nativeWholeTemplateFallback',whole)):
                forged=copy.deepcopy(c.bound(m,reference));forged['sourceFiles']={'nativeCertificates':'unreviewed'}
                fake=self.plan/(key+'-unreviewed.json');m.atomic_json(fake,forged);m.meta(self.db,key,n.ref(m,fake));self.db.commit()
                with self.subTest(chain=key),self.assertRaisesRegex(RuntimeError,'another unreviewed consumer'):c.upgrade_preview(args,m,self.db)
                m.meta(self.db,key,reference);self.db.commit()


if __name__=='__main__':unittest.main()
