"""One new affected integration fixture for an original aligned source section.

Only isolated temporary databases/source dictionaries are used. No accepted
common-fallback test is loaded or repeated, and no project plan is opened.
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


class OriginalSectionFallbackTests(unittest.TestCase):
    def test_negative_section_crossing_whole_active_native_and_committed_history(self):
        with tempfile.TemporaryDirectory(prefix='original-section-only-') as directory:
            plan = Path(directory); old = plan/'old'; old.mkdir()
            source = sqlite3.connect(old/'plan.sqlite')
            for table in ('members', 'stored_contributions'):
                source.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
            source.execute('INSERT INTO stored_contributions VALUES(?,?,?,?,?)', ('.', 1, -2, -2, 'crossing-W'))
            source.commit(); source.close(); original_hash = m.sha(old/'plan.sqlite'); stat = (old/'plan.sqlite').stat()
            db = sqlite3.connect(plan/'plan.sqlite', uri=True); db.executescript(m.SCHEMA)
            try:
                binding = {'oldPlan': str(old), 'oldDatabaseBytes': stat.st_size, 'oldDatabaseMtimeNs': stat.st_mtime_ns,
                           'copyManifestSha256': 'fixture', 'plannerBindingSha256': 'fixture'}
                m.meta(db, 'binding', binding)
                db.execute('ATTACH DATABASE ? AS old', ('file:'+(old/'plan.sqlite').as_posix()+'?mode=ro',))
                classification = plan/'classification.json'; m.atomic_json(classification, {'protectedRegistryIds': []})
                m.meta(db, 'classification', n.ref(m, classification))
                cursor = {'phase': 'NATIVE_TRANSFER', 'batchesCommitted': 1, 'rowsProcessedSinceAdmission': 1024, 'retention': None}
                m.meta(db, 'nativeComposeCursor', cursor)
                objects, members, envelopes, states, bes, origins = {}, {}, {}, {}, {}, {}

                def seed(uid, root, kind, cells):
                    encoded = m.canonical({'registryId': 'fixture:'+uid, 'properties': {}})
                    pose = 'sp_'+m.digest(m.json.loads(encoded))[:24]
                    db.execute('INSERT INTO poses VALUES(?,?,1)', (pose, encoded))
                    obj = dict(zip(('dim','uuid','pose','x','y','z','kind','reason','source_status'),
                                   ('.', uid, pose, *root, kind, 'unchanged-original-policy', 'unknown_pose')))
                    db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)', tuple(obj.values()))
                    objects[uid] = obj; members[uid] = set(cells)
                    envelopes[pose] = {'resource': {'path': 'immutable-envelope', 'sha256': 'fixture'},
                                       'cells': [tuple(at[i]-root[i] for i in range(3)) for at in sorted(cells)],
                                       'openable': False, 'functionalDoor': False}
                    for at in cells: states[tuple(at)] = {'registryId': 'fixture:'+uid, 'properties': {}}
                    return obj

                contact = (-1,-1,-1); history_point = (-2,-1,-1); outside_history = (8,8,8)
                incoming_root = (2,-1,-1); incoming_uid = m.canonical([7,8,9,10])
                incoming = seed(incoming_uid, incoming_root, 'substrate', {incoming_root})
                opaque = seed('opaque-P', contact, 'preserve', {contact})
                whole = seed('crossing-W', (-2,-2,-2), 'whole', {(-2,-2,-2),(1,-2,-2)})
                donor_root = (3,-3,-3); donor = seed('active-E', donor_root, 'substrate', {donor_root,(4,-3,-3)})
                shared = seed('visual-only-G', (5,-2,-2), 'static', {(5,-2,-2)})
                historic = seed('historic-owner', (30,30,30), 'preserve', {(30,30,30)})
                bes[contact] = {'id': m.Tag(8, 'fixture:private-BE'), 'PrivatePayload': m.Tag(10, {'value': m.Tag(8, 'unaltered')})}
                bes[(-2,-2,-2)] = {'id': m.Tag(8, 'fixture:whole-root-BE'), 'opaque': m.Tag(11, [3,1,4])}
                wide_root = (5,-5,-5); wide_state = {'registryId': 'fixture:wide-native', 'properties': {}}
                states[wide_root] = wide_state
                bes[wide_root] = {'id': m.Tag(8, 'fixture:raw-wide-BE'), 'Private': m.Tag(8, 'keep')}
                for uid, obj in ((whole['uuid'], whole), (shared['uuid'], shared)):
                    db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)', tuple(obj[k] for k in ('dim','uuid','pose','x','y','z','kind')))
                role_points = [(-3,-2,-2),(-2,-2,-2),(1,-2,-2),(4,-2,-2)]
                for at in role_points:
                    db.execute('INSERT INTO whole_cells VALUES(?,?,?,?,?,?,?,?,?)', ('.',*at,whole['uuid'],'complete-W',at[0]+2,at[1]+2,at[2]+2))
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)', ('.',*at))
                for at in ((4,-2,-2),(5,-2,-2)):
                    db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)', ('.',*at,shared['uuid'],shared['pose'],at[0]-5,0,0))
                    db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,1)', ('.',*at))
                db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?', ('.',4,-2,-2))
                db.execute('INSERT INTO activated_native VALUES(?,?)', ('.',donor['uuid']))
                for at in ((-3,-3,-3),(-4,-3,-3)):
                    offset = tuple(at[i]-donor_root[i] for i in range(3))
                    db.execute('INSERT INTO pose_cells VALUES(?,?,?,?)', (donor['pose'],*offset))
                    db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)', ('.',*at,donor['uuid'],donor['pose'],*offset))
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)', ('.',*at))
                # Its empty own-root visual slot still has an activated donor.
                db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)', ('.',*donor_root))
                db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)', ('.',*incoming_root))
                db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)', ('.',*contact,opaque['uuid'],'functional_guest'))
                db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)', ('.',*history_point,historic['uuid'],'functional_guest'))
                db.execute('INSERT INTO protected_cells VALUES(?,?,?,?,?,?)', ('.',*outside_history,historic['uuid'],'functional_guest'))
                db.execute('INSERT INTO source_visual_owner_members VALUES(?,?,?,?,?,?)', ('.',whole['uuid'],'stored_contributions',1,-2,-2))
                identity = c.source_visual_member_identity(m, db)
                m.meta(db, 'sourceVisualMemberIndex', {'schemaVersion':1,'kind':'DERIVED_SOURCE_OWNER_MEMBER_POSITIONS','sourceIdentity':identity,'copiedCounts':{'stored_contributions':1,'members':0},'rows':1})
                export = plan/'exports.sqlite'; edb = sqlite3.connect(export)
                edb.execute('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT)'); edb.execute('CREATE TABLE native_fragments(state TEXT,x INTEGER,y INTEGER,z INTEGER)')
                key = m.canonical(wide_state); edb.execute('INSERT INTO native VALUES(?,?)',(key,m.canonical({'nativeVisualComplete':True})))
                for offset in ((-10,0,0),(1,0,0)):
                    edb.execute('INSERT INTO native_fragments VALUES(?,?,?,?)',(key,*offset))
                    at = tuple(wide_root[i]+offset[i] for i in range(3))
                    db.execute('INSERT INTO native_visual_fragments VALUES(?,?,?,?,?,?,?,?,?,?,?)', ('.',*at,*wide_root,key,*offset))
                    db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)', ('.',*at))
                db.execute('INSERT INTO native_visual_roots VALUES(?,?,?,?,?)', ('.',*wide_root,key))
                db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)', ('.',*wide_root))
                edb.commit(); edb.close(); m.meta(db,'poseFragments',{'path':str(export),'sha256':m.sha(export)})
                origins[donor_root] = [{'uuid':donor['uuid'],'legacyPose':'p-E','root':list(donor_root),'member':True,'object':[donor]}]
                origins[incoming_root] = [{'uuid':incoming_uid,'legacyPose':'p-C','root':list(incoming_root),'member':True,'object':[incoming]}]
                db.commit(); source_bytes = {at:m.encode_nbt(m.NbtFile('',m.Tag(10,be))) for at,be in bes.items()}

                def source_point(context, dim, at):
                    at = tuple(at)
                    return copy.deepcopy(states.get(at,m.AIR)),bes.get(at),{'fixture':'immutable-cached-section','chunk':[at[0]>>4,at[2]>>4]}
                def envelope(context, pose, data): return copy.deepcopy(envelopes[pose])
                def authority(context, pose): return ({'className':'fixture:opaque'},{'fixture':'cap'},{'support':{'required':False},'essentialMask':[[0,0,0]]},{'fixture':'spec'})
                def native_origins(context, dim, at, state): return copy.deepcopy(origins.get(tuple(at),[]))
                real = c.SourceVisualReservations
                with patch.object(real,'source_point',source_point),patch.object(real,'envelope',envelope),patch.object(real,'authority',authority),patch.object(real,'native_origins',native_origins),patch.object(real,'resource',return_value={'path':'bound-catalog','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})):
                    context = real(m,db)
                    # Store one genuine old positive encounter without running
                    # any accepted test. The source snapshots remain unchanged.
                    def owned_domain(ctx, dim, uid): return ctx.original_domain(dim,uid)
                    descriptor = {'kind':'legacy_native','uuid':incoming_uid,'pose':incoming['pose'],'root':list(incoming_root)}
                    with patch.object(real,'owner_domain',owned_domain):
                        context.clearance('.',history_point,descriptor)
                        context.clearance('.',outside_history,descriptor)
                    db.commit(); old_contacts = db.execute('SELECT * FROM source_visual_contacts').fetchall(); old_domains = db.execute('SELECT * FROM source_visual_domains').fetchall()
                    protected = n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',('.',*contact))[0]
                    failure = RuntimeError('Protected source contact\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':'.','position':list(contact),'protected':protected,'incoming':descriptor}))
                    before = n.counts(db); real_meta = m.meta
                    with self.assertRaisesRegex(RuntimeError,'seed changed'):
                        c.automatic_source_zone_retention(m,db,real(m,db),'.',incoming_root,m.AIR,failure)
                    self.assertEqual(n.counts(db),before); self.assertFalse(db.in_transaction)
                    real_point = c.point
                    def over_bound(module, handle, dim, at):
                        row = real_point(module,handle,dim,at)
                        if tuple(at)==(-16,-16,-16): row['fragments']=[{'uuid':'too-many-'+str(i)} for i in range(4097)]
                        return row
                    with patch.object(c,'point',side_effect=over_bound),self.assertRaisesRegex(RuntimeError,'budget'):
                        c.automatic_source_zone_retention(m,db,real(m,db),'.',incoming_root,states[incoming_root],failure)
                    self.assertEqual(n.counts(db),before); self.assertFalse(db.in_transaction)
                    def crash(handle, key, value=None):
                        if key=='sourceLocalRetentionJournal' and value is not None: raise RuntimeError('zone journal before commit')
                        return real_meta(handle,key,value)
                    with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):
                        c.automatic_source_zone_retention(m,db,real(m,db),'.',incoming_root,states[incoming_root],failure)
                    self.assertEqual(n.counts(db),before); self.assertIsNone(m.meta(db,'sourceLocalRetentionJournal'))
                    self.assertTrue(c.automatic_source_zone_retention(m,db,real(m,db),'.',incoming_root,states[incoming_root],failure))
                    event = m.json.loads(db.execute('SELECT data FROM source_local_retentions').fetchone()[0])
                    self.assertEqual(event['sourceZone']['minimum'],[-16,-16,-16]); self.assertEqual(event['sourceZone']['maximum'],[-1,-1,-1])
                    self.assertTrue(event['sourceZone']['sourceStatesDigest']); self.assertTrue(event['sourceZone']['typedBlockEntities'])
                    self.assertEqual({row['object']['uuid'] for row in event['originalDomains']},{opaque['uuid'],incoming_uid,whole['uuid'],donor['uuid'],historic['uuid']})
                    wdomain = next(row for row in event['originalDomains'] if row['object']['uuid']==whole['uuid'])
                    self.assertEqual({tuple(row['position']) for row in wdomain['domain']},members[whole['uuid']])
                    self.assertTrue(any(row['beDigest'] for row in wdomain['domain']))
                    self.assertEqual(len(event['zoneObjectWorkBefore'][whole['uuid']]['whole_cells']),len(role_points))
                    self.assertEqual(len(event['originalNativeCells']),1); self.assertTrue(event['originalNativeCells'][0]['sourceBeDigest'])
                    self.assertFalse(db.execute('SELECT 1 FROM activated_native').fetchone()); self.assertFalse(db.execute('SELECT 1 FROM substrate_fragments').fetchone()); self.assertFalse(db.execute('SELECT 1 FROM native_visual_roots').fetchone()); self.assertFalse(db.execute('SELECT 1 FROM native_visual_fragments').fetchone()); self.assertFalse(db.execute('SELECT 1 FROM whole_cells').fetchone())
                    self.assertEqual(db.execute('SELECT x,y,z,processed FROM cell_queue ORDER BY x,y,z').fetchall(),[(4,-2,-2,1),(5,-2,-2,1)])
                    self.assertEqual({row[0] for row in db.execute('SELECT uuid FROM selected_objects')},{shared['uuid']})
                    self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor); self.assertEqual(event['processedRowsReset'],0)
                    self.assertEqual(db.execute('SELECT * FROM source_visual_contacts').fetchall(),old_contacts); self.assertEqual(db.execute('SELECT * FROM source_visual_domains').fetchall(),old_domains)
                    c.validate_source_local_retentions(m,db,source_files_validator=lambda files:None)
                    with patch.object(real,'owner_domain',owned_domain),patch.object(real,'contact',side_effect=AssertionError('Non-superseded contacts must take the ordinary proof path')):
                        c.validate_source_visual_reservations(m,db,source_files_validator=lambda files:None)
                        db.execute('SAVEPOINT corrupt_zone')
                        db.execute("UPDATE source_local_retentions SET digest='changed'")
                        with self.assertRaises(RuntimeError): c.validate_source_visual_reservations(m,db,source_files_validator=lambda files:None)
                        db.execute('ROLLBACK TO corrupt_zone'); db.execute('RELEASE corrupt_zone')
                        # Outside the zone, only the complete retained incoming
                        # identity/pose/root can supersede its old positive row.
                        db.execute('SAVEPOINT wrong_actor')
                        key_row = db.execute('SELECT rowid,data FROM source_visual_contacts WHERE x=? AND y=? AND z=?',outside_history).fetchone()
                        changed = m.json.loads(key_row[1]); changed['incoming']['pose'] = 'not-the-retained-pose'
                        db.execute('UPDATE source_visual_contacts SET data=?,digest=? WHERE rowid=?',(m.canonical(changed),m.digest(changed),key_row[0]))
                        with self.assertRaises(AssertionError): c.validate_source_visual_reservations(m,db,source_files_validator=lambda files:None)
                        db.execute('ROLLBACK TO wrong_actor'); db.execute('RELEASE wrong_actor')
                    self.assertEqual(db.execute('SELECT * FROM source_visual_contacts').fetchall(),old_contacts)
                    for at,raw in source_bytes.items(): self.assertEqual(m.encode_nbt(m.NbtFile('',m.Tag(10,bes[at]))),raw)
                    self.assertEqual(m.sha(old/'plan.sqlite'),original_hash)
            finally:
                db.close()


if __name__=='__main__': unittest.main()
