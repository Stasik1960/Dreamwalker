package dev.dreamwalker.perfqa.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.dreamwalker.perfqa.PerformanceQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same native presentation boundary is used for every compared mod. */
@Mixin(value=RenderSystem.class, remap=false)
public abstract class FrameClockMixin {
    @Inject(method="flipFrame", at=@At("HEAD"), remap=false)
    private static void dw$presentation(long window, CallbackInfo callback) {
        PerformanceQa.presentation();
    }
}
