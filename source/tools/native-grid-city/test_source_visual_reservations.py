"""New migration-only visual occupancy/journal/admission boundaries."""
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
from test_native_source_upgrade_chains import SourceUpgradeChainTests


class GeneralVisualTests(unittest.TestCase):
    seed=CertificateTests.seed
    tearDown=CertificateTests.tearDown

    def setUp(self):
        CertificateTests.setUp(self)
        self.db.execute("ATTACH DATABASE ':memory:' AS old")
        for table in ('stored_contributions','members'):
            self.db.execute('CREATE TABLE old.'+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT)')
        self.uid=m.canonical([1,2,3,4]);self.owner=self.seed(self.uid,30,'static',(),selected=False)
        for x in (30,31,32):self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',x,0,0,self.uid,'functional_guest'))
        for x in (30,31):self.db.execute('INSERT INTO old.stored_contributions VALUES(?,?,?,?,?)',('.',x,0,0,self.uid))
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',32,0,0));self.db.commit()
        self.incoming={'kind':'legacy_native','uuid':'incoming_C','pose':'C_pose','root':[33,0,0]}
        self.root_state={'registryId':'fixture:'+self.uid,'properties':{}}
        self.be={'id':m.Tag(8,'bloodborne_dw:composite_cell'),'resident':m.Tag(10,{'uuid':m.Tag(11,[1,2,3,4]),'root':m.Tag(11,[30,0,0]),'id':m.Tag(8,self.root_state['registryId'])}),'payload':m.Tag(10,{'ImportedTypeVersion':m.Tag(3,1)})}
        self.states={30:self.root_state,31:{'registryId':'fixture:existing_helper','properties':{}},32:m.AIR}
        self.bes={30:self.be};self.context=c.SourceVisualReservations(m,self.db)
        self.context.envelope=lambda pose,encoded:{'resource':{'path':'fixture-source-envelope','sha256':'exact-source'},'offset':0,'length':2,'rowSha256':'exact-row','cells':[(0,0,0),(1,0,0)],'openable':False,'functionalDoor':False}
        self.context.authority=lambda pose:({'className':'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock'},{'path':'fixture-cap','sha256':'exact-cap'},{'openable':False,'essentialMask':[[0,0,0]],'support':{'required':False}},{'path':'fixture-spec','sha256':'exact-spec'})
        self.context.source_point=lambda dim,at:(copy.deepcopy(self.states.get(at[0],m.AIR)),self.bes.get(at[0]),{'dimension':dim,'chunk':[0,0],'region':'fixture','regionSha256':'region','cachedPayloadSha256':'cache'})
        self.context.native_origins=lambda dim,at,state:[]

    def test_positive_keeps_protection_all_work_cursor_and_deduplicates_journal(self):
        before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');head=m.meta(self.db,'nativeLocalRetention')
        self.context.clearance('.',[32,0,0],self.incoming);self.db.commit();journal=m.meta(self.db,'sourceVisualReservationJournal')
        self.context.clearance('.',[32,0,0],self.incoming);self.db.commit()
        self.assertEqual(m.meta(self.db,'sourceVisualReservationJournal'),journal);self.assertEqual(journal['contacts'],1)
        self.assertEqual(n.counts(self.db),before);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor);self.assertEqual(m.meta(self.db,'nativeLocalRetention'),head)
        self.assertEqual(self.db.execute('SELECT count(*) FROM protected_cells WHERE uuid=?',(self.uid,)).fetchone(),(3,))

    def test_root_helper_actual_be_v12_unknown_and_historical_original_reject(self):
        for at in ([30,0,0],[31,0,0]):
            with self.subTest(at=at),self.assertRaises(RuntimeError):self.context.clearance('.',at,self.incoming)
        self.bes[32]={'id':m.Tag(8,'fixture:real_container')}
        with self.assertRaisesRegex(RuntimeError,'BlockEntity'):self.context.clearance('.',[32,0,0],self.incoming)
        self.bes.pop(32);self.states[32]={'registryId':'fixture:protected_v12','properties':{}}
        with self.assertRaisesRegex(RuntimeError,'actual protected v12'):self.context.clearance('.',[32,0,0],self.incoming)
        self.states[32]=m.AIR;self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',32,0,0,'unknown','unknown_reason'))
        with self.assertRaises(RuntimeError):self.context.clearance('.',[32,0,0],self.incoming)
        self.db.rollback();self.context.strict={('.',32,0,0)}
        with self.assertRaisesRegex(RuntimeError,'Historical strict'):self.context.clearance('.',[32,0,0],self.incoming)

    def test_retained_physical_work_and_private_payload_fail_closed(self):
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',31,0,0))
        with self.assertRaisesRegex(RuntimeError,'runtime/service/door'):self.context.clearance('.',[32,0,0],self.incoming)
        self.db.rollback();self.be['payload'].value['UnknownGameData']=m.Tag(8,'preserve')
        with self.assertRaisesRegex(RuntimeError,'payload'):self.context.clearance('.',[32,0,0],self.incoming)

    def test_other_doors_support_and_future_endpoint_are_not_visual_space(self):
        door=self.seed('door',32,'preserve',(),selected=False)
        doc={'status':'PASS','nativeDescriptor':True,'baselineModJarSha256':c.OVERLAP_BASELINE_SHA,'poses':[{'key':door['pose'],'cells':[[0,0,0],[0,2,0]],'states':[{'open':False,'cells':[[0,0,0]]},{'open':True,'cells':[[0,0,0],[0,2,0]]}]}]}
        path=self.plan/'door-domains.json';m.atomic_json(path,doc);self.context.bindings['doorFootprintGuard']=n.ref(m,path)
        base=self.context.authority
        self.context.authority=lambda pose:base(pose) if pose['registryId']!='fixture:door' else ({},{},{'openable':True,'support':{'required':True,'offset':[0,-1,0]},'essentialMask':[[0,0,0]]},{})
        # There is no protected row for this other door's support at the target.
        self.db.execute("UPDATE objects SET y=1 WHERE uuid='door'")
        with self.assertRaisesRegex(RuntimeError,'another retained door'):self.context.clearance('.',[32,0,0],self.incoming)
        self.db.execute("UPDATE objects SET y=-2 WHERE uuid='door'");self.context._door_hits={}
        with self.assertRaisesRegex(RuntimeError,'another retained door'):self.context.clearance('.',[32,0,0],self.incoming)
        self.assertEqual(self.context.operation_offsets({'properties':{'rotation':'2'}},{'essentialMask':[[0,0,0]],'support':{'required':True,'offset':[1,-1,0]}}),{(0,0,0),(1,-1,0)})

    def test_atomic_rollback_retry_and_final_validator_rechecks_source_domain(self):
        original=m.meta
        def crash(db,key,value=None):
            if key=='sourceVisualReservationJournal' and value is not None:raise RuntimeError('journal before batch commit')
            return original(db,key,value)
        with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before batch'):self.context.clearance('.',[32,0,0],self.incoming)
        self.db.rollback();self.assertEqual(self.db.execute('SELECT count(*) FROM source_visual_contacts').fetchone(),(0,));self.assertIsNone(m.meta(self.db,'sourceVisualReservationJournal'))
        self.context.clearance('.',[32,0,0],self.incoming);self.db.commit()
        self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',32,0,0,2,0,'ordinary_C',1,m.canonical(m.AIR),0));self.db.commit()
        with patch.object(c,'SourceVisualReservations',return_value=self.context):
            c.validate_source_visual_reservations(m,self.db,finalized=True,source_files_validator=lambda files:None)
            self.db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',32,0,0,'new_unknown','unknown'))
            with self.assertRaisesRegex(RuntimeError,'proof changed'):c.validate_source_visual_reservations(m,self.db,finalized=True,source_files_validator=lambda files:None)
            self.db.rollback();self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',31,0,0));self.context.domains.clear()
            with self.assertRaisesRegex(RuntimeError,'runtime/service/door'):c.validate_source_visual_reservations(m,self.db,source_files_validator=lambda files:None)


