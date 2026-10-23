"""Execute the actual publication statements across SQLite commit crashes."""
import ast
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path

import migrate as m


class CommitBoundary:
    def __init__(self,db,where):self.db=db;self.where=where
    def __getattr__(self,name):return getattr(self.db,name)
    def commit(self):
        if self.where=='before':raise RuntimeError('injected before generation commit')
        self.db.commit()
        if self.where=='after':raise RuntimeError('injected after generation commit')


class PublicationTests(unittest.TestCase):
    def test_real_publication_before_and_after_generation_commit(self):
        module=ast.parse(Path(m.__file__).read_text(encoding='utf-8-sig'))
        tail=next(node for node in module.body if isinstance(node,ast.FunctionDef) and node.name=='compose_native_tail')
        needed=next(i for i,node in enumerate(tail.body) if isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and ast.unparse(node.value.func)=='atomic_json' and 'native-states-needed.json' in ast.unparse(node))
        generation=next(i for i,node in enumerate(tail.body) if isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and ast.unparse(node.value.func)=='meta' and len(node.value.args)>1 and isinstance(node.value.args[1],ast.Constant) and node.value.args[1].value=='compositionGeneration')
        self.assertLess(needed,generation)
        self.assertEqual(ast.unparse(tail.body[generation+1]),'db.commit()')
        statements=ast.Module(body=copy.deepcopy(tail.body[needed:generation+2]),type_ignores=[])
        executable=compile(ast.fix_missing_locations(statements),str(m.__file__)+'#actual-publication','exec')
        for boundary in ('before','after'):
            with self.subTest(boundary=boundary),tempfile.TemporaryDirectory(prefix='native-publication-fixture-') as directory:
                plan=Path(directory);database=plan/'fixture.sqlite';db=sqlite3.connect(database)
                db.execute('CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)')
                m.meta(db,'nativeComposeCursor',{'phase':'FINALIZING'});db.commit()
                output=plan/'compositions.jsonl';output.write_bytes(b'{"key":"fixture"}\n')
                values={'db':CommitBoundary(db,boundary),'plan':plan,'output':output,'needed_native':{'air':m.AIR},
                        'atomic_json':m.atomic_json,'meta':m.meta,'sha':m.sha,'__file__':m.__file__}
                with self.assertRaisesRegex(RuntimeError,'generation commit'):exec(executable,values)
                db.close();saved=sqlite3.connect(database)
                try:
                    current=m.meta(saved,'compositionGeneration')
                    self.assertEqual(m.meta(saved,'nativeComposeCursor'),{'phase':'FINALIZING'})
                    self.assertEqual(m.read(plan/'native-states-needed.json')['nativeStates'],[m.AIR])
                    if boundary=='before':self.assertIsNone(current)
                    else:self.assertEqual(current['compositionsSha256'],m.sha(output))
                finally:saved.close()


if __name__=='__main__':unittest.main()
