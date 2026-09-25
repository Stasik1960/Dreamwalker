package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/** Persistent and chunk-synced ownership link from an occupied cell to its logical root. */
public final class ArchitecturePartBlockEntity extends BlockEntity {
 private static final int REMOVALS_PER_TICK=256;
 private static final Map<ServerWorld,Map<Long,Set<Long>>> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<ServerWorld,LinkedHashSet<Long>> REMOVALS=Collections.synchronizedMap(new WeakHashMap<>());
 private static boolean validationRegistered;
 private BlockPos root=BlockPos.ORIGIN;
 private Identifier owner=new Identifier("minecraft","air");

 public ArchitecturePartBlockEntity(BlockPos pos,BlockState state){super(BloodborneBlocks.PART_BLOCK_ENTITY,pos,state);}

 static synchronized void registerValidation(){
  if(validationRegistered)return;validationRegistered=true;
  ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
   validatePending(world,chunk);
   for(BlockEntity entity:new ArrayList<>(chunk.getBlockEntities().values()))if(entity instanceof ArchitecturePartBlockEntity part){
    WorldChunk rootChunk=chunkAt(world,part.root,chunk);
    if(rootChunk==null)part.validateWhenRootLoads(world);else part.validateAgainst(world,rootChunk);
   }
  });
  ServerTickEvents.END_WORLD_TICK.register(ArchitecturePartBlockEntity::drainRemovals);
 }

 public void bind(BlockPos root,Identifier owner){this.root=root.toImmutable();this.owner=owner;markDirty();}
 public BlockPos rootPos(){return root;}
 public Block ownerBlock(){ArchitectureBlock registered=owner.getNamespace().equals(BloodborneBlocks.ID)?BloodborneBlocks.registeredBlock(owner.getPath()):null;return registered==null?Registries.BLOCK.get(owner):registered;}
 Identifier ownerId(){return owner;}

 void validateWhenRootLoads(ServerWorld world){
  long rootChunk=new ChunkPos(root).toLong();synchronized(PENDING){PENDING.computeIfAbsent(world,key->new HashMap<>()).computeIfAbsent(rootChunk,key->new HashSet<>()).add(pos.asLong());}
 }

 void validateOwner(ServerWorld world){
  WorldChunk rootChunk=chunkAt(world,root,null);
  if(rootChunk==null){validateWhenRootLoads(world);return;}
  validateAgainst(world,rootChunk);
 }

 private void validateAgainst(ServerWorld world,WorldChunk rootChunk){
  BlockState rootState=rootChunk.getBlockState(root);
  if(!GeometryRuntime.ownsHelper(rootState,root,pos,owner))queueRemoval(world,pos);
 }

 private static WorldChunk chunkAt(ServerWorld world,BlockPos at,WorldChunk callbackChunk){
  ChunkPos wanted=new ChunkPos(at);
  if(callbackChunk!=null&&callbackChunk.getPos().equals(wanted))return callbackChunk;
  // ServerChunkManager#getWorldChunk is getChunkNow: it uses CompletableFuture#getNow
  // and returns null while a holder is still being promoted. It never waits for loading.
  return world.getChunkManager().getWorldChunk(wanted.x,wanted.z);
 }

 private static void validatePending(ServerWorld world,WorldChunk rootChunk){
  long rootChunkKey=rootChunk.getPos().toLong();
  Set<Long> positions=null;synchronized(PENDING){Map<Long,Set<Long>> byChunk=PENDING.get(world);if(byChunk!=null){positions=byChunk.remove(rootChunkKey);if(byChunk.isEmpty())PENDING.remove(world);}}
  if(positions==null)return;
  for(long packed:positions){
   BlockPos helper=BlockPos.fromLong(packed);WorldChunk helperChunk=chunkAt(world,helper,rootChunk);
   if(helperChunk==null)continue;
   BlockEntity entity=helperChunk.getBlockEntity(helper);
   if(entity instanceof ArchitecturePartBlockEntity part&&new ChunkPos(part.root).toLong()==rootChunkKey)part.validateAgainst(world,rootChunk);
  }
 }

 private static void queueRemoval(ServerWorld world,BlockPos helper){
  synchronized(REMOVALS){REMOVALS.computeIfAbsent(world,key->new LinkedHashSet<>()).add(helper.asLong());}
 }

 private static void drainRemovals(ServerWorld world){
  List<Long> batch=new ArrayList<>(REMOVALS_PER_TICK);
  synchronized(REMOVALS){
   LinkedHashSet<Long> queued=REMOVALS.get(world);
   if(queued==null)return;
   Iterator<Long> iterator=queued.iterator();
   while(iterator.hasNext()&&batch.size()<REMOVALS_PER_TICK){batch.add(iterator.next());iterator.remove();}
   if(queued.isEmpty())REMOVALS.remove(world);
  }
  for(long packed:batch){
   BlockPos helper=BlockPos.fromLong(packed);WorldChunk helperChunk=chunkAt(world,helper,null);
   if(helperChunk==null)continue;
   BlockEntity entity=helperChunk.getBlockEntity(helper);
   if(!(entity instanceof ArchitecturePartBlockEntity part))continue;
   WorldChunk rootChunk=chunkAt(world,part.root,null);
   if(rootChunk==null){part.validateWhenRootLoads(world);continue;}
   BlockState rootState=rootChunk.getBlockState(part.root);
   if(!GeometryRuntime.ownsHelper(rootState,part.root,helper,part.owner))world.removeBlock(helper,false);
  }
 }

 @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putLong("Root",root.asLong());nbt.putString("Owner",owner.toString());}
 @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);root=BlockPos.fromLong(nbt.getLong("Root"));Identifier parsed=Identifier.tryParse(nbt.getString("Owner"));owner=parsed==null?new Identifier("minecraft","air"):parsed;}
 @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
 @Override public Packet<ClientPlayPacketListener> toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
