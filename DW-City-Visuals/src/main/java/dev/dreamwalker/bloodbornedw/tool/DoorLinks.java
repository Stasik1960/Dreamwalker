package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** Stable identities for two-cell mod doors, independent of open/closed state. */
public final class DoorLinks extends PersistentState {
    private record Entry(UUID id, String registry) {}
    private final Map<Long, Entry> entries = new HashMap<>();
    private DoorLinks() {}
    private static DoorLinks get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(DoorLinks::read, DoorLinks::new, "dw_linked_plain_doors");
    }
    public static boolean eligible(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return state.getBlock() instanceof DoorBlock && id.getNamespace().equals("bloodborne_dw");
    }
    public static boolean eligible(MechanismLinks.TargetRef ref) {
        Identifier id = Identifier.tryParse(ref.registryId());
        return id != null && id.getNamespace().equals("bloodborne_dw")
                && Registries.BLOCK.get(id) instanceof DoorBlock;
    }
    public static BlockPos root(BlockPos pos, BlockState state) {
        return state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    }
    public static ToolTarget target(ServerWorld world, BlockPos pos, BlockState state) {
        pos = root(pos, state);
        state = world.getBlockState(pos);
        if (!eligible(state) || state.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return null;
        DoorLinks saved = get(world);
        long key = pos.asLong();
        String registry = Registries.BLOCK.getId(state.getBlock()).toString();
        Entry entry = saved.entries.get(key);
        if (entry == null || !entry.registry.equals(registry)) {
            entry = new Entry(UUID.randomUUID(), registry);
            saved.entries.put(key, entry);
            saved.markDirty();
        }
        return new ToolTarget(ToolTarget.Kind.BLOCK, world.getRegistryKey().getValue().toString(), entry.id, key, registry);
    }
    public static ToolTarget existing(ServerWorld world, BlockPos pos, BlockState state) {
        pos = root(pos, state);
        state = world.getBlockState(pos);
        if (!eligible(state) || state.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return null;
        Entry entry = get(world).entries.get(pos.asLong());
        if (entry == null || !entry.registry.equals(Registries.BLOCK.getId(state.getBlock()).toString())) return null;
        return new ToolTarget(ToolTarget.Kind.BLOCK, world.getRegistryKey().getValue().toString(), entry.id, pos.asLong(), entry.registry);
    }
    public static MechanismLinks.TargetRef ref(ToolTarget target) {
        return new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE, target.dimension(), target.uuid(), target.root(), target.registry());
    }
    public static MechanismLinks.Status inspect(ServerWorld world, MechanismLinks.TargetRef ref) {
        BlockPos pos = BlockPos.fromLong(ref.root());
        if (!world.isChunkLoaded(pos)) return new MechanismLinks.Status(MechanismLinks.Availability.UNLOADED, false, false);
        BlockState state = world.getBlockState(pos);
        Entry entry = get(world).entries.get(ref.root());
        if (!eligible(state) || state.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER || entry == null
                || !entry.id.equals(ref.instance()) || !entry.registry.equals(ref.registryId())
                || !Registries.BLOCK.getId(state.getBlock()).toString().equals(ref.registryId()))
            return new MechanismLinks.Status(MechanismLinks.Availability.STALE, false, false);
        return new MechanismLinks.Status(MechanismLinks.Availability.LOADED, state.get(DoorBlock.OPEN), false);
    }
    public static boolean apply(ServerWorld world, MechanismLinks.TargetRef ref, boolean open) {
        if (inspect(world, ref).availability() != MechanismLinks.Availability.LOADED) return false;
        BlockPos pos = BlockPos.fromLong(ref.root());
        BlockState state = world.getBlockState(pos);
        ((DoorBlock)state.getBlock()).setOpen(null, world, state, pos, open);
        return world.getBlockState(pos).get(DoorBlock.OPEN) == open;
    }
    public static void removed(ServerWorld world, BlockPos pos) {
        DoorLinks saved = get(world);
        Entry entry = saved.entries.remove(pos.asLong());
        if (entry != null) {
            saved.markDirty();
            MechanismLinks.removedArchitecture(world.getServer(), new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,
                    world.getRegistryKey().getValue().toString(), entry.id, pos.asLong(), entry.registry));
        }
    }
    private static DoorLinks read(NbtCompound nbt) {
        DoorLinks saved = new DoorLinks();
        for (var element : nbt.getList("Doors", 10)) {
            NbtCompound entry = (NbtCompound)element;
            if (entry.containsUuid("Id")) saved.entries.put(entry.getLong("Pos"), new Entry(entry.getUuid("Id"), entry.getString("Registry")));
        }
        return saved;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        entries.forEach((pos, entry) -> {
            NbtCompound value = new NbtCompound();
            value.putLong("Pos", pos); value.putUuid("Id", entry.id); value.putString("Registry", entry.registry);
            list.add(value);
        });
        nbt.put("Doors", list);
        return nbt;
    }
}
