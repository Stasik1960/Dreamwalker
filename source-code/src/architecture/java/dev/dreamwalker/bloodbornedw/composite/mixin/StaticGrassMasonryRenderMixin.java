package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticGrassMasonryBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticGrassMasonryRenderView;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This one chunk-meshed family retains its former source-carrier lighting.
 * The recursive native-grass call no longer matches this narrowly typed hook. */
@Mixin(value=BlockModelRenderer.class,priority=1100)
public abstract class StaticGrassMasonryRenderMixin {
    @Inject(method="render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",at=@At("HEAD"),cancellable=true)
    private void dw$staticGrassSourceLight(BlockRenderView world,BakedModel model,BlockState state,BlockPos pos,MatrixStack matrices,VertexConsumer vertices,boolean cull,Random random,long seed,int overlay,CallbackInfo callback){
        if(!(state.getBlock() instanceof StaticGrassMasonryBlock))return;
        ((BlockModelRenderer)(Object)this).render(new StaticGrassMasonryRenderView(world,pos),model,Blocks.GRASS_BLOCK.getDefaultState(),pos,matrices,vertices,false,random,seed,overlay);
        callback.cancel();
    }
}
