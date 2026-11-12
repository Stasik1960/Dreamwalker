package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Shadow @Final private MinecraftClient client;

    @Redirect(method = "onEntityPassengersSet", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;setOverlayMessage(Lnet/minecraft/text/Text;Z)V"))
    private void nightShift$hideMountHint(InGameHud hud, Text message, boolean tinted) {
        if (client.player == null || !(client.player.getVehicle() instanceof SkateboardEntity)) {
            hud.setOverlayMessage(message, tinted);
        }
    }
}
