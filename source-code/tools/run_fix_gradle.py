import json,os,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parents[1];T=R/'build-tools';task=sys.argv[1] if len(sys.argv)>1 else 'help';label=task.replace(':','_');env={**os.environ,'JAVA_HOME':str(T/'jdk')};env['PATH']=str(T/'jdk/bin')+os.pathsep+env.get('PATH','')
cmd=[str(T/'jdk/bin/java.exe'),'-Xmx64m','-classpath',str(R/'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--max-workers=1','-Dorg.gradle.jvmargs=-Xmx1536m -XX:ActiveProcessorCount=2',task]
with (T/('gradle-'+label+'.log')).open('w',encoding='utf-8') as log:
 start=time.time();p=subprocess.Popen(cmd,cwd=R,env=env,stdout=log,stderr=subprocess.STDOUT);(T/'gradle-process.json').write_text(json.dumps({'pid':p.pid,'task':task,'cmd':cmd}));code=p.wait()
(T/('gradle-'+label+'.json')).write_text(json.dumps({'task':task,'exitCode':code,'seconds':time.time()-start},indent=2));sys.exit(code)
