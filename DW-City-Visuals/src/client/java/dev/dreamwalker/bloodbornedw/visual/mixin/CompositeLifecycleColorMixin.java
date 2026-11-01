package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CompositeLifecycleDispatch;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
abstract class CompositeLifecycleColorMixin {
    // A queued main-thread rebuild must observe the already-cleared biome cache.
    @Inject(method="resetChunkColor",at=@At("RETURN"))
    private void dw$colors(ChunkPos pos,CallbackInfo ci) { CompositeLifecycleDispatch.colors((ClientWorld)(Object)this,pos); }
}
