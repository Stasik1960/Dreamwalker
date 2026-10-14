package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Geometry, neighbour AO and biome changes must rebuild the cached owner mesh. */
@Mixin(ClientWorld.class)
public abstract class CompositeMeshWorldMixin {
    @Inject(method="updateListeners",at=@At("HEAD"))
    private void dreamwalker$block(BlockPos pos,BlockState oldState,BlockState newState,int flags,CallbackInfo ci){CompositeWorldMesh.sectionChanged(net.minecraft.util.math.ChunkSectionPos.from(pos).asLong());}
    @Inject(method="reloadColor",at=@At("HEAD"))
    private void dreamwalker$biome(CallbackInfo ci){CompositeWorldMesh.changed();}
    @Inject(method="resetChunkColor",at=@At("HEAD"))
    private void dreamwalker$chunkBiome(net.minecraft.util.math.ChunkPos pos,CallbackInfo ci){CompositeWorldMesh.chunkColorsChanged(pos);}
}
