package dev.dreamwalker.bloodbornedw.restorationqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
/** One explicitly requested in-place repair. This QA tool is never shipped. */
public final class CityRestorationApply implements ModInitializer {
 private final Gson json=new GsonBuilder().setPrettyPrinting().create();private Path out;private List<JsonObject> posts,lamps;private final JsonArray results=new JsonArray();private int phase,index,age;private boolean done;
 @Override public void onInitialize(){try{
  out=Path.of(System.getProperty("dw.restore.apply.plan"));posts=rows("post-replacement-plan.json","posts");lamps=rows("lantern-replacement-plan.json","lamps");
 }catch(Exception e){throw new RuntimeException(e);}
 ServerTickEvents.END_SERVER_TICK.register(server->{if(done||++age<40)return;var w=server.getOverworld();
  if(phase==0&&index>=posts.size()){phase++;index=0;}if(phase==1&&index>=lamps.size()){done=true;write();server.stop(false);return;}
  var row=(phase==0?posts:lamps).get(index++);var result=new JsonObject();result.addProperty("kind",phase==0?"post":"lantern");result.add("source",row);try{if(phase==0)post(w,row,result);else lamp(w,row,result);}catch(Throwable e){e.printStackTrace();result.addProperty("status","ERROR_PRESERVED_OR_ROLLED_BACK");result.addProperty("reason",e.toString());}results.add(result);if(index%20==0)write();
 });}
 private List<JsonObject> rows(String file,String key)throws Exception{var list=new ArrayList<JsonObject>();for(var row:JsonParser.parseString(Files.readString(out.resolve(file))).getAsJsonObject().getAsJsonArray(key))list.add(row.getAsJsonObject());return list;}
 private void write(){try{var n=new JsonObject();n.addProperty("status",done?"COMPLETE":"APPLYING");n.addProperty("phase",phase);n.addProperty("cursor",index);n.add("objects",results);Files.writeString(out.resolve("map-apply.json"),json.toJson(n));}catch(Exception e){throw new RuntimeException(e);}}
 private static BlockPos pos(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
 private BlockState state(JsonObject row){Map<String,String> props=new TreeMap<>();row.getAsJsonObject("properties").entrySet().forEach(e->props.put(e.getKey(),e.getValue().getAsString()));return CompositeData.state(new BlockData(row.get("registryId").getAsString(),props,new byte[0]));}
 private record Before(BlockData data,VoxelShape collision,int opacity){}
 private Map<BlockPos,Before> snapshot(ServerWorld w,Collection<BlockPos> cells){Map<BlockPos,Before> before=new LinkedHashMap<>();for(var p:cells){w.getChunk(p.getX()>>4,p.getZ()>>4);var s=w.getBlockState(p);var be=w.getBlockEntity(p);if(be!=null)throw new IllegalStateException("Real block data at repair cell "+p);var data=new NbtCompound();if(s.getBlock() instanceof NativePhysicsBlock)data.putInt(NativeVisuals.ITEM_KEY,NativeVisuals.required(w,p));before.put(p,new Before(CompositeData.data(s,data),s.getCollisionShape(w,p),s.getOpacity(w,p)));}return before;}
 private Map<BlockPos,NbtCompound> guests(ServerWorld w,Collection<BlockPos> cells){Map<BlockPos,NbtCompound> found=new LinkedHashMap<>();for(var p:cells)for(var c:CompositeRuntime.contributions(w,p)){var root=CompositeData.pos(c.owner().root());w.getChunk(root.getX()>>4,root.getZ()>>4);var be=w.getBlockEntity(root);if(be!=null)found.putIfAbsent(root,be.createNbtWithIdentifyingData());}return found;}
 private void post(ServerWorld w,JsonObject row,JsonObject report){var root=pos(row.getAsJsonArray("root"));var clears=new LinkedHashMap<BlockPos,BlockData>();
  for(var raw:row.getAsJsonArray("before")){var b=raw.getAsJsonObject();var p=root.add(pos(b.getAsJsonArray("offset")));w.getChunk(p.getX()>>4,p.getZ()>>4);var expected=b.getAsJsonObject("state");if(!w.getBlockState(p).equals(state(expected)))throw new IllegalStateException("Post input changed at "+p);if(expected.has("nativeVisual")&&NativeVisuals.required(w,p)!=expected.get("nativeVisual").getAsInt())throw new IllegalStateException("Post visual changed at "+p);clears.put(p,CompositeData.data(Blocks.AIR.getDefaultState(),new NbtCompound()));}
  int v=row.get("variant").getAsInt();var s=CompositeArchitecture.kindBlock("source_street_lamp_post").getDefaultState().with(CompositeRootBlock.VARIANT,v%16).with(ImportedArtworkBlock.ART_BANK,v/16);var payload=ImportedTypeAliases.markPayload(null);payload.putInt(ImportedWholeSourceOpticsV127.CONTEXT,1);var obj=CompositeRuntime.instance(w,root,s,UUID.randomUUID(),payload);install(w,obj,clears,report,true);
 }
 private void lamp(ServerWorld w,JsonObject row,JsonObject report){BlockPos root=pos(row.getAsJsonArray("root"));var pose=StaticArchitectureCatalog.poses().stream().filter(p->p.key().equals(row.get("pose").getAsString())).findFirst().orElseThrow();var clears=new LinkedHashMap<BlockPos,BlockData>();
  for(var offset:pose.footprint().keySet()){var p=root.add(offset.x(),offset.y(),offset.z());w.getChunk(p.getX()>>4,p.getZ()>>4);var s=w.getBlockState(p);if(!(s.getBlock() instanceof StaticArchitectureBlock))continue;var recipe=StaticArchitectureCatalog.recipe(s);int selected=-1;for(int i=0;i<recipe.fragments().size();i++){var f=recipe.fragments().get(i);if(f.pose()==pose&&p.add(f.origin().x(),f.origin().y(),f.origin().z()).equals(root)){selected=i;break;}}if(selected>=0)clears.put(p,CompositeData.data(recipe.without(selected),new NbtCompound()));}
  if(clears.isEmpty()){report.addProperty("status","ALREADY_RETAINED_OR_CHANGED");return;}
  // If the original root also holds independent artwork, use a freed helper
  // as anchor and compensate its local shift. The visual/physics stay put.
  BlockPos anchor=root;var remaining=clears.get(anchor);if(remaining==null||!CompositeData.state(remaining).isAir())anchor=clears.entrySet().stream().filter(e->CompositeData.state(e.getValue()).isAir()).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalStateException("No free lantern anchor; retain original"));
  var payload=pose.payload();if(!anchor.equals(root)){var old=CompositeSourceShift.local(payload);var delta=root.subtract(anchor);var local=new CompositeSourceShift.Shift(delta.getX(),delta.getY(),delta.getZ()).rotate(-pose.state().get(CompositeRootBlock.ROTATION));var shift=new NbtList();shift.add(NbtDouble.of(old.x()+local.x()));shift.add(NbtDouble.of(old.y()+local.y()));shift.add(NbtDouble.of(old.z()+local.z()));payload.put("SourceShift",shift);}
  var obj=CompositeRuntime.instance(w,anchor,pose.state(),UUID.randomUUID(),payload);install(w,obj,clears,report,false);
 }
 private void install(ServerWorld w,ObjectInstance obj,Map<BlockPos,BlockData> nativeChanges,JsonObject report,boolean compareOpacity){
  Set<BlockPos> touched=new LinkedHashSet<>(nativeChanges.keySet());for(var p:obj.cells().keySet())touched.add(CompositeData.pos(obj.owner().root().add(p)));var before=snapshot(w,touched);var protectedData=guests(w,touched);
  var result=SourceConversionScope.initialInstances(Set.of(obj.owner().instanceId()),()->WorldEditObjectTransactions.replace(w,List.of(),List.of(obj),nativeChanges));
  if(result.outcome()!=TransactionCore.Outcome.COMMITTED){report.addProperty("status","PRESERVED_LOCAL_EXCEPTION");report.addProperty("reason",result.reason());return;}
  String failed=null;for(var e:before.entrySet()){var now=w.getBlockState(e.getKey());if(VoxelShapes.matchesAnywhere(e.getValue().collision,now.getCollisionShape(w,e.getKey()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME)){failed="collision changed at "+e.getKey();break;}if(compareOpacity&&now.getOpacity(w,e.getKey())!=e.getValue().opacity){failed="opacity changed at "+e.getKey()+" before="+e.getValue().opacity+" after="+now.getOpacity(w,e.getKey());break;}}
  for(var e:protectedData.entrySet())if(w.getBlockEntity(e.getKey())==null||!e.getValue().equals(w.getBlockEntity(e.getKey()).createNbtWithIdentifyingData())){failed="protected owner data changed at "+e.getKey();break;}
  if(failed!=null){var restore=new LinkedHashMap<BlockPos,BlockData>();before.forEach((p,b)->restore.put(p,b.data));var rollback=WorldEditObjectTransactions.replace(w,List.of(obj.owner()),List.of(),restore);if(rollback.outcome()!=TransactionCore.Outcome.COMMITTED)throw new IllegalStateException("REPAIR ROLLBACK FAILED: "+failed+" / "+rollback.reason());report.addProperty("status","ROLLED_BACK_LOCAL_EXCEPTION");report.addProperty("reason",failed);return;}
  report.addProperty("status","COMMITTED");report.addProperty("cells",touched.size());report.addProperty("protectedOwnersVerified",protectedData.size());report.addProperty("root",CompositeData.pos(obj.owner().root()).toShortString());
 }
}
