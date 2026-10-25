import json, subprocess
from pathlib import Path
P = Path(__file__).resolve().parents[1]
D = P / 'interaction-evidence/server-1'
pid = json.loads((D / 'process.json').read_text())['pid']
for name, command in [('heap', 'GC.heap_info'), ('threads', 'Thread.print')]:
    result = subprocess.run([str(P / 'build-tools/jdk/bin/jcmd.exe'), str(pid), command], capture_output=True, text=True, timeout=25)
    (D / (name + '.txt')).write_text(result.stdout + result.stderr, encoding='utf-8')
