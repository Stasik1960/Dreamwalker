from common import *
import subprocess,time
def main():
 log_to('build-wrapper');version='0.1.0-v12.7.4-owner32-static-city.6-chunk-mesh.3-materials.1-streaming.7-blocks.1'
 args=[str(PROJECT/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1G',r'-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar','-Pmod_version='+version,*sys.argv[1:]]
 if not sys.argv[1:]:args.append('remapJar')
 start=time.time()
 with (OUT/'build.log').open('w',encoding='utf8') as f:r=subprocess.run(args,cwd=PROJECT,env=dict(os.environ,JAVA_HOME=str(JAVA_HOME)),stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 write(OUT/'build.json',{'status':'PASS' if r.returncode==0 else 'FAIL','exitCode':r.returncode,'seconds':time.time()-start,'args':args})
 if r.returncode:raise RuntimeError('Build failed')
 print('PASS')
if __name__=='__main__':main()
