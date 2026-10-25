package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualView;
import net.minecraft.client.render.chunk.*;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
@Mixin(ChunkRendererRegion.class)
public abstract class NativeVisualRenderRegionMixin implements NativeVisualView {
    @Shadow @Final private int chunkXOffset;
    @Shadow @Final private int chunkZOffset;
    @Shadow @Final protected RenderedChunk[][] chunks;
    @Override public int nativeVisualIndex(BlockPos pos){int x=(pos.getX()>>4)-chunkXOffset,z=(pos.getZ()>>4)-chunkZOffset;return x<0||z<0||x>=chunks.length||z>=chunks[x].length?-1:((NativeVisualView)(Object)chunks[x][z]).nativeVisualIndex(pos);}
}
