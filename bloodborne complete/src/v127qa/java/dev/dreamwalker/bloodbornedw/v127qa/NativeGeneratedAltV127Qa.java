package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.tool.BbAlternateService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** QA-only: actual production source renderer -> native BlockModelRenderer -> captured vertices. */
public final class NativeGeneratedAltV127Qa {
    private NativeGeneratedAltV127Qa(){}
    public static JsonObject verify(MinecraftClient client)throws Exception {
        var report=new JsonObject();var cases=new JsonArray();report.add("cases",cases);
        for(String id:List.of("source_addon_barrel_0","source_grass_block")){
            var block=CompositeArchitecture.kindBlock(id);var base=block.getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE);var alt=base.with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT);
            check(BbAlternateService.generatedAlternate(block,alt,block.spec.pose(alt).parts()),"Fixture uses actual generated NEW alternate: "+id);
            int tinted=0,untinted=0;
            for(var part:block.spec.pose(base).parts()){
                var model=client.getBakedModelManager().getModel(part.model());
                var faces=new ArrayList<Direction>(List.of(Direction.values()));faces.add(null);
                for(var face:faces)for(var quad:model.getQuads(base,face,Random.create(42))){if(quad.hasColor())tinted++;else untinted++;}
            }
            check(id.equals("source_grass_block")?tinted>0:untinted>0,"Actual source model contains required native tinted/untinted faces: "+id);
            var pos=new BlockPos(id.equals("source_grass_block")?50:12,64,0);var before=render(block,base,pos);var after=render(block,alt,pos);
            check(!before.isEmpty()&&before.size()==after.size(),"Actual BASE/ALT emit the same nonempty vertex count: "+id);
            int changed=0;
            for(int i=0;i<before.size();i++){
                var original=before.get(i);var actual=after.get(i);
                check(original.geometry.equals(actual.geometry),"BASE/ALT preserve positions, alpha, UV, lighting, overlay and normals at vertex "+i+": "+id);
                // Independent documented factors, not read back from the production wrapper.
                check(close(actual.red,original.red*.60f)&&close(actual.green,original.green*.67f)&&close(actual.blue,original.blue*.72f),"Every native emitted RGB receives exactly one ALT multiplication: "+id+" vertex "+i);
                if(!close(original.red,actual.red)||!close(original.green,actual.green)||!close(original.blue,actual.blue))changed++;
            }
            check(changed==before.size(),"ALT changes every actual source face, including untinted faces: "+id);
            var row=new JsonObject();row.addProperty("id",id);row.addProperty("nativeTintedQuads",tinted);row.addProperty("nativeUntintedQuads",untinted);row.addProperty("emittedVertices",before.size());row.addProperty("onceTonedVertices",changed);row.addProperty("geometryUvAlphaLightOverlayNormals","EXACT_UNCHANGED");row.addProperty("status","PASS_ACTUAL_PRODUCTION_RENDER_PATH");cases.add(row);
        }
        report.addProperty("status","PASS_NATIVE_SOURCE_GENERATED_ALT_ALL_FACES_ONCE");report.addProperty("method","Private production CompositeClient.renderParts with actual baked source models, native BlockModelRenderer and recording VertexConsumer; independent RGB factors .60/.67/.72");return report;
    }
    private static List<Vertex> render(CompositeRootBlock block,BlockState state,BlockPos pos)throws Exception {
        var target=new Capture();VertexConsumerProvider consumers=layer->target;
        var method=CompositeClient.class.getDeclaredMethod("renderParts",CompositeRootBlock.class,BlockState.class,MatrixStack.class,VertexConsumerProvider.class,int.class,int.class,String.class,BlockPos.class,NbtCompound.class);method.setAccessible(true);
        method.invoke(null,block,state,new MatrixStack(),consumers,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null,pos,null);return List.copyOf(target.vertices);
    }
    private record Geometry(double x,double y,double z,float alpha,float u,float v,int overlay,int light,float nx,float ny,float nz){}
    private record Vertex(Geometry geometry,float red,float green,float blue){}
    private static final class Capture implements VertexConsumer {
        private final List<Vertex> vertices=new ArrayList<>();private double x,y,z;private float red,green,blue,alpha,u,v,nx,ny,nz;private int overlay,light;
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){red=r/255f;green=g/255f;blue=b/255f;alpha=a/255f;return this;}
        @Override public VertexConsumer texture(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer overlay(int u,int v){overlay=u|(v<<16);return this;}
        @Override public VertexConsumer light(int u,int v){light=u|(v<<16);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        @Override public void next(){vertices.add(new Vertex(new Geometry(x,y,z,alpha,u,v,overlay,light,nx,ny,nz),red,green,blue));}
        @Override public void fixedColor(int r,int g,int b,int a){color(r,g,b,a);}
        @Override public void unfixColor(){}
        @Override public void vertex(float x,float y,float z,float red,float green,float blue,float alpha,float u,float v,int overlay,int light,float nx,float ny,float nz){vertices.add(new Vertex(new Geometry(x,y,z,alpha,u,v,overlay,light,nx,ny,nz),red,green,blue));}
    }
    private static boolean close(float a,float b){return Math.abs(a-b)<.000002f;}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
