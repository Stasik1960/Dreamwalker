package dev.dreamwalker.buildingqa;

import dev.dreamwalker.buildingqa.mixin.MouseInputAccessor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
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
    private static final List<Fixture> FIXTURES = List.of(MASONRY, GRILLE);
    private static final Vec3d OVERHEAD = Vec3d.of(GRILLE.pos()).add(1.3, 4.0, -2.3);

    private record Fixture(BlockPos pos, String path, String number) {
        boolean forced(BlockState state) {
            if (this == MASONRY) return state.contains(Properties.OPEN) && state.get(Properties.OPEN);
            var axis = state.getBlock().getStateManager().getProperty("wall_axis");
            return axis != null && state.get(axis).toString().equalsIgnoreCase("x");
        }
    }

    private enum Stage { PREPARE, AUTO, FORCE, RESTORE, TOP }
    private CompletableFuture<Void> prepared;
    private CompletableFuture<Boolean> serverForced;
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
            return "PASS Watcher's Seal mouse click sets a full straight section across the player's view and restores connections for 93167 and 93185";
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

        boolean expected = stage == Stage.FORCE;
        if (!confirmed(game, fixture, expected)) return null;
        if (expected) {
            capture(game, "qa-" + fixture.number() + "-straight.png");
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

    void cleanup(MinecraftClient game) {
        if (previousGraphics == null) return;
        game.options.hudHidden = previousHud;
        game.options.getGraphicsMode().setValue(previousGraphics);
    }

    private boolean confirmed(MinecraftClient game, Fixture fixture, boolean expected) {
        if (fixture.forced(game.world.getBlockState(fixture.pos())) != expected) return false;
        if (serverForced == null) {
            var server = game.getServer();
            var id = game.player.getUuid();
            serverForced = server.submit(() -> fixture.forced(
                    server.getPlayerManager().getPlayer(id).getServerWorld().getBlockState(fixture.pos())));
            return false;
        }
        if (!serverForced.isDone()) return false;
        boolean actual = serverForced.join();
        if (actual != expected) serverForced = null;
        return actual == expected;
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
        serverForced = null;
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
        if (fixture == GRILLE) {
            place(player, pos.east(), fixture.path());
            place(player, pos.east(2), "connected_fence_93185_post");
            place(player, pos.east(3), fixture.path());
        }
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Registries.ITEM.get(TOOL)));
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
