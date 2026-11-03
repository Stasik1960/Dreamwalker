package dev.dreamwalker.bloodbornedw.composite.overlaymixin;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRenderView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=StaticArchitectureRenderView.class,remap=false)
public interface StaticLightingViewAccess {
 @Accessor("delegate") BlockRenderView dw$delegate();
 @Accessor("root") BlockPos dw$root();
 @Accessor("transform") Matrix4f dw$transform();
 @Accessor("normals") Matrix3f dw$normals();
}
