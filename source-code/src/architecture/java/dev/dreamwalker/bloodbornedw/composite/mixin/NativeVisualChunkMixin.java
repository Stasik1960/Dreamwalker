package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.*;
@Mixin(Chunk.class)
public abstract class NativeVisualChunkMixin implements NativeVisualChunk,dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.Holder {
    @Unique private volatile NativeVisualData dw$visuals=NativeVisualData.EMPTY;
    @Override public NativeVisualData nativeVisualData(){return dw$visuals;}
    @Override public void nativeVisualData(NativeVisualData data){dw$visuals=java.util.Objects.requireNonNull(data);}
    @Unique private volatile dev.dreamwalker.bloodbornedw.architecture.FauxWaterData dw$fauxWater=dev.dreamwalker.bloodbornedw.architecture.FauxWaterData.EMPTY;
    @Override public dev.dreamwalker.bloodbornedw.architecture.FauxWaterData fauxWaterData(){return dw$fauxWater;}
    @Override public void fauxWaterData(dev.dreamwalker.bloodbornedw.architecture.FauxWaterData value){dw$fauxWater=java.util.Objects.requireNonNull(value);}
}
