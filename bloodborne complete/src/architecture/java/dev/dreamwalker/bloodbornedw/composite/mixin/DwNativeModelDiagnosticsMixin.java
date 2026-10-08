package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual vanilla block-model render entry; alternative renderer bypasses are explicitly unmeasured. */
@Mixin(BlockModelRenderer.class)
public abstract class DwNativeModelDiagnosticsMixin {
    @Inject(method="render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",at=@At("HEAD"))
    private void dw$actualModel(BlockRenderView view,BakedModel model,BlockState state,BlockPos pos,MatrixStack matrices,VertexConsumer vertices,boolean cull,Random random,long seed,int overlay,CallbackInfo callback){
        if(!DwClientDiagnostics.enabled()||!Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw"))return;
        var client=MinecraftClient.getInstance();String item=client.player==null?null:Registries.ITEM.getId(client.player.getMainHandStack().getItem()).toString();
        DwClientDiagnostics.visualModel(item,state,null,pos,BlockModels.getModelId(state),model,"native-block-model-render-entry");
    }
}
