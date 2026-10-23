"""New native-retraction boundaries only, using temporary databases."""
import copy
import sqlite3
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c
from test_native_certificates import CertificateTests


class RetractionTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown
    install_upgrade=CertificateTests.install_upgrade

    def setUp(self):
        CertificateTests.setUp(self)
        exports=self.plan/'exports.sqlite';db=sqlite3.connect(exports);db.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');db.commit();db.close()
        m.meta(self.db,'poseFragments',n.ref(m,exports));old=m.read(self.old_receipt);old['semanticBindings']=n.snapshot_bindings(m,self.db);m.atomic_json(self.old_receipt,old)
        self.old_bytes=self.old_receipt.read_bytes();reference=n.ref(m,self.old_receipt);m.meta(self.db,'nativeLocalRetention',reference)
        cursor=m.meta(self.db,'nativeComposeCursor');m.meta(self.db,'nativeComposeCursor',{**cursor,'retention':reference});self.db.commit()
        self.catalog_patch=patch.object(m,'legacy_native_catalog',return_value=({'recipes':[{'fragments':[{'origin':[5,0,0],'pose':'legacy_B'}]}]},{}));self.catalog_patch.start();self.addCleanup(self.catalog_patch.stop)

    def certificate(self,foreign_empty_slot=False,foreign_strict=False):
        self.install_upgrade();a=self.seed('A_new',7,'substrate',(),selected=False);a['runtimeMemberCells']=[[7,0,0],[8,0,0]]
        self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','A_new'));substrate=[]
        for x in (2,3,7,8):
            row={'dim':'.','x':x,'y':0,'z':0,'uuid':'A_new','pose':a['pose'],'dx':x-7,'dy':0,'dz':0};substrate.append(row)
            self.db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',tuple(row.values()));self.db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(a['pose'],x-7,0,0))
            self.db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,0)',('.',x,0,0))
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE x IN(3,4,7)')
        if foreign_empty_slot:
            uid=m.synthetic_uuid('.',(11,0,0),'legacy_B');self.seed(uid,11,'substrate',(),selected=False);self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.',uid))
            self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',6,0,0,'D_new',self.owner['pose'],3,0,0));self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',6,0,0))
        if foreign_strict:
            self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',8,0,0,'E_independent',self.owner['pose'],0,0,0))
        self.db.commit();positions=(2,3,4,6,7,8) if foreign_empty_slot else (2,3,4,7,8)
        snapshots=[c.point(m,self.db,'.',[x,0,0]) for x in positions]
        owner=copy.deepcopy(self.owner);owner['fragments']=n.row_dict(self.db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',('.','D_new'))
        owner['runtimeMemberCells']=[[3,0,0],[4,0,0]];owner['pointDemands']=[p for p in snapshots if p['position'][0] in {row['x'] for row in owner['fragments']}]
        owner['memberPolicies']=[{'position':[3,0,0],'policy':'original','sourceState':m.AIR,'storedContributions':[{'uuid':'D_new'}],'pointDemand':next(p for p in snapshots if p['position'][0]==3)},
            {'position':[4,0,0],'policy':'nonroot_guest_overlay','sourceState':{'registryId':'bloodborne_dw:composite_cell','properties':{}},'storedContributions':[{'uuid':'D_new'}],
             'pointDemand':next(p for p in snapshots if p['position'][0]==4),'isRoot':False,'hasStoredDContribution':True,'rootUnchanged':True,
             'existingBeBridge':{'hostState':{'registryId':'bloodborne_dw:composite_cell','properties':{}},'retainedResident':0,'existingCompositeBE':True}}]
        native={key:self.native[key] for key in ('uuid','pose','poseDataSha256','root','runtimeMemberCells')};native.update(dimension='.',actualRootState=m.AIR,recipeIndex=1)
        anative={key:a[key] for key in ('uuid','pose','poseDataSha256','root','runtimeMemberCells')};anative.update(dimension='.',actualRootState=m.AIR,recipeIndex=2)
        activated={'dim':'.','uuid':'A_new'};proof=self.plan/'retraction-runtime-proof.json'
        m.atomic_json(proof,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'nativeComponents':[native,{**anative,'activatedRow':activated,'substrateFragments':substrate}],
                            'owners':[copy.deepcopy(owner)],'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True}})
        native['sourceProof']=n.ref(m,proof);anative['sourceProof']=n.ref(m,proof);owner['sourceGeometryProof']=n.ref(m,proof);owner['classProof']=n.ref(m,proof)
        guard_rows=[]
        for x in (3,7,6) if foreign_empty_slot else (3,7):
            state={'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':'0'}} if x==6 else {'registryId':'fixture:source','properties':{}}
            roots=[{'uuid':uid,'pose':pose,'root':[rx,ry,rz]} for uid,pose,rx,ry,rz in m.native_roots_at(self.db,'.',(x,0,0),state,None)]
            foreign=[{'dim':'.','uuid':row['uuid']} for row in roots if row['uuid']!='A_new' and self.db.execute('SELECT 1 FROM activated_native WHERE uuid=?',(row['uuid'],)).fetchone()]
            guard_rows.append({'dimension':'.','position':[x,0,0],'sourceState':state,'nativeRoots':roots,'foreignActivated':foreign})
        guard=self.plan/'retirement-native-guard.json';m.atomic_json(guard,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'points':guard_rows})
        document={'schemaVersion':1,'status':'PASS','kind':'FINITE_NATIVE_RETRACTION_CERTIFICATE_V1','sourceBinding':m.meta(self.db,'binding'),'failedRun':n.ref(m,self.failure),
                  'ownerGraphTraversal':False,'applyAuthorization':False,'native':native,'owners':[owner],
                  'retractedNatives':[{'native':anative,'activatedRow':activated,'substrateFragments':substrate,'pointDemands':[p for p in snapshots if p['position'][0] in(2,3,7,8)],'sourceProof':n.ref(m,proof)}],
                  'nativePointDemands':[p for p in snapshots if p['position'][0]==2],'pointDemands':snapshots,'retirementNativeGuard':n.ref(m,guard)}
        path=self.plan/'retraction.json';m.atomic_json(path,document)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'retraction-preview.json',execute=False,expected_preview_sha256=None)

    def test_exact_completed_retraction_preserves_foreign_background_and_cursor(self):
        args=self.certificate();before=m.meta(self.db,'nativeComposeCursor');c.retract(args,m)
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retract(args,m);n.validate_resume(m,self.db,self.plan);c.retract(args,m)
        self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(4,1),(5,0),(9,1)])
        self.assertEqual(self.db.execute('SELECT uuid FROM activated_native').fetchall(),[('previous-native',)])
        self.assertEqual(self.db.execute('SELECT count(*) FROM substrate_fragments').fetchone(),(0,))
        receipt=c.bound(m,m.meta(self.db,'nativeLocalRetention'));self.assertEqual(len(receipt['retiredCompletedQueueRows']),2);self.assertEqual(len(receipt['retiredPendingQueueRows']),2)
        self.assertEqual(m.meta(self.db,'nativeComposeCursor')['batchesCommitted'],before['batchesCommitted']);self.assertEqual(self.old_receipt.read_bytes(),self.old_bytes)

    def test_foreign_empty_native_helper_slot_keeps_completed_suppression_work(self):
        args=self.certificate(foreign_empty_slot=True);c.retract(args,m);preview=m.read(args.preview)
        row=next(p for p in preview['pointPlan'] if p['position']==[6,0,0]);self.assertFalse(row['pruneQueueAndBridge']);self.assertTrue(row['remainingDemands']['implicitNativeActivations'])
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retract(args,m)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=6').fetchone(),(1,))

    def test_foreign_demand_on_strict_member_rejects_without_partial_retraction(self):
        args=self.certificate(foreign_strict=True);before=n.counts(self.db)
        with self.assertRaisesRegex(RuntimeError,'Independent demand'):c.retract(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertEqual(self.db.execute("SELECT count(*) FROM activated_native WHERE uuid='A_new'").fetchone(),(1,))

    def test_receipt_before_commit_crash_rolls_back_and_retries_identically(self):
        args=self.certificate();c.retract(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);before=n.counts(self.db);original=m.atomic_json
        def crash(path,value):
            original(path,value)
            if Path(path).parent.name=='native-retention-receipts':raise RuntimeError('fixture interruption after receipt before commit')
        with patch.object(m,'atomic_json',side_effect=crash),self.assertRaisesRegex(RuntimeError,'fixture interruption'):c.retract(args,m)
        self.assertEqual(n.counts(self.db),before);c.retract(args,m);n.validate_resume(m,self.db,self.plan)

    def test_second_source_upgrade_keeps_tuple2_and_tuple3_ancestors_immutable(self):
        directory=self.plan/'prior-reviewed-source';directory.mkdir();archives={};old3={}
        for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py')):
            path=directory/filename;path.write_text('previous reviewed '+name,encoding='utf-8');archives[filename]={'archive':str(path),'sha256':m.sha(path)};old3[name]=m.sha(path)
        context=Path(m.__file__).with_name('context_stage.py');archives['context_stage.py']={'archive':str(context),'sha256':m.sha(context)}
        prior_proof=directory/'proof.json';m.atomic_json(prior_proof,{'sourceFiles':old3,'oldSourceFiles':self.old_files})
        first=directory/'transition.json';m.atomic_json(first,{'status':'PASS','kind':'NATIVE_CONSUMER_SOURCE_ONLY_TRANSITION','sourceBinding':m.meta(self.db,'binding'),
            'fromSourceFiles':self.old_files,'toSourceFiles':old3,'parentRetention':m.meta(self.db,'nativeLocalRetention'),
            'archivedSources':{key:{'path':value['archive'],'sha256':value['sha256']} for key,value in archives.items()},'sourceProof':n.ref(m,prior_proof),'failedRun':n.ref(m,self.failure)})
        immutable=first.read_bytes();old_head=m.meta(self.db,'nativeLocalRetention');head=m.read(self.old_receipt)
        preview=directory/'head-preview.json';m.atomic_json(preview,{'sourceBinding':m.meta(self.db,'binding'),'cases':[]})
        head.update(sourceFiles=old3,previousRetention=old_head,preview=n.ref(m,preview));head_path=directory/'head.json';m.atomic_json(head_path,head)
        m.meta(self.db,'nativeLocalRetention',n.ref(m,head_path));cursor=m.meta(self.db,'nativeComposeCursor');m.meta(self.db,'nativeComposeCursor',{**cursor,'retention':n.ref(m,head_path)})
        m.meta(self.db,'nativeResumeSourceBinding',{'sourceFiles':old3,'transition':n.ref(m,first)});m.meta(self.db,'nativeResumeSourceTransition',n.ref(m,first));self.db.commit()
        failure=directory/'failed.json';m.atomic_json(failure,{'status':'FAIL','exitCode':1,'args':['--plan',str(self.plan),'resume-native-compose'],'inputSources':archives,'log':m.read(self.failure)['log']})
        source=directory/'new-proof.json';m.atomic_json(source,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':old3})
        args=SimpleNamespace(plan=self.plan,failed_run=failure,source_proof=source,source_proof_sha256=m.sha(source),preview=directory/'upgrade-preview.json',execute=False,expected_preview_sha256=None)
        before=n.counts(self.db);c.upgrade(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.upgrade(args,m)
        self.assertEqual(first.read_bytes(),immutable);self.assertEqual(n.counts(self.db),before)
        c.require_source_files(m,self.db,self.old_files);c.require_source_files(m,self.db,old3);c.require_source_files(m,self.db,c.source_files(m));n.validate_resume(m,self.db,self.plan)


if __name__=='__main__':unittest.main()
