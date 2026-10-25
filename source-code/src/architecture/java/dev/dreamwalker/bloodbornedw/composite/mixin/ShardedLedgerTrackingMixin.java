package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeNetworking;
import dev.dreamwalker.bloodbornedw.composite.CompositeInitialSync;
import dev.dreamwalker.bloodbornedw.composite.CompositeLedger;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.BitSet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
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
    @WrapMethod(method="sendChunkDataPackets")
    private void bloodborne_dw$watchedOwners(ServerPlayerEntity player,MutableObject<?> packets,WorldChunk chunk,Operation<Void> original){
        try(var proof=CompositeInitialSync.begin(world,chunk.getPos(),packets.getValue()==null&&CompositeLedger.get(world).sharded())){
            original.call(player,packets,chunk);proof.confirm(packets.getValue());
            CompositeNetworking.watchedChunk(player,world,chunk.getPos());
        }
    }
    @WrapOperation(method="sendChunkDataPackets",at=@At(value="NEW",target="(Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/light/LightingProvider;Ljava/util/BitSet;Ljava/util/BitSet;)Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;"))
    private ChunkDataS2CPacket bloodborne_dw$vanillaWitness(WorldChunk chunk,LightingProvider light,BitSet sky,BitSet block,Operation<ChunkDataS2CPacket> original){
        var scope=CompositeInitialSync.capturePacket(chunk);ChunkDataS2CPacket packet=null;
        try{return packet=original.call(chunk,light,sky,block);}finally{if(scope!=null)scope.endPacket(packet);}
    }
}
