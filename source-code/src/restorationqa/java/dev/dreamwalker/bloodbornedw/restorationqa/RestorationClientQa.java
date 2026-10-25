package dev.dreamwalker.bloodbornedw.restorationqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
public final class RestorationClientQa implements ClientModInitializer {
 private int ticks,stage;private Path out;private final JsonArray observations=new JsonArray();
 @Override public void onInitializeClient(){String d=System.getProperty("dw.restore.qa.output");if(d==null)return;out=Path.of(d);ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.world==null||c.player==null||c.getServer()==null)return;try{tick(c);}catch(Throwable e){e.printStackTrace();write("FAIL",e.toString());c.scheduleStop();}});}
 private void tick(MinecraftClient c)throws Exception{
  if(++ticks==1){if(stage==0)move(c,-135,52,-222,180,12);else move(c,-25.05,-56.25,-916.85,180,20);}
  if(ticks<180)return;
  if(stage==0){ScreenshotRecorder.saveScreenshot(c.runDirectory,"restoration-post-before.png",c.getFramebuffer(),t->{});var b=CompositeArchitecture.kindBlock("source_assembly_orange_stained_glass_pane");for(int v:new int[]{2,14,16,21,22}){var s=b.getDefaultState().with(ImportedCompactBlock.COMPACT,true).with(CompositeRootBlock.VARIANT,v%16);if(s.contains(ImportedArtworkBlock.ART_BANK))s=s.with(ImportedArtworkBlock.ART_BANK,v/16);var model=c.getBlockRenderManager().getModel(s);var r=new JsonObject();r.addProperty("state",s.toString());int n=0;double min=1e9,max=-1e9;for(int face=-1;face<6;face++)for(var q:model.getQuads(s,face<0?null:Direction.values()[face],Random.create(0))){n++;var a=q.getVertexData();for(int i=0;i<4;i++){double x=Float.intBitsToFloat(a[i*8]);min=Math.min(min,x);max=Math.max(max,x);}}r.addProperty("quads",n);r.addProperty("minX",min);r.addProperty("maxX",max);observations.add(r);}stage++;ticks=0;}
  else {ScreenshotRecorder.saveScreenshot(c.runDirectory,"restoration-window-before.png",c.getFramebuffer(),t->{});write("EXPORTED","raw source states and screenshots");c.scheduleStop();}
 }
 private void move(MinecraftClient c,double x,double y,double z,float yaw,float pitch){c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());if(p!=null){p.changeGameMode(GameMode.SPECTATOR);p.teleport(c.getServer().getOverworld(),x,y,z,yaw,pitch);}});}
 private void write(String status,String text){try{var n=new JsonObject();n.addProperty("status",status);n.addProperty("message",text);n.add("models",observations);Files.writeString(out.resolve("client-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(n));}catch(Exception e){throw new RuntimeException(e);}}
}
