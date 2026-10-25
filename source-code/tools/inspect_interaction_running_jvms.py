import json,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';G='c:/users/vakir/appdata/local/limacina/project/dreamwalker';root=str(P).replace('\\','/').lower()
ps="Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') } | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"
result=subprocess.run(['powershell.exe','-NoProfile','-Command',ps],capture_output=True,text=True,timeout=20);raw=json.loads(result.stdout) if result.stdout.strip() else [];raw=raw if isinstance(raw,list) else [raw];rows=[]
for process in raw:
 cmd=(process.get('CommandLine') or '').replace('\\','/').lower();kind='qa-project' if root in cmd else 'limacina-client' if G in cmd else 'other-java'
 rows.append({'pid':process['ProcessId'],'kind':kind})
file=Path('C:/Users/vakir/Desktop/bloodborne/session.lock');report={'jvms':rows,'desktopWorldSessionLockMtimeNs':file.stat().st_mtime_ns}
(D/'running-jvms.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
