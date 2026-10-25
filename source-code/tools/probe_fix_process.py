import json,subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'fix-evidence/export';pid=json.loads((D/'process.json').read_text())['pid']
for cmd in ('Thread.print','GC.heap_info'):
 with (D/(cmd+'.log')).open('w',encoding='utf-8') as out:subprocess.run([str(P/'build-tools/jdk/bin/jcmd.exe'),str(pid),cmd],stdout=out,stderr=subprocess.STDOUT,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
