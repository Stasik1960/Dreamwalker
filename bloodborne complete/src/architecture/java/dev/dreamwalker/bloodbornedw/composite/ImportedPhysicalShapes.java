package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Exact ordinary NEW cuboids. The protected legacy 16³ renderer remains
 * unchanged; it may outward-round old collisions, but must not enlarge the
 * newly simplified model-bounded physical cuboids. Selection stays legacy.
 */
public final class ImportedPhysicalShapes {
    private static final int MAX_CACHE = 8192;
    private static final Map<List<Box>, VoxelShape> CACHE = new ConcurrentHashMap<>();
    private ImportedPhysicalShapes() {}

    public static boolean isImported(Owner owner) {
        Identifier id = Identifier.tryParse(owner.registryId());
        return id != null && Registries.BLOCK.containsId(id)
                && Registries.BLOCK.get(id) instanceof CompositeRootBlock block
                && block.spec.simplePhysicalCollision;
    }
    public static VoxelShape exact(List<Box> boxes) {
        if (boxes.isEmpty()) return VoxelShapes.empty();
        VoxelShape cached = CACHE.get(boxes); if (cached != null) return cached;
        VoxelShape result = VoxelShapes.empty();
        for (Box box : boxes) result = VoxelShapes.union(result, VoxelShapes.cuboid(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        if (CACHE.size() >= MAX_CACHE) CACHE.clear();
        VoxelShape raced = CACHE.putIfAbsent(List.copyOf(boxes), result);
        return raced == null ? result : raced;
    }
    public static VoxelShape physical(CompositeData.Contribution entry, BlockPos cell) {
        var boxes = CompositeRuntime.physicalParts(entry, cell);
        return isImported(entry.owner()) ? exact(boxes) : CompositeRuntime.shape(boxes);
    }
    /** Optional opt-in: absent means execute the original complete old path. */
    public static Optional<VoxelShape> collision(BlockView world, BlockPos pos, BlockState fallback) {
        var entries = CompositeRuntime.contributions(world, pos);
        boolean imported = entries.stream().anyMatch(entry -> isImported(entry.owner()));
        if (entries.isEmpty() && fallback.getBlock() instanceof CompositeRootBlock block && block.spec.simplePhysicalCollision)
            return Optional.of(exact(block.spec.footprint(fallback, 0).get(Cell.ORIGIN).collision()));
        if (!imported) return Optional.empty();
        List<Box> legacy = new ArrayList<>(), exact = new ArrayList<>();
        for (var entry : entries) if (validOrDeferred(world, entry.owner())) {
            (isImported(entry.owner()) ? exact : legacy).addAll(CompositeRuntime.physicalParts(entry, pos));
        }
        return Optional.of(VoxelShapes.union(CompositeRuntime.shape(legacy), exact(exact)));
    }
    private static boolean validOrDeferred(BlockView view, Owner owner) {
        if (CompositeShapeSnapshots.worker(view)) return CompositeShapeSnapshots.validOrDeferred(view, owner);
        if (!(view instanceof World world) || !world.isChunkLoaded(CompositeData.pos(owner.root()))) return true;
        var entity = world.getBlockEntity(CompositeData.pos(owner.root()));
        boolean valid = entity instanceof CompositeBlockEntity root && owner.equals(root.resident());
        if (!valid && world instanceof ServerWorld server) CompositeRuntime.scheduleCleanup(server, CompositeData.pos(owner.root()));
        return valid;
    }
}
