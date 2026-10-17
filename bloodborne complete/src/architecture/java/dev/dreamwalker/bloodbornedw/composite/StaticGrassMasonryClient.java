package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** A shared ordinary chunk model: the exact source quads, UVs and v15 biome tint. */
@Environment(EnvType.CLIENT)
public final class StaticGrassMasonryClient {
    private StaticGrassMasonryClient(){}
    public static void initialize(){
        var block=StaticGrassMasonryBlock.block();
        var source=CompositeArchitecture.kindBlock(StaticGrassMasonryBlock.LEGACY_PATH);
        BlockRenderLayerMap.INSTANCE.putBlock(block,RenderLayer.getCutout());
        ColorProviderRegistry.BLOCK.register((state,world,pos,tint)->tint==0?RemainingSourceTint.color(source,world,pos):0xFFFFFF,block);
        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
            if(model!=null&&context.id() instanceof ModelIdentifier id
                &&id.getNamespace().equals(StaticGrassMasonryBlock.ID.getNamespace())
                &&id.getPath().equals(StaticGrassMasonryBlock.ID.getPath()))return new Unculled(model);
            return model;
        }));
    }
    /** The old owner renderer requested every face with culling disabled. Keep
     * those same baked quad objects in one immutable list per resource bake;
     * no per-position mesh, copied vertex data or BlockEntity is needed. */
    private static final class Unculled implements BakedModel {
        private final BakedModel source;
        private final List<BakedQuad> quads;
        private Unculled(BakedModel source){
            this.source=source;
            var all=new ArrayList<BakedQuad>();
            for(Direction side:Direction.values())all.addAll(source.getQuads(null,side,Random.create(42)));
            all.addAll(source.getQuads(null,null,Random.create(42)));
            quads=List.copyOf(all);
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return side==null?quads:List.of();}
        @Override public boolean useAmbientOcclusion(){return source.useAmbientOcclusion();}
        @Override public boolean hasDepth(){return source.hasDepth();}
        @Override public boolean isSideLit(){return source.isSideLit();}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return source.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return source.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return source.getOverrides();}
    }
}
