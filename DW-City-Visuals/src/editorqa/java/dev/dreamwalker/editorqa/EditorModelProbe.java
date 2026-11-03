package dev.dreamwalker.editorqa;

import dev.dreamwalker.bloodbornedw.editor.EditorView;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.client.render.model.BakedQuad;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.screen.PlayerScreenHandler;

/** Disposable QA observer. No world or model is retained across calls. */
public final class EditorModelProbe {
    private static final BlockPos TARGET=new BlockPos(117,Boolean.getBoolean("dw.editorqa.uiBeFixture")?200:62,-1091);
    private static final AtomicLong ALL=new AtomicLong(), HIT=new AtomicLong();
    private static final ConcurrentHashMap<String,AtomicLong> LIMITS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,AtomicLong> CALLS=new ConcurrentHashMap<>();
    private static volatile long generation=Long.MIN_VALUE;
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<String> BEFORE=new ThreadLocal<>();
    private EditorModelProbe() {}
    public static String counts(){return "modelCalls="+ALL.get()+" targetCalls="+HIT.get()+" stages="+CALLS;}
    private static void count(String stage,boolean target){CALLS.computeIfAbsent(stage+(target?"_target":"_all"),k->new AtomicLong()).incrementAndGet();}
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        Class<?> type=object instanceof Class<?> c?c:object.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object scope() throws ReflectiveOperationException {
        return ((ThreadLocal<?>)field(Class.forName("dev.dreamwalker.bloodbornedw.editor.EditorRenderScope"),"CURRENT")).get();
    }
    private static Object snapshot(Object view) throws ReflectiveOperationException {return field(view,"dw$editorSnapshot");}
    private static boolean target(BlockPos pos){
        if(pos.equals(TARGET))return true;
        if(pos.getX()!=(TARGET.getX()&15)||pos.getY()!=(TARGET.getY()&15)||pos.getZ()!=(TARGET.getZ()&15))return false;
        try{Object s=snapshot(scope());return ((Number)field(s,"x")).intValue()==(TARGET.getX()>>4)-1&&((Number)field(s,"y")).intValue()==(TARGET.getY()>>4)-1&&((Number)field(s,"z")).intValue()==(TARGET.getZ()>>4)-1;}catch(Throwable ignored){return false;}
    }
    private static boolean allow(String stage){
        long next=EditorRenderProbe.generation();
        if(next!=generation)synchronized(LIMITS){if(next!=generation){LIMITS.clear();generation=next;}}
        return LIMITS.computeIfAbsent(stage,k->new AtomicLong()).getAndIncrement()<6;
    }
    private static String view(Object view,BlockPos pos){
        if(view==null)return "null";
        String result=view.getClass().getName();
        if(view instanceof EditorView e)result+=" has="+e.dw$hasEditorCells()+" style="+e.dw$editorStyle(pos)+" globalStyle="+e.dw$editorStyle(TARGET);
        try{Object s=snapshot(view);int x=((Number)field(s,"x")).intValue(),y=((Number)field(s,"y")).intValue(),z=((Number)field(s,"z")).intValue();result+=" origin="+x+","+y+","+z+" index="+((pos.getX()>>4)-x+((pos.getZ()>>4)-z)*3+((pos.getY()>>4)-y)*9);}catch(Throwable ignored){}
        return result;
    }
    private static String model(Object model){
        if(model==null)return "null";
        String text=model.getClass().getName();
        for(String name:new String[]{"fixedRole","manualStyle","resolved","forceBase","delegate"})try{Object value=field(model,name);text+=" "+name+"="+(name.equals("delegate")?(value==null?"null":value.getClass().getName()):value);}catch(Throwable ignored){}
        return text;
    }
    private static void log(String stage,BlockPos pos,BlockRenderView world,String detail){
        if(!allow(stage))return;
        EditorRenderProbe.record(stage,pos,EditorRenderProbe.liveStyle(),world,"nano="+System.nanoTime()+" "+detail);
    }
    public static void scopeInput(EditorView input){
        if(!EditorRenderProbe.enabled()||input==null)return;
        try{
            Object data=snapshot(input);
            int x=((Number)field(data,"x")).intValue(),y=((Number)field(data,"y")).intValue(),z=((Number)field(data,"z")).intValue();
            if(x!=(TARGET.getX()>>4)-1||y!=(TARGET.getY()>>4)-1||z!=(TARGET.getZ()>>4)-1)return;
            String detail="input="+view(input,TARGET)+" previous="+view(scope(),TARGET);
            Object[] sections=(Object[])field(data,"sections");
            for(int i=0;i<sections.length;i++)if(sections[i] instanceof RetainedFrameData attachment&&(i==13||attachment.dw$editor().size()!=0))detail+=" section"+i+"={identity="+System.identityHashCode(sections[i])+" count="+attachment.dw$editor().size()+" style="+attachment.dw$editor().style(TARGET)+"}";
            log("scope_input",TARGET,null,detail);
        }catch(Throwable failure){log("scope_probe_error",TARGET,null,failure.toString());}
    }
    public static void terrain(String stage,BlockPos pos,BlockRenderView world,Object model){terrain(stage,pos,world,model,null);}
    public static void terrain(String stage,BlockPos pos,BlockRenderView world,Object model,Object state){
        if(!EditorRenderProbe.enabled())return;
        ALL.incrementAndGet();
        count(stage,false);
        try{
            boolean hit=target(pos);ACTIVE.set(hit);
            if(!hit)return;HIT.incrementAndGet();count(stage,true);
            log(stage,pos,world,"state="+state+" explicit="+view(world,pos)+" scope="+view(scope(),pos)+" model="+model(model));
        }catch(Throwable failure){log("model_probe_error",pos,null,failure.getClass().getName()+":"+failure.getMessage());}
    }
    public static void emit(boolean begin,BlockPos pos,BlockRenderView world,Object model){
        if(!EditorRenderProbe.enabled())return;
        if(begin)terrain("emit_head",pos,world,model);
        else {try{if(ACTIVE.get())log("emit_return",pos,world,model(model));}finally{ACTIVE.remove();BEFORE.remove();}}
    }
    public static void quads(Object model,int size){
        if(!EditorRenderProbe.enabled())return;ALL.incrementAndGet();
        count("getQuads",false);
        if(ACTIVE.get()){HIT.incrementAndGet();count("getQuads",true);log("getQuads",TARGET,null,model(model)+" returned="+size);}
    }
    private static String quad(Object quad){
        if(quad instanceof BakedQuad q){int[] v=q.getVertexData();return "sprite="+q.getSprite().getContents().getId()+" color="+Integer.toHexString(v[3])+" uv="+Integer.toHexString(v[4])+","+Integer.toHexString(v[5]);}
        if(quad instanceof MutableQuadView q){
            SpriteAtlasTexture atlas=(SpriteAtlasTexture)MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            return "sprite="+SpriteFinder.get(atlas).find(q).getContents().getId()+" color="+Integer.toHexString(q.color(0))+" uv="+q.u(0)+","+q.v(0);
        }
        return "null";
    }
    public static void materialBefore(Object quad,int role,boolean manual){
        if(!EditorRenderProbe.enabled())return;ALL.incrementAndGet();
        count("material",false);
        if(ACTIVE.get())try{BEFORE.set("role="+role+" manual="+manual+" "+quad(quad));}catch(Throwable failure){BEFORE.set(failure.getClass().getName());}
    }
    public static void materialAfter(String stage,Object quad){
        if(!EditorRenderProbe.enabled()||!ACTIVE.get())return;
        try{HIT.incrementAndGet();count(stage,true);log(stage,TARGET,null,"before={"+BEFORE.get()+"} after={"+quad(quad)+"}");}catch(Throwable failure){log("material_probe_error",TARGET,null,failure.getClass().getName());}finally{BEFORE.remove();}
    }
}
