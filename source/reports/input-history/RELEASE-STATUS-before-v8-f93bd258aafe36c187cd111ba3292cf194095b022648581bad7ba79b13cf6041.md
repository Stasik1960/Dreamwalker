# NOT_READY - first set awaiting visual/game acceptance

`dreamwalker-bb-fabric-1.20.1`, version `0.1.0-prototype.2`, contains the full supplied RP and first architectural set: double door, wood/thin windows, eight-yaw ladder, wall, 18-part tree and independent roof. Roof/window demonstrate unrelated objects encoded by the same original jungle-stairs carrier. Full catalog/city conversion remains unfinished.

Current production SHA256: `1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`. Attempt14 passes 61/61 Fabric GameTests, 19 core checks and build/remap. Actual V7 minimal, reopened and full selected 109-mod clients pass world/resource/save/normal-exit checks; the selected 83-mod dedicated server passes. Minimal client uses 4 GB: 52 composite source models, 192 ladder states, 512 representative wall states, vanilla stone, 10 inventory models and 68 scene roots checked without missing models/sprites. The wall has 32768 states with 40 shared primitive IDs, 160 native bakes and 4608 appearances; all six native rerender checks pass. Independent all-state selector/rotation/UV/weight equivalence has zero mismatches. Historical OOM and parent-link failures remain preserved.

RP audit passes 710 resource checks, 306 catalog entries and 293 animation clips; all 707 supplied RP files remain unchanged. Optional QA is absent from production. Simultaneous standalone RP installation is correctly rejected.

New-build V7 authoring and production-only reopening pass with exit0; independent actual ordinary-client persistence also passes (CLIENT_SCENE_PERSISTENCE_V7.json): 68 root states, 12 composite identities, 448 cached composite BEs, owner ledger and native chest NBT persist. `FIRST_SET_REVIEW_SCENE_V7.json` packages `First-set-new-placement-scene-v7.zip`, SHA256 `30b811289a02ec162b4137e5b89691c12539addca44059377dde8cff1af156db`.

Bounded source V7 author, production-only reopen and persisted no-op replay pass with exit0: 41 objects / 110 recognized source cells, including 34 two-cell ladders. Independent disk comparisons record 460 declared changes, 0 unplanned native/foreign-BE changes, both light9 cells and typed legacy data for 15 RP entities retained. Relative to preparation, 1157 first-save and 1587 reopened metadata/DFU/tick differences plus 4363 extra game-created chunks are listed separately. Second save compares 4434 chunks/472 BEs with 0 logical-state/typed-BE changes, preserving 41 roots and 15 envelopes. Original world bytes/live entity NBT are not claimed unchanged. `FIRST_SET_SOURCE_REVIEW_SCENE_V7.json` verifies `First-set-migrated-source-fixture.zip`, SHA256 `daae8e9c8fd51f612b63fe6076af1e6a59874b4acc06afdfef9f202c071ba228`, including all saved/generated terrain.

`SOURCE_LAMP_BLOCK_AUDIT.json` proves exactly `[228,69,-932]` and `[229,69,-931]`: original invisible AirBlock, light9, empty collision/outline/fluid, replaceable, isAir=false, no item/BE. Bounded mapping preserves source state/coordinate provenance and target semantics. Actual original client, Attempt14 native and independent saved V7 checks pass. Unsupported IDs fail preparation before loading.

Original Forge 1.18.2 swept reference contains 165 observed shapes and 7 FakePlayer movements. Initial source-ladder climbing/fixed backing matches the original. New door/open behavior, fixed-center window and narrow/passable tree physics remain proposals pending user review. Manual graphics/gameplay, Creative UI, two clients, RP gameplay and user acceptance remain NOT_RUN/NOT_ACCEPTED.

ALT actual texture-and-model replacement passes: 32 ALT0 states use red_wool, and 32 ALT1 poses exactly match corresponding BASE2 baked fingerprints and differ from BASE1; BASE stays unchanged. `ALT_RUNTIME_V7.json` records the loaded pack/priority and native observations. Active shader technical profile also passes: Iris 1.7.6/Kappa_v5.2 on 109 selected mods, actual IrisRenderingPipeline/no fallback/valid shaderMap/rendering enabled, frame counter 0→25, all 42 provided settings read back through actual getters, normal save/exit0. Independent SHADER_RUNTIME_INDEPENDENT_V7.json records source/staged hashes and game-normalized config bytes. No matched-camera artistic result or manual visual PASS is inferred from pipeline, resources or fingerprints.

Release conditions remain open:

- TASK section 8 requires technical checks **and user visual/game acceptance of disputed first-set cases** before mass generation/city conversion. That gate remains open.
- Used source missing models/surfaces require a reconstruction/exclusion decision; no choice is approved.
- Semantic catalog membership/global frequencies and final five-digit IDs are unfinished.
- Full city/NBT migration, eight unmatched legacy utility stacks, production gallery and gallery-only height configuration are unfinished. Main-world height stays unchanged.
- Manual matched-camera graphics, passage/climb, two-client synchronization, RP forms/levers/lamp travel and representative city performance need separate evidence.

Read `docs/PROTOTYPE_REVIEW.md`, `docs/SOURCE_LADDER_ASSEMBLY.md`, `docs/SOURCE_REVIEW_MIGRATION.md`, current `SERVER_*_V7.json`/`CLIENT_FIRST_SET_*_V7.json` and independent source reports. Prior SHA/results and prototype.1 review/status files remain historical. Original inputs and the user's working game remain unchanged.
