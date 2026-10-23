"""One new cache boundary: immutable source, exact rows, atomic retry/reuse."""
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_resume as n
import native_certificates as c


class SourceVisualMemberIndexTests(unittest.TestCase):
    def test_atomic_owner_index_matches_original_full_rows_and_reuses_without_native_changes(self):
        with tempfile.TemporaryDirectory(prefix='source-member-index-') as temporary:
            root=Path(temporary);old_dir=root/'immutable-old';old_dir.mkdir();old_path=old_dir/'plan.sqlite'
            old=sqlite3.connect(old_path)
            for table in ('stored_contributions','members'):
                old.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,payload TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
                old.executemany('INSERT INTO '+table+' VALUES(?,?,?,?,?,?)',[('.',5,2,0,'owner','late-'+table),('.',1,3,0,'other','foreign'),('.',1,2,0,'owner','early-'+table),('DIM-1',1,2,0,'owner','dimension')])
            old.commit();old.close();source_hash=m.sha(old_path);stat=old_path.stat()
            db=sqlite3.connect(root/'plan.sqlite',uri=True);db.executescript(m.SCHEMA)
            try:
                db.execute('ATTACH DATABASE ? AS old',('file:'+old_path.as_posix()+'?mode=ro',))
                m.meta(db,'binding',{'oldPlan':str(old_dir),'oldDatabaseBytes':stat.st_size,'oldDatabaseMtimeNs':stat.st_mtime_ns,'copyManifestSha256':'immutable-copy','plannerBindingSha256':'immutable-planner'})
                cursor={'phase':'NATIVE_TRANSFER','batchesCommitted':8,'rowsProcessedSinceAdmission':8192};m.meta(db,'nativeComposeCursor',cursor)
                m.meta(db,'nativeLocalRetention',{'path':'unchanged-receipt','sha256':'unchanged'});db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',9,0,0));db.commit()
                before=n.counts(db);retention=m.meta(db,'nativeLocalRetention');original=m.meta
                def crash(database,key,value=None):
                    if key=='sourceVisualMemberIndex' and value is not None:raise RuntimeError('cache before commit')
                    return original(database,key,value)
                with patch.object(m,'meta',side_effect=crash),self.assertRaisesRegex(RuntimeError,'before commit'):c.prepare_source_visual_member_index(m,db)
                self.assertEqual(db.execute('SELECT count(*) FROM source_visual_owner_members').fetchone(),(0,));self.assertIsNone(m.meta(db,'sourceVisualMemberIndex'));self.assertFalse(db.in_transaction)
                c.prepare_source_visual_member_index(m,db);binding=m.meta(db,'sourceVisualMemberIndex')
                self.assertEqual(binding['copiedCounts'],{'stored_contributions':4,'members':4});self.assertEqual(binding['rows'],8)
                context=c.SourceVisualReservations(m,db)
                for table in ('stored_contributions','members'):
                    expected=n.row_dict(db,'SELECT * FROM old.'+table+' WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',('.','owner'))
                    self.assertEqual(context.owner_member_rows('.','owner',table),expected)
                    self.assertEqual(m.digest(context.owner_member_rows('.','owner',table)),m.digest(expected))
                    self.assertEqual([row['payload'] for row in context.owner_member_rows('.','owner',table)],['early-'+table,'late-'+table])
                plans=db.execute('EXPLAIN QUERY PLAN SELECT x,y,z FROM source_visual_owner_members WHERE dim=? AND uuid=? AND sourceTable=? ORDER BY x,y,z LIMIT 4097',('.','owner','members')).fetchall()
                self.assertTrue(any('dim=? AND uuid=? AND sourceTable=?' in row[3] for row in plans),plans)
                plans=db.execute('EXPLAIN QUERY PLAN SELECT * FROM old.members WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',('.',1,2,0,'owner')).fetchall()
                self.assertTrue(any('dim=? AND x=? AND y=? AND z=? AND uuid=?' in row[3] for row in plans),plans)
                statements=[];db.set_trace_callback(statements.append);c.prepare_source_visual_member_index(m,db);db.set_trace_callback(None)
                self.assertFalse(any(row.upper().startswith('INSERT') for row in statements));self.assertEqual(m.meta(db,'sourceVisualMemberIndex'),binding)
                self.assertEqual(n.counts(db),before);self.assertEqual(m.meta(db,'nativeComposeCursor'),cursor);self.assertEqual(m.meta(db,'nativeLocalRetention'),retention)
                self.assertEqual(m.sha(old_path),source_hash)
                db.execute('DELETE FROM source_visual_owner_members WHERE dim=? AND uuid=? AND sourceTable=? AND x=?',('.','owner','members',5));db.commit()
                with self.assertRaisesRegex(RuntimeError,'incomplete'):c.prepare_source_visual_member_index(m,db)
            finally:db.close()


if __name__=='__main__':unittest.main()
