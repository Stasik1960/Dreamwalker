package dev.dreamwalker.bloodbornedw.tool;

import java.util.WeakHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

/** Bounds duplicate action packets without retaining disconnected players. */
public final class ClickRateLimit {
    private static final WeakHashMap<ServerPlayerEntity, long[]> LAST = new WeakHashMap<>();
    private ClickRateLimit() {}

    public static boolean accept(ServerPlayerEntity player, int channel) {
        long now = player.getServer().getTicks();
        long[] last = LAST.computeIfAbsent(player, ignored -> new long[] { Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE });
        if (last[channel] != Long.MIN_VALUE && now - last[channel] < 2) return false;
        last[channel] = now;
        return true;
    }
}
