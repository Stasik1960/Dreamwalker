package dev.nightshift.skate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

/** Server-side regression tests for the movement state machine and rail entry. */
public final class SkateboardGameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "night_shift_creative_tab")
    public void creativeTabIncludesEveryModItemOnce(TestContext context) {
        ItemGroup group = Registries.ITEM_GROUP.get(NightShiftSkate.id("skateboards"));
        context.assertTrue(group != null, "the dedicated creative tab must be registered");
        group.updateEntries(new ItemGroup.DisplayContext(FeatureFlags.VANILLA_FEATURES, false,
                context.getWorld().getRegistryManager()));
        List<Item> displayed = group.getDisplayStacks().stream().map(ItemStack::getItem).toList();
        List<Item> expected = Registries.ITEM.stream()
                .filter(item -> Registries.ITEM.getId(item).getNamespace().equals(NightShiftSkate.ID)).toList();
        context.assertTrue(displayed.size() == expected.size() && new HashSet<>(displayed).containsAll(expected),
                "the creative tab must contain every mod item exactly once");
        context.complete();
    }

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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "night_shift_workbench")
    public void workbenchSwapsPartsAndTheAssemblySurvivesPlacementAndPickup(TestContext context) {
        BlockPos workbench = new BlockPos(1, 1, 1);
        BlockPos surface = new BlockPos(2, 1, 2);
        context.setBlockState(workbench, SkateParts.WORKBENCH);
        context.setBlockState(surface, Blocks.SMOOTH_STONE);
        BlockPos benchPos = context.getAbsolutePos(workbench);
        BlockPos surfacePos = context.getAbsolutePos(surface);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        player.getAbilities().allowModifyWorld = true;
        player.refreshPositionAndAngles(surfacePos.getX() + 0.5, surfacePos.getY() + 1,
                surfacePos.getZ() - 0.5, 0, 0);
        var benchState = context.getWorld().getBlockState(benchPos);
        var benchHit = new BlockHitResult(Vec3d.ofCenter(benchPos), Direction.UP, benchPos, false);
        ActionResult opened = benchState.onUse(context.getWorld(), player, Hand.MAIN_HAND, benchHit);
        context.assertTrue(opened.isAccepted() && player.currentScreenHandler instanceof SkateWorkbenchScreenHandler,
                "interacting with the workbench must open its assembly menu");
        SkateWorkbenchScreenHandler handler = (SkateWorkbenchScreenHandler) player.currentScreenHandler;
        ItemStack original = new ItemStack(NightShiftSkate.IRON_VESPERS);
        original.setCustomName(Text.literal("Mixed Night"));
        original.getOrCreateNbt().putInt("Sentinel", 42);
        BoardSetup old = BoardSetup.preset(BoardStyle.IRON_VESPERS);
        BoardSetup mixed = new BoardSetup(SkatePart.Deck.GRAVE_PROCESSION, SkatePart.Trucks.FLYING_BUTTRESS,
                SkatePart.Wheels.STAINED_GLASS);

        handler.getSlot(SkateWorkbenchScreenHandler.BOARD).setStack(original);
        handler.getSlot(1).setStack(new ItemStack(SkateParts.item(old.deck()), 2));
        context.assertTrue(!handler.getSlot(SkateWorkbenchScreenHandler.OUTPUT).hasStack(),
                "replacing a part with itself must not create a result");
        for (SkatePart.Type type : SkatePart.Type.values()) {
            handler.getSlot(1 + type.ordinal()).setStack(new ItemStack(SkateParts.item(mixed.part(type)), 2));
        }
        ItemStack preview = handler.getSlot(SkateWorkbenchScreenHandler.OUTPUT).getStack().copy();
        context.assertTrue(BoardSetup.fromStack(preview).equals(mixed) && preview.getName().equals(original.getName())
                && preview.getNbt().getInt("Sentinel") == 42, "preview must keep the original item data and show all swaps");

        handler.onSlotClick(SkateWorkbenchScreenHandler.OUTPUT, 0, SlotActionType.QUICK_MOVE, player);
        context.assertTrue(!handler.getSlot(0).hasStack() && !handler.getSlot(SkateWorkbenchScreenHandler.OUTPUT).hasStack(),
                "taking a result must consume the original board and clear the output");
        for (SkatePart.Type type : SkatePart.Type.values()) {
            context.assertTrue(handler.getSlot(1 + type.ordinal()).getStack().getCount() == 1,
                    "exactly one replacement must be consumed per slot");
            context.assertTrue(player.getInventory().contains(new ItemStack(SkateParts.item(old.part(type)))),
                    "the previous part must be returned to inventory");
        }

        ItemStack assembled = preview.copy();
        context.assertTrue(player.getInventory().contains(assembled), "shift-click must deliver the assembly to inventory");
        player.closeHandledScreen();
        for (SkatePart.Type type : SkatePart.Type.values()) {
            context.assertTrue(player.getInventory().contains(new ItemStack(SkateParts.item(mixed.part(type)))),
                    "closing the workbench must return all unused inputs");
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (ItemStack.canCombine(player.getInventory().getStack(slot), assembled)) {
                player.getInventory().removeStack(slot);
                break;
            }
        }
        player.setStackInHand(Hand.MAIN_HAND, assembled);
        var hit = new BlockHitResult(Vec3d.ofCenter(surfacePos).add(0, 0.5, 0), Direction.UP, surfacePos, false);
        context.assertTrue(assembled.getItem().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted()
                && assembled.isEmpty(), "survival placement must consume the assembled board");
        var boards = context.getWorld().getEntitiesByType(NightShiftSkate.SKATEBOARD_ENTITY,
                new Box(surfacePos).expand(2), entity -> true);
        context.assertTrue(boards.size() == 1 && boards.get(0).getSetup().equals(mixed), "placed board must track its parts");
        SkateboardEntity placed = boards.get(0);
        NbtCompound saved = placed.writeNbt(new NbtCompound());
        saved.remove("UUID");
        placed.discard();
        SkateboardEntity loaded = NightShiftSkate.SKATEBOARD_ENTITY.create(context.getWorld());
        loaded.readNbt(saved);
        context.assertTrue(loaded.getSetup().equals(mixed), "reload must restore the complete assembly");
        context.assertTrue(context.getWorld().spawnEntity(loaded), "restored assembly must spawn");
        player.setSneaking(true);
        context.assertTrue(loaded.interact(player, Hand.MAIN_HAND).isAccepted()
                && player.getInventory().contains(preview), "pickup must return the assembly and its original NBT");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "night_shift_workbench")
    public void allPartsHaveRecipesAndUnknownIdsFallBackToTheOriginal(TestContext context) {
        var manager = context.getWorld().getRecipeManager();
        context.assertTrue(manager.get(NightShiftSkate.id("skate_workbench")).isPresent(), "the workbench must be craftable");
        context.assertTrue(SkateParts.all().size() == 24, "all eight parts of each category must be registered");
        for (SkatePart part : SkateParts.all()) {
            context.assertTrue(manager.get(NightShiftSkate.id(part.itemId())).isPresent(), "missing recipe for " + part.itemId());
        }
        ItemStack damaged = new ItemStack(NightShiftSkate.GRAVE_PROCESSION);
        NbtCompound data = damaged.getOrCreateSubNbt("NightShiftParts");
        data.putString("deck", "unknown_part");
        data.putString("wheels", SkatePart.Wheels.MOON_HUB.id());
        BoardSetup expected = BoardSetup.preset(BoardStyle.GRAVE_PROCESSION).with(SkatePart.Wheels.MOON_HUB);
        context.assertTrue(BoardSetup.fromStack(damaged).equals(expected), "unknown parts must only reset the affected slot");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45, batchId = "night_shift_part_physics")
    public void fittedWheelsAndDeckChangeAccelerationAndOllie(TestContext context) {
        makeFloor(context, 1, 1, 8, 20);
        SkateboardEntity stock = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0);
        SkateboardEntity tuned = spawnBoard(context, new Vec3d(5.5, 2.05, 2.5), 0);
        tuned.setBoardStack(BoardSetup.DEFAULT.with(SkatePart.Deck.CATHEDRAL_SPIRE).with(SkatePart.Wheels.ROSARY_BEAD)
                .applyTo(new ItemStack(NightShiftSkate.SKATEBOARD)));
        mount(context, stock);
        mount(context, tuned);
        for (int tick = 1; tick <= 11; tick += 5) {
            context.runAtTick(tick, () -> {
                stock.acceptControls(SkateControls.ACCELERATE);
                tuned.acceptControls(SkateControls.ACCELERATE);
            });
        }
        context.runAtTick(12, () -> {
            context.assertTrue(tuned.getVelocity().horizontalLength() > stock.getVelocity().horizontalLength() * 1.10,
                    "Rosary Bead wheels must accelerate faster than the stock wheels");
            stock.acceptControls(SkateControls.JUMP);
            tuned.acceptControls(SkateControls.JUMP);
        });
        context.runAtTick(17, () -> {
            stock.acceptControls(0);
            tuned.acceptControls(0);
        });
        context.runAtTick(18, () -> {
            context.assertTrue(stock.getVelocity().y > 0.20 && tuned.getVelocity().y > stock.getVelocity().y * 1.10,
                    "Cathedral Spire must give a stronger ollie than the stock deck");
            context.complete();
        });
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 205, batchId = "night_shift_coasting")
    public void releasingThrottleKeepsMomentumOnPavement(TestContext context) {
        // The test's long lane extends past the test structure and the spawn simulation area.
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 125; z++) {
                context.setBlockState(new BlockPos(x, 20, z), Blocks.SMOOTH_STONE);
            }
        }
        ChunkPos firstChunk = new ChunkPos(context.getAbsolutePos(new BlockPos(2, 20, 1)));
        ChunkPos lastChunk = new ChunkPos(context.getAbsolutePos(new BlockPos(2, 20, 125)));
        List<ChunkPos> forcedByTest = new ArrayList<>();
        for (int z = firstChunk.z; z <= lastChunk.z; z++) {
            if (context.getWorld().setChunkForced(firstChunk.x, z, true)) {
                forcedByTest.add(new ChunkPos(firstChunk.x, z));
            }
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 21.05, 2.5), 0.0F);
        mount(context, board);
        for (int tick = 1; tick <= 25; tick += 5) {
            context.runAtTick(tick, () -> board.acceptControls(SkateControls.ACCELERATE));
        }
        double[] release = new double[2];
        context.runAtTick(26, () -> {
            release[0] = board.getVelocity().horizontalLength();
            release[1] = board.getZ();
            context.assertTrue(release[0] > 0.55, "the board must be at cruising speed before release");
            board.acceptControls(0);
        });
        context.runAtTick(186, () -> {
            for (ChunkPos chunk : forcedByTest) {
                context.getWorld().setChunkForced(chunk.x, chunk.z, false);
            }
            double speed = board.getVelocity().horizontalLength();
            double retained = speed / release[0];
            context.assertTrue(retained >= 0.69 && retained <= 0.71,
                    "eight seconds of coasting must retain 70% speed, before=" + release[0]
                            + ", after=" + speed + ", retained=" + retained);
            context.assertTrue(board.getZ() - release[1] > 75.0,
                    "eight seconds of coasting must carry the board at least 75 blocks without another push");
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

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 30, batchId = "night_shift_wall_collision")
    public void glancingOffAWallPreservesSpeedAlongIt(TestContext context) {
        makeFloor(context, 1, 1, 5, 14);
        for (int z = 1; z <= 13; z++) {
            for (int y = 2; y <= 4; y++) {
                context.setBlockState(new BlockPos(4, y, z), Blocks.SMOOTH_STONE);
            }
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(3.55, 2, 2.5), -55.0f);
        mount(context, board);
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        context.runAtTick(3, () -> board.setVelocity(0.82, 0, 0));
        context.runAtTick(12, () -> {
            context.assertTrue(board.getX() <= origin.getX() + 3.701, "the board must stay outside the wall");
            context.assertTrue(board.getZ() > origin.getZ() + 5.2 && board.getRideSpeed() > 0.35,
                    "a graze must carry the board along the wall without killing its speed");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 25, batchId = "night_shift_side_block_landing")
    public void descendingAcrossABlockEdgeLandsOnTop(TestContext context) {
        context.setBlockState(new BlockPos(4, 2, 3), Blocks.SMOOTH_STONE);
        verifySideLanding(context, 3.59, 3.0);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 25, batchId = "night_shift_side_rail_landing")
    public void descendingAcrossIronBarsDoesNotCatchOnTheSide(TestContext context) {
        for (int z = 1; z <= 6; z++) {
            context.setBlockState(new BlockPos(4, 2, z), Blocks.IRON_BARS);
        }
        verifySideLanding(context, 3.95, 3.0);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 25, batchId = "night_shift_side_park_rail_landing")
    public void descendingAcrossAParkRailClearsTheCrossbar(TestContext context) {
        var rail = SkateParkBlocks.RAIL.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.Z);
        for (int z = 1; z <= 6; z++) {
            context.setBlockState(new BlockPos(4, 2, z), rail);
        }
        verifySideLanding(context, 3.93, 2.9375);
    }

    private static void verifySideLanding(TestContext context, double startX, double top) {
        makeFloor(context, 1, 1, 9, 6);
        SkateboardEntity board = spawnBoard(context, new Vec3d(startX, top + 0.10, 3.5), -90.0f);
        mount(context, board);
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        board.setVelocity(0.63, -0.16, 0);
        context.runAtTick(2, () -> {
            context.assertTrue(board.getX() >= origin.getX() + startX + 0.55,
                    "the board above the edge must carry across instead of getting caught on the side");
            context.assertTrue(board.getY() >= origin.getY() + top - 0.001 && !board.isGrinding(),
                    "a perpendicular approach must roll across the top without entering a grind");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 25, batchId = "night_shift_block_too_high")
    public void collisionStillStopsABoardBelowTheTopOfABlock(TestContext context) {
        makeFloor(context, 1, 1, 7, 6);
        context.setBlockState(new BlockPos(4, 2, 3), Blocks.SMOOTH_STONE);
        SkateboardEntity board = spawnBoard(context, new Vec3d(3.59, 2.64, 3.5), -90.0f);
        mount(context, board);
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        board.setVelocity(0.63, -0.16, 0);
        context.runAtTick(4, () -> {
            context.assertTrue(board.getX() <= origin.getX() + 3.741 && board.getY() < origin.getY() + 3,
                    "an airborne board below the top must stop at the side of the block");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_low_ramp")
    public void lowRampRollsOntoHalfDeckWithoutAnOllie(TestContext context) {
        makeFloor(context, 1, 1, 4, 17);
        context.setBlockState(new BlockPos(2, 2, 6),
                SkateParkBlocks.LOW_RAMP.getDefaultState().with(SkateRampBlock.FACING, Direction.SOUTH));
        for (int z = 7; z <= 15; z++) {
            context.setBlockState(new BlockPos(2, 2, z), SkateParkBlocks.DECK.getDefaultState());
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 4.55), 0.0f);
        mount(context, board);
        double originY = context.getAbsolutePos(BlockPos.ORIGIN).getY();
        double originZ = context.getAbsolutePos(BlockPos.ORIGIN).getZ();
        context.runAtTick(3, () -> board.setVelocity(0, 0, 0.32));
        boolean[] followedSlope = {false};
        for (int tick = 6; tick <= 12; tick++) {
            context.runAtTick(tick, () -> {
                double z = board.getZ() - originZ;
                if (z > 6.05 && z < 6.70 && board.getY() > originY + 2.12 && board.getY() < originY + 2.5) {
                    followedSlope[0] |= board.isOnGround() && board.getRampPitch() > 8.0f;
                }
            });
        }
        context.runAtTick(17, () -> {
            context.assertTrue(followedSlope[0], "the wheels must climb an intermediate part of the ramp while grounded");
            context.assertTrue(board.getZ() > originZ + 7.5 && board.isOnGround()
                            && Math.abs(board.getY() - originY - 2.5) < 0.03,
                    "the low ramp must lead onto a half deck without a jump");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_ramp_launch")
    public void steepRampCarriesSpeedIntoAnAerialAtTheLip(TestContext context) {
        makeFloor(context, 1, 1, 4, 20);
        context.setBlockState(new BlockPos(2, 2, 6),
                SkateParkBlocks.STEEP_RAMP.getDefaultState().with(SkateRampBlock.FACING, Direction.SOUTH));
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 4.3), 0.0f);
        mount(context, board);
        double originY = context.getAbsolutePos(BlockPos.ORIGIN).getY();
        double originZ = context.getAbsolutePos(BlockPos.ORIGIN).getZ();
        context.runAtTick(3, () -> board.setVelocity(0, 0, 0.75));
        boolean[] launched = {false};
        for (int tick = 6; tick <= 12; tick++) {
            context.runAtTick(tick, () -> launched[0] |= !board.isOnGround() && board.getY() > originY + 3.06
                    && board.getVelocity().y > 0.20 && board.getVelocity().horizontalLength() > 0.60);
        }
        context.runAtTick(13, () -> {
            context.assertTrue(launched[0] && board.getZ() > originZ + 8,
                    "a fast board must carry its horizontal speed off the exposed top of the ramp");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_ramp_descent")
    public void downhillRampAddsSpeedWithoutThrottle(TestContext context) {
        makeFloor(context, 1, 1, 4, 16);
        context.setBlockState(new BlockPos(2, 2, 6),
                SkateParkBlocks.STEEP_RAMP.getDefaultState().with(SkateRampBlock.FACING, Direction.SOUTH));
        for (int z = 7; z <= 12; z++) {
            context.setBlockState(new BlockPos(2, 2, z), SkateParkBlocks.PAVING);
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 3.03, 8.15), 180.0f);
        mount(context, board);
        double originY = context.getAbsolutePos(BlockPos.ORIGIN).getY();
        double originZ = context.getAbsolutePos(BlockPos.ORIGIN).getZ();
        context.runAtTick(3, () -> board.setVelocity(0, 0, -0.22));
        context.runAtTick(19, () -> {
            double speed = board.getVelocity().horizontalLength();
            context.assertTrue(board.isOnGround() && board.getY() < originY + 2.05 && board.getZ() < originZ + 5.5,
                    "the board must roll down onto the lower paving");
            context.assertTrue(speed > 0.31, "gravity must add speed during the descent, got " + speed);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 35, batchId = "night_shift_modular_rail")
    public void parkRailUsesItsPlacedAxis(TestContext context) {
        for (String id : List.of("park_paving", "park_deck", "low_ramp", "steep_ramp", "park_rail", "park_ledge")) {
            context.assertTrue(context.getWorld().getRecipeManager().get(NightShiftSkate.id(id)).isPresent(),
                    "the park component " + id + " must have a loaded crafting recipe");
        }
        verifyModularRail(context, SkateParkBlocks.RAIL, Direction.Axis.Z);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 35, batchId = "night_shift_modular_ledge")
    public void stoneLedgeCanBeGrindedAcrossItsPlacedAxis(TestContext context) {
        verifyModularRail(context, SkateParkBlocks.LEDGE, Direction.Axis.X);
    }

    private static void verifyModularRail(TestContext context, Block block, Direction.Axis axis) {
        boolean alongX = axis == Direction.Axis.X;
        var state = block.getDefaultState().with(Properties.HORIZONTAL_AXIS, axis);
        for (int i = 2; i <= 10; i++) {
            context.setBlockState(new BlockPos(alongX ? i : 4, 2, alongX ? 4 : i), state);
        }
        BlockPos start = context.getAbsolutePos(new BlockPos(alongX ? 2 : 4, 2, alongX ? 4 : 2));
        double height = state.getCollisionShape(context.getWorld(), start).getMax(Direction.Axis.Y);
        SkateboardEntity board = spawnBoard(context,
                new Vec3d(alongX ? 2.65 : 4.5, 2 + height + 0.08, alongX ? 4.5 : 2.65), alongX ? -90 : 0);
        mount(context, board);
        board.setVelocity(alongX ? 0.21 : 0, -0.10, alongX ? 0 : 0.21);
        context.runAtTick(6, () -> {
            context.assertTrue(board.isGrinding() && Math.abs(board.getY() - start.getY() - height) < 0.02,
                    "park rails and ledges must use their real collision height and placed axis");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 50, batchId = "night_shift_grabs")
    public void nosegrabAfterAnOllieEarnsPointsOnLanding(TestContext context) {
        makeFloor(context, 1, 1, 4, 20);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        ServerPlayerEntity rider = mount(context, board);
        int initial = SkateCombo.totalPoints(rider);
        context.runAtTick(2, () -> board.acceptControls(SkateControls.JUMP));
        context.runAtTick(11, () -> board.acceptControls(SkateControls.ACCELERATE | SkateControls.GRAB));
        context.runAtTick(14, () -> {
            context.assertTrue(board.isAirborne() && board.getTrickId() == SkateTrick.NOSEGRAB.ordinal(),
                    "W + G on takeoff must start the nosegrab and track airborne state");
            context.assertTrue(SkateCombo.totalPoints(rider) == initial, "a grab must not pay before landing");
        });
        context.runAtTick(33, () -> {
            context.assertTrue(!board.isAirborne() && board.getTrickId() == 0,
                    "a clean landing must end the grab and the airborne pose");
            rider.stopRiding();
            context.assertTrue(SkateCombo.totalPoints(rider) - initial == SkateTrick.NOSEGRAB.points(),
                    "the landed nosegrab must add to the combo once");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45, batchId = "night_shift_manual")
    public void manualAndNoseManualTiltOppositeWaysAndAwardPoints(TestContext context) {
        makeFloor(context, 1, 1, 4, 24);
        SkateboardEntity board = spawnBoard(context, new Vec3d(2.5, 2.05, 2.5), 0.0F);
        ServerPlayerEntity rider = mount(context, board);
        int initial = SkateCombo.totalPoints(rider);
        context.runAtTick(3, () -> {
            board.setVelocity(0.0, 0.0, 0.43);
            board.acceptControls(SkateControls.GRAB);
        });
        context.runAtTick(10, () -> {
            context.assertTrue(board.getManualPitch() > 6.0f, "a grounded G press must lift the nose");
            board.acceptControls(SkateControls.GRAB);
        });
        context.runAtTick(16, () -> {
            context.assertTrue(Math.abs(board.getManualPitch()) < 2.0f, "a second G press must level the board");
            board.acceptControls(SkateControls.ACCELERATE | SkateControls.GRAB);
        });
        context.runAtTick(25, () -> {
            context.assertTrue(board.getManualPitch() < -6.0f, "grounded W + G must lift the tail instead");
            rider.stopRiding();
            context.assertTrue(SkateCombo.totalPoints(rider) - initial > 140,
                    "both sustained manuals must contribute points");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_shift_wallride")
    public void wallRideFollowsWallAndJumpsAway(TestContext context) {
        makeFloor(context, 3, 1, 7, 22);
        for (int z = 1; z <= 20; z++) {
            for (int y = 2; y <= 4; y++) {
                context.setBlockState(new BlockPos(3, y, z), Blocks.SMOOTH_STONE);
            }
        }
        SkateboardEntity board = spawnBoard(context, new Vec3d(4.58, 3.40, 2.5), 0.0F);
        ServerPlayerEntity rider = mount(context, board);
        int initial = SkateCombo.totalPoints(rider);
        board.setVelocity(0.0, 0.0, 0.50);
        context.runAtTick(2, () -> board.acceptControls(SkateControls.RIGHT | SkateControls.GRAB));
        context.runAtTick(7, () -> {
            context.assertTrue(board.getWallRideLean() > 0.75f && board.getTrickId() == 0,
                    "G toward a close side wall must enter a wallride instead of a grab");
            context.assertTrue(Math.abs(board.getX() - context.getAbsolutePos(new BlockPos(4, 0, 0)).getX() - 0.42) < 0.06,
                    "the wallride must track the wall face at riding clearance");
            board.acceptControls(SkateControls.JUMP);
        });
        context.runAtTick(9, () -> {
            context.assertTrue(board.getVelocity().x > 0.20 && board.getVelocity().y > 0.20,
                    "Space must launch upward and away from the wall");
            rider.stopRiding();
            context.assertTrue(SkateCombo.totalPoints(rider) - initial == 250,
                    "a sustained wallride must score once");
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
