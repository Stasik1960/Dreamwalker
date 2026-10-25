package dev.dreamwalker.bloodbornedw.composite;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

public final class BlockRepairClientChecks implements ClientModInitializer {
    private boolean ran;
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(client->{if(!ran&&Boolean.getBoolean("dw.blockqa.assetCheck")&&client.currentScreen!=null&&client.getOverlay()==null){ran=true;check(client);}});}
    private void need(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    private void check(MinecraftClient client){
        JsonObject report=new JsonObject();int states=0,quads=0,vertices=0;Set<String> sprites=new TreeSet<>();
        try{
            for(var block:MaterialSlabs.blocks())for(var type:SlabType.values())for(var facing:Direction.Type.HORIZONTAL){
                var state=block.getDefaultState().with(SlabBlock.TYPE,type).with(MaterialSlabs.FACING,facing);var model=client.getBlockRenderManager().getModel(state);String id=Registries.BLOCK.getId(block).toString();
                List<BakedQuad> faces=new ArrayList<>(model.getQuads(state,null,Random.create(0)));for(var side:Direction.values())faces.addAll(model.getQuads(state,side,Random.create(0)));
                need(faces.size()==6,id+" must bake all six slab faces: "+type+"/"+facing+" "+faces.size());
                double low=type==SlabType.TOP?.5:0,high=type==SlabType.BOTTOM?.5:1;
                for(var q:faces){
                    String texture=q.getSprite().getContents().getId().toString();need(!texture.contains("missingno"),id+" missing sprite");sprites.add(texture);
                    int[] data=q.getVertexData();int stride=data.length/4;
                    for(int vertex=0;vertex<4;vertex++){
                        double x=Float.intBitsToFloat(data[vertex*stride]),y=Float.intBitsToFloat(data[vertex*stride+1]),z=Float.intBitsToFloat(data[vertex*stride+2]);
                        need(x>=-1e-6&&x<=1.000001&&z>=-1e-6&&z<=1.000001&&y>=low-1e-6&&y<=high+1e-6,id+" invalid baked slab bounds");
                        double u=Float.intBitsToFloat(data[vertex*stride+4]),v=Float.intBitsToFloat(data[vertex*stride+5]);var s=q.getSprite();
                        need(u>=s.getMinU()-1e-6&&u<=s.getMaxU()+1e-6&&v>=s.getMinV()-1e-6&&v<=s.getMaxV()+1e-6,id+" UV escapes its source sprite");vertices++;
                    }
                    quads++;
                }
                states++;
            }
            report.addProperty("status","PASS");report.addProperty("states",states);report.addProperty("faces",quads);report.addProperty("vertices",vertices);report.add("sprites",new Gson().toJsonTree(sprites));
        }catch(Throwable failure){report.addProperty("status","FAIL");report.addProperty("error",failure.toString());failure.printStackTrace();}
        try{var out=Path.of(System.getProperty("dw.blockqa.output"));Files.createDirectories(out);Files.writeString(out.resolve("client-assets.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}
        client.scheduleStop();
    }
}
