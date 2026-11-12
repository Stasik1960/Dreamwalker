package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("RETURN"))
    private void nightShift$keepRiderOnBoard(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && player.getVehicle() instanceof SkateboardEntity) {
            // The skate client reads the key binding directly; vanilla Shift must not dismount here.
            sneaking = false;
        }
    }
}
