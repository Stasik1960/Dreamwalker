package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.loader.api.FabricLoader;

/** Versions used by the project's current client compatibility baseline. */
public final class SodiumCompatibility {
    private SodiumCompatibility() {}

    public static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("not installed");
    }

    public static boolean verifiedClient() {
        return version("sodium").equals("0.5.13+mc1.20.1")
                && (version("indium").equals("not installed") || version("indium").equals("1.0.36+mc1.20.1"))
                && (version("iris").equals("not installed") || version("iris").equals("1.7.6+mc1.20.1"));
    }

    public static String installed() {
        return "Sodium " + version("sodium") + ", Indium " + version("indium") + ", Iris " + version("iris");
    }

    /** Pseudo mixins are optional when Sodium is absent; installed Sodium must expose their targets. */
    public static void checkTranslucentTargets() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;
        String[] targets = {
                "me/jellysquid/mods/sodium/client/gl/shader/ShaderLoader.class",
                "me/jellysquid/mods/sodium/client/render/chunk/shader/ChunkShaderOptions.class",
                "me/jellysquid/mods/sodium/client/gl/shader/ShaderConstants$Builder.class"
        };
        ClassLoader loader = SodiumCompatibility.class.getClassLoader();
        for (String target : targets) {
            if (loader.getResource(target) == null) {
                throw new IllegalStateException("Cannot apply DreamWalker translucent depth fix: "
                        + "Sodium target " + target + " is missing. Installed " + installed()
                        + "; verified with Sodium 0.5.13+mc1.20.1, Indium 1.0.36+mc1.20.1 "
                        + "and Iris 1.7.6+mc1.20.1.");
            }
        }
    }
}
