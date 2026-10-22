$ErrorActionPreference='Stop'
$taskBin='C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin'
$taskMc='C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar'
& (Join-Path $taskBin 'javap.exe') -p -classpath $taskMc net.minecraft.network.NetworkState net.minecraft.network.packet.s2c.play.ChunkUnloadS2CPacket net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket net.minecraft.server.MinecraftServer *> (Join-Path $PSScriptRoot 'api2.txt')
