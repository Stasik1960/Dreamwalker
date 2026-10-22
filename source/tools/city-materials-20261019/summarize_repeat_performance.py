"""Keep the failed first observation and the targeted repeat, without selection."""
import json
import statistics
from pathlib import Path
from compare import render_work

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))

def main():
    comparison=read(OUT/'comparison.json');cap=read(OUT/'reopen-final2/client-capture-output.json')
    launch=read(OUT/'reopen-final2/launch.json')
    assert cap['captureComplete'] and launch['normalSaveMessages'] and launch['exitCode']==0
    frame=cap['frames'][0];reference=comparison['runs']['streaming.5']['frames'][0]
    assert cap['productionJarSha256']==comparison['runs']['materials.1']['jarSha256']
    assert frame['actualCamera']==reference['camera']
    for key,value in reference['settings'].items():assert frame[key]==value,key
    assert frame['requiredClientChunkEvidence']['delivered']==289 and frame['terrainRenderComplete']
    samples=[json.loads(line) for line in (OUT/'reopen-final2/memory.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    window=[m for m in samples if m['withinMeasuredPresentationWindow']]
    assert len(window)>=3 and frame['frameTimes']['measurements']>100
    result={'status':'OBSERVED_TARGETED_REPEAT_AFTER_FPS_ANOMALY','jarSha256':cap['productionJarSha256'],
        'baselineFps':reference['frames']['fpsAverage'],
        'firstCandidateFps':comparison['runs']['materials.1']['frames'][0]['frames']['fpsAverage'],
        'repeatFps':frame['frameTimes']['fpsAverage'],'repeatP95FrameMs':frame['frameTimes']['p95Ms'],
        'repeatWindowRssMedianBytes':statistics.median(m['workingSetBytes'] for m in window),
        'repeatWindowCommitMaxBytes':max(m['privateCommitBytes'] for m in window),
        'residentChunks':frame['loadedChunks'],'requiredDelivered':289,'renderWorkDuringWindow':render_work(frame),
        'firstSceneObservedAfterMemorySamplerStartSeconds':next(m['elapsedSeconds'] for m in samples if m.get('captureFrame')),
        'limitations':['Same final JAR/settings and one saved-world reopen; the world was not reset after the final comparison.',
            'Enhanced triangle diagnostics occur after the frame window; this is a targeted anomaly check, not a replacement for the matched pair.',
            'No first observation was discarded; the main comparison retains all three lower FPS values.']}
    host=OUT/'reopen-final2/host-state-at-fps.json'
    if host.exists():result['hostSnapshot']=read(host)
    (OUT/'repeat-performance.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='hostSnapshot'}))

if __name__=='__main__':main()
