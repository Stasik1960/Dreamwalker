package dev.dreamwalker.bloodbornedw.composite;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Ephemeral seat: exists only while occupied; no map data or idle entities. */
public final class BenchSeatEntity extends Entity {
    private BlockPos root=BlockPos.ORIGIN;private String identity="";private int index=-1;private Vec3d returnPoint=Vec3d.ZERO;
    public BenchSeatEntity(EntityType<? extends BenchSeatEntity> type,World world){super(type,world);setNoGravity(true);noClip=true;setInvisible(true);}
    void bind(BlockPos root,String identity,int index,Vec3d point,float yaw,Vec3d returnPoint){this.root=root.toImmutable();this.identity=identity;this.index=index;this.returnPoint=returnPoint;refreshPositionAndAngles(point.x,point.y,point.z,yaw,0);}
    public BlockPos rootPos(){return root;}public String identity(){return identity;}public int index(){return index;}
    @Override protected void initDataTracker(){}
    @Override protected void readCustomDataFromNbt(NbtCompound tag){}
    @Override protected void writeCustomDataToNbt(NbtCompound tag){}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
    @Override public boolean isCollidable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected boolean canAddPassenger(Entity p){return p instanceof PlayerEntity&&!hasPassengers();}
    @Override public double getMountedHeightOffset(){return -.40;}
    @Override public boolean shouldSave(){return false;}
    @Override public Vec3d updatePassengerForDismount(LivingEntity passenger){
        BlockPos center=BlockPos.ofFloored(getX(),getY(),getZ());
        for(int radius=1;radius<=3;radius++)for(int dy:new int[]{0,-1,1})for(int[] d:Dismounting.getDismountOffsets(getHorizontalFacing())){
            BlockPos p=center.add(d[0]*radius,dy,d[1]*radius);if(!getWorld().isChunkLoaded(p))continue;
            Vec3d free=Dismounting.findRespawnPos(passenger.getType(),getWorld(),p,false);if(free!=null)return free;
        }
        if(getWorld().isChunkLoaded(BlockPos.ofFloored(returnPoint))&&getWorld().isSpaceEmpty(passenger,passenger.getBoundingBox().offset(returnPoint.subtract(passenger.getPos()))))return returnPoint;
        return super.updatePassengerForDismount(passenger);
    }
    @Override public void tick(){super.tick();if(getWorld().isClient)return;if(!hasPassengers()||!BenchSeating.valid(getWorld(),root,identity)){discard();return;}setVelocity(0,0,0);}
}
