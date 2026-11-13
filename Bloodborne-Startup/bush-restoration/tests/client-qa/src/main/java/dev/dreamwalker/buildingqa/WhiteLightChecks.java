package dev.dreamwalker.buildingqa;

import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.LightType;

/** Looks at the white marker inside a sealed room and measures the rendered pixels. */
final class WhiteLightChecks {
    private static final Identifier LIGHT = new Identifier("bloodborne_dw", "dw_whiteness");
    private static final BlockPos TARGET = WindowRotationChecks.OPEN_WINDOW.add(-3, 20, -8);
    private CompletableFuture<Void> prepare;
    private int wait;
    private boolean previousHud;
    private GraphicsMode previousGraphics;

    String tick(MinecraftClient game) throws Exception {
        if (prepare == null) {
            previousHud = game.options.hudHidden;
            game.options.hudHidden = true;
            previousGraphics = game.options.getGraphicsMode().getValue();
            game.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            var server = game.getServer();
            var playerId = game.player.getUuid();
            prepare = server.submit(() -> setup(server.getPlayerManager().getPlayer(playerId)));
            return null;
        }
        if (!prepare.isDone()) return null;
        prepare.join();
        if (++wait > 180) throw new AssertionError("white Light room or camera did not load");
        if (wait < 35 || game.currentScreen != null || !game.world.isChunkLoaded(TARGET)
                || Math.abs(game.player.getX() - (TARGET.getX() + .5)) > .01
                || Math.abs(game.player.getZ() - (TARGET.getZ() - 2.5)) > .01) return null;

        var state = game.world.getBlockState(TARGET);
        if (!state.isOf(Registries.BLOCK.get(LIGHT)) || game.world.getBlockEntity(TARGET) == null) {
            throw new AssertionError("white Light is missing its block or block entity");
        }
        if (state.getLuminance() != 0 || game.world.getLightLevel(LightType.BLOCK, TARGET.north()) != 0
                || game.world.getLightLevel(LightType.SKY, TARGET.north()) != 0) {
            throw new AssertionError("white Light leaked illumination or room is not dark");
        }
        ItemStack item = new ItemStack(Registries.ITEM.get(LIGHT));
        var network = game.getNetworkHandler();
        ItemGroups.updateDisplayContext(network.getEnabledFeatures(), game.player.hasPermissionLevel(2),
                network.getRegistryManager());
        if (ItemGroups.getGroupsToDisplay().stream().noneMatch(group -> group.contains(item))) {
            throw new AssertionError("white Light is absent from the creative inventory");
        }

        try (NativeImage image = ScreenshotRecorder.takeScreenshot(game.getFramebuffer())) {
            image.writeTo(game.runDirectory.toPath().resolve("qa-white-light.png"));
            int bright = 0;
            for (int dy = -12; dy <= 12; dy++) {
                for (int dx = -12; dx <= 12; dx++) {
                    int pixel = image.getColor(image.getWidth() / 2 + dx, image.getHeight() / 2 + dy);
                    if ((pixel & 255) >= 247 && ((pixel >>> 8) & 255) >= 247 && ((pixel >>> 16) & 255) >= 247) bright++;
                }
            }
            if (bright < 540) throw new AssertionError("white Light was shaded in the dark room (" + bright + "/625)");
        }
        return "PASS white Light creative tab, opaque white render in darkness and zero emitted light";
    }

    void cleanup(MinecraftClient game) {
        game.options.hudHidden = previousHud;
        game.options.getGraphicsMode().setValue(previousGraphics);
    }

    private static void setup(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        for (int z = -6; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 3; y++) {
                    boolean shell = Math.abs(x) == 2 || y == -2 || y == 3 || z == -6 || z == 2;
                    world.setBlockState(TARGET.add(x, y, z),
                            (shell ? Blocks.BLACK_CONCRETE : Blocks.AIR).getDefaultState());
                }
            }
        }
        world.setBlockState(TARGET.down(), Blocks.BLACK_CONCRETE.getDefaultState());
        player.changeGameMode(GameMode.CREATIVE);
        player.clearStatusEffects();
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN)) {
            player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        var item = (BlockItem) Registries.ITEM.get(LIGHT);
        var stack = new ItemStack(item);
        var hit = new BlockHitResult(Vec3d.ofCenter(TARGET).add(0, -.5, 0), Direction.UP, TARGET.down(), false);
        if (!item.place(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit)).isAccepted()) {
            throw new AssertionError("could not place white Light against the dark-room support");
        }
        player.teleport(world, TARGET.getX() + .5, TARGET.getY() - 1, TARGET.getZ() - 2.5, 0, 2.6f);
    }
}
