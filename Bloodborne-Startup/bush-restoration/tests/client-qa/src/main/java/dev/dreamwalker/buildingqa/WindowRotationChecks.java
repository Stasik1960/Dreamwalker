package dev.dreamwalker.buildingqa;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

final class WindowRotationChecks {
    static final BlockPos OPEN_WINDOW = new BlockPos(437, 276, -850);

    static void verify(ServerPlayerEntity player) throws ReflectiveOperationException {
        var world = player.getServerWorld();
        Vec3d previous = player.getPos();
        float yaw = player.getYaw(), pitch = player.getPitch();
        player.changeGameMode(GameMode.CREATIVE);
        try {
            place(player, OPEN_WINDOW, "source_shutter_window", 0, false);
            var closed = OPEN_WINDOW.east(4);
            var diagonal = OPEN_WINDOW.east(8);
            place(player, closed, "source_shutter_window", 44, false);
            place(player, diagonal, "source_shutter_window", 44, true);
            require(rotation(world.getBlockState(OPEN_WINDOW)) == 0 && rotation(world.getBlockState(closed)) == 0,
                    "window cardinal orientation changed with diagonal gaze");
            require(rotation(world.getBlockState(diagonal)) == 1, "Shift did not place the window at 45 degrees");

            for (BlockPos pos : new BlockPos[]{OPEN_WINDOW, closed}) {
                var state = world.getBlockState(pos);
                var selection = state.getOutlineShape(world, pos);
                require(!selection.isEmpty() && Math.abs(selection.getMin(Direction.Axis.Y)) < 1e-6,
                        "window selection is not flush with the block at " + pos);
            }
            player.setSneaking(false);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.refreshPositionAndAngles(OPEN_WINDOW.getX() + .5, OPEN_WINDOW.getY() + 1.25 - player.getStandingEyeHeight(),
                    OPEN_WINDOW.getZ() + 3.5, 180, 0);
            var hit = (BlockHitResult) player.raycast(5, 1, false);
            var action = player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            require(action.isAccepted() && world.getBlockState(OPEN_WINDOW).get(Properties.OPEN),
                    "player could not open the shutter, hit " + hit.getBlockPos() + ", action " + action + ", " + transactionReason());
            var openSelection = world.getBlockState(OPEN_WINDOW).getOutlineShape(world, OPEN_WINDOW);
            require(!openSelection.isEmpty() && Math.abs(openSelection.getMin(Direction.Axis.Y)) < 1e-6,
                    "open window selection is not flush with the block");
            player.changeGameMode(GameMode.SURVIVAL);
            double x = OPEN_WINDOW.getX() + .5;
            double y = OPEN_WINDOW.getY() + 6.0 / 16;
            double z = OPEN_WINDOW.getZ() + .25;
            player.refreshPositionAndAngles(x, y, z, 0, 0);
            player.setPose(EntityPose.STANDING);
            player.move(MovementType.SELF, new Vec3d(0, 0, 1.5));
            require(player.getZ() < OPEN_WINDOW.getZ() + 1, "standing player passed through the open glass");
            player.refreshPositionAndAngles(x, y, z, 0, 0);
            player.setPose(EntityPose.SWIMMING);
            player.move(MovementType.SELF, new Vec3d(0, 0, 1.5));
            require(player.getZ() > OPEN_WINDOW.getZ() + 1, "crawl-sized player could not pass beneath the open glass");
            require(!world.getBlockState(OPEN_WINDOW).getCollisionShape(world, OPEN_WINDOW, ShapeContext.absent()).isEmpty(),
                    "open window lost placement collision");
            player.changeGameMode(GameMode.CREATIVE);
            var rotated = OPEN_WINDOW.east(16);
            place(player, rotated, "source_standard_lantern", 44, false);
            require(rotation(world.getBlockState(rotated)) % 2 == 0, "ordinary diagonal gaze placed a lantern diagonally");
        } finally {
            player.setSneaking(false);
            player.setPose(EntityPose.STANDING);
            player.changeGameMode(GameMode.CREATIVE);
            player.refreshPositionAndAngles(previous.x, previous.y, previous.z, yaw, pitch);
        }
    }

    private static int rotation(net.minecraft.block.BlockState state) {
        var property = state.getBlock().getStateManager().getProperty("rotation");
        if (property == null || !(state.get(property) instanceof Integer value)) {
            throw new AssertionError("missing rotation in " + state);
        }
        return value;
    }

    private static void place(ServerPlayerEntity player, BlockPos pos, String path, float yaw, boolean shift)
            throws ReflectiveOperationException {
        var world = player.getServerWorld();
        for (int x = -3; x <= 3; x += 6) {
            for (int z = -3; z <= 3; z += 6) world.getChunk(pos.add(x, 0, z));
        }
        require(world.getBlockState(pos).isAir(), "QA fixture must start in air at " + pos + ": " + world.getBlockState(pos));
        boolean shutter = path.equals("source_shutter_window");
        BlockPos support = shutter ? pos.north() : pos.down();
        Direction face = shutter ? Direction.SOUTH : Direction.UP;
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        player.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + 3.5, yaw, 0);
        player.setSneaking(shift);
        var stack = new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw", path)));
        var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(-face.getOffsetX() * .5, -face.getOffsetY() * .5,
                -face.getOffsetZ() * .5), face, support, false);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var context = new ItemPlacementContext(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        var action = ((BlockItem) stack.getItem()).place(context);
        require(action.isAccepted(), "could not place " + path + " at " + context.getBlockPos()
                + ", action " + action + ", " + transactionReason());
        if (shutter) world.removeBlock(support, false);
    }

    private static String transactionReason() throws ReflectiveOperationException {
        Class<?> runtime = Class.forName("dev.dreamwalker.bloodbornedw.composite.CompositeRuntime");
        Object transaction = runtime.getMethod("lastResult").invoke(null);
        return transaction == null ? "no placement transaction" : transaction.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
