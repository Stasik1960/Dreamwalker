import json,os,subprocess,sys,time
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';D.mkdir(exist_ok=True);tasks=sys.argv[1:] or ['remapJar'];start=time.time();heap=os.environ.get('DW_NPC_BUILD_HEAP','1G')
(D/'build.json').write_text(json.dumps({'status':'RUNNING','exitCode':None,'tasks':tasks}),encoding='utf-8')
args=[str(P/'gradlew.bat'),'--offline','--no-daemon','--max-workers=1','-Dorg.gradle.jvmargs=-Xmx'+heap,'-Pworldedit_api_jar='+str(P/'build-tools/worldedit-mod-7.2.15.jar'),*tasks]
with (D/'build.log').open('w',encoding='utf-8') as out:r=subprocess.run(args,cwd=P,env={**os.environ,'JAVA_HOME':str(P/'build-tools/jdk')},stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(D/'build.json').write_text(json.dumps({'exitCode':r.returncode,'seconds':time.time()-start,'tasks':tasks},indent=2),encoding='utf-8');raise SystemExit(r.returncode)
