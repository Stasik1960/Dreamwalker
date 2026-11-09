package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ForcedCompositePlacement {
    public static final String INDEPENDENT_SUPPORT = "DwIndependentSupport";
    private static final int SEARCH_RADIUS = 4;

    private ForcedCompositePlacement() {}

    public static ObjectInstance instance(ServerWorld world, BlockPos requested, BlockState state, UUID id,
            NbtCompound payload, PlayerEntity player) {
        var block = (CompositeRootBlock) state.getBlock();
        if (block.spec.requiredSupport) payload.putBoolean(INDEPENDENT_SUPPORT, true);
        if (!eligible(world, requested, player)) {
            return CompositeRuntime.instance(world, requested, state, id, payload);
        }
        var existing = world.getBlockState(requested);
        if (free(existing) || DwUtilityBlocks.marker(existing)) {
            return CompositeRuntime.instance(world, requested, state, id, payload);
        }

        boolean glazing = block instanceof ThinWindowRootBlock && payload.getBoolean("GlazingMounted");
        BlockPos root = nearbyRoot(world, requested, player, glazing);
        if (root.equals(requested)) return CompositeRuntime.instance(world, root, state, id, payload);

        int y = requested.getY() - root.getY();
        if (glazing) {
            payload.putDouble("MountY", payload.getDouble("MountY") + y);
            payload.putDouble("GlazingSurfaceY", payload.getDouble("GlazingSurfaceY") + y);
        } else {
            var delta = new CompositeSourceShift.Shift(requested.getX() - root.getX(), y,
                    requested.getZ() - root.getZ()).rotate(-state.get(CompositeRootBlock.ROTATION));
            var shift = CompositeSourceShift.local(payload);
            double x = shift.x() + delta.x(), z = shift.z() + delta.z(), height = shift.y() + delta.y();
            if (Math.abs(x) > 32 || Math.abs(height) > 32 || Math.abs(z) > 32)
                return CompositeRuntime.instance(world, requested, state, id, payload);
            var position = new NbtList();
            position.add(NbtDouble.of(x));
            position.add(NbtDouble.of(height));
            position.add(NbtDouble.of(z));
            payload.put("SourceShift", position);
        }
        return CompositeRuntime.instance(world, root, state, id, payload);
    }

    private static BlockPos nearbyRoot(ServerWorld world, BlockPos requested, PlayerEntity player, boolean glazing) {
        int horizontal = glazing ? 0 : SEARCH_RADIUS;
        for (var candidate : BlockPos.iterateOutwards(requested, horizontal, SEARCH_RADIUS, horizontal)) {
            int distance = candidate.getManhattanDistance(requested);
            if (distance > SEARCH_RADIUS) break;
            if (distance == 0 || !available(world, candidate, player)) continue;
            // Prefer a vertical move when equally close, so later yaw still pivots around the same X/Z.
            var above = requested.up(distance);
            if (available(world, above, player)) return above;
            var below = requested.down(distance);
            if (available(world, below, player)) return below;
            return candidate.toImmutable();
        }
        return requested;
    }

    private static boolean eligible(ServerWorld world, BlockPos pos, PlayerEntity player) {
        return player != null && player.getAbilities().allowModifyWorld && world.isInBuildLimit(pos)
                && world.getWorldBorder().contains(pos) && world.isChunkLoaded(pos) && world.canPlayerModifyAt(player, pos);
    }

    private static boolean available(ServerWorld world, BlockPos pos, PlayerEntity player) {
        return eligible(world, pos, player) && free(world.getBlockState(pos));
    }

    private static boolean free(BlockState state) {
        return state.isOf(Blocks.AIR) || state.isOf(Blocks.CAVE_AIR) || state.isOf(Blocks.VOID_AIR)
                || state.isOf(CompositeArchitecture.CELL);
    }
}
