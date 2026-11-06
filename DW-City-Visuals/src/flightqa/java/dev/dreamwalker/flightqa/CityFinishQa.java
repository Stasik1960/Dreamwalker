package dev.dreamwalker.flightqa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/** Optional camera checks for the stone and shutter color corrections. */
final class CityFinishQa {
    private record View(String file, double x, double y, double z, float yaw, float pitch,
                        BlockPos target, String id) {}
    private static final List<View> VIEWS = List.of(
        new View("railing", 203.219, 61.49820, -958.758, -114.6f, 20.4f,
                 new BlockPos(206, 61, -961), "static_architecture_15"),
        new View("column", 193.954, 60.74602, -962.723, 113.1f, 24.7f,
                 new BlockPos(190, 60, -965), "static_architecture_0"),
        new View("shutter", 208.469, 72.03620, -936.369, -178.7f, 19.1f,
                 new BlockPos(208, 72, -940), "static_architecture_256"),
        new View("shutter-lower", 208.469, 68.03620, -936.369, -178.7f, 19.1f,
                 new BlockPos(206, 70, -940), "static_architecture_256"));

    private final Path output;
    private CompletableFuture<Void> teleport;
    private int index;
    private long readyAt;
    private long deadline;

    CityFinishQa(Path output) {
        this.output = output;
    }

    void tick(MinecraftClient client) throws Exception {
        if (index == VIEWS.size() || client.currentScreen != null || client.getOverlay() != null) return;
        View view = VIEWS.get(index);
        long now = System.nanoTime();
        if (teleport == null) {
            deadline = now + 120_000_000_000L;
            client.options.hudHidden = true;
            var server = client.getServer();
            var uuid = client.player.getUuid();
            teleport = server.submit(() -> {
                var world = server.getOverworld();
                var player = server.getPlayerManager().getPlayer(uuid);
                if (index == 0) {
                    world.setTimeOfDay(6000);
                    world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    world.setWeather(0, 0, false, false);
                    player.changeGameMode(GameMode.CREATIVE);
                    player.getAbilities().flying = true;
                    player.sendAbilitiesUpdate();
                    player.setNoGravity(true);
                }
                player.teleport(world, view.x(), view.y(), view.z(), view.yaw(), view.pitch());
            });
            return;
        }
        if (now > deadline) throw new IllegalStateException("Scene did not load: " + view.file());
        if (!teleport.isDone()) return;
        teleport.join();
        var target = Registries.BLOCK.getId(client.world.getBlockState(view.target()).getBlock());
        if (client.player.squaredDistanceTo(view.x(), view.y(), view.z()) > 0.01
                || !target.toString().equals("bloodborne_dw:" + view.id())
                || !FlightQa.full(client) || !client.worldRenderer.isTerrainRenderComplete()) {
            readyAt = 0;
            return;
        }
        if (readyAt == 0) readyAt = now;
        if (now - readyAt < 6_000_000_000L) return;
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(output.resolve(view.file() + ".png"));
        }
        ++index;
        teleport = null;
        readyAt = 0;
        if (index == VIEWS.size()) {
            Files.writeString(output.resolve("city-result.txt"), "PASS: photographed all requested map views");
            client.scheduleStop();
        }
    }
}
