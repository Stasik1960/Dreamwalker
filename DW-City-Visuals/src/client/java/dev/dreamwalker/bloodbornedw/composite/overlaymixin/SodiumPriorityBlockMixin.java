package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.TerrainPriorityScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",remap=false)
public abstract class SodiumPriorityBlockMixin {
 @WrapMethod(method="renderModel",remap=false)
 private void dw$priority(@Coerce Object context,@Coerce Object buffers,Operation<Void> original) {
  float previous=TerrainPriorityScope.begin(context);
  try { original.call(context,buffers); } finally { TerrainPriorityScope.end(previous); }
 }
}