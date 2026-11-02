package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient",remap=false)
abstract class FacadeStaticSourceMixin {
    @Redirect(method="renderSource",at=@At(value="INVOKE",target="Lnet/minecraft/class_1092;getModel(Lnet/minecraft/class_2960;)Lnet/minecraft/class_1087;",ordinal=0),remap=false)
    private static BakedModel dw$facades(BakedModelManager manager, Identifier id,
            BlockModelRenderer renderer, BlockRenderView world, BlockPos root, MatrixStack matrices,
            VertexConsumer vertices, int overlay, @Coerce Object block, BlockState state,
            @Coerce Object shift, double mountY, List<?> parts, @Coerce Object pose,
            @Coerce Object owner, boolean wholeSourceOptics) {
        return CityFacadesClient.staticModel(manager.getModel(id),root,state,world);
    }
}

