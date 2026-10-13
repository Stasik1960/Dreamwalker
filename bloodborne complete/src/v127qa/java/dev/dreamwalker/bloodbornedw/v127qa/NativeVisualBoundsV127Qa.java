package dev.dreamwalker.bloodbornedw.v127qa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

/** Actual source renderer vertices and native Frustum. No world authoring or altered physics. */
public final class NativeVisualBoundsV127Qa {
    private static BakedModel beforeEpoch;
    private static final List<java.lang.ref.WeakReference<BakedModel>> oldCacheModels=new ArrayList<>();
    private static long beforeClears;
    private NativeVisualBoundsV127Qa(){}

    public static JsonObject verify(MinecraftClient client)throws Exception{
        var report=new JsonObject();var rows=new JsonArray();report.add("cases",rows);int vertices=0;
        var selected=new LinkedHashSet<CompositeRootBlock>();for(String path:List.of("source_addon_barrel_0","source_grass_block","source_1_spirelamp_001"))selected.add(CompositeArchitecture.kindBlock(path));
        var all=CompositeArchitecture.blocks().stream().filter(CompositeVisualBoundsV127::supports).toList();
        selected.add(all.stream().filter(b->b.spec.openable).findFirst().orElseThrow());
        selected.add(all.stream().filter(b->b.spec.displayName.toLowerCase(Locale.ROOT).contains("арк")).findFirst().orElseThrow());
        selected.add(all.stream().filter(b->b.spec.pose(b.getDefaultState()).collision().isEmpty()).findFirst().orElseThrow());
        selected.add(all.stream().filter(b->ImportedArtworkBlock.bankProperty(b.getDefaultState())!=null).findFirst().orElseThrow());
        for(var block:selected)for(int rotation:new int[]{0,1,3})for(boolean open:block.spec.openable?new boolean[]{false,true}:new boolean[]{false})for(var profile:CompositeRootBlock.Profile.values()){
            var state=block.getDefaultState().with(CompositeRootBlock.ROTATION,rotation).with(CompositeRootBlock.OPEN,open).with(CompositeRootBlock.PROFILE,profile);
            if(ImportedArtworkBlock.bankProperty(state)!=null){int variant=block.spec.variants.size()-1;state=state.with(ImportedArtworkBlock.bankProperty(state),variant/16).with(CompositeRootBlock.VARIANT,variant%16);}
            var payload=new NbtCompound();var shifts=new NbtList();shifts.add(NbtDouble.of(.3125));shifts.add(NbtDouble.of(-.375));shifts.add(NbtDouble.of(.625));payload.put("SourceShift",shifts);payload.putDouble("MountY",.5);
            vertices+=assertBounds(client,block,state,payload,rows,"representative_pose");
        }
        // Exercise the same alias/legacy payload resolution as the renderer, not a renamed model.
        var alias=ImportedTypeAliases.mappings().stream().filter(m->!m.oldRegistryId().equals(m.newRegistryId())).findFirst().orElseThrow();
        var old=CompositeArchitecture.kindBlock(new net.minecraft.util.Identifier(alias.oldRegistryId()).getPath()).getDefaultState().with(CompositeRootBlock.VARIANT,alias.oldVariant());
        var extra=new NbtCompound();check(ImportedTypeAliases.needsMigration(old,extra),"Real old alias requires renderer migration");var effective=ImportedTypeAliases.target(old);var migrated=ImportedTypeAliases.transformPayload(old,extra);
        check(extra.isEmpty(),"Alias renderer transform leaves supplied source payload untouched");vertices+=assertBounds(client,(CompositeRootBlock)effective.getBlock(),effective,migrated,rows,"actual_alias_resolution");
        var legacy=ImportedTypeAliases.mappings().stream().map(m->{var block=CompositeArchitecture.kindBlock(new net.minecraft.util.Identifier(m.oldRegistryId()).getPath());return block.getDefaultState().with(CompositeRootBlock.VARIANT,m.oldVariant());}).filter(s->{var payload=ImportedTypeAliases.transformPayload(s,new NbtCompound());var target=ImportedTypeAliases.target(s);return ImportedTypeAliases.readLegacyStairArt(payload).isPresent()&&!ImportedStairLegacyArt.selectedParts((CompositeRootBlock)target.getBlock(),target,payload).equals(((CompositeRootBlock)target.getBlock()).spec.pose(target).parts());}).findFirst();
        check(legacy.isPresent(),"Native legacy-art payload cohort exists");var legacyOld=legacy.get();var legacyState=ImportedTypeAliases.target(legacyOld);var legacyPayload=ImportedTypeAliases.transformPayload(legacyOld,new NbtCompound());
        check(!ImportedStairLegacyArt.selectedParts((CompositeRootBlock)legacyState.getBlock(),legacyState,legacyPayload).equals(((CompositeRootBlock)legacyState.getBlock()).spec.pose(legacyState).parts()),"Selected legacy parts exercise actual nondefault art");vertices+=assertBounds(client,(CompositeRootBlock)legacyState.getBlock(),legacyState,legacyPayload,rows,"actual_legacy_stair_parts");
        var original=CompositeArchitecture.blocks().stream().filter(b->!CompositeVisualBoundsV127.supports(b)).findFirst().orElseThrow();
        var originalVertices=capture(original,original.getDefaultState(),new NbtCompound());check(CompositeVisualBoundsV127.localBounds(original,original.getDefaultState(),new NbtCompound()).isEmpty(),"Original V12 never enters visual cache");check(originalVertices.equals(capture(original,original.getDefaultState(),new NbtCompound())),"Original V12 emitted coordinates remain exactly stable");
        var actual=(CompositeBlockEntity)client.world.getBlockEntity(new BlockPos(12,64,0));check(actual!=null&&actual.resident()!=null,"Actual placed NEW owner exists");var saved=actual.createNbt();var copy=actual.payload();copy.putString("QaMutation","isolated-copy");check(saved.equals(actual.createNbt()),"Public payload remains a defensive copy");
        var worldField=field("activeWorld");var frustumField=field("activeFrustum");var cameraField=field("activeCamera");var distanceField=field("activeDistance");var previousWorld=worldField.get(null);var previousFrustum=frustumField.get(null);var previousCamera=cameraField.get(null);double previousDistance=distanceField.getDouble(null);
        try{
            var frustum=new Frustum(new Matrix4f(),new Matrix4f());var pos=actual.getPos();frustum.setPosition(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);worldField.set(null,client.world);frustumField.set(null,frustum);cameraField.set(null,Vec3d.ofCenter(pos));distanceField.setDouble(null,144);
            var block=(CompositeRootBlock)actual.getCachedState().getBlock();var payload=actual.payload();check(CompositeVisualBoundsV127.visible(actual,block,actual.getCachedState(),payload),"Native frustum retains intersecting actual NEW model");
            frustum.setPosition(100000,100000,100000);check(!CompositeVisualBoundsV127.visible(actual,block,actual.getCachedState(),payload),"Native frustum culls actual offscreen NEW model");
            check(CompositeVisualBoundsV127.visible(actual,original,original.getDefaultState(),payload),"Original V12 bypass remains visible even against rejecting frustum");
            frustumField.set(null,null);check(CompositeVisualBoundsV127.visible(actual,block,actual.getCachedState(),payload),"Absent current frame frustum fails open");
            var roomy=new Frustum(new Matrix4f(),new Matrix4f().scaling(.001f));roomy.setPosition(pos.getX()+100,pos.getY()+.5,pos.getZ()+.5);frustumField.set(null,roomy);cameraField.set(null,new Vec3d(pos.getX()+100,pos.getY()+.5,pos.getZ()+.5));distanceField.setDouble(null,16);check(!CompositeVisualBoundsV127.visible(actual,block,actual.getCachedState(),payload),"Actual NEW owner outside camera range is rejected even inside native frustum");check(CompositeVisualBoundsV127.visible(actual,original,original.getDefaultState(),payload),"Original V12 also bypasses range rejection");
        }finally{worldField.set(null,previousWorld);frustumField.set(null,previousFrustum);cameraField.set(null,previousCamera);distanceField.setDouble(null,previousDistance);}
        double radius=144;var camera=Vec3d.ZERO;check(CompositeVisualBoundsV127.withinDistance(new Box(radius,-500,-1,radius+1,500,1),camera,radius),"Exact nearest visual edge on view-range boundary remains visible");check(!CompositeVisualBoundsV127.withinDistance(new Box(radius+.01,-500,-1,radius+1,500,1),camera,radius),"Whole visual model beyond range is rejected");check(CompositeVisualBoundsV127.withinDistance(new Box(-.5,10000,-.5,.5,10100,.5),camera,radius),"Tall or elevated models use horizontal range without vertical disappearance");
        var wide=all.stream().map(b->CompositeVisualBoundsV127.localBounds(b,b.getDefaultState(),new NbtCompound())).filter(Optional::isPresent).map(Optional::get).filter(b->b.maxX-b.minX>8).findFirst().orElseThrow();double rootX=wide.maxX>.5?-radius-wide.maxX+.5:radius-wide.minX-.5;var worldWide=wide.offset(rootX,0,-(wide.minZ+wide.maxZ)/2);check(Math.abs(rootX)>radius&&CompositeVisualBoundsV127.withinDistance(worldWide,camera,radius),"Actual large native model is retained when its root is far but a visual edge intersects range");report.addProperty("cameraRangeContract","PASS_NEAR_BOUNDARY_FAR_LARGE_MODEL_EDGE_AND_HEIGHT_PRESERVATION");
        check(saved.equals(actual.createNbt()),"Visibility/renderer inspection leaves exact instance NBT untouched");
        // An unknown/dynamic adapter must not be treated as a fixed JSON mesh.
        var barrel=CompositeArchitecture.kindBlock("source_addon_barrel_0");var state=barrel.getDefaultState();var parts=ImportedStairLegacyArt.selectedParts(barrel,state,new NbtCompound());var nativeModels=new ArrayList<BakedModel>();for(var part:parts)nativeModels.add(client.getBakedModelManager().getModel(part.model()));
        int[] queried={0};var real=nativeModels.get(0);BakedModel dynamic=(BakedModel)java.lang.reflect.Proxy.newProxyInstance(BakedModel.class.getClassLoader(),new Class<?>[]{BakedModel.class},(proxy,method,args)->{if(method.getName().equals("getQuads"))queried[0]++;return method.invoke(real,args);});nativeModels.set(0,dynamic);
        var calculate=CompositeVisualBoundsV127.class.getDeclaredMethod("calculate",CompositeRootBlock.class,BlockState.class,List.class,List.class,BakedModel.class);calculate.setAccessible(true);check(((Optional<?>)calculate.invoke(null,barrel,state,parts,nativeModels,client.getBakedModelManager().getMissingModel())).isEmpty()&&queried[0]==0,"Unknown dynamic model fails open without guessing sampled geometry");
        nativeModels.set(0,client.getBakedModelManager().getMissingModel());check(((Optional<?>)calculate.invoke(null,barrel,state,parts,nativeModels,client.getBakedModelManager().getMissingModel())).isEmpty(),"Missing native model fails open");
        long evictions=((Number)CompositeVisualBoundsV127.snapshot().get("evictions")).longValue();int lookups=0;
        outer:for(var block:all)for(int rotation=0;rotation<8;rotation++)for(boolean open:new boolean[]{false,true})for(var profile:CompositeRootBlock.Profile.values()){
            CompositeVisualBoundsV127.localBounds(block,block.getDefaultState().with(CompositeRootBlock.ROTATION,rotation).with(CompositeRootBlock.OPEN,open).with(CompositeRootBlock.PROFILE,profile),new NbtCompound());lookups++;
            if(((Number)CompositeVisualBoundsV127.snapshot().get("evictions")).longValue()>evictions)break outer;
        }
        check(((Number)CompositeVisualBoundsV127.snapshot().get("cacheEntries")).intValue()==2048&&((Number)CompositeVisualBoundsV127.snapshot().get("evictions")).longValue()>evictions,"Actual visibility cache is bounded at2048 and evicts across real source states");report.addProperty("nativeCacheBoundLookups",lookups);report.addProperty("unknownDynamicModel","PASS_FAIL_OPEN_NO_QUAD_QUERY");
        report.addProperty("status","PASS_NATIVE_VERTEX_ENCLOSURE_AND_FRUSTUM");report.addProperty("actualEmittedVerticesEnclosed",vertices);report.addProperty("originalV12","BYPASS_AND_EXACT_RENDER_COORDINATES");report.addProperty("physicsAndInstanceNbt","UNCHANGED");report.add("cache",new Gson().toJsonTree(CompositeVisualBoundsV127.snapshot()));return report;
    }
    private static int assertBounds(MinecraftClient client,CompositeRootBlock block,BlockState state,NbtCompound payload,JsonArray rows,String category)throws Exception{
        var bounds=CompositeVisualBoundsV127.localBounds(block,state,payload);check(bounds.isPresent(),"Exact native source bounds are available: "+block.spec.id+" "+state);
        var emitted=capture(block,state,payload);check(!emitted.isEmpty(),"Production source renderer emits vertices");var shift=CompositeSourceShift.world(state,payload);var origin=new BlockPos(17,70,-9);double mount=payload.getDouble("MountY");var world=bounds.get().offset(origin.getX()+shift.x(),origin.getY()+shift.y()+mount,origin.getZ()+shift.z());
        for(var v:emitted){check(bounds.get().contains(v),"Actual native emitted local vertex enclosed: "+block.spec.id+" "+v);check(world.contains(v.add(origin.getX()+shift.x(),origin.getY()+shift.y()+mount,origin.getZ()+shift.z())),"Outer root/SourceShift/mount translation enclosed");}
        check(bounds.equals(CompositeVisualBoundsV127.localBounds(block,state,payload)),"Repeated lookup reuses identical bound value");var row=new JsonObject();row.addProperty("category",category);row.addProperty("registry",block.spec.id.toString());row.addProperty("state",state.toString());row.addProperty("vertices",emitted.size());row.addProperty("bounds",bounds.get().toString());row.addProperty("status","PASS_ACTUAL_NATIVE_RENDERER_VERTEX_ENCLOSURE");rows.add(row);return emitted.size();
    }
    public static void prepareReload(MinecraftClient client)throws Exception{
        oldCacheModels.clear();beforeEpoch=client.getBakedModelManager().getMissingModel();beforeClears=((Number)CompositeVisualBoundsV127.snapshot().get("bakeEpochClears")).longValue();
        var cache=(Map<?,?>)field("CACHE").get(null);check(!cache.isEmpty(),"Real visibility cache is populated before native reload");var unique=Collections.newSetFromMap(new IdentityHashMap<BakedModel,Boolean>());for(var entry:cache.values()){var models=entry.getClass().getDeclaredMethod("models");models.setAccessible(true);unique.addAll((List<BakedModel>)models.invoke(entry));}for(var model:unique)oldCacheModels.add(new java.lang.ref.WeakReference<>(model));
    }
    public static JsonObject verifyReload(MinecraftClient client)throws Exception{
        check(beforeEpoch!=null&&client.getBakedModelManager().getMissingModel()!=beforeEpoch,"Actual native resource reload changes baked epoch identity");var barrel=CompositeArchitecture.kindBlock("source_addon_barrel_0");check(CompositeVisualBoundsV127.localBounds(barrel,barrel.getDefaultState(),new NbtCompound()).isPresent(),"Reloaded actual source model still has visual bounds");
        check(((Number)CompositeVisualBoundsV127.snapshot().get("bakeEpochClears")).longValue()>beforeClears,"Actual changed epoch cleared all cached entries");var oldLive=Collections.newSetFromMap(new IdentityHashMap<BakedModel,Boolean>());for(var ref:oldCacheModels){var model=ref.get();if(model!=null)oldLive.add(model);}var cache=(Map<?,?>)field("CACHE").get(null);int newModels=0;for(var entry:cache.values()){var models=entry.getClass().getDeclaredMethod("models");models.setAccessible(true);for(var model:(List<BakedModel>)models.invoke(entry)){check(!oldLive.contains(model),"No pre-reload baked model remains retained in visibility cache");newModels++;}}
        var result=new JsonObject();result.addProperty("status","PASS_ACTUAL_RESOURCE_RELOAD_OLD_CACHE_RELEASED");result.addProperty("oldModelIdentities",oldCacheModels.size());result.addProperty("newModelIdentitiesChecked",newModels);result.add("cache",new Gson().toJsonTree(CompositeVisualBoundsV127.snapshot()));oldCacheModels.clear();beforeEpoch=null;return result;
    }
    private static List<Vec3d> capture(CompositeRootBlock block,BlockState state,NbtCompound payload)throws Exception{
        var target=new Capture();VertexConsumerProvider providers=layer->target;var method=CompositeClient.class.getDeclaredMethod("renderParts",CompositeRootBlock.class,BlockState.class,MatrixStack.class,VertexConsumerProvider.class,int.class,int.class,String.class,BlockPos.class,NbtCompound.class);method.setAccessible(true);method.invoke(null,block,state,new MatrixStack(),providers,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null,BlockPos.ORIGIN,payload);return List.copyOf(target.vertices);
    }
    private static final class Capture implements VertexConsumer {
        final List<Vec3d> vertices=new ArrayList<>();double x,y,z;
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}public VertexConsumer color(int a,int b,int c,int d){return this;}public VertexConsumer texture(float a,float b){return this;}public VertexConsumer overlay(int a,int b){return this;}public VertexConsumer light(int a,int b){return this;}public VertexConsumer normal(float a,float b,float c){return this;}public void next(){vertices.add(new Vec3d(x,y,z));}public void fixedColor(int a,int b,int c,int d){}public void unfixColor(){}public void vertex(float x,float y,float z,float a,float b,float c,float d,float u,float v,int overlay,int light,float nx,float ny,float nz){vertices.add(new Vec3d(x,y,z));}
    }
    private static Field field(String name)throws Exception{var field=CompositeVisualBoundsV127.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
