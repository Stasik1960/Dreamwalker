import argparse,csv,datetime,hashlib,json,math,statistics
from pathlib import Path
PROJECT=Path(__file__).absolute().parents[2]
OUT=PROJECT.parent/'research/dedicated-streaming-20261019'

def epoch(value):return datetime.datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()
def load(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def prepare(case):
    folder=OUT/case;r=load(folder/'route.json');rows=[]
    for p in r['phases']:
        end=epoch(p['endUtc'])*1000;rows.append([end-p['durationNanos']/1e6,end,'tower' if p['point']==1 else 'brick',p['deliveries']])
    (folder/'intervals.tsv').write_text(''.join('\t'.join(map(str,row))+'\n' for row in rows))
    write(folder/'phase-provenance.json',{'source':'Direct server UTC end markers and monotonic phase duration; millisecond precision in JFR analyzer','intervals':rows})

def percentile(values,p):
    values=sorted(values);return values[min(len(values)-1,max(0,math.ceil(len(values)*p)-1))]

def summary(case):
    folder=OUT/case;r=load(folder/'route.json');analysis=load(folder/'target-analysis.json')['phaseThreads']['TRAVEL_TOTAL|ALL']
    with (folder/'ticks.csv').open() as f:rows=[row for row in csv.DictReader(f) if row['phase'].startswith(('MOVE_','SETTLE_'))]
    work=[int(row['workNanos'])/1e6 for row in rows];interval=[int(row['startIntervalNanos'])/1e6 for row in rows]
    deliveries=sum(p['deliveries'] for p in r['phases']);memory=[json.loads(line) for line in (folder/'memory.jsonl').read_text().splitlines()]
    start=epoch(r['phases'][0]['endUtc'])-r['phases'][0]['durationNanos']/1e9;end=epoch(r['routeEndUtc'])
    active=[m for m in memory if start<=m['utc']<=end]
    endgc=epoch(r['afterUnloadGc']['utc']);unloaded=min(memory,key=lambda m:abs(m['utc']-endgc))
    return {'routeSeconds':r['routeNanos']/1e9,'tickCount':len(rows),'tickWorkMs':{'p50':statistics.median(work),'p95':percentile(work,.95),'p99':percentile(work,.99),'max':max(work),'over50':sum(x>50 for x in work),'over100':sum(x>100 for x in work),'over1000':sum(x>1000 for x in work)},
            'tickStartIntervalMs':{'p99':percentile(interval,.99),'max':max(interval),'over100':sum(x>100 for x in interval),'sumIntervalsOver100Ms':sum(x for x in interval if x>100)},
            'deliveredChunks':deliveries,'endpointRequiredChunks':[p['requiredEndpointChunks'] for p in r['phases']],
            'ownerPackets':sum(p['ownerPackets'] for p in r['phases']),'ownerPayloadBytes':sum(p['ownerBytes'] for p in r['phases']),
            'weightedTemporaryAllocationBytes':analysis['weightedAllocationBytes'],'weightedTemporaryGiBPer1000Deliveries':analysis['weightedAllocationBytes']/2**30/deliveries*1000,'gcPauses':analysis['gcPauses'],
            'peakRouteWorkingSet':max(m['workingSet'] for m in active),'peakRoutePrivateBytes':max(m['privateBytes'] for m in active),
            'heapBeforeRoute':r['beforeRoute']['heapUsed'],'heapAfterUnloadGc':r['afterUnloadGc']['heapUsed'],'processAfterUnloadGc':unloaded,
            'shardsAfterUnloadGc':r['afterUnloadGc']['shards'],'streamingAfterUnloadGc':r['afterUnloadGc']['streaming'],'snapshotsAfterUnloadGc':r['afterUnloadGc']['snapshots']}

def compare():
    b,a=[load(OUT/c/'prepared.json') for c in ['baseline','after']]
    assert b['java']==a['java'] and b['qaSha256']==a['qaSha256']
    assert b['serverPropertiesSha256']==a['serverPropertiesSha256']
    assert load(OUT/'baseline/reset.json')['manifestSha256']==load(OUT/'after/reset.json')['manifestSha256']
    def mods(r):return [m for m in r['mods'] if not m['name'].startswith('dreamwalker-bb-')]
    assert mods(b)==mods(a)
    def args(r):return [s.replace('baseline','CASE').replace('/after','/CASE').replace('\\after','\\CASE') if s.startswith('-Ddw.qa.output=') else s for s in r['args']]
    assert args(b)==args(a)
    result={'status':'ONE_MATCHED_DEDICATED_PAIR','before':summary('baseline'),'after':summary('after'),
            'limitations':['Windows localhost; minimal shared mod set, not the remote full mod pack.','Real TCP chunk receipts, without a rendering client. No client frame freeze claim.','JFR allocation samples estimate cumulative temporary bytes, not live heap. Categories overlap.','OS file cache not cleared; baseline first. One pair does not establish statistical confidence.','Wire receiver in the common QA addon does not recognize remapped unload class names; its unload/resident counters are excluded. Server shard/snapshot/queue metrics establish district release.']}
    write(OUT/'comparison.json',result)
    print(json.dumps(result,indent=2))

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--prepare',choices=['baseline','after']);ap.add_argument('--compare',action='store_true');a=ap.parse_args()
    if a.prepare:prepare(a.prepare)
    if a.compare:compare()
