package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext",remap=false)
abstract class FacadeLegacyIndigoMixin {
    @ModifyVariable(method="tessellateBlock",at=@At("HEAD"),argsOnly=true,remap=false)
    private BakedModel dw$facades(BakedModel model,BlockState state,BlockPos pos,BakedModel original,MatrixStack matrices) {
        return CityFacadesClient.legacyTerrainModel(model,pos,state);
    }
}
