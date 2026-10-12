package dev.dreamwalker.bloodbornedw.tool;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** The old native V12 ALT parents were byte-identical to BASE. Apply a colour
 * only in ALT state wrappers; the immutable source mesh, UV and BASE are untouched. */
@Environment(EnvType.CLIENT)
public final class NativeAlternateVisualClient {
    private static boolean initialized;
    private NativeAlternateVisualClient(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ModelLoadingPlugin.register(plugin->{Map<BakedModel,BakedModel> wrappers=new IdentityHashMap<>();
            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
                if(model==null||!context.id().getNamespace().equals("bloodborne_dw"))return model;
                String path=context.id().getPath();
                boolean state=context.id() instanceof ModelIdentifier id&&!id.getVariant().equals("inventory")&&Arrays.asList(id.getVariant().split(",")).contains("profile=alt")&&(path.equals("prototype_ladder")||path.startsWith("prototype_ladder_art_")||path.equals("prototype_wall")||path.startsWith("prototype_wall_skin_"));
                boolean item=path.startsWith("item/prototype_ladder_alt_")||path.startsWith("item/wall/alt/");
                return state||item?wrappers.computeIfAbsent(model,TonedModel::new):model;
            });
        });
    }
    /** Packed Fabric colours are ARGB; vanilla vertex arrays store ABGR. */
    static int toneArgb(int color){return (color&0xff000000)|(Math.round(((color>>>16)&255)*BbAlternateService.ALT_RED)<<16)|(Math.round(((color>>>8)&255)*BbAlternateService.ALT_GREEN)<<8)|Math.round((color&255)*BbAlternateService.ALT_BLUE);}
    static int toneAbgr(int color){return (color&0xff000000)|(Math.round(((color>>>16)&255)*BbAlternateService.ALT_BLUE)<<16)|(Math.round(((color>>>8)&255)*BbAlternateService.ALT_GREEN)<<8)|Math.round((color&255)*BbAlternateService.ALT_RED);}
    private static final class TonedModel extends ForwardingBakedModel {
        private final Map<BakedQuad,BakedQuad> colors=new IdentityHashMap<>();
        TonedModel(BakedModel model){wrapped=model;}
        @Override public boolean isVanillaAdapter(){return false;}
        private boolean tone(MutableQuadView quad){for(int vertex=0;vertex<4;vertex++)quad.color(vertex,toneArgb(quad.color(vertex)));return true;}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){context.pushTransform(this::tone);try{super.emitBlockQuads(world,state,pos,random,context);}finally{context.popTransform();}}
        @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){context.pushTransform(this::tone);try{super.emitItemQuads(stack,random,context);}finally{context.popTransform();}}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){var result=new ArrayList<BakedQuad>();synchronized(colors){for(var quad:wrapped.getQuads(state,face,random))result.add(colors.computeIfAbsent(quad,this::toneVanilla));}return result;}
        private BakedQuad toneVanilla(BakedQuad quad){int[] data=quad.getVertexData().clone();int stride=data.length/4;if(stride<8)throw new IllegalStateException("Unexpected native ALT vertex format");for(int vertex=0;vertex<4;vertex++){int index=vertex*stride+3;data[index]=toneAbgr(data[index]);}return new BakedQuad(data,quad.getColorIndex(),quad.getFace(),quad.getSprite(),quad.hasShade());}
    }
}
