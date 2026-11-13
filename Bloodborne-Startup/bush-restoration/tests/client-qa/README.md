# Bloodborne building and Original Pick gameplay checks

Build from this directory with Gradle 8.8 and Java 17: `gradle --no-daemon remapJar`.

Copy the Bush Restoration test profile to a new game directory and use Fabric 1.20.1 with the map's dependencies, the DreamWalker and Original Pick builds under test, and the test JAR from `build/libs`. Set `soundCategory_master:0.0` in the copy's `options.txt`. Enable the harness with the JVM flag `-Ddw.qa.disposable=true` and the file `disposable-building-qa.marker` in that game directory. Launch the copy of Bloodborne in singleplayer.

The harness proceeds through the experimental world backup prompt in the disposable game directory. It exercises WorldEdit selection and the wooden axe, model and slab registration, 45-degree RP colliders, sign placement, wall adjacency, and the Original Pick key against five existing city fragments. It writes `qa-pick-result.txt` in the game directory and stops the client. The test sets up fixtures in the copied save and temporarily gives its test player operator permissions.

Add `-Ddw.qa.glass=true` to also compare player-model pixels through the patterned blue glass, behind an opaque control, and in an open control. This saves six `qa-glass-*.png` frames in the test game directory.
