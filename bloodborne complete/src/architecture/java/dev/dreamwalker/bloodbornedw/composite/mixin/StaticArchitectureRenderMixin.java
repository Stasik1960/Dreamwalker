package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BlockModelRenderer.class,priority=1100)
public abstract class StaticArchitectureRenderMixin {
    @Inject(method="render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",at=@At("HEAD"),cancellable=true)
    private void dw$staticArchitecture(BlockRenderView world,BakedModel model,BlockState state,BlockPos pos,MatrixStack matrices,VertexConsumer vertices,boolean cull,Random random,long seed,int overlay,CallbackInfo callback){
        if(!(state.getBlock() instanceof StaticArchitectureBlock))return;
        StaticArchitectureClient.render((BlockModelRenderer)(Object)this,world,state,pos,matrices,vertices,overlay);callback.cancel();
    }
}
