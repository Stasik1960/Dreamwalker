package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.*;
@Mixin(Chunk.class)
public abstract class NativeVisualChunkMixin implements NativeVisualChunk {
    @Unique private volatile NativeVisualData dw$visuals=NativeVisualData.EMPTY;
    @Override public NativeVisualData nativeVisualData(){return dw$visuals;}
    @Override public void nativeVisualData(NativeVisualData data){dw$visuals=java.util.Objects.requireNonNull(data);}
}
