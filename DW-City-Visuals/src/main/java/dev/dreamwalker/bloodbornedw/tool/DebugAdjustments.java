package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Sparse settings. A zero pose never changes the original render or shape. */
public final class DebugAdjustments {
    public static final Identifier SYNC = new Identifier("bloodborne_dw", "debug_adjustments");
    public static final int SYNC_BATCH = 256;
    private static final int AXIS_BITS = 6;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    private static final int MAX_HALF_STEPS = 16;
    private static final int Y_SHIFT = AXIS_BITS;
    private static final int Z_SHIFT = AXIS_BITS * 2;
    private static final int TURN_SHIFT = AXIS_BITS * 3;
    private static final int POSE_MASK = (1 << (TURN_SHIFT + 3)) - 1;
    private static final int NO_PLAYERS = 1 << (TURN_SHIFT + 3);
    private static final int NO_BLOCKS = NO_PLAYERS << 1;
    private static final int RENDER_PRIORITY = NO_BLOCKS << 1;
    private static final int SETTINGS_MASK = POSE_MASK | NO_PLAYERS | NO_BLOCKS | RENDER_PRIORITY;
    private static final Map<ServerWorld, Saved> SERVER = new ConcurrentHashMap<>();
    private static final Map<Identifier, Map<Long, Entry>> CLIENT = new ConcurrentHashMap<>();

    private DebugAdjustments() {}

    public interface ObjectSettings {
        int dw$debugSettings();
        void dw$debugSettings(int value);
    }

    public record Entry(String registry, UUID owner, int value) {
        public Entry { value = sanitize(value); }
    }

    private static int axis(int value, int shift) {
        int raw = value >> shift & AXIS_MASK;
        return raw >= 1 << (AXIS_BITS - 1) ? raw - (1 << AXIS_BITS) : raw;
    }

    public static double x(int value) { return axis(value, 0) * 0.5; }
    public static double y(int value) { return axis(value, Y_SHIFT) * 0.5; }
    public static double z(int value) { return axis(value, Z_SHIFT) * 0.5; }
    public static int turns(int value) { return value >> TURN_SHIFT & 7; }
    public static boolean hasPose(int value) { return (value & POSE_MASK) != 0; }

    public static int sanitize(int value) {
        if (Math.abs(axis(value, 0)) > MAX_HALF_STEPS || Math.abs(axis(value, Y_SHIFT)) > MAX_HALF_STEPS
                || Math.abs(axis(value, Z_SHIFT)) > MAX_HALF_STEPS) return value & (NO_PLAYERS | NO_BLOCKS | RENDER_PRIORITY);
        return value & SETTINGS_MASK;
    }

    static int moved(int value, Direction direction) {
        int x = axis(value, 0) + direction.getOffsetX();
        int y = axis(value, Y_SHIFT) + direction.getOffsetY();
        int z = axis(value, Z_SHIFT) + direction.getOffsetZ();
        if (Math.abs(x) > MAX_HALF_STEPS || Math.abs(y) > MAX_HALF_STEPS || Math.abs(z) > MAX_HALF_STEPS) return value;
        int axes = AXIS_MASK | AXIS_MASK << Y_SHIFT | AXIS_MASK << Z_SHIFT;
        return (value & ~axes) | (x & AXIS_MASK) | (y & AXIS_MASK) << Y_SHIFT | (z & AXIS_MASK) << Z_SHIFT;
    }

    static int turned(int value) {
        return (value & ~(7 << TURN_SHIFT)) | ((turns(value) + 1) & 7) << TURN_SHIFT;
    }

    public static int objectSettings(RpObjectEntity object) { return ((ObjectSettings)(Object)object).dw$debugSettings(); }
    public static boolean playersDisabled(RpObjectEntity object) { return (objectSettings(object) & NO_PLAYERS) != 0; }
    public static boolean blocksDisabled(RpObjectEntity object) { return (objectSettings(object) & NO_BLOCKS) != 0; }
    public static boolean playersDisabled(World world, BlockPos root) { return (at(world, root) & NO_PLAYERS) != 0; }
    public static boolean blocksDisabled(World world, BlockPos root) { return (at(world, root) & NO_BLOCKS) != 0; }
    public static boolean playersDisabled(World world, ObjectInstance.Owner owner) { return (at(world, owner) & NO_PLAYERS) != 0; }
    public static boolean blocksDisabled(World world, ObjectInstance.Owner owner) { return (at(world, owner) & NO_BLOCKS) != 0; }

