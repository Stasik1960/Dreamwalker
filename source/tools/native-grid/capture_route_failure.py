"""One read-only thread snapshot after a failed owned route, never a new JVM."""
import argparse, json, subprocess, time
from run_profiles import ROOT, JAVA, write

p=argparse.ArgumentParser();p.add_argument('--out',required=True);a=p.parse_args()
out=(ROOT/a.out).resolve()
assert out.is_relative_to(ROOT)
client=json.loads((out/'client/client-route.json').read_text(encoding='utf-8'))
assert client['status']=='FAIL','Only diagnose an already failed measurement'
pid=int(json.loads((out/'pids.json').read_text())['server'])
query=subprocess.check_output(['powershell.exe','-NoProfile','-Command',
    f'(Get-CimInstance Win32_Process -Filter "ProcessId={pid}").CommandLine'],text=True,timeout=10)
assert str(out/'server.args').lower() in query.replace('/','\\').lower(),'Not the owned route server'
target=out/'failed-server-threads.txt';assert not target.exists()
started=time.monotonic()
with target.open('w',encoding='utf-8') as log:
    try:
        result=subprocess.run([str(JAVA.with_name('jcmd.exe')),str(pid),'Thread.print'],
            stdout=log,stderr=subprocess.STDOUT,timeout=20,creationflags=subprocess.CREATE_NO_WINDOW)
        code=result.returncode
    except subprocess.TimeoutExpired:code='timeout'
write(out/'failed-server-diagnostic.json',{'status':'FAILED_ROUTE_DIAGNOSTIC_ONLY','pid':pid,
    'command':'Thread.print','exitCode':code,'seconds':time.monotonic()-started,
    'readOnly':True,'newMinecraftProcesses':0,'doesNotValidateGame':True})
