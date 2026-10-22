"""Compare the single explicitly requested matched native material/performance pair."""
import json
import argparse
import re
import statistics
from pathlib import Path

ROOT=Path(__file__).absolute().parents[3]
OUT=ROOT/'research/city-materials-20261019'


def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))


def errors(path):
    rows=[]
    for line in path.read_text(encoding='utf-8',errors='replace').splitlines():
        match=re.match(r'^\[[^]]+\] \[[^]]+/(WARN|ERROR)\]: (.*)$',line)
        if match:rows.append({'level':match[1],'message':match[2]})
    return rows


def family(message):
    message=re.sub(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}','<owner>',message)
    message=re.sub(r'-?\d+, -?\d+, -?\d+','<position>',message)
    message=re.sub(r'Running \d+ms or \d+ ticks behind','Running <duration>ms or <ticks> ticks behind',message)
    return message


def render_work(frame):
    start=frame['existingRenderCountersAtWindowStart'];end=frame['existingRenderCountersAtCapture']
    return {label:end[group][key]-start[group][key] for label,group,key in [
        ('ownedRootsBuilt','compositeTerrain','sectionBuiltOwnedRoots'),
        ('ownedPartsBuilt','compositeTerrain','sectionBuiltOwnedParts'),
        ('fallbackMeshesBaked','compositeMesh','bakedOwners'),
        ('batchUploads','compositeMesh','batchUploads')]}


