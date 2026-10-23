"""Small file SQLite fixtures only; never opens the live migration or a world."""
import copy,sqlite3,tempfile,unittest
from pathlib import Path
import assembly_overrides as a
m=a.m
class AssemblyFixtures(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='assembly-override-fixture-',dir=m.ROOT/'research');self.root=Path(self.temp.name);self.plan=self.root/'plan';self.plan.mkdir();self.old=self.root/'old';self.old.mkdir();db=sqlite3.connect(self.old/'plan.sqlite');db.executescript('CREATE TABLE stored_owners(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid));CREATE TABLE be_owners(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid));CREATE TABLE members(dim TEXT,x INT,y INT,z INT,uuid TEXT);CREATE TABLE stored_contributions(dim TEXT,x INT,y INT,z INT,uuid TEXT);');db.commit();db.close()
        self.db=sqlite3.connect(self.plan/'plan.sqlite');self.db.executescript(m.SCHEMA);self.db.execute('PRAGMA cache_size=-1024')
        for key,value in {'oldPlan':str(self.old),'binding':{'fixture':'exactimmutable'},'classification':{'retained':'private-payload-decisions'},'wholeTemplates':{},'applyStarted':True}.items():m.meta(self.db,key,value)
        proof=self.root/'decision.json';m.atomic_json(proof,{'old':'explicituserdecision'});self.doc={'schemaVersion':1,'status':'CONFIRMED_FINITE_ASSEMBLY_MANIFEST','sourceBinding':m.meta(self.db,'binding'),'entries':[],'poseMatches':[]};self.proof={'path':str(proof),'sha256':m.sha(proof),'record':'exactpose'};self.manifest=self.root/'manifest.json';self.preview=self.root/'preview.json'
    def tearDown(self):
        self.db.close();m.checked(self.root);self.assertTrue(self.root.resolve().is_relative_to(m.ROOT/'research'));self.temp.cleanup()
    def add(self,pose,uid,kind='static',policy='compiled_whole_or_local_original',root=0,footprint=True):
        encoded=m.canonical({'registryId':'fixture:'+pose,'properties':{'variant':'1'},'shift':[0,0,0],'mountY':0});self.db.execute('INSERT INTO poses(key,data) VALUES(?,?)',(pose,encoded));self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.',uid,pose,root,64,0,kind,'existing-'+kind,'private_payload' if kind=='preserve' else 'stored'))
        if footprint:self.db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(pose,0,0,0));self.db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(pose,1,0,0))
        if kind=='static':
            old=sqlite3.connect(self.old/'plan.sqlite');old.execute('INSERT INTO stored_owners VALUES(?,?)',('.',uid));old.commit();old.close()
        entry={'key':'asm-'+pose,'matchScope':'exact_pose','currentMainItem':{'registryId':'fixture:'+pose},'recipe':{'components':[{'id':'piece','offset':[0,0,0]}]},'decisionSources':[self.proof],'preservation':{'route':'whole','runtimePolicy':policy,'requireOriginalRegistryId':policy=='local_original_v12'}};self.doc['entries'].append(entry);self.doc['poseMatches'].append({'assemblyKey':entry['key'],'pose':pose,'poseDataSha256':a.data_sha(encoded),'matchEvidence':{'actual':'exact'}});return entry
    def publish(self):self.db.commit();m.atomic_json(self.manifest,self.doc);return a.preview(self.plan,self.manifest,self.preview)
    def promoted(self):
        self.db.execute("DELETE FROM meta WHERE key='applyStarted'");m.meta(self.db,'successfulPilotGate','synthetic-passed-gate');m.meta(self.db,'fullNativeFunctionGateRequired',True);self.db.commit()
    def test_preview_is_read_only_and_preserves_existing_priorities(self):
        self.add('real','r');self.add('native','n','substrate',root=4);self.add('data','d','preserve',root=8);self.add('function','f',policy='local_original_functional',root=12);self.add('missing','m',root=16,footprint=False);self.db.commit();before=m.sha(self.plan/'plan.sqlite');report=self.publish();self.assertEqual(report['status'],'FINITE_PREVIEW_PASS');self.assertEqual(report['matchedObjects'],5);self.assertEqual(m.sha(self.plan/'plan.sqlite'),before);self.assertEqual(report['wholeTemplateRequests']['count'],1);self.assertEqual(self.db.execute("SELECT kind FROM objects WHERE uuid='n'").fetchone()[0],'substrate')
    def test_active_applied_pilot_is_refused_before_any_persistent_change(self):
        self.add('real','r');self.publish();before=list(self.db.execute('SELECT * FROM objects'))
        with self.assertRaisesRegex(RuntimeError,'after successful-pilot promotion'):a.apply(self.plan,self.manifest,self.preview,m.sha(self.preview),self.root/'applied.json')
        self.assertEqual(list(self.db.execute('SELECT * FROM objects')),before)
    def test_only_matched_objects_change_and_native_footprint_is_finite(self):
        self.add('real','r');self.add('native','n','substrate',root=4);self.add('data','d','preserve',root=8);self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.','outside','unmatched',1000,64,0,'preserve','real-game-data','private_payload'));self.publish();self.promoted();out=self.root/'applied.json';saved=a.apply(self.plan,self.manifest,self.preview,m.sha(self.preview),out);self.assertEqual(saved['status'],'PASS');self.assertEqual(dict(self.db.execute('SELECT uuid,kind FROM objects')),{'r':'whole','n':'preserve','d':'preserve','outside':'preserve'});self.assertEqual(self.db.execute("SELECT reason FROM objects WHERE uuid='outside'").fetchone()[0],'real-game-data');self.assertFalse(self.db.execute("SELECT 1 FROM protected_cells WHERE uuid='outside'").fetchone());self.assertEqual(self.db.execute("SELECT count(*) FROM protected_cells WHERE uuid='n'").fetchone()[0],2);self.assertEqual(m.meta(self.db,'classification'),{'retained':'private-payload-decisions'});self.assertEqual(m.meta(self.db,'wholeTemplates')['real'],'wo_real');self.assertEqual(a.apply(self.plan,self.manifest,self.preview,m.sha(self.preview),out),saved)
    def test_preview_detects_static_fragments_native_and_substrate_refs(self):
        self.add('real','r');self.db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.','r','real',0,64,0,'static'));self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',0,64,0,'r','real',0,0,0));self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','r'));self.db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',0,64,0,'r','real',0,0,0));report=self.publish();self.assertEqual(report['status'],'NEEDS_BOUNDED_PILOT_CORRECTION');self.assertEqual(report['pilotIntersections'],{'selected_static':1,'architectural_fragment':1,'activated_native':1,'substrate_visual':1});self.promoted()
        with self.assertRaisesRegex(RuntimeError,'bounded correction'):a.apply(self.plan,self.manifest,self.preview,m.sha(self.preview),self.root/'no.json')
    def test_exact_pose_hash_and_v12_identity_are_required(self):
        self.add('real','r');self.doc['poseMatches'][0]['poseDataSha256']='wrong';self.db.commit();m.atomic_json(self.manifest,self.doc)
        with self.assertRaisesRegex(RuntimeError,'SourcePose changed'):a.preview(self.plan,self.manifest,self.preview)
        self.doc['poseMatches'][0]['poseDataSha256']=a.data_sha(self.db.execute("SELECT data FROM poses WHERE key='real'").fetchone()[0]);self.doc['entries'][0]['preservation']['requireOriginalRegistryId']=True;m.atomic_json(self.manifest,self.doc)
        with self.assertRaisesRegex(RuntimeError,'only for the protected v12'):a.preview(self.plan,self.manifest,self.preview)
    def test_uncertain_empty_recipe_is_preserved_without_new_template(self):
        entry=self.add('uncertain','u',policy='local_original_due_uncertainty');entry['recipe']['components']=[];report=self.publish();self.assertEqual(report['wholeTemplateRequests']['count'],0);self.assertEqual(report['changes'][0]['reason'],'preserved_assembly_unproved_component_recipe')
    def test_native_root_placement_is_not_hidden_by_retained_background_bridge(self):
        self.add('native','n','substrate',root=4);self.add('retained','r','preserve',root=8)
        for x in (4,8):
            self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',x,64,0,0,0,'bg',42,'{}',0));self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',x,64,0,'{}',1,42))
        report=self.publish();self.assertEqual(report['pilotIntersections'],{'root_placement':1})
    def test_functional_policy_cannot_accept_a_previously_compiled_whole(self):
        self.add('door','d','whole',policy='local_original_functional');self.db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.','d','door',0,64,0,'whole'));report=self.publish();self.assertEqual(report['pilotIntersections'],{'selected_static':1})
    def test_unsupported_finite_role_is_retained_locally(self):
        self.add('huge','h');self.db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',('huge',128,0,0));report=self.publish();self.assertEqual(report['wholeTemplateRequests']['count'],0);self.assertEqual(report['changes'][0]['reason'],'preserved_assembly_whole_role_exceeds_127')
if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(AssemblyFixtures));m.atomic_json(m.ROOT/'research/assembly-override-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[e for _,e in result.errors],'failures':[e for _,e in result.failures],'activePlanWrites':0,'worldFilesRead':0,'mapWrites':0});raise SystemExit(0 if result.wasSuccessful() else 1)
