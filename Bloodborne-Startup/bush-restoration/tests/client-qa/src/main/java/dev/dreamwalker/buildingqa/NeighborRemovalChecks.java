package dev.dreamwalker.buildingqa;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.WallShape;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.world.GameMode;

/** Checks that a removed installed block retracts neighboring arms after deferred publication. */
final class NeighborRemovalChecks {
    private static final BlockPos START = new BlockPos(435, 310, -837);
    private static final Identifier INSTALLED = new Identifier("bloodborne_dw", "source_bricks_stone_2");
    private static final Identifier NRP_PICKAXE = new Identifier("bloodborne_dw", "nrp_pickaxe");
    private static final Identifier DAWN_WALL = new Identifier("dawnoftimebuilder", "covered_sandstone_wall");

    private NeighborRemovalChecks() {}

    static void verify(ServerPlayerEntity player) throws ReflectiveOperationException {
        var world = player.getServerWorld();
        require(Registries.BLOCK.containsId(DAWN_WALL), "Dawn of Time capped wall fixture is missing");
        var drain = Class.forName("dev.dreamwalker.bloodbornedw.composite.CompositeRuntime")
                .getMethod("drain", ServerWorld.class);
        var fallback = Class.forName("dev.dreamwalker.bloodbornedw.composite.ForcedBlockRemoval")
                .getMethod("creativeFallback", ServerWorld.class, BlockPos.class, PlayerEntity.class);
        var savedMode = player.interactionManager.getGameMode();
        var savedStack = player.getMainHandStack().copy();
        Vec3d savedPosition = player.getPos();
        float savedYaw = player.getYaw(), savedPitch = player.getPitch();
        try {
            for (Path path : Path.values()) {
                BlockPos pos = START.south(path.ordinal() * 4);
                world.getChunk(pos);
                for (Direction direction : Direction.Type.HORIZONTAL) world.getChunk(pos.offset(direction));
                player.changeGameMode(GameMode.CREATIVE);
                player.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() - 2.5, 0, 25);
                placeInstalled(player, pos);
                var neighbors = List.of(
                        neighbor(world, pos, Direction.WEST, Registries.BLOCK.get(DAWN_WALL)),
                        neighbor(world, pos, Direction.EAST, Blocks.COBBLESTONE_WALL),
                        neighbor(world, pos, Direction.NORTH, Blocks.GLASS_PANE),
                        neighbor(world, pos, Direction.SOUTH, Blocks.OAK_FENCE));
                for (var fixture : neighbors) assertConnection(world, fixture, true, path);

                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                if (path == Path.NRP) {
                    player.changeGameMode(GameMode.SURVIVAL);
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Registries.ITEM.get(NRP_PICKAXE)));
                }
                // The custom handlers can consume the interaction while still removing the block.
                if (path == Path.FALLBACK) {
                    require((boolean) fallback.invoke(null, world, pos, player), "forced fallback did not remove " + pos);
                } else {
                    player.interactionManager.tryBreakBlock(pos);
                }
                require(world.getBlockState(pos).isAir(), path + " did not remove the installed block " + pos);
                drain.invoke(null, world);
                for (var fixture : neighbors) assertConnection(world, fixture, false, path);
            }
        } finally {
            player.changeGameMode(savedMode);
            player.setStackInHand(Hand.MAIN_HAND, savedStack);
            player.teleport(world, savedPosition.x, savedPosition.y, savedPosition.z, savedYaw, savedPitch);
        }
    }

    private static void placeInstalled(ServerPlayerEntity player, BlockPos pos) {
        var support = pos.down();
        var world = player.getServerWorld();
        require(world.getBlockState(pos).isAir(), "installed block fixture must start in air " + pos);
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        require(Registries.ITEM.get(INSTALLED) instanceof BlockItem, "missing installed fixture " + INSTALLED);
        var item = (BlockItem) Registries.ITEM.get(INSTALLED);
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false);
        var context = new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(item), hit);
        require(item.place(context).isAccepted(), "could not place DreamWalker fixture at " + pos);
        require(world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.WEST),
                "installed fixture does not offer a full side at " + pos);
    }

    private static Fixture neighbor(ServerWorld world, BlockPos center, Direction side, Block block) {
        BlockPos pos = center.offset(side);
        var facing = side.getOpposite();
        var state = block.getDefaultState().getStateForNeighborUpdate(facing, world.getBlockState(center), world, pos, center);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        return new Fixture(pos, facing, block);
    }

    private static void assertConnection(ServerWorld world, Fixture fixture, boolean expected, Path path) {
        BlockState state = world.getBlockState(fixture.pos());
        require(state.isOf(fixture.block()), path + " lost its neighbor " + fixture.block());
        String name = fixture.facing().asString();
        var property = state.getProperties().stream().filter(value -> value.getName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("missing connection property " + name + " on " + state));
        Object connection = state.get(property);
        boolean connected;
        if (connection instanceof Boolean flag) connected = flag;
        else if (connection instanceof WallShape shape) connected = shape != WallShape.NONE;
        else throw new AssertionError("unexpected connection " + connection + " on " + state);
        require(connected == expected, path + " " + (expected ? "failed to connect" : "left a stale connection")
                + " on " + Registries.BLOCK.getId(fixture.block()) + " at " + fixture.pos() + " (" + state + ")");
    }

    private static void require(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }

    private record Fixture(BlockPos pos, Direction facing, Block block) {}

    private enum Path { CREATIVE, NRP, FALLBACK }
}
