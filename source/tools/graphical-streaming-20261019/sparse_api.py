import subprocess
from common import *
wait_foreign();jar=Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar')
with (OUT/'membership-fix/collector-api.txt').open('w') as log:
 subprocess.run([str(JAVA_HOME/'bin/javap.exe'),'-c','-classpath',str(jar),'net.minecraft.nbt.scanner.NbtCollector','net.minecraft.nbt.scanner.NbtScanner'],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
