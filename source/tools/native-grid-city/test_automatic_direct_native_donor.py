"""Only the new bounded native-donor + directly conflicting static branch."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class AutomaticNativeDonorTests(unittest.TestCase):
    def test_native_original_retraction_static_contact_residual_and_visual_skip(self):
        with tempfile.TemporaryDirectory(prefix='direct-native-donor-fixture-') as directory:
            plan=Path(directory);old=plan/'old';old.mkdir();tree=m.canonical([1,2,3,4]);native=m.canonical([5,6,7,8]);decor=m.canonical([9,10,11,12]);foreign='independent-native'
            source=sqlite3.connect(old/'plan.sqlite')
            for table in ('members','stored_contributions'):source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            for z in (-1,0,1):source.execute('INSERT INTO stored_contributions VALUES(?,?,?,?,?)',('.',10,0,z,decor))
            source.commit();source.close();stat=(old/'plan.sqlite').stat();old_hash=m.sha(old/'plan.sqlite')
            db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA);contexts=[]
            try:
                m.meta(db,'binding',{'oldPlan':str(old),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'fixture','plannerBindingSha256':'fixture'})
                db.execute('ATTACH DATABASE ? AS old',('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',))
                cls=plan/'classification.json';m.atomic_json(cls,{'protectedRegistryIds':[]});m.meta(db,'classification',n.ref(m,cls))
                cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':226,'rowsProcessedSinceAdmission':231424,'retention':None};m.meta(db,'nativeComposeCursor',cursor)
                objects={}
                for uid,root,kind in ((tree,(1,1,0),'preserve'),(native,(10,1,0),'substrate'),(decor,(10,0,0),'static')):
                    data=m.canonical({'registryId':'fixture:'+uid,'properties':{}});pose='sp_'+m.digest(m.json.loads(data))[:24];db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,data))
                    obj={'dim':'.','uuid':uid,'pose':pose,'x':root[0],'y':root[1],'z':root[2],'kind':kind,'reason':'original-kind-unchanged','source_status':'stored'};objects[uid]=obj;db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()))
                e=objects[native];f=objects[decor];db.execute('INSERT INTO activated_native VALUES(?,?)',('.',native));db.execute('INSERT INTO activated_native VALUES(?,?)',('.',foreign));db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(f[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                for x in range(18):
                    db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,native,e['pose'],x-10,-1,0));db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(e['pose'],x-10,-1,0));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,?)',('.',x,0,0,int(x>=14)))
                fvisual={(10,0,z) for z in (-1,0,1)}|{(x,1,0) for x in (9,10,11)}|{(10,2,0)}
                for at in sorted(fvisual):
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*at,decor,f['pose'],at[0]-10,at[1],at[2]));db.execute('INSERT OR REPLACE INTO cell_queue VALUES(?,?,?,?,1)',('.',*at))
                # Independent explicit work and an empty native-root visual slot
                # both survive. Neither foreign footprint is inspected.
                db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',11,1,0,foreign,'foreign-pose',0,0,0))
                for at in ((17,0,0),(11,1,0)):db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',*at,'untouched-existing-host',0,None))
                for z in (-1,0,1):db.execute('INSERT INTO source_visual_owner_members VALUES(?,?,?,?,?,?)',('.',decor,'stored_contributions',10,0,z))
                identity=c.source_visual_member_identity(m,db);m.meta(db,'sourceVisualMemberIndex',{'schemaVersion':1,'kind':'DERIVED_SOURCE_OWNER_MEMBER_POSITIONS','sourceIdentity':identity,'copiedCounts':{'stored_contributions':3,'members':0},'rows':3})
                export=plan/'exports.sqlite';edb=sqlite3.connect(export);edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');edb.commit();edb.close();m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)});db.commit()
                tree_cells={(1,y,z) for y in (0,1,2) for z in (-1,0,1)};native_cells={(10,1,0)};decor_cells={(10,0,z) for z in (-1,0,1)}
                tree_state={'registryId':'fixture:old-native-tree','properties':{}};native_state={'registryId':'fixture:old-native-donor','properties':{}};decor_state={'registryId':'fixture:'+decor,'properties':{}}
                resident={'uuid':m.Tag(11,[9,10,11,12]),'root':m.Tag(11,[10,0,0]),'id':m.Tag(8,decor_state['registryId'])};be={'id':m.Tag(8,'bloodborne_dw:composite_cell'),'resident':m.Tag(10,resident),'payload':m.Tag(10,{'ImportedTypeVersion':m.Tag(3,1)})}
                def source_point(context,dim,at):
                    at=tuple(at);state=tree_state if at in tree_cells else native_state if at in native_cells or at==(20,0,0) else decor_state if at==(10,0,0) else m.AIR
                    return copy.deepcopy(state),be if at==(10,0,0) else None,{'fixture':'source-cache-unchanged'}
                def envelope(context,pose,encoded):
                    cells=[(0,y,z) for y in (-1,0,1) for z in (-1,0,1)] if pose==objects[tree]['pose'] else [(0,0,0)] if pose==e['pose'] else [(0,0,z) for z in (-1,0,1)]
                    return {'resource':{'path':'bound-runtime-envelope','sha256':'fixture'},'cells':cells,'openable':False,'functionalDoor':False}
                def authority(context,pose):return ({'className':'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock'},{'fixture':'cap'},{'essentialMask':[[0,0,0]],'support':{'required':False}},{'fixture':'spec'})
                def origins(context,dim,at,state):
                    at=tuple(at)
                    if at in tree_cells:return [{'uuid':tree,'root':[1,1,0],'member':True,'object':[objects[tree]]}]
                    if at in native_cells or at==(20,0,0):return [{'uuid':native,'root':[10,1,0],'member':at in native_cells,'object':[e]}]
                    if at in ((17,0,0),(11,1,0)):return [{'uuid':foreign,'root':[40,0,0],'member':True,'object':[]}]
                    return []
                real_context=c.SourceVisualReservations
                def factory(*args):
                    result=real_context(*args);contexts.append(result);return result
                failure=RuntimeError('A consumed native helper belongs to a retained/protected local object: '+str(('.',tree,'preserve','original-kind-unchanged')))
                with patch.object(real_context,'source_point',source_point),patch.object(real_context,'envelope',envelope),patch.object(real_context,'authority',authority),patch.object(real_context,'native_origins',origins),patch.object(c,'SourceVisualReservations',side_effect=factory):
                    # A real next native at Fphysical is rejected, never followed.
                    db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',10,0,1,'unfollowed-F','unknown',0,0,0));db.commit();before=n.counts(db)
                    with self.assertRaisesRegex(RuntimeError,'Independent demand'):c.automatic_direct_native_retention(m,db,factory(m,db),'.',(1,0,0),tree_state,failure)
                    self.assertEqual(n.counts(db),before);db.execute("DELETE FROM substrate_fragments WHERE uuid='unfollowed-F'");db.commit()
                    real_meta=m.meta
                    def crash(handle,key,value=None):
                        if key=='sourceLocalRetentionJournal' and value is not None:raise RuntimeError('native donor journal before commit')
                        return real_meta(handle,key,value)
                    before=n.counts(db)
                    with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.automatic_direct_native_retention(m,db,factory(m,db),'.',(1,0,0),tree_state,failure)
                    self.assertEqual(n.counts(db),before);self.assertEqual(db.execute('SELECT count(*) FROM substrate_fragments WHERE uuid=?',(native,)).fetchone(),(18,));self.assertIsNone(m.meta(db,'sourceLocalRetentionJournal'))
                    self.assertTrue(c.automatic_direct_native_retention(m,db,factory(m,db),'.',(1,0,0),tree_state,failure))
                    self.assertEqual(db.execute('SELECT kind FROM objects WHERE uuid=?',(native,)).fetchone(),('substrate',));self.assertFalse(db.execute('SELECT 1 FROM activated_native WHERE uuid=?',(native,)).fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments WHERE uuid=?',(native,)).fetchone());self.assertFalse(db.execute('SELECT 1 FROM selected_objects').fetchone())
                    self.assertEqual(db.execute('SELECT x,y,z,processed FROM cell_queue ORDER BY x,y,z').fetchall(),[(11,1,0,1),(17,0,0,1)]);self.assertEqual(db.execute('SELECT count(*) FROM bridge_cells').fetchone(),(2,));self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    event=m.json.loads(db.execute('SELECT data FROM source_local_retentions').fetchone()[0]);self.assertEqual(len(event['retractedNatives'][native]['substrateFragments']),18);self.assertEqual(len(event['fragmentsBefore'][decor]),7)
                    for at in tree_cells|native_cells|decor_cells:self.assertTrue(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',*at)).fetchone())
                    context=factory(m,db);self.assertEqual(set(context.local_native_domains()),{('.',native)})
                    with self.assertRaisesRegex(RuntimeError,'retained/protected'):c.check_retained_native_member(context,'.',(10,1,0),native_state)
                    with self.assertRaisesRegex(RuntimeError,'cannot be activated'):n.check_native_activation(m,db,'.',native)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);head=m.meta(db,'sourceLocalRetentionJournal');c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);self.assertEqual(m.meta(db,'sourceLocalRetentionJournal'),head)
                    # A later pure-visual recipe reference is skipped in the
                    # ordinary loop; it cannot re-activate or re-retain E.
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',20,0,0));db.commit();empty=m.NbtFile('',m.Tag(10,{}));phases=[]
                    def checkpoint(api,handle,phase,processed=0):
                        phases.append((phase,processed))
                        if phase=='FINALIZING':raise RuntimeError('native-donor fixture clean finalization')
                    with patch.object(m,'get_source_chunk',return_value=(empty,'fixture','fixture')),patch.object(m,'source_state',side_effect=lambda sections,at:source_point(context,'.',at)[0]),patch.object(m,'native_roots_at',return_value=[(native,e['pose'],10,1,0)]),patch.object(n,'check_native_activation',side_effect=AssertionError('retained native must be skipped')),patch.object(n,'checkpoint',side_effect=checkpoint):
                        with self.assertRaisesRegex(RuntimeError,'clean finalization'):m.compose_native_tail(db,plan,None,0)
                    self.assertEqual(phases,[('NATIVE_TRANSFER',1),('FINALIZING',0)]);self.assertEqual(db.execute('SELECT processed FROM cell_queue WHERE x=20').fetchone(),(1,));self.assertEqual(m.meta(db,'sourceLocalRetentionJournal'),head)
                    self.assertEqual(m.sha(old/'plan.sqlite'),old_hash)
                    for context in contexts:
                        if context.exports is not None:context.exports.close()
            finally:db.close()


if __name__=='__main__':unittest.main()
