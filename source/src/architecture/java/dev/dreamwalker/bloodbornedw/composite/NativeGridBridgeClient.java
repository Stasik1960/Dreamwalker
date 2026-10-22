package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** The shared background enters the ordinary section model/material pipeline.
 * Only observed functional carrier registry IDs get this metadata wrapper. */
@Environment(EnvType.CLIENT)
public final class NativeGridBridgeClient {
    private NativeGridBridgeClient() {}
    public static void initialize() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
            if(!(context.id() instanceof ModelIdentifier id)||id.getVariant().equals("inventory"))return model;
            Identifier blockId=new Identifier(id.getNamespace(),id.getPath());
            if(!Registries.BLOCK.containsId(blockId))return model;
            BlockState state=Registries.BLOCK.get(blockId).getDefaultState();
            if(!NativeGridBridge.carrier(state))return model;
            for(String text:id.getVariant().split(",")){
                String[] pair=text.split("=",2);if(pair.length!=2)continue;
                Property<?> property=state.getBlock().getStateManager().getProperty(pair[0]);
                if(property!=null)state=property(state,property,pair[1]);
            }
            return new Model(model,ImportedCompactBlock.compact(state));
        }));
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
    private static final class Model implements BakedModel,FabricBakedModel {
        private final BakedModel legacy;
        private final boolean delegateCompact;
        Model(BakedModel legacy,boolean delegateCompact){this.legacy=legacy;this.delegateCompact=delegateCompact;}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return delegateCompact?legacy.getQuads(state,side,random):List.of();}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
            if(delegateCompact){
                FabricBakedModel fabric=(FabricBakedModel)legacy;
                if(!fabric.isVanillaAdapter())fabric.emitBlockQuads(world,state,pos,random,context);
                else context.bakedModelConsumer().accept(legacy,state);
            }
            int index=NativeGridBridge.renderBase(world.getBlockEntityRenderData(pos));
            if(index>=0)NativeGridClient.emitBridgeBase(index,world,pos,context);
        }
        @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){((FabricBakedModel)legacy).emitItemQuads(stack,random,context);}
        @Override public boolean useAmbientOcclusion(){return true;}
        @Override public boolean hasDepth(){return legacy.hasDepth();}
        @Override public boolean isSideLit(){return legacy.isSideLit();}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return legacy.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return legacy.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return legacy.getOverrides();}
    }
}
