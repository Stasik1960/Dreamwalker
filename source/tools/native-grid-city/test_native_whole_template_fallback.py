"""New W-only fallback boundaries; temporary databases, no active inputs."""
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


class WholeTemplateFallbackTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown
    install_upgrade=CertificateTests.install_upgrade

    def setUp(self):
        CertificateTests.setUp(self)
        self.db.execute("DELETE FROM fragments WHERE uuid='D_new'")
        self.db.execute("UPDATE objects SET kind='whole',reason='confirmed_sculpture_whole_object_template' WHERE uuid='D_new'")
        self.db.execute("UPDATE selected_objects SET kind='whole' WHERE uuid='D_new'")
        self.owner['object'].update(kind='whole',reason='confirmed_sculpture_whole_object_template');self.owner['selected']['kind']='whole'
        self.template='wo_existing72_exact_key'
        for x in (2,3,6,7,8):
            self.db.execute('INSERT INTO whole_cells VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,'D_new',self.template,x-3,0,0))
            self.db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,0)',('.',x,0,0))
        self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',12,0,0,'E_independent',self.db.execute("SELECT pose FROM objects WHERE uuid='E_independent'").fetchone()[0],7,0,0))
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',12,0,0))
        classification=m.read(m.meta(self.db,'classification')['path'])
        classification['poses'].append({'key':self.owner['pose'],'registryId':'fixture:D_new','variant':0,'route':'whole','reason':self.owner['object']['reason'],'template':self.template})
        self.classification=self.plan/'new-classification.json';m.atomic_json(self.classification,classification);m.meta(self.db,'classification',n.ref(m,self.classification))
        self.exports=self.plan/'native.sqlite';exports=sqlite3.connect(self.exports);exports.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');exports.commit();exports.close();m.meta(self.db,'poseFragments',n.ref(m,self.exports))
        receipt=m.read(self.old_receipt);receipt['semanticBindings']=n.snapshot_bindings(m,self.db);m.atomic_json(self.old_receipt,receipt)
        head=n.ref(m,self.old_receipt);m.meta(self.db,'nativeLocalRetention',head);cursor=m.meta(self.db,'nativeComposeCursor');cursor['retention']=head;m.meta(self.db,'nativeComposeCursor',cursor);self.db.commit();self.install_upgrade()
        self.recipes=[]
        for pose,origin,resident in (('legacy_C',[0,0,0],0),('legacy_C',[-10,0,0],-1),('legacy_W',[0,0,0],0),('legacy_W',[-3,0,0],-1),('legacy_W',[-4,0,0],-1),('legacy_B',[0,0,0],0)):
            self.recipes.append({'fragments':[{'pose':pose,'origin':origin,'member':True}],'resident':resident,'remove':[-1]})
        self.catalog={'recipes':self.recipes};self.recipe_path=self.plan/'recipes.json';m.atomic_json(self.recipe_path,self.catalog)
        self.catalog_patch=patch.object(m,'legacy_native_catalog',return_value=(self.catalog,{}));self.catalog_patch.start();self.addCleanup(self.catalog_patch.stop)
        self.uid_patch=patch.object(m,'synthetic_uuid',side_effect=lambda dim,root,pose:{'legacy_C':'C_new','legacy_W':'D_new','legacy_B':'previous-native'}[pose]);self.uid_patch.start();self.addCleanup(self.uid_patch.stop)
        Path(m.read(self.failure)['log']).write_text('Native overhang would overlap a dedicated whole-object role',encoding='utf-8')

    def certificate(self,foreign_native=False,protected=False):
        if foreign_native:self.seed('previous-native',8,'substrate',(),selected=False);self.db.commit()
        def physical(x,index):
            return {'position':[x,0,0],'sourceState':{'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':str(index)}},'sourceBE':{'exists':False},
                    'recipeIndex':index,'recipe':copy.deepcopy(self.recipes[index]),'matchingNativeFragments':copy.deepcopy(self.recipes[index]['fragments']),'pointDemand':c.point(m,self.db,'.',[x,0,0])}
        native={key:self.native[key] for key in ('uuid','object','pose','poseDataSha256','root')};native.update(dimension='.',runtimeMemberCells=[[2,0,0],[12,0,0]],visualCells=[[2,0,0],[12,0,0]],physicalMembers=[physical(2,0),physical(12,1)],actualRootState=physical(2,0)['sourceState'])
        templates=self.plan/'wholes.jsonl';templates.write_text('{"key":"'+self.template+'"}\n',encoding='utf-8')
        ids=self.plan/'statue-ids.json';m.atomic_json(ids,{'entries':[{'registryId':'fixture:D_new','classification':'whole_statue','protectedV12':protected}]})
        authority={'classification':n.ref(m,self.classification),'statueIds':n.ref(m,ids),'wholeTemplates':n.ref(m,templates),'exactWholeTemplateRequests':[{'pose':self.owner['pose'],'key':self.template}]}
        whole={key:self.owner[key] for key in ('uuid','object','selected','pose','poseDataSha256','root')};whole.update(dimension='.',runtimeMemberCells=[[3,0,0],[6,0,0],[7,0,0]],physicalMembers=[physical(3,2),physical(6,3),physical(7,4)],wholeCells=n.row_dict(self.db,'SELECT * FROM whole_cells WHERE uuid=? ORDER BY x,y,z',('D_new',)),fragments=[],wholeAuthority=authority)
        points=[c.point(m,self.db,'.',[x,0,0]) for x in (2,3,6,7,8,12)]
        keep=[{'dimension':'.','position':[x,0,0],'reason':'exact_pending_native_activation','nativeUuid':'C_new'} for x in (2,12)]
        binding=m.meta(self.db,'binding')
        facts=self.plan/'exact-facts.json';m.atomic_json(facts,{'sourceBinding':binding,'native':{'object':native['object'],'runtimeMemberCells':native['runtimeMemberCells'],'physicalMembers':native['physicalMembers'],'visualOffsets':[[0,0,0],[10,0,0]]},
            'whole':{'object':whole['object'],'poseData':{'registryId':'fixture:D_new'},'runtimeMemberCells':whole['runtimeMemberCells'],'physicalMembers':whole['physicalMembers'],'ownPlanRows':{'whole_cells':whole['wholeCells'],'selected_objects':[whole['selected']]}}})
        semantics=self.plan/'whole-semantics.json';m.atomic_json(semantics,{'status':'PASS_SOURCE_REVIEW_NOT_EXECUTION','sourceBinding':binding,'statueUuid':'D_new','pose':whole['pose'],'runtimeMemberCells':whole['runtimeMemberCells'],'wholeModelAndMemberLifecyclePreserved':True,'itemPickDropGapUnresolved':True,'gameplayPass':False})
        source=self.plan/'fallback-source.json';m.atomic_json(source,{'status':'PASS','sourceBinding':binding,'native':native,'whole':whole,'keepNativeQueue':keep,'pointDemands':points,'sourceFacts':n.ref(m,facts),'nativeWholeSemantics':n.ref(m,semantics)})
        located={'nativeObject':native['object'],'wholeObject':whole['object'],'targetWholeRole':whole['wholeCells'][0],'pendingCell':['.',12,0,0],'replayReachedExactLine556AfterAllPriorGuards':True}
        location=self.plan/'location.json';m.atomic_json(location,{'status':'LOCATED_EXACT_CURRENT_PENDING_BATCH_WHOLE_ROLE_CONFLICT','failedRun':n.ref(m,self.failure),'sourceBinding':binding,'nativeComposeCursor':m.meta(self.db,'nativeComposeCursor'),'actualConflict':located,'reconstructionBlocker':None,'sourceAlgorithms':{'migrate':{'sha256':self.old_files['migrate']},'nativeResume':{'sha256':self.old_files['nativeResume']}}})
        rows=[]
        for x,index in ((3,2),(6,3),(7,4),(8,5 if foreign_native else None)):
            active=[{'dim':'.','uuid':'previous-native'}] if foreign_native and x==8 else []
            roots=[{'uuid':'previous-native','pose':self.db.execute("SELECT pose FROM objects WHERE uuid='previous-native'").fetchone()[0],'root':[8,0,0],'objectKind':'substrate','reason':'original','activatedNative':active,'allowedPreservedNative':False,'legacyPoseKey':'legacy_B','member':True}] if active else []
            rows.append({'dimension':'.','position':[x,0,0],'queue':[{'processed':0}],'sourceState':physical(x,index)['sourceState'] if index is not None else m.AIR,
                         'nativeExport':{'exists':False,'storedDataSha256':None,'nativeVisualComplete':None},'legacyRecipeIndex':index,'legacyRecipe':copy.deepcopy(self.recipes[index]) if index is not None else None,
                         'nativeRoots':roots,'foreignActivated':active,'nativeRootsAtError':None,'sourceRecipeResolutionPassed':False,'queuePruningSafe':True})
        guard=self.plan/'fallback-guard.json';m.atomic_json(guard,{'status':'PASS','kind':'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK_QUEUE_GUARD','sourceBinding':binding,'failedRun':n.ref(m,self.failure),'nativeUuid':'C_new','wholeOwnerUuid':'D_new','points':rows,'fragmentBinding':m.meta(self.db,'poseFragments'),'sourceRecipeResource':n.ref(m,self.recipe_path)})
        source_data=m.read(source);source_data.update(failedRun=n.ref(m,self.failure),locationProof=n.ref(m,location),queueNativeGuard=n.ref(m,guard));m.atomic_json(source,source_data)
        document={'schemaVersion':1,'status':'PASS','kind':'FINITE_NATIVE_WHOLE_TEMPLATE_FALLBACK_V1','sourceBinding':binding,'failedRun':n.ref(m,self.failure),'locationProof':n.ref(m,location),'sourceProof':n.ref(m,source),
                  'native':native,'whole':whole,'keepNativeQueue':keep,'pointDemands':points,'queueNativeGuard':n.ref(m,guard),'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},'itemPickDropGapUnresolved':True,'gameplayPass':False,'ownerGraphTraversal':False,'applyAuthorization':False}
        path=self.plan/'fallback-certificate.json';m.atomic_json(path,document)
        return SimpleNamespace(plan=self.plan,certificate=path,certificate_sha256=m.sha(path),preview=self.plan/'fallback-preview.json',execute=False,expected_preview_sha256=None)

    def execute(self,args):
        c.fallback_whole_template(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);c.fallback_whole_template(args,m)

    def test_w_only_keeps_native_seed_and_root_and_historical_heads(self):
        args=self.certificate();before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');head=m.meta(self.db,'nativeLocalRetention');self.execute(args)
        self.assertEqual(n.counts(self.db),{**before,'selected_objects':before['selected_objects']-1,'whole_cells':before['whole_cells']-5,'cell_queue':before['cell_queue']-4})
        self.assertEqual(m.meta(self.db,'nativeLocalRetention'),head);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor);n.validate_resume(m,self.db,self.plan)
        self.assertEqual(self.db.execute("SELECT kind FROM objects WHERE uuid='C_new'").fetchone(),('substrate',));self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue WHERE x IN(2,12) ORDER BY x').fetchall(),[(2,0),(12,0)])
        for x in (3,6,7):self.assertIsNotNone(self.db.execute("SELECT 1 FROM protected_cells WHERE x=? AND uuid='D_new'",(x,)).fetchone())
        c.fallback_whole_template(args,m);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor)

    def test_wrong_kind_v12_and_uncertified_template_reject(self):
        args=self.certificate();doc=m.read(args.certificate)
        for mutate in ('kind','template'):
            changed=copy.deepcopy(doc)
            if mutate=='kind':changed['native']['object']['kind']='preserve'
            if mutate=='template':changed['whole']['wholeCells'][0]['template']='uncertified'
            with self.subTest(mutate=mutate),self.assertRaises(RuntimeError):c.whole_template_fallback_proof(m,changed)
        args=self.certificate(protected=True)
        with self.assertRaisesRegex(RuntimeError,'non-v12 sculpture'):c.whole_template_fallback_proof(m,m.read(args.certificate))

    def test_foreign_physical_demand_and_visual_protection_reject(self):
        pose=self.db.execute("SELECT pose FROM objects WHERE uuid='E_independent'").fetchone()[0]
        self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',3,0,0,'E_independent',pose,-2,0,0));self.db.commit();args=self.certificate()
        with self.assertRaisesRegex(RuntimeError,'Independent physical demand'):c.fallback_whole_template(args,m)
        self.db.execute("DELETE FROM fragments WHERE x=3 AND uuid='E_independent'");self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',2,0,0,'unknown','unknown_original'));self.db.commit();args=self.certificate()
        with self.assertRaisesRegex(RuntimeError,'visual clearance'):c.fallback_whole_template(args,m)

    def test_active_native_empty_root_blocks_otherwise_undemanded_prune(self):
        args=self.certificate(foreign_native=True)
        with self.assertRaisesRegex(RuntimeError,'Foreign activated empty-root'):c.fallback_whole_template(args,m)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=8').fetchone(),(0,))

    def test_completed_whole_only_queue_cannot_be_retired(self):
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE x=8');self.db.commit();args=self.certificate()
        with self.assertRaisesRegex(RuntimeError,'Never retire completed'):c.fallback_whole_template(args,m)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE x=8').fetchone(),(1,))

    def test_receipt_before_commit_crash_rolls_back_and_retries(self):
        args=self.certificate();c.fallback_whole_template(args,m);args.execute=True;args.expected_preview_sha256=m.sha(args.preview);before=n.counts(self.db);original=m.atomic_json
        def crash(path,value):
            original(path,value)
            if Path(path).parent.name=='native-whole-template-fallbacks':raise RuntimeError('fallback receipt before commit')
        with patch.object(m,'atomic_json',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.fallback_whole_template(args,m)
        self.assertEqual(n.counts(self.db),before);self.assertIsNone(m.meta(self.db,'nativeWholeTemplateFallback'));c.fallback_whole_template(args,m);n.validate_resume(m,self.db,self.plan)

    def test_finalizing_and_final_apply_recheck_only_original_w(self):
        args=self.certificate();self.execute(args);self.db.execute('UPDATE cell_queue SET processed=1');self.db.commit()
        self.db.execute('BEGIN');n.checkpoint(m,self.db,'FINALIZING');self.db.rollback()
        self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','C_new'))
        for x in (2,12):self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',x,0,0,0,0,'compiled_C',1,m.canonical(m.AIR),0))
        self.db.commit();c.validate_whole_template_fallbacks(m,self.db,finalized=True)
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',3,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'physical member has replacement work'):n.checkpoint(m,self.db,'FINALIZING')
        self.assertIsNone(self.db.execute('SELECT 1 FROM placements WHERE x=3').fetchone())


if __name__=='__main__':unittest.main()
