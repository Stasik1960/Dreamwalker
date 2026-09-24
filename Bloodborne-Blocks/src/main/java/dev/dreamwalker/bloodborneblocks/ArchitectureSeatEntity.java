package dev.dreamwalker.bloodborneblocks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Non-persistent, invisible vehicle used for a single occupied bench seat. */
public final class ArchitectureSeatEntity extends Entity {
 private static final TrackedData<Float> MOUNTED_HEIGHT_OFFSET=DataTracker.registerData(ArchitectureSeatEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private BlockPos root=BlockPos.ORIGIN;
 private int seatIndex=-1;

 public ArchitectureSeatEntity(EntityType<? extends ArchitectureSeatEntity> type,World world){super(type,world);setNoGravity(true);noClip=true;setInvisible(true);}

 void bind(BlockPos root,int seatIndex,double x,double y,double z,float yaw){bind(root,seatIndex,x,y,z,yaw,0);}
 void bind(BlockPos root,int seatIndex,double x,double y,double z,float yaw,double mountedHeightOffset){this.root=root.toImmutable();this.seatIndex=seatIndex;dataTracker.set(MOUNTED_HEIGHT_OFFSET,(float)mountedHeightOffset);refreshPositionAndAngles(x,y,z,yaw,0);}
 BlockPos rootPos(){return root;}
 int seatIndex(){return seatIndex;}
 float mountedHeightOffset(){return dataTracker.get(MOUNTED_HEIGHT_OFFSET);}

 @Override protected void initDataTracker(){dataTracker.startTracking(MOUNTED_HEIGHT_OFFSET,0f);}
 @Override protected void readCustomDataFromNbt(NbtCompound nbt){root=BlockPos.fromLong(nbt.getLong("Root"));seatIndex=nbt.getInt("SeatIndex");}
 @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putLong("Root",root.asLong());nbt.putInt("SeatIndex",seatIndex);}
 @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
 @Override public boolean isCollidable(){return false;}
 @Override public boolean isPushable(){return false;}
 @Override protected boolean canAddPassenger(Entity passenger){return passenger instanceof PlayerEntity&&!hasPassengers();}
 @Override public double getMountedHeightOffset(){return mountedHeightOffset();}
 @Override public boolean shouldSave(){return false;}

 @Override public net.minecraft.util.math.Vec3d updatePassengerForDismount(net.minecraft.entity.LivingEntity passenger){
  BlockPos center=BlockPos.ofFloored(getX(),getY(),getZ());
  for(int radius=1;radius<=3;radius++)for(int[] direction:net.minecraft.entity.Dismounting.getDismountOffsets(getHorizontalFacing())){
   BlockPos target=center.add(direction[0]*radius,0,direction[1]*radius);
   if(!getWorld().isChunkLoaded(target))continue;
   var point=net.minecraft.entity.Dismounting.findRespawnPos(passenger.getType(),getWorld(),target,false);
   if(point!=null)return point;
  }
  return super.updatePassengerForDismount(passenger);
 }

 @Override public void tick(){
  super.tick();if(getWorld().isClient)return;
  if(!hasPassengers()||!getWorld().isChunkLoaded(root)||!FunctionalFurniture.isBench(getWorld().getBlockState(root))){discard();return;}
  setVelocity(0,0,0);
 }
}
