import subprocess
from common import *
wait_foreign();classes=PROJECT/'build/graphical-streaming-analysis'
with (OUT/'palette-analysis.log').open('w') as log:
 subprocess.run([str(JAVA_HOME/'bin/javac.exe'),'-J-Xmx256m','--release','17','-cp',str(classes),'-d',str(classes),str(TOOLS/'PaletteProfile.java')],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
 for label in ['incomplete-memory-pressure','retention']:
  for side in (['server','client'] if label=='incomplete-memory-pressure' else ['server']):
   src=OUT/label/side/('server.jfr' if side=='server' else 'client.jfr') if label!='retention' else OUT/label/side/'startup.jfr'
   subprocess.run([str(JAVA),'-Xmx512m','-cp',str(classes),'PaletteProfile',str(src),str(src.parent/'palette-profile.json')],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
