package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Additional typed NBT in the existing vanilla chunk packet. Initial visual
 * and physical data therefore arrive in the same packet and publication scope. */
@Mixin(ChunkData.class)
public abstract class NativeVisualChunkPacketMixin {
    @Shadow @Final private NbtCompound heightmap;
    @Inject(method="<init>(Lnet/minecraft/world/chunk/WorldChunk;)V",at=@At("RETURN"))
    private void dw$packetVisuals(WorldChunk chunk,CallbackInfo ci){var data=NativeVisuals.data(chunk);if(!data.empty())heightmap.put(NativeVisualData.KEY,data.nbt());var faux=dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data(chunk);if(!faux.empty())heightmap.put(dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.KEY,faux.nbt());}
}
