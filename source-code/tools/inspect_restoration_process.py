import json,subprocess,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'restoration-evidence';pid=json.loads((D/sys.argv[1]/'process.json').read_text())['pid']
r=subprocess.run([str(P/'build-tools/jdk/bin/jcmd.exe'),str(pid),'Thread.print'],capture_output=True,text=True,errors='replace',timeout=30)
(D/(sys.argv[1]+'-threads.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
