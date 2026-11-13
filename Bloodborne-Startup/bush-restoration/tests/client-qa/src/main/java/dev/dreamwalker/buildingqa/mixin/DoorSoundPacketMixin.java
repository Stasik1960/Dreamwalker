package dev.dreamwalker.buildingqa.mixin;

import dev.dreamwalker.buildingqa.DoorSoundChecks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class DoorSoundPacketMixin {
    @Inject(method = "onPlaySound", at = @At("RETURN"))
    private void dw$heardDoor(PlaySoundS2CPacket packet, CallbackInfo ci) {
        DoorSoundChecks.record(packet);
    }
}
