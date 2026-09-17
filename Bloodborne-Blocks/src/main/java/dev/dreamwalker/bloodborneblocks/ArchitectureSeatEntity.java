package dev.dreamwalker.bloodborneblocks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Non-persistent, invisible vehicle used for a single occupied bench seat. */
public final class ArchitectureSeatEntity extends Entity {
 private BlockPos root=BlockPos.ORIGIN;

 public ArchitectureSeatEntity(EntityType<? extends ArchitectureSeatEntity> type,World world){super(type,world);setNoGravity(true);noClip=true;setInvisible(true);}

 void bind(BlockPos root,double x,double y,double z,float yaw){this.root=root.toImmutable();refreshPositionAndAngles(x,y,z,yaw,0);}
 BlockPos rootPos(){return root;}

 @Override protected void initDataTracker(){}
 @Override protected void readCustomDataFromNbt(NbtCompound nbt){root=BlockPos.fromLong(nbt.getLong("Root"));}
 @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putLong("Root",root.asLong());}
 @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
 @Override public boolean isCollidable(){return false;}
 @Override public boolean isPushable(){return false;}
 @Override protected boolean canAddPassenger(Entity passenger){return passenger instanceof PlayerEntity&&!hasPassengers();}
 @Override public double getMountedHeightOffset(){return 0;}
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
