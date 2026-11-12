package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class SelectionAxeProgressMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow public abstract void cancelBlockBreaking();
    @Shadow private void syncSelectedSlot() { throw new AssertionError(); }

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void dreamwalker$syncAxeBeforeSelection(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> callback) {
        // A hotbar key and a click can arrive in the same client tick.
        if (SelectionAxe.heldBy(client.player)) syncSelectedSlot();
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$stopMiningWithSelectionAxe(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> callback) {
        if (!SelectionAxe.heldBy(client.player)) return;
        cancelBlockBreaking();
        callback.setReturnValue(false);
    }
}
