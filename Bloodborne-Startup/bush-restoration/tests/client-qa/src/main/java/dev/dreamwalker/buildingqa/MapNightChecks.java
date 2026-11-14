package dev.dreamwalker.buildingqa;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;

final class MapNightChecks {
    private static final BlockPos ARCH_FACE = new BlockPos(97, 100, -768);
    private static final BlockPos STRAY_LIGHT = new BlockPos(47, 8, -1105);
    private record ExpectedBlock(BlockPos pos, String id) {}
    private static final List<ExpectedBlock> RESTORED = List.of(
        new ExpectedBlock(new BlockPos(170, 59, -992), "yuushya:red_curtain"),
        new ExpectedBlock(new BlockPos(176, 59, -983), "yuushya:peking_roast_duck"),
        new ExpectedBlock(new BlockPos(168, 67, -983), "yuushya:carpet_3"),
        new ExpectedBlock(new BlockPos(165, 58, -997), "handcrafted:spruce_cupboard"),
        new ExpectedBlock(new BlockPos(20, -4, -1103), "minecraft:dark_oak_wood"),
        new ExpectedBlock(new BlockPos(18, 0, -1106), "minecraft:oak_leaves")
    );
    private record Scene(String name, double x, double y, double z, float yaw, float pitch, BlockPos focus,
                         long time, String dimension) {
        Scene(String name, double x, double y, double z, float yaw, float pitch, BlockPos focus, long time) {
            this(name, x, y, z, yaw, pitch, focus, time, "minecraft:overworld");
        }
    }
    private static final List<Scene> SCENES = List.of(
        new Scene("arch-day", 97.076, 98.59078, -770.012, -3.8f, -.6f, ARCH_FACE, 6000),
        new Scene("arch-night", 97.076, 98.59078, -770.012, -3.8f, -.6f, ARCH_FACE, 18000),
        new Scene("lower-light-night", 47.5, 8.1, -1108.5, 0, -8, STRAY_LIGHT, 18000),
        new Scene("west-light-night", -111.5, 24, -770.5, 0, 0, new BlockPos(-112, 25, -768), 18000),
        new Scene("interior-ground-night", 173.5, 58.15, -975.5, 180, 3, new BlockPos(177, 60, -980), 18000),
        new Scene("interior-upper-night", 167.5, 67, -983.5, 180, 6, new BlockPos(168, 67, -986), 18000),
        new Scene("vegetation-day", 28.5, 1.7, -1090.5, 180, 5, new BlockPos(28, 2, -1097), 6000),
        new Scene("vegetation-night", 28.5, 1.7, -1090.5, 180, 5, new BlockPos(28, 2, -1097), 18000),
        new Scene("old-light-night", 34.5, 87, 139.5, 145, 0,
            new BlockPos(32, 87, 136), 18000, "bloodborne_dw:bloodborne_old")
    );
    private final List<String> lines = new ArrayList<>();
    private final List<Scene> scenes;
    private int index, waited;
    private CompletableFuture<String> setup;

    MapNightChecks() {
        String selected = System.getProperty("dw.qa.mapScene", "");
        scenes = SCENES.stream().filter(scene -> selected.isEmpty() || scene.name().equals(selected)).toList();
        if (scenes.isEmpty()) throw new IllegalArgumentException("Unknown map scene " + selected);
    }

    String tick(MinecraftClient game) throws Exception {
        Scene scene = scenes.get(index);
        if (setup == null) {
            game.options.hudHidden = true;
            var server = game.getServer();
            var id = game.player.getUuid();
            setup = server.submit(() -> {
                var player = server.getPlayerManager().getPlayer(id);
                var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(scene.dimension())));
                if (world == null) throw new IllegalStateException("Missing dimension " + scene.dimension());
                player.changeGameMode(GameMode.SPECTATOR);
                var overworld = server.getOverworld();
                overworld.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                overworld.setTimeOfDay(scene.time());
                player.teleport(world, scene.x(), scene.y(), scene.z(), scene.yaw(), scene.pitch());
                return world.getRegistryKey().getValue().toString();
            });
            return null;
        }
        if (++waited > 1200) throw new IllegalStateException("Terrain did not finish for " + scene.name());
        if (waited < 100 || !setup.isDone() || game.currentScreen != null
                || Math.abs(game.player.getX() - scene.x()) > .1
                || Math.abs(game.player.getY() - scene.y()) > .1
                || Math.abs(game.player.getZ() - scene.z()) > .1
                || !game.world.getRegistryKey().getValue().toString().equals(scene.dimension())
                || game.world.getTimeOfDay() % 24000 != scene.time()
                || !game.worldRenderer.isRenderingReady(scene.focus())) return null;
        if (scene.name().startsWith("interior") || scene.name().startsWith("vegetation")) {
            for (var block : RESTORED) {
                boolean vegetation = block.pos().getY() < 10;
                if (vegetation != scene.name().startsWith("vegetation")) continue;
                if (!game.world.isChunkLoaded(block.pos())) return null;
                var actual = Registries.BLOCK.getId(game.world.getBlockState(block.pos()).getBlock());
                if (!actual.toString().equals(block.id())) {
                    throw new IllegalStateException("Restored block at " + block.pos() + " is " + actual
                        + ", expected " + block.id());
                }
            }
        }
        var state = game.world.getBlockState(scene.focus());
        int sky = game.world.getLightLevel(LightType.SKY, scene.focus());
        int block = game.world.getLightLevel(LightType.BLOCK, scene.focus());
        if (scene.focus().equals(ARCH_FACE) && sky < 12) {
            throw new IllegalStateException("Repaired arch has sky light " + sky);
        }
        if ((scene.name().equals("west-light-night") && sky < 14)
                || (scene.name().equals("old-light-night") && sky < 11)) {
            throw new IllegalStateException(scene.name() + " still has sky light " + sky);
        }
        if (scene.focus().equals(STRAY_LIGHT) && block == 0) {
            throw new IllegalStateException("Known lantern's light is missing in the lower street");
        }
        String observed = scene.name() + " " + setup.join() + " time=" + scene.time() + " state="
            + Registries.BLOCK.getId(state.getBlock()) + " sky=" + sky + " block=" + block;
        try (var image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve("qa-map-" + scene.name() + ".png"));
        }
        lines.add(observed);
        Files.write(game.runDirectory.toPath().resolve("qa-map-light.txt"), lines);
        setup = null;
        waited = 0;
        if (++index < scenes.size()) return null;
        return "PASS client map captures: " + scenes.stream().map(Scene::name).collect(Collectors.joining(", "));
    }
}
