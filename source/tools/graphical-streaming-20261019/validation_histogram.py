import subprocess
from common import *
case=OUT/'validation-tail';p=json.loads((case/'prepared.json').read_text());active={j['ProcessId'] for j in java_processes()}
if p['serverPid'] in active:
 with (case/'server/oom-histogram.txt').open('w') as log:
  subprocess.run([str(JAVA_HOME/'bin/jcmd.exe'),str(p['serverPid']),'GC.class_histogram','-all'],stdout=log,stderr=subprocess.STDOUT,timeout=45,creationflags=subprocess.CREATE_NO_WINDOW)