def run(case):
    folder=OUT/case
    prep=read(folder/'prepared.json'); cap=read(folder/'client-capture-output.json'); launch=read(folder/'launch.json')
    assert cap['captureComplete'] and launch['exitCode']==0 and launch['normalSaveMessages']
    memory=[json.loads(x) for x in (folder/'memory.jsonl').read_text(encoding='utf-8-sig').splitlines() if x.strip()]
    assert len(memory)>10
    frames=[]
    for frame in cap['frames']:
        assert frame['requiredClientChunkEvidence']['delivered']==289
        assert frame['requiredClientBlockEvidence']['ready'] and frame['terrainRenderComplete']
        times=frame['frameTimes'];assert times['measurements']>100 and times['rawPresentationIntervalsNs']
        samples=[m for m in memory if m['captureFrame']==frame['key'] and m['withinMeasuredPresentationWindow']]
        assert len(samples)>=3,(case,frame['key'],len(samples))
        probes=frame.get('pixelRayProbes',[])+([frame['pixelRayProbe']] if 'pixelRayProbe' in frame else [])
        assert all(p['visibleTriangleProbe']['rendererFailures']==0 for p in probes)
        frames.append({'key':frame['key'],'camera':frame['actualCamera'],
            'settings':{k:frame[k] for k in ['dayTime','ambientOcclusion','gamma','rainGradient','thunderGradient','renderDistance','simulationDistance','glRenderer']},
            'deliveredRequiredChunks':289,'residentChunks':frame['loadedChunks'],
            'frames':{k:times[k] for k in ['measurements','averageMs','fpsAverage','medianMs','p95Ms']},
            'windowMemorySamples':len(samples),
            'windowRssMedianBytes':statistics.median(m['workingSetBytes'] for m in samples),
            'windowRssMaxBytes':max(m['workingSetBytes'] for m in samples),
            'windowCommitMaxBytes':max(m['privateCommitBytes'] for m in samples),
            'nativeHeapAndGcAtWindowEnd':frame['nativeMemory']})
        frames[-1]['renderWorkDuringWindow']=render_work(frame)
    phases=[]
    for p in cap['streamingPhases']:
        assert p['endedNanoTime']>p['startedNanoTime']
        assert p['presentation'].get('count',0)<p['presentation']['sampleLimit']
        phases.append({'key':p['phase'],'navigationSettleAnd20sWindowMs':p['durationMs'],
            'chunkDelivery':p['chunkDelivery'],
            'serverTicks':{k:v for k,v in p['serverWorldTicks'].items() if k!='rawNs'},
            'presentationsDuringPhase':{k:v for k,v in p['presentation'].items() if k!='rawNs'}})
    logs=errors(folder/'launch-console.log')
    first_scene=next(m for m in memory if m.get('captureFrame'))
    return {'jarSha256':prep['artifact_sha256'],'worldInputManifest':prep['matchedWorldReset']['manifestSha256'],
        'optionsSha256':prep['optionsSha256'],'qaAddonSha256':prep['extra_mods'][0]['sha256'],
        'command':prep['command'],'captureInput':read(folder/'client-capture-input.json'),
        'frames':frames,'phases':phases,'logWarningsAndErrors':logs,
        'peakRssFullProcessBytes':max(m['workingSetBytes'] for m in memory),
        'peakCommitFullProcessBytes':max(m['privateCommitBytes'] for m in memory),
        'firstSceneObservedAfterMemorySamplerStartSeconds':first_scene['elapsedSeconds'],
        'samplerRecordedDurationSeconds':memory[-1]['elapsedSeconds'],
        'normalSaveAndExit':True}


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--base',default='compare-base');ap.add_argument('--candidate',default='compare-final2');a=ap.parse_args()
    base=run(a.base);candidate=run(a.candidate)
    for key in ['worldInputManifest','optionsSha256','qaAddonSha256','command']:
        assert base[key]==candidate[key],key
    def normalized(marker):
        marker=dict(marker);marker.pop('productionJarSha256');marker.pop('captureCase')
        marker['frames']=[{k:v for k,v in f.items() if k!='fileName'} for f in marker['frames']]
        return marker
    assert normalized(base['captureInput'])==normalized(candidate['captureInput'])
    changes=[]
    for a,b in zip(base['frames'],candidate['frames']):
        assert a['key']==b['key'] and a['camera']==b['camera'] and a['settings']==b['settings']
        changes.append({'key':a['key'],'fpsBefore':a['frames']['fpsAverage'],'fpsAfter':b['frames']['fpsAverage'],
            'fpsChangePercent':100*(b['frames']['fpsAverage']/a['frames']['fpsAverage']-1),
            'p95FrameMsBefore':a['frames']['p95Ms'],'p95FrameMsAfter':b['frames']['p95Ms'],
            'rssMedianChangeBytes':b['windowRssMedianBytes']-a['windowRssMedianBytes'],
            'heapUsedSnapshotChangeBytes':b['nativeHeapAndGcAtWindowEnd']['heapUsedBytes']-a['nativeHeapAndGcAtWindowEnd']['heapUsedBytes']})
    before_messages={(r['level'],r['message']) for r in base['logWarningsAndErrors']}
    new_messages=[r for r in candidate['logWarningsAndErrors'] if (r['level'],r['message']) not in before_messages]
    before_families={(r['level'],family(r['message'])) for r in base['logWarningsAndErrors']}
    new_families=sorted({(r['level'],family(r['message'])) for r in new_messages}-before_families)
    result={'status':'PASS_MATCHED_PAIR_COMPLETED_REQUIRES_INTERPRETATION','runs':{'streaming.5':base,'materials.1':candidate},
        'changes':changes,'newWarningOrErrorMessages':new_messages,'newWarningOrErrorFamilies':new_families,
        'limitations':['One sequential baseline-first pair; no statistical confidence estimate.',
            'OS caches were not flushed; both cases restore byte-identical active inputs in the same one working save.',
            'RAM/commit sampled about every 2 seconds; window RAM uses the 20-second settled presentation windows.',
            'Heap is an unforced-GC snapshot and is not retained live-object size.',
            'Phase duration includes teleport loading/settling plus the same 20-second FPS window, not a repeated travel route.']}
    (OUT/'comparison.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'status':result['status'],'changes':changes,'newExactMessageCount':len(new_messages),'newFamilies':new_families}))


if __name__=='__main__':main()
