# Bloodborne building and Original Pick gameplay checks

Build from this directory with Gradle 8.8 and Java 17: `gradle --no-daemon remapJar`.

Copy the Bush Restoration test profile to a new game directory and use Fabric 1.20.1 with the map's dependencies, the DreamWalker build with bundled Original Pick, and the test JAR from `build/libs`. Set `soundCategory_master:0.0` in the copy's `options.txt`. Enable the harness with the JVM flag `-Ddw.qa.disposable=true` and the file `disposable-building-qa.marker` in that game directory. Launch the copy of Bloodborne in singleplayer.

The harness proceeds through the experimental world backup prompt in the disposable game directory. It exercises WorldEdit selection and the wooden axe, model and slab registration, 45-degree RP colliders, sign placement, wall adjacency, connected fences, the shutter window, utility protection, and the Original Pick key against seven existing city fragments. It writes `qa-pick-result.txt` in the game directory and stops the client. The test sets up fixtures in the copied save and temporarily gives its test player operator permissions.

Add `-Ddw.qa.crawl=true` with Crawl 0.12.0 to test walking and crawling against the open shutter with keyboard input. Add `-Ddw.qa.whiteLight=true` to measure pixels of the white Light block in a dark room, and `-Ddw.qa.fenceGallery=true` to render a gallery of all 13 fences and posts.

Add `-Ddw.qa.glass=true` to also compare player-model pixels through the patterned blue glass, behind an opaque control, and in an open control. This saves six `qa-glass-*.png` frames in the test game directory.

Add `-Ddw.qa.shiftOnly=true` to run only the BB model tool check. It drives real client mouse bindings across all six shift modes, checks matching quarter-block offsets on the server and client, and compares rendered frames for fresh compact, fixed-ID composite, and baked city blocks. It also checks the tool in Survival with operator level 2.
