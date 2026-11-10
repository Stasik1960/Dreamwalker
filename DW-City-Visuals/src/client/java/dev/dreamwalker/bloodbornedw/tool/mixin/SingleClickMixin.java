package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress held-button repetition without discarding presses queued by the key binding. */
@Mixin(MinecraftClient.class)
abstract class SingleClickMixin {
    @Shadow public ClientPlayerEntity player;

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void dw$noHeldBreaking(boolean breaking, CallbackInfo ci) {
        if (player != null && DebugToolModes.isModelTool(player.getMainHandStack())) ci.cancel();
    }

    @WrapOperation(method = "handleInputEvents", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/MinecraftClient;doItemUse()V"))
    private void dw$skipHeldUse(MinecraftClient client, Operation<Void> original) {
        if (player != null) {
            var stack = player.getMainHandStack();
            if (DebugToolModes.isModelTool(stack) || stack.isOf(Items.COMPASS)) return;
        }
        original.call(client);
    }
}
