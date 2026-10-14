"""Summarize actual six-view final measurements against the saved v12.7 runs."""
import argparse,json,statistics
from pathlib import Path
from verify_v1271_final_readiness import ROOT,read,sha,write

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--report',type=Path,default=ROOT/'build/final-city-fixes/performance-final5.json')
    a=p.parse_args()
    baseline=Path('C:/Users/vakir/.codex/worktrees/3f07/DW/audits/Dreamwalker-BB/2026-10-14-version-comparison')
    old_native={r['station']:r for r in read(baseline/'measurements-summary.json')['measurements']['fresh'] if r['version']=='v127'}
    old_sodium={r['station']:r for r in next(r for r in read(baseline/'sodium-measurements.json') if r['version']=='v127')['stations']}
    jar=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7.1-city-fixes.jar';digest=sha(jar)
    groups={}
    for renderer,run,old in [('native','native-r3',old_native),('sodium','sodium-r1',old_sodium)]:
        directory=ROOT/('build/runtime-client-comparison-fresh-final-'+run)
        report_path=directory/'v127-gallery-benchmark.json';report=read(report_path)
        launch=read(ROOT/('build/final-city-fixes/'+run+'/result.json'))
        assert launch['exitCode']==0 and launch['productionJarSha256']==digest and report['productionJarSha256']==digest
        assert report['status'].startswith('PASS_ENGINE')
        proof=report['nativeMeshLightPublication']
        assert proof['status'].startswith('PASS_ACTUAL_NATIVE') and proof['actualClientNeighbourUnload']['status'].startswith('PASS_ACTUAL_CLIENT')
        rows=[]
        for row in report['stationMeasurements']:
            if row['kind']!='WARM_STATIONARY':continue
            before=old[row['station']];actual=row['actualWorldRenderFrameIntervals'];focus=row['actualWindowFocusSamples']
            assert focus['unfocusedRenderCallbacks']==0 and focus['iconifiedRenderCallbacks']==0
            assert row['staticOwnerMeshCache']['cachedBytes']<=row['staticOwnerMeshCache']['cacheByteLimit']
            old_fps=before['fpsMedian'] if renderer=='native' else before['fps']
            old_p95=before['frameP95msMedian'] if renderer=='native' else before['p95ms']
            rows.append({'station':row['station'],'baselineV127Fps':old_fps,'finalFps':actual['averageObservedFps'],
                'changePercent':(actual['averageObservedFps']/old_fps-1)*100,'baselineV127P95ms':old_p95,
                'finalP95ms':actual['p95Milliseconds'],'actualFocusedSamples':focus['focusedRenderCallbacks'],
                'cacheBytes':row['staticOwnerMeshCache']['cachedBytes'],'actualServerTickP95ms':row['actualIntegratedServerTickDurations']['p95Milliseconds']})
        assert len(rows)==6
        groups[renderer]={'baselineMeanOfSixViewsFps':statistics.mean(r['baselineV127Fps'] for r in rows),
            'finalMeanOfSixViewsFps':statistics.mean(r['finalFps'] for r in rows),'stations':rows,
            'nativeReport':str(report_path),'nativeReportSha256':sha(report_path),'cachePublicationAndUnload':proof,
            'screenshots':[{'path':str(directory/'screenshots'/r['fileName']),'sha256':r['sha256']} for r in report['frames']],
            'graphics':report['graphics']}
    assert groups['native']['finalMeanOfSixViewsFps']>=groups['native']['baselineMeanOfSixViewsFps']
    assert groups['sodium']['finalMeanOfSixViewsFps']>=groups['sodium']['baselineMeanOfSixViewsFps']
    result={'status':'PASS_FINAL_NATIVE_AND_SODIUM_AT_V127_LEVEL','productionJarSha256':digest,
        'scope':'Same controlled common city slice, six actual viewpoints; native and Sodium0.5.13/Indium1.0.36/EntityCulling1.6.6, 1280x720, Fancy, distance8/simulation5, VSyncoff, cap120, Java17, RTX3070Laptop',
        'baselineScope':'Native v12.7 medians of two saved runs; Sodium one saved run; final one run per renderer. Timing noise near the120FPS cap remains.',
        'notMeasured':'Whole converted city, every position, or the full user mod stack; comparisons do not claim an FPS guarantee beyond this fixture.',
        'groups':groups}
    write(a.report,result)
    print(json.dumps({k:v for k,v in result.items() if k!='groups'},ensure_ascii=False))
    for key,group in groups.items():print(key,group['baselineMeanOfSixViewsFps'],group['finalMeanOfSixViewsFps'])

if __name__=='__main__':main()
