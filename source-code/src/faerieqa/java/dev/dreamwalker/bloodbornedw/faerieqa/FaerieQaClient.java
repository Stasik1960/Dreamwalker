package dev.dreamwalker.bloodbornedw.faerieqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** One JVM/scene, ABBA texture-on/off periods. No production benchmark controls. */
public final class FaerieQaClient implements ClientModInitializer {
    public static volatile boolean freeze,collect;private static long lastFrame,atlasTicks,loops;private static int previousZero=-1;
    private static final List<Double> intervals=new ArrayList<>();private static final Map<String,String> phases=new HashMap<>();
    public static void frameStart(){long now=System.nanoTime();if(collect&&lastFrame!=0)intervals.add((now-lastFrame)/1e6);lastFrame=collect?now:0;}
    public static void spriteTick(String id,int frame,int time){atlasTicks++;phases.put(id,frame+":"+time);if(id.endsWith("tile_00")){if(previousZero==31&&frame==0)loops++;previousZero=frame;}}
    private final JsonObject report=new JsonObject();private final JsonArray checks=new JsonArray(),samples=new JsonArray();private Path out;private boolean done;private int stage,age,period;private volatile boolean positioned;private long periodStart,sampleCpu;private static final boolean[] MODES={false,true,true,false};
    private final com.sun.management.OperatingSystemMXBean os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);save();}
    private void save(){report.add("checks",checks);report.add("performancePeriods",samples);try{Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
    private void frame(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});checks.add("frame "+name);save();}
    @Override public void onInitializeClient(){String dir=System.getProperty("dw.faerie.qa.output");if(dir==null)return;out=Path.of(dir);report.addProperty("status","STARTING");save();ClientTickEvents.END_CLIENT_TICK.register(c->{if(done)return;try{tick(c);}catch(Throwable t){collect=false;freeze=false;report.addProperty("status","FAIL");report.addProperty("error",t.toString());t.printStackTrace();save();done=true;c.scheduleStop();}});}
    private void position(MinecraftClient c,double x,double y,double z,float yaw,float pitch,boolean item){positioned=false;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getInventory().clear();if(item)p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(FaerieVeilBlock.ITEM));p.playerScreenHandler.sendContentUpdates();p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),x,y,z,yaw,pitch);positioned=true;});age=0;}
    private void front(MinecraftClient c){var b=FaerieQa.BASE;position(c,b.getX()+16,b.getY()+7,b.getZ()+23,180,0,false);}
    private boolean ready(MinecraftClient c,BlockPos pos){boolean ready=positioned&&c.world.isChunkLoaded(pos)&&c.world.getBlockState(pos).isOf(FaerieVeilBlock.BLOCK);if(!ready&&age>1200)throw new AssertionError("Chunk/teleport not ready "+pos);return ready;}
    private void projection(MinecraftClient c)throws Exception{
        var model=c.getBlockRenderManager().getModel(FaerieVeilBlock.BLOCK.getDefaultState());var field=model.getClass().getDeclaredField("session");field.setAccessible(true);var session=field.get(model);var prepare=session.getClass().getDeclaredMethod("prepare");prepare.setAccessible(true);prepare.invoke(session);var pf=session.getClass().getDeclaredField("projections");pf.setAccessible(true);var values=(FaerieVeilClient.Projection[])pf.get(session);
        for(var projection:values){for(var pos:List.of(new BlockPos(-9,-1,-17),new BlockPos(0,0,0),new BlockPos(7,15,8))){int t=projection.at(pos);var u=pos.add(projection.uAxis()==0?projection.uSign():0,projection.uAxis()==1?projection.uSign():0,projection.uAxis()==2?projection.uSign():0);var v=pos.add(projection.vAxis()==0?projection.vSign():0,projection.vAxis()==1?projection.vSign():0,projection.vAxis()==2?projection.vSign():0);check(projection.at(u)%8==(t%8+1)%8,"continuous U including negative positions "+projection);check(projection.at(v)/8==(t/8+1)%8,"continuous V including negative positions "+projection);}}
    }
    private void beginPeriod(){freeze=!MODES[period];collect=false;intervals.clear();lastFrame=0;periodStart=System.nanoTime();}
    private void endPeriod(){collect=false;var sorted=intervals.stream().mapToDouble(Double::doubleValue).sorted().toArray();check(sorted.length>20,"enough measured frames "+period);double sum=Arrays.stream(sorted).sum();var row=new JsonObject();row.addProperty("animated",MODES[period]);row.addProperty("frames",sorted.length);row.addProperty("seconds",sum/1000);row.addProperty("fps",sorted.length*1000/sum);row.addProperty("p50FrameMs",sorted[sorted.length/2]);row.addProperty("p95FrameMs",sorted[(int)((sorted.length-1)*.95)]);row.addProperty("p99FrameMs",sorted[(int)((sorted.length-1)*.99)]);row.addProperty("over50ms",Arrays.stream(sorted).filter(v->v>50).count());row.addProperty("over100ms",Arrays.stream(sorted).filter(v->v>100).count());row.addProperty("processCpuPercent",(os.getProcessCpuTime()-sampleCpu)/(sum*1e6)*100);row.addProperty("heapUsedMiB",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1048576.0);samples.add(row);save();}
    private void tick(MinecraftClient c)throws Exception{
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;age++;var b=FaerieQa.BASE;
        if(stage==0){var path=out.resolve("server-checks.json");if(!Files.exists(path))return;check(JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("status").getAsString().equals("PASS"),"server saved wall reload passed");c.options.hudHidden=true;c.options.getEnableVsync().setValue(false);c.options.getMaxFps().setValue(260);front(c);stage=1;return;}
        if(stage==1&&age>=160&&ready(c,b)){projection(c);check(c.world.getBlockEntity(b)==null,"rendered wall has no block entities");frame(c,"veil-front.png");report.addProperty("frontSpritePhases",new HashSet<>(phases.values()).size());report.addProperty("visibleAnimatedSprites",phases.size());check(phases.size()==64,"all 64 wall pieces animate");check(new HashSet<>(phases.values()).size()==1,"all visible pieces keep identical animation phase");position(c,b.getX()+16,b.getY()+7,b.getZ()-23,0,0,false);stage=2;return;}
        if(stage==2&&age>=100&&ready(c,b)){frame(c,"veil-back.png");position(c,b.getX()+55,b.getY()+3,b.getZ()+8,90,0,false);stage=3;return;}
        if(stage==3&&age>=100&&ready(c,b.add(40,0,0))){frame(c,"veil-east.png");position(c,b.getX()+.5,b.getY()+2,b.getZ()+.5,180,0,false);stage=4;return;}
        if(stage==4&&age>=70&&positioned){var pos=BlockPos.ofFloored(c.player.getPos());check(c.world.getBlockState(pos).isOf(FaerieVeilBlock.BLOCK)&&c.world.getBlockState(pos).getCollisionShape(c.world,pos,ShapeContext.of(c.player)).isEmpty(),"real client occupies opaque veil freely");front(c);stage=5;return;}
        if(stage==5&&age>=120&&ready(c,b)){check(loops>=1,"animation loops without stopping");report.addProperty("wallBlocks",FaerieQa.WIDTH*FaerieQa.HEIGHT);report.addProperty("loopCountBeforeBenchmark",loops);beginPeriod();stage=6;return;}
        if(stage==6){double elapsed=(System.nanoTime()-periodStart)/1e9;if(elapsed>=5&&!collect){intervals.clear();lastFrame=0;sampleCpu=os.getProcessCpuTime();collect=true;}if(elapsed>=20){endPeriod();if(++period<MODES.length)beginPeriod();else{freeze=false;report.addProperty("atlasTicks",atlasTicks);report.addProperty("status","PASS");save();done=true;c.scheduleStop();}}return;}
        if(age>2400)throw new AssertionError("Stage stalled "+stage);
    }
}
