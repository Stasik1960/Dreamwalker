package dev.dreamwalker.bloodbornedw.galleryqa.capture.mixin;
import dev.dreamwalker.bloodbornedw.galleryqa.GalleryBenchmarkV127;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Explicit QA camera only. The delivered world requires no mixin/add-on. */
@Mixin(targets="net.minecraft.class_757",remap=false)
public final class GalleryCameraLock {
    @Inject(method="method_3188",at=@At("HEAD"),remap=false,require=1)
    private void gallery127$lock(CallbackInfo info){
        GalleryBenchmarkV127.lockCamera(MinecraftClient.getInstance());
    }
}
