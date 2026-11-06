package dev.dreamwalker.serverqa;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class CityFollowupQa implements ModInitializer {
    private static final BlockPos SHIFT_ROOT = new BlockPos(144, 222, -938);
    private static final UUID SHIFT_OWNER = UUID.fromString("a49d55c8-471d-4f22-9467-62c7630015bf");
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
            if (Boolean.getBoolean("dw.cityfollowup.doors")) {
                DoorOverlapQa.verify(world, player);
                Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), "PASS: ordinary door is blocked; no-collide door opens and preserves the obstruction");
                server.stop(false);
                return;
            }
            if (Boolean.getBoolean("dw.cityfollowup.reload")) {
                world.getChunk(SHIFT_ROOT);
                world.getChunk(SHIFT_ROOT.west());
                checkShift(world, -0.5);
                require(CompositeRuntime.contributions(world, SHIFT_ROOT.west()).stream()
                        .anyMatch(part -> SHIFT_OWNER.equals(part.owner().instanceId())), "Cross-chunk contribution after reload");
                Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), "PASS: half-block shift survives server restart");
                server.stop(false);
                return;
            }
            player.setYaw(180);
            BlockPos from = SHIFT_ROOT;
            BlockPos west = from.west();
            world.getChunk(from);
            world.getChunk(west);
            require(world.getBlockState(from).isAir() && world.getBlockState(west).isAir(), "Fixture must start in air");
            var brick = Registries.BLOCK.get(new Identifier("bloodborne_dw", "source_stone_bricks"))
                    .getDefaultState().with(CompositeRootBlock.ROTATION, 2);
            require(CompositeRuntime.place(world, from, brick, SHIFT_OWNER, player).outcome() == TransactionCore.Outcome.COMMITTED,
                    "Fixture placement");
            var stack = new ItemStack(BbToolService.ITEM);
            stack.getOrCreateNbt().putInt("BBMode", BbToolItem.Mode.UP.ordinal());
            player.equipStack(EquipmentSlot.MAINHAND, stack);
            BbToolItem.cycle(player);
            require(BbToolItem.mode(stack) == BbToolItem.Mode.DOWN && BbHorizontalMoves.mode(stack) == 0,
                    "Up must cycle to down");
            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == BbHorizontalMoves.LEFT, "Down must cycle to left");
            var target = ToolTarget.block(world, from, player);
            require(target != null && SHIFT_OWNER.equals(target.uuid()), "Fixture ownership");
            world.setBlockState(west, Blocks.STONE.getDefaultState());
            BbToolService.apply(player, target);
            checkShift(world, -0.5);
            require(world.getBlockState(west).isOf(Blocks.STONE), "Intersected block is preserved");
            require(CompositeRuntime.contributions(world, west).stream().anyMatch(part -> SHIFT_OWNER.equals(part.owner().instanceId())
                    && !part.shape().collision().isEmpty() && !part.shape().selection().isEmpty()),
                    "Shifted collision and selection cross the chunk boundary");

            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == BbHorizontalMoves.RIGHT, "Left must cycle to right");
            BbToolService.apply(player, target);
            checkShift(world, 0);
            require(world.getBlockState(west).isOf(Blocks.STONE) && CompositeRuntime.contributions(world, west).stream()
                    .noneMatch(part -> SHIFT_OWNER.equals(part.owner().instanceId())), "Right shift cleans previous contribution");
            BbToolItem.cycle(player);
            require(BbHorizontalMoves.mode(stack) == 0 && BbToolItem.mode(stack) == BbToolItem.Mode.SPECIAL,
                    "Right must resume the original mode cycle at Special");
            stack.getOrCreateNbt().putInt("BBMode", BbToolItem.Mode.ROTATE.ordinal());
            stack.getOrCreateNbt().putInt("BBHorizontalMode", 1);
            require(BbHorizontalMoves.mode(stack) == 0, "Legacy whole-block setting is ignored");
            BbToolItem.cycle(player);
            require(BbToolItem.mode(stack) == BbToolItem.Mode.SPECIAL_NPC, "Rotate resumes its standard mode");
            stack.getOrCreateNbt().putInt("BBMode", BbToolItem.Mode.DOWN.ordinal());
            BbToolItem.cycle(player);
            BbToolService.apply(player, target);
            checkShift(world, -0.5);
            BlockPos window = new BlockPos(208, 72, -940);
            for (int x = -16; x <= 16; x += 16) {
                for (int z = -16; z <= 16; z += 16) world.getChunk(window.add(x, 0, z));
            }
            require(StaticArchitectureCatalog.contains(world.getBlockState(window)), "Window fixture");
            var guestCell = window.west();
            var guests = CompositeRuntime.contributions(world, guestCell).stream().map(part -> part.owner()).toList();
            require(!guests.isEmpty(), "Fixture must contain independent guests");
            player.refreshPositionAndAngles(208.469, 72.036, -936.369, 180, 19.1f);
            var scope = NativePlayerBreak.begin(world, player, guestCell);
            boolean removed = false;
            try {
                removed = Boolean.TRUE.equals(NativePlayerBreak.remove(world, guestCell));
                if (!removed) {
                    var refusal = StaticArchitectureRuntime.breakAt(world, guestCell, player, false);
                    throw new AssertionError("Screenshot window rejected: " + refusal);
                }
            } finally {
                NativePlayerBreak.end(scope, removed);
            }
            require(removed && !StaticArchitectureCatalog.contains(world.getBlockState(window)), "Screenshot window Creative break");
            require(CompositeRuntime.contributions(world, guestCell).isEmpty(),
                    "Creative cleanup must remove the guests of the clicked shared carrier");
            result = "PASS: vertical/horizontal mode order, half-block X/Z with rotation, root UUID, occupied cell, cross-chunk collision and selection, Creative shared-cell break";
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

    private static void checkShift(ServerWorld world, double x) {
        var state = world.getBlockState(SHIFT_ROOT);
        require(state.getBlock() instanceof CompositeRootBlock && state.get(CompositeRootBlock.ROTATION) == 2,
                "Root block and its rotation are retained");
        var root = (CompositeBlockEntity) world.getBlockEntity(SHIFT_ROOT);
        require(root != null && root.resident() != null && SHIFT_OWNER.equals(root.resident().instanceId()), "Root UUID is retained");
        var shift = CompositeSourceShift.world(state, root.payload());
        require(Math.abs(shift.x() - x) < 1e-8 && Math.abs(shift.z()) < 1e-8 && shift.y() == 0,
                "Expected world-space horizontal offset " + x + ", found " + shift);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
