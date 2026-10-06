package dev.dreamwalker.bloodbornerp.object;

import dev.dreamwalker.bloodbornerp.content.AssetBacked;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RpObjectEntity extends Entity implements GeoEntity, AssetBacked {
 private static final TrackedData<Boolean> OPEN=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Boolean> LOCKED=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private final String assetId;
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
 private final List<UUID> links=new ArrayList<>();
 private String transition="";private int transitionTicks;
 public RpObjectEntity(EntityType<? extends RpObjectEntity> type,World world,String assetId){super(type,world);this.assetId=assetId;refreshCollider();}
 @Override protected void initDataTracker(){dataTracker.startTracking(OPEN,false);dataTracker.startTracking(LOCKED,false);}
 @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
 @Override public String assetId(){return assetId;} public AssetSpec asset(){return AssetCatalog.get(assetId);}
 public boolean isOpen(){return dataTracker.get(OPEN);} public boolean isLocked(){return dataTracker.get(LOCKED);}
 public boolean setOpen(boolean open){if(!open&&canBeLinked()&&!getWorld().isClient&&(!getWorld().isSpaceEmpty(this,closedCollider())||!getWorld().getOtherEntities(this,closedCollider(),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()))return false;if(isOpen()!=open){transition=open?"open":"close";transitionTicks=transitionTicks(transition);}dataTracker.set(OPEN,open);refreshCollider();return true;}
 public void setLocked(boolean locked){dataTracker.set(LOCKED,locked);}
 public boolean isMechanism(){return assetId.startsWith("lever_");}
 public boolean canBeLinked(){return assetId.contains("door")||assetId.contains("gate")||assetId.contains("chest");}
 public List<UUID> links(){return List.copyOf(links);}
 public boolean addLink(RpObjectEntity target){if(!isMechanism()||!target.canBeLinked()||target.getWorld()!=getWorld()||!target.getWorld().isChunkLoaded(target.getBlockPos())||links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks||links.contains(target.getUuid()))return false;links.add(target.getUuid());return true;}
 public boolean removeLink(UUID target){return isMechanism()&&links.remove(target);}
 @Override public ActionResult interact(PlayerEntity player,Hand hand){
  if(getWorld().isClient)return ActionResult.SUCCESS;
  if(isLocked())return ActionResult.FAIL;
  if(assetId.equals("hunterlamp")&&player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer){dev.dreamwalker.bloodbornerp.lamp.LampService.open(serverPlayer);return ActionResult.CONSUME;}
  if(isMechanism()){setOpen(!isOpen());for(UUID id:List.copyOf(links)){Entity e=((ServerWorld)getWorld()).getEntity(id);if(e instanceof RpObjectEntity target&&target.canBeLinked()&&!target.isLocked())target.setOpen(!target.isOpen());}return ActionResult.CONSUME;}
  if(canBeLinked()){setOpen(!isOpen());return ActionResult.CONSUME;} return ActionResult.PASS;
 }
 @Override public boolean isCollidable(){return canBeLinked()&&!isOpen();}
 @Override public boolean canHit(){return true;}
 @Override public float getTargetingMargin(){return .35F;}
 @Override public void tick(){super.tick();if(transitionTicks>0)transitionTicks--;}
 @Override protected void readCustomDataFromNbt(NbtCompound nbt){dataTracker.set(OPEN,nbt.getBoolean("Open"));setLocked(nbt.getBoolean("Locked"));links.clear();for(NbtElement entry:nbt.getList("Links",NbtElement.INT_ARRAY_TYPE)){if(links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;try{UUID id=net.minecraft.nbt.NbtHelper.toUuid(entry);if(!links.contains(id))links.add(id);}catch(IllegalArgumentException ignored){}}refreshCollider();}
 @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putBoolean("Open",isOpen());nbt.putBoolean("Locked",isLocked());NbtList out=new NbtList();for(UUID id:links){if(out.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;out.add(net.minecraft.nbt.NbtHelper.fromUuid(id));}nbt.put("Links",out);}
 @Override public void onTrackedDataSet(TrackedData<?> data){super.onTrackedDataSet(data);if(data==OPEN){if(assetId!=null&&getWorld().isClient){transition=isOpen()?"open":"close";transitionTicks=transitionTicks(transition);}refreshCollider();}}
 @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(assetId!=null)refreshCollider();}
 @Override public void setYaw(float yaw){super.setYaw(yaw);if(assetId!=null)refreshCollider();}
 public void refreshCollider(){if(assetId==null||!canBeLinked())return;setBoundingBox(closedCollider());}
 private net.minecraft.util.math.Box closedCollider(){float width=Math.max(.2f,asset().width());double depth=Math.min(.25d,width);double radians=Math.toRadians(Math.round(getYaw()/90f)*90f);double x=Math.abs(Math.cos(radians))>.5?depth:width;double z=Math.abs(Math.cos(radians))>.5?width:depth;return new net.minecraft.util.math.Box(getX()-x/2,getY(),getZ()-z/2,getX()+x/2,getY()+Math.max(.2f,asset().height()),getZ()+z/2);}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){controllers.add(new AnimationController<>(this,"main",0,state->{String suffix=transitionTicks>0?transition:stableSuffix();AssetSpec.Clip clip=asset().clip(suffix);if(clip==null||clip.name().isBlank())return PlayState.STOP;Animation.LoopType loop=transitionTicks>0?Animation.LoopType.PLAY_ONCE:(clip.loop()?Animation.LoopType.LOOP:Animation.LoopType.HOLD_ON_LAST_FRAME);return state.setAndContinue(RawAnimation.begin().then(clip.name(),loop));}));}
 private String stableSuffix(){if(isOpen()){if(asset().clip("open_idle")!=null)return "open_idle";if(asset().clip("opened")!=null)return "opened";return "open";}if(asset().clip("closed_idle")!=null)return "closed_idle";if(asset().clip("closed")!=null)return "closed";return "idle";}
 private int transitionTicks(String suffix){AssetSpec.Clip clip=asset().clip(suffix);return clip==null?8:Math.max(1,Math.min(200,(int)(clip.seconds()*20)));}
 @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
