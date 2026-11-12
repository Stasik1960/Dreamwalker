package dev.nightshift.skate;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

record GrindRail(BlockPos pos, Direction.Axis axis, double height) {
    private static final TagKey<Block> GRINDABLE = TagKey.of(RegistryKeys.BLOCK, NightShiftSkate.id("grindable"));
    private static final double ENTRY_TOLERANCE = 0.60;
    private static final double END_TOLERANCE = 0.28;

    record Entry(GrindRail rail, double along) {}

    static @Nullable GrindRail at(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (!state.isIn(GRINDABLE)) {
            return null;
        }
        boolean x = world.getBlockState(pos.west()).isIn(GRINDABLE) || world.getBlockState(pos.east()).isIn(GRINDABLE);
        boolean z = world.getBlockState(pos.north()).isIn(GRINDABLE) || world.getBlockState(pos.south()).isIn(GRINDABLE);
        if (x == z) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return null;
        }
        return new GrindRail(pos.toImmutable(), x ? Direction.Axis.X : Direction.Axis.Z,
                pos.getY() + shape.getMax(Direction.Axis.Y));
    }

    static @Nullable Entry findEntry(World world, Vec3d current, Vec3d travel) {
        double speed = travel.horizontalLength();
        if (travel.y >= 0 || speed < 0.10) {
            return null;
        }
        Vec3d end = current.add(travel);
        BlockPos min = BlockPos.ofFloored(Math.min(current.x, end.x) - ENTRY_TOLERANCE, end.y - 1.7,
                Math.min(current.z, end.z) - ENTRY_TOLERANCE);
        BlockPos max = BlockPos.ofFloored(Math.max(current.x, end.x) + ENTRY_TOLERANCE, current.y + 0.1,
                Math.max(current.z, end.z) + ENTRY_TOLERANCE);
        Entry best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : BlockPos.iterate(min, max)) {
            GrindRail rail = at(world, candidate);
            if (rail == null || current.y < rail.height - 0.08 || end.y > rail.height + 0.16
                    || Math.abs(rail.along(travel)) < speed * 0.59) {
                continue;
            }
            double fraction = MathHelper.clamp((rail.height - current.y) / travel.y, 0, 1);
            Vec3d touchdown = current.add(travel.multiply(fraction));
            double lateral = Math.abs(rail.cross(touchdown) - rail.line());
            double along = rail.along(touchdown);
            double overhang = Math.max(rail.start() - along, along - (rail.start() + 1));
            double distance = lateral + Math.max(0, overhang) + fraction * 0.01;
            if (lateral <= ENTRY_TOLERANCE && overhang <= END_TOLERANCE && distance < bestDistance) {
                best = new Entry(rail, MathHelper.clamp(along, rail.start() + 0.02, rail.start() + 0.98));
                bestDistance = distance;
            }
        }
        return best;
    }

    double along(Vec3d vector) {
        return axis == Direction.Axis.X ? vector.x : vector.z;
    }

    double cross(Vec3d vector) {
        return axis == Direction.Axis.X ? vector.z : vector.x;
    }

    int start() {
        return axis == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    double line() {
        return (axis == Direction.Axis.X ? pos.getZ() : pos.getX()) + 0.5;
    }

    Vec3d point(double along) {
        return axis == Direction.Axis.X ? new Vec3d(along, height, line()) : new Vec3d(line(), height, along);
    }

    @Nullable GrindRail ahead(World world, double along) {
        int destination = MathHelper.floor(along);
        int block = start();
        GrindRail candidate = atBlock(world, block);
        while (candidate != null && block != destination) {
            block += Integer.signum(destination - block);
            candidate = atBlock(world, block);
        }
        return candidate;
    }

    private @Nullable GrindRail atBlock(World world, int block) {
        BlockPos next = axis == Direction.Axis.X ? new BlockPos(block, pos.getY(), pos.getZ())
                : new BlockPos(pos.getX(), pos.getY(), block);
        GrindRail candidate = at(world, next);
        if (candidate == null || candidate.axis != axis || candidate.height != height || candidate.line() != line()) {
            return null;
        }
        return candidate;
    }
}
