"""Only the proved dimension override scope and exact one-line transition."""
import unittest
import recover_completed_context_binding as r
class ScopeFixture(unittest.TestCase):
    def test_dimension_ledger_never_uses_overworld_repair_bytes(self):
        repair={'existingShards':{'data/bloodborne_dw_owner_shards/owners-1.0.nbt':{'beforeSha256':'old','repairedAfterSha256':'restored'}}};old={'DIM-1/data/bloodborne_dw_composite_owners.dat':'committed-nether'};pilot={'globalLedger':{'path':'data/bloodborne_dw_composite_owners.dat','beforeCleanupSha256':'cropped-overworld'}}
        self.assertEqual(r.expected_ledger('data/bloodborne_dw_composite_owners.dat','source',old,pilot,repair,'repaired-overworld'),'repaired-overworld')
        self.assertEqual(r.expected_ledger('DIM-1/data/bloodborne_dw_composite_owners.dat','source',old,pilot,repair,'repaired-overworld'),'committed-nether')
        self.assertEqual(r.expected_ledger('DIM1/data/bloodborne_dw_composite_owners.dat','immutable-end',old,pilot,repair,'repaired-overworld'),'immutable-end')
        self.assertEqual(r.expected_ledger('data/bloodborne_dw_owner_shards/owners-1.0.nbt','source',old,pilot,repair,'repaired-overworld'),'restored')
        self.assertNotEqual('changed-nether',r.expected_ledger('DIM-1/data/bloodborne_dw_composite_owners.dat','source',old,pilot,repair,'repaired-overworld'))
    def test_actual_frozen_source_transition_only_one_condition(self):
        original=r.m.ROOT/'research/native-grid-plan/context-extension/context_stage.before-dimension-fix.py';before=original.read_text(encoding='utf-8');after=r.Path(r.c.__file__).read_text(encoding='utf-8');self.assertEqual(r.m.sha(original),r.OLD_CONTEXT_SHA);self.assertEqual(after,before.replace('elif global_ledger:expected=repair_global',"elif rel=='data/bloodborne_dw_composite_owners.dat':expected=repair_global"))
if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ScopeFixture));r.m.atomic_json(r.m.ROOT/'research/dimension-recovery-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[v for _,v in result.errors],'failures':[v for _,v in result.failures],'mapWrites':0,'onlyProvedDimensionScope':True});raise SystemExit(0 if result.wasSuccessful() else 1)
