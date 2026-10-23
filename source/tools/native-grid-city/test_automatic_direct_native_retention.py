"""One new affected integration fixture; no accepted suites or active DB."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class AutomaticDirectNativeRetentionTests(unittest.TestCase):
    def test_batch_rollback_direct_retirement_shared_work_journal_and_upgrade_attach(self):
        with tempfile.TemporaryDirectory(prefix='direct-native-local-fixture-') as directory:
            plan=Path(directory);old=plan/'immutable-old';old.mkdir()
            tree=m.canonical([1,2,3,4]);decor=m.canonical([5,6,7,8]);foreign=m.canonical([9,10,11,12])
            original=sqlite3.connect(old/'plan.sqlite')
            for table in ('stored_contributions','members'):
                original.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            for x in (10,11,12):original.execute('INSERT INTO stored_contributions VALUES(?,?,?,?,?)',('.',x,0,0,decor))
            original.commit();original.close();old_hash=m.sha(old/'plan.sqlite');stat=(old/'plan.sqlite').stat()
            db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA)
            try:
                binding={'oldPlan':str(old),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'fixture','plannerBindingSha256':'fixture'}
                m.meta(db,'binding',binding);c.attach_source_journal_original(m,db)  # No journal: deliberately no attach.
                db.execute('ATTACH DATABASE ? AS old',('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',))
                classification=plan/'classification.json';m.atomic_json(classification,{'protectedRegistryIds':[]});m.meta(db,'classification',n.ref(m,classification))
                cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':225,'rowsProcessedSinceAdmission':230400,'retention':None};m.meta(db,'nativeComposeCursor',cursor)
                objects={}
                for uid,x,y,kind in ((tree,1,1,'preserve'),(decor,11,0,'static'),(foreign,30,0,'substrate')):
                    encoded=m.canonical({'registryId':'fixture:'+uid,'properties':{}});pose='sp_'+m.digest(m.json.loads(encoded))[:24]
                    db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,encoded));obj={'dim':'.','uuid':uid,'pose':pose,'x':x,'y':y,'z':0,'kind':kind,'reason':'fixture_original','source_status':'stored'}
                    db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()));objects[uid]=obj
                obj=objects[decor];db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(obj[k] for k in ('dim','uuid','pose','x','y','z','kind')))
                for x in range(21):
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,decor,obj['pose'],x-11,0,0));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,?)',('.',x,0,0,int(x>=14)))
                db.execute('INSERT INTO activated_native VALUES(?,?)',('.',foreign))
                for x in (18,19):db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,foreign,objects[foreign]['pose'],x-30,0,0))
                for x in (18,19,20):db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',x,0,0,'unchanged-independent-host',0,None))
                for x in (10,11,12):db.execute('INSERT INTO source_visual_owner_members VALUES(?,?,?,?,?,?)',('.',decor,'stored_contributions',x,0,0))
                identity=c.source_visual_member_identity(m,db);m.meta(db,'sourceVisualMemberIndex',{'schemaVersion':1,'kind':'DERIVED_SOURCE_OWNER_MEMBER_POSITIONS','sourceIdentity':identity,'copiedCounts':{'stored_contributions':3,'members':0},'rows':3})
                export=plan/'exports.sqlite';edb=sqlite3.connect(export);edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');edb.commit();edb.close();m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)});db.commit()
                tree_cells={(1,y,z) for y in (0,1,2) for z in (-1,0,1)};decor_cells={(x,0,0) for x in (10,11,12)}
                tree_state={'registryId':'fixture:native-tree','properties':{}};decor_state={'registryId':'fixture:'+decor,'properties':{}}
                be={'id':m.Tag(8,'bloodborne_dw:composite_cell'),'resident':m.Tag(10,{'uuid':m.Tag(11,[5,6,7,8]),'root':m.Tag(11,[11,0,0]),'id':m.Tag(8,decor_state['registryId'])}),'payload':m.Tag(10,{'ImportedTypeVersion':m.Tag(3,1)})}
                def source(context,dim,at):
                    at=tuple(at);state=tree_state if at in tree_cells else decor_state if at==(11,0,0) else m.AIR
                    return copy.deepcopy(state),be if at==(11,0,0) else None,{'fixture':'immutable-cached-source'}
                def envelope(context,pose,encoded):
                    is_tree=pose==objects[tree]['pose'];cells=[(0,y,z) for y in (-1,0,1) for z in (-1,0,1)] if is_tree else [(-1,0,0),(0,0,0),(1,0,0)]
                    return {'resource':{'path':'runtime-derived-fixture','sha256':'fixture'},'format':'native_footprint_proof' if is_tree else 'source_geometry','cells':cells,'openable':False,'functionalDoor':False}
                def authority(context,pose):return ({'className':'dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock'},{'fixture':'cap'},{'essentialMask':[[0,0,0]],'support':{'required':False}},{'fixture':'spec'})
                def origins(context,dim,at,state):
                    if tuple(at) in tree_cells:return [{'uuid':tree,'root':[1,1,0],'member':True,'object':[objects[tree]]}]
                    # x20 has NO explicit fragments: its active native empty root
                    # still requires the completed queue row and bridge to remain.
                    if tuple(at) in {(x,0,0) for x in (18,19,20)}:return [{'uuid':foreign,'root':[30,0,0],'member':True,'object':[objects[foreign]]}]
                    return []
                contexts=[];real_context=c.SourceVisualReservations
                def factory(*args):
                    result=real_context(*args);contexts.append(result);return result
                failure=RuntimeError('A consumed native helper belongs to a retained/protected local object: '+str(('.',tree,'preserve','fixture_original')))
                def make_context():return factory(m,db)
                with patch.object(real_context,'source_point',source),patch.object(real_context,'envelope',envelope),patch.object(real_context,'authority',authority),patch.object(real_context,'native_origins',origins),patch.object(real_context,'native_tree_envelope',lambda self,encoded:envelope(self,objects[tree]['pose'],encoded)),patch.object(c,'SourceVisualReservations',side_effect=factory):
                    context=make_context()
                    with self.assertRaisesRegex(RuntimeError,'retained/protected'):c.check_retained_native_member(context,'.',(1,0,0),tree_state)
                    # A real foreign physical demand blocks without a recursive
                    # retention of E; the exact transaction rolls back.
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',12,0,0,foreign,objects[foreign]['pose'],0,0,0));db.commit();before=n.counts(db)
                    with self.assertRaisesRegex(RuntimeError,'Independent demand'):c.automatic_direct_native_retention(m,db,context,'.',(1,0,0),tree_state,failure)
                    self.assertEqual(n.counts(db),before);db.execute('DELETE FROM fragments WHERE uuid=?',(foreign,));db.commit()
                    real_meta=m.meta
                    def crash(handle,key,value=None):
                        if key=='sourceLocalRetentionJournal' and value is not None:raise RuntimeError('fixture local journal before commit')
                        return real_meta(handle,key,value)
                    before=n.counts(db)
                    with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.automatic_direct_native_retention(m,db,make_context(),'.',(1,0,0),tree_state,failure)
                    self.assertEqual(n.counts(db),before);self.assertIsNone(m.meta(db,'sourceLocalRetentionJournal'))
                    touched=[]
                    def roots(handle,dim,at,actual,native=None):
                        touched.append(tuple(at))
                        if tuple(at)==(0,0,0):m.meta(handle,'sourceVisualReservationJournal',{'rolledBackOnly':True});contexts[-1].strict={('stale-batch',)}
                        if tuple(at)==(1,0,0):raise failure
                        return []
                    def checkpoint(api,handle,phase,processed=0):
                        self.assertEqual(phase,'FINALIZING');self.assertEqual(processed,0)
                        raise RuntimeError('fixture reached clean finalization boundary')
                    empty=m.NbtFile('',m.Tag(10,{}))
                    with patch.object(m,'get_source_chunk',return_value=(empty,'fixture','fixture')),patch.object(m,'source_state',side_effect=lambda sections,at:source(context,'.',at)[0]),patch.object(m,'native_roots_at',side_effect=roots),patch.object(n,'checkpoint',side_effect=checkpoint):
                        with self.assertRaisesRegex(RuntimeError,'clean finalization'):m.compose_native_tail(db,plan,None,0)
                    self.assertEqual(touched,[(0,0,0),(1,0,0)]);self.assertGreaterEqual(len(contexts),3);self.assertIsNone(contexts[-1].strict)
                    self.assertIsNone(m.meta(db,'sourceVisualReservationJournal'));self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    self.assertEqual(db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(18,1),(19,1),(20,1)])
                    self.assertEqual(db.execute('SELECT x FROM bridge_cells ORDER BY x').fetchall(),[(18,),(19,),(20,)])
                    self.assertEqual(db.execute('SELECT count(*) FROM substrate_fragments').fetchone(),(2,));self.assertEqual(db.execute('SELECT * FROM activated_native').fetchall(),[('.',foreign)])
                    self.assertFalse(db.execute('SELECT 1 FROM selected_objects').fetchone());self.assertFalse(db.execute('SELECT 1 FROM fragments').fetchone())
                    event=m.json.loads(db.execute('SELECT data FROM source_local_retentions').fetchone()[0]);retired=[row for row in event['pointPlan'] if row['retire']]
                    self.assertEqual(sum(row['before']['queue']==[{'processed':0}] for row in retired),14);self.assertEqual(sum(row['before']['queue']==[{'processed':1}] for row in retired),4)
                    for cell in tree_cells|decor_cells:self.assertTrue(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',*cell)).fetchone())
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);head=m.meta(db,'sourceLocalRetentionJournal')
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);self.assertEqual(m.meta(db,'sourceLocalRetentionJournal'),head)
                    self.assertTrue(make_context().strict_original('.',(11,0,0)))
                    # Actual source-upgrade entry opens a fresh ordinary handle.
                    # It must attach OLD read-only before any journal validation.
                    def upgrade_preview(args,api,handle):
                        self.assertEqual([row[1] for row in handle.execute('PRAGMA database_list')].count('old'),1)
                        c.validate_source_local_retentions(api,handle,source_files_validator=lambda files:None)
                        with self.assertRaises(sqlite3.OperationalError):handle.execute("DELETE FROM old.stored_contributions")
                        return {'fixture':'journal validated on real upgrade handle','head':api.meta(handle,'sourceLocalRetentionJournal')}
                    args=SimpleNamespace(plan=plan,execute=False,preview=plan/'upgrade-preview.json')
                    with patch.object(c,'upgrade_preview',side_effect=upgrade_preview):c.upgrade(args,m)
                    self.assertEqual(m.read(args.preview)['head'],head);self.assertEqual(m.sha(old/'plan.sqlite'),old_hash)
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',11,0,0));db.commit()
                    with self.assertRaisesRegex(RuntimeError,'replacement work'):c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    for context in contexts:
                        if context.exports is not None:context.exports.close()
            finally:db.close()


if __name__=='__main__':unittest.main()
