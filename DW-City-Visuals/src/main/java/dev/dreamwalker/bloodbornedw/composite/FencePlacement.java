package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Separate a fence's construction volume from the full-height movement collider. */
public final class FencePlacement {
    private static final VoxelShape UPPER_HALF = VoxelShapes.cuboid(-4, 1, -4, 5, 5, 5);
    private static final Map<VoxelShape, VoxelShape> CAPPED = new ConcurrentHashMap<>();
    private static final Map<VoxelShape, VoxelShape> MOVEMENT = new ConcurrentHashMap<>();

    private FencePlacement() {}

    public static boolean isFence(BlockState state) {
        if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock) return true;
        if (state.getBlock() instanceof PrototypeWallBlock) return true;
        if (state.getBlock() instanceof SourceFallbackCarrierV1271) return isFence(SourceFallbackCarrierV1271.source(state));
        var id = Registries.BLOCK.getId(state.getBlock());
        return id.getNamespace().equals("bloodborne_dw") && (id.getPath().contains("_fence")
                || id.getPath().contains("balustrade") || AutoStoneFence.matches(state)
                || id.getPath().equals("source_assembly_brown_stained_glass_pane"));
    }

    public static VoxelShape cap(VoxelShape shape) {
        if (shape.isEmpty() || shape.getMax(net.minecraft.util.math.Direction.Axis.Y) <= 1) return shape;
        if (CAPPED.size() >= 512) return VoxelShapes.combineAndSimplify(shape, UPPER_HALF, BooleanBiFunction.ONLY_FIRST);
        return CAPPED.computeIfAbsent(shape,
                value -> VoxelShapes.combineAndSimplify(value, UPPER_HALF, BooleanBiFunction.ONLY_FIRST));
    }

    /** Older saved fence poses can still contain one-block collision boxes. */
    public static VoxelShape movement(VoxelShape shape) {
        if (shape.isEmpty() || shape.getMax(net.minecraft.util.math.Direction.Axis.Y) >= 1.5) return shape;
        if (MOVEMENT.size() >= 512) return extendMovement(shape);
        return MOVEMENT.computeIfAbsent(shape, FencePlacement::extendMovement);
    }

    private static VoxelShape extendMovement(VoxelShape shape) {
        VoxelShape result = shape;
        for (Box box : shape.getBoundingBoxes()) {
            result = VoxelShapes.union(result, VoxelShapes.cuboid(box.minX, 0, box.minZ, box.maxX, 1.5, box.maxZ));
        }
        return result;
    }

    public static VoxelShape nativeShape(World world, BlockPos pos, VoxelShape shape) {
        if (DebugAdjustments.blocksDisabled(world, pos)) return VoxelShapes.empty();
        if (shape.isEmpty() || shape.getMax(net.minecraft.util.math.Direction.Axis.Y) <= 1) return shape;
        return isFence(world.getBlockState(pos)) ? cap(shape) : shape;
    }

    public static List<ObjectGeometry.Box> existingParts(World world, CompositeData.Contribution entry,
            BlockPos cell, List<ObjectGeometry.Box> parts) {
        if (parts.isEmpty() || DebugAdjustments.blocksDisabled(world, entry.owner())) return List.of();
        return overFence(entry, cell) ? List.of() : parts;
    }

    public static boolean overFence(CompositeData.Contribution entry, BlockPos cell) {
        return cell.getY() > entry.owner().root().y() && isFence(CompositeData.state(entry.rootData()));
    }

    public static List<Box> proposedBoxes(World world, ObjectInstance instance, List<Box> boxes) {
        if (boxes.isEmpty() || DebugAdjustments.blocksDisabled(world, instance.owner())) return List.of();
        if (!isFence(CompositeData.state(instance.rootData()))) return boxes;
        return cap(boxes, instance.owner().root().y() + 1);
    }

    public static List<Box> cap(List<Box> boxes, double ceiling) {
        List<Box> clipped = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            if (box.minY >= ceiling - PlacementPhysics.EPS) continue;
            clipped.add(box.maxY <= ceiling ? box :
                    new Box(box.minX, box.minY, box.minZ, box.maxX, ceiling, box.maxZ));
        }
        return clipped;
    }
}
