package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CompositeLifecycleDispatch;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh",remap=false)
abstract class CompositeLifecycleMeshMixin {
    @Inject(method="reload",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dw$reload(CallbackInfo ci) { if(CompositeLifecycleDispatch.deferReload())ci.cancel(); }
    // The existing ClientWorld hook lacks a world argument; the world-aware hook
    // invokes this method only inside its dispatch scope.
    @Inject(method="chunkColorsChanged",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dw$colors(ChunkPos pos,CallbackInfo ci) { if(!CompositeLifecycleDispatch.dispatchedColors())ci.cancel(); }
}
