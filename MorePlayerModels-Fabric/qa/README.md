# MPM protocol QA

The decoder fixture runs the original ping and update encoders and the Minecraft 1.20.1 decoder using synthetic profiles. The client fixture uses the patched JAR's actual sender through a test adapter for Fabric channel discovery. The socket fixture starts an isolated localhost Fabric server and verifies a large profile update, reconnection, the returned profile and the server's saved profile bytes.

Run from PowerShell with JDK 21, `JAVA_HOME` set, and a Loom cache containing Minecraft 1.20.1 intermediary and the extracted server. Pass a local Minecraft 1.20.1 client directory with Fabric API and CPM in `mods` and Mojang/Fabric runtime dependencies in `libraries`. The socket test requires Python 3.8+ on PATH. Use `-JdkHome`, `-LoomCache` and `-Python` for other locations. Pass `-FabricLoaderJar` if the client doesn't keep the Fabric Loader 0.19.3 JAR at its root as `0.19.3.jar`.

~~~powershell
.\RunProtocolQa.ps1 -Mode decoder -MpmJar $legacyJar -ClientDir $clientDir
.\RunProtocolQa.ps1 -Mode socket -MpmJar $legacyJar -ClientDir $clientDir -Baseline
.\RunProtocolQa.ps1 -Mode client -MpmJar $patchedJar -ClientDir $clientDir
.\RunProtocolQa.ps1 -Mode socket -MpmJar $patchedJar -ClientDir $clientDir
.\RunProtocolQa.ps1 -Mode socket -MpmJar $patchedJar -ClientDir $clientDir -PacketLimit
~~~

Socket mode uses a generated test world under `build/socket`, attaches an offline test peer on a free loopback port, and stops the dedicated server at the end. The resulting console log and world remain available in its per-run directory. No graphical client is needed. `-PacketLimit` tests a payload of exactly 245760 bytes for both ping and update.