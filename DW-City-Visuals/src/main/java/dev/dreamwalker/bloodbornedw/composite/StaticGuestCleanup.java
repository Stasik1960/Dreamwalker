package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Prepare all affected owners before changing any carrier or publishing a break. */
public final class StaticGuestCleanup {
    private StaticGuestCleanup() {}

    public static TransactionCore.Result remove(ServerWorld world, StaticArchitectureRuntime.Component target) {
        return remove(world, List.of(target), List.of());
    }

    /** Null means there is no shared static carrier at the clicked position. */
    public static TransactionCore.Result removeSharedCell(ServerWorld world, BlockPos hit) {
        var state = world.getBlockState(hit);
        if (!StaticArchitectureCatalog.contains(state)) return null;
        var parts = new ArrayList<StaticArchitectureRuntime.Component>();
        for (var fragment : StaticArchitectureCatalog.recipe(state).fragments()) {
            parts.add(new StaticArchitectureRuntime.Component(
                    CompositeData.pos(CompositeData.cell(hit).add(fragment.origin())), fragment.pose()));
        }
        var guests = CompositeRuntime.contributions(world, hit).stream().map(CompositeData.Contribution::owner)
                .distinct().toList();
        if (parts.size() + guests.size() < 2) return null;
        return remove(world, parts, guests);
    }

    private static TransactionCore.Result remove(ServerWorld world, List<StaticArchitectureRuntime.Component> parts,
            List<Owner> guests) {
        var access = new FabricCompositeWorld(world, Map.of()) {
            @Override public boolean writeSilently(Cell cell, CellSnapshot next) {
                var state = CompositeData.state(next.data());
                if (next.kind() == CellSnapshot.Kind.FOREIGN && state.isOf(Blocks.WATER)) {
                    world.setBlockState(CompositeData.pos(cell), state, Block.FORCE_STATE | Block.SKIP_DROPS);
                }
                return super.writeSilently(cell, next);
            }
        };
        var expected = new TreeMap<Cell, CellSnapshot>();
        var writes = new TreeMap<Cell, CellSnapshot>();
        var virtual = new TransactionCore.WorldAccess() {
            @Override public void assertMutationThread() { access.assertMutationThread(); }
            @Override public String dimensionKey() { return access.dimensionKey(); }
            @Override public boolean isLoaded(Cell cell) { return access.isLoaded(cell); }
            @Override public boolean inBounds(Cell cell) { return access.inBounds(cell); }
            @Override public CellSnapshot read(Cell cell) {
                var original = expected.computeIfAbsent(cell, access::read);
                return writes.getOrDefault(cell, original);
            }
            @Override public Optional<ObjectInstance> describeRoot(Owner owner, CellSnapshot snapshot) {
                return access.describeRoot(owner, snapshot);
            }
            @Override public boolean supportsGuestBindings(CellSnapshot carrier) {
                return access.supportsGuestBindings(carrier);
            }
            @Override public boolean intersectsEntities(Cell cell, Footprint footprint) { return false; }
            @Override public boolean writeSilently(Cell cell, CellSnapshot next) {
                throw new UnsupportedOperationException("Break preflight cannot write");
            }
            @Override public void publishCommitted(List<Cell> cells) {
                throw new UnsupportedOperationException("Break preflight cannot publish");
            }
        };

        for (Owner owner : guests) {
            if (!access.isLoaded(owner.root())) return rejected("static_guest_cleanup_unloaded_owner");
            if (world.getBlockEntity(CompositeData.pos(owner.root())) instanceof SourceLadderBlockEntity) {
                return rejected("static_guest_cleanup_linked_ladder");
            }
            var removal = CompositeRuntime.core().removal(virtual, owner);
            if (!removal.accepted()) return rejected(removal.reason());
            writes.putAll(removal.plan().writes());
        }

        for (var target : parts) {
            Cell root = CompositeData.cell(target.root());
            for (Cell offset : target.pose().footprint().keySet()) {
                Cell cell = root.add(offset);
                if (!access.isLoaded(cell) || !access.inBounds(cell)) {
                    return rejected("static_guest_cleanup_unloaded_cell:" + cell);
                }
                var before = virtual.read(cell);
                var state = CompositeData.state(before.data());
                if (!StaticArchitectureCatalog.contains(state)) continue;
                var fragments = StaticArchitectureCatalog.recipe(state).fragments();
                for (int i = 0; i < fragments.size(); i++) {
                    var fragment = fragments.get(i);
                    if (fragment.pose() != target.pose() || !cell.add(fragment.origin()).equals(root)) continue;
                    if (before.resident() != null || world.getBlockEntity(CompositeData.pos(cell)) != null) {
                        return rejected("static_guest_cleanup_has_block_entity:" + cell);
                    }
                    var next = StaticArchitectureCatalog.without(state, i);
                    var kind = CellSnapshot.Kind.FOREIGN;
                    if (next.isAir()) {
                        kind = before.guests().isEmpty() ? CellSnapshot.Kind.AIR : CellSnapshot.Kind.HELPER;
                        if (kind == CellSnapshot.Kind.HELPER) next = CompositeArchitecture.CELL.getDefaultState();
                    } else if (state.getBlock() instanceof StaticArchitectureBlock
                            && next.getBlock() instanceof StaticArchitectureBlock) {
                        access.authorizeStaticWrite(cell, next);
                    } else if (!next.isOf(Blocks.WATER)) {
                        return rejected("static_guest_cleanup_unsupported_carrier:" + cell);
                    }
                    writes.put(cell, new CellSnapshot(kind, CompositeData.data(next, new NbtCompound()),
                            null, before.guests()));
                    break;
                }
            }
        }
        if (writes.isEmpty() || expected.size() > 4096 || !NativePlayerBreak.allowCurrent(world, writes.keySet())) {
            return rejected("static_guest_cleanup_empty_protected_or_over_budget");
        }
        var plan = new TransactionCore.Plan(access.dimensionKey(), expected, writes, Map.of());
        var result = CompositeRuntime.execute(access, new TransactionCore.Preparation(plan, null));
        if (result.outcome() == TransactionCore.Outcome.COMMITTED) {
            for (Owner owner : guests) {
                MechanismLinks.removedArchitecture(world.getServer(), CompositeMechanismBridge.reference(world, owner));
            }
        }
        return result;
    }

    private static TransactionCore.Result rejected(String reason) {
        return new TransactionCore.Result(TransactionCore.Outcome.REJECTED, 0, reason);
    }
}
