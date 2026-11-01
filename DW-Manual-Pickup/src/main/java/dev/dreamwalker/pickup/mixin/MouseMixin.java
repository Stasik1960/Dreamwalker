package dev.dreamwalker.pickup.mixin;

import dev.dreamwalker.pickup.client.PickupInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void dwManualPickup$onButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        PickupInput.onMouse(window, button, action);
    }
}
