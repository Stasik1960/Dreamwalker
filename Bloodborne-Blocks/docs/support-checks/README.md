# Completed QA — support normalization, 2026-09-24

Pinned environment: Java 17.0.20.1, project Gradle wrapper 8.8, Minecraft 1.20.1,
Yarn/Fabric versions unchanged. Only Contract V2 support/collision normalization
and its verification are in scope. No city conversion or original-world edits.

## Commands and results

Executed from `Bloodborne-Blocks`, with `JAVA_HOME=build/toolchain/jdk-17.0.20.1`,
`BLOODBORNE_VANILLA_JAR` pointing to the cached 1.20.1 client JAR and the installed
Python interpreter supplied through `-PbloodbornePython`:

```powershell
.\gradlew.bat -I tools/verification-direct-resources.init.gradle '-PbloodbornePython=<python executable>' '-PaltStarterKitZip=../releases/Bloodborne-Blocks/Nightmare-QA-ALT/Bloodborne-Alt-Visual-Starter-Kit.zip' check build --console=plain
.\gradlew.bat -I tools/verification-direct-resources.init.gradle '-PbloodbornePython=<python executable>' logicalGameTest --console=plain
python -B tools/check_mixin_package.py build/libs/bloodborne-blocks-2.1.0-alpha.1.jar
python -B tools/record_support_release.py
```

- Final `check build`: **BUILD SUCCESSFUL**, 4m 39s. All **106 Python unit tests**
  in 21 unittest modules, the direct logical-world suite, and the registered
  data/Java checks passed. Unchanged compiled/archive tasks were reused.
- `logicalGameTest`: **BUILD SUCCESSFUL**, **28/28** required tests. Headless
  dedicated test server stopped normally. Three new support tests cover all
  700 FLOOR states, visible-family manual placement in every facing, and
  persisted obsolete-helper cleanup without deleting a foreign support block.
- Packaged mixin/refmap check passed.
- Release recording compares every packaged source resource byte-for-byte
  (except documented Loom mixin JSON augmentation), compiled class names,
  generated refmap and ZIP CRC. See [release-verification.json](release-verification.json).
- Additional temporary-world tests added after the combined build passed the
  direct `python -B tools/test_logical_world.py` run. They exercise real reused
  helper discovery, converter refusal plus independent verification, and both
  three-candidate conflict cases. Production code/JAR was unchanged afterward.

The optional existing init script avoids copying ~195k resource files; it does
not filter or replace packaged assets. New support transforms remain prepared
data; runtime does not perform mesh analysis per tick/frame.

## Earlier failures, resolved before delivery

- The first new delayed-helper GameTest shared a batch with fixtures that clear
  overlapping coordinates. It now runs in its own batch; an immediate platform
  assertion distinguishes setup interference from delayed validation failures.
- The QA2 tree generator returned noncanonical JSON bytes after its visual-slot
  pass. Its public entry point now invokes the same support finalizer; the
  existing byte-idempotence assertion was retained and passed.
- New prospective fallback helpers prevented an explicitly approved C003
  assembly match. Supersession now distinguishes actual existing source/owned
  helpers from discarded future writes. No source patterns or foreign-block
  protections were changed; converter and independent verifier agree. Narrow
  tests and the final full `check build` passed. See the implementation record
  in [SUPPORT-NORMALIZATION.md](../SUPPORT-NORMALIZATION.md).

Failed attempts are not counted as successful checks.

## Saved evidence

- [Normalization audit](../support-normalization-audit.json): all 47 families /
  740 states, 13 corrected below-floor families, four mount ambiguities,
  1020 -> 1020 global collision primitives, ordinary maximum two, zero failures.
- [GameTest XML](TEST-logical-gametest.xml): 28 passing tests.
- [Orientation](contract-orientation.json): 2960 rotation checks.
- [Original-source examples](reviewed-batch-02-source-examples.json): 20 passed,
  **eight SAFE_BLOCKED**, not misreported as successful conversions.
- [Tree cases](qa2-trees.json): 18 passing complete-source cases.
- [Gallery package](gallery-package.json): 47 families, 746 masters, 3144 helpers,
  704 FLOOR specimens and 7778 full white support cells checked.
- [Official LevelStorage](support-level-summary.txt): the fresh gallery appears
  in vanilla 1.20.1 `getLevelList()` / `loadSummaries()`, accepted with
  `version=19133`, `DataVersion=Version.Id=3465`. Reproduce with:

```powershell
.\gradlew.bat -I tools/verification-direct-resources.init.gradle -I tools/level-metadata-check.init.gradle '-PbloodbornePython=<python executable>' '-PlevelMetadataSaves=build/support-gallery-saves' '-PlevelMetadataExpectedValid=support-plane-gallery-20260924' '-PlevelMetadataExpectedStable=support-plane-gallery-20260924' '-PlevelMetadataReport=build/test-results/support-level-summary.txt' levelMetadataCheck
```

The actual Java ALT resolver also passed for all 1610 starter models and 2123
texture paths. Source resource/registry audits passed (298 logical definitions,
3316 states); the remaining non-V2 families were not normalized by this pass.

## Limits

No actual client model bake/resource reload, GUI screenshot or visual gameplay
acceptance was performed. Inspect the delivered gallery in Minecraft before
accepting its appearance. The four wall mount ambiguities remain unchanged;
the functional source-height door `o_c282_b` retains its documented volume
warning. There was no batch-03 or full-city conversion. Old support blocks
already replaced by previous helpers cannot be reconstructed automatically.
