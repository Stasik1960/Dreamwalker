# Opt-in V12.7 native function proof

QA source is `src/v127qa/java`; production has no dependency on it. The add-on
uses the historical runner-allowed id `dreamwalker_gallery_author` solely for
runner compatibility. It creates no catalogue/gallery. Its opt-in marker must
bind the current production JAR SHA, set `freshTestWorldOnly` and
`v127NativeFunctions`; the world must be a fresh ordinary project-build test world.
The fixture is a small stone floor with six function stations and one compact
conversion palette example. It never touches the original map, user profile or
release gallery.

Root must serialize all Java/Gradle/client/server processes through Guard. Build:

```text
tools/run_v124_stage.py --name v127-native-addon tools/build_v127_qa_addon.py --production-jar build/libs/<current-v127-jar>.jar --output build/v127-native-addon
```

Launch with the existing `run_v11_client.py`, isolated fresh flat world, cached
Java17, `--quick-play --launch`, this add-on, generated `--capture-input`, independent
run name, username and no source resourcepack. Its standard server fixture should
be created fresh with cheats enabled; do not copy the old gallery or original map.

The client sends actual creative-slot and interactBlock packets to hand-place:
original wall, original freestanding ladder, new wooden barrel, RP curtain,
DW-barrier and DW-light. It verifies actual synchronized blocks/entities, executes
the real `/bb alt all` command, polls the integrated server job on the server thread
until completion, and preserves server owner UUID acknowledgements.

15 expected actual framebuffer PNGs: wall/ladder/new barrel/RP/compact BASE and
ALT pairs, yellow DW-barrier held, red vanilla barrier held, empty-hand hidden
DW-barrier, DW-light held marker, and empty-hand invisible light14 at night.
`v127-native-client-qa.json` binds operations and camera coordinates to production
SHA and original PNG hashes. Native wall/ladder baked ABGR colours are recorded.
Light14 is read from the actual synchronized client light storage.

`PASS_ENGINE_CAPTURE` is not a pixel-review or reopen claim. Root must inspect all
raw frames with view_image, record observations and original SHA, wait for normal
client shutdown/save, then open and save the resulting world once more using the
production JAR without this QA add-on. Compare UUID, IDs, states, RP BbAltVisual and
light data on reopen. Physical mouse/keyboard input remains NOT_RUN; interaction
manager and normal network handling are the evidence for hand placement.
