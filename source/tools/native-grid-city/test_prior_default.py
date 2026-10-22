"""Only the proven native serialization admission fixture; no game/world."""
import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('prior_default_fixture_source',Path(__file__).with_name('test_migration.py'));suite=importlib.util.module_from_spec(spec);spec.loader.exec_module(suite)
result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite([suite.MigrationFixtures('test_context_static_waterlogged_default_is_comparison_only')]))
suite.m.atomic_json(suite.m.ROOT/'research/prior-default-fixtures.json',{'status':'PASS' if result.wasSuccessful() else 'FAIL','tests':result.testsRun,'errors':[v for _,v in result.errors],'failures':[v for _,v in result.failures],'mapWrites':0,'onlySyntheticMetadata':True})
raise SystemExit(0 if result.wasSuccessful() else 1)
