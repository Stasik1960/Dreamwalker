package dev.dreamwalker.capture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Native game framebuffer capture in a disposable single-player save only. */
public final class AutumnCapture implements ClientModInitializer {
 private static final Logger LOG=LoggerFactory.getLogger("AutumnCapture");
 private static final String[] STYLES={"01-pale-gold","02-classic-amber","03-copper-evening"};
 private static final String[] VIEWS={"overview","street"};
 private static final String[] TELEPORTS={"tp @s -400 160 20 150 25","tp @s -577 65 -302 0 -7"};
 private final long started=System.nanoTime();
 private long readyAt;
 private int style,view,frames;
 private boolean initialized,shotRequested,shotSaved,stopping;
 private CompletableFuture<Void> reload;

 public void onInitializeClient(){
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  WorldRenderEvents.END.register(context->{
   if(!shotRequested)return;
   shotRequested=false;
   MinecraftClient client=MinecraftClient.getInstance();
   String name=STYLES[style]+"-"+VIEWS[view]+".png";
   ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{
    LOG.info("AUTUMN_CAPTURE_SAVED {} {}",name,message.getString());
    client.execute(()->shotSaved=true);
   });
  });
 }
 private void tick(MinecraftClient client){
  if(stopping)return;
  if(System.nanoTime()-started>20L*60*1_000_000_000L){
   LOG.error("AUTUMN_CAPTURE_TIMEOUT");stopping=true;client.scheduleStop();return;
  }
  if(client.player==null||client.world==null)return;
  if(!initialized){
   initialized=true;
   client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
   client.options.setPerspective(Perspective.FIRST_PERSON);client.setScreen(null);
   client.player.networkHandler.sendChatCommand("gamemode spectator");
   client.player.networkHandler.sendChatCommand("time set 1000");
   client.player.networkHandler.sendChatCommand("gamerule doDaylightCycle false");
   client.player.networkHandler.sendChatCommand("weather clear");
   selectStyle(client);return;
  }
  client.setScreen(null);
  if(reload!=null){
   if(!reload.isDone())return;
   if(reload.isCompletedExceptionally()){
    LOG.error("AUTUMN_CAPTURE_RELOAD_FAILED");stopping=true;client.scheduleStop();return;
   }
   reload=null;move(client);return;
  }
  if(shotSaved){
   shotSaved=false;view++;
   if(view<VIEWS.length){move(client);return;}
   view=0;style++;
   if(style<STYLES.length){selectStyle(client);return;}
   LOG.info("AUTUMN_CAPTURE_COMPLETE 6 screenshots");
   stopping=true;client.scheduleStop();return;
  }
  if(readyAt!=0&&System.nanoTime()>=readyAt&&++frames>60){
   readyAt=0;shotRequested=true;
  }
 }
 private void selectStyle(MinecraftClient client){
  String pack="file/"+STYLES[style]+".zip";
  client.getResourcePackManager().scanPacks();
  client.getResourcePackManager().setEnabledProfiles(List.of("vanilla","fabric",pack));
  client.options.resourcePacks.clear();client.options.resourcePacks.addAll(List.of("vanilla","fabric",pack));
  client.options.write();
  LOG.info("AUTUMN_CAPTURE_STYLE {}",pack);
  reload=client.reloadResources();
 }
 private void move(MinecraftClient client){
  client.player.networkHandler.sendChatCommand(TELEPORTS[view]);
  readyAt=System.nanoTime()+40L*1_000_000_000L;frames=0;
  LOG.info("AUTUMN_CAPTURE_VIEW {} {}",STYLES[style],VIEWS[view]);
 }
}
