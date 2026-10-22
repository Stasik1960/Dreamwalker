import json,os,subprocess,sys,traceback
from pathlib import Path
from common import *
def main():
    wait_foreign();kind=sys.argv[1]
    sourceHashes={name:sha(PROJECT/name) for name in PRODUCTION_INPUTS} if kind=='production' else None
    tasks={'qa':['remapRouteQaJar','-I','tools/graphical-streaming-20261019/qa.init.gradle','-x','compileJava','-x','processResources'],'validation':['remapRouteQaJar','-I','tools/graphical-streaming-20261019/qa.init.gradle','-x','compileJava','-x','processResources'],'production':['remapJar'],'verify':['remapVerifyQaJar','-I','tools/graphical-streaming-20261019/verify.init.gradle','-x','compileJava','-x','processResources'],'membership':['runGametest','-I','tools/graphical-streaming-20261019/membership.init.gradle','-Pgametest_run_name=streaming7-membership-scanner','-Pgametest_heap_gb=2','-Pgametest_processor_count=2']}[kind]
    args=[str(PROJECT/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1G',r'-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar',*tasks]
    receipt=OUT/('build-'+kind+'.json');write(receipt,{'status':'RUNNING','args':args})
    with (OUT/('build-'+kind+'.log')).open('w',encoding='utf-8') as log:
        result=subprocess.run(args,cwd=PROJECT,env=dict(os.environ,JAVA_HOME=str(JAVA_HOME)),stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    if sourceHashes is not None:assert sourceHashes=={name:sha(PROJECT/name) for name in PRODUCTION_INPUTS},'Production sources changed during build'
    write(receipt,{'status':'PASS' if result.returncode==0 else 'FAIL','exitCode':result.returncode,'args':args,'sourceHashes':sourceHashes})
    if result.returncode:raise RuntimeError('Build failed: '+kind)
    if kind in ['qa','validation']:
        version=next(line.split('=',1)[1].strip() for line in (PROJECT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
        qa=PROJECT/'build/libs'/('dreamwalker-native-route-qa-'+version+'.jar');assert qa.is_file()
        frozen=OUT/('dreamwalker-native-route-qa.jar' if kind=='qa' else 'dreamwalker-native-tail-validation.jar');assert not frozen.exists();shutil.copyfile(qa,frozen)
import shutil
if __name__=='__main__':
    try:main()
    except BaseException:
        OUT.mkdir(parents=True,exist_ok=True);(OUT/'build-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
