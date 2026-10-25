import json,subprocess,sys
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence'/sys.argv[1];pid=json.loads((D/'process.json').read_text())['pid'];r=subprocess.run([str(P/'build-tools/jdk/bin/jcmd.exe'),str(pid),'Thread.print'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=25);(D/'thread-sample.txt').write_text(r.stdout+r.stderr,encoding='utf-8')
