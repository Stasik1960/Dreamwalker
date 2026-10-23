"""Low-memory finite component fixtures; no live plan/world is opened."""
import copy,hashlib,sqlite3,tempfile,unittest,zipfile
from pathlib import Path
import assembly_components as ac
m=ac.m

class ComponentFixtures(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='assembly-component-fixture-',dir=m.ROOT/'research');self.root=Path(self.temp.name);self.plan=self.root/'plan';self.plan.mkdir();self.old=self.root/'old';self.old.mkdir()
        old=sqlite3.connect(self.old/'plan.sqlite');old.executescript('CREATE TABLE stored_owners(dim TEXT,uuid TEXT,registry_id TEXT,x INT,y INT,z INT,PRIMARY KEY(dim,uuid));');old.close()
        self.db=sqlite3.connect('file:'+(self.plan/'plan.sqlite').as_posix(),uri=True);self.db.executescript(m.SCHEMA);self.db.execute('ATTACH DATABASE ? AS old',('file:'+(self.old/'plan.sqlite').as_posix()+'?mode=ro',))
        for key,value in {'oldPlan':str(self.old),'binding':{'fixture':'immutable'},'applyStarted':True}.items():m.meta(self.db,key,value)
        self.source=self.root/'source.java';self.source.write_text('finite existing matcher',encoding='utf-8');self.jar=self.root/'old.jar'
        with zipfile.ZipFile(self.jar,'w') as archive:archive.writestr('matcher.class',b'classproof');archive.writestr('recipe.json',b'recipeproof')
        self.algorithm={'source':ac.ref(self.source),'originalJar':ac.ref(self.jar),'classResource':'matcher.class','originalClassSha256':hashlib.sha256(b'classproof').hexdigest(),'recipeResource':'recipe.json','recipeSha256':hashlib.sha256(b'recipeproof').hexdigest()}
        self.manifest=self.root/'manifest.json';m.atomic_json(self.manifest,{'sourceBinding':m.meta(self.db,'binding'),'entries':[]})
        self.pose_preview=self.root/'pose-preview.json';m.atomic_json(self.pose_preview,{'status':'FINITE_PREVIEW_PASS','manifest':ac.ref(self.manifest),'sourceBinding':m.meta(self.db,'binding'),'poses':[],'separateFiniteComponentCoverageRequired':True})
        self.door=self.root/'door.json';record={'key':'lower','cells':[[0,0,0],[1,0,0]],'states':[{'open':False,'cells':[[0,0,0]]},{'open':True,'cells':[[1,0,0]]}]};m.atomic_json(self.door,{'poses':[record]})
        self.group={'groupKey':'g','assemblyKey':'recipe','dimension':'.','anchor':[10,64,0],'components':[]};self.raw_group={'groupKey':'g','assemblyKey':'recipe','dimension':'.','checksPassed':True,'members':[]};self.binding_group={'groupKey':'g','assemblyKey':'recipe','dimension':'.','decision':'local_original_existing_assembly','runtimePolicy':'local_original_functional','currentMainItem':{'registryId':'fixture:lower','installationPose':1},'hiddenPartRedirects':{'fixture:upper':'fixture:lower'},'components':[]}
        for key,kind,root in [('lower','preserve',[10,64,0]),('upper','static',[10,66,0])]:
            pose={'registryId':'fixture:'+key};encoded=m.canonical(pose);uid='uuid-'+key;self.db.execute('INSERT INTO poses(key,data) VALUES(?,?)',(key,encoded));self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.',uid,key,*root,kind,'keep-'+key,'stored'))
            old=sqlite3.connect(self.old/'plan.sqlite');old.execute('INSERT INTO stored_owners VALUES(?,?,?,?,?,?)',('.',uid,pose['registryId'],*root));old.commit();old.close()
            cells=[root,[11,64,0]] if key=='lower' else [root];footprint={'savedOwnMembershipCells':[root],'functionalBothStates':{'artifact':ac.ref(self.door),'pose':key,'record':record,'recordSha256':m.digest(record)} if key=='lower' else None,'protectedOwnCells':cells,'onlyOwnIdentity':True,'noForeignOwnerClosure':True}
            item={'uuid':uid,'pose':key,'root':root,'registryId':pose['registryId'],'poseDataSha256':ac.a.data_sha(encoded),'rawResidentSha256':'raw-'+key,'ownFootprint':footprint};self.binding_group['components'].append(item);self.raw_group['members'].append({**copy.deepcopy(item),'checks':{v:True for v in ac.CHECKS}});self.group['components'].append({'uuid':uid,'pose':key,'root':root,'component':{'registryId':pose['registryId']}})
        self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.','unmatched-same-pose','upper',1000,66,0,'static','unrelated-source','stored'))
        self.groups=self.root/'groups.jsonl';self.groups.write_text(m.canonical(self.group)+'\n',encoding='utf-8');self.coverage=self.root/'coverage.json';self.raw=self.root/'raw.json';self.bindings=self.root/'bindings.json';self.preview=self.root/'preview.json';self.publish()
    def tearDown(self):
        self.db.close();m.checked(self.root);self.assertTrue(self.root.resolve().is_relative_to(m.ROOT/'research'));self.temp.cleanup()
    def publish(self):
        m.atomic_json(self.coverage,{'sourceBinding':m.meta(self.db,'binding'),'manifest':ac.ref(self.manifest),'componentGroups':ac.ref(self.groups)})
        m.atomic_json(self.raw,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'manifest':ac.ref(self.manifest),'failures':[],'algorithm':self.algorithm,'groups':[self.raw_group],'actualOwnerCount':2,'actualGroupCount':1})
        self.doc={'schemaVersion':1,'status':'PASS','applyAuthorization':False,'sourceBinding':m.meta(self.db,'binding'),'manifest':ac.ref(self.manifest),'coverage':ac.ref(self.coverage),'rawTupleProof':ac.ref(self.raw),'matcherProof':{'status':'PASS','algorithm':self.algorithm,'rawTupleProof':ac.ref(self.raw)},'groups':[self.binding_group]};m.atomic_json(self.bindings,self.doc);self.db.commit()
    def promoted(self):
        self.db.execute("DELETE FROM meta WHERE key='applyStarted'");m.meta(self.db,'successfulPilotGate','passed-fixture');m.meta(self.db,'fullNativeFunctionGateRequired',True);self.pose_receipt=self.root/'pose-applied.json';m.atomic_json(self.pose_receipt,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'acceptedPreview':ac.ref(self.pose_preview),'matchedPoses':[],'matchedObjectsAfterSha256':hashlib.sha256().hexdigest()});m.meta(self.db,'preservedAssemblyOverrides',{'manifest':ac.ref(self.manifest),'receipt':ac.ref(self.pose_receipt)});self.db.commit()
    def test_preview_has_no_main_database_writes_and_no_assembly_success_claim(self):
        before=m.sha(self.plan/'plan.sqlite');saved=ac.preview(self.plan,self.bindings,self.pose_preview,self.preview);self.assertEqual(saved['status'],'FINITE_COMPONENT_PREVIEW_PASS');self.assertEqual(m.sha(self.plan/'plan.sqlite'),before);self.assertEqual(saved['exactOwnerCount'],2);self.assertEqual(saved['runtimeAssemblyOutcomes'],'PENDING_ACTUAL_FULL_LOAD');self.assertEqual(saved['requiredActualRuntimeOutcomes'][0]['expectedInstallationPose'],1)
    def test_active_applied_pilot_is_refused(self):
        ac.preview(self.plan,self.bindings,self.pose_preview,self.preview)
        with self.assertRaisesRegex(RuntimeError,'after successful-pilot promotion'):ac.apply(self.plan,self.bindings,self.preview,m.sha(self.preview),self.root/'apply.json')
        self.assertEqual(self.db.execute("SELECT kind FROM objects WHERE uuid='uuid-upper'").fetchone()[0],'static')
    def test_only_two_uuid_kinds_change_both_state_cells_protected_and_resume(self):
        ac.preview(self.plan,self.bindings,self.pose_preview,self.preview);self.promoted();out=self.root/'apply.json';saved=ac.apply(self.plan,self.bindings,self.preview,m.sha(self.preview),out);self.assertEqual(saved['status'],'PASS');self.assertEqual(self.db.execute("SELECT kind,reason FROM objects WHERE uuid='uuid-lower'").fetchone(),('preserve','keep-lower'));self.assertEqual(self.db.execute("SELECT kind FROM objects WHERE uuid='uuid-upper'").fetchone()[0],'preserve');self.assertEqual(self.db.execute("SELECT kind,reason FROM objects WHERE uuid='unmatched-same-pose'").fetchone(),('static','unrelated-source'));self.assertEqual(self.db.execute('SELECT count(*) FROM protected_cells').fetchone()[0],3);self.assertEqual(ac.apply(self.plan,self.bindings,self.preview,m.sha(self.preview),out),saved);ac.require_bound(self.db)
    def test_failed_raw_match_and_missing_open_state_refused(self):
        self.raw_group['members'][0]['checks']['noPendingTypeMigration']=False;self.publish()
        with self.assertRaisesRegex(RuntimeError,'precondition'):ac.preview(self.plan,self.bindings,self.pose_preview,self.preview)
        self.raw_group['members'][0]['checks']['noPendingTypeMigration']=True;record=self.binding_group['components'][0]['ownFootprint']['functionalBothStates']['record'];record['states']=record['states'][:1];self.raw_group['members'][0]['ownFootprint']=copy.deepcopy(self.binding_group['components'][0]['ownFootprint']);m.atomic_json(self.door,{'poses':[record]});both=self.binding_group['components'][0]['ownFootprint']['functionalBothStates'];both['artifact']=ac.ref(self.door);both['recordSha256']=m.digest(record);self.raw_group['members'][0]['ownFootprint']=copy.deepcopy(self.binding_group['components'][0]['ownFootprint']);self.publish()
        with self.assertRaisesRegex(RuntimeError,'open and closed'):ac.preview(self.plan,self.bindings,self.pose_preview,self.preview)
    def test_independent_native_fragment_or_cleanup_is_not_whole_preservation(self):
        self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',10,66,0,'uuid-upper','upper',0,0,0));self.db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.','uuid-upper','static'));self.db.commit();saved=ac.preview(self.plan,self.bindings,self.pose_preview,self.preview);self.assertEqual(saved['status'],'NEEDS_BOUNDED_PILOT_CORRECTION');self.assertEqual({v['kind'] for v in saved['pilotIntersections']},{'fragments','cleanup_owners'})
    def test_other_resident_or_extra_group_cannot_reuse_raw_match(self):
        self.binding_group['components'][1]['uuid']='unmatched-same-pose';self.publish()
        with self.assertRaisesRegex(RuntimeError,'identities disagree'):ac.preview(self.plan,self.bindings,self.pose_preview,self.preview)
    def test_postcompose_plain_overwrite_rejected_exact_bridge_allowed(self):
        ac.preview(self.plan,self.bindings,self.pose_preview,self.preview);self.promoted();ac.apply(self.plan,self.bindings,self.preview,m.sha(self.preview),self.root/'apply.json');self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',10,64,0,0,0,'shared-background',42,'{}',0))
        with self.assertRaisesRegex(RuntimeError,'independent native geometry'):ac.require_bound(self.db,compiled=True)
        self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',10,64,0,'{}',1,42));ac.require_bound(self.db,compiled=True)
    def test_pose_preview_cannot_replace_distinct_raw_component_gate(self):
        ac.preview(self.plan,self.bindings,self.pose_preview,self.preview);self.promoted()
        with self.assertRaisesRegex(RuntimeError,'distinct finite component'):ac.a.require_bound(self.db)
        ac.apply(self.plan,self.bindings,self.preview,m.sha(self.preview),self.root/'apply.json');ac.a.require_bound(self.db)

if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ComponentFixtures));m.atomic_json(m.ROOT/'research/assembly-component-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[e for _,e in result.errors],'failures':[e for _,e in result.failures],'activePlanWrites':0,'worldFilesRead':0,'mapWrites':0});raise SystemExit(0 if result.wasSuccessful() else 1)