    private static Entry entry(World world, long root) {
        if (world instanceof ServerWorld serverWorld) {
            Saved saved = SERVER.get(serverWorld);
            return saved == null ? null : saved.entries.get(root);
        }
        return clientEntry(world.getRegistryKey().getValue(), root);
    }

    private static int at(World world, BlockPos root) {
        Entry entry = entry(world, root.asLong());
        return entry != null && matches(entry, world.getBlockState(root)) ? entry.value() : 0;
    }

    private static int at(World world, ObjectInstance.Owner owner) {
        if (owner == null) return 0;
        Entry entry = entry(world, CompositeData.pos(owner.root()).asLong());
        return entry != null && entry.registry().equals(owner.registryId())
                && (entry.owner() == null || entry.owner().equals(owner.instanceId())) ? entry.value() : 0;
    }

    public static int renderPose(World world, BlockPos root, BlockState state) {
        Entry entry = entry(world, root.asLong());
        return entry != null && matches(entry, state) ? entry.value() : 0;
    }

    public static int renderPriority(World world, BlockPos root, BlockState state) {
        return hasRenderPriority(renderPose(world, root, state)) ? 1 : 0;
    }

    public static boolean renderPriority(World world, ObjectInstance.Owner owner) {
        return hasRenderPriority(at(world, owner));
    }

    public static boolean hasRenderPriority(int value) {
        return (value & RENDER_PRIORITY) != 0;
    }

    public static boolean toggleRenderPriority(ServerPlayerEntity player, ToolTarget target) {
        if (target.entity(player.getServer()) != null) return false;
        return edit(player, target, value -> {
            int changed = value ^ RENDER_PRIORITY;
            String priority = hasRenderPriority(changed) ? "Повышенный" : "Автоматический";
            player.sendMessage(Text.literal(priority + " приоритет перекрытия · "
                    + BlockPos.fromLong(target.root()).toShortString()), true);
            return changed;
        });
    }

    private static boolean matches(Entry entry, BlockState state) {
        return entry.registry().equals(Registries.BLOCK.getId(state.getBlock()).toString());
    }

    public static boolean move(ServerPlayerEntity player, ToolTarget target, Direction direction) {
        return edit(player, target, value -> moved(value, direction));
    }

    public static boolean rotate(ServerPlayerEntity player, ToolTarget target) {
        return edit(player, target, DebugAdjustments::turned);
    }

    public static boolean toggle(ServerPlayerEntity player, ToolTarget target, boolean players) {
        int flag = players ? NO_PLAYERS : NO_BLOCKS;
        return edit(player, target, value -> {
            int changed = value ^ flag;
            player.sendMessage(Text.literal("Коллизия для " + (players ? "игроков" : "блоков")
                    + ((changed & flag) == 0 ? " включена." : " отключена.")), true);
            return changed;
        });
    }

    private static boolean edit(ServerPlayerEntity player, ToolTarget target, IntUnaryOperator action) {
        if (target.entity(player.getServer()) instanceof RpObjectEntity object) {
            ObjectSettings access = (ObjectSettings)(Object)object;
            int before = access.dw$debugSettings();
            int after = action.applyAsInt(before);
            if (after == before) return false;
            access.dw$debugSettings(after);
            return true;
        }
        ServerWorld world = player.getServerWorld();
        Saved saved = SERVER.get(world);
        if (saved == null) return false;
        Entry old = saved.entries.get(target.root());
        int before = old != null && old.registry().equals(target.registry())
                && (old.owner() == null || old.owner().equals(target.uuid())) ? old.value() : 0;
        int after = action.applyAsInt(before);
        if (after == before) return false;
        Entry changed = new Entry(target.registry(), target.uuid(), after);
        if (after == 0) saved.entries.remove(target.root());
        else saved.entries.put(target.root(), changed);
        saved.markDirty();
        syncChanged(world, target.root(), changed);
        return true;
    }

