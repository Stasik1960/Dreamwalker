package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;

import java.nio.file.Files;
import java.nio.file.Path;

/** One-shot diagnostic of the bounded model and neighbouring light at camera 9. */
final class CornerTargetModelDiagnostic {
    private CornerTargetModelDiagnostic() {}

    static void write(MinecraftClient client,BlockPos target,Path output)throws Exception {
        var world=client.world;
        var state=world.getBlockState(target);
        var model=client.getBlockRenderManager().getModel(state);
        JsonObject dump=new JsonObject();
        dump.addProperty("note","BakedModel.getQuads snapshot, not a capture of later dynamic rendering; an empty list does not prove the block has no emitted geometry.");
        dump.addProperty("blockId",Registries.BLOCK.getId(state.getBlock()).toString());
        dump.addProperty("state",state.toString());
        dump.addProperty("renderType",state.getRenderType().toString());
        dump.addProperty("modelClass",model.getClass().getName());
        dump.addProperty("ambientOcclusion",model.useAmbientOcclusion());
        JsonArray origin=new JsonArray();origin.add(target.getX());origin.add(target.getY());origin.add(target.getZ());dump.add("target",origin);
        JsonArray faces=new JsonArray();
        // Query each side and the unculled list with the same seed, as independent requests.
        for(int side=0;side<=Direction.values().length;side++){
            Direction face=side==Direction.values().length?null:Direction.values()[side];
            var quads=model.getQuads(state,face,Random.create(42));
            JsonObject group=new JsonObject();group.addProperty("queryFace",face==null?"unculled":face.getName());group.addProperty("count",quads.size());
            JsonArray records=new JsonArray();for(BakedQuad quad:quads)records.add(quad(quad));group.add("quads",records);faces.add(group);
        }
        dump.add("faces",faces);
        JsonArray neighbors=new JsonArray();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            BlockPos pos=target.add(dx,dy,dz);var neighbor=world.getBlockState(pos);JsonObject cell=new JsonObject();
            cell.addProperty("x",pos.getX());cell.addProperty("y",pos.getY());cell.addProperty("z",pos.getZ());
            cell.addProperty("state",neighbor.toString());cell.addProperty("blockId",Registries.BLOCK.getId(neighbor.getBlock()).toString());
            cell.addProperty("opacity",neighbor.getOpacity(world,pos));cell.addProperty("sky",world.getLightLevel(LightType.SKY,pos));cell.addProperty("block",world.getLightLevel(LightType.BLOCK,pos));neighbors.add(cell);
        }
        dump.add("neighbors",neighbors);
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(dump));
    }

    private static JsonObject quad(BakedQuad quad){
        JsonObject record=new JsonObject();record.addProperty("face",quad.getFace().getName());record.addProperty("shade",quad.hasShade());
        record.addProperty("tinted",quad.hasColor());record.addProperty("tintIndex",quad.getColorIndex());
        record.addProperty("sprite",quad.getSprite().getContents().getId().toString());
        int[] data=quad.getVertexData();record.addProperty("stride",data.length/4);record.addProperty("rawLength",data.length);
        JsonArray vertices=new JsonArray();
        if(data.length%4==0&&data.length/4>=8){
            int stride=data.length/4;float[][] points=new float[4][3];
            for(int i=0;i<4;i++){
                int at=i*stride;JsonObject vertex=new JsonObject();
                for(int axis=0;axis<3;axis++){float value=Float.intBitsToFloat(data[at+axis]);points[i][axis]=value;number(vertex,new String[]{"x","y","z"}[axis],value);}
                vertex.addProperty("colorRaw",String.format("%08x",data[at+3]));number(vertex,"u",Float.intBitsToFloat(data[at+4]));number(vertex,"v",Float.intBitsToFloat(data[at+5]));
                vertex.addProperty("lightRaw",String.format("%08x",data[at+6]));vertex.addProperty("normalRaw",String.format("%08x",data[at+7]));vertices.add(vertex);
            }
            float ax=points[1][0]-points[0][0],ay=points[1][1]-points[0][1],az=points[1][2]-points[0][2];
            float bx=points[3][0]-points[0][0],by=points[3][1]-points[0][1],bz=points[3][2]-points[0][2];
            JsonObject normal=new JsonObject();number(normal,"x",ay*bz-az*by);number(normal,"y",az*bx-ax*bz);number(normal,"z",ax*by-ay*bx);record.add("crossProductNormal",normal);
        }
        record.add("vertices",vertices);return record;
    }

    private static void number(JsonObject object,String key,float value){
        if(Float.isFinite(value))object.addProperty(key,value);else object.addProperty(key,Float.toString(value));
    }
}
