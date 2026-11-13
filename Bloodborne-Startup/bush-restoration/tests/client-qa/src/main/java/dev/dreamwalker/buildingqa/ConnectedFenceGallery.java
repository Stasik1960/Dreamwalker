package dev.dreamwalker.buildingqa;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Captures separate rows near the other fixtures with the actual client renderer. */
final class ConnectedFenceGallery {
    private static final BlockPos ORIGIN = WindowRotationChecks.OPEN_WINDOW.add(-3, 8, -8);
    private static final double CAMERA_X = ORIGIN.getX() + 6.5;
    private static final List<List<String>> ROWS = List.of(
            List.of("93195", "93185_plain", "93185_ornate", "93185_double", "93185_post"),
            List.of("93164", "93181", "93214", "90993"),
            List.of("90910", "90969", "93166", "column"));
    private CompletableFuture<Void> setup;
    private int row;
    private int ticks;
    private boolean previousHud;
    private GraphicsMode previousGraphics;

    String tick(MinecraftClient game) throws Exception {
        BlockPos start = ORIGIN.south(10 * row);
        if (setup == null) {
            if (row == 0) {
                previousHud = game.options.hudHidden;
                game.options.hudHidden = true;
                previousGraphics = game.options.getGraphicsMode().getValue();
                game.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            }
            var server = game.getServer();
            var id = game.player.getUuid();
            List<String> styles = ROWS.get(row);
            setup = server.submit(() -> position(server.getPlayerManager().getPlayer(id), start, styles));
            return null;
        }
        if (!setup.isDone()) return null;
        setup.join();
        if (++ticks > 180) throw new AssertionError("fence showcase did not load for row " + (row + 1));
        if (ticks < 55 || game.currentScreen != null || !game.world.isChunkLoaded(start)
                || Math.abs(game.player.getX() - CAMERA_X) > .02
                || Math.abs(game.player.getZ() - (start.getZ() - 5.5)) > .02) return null;

        var styles = ROWS.get(row);
        for (int i = 0; i < styles.size(); ++i) {
            var id = new Identifier("bloodborne_dw", "connected_fence_" + styles.get(i));
            if (!game.world.getBlockState(start.east(3 * i)).isOf(Registries.BLOCK.get(id))) {
                throw new AssertionError("fence showcase is missing " + id);
            }
        }
        try (var image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve("qa-fence-gallery-" + (row + 1) + ".png"));
        }
        ++row;
        if (row == ROWS.size()) return "PASS 13 unified fence and column client models captured in three rows";
        setup = null;
        ticks = 0;
        return null;
    }

    void cleanup(MinecraftClient game) {
        game.options.hudHidden = previousHud;
        game.options.getGraphicsMode().setValue(previousGraphics);
    }

    private static void position(ServerPlayerEntity player, BlockPos start, List<String> styles) {
        var world = player.getServerWorld();
        for (int x = -1; x <= 14; ++x) {
            for (int z = -8; z <= 2; ++z) {
                world.setBlockState(start.add(x, -1, z), Blocks.SMOOTH_STONE.getDefaultState());
            }
        }
        player.changeGameMode(GameMode.CREATIVE);
        player.clearStatusEffects();
        for (int i = 0; i < styles.size(); ++i) {
            String style = styles.get(i);
            var pos = start.east(3 * i);
            if (style.equals("93185_post")) world.setBlockState(pos.west(), Blocks.STONE.getDefaultState());
            String item = "bloodborne_dw:connected_fence_" + style;
            var stack = new ItemStack(Registries.ITEM.get(new Identifier(item)));
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, -.5, 0), Direction.UP, pos.down(), false);
            if (!stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted()) {
                throw new AssertionError("could not place " + item + " for the fence showcase");
            }
            if (!style.equals("column") && !style.equals("93185_post")) {
                world.setBlockState(pos.east(), world.getBlockState(pos));
            }
        }
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        player.teleport(world, CAMERA_X, start.getY(), start.getZ() - 5.5, 0, 8);
    }
}
