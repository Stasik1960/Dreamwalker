# Bloodborne map lighting

The map audit reads saved Java 1.20.1 chunks in the overworld and the historical Bloodborne dimension. It measures skylight and block light separately, including vanilla's implicit skylight in empty sections. Air immediately next to air can differ by at most one level when both cells have valid light data. Sections below the build limit and chunks that have yet to reach the full generation stage are excluded from that test.

The block light audit uses the actual loaded Minecraft/Fabric state luminance table. A local light maximum in a non-emitting block can arise when the brighter neighboring section has stale light data. Native recomputation establishes the new values with block opacity, culling shapes and real emitting states. Invisibility alone gives no basis to remove an authored `minecraft:light` block.

## Verification samples

The source snapshot already had skylight 14 and 13 on the two sides of the chunk boundary at the reported arch. The paired replay preserved both values. At `(-112, 25, -768)` in the overworld, skylight changed from 9 to 14 across an air-to-air face with the adjoining 15. At `(32, 87, 136)` in the old dimension, skylight changed from 0 to 11 across the 12-to-0 boundary.

An apparent block-light maximum of 9 at `(47, 8, -1105)` persisted after native Minecraft recomputation. The DreamWalker lantern at `(51, 8, -1103)` emits 15 and the recomputed neighboring step toward it is 10. That is an illuminated cell with a real nearby source.

The production replay processed 7,158 full chunks in 184 overlapping tiles and emitted one independent cache per chunk. It covered all 5,949 full overworld chunks and all 1,209 full chunks in the historical dimension. The Nether and End in the input contain no saved terrain regions. The other 7,707 chunk records are earlier generation stages and retain their original bytes. The scratch process ran with time 18000 and the game profile's DreamWalker, CustomNPCs, Yuushya, Decocraft and relevant block dependencies.

The ten local block-light maxima initially suspected in the overworld all retained their levels after native propagation from real loaded blocks. Their light comes from lanterns, a torch and explicit invisible light blocks. Source lookup covered 1,513,103 runtime block states and every block ID used by the input. The fresh engine started with empty light storage and could not inherit these old maxima from saved arrays.

The full replay removed the stale sky discontinuities measured in both dimensions: 148,849 adjacent-air pairs in the overworld and 15,927 in the historical dimension fell to zero. All 611 previously unlit full historical chunks received a complete light cache. After the replay both dimensions had zero unresolved block-light maxima and zero unknown states among those inspected.

In the overworld, 35 cached block-light values were reduced and 30 of them returned to zero. At `(217, 54, -1101)`, `(196, 54, -1001)` and `(164, 54, -1057)`, the cache reported block light 12 inside an opaque top slab directly below a real lantern. The current geometry blocks the light path, so the replay recorded 0 inside the slab and retained the lantern. At `(116, 61, -976)` stale skylight 14 and block light 7 both fell to 0 inside the relevant bottom slab. This repair did not alter authored block or entity data.

The dedicated production process emitted and closed all 7,158 cache snapshots. During shutdown it exhausted heap saving the disposable server copy. The maintenance scripts imported the separate snapshots into 17 region files and did not use the scratch save. An independent re-read verified each replacement, the non-light NBT and original timestamps. The hashes of the installed region files matched the verified outputs.

## Maintenance workflow

1. Run `audit_lighting.py WORLD AUDIT.json` on the offline snapshot. It requires Python 3 and NumPy. Optionally pass `--emissions STATE_LUMINANCE.tsv` to classify states using the runtime table.
2. Build the fixture with the project's Gradle wrapper by setting `-p DW-City-Visuals/tools/lighting_maintenance/server`. Run `relight_map.py WORLD SCRATCH --audit AUDIT.json --all-full` with the Java, Fabric, DreamWalker and local dependency paths described by `--help`. SCRATCH must be a new directory.
3. The script launches a muted, localhost-only Fabric server with the copied save. The fixture sets the scratch time to midnight and uses a fresh native lighting engine on overlapping windows. The source save remains read-only. Each target window includes the full-chunk halo needed for lateral propagation.
4. Use `relight_map.py WORLD SCRATCH --transplant OUTPUT` to write verified replacement region files. The transplant copies only sky and block light arrays and light markers. Block states, block entities, heightmaps, tags and original timestamps are checked against the input.
5. Run `verify_relight.py WORLD OUTPUT SCRATCH --report VERIFY.json` for an independent round-trip check. Any untargeted chunk must retain its original compressed record, and every targeted chunk must retain all authored NBT. Verify the overlay with `audit_lighting.py WORLD AFTER.json --patch OUTPUT --emissions SCRATCH/registry.tsv` before applying the replacements to an offline copy of the same save.
