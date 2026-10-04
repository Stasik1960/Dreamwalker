package dev.dreamwalker.bloodborneblocks.qa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.registry.state.Property;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Disposable QA mod: compiled separately, never included in the release JAR. */
public final class WorldEditSmokeInitializer implements ModInitializer {
 private static volatile boolean checked;
 private static volatile boolean serverStarted;
 private static int visibleTicks;
 private static boolean captured;
 public void onInitialize(){
  register("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents","SERVER_STARTED",
   "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted",()->serverStarted=true);
  register("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents","END_SERVER_TICK",
   "net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick",()->{
    if(!serverStarted||checked)return;
    try{verify();checked=true;}catch(Exception failure){throw new IllegalStateException("WORLD_EDIT_RUNTIME_QA_FAILED",failure);}
   });
  if(FabricLoader.getInstance().getEnvironmentType().name().equals("CLIENT"))register(
   "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents","END_CLIENT_TICK",
   "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick",WorldEditSmokeInitializer::clientTick);
 }
 private static void register(String holder,String field,String listener,Runnable callback){
  try{
   Class<?> type=Class.forName(listener);
   Object proxy=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
    if(m.getDeclaringClass()==Object.class)return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->"Bloodborne QA listener";};
    callback.run();return null;
   });
   Object event=Class.forName(holder).getField(field).get(null);
   Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register",Object.class).invoke(event,proxy);
  }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
 }
 @SuppressWarnings({"unchecked","rawtypes"}) private static void verify()throws Exception{
  long started=System.nanoTime();int states=0,transitions=0,nativeChecks=0,wideBlocks=0;
  Class<?> adapter=Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
  Method toNative=adapter.getMethod("adapt",BlockState.class);
  Class<?> nativeProperty=Class.forName("net.minecraft.class_2769");
  Object leaf=toNative.invoke(null,BlockTypes.OAK_LEAVES.getDefaultState());
  Method nativeEntries=leaf.getClass().getMethod("method_11656");
  Map<Object,Comparable<?>> leafEntries=(Map)nativeEntries.invoke(leaf);
  Map.Entry<Object,Comparable<?>> absent=leafEntries.entrySet().iterator().next();
  for(String id:Files.readAllLines(Path.of("qa-block-ids.txt"))){
   BlockType type=BlockTypes.get("bloodborne_blocks:"+id);if(type==null)throw new AssertionError("WorldEdit omitted "+id);
   boolean wide=type.getProperties().stream().anyMatch(p->p.getValues().size()>=64);
   for(BlockState state:type.getAllStates()){
    states++;
    for(Property property:type.getProperties()){
     List<?> values=property.getValues();
     for(Object value:List.of(values.get(0),values.get(values.size()-1))){
      Map<Property<?>,Object> changed=new HashMap<>(state.getStates());changed.put(property,value);
      if(state.with(property,value)!=type.getState(changed))throw new AssertionError("WorldEdit transition identity "+id);
      transitions++;
     }
     if(state.with(property,state.getStates().get(property))!=state)throw new AssertionError("WorldEdit self identity");
     if(state.with(property,"__invalid_qa_value__")!=state)throw new AssertionError("WorldEdit invalid fallback");
    }
   }
   if(!wide)continue;wideBlocks++;
   Object nativeState=toNative.invoke(null,type.getDefaultState());
   Map<Object,Comparable<?>> values=(Map)nativeEntries.invoke(nativeState);
   Method with=nativeState.getClass().getMethod("method_11657",nativeProperty,Comparable.class);
   Method withOptional=nativeState.getClass().getMethod("method_47968",nativeProperty,Comparable.class);
   Method cycle=nativeState.getClass().getMethod("method_28493",nativeProperty);
   Method back=adapter.getMethod("adapt",Class.forName("net.minecraft.class_2680"));
   for(var entry:values.entrySet()){
    if(with.invoke(nativeState,entry.getKey(),entry.getValue())!=nativeState)throw new AssertionError("Native self identity");
    Object cycled=cycle.invoke(nativeState,entry.getKey());
    Comparable value=((Map<Object,Comparable<?>>)nativeEntries.invoke(cycled)).get(entry.getKey());
    if(with.invoke(nativeState,entry.getKey(),value)!=cycled)throw new AssertionError("Native transition identity");
    if(withOptional.invoke(nativeState,entry.getKey(),value)!=cycled)throw new AssertionError("Native optional transition");
    if(toNative.invoke(null,back.invoke(null,cycled))!=cycled)throw new AssertionError("Adapter round trip");
    nativeChecks+=4;
   }
   if(withOptional.invoke(nativeState,absent.getKey(),absent.getValue())!=nativeState)throw new AssertionError("Native missing optional property");
   try{with.invoke(nativeState,absent.getKey(),absent.getValue());throw new AssertionError("Native accepted missing property");}
   catch(InvocationTargetException expected){if(!(expected.getCause()instanceof IllegalArgumentException))throw expected;}
   nativeChecks+=2;
  }
  Map<String,Object> proof=new LinkedHashMap<>();proof.put("passed",true);proof.put("states",states);proof.put("transitions",transitions);
  proof.put("nativeChecks",nativeChecks);proof.put("wideBlocks",wideBlocks);proof.put("seconds",(System.nanoTime()-started)/1e9);
  proof.put("lithiumPresent",FabricLoader.getInstance().isModLoaded("lithium"));
  proof.put("heapUsedBytes",java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
  Files.writeString(Path.of("worldedit-runtime-proof.json"),new Gson().toJson(proof));
  System.out.println("WORLD_EDIT_RUNTIME_QA_PASSED "+new Gson().toJson(proof));
 }
 private static void clientTick(){
  if(!checked)return;
  try{
   Class<?> clientClass=Class.forName("net.minecraft.class_310");Object client=clientClass.getMethod("method_1551").invoke(null);
   if(clientClass.getField("field_1687").get(client)==null||clientClass.getField("field_1724").get(client)==null)return;
   if(clientClass.getField("field_1755").get(client)!=null)return;
   visibleTicks++;
   if(visibleTicks==100){
    Class<?> recorder=Class.forName("net.minecraft.class_318");
    Object framebuffer=clientClass.getMethod("method_1522").invoke(client);
    for(Method method:recorder.getMethods())if(method.getName().equals("method_1659")&&method.getParameterCount()==3){
     method.invoke(null,Path.of(".").toFile(),framebuffer,(java.util.function.Consumer<Object>)text->System.out.println("QA_SCREENSHOT_SAVED"));captured=true;break;
    }
    if(!captured)throw new IllegalStateException("Screenshot API signature changed");
   }
   if(visibleTicks==160){System.out.println("FULL_PACK_CLIENT_WORLD_VISIBLE");clientClass.getMethod("method_1592").invoke(client);}
  }catch(ReflectiveOperationException error){throw new IllegalStateException("Client QA callback failed",error);}
 }
}
