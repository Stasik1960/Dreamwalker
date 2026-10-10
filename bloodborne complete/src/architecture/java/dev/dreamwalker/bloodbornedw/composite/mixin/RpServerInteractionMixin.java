package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectSelection;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Validate ordinary RP packets on the world thread, not against their large visual AABB. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class RpServerInteractionMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method="onPlayerInteractEntity",at=@At(value="INVOKE",
        target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
        shift=At.Shift.AFTER),cancellable=true)
    private void dreamwalker$physicalTarget(PlayerInteractEntityC2SPacket packet,CallbackInfo ci) {
        var entity=packet.getEntity(player.getServerWorld());
        if(!(entity instanceof RpObjectEntity))return;
        var hit=RpObjectSelection.playerTarget(player,RpObjectSelection.entityReach(player));
        if(hit==null||hit.getEntity()!=entity||!player.getAbilities().allowModifyWorld
            ||!player.getWorld().canPlayerModifyAt(player,net.minecraft.util.math.BlockPos.ofFloored(hit.getPos())))ci.cancel();
    }
}
