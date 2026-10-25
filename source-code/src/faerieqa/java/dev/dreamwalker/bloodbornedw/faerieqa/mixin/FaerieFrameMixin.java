package dev.dreamwalker.bloodbornedw.faerieqa.mixin;
import dev.dreamwalker.bloodbornedw.faerieqa.FaerieQaClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class FaerieFrameMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void dw$sampleFrame(boolean tick,CallbackInfo ci){FaerieQaClient.frameStart();}
}
