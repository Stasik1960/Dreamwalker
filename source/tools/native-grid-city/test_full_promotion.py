"""Only the scoped metadata/full-function promotion fixture; no game/world."""
import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('full_promotion_fixture_source',Path(__file__).with_name('test_migration.py'));suite=importlib.util.module_from_spec(spec);spec.loader.exec_module(suite)
result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite([suite.MigrationFixtures('test_full_promotion_removes_only_context_and_requires_fresh_full_functions')]))
suite.m.atomic_json(suite.m.ROOT/'research/full-promotion-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[v for _,v in result.errors],'failures':[v for _,v in result.failures],'mapWrites':0,'onlySyntheticMetadata':True})
raise SystemExit(0 if result.wasSuccessful() else 1)
