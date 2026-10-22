import json,subprocess
from common import *
case=OUT/'baseline'
p=json.loads((case/'prepared.json').read_text())
with (case/'client/startup-thread.txt').open('w',encoding='utf-8') as log:
    result=subprocess.run([str(JAVA_HOME/'bin/jcmd.exe'),str(p['clientPid']),'Thread.print','-l'],stdout=log,stderr=subprocess.STDOUT,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
write(case/'client/startup-thread-receipt.json',{'exitCode':result.returncode,'pid':p['clientPid']})
