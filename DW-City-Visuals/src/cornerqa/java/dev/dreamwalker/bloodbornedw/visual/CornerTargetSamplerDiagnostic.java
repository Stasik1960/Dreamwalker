package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, QA-only sample trace, written after native rendering becomes idle. */
public final class CornerTargetSamplerDiagnostic {
    private static final int LIMIT=256;
    private static final Map<String,JsonObject> RECORDS=new LinkedHashMap<>();
    private static int duplicates,dropped,neighborRecords;
    private CornerTargetSamplerDiagnostic() {}

    static synchronized void clear(){RECORDS.clear();duplicates=0;dropped=0;neighborRecords=0;}

    public static void capture(CornerSurfaceLight sampler,BakedQuad quad,StaticLightingViewAccess view,int[] nativeLight,boolean sampled,boolean qualifiedChild){
        if(view==null||view.dw$root()==null)return;
        BlockPos root=view.dw$root();
        if(root.getX()<259||root.getX()>261||root.getY()<55||root.getY()>57||root.getZ()<-922||root.getZ()>-920)return;
        synchronized(CornerTargetSamplerDiagnostic.class){
        int[] data=quad.getVertexData();if(data==null||data.length%4!=0||data.length/4<8)return;
        int stride=data.length/4;StringBuilder key=new StringBuilder(root.toShortString()).append('/').append(quad.getFace()).append('/').append(qualifiedChild);
        for(int i=0;i<4;i++){
            int at=i*stride;for(int j=0;j<3;j++)key.append('/').append(data[at+j]);
            Vector3f position=new Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));
            view.dw$transform().transformPosition(position);key.append('/').append(Float.floatToRawIntBits(position.x)).append('/').append(Float.floatToRawIntBits(position.y)).append('/').append(Float.floatToRawIntBits(position.z));
        }
        String id=key.toString();if(RECORDS.containsKey(id)){duplicates++;return;}
        boolean target=root.getX()==260&&root.getY()==56&&root.getZ()==-921;
        if(RECORDS.size()>=LIMIT||(!target&&neighborRecords>=LIMIT/2)){dropped++;return;}
        if(!target)neighborRecords++;
        JsonObject row=new JsonObject();row.addProperty("root",root.toShortString());row.addProperty("face",quad.getFace().getName());row.addProperty("shade",quad.hasShade());row.addProperty("sampled",sampled);row.addProperty("qualifiedChild",qualifiedChild);row.addProperty("mixedCornerMask",sampler.mixedCornerMask());
        row.addProperty("sprite",quad.getSprite().getContents().getId().toString());row.addProperty("correctedStage",CornerLightingQa.corrected());row.addProperty("nightStage",CornerLightingQa.stageNight());
        JsonArray input=new JsonArray();if(nativeLight!=null)for(int light:nativeLight)input.add(String.format("%08x",light));row.add("nativeLight",input);
        if(sampled){JsonArray output=new JsonArray();for(int light:sampler.lights())output.add(String.format("%08x",light));row.add("sampledLight",output);JsonArray brightness=new JsonArray();for(float value:sampler.brightness())brightness.add(value);row.add("sampledBrightness",brightness);}
        Vector3f[] points=new Vector3f[4];JsonArray local=new JsonArray(),worldVertices=new JsonArray();
        for(int i=0;i<4;i++){
            int at=i*stride;points[i]=new Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));
            if(!points[i].isFinite()){row.addProperty("error","nonfinite local vertex");RECORDS.put(id,row);return;}
            local.add(vector(points[i]));Vector3f world=new Vector3f(points[i]);view.dw$transform().transformPosition(world);world.add(root.getX(),root.getY(),root.getZ());worldVertices.add(vector(world));
        }
        row.add("localVertices",local);row.add("worldVertices",worldVertices);
        Vector3f edge1=new Vector3f(points[1]).sub(points[0]),edge2=new Vector3f(points[3]).sub(points[0]);Vector3f normal=new Vector3f(edge1).cross(edge2);Direction face=quad.getFace();
        if(normal.x*face.getOffsetX()+normal.y*face.getOffsetY()+normal.z*face.getOffsetZ()<0)normal.negate();
        row.add("localNormal",vector(normal));row.add("localEdge1",vector(edge1));row.add("localEdge2",vector(edge2));
        view.dw$normals().transform(normal);view.dw$transform().transformDirection(edge1);view.dw$transform().transformDirection(edge2);
        row.add("worldEdge1",vector(edge1));row.add("worldEdge2",vector(edge2));
        if(!normal.isFinite()||normal.lengthSquared()<1e-10f){row.addProperty("error","nonfinite or degenerate transformed normal");RECORDS.put(id,row);return;}
        normal.normalize();row.add("worldNormal",vector(normal));
        Vector3f center=new Vector3f();for(Vector3f point:points)center.add(point);center.mul(.25f);view.dw$transform().transformPosition(center);
        Vector3f probe=new Vector3f(center).add(normal.x*.001f,normal.y*.001f,normal.z*.001f);row.add("centerProbe",probe(view,root,probe));
        JsonArray corners=new JsonArray();for(Vector3f point:points){probe.set(point);view.dw$transform().transformPosition(probe);probe.lerp(center,.01f).add(normal.x*.001f,normal.y*.001f,normal.z*.001f);corners.add(probe(view,root,probe));}row.add("cornerProbes",corners);
        RECORDS.put(id,row);
        }
    }

    static synchronized void write(Path path)throws Exception{
        JsonObject dump=new JsonObject();dump.addProperty("note","Observed sample calls in a 3x3x3 root region, capped unique position/face geometries, captured after native idle. Multiple materials with same geometry collapse.");
        dump.addProperty("unique",RECORDS.size());dump.addProperty("neighborRecords",neighborRecords);dump.addProperty("targetRecords",RECORDS.size()-neighborRecords);dump.addProperty("duplicates",duplicates);dump.addProperty("dropped",dropped);dump.addProperty("maxRecords",LIMIT);dump.addProperty("correctedStage",CornerLightingQa.corrected());dump.addProperty("nightStage",CornerLightingQa.stageNight());
        JsonArray records=new JsonArray();for(JsonObject row:RECORDS.values())records.add(row);dump.add("records",records);Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(dump));
    }

    private static JsonArray vector(Vector3f p){JsonArray v=new JsonArray();for(float x:new float[]{p.x,p.y,p.z}){if(Float.isFinite(x))v.add(x);else v.add(Float.toString(x));}return v;}
    private static JsonObject probe(StaticLightingViewAccess view,BlockPos root,Vector3f point){
        JsonObject result=new JsonObject();Vector3f absolute=new Vector3f(point).add(root.getX(),root.getY(),root.getZ());result.add("position",vector(absolute));
        if(!absolute.isFinite()){result.addProperty("error","nonfinite probe");return result;}
        BlockPos cell=BlockPos.ofFloored(absolute.x,absolute.y,absolute.z);var world=view.dw$delegate();var state=world.getBlockState(cell);
        result.addProperty("cell",cell.toShortString());result.addProperty("state",state.toString());result.addProperty("opacity",state.getOpacity(world,cell));result.addProperty("sky",world.getLightLevel(LightType.SKY,cell));result.addProperty("block",world.getLightLevel(LightType.BLOCK,cell));return result;
    }
}
