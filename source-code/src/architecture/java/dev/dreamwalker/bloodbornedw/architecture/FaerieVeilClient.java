package dev.dreamwalker.bloodbornedw.architecture;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Shared atlas animation. World position chooses a piece of one 8x8-block
 * composition during chunk baking; no per-block state or per-frame rendering. */
public final class FaerieVeilClient {
    public static final int PERIOD=8,TILES=PERIOD*PERIOD;
    private FaerieVeilClient(){}
    public static Identifier tileModel(int tile){return new Identifier("bloodborne_dw",String.format(java.util.Locale.ROOT,"block/faerie_veil/tile_%02d",tile));}
    public static void initialize(){ModelLoadingPlugin.register(plugin->{
        Session session=new Session();Identifier[] ids=new Identifier[TILES];for(int i=0;i<TILES;i++)ids[i]=tileModel(i);plugin.addModels(ids);
        plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
            var id=context.id();if(id.getNamespace().equals("bloodborne_dw")&&id.getPath().startsWith("block/faerie_veil/tile_")){int tile=Integer.parseInt(id.getPath().substring("block/faerie_veil/tile_".length()));session.tiles[tile]=model;return model;}
            if(id instanceof ModelIdentifier block&&block.getNamespace().equals("bloodborne_dw")&&block.getPath().equals(FaerieVeilBlock.ID.getPath()))return new Model(model,session);
            return model;
        });
    });}
    /** Derive UV directions from actual baked cube faces, avoiding mirrored
     * seams on north/east/down faces and across negative world coordinates. */
    public record Projection(int uAxis,int uSign,int vAxis,int vSign){
        private static int coordinate(BlockPos p,int axis){return axis==0?p.getX():axis==1?p.getY():p.getZ();}
        private static int tile(int value,int sign){return Math.floorMod(sign>0?value:-value-1,PERIOD);}
        public int at(BlockPos pos){return tile(coordinate(pos,vAxis),vSign)*PERIOD+tile(coordinate(pos,uAxis),uSign);}
    }
    private static Projection projection(BakedQuad quad){
        int[] data=quad.getVertexData();int[] axes=new int[2],signs=new int[2];
        for(int field=0;field<2;field++){double best=0;int selected=-1,sign=0;
            for(int axis=0;axis<3;axis++){double covariance=0;for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)covariance+=(Float.intBitsToFloat(data[a*8+axis])-Float.intBitsToFloat(data[b*8+axis]))*(Float.intBitsToFloat(data[a*8+4+field])-Float.intBitsToFloat(data[b*8+4+field]));
                if(Math.abs(covariance)>best){best=Math.abs(covariance);selected=axis;sign=covariance>0?1:-1;}}
            if(selected<0)throw new IllegalStateException("Degenerate veil UV projection");axes[field]=selected;signs[field]=sign;
        }
        return new Projection(axes[0],signs[0],axes[1],signs[1]);
    }
    private static final class Session {
        final BakedModel[] tiles=new BakedModel[TILES];final BakedQuad[][] faces=new BakedQuad[6][TILES];final Projection[] projections=new Projection[6];RenderMaterial material;boolean ready;
        synchronized void prepare(){if(ready)return;var renderer=RendererAccess.INSTANCE.getRenderer();if(renderer==null)throw new IllegalStateException("Veil requires Fabric Rendering API / Indium with Sodium");material=renderer.materialFinder().blendMode(BlendMode.SOLID).find();
            for(var face:Direction.values())for(int tile=0;tile<TILES;tile++){var model=Objects.requireNonNull(tiles[tile],"Missing veil tile "+tile);var quads=model.getQuads(null,face,Random.create(0));if(quads.size()!=1)throw new IllegalStateException("Veil tile must have one quad per face");faces[face.ordinal()][tile]=quads.get(0);if(tile==0)projections[face.ordinal()]=projection(quads.get(0));}
            ready=true;
        }
        void emit(BlockPos pos,RenderContext context){prepare();var emitter=context.getEmitter();for(var face:Direction.values()){int tile=projections[face.ordinal()].at(pos);emitter.fromVanilla(faces[face.ordinal()][tile],material,face);emitter.emit();}}
    }
    private static final class Model implements BakedModel,FabricBakedModel {
        private final BakedModel delegate;private final Session session;Model(BakedModel delegate,Session session){this.delegate=delegate;this.session=session;}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){session.emit(pos,context);}
        @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){session.emit(BlockPos.ORIGIN,context);}
        @Override public java.util.List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return delegate.getQuads(state,side,random);}
        @Override public boolean useAmbientOcclusion(){return true;}
        @Override public boolean hasDepth(){return true;}
        @Override public boolean isSideLit(){return true;}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return delegate.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return delegate.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return delegate.getOverrides();}
    }
}
