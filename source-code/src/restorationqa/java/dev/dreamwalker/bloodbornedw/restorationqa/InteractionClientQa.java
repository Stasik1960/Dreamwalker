package dev.dreamwalker.bloodbornedw.restorationqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.tool.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.*;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

/** Actual client particles, screen overlays, key command and window rendering. */
public final class InteractionClientQa implements ClientModInitializer {
    private Path out;private int stage,age;private volatile boolean ready,lease;private final JsonArray checks=new JsonArray();
    @Override public void onInitializeClient(){out=Path.of(System.getProperty("dw.interaction.qa.output"));ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;try{tick(c);}catch(Throwable t){t.printStackTrace();save("FAIL",t.toString());c.scheduleStop();}});}
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks.add(name);}
    private void move(MinecraftClient c,double x,double y,double z,float yaw,float pitch){ready=false;age=0;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),x,y,z,yaw,pitch);ready=true;});}
    private void frame(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name+".png",c.getFramebuffer(),t->{});}
    private boolean overlay(MinecraftClient c,String text)throws Exception{for(var f:c.inGameHud.getClass().getDeclaredFields())if(Text.class.isAssignableFrom(f.getType())){f.setAccessible(true);var value=(Text)f.get(c.inGameHud);if(value!=null&&value.getString().equals(text))return true;}return false;}
    private void press(){KeyBinding.onKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_F8,0));}
    private void tick(MinecraftClient c)throws Exception{
        age++;var b=InteractionQa.BASE;
        if(stage==0){c.options.hudHidden=true;NoCollideKeyClient.KEY.setBoundKey(InputUtil.fromKeyCode(GLFW.GLFW_KEY_F8,0));KeyBinding.updateKeysByCode();c.getServer().execute(()->{try{if(!c.getServer().getOverworld().getBlockState(b.add(-6,0,-1)).isOf(CompositeArchitecture.kindBlock("source_shutter_window")))InteractionQa.prepareScene(c.getServer().getOverworld());var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());c.getServer().getPlayerManager().addToOperators(p.getGameProfile());p.getInventory().clear();p.playerScreenHandler.sendContentUpdates();ready=true;}catch(Throwable t){t.printStackTrace();save("FAIL",t.toString());c.scheduleStop();}});stage=1;age=0;return;}
        if(stage==1&&ready){move(c,b.getX()-5.5,b.getY(),b.getZ()-5,0,0);stage=2;return;}
        if(stage==2&&ready&&age>100){check(c.world.getBlockState(b.add(-6,0,-1)).isOf(CompositeArchitecture.kindBlock("source_shutter_window")),"client sees independent window root");frame(c,"window-closed-front");ready=false;c.getServer().execute(()->{var w=c.getServer().getOverworld();var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(w,b.add(-6,0,-1));var result=CompositeRuntime.transition(w,owner,w.getBlockState(b.add(-6,0,-1)).with(CompositeRootBlock.OPEN,true),null);if(result.outcome()!=dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED)throw new AssertionError(result.reason());ready=true;});stage=3;age=0;return;}
        if(stage==3&&ready&&age>80){check(c.world.getBlockState(b.add(-6,0,-1)).get(CompositeRootBlock.OPEN),"window open state reaches client");frame(c,"window-open-front");move(c,b.getX()-5.5,b.getY(),b.getZ()+1.5,180,0);stage=4;return;}
        if(stage==4&&ready&&age>80){frame(c,"window-open-back");move(c,b.getX()+1.5,b.getY()+.25,b.getZ()+1.5,0,0);stage=5;return;}
        if(stage==5&&ready&&age>70){check(c.world.getBlockState(BlockPos.ofFloored(c.player.getEyePos())).isOf(DwUtilityBlocks.DARKNESS),"eye is inside darkness fixture");check(c.world.getBlockState(BlockPos.ofFloored(c.player.getEyePos())).shouldBlockVision(c.world,BlockPos.ofFloored(c.player.getEyePos())),"darkness requests opaque in-wall overlay");frame(c,"inside-darkness");move(c,b.getX()+6.5,b.getY()+.25,b.getZ()+1.5,0,0);stage=6;return;}
        if(stage==6&&ready&&age>70){check(c.world.getBlockState(BlockPos.ofFloored(c.player.getEyePos())).isOf(FaerieVeilBlock.BLOCK),"eye is inside faerie fixture");frame(c,"inside-faerie");move(c,b.getX()+6.5,b.getY(),b.getZ()-4,0,0);stage=7;return;}
        if(stage==7&&ready&&age>70){for(var pos:java.util.List.of(b.up(),b.add(5,1,0))){check(!c.world.getBlockState(pos).getOutlineShape(c.world,pos).isEmpty(),"contextless particle query has bounds "+pos);for(int i=0;i<20;i++)c.particleManager.addBlockBreakingParticles(pos,Direction.NORTH);}check(true,"actual client hit-particle path survives both materials");frame(c,"faerie-hit-particles");ready=false;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(CompositeArchitecture.kindBlock("source_andesite")));p.playerScreenHandler.sendContentUpdates();ready=true;});stage=8;age=0;return;}
        if(stage==8&&ready&&age>30){press();stage=9;age=0;return;}
        if(stage==9){c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());lease=NoCollidePlacement.armed(p);});if(lease&&overlay(c,"Коллизии отключены")){check(true,"configured key sends real server command and confirms activation");c.player.getInventory().selectedSlot=(c.player.getInventory().selectedSlot+1)%9;stage=10;age=0;return;}}
        if(stage==10&&overlay(c,"Режим без коллизий сброшен")){check(true,"slot change shows real expiration notification");press();stage=11;age=0;return;}
        if(stage==11&&overlay(c,"Неподходящий блок")){check(true,"key rejects unsuitable held item with requested message");save("PASS","");c.scheduleStop();stage=12;return;}
        if(age>1200)throw new AssertionError("Client QA stage stalled "+stage);
    }
    private synchronized void save(String status,String error){try{var j=new JsonObject();j.addProperty("status",status);j.addProperty("stage",stage);j.add("checks",checks);if(!error.isEmpty())j.addProperty("error",error);Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(j));}catch(Exception e){throw new RuntimeException(e);}}
}
