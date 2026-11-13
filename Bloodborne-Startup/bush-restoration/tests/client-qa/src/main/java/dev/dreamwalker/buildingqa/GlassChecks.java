package dev.dreamwalker.buildingqa;

import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
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

/** Measures actual player pixels through a composite block, with opaque and unobstructed controls. */
final class GlassChecks {
    private static final BlockPos GLASS = new BlockPos(227, 300, -817);
    private static final Identifier GLASS_ID = new Identifier("bloodborne_dw:source_light_blue_stained_glass");
    private static final int DUMMY_ID = -42;
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("dd8aa755-6945-4e6e-8805-56e80eeb9b0a"), "DWGlassModel");
    private static final String[] FIXTURES = {"glass", "opaque", "open"};

    private final int[][] absent = new int[FIXTURES.length][];
    private final int[] changed = new int[FIXTURES.length];
    private CompletableFuture<Void> serverWork;
    private OtherClientPlayerEntity dummy;
    private boolean withPlayer;
    private int fixture;
    private int wait;

    String tick(MinecraftClient client) throws Exception {
        if (serverWork == null) {
            client.options.hudHidden = true;
            var server = client.getServer();
            var playerId = client.player.getUuid();
            int station = fixture;
            serverWork = server.submit(() -> {
                var player = server.getPlayerManager().getPlayer(playerId);
                if (station == 0) setup(player);
                player.teleport(player.getServerWorld(), GLASS.getX() + station + .5, 299, -819.5, 0, 4.7f);
            });
            return null;
        }
        if (!serverWork.isDone()) return null;
        serverWork.join();
        if (++wait > 180) throw new IllegalStateException("The glass camera or fixture did not load");
        BlockPos station = GLASS.add(fixture, 0, 0);
        if (wait < 24 || client.currentScreen != null || !client.world.isChunkLoaded(station)
                || Math.abs(client.player.getX() - (station.getX() + .5)) > .01
                || Math.abs(client.player.getY() - 299) > .01
                || Math.abs(client.player.getZ() + 819.5) > .01) return null;
        if (fixture == 0 && (!Registries.BLOCK.getId(client.world.getBlockState(station).getBlock()).equals(GLASS_ID)
                || client.world.getBlockEntity(station) == null)) {
            throw new IllegalStateException("Composite glass is missing on the client");
        }
        int[] pixels = capture(client, client.runDirectory.toPath().resolve("qa-glass-" + FIXTURES[fixture]
                + (withPlayer ? "-player" : "-empty") + ".png"));
        wait = 0;
        if (!withPlayer) {
            absent[fixture] = pixels;
            dummy = new OtherClientPlayerEntity(client.world, PROFILE);
            dummy.setNoGravity(true);
            dummy.refreshPositionAndAngles(station.getX() + .5, 299, -814.4, 180, 0);
            dummy.setHeadYaw(180);
            dummy.setBodyYaw(180);
            dummy.setId(DUMMY_ID);
            client.world.addPlayer(DUMMY_ID, dummy);
            withPlayer = true;
            return null;
        }
        changed[fixture] = difference(absent[fixture], pixels);
        client.world.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
        withPlayer = false;
        serverWork = null;
        if (++fixture < FIXTURES.length) return null;
        int tint = difference(absent[0], absent[2]);
        String counts = " (glass=" + changed[0] + ", opaque=" + changed[1] + ", open=" + changed[2]
                + ", glass vs. open=" + tint + " changed pixels)";
        return changed[0] >= 35 && changed[1] <= 12 && changed[2] >= 70 && tint >= 70
                ? "PASS player model visible through composite glass" + counts
                : "FAIL composite glass player pixels or controls" + counts;
    }

    private static void setup(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        for (int x = 225; x <= 231; x++) {
            for (int z = -821; z <= -812; z++) {
                world.setBlockState(new BlockPos(x, 298, z), Blocks.STONE.getDefaultState());
            }
            for (int y = 299; y <= 302; y++) {
                world.setBlockState(new BlockPos(x, y, -812), Blocks.WHITE_CONCRETE.getDefaultState());
            }
        }
        world.setBlockState(GLASS.east(), Blocks.BLACK_CONCRETE.getDefaultState());
        world.setBlockState(GLASS.down(), Blocks.STONE.getDefaultState());
        var before = player.getMainHandStack().copy();
        var stack = new ItemStack(Registries.ITEM.get(GLASS_ID));
        player.setYaw(0);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        try {
            var hit = new BlockHitResult(Vec3d.ofCenter(GLASS).add(0, -.5, 0), Direction.UP, GLASS.down(), false);
            if (!stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted()) {
                throw new IllegalStateException("Composite glass item placement failed");
            }
        } finally {
            player.setStackInHand(Hand.MAIN_HAND, before);
        }
    }

    private static int[] capture(MinecraftClient client, Path file) throws Exception {
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(file);
            int radius = Math.max(12, image.getHeight() / 30);
            int[] pixels = new int[4 * radius * radius];
            int index = 0;
            for (int y = -radius; y < radius; y++) {
                for (int x = -radius; x < radius; x++) {
                    pixels[index++] = image.getColor(image.getWidth() / 2 + x, image.getHeight() / 2 + y);
                }
            }
            return pixels;
        }
    }

    private static int difference(int[] first, int[] second) {
        int changed = 0;
        for (int i = 0; i < first.length; i++) {
            int distance = 0;
            for (int shift = 0; shift < 24; shift += 8) {
                distance += Math.abs((first[i] >>> shift & 255) - (second[i] >>> shift & 255));
            }
            if (distance > 28) changed++;
        }
        return changed;
    }
}
