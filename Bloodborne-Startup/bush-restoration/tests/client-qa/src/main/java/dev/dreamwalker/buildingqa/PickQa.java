package dev.dreamwalker.buildingqa;

import dev.dreamwalker.buildingqa.mixin.BackupPromptAccessor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

/** Drives the Original Pick key and checks the inventory on both ends of the game connection. */
public final class PickQa implements ClientModInitializer {
    private record Aim(double x, double y, double z, float yaw, float pitch, BlockPos target, String item,
                       String variant) {}

    private static final String DW = "bloodborne_dw:";
    private static final List<Aim> CASES = List.of(
            new Aim(97.484, 122.90780, -707.696, 88.4f, 5.2f, new BlockPos(93, 124, -708),
                    DW + "source_addon_wall_deco_3_form_2", null),
            new Aim(444.405, 43.00313, -838.782, -64.2f, 5.0f, new BlockPos(447, 44, -838),
                    DW + "source_black_wool", "0"),
            new Aim(444.809, 43.00313, -833.297, -9.6f, 9.2f, new BlockPos(445, 44, -831),
                    DW + "source_cyan_glazed_terracotta", "0"),
            new Aim(445.021, 43.00313, -836.632, -7.9f, 38.7f, new BlockPos(445, 43, -835),
                    DW + "source_gray_wool", "0"),
            new Aim(226.867, 50.665, -819.466, -21.6f, 10.2f, new BlockPos(228, 51, -815),
                    DW + "source_weathered_cut_copper", null),
            new Aim(141.5, 89.98, -786.5, 90.0f, 65.0561f, new BlockPos(140, 89, -787),
                    DW + "connected_fence_93185_post", null),
            new Aim(131.5, 89.98, -781.5, -90.0f, 65.0561f, new BlockPos(132, 89, -782),
                    DW + "connected_fence_93185_plain", null)
    );

    private final List<String> results = new ArrayList<>();
    private int test, ticks, failures;
    private boolean moved, pressed, done;
    private BackupPromptScreen acknowledged;
    private CompletableFuture<ItemStack> serverStack;
    private CompletableFuture<List<String>> serverChecks;
    private final GlassChecks glass = Boolean.getBoolean("dw.qa.glass") ? new GlassChecks() : null;
    private WhiteLightChecks whiteLight = Boolean.getBoolean("dw.qa.whiteLight") ? new WhiteLightChecks() : null;
    private WindowCrawlChecks windowCrawl = Boolean.getBoolean("dw.qa.crawl") ? new WindowCrawlChecks() : null;
    private ConnectedFenceGallery fenceGallery = Boolean.getBoolean("dw.qa.fenceGallery")
            ? new ConnectedFenceGallery() : null;
    private boolean glassFinished;
    private ModelShiftChecks shift;
    private DoorSoundChecks doorSounds = new DoorSoundChecks();
    private DoorConsoleVisualChecks doorConsole = new DoorConsoleVisualChecks();
    private FenceWallViewChecks fenceWallView = Boolean.getBoolean("dw.qa.fenceGallery") ? new FenceWallViewChecks() : null;
    private int serverWait;
    private boolean serverTestFinished;

