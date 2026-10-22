import argparse,collections,csv,datetime,json,math,statistics,subprocess,traceback
from common import *
def percentile(v,p):v=sorted(v);return v[min(len(v)-1,max(0,math.ceil(len(v)*p)-1))]
def summary(case,selected=None):
    folder=OUT/case;route=json.loads((folder/'client/client-route.json').read_text());profile=json.loads((folder/'frame-profile.json').read_text());serverroute=json.loads((folder/'server/server-route.json').read_text())
    travel=[p for p in route['phases'] if p['phase']!='facade' and (selected is None or p['phase'] in selected)];start=min(p['startEpochNs'] for p in travel);end=max(p['endEpochNs'] for p in travel)
    names={p['phase'] for p in travel};statphase=travel[0]['phase'] if len(travel)==1 else 'TRAVEL'
    with (folder/'client/client-frames.csv').open() as f:frames=[r for r in csv.DictReader(f) if r['phase'] in names]
    ms=[int(r['intervalNs'])/1e6 for r in frames]
    memory=[json.loads(s) for s in (folder/'memory.jsonl').read_text().splitlines() if s.strip()];active=[m for m in memory if start/1e9<=m['utc']<=end/1e9]
    peaks={};lifetime={}
    for side in ['client','server']:
        samples=[p for m in active for p in m['processes'] if p['label']==side]
        peaks[side]={'workingSet':max(s['workingSet'] for s in samples),'privateBytes':max(s['privateBytes'] for s in samples),'pageFaultDelta':samples[-1]['pageFaultCount']-samples[0]['pageFaultCount']}
        full=[p for m in memory for p in m['processes'] if p['label']==side]
        lifetime[side]={'workingSet':max(s['processLifetimePeakWorkingSet'] for s in full),'privateBytesSampledPeak':max(s['privateBytes'] for s in full)}
    phases=[]
    with (folder/'client/client-frames.csv').open() as f:allframes=list(csv.DictReader(f))
    for phase in route['phases']:
        values=[int(r['intervalNs'])/1e6 for r in allframes if r['phase']==phase['phase']]
        phases.append({'phase':phase['phase'],'durationSeconds':phase['durationNs']/1e9,'frames':len(values),'maxFrameMs':max(values),'frameP99Ms':percentile(values,.99),'framesOver100Ms':sum(x>100 for x in values),'framesOver1000Ms':sum(x>1000 for x in values),'sumFrameIntervalsOver100Ms':sum(x for x in values if x>100),'loads':phase['loads'],'unloads':phase['unloads']})
    stalls=[s for s in profile['stallsOver100Ms'] if s['phase'] in names]
    t=[]
    ticklines=(folder/'server/server-ticks.csv').read_text().splitlines(keepends=True)
    truncatedTickTail=bool(ticklines and not ticklines[-1].endswith('\n'))
    if truncatedTickTail:ticklines=ticklines[:-1]
    for row in csv.DictReader(ticklines):
        epoch=datetime.datetime.fromisoformat(row['utc'].replace('Z','+00:00')).timestamp()
        if start/1e9<=epoch<=end/1e9:t.append(row)
    tw=[int(r['workNs'])/1e6 for r in t];ti=[int(r['startIntervalNs'])/1e6 for r in t]
    return {'selectedPhases':sorted(names),'clientStatus':route['status'],'routeSeconds':sum(p['durationNs'] for p in travel)/1e9,'frames':len(ms),'frameP95Ms':percentile(ms,.95),'frameP99Ms':percentile(ms,.99),'maxFrameMs':max(ms),
       'framesOver50Ms':sum(x>50 for x in ms),'framesOver100Ms':sum(x>100 for x in ms),'framesOver1000Ms':sum(x>1000 for x in ms),'sumFrameIntervalsOver100Ms':sum(x for x in ms if x>100),
       'stallGcOverlapMs':{side:sum(s[side+'GcOverlapMs'] for s in stalls) if side+'|'+statphase+'|ALL' in profile['phaseThreads'] else None for side in ['client','server']},
       'acceptedChunkDeliveries':sum(p['loads'] for p in travel),'chunkUnloads':sum(p['unloads'] for p in travel),'endpointRequiredFullChunks':[p['requiredFullChunks'] for p in travel],
       'peaks':peaks,'lifetimePeaks':lifetime,'perPhase':phases,'lowestSystemAvailablePhysical':min(m['systemAvailablePhysical'] for m in active),
       'maxSystemPageReadRates':{k:max(m['systemPageReadRates'].get(k,0) for m in active) for k in ['Pages Input/sec','Page Reads/sec']},
       'serverTickCount':len(t),'truncatedServerTickTailIgnored':truncatedTickTail,'serverTickWorkP99Ms':percentile(tw,.99),'serverTickWorkMaxMs':max(tw),'serverStartIntervalMaxMs':max(ti),
       'serverAfterUnloadGc':serverroute.get('afterUnloadGc'),
       'weightedAllocationBytesPer1000ChunkDeliveries':{side:profile['phaseThreads'][side+'|'+statphase+'|ALL']['weightedAllocationBytes']*1000/sum(p['loads'] for p in travel) if side+'|'+statphase+'|ALL' in profile['phaseThreads'] else None for side in ['client','server']},
       'jfr':{side:profile['phaseThreads'].get(side+'|'+statphase+'|ALL') for side in ['client','server']},'phaseEvidence':travel}
