"""One real cached-source decoding fixture for the draft digest-only cache."""
import copy
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import migrate as m
import native_certificates as c


class SourceBeDigestCacheTests(unittest.TestCase):
    def test_real_source_shared_digest_and_payload_identity_fallbacks(self):
        with tempfile.TemporaryDirectory(prefix='source-be-digest-') as directory:
            plan=Path(directory);at=(1,2,3);region='region/r.0.0.mca';region_sha='immutable-region-fixture'
            fields={'id':m.Tag(8,'fixture:opaque'),'x':m.Tag(3,at[0]),'y':m.Tag(3,at[1]),'z':m.Tag(3,at[2]),'payload':m.Tag(10,{'private':m.Tag(11,list(range(1024)))})}
            changed=copy.deepcopy(fields);changed['payload'].value['private'].value[0]=99
            def old_database(path,be):
                db=sqlite3.connect(path);db.execute('CREATE TABLE chunk_cache(dim TEXT,x INTEGER,z INTEGER,region_path TEXT,region_hash TEXT,compression INTEGER,payload BLOB,PRIMARY KEY(dim,x,z))')
                for table in ('members','stored_contributions'):db.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,PRIMARY KEY(dim,x,y,z,uuid))')
                section=m.Tag(10,{'Y':m.Tag(1,0),'block_states':m.Tag(10,{'palette':m.Tag(9,[m.Tag(10,{'Name':m.Tag(8,'minecraft:stone')})],10)})})
                payload=m.encode_nbt(m.NbtFile('',m.Tag(10,{'sections':m.Tag(9,[section],10),'block_entities':m.Tag(9,[m.Tag(10,be)],10)})))
                db.execute('INSERT INTO chunk_cache VALUES(?,?,?,?,?,?,?)',('.',0,0,region,region_sha,3,payload))
                for uid in ('owner-a','owner-b'):
                    for table in ('members','stored_contributions'):db.execute('INSERT INTO '+table+' VALUES(?,?,?,?,?)',('.',*at,uid))
                db.commit();db.close()
            old_a=plan/'old-a.sqlite';old_b=plan/'old-b.sqlite';old_database(old_a,fields);old_database(old_b,changed);source_shas={str(path):m.sha(path) for path in (old_a,old_b)}
            manifest=plan/'manifest.json';m.atomic_json(manifest,{'files':{region:{'sha256':region_sha}}});db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix(),uri=True);db.executescript(m.SCHEMA)
            try:
                m.meta(db,'binding',{'copyManifest':str(manifest)});data=m.canonical({'registryId':'minecraft:stone','properties':{}});pose='sp_'+m.digest(m.json.loads(data))[:24];db.execute('INSERT INTO poses VALUES(?,?,2)',(pose,data))
                for uid in ('owner-a','owner-b'):db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.',uid,pose,*at,'static','unchanged','opaque'))
                db.commit();db.execute('ATTACH DATABASE ? AS old',('file:'+old_a.as_posix()+'?mode=ro',))
                expected=m.hashlib.sha256(m.encode_nbt(m.NbtFile('',m.Tag(10,fields)))).hexdigest();expected_changed=m.hashlib.sha256(m.encode_nbt(m.NbtFile('',m.Tag(10,changed)))).hexdigest();real=c.SourceVisualReservations
                envelope={'resource':{'path':'fixture','sha256':'fixture'},'cells':[(0,0,0)],'openable':False,'functionalDoor':False}
                with patch.object(real,'envelope',return_value=envelope),patch.object(real,'authority',return_value=({},None,{'essentialMask':[[0,0,0]],'support':{'required':False}},None)),patch.object(real,'native_origins',return_value=[]),patch.object(real,'resource',return_value={'path':'fixture','sha256':c.ORIGINAL_NATIVE_CATALOG_SHA}),patch.object(m,'legacy_native_catalog',return_value=({'poses':[],'recipes':[]},{})),patch.object(m,'encode_nbt',wraps=m.encode_nbt) as encode:
                    # source_point itself is real: SQLite payload verification,
                    # decode, BE-map and cachedPayloadSha256 are exercised.
                    context=real(m,db);first=context.original_domain('.','owner-a');second=context.original_domain('.','owner-b');zone=context.zone_snapshot('.',(0,0,0));again=context.original_domain('.','owner-a')
                    self.assertEqual(first,again);self.assertEqual(encode.call_count,1)
                    for domain in (first,second):self.assertEqual(domain['rootBeDigest'],expected);self.assertEqual(domain['domain'][0]['beDigest'],expected)
                    self.assertEqual(zone['typedBlockEntities'],[{'position':list(at),'typedBeDigest':expected}])
                    _,cached,proof=context.source_point('.',at);self.assertIs(cached,context.chunks[('.',0,0)][1][at]);self.assertEqual(context.source_be_digest('.',at,cached,proof),expected);self.assertEqual(encode.call_count,1)
                    # Arbitrary or mutable mocked results never have the exact
                    # current decoded-map object authority, so every call hashes.
                    mutable=copy.deepcopy(cached);self.assertEqual(context.source_be_digest('.',at,mutable,proof),expected);self.assertEqual(encode.call_count,2)
                    mutable['payload'].value['private'].value[0]=99;self.assertEqual(context.source_be_digest('.',at,mutable,proof),expected_changed);self.assertEqual(encode.call_count,3)
                    wrong_proof={**proof,'cachedPayloadSha256':'another-payload'};self.assertEqual(context.source_be_digest('.',at,cached,wrong_proof),expected);self.assertEqual(encode.call_count,4)
                    mocked=real(m,db)
                    with patch.object(mocked,'source_point',return_value=(m.AIR,mutable,proof)):
                        _,fake,fake_proof=mocked.source_point('.',at);self.assertEqual(mocked.source_be_digest('.',at,fake,fake_proof),expected_changed)
                        fake['payload'].value['private'].value[0]=0;self.assertEqual(mocked.source_be_digest('.',at,fake,fake_proof),expected)
                    self.assertEqual(encode.call_count,6);self.assertNotIn('_source_be_digests',mocked.__dict__)
                    # Evicted fields cannot use an old content-key hit without
                    # membership in the CURRENT source chunk cache entry.
                    context.chunks.clear();self.assertEqual(context.source_be_digest('.',at,cached,proof),expected);self.assertEqual(encode.call_count,7)
                    db.execute('DETACH DATABASE old');db.execute('ATTACH DATABASE ? AS old',('file:'+old_b.as_posix()+'?mode=ro',))
                    _,new_fields,new_proof=context.source_point('.',at);self.assertNotEqual(new_proof['cachedPayloadSha256'],proof['cachedPayloadSha256']);self.assertEqual(context.source_be_digest('.',at,new_fields,new_proof),expected_changed);self.assertEqual(encode.call_count,8)
                    fresh=real(m,db);fresh_domain=fresh.original_domain('.','owner-a');self.assertEqual(fresh_domain['rootBeDigest'],expected_changed);self.assertEqual(encode.call_count,9)
                    # Values retain no dictionaries/Tag references; LRU bound
                    # uses only content keys and complete SHA strings.
                    memo=fresh.__dict__['_source_be_digests'];key=next(iter(memo));memo.clear()
                    for index in range(4096):memo[('old-content','.',index,0,0)]='0'*64
                    self.assertEqual(fresh.source_be_digest('.',at,fresh.source_point('.',at)[1],new_proof),expected_changed);self.assertEqual(len(memo),4096);self.assertNotIn(('old-content','.',0,0,0),memo);self.assertEqual(next(reversed(memo)),key)
                    self.assertTrue(all(isinstance(value,str) and len(value)==64 for value in memo.values()));self.assertTrue(all(isinstance(item,tuple) and isinstance(item[0],str) and isinstance(item[1],str) and all(type(v)is int for v in item[2:]) for item in memo))
                self.assertEqual({str(path):m.sha(path) for path in (old_a,old_b)},source_shas)
            finally:db.close()


if __name__=='__main__':unittest.main()
