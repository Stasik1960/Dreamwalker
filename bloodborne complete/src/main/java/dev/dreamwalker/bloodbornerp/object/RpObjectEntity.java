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
 private static final TrackedData<Float> SCALE=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private final String assetId;
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
 private final List<UUID> links=new ArrayList<>();
 private String transition="";private int transitionTicks;private int leverPulseTicks;private String legacyConnectionId="";
 private static final java.util.Set<String> NON_COLLIDING=java.util.Set.of("curtainsmall","curtain_half","curtains","door_empty","gate_empty","trapdoor","wood_gate");
 private static final java.util.Set<String> PASSAGES=java.util.Set.of("door_1","door_2","main_gate","small_gate");
 public RpObjectEntity(EntityType<? extends RpObjectEntity> type,World world,String assetId){super(type,world);this.assetId=assetId;refreshCollider();}
 @Override protected void initDataTracker(){dataTracker.startTracking(OPEN,false);dataTracker.startTracking(LOCKED,false);dataTracker.startTracking(SCALE,1f);}
 @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
 @Override public String assetId(){return assetId;} public AssetSpec asset(){return AssetCatalog.get(assetId);}
 public boolean isOpen(){return dataTracker.get(OPEN);} public boolean isLocked(){return dataTracker.get(LOCKED);}
 public float objectScale(){return dataTracker.get(SCALE);}
 public void setObjectScale(float scale){dataTracker.set(SCALE,Float.isFinite(scale)&&scale>=0&&scale<=8?scale:1f);refreshCollider();}
 public boolean setOpen(boolean open){if(!open&&PASSAGES.contains(assetId)&&!getWorld().isClient&&!getWorld().getOtherEntities(this,closedCollider(),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return false;if(isOpen()!=open){transition=open?"open":isMechanism()?"":"close";transitionTicks=transition.isEmpty()?0:transitionTicks(transition);}dataTracker.set(OPEN,open);refreshCollider();return true;}
 public void setLocked(boolean locked){dataTracker.set(LOCKED,locked);}
 public boolean isMechanism(){return assetId.startsWith("lever_");}
 public boolean canBeLinked(){return PASSAGES.contains(assetId)||assetId.equals("chest");}
 public List<UUID> links(){return List.copyOf(links);}
 public boolean addLink(RpObjectEntity target){if(!isMechanism()||!target.canBeLinked()||target.getWorld()!=getWorld()||!target.getWorld().isChunkLoaded(target.getBlockPos())||links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks||links.contains(target.getUuid()))return false;links.add(target.getUuid());return true;}
 public boolean removeLink(UUID target){return isMechanism()&&links.remove(target);}
 @Override public ActionResult interact(PlayerEntity player,Hand hand){
  if(getWorld().isClient)return ActionResult.SUCCESS;
  if(isLocked())return ActionResult.FAIL;
  if(assetId.equals("hunterlamp")&&player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer){dev.dreamwalker.bloodbornerp.lamp.LampService.open(serverPlayer);return ActionResult.CONSUME;}
  if(isMechanism()){if(isOpen())return ActionResult.FAIL;resolveLegacyLinks();setOpen(true);leverPulseTicks=70;return ActionResult.CONSUME;}
  if(assetId.equals("door_1")||assetId.equals("door_2")||assetId.equals("chest")){setOpen(!isOpen());return ActionResult.CONSUME;} return ActionResult.PASS;
 }
 private void resolveLegacyLinks(){
  if(!links.isEmpty()||legacyConnectionId.isBlank()||legacyConnectionId.equals("0")||!(getWorld() instanceof ServerWorld world))return;
  for(Entity e:world.iterateEntities()){
   if(links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;
   if(e instanceof RpObjectEntity target&&(target.assetId.equals("main_gate")||target.assetId.equals("small_gate"))&&legacyConnectionId.equals(target.legacyConnectionId)&&squaredDistanceTo(target)<=512d*512d)addLink(target);
  }
 }
 @Override public boolean isCollidable(){return !isRemoved()&&!NON_COLLIDING.contains(assetId)&&(!PASSAGES.contains(assetId)||!isOpen());}
 @Override public boolean canHit(){return true;}
 @Override public net.minecraft.item.ItemStack getPickBlockStack(){
  var stack=net.minecraft.registry.Registries.ITEM.get(dev.dreamwalker.bloodbornerp.BloodborneRp.id(assetId+"_placer")).getDefaultStack();
  NbtCompound state=new NbtCompound();state.putBoolean("Open",isOpen());state.putBoolean("Locked",isLocked());state.putFloat("Scale",objectScale());
  stack.getOrCreateNbt().put("bloodborne_rp_object",state);if(hasCustomName())stack.setCustomName(getCustomName());return stack;
 }
 @Override public boolean damage(net.minecraft.entity.damage.DamageSource source,float amount){
  if(getWorld().isClient||!source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)||!(source.getAttacker() instanceof PlayerEntity player)||(!player.isCreative()&&!player.hasPermissionLevel(2))||player.squaredDistanceTo(this)>64)return false;
  removeByBuilder();return true;
 }
 public void removeByBuilder(){if(getWorld() instanceof ServerWorld world)dev.dreamwalker.bloodbornerp.lamp.LampService.removeByEntity(world.getServer(),getUuid());discard();}
 @Override public float getTargetingMargin(){return .35F;}
 @Override public void tick(){super.tick();if(transitionTicks>0)transitionTicks--;if(!getWorld().isClient&&isMechanism()&&isOpen()){if(leverPulseTicks>0&&--leverPulseTicks==0){for(UUID id:List.copyOf(links)){Entity e=((ServerWorld)getWorld()).getEntity(id);if(e instanceof RpObjectEntity target&&target.canBeLinked()&&!target.isLocked())target.setOpen(!target.isOpen());}}if(leverPulseTicks==0)setOpen(false);}}
 @Override protected void readCustomDataFromNbt(NbtCompound nbt){dataTracker.set(OPEN,nbt.contains("Open",NbtElement.BYTE_TYPE)?nbt.getBoolean("Open"):((assetId.equals("main_gate")||assetId.equals("small_gate"))&&nbt.getInt("EntityState")!=0));legacyConnectionId=nbt.contains("ConnectionId",NbtElement.STRING_TYPE)&&hasCustomName()?getCustomName().getString():nbt.getString("ConnectionId");if(legacyConnectionId.length()>64)legacyConnectionId="";leverPulseTicks=isMechanism()&&isOpen()?(nbt.contains("LeverPulseTicks",NbtElement.NUMBER_TYPE)?Math.max(1,Math.min(70,nbt.getInt("LeverPulseTicks"))):70):0;setLocked(nbt.getBoolean("Locked"));setObjectScale(nbt.contains("Scale",NbtElement.NUMBER_TYPE)?nbt.getFloat("Scale"):1f);links.clear();for(NbtElement entry:nbt.getList("Links",NbtElement.INT_ARRAY_TYPE)){if(links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;try{UUID id=net.minecraft.nbt.NbtHelper.toUuid(entry);if(!links.contains(id))links.add(id);}catch(IllegalArgumentException ignored){}}refreshCollider();}
 @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putBoolean("Open",isOpen());nbt.putBoolean("Locked",isLocked());nbt.putFloat("Scale",objectScale());if(isMechanism()&&isOpen())nbt.putInt("LeverPulseTicks",leverPulseTicks);if(!legacyConnectionId.isEmpty())nbt.putString("ConnectionId",legacyConnectionId);NbtList out=new NbtList();for(UUID id:links){if(out.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;out.add(net.minecraft.nbt.NbtHelper.fromUuid(id));}nbt.put("Links",out);}
 @Override public void onTrackedDataSet(TrackedData<?> data){super.onTrackedDataSet(data);if(data==OPEN){if(assetId!=null&&getWorld().isClient){transition=isOpen()?"open":isMechanism()?"":"close";transitionTicks=transition.isEmpty()?0:transitionTicks(transition);}refreshCollider();}}
 @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(assetId!=null)refreshCollider();}
 @Override public void setYaw(float yaw){super.setYaw(yaw);if(assetId!=null)refreshCollider();}
 public void refreshCollider(){if(assetId==null)return;setBoundingBox(closedCollider());}
 // Forge decorations use their registered EntityDimensions, including square X/Z bounds.
 private net.minecraft.util.math.Box closedCollider(){return getDimensions(getPose()).getBoxAt(getX(),getY(),getZ());}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){controllers.add(new AnimationController<>(this,"main",0,state->{String suffix=transitionTicks>0?transition:stableSuffix();AssetSpec.Clip clip=asset().clip(suffix);if(clip==null||clip.name().isBlank())return PlayState.STOP;Animation.LoopType loop=transitionTicks>0?Animation.LoopType.PLAY_ONCE:(clip.loopType()==Animation.LoopType.PLAY_ONCE?Animation.LoopType.HOLD_ON_LAST_FRAME:clip.loopType());return state.setAndContinue(RawAnimation.begin().then(clip.name(),loop));}));}
 private String stableSuffix(){if(isMechanism())return isOpen()?"open":"new";if(isOpen()){if(asset().clip("open_idle")!=null)return "open_idle";if(asset().clip("opened")!=null)return "opened";return "open";}if(asset().clip("closed_idle")!=null)return "closed_idle";if(asset().clip("closed")!=null)return "closed";return "idle";}
 private int transitionTicks(String suffix){AssetSpec.Clip clip=asset().clip(suffix);return clip==null?8:Math.max(1,Math.min(200,(int)(clip.seconds()*20)));}
 @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
