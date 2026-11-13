package dev.dreamwalker.buildingqa;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;

/** Destructive checks against the released city, run only on the disposable QA save. */
final class MixedCellRemovalChecks {
    private static final String COMPOSITE = "dev.dreamwalker.bloodbornedw.composite.";
    private static final BlockPos SCREENSHOT = new BlockPos(372, 31, -860);
    private static final BlockPos TWO_OWNERS = SCREENSHOT.down(2);
    private static final BlockPos ORPHAN = new BlockPos(342, 71, -919);
    private static final BlockPos ORPHAN_CREATIVE = new BlockPos(-123, 41, -1060);
    private static final BlockPos DOOR = new BlockPos(-137, 30, -1109);
    private static final BlockPos ADJACENT = DOOR.west();
    private static final BlockPos RETAINED_FRAME = new BlockPos(162, 95, -759);

    private MixedCellRemovalChecks() {}

    static List<String> verify(ServerPlayerEntity player) {
        player.changeGameMode(GameMode.CREATIVE);
        player.getServer().getPlayerManager().addToOperators(player.getGameProfile());
        return List.of(
                WorldChecks.verify("Creative break clears the city fragment at 372,31,-860", () -> creative(player)),
                WorldChecks.verify("Creative break clears a city fragment with no live owner", () -> creativeOrphan(player)),
                WorldChecks.verify("WorldEdit //set 0 clears mixed geometry, with undo and redo", () -> worldEdit(player)),
                WorldChecks.verify("WorldEdit //set 0 clears two overlapping owners", () -> overlapping(player)),
                WorldChecks.verify("WorldEdit //set 0 clears a city fragment with no live owner", () -> orphan(player)),
                WorldChecks.verify("WorldEdit clears and restores a retained frame", () -> retainedFrame(player)));
    }

    private static void creative(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        player.teleport(world, 370.359, 30.34634, -861.736, -43.4f, 7.8f);
        world.getChunk(SCREENSHOT);
        require(base(world, SCREENSHOT) == 150580 && !owners(world, SCREENSHOT).isEmpty(), "Missing screenshot fixture");
        breakInCreative(player, SCREENSHOT);
        assertEmpty(world, SCREENSHOT);
        require(world.setBlockState(SCREENSHOT, Blocks.STONE.getDefaultState())
                && world.getBlockState(SCREENSHOT).isOf(Blocks.STONE), "Could not reuse cleared space");
    }

    private static void creativeOrphan(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        player.teleport(world, -120.5, 40.0, -1059.5, 90, 0);
        world.getChunk(ORPHAN_CREATIVE);
        require(base(world, ORPHAN_CREATIVE) == 102671 && owners(world, ORPHAN_CREATIVE).isEmpty(),
                "Missing orphan fixture");
        breakInCreative(player, ORPHAN_CREATIVE);
        assertEmpty(world, ORPHAN_CREATIVE);
    }

    private static void breakInCreative(ServerPlayerEntity player, BlockPos pos) {
        ItemStack held = player.getMainHandStack();
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        try {
            require(player.interactionManager.tryBreakBlock(pos), "Creative break was rejected");
        } finally {
            player.setStackInHand(Hand.MAIN_HAND, held);
        }
    }

    private static void worldEdit(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        world.getChunk(DOOR);
        world.getChunk(ADJACENT);
        require(base(world, DOOR) == 20411 && !owners(world, DOOR).isEmpty(), "Missing door fixture");
        require(base(world, ADJACENT) == 123278, "Missing independent adjacent geometry");
        select(player, DOOR);
        command(player, "//set 0");
        assertEmpty(world, DOOR);
        assertNeighbor(world);
        command(player, "//undo");
        require(base(world, DOOR) == 20411 && !owners(world, DOOR).isEmpty(), "Undo lost the exact city fragment or door");
        assertNeighbor(world);
        command(player, "//redo");
        assertEmpty(world, DOOR);
        assertNeighbor(world);
    }

    private static void orphan(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        world.getChunk(ORPHAN);
        require(base(world, ORPHAN) == 30245 && owners(world, ORPHAN).isEmpty(), "Missing orphan fixture");
        select(player, ORPHAN);
        command(player, "//set 0");
        assertEmpty(world, ORPHAN);
        command(player, "//undo");
        require(base(world, ORPHAN) == 30245 || visual(world, ORPHAN) == 30245,
                "Undo lost the original orphan geometry");
        command(player, "//redo");
        assertEmpty(world, ORPHAN);
    }

    private static void overlapping(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        world.getChunk(TWO_OWNERS);
        require(base(world, TWO_OWNERS) == 18697 && owners(world, TWO_OWNERS).size() == 2,
                "Missing two-owner fixture");
        select(player, TWO_OWNERS);
        command(player, "//set 0");
        assertEmpty(world, TWO_OWNERS);
    }

    private static void retainedFrame(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        world.getChunk(RETAINED_FRAME);
        var frame = Class.forName(COMPOSITE + "ForcedBlockRemoval")
                .getMethod("retainedFrame", ServerWorld.class, BlockPos.class);
        Object original = frame.invoke(null, world, RETAINED_FRAME);
        require(original != null, "Missing retained frame fixture");
        select(player, RETAINED_FRAME);
        command(player, "//set 0");
        assertEmpty(world, RETAINED_FRAME);
        require(frame.invoke(null, world, RETAINED_FRAME) == null, "Retained frame still registered after deletion");
        command(player, "//undo");
        require(original.equals(frame.invoke(null, world, RETAINED_FRAME)), "Undo lost retained frame state");
    }

    private static void select(ServerPlayerEntity player, BlockPos pos) {
        String coordinates = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        command(player, "//pos1 " + coordinates);
        command(player, "//pos2 " + coordinates);
    }

    private static void command(ServerPlayerEntity player, String command) {
        player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command);
    }

    private static void assertEmpty(ServerWorld world, BlockPos pos) throws Exception {
        var state = world.getBlockState(pos);
        require(state.isOf(Blocks.AIR) && world.getBlockEntity(pos) == null && owners(world, pos).isEmpty()
                && base(world, pos) < 0 && visual(world, pos) < 0
                && state.getOutlineShape(world, pos).isEmpty() && state.getCollisionShape(world, pos).isEmpty(),
                "Residual state, owner, geometry or collision at " + pos.toShortString());
    }

    private static void assertNeighbor(ServerWorld world) throws Exception {
        require(base(world, ADJACENT) == 123278 || visual(world, ADJACENT) == 123278,
                "Removing the door lost unrelated geometry outside the selection");
    }

    private static int base(ServerWorld world, BlockPos pos) throws Exception {
        return (Integer) invoke("NativeGridBridge", "base", world, pos);
    }

    private static int visual(ServerWorld world, BlockPos pos) throws Exception {
        return (Integer) invoke("NativeVisuals", "index", world, pos);
    }

    private static List<?> owners(ServerWorld world, BlockPos pos) throws Exception {
        return (List<?>) invoke("CompositeRuntime", "contributions", world, pos);
    }

    private static Object invoke(String owner, String name, ServerWorld world, BlockPos pos) throws Exception {
        return Class.forName(COMPOSITE + owner).getMethod(name, BlockView.class, BlockPos.class).invoke(null, world, pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
