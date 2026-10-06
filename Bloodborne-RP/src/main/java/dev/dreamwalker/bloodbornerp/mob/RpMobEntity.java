package dev.dreamwalker.bloodbornerp.mob;

import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.content.AssetBacked;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared hostile implementation for catalog-backed Bloodborne encounters. */
public final class RpMobEntity extends HostileEntity implements GeoEntity, AssetBacked {
 private static final String FROZEN_NBT = "bloodborne_rp_frozen";
 private static final TrackedData<Integer> ATTACK_VARIANT = DataTracker.registerData(RpMobEntity.class, TrackedDataHandlerRegistry.INTEGER);
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
 private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
 @Nullable private final ServerBossBar bossBar;
 private static final TrackedData<Boolean> FROZEN=DataTracker.registerData(RpMobEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private int animationTicks;

 public RpMobEntity(EntityType<? extends RpMobEntity> type, World world, String assetId) {
  super(type, world);
  this.assetId = assetId;
  this.bossBar = isBoss(assetId) ? new ServerBossBar(asset().displayName() == null
   ? net.minecraft.text.Text.literal(assetId) : net.minecraft.text.Text.literal(asset().displayName()), BossBar.Color.RED, BossBar.Style.PROGRESS) : null;
 }

 @Override public String assetId() { return assetId; }
 public AssetSpec asset() { return AssetCatalog.get(assetId); }
 public boolean getFrozen() { return getDataTracker().get(FROZEN); }
 public boolean isFrozen() { return getDataTracker().get(FROZEN); }
 public void setFrozen(boolean frozen) {
  getDataTracker().set(FROZEN,frozen);
  if (isFrozen()) {
   getNavigation().stop();
   setVelocity(Vec3d.ZERO);
   setTarget(null);
  }
 }

 public static DefaultAttributeContainer.Builder createAttributes(String id) {
  MobProfile profile = profile(id);
  return HostileEntity.createHostileAttributes()
   .add(EntityAttributes.GENERIC_MAX_HEALTH, profile.health())
   .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, profile.speed())
   .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, profile.damage())
   .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0D);
 }

 public static double maxHealthFor(String id) { return profile(id).health(); }
 private static MobProfile profile(String id) {
  MobProfile profile = PROFILES.get(id);
  if (profile == null) throw new IllegalArgumentException("Unknown RP mob profile: " + id);
  return profile;
 }
 private record MobProfile(double health, double speed, double damage) {}

 private static boolean isBoss(String id) {
  return id.equals("cleric_beast") || id.equals("vicar_amelia") || id.equals("blood_starved_beast");
 }
 public boolean usesRangedAttack() { return assetId.equals("huntsman_d") || assetId.equals("huntsman_wheelchair"); }

 @Override protected void initGoals() {
  goalSelector.add(0, new SwimGoal(this));
  goalSelector.add(2, new TelegraphedAttackGoal(this));
  goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
  targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
   player -> RpConfig.INSTANCE.monstersAttackPlayers && !isFrozen()));
 }

 @Override protected void initDataTracker() {
  super.initDataTracker();
  getDataTracker().startTracking(ATTACK_VARIANT, 0);
  getDataTracker().startTracking(FROZEN,false);
 }

 @Override public void tick() {
  super.tick();
  if (animationTicks > 0 && --animationTicks == 0) getDataTracker().set(ATTACK_VARIANT, 0);
  if (isFrozen()) {
   getNavigation().stop();
   setVelocity(Vec3d.ZERO);
  }
  if (!getWorld().isClient && bossBar != null) bossBar.setPercent(MathHelper.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
 }

 @Override public boolean canMoveVoluntarily() { return !isFrozen() && super.canMoveVoluntarily(); }
 @Override protected net.minecraft.sound.SoundEvent getAmbientSound() {
  return dev.dreamwalker.bloodbornerp.content.RpSounds.mob(assetId,"idle",SoundEvents.ENTITY_ZOMBIE_AMBIENT);
 }
 @Override protected net.minecraft.sound.SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
  return dev.dreamwalker.bloodbornerp.content.RpSounds.mob(assetId,"hurt",SoundEvents.ENTITY_ZOMBIE_HURT);
 }
 @Override protected net.minecraft.sound.SoundEvent getDeathSound() {
  return dev.dreamwalker.bloodbornerp.content.RpSounds.mob(assetId,"death",SoundEvents.ENTITY_ZOMBIE_DEATH);
 }
 @Override public boolean isAiDisabled() { return isFrozen() || super.isAiDisabled(); }
 @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
 @Override public void onStartedTrackingBy(ServerPlayerEntity player) { super.onStartedTrackingBy(player); if (bossBar != null) bossBar.addPlayer(player); }
 @Override public void onStoppedTrackingBy(ServerPlayerEntity player) { super.onStoppedTrackingBy(player); if (bossBar != null) bossBar.removePlayer(player); }
 @Override public void onDeath(net.minecraft.entity.damage.DamageSource source) { if (bossBar != null) bossBar.clearPlayers(); super.onDeath(source); }
 @Override public void writeCustomDataToNbt(NbtCompound nbt) { super.writeCustomDataToNbt(nbt); nbt.putBoolean(FROZEN_NBT, isFrozen()); }
 @Override public void readCustomDataFromNbt(NbtCompound nbt) { super.readCustomDataFromNbt(nbt); setFrozen(nbt.getBoolean(FROZEN_NBT)); }

 @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
  controllers.add(new AnimationController<>(this, "main", 0, state -> {
   state.getController().setAnimationSpeed(isFrozen()?0:1);
   String suffix = deathTime > 0 ? MobAnimationSelector.death(asset()) : hurtTime > 0 ? MobAnimationSelector.hit(asset()) : attackAnimationSuffix();
   if (suffix.isEmpty()) suffix = MobAnimationSelector.movement(asset(),state.isMoving(),state.getLimbSwingAmount());
   AssetSpec.Clip clip = asset().clip(suffix);
   if (clip == null || clip.name().isBlank()) return PlayState.STOP;
   return state.setAndContinue(RawAnimation.begin().then(clip.name(),MobAnimationSelector.loopType(asset(),suffix)));
  }));
 }
 @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

 private int beginAttackAnimation() {
  List<String> options = attackOptions();
  getDataTracker().set(ATTACK_VARIANT, options.isEmpty() ? 0 : random.nextInt(options.size()) + 1);
  if (options.isEmpty()) return 1;
  animationTicks = Math.max(1, (int)Math.ceil(asset().clip(options.get(getDataTracker().get(ATTACK_VARIANT) - 1)).seconds() * 20.0F));
  return animationTicks;
 }

 private List<String> attackOptions() {
  return MobAnimationSelector.attacks(assetId).stream().filter(suffix -> asset().clip(suffix) != null).toList();
 }

 private String attackAnimationSuffix() {
  int variant = getDataTracker().get(ATTACK_VARIANT);
  List<String> options = attackOptions();
  return variant > 0 && variant <= options.size() ? options.get(variant - 1) : "";
 }

 private final class TelegraphedAttackGoal extends Goal {
  private int ticks;
  private int windupTicks;
  private boolean ranged;
  TelegraphedAttackGoal(RpMobEntity ignored) { setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK)); }
  @Override public boolean canStart() { return validTarget(getTarget()) && !isFrozen(); }
  @Override public boolean shouldContinue() { return validTarget(getTarget()) && !isFrozen(); }
   @Override public void start() { ticks = 0; windupTicks = 0; ranged = usesRangedAttack(); }
  @Override public void stop() { ticks = 0; windupTicks = 0; getNavigation().stop(); }
  @Override public void tick() {
   LivingEntity target = getTarget();
   if (!validTarget(target)) return;
   getLookControl().lookAt(target, 30.0F, 30.0F);
   double distance = squaredDistanceTo(target);
   boolean inRange = ranged ? AttackRules.withinRangedReach(distance)
    : AttackRules.withinMeleeReach(distance, getWidth(), target.getWidth());
   if (!inRange || !canSee(target)) { getNavigation().startMovingTo(target, ranged ? 0.85D : 1.0D); ticks = 0; return; }
   getNavigation().stop();
   if (ticks++ == 0) windupTicks = beginAttackAnimation();
   if (ticks >= windupTicks) {
    if (ranged) fireAt(target); else strike(target);
    ticks = 0;
   }
  }
  private boolean validTarget(@Nullable LivingEntity target) {
   return target instanceof PlayerEntity && target.isAlive() && RpConfig.INSTANCE.monstersAttackPlayers && !isFrozen();
  }
  private void strike(LivingEntity target) {
   if (canSee(target) && AttackRules.withinMeleeReach(squaredDistanceTo(target), getWidth(), target.getWidth())) {
    tryAttack(target);
   }
  }
  private void fireAt(LivingEntity target) {
   if (!canSee(target) || !AttackRules.withinRangedReach(squaredDistanceTo(target))) return;
   ArrowEntity arrow = new ArrowEntity(getWorld(), RpMobEntity.this);
   Vec3d direction = target.getEyePos().subtract(getEyePos());
   arrow.setVelocity(direction.x, direction.y, direction.z, 1.6F, 4.0F);
   arrow.setDamage(getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
   getWorld().spawnEntity(arrow);
   playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0F, 0.85F + random.nextFloat() * 0.3F);
  }
 }
}
