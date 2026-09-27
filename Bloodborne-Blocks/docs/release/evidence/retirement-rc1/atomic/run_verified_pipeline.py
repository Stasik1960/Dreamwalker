import datetime,json,os,subprocess,sys,time
from pathlib import Path
BASE=Path.cwd();RUN=BASE/'build/retirement-rc1-20260927';OUT=RUN/'atomic-diagnostic';ENGINE=Path('C:/Users/vakir/Documents/ChatGPT/DW/Bloodborne-Blocks')
stage=RUN/'verified_phase2_stage.py'; prefix=[sys.executable,'-B','-X','utf8']
commands=[(n,prefix+[str(stage),n]) for n in ('logical','preservation','protected','helpers-registry')]
commands.append(('second-convert',prefix+[str(ENGINE/'tools/convert_logical_world.py'),str(OUT/'Bloodborne-MODDED-atomic-diagnostic-rc1'),str(OUT/'second-world-verified'),'--source-mode','modded','--atomic-owner-groups','--report',str(OUT/'second-verified.json'),'--report-root',str(OUT),'--progress']))
commands.extend((n,prefix+[str(stage),n]) for n in ('second-preservation','manifests'))
results=[]
for label,cmd in commands:
    print('START '+label,flush=True)
    record={'command':cmd,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'DIAGNOSTIC_ONLY existing repair engine; not rc1 runtime validation'}
    start=time.monotonic()
    with (OUT/('verified-'+label+'.log')).open('wb') as log:
        process=subprocess.Popen(cmd,cwd=BASE,stdout=log,stderr=subprocess.STDOUT,env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
        record['pid']=process.pid
        (OUT/('verified-'+label+'.command.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
        try:code=process.wait(timeout=900)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=log,stderr=subprocess.STDOUT);process.wait(timeout=30);code=-1
    record.update(exitCode=code,seconds=round(time.monotonic()-start,2))
    (OUT/('verified-'+label+'.command.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    results.append({'stage':label,'exitCode':code,'seconds':record['seconds']});print(json.dumps(results[-1]),flush=True)
    if code not in (0,1):break
(OUT/'verified-pipeline.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf8')
