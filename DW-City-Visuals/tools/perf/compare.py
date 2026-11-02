"""Compare bounded matching performance runs without mixing startup and steady scenes.

Input manifest: schema1, runs=[{name,variant,report,memory}], optional lock.
Each report is performance.json produced by the identical standalone QA JAR.
Variants are baseline/original/alt; repeated baselines quantify observed drift.
Paths may be absolute or relative to the manifest. Failed runs are rejected.
"""
from __future__ import annotations
import argparse, hashlib, json, math, statistics
from pathlib import Path
from cohort import digest_json

BASELINE_SHA='1f2e71c6203a9b3794d9c3cf6cf6cbe6e0a93a750584dc9df967bd2cd166e621'

def percentile(values,fraction):
    values=sorted(values)
    if not values:return None
    at=(len(values)-1)*fraction;lo=math.floor(at);hi=math.ceil(at)
    return values[lo]+(values[hi]-values[lo])*(at-lo)

def distribution(raw_ns):
    if not raw_ns:return {'samples':0}
    values=[n/1e6 for n in raw_ns];total=sum(raw_ns)
    worst=sorted(raw_ns)[-max(1,math.ceil(len(raw_ns)*.01)):]
    return {'samples':len(raw_ns),'meanFps':1e9*len(raw_ns)/total,
            'meanMs':statistics.mean(values),'medianMs':percentile(values,.5),
            'p95Ms':percentile(values,.95),'p99Ms':percentile(values,.99),'maxMs':max(values),
            'onePercentLowFps':1e9*len(worst)/sum(worst),
            **{f'over{n}ms':sum(v>n for v in values) for n in [50,100,250,1000]}}

def memory_summary(rows):
    if not rows:return {'samples':0,'status':'NO_NATIVE_PROCESS_SAMPLES_IN_THIS_WINDOW'}
    result={'samples':len(rows)}
    for field in ['workingSetBytes','privateBytes']:
        vals=[v[field] for v in rows]
        result[field]={'min':min(vals),'median':statistics.median(vals),'sampledPeak':max(vals)}
    result['pageFaultCountDelta']=rows[-1]['pageFaultCount']-rows[0]['pageFaultCount']
    result['minimumSystemAvailablePhysicalBytes']=min(v['systemAvailablePhysicalBytes'] for v in rows)
    result['minimumSystemAvailableCommitBytes']=min(v['systemAvailableCommitBytes'] for v in rows)
    return result

def change(old,new):
    return None if old==0 else 100*(new/old-1)

def require(condition,message):
    if not condition:raise AssertionError(message)

def verify_native_idle(snapshot):
    required=['pendingMeshes','pendingSectionUpdates','shapeSnapshotPendingCells','shapeSnapshotBatchDepth']
    require(isinstance(snapshot,dict) and all(k in snapshot for k in required+['pendingWholeView','idle','worldMesh','terrainUpdates','shapeSnapshots']),
            'Required common native readiness fields missing')
    for key in required:
        value=snapshot[key]
        require(type(value) in (int,float) and math.isfinite(value) and value==0,'Native work pending or counter invalid: '+key)
    require(snapshot['pendingWholeView'] is False and snapshot['idle'] is True,'Native whole-view update pending/not proven idle')
    require(snapshot['worldMesh']['pendingMeshes']==snapshot['pendingMeshes'],'World mesh snapshot/counter mismatch')
    require(snapshot['terrainUpdates']['pendingSectionUpdates']==snapshot['pendingSectionUpdates'] and snapshot['terrainUpdates']['pendingWholeView'] is False,'Terrain-update snapshot/counter mismatch')
    require(snapshot['shapeSnapshots']['shapeSnapshotPendingCells']==snapshot['shapeSnapshotPendingCells'] and snapshot['shapeSnapshots']['shapeSnapshotBatchDepth']==snapshot['shapeSnapshotBatchDepth'],'Shape snapshot/counter mismatch')

