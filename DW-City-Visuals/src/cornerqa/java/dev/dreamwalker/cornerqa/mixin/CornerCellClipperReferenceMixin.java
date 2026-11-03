package dev.dreamwalker.cornerqa.mixin;

import dev.dreamwalker.bloodbornedw.composite.CornerQuadSplitter;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import dev.dreamwalker.bloodbornedw.visual.CornerLightingQa;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Reference stage keeps CornerLight1 and disables only the new cell clipper. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CornerQuadCellClipper",remap=false)
public abstract class CornerCellClipperReferenceMixin {
    @Inject(method="clip",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dw$reference(BakedQuad quad,StaticLightingViewAccess view,int mask,float[] brightness,int[] light,CallbackInfoReturnable<List<CornerQuadSplitter.Piece>> ci){
        if(!CornerLightingQa.corrected() && (Object)view instanceof dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRenderView)ci.setReturnValue(null);
    }
}
