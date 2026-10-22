import subprocess
from common import *
case=OUT/'normal-save';p=json.loads((case/'receipt.json').read_text())
assert p['status']=='RUNNING'
with (case/'heap-diagnostic.txt').open('w') as log:
 for args in [['GC.heap_info'],['Thread.print','-l'],['GC.class_histogram','-all']]:
  try:subprocess.run([str(JAVA_HOME/'bin/jcmd.exe'),str(p['pid']),*args],stdout=log,stderr=subprocess.STDOUT,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
  except subprocess.TimeoutExpired:break
