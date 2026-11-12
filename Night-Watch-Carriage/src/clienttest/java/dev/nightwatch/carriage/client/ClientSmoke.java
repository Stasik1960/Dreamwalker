package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.NightWatchCarriage;
import dev.nightwatch.carriage.client.CarriageDashboard.Control;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated client test: exercises the renderer, mixins and real client-to-server packets. */
public final class ClientSmoke implements ClientModInitializer {
    private int phase, ticks, titleTicks;
    private volatile int carriageId = -1;
    private double startX, startZ;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (phase == 0) {
            if (client.currentScreen instanceof AccessibilityOnboardingScreen) {
                client.setScreen(new TitleScreen());
                return;
            }
            if (!(client.currentScreen instanceof TitleScreen) || ++titleTicks < 20) return;
            client.options.pauseOnLostFocus = false;
            client.options.getViewDistance().setValue(5);
            GameRules rules = new GameRules();
            rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
            LevelInfo level = new LevelInfo("Carriage smoke test", GameMode.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
            client.createIntegratedServerLoader().createAndStart("carriage-qa-" + System.nanoTime(), level,
                new GeneratorOptions(42L, false, false),
                regs -> regs.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            advance();
            return;
        }
        if (++ticks > (phase == 1 ? 1600 : 200)) {
            throw new IllegalStateException("Carriage client smoke timed out in phase " + phase);
        }
        if (client.player == null || client.world == null || client.getServer() == null) return;
        if (phase == 1) {
            if (ticks < 35) return;
            var server = client.getServer();
            var id = client.player.getUuid();
            server.execute(() -> {
                ServerWorld world = server.getOverworld();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) throw new IllegalStateException("Missing local server player");
                BlockPos spawn = world.getSpawnPos();
                int sx = spawn.getX(), sz = spawn.getZ();
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 9; z++) {
                        world.setBlockState(new BlockPos(sx + x, y - 1, sz + z), Blocks.STONE_BRICKS.getDefaultState());
                    }
                }
                world.setTimeOfDay(3200);
                CarriageEntity carriage = NightWatchCarriage.CARRIAGE.create(world);
                carriage.refreshPositionAndAngles(sx + .5, y, sz + .5, 0, 0);
                if (!world.spawnEntity(carriage)) throw new IllegalStateException("Carriage smoke spawn failed");
                player.networkHandler.requestTeleport(sx - 3.6, y + .45, sz + 4.85, -137, 12);
                carriageId = carriage.getId();
            });
            advance();
            return;
        }
        if (!(client.world.getEntityById(carriageId) instanceof CarriageEntity carriage)) return;
        switch (phase) {
            case 2 -> {
                if (ticks < 30) return;
                checkDynamicLights(carriage, 0);
                screenshot(client, "01-carriage-raised.png");
                reposition(client, carriage, 2.15);
                advance();
            }
            case 3 -> {
                if (ticks == 10) pressDashboardKey(client);
                if (ticks < 14) return;
                if (client.currentScreen instanceof CarriageScreen || !client.mouse.isCursorLocked()) {
                    throw new IllegalStateException("Dashboard unlocked the cursor before boarding");
                }
                clickWorldSeat(client, carriage, CarriageEntity.Seat.RIGHT);
                advance();
            }
            case 4 -> {
                if (carriage.seatOf(client.player) != CarriageEntity.Seat.RIGHT) return;
                reposition(client, carriage, 2.15);
                advance();
            }
            case 5 -> {
                if (ticks < 10 || client.player.hasVehicle()) return;
                clickWorldSeat(client, carriage, CarriageEntity.Seat.LEFT);
                advance();
            }
            case 6 -> {
                if (carriage.seatOf(client.player) != CarriageEntity.Seat.LEFT) return;
                reposition(client, carriage, 3.42);
                advance();
            }
            case 7 -> {
                if (ticks < 10 || client.player.hasVehicle()) return;
                clickWorldSeat(client, carriage, CarriageEntity.Seat.PULLER);
                advance();
            }
            case 8 -> {
                if (carriage.seatOf(client.player) != CarriageEntity.Seat.PULLER) return;
                client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                client.player.setYaw(-40f);
                client.player.setPitch(-8f);
                if (ticks < 70) return;
                screenshot(client, "02-dashboard-hud.png");
                pressDashboardKey(client);
                advance();
            }
            case 9 -> {
                if (ticks < 8) return;
                requireCursor(client);
                screenshot(client, "03-dashboard-cursor.png");
                click(client, Control.LEFT);
                advance();
            }
            case 10 -> {
                if (carriage.seatOf(client.player) != CarriageEntity.Seat.LEFT) return;
                requireCursor(client);
                click(client, Control.PULLER);
                advance();
            }
            case 11 -> {
                if (carriage.seatOf(client.player) != CarriageEntity.Seat.PULLER) return;
                CarriageScreen screen = requireCursor(client);
                click(client, Control.ROOF);
                click(client, Control.LAMPS);
                int key = KeyBindingHelper.getBoundKeyOf(CarriageClient.panelKey).getCode();
                if (!screen.keyPressed(key, 0, 0) || client.currentScreen != null || !client.mouse.isCursorLocked()) {
                    throw new IllegalStateException("Dashboard key did not restore the camera");
                }
                var server = client.getServer();
                server.execute(() -> server.getOverworld().setTimeOfDay(13200));
                advance();
            }
            case 12 -> {
                if (ticks < 30 || carriage.hoodRaised() || !carriage.lanternsOn() || carriage.visualHood(1) > .01f) return;
                checkDynamicLights(carriage, 12);
                startX = carriage.getX();
                startZ = carriage.getZ();
                screenshot(client, "04-carriage-folded-and-hitched.png");
                advance();
            }
            case 13 -> {
                client.onWindowFocusChanged(true);
                client.options.forwardKey.setPressed(true);
                double distance = Math.hypot(carriage.getX() - startX, carriage.getZ() - startZ);
                if (ticks > 8 && distance > .4 && carriage.speed() > 0 && Math.abs(carriage.visualWheel(1f)) > 10f) {
                    client.options.forwardKey.setPressed(false);
                    screenshot(client, "05-carriage-moving.png");
                    pressDashboardKey(client);
                    advance();
                }
            }
            case 14 -> {
                if (ticks < 5) return;
                requireCursor(client);
                var server = client.getServer();
                var id = client.player.getUuid();
                server.execute(() -> server.getPlayerManager().getPlayer(id).stopRiding());
                advance();
            }
            case 15 -> {
                if (ticks < 5 || client.player.hasVehicle()) return;
                if (client.currentScreen != null || !client.mouse.isCursorLocked()) {
                    throw new IllegalStateException("Dashboard kept the cursor after dismount");
                }
                NightWatchCarriage.LOGGER.info("Carriage client smoke passed: aimed boarding at all three places, dashboard, dismount, hood, lamps and driving");
                advance();
                client.scheduleStop();
            }
            default -> { }
        }
    }

    private static void pressDashboardKey(MinecraftClient client) {
        client.onWindowFocusChanged(true);
        KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(CarriageClient.panelKey));
    }

    private static CarriageScreen requireCursor(MinecraftClient client) {
        if (!(client.currentScreen instanceof CarriageScreen screen) || client.mouse.isCursorLocked()) {
            throw new IllegalStateException("Dashboard did not unlock the cursor in the carriage");
        }
        return screen;
    }

    private static void click(MinecraftClient client, Control control) {
        var area = CarriageDashboard.layout(client, client.getWindow().getScaledWidth(),
            client.getWindow().getScaledHeight()).button(control);
        if (!requireCursor(client).mouseClicked(area.x() + area.width() / 2.0, area.y() + area.height() / 2.0, 0)) {
            throw new IllegalStateException("Dashboard button did not accept click: " + control);
        }
    }

    /** Center of the clickable harness crossbar in world space. */
    private static void reposition(MinecraftClient client, CarriageEntity carriage, double forward) {
        var server = client.getServer();
        var playerId = client.player.getUuid();
        int entityId = carriage.getId();
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            Entity vehicle = player.getWorld().getEntityById(entityId);
            player.stopRiding();
            Vec3d position = new Vec3d(0, 0, forward).rotateY((float) Math.toRadians(-vehicle.getYaw()))
                .add(vehicle.getPos());
            player.networkHandler.requestTeleport(position.x, position.y, position.z, vehicle.getYaw() + 180, 0);
        });
    }

    private static void clickWorldSeat(MinecraftClient client, CarriageEntity carriage, CarriageEntity.Seat seat) {
        Vec3d target = carriage.boardingPoint(seat, 1);
        client.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target);
        client.gameRenderer.updateTargetedEntity(1);
        if (!(client.crosshairTarget instanceof EntityHitResult hit) || hit.getEntity() != carriage) {
            throw new IllegalStateException("Crosshair did not target the carriage at " + seat + ": " + client.crosshairTarget);
        }
        client.interactionManager.interactEntityAtLocation(client.player, carriage, hit, Hand.MAIN_HAND);
    }

    private void advance() {
        phase++;
        ticks = 0;
    }

    private static void checkDynamicLights(CarriageEntity carriage, int expected) {
        if (!FabricLoader.getInstance().isModLoaded("lambdynlights")) return;
        try {
            Class<?> api = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            int level;
            try {
                Object instance = api.getMethod("get").invoke(null);
                Object sources = api.getMethod("entityLightSourceManager").invoke(instance);
                Class<?> manager = Class.forName("dev.lambdaurora.lambdynlights.api.entity.EntityLightSourceManager");
                level = (int) manager.getMethod("getLuminance", Entity.class).invoke(sources, carriage);
            } catch (NoSuchMethodException legacy) {
                Class<?> handlers = Class.forName("dev.lambdaurora.lambdynlights.api.DynamicLightHandlers");
                level = (int) handlers.getMethod("getLuminanceFrom", Entity.class).invoke(null, carriage);
            }
            if (level != expected) throw new IllegalStateException("Carriage lamp value: " + level + ", expected " + expected);
            NightWatchCarriage.LOGGER.info("LambDynamicLights reported carriage luminance {}", level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to query LambDynamicLights during smoke test", e);
        }
    }

    private static void screenshot(MinecraftClient client, String name) {
        try {
            Path dir = Path.of("screenshots");
            Files.createDirectories(dir);
            try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(dir.resolve(name));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to capture " + name, e);
        }
    }
}
