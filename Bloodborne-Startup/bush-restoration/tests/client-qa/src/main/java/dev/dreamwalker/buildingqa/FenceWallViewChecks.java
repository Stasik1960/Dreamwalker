package dev.dreamwalker.buildingqa;

import dev.dreamwalker.buildingqa.mixin.MouseInputAccessor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.WallShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

/** Verifies the Watcher's Seal through the player's normal mouse binding and captures the results. */
final class FenceWallViewChecks {
    private static final String NAMESPACE = "bloodborne_dw";
    private static final Identifier TOOL = new Identifier(NAMESPACE, "bb_block_tool");
    private static final Fixture MASONRY = new Fixture(new BlockPos(443, 290, -846), "source_assembly_brick_wall", "93167");
    private static final Fixture GRILLE = new Fixture(new BlockPos(451, 290, -846), "connected_fence_93185_plain", "93185");
    private static final Fixture NATIVE_WALL = new Fixture(new BlockPos(443, 290, -838), "prototype_wall", "prototype_wall");
    private static final BlockPos SIDE_WALL = new BlockPos(451, 290, -838);
    private static final List<Fixture> FIXTURES = List.of(MASONRY, NATIVE_WALL, GRILLE);
    private static final Vec3d OVERHEAD = Vec3d.of(GRILLE.pos()).add(1.3, 4.0, -2.3);

    private record Fixture(BlockPos pos, String path, String number) {
        boolean forced(BlockState state) {
            if (this == MASONRY) return state.contains(Properties.OPEN) && state.get(Properties.OPEN);
            return this == NATIVE_WALL ? property(state, "connections").equals("manual")
                    : property(state, "wall_axis").equals("x");
        }

        boolean sameId(BlockState state) {
            return this != MASONRY && property(state, this == NATIVE_WALL ? "connections" : "wall_axis").equals("same_id");
        }

        private static String property(BlockState state, String name) {
            var property = state.getBlock().getStateManager().getProperty(name);
            return property == null ? "" : value(state, property);
        }

