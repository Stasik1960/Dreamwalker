package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import java.util.function.Consumer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Publish one complete chunk packet, including its BE attachment and NBT.
 * The finally block also balances a corrupt packet's exceptional exit. */
@Mixin(ClientChunkManager.class)
public abstract class CompositeChunkPublicationMixin {
    @Redirect(method="loadChunkFromPacket",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;loadFromPacket(Lnet/minecraft/network/PacketByteBuf;Lnet/minecraft/nbt/NbtCompound;Ljava/util/function/Consumer;)V"),require=2)
    private void dreamwalker$publishChunk(WorldChunk chunk,PacketByteBuf buffer,NbtCompound heightmaps,Consumer<ChunkData.BlockEntityVisitor> blockEntities){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());
        try(var decode=CompositeData.openDecodeScope()){chunk.loadFromPacket(buffer,heightmaps,blockEntities);NativeVisuals.data(chunk,NativeVisualData.read(heightmaps.getCompound(NativeVisualData.KEY),chunk));dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data(chunk,dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.read(heightmaps.getCompound(dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.KEY),chunk));}
        finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
}
