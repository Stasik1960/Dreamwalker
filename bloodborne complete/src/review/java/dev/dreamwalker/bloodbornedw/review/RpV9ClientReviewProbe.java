package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import dev.dreamwalker.bloodbornerp.client.CatalogObjectRenderer;
import dev.dreamwalker.bloodbornerp.object.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.util.RenderUtils;

/** QA-only observation/evaluation of actual loaded entity tracker states and GeckoLib renderer models. */
public final class RpV9ClientReviewProbe {
    private static final Set<String> REQUIRED=Set.of("cage_obj_1","cage_obj_2","cage_obj_3","chandelier_small","stairs","ladder","npc_window","wood_gate");
    private RpV9ClientReviewProbe() {}
    public static JsonObject verify(MinecraftClient client)throws Exception{
        if(client.world==null)throw new IllegalStateException("RP review requires an actual client world");
        JsonObject out=new JsonObject();out.addProperty("schema","dreamwalker-rp-v9-actual-client-model-v1");out.addProperty("manualVisualAcceptance","NOT_RUN");out.addProperty("worldEntityStateEdited",false);
        List<RpObjectEntity> objects=new ArrayList<>();for(var entity:client.world.getEntities())if(entity instanceof RpObjectEntity rp&&REQUIRED.contains(rp.assetId()))objects.add(rp);
        Set<String> seen=new HashSet<>();JsonArray rows=new JsonArray();List<RpObjectEntity> firstCages=new ArrayList<>();
        for(RpObjectEntity object:objects){
            seen.add(object.assetId());if(object.assetId().equals("cage_obj_1"))firstCages.add(object);
            CatalogObjectRenderer renderer=renderer(client,object);var model=renderer.getGeoModel();Identifier location=model.getModelResource(object);BakedGeoModel baked=model.getBakedModel(location);
            model.setCustomAnimations(object,object.getId(),new AnimationState<>(object,0,0,0,false));
            JsonObject row=new JsonObject();row.addProperty("assetId",object.assetId());row.addProperty("uuid",object.getUuidAsString());row.add("position",vec(object.getPos()));row.addProperty("yaw",object.getYaw());row.addProperty("objectScale",object.objectScale());
            row.addProperty("renderer",renderer.getClass().getName());row.addProperty("model",location.toString());row.addProperty("texture",model.getTextureResource(object).toString());row.addProperty("animation",model.getAnimationResource(object).toString());
            row.add("loadedResources",resources(client,object,location,model.getTextureResource(object),model.getAnimationResource(object)));
            Counts counts=new Counts();MatrixStack matrix=new MatrixStack();matrix.translate(object.getX(),object.getY(),object.getZ());matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(object.getYaw()-90));matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(object.getPitch()));float scale=object.asset().scale()*object.objectScale();matrix.scale(scale,scale,scale);
            for(GeoBone bone:baked.topLevelBones())mesh(matrix,bone,counts,false);
            if(counts.quads==0)throw new IllegalStateException("Actual RP model has no visible baked quads:"+object.assetId());
            row.addProperty("visibleGeckoQuads",counts.quads);row.addProperty("visibleVertices",counts.vertices);row.add("actualEvaluatedMeshWorldBounds",box(counts.bounds()));row.add("serverDerivedVisualBounds",box(object.visualBounds()));
            if(object.hasCustomPhysicalGeometry()){
                Box expected=object.visualBounds(),actual=counts.bounds();double eps=.002;
                if(actual.minX<expected.minX-eps||actual.minY<expected.minY-eps||actual.minZ<expected.minZ-eps||actual.maxX>expected.maxX+eps||actual.maxY>expected.maxY+eps||actual.maxZ>expected.maxZ+eps)throw new IllegalStateException("Actual mesh outside RP culling bounds:"+object.assetId()+" actual="+actual+" bounds="+expected);
                row.addProperty("actualMeshEnclosedByCullingBounds",true);row.addProperty("physicalBoxCount",object.activePhysicalBoxes().size());row.addProperty("climbBoxCount",object.climbingBoxes().size());
            }
            if(object.supportsDogVisibility()){GeoBone dog=model.getBone(object.dogRootBone()).orElseThrow();row.addProperty("dogsVisibleTracker",object.dogsVisible());row.addProperty("actualDogBoneHidden",dog.isHidden());row.addProperty("actualDogChildrenHidden",dog.isHidingChildren());if(dog.isHidden()==object.dogsVisible()||dog.isHidingChildren()==object.dogsVisible())throw new IllegalStateException("Actual dog render state mismatch");}
            if(object.assetId().equals("ladder")){GeoBone bottom=model.getBone("bottom").orElseThrow();row.addProperty("openTracker",object.isOpen());row.add("actualBottomOffsetPixels",new Gson().toJsonTree(new double[]{bottom.getPosX(),bottom.getPosY(),bottom.getPosZ()}));if(Math.abs(bottom.getPosY()-object.ladderOffsetY())>1e-5||Math.abs(bottom.getPosZ()-object.ladderOffsetZ())>1e-5)throw new IllegalStateException("Actual ladder bone differs from tracked phase");}
            if(object.assetId().equals("wood_gate")){
                row.addProperty("remainingTicksTracker",object.woodGatePulseTicks());JsonArray bones=new JsonArray();
                for(String name:AuthoredObjectMotion.gateBones()){GeoBone bone=model.getBone(name).orElseThrow();JsonObject b=new JsonObject();b.addProperty("name",name);b.add("rotationRadians",new Gson().toJsonTree(new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ()}));b.add("offsetPixels",new Gson().toJsonTree(new float[]{bone.getPosX(),bone.getPosY(),bone.getPosZ()}));bones.add(b);
                    if(!object.woodGatePulseActive()){var initial=bone.getInitialSnapshot();if(Math.abs(bone.getRotX()-initial.getRotX())>1e-5||Math.abs(bone.getRotY()-initial.getRotY())>1e-5||Math.abs(bone.getRotZ()-initial.getRotZ())>1e-5||Math.abs(bone.getPosX())>1e-5||Math.abs(bone.getPosY())>1e-5||Math.abs(bone.getPosZ())>1e-5)throw new IllegalStateException("Idle loaded gate has background bone motion:"+name);}
                }row.add("actualGateBones",bones);
            }rows.add(row);
        }
        if(!seen.containsAll(REQUIRED)){Set<String> missing=new TreeSet<>(REQUIRED);missing.removeAll(seen);throw new IllegalStateException("Required RP fixtures not actually client-loaded:"+missing);}
        RpObjectEntity hidden=firstCages.stream().filter(c->!c.dogsVisible()).findFirst().orElseThrow(()->new IllegalStateException("Actual hidden cage not loaded"));RpObjectEntity visible=firstCages.stream().filter(RpObjectEntity::dogsVisible).findFirst().orElseThrow(()->new IllegalStateException("Actual visible same-type cage not loaded"));
        JsonArray shared=new JsonArray();Object sharedModel=null;
        for(RpObjectEntity object:List.of(hidden,visible,hidden)){
            var model=renderer(client,object).getGeoModel();BakedGeoModel baked=model.getBakedModel(model.getModelResource(object));if(sharedModel==null)sharedModel=baked;else if(sharedModel!=baked)throw new IllegalStateException("Dog test must exercise the real shared baked model");
            model.setCustomAnimations(object,object.getId(),new AnimationState<>(object,0,0,0,false));GeoBone root=model.getBone(object.dogRootBone()).orElseThrow();
            if(root.isHidden()==object.dogsVisible()||root.isHidingChildren()==object.dogsVisible())throw new IllegalStateException("Shared baked dog state leaked between instances");JsonObject step=new JsonObject();step.addProperty("uuid",object.getUuidAsString());step.addProperty("trackerDogsVisible",object.dogsVisible());step.addProperty("hidden",root.isHidden());step.addProperty("childrenHidden",root.isHidingChildren());shared.add(step);
        }
        out.add("loadedObjects",rows);out.add("sameTypeHiddenVisibleHiddenEvaluation",shared);out.addProperty("status","PASS_ACTUAL_LOADED_RP_MODEL_TRACKER_POSES");out.addProperty("scope","Actual client renderer/Gecko baked vertices and server-loaded tracker state evaluated through the existing custom-animation hook. No viewport, human action or visual acceptance is inferred.");return out;
    }
    private static CatalogObjectRenderer renderer(MinecraftClient client,RpObjectEntity object){var value=client.getEntityRenderDispatcher().getRenderer(object);if(!(value instanceof CatalogObjectRenderer renderer))throw new IllegalStateException("Unexpected actual RP renderer:"+value);return renderer;}
    private static JsonArray resources(MinecraftClient client,RpObjectEntity object,Identifier... locations)throws Exception{
        JsonArray result=new JsonArray();String[] original={object.asset().model(),object.asset().texture(),object.asset().animation()};
        for(int i=0;i<locations.length;i++){Identifier expected=new Identifier("bloodborne_rp",original[i]);if(!locations[i].equals(expected))throw new IllegalStateException("RP renderer fallback:"+object.assetId()+" "+locations[i]);byte[] actual;try(var input=client.getResourceManager().getResource(locations[i]).orElseThrow().getInputStream()){actual=input.readAllBytes();}byte[] supplied;try(var input=RpV9ClientReviewProbe.class.getResourceAsStream("/assets/bloodborne_rp/"+original[i])){if(input==null)throw new IllegalStateException("Bundled source RP resource missing");supplied=input.readAllBytes();}if(!Arrays.equals(actual,supplied))throw new IllegalStateException("RP resource pack changed original bytes:"+locations[i]);JsonObject row=new JsonObject();row.addProperty("id",locations[i].toString());row.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)));row.addProperty("byteExactBundledOriginal",true);result.add(row);}
        return result;
    }
    private static void mesh(MatrixStack stack,GeoBone bone,Counts counts,boolean parentHidden){
        stack.push();RenderUtils.prepMatrixForBone(stack,bone);
        if(!parentHidden&&!bone.isHidden())for(GeoCube cube:bone.getCubes()){stack.push();RenderUtils.translateToPivotPoint(stack,cube);RenderUtils.rotateMatrixAroundCube(stack,cube);RenderUtils.translateAwayFromPivotPoint(stack,cube);for(GeoQuad quad:cube.quads())if(quad!=null){counts.quads++;for(GeoVertex vertex:quad.vertices()){Vector4f v=stack.peek().getPositionMatrix().transform(new Vector4f(vertex.position().x(),vertex.position().y(),vertex.position().z(),1));counts.add(v.x,v.y,v.z);}}stack.pop();}
        for(GeoBone child:bone.getChildBones())mesh(stack,child,counts,parentHidden||bone.isHidingChildren());stack.pop();
    }
    private static final class Counts {int quads,vertices;double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;void add(double x,double y,double z){vertices++;minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}Box bounds(){return new Box(minX,minY,minZ,maxX,maxY,maxZ);}}
    private static JsonElement vec(Vec3d v){return new Gson().toJsonTree(new double[]{v.x,v.y,v.z});}
    private static JsonElement box(Box b){return new Gson().toJsonTree(new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ});}
}
