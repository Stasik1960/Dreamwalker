package dev.yharnamdice.client.mixin;

import dev.yharnamdice.client.TableScreen;
import dev.yharnamdice.TableSeat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class SeatedHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void yharnamDice$hideCrosshair(DrawContext context, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof TableScreen) ci.cancel();
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void yharnamDice$hideDismountNudge(Text message, boolean tinted, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getVehicle() instanceof TableSeat
                && message.getContent() instanceof TranslatableTextContent text
                && text.getKey().equals("mount.onboard")) ci.cancel();
    }
}
