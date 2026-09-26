package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import java.util.*;

/** Persistent and chunk-synced ownership links from one occupied cell to logical roots. */
public final class ArchitecturePartBlockEntity extends BlockEntity {
 static final int MAX_BINDINGS=16;
 private static final int REMOVALS_PER_TICK=256;
 private static final Identifier AIR=new Identifier("minecraft","air");
 private static final Comparator<Binding> ORDER=Comparator.comparingInt((Binding b)->b.root().getX()).thenComparingInt(b->b.root().getY()).thenComparingInt(b->b.root().getZ()).thenComparing(b->b.owner().toString());
 private static final Map<ServerWorld,Map<Long,Set<Long>>> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<ServerWorld,LinkedHashSet<Long>> REMOVALS=Collections.synchronizedMap(new WeakHashMap<>());
 private static boolean validationRegistered;
 private final List<Binding> bindings=new ArrayList<>();
 private List<GeometryRuntime.OwnedRoot> cachedShapeRoots=List.of();
 private VoxelShape cachedCollision=VoxelShapes.empty(),cachedOutline=VoxelShapes.empty();

 public record Binding(BlockPos root,Identifier owner) {public Binding {root=root.toImmutable();}}

 public ArchitecturePartBlockEntity(BlockPos pos,BlockState state){super(BloodborneBlocks.PART_BLOCK_ENTITY,pos,state);}

