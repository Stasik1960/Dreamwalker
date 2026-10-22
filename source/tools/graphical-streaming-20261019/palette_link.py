import json,subprocess,shutil
from common import *
wait_foreign();p=json.loads((OUT/'after-startup-link-failure/prepared.json').read_text());cp=p['serverArgs'][p['serverArgs'].index('-cp')+1];classes=PROJECT/'build/palette-link-probe';classes.mkdir(exist_ok=True)
candidate=json.loads((OUT/'candidate.json').read_text());jar=Path(candidate['jar']);assert sha(jar)==candidate['sha256'];shutil.copyfile(jar,SERVER/'mods'/jar.name)
with (OUT/'palette-link-compile.log').open('w') as log:subprocess.run([str(JAVA_HOME/'bin/javac.exe'),'-J-Xmx256m','--release','17','-cp',cp,'-d',str(classes),str(TOOLS/'PaletteLinkProbe.java')],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
with (OUT/'palette-link.log').open('w') as log:r=subprocess.run([str(JAVA),'-Xmx1G','-cp',str(classes)+';'+cp,'PaletteLinkProbe'],cwd=SERVER,stdout=log,stderr=subprocess.STDOUT,timeout=90,creationflags=subprocess.CREATE_NO_WINDOW)
write(OUT/'palette-link.json',{'exitCode':r.returncode})
