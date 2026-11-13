package dev.dreamwalker.buildingqa;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

/** Uses normal movement and the installed Crawl binding with the real 93241 item fixture. */
final class WindowCrawlChecks {
    private static final int CRAWL_KEY = GLFW.GLFW_KEY_F8;
    private static final int FORWARD_KEY = GLFW.GLFW_KEY_W;
    private static final double PANE = WindowRotationChecks.OPEN_WINDOW.getZ() + 15.0 / 16;
    private enum Stage { WAIT, WALK, LOWER, CRAWL, SERVER }
    private record ServerPosition(double z, String pose) {}

    private CompletableFuture<Void> setup;
    private CompletableFuture<ServerPosition> serverPosition;
    private KeyBinding crawlKey;
    private SimpleOption<Boolean> crawlToggle;
    private boolean previousCrawlToggle;
    private InputUtil.Key previousCrawlKey;
    private InputUtil.Key previousForwardKey;
    private Stage stage = Stage.WAIT;
    private int ticks;

    String tick(MinecraftClient game) throws Exception {
        if (setup == null) {
            initialize(game);
            return null;
        }
        if (!setup.isDone()) return null;
        setup.join();
        ++ticks;
        if (ticks > 160) {
            throw new AssertionError("window movement timed out at " + stage + ", z=" + game.player.getZ()
                    + ", pose=" + game.player.getPose());
        }

        switch (stage) {
            case WAIT -> {
                if (ticks < 30 || game.currentScreen != null
                        || game.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL
                        || Math.abs(game.player.getZ() - (WindowRotationChecks.OPEN_WINDOW.getZ() + .25)) > .02) {
                    return null;
                }
                key(game, FORWARD_KEY, true);
                advance(Stage.WALK);
            }
            case WALK -> {
                if (ticks < 25) return null;
                key(game, FORWARD_KEY, false);
                if (game.player.getPose() != EntityPose.STANDING
                        || game.player.getZ() >= PANE || game.player.getZ() < PANE - .48) {
                    throw new AssertionError("standing player did not stop at the open glass, z=" + game.player.getZ()
                            + ", pose=" + game.player.getPose());
                }
                screenshot(game, "qa-window-standing.png");
                key(game, CRAWL_KEY, true);
                advance(Stage.LOWER);
            }
            case LOWER -> {
                if (!isCrawling(game)) return null;
                ServerPosition position = observeServer(game);
                if (position == null || !"CRAWLING".equals(position.pose())) return null;
                screenshot(game, "qa-window-crawling.png");
                key(game, FORWARD_KEY, true);
                advance(Stage.CRAWL);
            }
            case CRAWL -> {
                if (game.player.getZ() < PANE + .40 || !isCrawling(game)) return null;
                advance(Stage.SERVER);
            }
            case SERVER -> {
                ServerPosition position = observeServer(game);
                if (position == null || position.z() <= PANE || !"CRAWLING".equals(position.pose())) return null;
                cleanup(game);
                return "PASS 93241 blocks standing movement and admits Crawl through the open window";
            }
        }
        return null;
    }

    private ServerPosition observeServer(MinecraftClient game) {
        if (serverPosition == null) {
            var server = game.getServer();
            var playerId = game.player.getUuid();
            serverPosition = server.submit(() -> {
                var player = server.getPlayerManager().getPlayer(playerId);
                return new ServerPosition(player.getZ(), player.getPose().name());
            });
        }
        if (!serverPosition.isDone()) return null;
        ServerPosition position = serverPosition.join();
        serverPosition = null;
        return position;
    }

    void cleanup(MinecraftClient game) {
        if (crawlKey == null) return;
        key(game, FORWARD_KEY, false);
        key(game, CRAWL_KEY, false);
        crawlKey.setBoundKey(previousCrawlKey);
        game.options.forwardKey.setBoundKey(previousForwardKey);
        crawlToggle.setValue(previousCrawlToggle);
        KeyBinding.updateKeysByCode();
        crawlKey = null;
    }

    @SuppressWarnings("unchecked")
    private void initialize(MinecraftClient game) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("crawl")) {
            throw new AssertionError("Crawl is missing from the disposable client");
        }
        Class<?> crawlClient = Class.forName("ru.fewizz.crawl.CrawlClient");
        crawlToggle = (SimpleOption<Boolean>) crawlClient.getField("crawlToggled").get(null);
        previousCrawlToggle = crawlToggle.getValue();
        crawlToggle.setValue(false);
        crawlKey = (KeyBinding) crawlClient.getField("key").get(null);
        previousCrawlKey = InputUtil.fromTranslationKey(crawlKey.getBoundKeyTranslationKey());
        previousForwardKey = InputUtil.fromTranslationKey(game.options.forwardKey.getBoundKeyTranslationKey());
        crawlKey.setBoundKey(InputUtil.fromKeyCode(CRAWL_KEY, 0));
        game.options.forwardKey.setBoundKey(InputUtil.fromKeyCode(FORWARD_KEY, 0));
        KeyBinding.updateKeysByCode();
        var server = game.getServer();
        var playerId = game.player.getUuid();
        setup = server.submit(() -> prepare(server.getPlayerManager().getPlayer(playerId)));
    }

    private static void prepare(ServerPlayerEntity player) {
        var window = WindowRotationChecks.OPEN_WINDOW;
        var world = player.getServerWorld();
        var state = world.getBlockState(window);
        if (!state.contains(Properties.OPEN) || !state.get(Properties.OPEN)) {
            throw new AssertionError("missing player-placed open 93241 at " + window);
        }
        for (int z = -1; z <= 3; z++) {
            world.setBlockState(window.down().south(z), Blocks.STONE.getDefaultState());
        }
        player.setSneaking(false);
        player.changeGameMode(GameMode.SURVIVAL);
        player.teleport(world, window.getX() + .5, window.getY() + 6.0 / 16, window.getZ() + .25, 0, 0);
    }

    private static boolean isCrawling(MinecraftClient game) {
        return "CRAWLING".equals(game.player.getPose().name());
    }

    private static void key(MinecraftClient game, int code, boolean pressed) {
        game.keyboard.onKey(game.getWindow().getHandle(), code, 0,
                pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    private void advance(Stage next) {
        stage = next;
        ticks = 0;
    }

    private static void screenshot(MinecraftClient game, String name) throws Exception {
        try (var image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve(name));
        }
    }
}
