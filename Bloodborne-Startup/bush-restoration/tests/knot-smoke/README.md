# Fabric loader and model shift checks

Use Java 17 and the runtime classpath from a Fabric 1.20.1 client profile. Create a temporary game directory and put the DreamWalker build, Fabric API, GeckoLib, and WorldEdit in its `mods` directory. Run these commands from the temporary directory with `<runtime-classpath>` set to the profile's Java classpath and `<game-jar>` set to its Minecraft JAR.

```text
javac -cp "<runtime-classpath>" -d classes "<source-path>/KnotNeighborSmoke.java"
java -Xmx192m -XX:+UseSerialGC "-Dfabric.gameJarPath=<game-jar>" -cp "<runtime-classpath>;<absolute-temporary-directory>/classes" KnotNeighborSmoke
```

The classpath separator shown is for Windows. Use a colon on Linux and macOS. This check initializes Fabric Loader and applies the mod's mixins without running the Minecraft entry point or loading a save. It checks legacy model offsets, quarter-block movement in each direction, rotation, and NBT persistence through the assembled JAR.
