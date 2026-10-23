"""One new combined source-only fixture: opaque P, pending C, WHOLE and wide."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class CommonOriginalFallbackTests(unittest.TestCase):
    def test_pending_native_opaque_foreign_data_whole_wide_and_actual_helper_index(self):
        with tempfile.TemporaryDirectory(prefix='common-original-fallback-') as directory:
            plan=Path(directory);old=plan/'old';old.mkdir();source=sqlite3.connect(old/'plan.sqlite')
            for table in ('members','stored_contributions'):source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            p='foreign-private-P';cid=m.canonical([1,2,3,4]);d='unsupported-private-D';wroot=(55,0,0);w=m.synthetic_uuid('.',wroot,'p-whole')
            for at in ((1,0,-2),(1,1,-2)):source.execute('INSERT INTO stored_contributions VALUES(?,?,?,?,?)',('.',*at,d))
            source.commit();source.close();original_hash=m.sha(old/'plan.sqlite');stat=(old/'plan.sqlite').stat();db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA);contexts=[]
            try:
                binding={'oldPlan':str(old),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'fixture','plannerBindingSha256':'fixture'};m.meta(db,'binding',binding)
                db.execute('ATTACH DATABASE ? AS old',('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',));cl=plan/'classification.json';m.atomic_json(cl,{'protectedRegistryIds':['fixture:v12']});m.meta(db,'classification',n.ref(m,cl))
                cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':234,'rowsProcessedSinceAdmission':239616,'retention':None};m.meta(db,'nativeComposeCursor',cursor)
                objects={};members={};states={};bes={};envelopes={};extra_origins={}
                def seed(uid,root,kind,cells):
                    data=m.canonical({'registryId':'fixture:'+uid,'properties':{}});pose='sp_'+m.digest(m.json.loads(data))[:24];db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,data));obj={'dim':'.','uuid':uid,'pose':pose,'x':root[0],'y':root[1],'z':root[2],'kind':kind,'reason':'original_reason_unchanged','source_status':'private_payload' if uid in (p,d) else 'unknown_pose'};db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()));objects[uid]=obj;members[uid]=set(cells)
                    envelopes[pose]={'resource':{'path':'bound-source-envelope','sha256':'fixture'},'cells':[tuple(at[i]-root[i] for i in range(3)) for at in sorted(cells)],'openable':False,'functionalDoor':False};states[tuple(root)]={'registryId':'fixture:'+uid,'properties':{}}
                    return obj
                pcells={(x,y,0) for x in (-1,0,1) for y in (0,1,2)};pobj=seed(p,(0,1,0),'preserve',pcells);croot=(1,-1,-1);co=seed(cid,croot,'substrate',{croot});do=seed(d,(1,0,-2),'static',{(1,0,-2),(1,1,-2)})
                for at in pcells:states[at]={'registryId':'fixture:unknown-P-helper','properties':{}};bes[at]={'id':m.Tag(8,'fixture:private_unknown_BE'),'PrivateGameData':m.Tag(10,{'opaque':m.Tag(8,'never interpret or clean')})};db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',*at,p,'standalone_whole_sprite_tree_confirmed_actual_planes'))
                bes[(1,0,-2)]={'id':m.Tag(8,'fixture:unsupported_D_BE'),'UnrecognisedPayload':m.Tag(10,{'keep':m.Tag(8,'exact bytes')})}
                db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(do[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                dvisual={(x,y,z) for x in range(3) for y in (-1,0,1) for z in (-3,-2,-1)}
                for at in sorted(dvisual):db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*at,d,do['pose'],at[0]-1,at[1],at[2]+2));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',*at))
                cvisual={(x,y,z) for x in range(3) for y in (-2,-1,0) for z in (-1,0)}
                for at in sorted(cvisual):db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(co['pose'],at[0]-1,at[1]+1,at[2]+1))
                for at in members[d]:db.execute('INSERT INTO source_visual_owner_members VALUES(?,?,?,?,?,?)',('.',d,'stored_contributions',*at))
                identity=c.source_visual_member_identity(m,db);m.meta(db,'sourceVisualMemberIndex',{'schemaVersion':1,'kind':'DERIVED_SOURCE_OWNER_MEMBER_POSITIONS','sourceIdentity':identity,'copiedCounts':{'stored_contributions':2,'members':0},'rows':2})
                wide_state={'registryId':'fixture:wide-native-state','properties':{}};export=plan/'exports.sqlite';edb=sqlite3.connect(export);edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)');edb.execute('CREATE TABLE native_fragments(state TEXT,x INTEGER,y INTEGER,z INTEGER)');edb.execute('INSERT INTO native VALUES(?,?)',(m.canonical(wide_state),m.canonical({'nativeVisualComplete':True})))
                for x in range(3):edb.execute('INSERT INTO native_fragments VALUES(?,?,?,?)',(m.canonical(wide_state),x,0,0))
                edb.commit();edb.close();m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)});db.commit()
                protected=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',0,0,0))[0]
                incoming={'kind':'legacy_native','uuid':cid,'pose':co['pose'],'root':list(croot)}
                failure=RuntimeError('Required native visual overhang meets a protected local object\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':'.','position':[0,0,0],'protected':protected,'incoming':incoming,'reason':'Unknown retained CompositeRoot mechanism'}))
                def source_point(context,dim,at):return copy.deepcopy(states.get(tuple(at),m.AIR)),bes.get(tuple(at)),{'fixture':'immutable-source-cache'}
                def envelope(context,pose,data):
                    return copy.deepcopy(envelopes[pose])
                def authority(context,pose):return ({'className':'fixture:unknown_storage'},{'fixture':'cap'},{'essentialMask':[[0,0,0]],'support':{'required':False}},{'fixture':'spec'})
                def origins(context,dim,at,state):
                    at=tuple(at)
                    if at in extra_origins:return extra_origins[at]
                    if at==croot:return [{'uuid':cid,'legacyPose':'p-c','root':list(croot),'member':True,'object':[co]}]
                    if w in objects and (at in members[w] or at==(60,0,0)):return [{'uuid':w,'legacyPose':'p-whole','root':list(wroot),'member':at in members[w],'object':[objects[w]]}]
                    if at in ((80,0,0),(81,0,0)):return [{'uuid':helper_uid,'legacyPose':'p-unknown','root':[80,0,0],'member':True,'object':[]}]
                    return []
                real=c.SourceVisualReservations
                def factory(*args):result=real(*args);contexts.append(result);return result
                with patch.object(real,'source_point',source_point),patch.object(real,'envelope',envelope),patch.object(real,'authority',authority),patch.object(real,'native_origins',origins),patch.object(real,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})),patch.object(real,'owner_domain',side_effect=AssertionError('No conversion-class or foreign runtime proof in original fallback')),patch.object(c,'SourceVisualReservations',side_effect=factory):
                    root_bytes=m.encode_nbt(m.NbtFile('',m.Tag(10,bes[(0,1,0)])));data_bytes=m.encode_nbt(m.NbtFile('',m.Tag(10,bes[(1,0,-2)])))
                    before=n.counts(db);real_meta=m.meta
                    def crash(handle,key,value=None):
                        if key=='sourceLocalRetentionJournal' and value is not None:raise RuntimeError('common original journal before commit')
                        return real_meta(handle,key,value)
                    with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.automatic_direct_native_retention(m,db,factory(m,db),'.',croot,states[croot],failure)
                    self.assertEqual(n.counts(db),before);self.assertFalse(db.execute('SELECT 1 FROM activated_native').fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments').fetchone())
                    self.assertTrue(c.automatic_direct_native_retention(m,db,factory(m,db),'.',croot,states[croot],failure))
                    event=m.json.loads(db.execute('SELECT data FROM source_local_retentions WHERE sequence=1').fetchone()[0]);self.assertEqual({row['object']['uuid'] for row in event['originalDomains']},{p,cid,d});self.assertEqual(event['protectedContact']['object'],pobj);self.assertIsNone(event['retractedNatives'][cid]['activatedRow']);self.assertEqual(event['retractedNatives'][cid]['substrateFragments'],[])
                    pdomain=next(row for row in event['originalDomains'] if row['object']['uuid']==p);self.assertEqual({tuple(row['position']) for row in pdomain['domain']},pcells);self.assertEqual(pdomain['rootBeDigest'],real.be_digest(contexts[-1],bes[(0,1,0)]));self.assertTrue(all(row['beDigest'] for row in pdomain['domain']))
                    self.assertEqual(len([row for row in event['pointPlan'] if row['retire']]),27);self.assertFalse(db.execute('SELECT 1 FROM cell_queue').fetchone());self.assertFalse(db.execute('SELECT 1 FROM activated_native').fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments').fetchone());self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,bes[(0,1,0)]))),root_bytes);self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,bes[(1,0,-2)]))),data_bytes)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    # Previously supported WHOLE route now uses the same journal,
                    # preserves its source-native component and deletes all roles.
                    wo=seed(w,wroot,'whole',{(55,y,0) for y in (0,1,2)});db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(wo[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                    for at in members[w]-{wroot}:states[at]={'registryId':'fixture:old-whole-native-helper','properties':{}}
                    for x in range(51,66):db.execute('INSERT INTO whole_cells VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,w,'shared_exact_template',x-55,0,0));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',x,0,0))
                    states[(50,0,0)]=wide_state;db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',50,0,0));db.commit()
                    wide={'kind':'wide_native','root':[50,0,0],'state':wide_state};context=factory(m,db)
                    with self.assertRaises(RuntimeError) as raised:c.check_whole_native_contact(context,'.',(51,0,0),wide)
                    self.assertTrue(c.automatic_direct_native_retention(m,db,context,'.',(50,0,0),wide_state,raised.exception))
                    self.assertFalse(db.execute('SELECT 1 FROM whole_cells').fetchone());self.assertFalse(db.execute('SELECT 1 FROM selected_objects').fetchone());self.assertFalse(db.execute('SELECT 1 FROM cell_queue').fetchone());self.assertEqual(db.execute('SELECT kind FROM objects WHERE uuid=?',(w,)).fetchone(),('whole',))
                    context=factory(m,db);self.assertIn(('.',w),context.local_native_domains());self.assertIn(('.',50,0,0),context.local_native_cells())
                    with patch.object(m,'native_roots_at',side_effect=AssertionError('Retained WHOLE visual must be filtered before old kind guard')):self.assertEqual(c.pending_native_roots(context,'.',(60,0,0),m.AIR,None),[])
                    with self.assertRaisesRegex(RuntimeError,'retained/protected'):c.check_retained_native_member(context,'.',(55,1,0),states.get((55,1,0),m.AIR))
                    with self.assertRaisesRegex(RuntimeError,'original native cell'):c.check_retained_native_member(context,'.',(50,0,0),wide_state)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);head=m.meta(db,'sourceLocalRetentionJournal');c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None);self.assertEqual(m.meta(db,'sourceLocalRetentionJournal'),head)
                    # Actual cached helpers remain included even when a bound
                    # current envelope omits them; visual pose_cells are not
                    # an authority for historic saved source data.
                    helper_uid=m.synthetic_uuid('.',(80,0,0),'p-unknown');unknown=seed(helper_uid,(80,0,0),'substrate',{(80,0,0)});pose=m.json.loads(db.execute('SELECT data FROM poses WHERE key=?',(unknown['pose'],)).fetchone()[0]);db.commit()
                    states[(80,0,0)]={'registryId':'fixture:actual-native-root','properties':{}};states[(81,0,0)]={'registryId':'fixture:actual-native-helper','properties':{}}
                    catalog={'poses':[{'key':'p-unknown',**pose}],'recipes':[{'fragments':[{'pose':'p-unknown','origin':[-x,0,0],'member':True}]} for x in range(3)]}
                    context=factory(m,db)
                    with patch.object(m,'legacy_native_catalog',return_value=(catalog,{})),patch.object(context,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}):
                        cells,proof=context.source_native_members('.',unknown,pose);self.assertEqual(cells,{(80,0,0),(81,0,0)});self.assertEqual(proof['actualCachedMemberCells'],[[80,0,0],[81,0,0]])
                        _,saved,authority=context.original_members('.',helper_uid);self.assertEqual(saved,{(80,0,0),(81,0,0)});self.assertEqual(authority['runtimeFootprintEnvelope']['cells'],[[0,0,0]]);self.assertEqual(authority['actualNativeSourceMembers'],proof)
                        self.assertEqual({tuple(row['position']) for row in context.original_domain('.',helper_uid)['domain']},saved)
                        db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',81,0,0));db.commit()
                        with self.assertRaisesRegex(RuntimeError,'replacement work'):context.original_domain('.',helper_uid)
                        db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',('.',81,0,0));db.commit()
                    # Physical D -> F -> WHOLE -> active native E -> static H
                    # closes both groups. Visual-only shared G stays selected.
                    np='opaque-second-P';nc=m.canonical([11,12,13,14]);nd='local-D';nf='local-F';nw='local-W';ng='visual-only-G';ne=m.synthetic_uuid('.',(105,3,0),'p-local-E');nh='local-H'
                    npo=seed(np,(100,2,0),'preserve',{(100,2,0)});nco=seed(nc,(101,0,0),'substrate',{(101,0,0)});ndo=seed(nd,(101,1,0),'static',{(101,1,0),(102,1,0)});nfo=seed(nf,(102,2,0),'static',{(102,2,0)});nwo=seed(nw,(102,3,0),'whole',{(102,3,0)});ngo=seed(ng,(104,1,0),'static',{(104,1,0)});neo=seed(ne,(105,3,0),'substrate',{(105,3,0)});nho=seed(nh,(106,3,0),'static',{(106,3,0)})
                    db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',100,2,0,np,'functional_guest'));bes[(100,2,0)]={'id':m.Tag(8,'fixture:opaque_new_P'),'private':m.Tag(8,'unchanged')}
                    extra_origins[(101,0,0)]=[{'uuid':nc,'legacyPose':'p-next-c','root':[101,0,0],'member':True,'object':[nco]}]
                    extra_origins[(105,3,0)]=[{'uuid':ne,'legacyPose':'p-local-E','root':[105,3,0],'member':True,'object':[neo]}]
                    for at in ((100,2,0),(101,0,0)):db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(nco['pose'],at[0]-101,at[1],at[2]))
                    works={nd:[(101,0,0),(101,1,0),(102,1,0),(103,1,0)],nf:[(102,1,0),(102,2,0)],nw:[(102,2,0),(102,3,0)],ng:[(103,1,0),(104,1,0)],nh:[(105,3,0),(106,3,0)]}
                    tail_ids=set()
                    for index in range(6):
                        uid='local-tail-'+str(index);tail_ids.add(uid);seed(uid,(107+index,3,0),'static',{(107+index,3,0)});works[uid]=[(106+index,3,0),(107+index,3,0)]
                    for uid,points in works.items():
                        obj=objects[uid];db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(obj[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                        for cell in points:
                            if obj['kind']=='whole':db.execute('INSERT INTO whole_cells VALUES(?,?,?,?,?,?,?,?,?)',('.',*cell,uid,'exact_local_whole',cell[0]-obj['x'],cell[1]-obj['y'],cell[2]-obj['z']))
                            else:db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*cell,uid,obj['pose'],cell[0]-obj['x'],cell[1]-obj['y'],cell[2]-obj['z']))
                            db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,0)',('.',*cell))
                    db.execute('INSERT INTO activated_native VALUES(?,?)',('.',ne))
                    for cell in ((102,3,0),(105,3,0),(103,1,0)):
                        offset=tuple(cell[i]-neo[key] for i,key in enumerate(('x','y','z')));db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(neo['pose'],*offset));db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*cell,ne,neo['pose'],*offset));db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,1)',('.',*cell))
                    db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?',('.',105,3,0))
                    db.commit();next_protected=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',100,2,0))[0]
                    next_failure=RuntimeError('Required native visual overhang meets a protected local object\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':'.','position':[100,2,0],'protected':next_protected,'incoming':{'kind':'legacy_native','uuid':nc,'pose':nco['pose'],'root':[101,0,0]}}))
                    before_bound=n.counts(db);head_before_bound=m.meta(db,'sourceLocalRetentionJournal');real_point=c.point
                    def too_many_direct_owners(module,handle,dimension,cell):
                        saved=real_point(module,handle,dimension,cell)
                        if tuple(cell)==(100,2,0):saved['fragments']=[{'uuid':'over-local-owner-cap-'+str(index)} for index in range(4097)]
                        return saved
                    with patch.object(c,'point',side_effect=too_many_direct_owners),self.assertRaisesRegex(RuntimeError,'bounded'):c.automatic_direct_native_retention(m,db,factory(m,db),'.',(101,0,0),states[(101,0,0)],next_failure)
                    self.assertEqual(n.counts(db),before_bound);self.assertEqual(m.meta(db,'sourceLocalRetentionJournal'),head_before_bound);self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor);self.assertFalse(db.in_transaction)
                    self.assertTrue(c.automatic_direct_native_retention(m,db,factory(m,db),'.',(101,0,0),states[(101,0,0)],next_failure))
                    next_event=m.json.loads(db.execute('SELECT data FROM source_local_retentions WHERE sequence=3').fetchone()[0]);self.assertEqual(set(next_event['selectedOwnersBefore']),{nd,nf,nw,nh}|tail_ids);self.assertGreater(len(next_event['selectedOwnersBefore']),8);self.assertEqual({row['object']['uuid'] for row in next_event['originalDomains']},{np,nc,nd,nf,nw,ne,nh}|tail_ids);self.assertEqual(next_event['retractedNatives'][ne]['activatedRow'],{'dim':'.','uuid':ne});self.assertEqual(len(next_event['retractedNatives'][ne]['substrateFragments']),3);self.assertFalse(db.execute('SELECT 1 FROM activated_native').fetchone());self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments').fetchone())
                    retired_native_root=next(row for row in next_event['pointPlan'] if row['position']==[105,3,0]);self.assertTrue(retired_native_root['retire']);self.assertEqual(retired_native_root['before']['queue'],[{'processed':1}])
                    self.assertEqual(n.row_dict(db,'SELECT * FROM selected_objects',()),[{key:ngo[key] for key in ('dim','uuid','pose','x','y','z','kind')}]);self.assertEqual(db.execute('SELECT x,y,z,processed FROM cell_queue ORDER BY x,y,z').fetchall(),[(103,1,0,0),(104,1,0,0)]);self.assertEqual({row['uuid'] for row in n.row_dict(db,'SELECT * FROM fragments',())},{ng});self.assertFalse(db.execute('SELECT 1 FROM whole_cells').fetchone());self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    # Re-contact an already retained substrate, then an opaque
                    # object kind: unchanged originals need no conversion kind.
                    opaque='opaque-protected-object';opaque_obj=seed(opaque,(300,0,0),'opaque_storage_kind',{(300,0,0)});bes[(300,0,0)]={'id':m.Tag(8,'fixture:future_storage'),'private':m.Tag(8,'keep exact bytes')};db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',300,0,0,opaque,'functional_guest'))
                    for index,(protected_uid,target) in enumerate(((ne,(105,3,0)),(opaque,(300,0,0)))):
                        root=(200+50*index,0,0);next_uid=m.canonical([21+index,22,23,24]);decor='recontact-D-'+str(index);source=seed(next_uid,root,'substrate',{root});retained=seed(decor,(root[0]+1,0,0),'static',{(root[0]+1,0,0)})
                        extra_origins[root]=[{'uuid':next_uid,'legacyPose':'p-recontact-'+str(index),'root':list(root),'member':True,'object':[source]}]
                        for cell in (target,root):db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)',(source['pose'],cell[0]-root[0],cell[1]-root[1],cell[2]-root[2]))
                        db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(retained[key] for key in ('dim','uuid','pose','x','y','z','kind')))
                        for cell in (root,(root[0]+1,0,0)):
                            db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*cell,decor,retained['pose'],cell[0]-retained['x'],0,0));db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',*cell))
                        db.commit();provenance=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',('.',*target,protected_uid))[0]
                        repeated=RuntimeError('Required native visual overhang meets a protected local object\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':'.','position':list(target),'protected':provenance,'incoming':{'kind':'legacy_native','uuid':next_uid,'pose':source['pose'],'root':list(root)}}))
                        self.assertTrue(c.automatic_direct_native_retention(m,db,factory(m,db),'.',root,states[root],repeated));record=m.json.loads(db.execute('SELECT data FROM source_local_retentions WHERE sequence=?',(4+index,)).fetchone()[0]);self.assertEqual(record['protectedContact']['object']['kind'],'substrate' if index==0 else 'opaque_storage_kind');self.assertEqual({row['object']['uuid'] for row in record['originalDomains']},{protected_uid,next_uid,decor});c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    self.assertEqual(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',('.',opaque)),[opaque_obj]);self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    # Unknown/private P was neither selected nor reclassified;
                    # actual protected data stays strict on resume/finalization.
                    self.assertEqual(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',('.',p)),[pobj]);self.assertEqual(m.sha(old/'plan.sqlite'),original_hash)
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',-1,2,0));db.commit()
                    with self.assertRaisesRegex(RuntimeError,'replacement work'):c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    for context in contexts:
                        if context.exports is not None:context.exports.close()
            finally:db.close()


if __name__=='__main__':unittest.main()
