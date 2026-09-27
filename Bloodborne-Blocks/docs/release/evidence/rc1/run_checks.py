import datetime,json,os,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/rc1-verification'
JDK=Path('C:/Users/vakir/Documents/ChatGPT/DW/Bloodborne-Blocks/build/toolchain/jdk-17.0.20.1')
cmd=[os.environ.get('COMSPEC','cmd.exe'),'/d','/c','gradlew.bat','--offline','--no-daemon','--console=plain','-I','tools/verification-direct-resources.init.gradle','-PbloodbornePython='+sys.executable,'-Dorg.gradle.java.installations.paths='+str(JDK),'check','build','logicalGameTest','checkReleaseVersion','--max-workers=1']
record={'command':cmd,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'2.1.0-rc.1 verification candidate; not restart/client/full-city QA','sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()}
start=time.monotonic()
with (OUT/'check-build-gametest.log').open('wb') as log:
    process=subprocess.Popen(cmd,cwd=ROOT,env=dict(os.environ,JAVA_HOME=str(JDK),PYTHONDONTWRITEBYTECODE='1'),stdout=log,stderr=subprocess.STDOUT)
    record['processId']=process.pid
    (OUT/'check-build-gametest.json').write_text(json.dumps(record,indent=2),encoding='utf8')
    try: code=process.wait(timeout=1500)
    except subprocess.TimeoutExpired:
        subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=log,stderr=subprocess.STDOUT)
        process.wait(timeout=30);code=-1
record.update(exitCode=code,seconds=round(time.monotonic()-start,2))
(OUT/'check-build-gametest.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
print(json.dumps(record),flush=True)
sys.exit(code)
