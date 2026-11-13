package dev.dreamwalker.citypick;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairLegacyArt;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualLayout;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CityOriginalPick implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("dw-city-original-pick");
    private static final int NO_FRAGMENT = Integer.MIN_VALUE;
    private final PickIndex table = new PickIndex();
    private long requestNumber;
    private boolean indexErrorLogged;

    @Override
    public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dw_city_original_pick.pick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
                "category.dw_city_original_pick"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!key.wasPressed()) return;
            while (key.wasPressed()) {
                // Collapse multiple queued input events into one pick.
            }
            request(client);
        });
    }

    private void request(MinecraftClient client) {
        long number = ++requestNumber;
        var world = client.world;
        var player = client.player;
        if (client.currentScreen != null || world == null || player == null || !player.getAbilities().creativeMode
                || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos position = hit.getBlockPos().toImmutable();
        int visual = fragmentId(world, position);
        if (visual == NO_FRAGMENT) return;
        String catalog = NativeVisualLayout.catalogSha();
        String version = FabricLoader.getInstance().getModContainer("bloodborne_dw")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
        var dimension = world.getRegistryKey().getValue();
        var chunk = new PickIndex.ChunkKey(dimension.getNamespace(), dimension.getPath(),
                position.getX() >> 4, position.getZ() >> 4);
        int cell = PickIndex.cellKey(position.getX(), position.getY(), position.getZ());
        var camera = client.getCameraEntity();
        var eye = camera == null ? player.getEyePos() : camera.getCameraPosVec(client.getTickDelta());
        var direction = hit.getPos().subtract(eye).normalize();
        var ray = new FragmentRay(eye.x - position.getX(), eye.y - position.getY(), eye.z - position.getZ(),
                direction.x, direction.y, direction.z);
        CompletableFuture.supplyAsync(() -> {
            try {
                return table.find(chunk, cell, visual, catalog, version, ray);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }, Util.getIoWorkerExecutor()).whenComplete((poses, failure) -> client.execute(() -> {
            if (failure != null) {
                if (!indexErrorLogged) LOGGER.warn("Unable to read the Bloodborne original-pick index", failure);
                indexErrorLogged = true;
                return;
            }
            if (number != requestNumber || client.currentScreen != null || poses.isEmpty()
                    || !stillPresent(client, world, player, position, visual)) return;
            List<ItemStack> choices = new ArrayList<>();
            for (var pose : poses) {
                var stack = sourceStack(pose);
                if (!stack.isEmpty() && choices.stream().noneMatch(existing -> ItemStack.canCombine(existing, stack))) {
                    choices.add(stack);
                }
            }
            if (choices.size() == 1) pick(client, player, choices.get(0));
            else if (!choices.isEmpty()) client.setScreen(new OriginalChoiceScreen(choices, choice -> {
                if (stillPresent(client, world, player, position, visual)) pick(client, player, choice);
            }));
        }));
    }

    private static boolean stillPresent(MinecraftClient client, ClientWorld world, ClientPlayerEntity player,
                                        BlockPos position, int visual) {
        return client.world == world && client.player == player && client.interactionManager != null
                && player.getAbilities().creativeMode && world.isChunkLoaded(position)
                && fragmentId(world, position) == visual;
    }

    private static int fragmentId(ClientWorld world, BlockPos position) {
        var state = world.getBlockState(position);
        if (state.getBlock() instanceof NativePhysicsBlock physical) {
            int visual = NativeVisuals.index(world, position);
            if (visual >= 0 && NativeVisualLayout.physics(visual) == physical.physics(state)) return visual;
        } else if (state.getBlock() instanceof StaticArchitectureBlock legacy) {
            int recipe = legacy.bank() * StaticArchitectureCatalog.BANK_SIZE + state.get(StaticArchitectureBlock.PART);
            return -1 - recipe;
        }
        return NO_FRAGMENT;
    }

    static ItemStack sourceStack(PickIndex.Pose pose) {
        Identifier id = new Identifier(pose.registryId());
        if (!Registries.ITEM.containsId(id) || !(Registries.ITEM.get(id) instanceof BlockItem item)) {
            return ItemStack.EMPTY;
        }
        if (item.getBlock() instanceof CompositeRootBlock root) {
            BlockState state = root.getDefaultState();
            for (var entry : pose.properties().entrySet()) {
                var property = root.getStateManager().getProperty(entry.getKey());
                if (property != null) {
                    state = apply(state, property, entry.getValue());
                    if (state == null) return ItemStack.EMPTY;
                }
            }
            NbtCompound payload = ImportedTypeAliases.markPayload(new NbtCompound());
            if (!pose.legacyArt().isEmpty() && ImportedStairLegacyArt.validKey(pose.legacyArt())) {
                payload.putString(ImportedStairLegacyArt.KEY, pose.legacyArt());
            }
            try {
                return root.art(state, payload);
            } catch (IllegalArgumentException | IllegalStateException invalidSource) {
                LOGGER.debug("Unavailable source state {}", id, invalidSource);
                return ItemStack.EMPTY;
            }
        }
        var stack = new ItemStack(item);
        if (!pose.properties().isEmpty()) {
            var tag = stack.getOrCreateSubNbt("BlockStateTag");
            pose.properties().forEach(tag::putString);
        }
        return stack;
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String name) {
        return property.parse(name).map(value -> state.with(property, value)).orElse(null);
    }

    private static void pick(MinecraftClient client, ClientPlayerEntity player, ItemStack stack) {
        var inventory = player.getInventory();
        inventory.addPickBlock(stack.copy());
        client.interactionManager.clickCreativeStack(player.getMainHandStack(), 36 + inventory.selectedSlot);
    }
}