 static synchronized void registerValidation(){
  if(validationRegistered)return;validationRegistered=true;
  ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
   validatePending(world,chunk);
   for(BlockEntity entity:new ArrayList<>(chunk.getBlockEntities().values()))if(entity instanceof ArchitecturePartBlockEntity part){part.validateWhenRootLoads(world);queueRemoval(world,part.pos);}
  });
  ServerTickEvents.END_WORLD_TICK.register(world->{GeometryRuntime.drainGuestRecoveries(world);drainRemovals(world);});
 }

 public boolean bind(BlockPos root,Identifier owner){
  if(root.equals(pos)||owner==null)return false;
  bindings.removeIf(binding->binding.root().equals(root)&&!binding.owner().equals(owner));
  if(bindings.size()>=MAX_BINDINGS&&!hasBinding(root,owner))return false;
  Binding binding=new Binding(root,owner);if(bindings.contains(binding))return true;
  bindings.add(binding);bindings.sort(ORDER);changed();return true;
 }
 public boolean unbind(BlockPos root,Identifier owner){boolean removed=bindings.remove(new Binding(root,owner));if(removed)changed();return removed;}
 public boolean unbindRoot(BlockPos root){boolean removed=bindings.removeIf(binding->binding.root().equals(root));if(removed)changed();return removed;}
 public boolean hasBinding(BlockPos root,Identifier owner){return bindings.contains(new Binding(root,owner));}
 public List<Binding> bindings(){return List.copyOf(bindings);}
 public boolean isEmpty(){return bindings.isEmpty();}
 public BlockPos rootPos(){return bindings.isEmpty()?BlockPos.ORIGIN:bindings.get(0).root();}
 public Block ownerBlock(){Identifier owner=ownerId();ArchitectureBlock registered=owner.getNamespace().equals(BloodborneBlocks.ID)?BloodborneBlocks.registeredBlock(owner.getPath()):null;return registered==null?Registries.BLOCK.get(owner):registered;}
 Identifier ownerId(){return bindings.isEmpty()?AIR:bindings.get(0).owner();}

 private void changed(){invalidateShapeCache();markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
 private void invalidateShapeCache(){cachedShapeRoots=List.of();cachedCollision=VoxelShapes.empty();cachedOutline=VoxelShapes.empty();}

 VoxelShape guestShape(List<GeometryRuntime.OwnedRoot> roots,boolean outline){
  if(!cachedShapeRoots.equals(roots)){VoxelShape collision=VoxelShapes.empty(),selection=VoxelShapes.empty();for(var root:roots){collision=VoxelShapes.union(collision,GeometryRuntime.cellShape(root.state(),root.offset(),false));selection=VoxelShapes.union(selection,GeometryRuntime.cellShape(root.state(),root.offset(),true));}cachedShapeRoots=List.copyOf(roots);cachedCollision=collision;cachedOutline=selection;}
  return outline?cachedOutline:cachedCollision;
 }

 void validateWhenRootLoads(ServerWorld world){for(Binding binding:bindings)validateWhenRootLoads(world,binding.root());}
 private void validateWhenRootLoads(ServerWorld world,BlockPos root){long rootChunk=new ChunkPos(root).toLong();synchronized(PENDING){PENDING.computeIfAbsent(world,key->new HashMap<>()).computeIfAbsent(rootChunk,key->new HashSet<>()).add(pos.asLong());}}
 void validateOwner(ServerWorld world){validateAvailableRoots(world,null);}

 private void validateAvailableRoots(ServerWorld world,WorldChunk callbackChunk){
  boolean dirty=false;
  for(Binding binding:new ArrayList<>(bindings)){
   WorldChunk rootChunk=chunkAt(world,binding.root(),callbackChunk);
   if(rootChunk==null){validateWhenRootLoads(world,binding.root());continue;}
   if(!GeometryRuntime.ownsHelper(rootChunk.getBlockState(binding.root()),binding.root(),pos,binding.owner())){bindings.remove(binding);dirty=true;}
  }
  if(dirty){bindings.sort(ORDER);changed();}
  if(bindings.isEmpty())queueRemoval(world,pos);
 }

 private static WorldChunk chunkAt(ServerWorld world,BlockPos at,WorldChunk callbackChunk){ChunkPos wanted=new ChunkPos(at);if(callbackChunk!=null&&callbackChunk.getPos().equals(wanted))return callbackChunk;return world.getChunkManager().getWorldChunk(wanted.x,wanted.z);}

 private static void validatePending(ServerWorld world,WorldChunk rootChunk){
  long rootChunkKey=rootChunk.getPos().toLong();Set<Long> positions=null;
  synchronized(PENDING){Map<Long,Set<Long>> byChunk=PENDING.get(world);if(byChunk!=null){positions=byChunk.remove(rootChunkKey);if(byChunk.isEmpty())PENDING.remove(world);}}
  if(positions==null)return;
  for(long packed:positions){BlockPos carrier=BlockPos.fromLong(packed);WorldChunk carrierChunk=chunkAt(world,carrier,rootChunk);if(carrierChunk==null)continue;BlockEntity entity=carrierChunk.getBlockEntity(carrier);if(entity instanceof ArchitecturePartBlockEntity)queueRemoval(world,carrier);}
 }

 private static void queueRemoval(ServerWorld world,BlockPos carrier){synchronized(REMOVALS){REMOVALS.computeIfAbsent(world,key->new LinkedHashSet<>()).add(carrier.asLong());}}

 private static void drainRemovals(ServerWorld world){
  List<Long> batch=new ArrayList<>(REMOVALS_PER_TICK);
  synchronized(REMOVALS){LinkedHashSet<Long> queued=REMOVALS.get(world);if(queued==null)return;Iterator<Long> iterator=queued.iterator();while(iterator.hasNext()&&batch.size()<REMOVALS_PER_TICK){batch.add(iterator.next());iterator.remove();}if(queued.isEmpty())REMOVALS.remove(world);}
  for(long packed:batch){BlockPos carrier=BlockPos.fromLong(packed);WorldChunk chunk=chunkAt(world,carrier,null);if(chunk==null)continue;BlockEntity entity=chunk.getBlockEntity(carrier);if(!(entity instanceof ArchitecturePartBlockEntity part))continue;part.validateAvailableRoots(world,null);if(!part.isEmpty())continue;if(world.getBlockState(carrier).isOf(BloodborneBlocks.PART_BLOCK))GeometryRuntime.removeHelper(world,carrier);else world.removeBlockEntity(carrier);}
 }

 @Override protected void writeNbt(NbtCompound nbt){
  super.writeNbt(nbt);if(bindings.isEmpty())return;
  Binding first=bindings.get(0);nbt.putLong("Root",first.root().asLong());nbt.putString("Owner",first.owner().toString());
  if(bindings.size()>1){NbtList owners=new NbtList();for(Binding binding:bindings){NbtCompound entry=new NbtCompound();entry.putLong("Root",binding.root().asLong());entry.putString("Owner",binding.owner().toString());owners.add(entry);}nbt.put("Owners",owners);}
 }
 @Override public void readNbt(NbtCompound nbt){
  super.readNbt(nbt);bindings.clear();invalidateShapeCache();
  if(nbt.contains("Owners",NbtElement.LIST_TYPE)){
   NbtList owners=nbt.getList("Owners",NbtElement.COMPOUND_TYPE);if(owners.size()>MAX_BINDINGS)return;
   Binding previous=null;
   for(int i=0;i<owners.size();i++){Binding binding=parse(owners.getCompound(i));if(binding==null||binding.root().equals(pos)||bindings.stream().anyMatch(saved->saved.root().equals(binding.root()))||(previous!=null&&ORDER.compare(previous,binding)>=0)){bindings.clear();return;}bindings.add(binding);previous=binding;}
   Binding first=parse(nbt);if(bindings.isEmpty()||first==null||!first.equals(bindings.get(0))){bindings.clear();return;}
  }else if(nbt.contains("Root",NbtElement.LONG_TYPE)&&nbt.contains("Owner",NbtElement.STRING_TYPE)){
   Binding binding=parse(nbt);if(binding!=null&&!binding.root().equals(pos))bindings.add(binding);
  }
 }
 private static Binding parse(NbtCompound nbt){if(!nbt.contains("Root",NbtElement.LONG_TYPE)||!nbt.contains("Owner",NbtElement.STRING_TYPE))return null;Identifier owner=Identifier.tryParse(nbt.getString("Owner"));return owner==null?null:new Binding(BlockPos.fromLong(nbt.getLong("Root")),owner);}
 @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
 @Override public Packet<ClientPlayPacketListener> toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
