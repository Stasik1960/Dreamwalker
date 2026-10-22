"""Close only the diagnosed, already stopped failed QA JVM."""
import ctypes, json, subprocess
from pathlib import Path
from run_profiles import ROOT, write

out=ROOT/'research/function-qa/candidate'
record=json.loads((out/'process.json').read_text(encoding='utf-8'))
assert record['pid']==9832
result=json.loads((out/'functions.json').read_text(encoding='utf-8'))
assert result['status']=='FAIL'
console=(out/'server-console.log').read_text(encoding='utf-8',errors='replace')
assert 'All dimensions are saved' in console and 'OutOfMemoryError' not in console
threads=(out/'post-save-threads.txt').read_text(encoding='utf-8')
assert '"Server thread"' not in threads and '"DestroyJavaVM"' in threads and '"Fzzy Config Worker-1"' in threads
command="Get-CimInstance Win32_Process -Filter 'ProcessId=9832' | Select-Object ProcessId,CommandLine | ConvertTo-Json"
raw=subprocess.check_output(['powershell.exe','-NoProfile','-NonInteractive','-Command',command],text=True,creationflags=subprocess.CREATE_NO_WINDOW)
actual=json.loads(raw)
assert actual['ProcessId']==record['pid'] and record['arguments'] in actual['CommandLine']
receipt={'status':'FAIL_QA_VM_LINGER_AFTER_GAME_SERVER_STOPPED','pid':record['pid'],'gameServerSavedAndStopped':True,'reason':'Only diagnosed non-daemon Fzzy config workers retain the failed QA VM; game server thread is absent','ownChildCommandLine':actual['CommandLine'],'functionalFailurePreserved':result['error']}
write(out/'post-save-vm-stop.json',receipt)
handle=ctypes.windll.kernel32.OpenProcess(0x0001,False,record['pid'])
assert handle
try:assert ctypes.windll.kernel32.TerminateProcess(handle,1)
finally:ctypes.windll.kernel32.CloseHandle(handle)
