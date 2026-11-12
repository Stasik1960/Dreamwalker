package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class SelectionAxeClientMixin {
    @Shadow public ClientPlayerEntity player;
    @Shadow public ClientPlayerInteractionManager interactionManager;
    @Shadow public HitResult crosshairTarget;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$selectWithoutSwing(CallbackInfoReturnable<Boolean> callback) {
        if (!SelectionAxe.heldBy(player)) return;
        if (interactionManager != null && crosshairTarget instanceof BlockHitResult block
                && block.getType() == HitResult.Type.BLOCK) {
            interactionManager.attackBlock(block.getBlockPos(), block.getSide());
        }
        callback.setReturnValue(false);
    }
}