    public static void initialize() {
        ServerWorldEvents.LOAD.register((server, world) -> SERVER.put(world,
                world.getPersistentStateManager().getOrCreate(Saved::read, Saved::new, "dw_debug_adjustments")));
        ServerWorldEvents.UNLOAD.register((server, world) -> SERVER.remove(world));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncWorld(handler.player, handler.player.getServerWorld()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> syncWorld(player, to));
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerWorld serverWorld)) return;
            Saved saved = SERVER.get(serverWorld);
            if (saved == null) return;
            Entry removed = saved.entries.remove(pos.asLong());
            if (removed != null) {
                saved.markDirty();
                syncChanged(serverWorld, pos.asLong(), new Entry(removed.registry(), removed.owner(), 0));
            }
        });
    }

    private static void syncWorld(ServerPlayerEntity player, ServerWorld world) {
        if (!ServerPlayNetworking.canSend(player, SYNC)) return;
        Saved saved = SERVER.get(world);
        if (saved == null) return;
        var entries = saved.entries.entrySet().iterator();
        int remaining = saved.entries.size();
        boolean reset = true;
        do {
            int count = Math.min(remaining, SYNC_BATCH);
            PacketByteBuf packet = packet(world, reset, count);
            for (int i = 0; i < count; i++) {
                var entry = entries.next();
                write(packet, entry.getKey(), entry.getValue());
            }
            ServerPlayNetworking.send(player, SYNC, packet);
            remaining -= count;
            reset = false;
        } while (remaining > 0);
    }

    private static void syncChanged(ServerWorld world, long root, Entry entry) {
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            if (!ServerPlayNetworking.canSend(player, SYNC)) continue;
            PacketByteBuf packet = packet(world, false, 1);
            write(packet, root, entry);
            ServerPlayNetworking.send(player, SYNC, packet);
        }
    }

    private static PacketByteBuf packet(ServerWorld world, boolean reset, int count) {
        PacketByteBuf packet = PacketByteBufs.create();
        packet.writeIdentifier(world.getRegistryKey().getValue());
        packet.writeBoolean(reset);
        packet.writeVarInt(count);
        return packet;
    }

    private static void write(PacketByteBuf packet, long root, Entry entry) {
        packet.writeLong(root);
        packet.writeString(entry.registry(), 256);
        packet.writeBoolean(entry.owner() != null);
        if (entry.owner() != null) packet.writeUuid(entry.owner());
        packet.writeVarInt(entry.value());
    }

    public static Entry readEntry(PacketByteBuf packet) {
        String registry = packet.readString(256);
        UUID uuid = packet.readBoolean() ? packet.readUuid() : null;
        return new Entry(registry, uuid, packet.readVarInt());
    }

    public static void resetClient() { CLIENT.clear(); }
    public static void resetClient(Identifier dimension) { CLIENT.remove(dimension); }
    public static Entry clientEntry(Identifier dimension, long root) {
        var entries = CLIENT.get(dimension);
        return entries == null ? null : entries.get(root);
    }

    public static Entry installClient(Identifier dimension, long root, Entry entry) {
        var entries = CLIENT.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>());
        return entry.value() == 0 ? entries.remove(root) : entries.put(root, entry);
    }

    private static final class Saved extends PersistentState {
        private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

        static Saved read(NbtCompound nbt) {
            Saved state = new Saved();
            for (var tag : nbt.getList("Entries", 10)) {
                NbtCompound cell = (NbtCompound)tag;
                var entry = new Entry(cell.getString("Registry"), cell.containsUuid("Owner") ? cell.getUuid("Owner") : null,
                        cell.getInt("Settings"));
                if (entry.value() != 0 && !entry.registry().isBlank()) state.entries.put(cell.getLong("Root"), entry);
            }
            return state;
        }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            var cells = new NbtList();
            entries.forEach((root, entry) -> {
                var cell = new NbtCompound();
                cell.putLong("Root", root);
                cell.putString("Registry", entry.registry());
                if (entry.owner() != null) cell.putUuid("Owner", entry.owner());
                cell.putInt("Settings", entry.value());
                cells.add(cell);
            });
            nbt.put("Entries", cells);
            return nbt;
        }
    }
}
