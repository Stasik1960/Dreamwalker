package dev.dreamwalker.bloodbornedw.visual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Resolve editor appearance from the immutable world snapshot used for this block render. */
@Mixin(BlockRenderManager.class)
abstract class FacadePositionAwareRenderMixin {
    @ModifyExpressionValue(
        method = "renderBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;getModel(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/model/BakedModel;")
    )
    private BakedModel dw$editorAppearance(BakedModel model, BlockState state, BlockPos pos,
                                           BlockRenderView world, MatrixStack matrices,
                                           VertexConsumer vertexConsumer, boolean cull, Random random) {
        return CityFacadesClient.terrainModel(model, pos, state, world);
    }
}
