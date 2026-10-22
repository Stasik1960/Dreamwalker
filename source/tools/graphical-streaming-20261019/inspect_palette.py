import subprocess
from common import *
wait_foreign()
with (OUT/'lithium-palette-bytecode.txt').open('w') as log:
 subprocess.run([str(JAVA_HOME/'bin/javap.exe'),'-p','-c','-cp',str(SERVER/'mods/lithium-fabric-mc1.20.1-0.11.4.jar'),'me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette'],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
