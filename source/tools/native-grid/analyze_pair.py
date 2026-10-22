"""Summarize the one pinned pilot pair; never declares a map READY."""
import argparse,csv,json,math
from pathlib import Path
from run_profiles import ROOT,write
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def distribution(values):
    values=sorted(values)
    def percentile(f):
        if not values:return None
        at=(len(values)-1)*f;lo=math.floor(at);hi=math.ceil(at)
        return values[lo]+(values[hi]-values[lo])*(at-lo)
    return {'samples':len(values),'p50Ms':percentile(.5),'p95Ms':percentile(.95),'p99Ms':percentile(.99),'maxMs':max(values,default=None),'over250ms':sum(v>250 for v in values),'over1s':sum(v>1000 for v in values),'over3s':sum(v>3000 for v in values)}
def run(folder):
    receipt=read(folder/'receipt.json');assert receipt['status']=='PASS',folder
    binding=read(folder/'binding.json');client=read(folder/'client/client-route.json');server=read(folder/'server/server-route.json')
    assert client['status']=='PASS_NATIVE_GRAPHICAL_ROUTE' and server['status']=='PASS_SAVED'
    phases={p['phase']:p for p in client['phases']}
    for p in phases.values():
        assert p['serverPositionAcknowledged'] and p['terrainRenderComplete'] and p['renderedAtTarget']
        assert all(c['full'] and c['deliveryObserved'] for c in p['requiredChunkEvidence'])
    frames={key:[] for key in phases};work={key:[] for key in phases};gaps={key:[] for key in phases}
    with (folder/'client/client-frames.csv').open(newline='',encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['phase'] in phases:frames[row['phase']].append(int(row['intervalNs'])/1e6)
    with (folder/'server/server-ticks.csv').open(newline='',encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['phase'] in phases:
                work[row['phase']].append(int(row['workNs'])/1e6)
                gaps[row['phase']].append(int(row['startIntervalNs'])/1e6)
    report={}
    for name,p in phases.items():
        report[name]={'seconds':p['durationNs']/1e9,'clientFrames':distribution(frames[name]),'serverTickWork':distribution(work[name]),'serverTickInterval':distribution(gaps[name]),'clientHeapMiB':p['heapUsed']/1024**2,'deliveredChunks':p['uniqueDeliveredChunks'],'residentChunks':p['residentChunks'],'requiredChunkEvidence':p['requiredChunkEvidence'],'movementDistance':p['nativeDistance'],'movementTicks':p['nativeMovementTicks'],'chunkWaitTicks':p['chunkWaitTicks'],'probeState':p['probeState']}
    server_samples=[v['heapUsed'] for v in server.values() if isinstance(v,dict) and 'heapUsed' in v]
    return {'binding':binding,'phases':report,'measuredSeconds':sum(p['seconds'] for p in report.values()),'clientFramesAll':distribution([v for a in frames.values() for v in a]),'serverTickWorkAll':distribution([v for a in work.values() for v in a]),'serverTickIntervalAll':distribution([v for a in gaps.values() for v in a]),'clientMaxPhaseHeapMiB':max(p['clientHeapMiB'] for p in report.values()),'serverMaxSnapshotHeapMiB':max(server_samples)/1024**2,'serverAfterUnloadGcHeapMiB':server['afterUnloadGc']['heapUsed']/1024**2,'totalDeliveredChunks':client['allUniqueDeliveredChunks']}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',type=Path,default=ROOT/'research/pilot/performance-baseline');parser.add_argument('--candidate',type=Path,default=ROOT/'research/pilot/performance-candidate');parser.add_argument('--out',type=Path,default=ROOT/'research/pilot/comparison.json');a=parser.parse_args()
    base,candidate=run(a.baseline),run(a.candidate)
    for key in ['qaSha256','profileLockSha256','routeSha256']:assert base['binding'][key]==candidate['binding'][key],key+' differs across the pair'
    assert base['phases'].keys()==candidate['phases'].keys()
    delta=100*(candidate['measuredSeconds']/base['measuredSeconds']-1)
    result={'status':'ONE_PAIR_ANALYZED_NOT_READINESS','baseline':base,'candidate':candidate,'measuredDurationChangePercent':delta,'limitations':'One local separate-server pair on16GiB PC, fixed3G client/4G server, actual scene/delivered-chunk evidence. Snapshot heap is not peak process RSS. No FPS or remote-hosting guarantee.','ready':False}
    write(a.out,result)
    print(json.dumps({'status':result['status'],'seconds':{'baseline':base['measuredSeconds'],'candidate':candidate['measuredSeconds']},'changePercent':delta,'candidateFrames':candidate['clientFramesAll'],'output':str(a.out)}))
if __name__=='__main__':main()
