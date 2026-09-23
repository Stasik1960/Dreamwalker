# Gallery metadata QA — 2026-09-23

Scope: fix the Singleplayer-list blocker only. No logical family, Contract V2,
runtime resource, JAR, city, or batch-02 migration-result changes.

## Proven cause

The previous synthetic `write_fixture` omitted `Data.version` (lowercase).
Minecraft 1.20.1's `LevelStorage` summary parser accepts storage versions 19132
and 19133; an absent field yields no summary. The current Anvil format is 19133.
This is distinct from `DataVersion=3465` and `Data.Version.Id=3465`, which were
already present in the rejected gallery.

The original metadata was compared with a real client-created 1.20.1 save and
an engine-written disposable Fabric GameTest save. LastPlayed, packs, features,
weather and border defaults were also missing from the synthetic metadata.
These are not all independent hard gates for summary loading: some have vanilla
fallbacks. The new validator checks our explicit generator policy, not a claimed
universal minimum save schema.

## Template and generator

- `tools/templates/level-1.20.1.dat` derives from the existing engine-written
  GameTest save using `tools/capture_fixture_level_template.py`.
- The capture allowlist retains normal metadata and vanilla worldgen structure;
  it excludes player data, UUIDs, entities, boss/event state and mod provenance.
- Packs/features are normalized to vanilla only, with no experimental bundle
  flag. The world remains creative, command-enabled, seed 0, bedrock-only flat.
- `tools/templates/level-1.20.1.provenance.json` records source/template hashes
  and normalization. The runtime generator needs only the checked-in template,
  not the source GameTest world or a user's save.
- `tools/fixture_level_metadata.py` loads a fresh template, sets gallery name,
  spawn, time, LastPlayed and gamerules, then validates typed NBT metadata.

## Checks actually performed

1. `python -B -X utf8 -m unittest test_fixture_level_metadata` from `tools/`:
   **5 tests passed** (including missing tags, storage/data-version confusion,
   external packs/experimental flags, immutable template, gzip writer).
2. Narrow existing migration regression tests: **2 tests passed**, including
   independent conversion and idempotence of every proven reviewed pattern.
   Existing batch-02 result files were not regenerated.
3. Unmodified Minecraft 1.20.1 classes loaded metadata copies through
   `LevelStorage.create(saves).getLevelList()` → `loadSummaries(levelList)`:

   | Metadata | Data.version | Official summary |
   | --- | --- | --- |
   | Old gallery | absent | omitted |
   | New gallery | 19133 | present, available, compatible, nonexperimental |
   | Engine-written control | 19133 | present, available, compatible |

   A separate valid-template control with only `Data.version` removed was also
   omitted. This isolates the missing storage-format field as the list blocker.

Reproduce the official summary check from `Bloodborne-Blocks/` with metadata
copies in the indicated scratch saves folder:

```powershell
$env:JAVA_HOME=(Resolve-Path 'build/toolchain/jdk-17.0.20.1').Path
.\gradlew.bat -I tools/verification-direct-resources.init.gradle `
  -I tools/level-metadata-check.init.gradle levelMetadataCheck `
  -PlevelMetadataSaves=build/gallery-metadata-qa-20260923/saves `
  -PlevelMetadataExpectedValid=fixed-gallery,engine-control `
  -PlevelMetadataExpectedInvalid=old-gallery `
  -PlevelMetadataExpectedStable=fixed-gallery `
  -PlevelMetadataReport=build/gallery-metadata-qa-20260923/level-summary.txt
```

The isolated check compiles its test harness only; it does not build the mod JAR.
Its report is `build/gallery-metadata-qa-20260923/level-summary.txt`.
**Limit:** this verifies the official Singleplayer summary-loading route, not
the client GUI or entry/gameplay. Summary loading skips Player/WorldGenSettings,
so this is not an assertion that the full worldgen codec was exercised.

## New deliverable

- Folder: `build/reviewed-batch-02-gallery-metadata-fixed/`.
- ZIP: `build/reviewed-batch-02-gallery-metadata-fixed.zip` (107388 bytes).
- ZIP SHA-256: `3acc1760533882bb65b7980997f6f0da1f41828b640ac536f52ede24998add55`.
- Display name: `Bloodborne batch-02 gallery (metadata fixed)`.
- The ZIP contains one world folder, with `level.dat` directly at its root.
  All five archived files were hash-compared with the generated folder.
- Compared with `reviewed-batch-02-gallery-ready`, **only `level.dat` differs**.
  The two region files, all 100 specimens (25 families × four facings), positions
  manifest and gallery README remain byte-identical. The old folder is preserved.
- Existing checkpoint JAR SHA-256 remains
  `51041bb4c6148206d0fdb18146fb5f05e82caa76b899a7c4c1f54194a0d8a6f2`.

Extract the new world folder into the actual Minecraft `saves` directory and
use the existing batch-02 checkpoint Fabric 1.20.1 installation/JAR. No files in
the real Minecraft saves directory were modified by this fix or its tests.
