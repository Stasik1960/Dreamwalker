"""Preserve recordings from only our current case's known child PIDs."""
import subprocess,sys
from common import *
case=OUT/sys.argv[1];p=json.loads((case/'prepared.json').read_text());active={j['ProcessId'] for j in java_processes()};jobs=[]
for side in ['server','client']:
 pid=p.get(side+'Pid')
 if pid not in active:continue
 log=(case/side/'emergency-dump.log').open('w')
 target=case/side/(side+'-emergency.jfr')
 job=subprocess.Popen([str(JAVA_HOME/'bin/jcmd.exe'),str(pid),'JFR.dump','filename='+str(target)],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 jobs.append((job,log,side))
for job,log,side in jobs:
 try:code=job.wait(timeout=40)
 except subprocess.TimeoutExpired:job.kill();code=job.wait(timeout=10)
 log.close()
