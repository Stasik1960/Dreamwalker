"""Recovery requires exact historical evidence and preserves unrelated cells."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from city_recovery import prepare, apply, load_plan
from convert_logical_world import World, PART, block_pos_long
from test_logical_world import assembly_source
from world_io import Tag, TAG_LIST, TAG_COMPOUND, TAG_INT

DIM='minecraft:overworld'
class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.resources=self.root/'logical';self.resources.mkdir()
        (self.root/'city').mkdir()
        (self.root/'city/geometry.json').write_text(json.dumps({'blocks':{'door':{'states':{'':{'cells':{'0,0,0':{},'0,1,0':{}}}}}}}))
        self.owner=(2,64,2);self.part=(2,65,2);self.empty=(3,64,2);self.foreign=(4,64,2)
        old=self.root/'old'
        assembly_source(old,{self.owner:('bloodborne_blocks:door',{}),self.part:(PART,{}),self.foreign:('minecraft:stone',{})})
        w=World(old,{});w.add_helper(DIM,self.part,self.owner,'bloodborne_blocks:door');w.save()
        self.archive=self.root/'reference.zip'
        with zipfile.ZipFile(self.archive,'w') as z:
            for p in old.glob('region/*.mca'):z.write(p,'city-recovery-reference/region/'+p.name)
        self.plan=self.root/'plan.json'
        self.plan.write_text(json.dumps({'referenceSha256':hashlib.sha256(self.archive.read_bytes()).hexdigest(),'cells':[
            {'dimension':DIM,'position':self.part,'before':'bloodborne_blocks:m_missing','after':PART,'ownerPosition':self.owner,'ownerState':'bloodborne_blocks:door'},
            {'dimension':DIM,'position':self.empty,'before':'bloodborne_blocks:m_empty','after':'minecraft:air'}]}))
        source=self.root/'source'
        assembly_source(source,{self.owner:('bloodborne_blocks:door',{}),self.part:('bloodborne_blocks:m_missing',{}),self.empty:('bloodborne_blocks:m_empty',{}),self.foreign:('minecraft:stone',{})})
        self.world=World(source,{})
    def prepare(self,declared=None):
        return prepare(self.world,self.resources,declared,plan_path=self.plan,reference=self.archive)
    def test_restore_and_idempotence(self):
        r=self.prepare();self.assertEqual((1,1),(r['restoredHelpers'],r['restoredEmptyCells']))
        apply(self.world,r)
        self.assertEqual((PART,()),self.world.get(DIM,self.part))
        self.assertEqual(('minecraft:air',()),self.world.get(DIM,self.empty))
        self.assertEqual(('minecraft:stone',()),self.world.get(DIM,self.foreign))
        self.assertEqual([],self.prepare()['entries'])
    def test_changed_owner_rejected(self):
        self.world.set(DIM,self.owner,('minecraft:stone',()))
        with self.assertRaisesRegex(ValueError,'owner changed'):self.prepare()
    def test_foreign_entity_rejected(self):
        self.world.add_helper(DIM,self.part,self.owner,'foreign:owner')
        with self.assertRaisesRegex(ValueError,'foreign entity'):self.prepare()
    def test_scheduled_tick_rejected(self):
        tick=Tag(TAG_COMPOUND,{a:Tag(TAG_INT,v) for a,v in zip(('x','y','z'),self.part)})
        self.world.chunk(DIM,2,2).root()['block_ticks']=Tag(TAG_LIST,[tick],TAG_COMPOUND)
        with self.assertRaisesRegex(ValueError,'scheduled tick'):self.prepare()
    def test_report_tampering_rejected(self):
        r=self.prepare();r['entries'][0]['changes'][0]['after']='minecraft:air'
        with self.assertRaisesRegex(ValueError,'report does not match'):self.prepare(r)
    def test_reference_tampering_rejected(self):
        self.archive.write_bytes(self.archive.read_bytes()+b'x')
        with self.assertRaisesRegex(ValueError,'checksum'):self.prepare()
    def test_wrong_historical_replacement_rejected(self):
        data=json.loads(self.plan.read_text());data['cells'][0]['after']='minecraft:air';self.plan.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError,'differs from old map'):self.prepare()
    def test_wrong_geometry_rejected(self):
        (self.root/'city/geometry.json').write_text(json.dumps({'blocks':{'door':{'states':{'':{'cells':{'0,0,0':{}}}}}}}))
        with self.assertRaisesRegex(ValueError,'outside current owner geometry'):self.prepare()

    def test_independent_checks_and_unrelated_edit(self):
        from convert_logical_world import convert
        from check_logical_world import check
        from verify_modded_preservation import verify
        from test_logical_world import resources
        self.resources.rmdir()
        resources(self.resources)
        output=self.root/'converted';report_path=self.root/'reports/report.json'
        report=convert(self.world.root,output,resources=self.resources,report_path=report_path,report_root=self.root/'reports')
        recovery=self.prepare();result=World(output,{})
        apply(result,recovery);result.save()
        report['cityRecovery']=recovery;report_path.write_text(json.dumps(report))
        actual_prepare=prepare
        def checked(world, res, declared):
            return actual_prepare(world,res,declared,plan_path=self.plan,reference=self.archive)
        with patch('city_recovery.prepare',side_effect=checked):
            self.assertTrue(check(self.world.root,output,report_path,self.resources)['ok'])
        with patch('city_recovery.prepare_directory',side_effect=lambda source,res,declared: self.prepare(declared)):
            self.assertEqual('PASS',verify(self.world.root,output,report)['result'])
        result=World(output,{})
        result.set(DIM,self.foreign,('minecraft:diamond_block',()));result.save()
        with patch('city_recovery.prepare',side_effect=checked):
            with self.assertRaisesRegex(AssertionError,'outside ledger'):
                check(self.world.root,output,report_path,self.resources)

if __name__=='__main__':unittest.main()
