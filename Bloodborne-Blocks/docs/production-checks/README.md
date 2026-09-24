# Executed production checks — 2026-09-24

Final result: **BUILD SUCCESSFUL**; 91 Python unit tests, the Java/data gates,
2,096 orientation checks, and **19/19 dedicated GameTests** passed.
The production registry has 33 logical blocks and one internal helper; all 524
BASE/ALT states pass support/shape checks. No missing-tag, registry or runtime
ERROR entries occurred. The test server emitted one brief `Can't keep up` warning
while executing the batched tests; it shut down normally. Gradle deprecation warnings
remain; dependency versions were not changed.

Evidence: [final build log](check-build-gametest.log),
[GameTest XML](TEST-production-gametest.xml), [JAR verification](artifact.json),
[vanilla summary loader](level-metadata.txt).

## Commands actually used

Run from `Bloodborne-Blocks`, with Java 17 and the bundled Python (numpy installed).
Local environment used JDK `build/toolchain/jdk-17.0.20.1`, and
`BLOODBORNE_VANILLA_JAR=C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar`.
The latter must be the real client assets JAR, not an asset-free mapped server JAR.

```powershell
.\gradlew.bat -I tools/verification-direct-resources.init.gradle '-PbloodbornePython=C:/Users/vakir/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' check build logicalGameTest
.\gradlew.bat -I tools/level-metadata-check.init.gradle levelMetadataCheck '-PlevelMetadataSaves=build/production-gallery-saves' '-PlevelMetadataExpectedValid=production-palette-gallery-ready-20260924' '-PlevelMetadataExpectedStable=production-palette-gallery-ready-20260924' '-PlevelMetadataReport=docs/production-checks/level-metadata.txt'
python -B tools/build_production_gallery.py --verify build/production-gallery-saves/production-palette-gallery-ready-20260924
python -B tools/verify_production_artifact.py ../releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-production-palette-20260924-mc1.20.1.jar --report docs/production-checks/artifact.json
```

The direct-resources init script bypasses stale pre-pruning copied resources, not
resource validation. No expansion/filtering is configured in this project. The
shipped JAR was subsequently compared against all 673 current source resources:
672 byte-for-byte, plus authored mixin settings compared semantically with Loom's
required added refmap. CRC, resource closure, deleted compatibility classes,
required runtime classes and exact production blockstate membership also passed.
For an ordinary local build, clear old generated build outputs first or use the
same init script; do not accidentally package pre-pruning `build/resources` leftovers.

## Behavioral coverage retained

- Complete-tree root/helper placement and destruction, exactly one whole-item drop.
- C282: all facings and BASE/ALT; fixed root, passable open state, both outer pivots;
  vertex-level hinge regression in Python, authentic foreign source panes,
  atomic blocked opening **and** blocked closing.
- Gate atomic transitions; railing four-neighbor connections; lantern lit/luminance;
  C654 A/B wall placement; master/helper ownership and stale-helper safety.
- Production manifest/registry equality; no retired items/targets; all rotations;
  identical BASE/ALT gameplay; exact mesh dedup; no orphan textures/models/tags;
  immutable source archive identity; semantic supersession and corrected item labels.
- C008 removed/canonical meshes: all 66 textured polygons compared at all facings;
  migration retains the second root and applies the proven −90° facing override.
- Positional weighted RNG: vectorized scan implementation compared to verified
  scalar selection, including negative coordinates and multipart draws.

Historical compiler-test reconciliation is documented in
[production-test-reconciliation.md](../production-test-reconciliation.md).

## Gallery and limits

Fresh ZIP `production-palette-gallery-ready-20260924.zip` contains a single world
folder with `level.dat` directly inside. 33 canonical specimens / 93 helpers /
327 white platform cells were verified against the final manifest. The official
Minecraft 1.20.1 `LevelStorage.create(...).getLevelList() -> loadSummaries(...)`
accepted this world (format 19133, DataVersion/Version.Id 3465).

This is **not** a claim that a graphical client was launched or all rendered assets
were visually accepted. No actual city conversion, production server deployment,
or compatibility test of old modded saves was performed. Original source world and
resource pack hashes were checked unchanged. Independent integration review cleared
the fixed statue-facing, real door-context and missing functional-test findings.

Agents: scout traced the registry/resource layers; implementation agent handled
runtime pruning and GameTests; gallery/audit agent measured resources and generated
the fixture; independent reviewer checked integration. The root agent owned source
usage analysis, semantic reconciliation, compiler/migration integration, final artifact
validation and publication. No expensive duplicate implementation was assigned.