def verify_binding(spec,report,folder):
    def read(name):return json.loads((folder/name).read_text(encoding='utf-8-sig'))
    def sha(name):return hashlib.sha256((folder/name).read_bytes()).hexdigest()
    pid=read('pid.json');lock=read('lock.json');binding=read('variant-binding.json');launch=read('launch-result.json')
    require(pid['pid']==report['pid'] and pid['kind']=='perf','Report/PID binding differs')
    require(pid['variant']==spec['variant']==report['variantLabel']==binding['variant'],'Variant label/seed/manifest differ')
    require(pid['jarSha256']==report['productionSha256']==binding['productionSha256'],'Loaded production differs from launcher binding')
    require(pid['qaSha256']==report['qaSha256']==binding['qaSha256']==lock['qaSha256'],'Loaded recorder differs from lock')
    require(sha('lock.json')==pid['lockFileSha256'],'Per-run lock bytes changed')
    require(sha('variant-binding.json')==pid['variantBindingSha256'],'Variant binding bytes changed')
    logical=digest_json(lock)
    require(logical==pid['profileLockSha256']==binding['profileLockSha256'],'Per-run logical cohort identity differs')
    canonical_path=Path(pid['cohortLockPath']);canonical_bytes=canonical_path.read_bytes()
    require(hashlib.sha256(canonical_bytes).hexdigest()==pid['cohortLockFileSha256'],'Canonical cohort lock bytes changed')
    canonical=json.loads(canonical_bytes)
    require(canonical['profileLockSha256']==digest_json(canonical['lock'])==logical,'Run differs from independently verified canonical cohort')
    require(report['planSha256']==lock['routeSha256'],'Actual QA plan differs from cohort lock')
    expectedProduction=lock['baselineProductionSha256' if spec['variant']=='baseline' else 'candidateProductionSha256']
    require(report['productionSha256']==expectedProduction,'Production does not match the cohort-pinned variant')
    expectedAlt=spec['variant']=='alt'
    require(binding['expectedAlt']==binding['modeSeed']['alt']==expectedAlt,'World mode seed does not match variant')
    if spec['variant']!='baseline':
        config=binding['clientVisualConfig']
        require(config['schema']==1 and config['alt']==expectedAlt and len(config['fingerprint'])==64,'Client preference seed is absent/incorrect')
    modeAfter=read('mode-after-run.json')
    require(sha('mode-after-run.json')==launch['modeAfterRunSha256'],'Post-run mode record bytes changed')
    require(modeAfter['variant']==spec['variant'] and modeAfter['matchesRequestedMode'] and modeAfter['alt']==expectedAlt,'Saved world mode differs from requested variant')
    require(launch['status']=='PASS' and launch['exitCode']==0 and launch['performanceStatus']=='PASS' and launch['performanceSaved'],'Runner did not finish QA and JVM cleanly')
    require(launch['normalSave'] and launch['samplerExitCode']==0 and launch['processMemoryNonempty'],'Runner did not complete save/RAM measurement')
    return logical

