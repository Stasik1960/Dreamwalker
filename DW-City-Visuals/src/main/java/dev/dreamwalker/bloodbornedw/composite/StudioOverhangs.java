package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Sparse, map-local links from foreign carrier cells to their static source roots. */
public final class StudioOverhangs extends PersistentState implements ModInitializer {
    public static final Identifier CHANNEL = new Identifier("bloodborne_dw", "studio_overhangs_v1");
    public static final int STRIDE = 7, PACKET_INTS = 35000, MAX_INTS = 1_400_000;
    private static final String FILE = "bloodborne_dw_studio_overhangs";
    private static final Map<ServerWorld, Index> SERVERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Index> CLIENT = new ConcurrentHashMap<>();
    private static final Index EMPTY = new Index(new int[0]);
    private final Index index;

    public enum Mode { PLAYERS, BLOCKS, OUTLINE }

    private record Part(BlockPos root, Cell offset, int pose) {}
    private record Key(int pose, Cell offset, Mode mode) {}
    private static final Map<Key, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    private static final class Index {
        final int[] raw;
        final Long2ObjectOpenHashMap<List<Part>> cells = new Long2ObjectOpenHashMap<>();
        Index(int[] raw) {
            if (raw.length > MAX_INTS || raw.length % STRIDE != 0) throw new IllegalArgumentException("Studio overhang record count");
            this.raw = raw;
            for (int i = 0; i < raw.length; i += STRIDE) {
                int x = raw[i], y = raw[i + 1], z = raw[i + 2];
                int ox = raw[i + 3], oy = raw[i + 4], oz = raw[i + 5], pose = raw[i + 6];
                if (Math.max(Math.abs(ox), Math.max(Math.abs(oy), Math.abs(oz))) > 48
                        || pose < 0 || pose >= StaticArchitectureCatalog.poses().size())
                    throw new IllegalArgumentException("Invalid Studio overhang pose or offset");
                cells.computeIfAbsent(BlockPos.asLong(x, y, z), key -> new ArrayList<>())
                        .add(new Part(new BlockPos(x + ox, y + oy, z + oz), new Cell(-ox, -oy, -oz), pose));
            }
        }
    }

    public StudioOverhangs() { index = EMPTY; }
    private StudioOverhangs(NbtCompound nbt) { index = new Index(nbt.getIntArray("Rows")); }
    @Override public NbtCompound writeNbt(NbtCompound nbt) { nbt.putIntArray("Rows", index.raw); return nbt; }

    @Override public void onInitialize() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            var saved = world.getPersistentStateManager().get(StudioOverhangs::new, FILE);
            if (saved != null) SERVERS.put(world, saved.index);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            SERVERS.remove(world);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> send(player));
    }

    private static void send(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) return;
        var world = player.getServerWorld();
        var index = index(world);
        for (int at = 0; at < Math.max(1, index.raw.length); at += PACKET_INTS) {
            var data = PacketByteBufs.create();
            data.writeIdentifier(world.getRegistryKey().getValue());
            data.writeVarInt(index.raw.length);
            data.writeVarInt(at);
            data.writeIntArray(java.util.Arrays.copyOfRange(index.raw, at, Math.min(at + PACKET_INTS, index.raw.length)));
            ServerPlayNetworking.send(player, CHANNEL, data);
        }
    }

    public static void acceptClient(Identifier dimension, int[] records) {
        if (records.length == 0) CLIENT.remove(dimension);
        else CLIENT.put(dimension, new Index(records));
    }

    public static void clearClient() { CLIENT.clear(); }

    private static Index index(World world) {
        if (world instanceof ServerWorld server) {
            return SERVERS.getOrDefault(server, EMPTY);
        }
        return CLIENT.getOrDefault(world.getRegistryKey().getValue(), EMPTY);
    }

    private static List<Part> parts(World world, BlockPos cell) {
        return index(world).cells.get(cell.asLong());
    }

    private static StaticArchitectureCatalog.Pose pose(Part part) {
        return StaticArchitectureCatalog.poses().get(part.pose);
    }

    private static boolean present(World world, Part part) {
        var chunk = world.getChunkManager().getWorldChunk(part.root.getX() >> 4, part.root.getZ() >> 4);
        return chunk != null && StaticArchitectureCatalog.matchesRoot(chunk.getBlockState(part.root), pose(part));
    }

    private static VoxelShape shape(Part part, Mode mode) {
        return SHAPES.computeIfAbsent(new Key(part.pose, part.offset, mode), key -> {
            var layout = StudioFootprints.staticPose(pose(part));
            if (layout == null) return VoxelShapes.empty();
            var volumes = switch (mode) {
                case PLAYERS -> layout.players();
                case BLOCKS -> layout.blocks();
                case OUTLINE -> layout.outline();
            };
            return ImportedPhysicalShapes.exact(volumes.getOrDefault(part.offset, List.of()));
        });
    }

    public static VoxelShape augment(BlockView view, BlockPos cell, VoxelShape original, Mode mode) {
        if (original == VoxelShapes.fullCube() || !(view instanceof World world) || CompositeShapeSnapshots.worker(view))
            return original;
        var parts = parts(world, cell);
        if (parts == null) return original;
        var result = original;
        for (var part : parts) {
            var extra = shape(part, mode);
            if (!extra.isEmpty() && present(world, part)) result = result.isEmpty() ? extra : VoxelShapes.union(result, extra);
        }
        return result;
    }

    public static Boolean remove(NativePlayerBreak.Scope scope, ServerWorld world, BlockPos pos) {
        if (scope == null || scope.world != world || !scope.clicked.equals(pos)) return null;
        var parts = parts(world, pos);
        if (parts == null) return null;
        PlayerEntity player = scope.player;
        var eye = player.getEyePos();
        var end = eye.add(player.getRotationVec(1).multiply(dev.dreamwalker.bloodbornedw.architecture.InteractionReach.block(player)));
        var carrier = world.getBlockState(pos);
        var nativeHit = carrier.getBlock().getOutlineShape(carrier, world, pos, ShapeContext.of(player)).raycast(eye, end, pos);
        double nearest = nativeHit == null ? Double.POSITIVE_INFINITY : nativeHit.getPos().squaredDistanceTo(eye);
        Part selected = null;
        for (var part : parts) {
            var hit = shape(part, Mode.OUTLINE).raycast(eye, end, pos);
            if (hit == null || !present(world, part)) continue;
            Vec3d point = hit.getPos();
            if (point.squaredDistanceTo(eye) < nearest - 1e-8) {
                nearest = point.squaredDistanceTo(eye);
                selected = part;
            }
        }
        if (selected == null) return null;
        scope.handled = true;
        var target = new StaticArchitectureRuntime.Component(selected.root, pose(selected));
        var item = NativeGridRetainedWholeItems.prepareDrop(world, target, player, !player.isCreative() && player.canHarvest(carrier));
        var result = StaticArchitectureRuntime.remove(world, target.root(), target.pose());
        NativeGridRetainedWholeItems.dropCommitted(world, target, item, result);
        return result.outcome() == TransactionCore.Outcome.COMMITTED;
    }
}
