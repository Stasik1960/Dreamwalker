package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** New objects only: physical boxes also define selection. A wholly passable
 * pose keeps one ordinary model-bounded AABB for picking, never player physics.
 * Saved ownership, original artwork and all protected V12 shapes stay unchanged.
 */
public final class ImportedSelectionShapes {
    private record Key(CompositeSpec spec, BlockState state, double mount, CompositeSourceShift.Shift shift) {}
    private static final Map<Key, Map<Cell, List<Box>>> CACHE = new ConcurrentHashMap<>();
    private ImportedSelectionShapes() {}

    public static Map<Cell, List<Box>> footprint(CompositeRootBlock block, BlockState state, NbtCompound payload) {
        if (!block.spec.simplePhysicalCollision) throw new IllegalArgumentException("Simple selection is restricted to new objects");
        double mount = payload.getDouble("MountY") + payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        var shift = CompositeSourceShift.world(state, payload);
        Key key = new Key(block.spec, state, mount, shift);
        var cached = CACHE.get(key); if (cached != null) return cached;
        var physical = CompositeSourceShift.footprint(block.spec, state, payload);
        Map<Cell, List<Box>> result = new TreeMap<>();
        if (physical.values().stream().anyMatch(part -> !part.collision().isEmpty())) {
            physical.forEach((cell, part) -> { if (!part.collision().isEmpty()) result.put(cell, part.collision()); });
        } else {
            var bounds = block.spec.pose(state).selection();
            if (!bounds.isEmpty()) {
                double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
                double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
                for (var box : bounds) {
                    x0 = Math.min(x0, box.from().x()/16); y0 = Math.min(y0, box.from().y()/16); z0 = Math.min(z0, box.from().z()/16);
                    x1 = Math.max(x1, box.to().x()/16); y1 = Math.max(y1, box.to().y()/16); z1 = Math.max(z1, box.to().z()/16);
                }
                var simple = new ImportedCollisionMath.Box(x0, y0, z0, x1, y1, z1);
                for (var box : ImportedCollisionMath.transform(List.of(simple), state.get(CompositeRootBlock.ROTATION), mount, false, shift.x(), shift.y(), shift.z()))
                    clip(box, result);
            }
        }
        Map<Cell, List<Box>> immutable = Collections.unmodifiableMap(result);
        if (CACHE.size() >= 8192) CACHE.clear();
        var raced = CACHE.putIfAbsent(key, immutable); return raced == null ? immutable : raced;
    }

    private static void clip(ImportedCollisionMath.Box box, Map<Cell, List<Box>> result) {
        for (int x=(int)Math.floor(box.x0()+1e-10); x<Math.ceil(box.x1()-1e-10); x++)
            for (int y=(int)Math.floor(box.y0()+1e-10); y<Math.ceil(box.y1()-1e-10); y++)
                for (int z=(int)Math.floor(box.z0()+1e-10); z<Math.ceil(box.z1()-1e-10); z++) {
                    double a=Math.max(0,box.x0()-x), b=Math.max(0,box.y0()-y), c=Math.max(0,box.z0()-z);
                    double d=Math.min(1,box.x1()-x), e=Math.min(1,box.y1()-y), f=Math.min(1,box.z1()-z);
                    if (d>a && e>b && f>c) result.put(new Cell(x,y,z), List.of(new Box(a,b,c,d,e,f)));
                }
    }

    public static Optional<List<Box>> parts(CompositeData.Contribution entry, BlockPos cell) {
        if (!ImportedPhysicalShapes.isImported(entry.owner())) return Optional.empty();
        BlockState state = CompositeData.state(entry.rootData());
        if (!(state.getBlock() instanceof CompositeRootBlock block) || !block.spec.id.toString().equals(entry.owner().registryId()))
            throw new IllegalStateException("Simple selection owner/state mismatch");
        return Optional.of(footprint(block, state, CompositeData.nbt(entry.rootData().blockEntityNbt()))
                .getOrDefault(CompositeData.cell(cell).subtract(entry.owner().root()), List.of()));
    }

    public static Optional<VoxelShape> outline(BlockView world, BlockPos pos, BlockState fallback, ShapeContext context) {
        var entries = CompositeRuntime.contributions(world, pos);
        if (entries.isEmpty() && fallback.getBlock() instanceof CompositeRootBlock block && block.spec.simplePhysicalCollision)
            return Optional.of(ImportedPhysicalShapes.exact(footprint(block, fallback, new NbtCompound()).getOrDefault(Cell.ORIGIN, List.of())));
        if (entries.stream().noneMatch(entry -> ImportedPhysicalShapes.isImported(entry.owner()))) return Optional.empty();
        List<Box> legacy = new ArrayList<>(), simple = new ArrayList<>();
        for (var entry : entries) if (validOrDeferred(world, entry.owner())) {
            var selected = parts(entry, pos);
            if (selected.isPresent()) simple.addAll(selected.get());
            else legacy.addAll(CompositeRuntime.contextSelection(world, entry, pos, context));
        }
        return Optional.of(VoxelShapes.union(CompositeRuntime.shape(legacy), ImportedPhysicalShapes.exact(simple)));
    }
    private static boolean validOrDeferred(BlockView view, dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner) {
        if (CompositeShapeSnapshots.worker(view)) return CompositeShapeSnapshots.validOrDeferred(view, owner);
        if (!(view instanceof World world) || !world.isChunkLoaded(CompositeData.pos(owner.root()))) return true;
        var entity = world.getBlockEntity(CompositeData.pos(owner.root()));
        boolean valid = entity instanceof CompositeBlockEntity root && owner.equals(root.resident());
        if (!valid && world instanceof ServerWorld server) CompositeRuntime.scheduleCleanup(server, CompositeData.pos(owner.root()));
        return valid;
    }
}
