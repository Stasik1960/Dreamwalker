package dev.nightshift.skate;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

/** Server-side regression tests for the movement state machine and rail entry. */
public final class SkateboardGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_primary_loop")
    public void survivalPlacementMountDismountAndCrouchPickupRoundTrip(TestContext context) {
        verifyBoardRoundTrip(context, NightShiftSkate.SKATEBOARD, BoardStyle.NIGHT_SHIFT, false);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_models")
    public void ironVespersKeepsItsItemAndModelAfterLoading(TestContext context) {
        verifyBoardRoundTrip(context, NightShiftSkate.IRON_VESPERS, BoardStyle.IRON_VESPERS, true);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_models")
    public void graveProcessionKeepsItsItemAndModelAfterLoading(TestContext context) {
        verifyBoardRoundTrip(context, NightShiftSkate.GRAVE_PROCESSION, BoardStyle.GRAVE_PROCESSION, true);
    }

    private static void verifyBoardRoundTrip(TestContext context, Item item, BoardStyle style, boolean reload) {
        BlockPos relativeSurface = new BlockPos(2, 1, 2);
        context.setBlockState(relativeSurface, Blocks.SMOOTH_STONE);
        BlockPos surface = context.getAbsolutePos(relativeSurface);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        player.getAbilities().allowModifyWorld = true;
        player.refreshPositionAndAngles(surface.getX() + 0.5, surface.getY() + 1.0,
                surface.getZ() - 0.5, 0.0F, 0.0F);
        ItemStack held = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, held);

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(surface).add(0, 0.5, 0),
                Direction.UP, surface, false);
        ActionResult placement = item.useOnBlock(
                new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.assertTrue(placement.isAccepted(), "right-clicking a solid top face must place the held skateboard");
        context.assertTrue(held.isEmpty(), "survival placement must consume exactly the held board");

        var boards = context.getWorld().getEntitiesByType(NightShiftSkate.SKATEBOARD_ENTITY,
                new Box(surface).expand(2.0), entity -> true);
        context.assertTrue(boards.size() == 1, "placement must create one board entity, got " + boards.size());
        SkateboardEntity board = boards.get(0);
        context.assertTrue(board.getStyle() == style, "placement must track the held board's style");
        if (reload) {
            NbtCompound saved = board.writeNbt(new NbtCompound());
            // The replacement is spawned in the same tick as removal of the original in this test.
            saved.remove("UUID");
            board.discard();
            board = NightShiftSkate.SKATEBOARD_ENTITY.create(context.getWorld());
            context.assertTrue(board != null, "board entity type must create a replacement");
            board.readNbt(saved);
            context.assertTrue(board.getStyle() == style, "saved board item must restore its render style");
            context.assertTrue(context.getWorld().spawnEntity(board), "replacement must spawn normally");
            context.assertTrue(context.getWorld().getRecipeManager().get(NightShiftSkate.id(style.itemId())).isPresent(),
                    "the variant must have a loaded crafting recipe");
        }

        player.setSneaking(false);
        context.assertTrue(board.interact(player, Hand.MAIN_HAND).isAccepted() && player.getVehicle() == board,
                "plain RMB on a free board must mount the player");
        board.updatePassengerPosition(player);
        context.assertTrue(Math.abs(player.getY() - board.getY() - style.riderHeight()) < 0.001,
                "the rider must stand on this board's deck height");
        player.setSneaking(true);
        context.assertTrue(board.interact(player, Hand.MAIN_HAND).isAccepted() && !player.hasVehicle(),
                "crouch + RMB while riding must dismount");
        context.assertTrue(board.interact(player, Hand.MAIN_HAND).isAccepted() && board.isRemoved(),
                "crouch + RMB from the ground must pick up and remove the board entity");
        context.assertTrue(player.getInventory().contains(new ItemStack(item)),
                "pickup must return the original board item to the survival inventory");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 80, batchId = "night_shift_movement")
    public void groundAccelerationThenBrakeLosesSpeed(TestContext context) {
        makeFloor(context, 1, 1, 6, 15);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        mount(context, board);

        for (int tick = 1; tick <= 14; tick++) {
            context.runAtTick(tick, () -> board.acceptControls(SkateControls.ACCELERATE));
        }
        double[] accelerated = new double[1];
        context.runAtTick(15, () -> {
            accelerated[0] = board.getVelocity().horizontalLength();
            context.assertTrue(accelerated[0] > 0.09,
                    "holding W must accelerate the occupied board, speed was " + accelerated[0]);
        });
        for (int tick = 15; tick <= 28; tick++) {
            context.runAtTick(tick, () -> board.acceptControls(SkateControls.BRAKE));
        }
        context.runAtTick(30, () -> {
            double braked = board.getVelocity().horizontalLength();
            context.assertTrue(braked < accelerated[0] * 0.55,
                    "holding S must remove most horizontal speed, before=" + accelerated[0] + ", after=" + braked);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 85, batchId = "night_shift_top_speed")
    public void sustainedAccelerationReachesHighSpeed(TestContext context) {
        makeFloor(context, 1, 1, 4, 90);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        mount(context, board);
        for (int tick = 1; tick <= 62; tick += 5) {
            context.runAtTick(tick, () -> board.acceptControls(SkateControls.ACCELERATE));
        }
        double[] early = new double[1];
        context.runAtTick(16, () -> early[0] = board.getVelocity().horizontalLength());
        context.runAtTick(62, () -> {
            double top = board.getVelocity().horizontalLength();
            context.assertTrue(top >= 1.05 && top > early[0] + 0.40,
                    "W must keep accelerating past the initial push, early=" + early[0] + ", later=" + top);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60, batchId = "night_shift_movement")
    public void chargedJumpLaunchesOnSpaceRelease(TestContext context) {
        makeFloor(context, 1, 1, 8, 5);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        mount(context, board);

        for (int tick = 1; tick <= 9; tick++) {
            context.runAtTick(tick, () -> board.acceptControls(SkateControls.JUMP));
        }
        context.runAtTick(10, () -> board.acceptControls(0));
        context.runAtTick(11, () -> {
            context.assertTrue(board.getVelocity().y > 0.22,
                    "releasing a charged Space must produce upward board velocity, got " + board.getVelocity().y);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60, batchId = "night_shift_grinding")
    public void descendingAlignedBoardEngagesStraightIronBarRail(TestContext context) {
        for (int z = 2; z <= 10; z++) {
            context.setBlockState(new BlockPos(4, 2, z), Blocks.IRON_BARS);
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(4.5, 3.08, 2.65), 0.0F);
        mount(context, board);
        board.setVelocity(0.0, -0.11, 0.19);

        context.runAtTick(5, () -> {
            context.assertTrue(board.isGrinding(),
                    "a descending board above 0.12 speed and aligned with a continuous iron-bar rail must grind");
            context.assertTrue(Math.abs(board.getX() - context.getAbsolutePos(new BlockPos(4, 0, 0)).getX() - 0.5) < 0.08,
                    "rail movement must snap the board to the rail centre line");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45, batchId = "night_shift_wide_grind")
    public void offsetApproachCapturesRailAndBoardslideEarnsPoints(TestContext context) {
        for (int z = 2; z <= 10; z++) {
            context.setBlockState(new BlockPos(4, 2, z), Blocks.IRON_BARS);
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(5.03, 3.08, 2.65), 0.0F);
        ServerPlayerEntity rider = mount(context, board);
        int initial = SkateCombo.totalPoints(rider);
        board.setVelocity(0.0, -0.11, 0.21);

        context.runAtTick(6, () -> {
            context.assertTrue(board.isGrinding(), "a 0.53-block offset must be inside the enlarged capture range");
            context.assertTrue(Math.abs(board.getX() - context.getAbsolutePos(new BlockPos(4, 0, 0)).getX() - 0.5) < 0.03,
                    "capture must still center the board on the rail");
            board.acceptControls(SkateControls.JUMP);
        });
        context.runAtTick(9, () -> {
            context.assertTrue(!board.isGrinding() && board.getVelocity().y > 0.0,
                    "jumping must finish the boardslide and launch upwards");
            rider.stopRiding();
            context.assertTrue(SkateCombo.totalPoints(rider) > initial, "the completed boardslide must earn points");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100, batchId = "night_shift_trick_combo")
    public void twoTricksStackAndBrakingBanksTheCombo(TestContext context) {
        makeFloor(context, 1, 1, 4, 60);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        ServerPlayerEntity rider = mount(context, board);
        int initial = SkateCombo.totalPoints(rider);

        context.runAtTick(2, () -> board.acceptControls(SkateControls.ACCELERATE | SkateControls.JUMP));
        context.runAtTick(11, () -> board.acceptControls(SkateControls.BRAKE | SkateControls.TRICK));
        context.runAtTick(13, () -> {
            context.assertTrue(board.getTrickId() == 4, "S + trick must start a 360 shove-it in the air");
            board.acceptControls(SkateControls.ACCELERATE);
        });
        context.runAtTick(23, () -> board.acceptControls(SkateControls.ACCELERATE));
        context.runAtTick(30, () -> board.acceptControls(SkateControls.ACCELERATE | SkateControls.JUMP));
        context.runAtTick(39, () -> board.acceptControls(SkateControls.ACCELERATE | SkateControls.CROUCH | SkateControls.TRICK));
        context.runAtTick(41, () -> {
            context.assertTrue(board.getTrickId() == 5, "Shift + trick must start a varial kickflip in the air");
            board.acceptControls(SkateControls.ACCELERATE);
        });
        context.runAtTick(51, () -> board.acceptControls(SkateControls.ACCELERATE));
        context.runAtTick(58, () -> {
            context.assertTrue(board.isOnGround() && board.getTrickId() == 0, "both tricks must land before stopping");
            context.assertTrue(SkateCombo.totalPoints(rider) == initial, "moving must keep earned points in the pending combo");
            board.acceptControls(SkateControls.BRAKE);
        });
        context.runAtTick(67, () -> board.acceptControls(SkateControls.BRAKE));
        context.runAtTick(77, () -> {
            int earned = SkateCombo.totalPoints(rider) - initial;
            context.assertTrue(earned == 650, "braking to a stop must bank both tricks together, got " + earned);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 155, batchId = "night_shift_combo_timer")
    public void newTrickRestartsTheFiveSecondWindow(TestContext context) {
        ServerPlayerEntity rider = context.createMockCreativeServerPlayerInWorld();
        SkateCombo combo = new SkateCombo();
        int initial = SkateCombo.totalPoints(rider);
        context.runAtTick(1, () -> combo.add(rider, 100));
        context.runAtTick(35, () -> combo.add(rider, 200));
        context.runAtTick(134, () -> {
            combo.tick(rider, false);
            context.assertTrue(SkateCombo.totalPoints(rider) == initial, "combo must still be pending after 99 ticks");
        });
        context.runAtTick(135, () -> {
            combo.tick(rider, false);
            context.assertTrue(SkateCombo.totalPoints(rider) - initial == 300, "combo must bank once at 100 ticks");
            combo.bank(rider);
            context.assertTrue(SkateCombo.totalPoints(rider) - initial == 300, "banking a closed combo cannot pay twice");
            context.complete();
        });
    }

    private static SkateboardEntity spawnBoard(TestContext context, Vec3d relativePosition, float yaw) {
        SkateboardEntity board = NightShiftSkate.SKATEBOARD_ENTITY.create(context.getWorld());
        context.assertTrue(board != null, "registered skateboard type must create a server entity");
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        board.refreshPositionAndAngles(
                origin.getX() + relativePosition.x,
                origin.getY() + relativePosition.y,
                origin.getZ() + relativePosition.z,
                yaw, 0.0F);
        context.assertTrue(context.getWorld().spawnEntity(board), "skateboard must spawn into the test world");
        return board;
    }

    private static ServerPlayerEntity mount(TestContext context, SkateboardEntity board) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        context.assertTrue(player.startRiding(board, true), "mock player must mount the skateboard");
        return player;
    }

    private static void makeFloor(TestContext context, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
            }
        }
    }
}
