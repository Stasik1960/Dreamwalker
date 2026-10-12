package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** New conversion worlds publish ownership exactly when vanilla publishes the
 * watched chunk. Legacy worlds return immediately and keep their old packets. */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ShardedLedgerTrackingMixin {
    @Shadow @Final private ServerWorld world;
    @Inject(method="sendChunkDataPackets",at=@At("TAIL"))
    private void bloodborne_dw$watchedOwners(ServerPlayerEntity player,MutableObject<?> packets,WorldChunk chunk,CallbackInfo ci){
        CompositeNetworking.watchedChunk(player,world,chunk.getPos());
    }
}
