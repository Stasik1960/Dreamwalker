package dev.dreamwalker.buildingqa;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Captures the exposed lintel and the underside of the placed console in the real renderer. */
final class DoorConsoleVisualChecks {
    private record View(BlockPos root, String block, String screenshot, Vec3d camera, float pitch) {}

    private static final BlockPos DOOR = new BlockPos(425, 276, -847);
    private static final BlockPos CONSOLE = ScreenshotBlockChecks.CONSOLE;
    private static final List<View> VIEWS = List.of(
            new View(DOOR, "prototype_double_door", "qa-carved-door-inner-frame.png",
                    Vec3d.of(DOOR).add(.5, .1, -.25), -76),
            new View(CONSOLE, "source_quartz_stairs_outer_1", "qa-console-90827-underside.png",
                    Vec3d.of(CONSOLE).add(.5, -2.82, -2.7), -25));

    private CompletableFuture<Void> setup;
    private GraphicsMode previousGraphics;
    private boolean previousHud;
    private int index;
    private int ticks;

    String tick(MinecraftClient game) throws Exception {
        View view = VIEWS.get(index);
        if (setup == null) {
            if (index == 0) {
                previousGraphics = game.options.getGraphicsMode().getValue();
                previousHud = game.options.hudHidden;
                game.options.hudHidden = true;
                game.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            }
            var server = game.getServer();
            var id = game.player.getUuid();
            setup = server.submit(() -> {
                var player = server.getPlayerManager().getPlayer(id);
                player.changeGameMode(GameMode.CREATIVE);
                player.clearStatusEffects();
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                var world = player.getServerWorld();
                if (view.root().equals(CONSOLE)) world.removeBlock(CONSOLE.down(), false);
                var camera = view.camera();
                player.teleport(world, camera.x, camera.y, camera.z, 0, view.pitch());
            });
            return null;
        }
        if (!setup.isDone()) return null;
        setup.join();
        if (++ticks > 150) throw new AssertionError("camera did not arrive at " + view.block());
        if (ticks < 45 || game.currentScreen != null || !game.world.isChunkLoaded(view.root())
                || game.player.getPos().squaredDistanceTo(view.camera()) > .03) return null;
        var block = Registries.BLOCK.get(new Identifier("bloodborne_dw", view.block()));
        if (!game.world.getBlockState(view.root()).isOf(block)) {
            throw new AssertionError("missing visible fixture " + view.block());
        }
        try (var image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve(view.screenshot()));
        }
        if (++index == VIEWS.size()) return "PASS player screenshots of the carved door interior and console 90827";
        setup = null;
        ticks = 0;
        return null;
    }

    void cleanup(MinecraftClient game) {
        if (previousGraphics != null) game.options.getGraphicsMode().setValue(previousGraphics);
        game.options.hudHidden = previousHud;
    }
}
