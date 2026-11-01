package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Placement scope and deferred, sparse restoration; never loads chunks. */
public final class RetainedFramePlacement implements ModInitializer {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<ServerWorld, Queue> QUEUES = new HashMap<>();
    private static final class Queue { final ArrayDeque<BlockPos> positions = new ArrayDeque<>(); final Set<BlockPos> seen = new HashSet<>(); }
    private static final int TICK_BUDGET = 4096;
    public static boolean active() { return DEPTH.get() > 0; }
    public static void enter() { DEPTH.set(DEPTH.get() + 1); }
    public static void exit() { int n = DEPTH.get() - 1; if (n == 0) DEPTH.remove(); else DEPTH.set(n); }
    public static void changed(WorldChunk chunk, BlockPos pos, BlockState before, BlockState after) {
        if (!(chunk.getWorld() instanceof ServerWorld world)) return;
        if (active() && RetainedFrameState.matches(before) && !RetainedFrameState.matches(after)) RetainedFrames.put(chunk, pos, before);
        if (RetainedFrameState.matches(after)) RetainedFrames.remove(chunk, pos);
        else if (after.isAir() && RetainedFrames.get(chunk, pos) != null) enqueue(world, pos);
    }
    private static void enqueue(ServerWorld world, BlockPos pos) {
        Queue queue = QUEUES.computeIfAbsent(world, ignored -> new Queue()); BlockPos copy = pos.toImmutable();
        if (queue.seen.add(copy)) queue.positions.add(copy);
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUES.keySet().removeIf(world -> world.getServer() == server));
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            for (long encoded : RetainedFrames.all(RetainedFrames.data(chunk)).keySet()) {
                BlockPos pos = BlockPos.fromLong(encoded);
                if (chunk.getBlockState(pos).isAir()) enqueue(world, pos);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Queue queue = QUEUES.get(world); if (queue == null) return;
            for (int i = 0; i < TICK_BUDGET && !queue.positions.isEmpty(); i++) {
                BlockPos pos = queue.positions.remove(); queue.seen.remove(pos);
                WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !chunk.getBlockState(pos).isAir()) continue;
                BlockState frame = RetainedFrames.get(chunk, pos);
                if (frame != null && world.setBlockState(pos, frame, Block.NOTIFY_ALL) && RetainedFrameState.matches(world.getBlockState(pos))) RetainedFrames.remove(chunk, pos);
            }
            if (queue.positions.isEmpty()) QUEUES.remove(world);
        });
    }
}

