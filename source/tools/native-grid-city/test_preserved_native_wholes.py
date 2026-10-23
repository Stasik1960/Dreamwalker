"""Only the new finite preserved-native/whole fallback, temporary databases."""
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


class PreservedWholeTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown
    install_upgrade=CertificateTests.install_upgrade

    def setUp(self):
        CertificateTests.setUp(self)
        exports=self.plan/'exports.sqlite';db=sqlite3.connect(exports)
        db.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)')
        self.air_export=m.canonical({'nativeVisualComplete':True});db.execute('INSERT INTO native VALUES(?,?)',(m.canonical(m.AIR),self.air_export));db.commit();db.close()
        self.exports=exports;m.meta(self.db,'poseFragments',n.ref(m,exports))
        old=m.read(self.old_receipt);old['semanticBindings']=n.snapshot_bindings(m,self.db);m.atomic_json(self.old_receipt,old)
        self.old_bytes=self.old_receipt.read_bytes();parent=n.ref(m,self.old_receipt);m.meta(self.db,'nativeLocalRetention',parent)
        m.meta(self.db,'nativeComposeCursor',{**m.meta(self.db,'nativeComposeCursor'),'retention':parent});self.db.commit()
        self.catalog={'recipes':[{'fragments':[{'origin':[0,0,0],'pose':'legacy_C','member':True}]},
                                 {'fragments':[{'origin':[0,0,0],'pose':'legacy_C2','member':True}]},
                                 {'fragments':[{'origin':[8,0,0],'pose':'legacy_B','member':True}]}]}
        self.recipe_path=self.plan/'recipes.json';m.atomic_json(self.recipe_path,self.catalog)
        mock=patch.object(m,'legacy_native_catalog',return_value=(self.catalog,{}));mock.start();self.addCleanup(mock.stop)

    def certificate(self,foreign_physical=False,foreign_active=False,processed=False):
        self.install_upgrade()
        uid=m.synthetic_uuid('.',(2,0,0),'legacy_C');self.db.execute("UPDATE objects SET uuid=?,kind='preserve',reason='confirmed_original_sprite' WHERE uuid='C_new'",(uid,))
        native=copy.deepcopy(self.native);native['uuid']=uid;native['object'].update(uuid=uid,kind='preserve',reason='confirmed_original_sprite')
        other=self.seed(m.synthetic_uuid('.',(6,0,0),'legacy_C2'),6,'preserve',(),selected=False)
        self.db.execute("UPDATE objects SET reason='confirmed_original_sprite' WHERE uuid=?",(other['uuid'],));other['object']['reason']='confirmed_original_sprite'
        self.db.execute("DELETE FROM fragments WHERE uuid='D_new'");self.db.execute("UPDATE objects SET kind='whole',reason='confirmed_preserved_assembly_template' WHERE uuid='D_new'")
        self.db.execute("UPDATE selected_objects SET kind='whole' WHERE uuid='D_new'")
        self.db.execute("DELETE FROM fragments WHERE uuid='E_independent' AND x=4")
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',6,0,0))
        whole=copy.deepcopy(self.owner);whole.update(dimension='.',fragments=[])
        whole['object'].update(kind='whole',reason='confirmed_preserved_assembly_template');whole['selected']['kind']='whole'
        roles=[]
        for x in (2,3,4,6):
            row={'dim':'.','x':x,'y':0,'z':0,'uuid':'D_new','template':'wo_'+whole['pose'],'dx':x-3,'dy':0,'dz':0};roles.append(row)
            self.db.execute('INSERT INTO whole_cells VALUES(?,?,?,?,?,?,?,?,?)',tuple(row.values()))
        if foreign_physical:self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',2,0,0,'F_untouched',whole['pose'],0,0,0))
        if foreign_active:
            buid=m.synthetic_uuid('.',(12,0,0),'legacy_B');self.seed(buid,12,'substrate',(),selected=False);self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.',buid))
        if processed:self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=4')
        self.db.commit()
        whole['runtimeMemberCells']=[[3,0,0],[4,0,0]];whole['wholeCells']=roles
        snapshots=[c.point(m,self.db,'.',[x,0,0]) for x in (2,3,4,6)]
        whole['pointDemands']=copy.deepcopy(snapshots)
        whole['memberPolicies']=[{'position':[x,0,0],'policy':'original','sourceState':m.AIR,'storedContributions':[{'uuid':'D_new'}],
                                 'pointDemand':next(row for row in snapshots if row['position']==[x,0,0])} for x in (3,4)]
        manifest=self.plan/'manifest.json';assembly='fixture:confirmed_tree_0'
        m.atomic_json(manifest,{'sourceBinding':m.meta(self.db,'binding'),'entries':[{'key':assembly,'preservation':{'runtimePolicy':'compiled_whole_or_local_original','noBackgroundMerge':True}}],
            'poseMatches':[{'assemblyKey':assembly,'pose':whole['pose'],'poseDataSha256':whole['poseDataSha256']}]})
        whole['wholePreservation']={'manifest':n.ref(m,manifest),'assemblyKey':assembly,'runtimePolicy':'compiled_whole_or_local_original'}
        natives=[]
        for index,entry in enumerate((native,other)):
            state={'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':str(index)}}
            natives.append({**{key:entry[key] for key in ('uuid','object','pose','poseDataSha256','root','runtimeMemberCells')},'dimension':'.','actualRootState':state,'recipeIndex':index})
        proof=self.plan/'whole-source-proof.json'
        m.atomic_json(proof,{'status':'PASS','sourceBinding':m.meta(self.db,'binding'),'nativeComponents':copy.deepcopy(natives),'owners':[copy.deepcopy(whole)]})
        for entry in natives:entry['sourceProof']=n.ref(m,proof)
        whole['sourceGeometryProof']=whole['classProof']=n.ref(m,proof)
        failure=m.read(self.failure);Path(failure['log']).write_text('A consumed native helper belongs to a retained/protected local object: '+uid,encoding='utf-8')
        guard_rows=[]
        for x in (2,3,4,6):
            state=natives[0]['actualRootState'] if x==2 else natives[1]['actualRootState'] if x==6 else {'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':'2'}} if foreign_active and x==4 else m.AIR
            recipe=int(state['properties']['part']) if state!=m.AIR else None
            error=None
            try:roots=m.native_roots_at(self.db,'.',(x,0,0),state,{'nativeVisualComplete':True} if state==m.AIR else None)
            except RuntimeError as exc:
                error=str(exc);roots=[(entry['uuid'],entry['pose'],*entry['root']) for entry in natives if entry['root']==[x,0,0]]
            rows=[];foreign=[]
            for ruid,pose,rx,ry,rz in roots:
                obj=n.row_dict(self.db,'SELECT * FROM objects WHERE uuid=?',(ruid,))[0];activated=n.row_dict(self.db,'SELECT * FROM activated_native WHERE uuid=?',(ruid,))
                rows.append({'uuid':ruid,'pose':pose,'root':[rx,ry,rz],'objectKind':obj['kind'],'reason':obj['reason'],'activatedNative':activated,
                             'allowedPreservedNative':ruid in {r['uuid'] for r in natives},'legacyPoseKey':self.catalog['recipes'][recipe]['fragments'][0]['pose'],'member':True})
                foreign.extend(activated)
            guard_rows.append({'dimension':'.','position':[x,0,0],'sourceState':state,'queue':next(row['queue'] for row in snapshots if row['position']==[x,0,0]),
                'nativeExport':{'exists':state==m.AIR,'storedDataSha256':m.hashlib.sha256(self.air_export.encode()).hexdigest() if state==m.AIR else None,'nativeVisualComplete':True if state==m.AIR else None},
                'legacyRecipeIndex':recipe,'legacyRecipe':self.catalog['recipes'][recipe] if recipe is not None else None,'nativeRoots':rows,'foreignActivated':foreign,
                'nativeRootsAtError':error,'sourceRecipeResolutionPassed':bool(error),'queuePruningSafe':True})
        guard=self.plan/'whole-guard.json';m.atomic_json(guard,{'schemaVersion':1,'status':'PASS','kind':'EXACT_PRESERVED_NATIVE_WHOLE_FALLBACK_QUEUE_GUARD',
            'sourceBinding':m.meta(self.db,'binding'),'failedRun':n.ref(m,self.failure),'wholeOwnerUuid':'D_new','allowedPreservedNatives':copy.deepcopy(natives),
            'points':guard_rows,'blocked':[],'otherProtectedOrigins':[],'fragmentBinding':n.ref(m,self.exports),'sourceRecipeResource':n.ref(m,self.recipe_path)})
        document={'schemaVersion':1,'status':'PASS','kind':'FINITE_PRESERVED_NATIVE_WHOLE_CERTIFICATE_V1','sourceBinding':m.meta(self.db,'binding'),'failedRun':n.ref(m,self.failure),
            'native':natives[0],'additionalPreservedNatives':natives[1:],'owners':[whole],'pointDemands':snapshots,
            'nativePointDemands':[row for row in snapshots if row['position'][0] in(2,6)],'queueNativeGuard':n.ref(m,guard),'sourceProof':n.ref(m,proof),
            'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},'ownerGraphTraversal':False,'applyAuthorization':False}
        path=self.plan/'whole-certificate.json';m.atomic_json(path,document)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'whole-preview.json',execute=False,expected_preview_sha256=None)

    def execute(self,args):
        c.retain_preserved_whole(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.retain_preserved_whole(args,m)

    def test_preserved_native_and_whole_original_survive_and_idempotence(self):
        args=self.certificate();objects=n.row_dict(self.db,'SELECT * FROM objects ORDER BY uuid',());cursor=m.meta(self.db,'nativeComposeCursor')
        self.execute(args);n.validate_resume(m,self.db,self.plan);c.retain_preserved_whole(args,m)
        self.assertEqual(n.row_dict(self.db,'SELECT * FROM objects ORDER BY uuid',()),objects)
        self.assertEqual(self.db.execute('SELECT count(*) FROM whole_cells').fetchone(),(0,));self.assertIsNone(self.db.execute("SELECT 1 FROM selected_objects WHERE uuid='D_new'").fetchone())
        self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(5,0),(9,1)])
        self.assertEqual(self.db.execute("SELECT count(*) FROM issues WHERE kind='whole_local_overlap'").fetchone(),(1,))
        self.assertEqual(m.meta(self.db,'nativeComposeCursor')['rowsProcessedSinceAdmission'],cursor['rowsProcessedSinceAdmission']);self.assertEqual(self.old_receipt.read_bytes(),self.old_bytes)

    def test_wrong_native_kind_or_reason_rejected_before_delta(self):
        args=self.certificate();uid=m.read(args.certificate)['native']['uuid'];before=n.counts(self.db)
        for column,value in (('kind','substrate'),('reason','unproved-other-reason')):
            self.db.execute('SAVEPOINT changed_original');self.db.execute('UPDATE objects SET '+column+'=? WHERE uuid=?',(value,uid));self.db.commit()
            with self.assertRaisesRegex(RuntimeError,'kind/reason/object'):c.retain_preserved_whole(args,m)
            self.assertEqual(n.counts(self.db),before)
            self.db.execute('UPDATE objects SET '+column+'=? WHERE uuid=?',('preserve' if column=='kind' else 'confirmed_original_sprite',uid));self.db.commit()

    def test_foreign_physical_demand_rejected_without_selection_changes(self):
        args=self.certificate(foreign_physical=True);before=n.counts(self.db)
        with self.assertRaisesRegex(RuntimeError,'Independent physical demand'):c.retain_preserved_whole(args,m)
        self.assertEqual(n.counts(self.db),before)

    def test_foreign_active_empty_native_slot_blocks_pending_prune(self):
        args=self.certificate(foreign_active=True);before=n.counts(self.db)
        with self.assertRaisesRegex(RuntimeError,'empty-root suppression'):c.retain_preserved_whole(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=4').fetchone(),(0,))

    def test_completed_row_is_not_a_new_retirement_policy(self):
        args=self.certificate(processed=True)
        with self.assertRaisesRegex(RuntimeError,'never completed retirement'):c.retain_preserved_whole(args,m)

    def test_receipt_before_commit_crash_rolls_back_and_retries(self):
        args=self.certificate();c.retain_preserved_whole(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);before=n.counts(self.db);original=m.atomic_json
        def crash(path,value):
            original(path,value)
            if Path(path).parent.name=='native-retention-receipts':raise RuntimeError('new whole receipt before SQL commit')
        with patch.object(m,'atomic_json',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before SQL commit'):c.retain_preserved_whole(args,m)
        self.assertEqual(n.counts(self.db),before);c.retain_preserved_whole(args,m);n.validate_resume(m,self.db,self.plan)

    def test_finalizing_boundary_revalidates_all_retained_physical_members(self):
        args=self.certificate();self.execute(args)
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=5');self.db.commit()
        self.db.execute('BEGIN');n.checkpoint(m,self.db,'FINALIZING');self.db.rollback()
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',1,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'replacement work'):n.checkpoint(m,self.db,'FINALIZING')
        self.db.rollback();self.assertEqual(m.meta(self.db,'nativeComposeCursor')['phase'],'NATIVE_TRANSFER');self.assertEqual(self.db.execute('SELECT count(*) FROM placements').fetchone(),(0,))


if __name__=='__main__':unittest.main()
