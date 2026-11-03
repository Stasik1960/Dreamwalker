package dev.dreamwalker.bloodbornedw.visual;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public final class CityVisualModeState extends PersistentState {
    public static boolean isAlt(MinecraftServer server) { return get(server).alt; }
    boolean alt;
    long revision;
    static CityVisualModeState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> {
            CityVisualModeState state = new CityVisualModeState();
            state.alt = nbt.getBoolean("alt");
            state.revision = Math.max(0, nbt.getLong("revision"));
            return state;
        }, CityVisualModeState::new, "dw_city_visual_mode");
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("alt", alt); nbt.putLong("revision", revision); return nbt;
    }
}
