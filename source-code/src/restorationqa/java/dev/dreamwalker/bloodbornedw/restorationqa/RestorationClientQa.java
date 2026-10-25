package dev.dreamwalker.bloodbornedw.restorationqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
/** Visual checks on a disposable copy of the repaired user world. */
public final class RestorationClientQa implements ClientModInitializer {
 private int ticks,stage;private Path out;private final JsonArray observations=new JsonArray();private volatile boolean pulse;private boolean packet;
 private static final double[][] VIEWS={{5.5,25,-1065,180,0},{15,25,-1074.5,90,0},{-25.05,-56.25,-916.85,180,20},{-22.5,-56.5,-917.5,150,17},{-27.5,-56.5,-917.5,210,17}};
 @Override public void onInitializeClient(){String d=System.getProperty("dw.restore.qa.output");if(d==null)return;out=Path.of(d);ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.world==null||c.player==null||c.getServer()==null)return;try{tick(c);}catch(Throwable e){e.printStackTrace();write("FAIL",e.toString());c.scheduleStop();}});}
 private void tick(MinecraftClient c)throws Exception{
  if(++ticks==1){var v=VIEWS[stage];move(c,v);c.options.hudHidden=true;}
  if(stage==0&&ticks==90)c.getServer().execute(()->{var w=c.getServer().getOverworld();var r=new JsonObject();var at=new BlockPos(5,22,-1075);r.addProperty("wholePostReopened",w.getBlockState(at).isOf(CompositeArchitecture.kindBlock("source_street_lamp_post"))&&w.getBlockEntity(at) instanceof CompositeBlockEntity be&&be.resident()!=null);pulse=LanternMotion.pulse(w,new BlockPos(4,28,-1075));r.addProperty("lanternPulse",pulse);synchronized(observations){observations.add(r);}});
  if(stage==0&&ticks==94){var f=Class.forName("dev.dreamwalker.bloodbornedw.composite.LanternMotionClient").getDeclaredField("ACTIVE");f.setAccessible(true);packet=!((Map<?,?>)f.get(null)).isEmpty();ScreenshotRecorder.saveScreenshot(c.runDirectory,"restoration-lantern-sway.png",c.getFramebuffer(),t->{});}
  if(ticks<150)return;
  ScreenshotRecorder.saveScreenshot(c.runDirectory,"restoration-view-"+stage+".png",c.getFramebuffer(),t->{});
  var r=new JsonObject();r.addProperty("view",stage);r.addProperty("nativeWindowVisual",NativeVisuals.index(c.world,new BlockPos(-25,-56,-920)));synchronized(observations){observations.add(r);}
  if(++stage==VIEWS.length){write(packet&&pulse?"PASS":"FAIL","Repaired map reopened; whole post, lantern packet and five visual angles captured");c.scheduleStop();}else ticks=0;
 }
 private void move(MinecraftClient c,double[] v){c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());if(p!=null){p.changeGameMode(GameMode.CREATIVE);p.clearStatusEffects();p.setHealth(p.getMaxHealth());p.getHungerManager().setFoodLevel(20);p.getAbilities().flying=true;p.sendAbilitiesUpdate();var w=c.getServer().getOverworld();w.setTimeOfDay(6000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,c.getServer());p.teleport(w,v[0],v[1],v[2],(float)v[3],(float)v[4]);}});}
 private void write(String status,String text){try{var n=new JsonObject();n.addProperty("status",status);n.addProperty("message",text);n.addProperty("swayPacketReceived",packet);synchronized(observations){n.add("observations",observations);}Files.writeString(out.resolve("client-probe.json"),new GsonBuilder().setPrettyPrinting().create().toJson(n));}catch(Exception e){throw new RuntimeException(e);}}
}
