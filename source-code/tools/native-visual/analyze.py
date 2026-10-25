"""Summarize completed routes with the existing frozen CSV reader; no JVMs."""
import argparse,csv,datetime,hashlib,json,math,sys
from pathlib import Path
P=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(P/'tools/native-grid'))
from analyze_pair import run as base_run,distribution

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def stats(values):
    assert values and all(v>0 for v in values)
    worst=sorted(values,reverse=True)[:max(1,math.ceil(len(values)*.01))]
    seconds=sum(values)/1000
    return {**distribution(values),'averageFps':1000*len(values)/sum(values),
        'onePercentLowFps':1000*len(worst)/sum(worst),
        'over50ms':sum(v>50 for v in values),'over100ms':sum(v>100 for v in values),
        'over250msPerMinute':sum(v>250 for v in values)*60/seconds}

def main():
    a=argparse.ArgumentParser();a.add_argument('--run',type=Path,required=True);a.add_argument('--out',type=Path,required=True);a.add_argument('--coverage-mask',type=Path);a=a.parse_args()
    assert not a.out.exists()
    f=a.run;verified=f/'receipt-verified.json';receipt=read(verified if verified.exists() else f/'receipt.json');b=read(f/'binding.json');c=read(f/'client/client-route.json');plan=read(Path(c['routePlan']))
    if verified.exists():assert receipt['originalReceiptSha256']==sha(f/'receipt.json')
    assert receipt['status']=='PASS' and receipt['clientExitCode']==receipt['serverExitCode']==0
    assert sha(Path(c['routePlan']))==c['routePlanSha256']==b['routeSha256']
    phases={p['phase']:p for p in c['phases']};steady={n:[] for n in phases};allframes=[]
    with (f/'client/client-frames.csv').open(newline='',encoding='utf-8') as stream:
        for row in csv.DictReader(stream):
            p=phases.get(row['phase']);end=int(row['endEpochNs']);duration=int(row['intervalNs'])
            if p:
                allframes.append(duration/1e6)
                if end-duration>=p['stationaryStartEpochNs'] and end<=p['endEpochNs']:steady[row['phase']].append(duration/1e6)
    loads={};unloads=rows=0;chunkfile=f/'client/client-chunks.csv'
    with chunkfile.open(newline='',encoding='utf-8') as stream:
        for row in csv.DictReader(stream):
            rows+=1
            if row['event']=='LOAD':loads.setdefault((int(row['cx']),int(row['cz'])),int(row['epochNs']))
            else:assert row['event']=='UNLOAD';unloads+=1
    assert rows==c['clientChunkJournal']['rows'] and len(loads)==c['allUniqueDeliveredChunks'] and sha(chunkfile)==c['clientChunkJournal']['sha256']
    result={'status':'RECORDED_METRICS_REQUIRES_SEPARATE_VISUAL_REVIEW','productionSha256':b['productionSha256'],
        'binding':b,'steadyAll':stats([v for values in steady.values() for v in values]),
        'steadyByPhase':{n:stats(v) for n,v in steady.items()},'framesIncludingMovement':stats(allframes),
        'summary':base_run(f,'receipt-verified.json' if verified.exists() else 'receipt.json'),'movement':{'blocks':sum(p['nativeDistance'] for p in phases.values()),
        'ticks':sum(p['nativeMovementTicks'] for p in phases.values()),'chunkWaitTicks':sum(p['chunkWaitTicks'] for p in phases.values())},
        'observedStartup':{'serverStartToClientPidSeconds':(f/'pids.json').stat().st_mtime-(f/'binding.json').stat().st_mtime,
        'clientPidToInitialPositionAcknowledgedSeconds':datetime.datetime.fromisoformat(c['initialPositioningUtc'].replace('Z','+00:00')).timestamp()-(f/'pids.json').stat().st_mtime},
        'limits':['One local PC runs both JVMs; results include CPU/RAM contention.',
        'Startup includes mod loader, initialization, connection and initial positioning, not map I/O alone.',
        '1% low is reciprocal mean of the worst ceil(1%N) complete stationary frame intervals.',
        'FPS limit and graphics are recorded in binding; reaching the limit hides further throughput gains.',
        'Heap snapshots are separate from process RSS; chunk delivery does not prove manual review of every surface.']}
    if verified.exists():result['effectiveReceipt']={'path':str(verified),'sha256':sha(verified),'qualifier':receipt['acceptanceQualifier'],'originalFailureRetained':True}
    city={n:v for n,v in steady.items() if n=='north-facade' or n.startswith('userpack-r')}
    if city:
        result['steadyCityWitnesses']=stats([v for values in city.values() for v in values])
        result['headlinePhases']=list(city)
        result['headlineScope']='Established nine facade/statue/tree views. Paving diagnosis and predominantly-sky distant cameras are reported separately.'
    samples=f/'host-samples/samples.csv'
    if not samples.exists():
        files=list((f/'host-samples').glob('*.csv')) if (f/'host-samples').is_dir() else []
        if len(files)==1:samples=files[0]
    if samples.exists():result['hostSamples']={'path':str(samples),'sha256':sha(samples)}
    if a.coverage_mask:
        mask=read(a.coverage_mask);target={(p['cx'],p['cz']) for p in mask['columns']};missing=sorted(target-set(loads))
        result['coverage']={'targetColumns':len(target),'observedTargetColumns':len(target)-len(missing),'missingColumns':missing,'complete':not missing}
    a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'out':str(a.out),'steady':result['steadyAll'],'full':result['framesIncludingMovement'],'coverage':result.get('coverage')}))
if __name__=='__main__':main()
