"""Compare locked continuous flight runs; retain raw inputs and external PID samples.

Rows use a predeclared 15% budget, with an additional no-increase stutter-count
gate. Counting-noise estimates are informational and never override these gates.
A zero baseline requires a zero candidate count. Flagged runs need investigation.
"""
import argparse, hashlib, json, math, re, statistics
from pathlib import Path

def percentile(a,p):
    a=sorted(a);i=(len(a)-1)*p;lo=int(i);hi=math.ceil(i)
    return a[lo]+(a[hi]-a[lo])*(i-lo)

def route_position(points,tick,duration):
    lengths=[math.dist([a[k] for k in 'xyz'],[b[k] for k in 'xyz']) for a,b in zip(points,points[1:]+points[:1])]
    distance=(tick%duration)*sum(lengths)/duration
    for i,length in enumerate(lengths):
        if distance<length or i==len(lengths)-1:return i,distance/length
        distance-=length
    raise ValueError('route')

def normalize_vm(s):
    return re.sub(r'(-D(?:dw\.flight\.(?:plan|output|variant|expectedSha256)|fabric\.modsFolder)=)[^,\]]+',r'\1<run-specific>',s)

def read_json(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def file_sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def load_run(path):
    # A report is located at <run>/evidence/flight.json; a run directory is also accepted.
    path=Path(path); report=path/'evidence'/'flight.json' if path.is_dir() else path
    game=report.parent.parent; run=read_json(report); recipe=read_json(game/'recipe.json'); process=read_json(game/'process.json'); exit_info=read_json(game/'exit.json')
    if exit_info['exitCode']!=0 or process['pid']!=run['pid']:raise ValueError('process PID or exit status mismatch')
    if not recipe.get('worldCopyOnly'):raise ValueError('world is not an isolated copy')
    if file_sha(game/'options.txt')!=recipe['optionsSha256']:raise ValueError('options file differs from recipe')
    if file_sha(game/'route.json')!=run['planSha256'] or recipe['planSha256']!=run['planSha256']:raise ValueError('plan differs from recipe or report')
    manifest=read_json(game/'evidence'/'world-input-manifest.json')
    world_digest=hashlib.sha256(json.dumps(manifest['files'],sort_keys=True,separators=(',',':')).encode('utf-8')).hexdigest()
    if world_digest!=manifest['sha256'] or world_digest!=recipe['sourceWorldFilesSha256']:raise ValueError('source world manifest mismatch')
    for mod in recipe['mods']:
        if file_sha(game/'mods'/mod['file'])!=mod['sha256']:raise ValueError('staged mod hash differs from recipe')
    prod=next(m for m in recipe['mods'] if m['id']=='bloodborne_dw');qa=next(m for m in recipe['mods'] if m['id']=='dw_flight_qa')
    if prod['sha256']!=run['productionSha256'] or qa['sha256']!=run['qaSha256']:raise ValueError('loaded JAR hash differs from recipe')
    rows=[json.loads(line) for line in (game/'evidence'/'process-memory.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    if not rows or any(row['pid']!=run['pid'] for row in rows):raise ValueError('memory sampler PID mismatch')
    start,end=run['flightStartEpochMillis'],run['flightEndEpochMillis']
    samples=[row for row in rows if start<=row['epochMillis']<=end]
    if len(samples)<2 or samples[0]['epochMillis']-start>2500 or end-samples[-1]['epochMillis']>2500 or any(b['epochMillis']-a['epochMillis']>2500 for a,b in zip(samples,samples[1:])):raise ValueError('memory sampler does not cover flight')
    run['_recipe']=recipe;run['_processMemory']={'samples':len(samples),'peakWorkingSetBytes':max(x['workingSetBytes'] for x in samples),'peakPrivateBytes':max(x['privateBytes'] for x in samples),'medianWorkingSetBytes':statistics.median(x['workingSetBytes'] for x in samples),'medianPrivateBytes':statistics.median(x['privateBytes'] for x in samples)}
    return run

def validate_positions(run):
    points=run['plan']['points'];duration=run['plan']['ticksPerLap'];positions=run['positions'];samples={p['serverSampleTick']:p for p in positions if p['serverSampleTick']>=0}
    ticks=sorted(samples)
    if not ticks or ticks[0]>60 or ticks[-1]<duration*2-60 or any(b-a>60 for a,b in zip(ticks,ticks[1:])):raise ValueError('server position samples do not cover both laps')
    for tick in ticks:
        segment,f=route_position(points,tick,duration);a,b=points[segment],points[(segment+1)%len(points)];p=samples[tick]
        if sum((p['server'+k.upper()]-(a[k]*(1-f)+b[k]*f))**2 for k in 'xyz')>=100:raise ValueError('server position diverged')
    return {'samples':len(ticks),'firstTick':ticks[0],'lastTick':ticks[-1]}

def metrics(run):
    f=run['frames']; raw=f['rawIntervalsNs']; elapsed=f['elapsedNs']; ticks=f['ticks']
    if len(raw)<1000 or len(raw)!=len(elapsed) or len(raw)!=len(ticks) or f['samples']!=len(raw) or any(not isinstance(x,int) or x<=0 for x in raw):raise ValueError('invalid frames')
    duration=run['plan']['ticksPerLap'];total_ticks=duration*2
    # Recording starts during END_CLIENT_TICK; at most ten catch-up ticks can
    # run before its first presentation. Raw elapsed checks still cover that time.
    if not 0<=ticks[0]<=10 or ticks[-1]!=total_ticks or any(not isinstance(t,int) or t<0 or t>total_ticks for t in ticks) or any(b<a or b-a>10 for a,b in zip(ticks,ticks[1:])):raise ValueError('incomplete or nonmonotonic frame ticks')
    if elapsed[0]!=raw[0] or any(b-a!=r for a,b,r in zip(elapsed,elapsed[1:],raw[1:])):raise ValueError('frame timeline gap')
    total=sum(raw)
    if f['totalNs']!=total or elapsed[-1]!=total or run['flightEndNanoTime']-run['flightStartNanoTime']!=total or abs(run['wallSeconds']-total/1e9)>1e-6:raise ValueError('frame timeline total mismatch')
    sec=total/1e9; ms=[x/1e6 for x in raw]
    worst=max(range(len(raw)),key=raw.__getitem__); tick=ticks[worst]
    points=run['plan']['points']; duration=run['plan']['ticksPerLap']; segment,frac=route_position(points,tick,duration)
    a,b=points[segment],points[(segment+1)%len(points)]
    pos={k:a[k]*(1-frac)+b[k]*frac for k in ('x','y','z')}
    result={'meanMs':statistics.mean(ms),'meanFps':len(raw)/sec,'p95Ms':percentile(ms,.95),'p99Ms':percentile(ms,.99),'p999Ms':percentile(ms,.999),'maxMs':max(ms),
            'worst':{'elapsedSeconds':elapsed[worst]/1e9,'tick':tick,'lap':tick//duration+1,'segment':segment,'position':pos},'seconds':sec}
    for n in (50,100,250,1000):
        count=sum(x>n for x in ms);result[f'over{n}msCount']=count;result[f'over{n}msPerMinute']=count*60/sec
    result['laps']=[]
    for lap in (0,1):
        sample=[raw[i]/1e6 for i,t in enumerate(ticks) if t//duration==lap]
        if not sample:raise ValueError('missing lap')
        lap_seconds=sum(sample)/1000
        result['laps'].append({'lap':lap+1,'samples':len(sample),'seconds':lap_seconds,'p99Ms':percentile(sample,.99),'maxMs':max(sample),**{f'over{n}msPerMinute':sum(x>n for x in sample)*60/lap_seconds for n in (50,100,250,1000)}})
    mem=run['memorySamples']
    if not mem:raise ValueError('missing Java memory samples')
    result['heapPeakBytes']=max(x['heapUsedBytes'] for x in mem)
    result['heapMedianBytes']=statistics.median(x['heapUsedBytes'] for x in mem)
    result['gcCountDelta']=mem[-1]['gcCount']-mem[0]['gcCount']
    result['gcTimeMsDelta']=mem[-1]['gcTimeMs']-mem[0]['gcTimeMs']
    result['processMemory']=run['_processMemory'];result['serverPositionCoverage']=validate_positions(run)
    return result

def compare(base,candidate):
    for r in (base,candidate):
        if r['status']!='PASS' or not r.get('saved') or not r.get('initialTerrainComplete') or not r.get('initialFullChunks') or not r.get('initialRendered') or not r.get('focusedAtStart') or not r.get('initialNativeReadiness',{}).get('idle') or r.get('correctionCount')!=0:raise ValueError('incomplete flight')
        if r['plan']['laps']!=2 or r['plan']['ticksPerLap']<2400 or r['wallSeconds']<240:raise ValueError('flight too short')
    for key in ('planSha256','qaSha256','actualOptions','java','glRenderer','glVendor','glVersion'):
        if base[key]!=candidate[key]:raise ValueError('mismatch: '+key)
    if normalize_vm(base['vmArguments'])!=normalize_vm(candidate['vmArguments']):raise ValueError('JVM arguments mismatch')
    for key in ('sourceWorld','sourceLevelSha256','sourceWorldFilesSha256','optionsSha256','mode'):
        if base['_recipe'][key]!=candidate['_recipe'][key]:raise ValueError('recipe mismatch: '+key)
    def mod_hashes(r):return sorted((m['id'],m['sha256']) for m in r['_recipe']['mods'] if m['id']!='bloodborne_dw')
    if mod_hashes(base)!=mod_hashes(candidate):raise ValueError('nonproduction mod hashes mismatch')
    def mods(r):return [(x['id'],x['version']) for x in r['loadedMods'] if x['id']!='bloodborne_dw']
    if mods(base)!=mods(candidate):raise ValueError('loaded mods mismatch')
    b,c=metrics(base),metrics(candidate); rows=[]
    def budget_row(key,bv,cv):
        within=(cv==0 if bv==0 else cv<=1.15*bv)
        return {'metric':key,'baseline':bv,'candidate':cv,'ratio':None if bv==0 else cv/bv,'baselineZero':bv==0,'within15Percent':within,'budgetRatio':1.15}
    for key in ('meanMs','p95Ms','p99Ms','p999Ms','maxMs','heapPeakBytes','heapMedianBytes'):
        rows.append(budget_row(key,b[key],c[key]))
    for key in ('peakWorkingSetBytes','medianWorkingSetBytes','peakPrivateBytes','medianPrivateBytes'):
        rows.append(budget_row(key,b['processMemory'][key],c['processMemory'][key]))
    no_stutter=True
    for n in (50,100,250,1000):
        k=f'over{n}msCount';rate=f'over{n}msPerMinute';bc,cc=b[k],c[k]
        row=budget_row(rate,b[rate],c[rate]);row.update({'baselineCount':bc,'candidateCount':cc,'noCountWorsening':cc<=bc,
            'informationalNoiseAllowanceCounts':1.96*math.sqrt(bc+cc*(b['seconds']/c['seconds'])**2)})
        rows.append(row);no_stutter &= cc<=bc
    strict=all(row['within15Percent'] for row in rows)
    status='PASS' if strict and no_stutter else 'INVESTIGATE'
    return {'status':status,'budget':'15% for each frame, memory and stutter-rate row; zero baseline requires zero candidate; noise is informational only', 'strictBudgetPass':strict,'noStutterWorsening':no_stutter,'baseline':b,'candidate':c,'rows':rows,'baselineProductionSha256':base['productionSha256'],'candidateProductionSha256':candidate['productionSha256'],'planSha256':base['planSha256'],'qaSha256':base['qaSha256']}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('baseline',type=Path);p.add_argument('candidate',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    result=compare(load_run(a.baseline),load_run(a.candidate))
    with a.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(result['status'])
if __name__=='__main__':main()

