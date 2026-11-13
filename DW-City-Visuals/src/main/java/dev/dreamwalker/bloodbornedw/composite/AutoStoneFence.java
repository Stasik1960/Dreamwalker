package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.player.PlayerEntity;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

/** Updates authored fences in loaded chunks and queues saved poses from the old wall rules. */
public final class AutoStoneFence implements ModInitializer {
    private record WallPose(int[] sides, boolean up, int pose, int baseTurn) {}
    private record Group(int[] poses, Set<Integer> compatible, boolean wall, int straight, List<WallPose> wallStates) {}
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
                    int straight = group.has("straight") ? group.get("straight").getAsInt() : -1;
                    boolean wall = family.getKey().endsWith("_wall") || group.get("post").getAsString().endsWith("_wall_post");
                    List<WallPose> wallStates = new ArrayList<>();
                    if (group.has("wallStates")) for (var rawState : group.getAsJsonArray("wallStates")) {
                        var value = rawState.getAsJsonObject(); var sides = value.getAsJsonArray("sides");
                        if (sides.size() != 4) throw new IllegalStateException("Invalid wall sides");
                        int[] heights = new int[4]; for (int i = 0; i < 4; i++) heights[i] = sides.get(i).getAsInt();
                        int pose = value.get("pose").getAsInt();
                        compatible.add(pose);
                        wallStates.add(new WallPose(heights, value.get("up").getAsBoolean(),
                                pose, value.get("baseTurn").getAsInt()));
                    }
                    groups.add(new Group(poses, Set.copyOf(compatible), wall, straight, List.copyOf(wallStates)));
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
        for (Group group : groups) if (group.straight() == pose || group.compatible().contains(pose)) return group;
        return null;
    }
    public static boolean matches(BlockState state) { return group(state) != null; }
    /** A mixed artwork registry is classified by its reviewed pose, not broad block tags. */
    public static boolean wallLike(BlockState state) {
        if (state.getBlock() instanceof PrototypeWallBlock) return true;
        Group group = group(state);
        return group != null && group.wall();
    }
    public static boolean fenceLike(BlockState state) {
        Group group = group(state);
        if (group == null || group.wall()) return false;
        String id = ((CompositeRootBlock) state.getBlock()).spec.id.getPath();
        return !id.contains("_pane") && !id.endsWith("_bars");
    }
    public static boolean knownFamily(BlockState state) {
        return state.getBlock() instanceof CompositeRootBlock block && block.spec != null
            && FAMILIES.containsKey(block.spec.id.toString());
    }

    /** Keep the selected straight artwork in the existing non-opening state bit. */
    public static Boolean toggleStraight(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player, Direction.Axis axis) {
        Group group = group(state);
        if (group == null || !(state.getBlock() instanceof CompositeRootBlock block) || block.spec.openable) return null;
        boolean straight = !state.get(CompositeRootBlock.OPEN);
        BlockState next = state.with(CompositeRootBlock.OPEN, straight);
        if (straight) {
            int rotation = axis == Direction.Axis.Z ? 0 : 2;
            int pose = group.straight() >= 0 ? group.straight() : group.poses()[2];
            for (WallPose value : group.wallStates()) {
                if (value.pose() == pose) {
                    boolean alongX = value.sides()[1] != 0 && value.sides()[3] != 0;
                    rotation = (rotation + 2 * value.baseTurn() + (alongX ? 2 : 0)) & 7;
                    break;
                }
            }
            next = next.with(CompositeRootBlock.VARIANT, pose % 16).with(CompositeRootBlock.ROTATION, rotation);
            var bank = ImportedArtworkBlock.bankProperty(next);
            if (bank != null) next = next.with(bank, pose / 16);
        } else {
            next = reconnect(world, pos, next);
        }
        if (ImportedCompactBlock.compact(state) && ImportedCompactBlock.compact(next)) {
            return world.setBlockState(pos, next, Block.NOTIFY_ALL);
        }
        var owner = world.getBlockEntity(pos) instanceof CompositeBlockEntity be ? be.resident() : null;
        if (owner == null) owner = ImportedCompactBlock.materialize(world, pos, player);
        if (owner == null) return false;
        if (next.contains(ImportedCompactBlock.COMPACT)) next = next.with(ImportedCompactBlock.COMPACT, false);
        return CompositeRuntime.transition(world, owner, next, player).outcome()
                == dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED;
    }
    /** Decode canonical poses to cached vanilla wall shapes without world or neighbor reads. */
    public static BlockState canonicalWall(BlockState state) {
        Group group = group(state);
        if (group == null || !group.wall() || (state.get(CompositeRootBlock.ROTATION) & 1) != 0) return null;
        int pose = ImportedArtworkBlock.variant(state), form = -1;
        int savedTurns = state.get(CompositeRootBlock.ROTATION) / 2;
        for (WallPose value : group.wallStates()) if (value.pose() == pose) {
            int turns = (savedTurns - value.baseTurn()) & 3;
            BlockState wall = Blocks.COBBLESTONE_WALL.getDefaultState().with(net.minecraft.block.WallBlock.UP, value.up());
            for (int i = 0; i < 4; i++) {
                int height = value.sides()[(i - turns + 4) & 3];
                wall = wall.with(PrototypeWallBlock.property(SIDES[i]),
                        height == 0 ? WallShape.NONE : height == 1 ? WallShape.LOW : WallShape.TALL);
            }
            return wall;
        }
        boolean post = true;
        if (group.straight() == pose) { form = 2; post = false; }
        else for (int i = 0; i < group.poses().length; i++) if (group.poses()[i] == pose) { form = i; break; }
        if (form < 0) return null;
        int base = MASKS[form];
        int mask = ((base << savedTurns) | (base >>> (4 - savedTurns))) & 15;
        BlockState wall = Blocks.COBBLESTONE_WALL.getDefaultState().with(net.minecraft.block.WallBlock.UP, post);
        for (int i = 0; i < 4; i++) wall = wall.with(PrototypeWallBlock.property(SIDES[i]),
                (mask & (1 << i)) == 0 ? WallShape.NONE : WallShape.LOW);
        return wall;
    }
    public static void defer(ServerWorld world, BlockPos waiting, BlockPos missing) {
        PENDING.computeIfAbsent(world, ignored -> new HashMap<>())
            .computeIfAbsent(ChunkPos.toLong(missing.getX() >> 4, missing.getZ() >> 4), ignored -> new HashSet<>())
            .add(waiting.toImmutable());
    }

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
        if (state.get(CompositeRootBlock.OPEN)) return state;
        // Diagonal placement remains the explicitly selected manual artwork.
        int yaw = state.get(CompositeRootBlock.ROTATION);
        if ((yaw & 1) != 0) return state;
        Group group = group(state); if (group == null) return state;
        int mask = 0;
        BlockState nativeWall = Blocks.COBBLESTONE_WALL.getDefaultState();
        for (int direction = 0; direction < 4; direction++) {
            Direction side = SIDES[direction];
            BlockPos neighborPos = pos.offset(side);
            var chunk = world.getChunkManager().getWorldChunk(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            if (chunk == null) {
                defer(world, pos, neighborPos);
                return state;
            }
            BlockState neighbor = chunk.getBlockState(neighborPos);
            boolean solidFace = neighbor.isSideSolidFullSquare(world, neighborPos, side.getOpposite());
            boolean connected;
            if (group.wall()) {
                nativeWall = nativeWall.getStateForNeighborUpdate(side, neighbor, world, pos, neighborPos);
                connected = nativeWall.get(PrototypeWallBlock.property(side)) != WallShape.NONE;
            } else if (fenceLike(state)) {
                connected = fenceLike(neighbor)
                    || ((FenceBlock) Blocks.NETHER_BRICK_FENCE).canConnect(neighbor, solidFace, side);
            } else {
                Group neighborGroup = group(neighbor);
                connected = neighborGroup != null && !neighborGroup.wall()
                    || ((FenceBlock) Blocks.NETHER_BRICK_FENCE).canConnect(neighbor, solidFace, side);
            }
            if (connected) mask |= 1 << direction;
        }
        if (group.wall()) {
            BlockPos above = pos.up();
            if (!world.isChunkLoaded(above)) { defer(world, pos, above); return state; }
            nativeWall = nativeWall.getStateForNeighborUpdate(Direction.UP, world.getBlockState(above), world, pos, above);
        }
        if (group.wall() && !group.wallStates().isEmpty()) {
            int[] heights = new int[4];
            for (int i = 0; i < 4; i++) {
                WallShape shape = nativeWall.get(PrototypeWallBlock.property(SIDES[i]));
                heights[i] = shape == WallShape.NONE ? 0 : shape == WallShape.LOW ? 1 : 2;
            }
            for (WallPose value : group.wallStates()) {
                if (value.up() != nativeWall.get(net.minecraft.block.WallBlock.UP)) continue;
                for (int turns = 0; turns < 4; turns++) {
                    boolean same = true;
                    for (int i = 0; i < 4; i++) if (heights[i] != value.sides()[(i - turns + 4) & 3]) { same = false; break; }
                    if (!same) continue;
                    int pose = value.pose();
                    BlockState next = state.with(CompositeRootBlock.VARIANT, pose % 16)
                            .with(CompositeRootBlock.ROTATION, ((turns + value.baseTurn()) & 3) * 2);
                    var bank = ImportedArtworkBlock.bankProperty(next);
                    return bank == null ? next : next.with(bank, pose / 16);
                }
            }
            throw new IllegalStateException("Wall connection height combination missing");
        }
        for (int form = 0; form < MASKS.length; form++) for (int turns = 0; turns < 4; turns++) {
            int rotated = ((MASKS[form] << turns) | (MASKS[form] >>> (4 - turns))) & 15;
            if (rotated != mask) continue;
            int pose = form == 2 && group.straight() >= 0 && group.wall() && !nativeWall.get(net.minecraft.block.WallBlock.UP) ? group.straight() : group.poses()[form];
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
        if (state.getBlock() instanceof PrototypeWallBlock block) {
            BlockState next = block.reconnect(state, world, pos);
            if (!next.equals(state)) world.setBlockState(pos, next, Block.NOTIFY_ALL);
            return;
        }
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
