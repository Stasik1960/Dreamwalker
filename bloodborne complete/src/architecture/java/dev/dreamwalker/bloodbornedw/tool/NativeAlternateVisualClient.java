package dev.dreamwalker.bloodbornedw.tool;

import java.util.Map;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/** ALT is a persisted profile slot. No authored alternate artwork is loaded. */
@Environment(EnvType.CLIENT)
public final class NativeAlternateVisualClient {
    private NativeAlternateVisualClient() {}
    public static void initialize() {}
    public static Map<String,Integer> snapshot() {
        return Map.of("alternateModelWrappers",0,"sharedAlternateColoredQuads",0,"perWorldOrPositionCacheEntries",0);
    }
}
