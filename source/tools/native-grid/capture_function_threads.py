"""One read-only thread dump of the current failed functional child."""
import json, subprocess
from run_profiles import ROOT, JAVA, write

out=ROOT/'research/function-qa/candidate'
record=json.loads((out/'process.json').read_text(encoding='utf-8'))
assert record['pid']==9832 and record['world']==str(ROOT/'research/pilot/candidate')
target=out/'post-save-threads.txt'
assert not target.exists(), 'Do not repeat this addressed diagnostic'
with target.open('w',encoding='utf-8') as stream:
    child=subprocess.run([str(JAVA.with_name('jcmd.exe')),str(record['pid']),'Thread.print','-l'],stdout=stream,stderr=subprocess.STDOUT,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
write(out/'post-save-threads-receipt.json',{'exitCode':child.returncode,'pid':record['pid'],'readOnly':True,'minecraftProcessesStarted':0,'output':str(target)})
assert child.returncode==0
