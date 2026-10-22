"""Guard child wrapper with durable exit and exception receipts."""
import datetime, hashlib, json, os, runpy, sys, time, traceback
from pathlib import Path
P=Path(__file__).resolve().parents[2]
O=P.parent/'research/processes';O.mkdir(parents=True,exist_ok=True)
script=Path(sys.argv[1]).resolve();args=sys.argv[2:]
assert script.is_relative_to(P),script
name=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+script.stem
input_sources={}
if script.stem=='migrate' and 'compose' in args:
    archive=O/(name+'.inputs');archive.mkdir()
    for source in (script,script.with_name('context_stage.py')):
        payload=source.read_bytes();destination=archive/source.name
        destination.write_bytes(payload)
        input_sources[str(source)]={'sha256':hashlib.sha256(payload).hexdigest(),'archive':str(destination)}
start=time.monotonic();code=0
with (O/(name+'.log')).open('w',encoding='utf-8',buffering=1) as log:
    sys.stdout=sys.stderr=log;sys.argv=[str(script),*args]
    sys.path.insert(0,str(script.parent))
    try:runpy.run_path(str(script),run_name='__main__')
    except SystemExit as e:code=e.code or 0
    except BaseException:traceback.print_exc();code=1
report={'status':'PASS' if code==0 else 'FAIL','exitCode':code,'script':str(script),'args':args,'seconds':time.monotonic()-start,'log':str(O/(name+'.log'))}
if input_sources:report['inputSources']=input_sources
(O/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(O/'latest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
sys.exit(code if isinstance(code,int) else 1)
