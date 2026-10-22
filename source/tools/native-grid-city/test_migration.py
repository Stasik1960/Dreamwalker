"""Destructive-path fixtures use synthetic NBT only, never a game map."""
import importlib.util, json, sqlite3, tempfile, unittest
from types import SimpleNamespace
from unittest.mock import patch
from pathlib import Path
SPEC=importlib.util.spec_from_file_location('native_migration',Path(__file__).with_name('migrate.py'))
m=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(m)
from world_io import RegionFile

class MigrationFixtures(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='native-grid-writer-',dir=m.ROOT/'research')
        self.path=Path(self.temporary.name)
        self.connections=[]
    def tearDown(self):
        # TemporaryDirectory contains only files created by this fixture. No
        # external paths or reparse points are ever admitted to this directory.
        for connection in self.connections:connection.close()
        self.temporary.cleanup()
    def database(self):
        db=sqlite3.connect(self.path/'fixture.sqlite');db.executescript(m.SCHEMA);self.connections.append(db);return db
    def chunk(self,cx=0):
        return m.NbtFile('',m.Tag(10,{'xPos':m.Tag(3,cx),'zPos':m.Tag(3,0),'sections':m.Tag(9,[],10),'block_entities':m.Tag(9,[],10),'Heightmaps':m.Tag(10,{'WORLD_SURFACE':m.Tag(12,[1])})}))
    def test_stream_preserves_untouched_compressed_chunk_and_source(self):
        source=self.path/'source.mca';target=self.path/'target.mca';rf=RegionFile()
        rf.set_chunk(0,0,self.chunk(),timestamp=123);rf.set_chunk(1,0,self.chunk(1),timestamp=456);source.write_bytes(rf.to_bytes());before=source.read_bytes()
        unchanged=rf.get_chunk(1,0)
        def transform(lx,lz,compression,payload):
            if lx!=0:return None
            nbt=m.decode_nbt(payload,compressed='zlib');m.rewrite_states(m.compound(nbt.root),{(0,64,0):{'registryId':'bloodborne_dw:native_grid_0','properties':{'part':'7'}}});return nbt
        stats=m.stream_region(source,target,transform);actual=RegionFile.open(target)
        self.assertEqual(source.read_bytes(),before);self.assertEqual(stats['changedChunks'],1)
        self.assertEqual(actual.get_chunk(1,0).compressed_payload,unchanged.compressed_payload)
        self.assertEqual(actual.get_chunk(1,0).timestamp,456)
        root=m.compound(actual.get_chunk(0,0).nbt().root);self.assertIn('Heightmaps',root)
    def test_recovery_after_atomic_swap_before_database_commit(self):
        db=self.database();target=self.path/'world';target.mkdir();file=target/'fixture.bin';file.write_bytes(b'after')
        before=m.hashlib.sha256(b'before').hexdigest();after=m.sha(file);plan=self.path/'plan';plan.mkdir()
        rel='fixture.bin';receipt=plan/'journals'/(m.hashlib.sha256(rel.encode()).hexdigest()+'.json')
        m.atomic_json(receipt,{'path':rel,'beforeSha256':before,'afterSha256':after,'stats':{'changed':1}})
        m.file_commit(db,plan,target,rel,'fixture',lambda *_:self.fail('Recovery repeated mutation'))
        self.assertEqual(db.execute('SELECT phase,after_hash FROM files').fetchone(),('committed',after))
        file.write_bytes(b'foreign')
        with self.assertRaisesRegex(RuntimeError,'Committed file changed'):m.file_commit(db,plan,target,rel,'fixture',lambda *_:{})
    def test_dictionary_removes_only_selected_and_rebases_retained_members(self):
        db=self.database();removed=[1,2,3,4];kept=[5,6,7,8]
        db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',m.canonical(removed),'static'))
        owner=lambda uid:m.Tag(10,{'uuid':m.Tag(11,uid),'root':m.Tag(11,[0,64,0])})
        nbt=m.NbtFile('',m.Tag(10,{'Dictionary':m.Tag(3,1),'ownerTable':m.Tag(9,[owner(removed),owner(kept)],10),
          'dataTable':m.Tag(9,[m.Tag(10,{'id':m.Tag(8,'removed')}),m.Tag(10,{'id':m.Tag(8,'retained')})],10),
          'shapeTable':m.Tag(9,[m.Tag(10,{}),m.Tag(10,{'exact':m.Tag(6,.375)})],10),
          'cells':m.Tag(9,[m.Tag(10,{'refs':m.Tag(9,[m.Tag(11,[0,0,0]),m.Tag(11,[1,1,1])],11)})],10),
          'ownerMembership':m.Tag(9,[m.Tag(10,{'ownerRef':m.Tag(3,1),'members':m.Tag(9,[m.Tag(10,{'shapeRef':m.Tag(3,1)})],10)})],10)}))
        stats=m.cleanup_dictionary(db,'.',nbt);root=m.compound(nbt.root)
        self.assertEqual(stats['removedOwners'],1);self.assertEqual(m.owner_uuid(root['ownerTable'].value[0]),m.canonical(kept))
        self.assertEqual(m.compound(root['cells'].value[0])['refs'].value[0].value,[0,0,0])
        member=m.compound(root['ownerMembership'].value[0]);self.assertEqual(member['ownerRef'].value,0)
        self.assertEqual(m.compound(member['members'].value[0])['shapeRef'].value,0)
        self.assertEqual(m.compound(root['shapeTable'].value[0])['exact'].value,.375)
    def test_region_rejects_overlapping_allocations(self):
        rf=RegionFile();rf.set_chunk(0,0,self.chunk());data=bytearray(rf.to_bytes());data[4:8]=data[0:4]
        source=self.path/'invalid.mca';source.write_bytes(data)
        with self.assertRaisesRegex(RuntimeError,'Overlapping'):m.stream_region(source,self.path/'out.mca',lambda *_:None)
    def test_region_accepts_zero_bytes_but_rejects_nonempty_short_header(self):
        source=self.path/'empty.mca';target=self.path/'empty-copy.mca';source.write_bytes(b'')
        self.assertEqual(m.stream_region(source,target,lambda *_:None)['emptyRegion'],1);self.assertEqual(target.read_bytes(),b'')
        source.write_bytes(b'not-a-region')
        with self.assertRaisesRegex(RuntimeError,'Short region header'):m.stream_region(source,target,lambda *_:None)
    def test_source_clone_resumes_committed_empty_entity_file(self):
        old=self.path/'old';source=self.path/'source';plan=self.path/'plan';target=self.path/'copy';old.mkdir();(source/'entities').mkdir(parents=True)
        (source/'entities'/'r.0.0.mca').write_bytes(b'');(source/'level.dat').write_bytes(b'synthetic-level-fixture')
        manifest={'source':str(source),'files':{p.relative_to(source).as_posix():{'bytes':p.stat().st_size,'sha256':m.sha(p)} for p in (source/'entities'/'r.0.0.mca',source/'level.dat')}}
        m.atomic_json(old/'copy.json',manifest);saved=sqlite3.connect(old/'plan.sqlite')
        for table in ('stored_contributions','members'):saved.execute('CREATE TABLE '+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT)')
        saved.commit();saved.close();db=m.connect(plan,old);m.meta(db,'oldPlan',str(old));m.meta(db,'scope',{'dimension':'.','rootBounds':[0,15,0,15]});db.commit();db.close()
        args=SimpleNamespace(source_plan=old,plan=plan,target=target,bounds=[0,15,0,15],context_radius=1,extra_chunks=None)
        with patch.object(m.shutil,'copy2',side_effect=RuntimeError('simulated-copy-interruption')):
            with self.assertRaisesRegex(RuntimeError,'simulated-copy-interruption'):m.clone(args)
        self.assertEqual((target/'entities'/'r.0.0.mca').read_bytes(),b'')
        m.clone(args);receipt=m.read(target.parent/(target.name+'-copy.json'))
        self.assertEqual(receipt['status'],'PASS');self.assertEqual((target/'level.dat').read_bytes(),(source/'level.dat').read_bytes())
    def test_partial_be_cleanup_preserves_foreign_typed_payload(self):
        db=self.database();gone=[1,2,3,4];kept=[5,6,7,8]
        db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',m.canonical(gone),'static'))
        contribution=lambda uid:m.Tag(10,{'owner':m.Tag(10,{'uuid':m.Tag(11,uid),'root':m.Tag(11,[0,64,0])}),'exact':m.Tag(6,.125)})
        nbt=self.chunk();root=m.compound(nbt.root)
        be=m.Tag(10,{'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'id':m.Tag(8,'bloodborne_dw:composite_cell'),'owners':m.Tag(9,[contribution(gone),contribution(kept)],10),'foreignGameData':m.Tag(11,[7,8,9])})
        root['block_entities']=m.Tag(9,[be],10);root['isLightOn']=m.Tag(1,1)
        result=m.transformed_chunk(db,'.',0,0,2,m.zlib.compress(m.encode_nbt(nbt)));actual=m.compound(result.root)
        retained=m.compound(actual['block_entities'].value[0]);owners=m.be_owners(retained)
        self.assertEqual(len(owners),1);self.assertEqual(m.owner_uuid(m.compound(owners[0])['owner']),m.canonical(kept))
        self.assertEqual(retained['foreignGameData'],m.Tag(11,[7,8,9]));self.assertEqual(m.compound(owners[0])['exact'].value,.125)
        self.assertNotIn('isLightOn',actual);self.assertIn('Heightmaps',actual)
        self.assertEqual(db.execute('SELECT count(*) FROM relight').fetchone()[0],9)
    def test_placement_cannot_erase_retained_be(self):
        db=self.database();db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',0,64,0,0,0,'test',0,m.canonical(m.AIR),0))
        nbt=self.chunk();m.compound(nbt.root)['block_entities']=m.Tag(9,[m.Tag(10,{'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'id':m.Tag(8,'minecraft:chest'),'Items':m.Tag(9,[],10)})],10)
        raw=m.zlib.compress(m.encode_nbt(nbt))
        with self.assertRaisesRegex(RuntimeError,'retained BlockEntity'):m.transformed_chunk(db,'.',0,0,2,raw)
    def test_consumed_native_helper_activates_its_real_root(self):
        db=self.database();root=(10,64,10);uid=m.synthetic_uuid('.',root,'legacy_pose')
        db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.',uid,'current_pose',*root,'substrate','native','selected'))
        catalog={'recipes':[{'fragments':[{'pose':'legacy_pose','origin':[-1,0,0]}]}]}
        with patch.object(m,'legacy_native_catalog',return_value=(catalog,{})):
            found=m.native_roots_at(db,'.',(11,64,10),{'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':'0'}})
            self.assertEqual(found,[(uid,'current_pose',*root)])
            db.execute("UPDATE objects SET kind='preserve',reason='historic_v12'")
            with self.assertRaisesRegex(RuntimeError,'retained/protected'):m.native_roots_at(db,'.',(11,64,10),{'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':'0'}})
    def test_container_is_retained_before_compilation(self):
        db=self.database();uid='[1,2,3,4]'
        db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.',uid,'pose',0,64,0,'static'))
        db.execute('INSERT INTO fragments VALUES(?,?,?,?,?,?,?,?,?)',('.',0,64,0,uid,'pose',0,0,0))
        nbt=self.chunk();m.compound(nbt.root)['block_entities']=m.Tag(9,[m.Tag(10,{'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'id':m.Tag(8,'minecraft:chest'),'Items':m.Tag(9,[],10)})],10)
        with patch.object(m,'get_source_chunk',return_value=(nbt,'fixture',None)):
            report=m.preflight_functional_guests(db,self.path)
        self.assertEqual(report['functionalGuestCells'],1);self.assertEqual(report['directlyRetainedObjects'],1)
        self.assertEqual(db.execute('SELECT count(*) FROM selected_objects').fetchone()[0],0)
        self.assertEqual(db.execute('SELECT count(*) FROM fragments').fetchone()[0],0)
        self.assertEqual(db.execute('SELECT reason FROM protected_cells').fetchone()[0],'functional_guest')
    def test_complete_native_base_uses_actual_model_not_legacy_pose(self):
        db=self.database()
        with patch.object(m,'legacy_native_catalog',side_effect=AssertionError('Native model must not decompose a different legacy recipe')):
            self.assertEqual(m.native_roots_at(db,'.',(0,64,0),{'registryId':'bloodborne_dw:ordinary_compact','properties':{'compact':'true'}},{'nativeVisualComplete':True}),[])
    def test_bridge_keeps_original_protected_resident_and_payload(self):
        db=self.database();gone=[1,2,3,4];stay=[5,6,7,8];host={'registryId':'bloodborne_dw:prototype_double_door','properties':{'open':'false'}}
        db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',m.canonical(gone),'static'))
        db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',0,64,0,0,0,'cell',42,m.canonical(host),0))
        db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',0,64,0,m.canonical(host),1,42))
        owner=lambda uid:m.Tag(10,{'uuid':m.Tag(11,uid),'root':m.Tag(11,[0,64,0])})
        contribution=lambda uid:m.Tag(10,{'owner':owner(uid)})
        payload=m.Tag(10,{'exactGameplayByte':m.Tag(1,7),'typedArray':m.Tag(11,[2,4,6])})
        nbt=self.chunk();root=m.compound(nbt.root);m.rewrite_states(root,{(0,64,0):host})
        root['block_entities']=m.Tag(9,[m.Tag(10,{'id':m.Tag(8,'bloodborne_dw:composite_cell'),'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'resident':owner(stay),'payload':payload,'owners':m.Tag(9,[contribution(gone),contribution(stay)],10)})],10)
        result=m.transformed_chunk(db,'.',0,0,2,m.zlib.compress(m.encode_nbt(nbt)));actual=m.compound(result.root);be=m.compound(actual['block_entities'].value[0])
        self.assertEqual(be['payload'],payload);self.assertEqual(m.owner_uuid(be['resident']),m.canonical(stay));self.assertEqual(be['NativeGridBase'],m.Tag(3,42))
        self.assertEqual([m.owner_uuid(m.compound(t)['owner']) for t in m.be_owners(be)],[m.canonical(stay)])
        sections={m.compound(s)['Y'].value:s for s in actual['sections'].value};self.assertEqual(m.source_state(sections,(0,64,0)),host)
    def test_bridge_removed_decorative_resident_becomes_generic_helper(self):
        db=self.database();gone=[1,2,3,4];stay=[5,6,7,8];host={'registryId':'bloodborne_dw:composite_cell','properties':{}}
        db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',m.canonical(gone),'static'))
        db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',0,64,0,0,0,'cell',88,m.canonical(m.AIR),0))
        db.execute('INSERT INTO bridge_cells VALUES(?,?,?,?,?,?,?)',('.',0,64,0,m.canonical(host),0,88))
        owner=lambda uid:m.Tag(10,{'uuid':m.Tag(11,uid),'root':m.Tag(11,[0,64,0])})
        nbt=self.chunk();root=m.compound(nbt.root)
        root['block_entities']=m.Tag(9,[m.Tag(10,{'id':m.Tag(8,'bloodborne_dw:composite_cell'),'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'resident':owner(gone),'payload':m.Tag(10,{'oldDecorPose':m.Tag(3,1)}),'owners':m.Tag(9,[m.Tag(10,{'owner':owner(uid)}) for uid in (gone,stay)],10)})],10)
        result=m.transformed_chunk(db,'.',0,0,2,m.zlib.compress(m.encode_nbt(nbt)));actual=m.compound(result.root);be=m.compound(actual['block_entities'].value[0])
        self.assertNotIn('resident',be);self.assertEqual(be['payload'],m.Tag(10,{}));self.assertEqual(be['NativeGridBase'].value,88)
        sections={m.compound(s)['Y'].value:s for s in actual['sections'].value};self.assertEqual(m.source_state(sections,(0,64,0)),host)
    def test_unknown_cleanup_only_helper_data_blocks_apply_preflight(self):
        db=self.database();db.execute("ATTACH DATABASE ':memory:' AS old")
        for table in ('stored_contributions','members'):db.execute('CREATE TABLE old.'+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT)')
        uid='[1,2,3,4]';pose={'registryId':'bloodborne_dw:source_bricks','properties':{},'shift':[0,0,0],'mountY':0,'legacyStairArt':''}
        db.execute('INSERT INTO poses VALUES(?,?,?)',('pose',m.canonical(pose),1));db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.',uid,'pose',0,64,0,'static'))
        db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',uid,'static'));db.execute('INSERT INTO old.stored_contributions VALUES(?,?,?,?,?)',('.',1,64,0,uid))
        decisions=self.path/'payload.json';m.atomic_json(decisions,{'decisions':[]});m.meta(db,'payloadDecisions',{'path':str(decisions)});m.meta(db,'catalog',{'sha256':'fixture'})
        nbt=self.chunk();m.compound(nbt.root)['block_entities']=m.Tag(9,[m.Tag(10,{'id':m.Tag(8,'bloodborne_dw:composite_cell'),'x':m.Tag(3,1),'y':m.Tag(3,64),'z':m.Tag(3,0),'owners':m.Tag(9,[m.Tag(10,{'owner':m.Tag(10,{'uuid':m.Tag(11,[1,2,3,4])})})],10),'unclassifiedGameState':m.Tag(11,[7,8,9])})],10)
        with patch.object(m,'connect',return_value=db),patch.object(m,'get_source_chunk',return_value=(nbt,'fixture',None)):
            with self.assertRaisesRegex(RuntimeError,'Actual cleanup/resident data'):m.audit_cleanup_bes(SimpleNamespace(plan=self.path))
        report=m.read(self.path/'cleanup-be-audit.json');self.assertEqual(report['status'],'FAIL');self.assertEqual(report['failures'][0]['reason'],'unknown_outer_payload_on_deleted_be')
    def test_pilot_index_filter_preserves_every_other_typed_field(self):
        packed=lambda x,z:((x&0xffffffff)|((z&0xffffffff)<<32))-(1<<64 if z<0 else 0)
        root={'DataVersion':m.Tag(3,3465),'data':m.Tag(10,{'OwnerShardSchema':m.Tag(3,1),'OwnerShardChunks':m.Tag(12,[packed(-7,4),packed(17,-56),packed(60,7)]),'cells':m.Tag(9,[],10),'private':m.Tag(11,[5,6,7])})}
        nbt=m.NbtFile('',m.Tag(10,root));before=m.decode_nbt(m.encode_nbt(nbt));data=m.compound(root['data']);order=list(data)
        proof=m.crop_pilot_global_index(nbt,{'data/bloodborne_dw_owner_shards/owners--7.4.nbt':'sha','data/bloodborne_dw_owner_shards/owners-17.-56.nbt':'sha'})
        self.assertEqual(proof['indexBefore'],3);self.assertEqual(proof['indexAfter'],2);self.assertEqual(data['OwnerShardChunks'].value,[packed(-7,4),packed(17,-56)]);self.assertEqual(list(data),order)
        m.compound(before.root)['data'].value.pop('OwnerShardChunks');data.pop('OwnerShardChunks');self.assertEqual(m.encode_nbt(before),m.encode_nbt(nbt))
    def test_pilot_global_before_override_is_narrow_and_never_full_city(self):
        db=self.database();receipt=self.path/'receipt.json';m.atomic_json(receipt,{'exact':'fixture'});target=self.path/'candidate';rel='data/bloodborne_dw_composite_owners.dat'
        m.meta(db,'scope',{'rootBounds':[0,15,0,15]});m.meta(db,'pilotMetadataBinding',{'path':str(receipt),'sha256':m.sha(receipt),'target':str(target),'globalLedger':{'path':rel,'sourceSha256':'source','beforeCleanupSha256':'cropped'}})
        self.assertEqual(m.pilot_ledger_before_hash(db,target,rel,'source'),'cropped');self.assertEqual(m.pilot_ledger_before_hash(db,target,'data/other.dat','untouched'),'untouched')
        with self.assertRaisesRegex(RuntimeError,'immutable source'):m.pilot_ledger_before_hash(db,target,rel,'foreign')
        db.execute("DELETE FROM meta WHERE key='scope'")
        with self.assertRaisesRegex(RuntimeError,'full-city'):m.pilot_ledger_before_hash(db,target,rel,'source')
    def test_storage_rebind_rejects_unrelated_jar_entry_change(self):
        def jar(path,values):
            with m.zipfile.ZipFile(path,'w') as archive:
                for name,value in values.items():archive.writestr(name,value)
        old=self.path/'old.jar';new=self.path/'new.jar';data={m.STORAGE_CLASS_PREFIX+'.class':b'old',m.STORAGE_MIXIN_CLASS:b'old mixin',m.CATALOG_REL:b'catalog','safe/light.class':b'same light'}
        jar(old,data);jar(new,{**data,m.STORAGE_CLASS_PREFIX+'.class':b'new',m.STORAGE_CLASS_PREFIX+'$Added.class':b'new inner'})
        proof=m.storage_jar_proof(old,new);self.assertTrue(proof['allOtherEntriesByteIdentical']);self.assertEqual(len(proof['changedEntries']),2)
        jar(new,{**data,m.STORAGE_CLASS_PREFIX+'.class':b'new','safe/light.class':b'changed light'})
        with self.assertRaisesRegex(RuntimeError,'outside the two authorized'):m.storage_jar_proof(old,new)
    def test_storage_rebind_adopts_only_flushed_native_cursor_rows(self):
        queue=b'# native-grid-relight-v1\tdimension\tcx\tcz\nminecraft:overworld\t0\t0\nminecraft:overworld\t1\t0\n';qh=m.hashlib.sha256(queue).hexdigest();body=b'minecraft:overworld\t0\t0\n';cursor=('# '+qh+' old\n').encode()+body
        engine={'nativeEngine':True,'saveFlushed':True,'queueSha256':qh,'runtimeSha256':'old','queued':2,'processed':1,'pending':1}
        adopted,proof=m.adopted_light_cursor(queue,cursor,'old','new',engine)
        self.assertEqual(adopted,('# '+qh+' new\n').encode()+body);self.assertEqual(proof['processed'],1);self.assertEqual(proof['pending'],1)
        with self.assertRaisesRegex(RuntimeError,'torn'):m.adopted_light_cursor(queue,cursor[:-1],'old','new',engine)
        with self.assertRaisesRegex(RuntimeError,'prove every'):m.adopted_light_cursor(queue,cursor,'old','new',{**engine,'processed':2,'pending':0})
        with self.assertRaisesRegex(RuntimeError,'exact completed prefix'):m.adopted_light_cursor(queue,('# '+qh+' old\nminecraft:overworld\t1\t0\n').encode(),'old','new',engine)
    def test_storage_metadata_swap_recovers_exact_before_or_after_only(self):
        path=self.path/'binding.json';path.write_bytes(b'old binding');old=m.sha(path)
        m._rebind_replace(path,b'new binding',old);m._rebind_replace(path,b'new binding',old);self.assertEqual(path.read_bytes(),b'new binding')
        path.write_bytes(b'foreign binding')
        with self.assertRaisesRegex(RuntimeError,'unrelated bytes'):m._rebind_replace(path,b'new binding',old)
    def test_storage_rebind_resumes_between_metadata_swap_and_sqlite_commit(self):
        db=self.database();plan=self.path/'plan';plan.mkdir();project=self.path/'project';project.mkdir();world=self.path/'world';world.mkdir()
        m.atomic_json(world/'NATIVE-GRID-INCOMPLETE.json',{'status':'DO_NOT_OPEN'});marker=(world/'NATIVE-GRID-INCOMPLETE.json').read_bytes()
        source_rows=[]
        for rel in sorted(m.STORAGE_SOURCE_PATHS):
            before=self.path/'original'/rel;after=project/rel;before.parent.mkdir(parents=True,exist_ok=True);after.parent.mkdir(parents=True,exist_ok=True);before.write_bytes(b'old source');after.write_bytes(b'new source')
            source_rows.append({'path':rel,'beforePath':str(before),'beforeSha256':m.sha(before),'afterSha256':m.sha(after)})
        source_receipt=self.path/'source-receipt.json';m.atomic_json(source_receipt,{'schemaVersion':1,'sourceRoot':str(project),'sourceEditsLimitedToListedFiles':True,'files':source_rows})
        catalog_path=self.path/'catalog.json';catalog_path.write_bytes(b'{"schemaVersion":1}');mesh=self.path/'meshes.bin';mesh.write_bytes(b'mesh')
        catalog={'path':str(catalog_path),'sha256':m.sha(catalog_path),'meshesSha256':m.sha(mesh)}
        old=self.path/'old.jar';new=self.path/'new.jar'
        for path,code in ((old,b'old'),(new,b'new')):
            with m.zipfile.ZipFile(path,'w') as archive:
                archive.writestr(m.STORAGE_CLASS_PREFIX+'.class',code);archive.writestr(m.STORAGE_MIXIN_CLASS,code);archive.writestr(m.CATALOG_REL,catalog_path.read_bytes());archive.writestr('assets/bloodborne_dw/native_grid/meshes.bin',mesh.read_bytes());archive.writestr('light.class',b'exact old light')
        old_hash,new_hash=m.sha(old),m.sha(new);old_runtime={'path':str(old),'sha256':old_hash};helper=self.path/'helper.jar';helper.write_bytes(b'unchanged actual native lighting helper')
        matrix=self.path/'matrix.json';m.atomic_json(matrix,{'pairs':[{'cell':0}]});matrix_binding={'path':str(matrix),'sha256':m.sha(matrix),'pairs':1}
        old_gate=self.path/'old-gate.json';m.atomic_json(old_gate,{'runtimeSha256':old_hash});old_gate_binding={'path':str(old_gate),'sha256':m.sha(old_gate)}
        new_gate=self.path/'new-gate.json';m.atomic_json(new_gate,{'status':'PASS','nativeRegistryQueries':True,'runtimeSha256':new_hash,'catalogSha256':catalog['sha256'],'bridgePlanSha256':m.sha(matrix),'pairsChecked':1})
        frozen={'plannerSha256':'original-planner','semanticGates':{'bridgeCarrierGate':old_gate_binding},'counts':{'placements':0}}
        for key,value in {'offlineApplied':True,'applyStarted':True,'scope':{'rootBounds':[0,15,0,15]},'target':str(world),'runtime':old_runtime,'catalog':catalog,'bridgeHostStates':matrix_binding,'bridgeCarrierGate':old_gate_binding,'frozenInputs':frozen}.items():m.meta(db,key,value)
        db.execute('INSERT INTO relight(dim,cx,cz,reason) VALUES(?,?,?,?)',('.',0,0,'fixture'));db.execute('INSERT INTO relight(dim,cx,cz,reason) VALUES(?,?,?,?)',('.',1,0,'fixture'));db.execute('INSERT INTO files VALUES(?,?,?,?,?,?)',('region/r.0.0.mca','source','applied','committed','region','{}'));db.commit();fingerprint=m.storage_plan_fingerprint(db)
        queue=b'# native-grid-relight-v1\tdimension\tcx\tcz\nminecraft:overworld\t0\t0\nminecraft:overworld\t1\t0\n';(plan/'relight-queue.tsv').write_bytes(queue);qh=m.hashlib.sha256(queue).hexdigest();done=('# '+qh+' '+old_hash+'\nminecraft:overworld\t0\t0\n').encode();(plan/'relight-engine.json.done.tsv').write_bytes(done)
        binding={'schemaVersion':1,'queueSha256':qh,'queued':2,'target':str(world),'catalog':catalog,'runtime':old_runtime}
        m.atomic_json(plan/'relight-binding.json',binding);m.atomic_json(plan/'frozen-inputs.json',frozen)
        m.atomic_json(plan/'relight-engine.json',{'nativeEngine':True,'saveFlushed':True,'world':str(world),'catalogSha256':catalog['sha256'],'queueSha256':qh,'runtimeSha256':old_hash,'queued':2,'processed':1,'pending':1})
        m.atomic_json(plan/'relight-launch-binding.json',{'runtimeSha256':old_hash,'helperSha256':m.sha(helper),'queueSha256':qh});m.atomic_json(plan/'relight-launch-receipt.json',{'status':'FAIL','runtimeSha256':old_hash});(plan/'relight-server-console.log').write_text('java.lang.OutOfMemoryError: Java heap space',encoding='utf-8')
        args=SimpleNamespace(plan=plan,old_runtime_jar=old,runtime_jar=new,receipt=new_gate,source_receipt=source_receipt,helper_jar=helper);replace=m._rebind_replace;calls=[]
        def crash_after_first_swap(*values):
            replace(*values);calls.append(True)
            if len(calls)==1:raise RuntimeError('simulated-rebind-interruption')
        new_connection=lambda _:sqlite3.connect(self.path/'fixture.sqlite')
        with patch.object(m,'PROJECT',project),patch.object(m,'connect',side_effect=new_connection):
            with patch.object(m,'_rebind_replace',side_effect=crash_after_first_swap):
                with self.assertRaisesRegex(RuntimeError,'simulated-rebind-interruption'):m.rebind_storage_runtime(args)
            self.assertEqual(m.read(plan/'relight-binding.json')['runtime']['sha256'],new_hash)
            m.rebind_storage_runtime(args);m.rebind_storage_runtime(args)
        self.assertEqual(m.meta(db,'runtime')['sha256'],new_hash);self.assertEqual(m.storage_plan_fingerprint(db),fingerprint)
        self.assertEqual((world/'NATIVE-GRID-INCOMPLETE.json').read_bytes(),marker);self.assertEqual((plan/'relight-engine.json.done.tsv').read_bytes().split(b'\n',1)[1],done.split(b'\n',1)[1]);self.assertEqual((plan/'relight-queue.tsv').read_bytes(),queue)
        directory=next((plan/'runtime-transitions').iterdir());completion=m.read(directory/'completion.json');self.assertEqual(completion['adoptedSavedChunks'],1);self.assertEqual(completion['pendingChunks'],1);self.assertEqual((directory/'before'/'relight-engine.json.done.tsv').read_bytes(),done)
    def context_stage_fixture(self):
        db=self.database();plan=self.path/'plan';plan.mkdir();(plan/'context-extension').mkdir();(plan/'journals').mkdir();m.atomic_json(plan/'journals'/'atomic-file.json',{'before':'exact source','after':'exact committed'})
        world=self.path/'candidate';world.mkdir();(world/'untouched-fixture-map.bin').write_bytes(b'never rewritten')
        catalog=self.path/'catalog.json';m.atomic_json(catalog,{key:[] for key in ('cells','meshes','materials','shapes','optics','wholeObjects')});(self.path/'meshes.bin').write_bytes(b'NGQ1'+m.struct.pack('>i',0));runtime=self.path/'production.jar';runtime.write_bytes(b'exact production fixture')
        catalog_binding={'path':str(catalog),'sha256':m.sha(catalog),'meshesSha256':m.sha(self.path/'meshes.bin')};runtime_binding={'path':str(runtime),'sha256':m.sha(runtime)}
        for key,value in {'offlineApplied':True,'applyStarted':True,'scope':{'dimension':'.','rootBounds':[0,15,0,15]},'target':str(world),'catalog':catalog_binding,'runtime':runtime_binding}.items():m.meta(db,key,value)
        db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.','uuid','pose',0,64,0,'static'));db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.','uuid','static'));db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.','uuid','pose',0,64,0,'static','fixture','selected'));db.execute('INSERT INTO files VALUES(?,?,?,?,?,?)',('region/r.0.0.mca','before','after','committed','region','{}'));db.commit()
        copy_path=self.path/'candidate-copy.json';m.atomic_json(copy_path,{'status':'PASS','selectedChunkSetSha256':'allowed-fixture'});copy=m.read(copy_path)
        eligible=plan/'context-extension'/'eligible.jsonl';eligible.write_text(m.canonical({'dimension':'.','uuid':'uuid','pose':'pose','root':[0,64,0],'kind':'static'})+'\n',encoding='utf-8');boundary=plan/'context-extension'/'boundary.json';m.atomic_json(boundary,{'entries':[]})
        light={'savedRows':['verified separately'], 'fixtureScope':'Only testing stage journal/transaction recovery; native saved-light proof has independent tests.'}
        inv={'status':'DRY_RUN','kind':'SAME_PILOT_CONTEXT_EXTENSION','target':str(world),'allowedChunks':[[0,0]],'mainChunks':[[0,0]],'rootBounds':[0,15,0,15],'allowedChunkSetSha256':'allowed-fixture','originalCopyReceipt':str(copy_path),'originalCopyReceiptSha256':m.sha(copy_path),'parentPlanFingerprint':m.storage_plan_fingerprint(db),'parentCatalog':catalog_binding,'parentRuntime':runtime_binding,'eligibleOwners':{'path':str(eligible),'sha256':m.sha(eligible)},'specificBoundaryRetentions':{'path':str(boundary),'sha256':m.sha(boundary)},'nativeSavedLight':light}
        inventory=plan/'context-extension'/'inventory.json';m.atomic_json(inventory,inv)
        return db,SimpleNamespace(plan=plan,inventory=inventory),world,copy,light,inv
    def exercise_context_stage_interruption(self,window):
        db,args,world,copy,light,inventory=self.context_stage_fixture();real_replace=m.context_stage.os.replace;real_json=m.atomic_json;fired=[]
        def replace(src,dst):
            if Path(src)==args.plan/'journals' and not fired:
                fired.append(window)
                if window=='before-move':raise RuntimeError('stage interrupted before move')
                real_replace(src,dst)
                if window=='after-move':raise RuntimeError('stage interrupted after move')
                return
            return real_replace(src,dst)
        def write_json(path,value):
            real_json(path,value)
            if Path(path).name=='context-extension-receipt.json' and window=='receipt-before-commit' and 'receipt' not in fired:
                fired.append('receipt');raise RuntimeError('stage interrupted after receipt')
        connect=lambda _:sqlite3.connect(self.path/'fixture.sqlite')
        with patch.object(m,'connect',side_effect=connect),patch.object(m.context_stage,'allowed_copy',return_value=(world,copy,{(0,0)},{(0,0)})),patch.object(m.context_stage,'saved_light',return_value=light):
            with patch.object(m.context_stage.os,'replace',side_effect=replace),patch.object(m,'atomic_json',side_effect=write_json):
                with self.assertRaisesRegex(RuntimeError,'stage interrupted'):m.context_stage.stage(args,m)
            self.assertIsNone(m.meta(db,'contextExtension'));self.assertEqual(m.storage_plan_fingerprint(db),inventory['parentPlanFingerprint']);self.assertFalse(db.execute("SELECT 1 FROM sqlite_master WHERE name='stage1_placements'").fetchone())
            m.context_stage.stage(args,m);m.context_stage.stage(args,m)
        self.assertEqual(db.execute('SELECT count(*) FROM selected_objects').fetchone()[0],0);self.assertEqual(db.execute('SELECT count(*) FROM stage1_selected_objects').fetchone()[0],1);self.assertEqual(db.execute('SELECT count(*) FROM objects').fetchone()[0],1)
        self.assertEqual((world/'untouched-fixture-map.bin').read_bytes(),b'never rewritten');self.assertEqual(m.read(args.plan/'context-extension'/'stage-intent.json')['phase'],'PREPARED');self.assertFalse((args.plan/'journals').exists());self.assertTrue((args.plan/'stage-history'/'stage-001'/'journals'/'atomic-file.json').is_file())
    def test_context_stage_resume_before_journal_move(self):self.exercise_context_stage_interruption('before-move')
    def test_context_stage_resume_after_journal_move(self):self.exercise_context_stage_interruption('after-move')
    def test_context_stage_resume_after_receipt_before_database_commit(self):self.exercise_context_stage_interruption('receipt-before-commit')
    def test_context_logical_carrier_requires_actual_selected_membership(self):
        db=self.database();db.execute("ATTACH DATABASE ':memory:' AS old")
        for table in ('members','stored_contributions'):db.execute('CREATE TABLE old.'+table+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT)')
        state={'registryId':'bloodborne_dw:source_compatibility_fragment','properties':{'part':'0'}};at=(0,64,0);uid='[1,2,3,4]'
        self.assertFalse(m.context_stage.logical_selected_carrier(m,db,'.',at,state))
        db.execute('INSERT INTO old.members VALUES(?,?,?,?,?)',('.',*at,uid));self.assertFalse(m.context_stage.logical_selected_carrier(m,db,'.',at,state))
        db.execute('INSERT INTO selected_objects VALUES(?,?,?,?,?,?,?)',('.',uid,'pose',*at,'static'));self.assertTrue(m.context_stage.logical_selected_carrier(m,db,'.',at,state))
        db.execute('INSERT INTO objects VALUES(?,?,?,?,?,?,?,?,?)',('.','native','substratepose',*at,'substrate','native','preserve'));self.assertFalse(m.context_stage.logical_selected_carrier(m,db,'.',at,state))
    def context_content_fixture(self):
        db=self.database()
        for table in ('placements','bridge_cells','cleanup_owners'):db.execute('CREATE TABLE stage1_'+table+' AS SELECT * FROM '+table)
        db.execute('CREATE TABLE context_allowed_chunks(cx INTEGER,cz INTEGER,PRIMARY KEY(cx,cz))');db.execute('INSERT INTO context_allowed_chunks VALUES(0,0)');db.execute('CREATE TABLE context_changed_chunks(dim TEXT,cx INTEGER,cz INTEGER,PRIMARY KEY(dim,cx,cz))')
        source=self.chunk();at=(0,64,0);gone=[1,2,3,4];stay=[5,6,7,8];owner=lambda uid:m.Tag(10,{'uuid':m.Tag(11,uid),'root':m.Tag(11,list(at))})
        m.compound(source.root)['block_entities']=m.Tag(9,[m.Tag(10,{'id':m.Tag(8,'bloodborne_dw:composite_cell'),'x':m.Tag(3,0),'y':m.Tag(3,64),'z':m.Tag(3,0),'owners':m.Tag(9,[m.Tag(10,{'owner':owner(uid),'exact':m.Tag(11,[9,8,7])}) for uid in (gone,stay)],10),'payload':m.Tag(10,{'functional':m.Tag(11,[4,5,6])})})],10)
        for prefix in ('','stage1_'):
            db.execute('INSERT INTO '+prefix+'placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',*at,0,0,'composition',42,m.canonical(m.AIR),0));db.execute('INSERT INTO '+prefix+'bridge_cells VALUES(?,?,?,?,?,?,?)',('.',*at,m.canonical({'registryId':'bloodborne_dw:composite_cell','properties':{}}),0,42));db.execute('INSERT INTO '+prefix+'cleanup_owners VALUES(?,?,?)',('.',m.canonical(gone),'static'))
        changes,entities=m.context_stage._content(m,db,'.',0,0,m.compound(source.root),'stage1_');actual=m.decode_nbt(m.encode_nbt(source));m.rewrite_states(m.compound(actual.root),changes);m.compound(actual.root)['block_entities']=m.Tag(9,entities,10)
        return db,source,actual
    def test_context_exact_prior_bridge_admission_and_identical_skip(self):
        db,source,actual=self.context_content_fixture();before=m.encode_nbt(source)
        m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root));self.assertEqual(m.encode_nbt(source),before)
        with patch.object(m.context_stage,'_original_chunk',return_value=m.compound(source.root)):
            result=m.context_stage.transformed_chunk(m,db,'.',0,0,2,m.zlib.compress(m.encode_nbt(actual)))
        self.assertIsNone(result);self.assertEqual(db.execute('SELECT count(*) FROM context_changed_chunks').fetchone()[0],0);self.assertEqual(db.execute('SELECT count(*) FROM relight').fetchone()[0],0)
        m.compound(m.compound(actual.root)['block_entities'].value[0])['NativeGridBase']=m.Tag(3,43)
        with self.assertRaisesRegex(RuntimeError,'BE payload/owners/NativeGridBase'):m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root))
    def test_context_old_bridge_last_owner_becomes_new_plain_cell(self):
        db,source,actual=self.context_content_fixture();db.execute('DELETE FROM bridge_cells');db.execute('INSERT INTO cleanup_owners VALUES(?,?,?)',('.',m.canonical([5,6,7,8]),'static'));db.execute('UPDATE placements SET type_index=59')
        with patch.object(m.context_stage,'_original_chunk',return_value=m.compound(source.root)):
            result=m.context_stage.transformed_chunk(m,db,'.',0,0,2,m.zlib.compress(m.encode_nbt(actual)))
        root=m.compound(result.root);self.assertEqual(root['block_entities'].value,[]);self.assertEqual(m.source_state(m.context_stage._sections(m,root),(0,64,0)),m.context_stage.grid_state(59));self.assertEqual(db.execute('SELECT count(*) FROM relight').fetchone()[0],9)
    def test_context_static_waterlogged_default_is_comparison_only(self):
        db,source,actual=self.context_content_fixture();at=(1,64,0)
        original={'registryId':'bloodborne_dw:static_architecture_0','properties':{'part':'124'}}
        saved={'registryId':original['registryId'],'properties':{'part':'124','waterlogged':'false'}}
        m.rewrite_states(m.compound(source.root),{at:original});m.rewrite_states(m.compound(actual.root),{at:saved})
        source_bytes=m.encode_nbt(source);actual_bytes=m.encode_nbt(actual)
        m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root))
        self.assertEqual(m.encode_nbt(source),source_bytes);self.assertEqual(m.encode_nbt(actual),actual_bytes)
        for wrong in (dict(saved,properties={'part':'124','waterlogged':'true'}),dict(saved,properties={'part':'123','waterlogged':'false'}),dict(saved,properties={'part':'124','waterlogged':'false','unknown':'false'}),dict(saved,registryId='bloodborne_dw:static_architecture_999999')):
            m.rewrite_states(m.compound(actual.root),{at:wrong})
            with self.assertRaisesRegex(RuntimeError,'exact archived stage placements'):m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root))
        self.assertEqual(m.context_stage._comparison_state(m,{'registryId':'other:static_architecture_0','properties':{'part':'124'}}),{'registryId':'other:static_architecture_0','properties':{'part':'124'}})
    def test_context_unknown_native_cell_and_typed_payload_are_not_admitted(self):
        db,source,actual=self.context_content_fixture();m.rewrite_states(m.compound(actual.root),{(1,64,0):m.context_stage.grid_state(42)})
        with self.assertRaisesRegex(RuntimeError,'exact archived stage placements'):m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root))
        m.rewrite_states(m.compound(actual.root),{(1,64,0):m.AIR});be=m.compound(m.compound(actual.root)['block_entities'].value[0]);be['payload']=m.Tag(10,{'functional':m.Tag(12,[4,5,6])})
        with self.assertRaisesRegex(RuntimeError,'BE payload/owners/NativeGridBase'):m.context_stage._verify_prior_chunk(m,db,'.',0,0,m.compound(actual.root),m.compound(source.root))
    def context_light_fixture(self):
        db=self.database();db.execute('CREATE TABLE context_changed_chunks(dim TEXT,cx INTEGER,cz INTEGER,PRIMARY KEY(dim,cx,cz))');db.execute("INSERT INTO context_changed_chunks VALUES('.',60,4)")
        plan=self.path/'plan';plan.mkdir();history=self.path/'history';history.mkdir();old=self.path/'old.jar';new=self.path/'new.jar'
        for path,count in ((old,1),(new,2)):
            with m.zipfile.ZipFile(path,'w') as archive:
                archive.writestr(m.CATALOG_REL,m.canonical({'cells':[{}]*count}));archive.writestr('assets/bloodborne_dw/native_grid/meshes.bin',b'same old prefix plus append'[:count+10]);archive.writestr('native/Light.class',b'exact bytecode')
        saved=['minecraft:overworld\t%d\t%d'%(x,z) for x,z in ((-8,3),(-8,4),(-8,5),(-7,3),(-7,4),(-7,5),(-6,3),(-6,4))];oldqueue=('# oldqueue\n'+'\n'.join(saved+['minecraft:overworld\t-6\t5'])+'\n').encode();oldhash=m.hashlib.sha256(oldqueue).hexdigest();cursor=('# '+oldhash+' '+m.sha(old)+'\n'+'\n'.join(saved)+'\n').encode();(history/'relight-engine.json.done.tsv').write_bytes(cursor);(plan/'relight-engine.json.done.tsv').write_bytes(cursor)
        newqueue=('# queue\n'+'\n'.join(saved+['minecraft:overworld\t-6\t5','minecraft:overworld\t60\t4'])+'\n').encode();(plan/'relight-queue.tsv').write_bytes(newqueue)
        m.meta(db,'runtime',{'path':str(new),'sha256':m.sha(new)});m.meta(db,'catalog',{'path':'fixture','sha256':'newcatalog'});m.meta(db,'target',str(self.path/'world'))
        extension={'history':str(history),'parentRuntime':{'path':str(old),'sha256':m.sha(old)},'nativeSavedLight':{'savedRows':saved,'cursorSha256':m.hashlib.sha256(cursor).hexdigest(),'nativeSavedEvidence':{'proof':'the original flushed engine checkpoint'},'dependencyRadius':11}}
        return db,plan,extension,cursor,newqueue
    def test_context_saved_light_prefix_preserves_rows_and_resume_progress(self):
        db,plan,extension,cursor,queue=self.context_light_fixture()
        with patch.object(m.context_stage,'receipt',return_value=extension),patch.object(m.context_stage,'catalog_prefix'):
            m.context_stage.adopt_light(m,db,plan);adopted=(plan/'relight-engine.json.done.tsv').read_bytes();self.assertEqual(adopted.split(b'\n',1)[1],cursor.split(b'\n',1)[1])
            self.assertTrue(adopted.startswith(('# '+m.hashlib.sha256(queue).hexdigest()+' '+m.meta(db,'runtime')['sha256']+'\n').encode()))
            (plan/'relight-engine.json.done.tsv').write_bytes(adopted+b'minecraft:overworld\t-6\t5\n');m.context_stage.adopt_light(m,db,plan);self.assertEqual(len((plan/'relight-engine.json.done.tsv').read_bytes().splitlines())-1,9)
            db.execute("INSERT INTO context_changed_chunks VALUES('.',-6,5)")
            with self.assertRaisesRegex(RuntimeError,'invalidates a saved light dependency'):m.context_stage.adopt_light(m,db,plan)
    def test_context_light_exemption_is_only_proven_prefix_and_dependency_bound(self):
        db,plan,extension,cursor,queue=self.context_light_fixture()
        with patch.object(m.context_stage,'receipt',return_value=extension),patch.object(m.context_stage,'_binding',return_value={'predictedChangedChunks':[['.',60,4]]}):
            self.assertFalse(m.context_stage.light_to_invalidate(m,db,'.',-7,4));self.assertTrue(m.context_stage.light_to_invalidate(m,db,'.',-6,5));self.assertTrue(m.context_stage.light_to_invalidate(m,db,'.',60,4))
            db.execute("INSERT INTO context_changed_chunks VALUES('.',4,4)")
            with self.assertRaisesRegex(RuntimeError,'committed edit'):m.context_stage.light_to_invalidate(m,db,'.',-7,4)
    def test_context_light_proof_rejects_unrelated_runtime_or_old_cell_model_change(self):
        db,plan,extension,cursor,queue=self.context_light_fixture();old=Path(extension['parentRuntime']['path']);new=Path(m.meta(db,'runtime')['path'])
        proof=m.context_stage._light_code_proof(m,old,new);self.assertTrue(proof['allProductionCodeAndOtherResourcesByteIdentical'])
        with m.zipfile.ZipFile(new,'a') as archive:archive.writestr('native/NewLight.class',b'unapproved new runtime')
        with self.assertRaisesRegex(RuntimeError,'production entry'):m.context_stage._light_code_proof(m,old,new)
    def test_context_light_invalidation_keeps_saved_chunk_bytes_and_clears_pending(self):
        db=self.database();plan=self.path/'plan';plan.mkdir();world=self.path/'world';region=world/'region'/'r.-1.0.mca';region.parent.mkdir(parents=True)
        rf=RegionFile()
        for cx,cz in ((-7,4),(-6,5)):
            chunk=self.chunk(cx);root=m.compound(chunk.root);root['zPos']=m.Tag(3,cz);root['isLightOn']=m.Tag(1,1);root['sections']=m.Tag(9,[m.Tag(10,{'Y':m.Tag(1,4),'SkyLight':m.Tag(7,bytes([255])*2048)})],10);rf.set_chunk(cx&31,cz&31,chunk,timestamp=12);db.execute('INSERT INTO relight(dim,cx,cz,reason) VALUES(?,?,?,?)',('.',cx,cz,'olddebt'))
        region.write_bytes(rf.to_bytes());saved=rf.get_chunk(25,4).compressed_payload;m.meta(db,'contextExtension',{'fixture':True})
        with patch.object(m.context_stage,'light_to_invalidate',side_effect=lambda api,connection,dim,cx,cz:cx!=-7):m.invalidate_debt(db,plan,world)
        after=RegionFile.open(region);self.assertEqual(after.get_chunk(25,4).compressed_payload,saved);self.assertIn('isLightOn',m.compound(after.get_chunk(25,4).nbt().root));self.assertNotIn('isLightOn',m.compound(after.get_chunk(26,5).nbt().root));self.assertEqual(db.execute('SELECT count(*) FROM relight').fetchone()[0],2)
    def test_context_native_function_query_uses_expression_index(self):
        db=self.database();explain=[row[3] for row in db.execute('EXPLAIN QUERY PLAN SELECT DISTINCT x,y,z FROM fragments WHERE dim=? AND x>>4=? AND z>>4=?',('.',57,4))]
        self.assertTrue(any('fragment_native_chunk' in row and '<expr>=?' in row for row in explain),explain)
    def test_context_final_actual_input_receipt_is_mandatory_and_exact(self):
        db=self.database();plan=self.path/'plan';(plan/'context-extension').mkdir(parents=True);raw=plan/'compositions.jsonl';raw.write_text('exact raw\n');states=plan/'native-states-needed.json';m.atomic_json(states,{'nativeStates':[m.AIR]});fragments=self.path/'fragments.bin';fragments.write_bytes(b'exact physical rows');function=self.path/'functions.json';m.atomic_json(function,{'status':'PASS'})
        binding={'path':str(fragments),'sha256':m.sha(fragments)};function_binding={'path':str(function),'sha256':m.sha(function),'states':1};generation={'compositionsSha256':m.sha(raw),'sourceFragments':binding}
        for k,v in {'binding':{'immutable':'source'},'compositionGeneration':generation,'poseFragments':binding,'contextNativeFunctionGate':function_binding}.items():m.meta(db,k,v)
        report={'status':'PASS','kind':'ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE','sourceBinding':{'immutable':'source'},'compositionGeneration':generation,'compositionsSha256':m.sha(raw),'nativeStatesNeededSha256':m.sha(states),'nativeFunctionGate':function_binding,'fragmentBinding':binding,**{k:[] for k in ('missingFunctionDecisions','nonPassiveBaseStates','missingNativeExports','invalidPhysicalNativeRows','missingActivatedSourcePoseFootprints')}};path=plan/'context-extension/final-state-coverage.json';m.atomic_json(path,report)
        accepted=m.context_stage._final_input_gate(m,db,plan);self.assertEqual(accepted['sha256'],m.sha(path));fragments.write_bytes(b'changed rows')
        with self.assertRaisesRegex(RuntimeError,'fragmentBinding'):m.context_stage._final_input_gate(m,db,plan,accepted)
        fragments.write_bytes(b'exact physical rows');m.atomic_json(path,{**report,'status':'BLOCKED_FINITE_INPUTS'})
        with self.assertRaisesRegex(RuntimeError,'receipt changed'):m.context_stage._final_input_gate(m,db,plan,accepted)
    def test_contained_route_patch_recovers_swap_before_sqlite_commit(self):
        spec=importlib.util.spec_from_file_location('contained_patch_fixture',Path(__file__).with_name('patch_contained_native_route.py'));tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
        db=self.database();plan=self.path/'plan';plan.mkdir();state={'registryId':'bloodborne_dw:source_assembly_sandstone_wall','properties':{'compact':'true','rotation':'0','variant':'0'}};body={'baseState':state,'fragments':[],'substrateFragments':[{'pose':'foreign','cell':[0,1,0]}],'baseVisualMode':'substrateFragments'};key='cell_'+m.digest(body)
        db.execute('INSERT INTO compositions VALUES(?,?,NULL)',(key,m.canonical(body)));db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,?,?,?)',('.',0,64,0,0,0,key,None,m.canonical(state),0));raw=plan/'compositions.jsonl';raw.write_text(m.canonical({'key':key,**body})+'\n',encoding='utf-8');generation={'compositionsSha256':m.sha(raw),'sourceFragments':{'sha256':'historical'}}
        fragments=self.path/'fragments.sqlite';export=sqlite3.connect(fragments);export.executescript('CREATE TABLE native(state TEXT PRIMARY KEY,data TEXT);CREATE TABLE native_fragments(state TEXT,x INTEGER,y INTEGER,z INTEGER)');native={'nativeVisualComplete':True,'quads':[{'vertices':[[0,0,0],[1,0,0],[1,1,0],[0,1,0]]}]};export.execute('INSERT INTO native VALUES(?,?)',(m.canonical(state),m.canonical(native)));export.commit();export.close()
        for k,v in {'contextExtension':{'fixture':True},'binding':{'immutable':'fixture'},'compositionGeneration':generation,'poseFragments':{'path':str(fragments),'sha256':m.sha(fragments)},'contextNativeFunctionDecisions':{m.canonical(state):{'decision':'static_base'}}}.items():m.meta(db,k,v)
        db.commit();proof=self.path/'proof.json';m.atomic_json(proof,{'status':'PASS','compositionKey':key,'inputCompositionsSha256':m.sha(raw),'baseState':state,'nativeRowSha256':m.digest(native),'fragmentsSha256':m.sha(fragments),'nativeVisualComplete':True,'nativeBounds':[0,0,0,1,1,0],'fromMode':'substrateFragments','toMode':'nativePlusSubstrateFragments','retainedForeignRefs':body['substrateFragments']});args=SimpleNamespace(plan=plan,proof=proof);replace=tool.os.replace;fired=[]
        def interrupt(src,dst):
            replace(src,dst)
            if Path(dst)==raw and not fired:fired.append(True);raise RuntimeError('interrupted raw swap before SQL commit')
        connect=lambda _:sqlite3.connect(self.path/'fixture.sqlite')
        with patch.object(m,'connect',side_effect=connect):
            with patch.object(tool.os,'replace',side_effect=interrupt):
                with self.assertRaisesRegex(RuntimeError,'interrupted raw swap'):tool.patch(args,m)
            self.assertEqual(m.meta(db,'compositionGeneration'),generation);self.assertIsNotNone(db.execute('SELECT 1 FROM compositions WHERE key=?',(key,)).fetchone());tool.patch(args,m);done=tool.patch(args,m)
        newkey=done['newKey'];self.assertEqual(db.execute('SELECT composition FROM placements').fetchone()[0],newkey);self.assertEqual(m.meta(db,'compositionGeneration')['sourceFragments'],generation['sourceFragments']);self.assertEqual(done['mapWrites'],0);self.assertEqual(db.execute('SELECT source_state FROM placements').fetchone()[0],m.canonical(state));self.assertEqual(json.loads(db.execute('SELECT data FROM compositions').fetchone()[0])['substrateFragments'],body['substrateFragments'])
    def test_full_promotion_removes_only_context_and_requires_fresh_full_functions(self):
        db=self.database();evidence_file=self.path/'untouched-evidence.bin';evidence_file.write_bytes(b'preserved pilot history');db.execute('CREATE TABLE stage1_fixture(value TEXT)');db.execute("INSERT INTO stage1_fixture VALUES('preserved')")
        for key in m.context_stage.PILOT_CONTEXT_META:m.meta(db,key,{'pilot':'only'})
        source={'immutable':'source'};m.meta(db,'binding',source);m.meta(db,'classification',{'retained':'classification'});m.context_stage.leave_pilot_context(m,db)
        self.assertTrue(m.meta(db,'fullNativeFunctionGateRequired'));self.assertTrue(all(m.meta(db,key)is None for key in m.context_stage.PILOT_CONTEXT_META));self.assertEqual(m.meta(db,'binding'),source);self.assertEqual(m.meta(db,'classification'),{'retained':'classification'});self.assertEqual(db.execute('SELECT value FROM stage1_fixture').fetchone()[0],'preserved');self.assertEqual(evidence_file.read_bytes(),b'preserved pilot history')
        pilot=self.path/'pilot-functions.json';m.atomic_json(pilot,{'scope':'CONTEXT_EXTENSION','status':'PASS','sourceBinding':source});m.meta(db,'fullNativeFunctionGate',{'path':str(pilot),'sha256':m.sha(pilot)});m.meta(db,'successfulPilotGate','a separately verified root gate');db.commit()
        with self.assertRaisesRegex(RuntimeError,'pilot function report'):m.context_stage.require_full_functions(m,db)
        actual=self.path/'full-inputs.json';m.atomic_json(actual,{'scope':'FULL_CITY','sourceBinding':source,'coverage':{'candidateObjects':0},'nativeBaseCandidates':[{'state':m.AIR}],'blockEntityHostCandidates':[]});full=self.path/'full-functions.json';m.atomic_json(full,{'status':'PASS','scope':'FULL_CITY','sourceBinding':source,'inputEvidence':{'path':str(actual),'sha256':m.sha(actual)},'states':[{'state':m.AIR,'decision':'static_base','reason':'explicit actual air'}]})
        with patch.object(m,'connect',side_effect=lambda _:sqlite3.connect(self.path/'fixture.sqlite')):m.context_stage.import_full_functions(SimpleNamespace(plan=self.path,report=full),m)
        m.context_stage.require_full_functions(m,db);self.assertEqual(m.meta(db,'fullNativeFunctionGate')['states'],1);self.assertIsNone(m.meta(db,'contextNativeFunctionGate'))

if __name__=='__main__':
    import time,traceback
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(MigrationFixtures))
    m.atomic_json(m.ROOT/'research'/'migration-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[text for _,text in result.errors],'failures':[text for _,text in result.failures]})
    raise SystemExit(0 if result.wasSuccessful() else 1)
