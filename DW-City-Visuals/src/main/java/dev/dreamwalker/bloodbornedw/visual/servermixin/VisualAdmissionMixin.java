package dev.dreamwalker.bloodbornedw.visual.servermixin;

import dev.dreamwalker.bloodbornedw.visual.CityVisualMode;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.MinecraftServer;

@Mixin(PlayerManager.class)
public abstract class VisualAdmissionMixin {
    @Shadow public abstract MinecraftServer getServer();
    @Inject(method = "onPlayerConnect", at = @At("HEAD"), cancellable = true)
    private void dw$checkVisualProof(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        if (!CityVisualMode.admit(getServer(), connection)) ci.cancel();
    }
}
