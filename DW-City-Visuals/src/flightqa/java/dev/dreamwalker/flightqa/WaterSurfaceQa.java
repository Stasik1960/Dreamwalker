package dev.dreamwalker.flightqa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/** Capture matched fake and ordinary water surfaces across chunk borders. */
final class WaterSurfaceQa {
    private static final BlockPos CENTER = new BlockPos(143, 224, -930);
    private final Path output;
    private CompletableFuture<Void> placement;
    private int view;
    private long readyAt;

    WaterSurfaceQa(Path output) {
        this.output = output;
    }

    void tick(MinecraftClient client) throws Exception {
        if (view == 2 || client.currentScreen != null || client.getOverlay() != null) return;
        if (placement == null) {
            client.options.hudHidden = true;
            teleport(client, true);
            return;
        }
        if (!placement.isDone()) return;
        placement.join();
        double y = view == 0 ? 218.0 : 232.0;
        if (Math.abs(client.player.getY() - y) > 0.1
                || !client.world.getBlockState(CENTER).isOf(Registries.BLOCK.get(new Identifier("bloodborne_dw", "dw_fake_water")))
                || !client.worldRenderer.isTerrainRenderComplete()) {
            readyAt = 0;
            return;
        }
        long now = System.nanoTime();
        if (readyAt == 0) readyAt = now;
        if (now - readyAt < 2_000_000_000L) return;
        Path image = output.resolve(view == 0 ? "water-below.png" : "water-above.png");
        try (var screenshot = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            screenshot.writeTo(image);
        }
        if (++view == 2) {
            Files.writeString(output.resolve("water-result.txt"), "PASS: photographed both sides of artificial and ordinary water");
            client.scheduleStop();
        } else {
            readyAt = 0;
            teleport(client, false);
        }
    }

    private void teleport(MinecraftClient client, boolean prepare) {
        var server = client.getServer();
        var uuid = client.player.getUuid();
        placement = server.submit(() -> {
            var world = server.getOverworld();
            var player = server.getPlayerManager().getPlayer(uuid);
            if (prepare) {
                var fake = Registries.BLOCK.get(new Identifier("bloodborne_dw", "dw_fake_water")).getDefaultState();
                for (int x = 128; x < 160; x++) {
                    for (int z = -944; z < -912; z++) {
                        world.setBlockState(new BlockPos(x, 224, z),
                                x < 148 ? fake : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
                world.setTimeOfDay(6000);
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                world.setWeather(0, 0, false, false);
                player.changeGameMode(GameMode.CREATIVE);
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
                player.setNoGravity(true);
            }
            player.teleport(world, 147.5, prepare ? 218 : 232, -946.5, 0, prepare ? -29 : 35);
        });
    }
}
