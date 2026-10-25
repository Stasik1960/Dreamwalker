package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.world.WorldSlice",remap=false)
public abstract class NativeVisualSodiumSliceMixin implements NativeVisualView {
    @Unique private NativeVisualSodiumSnapshot dw$visuals=NativeVisualSodiumSnapshot.EMPTY;
    @Inject(method="copyData",at=@At("RETURN"),remap=false)
    private void dw$copy(@Coerce Object context,CallbackInfo ci){dw$visuals=((NativeVisualSodiumSnapshot.Context)context).nativeVisualSnapshot();}
    @Inject(method="reset",at=@At("RETURN"),remap=false)
    private void dw$release(CallbackInfo ci){dw$visuals=NativeVisualSodiumSnapshot.EMPTY;}
    @Override public int nativeVisualIndex(BlockPos pos){return dw$visuals.nativeVisualIndex(pos);}
}
