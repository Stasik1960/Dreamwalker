package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext",remap=false)
public abstract class NativeVisualSodiumContextMixin implements NativeVisualSodiumSnapshot.Context {
    @Unique private NativeVisualSodiumSnapshot dw$visuals=NativeVisualSodiumSnapshot.EMPTY;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void dw$snapshot(CallbackInfo ci){dw$visuals=NativeVisualSodiumSnapshot.captureContext(this);}
    @Override public NativeVisualSodiumSnapshot nativeVisualSnapshot(){return dw$visuals;}
}
