package com.coraxberg.dwmagicconnect;

import com.coraxberg.dwmagicconnect.command.MagicConnectCommands;
import com.coraxberg.dwmagicconnect.config.DwMagicConnectConfig;
import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.coraxberg.dwmagicconnect.network.DwMagicConnectNetworking;
import com.coraxberg.dwmagicconnect.radio.MagicRelayService;
import com.coraxberg.rpchat.api.RpChatEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DwMagicConnectMod implements ModInitializer {
    public static final String MOD_ID = "dw_magic_connect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DwMagicConnectConfig.register();
        MagicConnectCommands.register();
        DwMagicConnectNetworking.registerServerReceivers();
        registerRadioInteraction();

        RpChatEvents.LOCAL_IC_MESSAGE.register(event -> MagicRelayService.relay(event));
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    private static void registerRadioInteraction() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (player.isSpectator() || !MagicConnectData.isRadio(stack)) {
                return TypedActionResult.pass(stack);
            }

            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                DwMagicConnectNetworking.openScreen(serverPlayer, hand, stack);
            }

            return TypedActionResult.success(stack, world.isClient);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (player.isSpectator() || !MagicConnectData.isRadio(stack)) {
                return ActionResult.PASS;
            }

            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                DwMagicConnectNetworking.openScreen(serverPlayer, hand, stack);
            }

            return world.isClient ? ActionResult.SUCCESS : ActionResult.CONSUME;
        });

        // Entity interaction has its own vanilla/Fabric path and does not reliably fall
        // through to UseItemCallback. Consume it as well so a radio opens consistently
        // when the crosshair happens to be over an entity.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (player.isSpectator() || !MagicConnectData.isRadio(stack)) {
                return ActionResult.PASS;
            }

            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                DwMagicConnectNetworking.openScreen(serverPlayer, hand, stack);
            }

            return world.isClient ? ActionResult.SUCCESS : ActionResult.CONSUME;
        });
    }
}
