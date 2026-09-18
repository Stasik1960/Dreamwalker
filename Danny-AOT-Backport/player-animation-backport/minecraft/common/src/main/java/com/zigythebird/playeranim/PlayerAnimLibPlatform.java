package com.zigythebird.playeranim;

import net.fabricmc.loader.api.FabricLoader;

public final class PlayerAnimLibPlatform {
    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }
}
