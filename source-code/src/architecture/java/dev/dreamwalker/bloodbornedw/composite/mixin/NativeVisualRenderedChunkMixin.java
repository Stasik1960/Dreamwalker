package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.client.render.chunk.RenderedChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RenderedChunk.class)
public abstract class NativeVisualRenderedChunkMixin implements NativeVisualView {
    @Unique private NativeVisualData dw$visuals=NativeVisualData.EMPTY;
    @Inject(method="<init>",at=@At("RETURN"))
    private void dw$snapshotVisuals(WorldChunk chunk,CallbackInfo ci){dw$visuals=NativeVisuals.data(chunk);}
    @Override public int nativeVisualIndex(BlockPos pos){return dw$visuals.at(pos);}
}
