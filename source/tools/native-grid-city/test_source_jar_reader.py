"""One focused fixture for reuse and lifetime of the bound read-only JAR."""
import gc
import shutil
import sqlite3
import tempfile
import unittest
import weakref
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_certificates as c


class SourceJarReaderTests(unittest.TestCase):
    def test_bound_specs_share_reader_and_release_without_changing_evidence(self):
        with tempfile.TemporaryDirectory(prefix='source-jar-reader-') as directory:
            plan=Path(directory);caps=plan/'tools/ordinary-stage1/source-capabilities.json';caps.parent.mkdir(parents=True)
            m.atomic_json(caps,{'states':[{'registryId':'fixture:a','className':'opaque-a'},{'registryId':'fixture:b','className':'opaque-b'}]})
            jar=plan/'game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar';jar.parent.mkdir();entries={'bloodborne_dw/composite/a.json':b'{"support":{"required":false},"private":[1,2]}','bloodborne_dw/composite/b.json':b'{"essentialMask":[[0,0,0]],"private":{"x":9}}','bloodborne_dw/composite/bad.json':b'{not valid JSON'}
            with m.zipfile.ZipFile(jar,'w') as archive:
                for name,raw in entries.items():archive.writestr(name,raw)
            with m.zipfile.ZipFile(jar) as archive:
                try:archive.read('bloodborne_dw/composite/missing.json')
                except KeyError as error:missing_text=str(error)
            jar_sha=m.sha(jar);caps_sha=m.sha(caps);db=sqlite3.connect(':memory:');db.executescript(m.SCHEMA);binding={'oldPlan':str(plan/'source-bindings/levels/old-plan'),'source':str(plan/'working/copy')};m.meta(db,'binding',binding)
            try:
                with patch.object(c,'OVERLAP_CAPABILITIES_SHA',caps_sha),patch.object(c,'OVERLAP_BASELINE_SHA',jar_sha),patch.object(m.zipfile,'ZipFile',wraps=m.zipfile.ZipFile) as opened:
                    context=c.SourceVisualReservations(m,db);cap_a,cap_ref,spec_a,ref_a=context.authority({'registryId':'fixture:a'});reader=context.__dict__['_source_jar'][1]
                    cap_b,_,spec_b,ref_b=context.authority({'registryId':'fixture:b'});self.assertEqual(opened.call_count,1);self.assertIs(context.__dict__['_source_jar'][1],reader)
                    self.assertEqual(cap_a['className'],'opaque-a');self.assertEqual(cap_b['className'],'opaque-b');self.assertEqual(cap_ref,{'path':str(caps),'sha256':caps_sha})
                    for key,spec,reference in (('a',spec_a,ref_a),('b',spec_b,ref_b)):
                        entry='bloodborne_dw/composite/'+key+'.json';self.assertEqual(spec,m.json.loads(entries[entry]));self.assertEqual(reference,{'jar':{'path':str(jar),'sha256':jar_sha},'entry':entry,'sha256':m.hashlib.sha256(entries[entry]).hexdigest()})
                    self.assertEqual(context.authority({'registryId':'fixture:a'}),(cap_a,cap_ref,spec_a,ref_a));self.assertEqual(opened.call_count,1)
                    for _ in range(2):
                        with self.assertRaises(KeyError) as failure:context.authority({'registryId':'fixture:missing'})
                        self.assertEqual(str(failure.exception),missing_text)
                    with self.assertRaises(m.json.JSONDecodeError):context.authority({'registryId':'fixture:bad'})
                    self.assertNotIn('spec:fixture:missing',context.__dict__);self.assertNotIn('spec:fixture:bad',context.__dict__);self.assertEqual(opened.call_count,1)
                    # Even a same-byte alternate resource cannot be mixed into
                    # the already bound context's reader.
                    alternate=plan/'alternate';(alternate/'game').mkdir(parents=True);alternate_jar=alternate/'game'/jar.name;shutil.copyfile(jar,alternate_jar);m.meta(db,'binding',{**binding,'source':str(alternate/'working/copy')})
                    with self.assertRaisesRegex(RuntimeError,'another bound resource'):context.authority({'registryId':'fixture:missing'})
                    self.assertEqual(opened.call_count,1);m.meta(db,'binding',binding)
                    context.close();context.close();self.assertIsNone(reader.fp);self.assertNotIn('_source_jar',context.__dict__)
                    # A fresh context owns and closes a new reader, never the
                    # caller's SQLite/export handles or the prior reader.
                    fresh=c.SourceVisualReservations(m,db);self.assertEqual(fresh.authority({'registryId':'fixture:b'}),(cap_b,cap_ref,spec_b,ref_b));self.assertEqual(opened.call_count,2)
                    fresh_reader=fresh.__dict__['_source_jar'][1];reference=weakref.ref(fresh);del fresh;gc.collect();self.assertIsNone(reference());self.assertIsNone(fresh_reader.fp)
                    partial=object.__new__(c.SourceVisualReservations);partial.close();partial.__del__();partial.close()
                    # New contexts still check the actual resource bytes before
                    # opening a central directory; this is no hash bypass.
                    with open(alternate_jar,'ab') as stream:stream.write(b'changed-bound-resource')
                    m.meta(db,'binding',{**binding,'source':str(alternate/'working/copy')});wrong=c.SourceVisualReservations(m,db)
                    with self.assertRaisesRegex(RuntimeError,'resource binding changed'):wrong.authority({'registryId':'fixture:a'})
                    self.assertEqual(opened.call_count,2);wrong.close()
                    self.assertEqual(db.execute('SELECT 1').fetchone(),(1,));self.assertEqual(m.sha(jar),jar_sha);self.assertEqual(m.sha(caps),caps_sha)
            finally:db.close()


if __name__=='__main__':unittest.main()
