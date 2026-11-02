package dev.dreamwalker.bloodbornedw.visual;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Finite camera pass on an isolated city copy; excluded from the delivered mod. */
public final class VisualRevisionQa implements ModInitializer, ClientModInitializer {
    private static final double[][] VIEWS = {
        {262.312,50.18922,-910.191,-103.9,8.4},
        {221.217,46.06987,-895.841,-161.8,18.7},
        {68.873,104.9155,-788.288,121.6,21.0},
        {125.434,60.07117,-858.094,-111.9,2.9},
        {120.85,47,-950.338,173.0,-6.0},
        {177.089,66.99565,-933.721,-153.3,21.9},
        {278.429,44.87609,-846.249,-91.8,0.3},
        {194.524,60.05132,-971.282,86.7,10.0},
        {293.234,61.5177,-979.893,-151.4,8.9},
        {249.608,46.24593,-839.298,-31.1,22.8},
        // West of the cemetery, outside every tree canopy. The former eastern
        // views sat inside neighbouring trees at x283/291 rather than framing
        // the intended x267/269/270 trees. Centre/top/edge rays are unobstructed.
        {240,57,-886,-90,32.4},
        {240,57,-864,-90,26.1},
        {240,57,-857.25,-90,31.1}
    };
    private static final Path OUT = Path.of(System.getProperty("dw.visualqa.output"));
    private final List<String> rows = new ArrayList<>();
    private int index, age; private volatile boolean ready; private boolean finished;
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CityVisualModeState state=CityVisualModeState.get(server);
            state.alt=Boolean.getBoolean("dw.visualqa.alt"); state.markDirty();
            server.getOverworld().setTimeOfDay(6000);
            server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
        });
    }
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient c) {
        if(finished || c.world==null || c.player==null || c.getServer()==null)return;
        try {
            if(age==0 && !ready) {
                age=-1; double[] v=VIEWS[index];
                c.options.hudHidden=true; c.options.getFov().setValue(70);
                c.getServer().execute(()->{
                    var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                    p.changeGameMode(GameMode.SPECTATOR);
                    p.teleport(p.getServerWorld(),v[0],v[1],v[2],(float)v[3],(float)v[4]);
                    ready=true;
                }); return;
            }
            if(!ready)return;
            if(++age<180)return;
            String name=String.format("visual7-%s-%02d.png",CityVisualClient.isAlt()?"alt":"original",index+1);
            ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),t->{});
            double[] v=VIEWS[index]; BlockPos pos=BlockPos.ofFloored(v[0],v[1],v[2]);
            rows.add("{\"view\":"+(index+1)+",\"alt\":"+CityVisualClient.isAlt()+",\"chunks\":"+c.world.getChunkManager().getDebugString().length()+",\"playerBlock\":\""+pos.toShortString()+"\"}");
            Files.createDirectories(OUT); Files.writeString(OUT.resolve("captures.json"),"["+String.join(",",rows)+"]");
            index++;age=0;ready=false;
            if(index==VIEWS.length){finished=true;Files.writeString(OUT.resolve("complete.json"),"{\"status\":\"CAPTURED\",\"count\":"+index+",\"alt\":"+CityVisualClient.isAlt()+"}");c.scheduleStop();}
        }catch(Exception e){finished=true;try{Files.writeString(OUT.resolve("error.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
}
