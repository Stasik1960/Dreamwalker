package dev.dreamwalker.bloodbornedw.architecture.wall;

import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.model.loading.v1.DelegatingUnbakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelVariant;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** State-to-appearance mapping without a multipart-selector ×state product.
 * Forty authored primitive IDs bake to160 quarter-turn/UV variants per reload.
 * State appearances only concatenate existing immutable primitive quads: no
 * duplicate geometry, quad transforms, mesh copies or global caches across reloads.
 */
public final class PrototypeWallModels {
    private static final String APPEARANCE="block/wall/appearance/";
    private static volatile Bank current;
    private PrototypeWallModels(){}
    public static void initialize(){
        ModelLoadingPlugin.register(plugin->{
            Bank bank=new Bank();current=bank;
            plugin.resolveModel().register(context->{
                Identifier id=context.id();
                if(!id.getNamespace().equals("bloodborne_dw")||!id.getPath().startsWith(APPEARANCE))return null;
                int key=Integer.parseInt(id.getPath().substring(APPEARANCE.length()));
                if(key<0||key>=20736)throw new IllegalArgumentException("Invalid wall appearance key:"+id);
                return new AppearanceUnbaked(bank,key);
            });
            Map<Integer,StateDelegateUnbaked> models=new HashMap<>();
            for(var block:PrototypeWallArchitecture.blocks())plugin.registerBlockStateResolver(block,context->{
                for(BlockState state:context.block().getStateManager().getStates()){
                    int key=key(state);StateDelegateUnbaked model=models.computeIfAbsent(key,value->new StateDelegateUnbaked(bank,value));
                    context.setModel(state,model);
                }
                bank.stateBindings+=context.block().getStateManager().getStates().size();bank.visualKeys=models.size();
            });
        });
    }
    /** Diagnostic counts for actual client QA, not a claim that resources baked successfully. */
    public static Map<String,Integer> snapshot(){
        Bank bank=current;if(bank==null)return Map.of();
        return Map.of("stateBindings",bank.stateBindings,"visualKeys",bank.visualKeys,
            "primitiveResourceIds",bank.dependencies.size(),"primitiveBakedModels",bank.primitiveBakedModels,
            "primitiveModelsWithGeometry",bank.primitiveModelsWithGeometry,"parentsLinked",bank.parentsLinked?1:0,
            "appearanceBakedModels",bank.appearances.size(),"clonedBakedQuads",0);
    }
    public static int junctionCacheSize(){return WallStackGeometry.cacheSize();}
    public static int key(BlockState state){
        state=PrototypeWallBlock.canonicalForm(state);
        int sides=PrototypeWallBlock.sideCode(state),art=PrototypeWallBlock.hasTallSide(state)?PrototypeWallBlock.material(state):0;
        return sides+81*((state.get(PrototypeWallBlock.POST)?1:0)+2*(state.get(PrototypeWallBlock.ROTATION)
            +8*((state.get(PrototypeWallBlock.PROFILE)==PrototypeWallBlock.Profile.ALT?1:0)+2*art)));
    }
    private static Identifier primitive(int profile,int diagonal,int kind){
        String name=kind==0?"post":kind==1?"low":"tall_"+(kind-2);
        return new Identifier("bloodborne_dw","block/wall/"+(profile==0?"base":"alt")+"/"+name+(diagonal==0?"":"_diagonal"));
    }
    private static final class Bank {
        final List<Identifier> dependencies;
        final BakedModel[][][][] parts=new BakedModel[2][2][10][4];
        final Map<Integer,AppearanceBaked> appearances=new HashMap<>();
        boolean parentsLinked,baked;
        volatile int stateBindings,visualKeys,primitiveBakedModels,primitiveModelsWithGeometry;
        Bank(){List<Identifier> all=new ArrayList<>();for(int profile=0;profile<2;profile++)for(int diagonal=0;diagonal<2;diagonal++)for(int kind=0;kind<10;kind++)all.add(primitive(profile,diagonal,kind));dependencies=List.copyOf(all);}
        synchronized void parents(Function<Identifier,UnbakedModel> lookup){if(parentsLinked)return;for(Identifier id:dependencies)lookup.apply(id).setParents(lookup);parentsLinked=true;}
        synchronized AppearanceBaked bake(int key,Baker baker){
            if(!baked){
                for(int profile=0;profile<2;profile++)for(int diagonal=0;diagonal<2;diagonal++)for(int kind=0;kind<10;kind++)for(int quarter=0;quarter<4;quarter++){
                    Identifier id=primitive(profile,diagonal,kind);
                    // Exact old multipart bake settings: only LOW uses UV lock.
                    parts[profile][diagonal][kind][quarter]=Objects.requireNonNull(baker.bake(id,new ModelVariant(id,ModelRotation.get(0,quarter*90).getRotation(),kind==1,1)),"Missing wall primitive:"+id);
                    BakedModel part=parts[profile][diagonal][kind][quarter];int quads=part.getQuads(null,null,Random.create(0)).size();
                    for(Direction face:Direction.values())quads+=part.getQuads(null,face,Random.create(0)).size();
                    if(quads>0)primitiveModelsWithGeometry++;
                    primitiveBakedModels++;
                }
                baked=true;
            }
            return appearances.computeIfAbsent(key,value->new AppearanceBaked(this,value));
        }
    }
    private static final class StateDelegateUnbaked implements UnbakedModel {
        final Bank bank;final DelegatingUnbakedModel delegate;
        StateDelegateUnbaked(Bank bank,int key){this.bank=bank;delegate=new DelegatingUnbakedModel(new Identifier("bloodborne_dw",APPEARANCE+key));}
        @Override public Collection<Identifier> getModelDependencies(){return delegate.getModelDependencies();}
        // Native ModelLoader links only top-level modelsToBake. Fabric's delegate
        // intentionally has an empty setParents; our dependency-only JSON parents
        // therefore must be linked from this top-level state model, once per bank.
        @Override public void setParents(Function<Identifier,UnbakedModel> lookup){bank.parents(lookup);}
        @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> textureGetter,ModelBakeSettings settings,Identifier id){return delegate.bake(baker,textureGetter,settings,id);}
    }
    private static final class AppearanceUnbaked implements UnbakedModel {
        final Bank bank;final int key;
        AppearanceUnbaked(Bank bank,int key){this.bank=bank;this.key=key;}
        @Override public Collection<Identifier> getModelDependencies(){return bank.dependencies;}
        @Override public void setParents(Function<Identifier,UnbakedModel> lookup){bank.parents(lookup);}
        @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> textureGetter,ModelBakeSettings settings,Identifier id){return bank.bake(key,baker);}
    }
    private static final class AppearanceBaked implements BakedModel {
        final List<BakedModel> selected;
        final BakedModel particle;
        final AtomicReferenceArray<List<BakedQuad>> faces=new AtomicReferenceArray<>(7);
        AppearanceBaked(Bank bank,int key){
            int sides=key%81,rest=key/81,post=rest%2;rest/=2;int yaw=rest%8;rest/=8;int profile=rest%2,art=rest/2,quarter=yaw/2,diagonal=yaw&1;
            List<BakedModel> selected=new ArrayList<>(5);int particleKind=0;
            if(post!=0)selected.add(bank.parts[profile][diagonal][0][quarter]);
            for(int direction=0;direction<4;direction++){int shape=sides%3;sides/=3;if(shape==0)continue;int kind=shape==1?1:2+art;selected.add(bank.parts[profile][diagonal][kind][(quarter+direction)&3]);if(particleKind==0||shape==2)particleKind=kind;}
            this.selected=List.copyOf(selected);
            // The particle matches the actual selected arm material/profile; a
            // standalone post uses its own authored particle texture.
            particle=bank.parts[profile][diagonal][particleKind][quarter];
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){
            int index=face==null?6:face.ordinal();List<BakedQuad> cached=faces.get(index);if(cached!=null)return cached;
            List<BakedQuad> quads=new ArrayList<>();for(BakedModel model:selected)quads.addAll(model.getQuads(state,face,Random.create(0)));
            List<BakedQuad> immutable=List.copyOf(quads);faces.compareAndSet(index,null,immutable);return faces.get(index);
        }
        @Override public boolean useAmbientOcclusion(){return particle.useAmbientOcclusion();}
        @Override public boolean hasDepth(){return particle.hasDepth();}
        @Override public boolean isSideLit(){return particle.isSideLit();}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return particle.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return particle.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return ModelOverrideList.EMPTY;}
    }
}
