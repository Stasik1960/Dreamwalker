package dev.dreamwalker.renderdiag.mixin;
import dev.dreamwalker.renderdiag.RenderDiag;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="dev.dreamwalker.bloodbornedw.editor.EditorData",remap=false)
abstract class EditorDataMixin {
    @Inject(method="style",at=@At("HEAD"),remap=false) private static void style(BlockRenderView view,BlockPos pos,CallbackInfoReturnable<Integer> ci) { if(RenderDiag.ENABLED) (view instanceof ClientWorld?RenderDiag.styleWorld:RenderDiag.styleOther).increment(); }
}
