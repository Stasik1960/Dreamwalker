package dev.dreamwalker.pickup.mixin;

import dev.dreamwalker.pickup.client.PickupInput;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void dwManualPickup$onKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        PickupInput.onKeyboard(window, key, scanCode, action);
    }
}
