package dev.dreamwalker.flightqa.mixin;

import dev.dreamwalker.flightqa.FlightQa;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A correction during travel invalidates the route even if the next tick restores it. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PlayerCorrectionMixin {
    @Inject(method="onPlayerPositionLook", at=@At("HEAD"))
    private void dw$correction(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        FlightQa.correction();
    }
}
