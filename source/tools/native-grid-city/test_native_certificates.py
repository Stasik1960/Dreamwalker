"""Targeted source-lineage and generic-certificate fixtures; temporary DB only."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import migrate as m
import native_resume as n
import native_certificates as c


class CertificateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='native-certificate-fixture-');self.plan=Path(self.temp.name)
        self.db=sqlite3.connect(self.plan/'plan.sqlite');self.db.executescript(m.SCHEMA)
        m.meta(self.db,'binding',{'fixture':'immutable-original'});m.meta(self.db,'successfulPilotGate',{'fixture':True});m.meta(self.db,'fullNativeFunctionGateRequired',True)
        classification=self.plan/'classification.json';m.atomic_json(classification,{'schemaVersion':1,'protectedRegistryIds':['fixture:protected_v12'],'poses':[{'registryId':'fixture:functional','variant':0,'route':'preserve'}]})
        m.meta(self.db,'classification',n.ref(m,classification))
        for x,uid in ((0,'old_C'),(1,'old_D')):self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',x,0,0,uid,'preserved-original-member'))
        self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','previous-native'))
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',9,0,0))
        self.native=self.seed('C_new',2,'substrate',(2,),selected=False)
        self.owner=self.seed('D_new',3,'static',(2,3,4));self.seed('E_independent',5,'static',(4,5))
        self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',4,0,0,'original-shared-host',1,None))
        archive=self.plan/'failed.inputs';archive.mkdir();rows={}
        for filename,payload in (('migrate.py',b'old actual main'),('native_resume.py',b'old actual native'),('context_stage.py',Path(m.__file__).with_name('context_stage.py').read_bytes())):
            path=archive/filename;path.write_bytes(payload);rows[filename]={'archive':str(path),'sha256':m.sha(path)}
        self.old_files={'migrate':rows['migrate.py']['sha256'],'nativeResume':rows['native_resume.py']['sha256']}
        log=self.plan/'failed.log';log.write_text('Required native visual overhang meets a protected local object C_new',encoding='utf-8')
        self.failure=self.plan/'failed.json';m.atomic_json(self.failure,{'status':'FAIL','exitCode':1,'args':['--plan',str(self.plan),'resume-native-compose'],'inputSources':rows,'log':str(log)})
        old_native={'dimension':'.','uuid':'old_C','pose':'old_pose','root':[0,0,0],'mustRemainOriginalCells':[[0,0,0]]}
        old_case={'retainedNative':old_native,'retainedOwners':[{'dimension':'.','uuid':'old_D','runtimeMemberCells':[[1,0,0]]}]}
        scope=self.plan/'old-scope.json';m.atomic_json(scope,{'fixture':'old-reviewed-scope'})
        preview=self.plan/'old-preview.json';m.atomic_json(preview,{'sourceBinding':m.meta(self.db,'binding'),'cases':[old_case]})
        self.old_receipt=self.plan/'old-receipt.json'
        receipt={'status':'PASS','kind':'EXPLICIT_FINITE_NATIVE_RETENTION','sourceBinding':m.meta(self.db,'binding'),'semanticBindings':n.snapshot_bindings(m,self.db),
                 'sourceFiles':self.old_files,'scope':n.ref(m,scope),'preview':n.ref(m,preview),'previousRetention':None,
                 'evidence':{'failedSource':rows['migrate.py'],'failedRun':n.ref(m,self.failure)}}
        m.atomic_json(self.old_receipt,receipt);self.old_bytes=self.old_receipt.read_bytes();parent=n.ref(m,self.old_receipt)
        m.meta(self.db,'nativeLocalRetention',parent);m.meta(self.db,'nativeRetainedComponents',[old_native])
        m.meta(self.db,'nativeComposeCursor',{'phase':'NATIVE_TRANSFER','retention':parent,'batchesCommitted':5,'rowsProcessedSinceAdmission':5120});self.db.commit()
        proof=self.plan/'source-proof.json';m.atomic_json(proof,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':c.source_files(m),'oldSourceFiles':self.old_files})
        self.upgrade_args=SimpleNamespace(plan=self.plan,failed_run=self.failure,source_proof=proof,source_proof_sha256=m.sha(proof),preview=self.plan/'upgrade-preview.json',execute=False,expected_preview_sha256=None)

    def seed(self,uid,x,kind,positions,selected=True):
        data=m.canonical({'registryId':'fixture:'+uid});pose='sp_'+m.hashlib.sha256(data.encode()).hexdigest()[:24]
        self.db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,data))
        obj={'dim':'.','uuid':uid,'pose':pose,'x':x,'y':0,'z':0,'kind':kind,'reason':'original','source_status':'stored'}
        self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()))
        selected_row={key:obj[key] for key in ('dim','uuid','pose','x','y','z','kind')}
        if selected:self.db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(selected_row.values()))
        fragments=[]
        if selected:
            for at in positions:
                fragments.append({'x':at,'y':0,'z':0,'pose':pose,'dx':at-x,'dy':0,'dz':0})
                self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',at,0,0,uid,pose,at-x,0,0))
                self.db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,0)',('.',at,0,0))
        return {'uuid':uid,'pose':pose,'poseDataSha256':m.hashlib.sha256(data.encode()).hexdigest(),'root':[x,0,0],
                'object':obj,'selected':selected_row,'fragments':fragments,'runtimeMemberCells':[[x,0,0]]}

    def tearDown(self):self.db.close();self.temp.cleanup()

    def install_upgrade(self):
        c.upgrade(self.upgrade_args,m);self.upgrade_args.execute=True;self.upgrade_args.expected_preview_sha256=m.sha(self.upgrade_args.preview)
        c.upgrade(self.upgrade_args,m)

    def certificate(self,foreign_member=False,processed=False,projection=None,existing_guest_be=False):
        native={key:self.native[key] for key in ('uuid','pose','poseDataSha256','root','runtimeMemberCells')};native.update(dimension='.',actualRootState=m.AIR,recipeIndex=1)
        owner=copy.deepcopy(self.owner)
        if foreign_member:owner['runtimeMemberCells'].append([4,0,0])
        if processed:self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=3');self.db.commit()
        owner['pointDemands']=[c.point(m,self.db,'.',[x,0,0]) for x in (2,3,4)]
        coverage=None
        if projection:
            coverage={'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True}
            at=[3,0,0] if projection=='root_existing_be_background' else [4,0,0]
            if at not in owner['runtimeMemberCells']:owner['runtimeMemberCells'].append(at)
            policy={'position':at,'policy':projection,'sourceState':{'registryId':'fixture:normal','properties':{}},
                    'storedContributions':[{'uuid':owner['uuid']}],'pointDemand':next(p for p in owner['pointDemands'] if p['position']==at)}
            if projection=='nonroot_guest_overlay':
                policy.update(isRoot=False,hasStoredDContribution=True,rootUnchanged=True)
                if existing_guest_be:
                    policy['sourceState']={'registryId':'bloodborne_dw:composite_cell','properties':{}}
                    policy['existingBeBridge']={'hostState':policy['sourceState'],'retainedResident':0,'existingCompositeBE':True}
            else:policy['rootProof']={'existingCompositeBE':True,'residentUuid':owner['uuid'],'privateOuterFields':[],'preserveOriginalRegistry':True,'nonV12':True}
            owner['memberPolicies']=[policy if point==at else {'position':point,'policy':'original','sourceState':m.AIR,'storedContributions':[{'uuid':owner['uuid']}],
                                    'pointDemand':next(p for p in owner['pointDemands'] if p['position']==point)} for point in owner['runtimeMemberCells']]
        source=self.plan/'physical-source-proof.json';m.atomic_json(source,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'nativeComponents':[native],'owners':[copy.deepcopy(owner)],'finalCoverageRequired':coverage})
        native['sourceProof']=n.ref(m,source);owner['sourceGeometryProof']=n.ref(m,source);owner['classProof']=n.ref(m,source)
        certificate={'schemaVersion':1,'status':'PASS','kind':'FINITE_NATIVE_RETENTION_CERTIFICATE_V1','sourceBinding':m.meta(self.db,'binding'),
                     'failedRun':n.ref(m,self.failure),'ownerGraphTraversal':False,'applyAuthorization':False,
                     'cases':[{'native':native,'owners':[owner],'nativePointDemands':[c.point(m,self.db,'.',[2,0,0])],
                               'classification':'FINITE_PENDING_ORIGINAL_RETENTION','conflicts':[]}]}
        path=self.plan/'certificate.json';m.atomic_json(path,certificate)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'certificate-preview.json',execute=False,expected_preview_sha256=None)

    def test_source_upgrade_metadata_only_accepts_old_code_and_rejects_unbound_code(self):
        before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');self.install_upgrade()
        self.assertEqual(n.counts(self.db),before);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor)
        self.assertEqual(self.old_receipt.read_bytes(),self.old_bytes)
        c.require_source_files(m,self.db,self.old_files)
        with self.assertRaisesRegex(RuntimeError,'outside the reviewed'):c.require_source_files(m,self.db,{'migrate':'forged','nativeResume':'forged'})

    def test_old_failure_tuple_and_upgrade_parent_remain_valid_after_generic_append(self):
        self.install_upgrade();args=self.certificate();c.retain(args,m)
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retain(args,m)
        c.active_binding(m,self.db);n.validate_resume(m,self.db,self.plan)
        self.assertEqual(self.old_receipt.read_bytes(),self.old_bytes)
        self.assertEqual(self.db.execute("SELECT uuid FROM selected_objects ORDER BY uuid").fetchall(),[('E_independent',)])
        self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(4,0),(5,0),(9,1)])
        self.assertEqual(self.db.execute('SELECT x FROM bridge_cells').fetchall(),[(4,)])
        self.assertEqual(m.meta(self.db,'nativeComposeCursor')['batchesCommitted'],5)

    def test_independent_physical_member_is_declined_without_graph_expansion(self):
        self.install_upgrade();args=self.certificate(foreign_member=True);before=n.counts(self.db)
        with self.assertRaisesRegex(RuntimeError,'Independent demand'):c.retain(args,m)
        self.assertEqual(n.counts(self.db),before)
        self.assertEqual(self.db.execute('SELECT count(*) FROM selected_objects').fetchone(),(2,))

    def test_processed_queue_cannot_be_admitted_or_reset(self):
        self.install_upgrade();args=self.certificate(processed=True)
        with self.assertRaisesRegex(RuntimeError,'Processed native queue'):c.retain(args,m)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=3').fetchone(),(1,))

    def test_nonroot_guest_preserves_processed_independent_background_and_original_root(self):
        self.install_upgrade();self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=4');self.db.commit()
        args=self.certificate(projection='nonroot_guest_overlay');c.retain(args,m)
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retain(args,m)
        n.validate_resume(m,self.db,self.plan)
        self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(4,1),(5,0),(9,1)])
        self.assertEqual(self.db.execute("SELECT x FROM protected_cells WHERE uuid='D_new'").fetchall(),[(3,)])
        self.assertEqual(self.db.execute("SELECT kind FROM objects WHERE uuid='D_new'").fetchone(),('static',))

    def test_existing_root_be_bridge_is_derived_without_resetting_processed_work(self):
        self.install_upgrade();self.seed('F_root_background',6,'static',(3,6));self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=3');self.db.commit()
        args=self.certificate(projection='root_existing_be_background');c.retain(args,m)
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retain(args,m)
        n.validate_resume(m,self.db,self.plan)
        self.assertEqual(self.db.execute('SELECT host_state,retained_resident FROM bridge_cells WHERE x=3').fetchone(),(m.canonical({'registryId':'fixture:normal','properties':{}}),1))
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=3').fetchone(),(1,))
        self.assertEqual(self.db.execute("SELECT count(*) FROM selected_objects WHERE uuid='F_root_background'").fetchone(),(1,))

    def test_existing_nonroot_helper_bridge_is_rebound_before_processed_queue_resume(self):
        self.install_upgrade();self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=4');self.db.commit()
        args=self.certificate(projection='nonroot_guest_overlay',existing_guest_be=True);c.retain(args,m)
        args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retain(args,m)
        n.validate_resume(m,self.db,self.plan)
        self.assertEqual(self.db.execute('SELECT host_state,retained_resident FROM bridge_cells WHERE x=4').fetchone(),(m.canonical({'registryId':'bloodborne_dw:composite_cell','properties':{}}),0))
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=4').fetchone(),(1,))
        self.db.execute('DELETE FROM bridge_cells WHERE x=4')
        with self.assertRaisesRegex(RuntimeError,'existing CompositeBE carrier'):n.validate_resume(m,self.db,self.plan)

    def test_unproved_policy_edit_does_not_override_bound_source_proof(self):
        self.install_upgrade();args=self.certificate(projection='nonroot_guest_overlay');doc=m.read(args.certificate)
        doc['cases'][0]['owners'][0]['memberPolicies'][-1]['existingBeBridge']={'hostState':m.AIR,'retainedResident':0,'existingCompositeBE':True}
        m.atomic_json(args.certificate,doc);args.certificate_sha256=m.sha(args.certificate)
        with self.assertRaisesRegex(RuntimeError,'footprint/policies'):c.retain(args,m)

    def test_projected_v12_unknown_carrier_and_private_root_are_rejected(self):
        args=self.certificate(projection='nonroot_guest_overlay');doc=m.read(args.certificate);owner=doc['cases'][0]['owners'][0]
        for state in ('fixture:protected_v12','fixture:functional','bloodborne_dw:composite_cell'):
            changed=copy.deepcopy(owner);changed['memberPolicies'][-1]['sourceState']['registryId']=state
            with self.subTest(state=state),self.assertRaises(RuntimeError):c.member_policies(m,changed,m.read(m.meta(self.db,'classification')['path']))
        changed=copy.deepcopy(owner);policy=changed['memberPolicies'][-1];policy['policy']='root_existing_be_background';policy['position']=owner['root'];policy['pointDemand']=owner['pointDemands'][1]
        policy['rootProof']={'existingCompositeBE':True,'residentUuid':owner['uuid'],'privateOuterFields':['inventory'],'preserveOriginalRegistry':True,'nonV12':True}
        changed['memberPolicies']=[policy]
        changed['runtimeMemberCells']=[owner['root']]
        with self.assertRaisesRegex(RuntimeError,'resident/payload'):c.member_policies(m,changed,m.read(m.meta(self.db,'classification')['path']))

    def test_lineage_stays_code_bound_but_native_semantic_gate_remains_strict(self):
        self.install_upgrade();m.meta(self.db,'fullNativeFunctionGate',{'changed':'not-admitted-native-phase'});self.db.commit()
        c.active_binding(m,self.db)
        with self.assertRaisesRegex(RuntimeError,'input bindings changed'):n.validate_resume(m,self.db,self.plan)


if __name__=='__main__':unittest.main()
