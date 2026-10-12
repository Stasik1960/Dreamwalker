# Opt-in V12.7 native function proof

QA source is `src/v127qa/java`; production has no dependency on it. The add-on
uses the historical runner-allowed id `dreamwalker_gallery_author` solely for
runner compatibility. It creates no catalogue/gallery. Its opt-in marker must
bind the current production JAR SHA, set `freshTestWorldOnly` and
`v127NativeFunctions`; the world must be a fresh ordinary project-build test world.
The fixture is a small stone floor with six function stations, one banked compact
conversion palette example and one authored grass compact example. It never touches the original map, user profile or
release gallery.

Root must serialize all Java/Gradle/client/server processes through Guard. Build:

```text
tools/run_v124_stage.py --name v127-native-addon tools/build_v127_qa_addon.py --production-jar build/libs/<current-v127-jar>.jar --output build/v127-native-addon
```

Create an ordinary fresh flat world with production only using the existing
`run_v11_server.py` and an existing accepted EULA file. Its normal startup/save/stop
creates `build/runtime-server-<name>/isolated-smoke-world`; no authoring add-on is
used. Then `prepare_v127_native_inputs.py` verifies the flat generator, rejects any
gallery manifest, copies without following links, enables creative/cheats only in
the new copy, binds the production SHA, and verifies the source remains unchanged:

```text
tools/prepare_v127_native_inputs.py --production-jar build/libs/<jar>.jar --fixture-source build/runtime-server-<fresh-name>/isolated-smoke-world --output build/v127-native-input
tools/run_v11_client.py --jar build/libs/<jar>.jar --fixture build/v127-native-input/world --run-name v127-functions --report build/v127-native-client-launch.json --username V127Functions --qa-addon build/libs/dreamwalker-v127-native-functions-qa-1.2.7.jar --capture-input build/v127-native-input/client-capture-input.json --quick-play --launch
```

Use cached Java17 and no source resourcepack. All commands remain serialized
through Guard. Do not copy the old gallery or original map.

The client sends actual creative-slot and interactBlock packets to hand-place:
original wall, original freestanding ladder, new wooden barrel, RP curtain,
DW-barrier and DW-light. It verifies actual synchronized blocks/entities, executes
the real `/bb alt all` command, polls the integrated server job on the server thread
until completion, and preserves server owner UUID acknowledgements.

17 expected actual framebuffer PNGs: wall/ladder/new barrel/RP/banked compact/authored grass BASE and
ALT pairs, yellow DW-barrier held, red vanilla barrier held, empty-hand hidden
DW-barrier, DW-light held marker, and empty-hand invisible light14 at night.
The fixture and client fail if either compact palette example is missing; a reduced
frame count cannot satisfy this proof.
`v127-native-client-qa.json` binds operations and camera coordinates to production
SHA and original PNG hashes. Native wall/ladder baked ABGR colours are recorded.
Light14 is read from the actual synchronized client light storage.
The grass frames additionally compare the actual registered block and item color
callbacks of all three authored grass artworks with the owner renderer's v15
palette on the real biome position and inventory sample. Native model tint-index0
quads and the untouched vanilla grass color provider are checked in the client.

The fresh run additionally saves explicit tool-command fixtures on the barrel,
DW-barrier and DW-light, including typed personal/global use counters. After all
frames, the real integrated server records native owner/RP UUIDs, every fixture's
registered state/profile, exact command SNBT and light14 before normal shutdown.
Heap/cache counts are observed before/after `all` and before shutdown, without
forced GC or synthetic estimates.

`PASS_ENGINE_CAPTURE` is not a pixel-review or reopen claim. Root must inspect all
raw frames with view_image and wait for normal client shutdown/save. Prepare a
second fresh evidence directory from that saved function world:

```text
tools/prepare_v127_native_inputs.py --production-jar build/libs/<jar>.jar --fixture-source build/runtime-client-v127-functions/saves/prototype-fixture --output build/v127-native-reopen-input --previous-evidence build/runtime-client-v127-functions/v127-native-client-qa.json
tools/run_v11_client.py --jar build/libs/<jar>.jar --fixture build/v127-native-reopen-input/world --run-name v127-functions-reopen --report build/v127-native-reopen-launch.json --username V127Functions --qa-addon build/libs/dreamwalker-v127-native-functions-qa-1.2.7.jar --capture-input build/v127-native-reopen-input/client-capture-input.json --quick-play --launch
```

The marker adds `v127ReopenOnly`; the add-on does not place objects, seed commands,
or run `all` again. It records existing ALT/marker frames, actual reopened heap,
then requires exact equality of UUIDs, states, RP profile, light and all command
limits/counters. Only that result is `PASS_ENGINE_REOPEN`. A production-only native
server/client reopen remains a separate compatibility check. Physical keyboard
and mouse input remains NOT_RUN; client interaction manager and normal packets
are the evidence for hand placement.

The separate cold-map GameTest writes an actual RP object through Minecraft's
entity-region storage into an unloaded, entity-only chunk, then exercises the
same asynchronous chunk/edit path as `all`. The profile job waits for both the
block FULL future and native entity-manager readiness before capturing the RP
UUID cursor. It must change the saved object exactly once and preserve its UUID,
position, rotation, scale, name and typed RP/source NBT. This check has its own
native `v127-cold-rp-alt-proof.json`; compilation is not a PASS for that check.
