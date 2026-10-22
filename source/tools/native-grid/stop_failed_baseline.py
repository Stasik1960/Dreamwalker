"""Stop only our already OOM-failed disposable baseline process."""
import os,signal,subprocess
from run_profiles import ROOT,write
pid=5428;folder=ROOT/'research/function-qa/baseline'
assert 'OutOfMemoryError' in (folder/'server-console.log').read_text(encoding='utf-8',errors='replace')
command=subprocess.check_output(['powershell.exe','-NoProfile','-Command',f'(Get-CimInstance Win32_Process -Filter "ProcessId={pid}").CommandLine'],text=True).strip()
assert 'function-qa\\server-baseline\\functions.args' in command,command
os.kill(pid,signal.SIGTERM)
write(folder/'forced-stop.json',{'pid':pid,'reason':'Repeated actual4G Java heap OOM before startup completion; exact owned command line verified','status':'FAILED_NOT_GAME_READY','originalWorldUntouched':True})
