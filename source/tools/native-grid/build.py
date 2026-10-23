"""Run only under Guard.ps1 -Mode Operation. Root owns every build invocation."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, sys, time
P=Path(__file__).resolve().parents[2]
O=P.parent/'research/build'
O.mkdir(parents=True,exist_ok=True)
JAVA=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma')
API=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar')
assert API.is_file()
if 'remapNativeGridRelightJar' in sys.argv[1:]:
    prior=P/'build/libs/dreamwalker-native-grid-relight-0.1.0-v12.7.4-native-grid.1.jar'
    if prior.is_file():
        digest=hashlib.sha256(prior.read_bytes()).hexdigest()
        history=P.parent/'research/runtime-history';history.mkdir(parents=True,exist_ok=True)
        preserved=history/('native-grid-relight-'+digest+'.jar')
        if not preserved.exists():shutil.copy2(prior,preserved)
        assert hashlib.sha256(preserved.read_bytes()).hexdigest()==digest
name=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')
args=[str(P/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1G','-Pworldedit_api_jar='+str(API),'-Pmod_version=0.1.0-v12.7.4-native-grid.1',*sys.argv[1:]]
start=time.monotonic()
with (O/(name+'.log')).open('w',encoding='utf-8') as log:
    r=subprocess.run(args,cwd=P,env=dict(os.environ,JAVA_HOME=str(JAVA)),stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
report={'status':'PASS' if r.returncode==0 else 'FAIL','exitCode':r.returncode,'seconds':time.monotonic()-start,'args':args,'log':str(O/(name+'.log'))}
(O/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(O/'latest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
sys.exit(r.returncode)
