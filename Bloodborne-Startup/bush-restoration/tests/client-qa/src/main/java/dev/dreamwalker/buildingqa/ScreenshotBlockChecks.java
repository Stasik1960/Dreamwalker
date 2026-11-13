package dev.dreamwalker.buildingqa;

import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Places the reviewed source items through the real block-item path. */
final class ScreenshotBlockChecks {
    private ScreenshotBlockChecks() {}

    static void verify(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos fixture = new BlockPos(434, 280, -830);
        float previousYaw = player.getYaw();
        player.setYaw(0);
        try {
            BlockPos glass = place(player, fixture, "source_light_blue_stained_glass", 0);
            require(solid(world, glass), "patterned blue glass has no collision");
            require(!solid(world, glass.up()) && world.getBlockState(glass.up()).isAir(),
                    "patterned blue glass still occupies the second block");
            BlockPos rail = place(player, fixture.east(3), "source_warped_trapdoor_open", 0);
            require(solid(world, rail) && solid(world, rail.up()), "balustrade collision misses the upper rail");
            BlockPos wall = place(player, fixture.east(6), "source_compatibility_fragment", 33);
            require(solid(world, wall), "compatibility wall 34 still has no collision");
            BlockPos pilaster = place(player, fixture.east(9), "source_dark_oak_trapdoor_open", 0);
            BlockPos upper = pilaster.up();
            var top = world.getBlockState(upper).getCollisionShape(world, upper);
            require(solid(world, pilaster) && !top.isEmpty()
                    && Math.abs(top.getMax(Direction.Axis.Y) - 15.0 / 16.0) < 1.0e-6
                    && !solid(world, pilaster.up(2)), "the pilaster body should reach 31/16 of a block from one placement");
        } finally {
            player.setYaw(previousYaw);
        }
    }

    private static BlockPos place(ServerPlayerEntity player, BlockPos support, String path, int variant) {
        ServerWorld world = player.getServerWorld();
        world.getChunk(support);
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        var id = new Identifier("bloodborne_dw", path);
        require(Registries.ITEM.get(id) instanceof BlockItem, "missing block item " + id);
        var item = (BlockItem) Registries.ITEM.get(id);
        var stack = new ItemStack(item);
        if (variant > 15) stack.getOrCreateSubNbt("BlockStateTag").putString("art_bank", Integer.toString(variant / 16));
        stack.getOrCreateSubNbt("BlockStateTag").putString("variant", Integer.toString(variant % 16));
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
        require(item.place(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit)).isAccepted(),
                "could not place " + id + " at " + support.up().toShortString());
        return support.up();
    }

    private static boolean solid(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
