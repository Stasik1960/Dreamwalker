package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
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
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.*;

/** Ordinary chunk-meshed static parts, using the existing shared source models
 * and vanilla light/AO renderer. There is no per-position render cache. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureClient {
    private static boolean initialized;
    private StaticArchitectureClient(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        for(var bank:StaticArchitectureBlock.banks())BlockRenderLayerMap.INSTANCE.putBlock(bank,RenderLayer.getCutout());
        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{
            if(context.id() instanceof ModelIdentifier id&&id.getNamespace().equals("bloodborne_dw")&&id.getPath().startsWith("static_architecture_"))return new MarkerModel(model);
            return model;
        }));
    }
    public static void render(BlockModelRenderer renderer,BlockRenderView world,BlockState state,BlockPos cell,MatrixStack outputMatrices,VertexConsumer vertices,int overlay){
        var client=MinecraftClient.getInstance();var recipe=StaticArchitectureCatalog.recipe(state);
        for(var fragment:recipe.fragments()){
            // One complete authored model stays in its native root section.
            // Static helper cells contain only their shared physical parts.
            // No newly interpolated lightmap vertices are introduced.
            if(!fragment.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN))continue;
            StaticArchitectureSectionBounds.root();
            var pose=fragment.pose();var origin=fragment.origin();BlockPos root=cell.add(origin.x(),origin.y(),origin.z());
            var shift=pose.shift().rotate(pose.state().get(CompositeRootBlock.ROTATION));
            MatrixStack source=new MatrixStack();source.translate(shift.x(),pose.mountY()+shift.y(),shift.z());
            rotate(source,.5,.5,.5,-pose.state().get(CompositeRootBlock.ROTATION)*45,false);
            int tint=RemainingSourceTint.color(pose.block(),world,root);
            var output=new SourceTintConsumer(vertices,tint);
            for(var part:pose.parts()){
                source.push();source.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
                rotate(source,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
                rotate(source,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
                rotate(source,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
                var view=new StaticArchitectureRenderView(world,root,source.peek().getPositionMatrix(),source.peek().getNormalMatrix(),pose);
                var carrier=ImportedSourceLightingV127.renderCarrier(pose.block().spec,pose.state());
                MatrixStack rendered=new MatrixStack();rendered.peek().getPositionMatrix().set(outputMatrices.peek().getPositionMatrix()).mul(source.peek().getPositionMatrix());
                rendered.peek().getNormalMatrix().set(outputMatrices.peek().getNormalMatrix()).mul(source.peek().getNormalMatrix());
                var model=client.getBakedModelManager().getModel(part.model());
                if(part.model().getPath().startsWith("remaining/source_emissive/")){
                    renderer.render(rendered.peek(),output,pose.state(),model,((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay);
                }else{
                    var automatic=carrier.getModelOffset(view,BlockPos.ORIGIN);rendered.translate(-automatic.x,-automatic.y,-automatic.z);
                    renderer.render(view,model,carrier,BlockPos.ORIGIN,rendered,output,false,Random.create(42),42,overlay);
                }
                source.pop();
            }
        }
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
    private static final class SourceTintConsumer implements VertexConsumer {
        private final VertexConsumer delegate;private final float red,green,blue;
        private SourceTintConsumer(VertexConsumer delegate,int tint){this.delegate=delegate;red=((tint>>16)&255)/255f;green=((tint>>8)&255)/255f;blue=(tint&255)/255f;}
        @Override public void quad(MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){delegate.quad(matrix,quad,brightness,quad.hasColor()?red:r,quad.hasColor()?green:g,quad.hasColor()?blue:b,lights,overlay,useColor);}
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
    private static final class MarkerModel implements BakedModel {
        private final BakedModel source;
        MarkerModel(BakedModel source){this.source=Objects.requireNonNull(source);}
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
