import shutil,subprocess
from common import *
wait_foreign();source=Path(r'C:\Users\vakir\AppData\Local\Temp\2026_10_20_01_17_17_88372');target=OUT/'after/client/jfr-repository';target.mkdir(exist_ok=False)
for p in ordinary(source).glob('*.jfr'):shutil.copyfile(ordinary(p),target/p.name)
with (OUT/'after/client/recovery.log').open('w') as log:
 r=subprocess.run([str(JAVA_HOME/'bin/jfr.exe'),'assemble',str(target),str(OUT/'after/client/client.jfr')],env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xmx512m'),stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
write(OUT/'after/client/recovery.json',{'status':'PARTIAL_WATCHDOG_RUN_NOT_COMPLETE','exitCode':r.returncode,'serverRecordingUnavailable':True})
assert r.returncode==0
