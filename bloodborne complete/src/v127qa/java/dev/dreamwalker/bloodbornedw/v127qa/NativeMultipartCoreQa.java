package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;

/** Isolated actual native bake/quad contracts plus a real Fabric callback
 * observer. No gallery, no world mutation and no per-state callback ledger. */
public final class NativeMultipartCoreQa implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static void require(boolean value,String label){if(!value)throw new IllegalStateException(label);}
    private static BakedModel child(int color){
        int[] vertices=new int[32];vertices[3]=color;var quad=new BakedQuad(vertices,-1,Direction.UP,null,true);
        return (BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(),new Class<?>[]{BakedModel.class},(proxy,method,args)->switch(method.getName()){
            case "getQuads"->args[1]==null?List.of(quad):List.of();case "getParticleSprite"->null;case "getTransformation"->ModelTransformation.NONE;case "getOverrides"->ModelOverrideList.EMPTY;case "isBuiltin"->false;case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"NativeQaChild"+color;default->true;});
    }
    private static MultipartModelComponent component(String model,String property,String value){return new MultipartModelComponent(new SimpleMultipartModelSelector(property,value),new WeightedUnbakedModel(List.of(new ModelVariant(new Identifier("bloodborne_dw:block/"+model),AffineTransformation.identity(),false,1))));}
    private static final class Spy implements Baker {
        BakedModel a=child(0xff111111),b=child(0xff222222);Identifier current;final List<String> calls=new ArrayList<>();
        @Override public UnbakedModel getOrLoadModel(Identifier id){throw new AssertionError("Unexpected raw model lookup");}
        @Override public BakedModel bake(Identifier id,ModelBakeSettings settings){calls.add(current+" -> "+id);return id.getPath().endsWith("qa_a")?a:b;}
    }
    private static BakedModel bake(MultipartUnbakedModel source,Spy spy,ModelBakeSettings rotation,String id){spy.current=new Identifier("bloodborne_dw",id);return source.bake(spy,ignored->null,rotation,spy.current);}
    @Override public void onInitializeClient(){
        Path output=null;JsonObject report=new JsonObject();
        try {
            Path directory=MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize(),marker=directory.resolve("client-capture-input.json");if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))return;
            var input=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();if(!input.has("v127NativeFunctions")||!input.get("v127NativeFunctions").getAsBoolean())return;require(input.get("freshTestWorldOnly").getAsBoolean(),"Isolated marker");
            output=directory.resolve("v127-native-client-core-proof.json");require(!Files.exists(output),"Fresh model proof directory");var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();require(paths.size()==1,"One production artifact");String artifact=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(paths.get(0))));require(artifact.equals(input.get("productionJarSha256").getAsString()),"Exact production artifact binding");report.addProperty("productionJarSha256",artifact);
            var block=Registries.BLOCK.get(new Identifier("bloodborne_dw:source_assembly_brick_wall"));require(block!=Blocks.AIR,"Owned registered source type");var manager=block.getStateManager();var parts=List.of(component("qa_a","variant","0|1"),component("qa_b","rotation","0|2"));var source=new MultipartUnbakedModel(manager,parts);var spy=new Spy();
            var first=bake(source,spy,ModelRotation.X0_Y0,"qa_top_a");var second=bake(source,spy,ModelRotation.X0_Y0,"qa_top_b");require(first==second,"Exact ordered child/predicate graph shares a core");require(spy.calls.size()==4&&spy.calls.get(2).startsWith("bloodborne_dw:qa_top_b"),"Every actual child bake runs again with the current top-level context");
            var stateMap=((MultipartStateCacheData)first).dreamwalker$stateBits();require(stateMap instanceof MultipartModelCoreCache.OwnedStates&&stateMap.isEmpty(),"Shared state map starts lazy and owned");
            var graph=new ArrayList<org.apache.commons.lang3.tuple.Pair<java.util.function.Predicate<BlockState>,BakedModel>>();graph.add(org.apache.commons.lang3.tuple.Pair.of(parts.get(0).getPredicate(manager),spy.a));graph.add(org.apache.commons.lang3.tuple.Pair.of(parts.get(1).getPredicate(manager),spy.b));var entry=MultipartModelCoreCache.entry(graph,ModelRotation.X0_Y0,child(3),manager);require(MultipartModelCoreCache.same(entry,graph,ModelRotation.X0_Y0),"Exact graph signature");var reverse=new ArrayList<>(graph);Collections.reverse(reverse);require(!MultipartModelCoreCache.same(entry,reverse,ModelRotation.X0_Y0),"Order participates in sharing");require(!MultipartModelCoreCache.same(entry,graph,ModelRotation.X0_Y90),"Rotation identity participates in sharing");ModelBakeSettings uv=new ModelBakeSettings(){public AffineTransformation getRotation(){return ModelRotation.X0_Y0.getRotation();}public boolean isUvLocked(){return true;}};require(!MultipartModelCoreCache.same(entry,graph,uv),"UV-lock participates in sharing");
            spy.b=child(4);require(bake(source,spy,ModelRotation.X0_Y0,"qa_changed_child")!=first,"Different child identity never shares");require(bake(source,spy,ModelRotation.X0_Y90,"qa_rotation")!=first,"Different transform never shares");var fresh=new MultipartUnbakedModel(manager,parts);require(bake(fresh,spy,ModelRotation.X0_Y0,"qa_reload")!=first,"Fresh parsed reload graph has fresh lifetime");
            var reference=new MultipartBakedModel.Builder();
            // The exact raw counterpart uses the first graph's native quads.
            reference.addComponent(parts.get(0).getPredicate(manager),graph.get(0).getRight());reference.addComponent(parts.get(1).getPredicate(manager),graph.get(1).getRight());var nativeRaw=reference.build();require(!(((MultipartStateCacheData)nativeRaw).dreamwalker$stateBits() instanceof MultipartModelCoreCache.OwnedStates),"Ordinary native builds retain vanilla state maps");
            require(first.useAmbientOcclusion()==nativeRaw.useAmbientOcclusion()&&first.hasDepth()==nativeRaw.hasDepth()&&first.isSideLit()==nativeRaw.isSideLit()&&first.isBuiltin()==nativeRaw.isBuiltin(),"Native model flags equal the independent raw build");require(first.getParticleSprite()==nativeRaw.getParticleSprite()&&first.getTransformation()==nativeRaw.getTransformation()&&first.getOverrides()==nativeRaw.getOverrides(),"Particle, transforms and overrides preserve exact native identities");report.addProperty("rawNativeModelHeaderIdentities","PASS");
            var sample=manager.getStates().subList(0,Math.min(32,manager.getStates().size()));var expected=new IdentityHashMap<BlockState,List<BakedQuad>>();for(var state:sample)expected.put(state,nativeRaw.getQuads(state,null,Random.create(17)));
            var pool=Executors.newFixedThreadPool(4);AtomicInteger comparisons=new AtomicInteger();try{List<Future<?>> jobs=new ArrayList<>();for(int thread=0;thread<4;thread++)jobs.add(pool.submit(()->{for(int turn=0;turn<16;turn++)for(var state:sample){require(first.getQuads(state,null,Random.create(17)).equals(expected.get(state)),"Concurrent shared quads equal independent raw native quads");comparisons.incrementAndGet();}}));for(var job:jobs)job.get();}finally{pool.shutdown();require(pool.awaitTermination(30,TimeUnit.SECONDS),"All isolated QA workers released");}
            require(stateMap.size()<=manager.getStates().size(),"Cache bounded by exact registered state count");int previous=stateMap.size();stateMap.put(Blocks.STONE.getDefaultState(),new BitSet());require(stateMap.size()==previous,"Foreign state is never retained");
            var ctor=Arrays.stream(BlockState.class.getDeclaredConstructors()).filter(value->value.getParameterCount()==3).findFirst().orElseThrow();ctor.setAccessible(true);var unregistered=(BlockState)ctor.newInstance(block,block.getDefaultState().getEntries(),null);require(!manager.getStates().contains(unregistered),"Same-owner unregistered fixture is outside the native state domain");stateMap.put(unregistered,new BitSet());require(stateMap.size()==previous,"Unregistered same-owner state cannot expand the cache");
            var foreignParts=List.of(component("qa_a","facing","north|south"));var foreign=new MultipartUnbakedModel(Blocks.OAK_STAIRS.getStateManager(),foreignParts);var foreignA=bake(foreign,spy,ModelRotation.X0_Y0,"qa_foreign_a");require(foreignA!=bake(foreign,spy,ModelRotation.X0_Y0,"qa_foreign_b")&&!(((MultipartStateCacheData)foreignA).dreamwalker$stateBits() instanceof MultipartModelCoreCache.OwnedStates),"Foreign parsed model and state storage unchanged");
            report.addProperty("status","PASS_NATIVE_CORE_CONTRACT_WAITING_FOR_REAL_RESOURCE_CALLBACKS");report.addProperty("exactChildOrderTransformUvAndReload","PASS");report.addProperty("actualChildBakeCalls",spy.calls.size());report.add("childBakeContextSamples",JSON.toJsonTree(spy.calls));report.addProperty("concurrentRawQuadComparisons",comparisons.get());report.addProperty("registeredStateCacheSize",stateMap.size());report.addProperty("registeredStateDomainBound",manager.getStates().size());report.addProperty("foreignAndUnregisteredStateNotRetained",true);report.addProperty("maximumEntriesPerParsedModel",1);report.add("runtimeCoreCounters",JSON.toJsonTree(MultipartModelCoreCache.metrics()));Files.writeString(output,JSON.toJson(report)+"\n");
            LongAdder ownCallbacks=new LongAdder(),foreignCallbacks=new LongAdder(),ownBefore=new LongAdder(),foreignBefore=new LongAdder();AtomicReference<String> firstId=new AtomicReference<>(),lastId=new AtomicReference<>(),firstBefore=new AtomicReference<>(),lastBefore=new AtomicReference<>();
            ModelLoadingPlugin.register(plugin->{
                plugin.modifyModelBeforeBake().register((model,context)->{if(context.id() instanceof ModelIdentifier){String ns=context.id().getNamespace();if(ns.equals("bloodborne_dw")||ns.equals("bloodborne_rp")){ownBefore.increment();firstBefore.compareAndSet(null,context.id().toString());lastBefore.set(context.id().toString());}else foreignBefore.increment();}return model;});
                plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,(model,context)->{if(context.id() instanceof ModelIdentifier){String ns=context.id().getNamespace();if(ns.equals("bloodborne_dw")||ns.equals("bloodborne_rp")){ownCallbacks.increment();firstId.compareAndSet(null,context.id().toString());lastId.set(context.id().toString());}else foreignCallbacks.increment();}return model;});
            });
            Path destination=output;AtomicBoolean wrote=new AtomicBoolean();ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.world==null||client.player==null||!wrote.compareAndSet(false,true))return;try{require(ownCallbacks.sum()>0&&foreignCallbacks.sum()>0&&ownBefore.sum()==ownCallbacks.sum()&&foreignBefore.sum()==foreignCallbacks.sum(),"Real Fabric before/after per-top-level callback counts match");require(Objects.equals(firstBefore.get(),firstId.get())&&Objects.equals(lastBefore.get(),lastId.get()),"Real before/after current top-level ID samples match");report.addProperty("status","PASS_ACTUAL_CLIENT_CORE_AND_FABRIC_CALLBACK_CONTRACT");report.addProperty("actualOwnedTopLevelCallbacks",ownCallbacks.sum());report.addProperty("actualForeignTopLevelCallbacks",foreignCallbacks.sum());report.addProperty("actualOwnedBeforeBakeCallbacks",ownBefore.sum());report.addProperty("actualForeignBeforeBakeCallbacks",foreignBefore.sum());report.addProperty("firstActualOwnedId",firstId.get());report.addProperty("lastActualOwnedId",lastId.get());report.add("runtimeCoreCounters",JSON.toJsonTree(MultipartModelCoreCache.metrics()));Files.writeString(destination,JSON.toJson(report)+"\n");}catch(Exception failure){report.addProperty("status","FAIL_ACTUAL_CLIENT_CORE_CALLBACK_CONTRACT");report.addProperty("error",failure.toString());try{Files.writeString(destination,JSON.toJson(report)+"\n");}catch(Exception ignored){}}});
        }catch(Throwable failure){report.addProperty("status","FAIL_NATIVE_CLIENT_CORE_CONTRACT");report.addProperty("error",failure.toString());if(output!=null)try{Files.writeString(output,JSON.toJson(report)+"\n");}catch(Exception ignored){}throw new IllegalStateException("Native multipart core contract failed",failure);}
    }
}
