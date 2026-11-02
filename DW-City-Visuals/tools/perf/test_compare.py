"""Synthetic arithmetic/invalid-cohort checks, not game evidence."""
import hashlib,json,tempfile,unittest
from pathlib import Path
from compare import distribution, memory_summary, validate_cohort, change, verify_binding, verify_native_idle, BASELINE_SHA
from cohort import seal_cohort

class RecorderAnalysisTests(unittest.TestCase):
    def test_interval_arithmetic(self):
        result=distribution([10_000_000,20_000_000,60_000_000,300_000_000,1_200_000_000])
        self.assertAlmostEqual(result['meanFps'],5e9/1_590_000_000)
        self.assertEqual(result['medianMs'],60)
        self.assertEqual([result[k] for k in ['over50ms','over100ms','over250ms','over1000ms']],[3,2,2,1])
        self.assertEqual(result['maxMs'],1200)
        self.assertAlmostEqual(result['onePercentLowFps'],1/1.2)
    def test_ram_measures_are_separate(self):
        rows=[dict(workingSetBytes=10,privateBytes=50,pageFaultCount=4,systemAvailablePhysicalBytes=100,systemAvailableCommitBytes=200),
              dict(workingSetBytes=15,privateBytes=40,pageFaultCount=9,systemAvailablePhysicalBytes=90,systemAvailableCommitBytes=190)]
        result=memory_summary(rows)
        self.assertEqual(result['workingSetBytes']['sampledPeak'],15)
        self.assertEqual(result['privateBytes']['sampledPeak'],50)
        self.assertEqual(result['pageFaultCountDelta'],5)
        self.assertEqual(memory_summary([])['samples'],0)
    def test_mixed_recorder_or_settings_rejected(self):
        a=dict(name='a',qaSha256='same',planSha256='plan',glRenderer='gpu',heapMaxBytes=100,options={'maxFps':260},loadedMods=[])
        with self.assertRaises(AssertionError):validate_cohort([a,dict(a,name='b',qaSha256='different')])
        with self.assertRaises(AssertionError):validate_cohort([a,dict(a,name='b',options={'maxFps':30})])
    def test_zero_baseline_no_invented_percent(self):
        self.assertIsNone(change(0,5))
        self.assertAlmostEqual(change(100,90),-10)
    def test_native_idle_requires_all_real_counters(self):
        snapshot={'pendingMeshes':0,'pendingSectionUpdates':0,'pendingWholeView':False,
                  'shapeSnapshotPendingCells':0,'shapeSnapshotBatchDepth':0,'idle':True,
                  'worldMesh':{'pendingMeshes':0},'terrainUpdates':{'pendingSectionUpdates':0,'pendingWholeView':False},
                  'shapeSnapshots':{'shapeSnapshotPendingCells':0,'shapeSnapshotBatchDepth':0}}
        verify_native_idle(snapshot)
        for key in ['pendingMeshes','pendingSectionUpdates','shapeSnapshotPendingCells','shapeSnapshotBatchDepth']:
            with self.assertRaises(AssertionError):verify_native_idle(dict(snapshot,**{key:1}))
        with self.assertRaises(AssertionError):verify_native_idle(dict(snapshot,pendingWholeView=True))
        with self.assertRaises(AssertionError):verify_native_idle({'idle':True})
        with self.assertRaises(AssertionError):verify_native_idle(None)
    def test_cohort_is_immutable_and_changes_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'cohort.json';lock={'mods':['one'],'world':'fixed'}
            sha=seal_cohort(path,lock,'baseline-A0');original=path.read_bytes()
            self.assertEqual(seal_cohort(path,lock,'baseline-A1'),sha)
            self.assertEqual(path.read_bytes(),original)
            with self.assertRaises(AssertionError):seal_cohort(path,dict(lock,world='changed'),'new-alt')
            self.assertEqual(path.read_bytes(),original)
    def test_binding_rechecks_files_and_variants(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp);qa='1'*64;plan='2'*64
            lock={'qaSha256':qa,'routeSha256':plan,'baselineProductionSha256':BASELINE_SHA,'candidateProductionSha256':'3'*64}
            canonical=folder/'cohort-lock.json';logical=seal_cohort(canonical,lock,'baseline-A0')
            def write(name,value):(folder/name).write_text(json.dumps(value,indent=2))
            def sha(name):return hashlib.sha256((folder/name).read_bytes()).hexdigest()
            write('lock.json',lock)
            binding={'variant':'baseline','productionSha256':BASELINE_SHA,'qaSha256':qa,'profileLockSha256':logical,
                     'expectedAlt':False,'modeSeed':{'alt':False},'clientVisualConfig':None}
            write('variant-binding.json',binding)
            write('mode-after-run.json',{'variant':'baseline','matchesRequestedMode':True,'alt':False})
            write('launch-result.json',{'status':'PASS','exitCode':0,'performanceStatus':'PASS','performanceSaved':True,'normalSave':True,'samplerExitCode':0,'processMemoryNonempty':True,'modeAfterRunSha256':sha('mode-after-run.json')})
            pid={'pid':100,'kind':'perf','variant':'baseline','jarSha256':BASELINE_SHA,'qaSha256':qa,
                 'profileLockSha256':logical,'lockFileSha256':sha('lock.json'),'variantBindingSha256':sha('variant-binding.json'),
                 'cohortLockPath':str(canonical),'cohortLockFileSha256':sha('cohort-lock.json')}
            write('pid.json',pid)
            report={'pid':100,'variantLabel':'baseline','productionSha256':BASELINE_SHA,'qaSha256':qa,'planSha256':plan}
            self.assertEqual(verify_binding({'variant':'baseline'},report,folder),logical)
            with self.assertRaises(AssertionError):verify_binding({'variant':'alt'},report,folder)
            write('lock.json',dict(lock,unexpectedChange=True))
            with self.assertRaises(AssertionError):verify_binding({'variant':'baseline'},report,folder)

if __name__=='__main__':unittest.main()
