# Build the DreamWalker MPM patch

This project overlays the editable network code in `src/` onto the DreamWalker Fabric build of More Player Models `1.0.6-dw.3`. It compiles only the recovered and changed classes. The original game resources, mixins, license and the remaining mod classes come from the pinned input JAR. The output version is `1.0.6-dw.4` for Minecraft 1.20.1.

Use PowerShell and a JDK 17 or newer. Run Gradle 8.8 to prepare dependencies on a machine without the Fabric Loom caches. Set `JAVA_HOME` to the JDK before running Gradle.

```powershell
gradle --no-daemon -p .\tools\bootstrap preparePatchDependencies
```

This downloads Minecraft, Yarn `1.20.1+build.10`, Fabric API `0.92.12+1.20.1` and the compile-time libraries. It prepares the intermediary Minecraft JAR used by the patch. This command is optional if the required files are already cached.

Use the original `MorePlayerModels-Fabric-1.20.1-1.0.6-dw.3.jar` from the DreamWalker client. The build requires SHA-256 `9d49536ad3bb099eb716942a98a00adca731232f484f2137f83ebbdcd7d6adbc`.

```powershell
.\build.ps1 -BaseJar 'C:\path\to\MorePlayerModels-Fabric-1.20.1-1.0.6-dw.3.jar'
```

The script checks the input hash, compiles with `javac --release 17`, and writes `build/MorePlayerModels-Fabric-1.20.1-1.0.6-dw.4.jar` alongside its SHA-256 output. It reads the input JAR in place. Rebuilding with the same JDK and dependency set yields identical bytes.

Use `-JdkHome` to select a JDK, `-GradleCache` to point at a cache directory or `-OutputPath` for a separate output file. For a different dependency layout, supply `-MinecraftJar`, `-FabricApiJar`, `-FabricLoaderJar` and `-VanillaClasspath` explicitly. `-AdditionalClasspath` accepts further compile-time JARs.
