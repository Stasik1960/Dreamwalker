package dev.dreamwalker.bloodbornedw.fixqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.stat.Stats;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** App-internal functional driver: real pick action, Stats UI and frame output. */
public final class FixQaClient implements ClientModInitializer {
    private final JsonObject report=new JsonObject();private final JsonArray checks=new JsonArray();private Path out;private int stage,age,scene;private boolean off,done;
    private final double[][] cameras={
        {169.619,66.26819,-831.425,40.6,29.1,166,65,-829},
        {139.946,76.76834,-937.482,-115.9,55.7,142,74,-939},
        {35.694,63.71495,-697.403,79,1.4,31,65,-697},
        {75.5,26.28,-853.5,0,25,75,26,-851},
        {26.260,11.81426,-966.017,-26.2,57.5,30,0,-961},
        {6.889,23.2782,-864.550,71.7,32.3,6,23,-864},
        {85.971,50.1522,-1013.941,24.6,40.5,84,48,-1011},
        {58.727,70.4695,-665.628,-35.8,37.2,62,66,-660}
    };
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks.add(message);}
    private void save(){try{report.add("checks",checks);Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
    @Override public void onInitializeClient(){String path=System.getProperty("dw.fix.qa.output");if(path==null)return;out=Path.of(path);try{Files.createDirectories(out);}catch(Exception e){throw new RuntimeException(e);}report.addProperty("status","STARTING");save();ClientTickEvents.END_CLIENT_TICK.register(client->{if(done)return;try{tick(client);}catch(Throwable failure){failure.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",failure.toString());save();done=true;client.scheduleStop();}});}
    private void camera(MinecraftClient client){
        double[] c=cameras[scene];SourceLightingDiagnostics.phase((off?"AO_OFF_":"AO_ON_")+scene);
        boolean initial=scene==0&&!off;
        client.getServer().execute(()->{var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.changeGameMode(GameMode.CREATIVE);if(initial){player.getInventory().clear();player.playerScreenHandler.sendContentUpdates();}player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(player.getServerWorld(),c[0],c[1],c[2],(float)c[3],(float)c[4]);var world=player.getServerWorld();world.setTimeOfDay(6000);world.setWeather(0,0,false,false);});age=0;
    }
    private void screenshot(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),message->{});checks.add("frame "+name);save();}
    private void pick(MinecraftClient client){
        double[] c=cameras[scene];var pos=new BlockPos((int)c[5],(int)c[6],(int)c[7]);var state=client.world.getBlockState(pos);check(!state.isAir(),"pick target loaded at "+pos);
        client.crosshairTarget=new BlockHitResult(Vec3d.ofCenter(pos),Direction.NORTH,pos,false);((FixClientAccess)client).dw$pick();var held=client.player.getMainHandStack();
        check(!held.isEmpty(),"actual middle-button action returns item at "+pos);
        if(scene<2)check(held.getNbt()!=null&&held.getNbt().getInt(NativeVisuals.ITEM_KEY)==(scene==0?40837:23514),"middle-button preserves repaired native visual index");
        if(scene==2)check(state.equals(SourceFallbackCarrierV1271.itemState(held)),"middle-button preserves fallback source/bank/profile");
        if(scene==3)check(!(held.getItem() instanceof net.minecraft.item.BlockItem item&&item.getBlock() instanceof StaticArchitectureBlock),"old static carrier picks its authored object");
    }
    private void tick(MinecraftClient client)throws Exception{
        if(client.world==null||client.player==null||client.getServer()==null||client.getOverlay()!=null)return;
        age++;
        if(stage==0){
            var serverReport=out.resolve("server-checks.json");if(!Files.exists(serverReport))return;check(JsonParser.parseString(Files.readString(serverReport)).getAsJsonObject().get("status").getAsString().equals("PASS"),"integrated server regression checks passed");
            client.options.getAo().setValue(true);camera(client);stage=1;report.addProperty("status","SCENES");save();return;
        }
        if(stage==1){
            if(age<120)return;
            if(!off&&scene<4)pick(client);
            screenshot(client,(off?"ao-off-":"fixed-")+scene+".png");stage=2;age=0;return;
        }
        if(stage==2){
            if(age<25)return;scene++;
            if(scene<cameras.length){camera(client);stage=1;return;}
            if(!off){off=true;scene=3;client.options.getAo().setValue(false);client.worldRenderer.reload();camera(client);stage=1;return;}
            client.options.getAo().setValue(true);client.worldRenderer.reload();
            var skillet=Registries.ITEM.get(new Identifier("farmersdelight","skillet"));check(skillet!=net.minecraft.item.Items.AIR,"Farmer's Delight skillet is installed");
            client.getServer().execute(()->{var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.incrementStat(Stats.USED.getOrCreateStat(skillet));player.incrementStat(Stats.CRAFTED.getOrCreateStat(skillet));});stage=3;age=0;return;
        }
        if(stage==3&&age>30){client.setScreen(new StatsScreen(new GameMenuScreen(true),client.player.getStatHandler()));stage=4;age=0;return;}
        if(stage==4&&age>80){
            check(client.currentScreen instanceof StatsScreen,"Statistics screen opened without crash");
            var items=client.currentScreen.children().stream().filter(e->e instanceof ButtonWidget b&&b.getMessage().getString().equals("Items")).map(e->(ButtonWidget)e).findFirst().orElseThrow();items.onPress();stage=5;age=0;return;
        }
        if(stage==5&&age>100){check(client.currentScreen instanceof StatsScreen,"Statistics Items tab renders skillet without crash");screenshot(client,"statistics-items-skillet.png");stage=6;age=0;return;}
        if(stage==6&&age>40){client.setScreen(null);report.addProperty("status","PASS");save();done=true;client.scheduleStop();}
    }
}
