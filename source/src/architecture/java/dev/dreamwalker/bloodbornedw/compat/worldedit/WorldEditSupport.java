package dev.dreamwalker.bloodbornedw.compat.worldedit;

import net.fabricmc.loader.api.FabricLoader;

/** WorldEdit remains optional; its API classes are never loaded without it. */
public final class WorldEditSupport {
    private WorldEditSupport() {}
    public static void initialize() {
        if (FabricLoader.getInstance().isModLoaded("worldedit")) WorldEditBridge.initialize();
    }
}
