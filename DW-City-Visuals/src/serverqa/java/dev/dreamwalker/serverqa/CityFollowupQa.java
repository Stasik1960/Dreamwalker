package dev.dreamwalker.serverqa;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRuntime;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.tool.BbHorizontalMoves;
import dev.dreamwalker.bloodbornedw.tool.BbToolItem;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class CityFollowupQa implements ModInitializer {
    private boolean pending;

    @Override public void onInitialize() {
        if (System.getProperty("dw.cityfollowup.qa") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pending = true);
        ServerTickEvents.END_SERVER_TICK.register(this::verify);
    }

    private void verify(MinecraftServer server) {
        if (!pending) return;
        pending = false;
        String result;
        try {
            var world = server.getOverworld();
            var player = FakePlayer.get(world, new GameProfile(UUID.randomUUID(), "DreamWalkerQA"));
            player.changeGameMode(GameMode.CREATIVE);
            require(player.isCreative() && player.getAbilities().allowModifyWorld, "Creative game mode fixture");
            player.setYaw(180);
            BlockPos from = new BlockPos(140, 222, -938);
            BlockPos west = from.west();
            world.getChunk(from);
            world.getChunk(west);
            require(world.getBlockState(from).isAir() && world.getBlockState(west).isAir(), "Fixture must start in air");
            var grass = Registries.BLOCK.get(new Identifier("bloodborne_dw", "source_addon_grass_0")).getDefaultState();
            UUID owner = UUID.randomUUID();
            require(CompositeRuntime.place(world, from, grass, owner, player).outcome() == TransactionCore.Outcome.COMMITTED,
                    "Fixture placement");
            var stack = new ItemStack(BbToolService.ITEM);
            stack.getOrCreateNbt().putInt("BBMode", BbToolItem.Mode.ROTATE.ordinal());
            player.equipStack(EquipmentSlot.MAINHAND, stack);
            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == BbHorizontalMoves.LEFT, "Rotate must cycle to left");
            var target = ToolTarget.block(world, from, player);
            require(target != null && owner.equals(target.uuid()), "Fixture ownership");
            BbToolService.apply(player, target);
            require(world.getBlockState(from).isAir() && owner.equals(ToolTarget.block(world, west, player).uuid()), "Left shift");

            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == BbHorizontalMoves.RIGHT, "Left must cycle to right");
            world.setBlockState(from, Blocks.STONE.getDefaultState());
            target = ToolTarget.block(world, west, player);
            BbToolService.apply(player, target);
            require(world.getBlockState(from).isOf(Blocks.STONE) && owner.equals(ToolTarget.block(world, west, player).uuid()),
                    "Occupied root must leave both cells intact");
            world.setBlockState(from, Blocks.AIR.getDefaultState());
            BbToolService.apply(player, target);
            require(world.getBlockState(west).isAir() && owner.equals(ToolTarget.block(world, from, player).uuid()), "Right shift");
            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == 0 && BbToolItem.mode(stack) == BbToolItem.Mode.SPECIAL_NPC,
                    "Right must resume the original mode cycle at Special NPC");
            BlockPos window = new BlockPos(208, 72, -940);
            for (int x = -16; x <= 16; x += 16) {
                for (int z = -16; z <= 16; z += 16) world.getChunk(window.add(x, 0, z));
            }
            require(StaticArchitectureCatalog.contains(world.getBlockState(window)), "Window fixture");
            player.refreshPositionAndAngles(208.469, 72.036, -936.369, 180, 19.1f);
            var scope = NativePlayerBreak.begin(world, player, window);
            boolean removed = false;
            try {
                removed = Boolean.TRUE.equals(NativePlayerBreak.remove(world, window));
                if (!removed) {
                    var refusal = StaticArchitectureRuntime.breakAt(world, window, player, false);
                    throw new AssertionError("Screenshot window rejected: " + refusal);
                }
            } finally {
                NativePlayerBreak.end(scope, removed);
            }
            require(removed && !StaticArchitectureCatalog.contains(world.getBlockState(window)), "Screenshot window Creative break");
            result = "PASS: left/right item modes, UUID, occupied destination, ordinary cycle and Creative window break";
        } catch (Throwable failure) {
            result = "FAIL: " + failure;
            failure.printStackTrace();
        }
        try {
            Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), result);
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            server.stop(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
