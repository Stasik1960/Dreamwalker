"""Compare matched current-v8/editor recordings against a stated relative budget."""
import argparse,json,math,statistics
from pathlib import Path

def load(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def percentile(values,p):
    values=sorted(values);return values[max(0,math.ceil(len(values)*p)-1)]
def validate(run):
    recipe=load(run/'recipe.json'); report=load(run/'evidence/performance.json');exit=load(run/'exit.json')
    if exit['exitCode']!=0 or report.get('status')!='PASS' or not report.get('saved'):raise ValueError('Incomplete run '+str(run))
    production=[m for m in recipe.get('mods',[]) if m.get('id')=='bloodborne_dw']
    if len(production)!=1 or report.get('productionSha256')!=production[0].get('sha256'):raise ValueError('Production identity mismatch')
    if not report.get('qaSha256') or not recipe.get('sourceLevelSha256') or not recipe.get('optionsSha256'):
        raise ValueError('Missing QA, source-world, or options identity')
    phases=report['phases']
    names=[p.get('phase') for p in phases]
    if not phases or len(set(names))!=len(names) or any(not n or p.get('status')!='PASS' or not p.get('pngWriteVerified') for n,p in zip(names,phases)):raise ValueError('Incomplete or duplicate phases')
    for p in phases:
        for kind in ('loading','steady'):
            f=p[kind];raw=f['rawIntervalsNs']
            if f.get('overflow') or len(raw)!=f['samples'] or not raw or any(v<=0 for v in raw):raise ValueError('Invalid frame records')
            if sum(raw)!=f['intervalTotalNs']:raise ValueError('Frame sum mismatch')
        if not isinstance(p.get('steadyStartEpochMillis'),int) or not isinstance(p.get('steadyEndEpochMillis'),int) or p['steadyEndEpochMillis']<=p['steadyStartEpochMillis']:
            raise ValueError('Invalid steady memory interval')
    return recipe,report
def lock(recipe,report):
    return {'mode':recipe['mode'],'sourceWorld':str(Path(recipe['sourceWorld']).resolve()),
            'sourceLevel':recipe['sourceLevelSha256'],'options':recipe['optionsSha256'],
            'mods':sorted((m['id'],m['sha256']) for m in recipe['mods'] if m['id']!='bloodborne_dw'),
            'qaSha256':report['qaSha256'],'planSha256':report['planSha256'],
            'route':[(p['phase'],p['x'],p['y'],p['z'],p['yaw'],p['pitch']) for p in report['phases']]}
def metrics(report,phase):
    selected=next(p for p in report['phases'] if p['phase']==phase)
    raw=selected['steady']['rawIntervalsNs'];loading=selected['loading']['rawIntervalsNs']
    memories=[m for m in report['memorySamples'] if m.get('stage')=='STEADY' and m.get('phase')==phase]
    if not memories:raise ValueError('Missing steady memory samples')
    return {'meanFrameMs':statistics.mean(raw)/1e6,'p95FrameMs':percentile(raw,.95)/1e6,
            'p99FrameMs':percentile(raw,.99)/1e6,'loadingSeconds':selected['loading']['readyAndSettleSeconds'],
            'medianHeapBytes':statistics.median(m['heapUsedBytes'] for m in memories),
            'steadyOver50msRate':sum(v>50e6 for v in raw)/(sum(raw)/1e9),
            'loadingOver250msRate':sum(v>250e6 for v in loading)/(sum(loading)/1e9)}
def process_memory(run,report,phase):
    p=next(p for p in report['phases'] if p['phase']==phase);start=p['steadyStartEpochMillis'];end=p['steadyEndEpochMillis']
    rows=[json.loads(line) for line in (run/'evidence/process-memory.jsonl').read_text().splitlines()]
    rows=[r for r in rows if start<=r['epochMillis']<=end]
    if not rows or any(not isinstance(r.get(k),int) or r[k]<0 for r in rows for k in ('workingSetBytes','privateBytes')):raise ValueError('Missing or invalid process RAM interval samples')
    return {'medianWorkingSetBytes':statistics.median(r['workingSetBytes'] for r in rows),
            'peakWorkingSetBytes':max(r['workingSetBytes'] for r in rows),
            'medianPrivateBytes':statistics.median(r['privateBytes'] for r in rows)}

def route_events(report):
    result={}
    for kind,threshold in (('steady',50e6),('loading',250e6)):
        raw=[v for p in report['phases'] for v in p[kind]['rawIntervalsNs']]
        count=sum(v>threshold for v in raw);seconds=sum(raw)/1e9
        result[kind]={'events':count,'recordedSeconds':seconds,'eventsPerSecond':count/seconds}
    return result
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--baseline',type=Path,action='append',required=True)
    ap.add_argument('--candidate',type=Path,required=True);ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--budget',type=float,default=.15);a=ap.parse_args()
    if not 0<a.budget<=1:raise ValueError('Invalid comparison budget')
    if not a.baseline:raise ValueError('At least one baseline is required')
    baselines=[(p,*validate(p)) for p in a.baseline];recipe,report=validate(a.candidate)
    for _,r,b in baselines:
        if lock(r,b)!=lock(recipe,report):raise ValueError('Mixed profile, mod set, camera route or source world')
    rows=[];failures=[]
    baseline_phases={p['phase'] for p in baselines[0][2]['phases']}
    if any({p['phase'] for p in b['phases']}!=baseline_phases for _,_,b in baselines) or {p['phase'] for p in report['phases']}!=baseline_phases:
        raise ValueError('Phase set mismatch')
    for phase in (p['phase'] for p in report['phases']):
        candidate=metrics(report,phase)|process_memory(a.candidate,report,phase)
        base=[metrics(b,phase)|process_memory(p,b,phase) for p,_,b in baselines]
        for key,value in candidate.items():
            control=statistics.median(b[key] for b in base)
            ratio=value/control-1 if control else (0.0 if value==0 else None)
            passed=(value==0 if control==0 else value<=control*(1+a.budget))
            row={'phase':phase,'metric':key,'baseline':control,'candidate':value,'relativeIncrease':ratio,'pass':passed}
            rows.append(row)
            if not passed:failures.append(row)
    result={'schema':1,'budget':a.budget,'baselineRuns':[str(p) for p in a.baseline],'candidateRun':str(a.candidate),
            'pass':not failures,'failures':failures,'measurements':rows,
            'scope':'Matched recorded camera visits and process RAM intervals; does not prove every map location or eliminate OS noise.'}
    # Keep the strict per-visit verdict above unchanged. Whole-route event totals
    # describe rare events without treating a single zero-count visit as a rate estimate.
    candidate_events=route_events(report);baseline_events=[route_events(b) for _,_,b in baselines]
    event_rows=[]
    for kind,values in candidate_events.items():
        control=statistics.median(b[kind]['eventsPerSecond'] for b in baseline_events)
        event_rows.append({'kind':kind,'candidate':values,'baselines':[b[kind] for b in baseline_events],
                           'relativeIncrease':values['eventsPerSecond']/control-1 if control else (0.0 if values['events']==0 else None),
                           'withinBudget':values['eventsPerSecond']<=control*(1+a.budget) if control else values['events']==0})
    result['wholeRouteRareEvents']={'measurements':event_rows,'withinBudget':all(row['withinBudget'] for row in event_rows),
                                  'scope':'Additional aggregate evidence; does not override the strict per-visit pass or its failures.'}
    a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'pass':result['pass'],'failedMetrics':len(failures),'output':str(a.output)}))
if __name__=='__main__':main()
