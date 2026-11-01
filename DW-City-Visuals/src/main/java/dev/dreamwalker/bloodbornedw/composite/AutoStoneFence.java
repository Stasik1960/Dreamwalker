package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Sparse authored-fence updates; no city scans, chunk loading or client tick work. */
public final class AutoStoneFence implements ModInitializer {
    private record Group(int[] poses, Set<Integer> compatible) {}
    private static final int[] MASKS = {0, 1, 5, 3, 7, 15};
    private static final Map<String, List<Group>> FAMILIES = load();
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Map<ServerWorld, Queue> QUEUES = new HashMap<>();
    private static final Map<ServerWorld, Map<Long, Set<BlockPos>>> PENDING = new HashMap<>();
    private static final class Queue {
        final ArrayDeque<BlockPos> positions = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
    }
    private static Map<String, List<Group>> load() {
        var stream = AutoStoneFence.class.getResourceAsStream("/bloodborne_dw/stone-fence-connections.json");
        if (stream == null) throw new IllegalStateException("Missing fence artwork mapping");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var families = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("families");
            Map<String, List<Group>> result = new HashMap<>();
            for (var family : families.entrySet()) {
                List<Group> groups = new ArrayList<>();
                for (var raw : family.getValue().getAsJsonObject().getAsJsonArray("groups")) {
                    var group = raw.getAsJsonObject(); var array = group.getAsJsonArray("poses");
                    if (array.size() != MASKS.length) throw new IllegalStateException("Fence shapes incomplete");
                    int[] poses = new int[array.size()]; for (int i = 0; i < poses.length; i++) poses[i] = array.get(i).getAsInt();
                    Set<Integer> compatible = new HashSet<>();
                    for (var pose : group.getAsJsonArray("compatible")) compatible.add(pose.getAsInt());
                    groups.add(new Group(poses, Set.copyOf(compatible)));
                }
                result.put(family.getKey(), List.copyOf(groups));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static Group group(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock block) || block.spec == null) return null;
        List<Group> groups = FAMILIES.get(block.spec.id.toString()); if (groups == null) return null;
        int pose = ImportedArtworkBlock.variant(state);
        for (Group group : groups) if (group.compatible().contains(pose)) return group;
        return null;
    }
    public static boolean matches(BlockState state) { return group(state) != null; }
    public static void enqueue(ServerWorld world, BlockPos pos) {
        Queue queue = QUEUES.computeIfAbsent(world, ignored -> new Queue());
        BlockPos copy = pos.toImmutable();
        if (queue.seen.add(copy)) queue.positions.add(copy);
    }
    public static void changed(ServerWorld world, BlockPos pos, BlockState before, BlockState after) {
        if (matches(after)) enqueue(world, pos);
        if (!before.isOf(after.getBlock()) && (matches(before) || matches(after))) {
            for (Direction side : SIDES) {
                BlockPos neighbor = pos.offset(side);
                var chunk = world.getChunkManager().getWorldChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (chunk != null && matches(chunk.getBlockState(neighbor))) enqueue(world, neighbor);
            }
        }
    }
    private static BlockState reconnect(ServerWorld world, BlockPos pos, BlockState state) {
        // Diagonal placement remains the explicitly selected manual artwork.
        int yaw = state.get(CompositeRootBlock.ROTATION);
        if ((yaw & 1) != 0) return state;
        Group group = group(state); if (group == null) return state;
        int mask = 0;
        for (int direction = 0; direction < 4; direction++) {
            Direction side = SIDES[direction];
            BlockPos neighborPos = pos.offset(side);
            var chunk = world.getChunkManager().getWorldChunk(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (chunk == null) {
                PENDING.computeIfAbsent(world, ignored -> new HashMap<>())
                    .computeIfAbsent(ChunkPos.toLong(neighborPos.getX() >> 4, neighborPos.getZ() >> 4), ignored -> new HashSet<>())
                    .add(pos.toImmutable());
                return state;
            }
            BlockState neighbor = chunk.getBlockState(neighborPos);
            boolean connected = matches(neighbor) || ((FenceBlock)Blocks.NETHER_BRICK_FENCE).canConnect(neighbor,
                    neighbor.isSideSolidFullSquare(world, neighborPos, side.getOpposite()), side);
            if (connected) mask |= 1 << direction;
        }
        for (int form = 0; form < MASKS.length; form++) for (int turns = 0; turns < 4; turns++) {
            int rotated = ((MASKS[form] << turns) | (MASKS[form] >>> (4 - turns))) & 15;
            if (rotated != mask) continue;
            int pose = group.poses()[form];
            BlockState next = state.with(CompositeRootBlock.VARIANT, pose % 16)
                .with(CompositeRootBlock.ROTATION, turns * 2);
            var bank = ImportedArtworkBlock.bankProperty(next);
            return bank == null ? next : next.with(bank, pose / 16);
        }
        throw new IllegalStateException("Fence connection mask missing");
    }
    private static void update(ServerWorld world, BlockPos pos) {
        var chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        BlockState state = chunk.getBlockState(pos);
        if (!matches(state)) return;
        BlockState next = reconnect(world, pos, state);
        if (next.equals(state)) return;
        if (ImportedCompactBlock.compact(state)) world.setBlockState(pos, next, Block.NOTIFY_ALL);
        else if (chunk.getBlockEntity(pos) instanceof CompositeBlockEntity own && own.resident() != null)
            CompositeRuntime.transition(world, own.resident(), next, null);
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            QUEUES.keySet().removeIf(world -> world.getServer() == server);
            PENDING.keySet().removeIf(world -> world.getServer() == server);
        });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            var pending = PENDING.get(world); if (pending == null) return;
            var positions = pending.remove(chunk.getPos().toLong());
            if (positions != null) positions.forEach(pos -> enqueue(world, pos));
            if (pending.isEmpty()) PENDING.remove(world);
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Queue queue = QUEUES.get(world);
            if (queue == null) return;
            for (int i = 0; i < 128 && !queue.positions.isEmpty(); i++) {
                BlockPos pos = queue.positions.remove(); queue.seen.remove(pos);
                update(world, pos);
            }
            if (queue.positions.isEmpty()) QUEUES.remove(world);
        });
    }
}
