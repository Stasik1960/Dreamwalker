import shutil,subprocess,sys
from common import *
wait_foreign();case=OUT/sys.argv[1];p=json.loads((case/'prepared.json').read_text())
for side in ['client','server']:
 target=case/side/(side+'-partial.jfr')
 if target.exists() and target.stat().st_size>0:continue
 candidates=list(Path(os.environ['TEMP']).glob('*_'+str(p[side+'Pid'])))
 if not candidates:continue
 source=max(candidates,key=lambda f:f.stat().st_mtime);repository=case/side/'recovered-repository';repository.mkdir(exist_ok=False)
 for f in ordinary(source).glob('*.jfr'):shutil.copyfile(ordinary(f),repository/f.name)
 with (case/side/'recovery.log').open('w') as log:
  result=subprocess.run([str(JAVA_HOME/'bin/jfr.exe'),'assemble',str(repository),str(target)],env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xmx512m'),stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 assert result.returncode==0
