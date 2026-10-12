"""Serialized Java 17 compile/build/native GameTest launcher, always via Guard Operation."""
from pathlib import Path
import subprocess,os,argparse,json,datetime
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--name',required=True);p.add_argument('--stage',choices=['compile','build','gametest'],required=True);a=p.parse_args()
assert a.name.replace('-','').replace('_','').isalnum()
out=ROOT/'build/v127-validation'/a.name;out.mkdir(parents=True,exist_ok=False)
java=Path.home()/'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
env=dict(os.environ,JAVA_HOME=str(java),PATH=str(java/'bin')+os.pathsep+os.environ['PATH'])
tasks={'compile':['compileJava','compileGametestJava'],'build':['build','compileGametestJava'],'gametest':['runGametest']}[a.stage]
command=[str(ROOT/'gradlew.bat'),'--no-daemon','-Dorg.gradle.java.home='+str(java),'-Pgametest_run_name='+a.name,*tasks]
with (out/'gradle.log').open('wb') as log:r=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
data={'status':'PASS' if r.returncode==0 else 'FAIL','exitCode':r.returncode,'command':command,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Disposable QA world; no original map or gallery changes'}
(out/'result.json').write_text(json.dumps(data,indent=2)+'\n');print(json.dumps(data));raise SystemExit(r.returncode)
