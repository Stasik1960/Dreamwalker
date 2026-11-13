# Dreamwalker City Original Pick

Client add-on for creative-mode J picking of indexed Bloodborne city fragments.
Version 1.0.2 targets DreamWalker v22 Building Fixes and the Bush Restoration map.
The Java sources under `mod/src` were recovered from the original 1.0.0 project.
The index generator and the compatibility test came from commit `1918294`.

Use Java 17, Python 3.12+, the exact Original Pick 1.0.0 JAR, the
[Seam Repair Compact Startup JAR](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-seam-repair-compact-startup.1),
the [Bush Restoration JSON report](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-bush-restoration.1),
and a packaged Building Fixes JAR.

```powershell
python rebase_index.py ORIGINAL_PICK_1.0.0.jar SEAM_REPAIR.jar BUILDING_FIXES.jar BUSH_REPORT.json indexed.jar --addon-version 1.0.2
python extract-compile-api.py BUILDING_FIXES.jar compile-api.jar
$api = (Resolve-Path compile-api.jar).Path
$data = (Resolve-Path indexed.jar).Path
.\mod\gradlew.bat -p mod "-PdreamwalkerJar=$api" "-PpickData=$data" remapJar
$jar = (Resolve-Path mod/build/libs/dreamwalker-city-original-pick-1.0.2.jar).Path
javac -cp $jar -d mod/build/check tests/IndexCompatibilityCheck.java
java -cp "mod/build/check;$jar" dev.dreamwalker.citypick.IndexCompatibilityCheck
```
