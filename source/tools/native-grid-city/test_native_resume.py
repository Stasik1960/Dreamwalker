"""Small file-SQLite atomicity fixtures; no project/world database is opened."""
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import migrate as m
import native_resume as n


class NativeResumeTests(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory(prefix='native-resume-fixture-')
        self.db=sqlite3.connect(Path(self.directory.name)/'fixture.sqlite')
        self.db.executescript(m.SCHEMA)
        self.cells=[[-270+dx,51+dy,-192+dz] for dx in range(3) for dy in range(3) for dz in range(3)]
        obj=('.',n.D,'fixture_pose',-269,52,-191,'static','original_reason','unknown_pose')
        self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',obj)
        self.db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',obj[:7])
        for x,y,z in self.cells:
            self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',x,y,z,n.D,'fixture_pose',x+269,y-52,z+191))
            self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,0)',('.',x,y,z))
        self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',-270,53,-191,'original-host-state',1,None))
        self.db.execute('INSERT INTO cell_queue VALUES(?,?,?,?,1)',('.',100,10,100))
        self.db.execute('INSERT INTO activated_native VALUES(?,?)',('.','already-committed-other-native'))
        m.meta(self.db,'classification',{'unchanged':'classification'})
        self.db.commit()
        self.preview={'beforeLocal':{'cells':[{'position':at} for at in self.cells],
                                     'object':[{'x':-269,'y':52,'z':-191}]},
                      'retainedNative':{'mustRemainOriginalCells':[list(n.C_ROOT)]}}

    def tearDown(self):
        self.db.close();self.directory.cleanup()

    def delta(self):
        self.db.execute('BEGIN IMMEDIATE')
        try:n.apply_local_delta(m,self.db,self.preview)
        except BaseException:self.db.rollback();raise

    def test_only_pending_owner_removed_existing_native_and_inputs_unchanged(self):
        old=self.db.execute('SELECT * FROM objects').fetchall()
        self.delta();self.db.commit()
        self.assertEqual(self.db.execute('SELECT * FROM objects').fetchall(),old)
        self.assertEqual(m.meta(self.db,'classification'),{'unchanged':'classification'})
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(0,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM bridge_cells').fetchone(),(0,))
        self.assertEqual(self.db.execute('SELECT * FROM cell_queue').fetchall(),[('.',100,10,100,1)])
        self.assertEqual(self.db.execute('SELECT * FROM activated_native').fetchall(),[('.','already-committed-other-native')])
        self.assertEqual(self.db.execute('SELECT count(*) FROM protected_cells WHERE uuid=?',(n.C,)).fetchone(),(1,))

    def test_other_owner_demand_refused_and_entire_delta_rolled_back(self):
        at=self.cells[-1]
        self.db.execute('INSERT INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',*at,'other','other_pose',0,0,0));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'independent demand'):self.delta()
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(27,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM selected_objects').fetchone(),(1,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM bridge_cells').fetchone(),(1,))

    def test_processed_row_refused_and_transaction_rolled_back(self):
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?',('.',*self.cells[-1]));self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'pending rows'):self.delta()
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(27,))

    def test_before_commit_crash_preserves_all_original_pending_work(self):
        self.delta();self.db.rollback()
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(27,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM cell_queue WHERE processed=0').fetchone(),(27,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM protected_cells').fetchone(),(0,))

    def test_batch_cursor_commits_or_rolls_back_with_processed_rows(self):
        cursor={'phase':'NATIVE_TRANSFER','retention':{'path':'receipt','sha256':'hash'},'batchesCommitted':0,'rowsProcessedSinceAdmission':0}
        m.meta(self.db,'nativeComposeCursor',cursor);self.db.commit()
        args=('.',*self.cells[0])
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?',args)
        n.checkpoint(m,self.db,'NATIVE_TRANSFER',1);self.db.rollback()
        self.assertEqual(m.meta(self.db,'nativeComposeCursor'),cursor)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),(0,))
        self.db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?',args)
        n.checkpoint(m,self.db,'NATIVE_TRANSFER',1);self.db.commit()
        self.assertEqual(m.meta(self.db,'nativeComposeCursor')['rowsProcessedSinceAdmission'],1)
        self.assertEqual(self.db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),(1,))

    def test_only_exact_retained_C_activation_refused(self):
        m.meta(self.db,'nativeLocalRetention',{'path':'receipt','sha256':'hash'})
        with self.assertRaisesRegex(RuntimeError,'exact retained'):n.check_native_activation(m,self.db,'.',n.C)
        n.check_native_activation(m,self.db,'.','some-other-native')

    def test_finalization_cannot_be_rewound(self):
        m.meta(self.db,'nativeComposeCursor',{'phase':'FINALIZING'})
        with self.assertRaisesRegex(RuntimeError,'cannot rewind'):n.checkpoint(m,self.db,'NATIVE_TRANSFER',1)

    def test_resume_calls_only_shared_tail_not_initial_selection(self):
        calls=[]
        fake=SimpleNamespace(checked=lambda x:Path(x),connect=lambda p:self.db,
                             compose_native_tail=lambda *args:calls.append(args),require=m.require)
        self.db.execute("INSERT INTO issues VALUES('protected_local_overlap','.',1,2,3,?)",(json.dumps({'policy':'Own footprint only; no graph closure'}),))
        with patch.object(n,'validate_resume',return_value={}):
            # Use a proxy so resume's finally closes only this fixture handle.
            with patch.object(self,'db',self.db):n.resume(SimpleNamespace(plan=self.directory.name),fake)
        self.assertEqual(len(calls),1)
        self.assertEqual(calls[0][2:],(None,1))
        self.db=sqlite3.connect(Path(self.directory.name)/'fixture.sqlite')


if __name__=='__main__':unittest.main()
