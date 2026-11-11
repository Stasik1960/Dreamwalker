package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.TerrainPriorityScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="link.infra.indium.renderer.render.TerrainRenderContext",remap=false)
public abstract class IndiumPriorityBlockMixin {
 @WrapMethod(method="tessellateBlock",remap=false)
 private void dw$priority(@Coerce Object context,Operation<Void> original) {
  int previous=TerrainPriorityScope.begin(context);
  try { original.call(context); } finally { TerrainPriorityScope.end(previous); }
 }
}