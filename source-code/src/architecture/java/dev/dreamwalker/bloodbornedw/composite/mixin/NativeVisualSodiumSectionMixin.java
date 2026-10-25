package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection",remap=false)
public abstract class NativeVisualSodiumSectionMixin implements NativeVisualSodiumSnapshot.Section {
    @Unique private NativeVisualData dw$visuals=NativeVisualData.EMPTY;
    @Unique private ChunkSectionPos dw$visualPos;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void dw$snapshot(World world,WorldChunk chunk,ChunkSection section,ChunkSectionPos pos,CallbackInfo ci){dw$visuals=NativeVisuals.data(chunk);dw$visualPos=pos;}
    @Override public NativeVisualData nativeVisualSectionData(){return dw$visuals;}
    @Override public ChunkSectionPos nativeVisualSectionPos(){return dw$visualPos;}
}
