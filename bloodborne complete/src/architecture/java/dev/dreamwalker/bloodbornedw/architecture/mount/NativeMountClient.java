package dev.dreamwalker.bloodbornedw.architecture.mount;

import java.util.*;
import java.util.function.Supplier;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Existing immutable native model quads are reused; no height state product or rebake. */
public final class NativeMountClient {
    private NativeMountClient(){}
    public static void initialize(){
        ModelLoadingPlugin.register(plugin->{
            Map<BakedModel,BakedModel> wrappers=new IdentityHashMap<>();
            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
                if(model==null||!(context.id() instanceof ModelIdentifier id)||!id.getNamespace().equals("bloodborne_dw")||id.getVariant().equals("inventory"))return model;
                if(!(id.getPath().equals("prototype_ladder")||id.getPath().startsWith("prototype_ladder_art_")||id.getPath().equals("prototype_wall")||id.getPath().startsWith("prototype_wall_skin_")))return model;
                return wrappers.computeIfAbsent(model,MountedModel::new);
            });
        });
        BlockEntityRendererRegistry.register(SourceLadderRuntime.ENTITY,context->new net.minecraft.client.render.block.entity.BlockEntityRenderer<dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity>(){
            @Override public void render(dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){NativeMountClient.render(entity,matrices,consumers,light,overlay);}
            @Override public boolean rendersOutsideBoundingBox(dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity entity){return true;}
            @Override public int getRenderDistance(){return 1024;}
        });
    }
    public static void render(CompositeBlockEntity entity,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        if(entity.resident()==null||!entity.getPos().equals(CompositeData.pos(entity.resident().root()))||!VerticalMount.isNative(entity.getCachedState()))return;
        double amount=entity.getWorld()==null?entity.payload().getDouble(VerticalMount.KEY):VerticalMount.offset(entity.getWorld(),entity.getPos());if(amount==0)return;
        var client=MinecraftClient.getInstance();BlockState state=entity.getCachedState();var model=client.getBlockRenderManager().getModel(state);
        matrices.push();matrices.translate(0,amount,0);
        var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(state);
        var layer=state.getBlock() instanceof dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock?RenderLayer.getSolid():RenderLayer.getCutout();
        client.getBlockRenderManager().getModelRenderer().render(matrices.peek(),consumers.getBuffer(layer),state,model,1,1,1,light,overlay);matrices.pop();
    }
    /** Chunk rendering keeps the old palette anchor; the BER renders its translated instance. */
    private static final class MountedModel extends ForwardingBakedModel {
        MountedModel(BakedModel model){wrapped=model;}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
            if(VerticalMount.offset(world,pos)!=0)return;
            super.emitBlockQuads(world,state,pos,random,context);
        }
    }
}
