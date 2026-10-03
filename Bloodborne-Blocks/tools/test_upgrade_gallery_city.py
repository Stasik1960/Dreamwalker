import hashlib,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from upgrade_gallery_city import upgrade,apply_rule,translate_expected,city_files,find_candidates,select_owner_patterns,StreamingWorld
from dataclasses import replace
from convert_logical_world import World,Expected,Rule,PART,hash_tree
from city_palette import helper_bindings,audit_helpers
from test_logical_world import assembly_source,entity
from reviewed_migration_fixture import write_fixture
from world_io import compound,Tag,TAG_LIST,TAG_COMPOUND

DIM='minecraft:overworld';ORIGIN=(4,65,4)
OLD=('bloodborne_blocks:city_old',());NEW=('bloodborne_blocks:owner_new',())
CARRIER=('bloodborne_blocks:city_carrier',());GUEST=('bloodborne_blocks:owner_guest',())
SHAPE=frozenset({(0,0,0),(1,0,0),(2,0,0)})

class UpgradeGalleryTests(unittest.TestCase):
    def fixture(self,base,kind='ordinary'):
        source=base/'source';city=base/'resources/city';logical=city.parent/'logical'
        city.mkdir(parents=True);logical.mkdir()
        defs=[{'id':'city_old','city_compat':True,'states':{'':{}}},
              {'id':'owner_new','whole_owner':True,'states':{'':{}}},
              {'id':'city_carrier','city_compat':True,'states':{'':{}}},
              {'id':'owner_guest','whole_owner':True,'states':{'':{}}}]
        (city/'definitions.json').write_text(json.dumps({'blocks':defs}))
        geo={'blocks':{d['id']:{'states':{'':{'cells':{'0,0,0':{}}}}} for d in defs}}
        geo['blocks']['owner_new']['states']['']['cells']={','.join(map(str,o)):{} for o in SHAPE}
        geo['blocks']['owner_guest']['states']['']['cells']={'0,0,0':{},'-1,0,0':{},'-2,0,0':{}}
        (city/'geometry.json').write_text(json.dumps(geo))
        (logical/'definitions.json').write_text('{"blocks":[]}');(logical/'geometry.json').write_text('{"blocks":{}}')
        blocks={ORIGIN:(OLD[0],{}),(5,65,4):(OLD[0],{}),(6,65,4):(CARRIER[0],{}),(7,65,4):(GUEST[0],{})}
        entities=[]
        if kind=='foreign':blocks[(6,65,4)]=('yuushya:curtain',{})
        if kind=='foreign_nbt':entities=[entity((6,65,4),'minecraft:chest')]
        assembly_source(source,blocks,entities)
        world=World(source,{})
        if kind in ('guest','source_guest'):
            point=(5 if kind=='source_guest' else 6,65,4)
            world.add_shared_helper(DIM,point,[(GUEST[0],(7,65,4))])
        if kind=='ticks':
            chunk=world.chunks[(DIM,0,0)];chunk.root()['block_ticks']=Tag(TAG_LIST,[entity(ORIGIN,'minecraft:stone')],TAG_COMPOUND);chunk.changed=True
        world.save()
        (source/'region/r.32.0.mca').write_bytes(b'exhibit sentinel')
        citypath='region/r.0.0.mca'
        (source/'gallery-manifest.json').write_text(json.dumps({'cityContext':[{'path':citypath,'sha256':hashlib.sha256((source/citypath).read_bytes()).hexdigest()}]}))
        rule=Rule(1,Expected((0,0,0),OLD),NEW,(0,0,0),(Expected((1,0,0),OLD),),None,SHAPE,source_reference='test')
        return source,city,defs,rule

    def test_saved_assembly_scoping_and_idempotence(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base);before=hash_tree(source)
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([rule],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(1,report['counts']['converted']);self.assertEqual(before,hash_tree(source))
            self.assertEqual(b'exhibit sentinel',(base/'out/region/r.32.0.mca').read_bytes())
            view=base/'view';view.mkdir();(view/'region').mkdir();(view/'region/r.0.0.mca').write_bytes((base/'out/region/r.0.0.mca').read_bytes())
            world=World(view,{});self.assertEqual(NEW,world.get(DIM,ORIGIN))
            self.assertEqual((PART,()),world.get(DIM,(5,65,4)));self.assertEqual(CARRIER,world.get(DIM,(6,65,4)))
            self.assertEqual([],find_candidates(world,[rule]));self.assertTrue(audit_helpers(world,city)['ok'])

    def test_overlap_and_source_guest_preserve_binding(self):
        for kind in ('guest','source_guest'):
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as tmp:
                base=Path(tmp);source,city,defs,rule=self.fixture(base,kind)
                (source/'region/r.32.0.mca').unlink();world=World(source,{})
                self.assertIsNone(apply_rule(world,world.block_entities(),DIM,ORIGIN,rule,{d['id']:d for d in defs}))
                world.save();saved=World(source,{});point=(5 if kind=='source_guest' else 6,65,4)
                bindings=helper_bindings(compound(saved.block_entities()[(DIM,*point)]))
                self.assertIn(((7,65,4),GUEST[0]),bindings);self.assertIn((ORIGIN,NEW[0]),bindings)
                self.assertTrue(audit_helpers(saved,city)['ok'])

    def test_rejection_is_atomic(self):
        for kind,reason in [('foreign','foreign_block_carrier'),('foreign_nbt','foreign_block_entity'),('ticks','scheduled_ticks')]:
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as tmp:
                base=Path(tmp);source,city,defs,rule=self.fixture(base,kind)
                (source/'region/r.32.0.mca').unlink();before=hash_tree(source);world=World(source,{})
                self.assertEqual(reason,apply_rule(world,world.block_entities(),DIM,ORIGIN,rule,{d['id']:d for d in defs}))
                world.save();self.assertEqual(before,hash_tree(source))

    def test_translation_and_path_guards(self):
        piece=Expected((1,2,3),OLD,SHAPE);new=translate_expected(piece,{OLD:NEW})
        self.assertEqual(NEW,new.state);self.assertEqual(piece.shape,new.shape);self.assertEqual(piece.offset,new.offset)
        with self.assertRaises(ValueError):city_files({'cityContext':[{'path':'../region/r.0.0.mca'}]})
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            for output,report in [(source,base/'report'),(source/'nested',base/'report'),(base/'out',source/'report')]:
                with self.assertRaises(ValueError):upgrade(source,output,report,city)

    def test_ambiguous_signature_has_explicit_order_independent_choice(self):
        a=Rule(1,Expected((0,0,0),OLD),NEW,(0,0,0),(),None,SHAPE,source_reference='minecraft:a')
        b=replace(a,number=2,target=GUEST,shape=frozenset({(0,0,0)}),source_reference='minecraft:b')
        winners,choices=select_owner_patterns([a,b]);reverse,other=select_owner_patterns([b,a])
        self.assertEqual([b],winners);self.assertEqual(winners,reverse);self.assertEqual(choices,other)
        self.assertEqual(2,len(choices[b.source_reference]['alternatives']))

    def test_overlapping_complete_pattern_remains_reviewable(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            other=replace(rule,number=2,target=GUEST,shape=frozenset({(0,0,0)}),source_reference='competing')
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([rule,other],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(1,report['counts']['converted']);self.assertEqual(1,len(report['overlappingChoices']))
            self.assertEqual('incomplete_after_prior_conversion',report['overlappingChoices'][0]['reason'])
            self.assertEqual([NEW[0]],report['overlappingChoices'][0]['chosenTargets'])

    def test_blocked_atomic_closure_reserves_its_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            atomic=replace(rule,number=0,atomic_owner_group=True,allowed_origins=((DIM,ORIGIN),),
                           transaction_id='frozen-owner',preflight_error='missing_runtime_owner')
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([atomic,rule],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(0,report['counts']['converted'])
            self.assertIn('missing_runtime_owner',report['counts']['reasons'])
            self.assertIn('reserved_by_atomic_owner_group',report['counts']['reasons'])

    def test_cross_chunk_binding_survives_single_chunk_cache_eviction(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            source=base/'cross-chunk'
            write_fixture(source,{(15,65,4):(OLD[0],{}),(16,65,4):(OLD[0],{}),(17,65,4):(GUEST[0],{})})
            world=World(source,{});world.add_shared_helper(DIM,(16,65,4),[(GUEST[0],(17,65,4))]);world.save()
            world=StreamingWorld(source,{},cache_limit=1)
            self.assertIsNone(apply_rule(world,world.block_entities(),DIM,(15,65,4),rule,{d['id']:d for d in defs}))
            self.assertEqual(NEW,world.get(DIM,(15,65,4)))
            self.assertEqual(GUEST,world.get(DIM,(17,65,4)))
            world.save();saved=StreamingWorld(source,{},cache_limit=1)
            self.assertEqual(NEW,saved.get(DIM,(15,65,4)))
            bindings=helper_bindings(compound(saved.block_entities()[(DIM,16,65,4)]))
            self.assertEqual([((15,65,4),NEW[0]),((17,65,4),GUEST[0])],bindings)
            self.assertTrue(audit_helpers(saved,city)['ok'])

    def test_reserved_nonroot_carrier_allows_import_overlap_without_state_loss(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            (source/'region/r.32.0.mca').unlink();world=World(source,{})
            reserved={(DIM,(6,65,4)):'protected-other-owner'}
            self.assertIsNone(apply_rule(world,world.block_entities(),DIM,ORIGIN,rule,{d['id']:d for d in defs},reserved=reserved))
            self.assertEqual(CARRIER,world.get(DIM,(6,65,4)))
            world.save();saved=World(source,{})
            self.assertEqual(CARRIER,saved.get(DIM,(6,65,4)));self.assertTrue(audit_helpers(saved,city)['ok'])

    def test_logical_upper_anchor_uses_compiled_root_and_shape(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            (source/'region/r.32.0.mca').unlink();world=World(source,{})
            target=('bloodborne_blocks:o_logical',(('root_anchor','upper'),))
            defs.append({'id':'o_logical','logical':True,'states':{'root_anchor=upper':{}}})
            logical_rule=replace(rule,target=target)
            self.assertIsNone(apply_rule(world,world.block_entities(),DIM,ORIGIN,logical_rule,{d['id']:d for d in defs},geometry={'blocks':{}}))
            self.assertEqual(target,world.get(DIM,ORIGIN))

    def test_wrong_weighted_variant_does_not_create_dispute(self):
        from source_variant_rng import weighted_index
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            selected=weighted_index([1,1],ORIGIN)
            guard={'offset':[0,0,0],'weights':[1,1],'indices':[selected],'multipart':False}
            valid=replace(rule,variant_guards=(guard,))
            invalid=replace(rule,number=2,target=GUEST,variant_guards=({**guard,'indices':[1-selected]},))
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([valid,invalid],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(1,report['counts']['converted']);self.assertEqual([],report['overlappingChoices'])

    def test_successful_atomic_closure_does_not_create_ordinary_dispute(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            atomic=replace(rule,number=0,atomic_owner_group=True,allowed_origins=((DIM,ORIGIN),),transaction_id='proven')
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([atomic,rule],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(1,report['counts']['converted']);self.assertEqual([],report['overlappingChoices'])

    def test_later_conversion_can_unlock_an_earlier_storage_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp);source,city,defs,rule=self.fixture(base)
            later=('bloodborne_blocks:owner_later',())
            defs.append({'id':'owner_later','whole_owner':True,'states':{'':{}}})
            (city/'definitions.json').write_text(json.dumps({'blocks':defs}))
            geo=json.loads((city/'geometry.json').read_bytes());geo['blocks']['owner_later']={'states':{'':{'cells':{'0,0,0':{}}}}}
            (city/'geometry.json').write_text(json.dumps(geo))
            blocked=replace(rule,root_offset=(2,0,0))
            freeing=Rule(2,Expected((0,0,0),CARRIER),later,(2,0,0),(),None,frozenset({(0,0,0)}),source_reference='freeing')
            with patch('upgrade_gallery_city.compile_city_rules',return_value=([blocked,freeing],{})):
                report=upgrade(source,base/'out',base/'report.json',city)
            self.assertEqual(2,report['counts']['converted']);self.assertEqual([1,0],report['followUpPasses'])
            self.assertEqual([],report['rejected'])

if __name__=='__main__':unittest.main()
