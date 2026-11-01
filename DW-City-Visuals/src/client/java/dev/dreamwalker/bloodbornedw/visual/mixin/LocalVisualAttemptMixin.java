package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServerLoader.class)
public abstract class LocalVisualAttemptMixin {
    @Inject(method = "start(Lnet/minecraft/client/gui/screen/Screen;Ljava/lang/String;)V", at = @At("HEAD"))
    private void dw$attempt(Screen parent, String save, CallbackInfo ci) { CityVisualClient.connectionStarting(); }
}
