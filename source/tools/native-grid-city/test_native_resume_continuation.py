"""Only the new finalization boundaries and explicit finite-scope contract."""
import sqlite3
import tempfile
import unittest
from pathlib import Path

import migrate as m
import native_resume as n


class ContinuationTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='native-finalize-fixture-')
        self.db=sqlite3.connect(Path(self.temporary.name)/'fixture.sqlite')
        self.db.executescript(m.SCHEMA)
        m.meta(self.db,'successfulPilotGate',{'fixture':True})
        m.meta(self.db,'fullNativeFunctionGateRequired',True)
        m.meta(self.db,'nativeComposeCursor',{'phase':'FINALIZING','batchesCommitted':940,'rowsProcessedSinceAdmission':1024})
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',1,2,3))
        self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','already-committed-native'))
        self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',1,2,3,'selected-owner','pose',0,0,0))
        self.db.commit()

    def tearDown(self):
        self.db.close();self.temporary.cleanup()

    def test_crash_after_finalizing_cursor_before_any_derived_rows(self):
        cursor=m.meta(self.db,'nativeComposeCursor')
        n.require_unfinalized(m,self.db,finalizing=True)
        n.restart_finalization(m,self.db)
        # Reentering the shared tail reaches FINALIZING with no pending work;
        # repeating that same checkpoint is legal and does not rewind it.
        n.checkpoint(m,self.db,'FINALIZING');self.db.commit()
        self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor)
        self.assertEqual(self.db.execute('SELECT * FROM cell_queue').fetchall(),[('.',1,2,3,1)])
        self.assertEqual(self.db.execute('SELECT * FROM activated_native').fetchall(),[('.','already-committed-native')])

    def test_partial_derived_commit_rebuild_and_atomic_abort_preserve_native(self):
        self.db.execute('INSERT INTO compositions VALUES(?,?,NULL)',('partial','{}'))
        self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,NULL,?,0)',('.',1,2,3,0,0,'partial','{}'))
        self.db.execute('INSERT INTO source_chunks VALUES(?,?,?,?,?)',('.',0,0,'source-region','source-sha'))
        m.meta(self.db,'plannedBeSafety',{'partial':True});m.meta(self.db,'compositionsSha256','partial-output')
        self.db.commit()
        self.db.execute("CREATE TRIGGER fail_mid_restart BEFORE DELETE ON compositions BEGIN SELECT RAISE(ABORT,'synthetic crash boundary'); END")
        self.db.commit()
        with self.assertRaisesRegex(sqlite3.IntegrityError,'synthetic crash'):n.restart_finalization(m,self.db)
        self.assertEqual(self.db.execute('SELECT count(*) FROM placements').fetchone(),(1,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM compositions').fetchone(),(1,))
        self.db.execute('DROP TRIGGER fail_mid_restart');self.db.commit()
        n.restart_finalization(m,self.db)
        for table in ('placements','compositions','source_chunks'):
            self.assertEqual(self.db.execute('SELECT count(*) FROM '+table).fetchone(),(0,))
        self.assertIsNone(m.meta(self.db,'plannedBeSafety'));self.assertIsNone(m.meta(self.db,'compositionsSha256'))
        self.assertEqual(self.db.execute('SELECT * FROM cell_queue').fetchall(),[('.',1,2,3,1)])
        self.assertEqual(self.db.execute('SELECT count(*) FROM activated_native').fetchone(),(1,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(1,))
        m.meta(self.db,'compositionGeneration',{'completed':True});self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'generation/apply'):n.restart_finalization(m,self.db)

    def scope_inputs(self):
        scope_path=m.DEFAULT_PLAN/'full/native-retention-isolated-scope.json'
        scope=m.read(scope_path);report=m.read(scope['candidateReport']['path'])
        m.meta(self.db,'binding',report['sourceBinding']);self.db.commit()
        return scope_path,scope,report

    def test_exact_reviewed_eight_scope_is_explicit_and_finite(self):
        scope_path,scope,_=self.scope_inputs()
        approved,candidates=n.load_scope(m,self.db,scope_path,m.sha(scope_path))
        self.assertEqual(len(candidates),8)
        self.assertEqual({c['nativeObject']['uuid'] for c in candidates},{r['nativeUuid'] for r in scope['cases']})
        self.assertEqual(approved,scope)

    def test_declined_and_unbound_scope_cannot_become_retention_authority(self):
        _,scope,report=self.scope_inputs()
        declined=next(row for row in report['candidates'] if row['classification']=='DECLINED_NON_ISOLATED_OR_NOT_PENDING')
        scope['cases']=[{'nativeUuid':declined['nativeObject']['uuid'],'selectedOwnerUuid':declined['directSelectedOwners'][0]['uuid']}]
        scope_path=Path(self.temporary.name)/'declined-scope.json';m.atomic_json(scope_path,scope)
        with self.assertRaisesRegex(RuntimeError,'Declined/non-isolated'):n.load_scope(m,self.db,scope_path,m.sha(scope_path))
        with self.assertRaisesRegex(RuntimeError,'scope SHA'):n.load_scope(m,self.db,scope_path,'wrong-hash')


if __name__=='__main__':unittest.main()
