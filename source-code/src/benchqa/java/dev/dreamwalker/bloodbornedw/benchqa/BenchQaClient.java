package dev.dreamwalker.bloodbornedw.benchqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Real client use packet, passenger synchronization, rendered posture and exit. */
public final class BenchQaClient implements ClientModInitializer {
    private int stage,age;private boolean done;private Path out;private final JsonArray checks=new JsonArray();private final JsonObject report=new JsonObject();private BlockPos root=BenchQa.CLIENT;private List<BlockPos> mapRoots=List.of();private volatile boolean mapChecked,positioned;private String mapError;
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
    private void save(){report.add("checks",checks);try{Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
    private void frame(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});checks.add("frame "+name);save();}
    @Override public void onInitializeClient(){String path=System.getProperty("dw.bench.qa.output");if(path==null)return;out=Path.of(path);report.addProperty("status","STARTING");try{Files.createDirectories(out);}catch(Exception e){throw new RuntimeException(e);}save();ClientTickEvents.END_CLIENT_TICK.register(c->{if(done)return;try{tick(c);}catch(Throwable t){t.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",t.toString());save();done=true;c.scheduleStop();}});}
    private void position(MinecraftClient c,BlockPos p){positioned=false;c.getServer().execute(()->{var player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());player.stopRiding();player.changeGameMode(GameMode.CREATIVE);player.getInventory().clear();player.playerScreenHandler.sendContentUpdates();player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.teleport(player.getServerWorld(),p.getX()+.5,p.getY()+.5,p.getZ()-2.2,0,12);positioned=true;});age=0;}
    private boolean ready(MinecraftClient c){boolean ready=positioned&&c.player.squaredDistanceTo(Vec3d.ofCenter(root))<64&&c.world.isChunkLoaded(root)&&!BenchSeating.targets(c.world,root).isEmpty();if(!ready&&age>1200)throw new AssertionError("Timed out awaiting teleport/chunk/bench sync at "+root+" positioned="+positioned+" player="+c.player.getPos()+" block="+c.world.getBlockState(root)+" map="+mapError);return ready;}
    private void interact(MinecraftClient c){var targets=BenchSeating.targets(c.world,root);check(!targets.isEmpty(),"client resolves current bench without map rewrite");var contact=targets.get(0).seats().get(1).contact();c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(contact,Direction.UP,root,false));age=0;}
    private void tick(MinecraftClient c)throws Exception{
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;age++;
        if(stage==0){Path p=out.resolve("server-checks.json");if(!Files.exists(p))return;check(JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("status").getAsString().equals("PASS"),"integrated server seating regressions passed");
            Path input=Path.of(System.getProperty("dw.bench.qa.roots"));var all=JsonParser.parseString(Files.readString(input)).getAsJsonArray();List<BlockPos> roots=new ArrayList<>();for(var e:all){var r=e.getAsJsonObject();if(r.getAsJsonObject("state").get("Name").getAsString().equals("bloodborne_dw:source_hold_bench_2"))continue;var v=r.getAsJsonArray("pos");roots.add(new BlockPos(v.get(0).getAsInt(),v.get(1).getAsInt(),v.get(2).getAsInt()));}mapRoots=List.copyOf(roots);
            c.getServer().execute(()->{try{var w=c.getServer().getOverworld();for(var cell:mapRoots){w.getChunk(cell);if(BenchSeating.targets(w,cell).isEmpty())throw new AssertionError("Map bench unresolved at "+cell);}}catch(Throwable t){mapError=t.toString();}mapChecked=true;});position(c,root);stage=1;return;}
        if(stage==1&&age>=120&&ready(c)){check(mapChecked&&mapError==null,"all "+mapRoots.size()+" intact map benches resolve on server: "+mapError);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);interact(c);stage=2;return;}
        if(stage==2&&age>=40){check(c.player.getVehicle() instanceof BenchSeatEntity,"real use packet synchronizes invisible seat and rider");check(java.lang.Math.abs(c.player.getY()-(c.player.getVehicle().getY()-.75))<.02,"client hip height matches plank");frame(c,"fixture-seated.png");stage=3;age=0;return;}
        if(stage==3&&age>=40){c.options.sneakKey.setPressed(true);stage=4;age=0;return;}
        if(stage==4&&age>=40){c.options.sneakKey.setPressed(false);check(!c.player.hasVehicle(),"normal Shift dismount synchronizes to client");frame(c,"fixture-dismounted.png");check(mapChecked&&mapError==null,"all "+mapRoots.size()+" intact map benches resolve on server: "+mapError);root=mapRoots.stream().filter(p->c.getServer().getOverworld().getBlockState(p).getBlock() instanceof StaticArchitectureBlock).findFirst().orElse(mapRoots.get(0));position(c,root);stage=5;return;}
        if(stage==5&&age>=140&&ready(c)){interact(c);stage=6;return;}
        if(stage==6&&age>=50){check(c.player.getVehicle() instanceof BenchSeatEntity,"existing optimized city bench seats actual player");frame(c,"city-bench-seated.png");stage=7;age=0;return;}
        if(stage==7&&age>=40){c.getServer().execute(()->c.getServer().getPlayerManager().getPlayer(c.player.getUuid()).stopRiding());stage=8;age=0;return;}
        if(stage==8&&age>=40){check(!c.player.hasVehicle(),"existing city bench dismount succeeds");c.options.setPerspective(Perspective.FIRST_PERSON);report.addProperty("status","PASS");save();done=true;c.scheduleStop();}
    }
}
