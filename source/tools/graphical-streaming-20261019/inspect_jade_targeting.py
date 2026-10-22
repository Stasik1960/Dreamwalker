"""Read the installed Jade bytecode; no Minecraft launch or world changes."""
import hashlib,json,subprocess
from pathlib import Path

project=Path(__file__).absolute().parents[2]
out=project.parent/'research/jade-targeting-20261020'
out.mkdir(exist_ok=True)
jar=Path(r'C:\Users\vakir\Desktop\Dreamwalker-RP-Test-20261019\client-mods\Jade-1.20-Fabric-11.13.3.jar')
javap=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\javap.exe')
before=hashlib.sha256(jar.read_bytes()).hexdigest()
with (out/'jade-raytracing.javap.txt').open('w',encoding='utf-8') as log:
 result=subprocess.run([str(javap),'-J-Xmx128M','-c','-p','-classpath',str(jar),'snownee.jade.overlay.RayTracing'],stdout=log,stderr=subprocess.STDOUT,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
assert result.returncode==0
assert hashlib.sha256(jar.read_bytes()).hexdigest()==before
(out/'receipt.json').write_text(json.dumps({'status':'READ_ONLY_BYTECODE_INSPECTION','jade':str(jar),'sha256':before,'minecraftLaunched':False,'worldChanged':False},indent=2)+'\n',encoding='utf-8')
