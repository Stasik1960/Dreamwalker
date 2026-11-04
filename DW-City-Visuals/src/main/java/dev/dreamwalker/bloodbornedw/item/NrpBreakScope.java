package dev.dreamwalker.bloodbornedw.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Narrow thread scope for the existing loot and native whole-block pipelines. */
public final class NrpBreakScope implements AutoCloseable {
    private static final ThreadLocal<NrpBreakScope> CURRENT = new ThreadLocal<>();
    private final NrpBreakScope previous;
    private final PlayerEntity player;
    private final ServerWorld world;
    private final boolean adventure;

    private NrpBreakScope(ServerPlayerEntity player,ServerWorld world) {
        previous = CURRENT.get();
        this.player = player;
        this.world = world;
        adventure = player.interactionManager.getGameMode() == GameMode.ADVENTURE;
        CURRENT.set(this);
    }

    public static NrpBreakScope open(ServerPlayerEntity player,ServerWorld world) {
        return new NrpBreakScope(player,world);
    }

    public static boolean dropsIn(World world) {
        var scope = CURRENT.get();
        return scope != null && scope.world == world;
    }

    public static boolean permitsAdventure(ServerWorld world,PlayerEntity player) {
        var scope = CURRENT.get();
        return scope != null && scope.adventure && scope.world == world && scope.player == player;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
