"""New one-row visual-only correction boundaries; temporary DBs only."""
import copy
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c
from test_native_certificates import CertificateTests


class VisualProtectionTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown
    install_upgrade=CertificateTests.install_upgrade

    def setUp(self):
        CertificateTests.setUp(self);self.install_upgrade()
        self.db.execute("DELETE FROM fragments WHERE uuid='D_new'");self.db.execute("DELETE FROM selected_objects WHERE uuid='D_new'")
        self.db.execute("UPDATE objects SET kind='whole',reason='confirmed_sculpture_whole_object_template' WHERE uuid='D_new'")
        self.db.execute('DELETE FROM cell_queue WHERE x IN(2,3)');self.db.execute('DELETE FROM bridge_cells WHERE x=4')
        for x in (2,3,4):self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',x,0,0,'D_new','whole_local_overlap'))
        self.failed_native=self.seed('failed_C',6,'substrate',(),selected=False);self.db.commit()
        failure=m.read(self.failure);Path(failure['log']).write_text('Required native visual overhang meets a protected local object failed_C',encoding='utf-8')

    def certificate(self):
        whole=copy.deepcopy(self.owner);whole['object'].update(kind='whole',reason='confirmed_sculpture_whole_object_template')
        whole={key:whole[key] for key in ('object','pose','poseDataSha256','root')};whole['runtimeMemberCells']=[[2,0,0],[3,0,0]]
        whole['physicalMembers']=[{'position':[x,0,0],'sourceState':{'registryId':'fixture:native_whole','properties':{'part':str(x)}},
            'sourceBE':{'exists':False},'pointDemand':c.point(m,self.db,'.',[x,0,0])} for x in (2,3)]
        native={key:self.failed_native[key] for key in ('uuid','pose','poseDataSha256','root')};native['dimension']='.'
        row={'dim':'.','x':4,'y':0,'z':0,'uuid':'D_new','reason':'whole_local_overlap'};point=c.point(m,self.db,'.',[4,0,0])
        recipe=self.plan/'native-member-source.json';m.atomic_json(recipe,{'sourceBinding':m.meta(self.db,'binding'),'statue':{**whole,'members':whole['physicalMembers']},
            'actualTarget':{'position':[4,0,0],'sourceBE':{'exists':False},'sourceState':{'registryId':'fixture:ordinary_target','properties':{}}},'targetStatueRecipeOrigins':[]})
        semantics=self.plan/'native-whole-semantics.json';m.atomic_json(semantics,{'status':'PASS_SOURCE_REVIEW_NOT_EXECUTION','sourceBinding':m.meta(self.db,'binding'),
            'statueUuid':'D_new','pose':whole['pose'],'runtimeMemberCells':whole['runtimeMemberCells'],'wholeModelAndMemberLifecyclePreserved':True,'itemPickDropGapUnresolved':True,'gameplayPass':False})
        history=self.plan/'strict-history.json';m.atomic_json(history,{'sourceBinding':m.meta(self.db,'binding'),'strictOriginalTargetMatches':[],
            'targetPosition':[4,0,0],'latestCommittedReceipt':m.meta(self.db,'nativeLocalRetention')})
        source=self.plan/'correction-source.json';m.atomic_json(source,{'status':'PASS_SOURCE_CORRECTION_ONLY','sourceBinding':m.meta(self.db,'binding'),
            'native':native,'retainedWhole':whole,'protectionRow':row,'pointSnapshot':point,'sourceRecipeProof':n.ref(m,recipe)})
        document={'schemaVersion':1,'status':'PASS_SOURCE_CORRECTION_ONLY','kind':'FINITE_NATIVE_VISUAL_PROTECTION_CORRECTION_V1','sourceBinding':m.meta(self.db,'binding'),
            'failedRun':n.ref(m,self.failure),'native':native,'retainedWhole':whole,'protectionRow':row,'pointSnapshot':point,'sourceProof':n.ref(m,source),
            'nativeWholeSemantics':n.ref(m,semantics),'historicalStrictOriginalCheck':n.ref(m,history),'ownerGraphTraversal':False,'applyAuthorization':False,'itemPickDropGapUnresolved':True,'gameplayPass':False}
        path=self.plan/'correction.json';m.atomic_json(path,document)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'correction-preview.json',execute=False,expected_preview_sha256=None)

    def execute(self,args):
        c.correct_visual_protection(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.correct_visual_protection(args,m)

    def test_exact_row_only_cursor_and_native_work_stay_unchanged(self):
        args=self.certificate();before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');retained=m.meta(self.db,'nativeRetainedComponents')
        physical=n.row_dict(self.db,'SELECT * FROM protected_cells WHERE x IN(2,3) ORDER BY x',())
        self.execute(args);n.validate_resume(m,self.db,self.plan);c.correct_visual_protection(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor);self.assertEqual(m.meta(self.db,'nativeRetainedComponents'),retained)
        self.assertIsNone(self.db.execute('SELECT 1 FROM protected_cells WHERE x=4').fetchone());self.assertEqual(n.row_dict(self.db,'SELECT * FROM protected_cells WHERE x IN(2,3) ORDER BY x',()),physical)
        self.assertEqual(self.old_receipt.read_bytes(),self.old_bytes)

    def test_root_physical_unknown_reason_and_extra_owner_reject(self):
        args=self.certificate();original=m.read(args.certificate)
        for mode in ('root','physical','unknown_reason','extra_owner'):
            document=copy.deepcopy(original)
            if mode in ('root','physical'):
                x=3 if mode=='root' else 2;document['protectionRow']['x']=x;document['pointSnapshot']['position']=[x,0,0]
            elif mode=='unknown_reason':document['protectionRow']['reason']='unclassified_protection'
            else:document['pointSnapshot']['protected_cells'].append({'dim':'.','x':4,'y':0,'z':0,'uuid':'unknown_owner','reason':'functional_guest'})
            source=m.read(document['sourceProof']['path'])
            for key in ('native','retainedWhole','protectionRow','pointSnapshot'):source[key]=document[key]
            path=self.plan/('bad-source-'+mode+'.json');m.atomic_json(path,source);document['sourceProof']=n.ref(m,path)
            with self.assertRaises(RuntimeError):c.visual_correction_proof(m,document)

    def test_protected_v12_original_whole_rejects(self):
        args=self.certificate();document=m.read(args.certificate);reference=m.meta(self.db,'classification');classification=c.bound(m,reference)
        classification['protectedRegistryIds'].append('fixture:D_new');path=self.plan/'classification-protected.json';m.atomic_json(path,classification);m.meta(self.db,'classification',n.ref(m,path));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'v12/protected'):c.check_correction_whole(m,self.db,document)

    def test_missing_own_physical_protection_or_queue_work_rejects(self):
        args=self.certificate();before=n.counts(self.db)
        self.db.execute("DELETE FROM protected_cells WHERE x=2 AND uuid='D_new'");self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',2,0,0,'other_original','whole_local_overlap'));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'protection is missing'):c.correct_visual_protection(args,m)
        self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',2,0,0,'D_new','whole_local_overlap'));self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',2,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'physical member has replacement work'):c.correct_visual_protection(args,m)
        self.assertEqual(n.counts(self.db)['selected_objects'],before['selected_objects'])

    def test_historical_strict_original_member_never_released(self):
        args=self.certificate();old=m.meta(self.db,'nativeLocalRetention');receipt=c.bound(m,old);preview=c.bound(m,receipt['preview'])
        preview['cases'][0]['retainedOwners'][0]['runtimeMemberCells'].append([4,0,0])
        path=self.plan/'strict-preview.json';m.atomic_json(path,preview);receipt.update(preview=n.ref(m,path),previousRetention=old)
        path=self.plan/'strict-receipt.json';m.atomic_json(path,receipt);m.meta(self.db,'nativeLocalRetention',n.ref(m,path));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'Historical strict original owner'):c.correction_strict_history(m,self.db,'.',[4,0,0],old)

    def test_receipt_before_commit_rollback_and_retry(self):
        args=self.certificate();c.correct_visual_protection(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);before=n.counts(self.db);original=m.atomic_json
        def crash(path,value):
            original(path,value)
            if Path(path).parent.name=='native-visual-protection-corrections':raise RuntimeError('new correction receipt before commit')
        with patch.object(m,'atomic_json',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.correct_visual_protection(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertIsNotNone(self.db.execute('SELECT 1 FROM protected_cells WHERE x=4').fetchone())
        c.correct_visual_protection(args,m);n.validate_resume(m,self.db,self.plan)

    def test_finalizing_and_final_apply_recheck_original_whole(self):
        args=self.certificate();self.execute(args);self.db.execute('UPDATE cell_queue SET processed=1');self.db.commit()
        self.db.execute('BEGIN');n.checkpoint(m,self.db,'FINALIZING');self.db.rollback()
        document=m.read(args.certificate);source=c.bound(m,c.bound(m,document['sourceProof'])['sourceRecipeProof'])['actualTarget']['sourceState']
        self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,NULL,?,0)',('.',4,0,0,0,0,'fixture_composition',m.canonical(source)))
        self.db.execute('UPDATE placements SET type_index=1');self.db.commit();c.validate_visual_protection_corrections(m,self.db,finalized=True)
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',2,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'physical member has replacement work'):c.validate_visual_protection_corrections(m,self.db,finalized=True)
        self.assertIsNone(self.db.execute('SELECT 1 FROM protected_cells WHERE x=4').fetchone())


if __name__=='__main__':unittest.main()
