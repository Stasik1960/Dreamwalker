package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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
import net.minecraft.world.World;
import java.util.*;

/** Persistent and chunk-synced ownership link from an occupied cell to its logical root. */
public final class ArchitecturePartBlockEntity extends BlockEntity {
 private static final Map<ServerWorld,Map<Long,Set<Long>>> PENDING=Collections.synchronizedMap(new WeakHashMap<>());
 static {ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->validatePending(world,chunk.getPos().toLong()));}
 private BlockPos root=BlockPos.ORIGIN;
 private Identifier owner=new Identifier("minecraft","air");

 public ArchitecturePartBlockEntity(BlockPos pos,BlockState state){super(BloodborneBlocks.PART_BLOCK_ENTITY,pos,state);}

 public void bind(BlockPos root,Identifier owner){this.root=root.toImmutable();this.owner=owner;markDirty();}
 public BlockPos rootPos(){return root;}
 public Block ownerBlock(){return Registries.BLOCK.get(owner);}

 void validateWhenRootLoads(ServerWorld world){
  long rootChunk=new ChunkPos(root).toLong();synchronized(PENDING){PENDING.computeIfAbsent(world,key->new HashMap<>()).computeIfAbsent(rootChunk,key->new HashSet<>()).add(pos.asLong());}
 }

 void validateOwner(ServerWorld world){
  if(!world.isChunkLoaded(root))return;BlockState rootState=world.getBlockState(root);
  if(!(rootState.getBlock() instanceof ArchitectureBlock)||!rootState.isOf(ownerBlock()))world.removeBlock(pos,false);
 }

 private static void validatePending(ServerWorld world,long rootChunk){
  Set<Long> positions=null;synchronized(PENDING){Map<Long,Set<Long>> byChunk=PENDING.get(world);if(byChunk!=null){positions=byChunk.remove(rootChunk);if(byChunk.isEmpty())PENDING.remove(world);}}
  if(positions==null)return;
  for(long packed:positions){BlockPos helper=BlockPos.fromLong(packed);if(!world.isChunkLoaded(helper))continue;BlockEntity entity=world.getBlockEntity(helper);if(entity instanceof ArchitecturePartBlockEntity part&&new ChunkPos(part.root).toLong()==rootChunk)part.validateOwner(world);}
 }

 @Override public void setWorld(World world){
  super.setWorld(world);
  if(!world.isClient)world.scheduleBlockTick(pos,BloodborneBlocks.PART_BLOCK,1);
 }

 @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putLong("Root",root.asLong());nbt.putString("Owner",owner.toString());}
 @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);root=BlockPos.fromLong(nbt.getLong("Root"));Identifier parsed=Identifier.tryParse(nbt.getString("Owner"));owner=parsed==null?new Identifier("minecraft","air"):parsed;}
 @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
 @Override public Packet<ClientPlayPacketListener> toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
