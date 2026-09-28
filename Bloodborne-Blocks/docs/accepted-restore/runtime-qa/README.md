# Dedicated runtime QA — packaged 2.1.0-rc.2

Result: **PASS — official packaged launcher lifecycle**.

The official Fabric server launcher for Minecraft 1.20.1 / Loader 0.16.10 / installer 1.1.2 was launched with Java 17.0.20.1 using `java -Xmx2G -jar fabric-server-launch.jar nogui` from the isolated `packaged-server` directory. It loaded exactly the packaged `bloodborne-blocks-2.1.0-rc.2.jar` and cached Fabric API 0.92.9+1.20.1, bound to `127.0.0.1:25580`, and loaded the 186-file copied city world.

Fresh startup reached `Done (6.989s)`; `save-all flush` and `stop` completed with all chunks/dimensions saved. Restart reached `Done (5.371s)` and repeated save/stop completed. No server process remained. The packaged mod SHA-256 matches `1CE7F8A57DBBEAF6C0D913DAF6E02A4AD1FE63FCEAC78CDD26773DD5615D1F56`.

36 control chunks around the tree, books, window and C474 were force-loaded and
retained after another save/restart. Idle profiling: 429 ticks / 21.06 seconds,
20.37 TPS. This is not player movement or production load. Peak RAM and graphical
client/FPS checks were not captured.

The fresh-start log records skipped external-mod entities (Handcrafted,
Supplementaries, Amendments, Pool Billiards and others) because those mods were
absent. Therefore the full-modpack `DEDICATED_RESTART_PASS` remains BLOCKED;
only packaged Bloodborne/API smoke passed. This QA copy is never the download
artifact. All external-mod data remains unchanged in the published offline copy.

Evidence beside this README: `result.json`, `fresh.log.gz`, `restart.log`,
`supplement-result.json`, `controls-profile.log.gz`, `supplement-restart.log`.
The launcher follows the [official Fabric server instructions](https://fabricmc.net/use/?page=server).
