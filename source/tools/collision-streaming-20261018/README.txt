Matched collision/streaming QA, 2026-10-18

All workstation process launches use C:\Users\vakir\CodexControl\Guard.ps1
-Mode Operation; read the storage mode/result and run -Mode Check first.

build_gallery.ps1 builds only the separate QA gallery add-on, explicitly
excluding production compileJava/processResources, and freezes its JAR/hash
under deliverables/collision-streaming-20261018/evidence. Both route cases use
that exact same JAR; it is not part of the production mod.

prepare_route.py requires --case, --jar, --world-source and --gallery-addon.
Use the unchanged verified backup/world as --world-source for BOTH timing runs.
The baseline JAR is deliverables/streaming-dh-20261018/game/
dreamwalker-bb-streaming.4.jar, SHA256
cda709e0ac540b80d3781770e297ba6ef56b2f73ef661077aa850149d2c1fe60.
It creates a fresh independent runtime/save, reuses immutable installed native
libraries/assets, and retains the Java17/-Xmx10G/1280x720/rd12/sd12/fps260
profile and exact options. JFR profile is equal; filenames differ per runtime.
Source authoring-history folders/session.lock are excluded from active copies.
The existing camera pumpkin removal is applied identically to both copies.

run_route.py --case shapes-before (or shapes-after) runs one three-district
pass: facade teleport/settle, native 1-block/client-tick travel to tower, then
brick. FULL chunk availability gates movement. Each endpoint requires all
289 exact addressed FULL chunks in center +/-8, an addressed non-air block,
terrain render complete, >=80 settled ticks and 100 stable resident-count ticks.
The QA add-on records accepted ClientChunkEvents.CHUNK_LOAD/CHUNK_UNLOAD counts
and unique deliveries within each phase; these are distinct from resident
chunk counts and must be reported separately. The existing flipFrame clock
records every presentation interval; one finite sample cap is checked before
comparison. Startup and final save are outside performance phase timing.

summarize_route.py --before shapes-before --after shapes-after verifies Java,
command, QA JAR, active world manifests, options and equal endpoint deliveries.
It reports route time, pause counts/duration, process RAM/commit, heap snapshots
and delivery denominators. One pair gives an observation, not confidence bounds.
OS file cache is not flushed and baseline runs first. JFR contains startup;
phase nanosecond bounds plus sampled memory UTC/capture timestamps help isolate
the route (UTC alignment is approximate at the ~2-second monitor interval).

prepare_reopen.py --source-case shapes-after --case shapes-reopen requires the
after client to have saved/exited normally, copies those saved bytes to a fresh
runtime and prepares only the final brick view. Run:
run_route.py --case shapes-reopen --expected-frames 1
This is an actual new-process save/reopen check, not a repeated performance run.

Confirmed barrier edits are made to deliverables/.../map/world separately.
The performance pair deliberately retains equal original backup maps to
isolate code changes from geometry removal. No conversion, static migration,
DH/LOD, lighting, texture or render rewrite is part of these helpers.
