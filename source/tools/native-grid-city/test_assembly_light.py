"""Finite full-only debt/receipt fixtures. No live source, plan, or world reads."""
import copy,sqlite3,tempfile,unittest
from pathlib import Path
import assembly_components as ac
m=ac.m

class AssemblyLightFixtures(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='assembly-light-fixture-',dir=m.ROOT/'research');self.root=Path(self.temp.name);self.db=sqlite3.connect(':memory:');self.db.execute('CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)')
        self.request=self.root/'request.json';self.phase=self.root/'engine.assemblies.json';self.groups=[];self.outcomes=[]
        for i in range(4):
            row={'groupKey':str(i),'root':[10+i,64,0],'primaryOwnerUuid':'primary-'+str(i),'expectedRegistryId':'fixture:whole','expectedInstallationPose':1,'recipeKey':'exactrecipe','retiredOwnerUuids':['upper-'+str(i)]};self.groups.append(row)
            self.outcomes.append({'status':'PASS','groupKey':row['groupKey'],'root':row['root'],'primaryOwnerUuid':row['primaryOwnerUuid'],'registryId':row['expectedRegistryId'],'installationPose':1,'recipeKey':row['recipeKey'],'retiredOwnerUuids':row['retiredOwnerUuids'],'oldUpperResidentAndContributionAbsent':True,'oldUpperSavedMembershipAbsent':True,'mergedFootprintWithinDeclaredCells':True,'primaryWholeMembershipComplete':True})
        m.atomic_json(self.request,{'groups':self.groups});self.binding={'assemblyRequest':ac.ref(self.request),'assemblyPhaseReceipt':str(self.phase),'target':'fixtureworld','runtime':{'sha256':'runtime'},'catalog':{'sha256':'catalog'}};m.meta(self.db,'fullAssemblyLightRequest',ac.ref(self.request))
        self.phase_doc={'status':'PASS','actualRuntimeQueries':True,'saveFlushed':True,'requestSha256':m.sha(self.request),'world':'fixtureworld','runtimeSha256':'runtime','catalogSha256':'catalog','outcomes':self.outcomes};self.publish()
    def tearDown(self):
        self.db.close();m.checked(self.root);self.assertTrue(self.root.resolve().is_relative_to(m.ROOT/'research'));self.temp.cleanup()
    def publish(self):m.atomic_json(self.phase,self.phase_doc);self.engine={'assemblyPhase':ac.ref(self.phase)}
    def test_two_owned_chunks_have_exact_twelve_native_light_neighbors(self):
        groups=[{'dimension':'.','ownedChunks':[[0,0],[1,0]]}];debt=ac.light_debt(groups);self.assertEqual(len(debt),12);self.assertIn(('.',-1,-1),debt);self.assertIn(('.',2,1),debt)
    def test_only_absent_mca_slots_are_excluded_not_reported_as_done(self):
        region=self.root/'region';region.mkdir();header=bytearray(8192);header[0:4]=(2<<8|1).to_bytes(4,'big');(region/'r.0.0.mca').write_bytes(header)
        present,absent=ac.present_debt(self.root,[('.',0,0),('.',1,0),('.',33,0)]);self.assertEqual(present,[('.',0,0)]);self.assertEqual(len(absent),2);self.assertTrue(all(v['location']==0 and 'done'not in v for v in absent))
        (region/'r.0.0.mca').write_bytes(b'short')
        with self.assertRaisesRegex(RuntimeError,'Short nonempty'):ac.present_debt(self.root,[('.',0,0)])
    def test_actual_four_whole_merges_are_required_for_full_light_acceptance(self):
        self.assertEqual(ac.accept_full_light(self.db,self.binding,self.engine),ac.ref(self.phase));self.phase_doc['outcomes']=self.outcomes[:3];self.publish()
        with self.assertRaisesRegex(RuntimeError,'all four exact anchors'):ac.accept_full_light(self.db,self.binding,self.engine)
    def test_unqueried_unsaved_wrong_runtime_or_retained_upper_is_rejected(self):
        original=copy.deepcopy(self.phase_doc)
        for key,value in [('actualRuntimeQueries',False),('saveFlushed',False),('runtimeSha256','other')]:
            self.phase_doc=copy.deepcopy(original);self.phase_doc[key]=value;self.publish()
            with self.assertRaisesRegex(RuntimeError,'not bound'):ac.accept_full_light(self.db,self.binding,self.engine)
        self.phase_doc=copy.deepcopy(original);self.phase_doc['outcomes'][0]['oldUpperSavedMembershipAbsent']=False;self.publish()
        with self.assertRaisesRegex(RuntimeError,'outcome is incomplete'):ac.accept_full_light(self.db,self.binding,self.engine)
    def test_header_binding_does_not_authorize_changed_request(self):
        m.atomic_json(self.request,{'groups':self.groups,'changed':True})
        with self.assertRaisesRegex(RuntimeError,'evidence changed'):ac.accept_full_light(self.db,self.binding,self.engine)

if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(AssemblyLightFixtures));m.atomic_json(m.ROOT/'research/assembly-light-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[e for _,e in result.errors],'failures':[e for _,e in result.failures],'activePlanWrites':0,'worldFilesRead':0,'mapWrites':0});raise SystemExit(0 if result.wasSuccessful() else 1)
