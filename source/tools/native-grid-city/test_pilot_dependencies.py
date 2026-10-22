"""Focused synthetic repair admission/staging fixtures; never game maps."""
import importlib.util,sqlite3,tempfile,unittest
from pathlib import Path
import pilot_dependencies as p
from world_io import RegionFile

class RepairFixtures(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='dependency-fixture-',dir=p.m.ROOT/'research');self.root=Path(self.tmp.name);self.db=sqlite3.connect(':memory:')
        for name in ('placements','stage1_placements'):self.db.execute('CREATE TABLE '+name+'(dim TEXT,x INTEGER,y INTEGER,z INTEGER,cx INTEGER,cz INTEGER)')
        for name in ('selected_objects','stage1_selected_objects'):self.db.execute('CREATE TABLE '+name+'(dim TEXT,uuid TEXT)')
        self.db.execute('CREATE TABLE stage1_source_chunks(dim TEXT,cx INTEGER,cz INTEGER)')
    def tearDown(self):self.db.close();self.tmp.cleanup()
    def helper(self):
        at=(256,36,-882);uid=[-1920773844,24597428,-2084089085,-1717829492];root=[254,36,-882];rid='bloodborne_dw:source_addon_chains_0';owner=p.m.Tag(10,{'owner':p.m.Tag(10,{'uuid':p.m.Tag(11,uid),'root':p.m.Tag(11,root),'id':p.m.Tag(8,rid)}),'selection':p.m.Tag(9,[p.m.Tag(9,[p.m.Tag(6,0.25)],6)],9),'collision':p.m.Tag(9,[],9),'rootData':p.m.Tag(10,{'preserved':p.m.Tag(8,'source payload')})})
        tag=p.m.Tag(10,dict(id=p.m.Tag(8,'bloodborne_dw:composite_cell'),x=p.m.Tag(3,at[0]),y=p.m.Tag(3,at[1]),z=p.m.Tag(3,at[2]),owners=p.m.Tag(9,[owner],10),payload=p.m.Tag(10,{})))
        return at,tag,{p.m.canonical(uid):{'root':root,'registryId':rid}}
    def test_payload_fastpath_is_exact_and_candidate_requires_no_cleanup_footprint(self):
        cache=('region/r.0.0.mca','bound',2,b'exact')
        self.assertTrue(p.exact_payload_equivalence(self.db,'baseline',0,0,2,b'exact',cache));self.assertFalse(p.exact_payload_equivalence(self.db,'baseline',0,0,1,b'exact',cache));self.assertFalse(p.exact_payload_equivalence(self.db,'baseline',0,0,2,b'changed',cache))
        self.assertTrue(p.exact_payload_equivalence(self.db,'candidate',0,0,2,b'exact',cache));self.db.execute("INSERT INTO stage1_source_chunks VALUES('.',0,0)");self.assertFalse(p.exact_payload_equivalence(self.db,'candidate',0,0,2,b'exact',cache))
    def test_nonempty_exact_outside_owner_loss_is_recorded_for_restoration(self):
        at,tag,allowed=self.helper();before=p.m.encode_nbt(p.m.NbtFile('',tag));result=p.prove_loss(self.db,tag,None,{'registryId':'bloodborne_dw:composite_cell','properties':{}},p.m.AIR,allowed)
        self.assertEqual(result['position'],list(at));self.assertEqual(len(result['lostOwners']),1);self.assertEqual(p.m.encode_nbt(p.m.NbtFile('',tag)),before);self.assertEqual(p.c._typed(p.m,p.untyped(result['expectedBlockEntity'])),result['expectedBlockEntity'])
    def test_loss_rejects_game_payload_grid_base_selected_and_unproved_owner(self):
        state={'registryId':'bloodborne_dw:composite_cell','properties':{}}
        for key,value in (('payload',p.m.Tag(10,{'game':p.m.Tag(8,'data')})),('NativeGridBase',p.m.Tag(3,42)),('unknown',p.m.Tag(3,1))):
            _,tag,allowed=self.helper();p.m.compound(tag)[key]=value
            with self.assertRaises(RuntimeError):p.prove_loss(self.db,tag,None,state,p.m.AIR,allowed)
        at,tag,allowed=self.helper()
        with self.assertRaises(RuntimeError):p.prove_loss(self.db,tag,None,state,p.m.AIR,{})
        self.db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?)',('.',*at,16,-56))
        with self.assertRaises(RuntimeError):p.prove_loss(self.db,tag,None,state,p.m.AIR,allowed)
    def test_empty_generated_shard_rejects_wrong_coordinate_and_owner_data(self):
        fields={'Schema':p.m.Tag(3,1),'Dictionary':p.m.Tag(1,1),'ChunkX':p.m.Tag(3,15),'ChunkZ':p.m.Tag(3,-56),'ownerTable':p.m.Tag(9,[],10)};nbt=p.m.NbtFile('',p.m.Tag(10,fields));p.empty_shard(nbt,15,-56)
        with self.assertRaises(RuntimeError):p.empty_shard(nbt,16,-56)
        fields['ownerTable']=p.m.Tag(9,[p.m.Tag(10,{})],10)
        with self.assertRaises(RuntimeError):p.empty_shard(nbt,15,-56)
    def test_empty_entity_poi_admission_rejects_records(self):
        nbt=p.m.NbtFile('',p.m.Tag(10,{'Entities':p.m.Tag(9,[p.m.Tag(10,{'id':p.m.Tag(8,'minecraft:pig')})],10)}));record=(2,p.m.zlib.compress(p.m.encode_nbt(nbt)),1)
        with self.assertRaises(RuntimeError):p.empty_layer_before(record,'entities')
        nbt=p.m.NbtFile('',p.m.Tag(10,{'Sections':p.m.Tag(10,{'0':p.m.Tag(10,{'Records':p.m.Tag(9,[p.m.Tag(10,{})],10)})})}));record=(2,p.m.zlib.compress(p.m.encode_nbt(nbt)),1)
        with self.assertRaises(RuntimeError):p.empty_layer_before(record,'poi')
    def test_exact_empty_native_carving_mask_wrapper_only(self):
        fields={'Status':p.m.Tag(8,'minecraft:initialize_light'),'sections':p.m.Tag(9,[],10),'CarvingMasks':p.m.Tag(10,{'AIR':p.m.Tag(12,[]),'LIQUID':p.m.Tag(12,[])})};nbt=p.m.NbtFile('',p.m.Tag(10,fields))
        def verify():return p.flat_before((2,p.m.zlib.compress(p.m.encode_nbt(nbt)),1))
        self.assertEqual(verify()['blockEntities'],0)
        for masks in ({'AIR':p.m.Tag(12,[1])},{'UNKNOWN':p.m.Tag(12,[])},{'AIR':p.m.Tag(11,[])}):
            fields['CarvingMasks']=p.m.Tag(10,masks)
            with self.assertRaises(RuntimeError):verify()
        fields['CarvingMasks']=p.m.Tag(10,{});fields['UpgradeData']=p.m.Tag(10,{'unexpected':p.m.Tag(3,1)})
        with self.assertRaises(RuntimeError):verify()
    def test_region_staging_preserves_untouched_payload_timestamp_and_adds_slot(self):
        rf=RegionFile();nbt=p.m.NbtFile('',p.m.Tag(10,{'xPos':p.m.Tag(3,1),'zPos':p.m.Tag(3,0)}));rf.set_chunk(1,0,nbt,timestamp=77);src=self.root/'before.mca';src.write_bytes(rf.to_bytes());before=p.read_slot(src,1,0);extra=(2,p.m.zlib.compress(p.m.encode_nbt(nbt)),88);out=self.root/'staged.mca';proof=p.stage_region(src,out,{2:extra})
        self.assertEqual(p.read_slot(out,1,0),before);self.assertEqual(p.read_slot(out,2,0),extra);self.assertEqual(proof['untouchedSlots'],1)
    def test_restore_only_known_contribution_appends_tables_and_keeps_other_semantics(self):
        import copy
        from probe_dependency_shards import semantic
        _,helper,allowed=self.helper();owner=p.m.compound(p.m.compound(helper)['owners'].value[0])['owner'];uid=next(iter(allowed));data=p.m.Tag(10,{'original':p.m.Tag(8,'unchanged')});shape=p.m.Tag(10,{'selection':p.m.Tag(9,[],9)})
        fields={'Dictionary':p.m.Tag(1,1),'Schema':p.m.Tag(3,1),'ChunkX':p.m.Tag(3,16),'ChunkZ':p.m.Tag(3,-56),'ownerTable':p.m.Tag(9,[owner],10),'dataTable':p.m.Tag(9,[data],10),'shapeTable':p.m.Tag(9,[shape],10),'cells':p.m.Tag(9,[],10),'ownerMembership':p.m.Tag(9,[],10),'unknownPreserved':p.m.Tag(11,[7,8,9])};actual=p.m.NbtFile('',p.m.Tag(10,fields));expected=copy.deepcopy(actual);p.m.compound(expected.root)['cells'].value.append(p.m.Tag(10,{'pos':p.m.Tag(11,[256,36,-882]),'refs':p.m.Tag(9,[p.m.Tag(11,[0,0,0])],11)}));prefix=p.m.encode_nbt(p.m.NbtFile('',p.m.Tag(10,copy.deepcopy({k:fields[k] for k in ('ownerTable','dataTable','shapeTable','ownerMembership','unknownPreserved')}))))
        proof=p.restore_contributions(actual,expected,{uid});self.assertEqual(proof['restoredContributions'],1);self.assertEqual(semantic(actual),semantic(expected));self.assertEqual(p.m.encode_nbt(p.m.NbtFile('',p.m.Tag(10,{k:fields[k] for k in ('ownerTable','dataTable','shapeTable','ownerMembership','unknownPreserved')}))),prefix)
    def test_changed_dependencies_force_fresh_real_light_namespace_without_old_cursor_adoption(self):
        self.db.execute('CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)');self.db.execute("CREATE TABLE relight(dim TEXT,cx INTEGER,cz INTEGER,reason TEXT,status TEXT DEFAULT 'pending',receipt TEXT,PRIMARY KEY(dim,cx,cz))");self.db.execute("INSERT INTO relight VALUES('.',-8,3,'old saved debt','pending',NULL)")
        inventory=self.root/'inventory.json';rich=self.root/'rich.json';p.m.atomic_json(inventory,{'finite':'source'});p.m.atomic_json(rich,{'finite':'repair'});receipt=self.root/'receipt.json';binding={'path':str(receipt),'sha256':None};fresh=self.root/'context-extended-relight-engine.json'
        report={'status':'PASS','kind':'PRESERVING_FINITE_PILOT_DEPENDENCY_REPAIR','sourceBinding':{'exact':'source'},'originalSelectionDomainUnchanged':427,'newOwnersRecursed':False,'inventory':{'path':str(inventory),'sha256':p.m.sha(inventory)},'repairPlan':str(rich),'repairPlanSha256':p.m.sha(rich),'lightPolicy':{'reuseSavedPrefix':False,'oldLightNowInvalid':True}};p.m.atomic_json(receipt,report);binding['sha256']=p.m.sha(receipt)
        for key,value in {'scope':{'pilot':True},'contextExtension':{'retained':'evidence'},'binding':report['sourceBinding'],'contextDependencyRepair':binding,'contextNativeLightRestart':{'repair':binding,'oldSavedRowsInvalidated':8,'freshReceipt':str(fresh),'freshDoneCursor':str(fresh)+'.done.tsv'},'target':'fixture','runtime':{'sha256':'newruntime'},'catalog':{'sha256':'samecatalog'}}.items():p.m.meta(self.db,key,value)
        old=self.root/'relight-engine.json.done.tsv';old.write_bytes(b'# real archived prefix\nold saved row\n');before=old.read_bytes();p.m.export_relight(self.db,self.root);result=p.m.read(self.root/'relight-binding.json');self.assertEqual(result['engineReceipt'],str(fresh));self.assertEqual(result['engineDoneCursor'],str(fresh)+'.done.tsv');self.assertFalse(result['savedPrefixReused']);self.assertEqual(old.read_bytes(),before);self.assertFalse(Path(str(fresh)+'.done.tsv').exists());self.assertTrue(p.c.light_to_invalidate(p.m,self.db,'.',-8,3))

if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(RepairFixtures));p.m.atomic_json(p.m.ROOT/'research/pilot-dependency-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[e for _,e in result.errors],'failures':[e for _,e in result.failures],'mapWrites':0});raise SystemExit(0 if result.wasSuccessful() else 1)
