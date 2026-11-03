package dev.dreamwalker.editorqa;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.chunk.ChunkStatus;

/** Read-only, bounded evidence in the disposable QA jar, never production. */
public final class EditorRenderProbe {
    private static final Gson GSON=new Gson();
    private static volatile BlockPos TARGET=new BlockPos(117,Boolean.getBoolean("dw.editorqa.uiBeFixture")?200:62,-1091);
    private static final boolean ENABLED=Boolean.getBoolean("dw.editorqa.renderProbe");
    private static final AtomicInteger EVENTS=new AtomicInteger();
    private static final AtomicLong GENERATION=new AtomicLong();
    public record ArmedEpoch(BlockPos target,int style,long epoch) {}
    private static volatile ArmedEpoch armed;
    private static volatile int lastStyle=Integer.MIN_VALUE;
    private static long changedTick,lastSample;
    private static boolean originsRecorded,writeErrorReported;
    private static Path output;
    private EditorRenderProbe() {}
    public static boolean enabled(){return ENABLED;}
    public static BlockPos target(){return TARGET;}
    public static synchronized void setTarget(BlockPos pos){armed=null;TARGET=pos.toImmutable();lastStyle=Integer.MIN_VALUE;}
    public static synchronized long arm(BlockPos pos,int style) {
        long epoch=GENERATION.incrementAndGet();
        TARGET=pos.toImmutable();armed=new ArmedEpoch(TARGET,style,epoch);EVENTS.set(0);return epoch;
    }
    public static ArmedEpoch armed(){return armed;}
    public static int liveStyle(){return lastStyle;}
    public static long generation(){return GENERATION.get();}
    public static void tick(MinecraftClient client) {
        if(!ENABLED||client.world==null||client.player==null)return;
        try {
            int style=EditorData.style(client.world,TARGET);long now=client.world.getTime();
            if(style!=lastStyle){lastStyle=style;changedTick=now;synchronized(EditorRenderProbe.class){if(armed==null)GENERATION.incrementAndGet();}EVENTS.set(0);describe(client,"style_change",style);}
            if(!originsRecorded){
                originsRecorded=true;
                for(String name:new String[]{"dev.dreamwalker.bloodbornedw.visual.CityFacadesClient","dev.dreamwalker.bloodbornedw.visual.FacadeModel","dev.dreamwalker.bloodbornedw.visual.FacadeMaterials","dev.dreamwalker.bloodbornedw.editor.CityEditorClient","dev.dreamwalker.bloodbornedw.editor.EditorData","dev.dreamwalker.bloodbornedw.editor.EditorRenderScope","dev.dreamwalker.bloodbornedw.composite.NativeVisuals"})
                    record("class_origin",TARGET,style,null,classInfo(name));
            }
            long clock=System.nanoTime();
            if(now-changedTick<=8&&clock-lastSample>100_000_000L){lastSample=clock;describe(client,"post_change_tick_"+(now-changedTick),style);}
        }catch(Throwable failure){record("tick_error",TARGET,lastStyle,null,failure.toString());}
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static String model(Object current){
        StringBuilder result=new StringBuilder();
        for(int depth=0;current!=null&&depth<4;depth++){
            if(depth>0)result.append(" -> ");result.append(current.getClass().getName());
            for(String n:new String[]{"fixedRole","manualStyle","resolved","forceBase"})try{result.append(' ').append(n).append('=').append(field(current,n));}catch(Throwable ignored){}
            try{current=field(current,"delegate");}catch(Throwable ignored){break;}
        }
        return result.toString();
    }
    private static void describe(MinecraftClient client,String stage,int style){
        var state=client.world.getBlockState(TARGET);
        String detail="modeAlt="+CityVisualClient.isAlt()+" state="+state+" model="+model(client.getBlockRenderManager().getModel(state))+" "+EditorModelProbe.counts();
        try{
            detail+=" nativeIndex="+NativeVisuals.index(client.world,TARGET);
            var chunk=client.world.getChunkManager().getChunk(TARGET.getX()>>4,TARGET.getZ()>>4,ChunkStatus.FULL,false);
            if(chunk!=null){var data=NativeVisuals.data(chunk);var attachment=(RetainedFrameData)(Object)data;var entity=chunk.getBlockEntities().get(TARGET);detail+=" nativeDataIdentity="+System.identityHashCode(data)+" editorCells="+attachment.dw$editor().size()+" retainedFrame="+attachment.dw$frames().get(TARGET.asLong())+" existingBlockEntity="+(entity==null?"none":entity.getClass().getName());}
        }catch(Throwable failure){detail+=" metadataError="+failure;}
        record(stage,TARGET,style,client.world,detail);
    }
    private static String classInfo(String name){
        try{Class<?> c=Class.forName(name,false,EditorRenderProbe.class.getClassLoader());return name+" origin="+c.getProtectionDomain().getCodeSource()+" loader="+c.getClassLoader();}
        catch(Throwable t){return name+" unavailable="+t;}
    }
    public static synchronized void record(String stage,BlockPos pos,int style,BlockRenderView view,String detail){
        if(!ENABLED||EVENTS.getAndIncrement()>=2048)return;
        try{
            if(output==null){String path=System.getProperty("dw.editorqa.renderProbeOutput","");if(path.isBlank())throw new IllegalStateException("Missing renderProbeOutput");output=Path.of(path);Files.createDirectories(output.getParent());}
            JsonObject row=new JsonObject();row.addProperty("stage",stage);row.addProperty("epochMillis",System.currentTimeMillis());row.addProperty("nano",System.nanoTime());row.addProperty("generation",generation());row.addProperty("thread",Thread.currentThread().getName());row.addProperty("pos",pos==null?"none":pos.toShortString());row.addProperty("style",style);row.addProperty("worldView",view==null?"none":view.getClass().getName());row.addProperty("detail",detail);
            Files.writeString(output,GSON.toJson(row)+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Throwable error){if(!writeErrorReported){writeErrorReported=true;System.err.println("Editor QA render probe write failed: "+error);}}
    }
}
