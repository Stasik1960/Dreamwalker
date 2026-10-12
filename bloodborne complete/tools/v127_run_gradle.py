"""Serialized Java 17 compile/build/native GameTest launcher, always via Guard Operation."""
from pathlib import Path
import subprocess,os,argparse,json,datetime,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--name',required=True);p.add_argument('--stage',choices=['compile','build','gametest'],required=True);p.add_argument('--heap-gb',type=int,choices=range(2,6),default=3);p.add_argument('--processors',type=int,choices=range(2,9),default=4);p.add_argument('--worldedit',action='store_true');a=p.parse_args()
assert a.name.replace('-','').replace('_','').isalnum()
out=ROOT/'build/v127-validation'/a.name;out.mkdir(parents=True,exist_ok=False)
java=Path.home()/'AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma'
env=dict(os.environ,JAVA_HOME=str(java),PATH=str(java/'bin')+os.pathsep+os.environ['PATH'])
tasks={'compile':['compileJava','compileGametestJava'],'build':['build','compileGametestJava'],'gametest':['runGametest']}[a.stage]
command=[str(ROOT/'gradlew.bat'),'--no-daemon','-Dorg.gradle.java.home='+str(java),'-Pgametest_run_name='+a.name,*tasks]
if a.stage=='gametest':
 command[4:4]=['-Pgametest_heap_gb='+str(a.heap_gb),'-Pgametest_processor_count='+str(a.processors)]
 if a.worldedit:command.insert(4,'-Pworldedit_qa=true')
with (out/'gradle.log').open('wb') as log:r=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
data={'status':'PASS' if r.returncode==0 else 'FAIL','exitCode':r.returncode,'command':command,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Disposable QA world; no original map or gallery changes'}
if a.stage=='gametest':
 report=ROOT/'build/test-results/gametest'/('TEST-'+a.name+'.xml');data['nativeXmlExists']=report.is_file();data['heapGiB']=a.heap_gb;data['activeProcessors']=a.processors;data['worldEditOptionalRuntime']=a.worldedit
 fatal=any(token in (out/'gradle.log').read_text(errors='replace') for token in ('OutOfMemoryError','BUILD FAILED','GameTestException'));data['fatalNativeLog']=fatal
 if report.is_file():
  tree=ET.parse(report).getroot();suites=[tree] if tree.tag=='testsuite' else list(tree.iter('testsuite'))
  data['tests']=sum(int(s.get('tests','0')) for s in suites);data['failures']=sum(int(s.get('failures','0'))+int(s.get('errors','0')) for s in suites)
  data['status']='PASS' if r.returncode==0 and data['tests']>=100 and data['failures']==0 and not fatal else 'FAIL'
 else:data['status']='FAIL';data['reason']='No native GameTest completion XML; Gradle success is insufficient'
(out/'result.json').write_text(json.dumps(data,indent=2)+'\n');print(json.dumps(data));raise SystemExit(0 if data['status']=='PASS' else 1)
