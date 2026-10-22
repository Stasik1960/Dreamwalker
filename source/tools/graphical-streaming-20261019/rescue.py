import shutil,subprocess
from common import *
wait_foreign()
source=Path(r'C:\Users\vakir\AppData\Local\Temp\2026_10_20_00_17_58_86908')
target=OUT/'baseline/client/jfr-repository';target.mkdir(exist_ok=False)
for p in ordinary(source).glob('*.jfr'):shutil.copyfile(ordinary(p),target/p.name)
with (OUT/'baseline/client/recovery.log').open('w') as log:
    r=subprocess.run([str(JAVA_HOME/'bin/jfr.exe'),'assemble',str(target),str(OUT/'baseline/client/client.jfr')],env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xmx512m'),stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
write(OUT/'baseline/client/recovery.json',{'status':'RECOVERED_PARTIAL_NATIVE_RECORDING_NOT_A_COMPLETE_ROUTE','exitCode':r.returncode,'source':str(source),'chunks':len(list(target.glob('*.jfr')))})
assert r.returncode==0
