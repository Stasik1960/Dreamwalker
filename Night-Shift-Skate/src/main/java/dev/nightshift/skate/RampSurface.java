package dev.nightshift.skate;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Continuous ramp surface sampled at the leading edge of the board's collision box. */
record RampSurface(double height, double centerHeight, Direction uphill, double rise) {
    private static final double EDGE_EPSILON = 0.00001;

    double grade(Vec3d heading) {
        return rise * (heading.x * uphill.getOffsetX() + heading.z * uphill.getOffsetZ());
    }

    static boolean crosses(World world, Box footprint, Vec3d travel) {
        Box sweep = footprint.stretch(travel.x, 0, travel.z);
        for (BlockPos pos : candidates(sweep, footprint.minY - 1.0, footprint.minY + 0.35)) {
            if (world.getBlockState(pos).getBlock() instanceof SkateRampBlock) {
                return true;
            }
        }
        return false;
    }

    static @Nullable RampSurface under(World world, Box footprint, double minHeight, double maxHeight) {
        RampSurface best = null;
        double centerX = (footprint.minX + footprint.maxX) * 0.5;
        double centerZ = (footprint.minZ + footprint.maxZ) * 0.5;
        for (BlockPos pos : candidates(footprint, minHeight - 1.0, maxHeight)) {
            var state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof SkateRampBlock ramp)) continue;

            Direction uphill = state.get(SkateRampBlock.FACING);
            double edgeX = switch (uphill) {
                case EAST -> footprint.maxX;
                case WEST -> footprint.minX;
                default -> centerX;
            };
            double edgeZ = switch (uphill) {
                case SOUTH -> footprint.maxZ;
                case NORTH -> footprint.minZ;
                default -> centerZ;
            };
            double height = ramp.heightAt(state, pos, edgeX, edgeZ);
            if (height < minHeight || height > maxHeight || best != null && height <= best.height()) continue;
            best = new RampSurface(height, ramp.heightAt(state, pos, centerX, centerZ), uphill, ramp.rise());
        }
        return best;
    }

    private static Iterable<BlockPos> candidates(Box footprint, double minY, double maxY) {
        return BlockPos.iterate(
                MathHelper.floor(footprint.minX + EDGE_EPSILON), MathHelper.floor(minY),
                MathHelper.floor(footprint.minZ + EDGE_EPSILON),
                MathHelper.floor(footprint.maxX - EDGE_EPSILON), MathHelper.floor(maxY),
                MathHelper.floor(footprint.maxZ - EDGE_EPSILON));
    }
}
