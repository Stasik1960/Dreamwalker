"""One new fixture: seed a not-yet-activated visual crossing upfront."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class OriginalSectionPendingCrossingTests(unittest.TestCase):
    def test_pending_outside_root_is_retained_without_visual_neighbor_walk(self):
        with tempfile.TemporaryDirectory(prefix='pending-section-crossing-') as directory:
            plan=Path(directory);old=plan/'old';old.mkdir();source=sqlite3.connect(old/'plan.sqlite')
            for table in ('members','stored_contributions'):source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            source.commit();source.close();old_sha=m.sha(old/'plan.sqlite');stat=(old/'plan.sqlite').stat();db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA)
            try:
                binding={'oldPlan':str(old),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'fixture','plannerBindingSha256':'fixture'};m.meta(db,'binding',binding);db.execute('ATTACH DATABASE ? AS old',('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',))
                classification=plan/'classification.json';m.atomic_json(classification,{'protectedRegistryIds':[]});m.meta(db,'classification',n.ref(m,classification));cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':1,'rowsProcessedSinceAdmission':1024,'retention':None};m.meta(db,'nativeComposeCursor',cursor)
                objects={};envelopes={};states={};bes={};origins={}
                def seed(uid,root,kind,visual):
                    data=m.canonical({'registryId':'fixture:'+uid,'properties':{}});pose='sp_'+m.digest(m.json.loads(data))[:24];db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,data));obj=dict(zip(('dim','uuid','pose','x','y','z','kind','reason','source_status'),('.',uid,pose,*root,kind,'original-unchanged','unknown_pose')));db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()));objects[uid]=obj;states[root]={'registryId':'fixture:'+uid,'properties':{}};envelopes[pose]={'resource':{'path':'immutable-envelope','sha256':'fixture'},'cells':[(0,0,0)],'openable':False,'functionalDoor':False}
                    for cell in visual:db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(pose,*(cell[i]-root[i] for i in range(3))))
                    return obj
                contact=(-1,-1,-1);actor_uid=m.canonical([1,2,3,4]);pending_uid=m.canonical([5,6,7,8]);actor_root=(4,-1,-1);pending_root=(8,-2,-2)
                actor=seed(actor_uid,actor_root,'substrate',[actor_root,contact]);pending=seed(pending_uid,pending_root,'substrate',[pending_root,(-1,-2,-2),(7,-2,-2)]);protected=seed('opaque-original',contact,'preserve',[contact]);neighbor=seed('outside-visual-neighbor',(9,-2,-2),'static',[(7,-2,-2),(9,-2,-2)])
                bes[contact]={'id':m.Tag(8,'fixture:opaque-private'),'private':m.Tag(10,{'keep':m.Tag(11,[3,1,4])})};raw_be=m.encode_nbt(m.NbtFile('',m.Tag(10,bes[contact])))
                for uid,root,key in ((actor_uid,actor_root,'p-actor'),(pending_uid,pending_root,'p-pending')):origins[root]=[{'uuid':uid,'legacyPose':key,'root':list(root),'member':True,'object':[objects[uid]]}]
                db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',*contact,protected['uuid'],'functional_guest'))
                db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(neighbor[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                for cell in ((7,-2,-2),(9,-2,-2)):
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*cell,neighbor['uuid'],neighbor['pose'],cell[0]-9,0,0));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',*cell))
                for root in (actor_root,pending_root):db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',*root))
                export=plan/'exports.sqlite';edb=sqlite3.connect(export);edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');edb.execute('CREATE TABLE native_fragments(state TEXT,x INTEGER,y INTEGER,z INTEGER)');edb.commit();edb.close();m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)});db.commit()
                neighbor_before=n.row_dict(db,'SELECT * FROM selected_objects',());failure=RuntimeError('Protected source contact\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':'.','position':list(contact),'incoming':{'kind':'legacy_native','uuid':actor_uid,'pose':actor['pose'],'root':list(actor_root)}}))
                def source_point(context,dimension,cell):
                    cell=tuple(cell);return copy.deepcopy(states.get(cell,m.AIR)),bes.get(cell),{'fixture':'immutable-source-section','chunk':[cell[0]>>4,cell[2]>>4]}
                def envelope(context,pose,data):return copy.deepcopy(envelopes[pose])
                def authority(context,pose):return ({'className':'fixture:unknown-opaque'},{'fixture':'cap'},{'essentialMask':[[0,0,0]],'support':{'required':False}},{'fixture':'spec'})
                def native_origins(context,dimension,cell,state):return copy.deepcopy(origins.get(tuple(cell),[]))
                real=c.SourceVisualReservations
                with patch.object(real,'source_point',source_point),patch.object(real,'envelope',envelope),patch.object(real,'authority',authority),patch.object(real,'native_origins',native_origins),patch.object(real,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})):
                    # P has no root, actual helper, source ledger, protected row,
                    # activation or derived visual row in the initial section.
                    self.assertFalse(any(row['uuid']==pending_uid for cell,rows in origins.items() if all(-16<=value<=-1 for value in cell) for row in rows))
                    for table in ('members','stored_contributions'):self.assertFalse(db.execute('SELECT 1 FROM old.'+table+' WHERE uuid=?',(pending_uid,)).fetchone())
                    self.assertFalse(db.execute('SELECT 1 FROM activated_native WHERE uuid=?',(pending_uid,)).fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments WHERE uuid=?',(pending_uid,)).fetchone())
                    self.assertTrue(c.automatic_source_zone_retention(m,db,real(m,db),'.',actor_root,states[actor_root],failure));event=m.json.loads(db.execute('SELECT data FROM source_local_retentions').fetchone()[0]);self.assertEqual(event['sourceZone']['minimum'],[-16,-16,-16])
                    self.assertEqual({domain['object']['uuid'] for domain in event['originalDomains']},{actor_uid,pending_uid,protected['uuid']});self.assertEqual(event['zoneObjectWorkBefore'][pending_uid]['activated_native'],[]);self.assertEqual(event['zoneObjectWorkBefore'][pending_uid]['substrate_fragments'],[]);self.assertEqual(event['zoneObjectWorkBefore'][pending_uid]['selected'],[])
                    self.assertEqual(n.row_dict(db,'SELECT * FROM selected_objects',()),neighbor_before);self.assertEqual(db.execute('SELECT x,y,z,processed FROM cell_queue ORDER BY x,y,z').fetchall(),[(7,-2,-2,0),(9,-2,-2,0)]);self.assertEqual({row[0] for row in db.execute('SELECT uuid FROM fragments')},{neighbor['uuid']});self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    context=real(m,db)
                    with patch.object(m,'native_roots_at',side_effect=AssertionError('Retained pending crossing must not reactivate')):self.assertEqual(c.pending_native_roots(context,'.',pending_root,states[pending_root],None),[])
                    with self.assertRaises(RuntimeError):n.check_native_activation(m,db,'.',pending_uid)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);self.assertEqual(m.sha(old/'plan.sqlite'),old_sha);self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,bes[contact]))),raw_be);self.assertEqual(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',('.',pending_uid)),[pending])
            finally:db.close()


if __name__=='__main__':unittest.main()
