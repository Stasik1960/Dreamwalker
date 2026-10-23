"""New finite member/visual-only/pair union paths; temporary SQLite only."""
import sqlite3
import tempfile
import unittest
from pathlib import Path

import migrate as m
import native_resume as n


class MemberRetentionTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='native-member-fixture-')
        self.db=sqlite3.connect(Path(self.temporary.name)/'fixture.sqlite');self.db.executescript(m.SCHEMA)
        self.owners={}
        for uid,root in (('D',1),('E',5)):
            obj={'dim':'.','uuid':uid,'pose':'pose_'+uid,'x':root,'y':0,'z':0,'kind':'static','reason':'source-original','source_status':'stored'}
            self.db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',tuple(obj.values()))
            self.db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',tuple(obj[k] for k in ('dim','uuid','pose','x','y','z','kind')))
            self.owners[uid]={'dimension':'.','uuid':uid,'object':obj,'selected':{k:obj[k] for k in ('dim','uuid','pose','x','y','z','kind')},'fragments':[],'runtimeMemberCells':[]}
        m.meta(self.db,'classification',{'original':'unchanged'});self.db.commit()

    def tearDown(self):self.db.close();self.temporary.cleanup()

    def fragments(self,uid,positions):
        owner=self.owners[uid]
        for x in positions:
            fragment={'x':x,'y':0,'z':0,'pose':owner['object']['pose'],'dx':x-owner['object']['x'],'dy':0,'dz':0}
            owner['fragments'].append(fragment)
            self.db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',x,0,0,uid,fragment['pose'],fragment['dx'],0,0))
            self.db.execute('INSERT OR IGNORE INTO cell_queue VALUES(?,?,?,?,0)',('.',x,0,0))

    def case(self,owners,policy):
        return {'policy':policy,'retainedOwners':[self.owners[uid] for uid in owners],
                'retainedNative':{'dimension':'.','uuid':'C','pose':'native_pose','root':[0,0,0],'mustRemainOriginalCells':[[0,0,0]]}}

    def apply(self,case):
        preview={'cases':[case],'pointPlan':n.point_plan(m,self.db,[case])}
        self.db.commit();self.db.execute('BEGIN IMMEDIATE')
        try:n.apply_scope_delta(m,self.db,preview);self.db.commit()
        except BaseException:self.db.rollback();raise
        return preview

    def test_visual_only_retention_preserves_E_work_and_protects_actual_members(self):
        self.fragments('D',range(5));self.fragments('E',(3,5))
        self.owners['D']['runtimeMemberCells']=[[1,0,0],[2,0,0],[4,0,0]]
        for x in (1,3):self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',x,0,0,'existing-source-host',1,None))
        original=self.db.execute('SELECT * FROM objects ORDER BY uuid').fetchall()
        preview=self.apply(self.case(('D',),'visual_only_keep_members'))
        self.assertEqual(sum(point['pruneQueueAndBridge'] for point in preview['pointPlan']),4)
        self.assertEqual(self.db.execute('SELECT uuid FROM selected_objects').fetchall(),[('E',)])
        self.assertEqual(self.db.execute('SELECT x,uuid FROM fragments ORDER BY x').fetchall(),[(3,'E'),(5,'E')])
        self.assertEqual(self.db.execute('SELECT x,processed FROM cell_queue ORDER BY x').fetchall(),[(3,0),(5,0)])
        self.assertEqual(self.db.execute('SELECT x FROM bridge_cells').fetchall(),[(3,)])
        self.assertEqual(self.db.execute("SELECT x FROM protected_cells WHERE uuid='D' ORDER BY x").fetchall(),[(1,),(2,),(4,)])
        self.assertFalse(self.db.execute('SELECT 1 FROM protected_cells WHERE x=3').fetchone())
        self.assertEqual(self.db.execute('SELECT * FROM objects ORDER BY uuid').fetchall(),original)
        self.assertEqual(m.meta(self.db,'classification'),{'original':'unchanged'})

    def test_independent_actual_member_rejected_without_retention_or_closure(self):
        self.fragments('D',range(5));self.fragments('E',(2,5))
        self.owners['D']['runtimeMemberCells']=[[1,0,0],[2,0,0],[4,0,0]]
        self.db.commit()
        with self.assertRaisesRegex(RuntimeError,'independent owner/demand'):n.point_plan(m,self.db,[self.case(('D',),'visual_only_keep_members')])
        self.assertEqual(self.db.execute('SELECT count(*) FROM selected_objects').fetchone(),(2,))
        self.assertEqual(self.db.execute('SELECT count(*) FROM fragments').fetchone(),(7,))

    def test_exact_pair_removes_both_models_and_counts_only_unique_queue_union(self):
        self.fragments('D',range(5));self.fragments('E',range(2,6))
        self.owners['D']['runtimeMemberCells']=[[1,0,0],[2,0,0],[4,0,0]]
        self.owners['E']['runtimeMemberCells']=[[2,0,0],[3,0,0]]
        self.db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',2,0,0,'existing-shared-source-host',1,None))
        original=self.db.execute('SELECT * FROM objects ORDER BY uuid').fetchall()
        preview=self.apply(self.case(('D','E'),'isolated_pair_keep_members'))
        self.assertEqual(len(preview['pointPlan']),6)
        self.assertEqual(sum(len(owner['fragments']) for owner in preview['cases'][0]['retainedOwners']),9)
        for table in ('selected_objects','fragments','cell_queue','bridge_cells'):
            self.assertEqual(self.db.execute('SELECT count(*) FROM '+table).fetchone(),(0,))
        self.assertEqual(self.db.execute("SELECT uuid FROM protected_cells WHERE x=2 ORDER BY uuid").fetchall(),[('D',),('E',)])
        self.assertEqual(self.db.execute('SELECT * FROM objects ORDER BY uuid').fetchall(),original)

    def test_frozen_actual_scope_names_eighteen_components_and_twenty_two_owners(self):
        path=m.DEFAULT_PLAN/'full/native-retention-eighteen-scope.json';scope=m.read(path)
        report=m.read(scope['candidateReport']['path']);m.meta(self.db,'binding',report['sourceBinding']);self.db.commit()
        _,candidates=n.load_scope(m,self.db,path,m.sha(path))
        self.assertEqual(len(candidates),18)
        self.assertEqual(sum(1+(candidate['_pair'] is not None) for candidate in candidates),22)
        self.assertEqual(sum(candidate['_retentionPolicy']=='visual_only_keep_members' for candidate in candidates),6)
        for candidate in candidates:
            points={(f['x'],f['y'],f['z']) for f in candidate['directSelectedOwners'][0]['fragments']}
            self.assertTrue({tuple(at) for at in candidate['_runtimeMemberCells']}<=points)


if __name__=='__main__':unittest.main()
