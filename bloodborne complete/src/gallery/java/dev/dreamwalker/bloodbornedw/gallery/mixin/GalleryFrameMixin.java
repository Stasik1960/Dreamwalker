package dev.dreamwalker.bloodbornedw.gallery.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dreamwalker.bloodbornedw.gallery.GalleryClientCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Existing diagnostics clock, active only for an explicitly requested camera window. */
@Mixin(value=RenderSystem.class,remap=false)
public abstract class GalleryFrameMixin {
    @Inject(method="flipFrame",at=@At("HEAD"),remap=false)
    private static void capture(long window,CallbackInfo callback){GalleryClientCapture.presentation();}
}