def load_run(spec,root):
    def path(value):
        p=Path(value);return p if p.is_absolute() else root/p
    report_path=path(spec['report']);report=json.loads(report_path.read_text(encoding='utf-8-sig'))
    require(report['status']=='PASS' and report.get('saved') is True,f"{spec['name']}: benchmark/save incomplete")
    lock_sha=verify_binding(spec,report,report_path.parent)
    require(len(report['phases'])==12,f"{spec['name']}: expected all 12 matched visits")
    if spec['variant']=='baseline':require(report['productionSha256']==BASELINE_SHA,'Wrong pre-ALT baseline')
    rows=[json.loads(line) for line in path(spec['memory']).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    require(rows and all(v['pid']==report['pid'] for v in rows),'RAM samples must belong to the actual benchmark PID')
    phase_rows=[];steady_raw=[];loading_raw=[];start=10**20;end=0
    cameras=report['plan']['cameras']
    for index,phase in enumerate(report['phases']):
        expected=cameras[index%6]
        require(phase['phase']==expected['name']+'-visit'+str(index//6+1),'Camera order/visit differs')
        actual=phase['actualCamera']
        require(phase['actualOptions']==report['actualOptions'],'Actual graphics/window settings changed within the run')
        for field in ['x','y','z']:
            require(abs(actual[field]-expected[field])<.05,'Actual position differs: '+field)
        for field in ['yaw','pitch']:
            delta=(actual[field]-expected[field]+180)%360-180
            require(abs(delta)<.1,'Actual view differs: '+field)
        require(phase['status']=='PASS' and phase['steady']['windowFocused'],'Unfocused/invalid steady visit')
        require(phase['loading']['readinessAtBoundary']['allViewChunksFull'] and phase['steady']['readinessAtEnd']['allViewChunksFull'],'Missing view chunks')
        require(phase['loading']['terrainComplete'] and phase['steady']['terrainComplete'],'Missing rendered terrain')
        require(phase['loading']['renderedAtTarget'] and phase['loading']['sceneProbeNonAir'],'Rendered populated scene not proven')
        verify_native_idle(phase['loading'].get('nativeReadinessAtBoundary'))
        verify_native_idle(phase['steady'].get('nativeReadinessAtEnd'))
        require(phase['loading']['nativeIdleStableSeconds']>=max(2,report['plan']['settleSeconds']),'Native/render readiness did not remain stable for two seconds')
        require(phase.get('pngWriteVerified') is True and phase.get('screenshotBytes',0)>0,'Screenshot callback/write not verified')
        png=report_path.parent/'profile/screenshots'/phase['screenshot']
        require(png.is_file() and png.stat().st_size==phase['screenshotBytes'],'Recorded screenshot is absent, empty or changed in size')
        with png.open('rb') as stream:require(stream.read(8)==b'\x89PNG\r\n\x1a\n','Screenshot file is not a PNG')
        require(phase['steady']['wallSeconds']>=report['plan']['steadySeconds'],'Steady window too short')
        for window in ['loading','steady']:
            raw=phase[window]['rawIntervalsNs'];require(raw and not phase[window]['overflow'],'Missing/truncated real frame intervals')
            recalculated=distribution(raw)
            for metric in ['meanFps','p95Ms','p99Ms','maxMs']:
                require(math.isclose(phase[window][metric],recalculated[metric],rel_tol=1e-9,abs_tol=1e-7),'Recorded frame statistics do not match raw intervals')
        lo,hi=phase['loadingStartEpochMillis'],phase['steadyEndEpochMillis'];start=min(start,lo);end=max(end,hi)
        visits={}
        for window in ['loading','steady']:
            lo=phase[window+'StartEpochMillis'];hi=phase[window+'EndEpochMillis']
            native=[v for v in rows if lo<=v['epochMillis']<=hi]
            visits[window]=memory_summary(native)
        steady_raw.extend(phase['steady']['rawIntervalsNs']);loading_raw.extend(phase['loading']['rawIntervalsNs'])
        phase_rows.append({'phase':phase['phase'],'loading':{k:v for k,v in phase['loading'].items() if k!='rawIntervalsNs'},
                           'steady':{k:v for k,v in phase['steady'].items() if k!='rawIntervalsNs'},'processMemory':visits})
    selected=[v for v in rows if start<=v['epochMillis']<=end]
    heap=[m['heapUsedBytes'] for m in report['memorySamples']]
    gc0=report['phases'][0]['memoryAtPhaseStart'];gc1=report['phases'][-1]['memoryAtPhaseEnd']
    return {'name':spec['name'],'variant':spec['variant'],'productionSha256':report['productionSha256'],
            'qaSha256':report['qaSha256'],'planSha256':report['planSha256'],'glRenderer':report['glRenderer'],
            'profileLockSha256':lock_sha,
            'options':report['actualOptions'],'loadedMods':report['loadedMods'],
            'heapMaxBytes':gc0['heapMaxBytes'],'report':report,'visits':phase_rows,
            'steadyAggregate':distribution(steady_raw),'loadingAggregate':distribution(loading_raw),
            'processMemoryMeasuredRoute':memory_summary(selected),'processMemoryWholeProcess':memory_summary(rows),
            'processLifetimePeakWorkingSetBytes':max(v['processLifetimePeakWorkingSetBytes'] for v in rows),
            'heapUsedSampledPeakBytes':max(heap,default=None),'gcCountDelta':gc1['gcCount']-gc0['gcCount'],
            'gcTimeMsDelta':gc1['gcTimeMs']-gc0['gcTimeMs']}

def validate_cohort(runs):
    reference=runs[0]
    mods=lambda r:[m for m in r['loadedMods'] if m['id']!='bloodborne_dw']
    for run in runs[1:]:
        for key in ['qaSha256','planSha256','glRenderer','heapMaxBytes','options']:
            require(run[key]==reference[key],f"Matched comparison differs in {key}: {run['name']}")
        if 'profileLockSha256' in reference:require(run['profileLockSha256']==reference['profileLockSha256'],'Frozen mod/config/world cohort changed')
        require(mods(run)==mods(reference),'Loaded nonproduction mod IDs/versions differ')

def compare(base,candidate):
    visits=[]
    for old,new in zip(base['visits'],candidate['visits']):
        require(old['phase']==new['phase'],'Phase mismatch')
        row={'phase':old['phase'],'steadyFpsChangePercent':change(old['steady']['meanFps'],new['steady']['meanFps']),
             'steadyP99ChangePercent':change(old['steady']['p99Ms'],new['steady']['p99Ms']),
             'loadingP99ChangePercent':change(old['loading']['p99Ms'],new['loading']['p99Ms']),
             'readyAndSettleChangePercent':change(old['loading']['readyAndSettleSeconds'],new['loading']['readyAndSettleSeconds']),
             'firstReadyChangePercent':change(old['loading']['firstReadySeconds'],new['loading']['firstReadySeconds']),
             'steadyStallsOver250msDelta':new['steady']['over250ms']-old['steady']['over250ms'],
             'loadingStallsOver250msDelta':new['loading']['over250ms']-old['loading']['over250ms'],
             'baseline':old,'candidate':new}
        visits.append(row)
    summary={'steadyFpsChangePercent':change(base['steadyAggregate']['meanFps'],candidate['steadyAggregate']['meanFps']),
             'steadyP99ChangePercent':change(base['steadyAggregate']['p99Ms'],candidate['steadyAggregate']['p99Ms']),
             'loadingP99ChangePercent':change(base['loadingAggregate']['p99Ms'],candidate['loadingAggregate']['p99Ms']),
             'gcTimeMsDelta':candidate['gcTimeMsDelta']-base['gcTimeMsDelta']}
    for field in ['workingSetBytes','privateBytes']:
        a=base['processMemoryMeasuredRoute'];b=candidate['processMemoryMeasuredRoute']
        summary[field+'SampledPeakChangePercent']=change(a[field]['sampledPeak'],b[field]['sampledPeak']) if a['samples'] and b['samples'] else None
    return {'baseline':base['name'],'candidate':candidate['name'],'summary':summary,'visits':visits}

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--manifest',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
    args=ap.parse_args();manifest=json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    require(manifest['schema']==1,'Manifest schema1 required')
    specs=manifest['runs'];require(all(s['variant'] in ['baseline','original','alt'] for s in specs),'Unknown variant')
    runs=[load_run(s,args.manifest.parent) for s in specs];validate_cohort(runs)
    baselines=[r for r in runs if r['variant']=='baseline'];require(baselines,'No completed pre-ALT baseline')
    require({'baseline','original','alt'}<={r['variant'] for r in runs},'All three compared configurations required')
    comparisons=[compare(base,candidate) for base in baselines for candidate in runs if candidate['variant']!='baseline']
    drift=[compare(baselines[0],base) for base in baselines[1:]]
    compact=[{k:v for k,v in run.items() if k not in ['report','loadedMods','visits']} for run in runs]
    result={'schema':1,'status':'MEASURED_MATCHED_COMPARISON','baselineSha256':BASELINE_SHA,
            'cohortLock':manifest.get('lock'),'runs':compact,'comparisons':comparisons,'observedBaselineDrift':drift,
            'limitations':['One bounded local route with two visits per scene per run; it does not prove every location or a global absence of regressions.',
                           'CPU-side presentation intervals include frame pacing; they are not GPU execution or display scanout timings.',
                           'Time to readiness includes a fixed settle interval and integrated server/client work, not pure disk I/O.',
                           'Process memory peaks are sampled. Java heap usage is reported separately from Working Set and Private Bytes.',
                           'Unchanged full modpack/config/world identity must be locked and hashed by the runner; loaded IDs/versions alone do not prove byte identity.',
                           'OS filesystem cache is not reset; repeated baseline reveals observed ordering drift without proving its cause.']}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({'status':result['status'],'runs':len(runs),'comparisons':[{'baseline':c['baseline'],'candidate':c['candidate'],**c['summary']} for c in comparisons],'baselineDrift':[d['summary'] for d in drift]},indent=2))

if __name__=='__main__':main()
