package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.building.mixin.CompositeShapeUpdateAccess;
import dev.dreamwalker.bloodbornedw.building.mixin.ImportedFenceAccess;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Support surfaces of dynamic DreamWalker carriers. */
public final class BuildingNeighbors {
    private static final Direction[] SIDES = Direction.values();
    private static final VoxelShape STANDING = Block.createCuboidShape(7, 0, 7, 9, 16, 9);
    private static final VoxelShape WALL_X = Block.createCuboidShape(0, 6, 7, 16, 10, 9);
    private static final VoxelShape WALL_Z = Block.createCuboidShape(7, 6, 0, 9, 10, 16);

    private BuildingNeighbors() {}

    public static boolean dreamWalker(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw");
    }

    public static boolean acceptsFenceNeighbor(BlockView world, BlockPos pos, BlockState neighbor, Block source) {
        return neighbor.isOf(source) || !sameIdFence(world, pos, neighbor);
    }

    public static boolean sameIdFence(BlockView world, BlockPos pos, BlockState state) {
        if (!dreamWalker(state)) return false;
        var properties = state.getBlock().getStateManager();
        var connections = properties.getProperty("connections");
        var wallAxis = properties.getProperty("wall_axis");
        return (connections != null && state.get(connections) instanceof StringIdentifiable value
                        && value.asString().equals("same_id"))
                || (wallAxis != null && state.get(wallAxis) instanceof StringIdentifiable axis
                        && axis.asString().equals("same_id"))
                || world instanceof ServerWorld server && ImportedFenceAccess.dreamwalker$matches(state)
                        && ImportedFenceAccess.dreamwalker$sameIdOnly(server, pos);
    }

    public static boolean fullFace(BlockState state, BlockView view, BlockPos pos, Direction face) {
        return Block.isFaceFullSquare(supportShape(state, view, pos), face);
    }

    public static boolean signSupport(BlockView view, BlockPos pos, Direction face) {
        BlockState support = view.getBlockState(pos);
        if (!dreamWalker(support)) return support.isSolid();
        VoxelShape required = switch (face.getAxis()) {
            case X -> WALL_X;
            case Y -> STANDING;
            case Z -> WALL_Z;
        };
        return !VoxelShapes.matchesAnywhere(supportShape(support, view, pos).getFace(face),
                required, BooleanBiFunction.ONLY_SECOND);
    }

    private static VoxelShape supportShape(BlockState state, BlockView view, BlockPos pos) {
        VoxelShape collision = state.getCollisionShape(view, pos, ShapeContext.absent());
        return VoxelShapes.combineAndSimplify(collision, VoxelShapes.fullCube(), BooleanBiFunction.AND);
    }

    public static void publishShapeChanges(ServerWorld world, Iterable<Cell> cells) {
        for (Cell cell : cells) {
            BlockPos pos = CompositeData.pos(cell);
            if (hasShapeNeighbor(world, pos)) {
                CompositeShapeUpdateAccess.dreamwalker$queue(world, "building-support:" + pos.asLong(),
                        () -> updateShapeNeighbors(world, pos));
            }
        }
    }

    private static boolean hasShapeNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction side : SIDES) {
            BlockPos neighbor = pos.offset(side);
            if (world.isChunkLoaded(neighbor) && !world.getBlockState(neighbor).isAir()) return true;
        }
        return false;
    }

    private static void updateShapeNeighbors(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return;
        BlockState state = world.getBlockState(pos);
        for (Direction side : SIDES) {
            BlockPos neighbor = pos.offset(side);
            // Third-party blocks can implement directional updates without the vanilla fence classes.
            if (world.isChunkLoaded(neighbor) && !world.getBlockState(neighbor).isAir()) {
                world.replaceWithStateForNeighborUpdate(side.getOpposite(), state, neighbor, pos,
                        Block.NOTIFY_LISTENERS, 32);
            }
        }
    }
}
