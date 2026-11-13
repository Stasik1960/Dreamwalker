# DreamWalker v22 Doors and Fences

Use the Bloodborne v22 Bush Restoration map and install the same DreamWalker
JAR on both sides. The J key uses the bundled City Original Pick 1.0.4 in
Creative mode. Remove older standalone copies of Original Pick from `mods`.

The creative catalogue contains 13 connected fence and column items. Original
fences already saved on the map keep their registered block and appearance.
Placing an old fence item or picking one of its indexed city fragments gives
the corresponding connected design.

The Watcher's Seal cycles fences and walls through automatic connections, a
straight section across the player's view, and automatic connections to blocks
with the same registry ID. The tall column shares its mode across both halves.

Creative breaking and WorldEdit `//set 0` clear both layers of a selected city
cell when it contains a baked fragment and part of an editable object. A
multi-block object is removed as a whole when its geometry occupies the cell.
Other baked fragments in the object's footprint are preserved.

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
the exact Bush Restoration input. Build the intermediate Building Fixes JAR
from [commit d3d673e4ce2](https://github.com/Stasik1960/Dreamwalker/tree/d3d673e4ce2).
For the `--original-pick` input, build 1.0.2 from the current checkout using
the [Original Pick build instructions](DW-City-Original-Pick/README.md).
The package bundles it as 1.0.4 and pins its city index to the DreamWalker version.

Quarter-block moves use additional bits in the existing settings value.
Previously saved half-block offsets, turns, and flags retain their values.
