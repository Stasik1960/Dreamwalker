"""One read-only stack of the already-owned slow authoring process."""
import argparse,json,subprocess,time
from pathlib import Path
from run_profiles import ROOT,sha,write

p=argparse.ArgumentParser();p.add_argument('--pid',type=int,required=True);p.add_argument('--output-name',default='compose-python-stack');a=p.parse_args()
assert Path(a.output_name).name==a.output_name and a.output_name not in ('.','..')
out=ROOT/'research/native-grid-plan/full';target=out/(a.output_name+'.txt')
assert not target.exists(),'Preserve the diagnostic'
query=subprocess.check_output(['powershell.exe','-NoProfile','-Command',
    f'Get-CimInstance Win32_Process -Filter "ProcessId={a.pid}" | Select-Object Name,CommandLine | ConvertTo-Json'],text=True,timeout=10)
actual=json.loads(query)
assert actual['Name']=='python.exe' and 'tools/native-grid-city/migrate.py' in actual['CommandLine'] and 'compose' in actual['CommandLine'] and '../research/native-grid-plan' in actual['CommandLine']
tool=ROOT/'research/diagnostics/py-spy-runtime/bin/py-spy.exe'
started=time.monotonic()
with target.open('w',encoding='utf-8') as stream:
    result=subprocess.run([str(tool),'dump','--pid',str(a.pid)],stdout=stream,stderr=subprocess.STDOUT,timeout=15,creationflags=subprocess.CREATE_NO_WINDOW)
write(out/(a.output_name+'-receipt.json'),{'status':'READ_ONLY_STACK_CAPTURED' if result.returncode==0 else 'STACK_CAPTURE_FAILED',
    'pid':a.pid,'exitCode':result.returncode,'seconds':time.monotonic()-started,'toolSha256':sha(tool),
    'outputSha256':sha(target),'newMinecraftProcesses':0,'worldWrites':0,'databaseWrites':0,'localsRequested':False})
assert result.returncode==0
