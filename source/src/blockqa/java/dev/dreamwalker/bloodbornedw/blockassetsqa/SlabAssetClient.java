package dev.dreamwalker.bloodbornedw.blockassetsqa;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
public final class SlabAssetClient implements ClientModInitializer {
 private boolean done;
 private static void need(boolean b,String message){if(!b)throw new IllegalStateException(message);}
 @Override public void onInitializeClient(){for(var block:SlabAssetFactory.DECORATED)net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block,net.minecraft.client.render.RenderLayer.getCutout());ClientTickEvents.END_CLIENT_TICK.register(c->{if(!done&&c.currentScreen!=null&&c.getOverlay()==null){done=true;run(c);}});}
 private void run(MinecraftClient c){
  JsonObject report=new JsonObject();int states=0,faces=0,vertices=0;Set<String> sprites=new TreeSet<>();
  try{
   for(var block:SlabAssetFactory.BLOCKS)for(var type:SlabType.values())for(var facing:Direction.Type.HORIZONTAL){
    var state=block.getDefaultState().with(SlabBlock.TYPE,type).with(Properties.HORIZONTAL_FACING,facing);var model=c.getBlockRenderManager().getModel(state);String id=Registries.BLOCK.getId(block).toString();
    List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,Random.create(0)));for(var side:Direction.values())quads.addAll(model.getQuads(state,side,Random.create(0)));
    boolean decorated=SlabAssetFactory.DECORATED.contains(block);need(decorated?quads.size()>6:quads.size()==6,id+" face count "+type+"/"+facing+": "+quads.size());double low=type==SlabType.TOP?.5:0,high=type==SlabType.BOTTOM?.5:1;Set<Direction> baseFaces=EnumSet.noneOf(Direction.class);
    for(var q:quads){
     var sprite=q.getSprite();String texture=sprite.getContents().getId().toString();need(!texture.contains("missingno"),id+" missing texture");sprites.add(texture);int[] data=q.getVertexData();int stride=data.length/4;
     boolean base=true;
     for(int v=0;v<4;v++){
      double x=Float.intBitsToFloat(data[v*stride]),y=Float.intBitsToFloat(data[v*stride+1]),z=Float.intBitsToFloat(data[v*stride+2]);boolean inside=x>=-1e-6&&x<=1.000001&&z>=-1e-6&&z<=1.000001&&y>=low-1e-6&&y<=high+1e-6;base&=inside;need(decorated?x>=-3&&x<=4&&z>=-3&&z<=4&&y>=-2&&y<=4:inside,id+" model bounds");
      double U=Float.intBitsToFloat(data[v*stride+4]),V=Float.intBitsToFloat(data[v*stride+5]);need(U>=sprite.getMinU()-1e-6&&U<=sprite.getMaxU()+1e-6&&V>=sprite.getMinV()-1e-6&&V<=sprite.getMaxV()+1e-6,id+" source atlas UV");vertices++;
     }if(base)baseFaces.add(q.getFace());faces++;
    }need(baseFaces.size()==6,id+" complete opaque slab base "+type+"/"+facing);if(decorated)need(net.minecraft.client.render.RenderLayers.getBlockLayer(state)==net.minecraft.client.render.RenderLayer.getCutout(),id+" cutout stone decoration");states++;
   }
   report.addProperty("status","PASS");report.addProperty("states",states);report.addProperty("faces",faces);report.addProperty("vertices",vertices);report.add("sprites",new Gson().toJsonTree(sprites));
  }catch(Throwable failure){report.addProperty("status","FAIL");report.addProperty("error",failure.toString());failure.printStackTrace();}
  try{var out=Path.of(System.getProperty("dw.blockqa.output"));Files.createDirectories(out);Files.writeString(out.resolve("client-assets.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){e.printStackTrace();}
  c.scheduleStop();
 }
}
