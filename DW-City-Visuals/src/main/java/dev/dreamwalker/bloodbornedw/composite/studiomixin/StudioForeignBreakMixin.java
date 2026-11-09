package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak;
import dev.dreamwalker.bloodbornedw.composite.StudioOverhangs;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NativePlayerBreak.class, remap = false)
public abstract class StudioForeignBreakMixin {
    @Shadow @Final private static ThreadLocal<NativePlayerBreak.Scope> CURRENT;
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private static void dw$breakSelectedOverhang(ServerWorld world, BlockPos cell, CallbackInfoReturnable<Boolean> result) {
        Boolean removed = StudioOverhangs.remove(CURRENT.get(), world, cell);
        if (removed != null) result.setReturnValue(removed);
    }
}
