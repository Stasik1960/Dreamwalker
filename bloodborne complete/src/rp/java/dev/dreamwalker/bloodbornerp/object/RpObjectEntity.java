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
 private static final TrackedData<Boolean> DOGS_VISIBLE=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Integer> WOOD_GATE_PULSE_TICKS=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Integer> LADDER_DEPLOY_TICKS=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<NbtCompound> VERTICAL_OFFSET=DataTracker.registerData(RpObjectEntity.class,TrackedDataHandlerRegistry.NBT_COMPOUND);
 private final java.util.LinkedHashMap<UUID,Long> gateRequests=new java.util.LinkedHashMap<>();
 private final String assetId;
 private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
 private final List<UUID> links=new ArrayList<>();
 private String transition="";private int transitionTicks;private int leverPulseTicks;private String legacyConnectionId="";
 private NbtCompound sourceLegacyPayload;
 private record GeometryKey(double x,double y,double z,float yaw,float pitch,float scale,boolean open,int ladderTicks,boolean motionSelection) {}
private record GeometrySnapshot(GeometryKey key,net.minecraft.util.math.Box visual,List<net.minecraft.util.math.Box> physical,List<net.minecraft.util.math.Box> selection,List<net.minecraft.util.math.Box> climbing,List<net.minecraft.util.math.Box> ordinaryVisual) {}
 private GeometrySnapshot geometrySnapshot;
 private boolean restoringData;
 private static final java.util.Set<String> NON_COLLIDING=java.util.Set.of("curtainsmall","curtain_half","curtains","door_empty","gate_empty","trapdoor","wood_gate");
 private static final java.util.Set<String> PASSAGES=java.util.Set.of("door_1","door_2","main_gate","small_gate");
 public RpObjectEntity(EntityType<? extends RpObjectEntity> type,World world,String assetId){super(type,world);this.assetId=assetId;if(assetId.equals("ladder"))dataTracker.set(OPEN,true);refreshCollider();}
 @Override protected void initDataTracker(){dataTracker.startTracking(OPEN,false);dataTracker.startTracking(LOCKED,false);dataTracker.startTracking(SCALE,1f);dataTracker.startTracking(DOGS_VISIBLE,true);dataTracker.startTracking(WOOD_GATE_PULSE_TICKS,0);dataTracker.startTracking(LADDER_DEPLOY_TICKS,0);dataTracker.startTracking(VERTICAL_OFFSET,new NbtCompound());}
 @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
 @Override public String assetId(){return RpObjectCompatibility.canonicalId(assetId);} public AssetSpec asset(){return AssetCatalog.get(assetId());}
 public String originalRegistryAssetId(){return assetId;}
 @Override protected net.minecraft.text.Text getDefaultName(){return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(net.minecraft.registry.Registries.ENTITY_TYPE.getId(getType()),super.getDefaultName());}
 public boolean isOpen(){return dataTracker.get(OPEN);} public boolean isLocked(){return dataTracker.get(LOCKED);}
 public float objectScale(){return dataTracker.get(SCALE);}
 /** Exact DOUBLE metadata; Pos already includes it, so reload/spawn never applies it twice. */
 public double verticalOffset(){return dataTracker.get(VERTICAL_OFFSET).getDouble("Value");}
 public boolean setVerticalOffset(double requested,net.minecraft.server.network.ServerPlayerEntity player){
  double before=verticalOffset();
  if(getWorld().isClient||!Double.isFinite(requested)||!RpPlacementRules.canEdit(player,this)){return false;}
  double delta=requested-before;var previous=getPos();var next=visualBounds().offset(0,delta,0);String invalid=RpPlacementRules.bounds(getWorld(),next);
  if(invalid!=null){return false;}
  if(delta==0)return true;
  var physical=activePhysicalBoxes();invalid=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(getWorld(),physical.stream().map(box->box.offset(0,delta,0)).toList(),this,false,physical);
  if(invalid!=null){return false;}
  setPosition(previous.x,previous.y+delta,previous.z);NbtCompound tracked=new NbtCompound();tracked.putDouble("Value",requested);dataTracker.set(VERTICAL_OFFSET,tracked);velocityDirty=true;
  dev.dreamwalker.bloodbornerp.lamp.LampService.entityMoved(this);
  ;return true;
 }
 public void setObjectScale(float scale){boolean valid=Float.isFinite(scale)&&scale>=0&&scale<=8;dataTracker.set(SCALE,valid?scale:1f);refreshCollider();if(!valid)dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Scale is non-finite or outside reviewed range 0..8; retained existing fallback 1");}
 /** Builder rotation uses the active geometry at the proposed yaw and commits only after server validation. */
 public boolean rotateObject(PlayerEntity player,float nextYaw){
  if(getWorld().isClient||!RpPlacementRules.canEdit(player,this)||!Float.isFinite(nextYaw)){return false;}
  float previous=getYaw();setYaw(net.minecraft.util.math.MathHelper.wrapDegrees(nextYaw));
  String conflict=RpPlacementRules.bounds(getWorld(),visualBounds());
  if(conflict==null)conflict=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(getWorld(),activePhysicalBoxes(),this,false);
  if(conflict!=null){setYaw(previous);return false;}return true;
 }
 public boolean setOpen(boolean open){
  boolean before=isOpen();
  if(before!=open&&!restoringData&&animationBusy()){return false;}
  if(assetId.equals("wood_gate")){return false;}
  if(!open&&PASSAGES.contains(assetId)&&!getWorld().isClient&&!getWorld().getOtherEntities(this,closedCollider(),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()){return false;}
  if(before!=open){if(assetId.equals("ladder")){dataTracker.set(LADDER_DEPLOY_TICKS,open?48:0);transition="";transitionTicks=0;}else{transition=open?"open":isMechanism()?"":"close";transitionTicks=transition.isEmpty()?0:transitionTicks(transition);}}
  dataTracker.set(OPEN,open);refreshCollider();
  if(before!=open){
   if(!restoringData&&getWorld() instanceof ServerWorld world)dev.dreamwalker.bloodbornedw.link.ObjectPolicies.changed(world,dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef.rp(this),before,open);
  }return true;
 }
 public void setLocked(boolean locked){dataTracker.set(LOCKED,locked);}
 public boolean isMechanism(){return assetId.startsWith("lever_");}
 public boolean canBeLinked(){return PASSAGES.contains(assetId)||assetId.equals("chest")||assetId.equals("ladder")||assetId.equals("wood_gate");}
 public boolean supportsDogVisibility(){return assetId.equals("cage_obj_1")||assetId.equals("cage_obj_2")||assetId.equals("cage_obj_3");}
 public boolean dogsVisible(){return dataTracker.get(DOGS_VISIBLE);}
 public boolean setDogsVisible(boolean visible){if(!supportsDogVisibility()||getWorld().isClient)return false;dataTracker.set(DOGS_VISIBLE,visible);return true;}
 public String dogRootBone(){return assetId.equals("cage_obj_2")?"main_2":"main";}
 public boolean isPulseOnlyMechanism(){return assetId.equals("wood_gate");}
 public boolean woodGatePulseActive(){return dataTracker.get(WOOD_GATE_PULSE_TICKS)>0;}
 public boolean animationBusy(){return transitionTicks>0||dataTracker.get(LADDER_DEPLOY_TICKS)>0||woodGatePulseActive();}
 public int woodGatePulseTicks(){return dataTracker.get(WOOD_GATE_PULSE_TICKS);}
 public boolean pulseFromMechanism(){if(!isPulseOnlyMechanism()||getWorld().isClient||isLocked()||woodGatePulseActive()){return false;}dataTracker.set(WOOD_GATE_PULSE_TICKS,32);return true;}
 /** Vanilla repeats held use roughly every4ticks. Every request refreshes its cadence, even when refused. */
 public boolean requestWoodGatePulse(PlayerEntity player,Hand hand){
  if(!isPulseOnlyMechanism()||getWorld().isClient||hand!=Hand.MAIN_HAND||isLocked()){return false;}
  long now=getWorld().getTime();Long previous=gateRequests.put(player.getUuid(),now);while(gateRequests.size()>32)gateRequests.remove(gateRequests.keySet().iterator().next());
  if(previous!=null&&now-previous<=6||woodGatePulseActive()){return false;}return pulseFromMechanism();
 }
 public double ladderOffsetY(){if(!assetId.equals("ladder"))return 0;if(!isOpen())return 228;double tick=48-dataTracker.get(LADDER_DEPLOY_TICKS);if(tick>=48)return 0;if(tick<=20)return 228+(181-228)*tick/20;if(tick<=46.4)return 181*(46.4-tick)/26.4;if(tick<=47.2)return 3*(tick-46.4)/.8;return 3*(48-tick)/.8;}
 public double ladderOffsetZ(){if(!assetId.equals("ladder"))return 0;if(!isOpen())return -2;double tick=48-dataTracker.get(LADDER_DEPLOY_TICKS);if(tick<=46.4)return -2;if(tick<=47.2)return -2+(tick-46.4)/.8;return -(48-tick)/.8;}
 public boolean hasCustomPhysicalGeometry(){return true;}
 public boolean hasReviewedWorkingGeometry(){return RpObjectGeometry.custom(assetId());}
 public boolean selectionMotionActive(){return isOpen()||transitionTicks>0||woodGatePulseActive()||supportsDogVisibility()&&dogsVisible();}
 private boolean legacyCollidable(){return !isRemoved()&&!NON_COLLIDING.contains(assetId)&&(!PASSAGES.contains(assetId)||!isOpen());}
 private GeometrySnapshot geometry(){
        GeometryKey key=new GeometryKey(getX(),getY(),getZ(),getYaw(),getPitch(),objectScale(),isOpen(),dataTracker.get(LADDER_DEPLOY_TICKS),selectionMotionActive());
        if(geometrySnapshot==null||!geometrySnapshot.key.equals(key))geometrySnapshot=new GeometrySnapshot(key,RpObjectGeometry.visualBounds(this),hasReviewedWorkingGeometry()?RpObjectGeometry.physicalBoxes(this):legacyCollidable()?List.of(closedCollider()):List.of(),RpObjectGeometry.selectionBoxes(this),RpObjectGeometry.climbingBoxes(this),RpObjectGeometry.ordinaryVisualBoxes(this));
        return geometrySnapshot;
    }


 public List<net.minecraft.util.math.Box> activePhysicalBoxes(){return geometry().physical;}
 public List<net.minecraft.util.math.Box> selectionBoxes(){return geometry().selection;}
 /** Ordinary interaction uses active physics, or visual parts for non-colliding objects. */
 public List<net.minecraft.util.math.Box> interactionBoxes(){var snapshot=geometry();return snapshot.physical.isEmpty()?snapshot.ordinaryVisual:snapshot.physical;}
 public List<net.minecraft.util.math.Box> climbingBoxes(){return assetId.equals("ladder")?geometry().climbing:List.of();}
 public net.minecraft.util.math.Box visualBounds(){return geometry().visual;}
 public net.minecraft.util.math.Box queryBounds(){var snapshot=geometry();var bounds=snapshot.visual;for(var box:snapshot.physical)bounds=bounds.union(box);return bounds;}
 public List<UUID> links(){return List.copyOf(links);}
 public boolean addLink(RpObjectEntity target){if(!isMechanism()||!target.canBeLinked()||target.getWorld()!=getWorld()||!target.getWorld().isChunkLoaded(target.getBlockPos())||links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks||links.contains(target.getUuid()))return false;if(!dev.dreamwalker.bloodbornedw.link.MechanismLinks.linkRp(this,target))return false;links.add(target.getUuid());return true;}
 public boolean removeLink(UUID target){if(!isMechanism())return false;boolean removed=dev.dreamwalker.bloodbornedw.link.MechanismLinks.unlinkRp(this,target);return links.remove(target)||removed;}
 @Override public ActionResult interact(PlayerEntity player,Hand hand){
  if(player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem||player.getOffHandStack().getItem() instanceof net.minecraft.item.BlockItem)return ActionResult.PASS;
  if(ObjectRegistry.isPlacementItem(player.getMainHandStack())||ObjectRegistry.isPlacementItem(player.getOffHandStack()))return ActionResult.PASS;
  if(getWorld().isClient)return ActionResult.SUCCESS;
  if(isLocked())return ActionResult.FAIL;
  if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer&&(isMechanism()||canBeLinked())&&!dev.dreamwalker.bloodbornedw.link.ObjectPolicies.allowManual(serverPlayer,dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef.rp(this)))return ActionResult.FAIL;
  if(assetId.equals("wood_gate"))return requestWoodGatePulse(player,hand)?ActionResult.CONSUME:ActionResult.FAIL;
  if(hand!=Hand.MAIN_HAND)return ActionResult.PASS;
  if(assetId.equals("hunterlamp")&&player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer){dev.dreamwalker.bloodbornerp.lamp.LampService.open(serverPlayer,this);return ActionResult.CONSUME;}
  if(isMechanism()){if(isOpen())return ActionResult.FAIL;resolveLegacyLinks();if(!setOpen(true))return ActionResult.FAIL;if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)dev.dreamwalker.bloodbornedw.link.MechanismLinks.noteInitiator(this,serverPlayer);leverPulseTicks=70;return ActionResult.CONSUME;}
  if(PASSAGES.contains(assetId)||assetId.equals("chest")||assetId.equals("ladder")){return setOpen(!isOpen())?ActionResult.CONSUME:ActionResult.FAIL;} return ActionResult.PASS;
 }
 private void resolveLegacyLinks(){
  if(!links.isEmpty()||legacyConnectionId.isBlank()||legacyConnectionId.equals("0")||!(getWorld() instanceof ServerWorld world))return;
  for(Entity e:world.iterateEntities()){
   if(links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;
   if(e instanceof RpObjectEntity target&&(target.assetId.equals("main_gate")||target.assetId.equals("small_gate"))&&legacyConnectionId.equals(target.legacyConnectionId)&&squaredDistanceTo(target)<=512d*512d)addLink(target);
  }
 }
 @Override public boolean isCollidable(){return false;}
 @Override public boolean canHit(){return true;}
 @Override public net.minecraft.item.ItemStack getPickBlockStack(){
  var stack=net.minecraft.registry.Registries.ITEM.get(dev.dreamwalker.bloodbornerp.BloodborneRp.id(assetId()+"_placer")).getDefaultStack();
  NbtCompound state=new NbtCompound();state.putBoolean("Open",isOpen());state.putBoolean("Locked",isLocked());state.putFloat("Scale",objectScale());
  if(supportsDogVisibility())state.putBoolean("DogsVisible",dogsVisible());
  stack.getOrCreateNbt().put("bloodborne_rp_object",state);if(hasCustomName())stack.setCustomName(getCustomName());return stack;
 }
 @Override public boolean damage(net.minecraft.entity.damage.DamageSource source,float amount){
  if(getWorld().isClient||isRemoved()||!source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)||!(source.getAttacker() instanceof PlayerEntity player)||!player.isCreative())return false;
  var selected=RpObjectSelection.playerTarget(player,RpObjectSelection.entityReach(player));
  if(selected==null||selected.getEntity()!=this||!getWorld().canPlayerModifyAt(player,net.minecraft.util.math.BlockPos.ofFloored(selected.getPos()))||!player.getAbilities().allowModifyWorld)return false;
  removeObject();return true;
 }
 public void removeObject(){
        if(isRemoved())return;
        dev.dreamwalker.bloodbornedw.link.MechanismLinks.removed(this);
        if(getWorld() instanceof ServerWorld world)dev.dreamwalker.bloodbornerp.lamp.LampService.removeByEntity(world.getServer(),getUuid());
        discard();
    }
 @Override public float getTargetingMargin(){return 0;}
