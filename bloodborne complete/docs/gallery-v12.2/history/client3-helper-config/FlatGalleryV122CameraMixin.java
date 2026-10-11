package dev.dreamwalker.bloodbornedw.gallery;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Minecraft 1.20.1 intermediary selector; opt-in QA class, no production mixins. */
@Mixin(targets="net.minecraft.class_757",remap=false)
public abstract class FlatGalleryV122CameraMixin {
    @Inject(method="method_3188",at=@At("HEAD"),remap=false,require=1)
    private void dwGalleryHoldPose(CallbackInfo ci){
        FlatGalleryV122ClientCapture.holdPlannedPose(MinecraftClient.getInstance());
    }
}
