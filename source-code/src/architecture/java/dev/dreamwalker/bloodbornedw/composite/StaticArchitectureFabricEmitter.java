package dev.dreamwalker.bloodbornedw.composite;

import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/** Sends the existing source renderer's exact prelit quads through Indium's
 * normal buffers. Iris therefore receives its ordinary FRAPI vertex path. */
public final class StaticArchitectureFabricEmitter {
    private static final ConcurrentHashMap<RenderLayer,RenderMaterial> MATERIALS=new ConcurrentHashMap<>();
    private StaticArchitectureFabricEmitter(){}
    private static RenderMaterial material(RenderLayer layer){return MATERIALS.computeIfAbsent(layer,key->{
        var renderer=RendererAccess.INSTANCE.getRenderer();if(renderer==null)throw new IllegalStateException("No FRAPI source renderer");
        var blend=key==RenderLayer.getSolid()?BlendMode.SOLID:key==RenderLayer.getTranslucent()?BlendMode.TRANSLUCENT:BlendMode.CUTOUT;
        return renderer.materialFinder().blendMode(blend).disableColorIndex(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();
    });}
    public static void emit(BlockRenderView world,BlockState state,BlockPos pos,RenderContext context){
        if(!(context instanceof StaticArchitecturePrelitContext lighting))throw new IllegalStateException("Source geometry requires its prelit FRAPI context");
        boolean previous=lighting.staticArchitecturePrelit();lighting.staticArchitecturePrelit(true);
        try{
            var consumers=new IdentityHashMap<RenderLayer,VertexConsumer>();
            StaticArchitectureClient.renderLayered(MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer(),world,state,pos,new MatrixStack(),
                layer->consumers.computeIfAbsent(layer,key->new SourceConsumer(context.getEmitter(),material(key),pos)),OverlayTexture.DEFAULT_UV);
        }finally{lighting.staticArchitecturePrelit(previous);}
    }
    private static final class SourceConsumer implements VertexConsumer {
        private final QuadEmitter out;private final RenderMaterial material;private final BlockPos root;
        private int index,color=0xffffffff,light;private float x,y,z,u,v,nx,ny,nz;private boolean fixed;
        SourceConsumer(QuadEmitter out,RenderMaterial material,BlockPos root){this.out=out;this.material=material;this.root=root;}
        @Override public VertexConsumer vertex(double X,double Y,double Z){
            // Sodium 0.5's packed positions represent [-8,24). Reject a backend
            // incompatibility instead of wrapping a preserved large model.
            double a=(root.getX()&15)+X,b=(root.getY()&15)+Y,c=(root.getZ()&15)+Z;
            if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||a< -8||a>=24||b< -8||b>=24||c< -8||c>=24)
                throw new IllegalStateException("Source geometry exceeds Sodium's exact position range at "+root);
            x=(float)X;y=(float)Y;z=(float)Z;return this;
        }
        @Override public VertexConsumer color(int r,int g,int b,int a){if(!fixed)color=a<<24|r<<16|g<<8|b;return this;}
        @Override public VertexConsumer texture(float U,float V){u=U;v=V;return this;}
        @Override public VertexConsumer overlay(int U,int V){return this;}
        @Override public VertexConsumer light(int U,int V){light=U|V<<16;return this;}
        @Override public VertexConsumer normal(float X,float Y,float Z){nx=X;ny=Y;nz=Z;return this;}
        @Override public void next(){
            if(index==0)out.material(material).colorIndex(-1).cullFace(null).tag(0);
            out.pos(index,x,y,z).color(index,color).uv(index,u,v).lightmap(index,light).normal(index,nx,ny,nz);
            if(++index==4){out.emit();index=0;}
        }
        @Override public void fixedColor(int r,int g,int b,int a){color=a<<24|r<<16|g<<8|b;fixed=true;}
        @Override public void unfixColor(){fixed=false;}
    }
}
