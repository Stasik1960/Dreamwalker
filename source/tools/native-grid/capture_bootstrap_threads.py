"""One read-only thread snapshot of the currently owned functional child."""
import json,subprocess
from run_profiles import ROOT,JAVA,write
out=ROOT/'research/function-qa/baseline-bootstrap'
record=json.loads((out/'process.json').read_text(encoding='utf-8'))
pid=int(record['pid']);assert pid>0
snapshot=out/'threads-after-startup.txt';assert not snapshot.exists()
command='(Get-CimInstance Win32_Process -Filter "ProcessId = '+str(pid)+'").CommandLine'
actual=subprocess.check_output(['powershell.exe','-NoProfile','-Command',command],text=True,timeout=20)
assert str(record['arguments']).lower() in actual.lower(),'PID is not the exact owned test child'
result=subprocess.run([str(JAVA.with_name('jcmd.exe')),str(pid),'Thread.print'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=20)
snapshot.write_text(result.stdout,encoding='utf-8')
write(out/'thread-snapshot-receipt.json',{'status':'CAPTURED' if result.returncode==0 else 'FAIL','pid':pid,'exitCode':result.returncode,'readOnly':True,'newMinecraftProcess':False,'file':str(snapshot)})
assert result.returncode==0