def main():
    wait_foreign()
    ap=argparse.ArgumentParser();ap.add_argument('--case',choices=['baseline','after'],required=True);a=ap.parse_args();folder=OUT/a.case
    classes=PROJECT/'build/graphical-streaming-analysis';classes.mkdir(exist_ok=True)
    compile=[str(JAVA_HOME/'bin/javac.exe'),'-J-Xmx256m','--release','17','-d',str(classes),str(PROJECT/'tools/collision-streaming-20261018/JfrShapeSummary.java'),str(TOOLS/'FrameProfile.java')]
    with (folder/'analysis-compile.log').open('w') as log:subprocess.run(compile,stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    with (folder/'analysis.log').open('w') as log:subprocess.run([str(JAVA),'-Xmx768m','-XX:ActiveProcessorCount=1','-cp',str(classes),'FrameProfile',str(folder)],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    result=summary(a.case);write(folder/'summary.json',result)
    if (OUT/'baseline/summary.json').exists() and (OUT/'after/summary.json').exists():
        b=json.loads((OUT/'baseline/prepared.json').read_text());n=json.loads((OUT/'after/prepared.json').read_text())
        for key in ['java','qaSha256','graphics','optionsSha256','serverPropertiesSha256']:assert b[key]==n[key],key
        for side in ['clientArgs','serverArgs']:
            def normalized(args):return [s.replace('baseline','CASE').replace('\\after','\\CASE').replace('/after','/CASE') if s.startswith('-Ddw.route.output=') else s for s in args]
            assert normalized(b[side])==normalized(n[side]),side
        def mods(r):return {(m['side'],m['name']):m['sha256'] for m in r['mods'] if not m['name'].startswith('dreamwalker-bb-')}
        assert mods(b)==mods(n)
        assert json.loads((OUT/'baseline/world-input.json').read_text())==json.loads((OUT/'after/world-input.json').read_text())
        br=json.loads((OUT/'baseline/client/client-route.json').read_text());nr=json.loads((OUT/'after/client/client-route.json').read_text())
        common=[p['phase'] for p in br['phases'] if p['phase']!='facade' and any(q['phase']==p['phase'] for q in nr['phases'])];assert common
        write(OUT/'comparison.json',{'status':'MATCHED_COMPLETED_NATIVE_ROUTE_PHASES','comparablePhases':common,'baselineRunStatus':b['status'],'afterRunStatus':n['status'],'before':summary('baseline',common),'after':summary('after',common),'afterFullAvailableRoute':result,'limitation':'Only completed common phases are compared. Inspect baseline client/server failure receipts for the excluded brick tail; no full-route speedup is inferred from an incomplete baseline.'})
if __name__=='__main__':
    try:main()
    except BaseException:
        OUT.mkdir(parents=True,exist_ok=True);(OUT/'analysis-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
