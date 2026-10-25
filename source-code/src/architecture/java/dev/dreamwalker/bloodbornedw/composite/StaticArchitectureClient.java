package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.*;
import java.util.function.Function;

/** Ordinary chunk-meshed static parts, using the existing shared source models
 * and vanilla light/AO renderer. There is no per-position render cache. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureClient {
    private static boolean initialized;
    private static final java.util.concurrent.atomic.LongAdder terrainRoots=new java.util.concurrent.atomic.LongAdder(),terrainParts=new java.util.concurrent.atomic.LongAdder(),missingSnapshots=new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder bypassedBer=new java.util.concurrent.atomic.LongAdder(),fallbackFrames=new java.util.concurrent.atomic.LongAdder();
    private StaticArchitectureClient(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        CompositeTerrainUpdates.initialize();
        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
            if(context.id() instanceof ModelIdentifier id&&id.getNamespace().equals("bloodborne_dw")&&id.getPath().startsWith("static_architecture_"))return new MarkerModel(model);
            if(context.id() instanceof ModelIdentifier id&&!id.getVariant().equals("inventory")&&!id.getVariant().contains("compact=true")&&CompositeTerrain.eligible(net.minecraft.registry.Registries.BLOCK.get(new Identifier(id.getNamespace(),id.getPath())).getDefaultState()))return new MarkerModel(model);
            return model;
        }));
    }
    public static RenderLayer layer(BlockState state){
        if(CompositeTerrain.eligible(state))return ((CompositeRootBlock)state.getBlock()).spec.translucent?RenderLayer.getTranslucent():RenderLayer.getCutout();
        var recipe=StaticArchitectureCatalog.recipe(state);
        if(recipe.translucent())return RenderLayer.getTranslucent();
        return recipe.resident()>=0&&StaticArchitectureGeometry.opaque(recipe.fragments().get(recipe.resident()).pose())?RenderLayer.getSolid():RenderLayer.getCutout();
    }
    public static void render(BlockModelRenderer renderer,BlockRenderView world,BlockState state,BlockPos cell,MatrixStack outputMatrices,VertexConsumer vertices,int overlay){
        renderTo(renderer,world,state,cell,outputMatrices,vertices,overlay);
    }
    /** Indigo can initialize the native terrain buffer selected by each proven
     * quad. Only genuinely transparent/ambiguous faces retain the original
     * translucent consumer; opaque stone, book covers and frames write depth
     * in the solid pass even when their source object also contains paper/glass. */
    public static void renderLayered(BlockModelRenderer renderer,BlockRenderView world,BlockState state,BlockPos cell,MatrixStack outputMatrices,Function<RenderLayer,VertexConsumer> consumerFactory,int overlay){
        RenderLayer original=layer(state);
        VertexConsumer output=new QuadLayerConsumer(consumerFactory,original);
        renderTo(renderer,world,state,cell,outputMatrices,output,overlay);
    }
    private static void renderTo(BlockModelRenderer renderer,BlockRenderView world,BlockState state,BlockPos cell,MatrixStack outputMatrices,VertexConsumer vertices,int overlay){
        vertices=StaticArchitectureSectionBounds.record(vertices);
        if(CompositeTerrain.eligible(state)){
            if(!CompositeTerrain.at(state,cell))return;
            Object data=world.getBlockEntityRenderData(cell);
            if(!(data instanceof CompositeTerrain.Snapshot snapshot)||snapshot.owner()==null||snapshot.state()!=state){missingSnapshots.increment();return;}
            if(!snapshot.meshable())return;
            var payload=snapshot.payload();var block=(CompositeRootBlock)state.getBlock();
            if(block.spec.simplePhysicalCollision&&ImportedTypeAliases.needsMigration(state,payload)){payload=ImportedTypeAliases.transformPayload(state,payload);state=ImportedTypeAliases.target(state);block=(CompositeRootBlock)state.getBlock();}
            var shift=CompositeSourceShift.world(state,payload);
            renderSource(renderer,world,cell,outputMatrices,vertices,overlay,block,state,shift,payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY),ImportedStairLegacyArt.selectedParts(block,state,payload),null,snapshot.owner(),payload.getInt(ImportedWholeSourceOpticsV127.CONTEXT)==1);
            terrainRoots.increment();return;
        }
        var client=MinecraftClient.getInstance();var recipe=StaticArchitectureCatalog.recipe(state);
        for(var fragment:recipe.fragments()){
            // One complete authored model stays in its native root section.
            // Static helper cells contain only their shared physical parts.
            // No newly interpolated lightmap vertices are introduced.
            if(!fragment.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN))continue;
            StaticArchitectureSectionBounds.root(cell,recipe);
            var pose=fragment.pose();var origin=fragment.origin();BlockPos root=cell.add(origin.x(),origin.y(),origin.z());
            renderSource(renderer,world,root,outputMatrices,vertices,overlay,pose.block(),pose.state(),pose.shift().rotate(pose.state().get(CompositeRootBlock.ROTATION)),pose.mountY(),pose.parts(),pose,null,true);
        }
    }
    /** Shared source transforms and native light/AO emission for both storage
     * forms. Owned instances never allocate an intermediate CPU/GPU mesh. */
    private static void renderSource(BlockModelRenderer renderer,BlockRenderView world,BlockPos root,MatrixStack outputMatrices,VertexConsumer vertices,int overlay,CompositeRootBlock block,BlockState state,CompositeSourceShift.Shift shift,double mountY,List<CompositeSpec.Part> parts,StaticArchitectureCatalog.Pose pose,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner,boolean wholeSourceOptics){
            var client=MinecraftClient.getInstance();
            MatrixStack source=new MatrixStack();source.translate(shift.x(),mountY+shift.y(),shift.z());
            rotate(source,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
            int tint=RemainingSourceTint.color(block,world,root);
            var output=new SourceTintConsumer(vertices,tint);
            int partIndex=0;
            for(var part:parts){
                source.push();source.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
                rotate(source,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
                rotate(source,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
                rotate(source,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
                var view=pose!=null?new StaticArchitectureRenderView(world,root,source.peek().getPositionMatrix(),source.peek().getNormalMatrix(),pose):new StaticArchitectureRenderView(world,root,source.peek().getPositionMatrix(),source.peek().getNormalMatrix(),owner,wholeSourceOptics);
                var carrier=ImportedSourceLightingV127.renderCarrier(block.spec,state);
                MatrixStack rendered=new MatrixStack();rendered.peek().getPositionMatrix().set(outputMatrices.peek().getPositionMatrix()).mul(source.peek().getPositionMatrix());
                rendered.peek().getNormalMatrix().set(outputMatrices.peek().getNormalMatrix()).mul(source.peek().getNormalMatrix());
                var model=pose==null?client.getBakedModelManager().getModel(part.model()):StaticArchitectureGeometry.visible(pose,partIndex,world,root);partIndex++;
                if(pose==null)terrainParts.increment();
                output.flatLighting(null,carrier,null);
                if(part.model().getPath().startsWith("remaining/source_emissive/")){
                    renderer.render(rendered.peek(),output,state,model,((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay);
                }else{
                    // Match vanilla's branch selection exactly. AO and its
                    // neighbourhood/weights remain native and unchanged.
                    boolean smooth=MinecraftClient.isAmbientOcclusionEnabled()&&carrier.getLuminance()==0&&model.useAmbientOcclusion();
                    boolean project=!smooth&&block.spec.id.getPath().startsWith("source_")&&carrier.isOpaqueFullCube(view,BlockPos.ORIGIN);
                    output.flatLighting(project?view:null,carrier,project?client.getBakedModelManager().getModel(part.model()):null);
                    var automatic=carrier.getModelOffset(view,BlockPos.ORIGIN);rendered.translate(-automatic.x,-automatic.y,-automatic.z);
                    renderer.render(view,model,carrier,BlockPos.ORIGIN,rendered,output,false,Random.create(42),42,overlay);
                }
                source.pop();
            }
    }
    public static void ownerFrame(boolean terrain){if(terrain)bypassedBer.increment();else fallbackFrames.increment();}
    public static Map<String,Object> terrainMetrics(){return Map.of("scope",CompositeTerrain.scope(),"sectionBuiltOwnedRoots",terrainRoots.sum(),"sectionBuiltOwnedParts",terrainParts.sum(),"missingRenderSnapshots",missingSnapshots.sum(),"regionRejectedSnapshots",CompositeTerrain.rejectedRegionRevisions(),"fallbackFrames",fallbackFrames.sum(),"bypassedBerFrames",bypassedBer.sum(),"ownedTerrainCpuMeshBytes",0L,"ownedTerrainSeparateGpuBytes",0L,"updates",CompositeTerrainUpdates.metrics());}
    /** Vanilla model rendering reaches quad(). Any unknown streaming primitive
     * stays on the cell's established layer instead of being guessed opaque. */
    private static final class QuadLayerConsumer implements VertexConsumer {
        private final Function<RenderLayer,VertexConsumer> factory;private final VertexConsumer fallback;private final boolean translucent;
        private VertexConsumer solid,cutout;
        private QuadLayerConsumer(Function<RenderLayer,VertexConsumer> factory,RenderLayer original){this.factory=factory;fallback=factory.apply(original);translucent=original==RenderLayer.getTranslucent();}
        private VertexConsumer solid(){return solid==null?solid=factory.apply(RenderLayer.getSolid()):solid;}
        private VertexConsumer nonOpaque(){return translucent?fallback:cutout==null?cutout=factory.apply(RenderLayer.getCutout()):cutout;}
        @Override public void quad(MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){
            (StaticArchitectureGeometry.opaqueQuad(quad)?solid():nonOpaque()).quad(matrix,quad,brightness,r,g,b,lights,overlay,useColor);
        }
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){fallback.vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz);}
        @Override public VertexConsumer vertex(double x,double y,double z){fallback.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){fallback.color(r,g,b,a);return this;}
        @Override public VertexConsumer texture(float u,float v){fallback.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){fallback.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v){fallback.light(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){fallback.normal(x,y,z);return this;}
        @Override public void next(){fallback.next();}
        @Override public void fixedColor(int r,int g,int b,int a){fallback.fixedColor(r,g,b,a);}
        @Override public void unfixColor(){fallback.unfixColor();}
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
    private static final class SourceTintConsumer implements VertexConsumer {
        private final VertexConsumer delegate;private final float red,green,blue;
        private StaticArchitectureRenderView flatView;private BlockState flatCarrier;
        private Set<BakedQuad> rootQuads=Set.of();
        private SourceTintConsumer(VertexConsumer delegate,int tint){this.delegate=delegate;red=((tint>>16)&255)/255f;green=((tint>>8)&255)/255f;blue=(tint&255)/255f;}
        private void flatLighting(StaticArchitectureRenderView view,BlockState carrier,BakedModel raw){flatView=view;flatCarrier=carrier;rootQuads=view==null?Set.of():SourceFlatLight.rootQuads(raw,carrier);}
        @Override public void quad(MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){
            if(flatView!=null&&rootQuads.contains(quad)){int light=flatView.flatLight(quad,flatCarrier);if(light>=0)lights=new int[]{light,light,light,light};}
            delegate.quad(matrix,quad,brightness,quad.hasColor()?red:r,quad.hasColor()?green:g,quad.hasColor()?blue:b,lights,overlay,useColor);
        }
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){delegate.vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz);}
        @Override public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){delegate.color(r,g,b,a);return this;}
        @Override public VertexConsumer texture(float u,float v){delegate.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){delegate.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v){delegate.light(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        @Override public void next(){delegate.next();}
        @Override public void fixedColor(int r,int g,int b,int a){delegate.fixedColor(r,g,b,a);}
        @Override public void unfixColor(){delegate.unfixColor();}
    }
    /** The source is only a shared particle/model metadata carrier. Actual
     * geometry is emitted by the narrowly scoped native renderer hook. */
    private static final class MarkerModel implements BakedModel,net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel {
        private final BakedModel source;
        MarkerModel(BakedModel source){this.source=Objects.requireNonNull(source);}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,java.util.function.Supplier<Random> random,net.fabricmc.fabric.api.renderer.v1.render.RenderContext context){StaticArchitectureFabricEmitter.emit(world,state,pos,context);}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return List.of();}
        @Override public boolean useAmbientOcclusion(){return true;}
        @Override public boolean hasDepth(){return true;}
        @Override public boolean isSideLit(){return true;}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return source.getParticleSprite();}
        @Override public ModelTransformation getTransformation(){return source.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return source.getOverrides();}
    }
}
