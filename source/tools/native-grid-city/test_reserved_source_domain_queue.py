"""One new finalization boundary, using only isolated temporary source data.

No accepted test class is imported or repeated. The saved positive domain is
created before completed native work reaches it, matching the actual failure.
"""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class ReservedSourceDomainQueueTests(unittest.TestCase):
    def test_completed_domain_contact_stages_original_section_in_outer_transaction(self):
        with tempfile.TemporaryDirectory(prefix='reserved-domain-finalizing-') as directory:
            plan=Path(directory);old=plan/'old';old.mkdir()
            owner=m.canonical([1,2,3,4]);donor=m.canonical([5,6,7,8]);other='independent-outside-visual'
            root=(217,142,-648);upper=(217,143,-648);donor_root=(216,142,-648);shared=(224,142,-648)
            source=sqlite3.connect(old/'plan.sqlite')
            for table in ('members','stored_contributions'):
                source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
                for at in (root,upper):source.execute('INSERT INTO '+table+' VALUES(?,?,?,?,?)',('.',*at,owner))
            source.commit();source.close();source_sha=m.sha(old/'plan.sqlite');stat=(old/'plan.sqlite').stat()
            db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA)
            try:
                binding={'oldPlan':str(old),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'fixture','plannerBindingSha256':'fixture'}
                m.meta(db,'binding',binding);db.execute('ATTACH DATABASE ? AS old',('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',))
                classification=plan/'classification.json';m.atomic_json(classification,{'protectedRegistryIds':[]});m.meta(db,'classification',n.ref(m,classification))
                cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':2,'rowsProcessedSinceAdmission':2048,'retention':None};m.meta(db,'nativeComposeCursor',cursor)
                objects={};states={};envelopes={}
                def seed(uid,at,kind,cells):
                    encoded=m.canonical({'registryId':'fixture:'+uid,'properties':{}});pose='sp_'+m.digest(m.json.loads(encoded))[:24]
                    db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,encoded))
                    obj={'dim':'.','uuid':uid,'pose':pose,'x':at[0],'y':at[1],'z':at[2],'kind':kind,'reason':'original-policy-unchanged','source_status':'unknown_pose'}
                    db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()));objects[uid]=obj
                    states[at]={'registryId':'fixture:'+uid,'properties':{}}
                    envelopes[pose]={'resource':{'path':'bound-source-envelope','sha256':'fixture'},'cells':[tuple(p[i]-at[i] for i in range(3)) for p in cells],'openable':False,'functionalDoor':False}
                    return obj
                preserved=seed(owner,root,'whole',[root,upper]);native=seed(donor,donor_root,'substrate',[donor_root]);outside=seed(other,shared,'static',[shared])
                states[upper]={'registryId':'fixture:existing-source-helper','properties':{}}
                root_be={'id':m.Tag(8,'bloodborne_dw:composite_cell'),'resident':m.Tag(10,{'uuid':m.Tag(11,[1,2,3,4]),'root':m.Tag(11,list(root)),'id':m.Tag(8,states[root]['registryId'])}),'payload':m.Tag(10,{'ImportedTypeVersion':m.Tag(3,1)})}
                bes={root:root_be};root_bytes=m.encode_nbt(m.NbtFile('',m.Tag(10,root_be)))
                for at in (root,upper):db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',*at,owner,'whole_local_overlap'))
                export=plan/'exports.sqlite';edb=sqlite3.connect(export);edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');edb.execute('CREATE TABLE native_fragments(state TEXT,x INTEGER,y INTEGER,z INTEGER)');edb.commit();edb.close();m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)})
                db.commit();real=c.SourceVisualReservations
                def source_point(context,dim,at):
                    at=tuple(at)
                    return copy.deepcopy(states.get(at,m.AIR)),bes.get(at),{'dimension':dim,'chunk':[at[0]>>4,at[2]>>4],'cachedPayloadSha256':'immutable-fixture-'+str((at[0]>>4,at[2]>>4))}
                def envelope(context,pose,data):return copy.deepcopy(envelopes[pose])
                def authority(context,pose):return ({'className':'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock'},{'path':'bound-cap','sha256':'fixture'},{'openable':False,'essentialMask':[[0,0,0]],'support':{'required':False}},{'path':'bound-spec','sha256':'fixture'})
                def origins(context,dim,at,state):
                    if tuple(at)==donor_root:return [{'uuid':donor,'legacyPose':'p-donor','root':list(donor_root),'member':True,'object':[native]}]
                    return []
                with patch.object(real,'source_point',source_point),patch.object(real,'envelope',envelope),patch.object(real,'authority',authority),patch.object(real,'native_origins',origins),patch.object(real,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})):
                    # This is an actual encoded owner-domain snapshot, before
                    # later bridge-fastpath work reaches its real members.
                    context=real(m,db);domain=context.owner_domain('.',owner);encoded=m.canonical(domain);digest=m.digest(domain)
                    db.execute('INSERT INTO source_visual_domains VALUES(?,?,?,?)',('.',owner,encoded,digest));db.commit()
                    db.execute('INSERT INTO activated_native VALUES(?,?)',('.',donor))
                    for at in (root,upper,shared):
                        offset=tuple(at[i]-donor_root[i] for i in range(3));db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(native['pose'],*offset))
                        db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*at,donor,native['pose'],*offset));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',*at))
                    # Its visually empty own-root is an independent activation
                    # demand until the entire native donor is retracted.
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',*donor_root))
                    for at in (root,upper):db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',*at,m.canonical(states[at]),1,None))
                    db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(outside[k] for k in ('dim','uuid','pose','x','y','z','kind')))
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*shared,other,outside['pose'],0,0,0));db.commit()
                    before=n.counts(db);saved_domains=db.execute('SELECT * FROM source_visual_domains').fetchall();saved_objects=db.execute('SELECT * FROM objects ORDER BY uuid').fetchall();protected_before=db.execute('SELECT * FROM protected_cells ORDER BY x,y,z,uuid').fetchall()
                    def snapshot():return {table:n.row_dict(db,'SELECT * FROM '+table+' ORDER BY 1,2',()) for table in ('cell_queue','substrate_fragments','bridge_cells','activated_native','selected_objects','fragments','source_local_retentions')}
                    unchanged=snapshot()
                    with self.assertRaisesRegex(RuntimeError,'runtime/service/door'):real(m,db).owner_domain('.',owner)
                    # Completed mode may never admit any outstanding q0.
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',225,142,-648));db.commit();db.execute('BEGIN IMMEDIATE')
                    with self.assertRaises(RuntimeError):c.stage_reserved_source_domain_queues(m,db,real(m,db))
                    db.rollback();db.execute('DELETE FROM cell_queue WHERE processed=0');db.commit();self.assertEqual(snapshot(),unchanged)
                    # A changed saved record is not an eligible contact.
                    db.execute('SAVEPOINT corrupt_domain');db.execute("UPDATE source_visual_domains SET digest='not-the-source-digest'");db.execute('RELEASE corrupt_domain');db.commit();db.execute('BEGIN IMMEDIATE')
                    with self.assertRaises(RuntimeError):c.stage_reserved_source_domain_queues(m,db,real(m,db))
                    db.rollback();db.execute('UPDATE source_visual_domains SET digest=?',(digest,));db.commit();self.assertEqual(snapshot(),unchanged)
                    saved_state=states[root];states[root]=m.AIR;db.execute('BEGIN IMMEDIATE')
                    with self.assertRaises(RuntimeError):c.stage_reserved_source_domain_queues(m,db,real(m,db))
                    db.rollback();states[root]=saved_state;self.assertEqual(snapshot(),unchanged)
                    # All staging mutations stay in the caller transaction;
                    # failing final validation cannot leave a committed zone.
                    db.execute('BEGIN IMMEDIATE');staged=c.stage_reserved_source_domain_queues(m,db,real(m,db));self.assertTrue(db.in_transaction);self.assertEqual(staged['sectionsRetained'],1)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);db.rollback()
                    self.assertEqual(n.counts(db),before);self.assertEqual(snapshot(),unchanged);self.assertIsNone(m.meta(db,'sourceLocalRetentionJournal'));self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    # Re-run only this affected boundary after rollback, then
                    # validate the same staged source journal before commit.
                    db.execute('BEGIN IMMEDIATE');staged=c.stage_reserved_source_domain_queues(m,db,real(m,db));self.assertEqual(staged['sectionsRetained'],1)
                    self.assertEqual({tuple(row['position']) for row in staged['contacts']},{root,upper})
                    self.assertTrue(all(row['uuid']==owner and row['domainDigest']==digest for row in staged['contacts']))
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);self.assertTrue(db.in_transaction)
                    self.assertEqual(c.stage_reserved_source_domain_queues(m,db,real(m,db)),{'contacts':[],'sectionsRetained':0})
                    # checkpoint remains the mandatory final full-validator
                    # call. Only the old retention-chain fixture dependency
                    # is stubbed; the new journal/source checks run for real.
                    parent={'path':'fixture-only-parent','sha256':'fixture'};m.meta(db,'nativeLocalRetention',parent)
                    def final_validation(module,handle,target):
                        self.assertTrue(handle.in_transaction);self.assertEqual(m.meta(handle,'nativeComposeCursor')['phase'],'FINALIZING')
                        c.validate_source_local_retentions(module,handle,source_files_validator=lambda files:None)
                        self.assertEqual(real(module,handle).owner_domain('.',owner),domain)
                    with patch.object(n,'validate_resume',side_effect=final_validation) as validation:
                        n.checkpoint(m,db,'FINALIZING');validation.assert_called_once()
                        self.assertTrue(db.in_transaction);db.commit()
                    event=m.json.loads(db.execute('SELECT data FROM source_local_retentions').fetchone()[0]);self.assertEqual(event['sourceZone']['minimum'],[208,128,-656]);self.assertEqual(event['processedRowsReset'],0)
                    self.assertEqual({row['object']['uuid'] for row in event['originalDomains']},{owner,donor});self.assertEqual(m.meta(db,'nativeComposeCursor'),{**cursor,'phase':'FINALIZING'})
                    self.assertFalse(db.execute('SELECT 1 FROM activated_native').fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments').fetchone());self.assertFalse(db.execute('SELECT 1 FROM bridge_cells').fetchone())
                    self.assertEqual(db.execute('SELECT x,y,z,processed FROM cell_queue').fetchall(),[(*shared,1)]);self.assertEqual(db.execute('SELECT uuid FROM selected_objects').fetchall(),[(other,)]);self.assertEqual(db.execute('SELECT uuid FROM fragments').fetchall(),[(other,)])
                    self.assertEqual(db.execute('SELECT * FROM objects ORDER BY uuid').fetchall(),saved_objects);self.assertEqual(db.execute('SELECT * FROM source_visual_domains').fetchall(),saved_domains)
                    self.assertTrue(all(row in db.execute('SELECT * FROM protected_cells').fetchall() for row in protected_before))
                    self.assertEqual(real(m,db).owner_domain('.',owner),domain);self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,root_be))),root_bytes);self.assertEqual(m.sha(old/'plan.sqlite'),source_sha)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
            finally:db.close()


if __name__=='__main__':unittest.main()
