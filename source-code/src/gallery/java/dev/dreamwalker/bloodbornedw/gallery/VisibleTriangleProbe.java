package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/** One-shot evidence for an explicitly requested screenshot pixel. It invokes
 * the shipped renderers and intersects their transformed output triangles;
 * collider geometry is used only to bound the diagnostic's search distance. */
final class VisibleTriangleProbe {
    private static final Gson JSON=new GsonBuilder().create();
    private VisibleTriangleProbe(){}
    static JsonObject capture(MinecraftClient client,Vec3d start,Vector3f direction,double distance){
        JsonObject out=new JsonObject();
        out.addProperty("method","SHIPPED_RENDERER_TRANSFORMED_TRIANGLES_AND_TEXTURE_ALPHA");
        out.addProperty("distance",distance);
        // Published source bounds allow 16 horizontal / 32 vertical root
        // displacement. Include every potential protruding root along this ray.
        Vec3d end=start.add(direction.x*distance,direction.y*distance,direction.z*distance);
        int minX=(int)Math.floor(Math.min(start.x,end.x))-17,maxX=(int)Math.floor(Math.max(start.x,end.x))+17;
        int minY=(int)Math.floor(Math.min(start.y,end.y))-33,maxY=(int)Math.floor(Math.max(start.y,end.y))+33;
        int minZ=(int)Math.floor(Math.min(start.z,end.z))-17,maxZ=(int)Math.floor(Math.max(start.z,end.z))+17;
        out.add("rootSearchFrom",JSON.toJsonTree(List.of(minX,minY,minZ)));out.add("rootSearchTo",JSON.toJsonTree(List.of(maxX,maxY,maxZ)));
        Recorder recorder=new Recorder(client,start,direction,distance);int cells=0,rendered=0,failures=0;
        var errors=new JsonArray();
        for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            var pos=new BlockPos(x,y,z);var state=client.world.getBlockState(pos);cells++;
            if(state.isAir())continue;
            try{
                if(state.getRenderType()==BlockRenderType.MODEL){
                    var renderEntity=client.world.getBlockEntity(pos);
                    boolean terrainRoot=state.getBlock() instanceof CompositeRootBlock&&renderEntity instanceof CompositeBlockEntity entity&&entity.resident()!=null;
                    // Unit native models cannot meet a ray outside their cube;
                    // imported/static models retain the full source-bound scan.
                    if(!terrainRoot&&!(state.getBlock() instanceof ImportedArtworkBlock)&&!(state.getBlock() instanceof StaticArchitectureBlock)
                        &&new Box(pos).raycast(start,end).isEmpty())continue;
                    recorder.begin(pos,state,"NATIVE_MODEL",List.of());
                    if(state.getBlock() instanceof StaticArchitectureBlock){
                        var recipe=StaticArchitectureCatalog.recipe(state);var sources=new JsonArray();
                        boolean canMeetRay=false;
                        for(var fragment:recipe.fragments())if(fragment.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)){
                            var bounds=fragment.pose().visualBounds().offset(pos);if(bounds.contains(start)||bounds.raycast(start,end).isPresent()){canMeetRay=true;break;}
                        }
                        if(!canMeetRay)continue;
                        for(var fragment:recipe.fragments())if(fragment.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)){
                            var row=new JsonObject();row.addProperty("pose",fragment.pose().key());row.addProperty("sourceState",fragment.pose().state().toString());
                            var carrier=ImportedSourceLightingV127.renderCarrier(fragment.pose().block().spec,fragment.pose().state());row.addProperty("carrier",carrier.toString());row.addProperty("carrierOpaqueFullCube",carrier.isOpaqueFullCube(client.world,pos));
                            row.add("models",JSON.toJsonTree(fragment.pose().parts().stream().map(p->p.model().toString()).toList()));sources.add(row);
                        }
                        recorder.source.add("staticSources",sources);
                        StaticArchitectureClient.render(client.getBlockRenderManager().getModelRenderer(),client.world,state,pos,new MatrixStack(),recorder,OverlayTexture.DEFAULT_UV);
                    }else if(terrainRoot){
                        var entity=(CompositeBlockEntity)renderEntity;var block=(CompositeRootBlock)state.getBlock();
                        var parts=ImportedStairLegacyArt.selectedParts(block,state,entity.payload());
                        recorder.source.add("models",JSON.toJsonTree(parts.stream().map(p->p.model().toString()).toList()));
                        recorder.source.addProperty("sourceRegistryId",entity.resident().registryId());
                        var carrier=ImportedSourceLightingV127.renderCarrier(block.spec,state);recorder.source.addProperty("carrier",carrier.toString());recorder.source.addProperty("carrierOpaqueFullCube",carrier.isOpaqueFullCube(client.world,pos));
                        StaticArchitectureClient.render(client.getBlockRenderManager().getModelRenderer(),client.world,state,pos,new MatrixStack(),recorder,OverlayTexture.DEFAULT_UV);
                    }else{
                        long seed=state.getRenderingSeed(pos);
                        client.getBlockRenderManager().getModelRenderer().render(client.world,client.getBlockRenderManager().getModel(state),state,pos,new MatrixStack(),recorder,true,Random.create(seed),seed,OverlayTexture.DEFAULT_UV);
                    }
                    rendered++;
                }else if(client.world.getBlockEntity(pos) instanceof CompositeBlockEntity entity&&entity.resident()!=null){
                    if(!(state.getBlock() instanceof CompositeRootBlock block))continue;
                    var parts=ImportedStairLegacyArt.selectedParts(block,state,entity.payload());var sprites=new ArrayList<Sprite>();
                    for(var part:parts){var model=client.getBakedModelManager().getModel(part.model());addSprites(sprites,model,state);}
                    recorder.begin(pos,state,"SHIPPED_BLOCK_ENTITY_DISPATCHER",sprites);
                    recorder.source.add("models",JSON.toJsonTree(parts.stream().map(p->p.model().toString()).toList()));
                    recorder.source.addProperty("sourceRegistryId",entity.resident().registryId());
                    client.getBlockEntityRenderDispatcher().render(entity,client.getTickDelta(),new MatrixStack(),layer->{recorder.layer=layer.toString();return recorder;});rendered++;
                }
            }catch(RuntimeException failure){failures++;if(errors.size()<4)errors.add(pos.toShortString()+": "+failure);}
        }
        recorder.hits.sort(Comparator.comparingDouble(h->h.get("distance").getAsDouble()));
        JsonArray hits=new JsonArray();for(var hit:recorder.hits)hits.add(hit);
        out.addProperty("searchedCells",cells);out.addProperty("renderedCandidates",rendered);out.addProperty("testedQuads",recorder.quads);out.addProperty("rendererFailures",failures);out.add("errors",errors);out.add("nearestIntersections",hits);
        out.addProperty("limitation","Bounded root search follows published source visual bounds; no collider is claimed to be the visible surface. Alpha uses source level 0; mip texels are reported separately.");
        return out;
    }
    private static void addSprites(List<Sprite> sprites,BakedModel model,BlockState state){
        for(int i=-1;i<6;i++)for(var quad:model.getQuads(state,i<0?null:Direction.values()[i],Random.create(42)))if(!sprites.contains(quad.getSprite()))sprites.add(quad.getSprite());
    }
    private static final class Recorder implements VertexConsumer{
        private final MinecraftClient client;private final Vec3d start;private final double dx,dy,dz,maxDistance;
        private BlockPos root;private JsonObject source,quadMetadata;private Sprite sprite;private List<Sprite> sprites=List.of();private String layer="";
        private final double[][] vertices=new double[4][14];private int count;private long quads;private final List<JsonObject> hits=new ArrayList<>();
        private double x,y,z;private float u,v,nx,ny,nz;private int red=255,green=255,blue=255,alpha=255,light,overlay;
        Recorder(MinecraftClient client,Vec3d start,Vector3f direction,double maxDistance){this.client=client;this.start=start;dx=direction.x;dy=direction.y;dz=direction.z;this.maxDistance=maxDistance;}
        void begin(BlockPos root,BlockState state,String renderer,List<Sprite> sprites){this.root=root;this.sprites=sprites;count=0;sprite=null;quadMetadata=null;layer="";source=new JsonObject();source.addProperty("root",root.toShortString());source.addProperty("state",state.toString());source.addProperty("renderer",renderer);source.addProperty("nativeRenderLayer",RenderLayers.getBlockLayer(state).toString());source.addProperty("actualRootOpacity",state.getOpacity(client.world,root));}
        @Override public void quad(MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){
            sprite=quad.getSprite();quadMetadata=new JsonObject();quadMetadata.addProperty("face",quad.getFace().getName());quadMetadata.addProperty("shade",quad.hasShade());quadMetadata.addProperty("tintIndex",quad.getColorIndex());quadMetadata.addProperty("hasTint",quad.hasColor());quadMetadata.add("brightness",JSON.toJsonTree(brightness));quadMetadata.add("rendererColor",JSON.toJsonTree(List.of(r,g,b)));quadMetadata.add("packedLights",JSON.toJsonTree(lights));quadMetadata.add("bakedVertexData",JSON.toJsonTree(quad.getVertexData()));
            quadMetadata.addProperty("provenOpaqueUsedUvAndMips",StaticArchitectureGeometry.opaqueQuad(quad));
            var unitCenter=matrix.getPositionMatrix().transformPosition(new Vector3f(.5f,.5f,.5f));
            quadMetadata.addProperty("sourceUnitOriginCell",BlockPos.ofFloored(root.getX()+unitCenter.x,root.getY()+unitCenter.y,root.getZ()+unitCenter.z).toShortString());
            VertexConsumer.super.quad(matrix,quad,brightness,r,g,b,lights,overlay,useColor);sprite=null;quadMetadata=null;
        }
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
            var point=vertices[count++];double[] values={x+root.getX(),y+root.getY(),z+root.getZ(),r,g,b,a,u,v,light,nx,ny,nz,overlay};System.arraycopy(values,0,point,0,14);
            if(count==4){quads++;intersect(0,1,2);intersect(2,3,0);count=0;}
        }
        private void intersect(int ai,int bi,int ci){
            double[] a=vertices[ai],b=vertices[bi],c=vertices[ci];double e1x=b[0]-a[0],e1y=b[1]-a[1],e1z=b[2]-a[2],e2x=c[0]-a[0],e2y=c[1]-a[1],e2z=c[2]-a[2];
            double px=dy*e2z-dz*e2y,py=dz*e2x-dx*e2z,pz=dx*e2y-dy*e2x,det=e1x*px+e1y*py+e1z*pz;
            if(det<=1e-10)return;double inverse=1/det,tx=start.x-a[0],ty=start.y-a[1],tz=start.z-a[2],u=(tx*px+ty*py+tz*pz)*inverse;if(u< -1e-7||u>1.0000001)return;
            double qx=ty*e1z-tz*e1y,qy=tz*e1x-tx*e1z,qz=tx*e1y-ty*e1x,v=(dx*qx+dy*qy+dz*qz)*inverse;if(v< -1e-7||u+v>1.0000001)return;
            double distance=(e2x*qx+e2y*qy+e2z*qz)*inverse;if(distance<.0001||distance>maxDistance)return;double w=1-u-v;
            double atlasU=a[7]*w+b[7]*u+c[7]*v,atlasV=a[8]*w+b[8]*u+c[8]*v;
            Sprite sampled=sprite;
            if(sampled==null)for(var candidate:sprites)if(atlasU>=candidate.getMinU()&&atlasU<=candidate.getMaxU()&&atlasV>=candidate.getMinV()&&atlasV<=candidate.getMaxV()){sampled=candidate;break;}
            JsonObject row=source.deepCopy();row.addProperty("distance",distance);row.addProperty("layer",layer);row.add("worldHit",JSON.toJsonTree(List.of(start.x+distance*dx,start.y+distance*dy,start.z+distance*dz)));row.add("atlasUv",JSON.toJsonTree(List.of(atlasU,atlasV)));row.add("triangle",JSON.toJsonTree(List.of(ai,bi,ci)));row.add("barycentric",JSON.toJsonTree(List.of(w,u,v)));
            row.add("actualRenderedVertices",JSON.toJsonTree(vertices));if(quadMetadata!=null)row.add("quad",quadMetadata.deepCopy());
            var surfaceSamples=new JsonArray();double centerX=0,centerY=0,centerZ=0;
            for(var vertex:vertices){centerX+=vertex[0]*.25;centerY+=vertex[1]*.25;centerZ+=vertex[2]*.25;}
            // BakedQuad.face is a cardinal lighting label. A rotated element's
            // actual face normal can be diagonal, so record its plane directly.
            var geometricNormal=new Vector3f((float)e1x,(float)e1y,(float)e1z).cross(new Vector3f((float)e2x,(float)e2y,(float)e2z)).normalize();
            if(geometricNormal.dot((float)a[10],(float)a[11],(float)a[12])<0)geometricNormal.negate();
            row.add("geometricNormal",JSON.toJsonTree(List.of(geometricNormal.x,geometricNormal.y,geometricNormal.z)));
            var centerCell=BlockPos.ofFloored(centerX+geometricNormal.x*.001,centerY+geometricNormal.y*.001,centerZ+geometricNormal.z*.001);
            var centerLight=new JsonObject();centerLight.addProperty("cell",centerCell.toShortString());centerLight.addProperty("sky",client.world.getLightLevel(net.minecraft.world.LightType.SKY,centerCell));centerLight.addProperty("block",client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,centerCell));centerLight.addProperty("state",client.world.getBlockState(centerCell).toString());row.add("physicalOutwardCenterLight",centerLight);
            for(var vertex:vertices){
                var cell=BlockPos.ofFloored(vertex[0]*.99+centerX*.01+vertex[10]*.001,vertex[1]*.99+centerY*.01+vertex[11]*.001,vertex[2]*.99+centerZ*.01+vertex[12]*.001);
                var sample=new JsonObject();sample.addProperty("cell",cell.toShortString());sample.addProperty("sky",client.world.getLightLevel(net.minecraft.world.LightType.SKY,cell));sample.addProperty("block",client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,cell));sample.addProperty("skyVisible",client.world.isSkyVisible(cell));surfaceSamples.add(sample);
            }
            row.add("physicalOutwardVertexLight",surfaceSamples);
            if(sampled!=null){
                var contents=sampled.getContents();row.addProperty("sprite",contents.getId().toString());row.add("spriteUv",JSON.toJsonTree(List.of(sampled.getMinU(),sampled.getMaxU(),sampled.getMinV(),sampled.getMaxV())));row.add("spriteSize",JSON.toJsonTree(List.of(contents.getWidth(),contents.getHeight())));
                double localU=(atlasU-sampled.getMinU())/(sampled.getMaxU()-sampled.getMinU()),localV=(atlasV-sampled.getMinV())/(sampled.getMaxV()-sampled.getMinV());row.add("spriteLocalUv",JSON.toJsonTree(List.of(localU,localV)));
                try{
                    var images=((dev.dreamwalker.bloodbornedw.composite.mixin.StaticArchitectureSpriteAccess)(Object)contents).staticArchitectureMipmapLevelsImages();JsonArray pixels=new JsonArray();
                    for(int level=0;level<images.length;level++){var image=images[level];int sx=Math.max(0,Math.min(image.getWidth()-1,(int)Math.floor(localU*Math.max(1,contents.getWidth()>>level)))),sy=Math.max(0,Math.min(image.getHeight()-1,(int)Math.floor(localV*Math.max(1,contents.getHeight()>>level))));int color=image.getColor(sx,sy);var pixel=new JsonObject();pixel.addProperty("level",level);pixel.add("xy",JSON.toJsonTree(List.of(sx,sy)));pixel.add("rgba",JSON.toJsonTree(List.of(color&255,(color>>8)&255,(color>>16)&255,(color>>>24))));pixels.add(pixel);}
                    row.add("nearestTextureTexels",pixels);if(!pixels.isEmpty()&&pixels.get(0).getAsJsonObject().getAsJsonArray("rgba").get(3).getAsInt()<26)row.addProperty("transparentAtBaseLevel",true);
                }catch(RuntimeException unavailable){row.addProperty("textureReadFailure",unavailable.toString());}
            }
            hits.add(row);hits.sort(Comparator.comparingDouble(h->h.get("distance").getAsDouble()));if(hits.size()>32)hits.remove(hits.size()-1);
        }
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}public VertexConsumer color(int r,int g,int b,int a){red=r;green=g;blue=b;alpha=a;return this;}public VertexConsumer texture(float u,float v){this.u=u;this.v=v;return this;}public VertexConsumer overlay(int u,int v){overlay=u|(v<<16);return this;}public VertexConsumer light(int u,int v){light=u|(v<<16);return this;}public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}public void next(){vertex((float)x,(float)y,(float)z,red/255f,green/255f,blue/255f,alpha/255f,u,v,overlay,light,nx,ny,nz);}public void fixedColor(int r,int g,int b,int a){color(r,g,b,a);}public void unfixColor(){}
    }
}
