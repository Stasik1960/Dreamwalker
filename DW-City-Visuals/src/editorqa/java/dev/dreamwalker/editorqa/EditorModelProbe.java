package dev.dreamwalker.editorqa;

import dev.dreamwalker.bloodbornedw.editor.EditorView;
import dev.dreamwalker.bloodbornedw.editor.EditorRenderScope;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
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
    private static BlockPos target(){return EditorRenderProbe.target();}
    private static final AtomicLong ALL=new AtomicLong(), HIT=new AtomicLong();
    private static final ConcurrentHashMap<String,AtomicLong> LIMITS=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,AtomicLong> CALLS=new ConcurrentHashMap<>();
    private static volatile long generation=Long.MIN_VALUE;
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<EditorRenderProbe.ArmedEpoch> BINDING=new ThreadLocal<>();
    private static final ThreadLocal<String> BEFORE=new ThreadLocal<>();
    private record QuadSnapshot(EditorRenderProbe.ArmedEpoch binding, String geometry, String appearance, boolean manual) {}
    private static final ThreadLocal<QuadSnapshot> QUAD_BEFORE=new ThreadLocal<>();
    private static final java.util.IdentityHashMap<Object,EditorRenderProbe.ArmedEpoch> COMPOSITE_MODELS=new java.util.IdentityHashMap<>();
    private static long compositeEpoch=Long.MIN_VALUE;
    public record QuadContext(boolean active,EditorRenderProbe.ArmedEpoch binding,String before,QuadSnapshot quad) {}
    private static final class QuadStats {
        final AtomicLong observed=new AtomicLong(), changed=new AtomicLong(), geometryChanged=new AtomicLong();
    }
    private static final ConcurrentHashMap<Long,QuadStats> QUAD_STATS=new ConcurrentHashMap<>();
    public static long[] quadStats(long generation) {
        QuadStats s=QUAD_STATS.get(generation);
        return s==null?new long[]{0,0,0}:new long[]{s.observed.get(),s.changed.get(),s.geometryChanged.get()};
    }
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
    private static boolean target(BlockPos pos,BlockPos expected){
        if(pos.equals(expected))return true;
        if(pos.getX()!=(expected.getX()&15)||pos.getY()!=(expected.getY()&15)||pos.getZ()!=(expected.getZ()&15))return false;
        try{Object s=snapshot(scope());return ((Number)field(s,"x")).intValue()==(expected.getX()>>4)-1&&((Number)field(s,"y")).intValue()==(expected.getY()>>4)-1&&((Number)field(s,"z")).intValue()==(expected.getZ()>>4)-1;}catch(Throwable ignored){return false;}
    }
    private static boolean allow(String stage){
        long next=EditorRenderProbe.generation();
        if(next!=generation)synchronized(LIMITS){if(next!=generation){LIMITS.clear();generation=next;}}
        return LIMITS.computeIfAbsent(stage,k->new AtomicLong()).getAndIncrement()<6;
    }
    private static String view(Object view,BlockPos pos){
        if(view==null)return "null";
        String result=view.getClass().getName();
        if(view instanceof EditorView e)result+=" has="+e.dw$hasEditorCells()+" style="+e.dw$editorStyle(pos)+" globalStyle="+e.dw$editorStyle(target());
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
            if(x!=(target().getX()>>4)-1||y!=(target().getY()>>4)-1||z!=(target().getZ()>>4)-1)return;
            String detail="input="+view(input,target())+" previous="+view(scope(),target());
            Object[] sections=(Object[])field(data,"sections");
            for(int i=0;i<sections.length;i++)if(sections[i] instanceof RetainedFrameData attachment&&(i==13||attachment.dw$editor().size()!=0))detail+=" section"+i+"={identity="+System.identityHashCode(sections[i])+" count="+attachment.dw$editor().size()+" style="+attachment.dw$editor().style(target())+"}";
            log("scope_input",target(),null,detail);
        }catch(Throwable failure){log("scope_probe_error",target(),null,failure.toString());}
    }
    public static void terrain(String stage,BlockPos pos,BlockRenderView world,Object model){terrain(stage,pos,world,model,null);}
    public static void terrain(String stage,BlockPos pos,BlockRenderView world,Object model,Object state){
        if(!EditorRenderProbe.enabled())return;
        ALL.incrementAndGet();
        count(stage,false);
        try{
            EditorRenderProbe.ArmedEpoch armed=EditorRenderProbe.armed();
            boolean hit=target(pos,armed==null?target():armed.target());ACTIVE.set(hit);BINDING.remove();
            if(!hit)return;HIT.incrementAndGet();count(stage,true);
            if(armed!=null && (world instanceof EditorView || scope() instanceof EditorView)
                    && EditorRenderScope.style(world,pos)==armed.style() && EditorRenderProbe.armed()==armed)BINDING.set(armed);
            if(armed!=null && stage.equals("terrain_return") && pos.equals(armed.target())
                    && world instanceof net.minecraft.client.world.ClientWorld && scope()==null
                    && EditorData.style(world,pos)==armed.style() && model!=null
                    && model.getClass().getName().equals("dev.dreamwalker.bloodbornedw.visual.FacadeModel")
                    && ((Number)field(model,"fixedRole")).intValue()==armed.style()
                    && Boolean.TRUE.equals(field(model,"manualStyle")) && Boolean.TRUE.equals(field(model,"resolved"))) {
                synchronized(COMPOSITE_MODELS){
                    if(EditorRenderProbe.armed()==armed){
                        if(compositeEpoch!=armed.epoch()){COMPOSITE_MODELS.clear();compositeEpoch=armed.epoch();}
                        if(COMPOSITE_MODELS.size()>=64)COMPOSITE_MODELS.clear();
                        COMPOSITE_MODELS.put(model,armed);
                    }
                }
            }
            log(stage,pos,world,"state="+state+" explicit="+view(world,pos)+" scope="+view(scope(),pos)+" model="+model(model));
        }catch(Throwable failure){log("model_probe_error",pos,null,failure.getClass().getName()+":"+failure.getMessage());}
    }
    public static void emit(boolean begin,BlockPos pos,BlockRenderView world,Object model){
        if(!EditorRenderProbe.enabled())return;
        if(begin)terrain("emit_head",pos,world,model);
        else {try{if(ACTIVE.get())log("emit_return",pos,world,model(model));}finally{ACTIVE.remove();BINDING.remove();BEFORE.remove();QUAD_BEFORE.remove();}}
    }
    public static void quads(Object model,int size){
        if(!EditorRenderProbe.enabled())return;ALL.incrementAndGet();
        count("getQuads",false);
        if(ACTIVE.get()){HIT.incrementAndGet();count("getQuads",true);log("getQuads",target(),null,model(model)+" returned="+size);}
    }
    public static QuadContext beginModelQuads(Object model) {
        if(!EditorRenderProbe.enabled())return null;
        EditorRenderProbe.ArmedEpoch armed=EditorRenderProbe.armed(),binding;
        synchronized(COMPOSITE_MODELS){
            if(armed==null||compositeEpoch!=armed.epoch()){COMPOSITE_MODELS.clear();compositeEpoch=armed==null?Long.MIN_VALUE:armed.epoch();return null;}
            binding=COMPOSITE_MODELS.get(model);
        }
        if(binding==null||binding!=armed||EditorRenderProbe.armed()!=armed)return null;
        QuadContext previous=new QuadContext(ACTIVE.get(),BINDING.get(),BEFORE.get(),QUAD_BEFORE.get());
        ACTIVE.set(true);BINDING.set(binding);BEFORE.remove();QUAD_BEFORE.remove();return previous;
    }
    public static void endModelQuads(QuadContext previous) {
        if(previous==null)return;
        ACTIVE.set(previous.active());
        if(previous.binding()==null)BINDING.remove();else BINDING.set(previous.binding());
        if(previous.before()==null)BEFORE.remove();else BEFORE.set(previous.before());
        if(previous.quad()==null)QUAD_BEFORE.remove();else QUAD_BEFORE.set(previous.quad());
    }
    private static String quad(Object quad){
        if(quad instanceof BakedQuad q){int[] v=q.getVertexData();return "sprite="+q.getSprite().getContents().getId()+" color="+Integer.toHexString(v[3])+" uv="+Integer.toHexString(v[4])+","+Integer.toHexString(v[5]);}
        if(quad instanceof MutableQuadView q){
            SpriteAtlasTexture atlas=(SpriteAtlasTexture)MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            return "sprite="+SpriteFinder.get(atlas).find(q).getContents().getId()+" color="+Integer.toHexString(q.color(0))+" uv="+q.u(0)+","+q.v(0);
        }
        return "null";
    }
    private static QuadSnapshot quadSnapshot(Object quad,boolean manual,EditorRenderProbe.ArmedEpoch binding) {
        StringBuilder geometry=new StringBuilder(),appearance=new StringBuilder();
        if(quad instanceof BakedQuad q) {
            int[] v=q.getVertexData();
            for(int i=0;i<4;i++){int o=i*8;geometry.append(v[o]).append(',').append(v[o+1]).append(',').append(v[o+2]).append(',').append(v[o+6]).append(',').append(v[o+7]).append(';');appearance.append(v[o+3]).append(',').append(v[o+4]).append(',').append(v[o+5]).append(';');}
            appearance.append(q.getSprite().getContents().getId());
        } else if(quad instanceof MutableQuadView q) {
            for(int i=0;i<4;i++){geometry.append(Float.floatToIntBits(q.x(i))).append(',').append(Float.floatToIntBits(q.y(i))).append(',').append(Float.floatToIntBits(q.z(i))).append(',').append(q.lightmap(i)).append(';');appearance.append(q.color(i)).append(',').append(Float.floatToIntBits(q.u(i))).append(',').append(Float.floatToIntBits(q.v(i))).append(';');}
            SpriteAtlasTexture atlas=(SpriteAtlasTexture)MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            appearance.append(SpriteFinder.get(atlas).find(q).getContents().getId());
        } else return null;
        return new QuadSnapshot(binding,geometry.toString(),appearance.toString(),manual);
    }
    public static void materialBefore(Object quad,int role,boolean manual){
        if(!EditorRenderProbe.enabled())return;ALL.incrementAndGet();
        count("material",false);
        if(ACTIVE.get())try{
            BEFORE.set("role="+role+" manual="+manual+" "+quad(quad));
            EditorRenderProbe.ArmedEpoch binding=BINDING.get();
            if(binding!=null && binding==EditorRenderProbe.armed() && manual && role==binding.style())QUAD_BEFORE.set(quadSnapshot(quad,true,binding));
            else QUAD_BEFORE.remove();
        }catch(Throwable failure){BEFORE.set(failure.getClass().getName());QUAD_BEFORE.remove();}
    }
    public static void materialAfter(String stage,Object quad){
        if(!EditorRenderProbe.enabled()||!ACTIVE.get())return;
        try{
            HIT.incrementAndGet();count(stage,true);
            QuadSnapshot before=QUAD_BEFORE.get(),after=quadSnapshot(quad,false,BINDING.get());
            if(before!=null&&after!=null&&before.manual()&&before.binding()==BINDING.get()&&before.binding()==EditorRenderProbe.armed()) {
                QuadStats stats=QUAD_STATS.computeIfAbsent(before.binding().epoch(),ignored->new QuadStats());
                stats.observed.incrementAndGet();
                if(!before.geometry().equals(after.geometry()))stats.geometryChanged.incrementAndGet();
                if(!before.appearance().equals(after.appearance()))stats.changed.incrementAndGet();
            }
            log(stage,target(),null,"before={"+BEFORE.get()+"} after={"+quad(quad)+"}");
        }catch(Throwable failure){log("material_probe_error",target(),null,failure.getClass().getName());}finally{BEFORE.remove();QUAD_BEFORE.remove();}
    }
}
