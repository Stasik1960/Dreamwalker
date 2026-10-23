"""One affected admission fixture; no accepted migration fixture is replayed."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class OriginalSectionProjectionSupersessionTests(unittest.TestCase):
    def test_committed_complete_original_replaces_historical_projection(self):
        with tempfile.TemporaryDirectory(prefix='original-projection-') as directory:
            plan=Path(directory);old=plan/'old.sqlite';source=sqlite3.connect(old)
            for table in ('members','stored_contributions'):source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            uid='[1,2,3,4]';root=(113,141,-554);outside=(128,141,-554)
            for at in (root,outside):
                for table in ('members','stored_contributions'):source.execute('INSERT INTO '+table+' VALUES(?,?,?,?,?)',('.',*at,uid))
            source.execute('INSERT INTO stored_contributions VALUES(?,?,?,?,?)',('.',*root,'foreign-saved-ledger'));source.commit();source.close();old_sha=m.sha(old)
            db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA);db.execute('ATTACH DATABASE ? AS old',('file:'+old.as_posix()+'?mode=ro',))
            try:
                binding={'fixture':'immutable-source'};m.meta(db,'binding',binding);cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':1071,'rowsProcessedSinceAdmission':1096704};m.meta(db,'nativeComposeCursor',cursor)
                data=m.canonical({'registryId':'fixture:opaque-original','properties':{}});pose='sp_'+m.digest(m.json.loads(data))[:24];db.execute('INSERT INTO poses VALUES(?,?,1)',(pose,data))
                obj=dict(zip(('dim','uuid','pose','x','y','z','kind','reason','source_status'),('.',uid,pose,*root,'static','unchanged-original','fixture')));db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()))
                state={'registryId':'fixture:opaque-original','properties':{'variant':'3'}};helper_state={'registryId':'fixture:helper','properties':{}}
                fields={'id':m.Tag(8,'fixture:opaque'),'payload':m.Tag(10,{'private':m.Tag(11,[4,2,9])})};be_bytes=m.encode_nbt(m.NbtFile('',m.Tag(10,fields)))
                envelope={'resource':{'path':'immutable-envelope','sha256':'fixture'},'cells':[(0,0,0),(15,0,0)],'openable':False,'functionalDoor':False}
                def source_point(context,dim,at):
                    at=tuple(at);value=state if at==root else helper_state if at==outside else m.AIR
                    return copy.deepcopy(value),fields if at==root else None,{'fixture':'source-chunk','chunk':[at[0]>>4,at[2]>>4]}
                real=c.SourceVisualReservations
                with patch.object(real,'source_point',source_point),patch.object(real,'envelope',return_value=envelope),patch.object(real,'authority',return_value=({},None,{'essentialMask':[[0,0,0]],'support':{'required':False}},None)),patch.object(real,'native_origins',return_value=[]),patch.object(real,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})):
                    policies=[]
                    for at in (root,outside):
                        policy={'position':list(at),'policy':'root_existing_be_background' if at==root else 'nonroot_guest_overlay','sourceState':copy.deepcopy(state if at==root else helper_state),'storedContributions':n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=?',('.',*at))}
                        policies.append(policy)
                        db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,?)',('.',*at,1 if at==root else 0));db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*at,'remaining-owner','sp-other',0,0,0))
                    policies[1]['existingBeBridge']={'hostState':copy.deepcopy(helper_state),'retainedResident':0,'existingCompositeBE':True}
                    for policy in policies:
                        bridge=c.projection_bridge(m,policy);db.execute('INSERT INTO bridge_cells(dim,x,y,z,host_state,retained_resident) VALUES(?,?,?,?,?,?)',('.',*policy['position'],bridge['host_state'],bridge['retained_resident']))
                    owner={'dimension':'.','uuid':uid,'object':obj,'runtimeMemberCells':[list(root),list(outside)],'memberPolicies':policies}
                    # Ordinary queue-present projection behavior remains usable.
                    c.validate_owner_members(m,db,owner)
                    before={at:c.point(m,db,'.',at) for at in (root,outside)}
                    for table in ('fragments','cell_queue','bridge_cells'):db.execute('DELETE FROM '+table)
                    context=real(m,db);domain=context.original_domain('.',uid);zone=context.zone_snapshot('.',(112,128,-560))
                    self.assertGreater(outside[0],zone['maximum'][0]);self.assertIn(list(outside),[row['position'] for row in domain['domain']])
                    for at in (root,outside):db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)',('.',*at,uid,c.LOCAL_ORIGINAL_REASON))
                    files={'migrate':'reviewed-parent-migrate','nativeResume':'reviewed-parent-resume','nativeCertificates':'reviewed-parent-certificates'}
                    event={'schemaVersion':1,'kind':'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION','sourceBinding':binding,'sourceFiles':files,'previous':None,'sourceZone':zone,'originalDomains':[domain],'originalNativeCells':[],
                           'pointPlan':[{'position':list(at),'before':before[at],'remainingDemands':{key:[] for key in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','foreignActivatedNative')},'retire':True} for at in (root,outside)],'processedRowsReset':0,'ownerGraphTraversal':False,'mapWrites':0}
                    def store(value):
                        digest=m.digest(value);db.execute('INSERT OR REPLACE INTO source_local_retentions VALUES(1,?,?)',(m.canonical(value),digest));m.meta(db,'sourceLocalRetentionJournal',{'schemaVersion':1,'events':1,'head':digest,'sourceBinding':binding})
                    store(event);db.commit();counts=n.counts(db);checked=[]
                    def check_sources(wanted):self.assertEqual(wanted,files);checked.append(copy.deepcopy(wanted))
                    # An upgrade callback accepts only its reviewed ancestor.
                    # The ordinary default remains strict against unknown bytes.
                    with self.assertRaises(RuntimeError):c.validate_owner_members(m,db,owner)
                    c.validate_owner_members(m,db,owner,source_files_validator=check_sources)
                    c.validate_source_local_retentions(m,db,source_files_validator=check_sources)
                    self.assertEqual(len(checked),3);self.assertEqual(n.counts(db),counts);self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
                    self.assertEqual(db.execute('SELECT count(*) FROM cell_queue').fetchone(),(0,));self.assertEqual(db.execute('SELECT count(*) FROM bridge_cells').fetchone(),(0,))
                    def rejects(change):
                        db.execute('SAVEPOINT negative')
                        try:
                            change()
                            with self.assertRaises(RuntimeError):c.validate_owner_members(m,db,owner,source_files_validator=check_sources)
                        finally:db.execute('ROLLBACK TO negative');db.execute('RELEASE negative')
                    rejects(lambda:db.execute('DELETE FROM meta WHERE key=?',('sourceLocalRetentionJournal',)))
                    rejects(lambda:db.execute("UPDATE source_local_retentions SET digest='corrupt'"))
                    rejects(lambda:db.execute('DELETE FROM protected_cells WHERE x=?',(root[0],)))
                    rejects(lambda:db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',*outside)))
                    rejects(lambda:db.execute('INSERT INTO bridge_cells(dim,x,y,z,host_state,retained_resident) VALUES(?,?,?,?,?,?)',('.',*outside,m.canonical(helper_state),0)))
                    for table in ('fragments','substrate_fragments','whole_cells','native_visual_fragments','native_visual_roots','placements'):
                        # The historical before image is enough to insert each
                        # relevant schema row without mocking current SQL checks.
                        if table=='fragments':sql='INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)';args=('.',*outside,'unexpected-owner','sp-other',0,0,0)
                        else:
                            columns=[row[1] for row in db.execute('PRAGMA table_info('+table+')')];values={key:0 for key in columns};values.update({'dim':'.','x':outside[0],'y':outside[1],'z':outside[2],'uuid':'unexpected-owner','pose':'sp-other','template':'wo-other','host_state':'{}','type':1,'type_id':1,'data':'{}','state':'{}','original_state':'{}','composition':'{}'})
                            sql='INSERT INTO '+table+'('+','.join(columns)+') VALUES('+','.join('?' for _ in columns)+')';args=tuple(values[key] for key in columns)
                        rejects(lambda sql=sql,args=args:db.execute(sql,args))
                    damaged=copy.deepcopy(event);damaged['pointPlan'][1]['retire']=False;rejects(lambda:store(damaged))
                    damaged=copy.deepcopy(event);damaged['pointPlan'][0]['remainingDemands']['fragments']=[{'uuid':'still-needed'}];rejects(lambda:store(damaged))
                    damaged=copy.deepcopy(event);damaged['originalDomains'][0]['object']['reason']='changed-identity';rejects(lambda:store(damaged))
                    wrong_owner=copy.deepcopy(owner);wrong_owner['memberPolicies'][0]['storedContributions']=[]
                    with self.assertRaises(RuntimeError):c.validate_owner_members(m,db,wrong_owner,source_files_validator=check_sources)
                    fields['payload'].value['private']=m.Tag(11,[4,2,8])
                    with self.assertRaises(RuntimeError):c.validate_owner_members(m,db,owner,source_files_validator=check_sources)
                    fields['payload'].value['private']=m.Tag(11,[4,2,9])
                    # A matching event cannot excuse corrupt unrelated history:
                    # the mandatory full validator still checks the entire head.
                    damaged=copy.deepcopy(event);damaged['previous']=None;digest=m.digest(damaged);db.execute('INSERT INTO source_local_retentions VALUES(2,?,?)',(m.canonical(damaged),digest));m.meta(db,'sourceLocalRetentionJournal',{'schemaVersion':1,'events':2,'head':digest,'sourceBinding':binding})
                    c.validate_owner_members(m,db,owner,source_files_validator=check_sources)
                    with self.assertRaises(RuntimeError):c.validate_source_local_retentions(m,db,source_files_validator=check_sources)
                    self.assertEqual(m.sha(old),old_sha);self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,fields))),be_bytes);self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor)
            finally:db.close()


if __name__=='__main__':unittest.main()
