package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateboardEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Shadow private int itemUseCooldown;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void nightShift$dismount(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null && client.player.getVehicle() instanceof SkateboardEntity
                && client.options.sneakKey.isPressed() && ClientPlayNetworking.canSend(NightShiftSkate.DISMOUNT)) {
            ClientPlayNetworking.send(NightShiftSkate.DISMOUNT, PacketByteBufs.empty());
            client.options.useKey.setPressed(false);
            itemUseCooldown = 4;
            ci.cancel();
        }
    }
}
