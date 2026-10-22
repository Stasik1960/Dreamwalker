import json,os,subprocess,sys,time
from pathlib import Path
project=Path(__file__).absolute().parents[2]
folder=Path(__file__).parent
kind=sys.argv[1]
env=dict(os.environ,JAVA_HOME=r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma')
args=[str(project/'gradlew.bat'),'--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1G',r'-Pworldedit_api_jar=C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\v127-dependencies\worldedit-mod-7.2.15.jar']
if kind=='qa':args+=['remapStreamingQaJar','-I','tools/streaming-6-20261019/qa.init.gradle','-x','compileJava','-x','processResources']
elif kind=='production':args+=['remapJar','check']
elif kind=='tests':args+=['runGametest','-I','tools/streaming-6-20261019/tests.init.gradle','-Pgametest_run_name=streaming6-membership','-Pgametest_heap_gb=2','-Pgametest_processor_count=2']
elif kind=='reopen':args+=['remapReopenQaJar','-I','tools/streaming-6-20261019/reopen.init.gradle','-x','compileJava','-x','processResources']
else:raise ValueError(kind)
receipt=folder/('build-'+kind+'-python.json')
receipt.write_text(json.dumps({'status':'STARTING','args':args}))
with (folder/('build-'+kind+'-python.log')).open('w') as log:
    result=subprocess.run(args,cwd=project,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
receipt.write_text(json.dumps({'status':'PASS' if result.returncode==0 else 'FAIL','exitCode':result.returncode,'args':args},indent=2))
sys.exit(result.returncode)
