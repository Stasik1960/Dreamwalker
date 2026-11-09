package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class StudioStaticRemoval {
    private StudioStaticRemoval() {}

    public static Map<Cell, Footprint> members(ServerWorld world, BlockPos root, StaticArchitectureCatalog.Pose pose) {
        var stock = pose.footprint();
        var layout = StudioFootprints.staticPose(pose);
        if (layout == null) return stock;
        Map<Cell, Footprint> result = new TreeMap<>(stock);
        for (Cell offset : layout.storage().keySet()) {
            if (result.containsKey(offset)) continue;
            BlockPos cell = root.add(offset.x(), offset.y(), offset.z());
            var chunk = world.getChunkManager().getWorldChunk(cell.getX() >> 4, cell.getZ() >> 4);
            if (chunk == null) {
                result.put(offset, Footprint.EMPTY);
                continue;
            }
            var state = chunk.getBlockState(cell);
            if (!StaticArchitectureCatalog.contains(state)) continue;
            var source = new Cell(-offset.x(), -offset.y(), -offset.z());
            if (StaticArchitectureCatalog.recipe(state).fragments().stream()
                    .anyMatch(fragment -> fragment.pose() == pose && fragment.origin().equals(source)))
                result.put(offset, Footprint.EMPTY);
        }
        return result;
    }

    public static TransactionCore.Result withAuthoredMembers(ServerWorld world, BlockPos root, StaticArchitectureCatalog.Pose pose,
            boolean cleanup) {
        var rootCell = CompositeData.cell(root);
        TreeSet<Cell> touched = new TreeSet<>();
        boolean guests = false;
        for (Cell offset : members(world, root, pose).keySet()) {
            Cell cell = rootCell.add(offset);
            if (world.getChunkManager().getWorldChunk(cell.x() >> 4, cell.z() >> 4) == null) return null;
            touched.add(cell);
            guests |= !CompositeRuntime.contributions(world, CompositeData.pos(cell)).isEmpty();
        }
        if (!guests && StudioFootprints.staticPose(pose) == null
                || !cleanup && !StaticArchitectureCatalog.matchesRoot(world.getBlockState(root), pose)) return null;
        FabricCompositeWorld access = new FabricCompositeWorld(world, Map.of()) {
            @Override public boolean writeSilently(Cell cell, CellSnapshot next) {
                var state = CompositeData.state(next.data());
                if (state.isOf(Blocks.WATER)) world.setBlockState(CompositeData.pos(cell), state, Block.FORCE_STATE | Block.SKIP_DROPS);
                return super.writeSilently(cell, next);
            }
        };
        Map<Cell, CellSnapshot> expected = new TreeMap<>(), writes = new TreeMap<>();
        for (Cell cell : touched) {
            var snapshot = access.read(cell);
            var state = CompositeData.state(snapshot.data());
            if (!StaticArchitectureCatalog.contains(state)) {
                if (!cleanup) return null;
                continue;
            }
            var recipe = StaticArchitectureCatalog.recipe(state);
            for (int index = 0; index < recipe.fragments().size(); index++) {
                var fragment = recipe.fragments().get(index);
                if (fragment.pose() != pose || !cell.add(fragment.origin()).equals(rootCell)) continue;
                var next = StaticArchitectureCatalog.without(state, index);
                var kind = CellSnapshot.Kind.FOREIGN;
                if (next.isAir()) {
                    kind = CellSnapshot.Kind.AIR;
                    if (!snapshot.guests().isEmpty()) {
                        kind = CellSnapshot.Kind.HELPER;
                        next = CompositeArchitecture.CELL.getDefaultState();
                    }
                } else if (StaticArchitectureCatalog.contains(next)) {
                    access.authorizeStaticWrite(cell, next);
                }
                expected.put(cell, snapshot);
                writes.put(cell, new CellSnapshot(kind, CompositeData.data(next, new NbtCompound()), null, snapshot.guests()));
                break;
            }
        }
        if (writes.isEmpty()) return null;
        return CompositeRuntime.execute(access, new TransactionCore.Preparation(
                new TransactionCore.Plan(access.dimensionKey(), expected, writes, Map.of()), null));
    }
}
