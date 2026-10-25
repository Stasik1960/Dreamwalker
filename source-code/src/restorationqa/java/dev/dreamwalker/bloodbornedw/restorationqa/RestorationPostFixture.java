package dev.dreamwalker.bloodbornedw.restorationqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
/** A temporary side-by-side fixture; never writes the user's map. */
public final class RestorationPostFixture implements ClientModInitializer{
 private int ticks;private volatile boolean installed,independent;private volatile String failure;private String referenceIssue;private Path out;private static final BlockPos ROOT=new BlockPos(15080,200,15080);
 public void onInitializeClient(){out=Path.of(System.getProperty("dw.restore.qa.output"));ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.world==null||c.player==null||c.getServer()==null)return;try{tick(c);}catch(Throwable e){failure=e.toString();e.printStackTrace();finish(c);}});}
 private void tick(MinecraftClient c)throws Exception{
  if(++ticks==1)c.getServer().execute(()->{try{install(c);installed=true;}catch(Throwable e){failure=e.toString();e.printStackTrace();}});
  if(failure!=null){finish(c);return;}if(!installed){ticks=1;return;}
  if(ticks==120)shot(c,"post-new-left-original-right.png");
  if(ticks==130)c.getServer().execute(()->LanternMotion.pulse(c.getServer().getOverworld(),ROOT.add(-1,6,0)));
  if(ticks==136)shot(c,"post-lantern-sway.png");
  if(ticks==190)c.getServer().execute(()->{var w=c.getServer().getOverworld();var at=ROOT.add(-1,6,0);var be=(CompositeBlockEntity)w.getBlockEntity(at);CompositeRuntime.remove(w,be.resident(),null,false);independent=w.getBlockState(ROOT).isOf(CompositeArchitecture.kindBlock("source_street_lamp_post"))&&w.getBlockEntity(ROOT.add(1,6,0)) instanceof CompositeBlockEntity;});
  if(ticks==240){shot(c,"post-one-lantern-removed.png");finish(c);}
 }
 private void install(MinecraftClient c)throws Exception{
  var w=c.getServer().getOverworld();var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.clearStatusEffects();p.setHealth(p.getMaxHealth());w.setTimeOfDay(6000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,c.getServer());
  for(int x=-5;x<=13;x++)for(int z=-4;z<=4;z++)w.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState());
  var doc=JsonParser.parseString(Files.readString(Path.of(System.getProperty("dw.restore.qa.plan")).resolve("post-replacement-plan.json"))).getAsJsonObject();JsonObject chosen=null;for(var row:doc.getAsJsonArray("posts"))if(row.getAsJsonObject().get("variant").getAsInt()==31){chosen=row.getAsJsonObject();break;}if(chosen==null)throw new IllegalStateException("Variant31 missing");
  var nativeChanges=new LinkedHashMap<BlockPos,BlockData>();for(var v:chosen.getAsJsonArray("before")){var row=v.getAsJsonObject();var offset=row.getAsJsonArray("offset");var state=row.getAsJsonObject("state");Map<String,String> props=new TreeMap<>();state.getAsJsonObject("properties").entrySet().forEach(e->props.put(e.getKey(),e.getValue().getAsString()));var data=new NbtCompound();if(state.has("nativeVisual"))data.putInt(NativeVisuals.ITEM_KEY,state.get("nativeVisual").getAsInt());nativeChanges.put(ROOT.add(8+offset.get(0).getAsInt(),offset.get(1).getAsInt(),offset.get(2).getAsInt()),new BlockData(state.get("registryId").getAsString(),props,CompositeData.bytes(data)));}
  // Reconstruct trusted old compact carriers directly, rather than asking
  // the logical object writer to create unsupported legacy compact parts.
  for(var entry:nativeChanges.entrySet())try{var state=CompositeData.state(entry.getValue());int flags=Block.FORCE_STATE|Block.NOTIFY_LISTENERS|Block.SKIP_DROPS;if(state.getBlock() instanceof NativePhysicsBlock){int visual=CompositeData.nbt(entry.getValue().blockEntityNbt()).getInt(NativeVisuals.ITEM_KEY);if(!NativeVisuals.write(w,entry.getKey(),visual,flags))throw new IllegalStateException("Reference native write");}else{w.setBlockState(entry.getKey(),state,flags);if(!w.getBlockState(entry.getKey()).equals(state))throw new IllegalStateException("Reference compact write "+entry.getKey());}}catch(RuntimeException e){referenceIssue=e.toString();e.printStackTrace();}
  var post=CompositeArchitecture.kindBlock("source_street_lamp_post").getDefaultState().with(CompositeRootBlock.VARIANT,15).with(ImportedArtworkBlock.ART_BANK,1);var objects=new ArrayList<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance>();var ids=new HashSet<UUID>();var id=UUID.randomUUID();ids.add(id);objects.add(CompositeRuntime.instance(w,ROOT,post,id,ImportedTypeAliases.markPayload(null)));
  var pose=StaticArchitectureCatalog.poses().stream().filter(v->v.key().equals("p7ac55f8cf55eb2afd65d")).findFirst().orElseThrow();for(int dx:new int[]{-1,1}){id=UUID.randomUUID();ids.add(id);objects.add(CompositeRuntime.instance(w,ROOT.add(dx,6,0),pose.state(),id,pose.payload()));}require(dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(ids,()->WorldEditObjectTransactions.replace(w,List.of(),objects,Map.of())));
  p.teleport(w,ROOT.getX()+4.5,ROOT.getY()+3,ROOT.getZ()+13,180,0);c.execute(()->c.options.hudHidden=true);
 }
 private void require(TransactionCore.Result r){if(r.outcome()!=TransactionCore.Outcome.COMMITTED)throw new IllegalStateException(r.reason());}
 private void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),t->{});}
 private void finish(MinecraftClient c){try{var n=new JsonObject();n.addProperty("status",failure==null&&installed&&independent?"PASS":"FAIL");n.addProperty("installed",installed);n.addProperty("lanternRemovedIndependently",independent);n.addProperty("referenceIssue",referenceIssue);n.addProperty("failure",failure);Files.writeString(out.resolve("post-fixture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(n));}catch(Exception e){throw new RuntimeException(e);}c.scheduleStop();}
}