    @Override
    public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Boolean.getBoolean("dw.qa.disposable")
                || !Files.isRegularFile(client.runDirectory.toPath().resolve("disposable-building-qa.marker"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(game -> {
            if (done) return;
            try {
                if (game.currentScreen instanceof BackupPromptScreen prompt && prompt != acknowledged
                        && game.getServer() == null) {
                    acknowledged = prompt;
                    System.out.println("DW PICK QA continuing backup prompt in the disposable copy");
                    ((BackupPromptAccessor) prompt).dw$getCallback().proceed(false, false);
                }
                if (game.world != null && game.player != null && game.getServer() != null) tick(game);
            } catch (Throwable failure) {
                if (windowCrawl != null) windowCrawl.cleanup(game);
                failure.printStackTrace();
                results.add("FAIL " + failure);
                finish(game, false);
            }
        });
    }

    private void tick(MinecraftClient game) throws Exception {
        if (Boolean.getBoolean("dw.qa.shiftOnly")) {
            if (shift == null) shift = new ModelShiftChecks();
            String outcome = shift.tick(game);
            if (outcome == null) return;
            note(outcome);
            finish(game, outcome.startsWith("PASS"));
            return;
        }
        if (!serverTestFinished) {
            checkServer(game);
            return;
        }
        if (doorSounds != null) {
            try {
                String outcome = doorSounds.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                note("FAIL door sound client test: " + failure);
                failures++;
            }
            doorSounds.cleanup();
            doorSounds = null;
            return;
        }
        if (doorConsole != null) {
            try {
                String outcome = doorConsole.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                note("FAIL door and console visuals: " + failure);
                failures++;
            }
            doorConsole.cleanup(game);
            doorConsole = null;
            return;
        }
        if (whiteLight != null) {
            try {
                String outcome = whiteLight.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                note("FAIL white Light client test: " + failure);
                failures++;
            }
            whiteLight.cleanup(game);
            whiteLight = null;
            return;
        }
        if (windowCrawl != null) {
            try {
                String outcome = windowCrawl.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                windowCrawl.cleanup(game);
                note("FAIL Crawl client test: " + failure);
                failures++;
            }
            windowCrawl = null;
            return;
        }
        if (fenceWallView != null) {
            try {
                String outcome = fenceWallView.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                note("FAIL fence wall client test: " + failure);
                failures++;
            }
            fenceWallView.cleanup(game);
            fenceWallView = null;
            return;
        }
        if (fenceGallery != null) {
            try {
                String outcome = fenceGallery.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
            } catch (Exception | AssertionError failure) {
                note("FAIL fence client gallery: " + failure);
                failures++;
            }
            fenceGallery.cleanup(game);
            fenceGallery = null;
            return;
        }
        if (test == CASES.size()) {
            if (glass != null && !glassFinished) {
                String outcome = glass.tick(game);
                if (outcome == null) return;
                note(outcome);
                if (outcome.startsWith("FAIL")) failures++;
                glassFinished = true;
            }
            finish(game, failures == 0);
            return;
        }
        Aim aim = CASES.get(test);
        ticks++;
        if (!moved) {
            if (ticks < 20) return;
            move(game, aim);
            moved = true;
            ticks = 0;
            return;
        }
        if (ticks > 220) {
            note("FAIL J " + aim.target().toShortString() + " hit=" + game.crosshairTarget
                    + " item=" + describe(game.player.getMainHandStack()) + " pressed=" + pressed
                    + " screen=" + (game.currentScreen == null ? "none" : game.currentScreen.getClass().getName()));
            failures++;
            if (game.currentScreen != null) game.setScreen(null);
            next();
            return;
        }
        if (!pressed) {
            if (ticks < 12 || !game.world.isChunkLoaded(aim.target()) || game.currentScreen != null
                    || !game.player.getAbilities().creativeMode || Math.abs(game.player.getX() - aim.x()) > .05) return;
            game.gameRenderer.updateTargetedEntity(1);
            if (!(game.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || !hit.getBlockPos().equals(aim.target())) return;
            long window = game.getWindow().getHandle();
            game.keyboard.onKey(window, GLFW.GLFW_KEY_J, 0, GLFW.GLFW_PRESS, 0);
            game.keyboard.onKey(window, GLFW.GLFW_KEY_J, 0, GLFW.GLFW_RELEASE, 0);
            pressed = true;
            ticks = 0;
            return;
        }
        ItemStack held = game.player.getMainHandStack();
        if (!aim.item().equals(Registries.ITEM.getId(held.getItem()).toString())) return;
        var data = held.getSubNbt("BlockStateTag");
        if (aim.variant() != null && (data == null || !aim.variant().equals(data.getString("variant")))) {
            throw new IllegalStateException("Unexpected variant: " + describe(held));
        }
        if (serverStack == null && ticks >= 4) {
            var server = game.getServer();
            var id = game.player.getUuid();
            serverStack = server.submit(() -> server.getPlayerManager().getPlayer(id).getMainHandStack().copy());
        }
        if (serverStack == null || !serverStack.isDone()) return;
        if (!ItemStack.areEqual(held, serverStack.join())) throw new IllegalStateException("Inventory desynchronized");
        note("PASS J " + aim.target().toShortString() + " -> " + describe(held) + " (server confirmed)");
        next();
    }

    private void checkServer(MinecraftClient game) {
        var server = game.getServer();
        var id = game.player.getUuid();
        if (serverChecks == null) {
            serverChecks = server.submit(() -> WorldChecks.start(server.getPlayerManager().getPlayer(id)));
            serverWait = 0;
            return;
        }
        if (!serverChecks.isDone() || ++serverWait < 8) return;
        for (String outcome : serverChecks.join()) {
            note(outcome);
            if (outcome.startsWith("FAIL")) failures++;
        }
        server.submit(() -> WorldChecks.finish(server.getPlayerManager().getPlayer(id)))
                .whenComplete((outcome, failure) -> game.execute(() -> {
                    if (failure != null) {
                        note("FAIL asynchronous integrated server checks: " + failure);
                        failures++;
                    }
                    if (outcome != null) {
                        note(outcome);
                        if (outcome.startsWith("FAIL")) failures++;
                    }
                    serverTestFinished = true;
                }));
        serverWait = Integer.MIN_VALUE;
    }

    private static void move(MinecraftClient game, Aim aim) {
        var server = game.getServer();
        var id = game.player.getUuid();
        server.execute(() -> {
            var player = server.getPlayerManager().getPlayer(id);
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            player.teleport(player.getServerWorld(), aim.x(), aim.y(), aim.z(), aim.yaw(), aim.pitch());
        });
    }

    private void next() {
        test++;
        ticks = 0;
        moved = false;
        pressed = false;
        serverStack = null;
    }

    private static String describe(ItemStack stack) {
        var data = stack.getSubNbt("BlockStateTag");
        return Registries.ITEM.getId(stack.getItem()) + (data == null ? "" : " " + data);
    }

    private void note(String message) {
        results.add(message);
        System.out.println("DW PICK QA " + message);
    }

    private void finish(MinecraftClient game, boolean success) {
        done = true;
        note(success ? "PASS integrated building/Original Pick QA" : "FAIL integrated building/Original Pick QA");
        try {
            Files.write(game.runDirectory.toPath().resolve("qa-pick-result.txt"), results);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        game.scheduleStop();
    }
}
