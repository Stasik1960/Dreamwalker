# Executed restoration checks — 2026-09-24

**BUILD SUCCESSFUL** (`logicalGameTest check build levelMetadataCheck`):
**122 Python unit tests**, Java/data gates, **7120 orientation checks**, and
**22/22 dedicated-server GameTests** passed. Server shut down normally.
Independent actual-gallery coverage: **PASS, 56/56 families**, 64 specimens.
Vanilla 1.20.1 LevelStorage accepted `production-functional-gallery-20260924`.
Resources: 2009 byte-for-byte matches + one semantic Loom/refmap comparison;
57 registry blockstates including helper, zero obsolete packaged resources.
Gradle/deprecated API warnings remain; dependency versions were not changed.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| JAR | 1882076 | `b5fff88651788b037e6983ca7c43938ce465e145c055120fd94944e1cf84be3e` |
| Gallery ZIP | 59169 | `b647b94274bfc52a6586883839258ccd0d1399d757901eb1afacc4b5d3b25291` |

Current evidence files:

- [Independent source → family → V2 → registry → gallery coverage](coverage.json)
- [Gallery specimen index](gallery.json)
- [Dedicated-server GameTests](TEST-gametest.xml)
- [Complete build log](check-build-gametest.log)
- [Vanilla 1.20.1 LevelStorage summaries](level-metadata.txt)
- [Final JAR byte/resource verification](artifact.json)

The required input is `docs/required-production-families.json`; it is not produced
from the filtered manifest. Actual gallery cells/helper ownership/pads and metadata
were verified separately, not merely counted in a manifest.

## Commands

From `Bloodborne-Blocks`, Java 17 and Python with numpy:

```powershell
$env:JAVA_HOME=(Resolve-Path build/toolchain/jdk-17.0.20.1).Path
$env:BLOODBORNE_VANILLA_JAR='C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
.\gradlew.bat -I tools/verification-direct-resources.init.gradle -I tools/level-metadata-check.init.gradle logicalGameTest check build levelMetadataCheck '-PbloodbornePython=C:/Users/vakir/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' '-PlevelMetadataSaves=build/production-gallery-saves' '-PlevelMetadataExpectedValid=production-functional-gallery-20260924' '-PlevelMetadataExpectedStable=production-functional-gallery-20260924' '-PlevelMetadataReport=build/restoration-checkpoint/level-metadata.txt' --no-daemon --console=plain
python -B -X utf8 tools/build_production_gallery.py --verify build/production-gallery-saves/production-functional-gallery-20260924
python -B -X utf8 tools/production_coverage.py --gallery build/production-gallery-saves/production-functional-gallery-20260924 --output docs/production-restoration-checks/coverage.json
python -B -X utf8 tools/verify_production_artifact.py ../releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-functional-restoration-20260924-mc1.20.1.jar --report docs/production-restoration-checks/artifact.json
```

The existing direct-resources init script avoids copying/hashing stale historical
build output trees; no source-resource filtering or expansion is configured.
Final JAR checking compares all current source resources, permits only Loom's
required refmap addition, and rejects obsolete packaged resources/test classes.

No new source-world scan or city conversion. Migration tests use small temporary
fixtures, including default-legacy refusal before copying. No graphical client
visual acceptance is claimed; LevelStorage is the actual vanilla summary loader.
See [limitations](../PRODUCTION-RESTORATION.md#gallery-and-remaining-qa).
