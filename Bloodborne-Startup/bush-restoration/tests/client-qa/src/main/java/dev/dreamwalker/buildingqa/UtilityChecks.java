package dev.dreamwalker.buildingqa;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Exercises the integrated server's real player placement and destruction packet handlers. */
final class UtilityChecks {
    private static final List<String> UTILITIES = List.of(
            "dw_darkness", "dw_whiteness", "dw_faerie_veil", "dw_light", "dw_barrier");

    private UtilityChecks() {}

    static void verify(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos support = new BlockPos(444, 282, -850);
        BlockPos target = support.up();
        var pickaxeId = new Identifier("bloodborne_dw", "nrp_pickaxe");
        require(Registries.ITEM.containsId(pickaxeId), "NRP pickaxe is missing");
        Item nrpPickaxe = Registries.ITEM.get(pickaxeId);
        GameMode originalMode = player.interactionManager.getGameMode();
        Vec3d originalPosition = player.getPos();
        float originalYaw = player.getYaw();
        float originalPitch = player.getPitch();
        ItemStack originalStack = player.getMainHandStack().copy();
        world.getChunk(support);
        var originalSupport = world.getBlockState(support);
        var originalTarget = world.getBlockState(target);
        try {
            require(originalTarget.isAir(), "utility fixture needs empty air at " + target);
            world.setBlockState(support, Blocks.STONE.getDefaultState());
            player.teleport(world, support.getX() + .5, support.getY() + 1, support.getZ() - 2.5, 0, 20);
            for (String path : UTILITIES) {
                var id = new Identifier("bloodborne_dw", path);
                require(Registries.ITEM.get(id) instanceof BlockItem, id + " has no block item");
                BlockItem item = (BlockItem) Registries.ITEM.get(id);
                player.changeGameMode(GameMode.CREATIVE);
                ItemStack stack = new ItemStack(item);
                player.setStackInHand(Hand.MAIN_HAND, stack);
                require(item.place(context(player, stack, support)).isAccepted(), id + " failed creative placement");
                require(world.getBlockState(target).isOf(item.getBlock()), id + " placement landed outside target");
                verifyProtection(player, world, target, item.getBlock(), nrpPickaxe);
                player.changeGameMode(GameMode.CREATIVE);
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(nrpPickaxe));
                act(player, target, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
                require(world.getBlockState(target).isAir(), id + " survived a creative-mode attack");
            }
        } finally {
            world.setBlockState(target, originalTarget);
            world.setBlockState(support, originalSupport);
            player.setStackInHand(Hand.MAIN_HAND, originalStack);
            player.changeGameMode(originalMode);
            player.teleport(world, originalPosition.x, originalPosition.y, originalPosition.z, originalYaw, originalPitch);
        }
    }

    private static void verifyProtection(ServerPlayerEntity player, ServerWorld world, BlockPos target, Block block,
            Item nrpPickaxe) {
        var state = world.getBlockState(target);
        String name = Registries.BLOCK.getId(block).toString();
        require(state.getHardness(world, target) == -1 && block.getBlastResistance() >= 3_600_000,
                name + " has weaker hardness or blast resistance than bedrock");
        require(state.isIn(BlockTags.WITHER_IMMUNE) && state.isIn(BlockTags.DRAGON_IMMUNE),
                name + " is missing the wither/dragon protection tags");
        require(!state.canBucketPlace(Fluids.WATER) && !state.canBucketPlace(Fluids.LAVA),
                name + " can be replaced with a bucket");

        for (GameMode mode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
            player.changeGameMode(mode);
            require(!state.canReplace(context(player, new ItemStack(Items.STONE), target)),
                    name + " is replaceable by ordinary placement in " + mode);
            for (ItemStack stack : List.of(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_PICKAXE),
                    new ItemStack(nrpPickaxe))) {
                player.setStackInHand(Hand.MAIN_HAND, stack);
                act(player, target, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
                act(player, target, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
                act(player, target, PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK);
                require(world.getBlockState(target).isOf(block), name + " was mined in " + mode);
                require(!player.interactionManager.tryBreakBlock(target) && world.getBlockState(target).isOf(block),
                        name + " was removed by direct player break in " + mode);
            }
        }
    }

    private static ItemPlacementContext context(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, .5, 0), Direction.UP, pos, false);
        return new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit);
    }

    private static void act(ServerPlayerEntity player, BlockPos pos, PlayerActionC2SPacket.Action action) {
        player.interactionManager.processBlockBreakingAction(pos, action, Direction.UP,
                player.getServerWorld().getTopY(), 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
