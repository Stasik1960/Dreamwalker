import json,subprocess,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence'/sys.argv[1];pid=json.loads((D/'process.json').read_text())['pid'];result=subprocess.run(['powershell.exe','-NoProfile','-Command',f'Get-CimInstance Win32_Process -Filter "ProcessId={pid}" | Select-Object -ExpandProperty CommandLine'],capture_output=True,text=True,timeout=15)
command=result.stdout.replace('\\','/').lower();expected=str(D/'launch.args').replace('\\','/').lower();assert expected in command,'PID does not belong to this QA launch'
subprocess.run(['powershell.exe','-NoProfile','-Command',f'Stop-Process -Id {pid} -Force'],check=True,capture_output=True,timeout=15);(D/'aborted.json').write_text(json.dumps({'pid':pid,'reason':'QA copied a build before its final receipt; discard this run'},indent=2),encoding='utf-8')
