# DreamWalker v22 Building Fixes

Use the Bloodborne v22 Bush Restoration map. The mod keeps the Distinct Forms
registry IDs and the native city catalog, and the Original Pick 1.0.2 index
targets the new runtime version. Install DreamWalker on both sides and
Original Pick on the client.

## Build

Use Java 17, Python 3.12+, Gradle Wrapper, and Git LFS. From
`bloodborne complete`, run `gradlew.bat remapJar sourcesJar` with
`-Pworldedit_api_jar=<path to worldedit-mod-7.2.15.jar>`. Pass its remapped
JAR and the original `DreamWalker-v22-Compact-Startup-Bush-Restoration.jar`
from [Bush Restoration](https://github.com/Stasik1960/Dreamwalker/releases/tag/v22-bloodborne-bush-restoration.1)
to `Bloodborne-Startup/bush-restoration/package_editing_fix.py`:

```text
python package_editing_fix.py BUSH_RESTORATION.jar REMAPPED_BUILD.jar DreamWalker-v22-Bush-Restoration-Building-Fixes.jar
```

The packaging step overlays compiled changes and regenerated assets onto
the exact Bush Restoration input. See [Original Pick](DW-City-Original-Pick/README.md)
for rebuilding the companion add-on and its index.

Quarter-block moves use additional bits in the existing settings value.
Previously saved half-block offsets, turns, and flags retain their values.
