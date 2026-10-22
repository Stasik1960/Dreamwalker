"""A validation-only prelude, never used by the measured before/after pair."""
import subprocess
from common import *

case=OUT/'validation-tail'
prepared=json.loads((case/'prepared.json').read_text());assert prepared['status']=='NATIVE_CLIENT_RUNNING'
client=json.loads((case/'client/client-route.json').read_text());assert not client['phases']
assert 'joined the game' not in (case/'server/console.log').read_text(errors='replace')
command=[str(JAVA_HOME/'bin/jcmd.exe'),str(prepared['serverPid']),'GC.run']
with (case/'validation-only-gc.log').open('w') as log:
 result=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=90,creationflags=subprocess.CREATE_NO_WINDOW)
write(case/'validation-only-gc.json',{'status':'PASS' if result.returncode==0 else 'FAIL','serverPid':prepared['serverPid'],'exitCode':result.returncode,'scope':'Before client join, functional tail verification only; absent from both numeric comparison runs.'})
assert result.returncode==0