class AncestorVisualUpgradeTests(unittest.TestCase):
    seed=SourceUpgradeChainTests.seed
    setUp=SourceUpgradeChainTests.setUp
    tearDown=SourceUpgradeChainTests.tearDown
    install_upgrade=SourceUpgradeChainTests.install_upgrade
    certificate=SourceUpgradeChainTests.certificate
    execute=SourceUpgradeChainTests.execute
    failure_with_current_archives=SourceUpgradeChainTests.failure_with_current_archives
    install_visual_correction=SourceUpgradeChainTests.install_visual_correction

    def test_existing_ancestor_head_and_both_optional_chains_upgrade_without_noop_retention(self):
        self.execute(self.certificate());self.install_visual_correction();admitted=c.source_files(m);head=m.meta(self.db,'nativeLocalRetention')
        self.assertNotEqual(c.bound(m,head)['sourceFiles'],admitted)
        failure,_=self.failure_with_current_archives('general-overlap-next','Actual next source visual guard')
        newer={**admitted,'nativeCertificates':m.hashlib.sha256((admitted['nativeCertificates']+'general').encode()).hexdigest()}
        proof=self.plan/'general-upgrade.json';m.atomic_json(proof,{'status':'PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY','kind':'NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE','sourceFiles':newer,'oldSourceFiles':admitted})
        args=SimpleNamespace(plan=self.plan,failed_run=failure,source_proof=proof,source_proof_sha256=m.sha(proof))
        before=n.counts(self.db);cursor=m.meta(self.db,'nativeComposeCursor');changes=self.db.total_changes
        with patch.object(c,'source_files',return_value=newer),patch.object(c,'check_correction_whole',wraps=c.check_correction_whole) as visual,patch.object(c,'whole_template_fallback_state',wraps=c.whole_template_fallback_state) as whole:
            result=c.upgrade_preview(args,m,self.db);self.assertEqual(result['parentRetention'],head);self.assertTrue(visual.called);self.assertTrue(whole.called)
            self.assertEqual(n.counts(self.db),before);self.assertEqual(self.db.total_changes,changes);self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor)
            with self.assertRaisesRegex(RuntimeError,'explicit native consumer source upgrade'):c.active_binding(m,self.db)
            bad={**c.bound(m,head),'sourceFiles':{'nativeCertificates':'unreviewed'},'previousRetention':head};path=self.plan/'bad-head.json';m.atomic_json(path,bad)
            m.meta(self.db,'nativeLocalRetention',n.ref(m,path));self.db.commit()
            with self.assertRaisesRegex(RuntimeError,'not a reviewed source-lineage ancestor'):c.upgrade_preview(args,m,self.db)


if __name__=='__main__':
    suite=unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(cls) for cls in (GeneralVisualTests,AncestorVisualUpgradeTests))
    unittest.TextTestRunner(verbosity=2).run(suite)