        private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
            return property.name(state.get(property));
        }
    }

    private enum Stage { PREPARE, AUTO, FORCE, SAME_ID, RESTORE, TOP }
    private CompletableFuture<Void> prepared;
    private CompletableFuture<Boolean> serverConfirmed;
    private boolean previousHud;
    private GraphicsMode previousGraphics;
    private Stage stage = Stage.PREPARE;
    private int fixtureIndex;
    private int ticks;

    String tick(MinecraftClient game) throws Exception {
        if (stage == Stage.TOP) {
            if (++ticks > 240) throw new AssertionError("top view of 93185 did not settle");
            if (!prepared.isDone()) return null;
            prepared.join();
            if (ticks < 25 || game.player.getPos().squaredDistanceTo(OVERHEAD) > .02
                    || !game.worldRenderer.isRenderingReady(GRILLE.pos())) return null;
            capture(game, "qa-93185-seams-and-post-top.png");
            return "PASS Watcher's Seal cycles three fence modes for 93167, prototype_wall and 93185; copied skin 0 places from the side";
        }

        Fixture fixture = FIXTURES.get(fixtureIndex);
        if (stage == Stage.PREPARE) {
            if (fixtureIndex == 0) {
                previousHud = game.options.hudHidden;
                previousGraphics = game.options.getGraphicsMode().getValue();
                game.options.hudHidden = true;
                game.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            }
            var server = game.getServer();
            var id = game.player.getUuid();
            prepared = server.submit(() -> prepare(server.getPlayerManager().getPlayer(id), fixture));
            advance(Stage.AUTO);
            return null;
        }
        if (++ticks > 240) {
            throw new AssertionError("Watcher's Seal " + fixture.number() + " stage " + stage + " state="
                    + game.world.getBlockState(fixture.pos()) + ", target=" + game.crosshairTarget);
        }
        if (!prepared.isDone()) return null;
        prepared.join();
        if (ticks < 20 || !aiming(game, fixture)) return null;

        if (stage == Stage.AUTO) {
            capture(game, "qa-" + fixture.number() + "-automatic.png");
            attack(game);
            advance(Stage.FORCE);
            return null;
        }

        if (!confirmed(game, fixture)) return null;
        if (stage == Stage.FORCE) {
            checkWallHeight(game, fixture);
            capture(game, "qa-" + fixture.number() + "-straight.png");
            attack(game);
            advance(Stage.SAME_ID);
            return null;
        }
        if (stage == Stage.SAME_ID) {
            capture(game, "qa-" + fixture.number() + "-same-id.png");
            attack(game);
            advance(Stage.RESTORE);
            return null;
        }
        if (++fixtureIndex < FIXTURES.size()) {
            advance(Stage.PREPARE);
        } else {
            var server = game.getServer();
            var id = game.player.getUuid();
            prepared = server.submit(() -> server.getPlayerManager().getPlayer(id).teleport(
                    server.getOverworld(), OVERHEAD.x, OVERHEAD.y, OVERHEAD.z, 0, 52));
            advance(Stage.TOP);
        }
        return null;
    }

    private static void checkWallHeight(MinecraftClient game, Fixture fixture) {
        var state = game.world.getBlockState(fixture.pos());
        if (fixture == MASONRY) {
            var outline = state.getOutlineShape(game.world, fixture.pos());
            if (outline.isEmpty() || outline.getMax(Direction.Axis.Y) != 1
                    || outline.getMin(Direction.Axis.X) != 0 || outline.getMax(Direction.Axis.X) != 1) {
                throw new AssertionError("93167 straight section must fill the cell's height and width: " + outline);
            }
        } else if (fixture == NATIVE_WALL && (state.get(Properties.EAST_WALL_SHAPE) != WallShape.TALL
                || state.get(Properties.WEST_WALL_SHAPE) != WallShape.TALL || state.get(Properties.UP))) {
            throw new AssertionError("prototype_wall needs two tall opposing sides with no post: " + state);
        }
    }

    void cleanup(MinecraftClient game) {
        if (previousGraphics == null) return;
        game.options.hudHidden = previousHud;
        game.options.getGraphicsMode().setValue(previousGraphics);
    }

    private boolean confirmed(MinecraftClient game, Fixture fixture) {
        Stage expected = stage;
        BlockState clientState = game.world.getBlockState(fixture.pos());
        if (fixture.forced(clientState) != (expected == Stage.FORCE)) return false;
        if (fixture != MASONRY && fixture.sameId(clientState) != (expected == Stage.SAME_ID)) return false;
        if (expected != Stage.FORCE && !connectedAsExpected(fixture, clientState, expected)) return false;
        if (serverConfirmed == null) {
            var server = game.getServer();
            var id = game.player.getUuid();
            serverConfirmed = server.submit(() -> {
                var world = server.getPlayerManager().getPlayer(id).getServerWorld();
                var state = world.getBlockState(fixture.pos());
                if (fixture.forced(state) != (expected == Stage.FORCE)) return false;
                if (expected == Stage.FORCE) return true;
                boolean restricted = fixture == MASONRY
                        ? importedSameId(world, fixture.pos()) : fixture.sameId(state);
                return restricted == (expected == Stage.SAME_ID) && connectedAsExpected(fixture, state, expected);
            });
            return false;
        }
        if (!serverConfirmed.isDone()) return false;
        boolean actual = serverConfirmed.join();
        if (!actual) serverConfirmed = null;
        return actual;
    }

    private static boolean connectedAsExpected(Fixture fixture, BlockState state, Stage expected) {
        if (fixture == GRILLE) {
            return state.get(Properties.EAST) && state.get(Properties.WEST) == (expected == Stage.RESTORE);
        }
        if (fixture == MASONRY) {
            try {
                state = (BlockState) Class.forName("dev.dreamwalker.bloodbornedw.composite.AutoStoneFence")
                        .getMethod("canonicalWall", BlockState.class).invoke(null, state);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        return state != null && state.get(Properties.EAST_WALL_SHAPE) != WallShape.NONE
                && (state.get(Properties.WEST_WALL_SHAPE) != WallShape.NONE) == (expected == Stage.RESTORE);
    }

    private static boolean importedSameId(ServerWorld world, BlockPos pos) {
        try {
            return (Boolean) Class.forName("dev.dreamwalker.bloodbornedw.composite.AutoStoneFence")
                    .getMethod("sameIdOnly", ServerWorld.class, BlockPos.class)
                    .invoke(null, world, pos);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean aiming(MinecraftClient game, Fixture fixture) {
        if (game.currentScreen != null || !game.worldRenderer.isRenderingReady(fixture.pos())
                || !game.player.getMainHandStack().isOf(Registries.ITEM.get(TOOL))) return false;
        game.gameRenderer.updateTargetedEntity(1);
        return game.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(fixture.pos());
    }

    private static void attack(MinecraftClient game) {
        var mouse = (MouseInputAccessor) game.mouse;
        long window = game.getWindow().getHandle();
        mouse.dw$onMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        mouse.dw$onMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
    }

    private void advance(Stage next) {
        stage = next;
        ticks = 0;
        serverConfirmed = null;
    }

    private static void prepare(ServerPlayerEntity player, Fixture fixture) {
        var world = player.getServerWorld();
        var pos = fixture.pos();
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) world.getChunk(pos.add(x, 0, z));
        player.getServer().getPlayerManager().addToOperators(player.getGameProfile());
        player.changeGameMode(GameMode.CREATIVE);
        player.clearStatusEffects();
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        player.teleport(world, pos.getX() + .5, pos.getY() - 1.02, pos.getZ() - 2.5, 0, 0);
        place(player, pos, fixture.path());
        place(player, pos.east(), fixture.path());
        world.setBlockState(pos.west(), Blocks.SMOOTH_STONE.getDefaultState());
        if (fixture == NATIVE_WALL) checkSidePlacement(player);
        if (fixture == GRILLE) {
            place(player, pos.east(2), "connected_fence_93185_post");
            place(player, pos.east(3), fixture.path());
        }
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Registries.ITEM.get(TOOL)));
    }

    private static void checkSidePlacement(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        var id = new Identifier(NAMESPACE, "prototype_wall_skin_0");
        BlockPos source = SIDE_WALL.west(3);
        place(player, source, id.getPath());
        var sourceState = world.getBlockState(source);
        ItemStack stack = sourceState.getBlock().getPickStack(world, source, sourceState);
        if (!stack.isOf(Registries.ITEM.get(id)) || !world.getBlockState(SIDE_WALL.down()).isAir()) {
            throw new AssertionError("expected a copied skin 0 wall and a side fixture without a floor");
        }
        BlockPos backing = SIDE_WALL.east();
        world.setBlockState(backing, Blocks.SMOOTH_STONE.getDefaultState());
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofCenter(backing).add(-.5, 0, 0), Direction.WEST, backing, false);
        if (!stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted()
                || !world.getBlockState(SIDE_WALL).isOf(sourceState.getBlock())) {
            throw new AssertionError("copied skin 0 wall did not place from the side without a floor");
        }
    }

    private static void place(ServerPlayerEntity player, BlockPos pos, String path) {
        var world = player.getServerWorld();
        world.setBlockState(pos.down(), Blocks.SMOOTH_STONE.getDefaultState());
        var stack = new ItemStack(Registries.ITEM.get(new Identifier(NAMESPACE, path)));
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofBottomCenter(pos), Direction.UP, pos.down(), false);
        if (!stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted()) {
            throw new AssertionError("could not place Watcher's Seal fixture " + path + " at " + pos);
        }
    }

    private static void capture(MinecraftClient game, String file) throws Exception {
        try (var image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve(file));
        }
    }
}
