"""One matched pair. Sky/diagnostic cameras never inflate headline city FPS."""
import csv,hashlib,json,math,sys,traceback
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
sys.path.insert(0,str(P/'tools/native-visual'))
from analyze import stats
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    reports={};bindings={}
    for kind in ('baseline','candidate'):
        folder=T/'runs'/(kind+'-1');receipt=read(folder/'receipt.json');assert receipt['status']=='PASS'
        binding=read(folder/'binding.json');client=read(folder/'client/client-route.json');bindings[kind]=binding
        phase={p['phase']:p for p in client['phases']};core=[n for n in phase if n=='north-facade' or n.startswith('userpack-r')];assert len(core)==9
        steady=[];moving=[]
        with (folder/'client/client-frames.csv').open(newline='',encoding='utf-8') as stream:
            for row in csv.DictReader(stream):
                n=row['phase']
                if n not in core:continue
                p=phase[n];end=int(row['endEpochNs']);duration=int(row['intervalNs']);moving.append(duration/1e6)
                if end-duration>=p['stationaryStartEpochNs'] and end<=p['endEpochNs']:steady.append(duration/1e6)
        reports[kind]={'productionSha256':binding['productionSha256'],'corePhases':core,'steadyCity':stats(steady),
            'cityFramesIncludingMovement':stats(moving),'metrics':{'path':str(T/(kind+'-metrics.json')),'sha256':sha(T/(kind+'-metrics.json'))},
            'resources':read(T/(kind+'-resources.json')),'startup':read(T/(kind+'-metrics.json'))['observedStartup']}
    for key in ('qaSha256','profileLockSha256','routeSha256','sharedMods','heapLimits','graphics','hostLogicalProcessors'):
        assert bindings['baseline'][key]==bindings['candidate'][key],key
    b,c=reports['baseline'],reports['candidate']
    result={'status':'ONE_MATCHED_PAIR_COMPLETE_NOT_FULL_CITY_ACCEPTANCE','runs':reports,
        'deltaPercent':{'cityAverageFps':100*(c['steadyCity']['averageFps']/b['steadyCity']['averageFps']-1),
        'cityOnePercentLowFps':100*(c['steadyCity']['onePercentLowFps']/b['steadyCity']['onePercentLowFps']-1)},
        'limitations':['One paired observation, same16GiB Ryzen5800H/RTX3070Laptop host running both JVMs.',
        'Nine established city witnesses form headline FPS. Other five views remain separate in raw metrics.',
        'Process RSS differs from heap. Low free RAM/shared CPU may affect loading tails; no numerical hardware correction.',
        'City geometry, protected objects and full-route readiness require separate visual/offline acceptance.']}
    (T/'comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'comparison-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
