"""Read local mapped Minecraft APIs; no game/build/world operation."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[3]
JAVAP=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\javap.exe')
JAR=Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar')
names=['net.minecraft.server.world.ServerLightingProvider','net.minecraft.server.world.ServerChunkManager','net.minecraft.world.chunk.Chunk','net.minecraft.world.Heightmap','net.minecraft.server.MinecraftServer','net.minecraft.server.world.ChunkTicketType']
with (ROOT/'research'/'native-relight-api.txt').open('w',encoding='utf-8') as stream:
    for name in names:
        result=subprocess.run([str(JAVAP),'-classpath',str(JAR),'-private',name],capture_output=True,text=True)
        stream.write(result.stdout+result.stderr+'\n')
with (ROOT/'research'/'native-relight-bytecode.txt').open('w',encoding='utf-8') as stream:
    result=subprocess.run([str(JAVAP),'-classpath',str(JAR),'-private','-c','net.minecraft.server.world.ServerLightingProvider'],capture_output=True,text=True)
    stream.write(result.stdout+result.stderr)
