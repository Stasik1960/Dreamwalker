import json,os,subprocess,sys,time
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'interaction-evidence';D.mkdir(exist_ok=True);tasks=sys.argv[1:] or ['remapJar'];start=time.time()
args=[str(P/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2G','-Pworldedit_api_jar='+str(P/'build-tools/worldedit-mod-7.2.15.jar'),*tasks]
with (D/'build.log').open('w',encoding='utf-8') as out:r=subprocess.run(args,cwd=P,env={**os.environ,'JAVA_HOME':str(P/'build-tools/jdk')},stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(D/'build.json').write_text(json.dumps({'exitCode':r.returncode,'seconds':time.time()-start,'tasks':tasks},indent=2),encoding='utf-8');sys.exit(r.returncode)
