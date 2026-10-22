"""One bounded heap histogram of the already failed owned baseline JVM."""
import json,subprocess,time
from run_profiles import ROOT,JAVA,write
pid=5428
query=subprocess.check_output(['powershell.exe','-NoProfile','-Command',f'(Get-CimInstance Win32_Process -Filter "ProcessId={pid}").CommandLine'],text=True)
assert 'function-qa\\server-baseline\\functions.args' in query,query
out=ROOT/'research/function-qa/baseline';tool=JAVA.with_name('jcmd.exe');start=time.monotonic()
with (out/'failed-heap-histogram.txt').open('w',encoding='utf-8') as log:
    try:r=subprocess.run([str(tool),str(pid),'GC.class_histogram','-all'],stdout=log,stderr=subprocess.STDOUT,timeout=40,creationflags=subprocess.CREATE_NO_WINDOW);code=r.returncode
    except subprocess.TimeoutExpired:code='timeout'
write(out/'failed-heap-diagnostic.json',{'pid':pid,'status':'FAILED_BASELINE_DIAGNOSTIC_ONLY','command':'GC.class_histogram -all','exitCode':code,'seconds':time.monotonic()-start,'doesNotValidateGame':True})
print(json.dumps({'exitCode':code,'output':str(out/'failed-heap-histogram.txt')}))
