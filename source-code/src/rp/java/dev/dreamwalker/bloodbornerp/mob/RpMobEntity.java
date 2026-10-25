package dev.dreamwalker.bloodbornerp.mob;

import dev.dreamwalker.bloodbornerp.content.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Persistent passive actors: tool-directed navigation and a two-hit corpse lifecycle. */
public final class RpMobEntity extends PathAwareEntity implements GeoEntity, AssetBacked {
 private static final String FROZEN_NBT="bloodborne_rp_frozen",CORPSE_NBT="bloodborne_rp_corpse",DESTINATION_NBT="bloodborne_rp_destination";
 private static final TrackedData<Integer> ATTACK_VARIANT=DataTracker.registerData(RpMobEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Integer> POSE_SEQUENCE=DataTracker.registerData(RpMobEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Boolean> FROZEN=DataTracker.registerData(RpMobEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Boolean> CORPSE=DataTracker.registerData(RpMobEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final Map<String, MobProfile> PROFILES = Map.ofEntries(
  Map.entry("cleric_beast", new MobProfile(80.0D, 0.20D, 8.0D)),
  Map.entry("vicar_amelia", new MobProfile(70.0D, 0.20D, 7.0D)),
  Map.entry("blood_starved_beast", new MobProfile(60.0D, 0.30D, 6.0D)),
  Map.entry("scourge_beast", new MobProfile(30.0D, 0.27D, 5.0D)),
  Map.entry("executioner", new MobProfile(40.0D, 0.21D, 6.0D)),
  Map.entry("huntsman_a", new MobProfile(20.0D, 0.27D, 4.0D)),
  Map.entry("huntsman_b", new MobProfile(20.0D, 0.27D, 4.0D)),
  Map.entry("huntsman_c", new MobProfile(20.0D, 0.27D, 4.0D)),
  Map.entry("huntsman_d", new MobProfile(18.0D, 0.25D, 4.0D)),
  Map.entry("huntsman_wheelchair", new MobProfile(16.0D, 0.16D, 4.0D)),
  Map.entry("large_huntsman", new MobProfile(35.0D, 0.22D, 6.0D)),
  Map.entry("carrion_crow", new MobProfile(10.0D, 0.32D, 3.0D)),
  Map.entry("giant_rat", new MobProfile(12.0D, 0.29D, 3.0D)),
  Map.entry("small_rat", new MobProfile(6.0D, 0.31D, 2.0D)),
  Map.entry("rabid_dog", new MobProfile(12.0D, 0.33D, 3.0D)),
  Map.entry("maneater_boar", new MobProfile(30.0D, 0.24D, 6.0D)),
  Map.entry("brick_troll", new MobProfile(50.0D, 0.18D, 7.0D)),
  Map.entry("rotted_corpse", new MobProfile(16.0D, 0.22D, 3.0D)));

 private final String assetId;
 private NbtCompound sourceLegacyPayload;
 private final AnimatableInstanceCache animationCache=GeckoLibUtil.createInstanceCache(this);
 private int animationTicks,lastPoseSequence=-1,visualSequence,lastVisualSequence=-1;
 private Vec3d destination;
 private boolean resumeDestination,ambientAlternate;
 private LivingEntity npcVisualSource;
 private boolean npcKilled,npcAttacking;
 private float previousNpcSwing;
 private int npcAttackUntil;
 private String npcAttack="";

 public RpMobEntity(EntityType<? extends RpMobEntity> type,World world,String id){super(type,world);assetId=id;setPersistent();normalizeAttributes();setHealth(2);}
 @Override public String assetId(){return assetId;}
 public AssetSpec asset(){return AssetCatalog.get(assetId);}
 @Override protected net.minecraft.text.Text getDefaultName(){return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.name(net.minecraft.registry.Registries.ENTITY_TYPE.getId(getType()),super.getDefaultName());}
 private static MobProfile profile(String id){var p=PROFILES.get(id);if(p==null)throw new IllegalArgumentException("Unknown RP mob profile: "+id);return p;}
 private record MobProfile(double health,double speed,double damage){}
 public static DefaultAttributeContainer.Builder createAttributes(String id){return PathAwareEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,2).add(EntityAttributes.GENERIC_MOVEMENT_SPEED,profile(id).speed()).add(EntityAttributes.GENERIC_ATTACK_DAMAGE,0).add(EntityAttributes.GENERIC_FOLLOW_RANGE,32);}
 public static double maxHealthFor(String id){profile(id);return 2;}
 public boolean usesRangedAttack(){return assetId.equals("huntsman_d")||assetId.equals("huntsman_wheelchair");}
 private void normalizeAttributes(){var health=getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);if(health!=null){health.setBaseValue(2);if(health.getValue()!=2)health.clearModifiers();}}
 @Override protected void initGoals(){} // No wandering, revenge, target selection or combat goals.
 @Override public void setTarget(@Nullable LivingEntity target){super.setTarget(null);}
 @Override public boolean tryAttack(Entity target){return false;}
 @Override public void checkDespawn(){} // Actors and corpses remain on peaceful and outside player range.
 @Override public boolean canImmediatelyDespawn(double distanceSquared){return false;}
 @Override public void takeKnockback(double strength,double x,double z){}
 @Override protected void initDataTracker(){super.initDataTracker();dataTracker.startTracking(ATTACK_VARIANT,0);dataTracker.startTracking(POSE_SEQUENCE,0);dataTracker.startTracking(FROZEN,false);dataTracker.startTracking(CORPSE,false);}
 public boolean isCorpse(){return dataTracker.get(CORPSE);}
 public boolean isNpcModelProxy(){return npcVisualSource!=null;}
 public boolean isVisualCorpse(){return npcVisualSource!=null?npcKilled||npcVisualSource.deathTime>0||!npcVisualSource.isAlive():isCorpse();}
 public boolean getFrozen(){return dataTracker.get(FROZEN);}
 public boolean isFrozen(){return getFrozen();}
 private void stopOrder(){destination=null;resumeDestination=false;getNavigation().stop();setVelocity(new Vec3d(0,getVelocity().y,0));}
 public void setFrozen(boolean value){dataTracker.set(FROZEN,value);if(value)stopOrder();}
 @Override public boolean isAiDisabled(){return isCorpse()||isFrozen()||super.isAiDisabled();}
 @Override public boolean canMoveVoluntarily(){return !isCorpse()&&!isFrozen()&&super.canMoveVoluntarily();}
 public boolean reviveActor(){if(getWorld().isClient||!isCorpse())return false;dataTracker.set(CORPSE,false);setFrozen(false);setHealth(2);deathTime=0;hurtTime=0;dataTracker.set(ATTACK_VARIANT,0);dataTracker.set(POSE_SEQUENCE,dataTracker.get(POSE_SEQUENCE)+1);stopOrder();return true;}
 @Override public boolean damage(DamageSource source,float amount){
  if(getWorld().isClient||isRemoved()||!(source.getAttacker() instanceof PlayerEntity player)||!player.isCreative())return false;
  if(isCorpse()){discard();return true;}
  normalizeAttributes();setHealth(1);dataTracker.set(CORPSE,true);dataTracker.set(ATTACK_VARIANT,0);dataTracker.set(POSE_SEQUENCE,dataTracker.get(POSE_SEQUENCE)+1);animationTicks=0;deathTime=0;hurtTime=0;stopOrder();playSound(getDeathSound(),getSoundVolume(),getSoundPitch());return true;
 }
 public boolean orderTo(BlockPos block){
  if(getWorld().isClient||isCorpse()||isRemoved()||!getWorld().isChunkLoaded(block))return false;
  var shape=getWorld().getBlockState(block).getCollisionShape(getWorld(),block,net.minecraft.block.ShapeContext.absent());
  double top=shape.isEmpty()?0:shape.getMax(Direction.Axis.Y);var target=new Vec3d(block.getX()+.5,block.getY()+top,block.getZ()+.5);
  if(!getWorld().getWorldBorder().contains(BlockPos.ofFloored(target)))return false;
  setFrozen(false);dataTracker.set(ATTACK_VARIANT,0);animationTicks=0;
  if(horizontalDistanceSquared(target)<.05&&Math.abs(getY()-target.y)<1){stopOrder();return true;}
  if(!getNavigation().startMovingTo(target.x,target.y,target.z,1))return false;
  destination=target;resumeDestination=false;return true;
 }
 private double horizontalDistanceSquared(Vec3d p){double x=getX()-p.x,z=getZ()-p.z;return x*x+z*z;}
 public Optional<Vec3d> orderedDestination(){return Optional.ofNullable(destination);}
 public void rotateActor45(){float yaw=MathHelper.wrapDegrees(getYaw()+45);setYaw(yaw);prevYaw=yaw;setBodyYaw(yaw);prevBodyYaw=yaw;setHeadYaw(yaw);prevHeadYaw=yaw;stopOrder();}
 private List<String> attackOptions(){return MobAnimationSelector.attacks(assetId).stream().filter(s->asset().clip(s)!=null).toList();}
 private String randomAttack(){var options=attackOptions();return options.isEmpty()?"":options.get(random.nextInt(options.size()));}
 public boolean playToolAttack(){if(getWorld().isClient||isCorpse())return false;var options=attackOptions();if(options.isEmpty())return false;stopOrder();int variant=random.nextInt(options.size());dataTracker.set(ATTACK_VARIANT,variant+1);dataTracker.set(POSE_SEQUENCE,dataTracker.get(POSE_SEQUENCE)+1);animationTicks=Math.max(1,(int)Math.ceil(asset().clip(options.get(variant)).seconds()*20));return true;}
 private String attackSuffix(){var options=attackOptions();int i=dataTracker.get(ATTACK_VARIANT);return i>0&&i<=options.size()?options.get(i-1):"";}
 @Override public void tick(){
  super.tick();
  if(getWorld().isClient)return;
  normalizeAttributes();setTarget(null);
  if(animationTicks>0&&--animationTicks==0)dataTracker.set(ATTACK_VARIANT,0);
  if(isCorpse()||isFrozen()){getNavigation().stop();setVelocity(new Vec3d(0,getVelocity().y,0));return;}
  if(destination==null)return;
  if(horizontalDistanceSquared(destination)<.05&&Math.abs(getY()-destination.y)<1){stopOrder();return;}
  if(resumeDestination){resumeDestination=false;if(!getNavigation().startMovingTo(destination.x,destination.y,destination.z,1))stopOrder();}
  else if(getNavigation().isIdle()){
   if(horizontalDistanceSquared(destination)<2.25&&Math.abs(getY()-destination.y)<1)getMoveControl().moveTo(destination.x,destination.y,destination.z,1);
   else stopOrder();
  }
 }
 @Override protected SoundEvent getAmbientSound(){return RpSounds.mob(assetId,"idle",SoundEvents.ENTITY_ZOMBIE_AMBIENT);}
 @Override protected SoundEvent getHurtSound(DamageSource source){return RpSounds.mob(assetId,"hurt",SoundEvents.ENTITY_ZOMBIE_HURT);}
 @Override protected SoundEvent getDeathSound(){return RpSounds.mob(assetId,"death",SoundEvents.ENTITY_ZOMBIE_DEATH);}
 @Override public void playSound(SoundEvent sound,float volume,float pitch){super.playSound(sound,volume/3f,pitch);}
 @Override public net.minecraft.sound.SoundCategory getSoundCategory(){return net.minecraft.sound.SoundCategory.HOSTILE;}
 @Override public void playAmbientSound(){ambientAlternate=!ambientAlternate;if(ambientAlternate&&!isCorpse())super.playAmbientSound();}
 @Override public void writeCustomDataToNbt(NbtCompound nbt){super.writeCustomDataToNbt(nbt);nbt.putBoolean(FROZEN_NBT,isFrozen());nbt.putBoolean(CORPSE_NBT,isCorpse());if(destination!=null){var a=new NbtList();a.add(NbtDouble.of(destination.x));a.add(NbtDouble.of(destination.y));a.add(NbtDouble.of(destination.z));nbt.put(DESTINATION_NBT,a);}SourceLegacyPayload.write(nbt,sourceLegacyPayload,this);}
 @Override public void readCustomDataFromNbt(NbtCompound nbt){sourceLegacyPayload=SourceLegacyPayload.read(nbt,this);super.readCustomDataFromNbt(nbt);normalizeAttributes();dataTracker.set(CORPSE,nbt.getBoolean(CORPSE_NBT));setFrozen(nbt.getBoolean(FROZEN_NBT));setHealth(isCorpse()?1:2);deathTime=0;hurtTime=0;var goal=nbt.getList(DESTINATION_NBT,NbtElement.DOUBLE_TYPE);if(goal.size()==3&&!isCorpse()&&!isFrozen()){var p=new Vec3d(goal.getDouble(0),goal.getDouble(1),goal.getDouble(2));if(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)){destination=p;resumeDestination=true;}}}

 /** A Custom NPC's model proxy is never ticked as a world mob. Borrow visual state only. */
 public void bindNpcVisualState(LivingEntity source,boolean killed,boolean attacking){
  npcVisualSource=source;npcKilled=killed;npcAttacking=attacking;age=source.age;setPos(source.getX(),source.getY(),source.getZ());prevX=source.prevX;prevY=source.prevY;prevZ=source.prevZ;setYaw(source.getYaw());prevYaw=source.prevYaw;setPitch(source.getPitch());prevPitch=source.prevPitch;bodyYaw=source.bodyYaw;prevBodyYaw=source.prevBodyYaw;headYaw=source.headYaw;prevHeadYaw=source.prevHeadYaw;setVelocity(source.getVelocity());hurtTime=source.hurtTime;deathTime=source.deathTime;
  float swing=source.handSwingProgress;boolean begin=swing>0&&(previousNpcSwing<=0||swing<previousNpcSwing);previousNpcSwing=swing;
  if(!killed&&(begin||attacking&&source.age>=npcAttackUntil&&source.limbAnimator.getSpeed()<.01f)){npcAttack=randomAttack();npcAttackUntil=source.age+(npcAttack.isEmpty()?10:Math.max(1,(int)Math.ceil(asset().clip(npcAttack).seconds()*20)));visualSequence++;}
 }
 public String currentAnimationSuffix(){
  if(npcVisualSource!=null){if(npcKilled||npcVisualSource.deathTime>0||!npcVisualSource.isAlive()){String death=MobAnimationSelector.death(asset());return death.isEmpty()?MobAnimationSelector.idle(asset()):death;}if(npcVisualSource.hurtTime>0)return hitOrIdle();if(npcVisualSource.age<npcAttackUntil&&!npcAttack.isEmpty())return npcAttack;float speed=npcVisualSource.limbAnimator.getSpeed();return MobAnimationSelector.movement(asset(),speed>.005f||npcVisualSource.getVelocity().horizontalLengthSquared()>.0001,speed);}
  if(isCorpse()){String death=MobAnimationSelector.death(asset());return death.isEmpty()?MobAnimationSelector.idle(asset()):death;}if(hurtTime>0)return hitOrIdle();String attack=attackSuffix();if(!attack.isEmpty())return attack;float speed=limbAnimator.getSpeed();return MobAnimationSelector.movement(asset(),speed>.005f||getVelocity().horizontalLengthSquared()>.0001,speed);
 }
 private String hitOrIdle(){String hit=MobAnimationSelector.hit(asset());return hit.isEmpty()?MobAnimationSelector.idle(asset()):hit;}
 @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){controllers.add(new AnimationController<>(this,"main",0,state->{
  int sequence=dataTracker.get(POSE_SEQUENCE);if(sequence!=lastPoseSequence||visualSequence!=lastVisualSequence){state.getController().forceAnimationReset();lastPoseSequence=sequence;lastVisualSequence=visualSequence;}
  state.getController().setAnimationSpeed(isVisualCorpse()&&MobAnimationSelector.death(asset()).isEmpty()||npcVisualSource==null&&isFrozen()&&!isCorpse()?0:1);String suffix=currentAnimationSuffix();var clip=asset().clip(suffix);if(clip==null||clip.name().isBlank())return PlayState.STOP;return state.setAndContinue(RawAnimation.begin().then(clip.name(),MobAnimationSelector.loopType(asset(),suffix)));
 }));}
 @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return animationCache;}
}
