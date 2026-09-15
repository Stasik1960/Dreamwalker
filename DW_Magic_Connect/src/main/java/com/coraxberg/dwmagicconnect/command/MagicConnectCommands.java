package com.coraxberg.dwmagicconnect.command;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import static net.minecraft.server.command.CommandManager.literal;

public final class MagicConnectCommands {
    private MagicConnectCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(commandRoot("MConnect"));
            dispatcher.register(commandRoot("mconnect"));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> commandRoot(String name) {
        return literal(name)
                .requires(source -> source.hasPermissionLevel(2)
                        && source.getEntity() instanceof ServerPlayerEntity)
                .executes(context -> createRadio(context.getSource()))
                .then(literal("remove")
                        .executes(context -> removeRadio(context.getSource())));
    }

    private static int createRadio(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        HeldItem held = selectedItem(player);
        if (held == null) {
            source.sendError(Text.translatable("command.dw_magic_connect.empty_hand"));
            return 0;
        }

        if (MagicConnectData.isRadio(held.stack())) {
            source.sendError(Text.translatable("command.dw_magic_connect.already_radio"));
            return 0;
        }

        MagicConnectData.createRadio(held.stack());
        syncInventory(player);
        source.sendFeedback(
                () -> Text.translatable("command.dw_magic_connect.created"),
                false
        );
        return 1;
    }

    private static int removeRadio(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        HeldItem held = selectedRadio(player);
        if (held == null) {
            if (selectedItem(player) == null) {
                source.sendError(Text.translatable("command.dw_magic_connect.empty_hand"));
            } else {
                source.sendError(Text.translatable("command.dw_magic_connect.not_radio"));
            }
            return 0;
        }

        MagicConnectData.removeRadio(held.stack());
        syncInventory(player);
        source.sendFeedback(
                () -> Text.translatable("command.dw_magic_connect.removed"),
                false
        );
        return 1;
    }

    private static HeldItem selectedItem(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isEmpty()) return new HeldItem(Hand.MAIN_HAND, mainHand);

        ItemStack offHand = player.getOffHandStack();
        if (!offHand.isEmpty()) return new HeldItem(Hand.OFF_HAND, offHand);
        return null;
    }

    private static HeldItem selectedRadio(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (MagicConnectData.isRadio(mainHand)) {
            return new HeldItem(Hand.MAIN_HAND, mainHand);
        }

        ItemStack offHand = player.getOffHandStack();
        if (MagicConnectData.isRadio(offHand)) {
            return new HeldItem(Hand.OFF_HAND, offHand);
        }
        return null;
    }

    private static void syncInventory(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    private record HeldItem(Hand hand, ItemStack stack) {
    }
}
