# Final automated verification — NightmareRunning QA + ALT

Executed on Windows, 2026-09-23 (delivery finalized 2026-09-24), using the project's
pinned Java 17.0.20.1, Gradle wrapper 8.8, Minecraft 1.20.1 and Fabric dependencies.
No original world was modified and no full-city conversion was run.

## Commands actually completed

From `Bloodborne-Blocks`, with `JAVA_HOME` pointing to the local Java 17 toolchain,
`BLOODBORNE_VANILLA_JAR` pointing to the cached 1.20.1 client JAR, and the configured
Python interpreter selected with `-PbloodbornePython=<python executable>`:

```powershell
.\gradlew.bat -I tools/verification-direct-resources.init.gradle '-PbloodbornePython=<python executable>' '-PaltStarterKitZip=../releases/Bloodborne-Blocks/Nightmare-QA-ALT/Bloodborne-Alt-Visual-Starter-Kit.zip' check compileGametestJava
.\gradlew.bat -I tools/verification-direct-resources.init.gradle logicalGameTest
.\gradlew.bat -I tools/verification-direct-resources.init.gradle build -x check
```

- `check compileGametestJava`: **BUILD SUCCESSFUL**; 77 Python unit tests
  (19 unittest modules), plus the direct logical-world check, orientation/resource
  audits and Java geometry/modular/logical/registry/Contract V2/model-resolver checks.
- `logicalGameTest`: **BUILD SUCCESSFUL**, 25/25 required tests passed; the
  headless dedicated test server stopped normally.
- `build -x check`: **BUILD SUCCESSFUL**, using the accumulated remapped JAR.
  `check` had already passed independently and also passed during the earlier
  combined build. This command did not repack the unchanged runtime JAR.
- Earlier combined `build logicalGameTest` finished the JAR but returned
  **BUILD FAILED** on a pre-existing test's out-of-tracked-chunk item query.
  The final test-only isolation fix and successful rerun are documented in
  [test-isolation.md](test-isolation.md). No failed run is counted as a pass.

The optional init script points checks/build at source resources without copying
~195k files. It does not exclude or transform the packaged assets.

## Evidence and scope

- [GameTest XML](TEST-logical-gametest.xml): 25 tests, including actual region
  command dispatch, helper ownership/NBT preservation, old states without visual,
  independent doors, lamp interaction and window collision.
- [Orientation](contract-orientation.json): 2960 checks, canonical roots and
  per-facing shape/footprint consistency.
- [Source examples](reviewed-batch-02-source-examples.json): 20 converted,
  **8 SAFE_BLOCKED**, never counted as successful migrations. Refusals preserve
  the complete fixture tree byte-for-byte.
- [Complete trees](qa2-trees.json): 18 passed source cases.
- [Vanilla LevelStorage](nightmare-level-summary.txt): official 1.20.1 world-list
  and summary loader accepts the fresh gallery; Data.version=19133,
  DataVersion=Version.Id=3465. Not a Singleplayer GUI screenshot.
- [Gallery package](gallery-package.json): 286 families, 1160 specimens,
  12125 owned helpers, six BASE/ALT proof specimens, validated metadata and ZIP CRC.
- The actual Java ALT resolver validated all 1610 exported models and 2123
  texture paths from the release starter ZIP; XYZ/UV matches post-fix BASE.
  Independent review also compared exported PNG bytes and animation metadata
  to their manifest sources. Registry/model/resource audits passed.
- The final runtime JAR passed the packaged mixin check.
- [Release verification](release-verification.json): byte-for-byte comparison
  of every packaged source resource (only Loom's expected mixin metadata
  augmentation allowed), expected production class names/refmap, ZIP CRC,
  artifact byte sizes and SHA-256. This records artifact checks, not fabricated
  proof of command execution.

Artifact recording, after the successful checks above:

```powershell
python -B tools/record_nightmare_release.py
```

The recorder intentionally refuses to replace an existing delivered JAR.
For a later release, choose a new destination instead of deleting a user's build.

## Explicitly not verified

An actual client model bake/resource reload and visual gameplay acceptance have
**not** been performed. The owner still needs to inspect the gallery in Minecraft.
The unnamed fence is `NEEDS_USER_DEBUG`; eight `NOT_MENTIONED` families remain
visually unapproved. Eight source placements need a separate embedding/ownership
decision before they can migrate. No batch-03 work is included.
