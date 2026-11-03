package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The prepared-mesh snapshot view also tracks every sampled world section. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$PartView",remap=false)
public interface CompositeLightingViewAccess extends StaticLightingViewAccess {
 @Override @Accessor("delegate") BlockRenderView dw$delegate();
 @Override @Accessor("root") BlockPos dw$root();
 @Override @Accessor("transform") Matrix4f dw$transform();
 @Override @Accessor("normals") Matrix3f dw$normals();
 @Invoker("dependency") void dw$dependency(BlockPos worldPos);
}