@Override public void tick(){super.tick();if(transitionTicks>0&&--transitionTicks==0)refreshCollider();if(!getWorld().isClient){if(woodGatePulseActive()){int before=woodGatePulseTicks();dataTracker.set(WOOD_GATE_PULSE_TICKS,before-1);}int ladderTicks=dataTracker.get(LADDER_DEPLOY_TICKS);if(ladderTicks>0){dataTracker.set(LADDER_DEPLOY_TICKS,ladderTicks-1);}if(isMechanism()&&isOpen()){if(leverPulseTicks>0&&--leverPulseTicks==0)dev.dreamwalker.bloodbornedw.link.MechanismLinks.pulse(this);if(leverPulseTicks==0&&!animationBusy())setOpen(false);}}}
 @Override protected void readCustomDataFromNbt(NbtCompound nbt){restoringData=true;try{validatePersistentData(nbt);sourceLegacyPayload=dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload.read(nbt,this);NbtCompound offset=new NbtCompound();double savedOffset=nbt.contains("VerticalOffset",NbtElement.DOUBLE_TYPE)?nbt.getDouble("VerticalOffset"):0;if(!Double.isFinite(savedOffset)){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Non-finite saved offset metadata defaults to zero; saved Pos retained");savedOffset=0;}offset.putDouble("Value",savedOffset);dataTracker.set(VERTICAL_OFFSET,offset);dataTracker.set(OPEN,nbt.contains("Open",NbtElement.BYTE_TYPE)?nbt.getBoolean("Open"):((assetId.equals("main_gate")||assetId.equals("small_gate"))&&nbt.getInt("EntityState")!=0));legacyConnectionId=nbt.contains("ConnectionId",NbtElement.STRING_TYPE)&&hasCustomName()?getCustomName().getString():nbt.getString("ConnectionId");if(legacyConnectionId.length()>64)legacyConnectionId="";leverPulseTicks=isMechanism()&&isOpen()?(nbt.contains("LeverPulseTicks",NbtElement.NUMBER_TYPE)?Math.max(1,Math.min(70,nbt.getInt("LeverPulseTicks"))):70):0;setLocked(nbt.getBoolean("Locked"));setObjectScale(nbt.contains("Scale",NbtElement.NUMBER_TYPE)?nbt.getFloat("Scale"):1f);links.clear();for(NbtElement entry:nbt.getList("Links",NbtElement.INT_ARRAY_TYPE)){if(links.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;try{UUID id=net.minecraft.nbt.NbtHelper.toUuid(entry);if(!links.contains(id))links.add(id);}catch(IllegalArgumentException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Links entry cannot be decoded as an exact UUID; existing skip behavior retained", failure);}}dataTracker.set(DOGS_VISIBLE,!nbt.contains("DogsVisible",NbtElement.BYTE_TYPE)||nbt.getBoolean("DogsVisible"));dataTracker.set(WOOD_GATE_PULSE_TICKS,0);gateRequests.clear();if(assetId.equals("ladder")){if(!nbt.contains("RpBehaviourVersion",NbtElement.NUMBER_TYPE))dataTracker.set(OPEN,true);dataTracker.set(LADDER_DEPLOY_TICKS,isOpen()?Math.max(0,Math.min(48,nbt.getInt("LadderDeployTicks"))):0);}refreshCollider();}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("RP object persistence read failed", failure);throw failure;}finally{restoringData=false;};}
 @Override protected void writeCustomDataToNbt(NbtCompound nbt){try{nbt.putBoolean("Open",isOpen());nbt.putBoolean("Locked",isLocked());nbt.putFloat("Scale",objectScale());nbt.putDouble("VerticalOffset",verticalOffset());nbt.putInt("RpBehaviourVersion",1);if(supportsDogVisibility())nbt.putBoolean("DogsVisible",dogsVisible());if(assetId.equals("ladder"))nbt.putInt("LadderDeployTicks",dataTracker.get(LADDER_DEPLOY_TICKS));if(isMechanism()&&isOpen())nbt.putInt("LeverPulseTicks",leverPulseTicks);if(!legacyConnectionId.isEmpty())nbt.putString("ConnectionId",legacyConnectionId);NbtList out=new NbtList();for(UUID id:links){if(out.size()>=dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.maxMechanismLinks)break;out.add(net.minecraft.nbt.NbtHelper.fromUuid(id));}nbt.put("Links",out);dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload.write(nbt,sourceLegacyPayload,this);}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("RP object persistence write failed", failure);throw failure;}finally{}}
 private void validatePersistentData(NbtCompound nbt){
  if(nbt.contains("VerticalOffset")&&!nbt.contains("VerticalOffset",NbtElement.DOUBLE_TYPE))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("VerticalOffset must be exact DOUBLE; saved Pos retained and metadata defaults to zero");
  for(String key:java.util.List.of("Open","Locked","DogsVisible"))if(nbt.contains(key)&&!nbt.contains(key,NbtElement.BYTE_TYPE))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Expected BYTE for persisted "+key+"; existing read rules retained");
  for(String key:java.util.List.of("Scale","RpBehaviourVersion","LadderDeployTicks","LeverPulseTicks"))if(nbt.contains(key)&&!nbt.contains(key,NbtElement.NUMBER_TYPE))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Expected NUMBER for persisted "+key+"; existing read rules retained");
  if(nbt.contains("Links")&&(!nbt.contains("Links",NbtElement.LIST_TYPE)||(nbt.get("Links") instanceof NbtList list&&!list.isEmpty()&&list.getHeldType()!=NbtElement.INT_ARRAY_TYPE)))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Links must contain typed UUID INT_ARRAY entries; existing read rules retained");
  if(nbt.contains("ConnectionId")&&!nbt.contains("ConnectionId",NbtElement.STRING_TYPE))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("ConnectionId must be STRING; existing read rules retained");
 }
 @Override public void onTrackedDataSet(TrackedData<?> data){super.onTrackedDataSet(data);if(data==OPEN){if(assetId!=null&&getWorld().isClient){transition=assetId.equals("ladder")?"":isOpen()?"open":isMechanism()?"":"close";transitionTicks=transition.isEmpty()?0:transitionTicks(transition);}refreshCollider();}else if(data==SCALE||data==LADDER_DEPLOY_TICKS||data==VERTICAL_OFFSET||data==WOOD_GATE_PULSE_TICKS||data==DOGS_VISIBLE)refreshCollider();}
 @Override public void setPosition(double x,double y,double z){super.setPosition(x,y,z);if(assetId!=null)refreshCollider();}
 @Override public void setYaw(float yaw){super.setYaw(yaw);if(assetId!=null)refreshCollider();}
 @Override public void setPitch(float pitch){super.setPitch(pitch);if(assetId!=null)refreshCollider();}
 public void refreshCollider(){if(assetId==null)return;setBoundingBox(queryBounds());}
 // Forge decorations use their registered EntityDimensions, including square X/Z bounds.
 private net.minecraft.util.math.Box closedCollider(){return getDimensions(getPose()).getBoxAt(getX(),getY(),getZ());}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){var controller=new AnimationController<>(this,"main",0,state->{if(assetId.equals("wood_gate")||assetId.equals("ladder"))return PlayState.STOP;String suffix=transitionTicks>0?transition:stableSuffix();AssetSpec.Clip clip=asset().clip(suffix);if(clip==null||clip.name().isBlank())return PlayState.STOP;Animation.LoopType loop=transitionTicks>0?Animation.LoopType.PLAY_ONCE:(clip.loopType()==Animation.LoopType.PLAY_ONCE?Animation.LoopType.HOLD_ON_LAST_FRAME:clip.loopType());return state.setAndContinue(RawAnimation.begin().then(clip.name(),loop));});if(assetId.equals("wood_gate"))controller.triggerableAnim("wood_gate_once",RawAnimation.begin().then(asset().animationName("idle"),Animation.LoopType.PLAY_ONCE));controllers.add(controller);}
 private String stableSuffix(){if(isMechanism())return isOpen()?"open":"new";if(isOpen()){if(asset().clip("open_idle")!=null)return "open_idle";if(asset().clip("opened")!=null)return "opened";return "open";}if(asset().clip("closed_idle")!=null)return "closed_idle";if(asset().clip("closed")!=null)return "closed";return "idle";}
 private int transitionTicks(String suffix){AssetSpec.Clip clip=asset().clip(suffix);return clip==null?0:Math.max(1,(int)Math.ceil(clip.seconds()*20));}
 @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
