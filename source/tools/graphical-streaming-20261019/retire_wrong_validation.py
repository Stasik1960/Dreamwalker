"""Stop only the client started by our validation supervisor with the old QA."""
import subprocess
from common import *
case=OUT/'validation-tail';p=json.loads((case/'prepared.json').read_text())
assert p['status']=='NATIVE_CLIENT_RUNNING'
assert p['qaSha256']==sha(OUT/'dreamwalker-native-route-qa.jar')
assert p['clientPid']==105052 and p['serverPid']==108292
assert any(j['ProcessId']==p['clientPid'] and j['ExecutablePath']==str(JAVA) for j in java_processes())
with (case/'owned-client-stop.log').open('w') as log:
 r=subprocess.run(['taskkill.exe','/PID',str(p['clientPid']),'/F'],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
assert r.returncode==0
write(case/'owned-client-stop.json',{'clientPid':p['clientPid'],'reason':'Old measurement QA selected by artifact glob; supervisor owns server and will stop/save it through its own stdin. No foreign process touched.'})
