import datetime, hashlib, json, os, subprocess, sys, time
from pathlib import Path
base=Path.cwd()
source=base/'build/retirement-rc1-20260927/Bloodborne-MODDED-retired-composites-rc1.zip'
out=base/'build/retirement-rc1-20260927/atomic-diagnostic'
out.mkdir(exist_ok=True)
engine=Path('C:/Users/vakir/Documents/ChatGPT/DW/Bloodborne-Blocks')
files=sorted((engine/'tools').glob('*.py'))
for folder in ['src/main/resources/bloodborne_blocks/logical','src/main/resources/bloodborne_blocks/city','docs/composite-grid-repair']:
    files.extend(p for p in (engine/folder).rglob('*') if p.is_file() and p.suffix in ('.json','.gz'))
manifest={p.relative_to(engine).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
cmd=[sys.executable,'-B','-X','utf8',str(engine/'tools/convert_logical_world.py'),str(source),str(out/'Bloodborne-MODDED-atomic-diagnostic-rc1'),'--source-mode','modded','--atomic-owner-groups','--expected-source-sha256','aed638e52143522abfbb3c63b85f377726727a5af039f532417ae0c6772504d1','--report',str(out/'first.json'),'--report-root',str(out),'--progress']
record={'command':cmd,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'DIAGNOSTIC_ONLY: existing uncommitted repair engine; not release rc1 runtime','engineFiles':manifest,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest()}
start=time.monotonic()
with (out/'first.log').open('xb') as log:
    process=subprocess.Popen(cmd,cwd=engine,stdout=log,stderr=subprocess.STDOUT,env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    record['pid']=process.pid
    (out/'first.command.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    try: code=process.wait(timeout=900)
    except subprocess.TimeoutExpired:
        subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=log,stderr=subprocess.STDOUT)
        process.wait(timeout=30); code=-1
record.update(exitCode=code,seconds=round(time.monotonic()-start,2),engineUnchanged=all(hashlib.sha256((engine/name).read_bytes()).hexdigest()==digest for name,digest in manifest.items()))
(out/'first.command.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in record.items() if k!='engineFiles'}),flush=True)
sys.exit(code)
