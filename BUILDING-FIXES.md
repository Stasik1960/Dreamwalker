# DreamWalker v22 Doors and Fences

Use the Bloodborne v22 Bush Restoration map and install the same DreamWalker
JAR on both sides. The J key uses the bundled City Original Pick 1.0.3 in
Creative mode. Remove older standalone copies of Original Pick from `mods`.

The creative catalogue contains 13 connected fence and column items. Original
fences already saved on the map keep their registered block and appearance.
Placing an old fence item or picking one of its indexed city fragments gives
the corresponding connected design.

The Watcher's Seal switches fences and walls between neighbor connections and
a straight section across the player's view. The tall column shares connections
between its two halves.

## Build

Use Java 17, Python 3.12+, Gradle Wrapper, and Git LFS. From
`bloodborne complete`, run `gradlew.bat remapJar sourcesJar` with
`-Pworldedit_api_jar=<path to worldedit-mod-7.2.15.jar>`. From `DW-City-Visuals`,
run `gradlew.bat remapWindowPlacementJar` with
`-PworldEditJar=<path to worldedit-mod-7.2.15.jar>`.
Pass both remapped JARs and the original `DreamWalker-v22-Compact-Startup-Bush-Restoration.jar`
from [Bush Restoration](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-bush-restoration.1)
to `Bloodborne-Startup/bush-restoration/package_editing_fix.py`:

```text
python package_editing_fix.py BUSH_RESTORATION.jar REMAPPED_BUILD.jar DreamWalker-v22-Bush-Restoration-Doors-and-Fences.jar --window-placement WINDOW_PLACEMENT.jar --original-pick ORIGINAL_PICK_1.0.2.jar
```

The packaging step overlays compiled changes and regenerated assets onto
the exact Bush Restoration input. To build the 1.0.2 input, use the Building
Fixes packaging instructions from commit `d3d673e4ce2` and the
[Original Pick build instructions](DW-City-Original-Pick/README.md). The new
package bundles a 1.0.3 index for the current DreamWalker version.

Quarter-block moves use additional bits in the existing settings value.
Previously saved half-block offsets, turns, and flags retain their values.
