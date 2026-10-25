package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.*;

/** Runs once in a fresh dedicated JVM against the normally saved working map.
 * QA-only, never packaged with the production JAR. */
public final class Streaming7Checks implements ModInitializer {
 private boolean done;private Path out;private final JsonArray checks=new JsonArray();
 private NbtList savedRows;private int savedIndex,unloadTicks;private ChunkPos waitingChunk;
 private final JsonArray savedProgress=new JsonArray();
 private void ok(boolean value,String label){if(!value)throw new IllegalStateException(label);checks.add(label);}
 @Override public void onInitialize(){if(System.getProperty("dw.verify.output")==null)return;ServerTickEvents.END_SERVER_TICK.register(server->{
  if(done)return;out=Path.of(System.getProperty("dw.verify.output"));JsonObject report=new JsonObject();report.add("checks",checks);
  try{ServerWorld world=server.getOverworld();boolean preflight=Boolean.getBoolean("dw.verify.preflight"),reopenOnly=Boolean.getBoolean("dw.verify.reopenOnly"),denseOnly=Boolean.getBoolean("dw.verify.denseOnly");if(reopenOnly){if(!savedCityStep(world))return;}else if(!preflight)savedCity(world);if(!reopenOnly){if(!denseOnly){palette();syncValues(world);mechanisms(world);}densePalette(world);}server.save(false,true,true);report.addProperty("status",preflight?"PASS_TARGETED_PREFLIGHT":reopenOnly?"PASS_FRESH_REOPEN":"PASS_FRESH_REOPEN_AND_TARGETED_SYNC_PALETTE_CHECKS");}
  catch(Throwable e){e.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",e.toString());}
  done=true;try{Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(Exception e){throw new RuntimeException(e);}server.stop(false);
 });}
 /** A blocking full-chunk request also attaches vanilla status dependencies.
  * Let its one-tick UNKNOWN ticket expire and those dependencies unload before
  * requesting a distant probe. This is validation, not a five-location burst. */
 private boolean savedCityStep(ServerWorld w)throws Exception {
  if(savedRows==null)savedRows=NbtIo.readCompressed(out.resolve("expected.nbt").toFile()).getList("probes",NbtElement.COMPOUND_TYPE);
  if(waitingChunk!=null){
   if(++unloadTicks%80!=0)return false;
   if(w.getChunkManager().getWorldChunk(waitingChunk.x,waitingChunk.z)!=null){if(unloadTicks>=400)throw new IllegalStateException("Saved probe did not unload: "+waitingChunk);return false;}
   JsonObject row=new JsonObject();row.addProperty("unloadedChunk",waitingChunk.toString());row.addProperty("loadedChunkCount",w.getChunkManager().getLoadedChunkCount());savedProgress.add(row);writeSavedProgress();waitingChunk=null;
  }
  if(savedIndex==savedRows.size()){ok(savedRows.size()==5,"all five existing city probes reopened sequentially");return true;}
  NbtCompound row=savedRows.getCompound(savedIndex);int[] p=row.getIntArray("pos");ChunkPos probe=new ChunkPos(p[0]>>4,p[2]>>4);boolean preloaded=w.getChunkManager().getWorldChunk(probe.x,probe.z)!=null;savedCityRow(w,row);waitingChunk=preloaded?null:probe;unloadTicks=0;savedIndex++;
  JsonObject progress=new JsonObject();progress.addProperty("checkedProbe",savedIndex);progress.addProperty("chunk",probe.toString());progress.addProperty("alreadyLoadedBeforeProbe",preloaded);progress.addProperty("loadedChunkCount",w.getChunkManager().getLoadedChunkCount());progress.addProperty("heapUsed",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());savedProgress.add(progress);writeSavedProgress();return false;
 }
 private void writeSavedProgress()throws Exception {JsonObject progress=new JsonObject();progress.add("checks",checks);progress.add("events",savedProgress);Files.writeString(out.resolve("progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(progress)+"\n");}
 private void savedCity(ServerWorld w)throws Exception {
  NbtList rows=NbtIo.readCompressed(out.resolve("expected.nbt").toFile()).getList("probes",NbtElement.COMPOUND_TYPE);
  for(int i=0;i<rows.size();i++)savedCityRow(w,rows.getCompound(i));ok(rows.size()==5,"all five existing city probes reopened");
 }
 private void savedCityRow(ServerWorld w,NbtCompound row){
   NbtCompound saved=row.getCompound("state");int[] p=row.getIntArray("pos");BlockPos pos=new BlockPos(p[0],p[1],p[2]);WorldChunk chunk=w.getChunk(pos.getX()>>4,pos.getZ()>>4);
   BlockState state=Registries.BLOCK.get(new Identifier(saved.getString("Name"))).getDefaultState();NbtCompound properties=saved.getCompound("Properties");for(String key:properties.getKeys())state=property(state,key,properties.getString(key));
   ok(chunk.getBlockState(pos)==state,"fresh JVM exact saved city palette "+pos.toShortString());
   if(row.getBoolean("hasEntity")){
    ok(chunk.getBlockEntity(pos) instanceof CompositeBlockEntity,"saved city root exists "+pos.toShortString());CompositeBlockEntity own=(CompositeBlockEntity)chunk.getBlockEntity(pos);NbtCompound tag=row.getCompound("entity");
    ok(own.contributions().equals(CompositeData.savedContributions(tag))&&own.payload().equals(tag.getCompound("payload"))&&Objects.equals(own.resident(),tag.contains("resident",10)?CompositeData.owner(tag.getCompound("resident")):null),"fresh JVM exact city owner and typed payload "+pos.toShortString());
   }else ok(chunk.getBlockEntity(pos)==null,"static city cell has no added BE "+pos.toShortString());
   if(state.getBlock() instanceof StaticArchitectureBlock)ok(state.getCollisionShape(w,pos)==StaticArchitectureCatalog.recipe(state).collisionShape(),"saved city uses shared exact collision "+pos.toShortString());
 }
 @SuppressWarnings("unchecked") private void palette()throws Exception {
  Class<?> cls=Class.forName("me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette");var ctor=Arrays.stream(cls.getConstructors()).filter(c->c.getParameterCount()==3).findFirst().orElseThrow();
  Palette<BlockState> palette=(Palette<BlockState>)ctor.newInstance(Block.STATE_IDS,21,null);var entries=cls.getDeclaredField("entries");entries.setAccessible(true);var bits=cls.getDeclaredField("indexBits");bits.setAccessible(true);
  ok(((Object[])entries.get(palette)).length==4096&&bits.getInt(palette)==21,"global-width palette starts at 4096 without changing ID bits");
  for(int i=0;i<5000;i++)okIndex(palette.index(Block.STATE_IDS.get(i))==i,"palette insertion "+i);
  for(int i=0;i<5000;i++)okIndex(palette.get(i)==Block.STATE_IDS.get(i),"palette lookup "+i);
  ok(((Object[])entries.get(palette)).length>=5000,"existing palette growth remains valid beyond initial capacity");
  Palette<BlockState> small=(Palette<BlockState>)ctor.newInstance(Block.STATE_IDS,4,null);ok(((Object[])entries.get(small)).length==16,"ordinary small palette capacity unchanged");
 }
 private static void okIndex(boolean pass,String label){if(!pass)throw new IllegalStateException(label);}
 private static final class CountingEntity extends CompositeBlockEntity {
  int dirties;CountingEntity(BlockPos pos,BlockState state){super(pos,state);}
  @Override public void markDirty(){dirties++;super.markDirty();}
 }
 private void syncValues(ServerWorld w){
  BlockPos p=new BlockPos(30008,104,30008);BlockState state=CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState();NbtCompound payload=new NbtCompound();payload.putString("SyncCheck","original");
  ObjectInstance instance=CompositeRuntime.instance(w,p,state,UUID.randomUUID(),payload);Owner owner=instance.owner();List<CompositeData.Contribution> values=List.of(new CompositeData.Contribution(owner,instance.rootData(),instance.cells().get(Cell.ORIGIN)));
  CountingEntity copy=new CountingEntity(p,state);copy.set(owner,values,payload);Object revision=copy.cleanupPayloadRevision();int dirties=copy.dirties;
  List<CompositeData.Contribution> canonical=List.copyOf(new ArrayList<>(values));Owner equal=new Owner(owner.instanceId(),owner.registryId(),owner.root());
  ok(!copy.applySyncedRoot(equal,canonical,payload.copy())&&copy.dirties==dirties&&copy.cleanupPayloadRevision()==revision&&copy.contributions()==canonical&&copy.resident()==equal,"equal owner sync keeps payload, skips dirty work and adopts canonical graph");
  NbtCompound changed=payload.copy();changed.putDouble("MountY",.375);
  ok(copy.applySyncedRoot(owner,canonical,changed)&&copy.dirties==dirties+1&&copy.payload().equals(changed),"changed typed payload still applies");changed.putString("SyncCheck","caller mutation");ok(!copy.payload().equals(changed),"changed sync payload remains independently owned");
  ObjectInstance replacement=CompositeRuntime.instance(w,p,state,UUID.randomUUID(),payload);List<CompositeData.Contribution> next=List.of(new CompositeData.Contribution(replacement.owner(),replacement.rootData(),replacement.cells().get(Cell.ORIGIN)));
  ok(copy.applySyncedRoot(replacement.owner(),next,payload)&&copy.resident().equals(replacement.owner())&&copy.contributions()==next,"replacement UUID and owner graph still apply");
  copy.set(owner,next,payload);int prior=copy.dirties;ok(copy.applySyncedRoot(replacement.owner(),next,payload)&&copy.dirties==prior+1,"resident authority is repaired even with an identical contribution graph and payload");
  ObjectInstance opened=CompositeRuntime.instance(w,p,state.with(CompositeRootBlock.OPEN,true),replacement.owner().instanceId(),payload);List<CompositeData.Contribution> open=List.of(new CompositeData.Contribution(opened.owner(),opened.rootData(),opened.cells().get(Cell.ORIGIN)));
  ok(copy.applySyncedRoot(opened.owner(),open,payload)&&copy.contributions()==open,"changed door geometry still replaces the full canonical contribution graph");
  ok(!CompositeInitialSync.visualChange(true,false,true,copy,open),"explicit initial identical vanilla BE needs no visual update");
  ok(CompositeInitialSync.visualChange(false,false,true,copy,open),"first live update after omitted initial ledger still invalidates geometry");
  ok(!CompositeInitialSync.visualChange(false,true,false,copy,open),"unchanged existing ledger stays a no-op");
  ok(CompositeInitialSync.visualChange(true,false,true,null,open),"foreign carrier without native BE always keeps its initial update");
 }
 private void mechanisms(ServerWorld w)throws Exception {
  BlockPos p=new BlockPos(30008,104,30008);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((p.getX()>>4)+x,(p.getZ()>>4)+z);
  for(BlockPos q:BlockPos.iterate(p.add(-5,-1,-5),p.add(5,8,5)))w.setBlockState(q,q.getY()==p.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);
  BlockState door=CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState();committed(CompositeRuntime.place(w,p,door,UUID.randomUUID(),null),"door placement");CompositeBlockEntity root=(CompositeBlockEntity)w.getBlockEntity(p);Owner owner=root.resident();checkOwner(w,p,owner);witness(w,p,root);
  committed(CompositeRuntime.transition(w,owner,door.with(CompositeRootBlock.OPEN,true),null),"door opening");ok(w.getBlockState(p).get(CompositeRootBlock.OPEN),"door opened");checkOwner(w,p,owner);copySync(w,p);
  committed(CompositeRuntime.transition(w,owner,door,null),"door closing");checkOwner(w,p,owner);committed(CompositeRuntime.remove(w,owner,null,false),"door destruction");ok(w.getBlockState(p).isAir(),"destroyed door root removed");
  BlockState stair=CompositeArchitecture.kindBlock("source_stairs_polished_deepslate_stairs_bottom").getDefaultState();committed(CompositeRuntime.place(w,p,stair,UUID.randomUUID(),null,ImportedTypeAliases.markPayload(new NbtCompound())),"stair placement");owner=((CompositeBlockEntity)w.getBlockEntity(p)).resident();checkOwner(w,p,owner);copySync(w,p);
  BlockState rotated=w.getBlockState(p).with(CompositeRootBlock.ROTATION,2);committed(CompositeRuntime.transition(w,owner,rotated,null),"stair rotation");checkOwner(w,p,owner);copySync(w,p);committed(CompositeRuntime.remove(w,owner,null,false),"stair destruction");ok(w.getBlockState(p).isAir(),"destroyed stair root removed");
 }
 private void copySync(ServerWorld w,BlockPos p){CompositeBlockEntity actual=(CompositeBlockEntity)w.getBlockEntity(p),copy=new CompositeBlockEntity(p,w.getBlockState(p));copy.readNbt(actual.createNbt());Object revision=copy.cleanupPayloadRevision();ok(!copy.applySyncedRoot(actual.resident(),actual.contributions(),actual.payload())&&copy.cleanupPayloadRevision()==revision,"vanilla BE followed by identical owner sync is a no-op for "+w.getBlockState(p));}
 private void witness(ServerWorld w,BlockPos p,CompositeBlockEntity root){
  WorldChunk chunk=w.getChunk(p.getX()>>4,p.getZ()>>4);Cell cell=CompositeData.cell(p);
  try(var scope=CompositeInitialSync.begin(w,chunk.getPos(),true)){
   var capture=CompositeInitialSync.capturePacket(chunk);ChunkDataS2CPacket packet=null;
   try{packet=new ChunkDataS2CPacket(chunk,w.getLightingProvider(),null,null);}finally{capture.endPacket(packet);}
   scope.confirm(packet);ok(scope.covers(cell,root.contributions()),"exact actual vanilla packet covers full root owner and typed payload");
   var ledger=CompositeLedger.get(w);var all=ledger.chunkCells(chunk.getPos());var selected=CompositeNetworking.initialCells(ledger,chunk.getPos());ok(!selected.isEmpty()&&selected.size()<all.size()&&all.stream().allMatch(c->selected.contains(c)||scope.covers(c,ledger.at(c))),"actual native packet drops proven root/helper duplicates and keeps every uncovered carrier plus a shard marker");
   ok(!scope.covers(cell,List.of())&&!scope.covers(new Cell(cell.x()+1,cell.y(),cell.z()),root.contributions()),"different contributions and missing foreign carrier keep full sync");
   scope.confirm(new Object());ok(!scope.covers(cell,root.contributions()),"cached or different packet cannot borrow another packet witness");scope.confirm(packet);
   java.util.concurrent.atomic.AtomicReference<NbtCompound> actual=new java.util.concurrent.atomic.AtomicReference<>();packet.getChunkData().getBlockEntities(chunk.getPos().x,chunk.getPos().z).accept((pos,type,tag)->{if(pos.equals(p))actual.set(tag);});
   NbtCompound tag=actual.get();ok(tag!=null,"root NBT is present in the exact vanilla packet");NbtCompound original=tag.getCompound("payload");NbtCompound changed=original.copy();changed.putInt("WitnessMismatch",1);tag.put("payload",changed);ok(!scope.covers(cell,root.contributions()),"different native typed payload cannot suppress owner sync");tag.put("payload",original);
   byte[] owners=tag.getByteArray(CompositeOwnerNbt.KEY);tag.putByteArray(CompositeOwnerNbt.KEY,owners.clone());ok(!scope.covers(cell,root.contributions()),"replaced native owner envelope falls back to full sync");
  }ok(!CompositeInitialSync.active(),"completed witness drops all thread-local packet/chunk references");
  try(var scope=CompositeInitialSync.begin(w,chunk.getPos(),false)){ok(CompositeInitialSync.capturePacket(chunk)==null&&!scope.covers(cell,root.contributions()),"cached vanilla packets keep conservative full sync");}
  try{try(var scope=CompositeInitialSync.begin(w,chunk.getPos(),true)){throw new IllegalStateException("injected witness failure");}}catch(IllegalStateException expected){ok(!CompositeInitialSync.active(),"exceptional send also releases witness references");}
 }
 private void checkOwner(ServerWorld w,BlockPos p,Owner owner){
  CompositeBlockEntity root=(CompositeBlockEntity)w.getBlockEntity(p);ObjectInstance expected=CompositeRuntime.instance(w,p,w.getBlockState(p),owner.instanceId(),root.payload());
  for(var entry:expected.cells().entrySet()){BlockPos pos=p.add(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());var saved=CompositeRuntime.contributions(w,pos).stream().filter(c->c.owner().equals(owner)).findFirst().orElseThrow();okIndex(saved.shape().equals(entry.getValue()),"exact owner collision and selection "+pos);okIndex(!VoxelShapes.matchesAnywhere(CompositeRuntime.shape(entry.getValue().collision()),w.getBlockState(pos).getCollisionShape(w,pos),BooleanBiFunction.NOT_SAME),"exact occupied collision volume "+pos);}
  ok(true,"exact collision and selection after "+w.getBlockState(p));
 }
 private void committed(TransactionCore.Result result,String label){ok(result.outcome()==TransactionCore.Outcome.COMMITTED,label+": "+result);}
 private void densePalette(ServerWorld w)throws Exception {
  List<BlockState> states=new ArrayList<>();for(int i=0;i<Math.min(513,StaticArchitectureCatalog.recipes().size());i++)states.add(StaticArchitectureCatalog.state(i));ok(states.size()==513,"dense palette has 513 distinct architectural states");
  ChunkPos pos=new ChunkPos(1875,1875);WorldChunk chunk=w.getChunk(pos.x,pos.z);
  for(int i=0;i<4096;i++)chunk.setBlockState(new BlockPos(pos.getStartX()+(i&15),80+((i>>8)&15),pos.getStartZ()+((i>>4)&15)),states.get(i%states.size()),false);
  Path file=out.resolve("dense-palette.nbt");NbtIo.writeCompressed(ChunkSerializer.serialize(w,chunk),file.toFile());ProtoChunk decoded=ChunkSerializer.deserialize(w,w.getPointOfInterestStorage(),pos,NbtIo.readCompressed(file.toFile()));ok(decoded instanceof WrapperProtoChunk,"native saved dense chunk reopens as fresh WorldChunk");WorldChunk reopened=((WrapperProtoChunk)decoded).getWrappedChunk();
  for(int i=0;i<4096;i++){BlockPos p=new BlockPos(pos.getStartX()+(i&15),80+((i>>8)&15),pos.getStartZ()+((i>>4)&15));BlockState expected=states.get(i%states.size());okIndex(reopened.getBlockState(p)==expected,"dense roundtrip state "+i);okIndex(expected.getCollisionShape(reopened,p)==StaticArchitectureCatalog.recipe(expected).collisionShape(),"shared collision after dense roundtrip "+i);}
  ok(true,"all 4096 dense cells retain exact states and shared collisions through native NBT save/reopen");
 }
 private static BlockState property(BlockState state,String key,String value){return apply(state,state.getBlock().getStateManager().getProperty(key),value);}
 private static <T extends Comparable<T>> BlockState apply(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
}
