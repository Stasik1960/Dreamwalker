package daot;

import daot.network.ModNetworking;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.Goal.Control;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TitanEntity extends HostileEntity implements GeoEntity, EyeHurtTitan, GrabbingTitan {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Boolean> DATA_IS_AGGRESSIVE = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_RUN_ANIMATION = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_IDLE_ANIMATION = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_EATING = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_EAT_TICK = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_EAT_PHASE = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_EYE_HURT = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_DEAD = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_EATING_TARGET_ID = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_DISMOUNT_ALLOWED = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_COMMANDED_STOP = DataTracker.registerData(TitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation IDLE2_ANIM = RawAnimation.begin().thenLoop("idle2");
   private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");
   private static final RawAnimation EYEHURT_ANIM = RawAnimation.begin().thenLoop("eyehurt");
   private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenLoop("eat");
   private static final RawAnimation SWOOP_ANIM = RawAnimation.begin().thenPlay("swoop");
   private static final RawAnimation HOLD_ANIM = RawAnimation.begin().thenLoop("hold");
   private static final RawAnimation WALK1_ANIM = RawAnimation.begin().thenLoop("walk1");
   private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation RUN2_ANIM = RawAnimation.begin().thenLoop("run2");
   private static final double WANDER_SPEED = 0.09;
   private static final double CHASE_SPEED = 0.27;
   private static final double CHASE_ANIM_SPEED = 1.0;
   private double speedMultiplier = 1.0;
   private static final float MAX_TURN_SPEED = 8.0F;
   private float targetYRot;
   private int eatTicks = 0;
   private LivingEntity eatingTarget = null;
   private boolean grabPhaseComplete = false;
   private TitanEntity.EatPhase eatPhase = TitanEntity.EatPhase.SWOOP;
   private int holdDuration = 0;
   private static final double GRAB_SPEED = 1.5;
   private static final double GRAB_SNAP_THRESHOLD = 0.5;
   private static final int SWOOP_DURATION = 10;
   private static final int PULL_TIMEOUT = 40;
   private int eyeHurtTicks = 0;
   private static final int EYE_HURT_DURATION = 100;
   private int eyeStunCooldown = 0;
   private static final int EYE_STUN_COOLDOWN = 200;
   private TitanNapeEntity napeEntity = null;
   private TitanEyeEntity eyeEntity = null;
   private int hitboxRespawnCooldown = 0;
   private int knockbackTicks = 0;
   private static final int KNOCKBACK_DURATION = 15;
   private double lastAnimationTime = 0.0;
   private static final double STOMP_THRESHOLD = 0.08;
   private static final double[] WALK_STOMP_KEYFRAMES = new double[]{0.5, 1.5};
   private static final double[] RUN_STOMP_KEYFRAMES = new double[]{0.25, 0.75};
   private final Set<UUID> selfInjectionPassengers = new HashSet<>();
   private final Map<UUID, Integer> sneakDeathTimers = new HashMap<>();
   private static final int SNEAK_DEATH_TICKS = 60;
   private final Map<UUID, Long> lastBlockedDismountTick = new HashMap<>();
   private static final int SNEAK_GRACE_TICKS = 5;
   private boolean pendingEjectEatingVictims = false;
   private int nightSleepStart;
   private int nightSleepEnd;
   private boolean isNightSleeping = false;
   private static final RawAnimation NIGHT_SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
   private static final AtomicInteger LOADED_TITAN_COUNT = new AtomicInteger(0);
   private static int lastSpawnTick = -1;
   private double animSpeedSqr = 0.0;
   private int animSpeedTick = -1;
   private int contactDamageCooldown = 0;

   private static double getWanderSpeed() {
      return ModConfig.get().pureTitanWanderSpeed;
   }

   private static double getChaseSpeed() {
      return ModConfig.get().pureTitanChaseSpeed;
   }

   public LivingEntity getEatingTarget() {
      return this.eatingTarget;
   }

   public TitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public TitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public TitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
      this.targetYRot = this.getYaw();
      this.randomizeNightSleepTimes();
   }

   private void randomizeNightSleepTimes() {
      this.nightSleepStart = 13000 + this.random.nextInt(2001);
      this.nightSleepEnd = 22000 + this.random.nextInt(1001);
   }

   public boolean isNightSleeping() {
      return this.isNightSleeping;
   }

   @Override
   protected EntityNavigation createNavigation(World world) {
      return new MobNavigation(this, world) {
         @Override
         protected PathNodeNavigator createPathNodeNavigator(int range) {
            this.nodeMaker = new LandPathNodeMaker();
            this.nodeMaker.setCanEnterOpenDoors(true);
            return new PathNodeNavigator(this.nodeMaker, Math.min(range, 200));
         }
      };
   }

   public static Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.09)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 15.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 2.0)
         .add(daot.compat.attributes.DaotEntityAttributes.SCALE, 1.0);
   }

   static int getBaseMaxTitans() {
      return ModConfig.get().baseTitanSpawnCap;
   }

   static int getExtraPerPlayer() {
      return ModConfig.get().extraTitansPerPlayer;
   }

   public static int getEffectiveMaxTitans(int playerCount) {
      int base = getBaseMaxTitans() + Math.max(0, playerCount - 1) * getExtraPerPlayer();
      return BloodmoonState.isActive() ? base * 3 : base;
   }

   static void onTitanLoaded() {
      LOADED_TITAN_COUNT.incrementAndGet();
   }

   static void onTitanUnloaded() {
      LOADED_TITAN_COUNT.updateAndGet(c -> Math.max(0, c - 1));
   }

   static int getLoadedTitanCount() {
      return LOADED_TITAN_COUNT.get();
   }

   static boolean tryClaimSpawnTick(ServerWorldAccess level) {
      int tick = level.toServerWorld().getServer().getTicks();
      if (tick == lastSpawnTick) {
         return false;
      } else {
         lastSpawnTick = tick;
         return true;
      }
   }

   public static boolean checkTitanSpawnRules(EntityType<TitanEntity> entityType, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random) {
      int playerCount = level.toServerWorld().getPlayers().size();
      int maxTitans = getEffectiveMaxTitans(playerCount);
      if (getLoadedTitanCount() >= maxTitans) {
         return false;
      } else if (!BreachManager.isNaturalSpawnAllowed(entityType, level, pos.getX(), pos.getZ())) {
         return false;
      } else {
         return random.nextFloat() >= 0.12F ? false : tryClaimSpawnTick(level);
      }
   }

   @Override
   public boolean canSpawn(WorldAccess world, SpawnReason spawnReason) {
      return true;
   }

   @Nullable
   @Override
   public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, net.minecraft.nbt.NbtCompound entityNbt) {
      if (!this.getWorld().isClient()) {
         int capPlayerCount = this.getWorld() instanceof ServerWorld sl ? sl.getPlayers().size() : 1;
         int capMaxTitans = getEffectiveMaxTitans(capPlayerCount);
         if (getLoadedTitanCount() >= capMaxTitans
            && spawnReason != SpawnReason.SPAWN_EGG
            && spawnReason != SpawnReason.COMMAND
            && spawnReason != SpawnReason.CONVERSION) {
            this.discard();
            return entityData;
         }
      }

      this.setPersistent();
      int selectedRunAnim = 1 + this.random.nextInt(2);
      this.setRunAnimation(selectedRunAnim);
      int selectedIdleAnim = 1 + this.random.nextInt(2);
      this.setIdleAnimation(selectedIdleAnim);
      if (!this.getWorld().isClient()) {
         TitanNapeEntity nape = new TitanNapeEntity(DannysAot.TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         TitanEyeEntity eye = new TitanEyeEntity(DannysAot.TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
         double scale = 0.85 + this.random.nextDouble() * 0.3;
         this.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE).setBaseValue(scale);
         this.speedMultiplier = 2.0 - scale;
         this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(getWanderSpeed() * this.speedMultiplier);
      }

      return super.initialize(world, difficulty, spawnReason, entityData, null);
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putInt("NightSleepStart", this.nightSleepStart);
      nbt.putInt("NightSleepEnd", this.nightSleepEnd);
      nbt.putBoolean("IsNightSleeping", this.isNightSleeping);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      double scale = this.getAttributeValue(daot.compat.attributes.DaotEntityAttributes.SCALE);
      this.speedMultiplier = 2.0 - scale;
      if (nbt.contains("NightSleepStart")) {
         this.nightSleepStart = nbt.getInt("NightSleepStart");
         this.nightSleepEnd = nbt.getInt("NightSleepEnd");
      }

      this.isNightSleeping = nbt.getBoolean("IsNightSleeping");
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   public void remove(RemovalReason reason) {
      if (this.napeEntity != null && !this.napeEntity.isRemoved()) {
         this.napeEntity.discard();
      }

      if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
         this.eyeEntity.discard();
      }

      super.remove(reason);
   }

   @Override
   public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
      return false;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      Entity attacker = source.getAttacker();
      if (!(attacker instanceof AttackTitanEntity)
         && !(attacker instanceof ArmoredTitanEntity)
         && !(attacker instanceof ColossalTitanEntity)
         && !(attacker instanceof FemaleTitanEntity)
         && !(attacker instanceof BeastTitanEntity)
         && !(attacker instanceof WarhammerTitanEntity)
         && !(attacker instanceof OgreTitanEntity)) {
         if (amount != Float.MAX_VALUE) {
            return false;
         } else {
            return this.isEating() && source.getAttacker() == this.eatingTarget ? false : super.damage(source, amount);
         }
      } else if (this.isDead()) {
         return false;
      } else if (amount == Float.MAX_VALUE) {
         return false;
      } else {
         float shifterDamage = this.getMaxHealth() / 3.0F;
         float newHealth = this.getHealth() - shifterDamage;
         if (newHealth <= 0.0F) {
            this.setHealth(0.0F);
            this.onDeath(source);
         } else {
            this.setHealth(newHealth);
         }

         return false;
      }
   }

   @Override
   public void onDeath(DamageSource damageSource) {
      if (!this.getWorld().isClient()) {
         this.setDead(true);
         LivingEntity eatingTargetRef = this.eatingTarget;
         if (eatingTargetRef != null) {
            eatingTargetRef.removeScoreboardTag("dannys-aot:being_grabbed");
         }

         this.setEating(false);
         this.setEatingTargetId(-1);
         this.setEyeHurt(false);
         this.grabPhaseComplete = false;
         this.eatingTarget = null;

         for (Entity passenger : this.getPassengerList()) {
            if (passenger instanceof ServerPlayerEntity serverPlayer && this.isSelfInjectionPassenger(passenger)) {
               serverPlayer.setInvisible(false);
               serverPlayer.stopRiding();
               serverPlayer.kill();
               DannysAot.LOGGER.info("Player {} died with their pure titan", serverPlayer.getName().getString());
            }
         }

         if (eatingTargetRef != null) {
            eatingTargetRef.stopRiding();
         }

         for (Entity passengerx : this.getPassengerList()) {
            passengerx.stopRiding();
         }
      }

      super.onDeath(damageSource);
   }

   @Override
   protected void updatePostDeath() {
      this.deathTime++;
      if (this.deathTime >= 200 && !this.getWorld().isClient() && !this.isRemoved()) {
         this.getWorld().sendEntityStatus(this, (byte)60);
         this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   public void triggerEyeHurt() {
      if (!this.getWorld().isClient() && !this.isDead() && !this.isEyeHurt() && this.eyeStunCooldown <= 0) {
         this.setEyeHurt(true);
         this.eyeHurtTicks = 100;
         this.eyeStunCooldown = 200;
         LivingEntity eatingTargetRef = this.eatingTarget;
         if (eatingTargetRef != null) {
            eatingTargetRef.removeScoreboardTag("dannys-aot:being_grabbed");
         }

         this.setTarget(null);
         this.setEating(false);
         this.setEatingTargetId(-1);
         this.grabPhaseComplete = false;
         this.eatingTarget = null;
         if (eatingTargetRef != null) {
            eatingTargetRef.stopRiding();
         }

         for (Entity passenger : this.getPassengerList()) {
            passenger.stopRiding();
         }
      }
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_IS_AGGRESSIVE, false);
      this.dataTracker.startTracking(DATA_RUN_ANIMATION, 1);
      this.dataTracker.startTracking(DATA_IDLE_ANIMATION, 1);
      this.dataTracker.startTracking(DATA_IS_EATING, false);
      this.dataTracker.startTracking(DATA_EAT_TICK, 0);
      this.dataTracker.startTracking(DATA_EAT_PHASE, 0);
      this.dataTracker.startTracking(DATA_IS_EYE_HURT, false);
      this.dataTracker.startTracking(DATA_IS_DEAD, false);
      this.dataTracker.startTracking(DATA_EATING_TARGET_ID, -1);
      this.dataTracker.startTracking(DATA_DISMOUNT_ALLOWED, false);
      this.dataTracker.startTracking(DATA_IS_COMMANDED_STOP, false);
   }

   @Override
   public boolean isAttacking() {
      return this.dataTracker.get(DATA_IS_AGGRESSIVE);
   }

   @Override
   public void setAttacking(boolean attacking) {
      this.dataTracker.set(DATA_IS_AGGRESSIVE, attacking);
   }

   public int getRunAnimation() {
      return this.dataTracker.get(DATA_RUN_ANIMATION);
   }

   public void setRunAnimation(int runAnim) {
      this.dataTracker.set(DATA_RUN_ANIMATION, runAnim);
   }

   public int getIdleAnimation() {
      return this.dataTracker.get(DATA_IDLE_ANIMATION);
   }

   public void setIdleAnimation(int idleAnim) {
      this.dataTracker.set(DATA_IDLE_ANIMATION, idleAnim);
   }

   @Override
   public boolean isEating() {
      return this.dataTracker.get(DATA_IS_EATING);
   }

   public void setEating(boolean eating) {
      this.dataTracker.set(DATA_IS_EATING, eating);
   }

   public int getEatTick() {
      return this.dataTracker.get(DATA_EAT_TICK);
   }

   public void setEatTick(int tick) {
      this.dataTracker.set(DATA_EAT_TICK, tick);
   }

   public int getSyncedEatPhase() {
      return this.dataTracker.get(DATA_EAT_PHASE);
   }

   public void setSyncedEatPhase(TitanEntity.EatPhase phase) {
      this.dataTracker.set(DATA_EAT_PHASE, phase.ordinal());
   }

   @Override
   public boolean isEyeHurt() {
      return this.dataTracker.get(DATA_IS_EYE_HURT);
   }

   public void setEyeHurt(boolean eyeHurt) {
      this.dataTracker.set(DATA_IS_EYE_HURT, eyeHurt);
   }

   @Override
   public boolean isDead() {
      return this.dataTracker.get(DATA_IS_DEAD);
   }

   public void setDead(boolean dead) {
      this.dataTracker.set(DATA_IS_DEAD, dead);
   }

   @Override
   public int getEatingTargetId() {
      return this.dataTracker.get(DATA_EATING_TARGET_ID);
   }

   public void setEatingTargetId(int id) {
      this.dataTracker.set(DATA_EATING_TARGET_ID, id);
   }

   public void addSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.add(passenger.getUuid());
   }

   public boolean isSelfInjectionPassenger(Entity passenger) {
      return this.selfInjectionPassengers.contains(passenger.getUuid());
   }

   public void removeSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.remove(passenger.getUuid());
      this.sneakDeathTimers.remove(passenger.getUuid());
   }

   public void notifyDismountBlocked(Entity passenger) {
      this.lastBlockedDismountTick.put(passenger.getUuid(), this.getWorld().getTime());
   }

   public boolean isDismountAllowed() {
      return this.dataTracker.get(DATA_DISMOUNT_ALLOWED);
   }

   public void setDismountAllowed(boolean allowed) {
      this.dataTracker.set(DATA_DISMOUNT_ALLOWED, allowed);
   }

   public boolean isKnockedBack() {
      return this.knockbackTicks > 0;
   }

   public boolean isCommandedStop() {
      return this.dataTracker.get(DATA_IS_COMMANDED_STOP);
   }

   @Override
   public void cancelEating() {
      if (this.isEating()) {
         if (this.eatingTarget != null) {
            this.eatingTarget.removeScoreboardTag("dannys-aot:being_grabbed");
            this.setDismountAllowed(true);
            this.eatingTarget.stopRiding();
            this.setDismountAllowed(false);
         }

         this.setEatingTargetId(-1);
         this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
         this.eatTicks = 0;
         this.setEatTick(0);
         this.eatPhase = TitanEntity.EatPhase.SWOOP;
         this.eatingTarget = null;
         this.grabPhaseComplete = false;
         this.setEating(false);
      }
   }

   public void applyKnockback(Vec3d velocity) {
      this.knockbackTicks = 15;
      this.setVelocity(velocity);
      this.velocityModified = true;
   }

   @Override
   protected void initGoals() {
      this.goalSelector.add(1, new TitanEntity.TitanChaseGoal(this));
      this.goalSelector.add(3, new TitanEntity.TitanWanderGoal(this));
      this.goalSelector.add(4, new TitanLookAtPlayerGoal(this, 40.0));
      this.goalSelector.add(5, new LookAroundGoal(this));
      this.targetSelector.add(1, new RevengeGoal(this));
      this.targetSelector.add(2, new TitanEntity.TitanFindTargetGoal(this));
   }

   @Override
   public void setTarget(LivingEntity target) {
      super.setTarget(ModNetworking.isFounder(target) ? null : target);
   }

   @Override
   public void tick() {
      if (this.knockbackTicks > 0) {
         this.knockbackTicks--;
      }

      if (!this.getWorld().isClient()) {
         if (this.age > 20 && !this.isDead()) {
            if (this.hitboxRespawnCooldown > 0) {
               this.hitboxRespawnCooldown--;
            } else if (this.napeEntity == null || this.napeEntity.isRemoved() || this.eyeEntity == null || this.eyeEntity.isRemoved()) {
               if (this.napeEntity == null || this.napeEntity.isRemoved()) {
                  TitanNapeEntity nape = new TitanNapeEntity(DannysAot.TITAN_NAPE, this.getWorld());
                  nape.setParentTitan(this);
                  nape.setPosition(this.getX(), this.getY(), this.getZ());
                  this.getWorld().spawnEntity(nape);
                  this.napeEntity = nape;
               }

               if (this.eyeEntity == null || this.eyeEntity.isRemoved()) {
                  TitanEyeEntity eye = new TitanEyeEntity(DannysAot.TITAN_EYE, this.getWorld());
                  eye.setParentTitan(this);
                  eye.setPosition(this.getX(), this.getY(), this.getZ());
                  this.getWorld().spawnEntity(eye);
                  this.eyeEntity = eye;
               }

               this.hitboxRespawnCooldown = 100;
            }
         }

         long timeOfDay = this.getWorld().getTimeOfDay() % 24000L;
         if (!BloodmoonState.isActive()
            && VillagerTransformTracker.getOwnerName(this) == null
            && timeOfDay >= this.nightSleepStart
            && timeOfDay <= this.nightSleepEnd) {
            if (!this.isNightSleeping) {
               this.isNightSleeping = true;
               if (this.isEating()) {
                  this.cancelEating();
               }
            }

            this.setAiDisabled(true);
            this.setTarget(null);
         } else if ((this.isNightSleeping || this.isAiDisabled()) && !TitanTossManager.isRagdolled(this.getId())) {
            this.isNightSleeping = false;
            this.setAiDisabled(false);
            this.randomizeNightSleepTimes();
         }

         if (this.pendingEjectEatingVictims) {
            this.pendingEjectEatingVictims = false;
            List<Entity> toEject = new ArrayList<>();

            for (Entity passenger : this.getPassengerList()) {
               if (!this.isSelfInjectionPassenger(passenger)) {
                  toEject.add(passenger);
               }
            }

            for (Entity passengerx : toEject) {
               passengerx.stopRiding();
               if (this.hasPassenger(passengerx)) {
                  Vec3d ejectPos = this.getPos().add(0.0, 1.0, 0.0);
                  passengerx.setPosition(ejectPos.x, ejectPos.y, ejectPos.z);
                  passengerx.detach();
               }

               if (this.hasPassenger(passengerx)) {
                  Vec3d awayPos = this.getPos().add(5.0, 2.0, 5.0);
                  passengerx.requestTeleport(awayPos.x, awayPos.y, awayPos.z);
                  passengerx.stopRiding();
               }
            }
         }

         for (Entity passengerx : this.getPassengerList()) {
            if (passengerx instanceof LivingEntity living && living.isDead()) {
               living.setInvisible(false);
               living.stopRiding();
               if (living == this.eatingTarget) {
                  this.setEating(false);
                  this.setEatingTargetId(-1);
                  this.eatTicks = 0;
                  this.setEatTick(0);
                  this.grabPhaseComplete = false;
                  this.eatPhase = TitanEntity.EatPhase.SWOOP;
                  this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
                  this.eatingTarget = null;
               }
            }
         }

         long currentTick = this.getWorld().getTime();

         for (Entity passengerxx : this.getPassengerList()) {
            if (passengerxx instanceof ServerPlayerEntity serverPlayer && this.isSelfInjectionPassenger(passengerxx)) {
               UUID playerUUID = serverPlayer.getUuid();
               Long lastBlockedTick = this.lastBlockedDismountTick.get(playerUUID);
               boolean isSneaking = serverPlayer.isSneaking() || lastBlockedTick != null && currentTick - lastBlockedTick <= 5L;
               if (isSneaking) {
                  int sneakTicks = this.sneakDeathTimers.getOrDefault(playerUUID, 0) + 1;
                  this.sneakDeathTimers.put(playerUUID, sneakTicks);
                  float remainingSeconds = (60 - sneakTicks) / 20.0F;
                  if (sneakTicks >= 60) {
                     serverPlayer.setInvisible(false);
                     this.sneakDeathTimers.remove(playerUUID);
                     this.lastBlockedDismountTick.remove(playerUUID);
                     serverPlayer.stopRiding();
                     RegistryKey<DamageType> becomeTitanKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "become_titan"));
                     RegistryEntry<DamageType> becomeTitanHolder = this.getWorld()
                        .getRegistryManager()
                        .getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE)
                        .getOrThrow(becomeTitanKey);
                     DamageSource becomeTitanDamage = new DamageSource(becomeTitanHolder);
                     if (serverPlayer.isCreative()) {
                        serverPlayer.kill();
                     } else {
                        serverPlayer.damage(becomeTitanDamage, Float.MAX_VALUE);
                     }
                  } else {
                     String countdown = String.format("%.1f", remainingSeconds);
                     serverPlayer.sendMessage(Text.literal("Accept your fate? (" + countdown + ")"), true);
                  }
               } else if (this.sneakDeathTimers.containsKey(playerUUID)) {
                  this.sneakDeathTimers.remove(playerUUID);
                  this.lastBlockedDismountTick.remove(playerUUID);
                  serverPlayer.sendMessage(Text.empty(), true);
               }
            }
         }

         LivingEntity currentTarget = this.getTarget();
         boolean hasTarget = currentTarget != null && currentTarget.isAlive();
         if (!hasTarget && currentTarget != null) {
            this.setTarget(null);
         }

         this.setAttacking(hasTarget);
         if (VillagerTransformTracker.isRegrouping(this)) {
            this.setAttacking(true);
         }

         String ownerName = VillagerTransformTracker.getOwnerName(this);
         if (ownerName != null && VillagerTransformTracker.hasActiveTarget(ownerName) && !this.isCommandedStop()) {
            this.setAttacking(true);
         }

         if (this.isEyeHurt()) {
            if (this.eyeHurtTicks > 0) {
               this.eyeHurtTicks--;
               if (this.eyeEntity != null) {
                  double vx = (this.random.nextDouble() - 0.5) * 0.15;
                  double vy = 0.3;
                  double vz = (this.random.nextDouble() - 0.5) * 0.15;
                  ((ServerWorld)this.getWorld())
                     .spawnParticles(daot.compat.BackportEffects.WHITE_SMOKE, this.eyeEntity.getX(), this.eyeEntity.getY(), this.eyeEntity.getZ(), 0, vx, vy, vz, 0.05);
                  ((ServerWorld)this.getWorld())
                     .spawnParticles(ParticleTypes.POOF, this.eyeEntity.getX(), this.eyeEntity.getY(), this.eyeEntity.getZ(), 0, vx, vy, vz, 0.05);
               }
            } else {
               this.setEyeHurt(false);
            }
         }

         boolean stopped = this.getCommandTags().contains("dannysaot_commanded_stop") || StrwsRestraintTracker.isFullyRestrained(this.getUuid());
         if (this.dataTracker.get(DATA_IS_COMMANDED_STOP) != stopped) {
            this.dataTracker.set(DATA_IS_COMMANDED_STOP, stopped);
         }

         double targetSpeed;
         if (this.isEating() || this.isEyeHurt() || this.isDead() || this.isCommandedStop()) {
            targetSpeed = 0.0;
         } else if (BloodmoonState.isActive()) {
            targetSpeed = getChaseSpeed() * 2.0 * this.speedMultiplier;
         } else if (VillagerTransformTracker.isRegrouping(this)) {
            targetSpeed = getChaseSpeed() * this.speedMultiplier;
         } else if (ownerName != null && VillagerTransformTracker.hasActiveTarget(ownerName)) {
            targetSpeed = getChaseSpeed() * 2.0 * this.speedMultiplier;
         } else if (hasTarget) {
            targetSpeed = getChaseSpeed() * this.speedMultiplier;
         } else {
            targetSpeed = getWanderSpeed() * this.speedMultiplier;
         }

         this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(targetSpeed);
         if ((this.isCommandedStop() || VillagerTransformTracker.isRegrouping(this)) && this.getTarget() != null) {
            this.setTarget(null);
         }

         if (VillagerTransformTracker.isRegrouping(this)) {
            VillagerTransformTracker.RegroupTarget regTarget = VillagerTransformTracker.getRegroupTarget(this);
            if (regTarget != null) {
               double dx = regTarget.x() - this.getX();
               double dz = regTarget.z() - this.getZ();
               double distSqr = dx * dx + dz * dz;
               if (distSqr < 4.0) {
                  VillagerTransformTracker.clearRegroup(this);
                  this.addCommandTag("dannysaot_commanded_stop");
                  this.setVelocity(0.0, this.getVelocity().getY(), 0.0);
                  float facingYaw = regTarget.facingYaw();
                  this.targetYRot = facingYaw;
                  this.setYaw(facingYaw);
                  this.bodyYaw = facingYaw;
                  this.headYaw = facingYaw;
                  this.prevYaw = facingYaw;
               } else {
                  double dist = Math.sqrt(distSqr);
                  float desiredYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                  this.targetYRot = desiredYaw;
                  this.setVelocity(
                     dx / dist * getChaseSpeed() * this.speedMultiplier, this.getVelocity().getY(), dz / dist * getChaseSpeed() * this.speedMultiplier
                  );
               }
            }
         }

         if (ownerName != null
            && VillagerTransformTracker.hasActiveTarget(ownerName)
            && !this.isCommandedStop()
            && !VillagerTransformTracker.isRegrouping(this)) {
            VillagerTransformTracker.ActiveTarget at = VillagerTransformTracker.getActiveTarget(ownerName);
            if (((ServerWorld)this.getWorld()).getEntity(at.targetUUID()) instanceof LivingEntity livingx && livingx.isAlive() && livingx != this) {
               this.setTarget(livingx);
            }
         }

         if (this.eyeStunCooldown > 0) {
            this.eyeStunCooldown--;
         }

         if (this.contactDamageCooldown > 0) {
            this.contactDamageCooldown--;
         } else {
            this.tickContactDamage();
         }

         if (!this.isEating() && !this.isEyeHurt() && !this.isDead() && !this.isNightSleeping) {
            LivingEntity target = this.getTarget();
            boolean alreadyMounted = target != null && target.getVehicle() != null;
            if (target != null && target.isAlive() && !this.hasPassenger(target) && !alreadyMounted) {
               if (target.getCommandTags().contains("dannys-aot:being_grabbed") && target.getVehicle() == null) {
                  target.removeScoreboardTag("dannys-aot:being_grabbed");
               }

               double distToTarget = this.distanceTo(target);
               boolean ejectMode = this.isTargetInEjectMode(target);
               double normalGrabReach = ejectMode ? 8.0 : 4.0;
               if (distToTarget < normalGrabReach && TitanGrabUtil.canReachTarget(this, target)) {
                  boolean targetOnGround = target.isOnGround() || ejectMode;
                  boolean veryClose = distToTarget < 3.0;
                  int eatChance = targetOnGround ? (veryClose ? 70 : 50) : 5;
                  if (this.random.nextInt(100) < eatChance) {
                     this.performEat(target);
                  }
               }
            }
         }

         if (this.isEating()) {
            if (this.eatingTarget != null) {
               Entity targetVehicle = this.eatingTarget.getVehicle();
               if (targetVehicle != null && targetVehicle != this) {
                  this.eatingTarget.removeScoreboardTag("dannys-aot:being_grabbed");
                  this.setEating(false);
                  this.setEatingTargetId(-1);
                  this.eatTicks = 0;
                  this.setEatTick(0);
                  this.grabPhaseComplete = false;
                  this.eatPhase = TitanEntity.EatPhase.SWOOP;
                  this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
                  this.eatingTarget = null;
               }
            }

            if (this.eatingTarget != null && TitanGrabImmunity.isImmune(this.eatingTarget)) {
               LivingEntity target = this.eatingTarget;
               target.removeScoreboardTag("dannys-aot:being_grabbed");
               this.setEating(false);
               this.setEatingTargetId(-1);
               this.eatTicks = 0;
               this.setEatTick(0);
               this.grabPhaseComplete = false;
               this.eatPhase = TitanEntity.EatPhase.SWOOP;
               this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
               this.eatingTarget = null;
               target.stopRiding();
               this.setTarget(null);
            }

            if (this.eatingTarget == null || !this.eatingTarget.isDead() && !this.eatingTarget.isRemoved()) {
               if (this.eatPhase == TitanEntity.EatPhase.SWOOP) {
                  this.eatTicks++;
                  this.setEatTick(this.eatTicks);
                  if (this.eatTicks >= 10) {
                     this.eatPhase = TitanEntity.EatPhase.PULL_TO_HOLD;
                     this.setSyncedEatPhase(TitanEntity.EatPhase.PULL_TO_HOLD);
                     this.eatTicks = 0;
                     this.setEatTick(0);
                     this.holdDuration = 40 + this.random.nextInt(61);
                  }
               } else if (this.eatPhase == TitanEntity.EatPhase.PULL_TO_HOLD) {
                  this.eatTicks++;
                  this.setEatTick(this.eatTicks);
                  if (this.eatingTarget != null && this.eatingTarget.isAlive()) {
                     Vec3d headPos = this.getPos().add(0.0, this.getHeight() - 3.0, 0.0);
                     float yawRad = (float)Math.toRadians(this.getYaw());
                     double offsetX = -Math.sin(yawRad) * 5.0;
                     double offsetZ = Math.cos(yawRad) * 5.0;
                     Vec3d holdPos = headPos.add(offsetX, -1.5, offsetZ);
                     Vec3d currentPos = this.eatingTarget.getPos();
                     Vec3d toTarget = holdPos.subtract(currentPos);
                     double distance = toTarget.length();
                     if (!(distance < 0.5) && this.eatTicks < 40) {
                        double speedMultiplier = Math.min(1.0, distance / 10.0);
                        double adjustedSpeed = 1.5 * Math.max(0.3, speedMultiplier);
                        Vec3d direction = toTarget.normalize();
                        Vec3d velocity = direction.multiply(Math.min(adjustedSpeed, distance));
                        this.eatingTarget.setVelocity(velocity);
                        this.eatingTarget.velocityModified = true;
                     } else {
                        this.eatingTarget.setVelocity(Vec3d.ZERO);
                        this.eatingTarget.requestTeleport(holdPos.x, holdPos.y, holdPos.z);
                        this.grabPhaseComplete = true;
                        this.eatPhase = TitanEntity.EatPhase.HOLD;
                        this.setSyncedEatPhase(TitanEntity.EatPhase.HOLD);
                        this.eatTicks = 0;
                        this.setEatTick(0);
                     }
                  }
               } else if (this.eatPhase == TitanEntity.EatPhase.HOLD) {
                  if (!this.isCommandedStop()) {
                     this.eatTicks++;
                     this.setEatTick(this.eatTicks);
                  }

                  if (this.eatingTarget != null && this.eatingTarget.isAlive()) {
                     Vec3d headPos = this.getPos().add(0.0, this.getHeight() - 3.0, 0.0);
                     float yawRad = (float)Math.toRadians(this.getYaw());
                     double offsetX = -Math.sin(yawRad) * 5.0;
                     double offsetZ = Math.cos(yawRad) * 5.0;
                     Vec3d holdPos = headPos.add(offsetX, -1.5, offsetZ);
                     this.eatingTarget.setVelocity(Vec3d.ZERO);
                     this.eatingTarget.requestTeleport(holdPos.x, holdPos.y, holdPos.z);
                  }

                  if (!this.isCommandedStop() && this.eatTicks >= this.holdDuration) {
                     this.eatPhase = TitanEntity.EatPhase.PULL_TO_EAT;
                     this.setSyncedEatPhase(TitanEntity.EatPhase.PULL_TO_EAT);
                     this.eatTicks = 0;
                     this.setEatTick(0);
                  }
               } else if (this.eatPhase == TitanEntity.EatPhase.PULL_TO_EAT) {
                  if (!this.isCommandedStop()) {
                     this.eatTicks++;
                     this.setEatTick(this.eatTicks);
                     if (this.eatingTarget != null && this.eatingTarget.isAlive()) {
                        Vec3d eatPos = this.getPos().add(0.0, this.getHeight() - 3.0, 0.0);
                        Vec3d currentPos = this.eatingTarget.getPos();
                        Vec3d toTarget = eatPos.subtract(currentPos);
                        double distance = toTarget.length();
                        if (!(distance < 0.5) && this.eatTicks < 40) {
                           double speedMultiplier = Math.min(1.0, distance / 10.0);
                           double adjustedSpeed = 1.5 * Math.max(0.3, speedMultiplier);
                           Vec3d direction = toTarget.normalize();
                           Vec3d velocity = direction.multiply(Math.min(adjustedSpeed, distance));
                           this.eatingTarget.setVelocity(velocity);
                           this.eatingTarget.velocityModified = true;
                        } else {
                           this.eatingTarget.setVelocity(Vec3d.ZERO);
                           this.eatingTarget.requestTeleport(eatPos.x, eatPos.y, eatPos.z);
                           this.eatingTarget.startRiding(this, true);
                           this.eatPhase = TitanEntity.EatPhase.EAT;
                           this.setSyncedEatPhase(TitanEntity.EatPhase.EAT);
                           this.eatTicks = 0;
                           this.setEatTick(0);
                        }
                     }
                  } else if (this.eatingTarget != null && this.eatingTarget.isAlive()) {
                     Vec3d headPos = this.getPos().add(0.0, this.getHeight() - 3.0, 0.0);
                     float yawRad = (float)Math.toRadians(this.getYaw());
                     double ptOffX = -Math.sin(yawRad) * 5.0;
                     double ptOffZ = Math.cos(yawRad) * 5.0;
                     Vec3d ptHoldPos = headPos.add(ptOffX, -1.5, ptOffZ);
                     this.eatingTarget.setVelocity(Vec3d.ZERO);
                     this.eatingTarget.requestTeleport(ptHoldPos.x, ptHoldPos.y, ptHoldPos.z);
                  }
               } else {
                  if (this.eatingTarget != null && this.eatingTarget.isAlive() && !this.eatingTarget.hasVehicle()) {
                     this.eatingTarget.startRiding(this, true);
                  }

                  if (!this.isCommandedStop()) {
                     this.eatTicks++;
                     this.setEatTick(this.eatTicks);
                     if (this.eatTicks >= 60) {
                        if (this.eatingTarget != null && !this.getWorld().isClient()) {
                           LivingEntity targetRef = this.eatingTarget;
                           TitanPowerHelper.handleEatingCompletion(this, targetRef, (ServerWorld)this.getWorld());
                           if (this.isDead()) {
                              return;
                           }
                        }

                        if (this.eatingTarget != null) {
                           this.eatingTarget.removeScoreboardTag("dannys-aot:being_grabbed");
                        }

                        this.setDismountAllowed(true);
                        this.setEating(false);
                        List<Entity> toEject = new ArrayList<>();

                        for (Entity passengerxxx : this.getPassengerList()) {
                           if (!this.isSelfInjectionPassenger(passengerxxx)) {
                              toEject.add(passengerxxx);
                           }
                        }

                        for (Entity passengerxxxx : toEject) {
                           float yaw = this.getYaw();
                           double yawRad = Math.toRadians(yaw);
                           double ejectX = this.getX() - Math.sin(yawRad) * 3.0;
                           double ejectZ = this.getZ() + Math.cos(yawRad) * 3.0;
                           double ejectY = this.getY() + 1.0;
                           passengerxxxx.stopRiding();
                           passengerxxxx.requestTeleport(ejectX, ejectY, ejectZ);
                           passengerxxxx.setVelocity(-Math.sin(yawRad) * 0.5, 0.2, Math.cos(yawRad) * 0.5);
                           if (passengerxxxx instanceof LivingEntity livingx) {
                              livingx.velocityModified = true;
                           }
                        }

                        this.setDismountAllowed(false);
                        this.setEatingTargetId(-1);
                        this.eatTicks = 0;
                        this.setEatTick(0);
                        this.grabPhaseComplete = false;
                        this.eatPhase = TitanEntity.EatPhase.SWOOP;
                        this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
                        this.eatingTarget = null;
                        this.setTarget(null);
                     } else if (this.eatTicks % 10 == 0 && this.eatingTarget != null && this.eatingTarget.isAlive()) {
                        float damage = (float)ModConfig.get().pureTitanEatingDamage;
                        if (DannysAot.isUnfairPureTitans(this.getWorld())) {
                           damage *= 2.0F;
                        }

                        if (damage > 0.0F) {
                           this.eatingTarget.timeUntilRegen = 0;
                           String[] titanEatDamageTypes = new String[]{"titan_devour", "titan_food", "titan_torn"};
                           String chosenType = titanEatDamageTypes[this.random.nextInt(titanEatDamageTypes.length)];
                           RegistryKey<DamageType> eatDamageKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", chosenType));
                           RegistryEntry<DamageType> eatDamageHolder = this.getWorld()
                              .getRegistryManager()
                              .getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE)
                              .getOrThrow(eatDamageKey);
                           DamageSource titanEatDamage = new DamageSource(eatDamageHolder, this);
                           this.eatingTarget.damage(titanEatDamage, damage);
                        }
                     }
                  }
               }
            } else {
               LivingEntity target = this.eatingTarget;
               if (!this.getWorld().isClient()) {
                  target.removeScoreboardTag("dannys-aot:being_grabbed");
                  TitanPowerHelper.handleEatingCompletion(this, target, (ServerWorld)this.getWorld());
                  if (this.isDead()) {
                     return;
                  }
               }

               this.setEating(false);
               this.setEatingTargetId(-1);
               this.eatTicks = 0;
               this.setEatTick(0);
               this.grabPhaseComplete = false;
               this.eatPhase = TitanEntity.EatPhase.SWOOP;
               this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
               this.eatingTarget = null;
               target.stopRiding();
            }
         } else if (this.eatTicks != 0) {
            if (this.eatingTarget != null) {
               this.eatingTarget.removeScoreboardTag("dannys-aot:being_grabbed");
            }

            this.eatTicks = 0;
            this.setEatTick(0);
            this.setEatingTargetId(-1);
            this.grabPhaseComplete = false;
            this.eatPhase = TitanEntity.EatPhase.SWOOP;
            this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
            this.eatingTarget = null;
         }
      }

      if (!this.getWorld().isClient() && this.isAlive() && !this.isEating()) {
         boolean aggressive = this.isAttacking();
         String stompOwner = VillagerTransformTracker.getOwnerName(this);
         boolean hasOwnerTarget = stompOwner != null && VillagerTransformTracker.hasActiveTarget(stompOwner) && !this.isCommandedStop();
         double baseCycleLength = aggressive ? 1.0 : 2.0;
         double animSpeed = aggressive ? 1.0 : 1.0;
         if (hasOwnerTarget) {
            animSpeed *= 2.0;
         }

         double secondsPerCycle = baseCycleLength / animSpeed;
         double ticksPerCycle = secondsPerCycle * 20.0;
         double currentAnimTime = this.age % ticksPerCycle / 20.0 * animSpeed;
         double[] keyframes = aggressive ? RUN_STOMP_KEYFRAMES : WALK_STOMP_KEYFRAMES;
         double cycleMod = aggressive ? 1.0 : 2.0;
         boolean isActuallyMoving = this.getVelocity().horizontalLengthSquared() > 0.001;
         if (isActuallyMoving
            && this.isAtStompKeyframe(currentAnimTime, keyframes, cycleMod)
            && !this.isAtStompKeyframe(this.lastAnimationTime, keyframes, cycleMod)) {
            this.triggerStompEffects();
         }

         this.lastAnimationTime = currentAnimTime;
      }

      super.tick();
      if (!this.getWorld().isClient() && this.isCommandedStop()) {
         this.setYaw(this.prevYaw);
         this.bodyYaw = this.prevYaw;
         this.headYaw = this.prevYaw;
      } else if (!this.getWorld().isClient()) {
         if (this.isEating()
            && this.eatingTarget != null
            && this.eatingTarget.isAlive()
            && (this.eatPhase == TitanEntity.EatPhase.PULL_TO_HOLD || this.eatPhase == TitanEntity.EatPhase.HOLD)) {
            double dx = this.eatingTarget.getX() - this.getX();
            double dz = this.eatingTarget.getZ() - this.getZ();
            if (dx * dx + dz * dz > 1.0E-4) {
               float desiredYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
               this.targetYRot = desiredYaw;
            }
         }

         if (!this.isEating() || this.eatPhase == TitanEntity.EatPhase.PULL_TO_HOLD || this.eatPhase == TitanEntity.EatPhase.HOLD) {
            float deltaRot = this.targetYRot - this.prevYaw;

            while (deltaRot > 180.0F) {
               deltaRot -= 360.0F;
            }

            while (deltaRot < -180.0F) {
               deltaRot += 360.0F;
            }

            float clampedDelta = Math.max(-8.0F, Math.min(8.0F, deltaRot));
            this.setYaw(this.prevYaw + clampedDelta);
            this.bodyYaw = this.getYaw();
         }
      }

      if (!this.getWorld().isClient() && !this.isCommandedStop()) {
         this.headYaw = MathHelper.clampAngle(this.headYaw, this.bodyYaw, this.getMaxHeadRotation());
      }
   }

   @Override
   public int getMaxHeadRotation() {
      return 60;
   }

   @Override
   public int getMaxLookYawChange() {
      return 8;
   }

   private boolean isTargetInEjectMode(Entity target) {
      Entity vehicle = target.getVehicle();
      if (vehicle instanceof AttackTitanEntity at && at.isDismounting()) {
         return true;
      } else if (vehicle instanceof ArmoredTitanEntity ar && ar.isDismounting()) {
         return true;
      } else {
         return vehicle instanceof ColossalTitanEntity ct && ct.isDismounting() ? true : vehicle instanceof FemaleTitanEntity ft && ft.isDismounting();
      }
   }

   public void performEat(LivingEntity target) {
      if (!this.isEating() && target != null) {
         if (!this.isCommandedStop()) {
            if (!VillagerTransformTracker.isShifterTitan(target)) {
               if (!TitanGrabImmunity.isImmune(target)) {
                  if (!this.hasPassenger(target)) {
                     Entity existingVehicle = target.getVehicle();
                     if (!(existingVehicle instanceof SmallTitanEntity)
                        && !(existingVehicle instanceof SmallTitan2Entity)
                        && !(existingVehicle instanceof FritzTitanEntity)
                        && !(existingVehicle instanceof TitanEntity)) {
                        if (!(existingVehicle instanceof AttackTitanEntity attackTitan && !attackTitan.isDismounting())) {
                           if (!(existingVehicle instanceof ArmoredTitanEntity armoredTitan && !armoredTitan.isDismounting())) {
                              if (!(existingVehicle instanceof ColossalTitanEntity colossalTitan && !colossalTitan.isDismounting())) {
                                 if (!(existingVehicle instanceof FemaleTitanEntity femaleTitan && !femaleTitan.isDismounting())) {
                                    target.removeScoreboardTag("dannys-aot:being_grabbed");
                                    target.addCommandTag("dannys-aot:being_grabbed");
                                    this.setEating(true);
                                    this.eatTicks = 0;
                                    this.setEatTick(0);
                                    this.eatingTarget = target;
                                    this.setEatingTargetId(target.getId());
                                    this.grabPhaseComplete = false;
                                    this.eatPhase = TitanEntity.EatPhase.SWOOP;
                                    this.setSyncedEatPhase(TitanEntity.EatPhase.SWOOP);
                                    if (existingVehicle instanceof AttackTitanEntity attackTitanx) {
                                       attackTitanx.setDismountAllowed(true);
                                       target.stopRiding();
                                       attackTitanx.setDismountAllowed(false);
                                    } else if (existingVehicle instanceof ArmoredTitanEntity armoredTitanx) {
                                       armoredTitanx.setDismountAllowed(true);
                                       target.stopRiding();
                                       armoredTitanx.setDismountAllowed(false);
                                    } else if (existingVehicle instanceof ColossalTitanEntity colossalTitanx) {
                                       colossalTitanx.setDismountAllowed(true);
                                       target.stopRiding();
                                       colossalTitanx.setDismountAllowed(false);
                                    } else if (existingVehicle instanceof FemaleTitanEntity femaleTitanx) {
                                       femaleTitanx.setDismountAllowed(true);
                                       target.stopRiding();
                                       femaleTitanx.setDismountAllowed(false);
                                    } else if (existingVehicle != null) {
                                       target.stopRiding();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      Vec3d headPos = this.getPos().add(0.0, this.getHeight() - 3.0, 0.0);
      if (this.getSyncedEatPhase() == TitanEntity.EatPhase.HOLD.ordinal()) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double offsetX = -Math.sin(yawRad) * 5.0;
         double offsetZ = Math.cos(yawRad) * 5.0;
         return headPos.add(offsetX, -1.5, offsetZ);
      } else {
         return headPos;
      }
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      Vec3d bpRidingPos = this.getPassengerRidingPos(passenger);
      positionUpdater.accept(passenger, bpRidingPos.x, bpRidingPos.y, bpRidingPos.z);
      if (!this.getWorld().isClient() && passenger instanceof PlayerEntity player) {
         if (this.isSelfInjectionPassenger(player)) {
            if (!player.isDead()) {
               player.setInvisible(true);
            }
         } else if (player.isInvisible()) {
            player.setInvisible(false);
         }
      }
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (!this.getWorld().isClient()) {
         this.selfInjectionPassengers.remove(passenger.getUuid());
         if (passenger instanceof PlayerEntity player) {
            player.setInvisible(false);
         }
      }
   }

   private boolean isAtStompKeyframe(double animTime, double[] keyframes, double cycleMod) {
      for (double keyframe : keyframes) {
         double diff = Math.abs(animTime % cycleMod - keyframe);
         if (diff < 0.08) {
            return true;
         }
      }

      return false;
   }

   private void triggerStompEffects() {
      if (!this.getWorld().isClient()) {
         boolean aggressive = this.isAttacking();
         double baseCycleLength = aggressive ? 1.0 : 2.0;
         double animSpeed = aggressive ? 1.0 : 1.0;
         double secondsPerCycle = baseCycleLength / animSpeed;
         double ticksPerCycle = secondsPerCycle * 20.0;
         double currentAnimTime = this.age % ticksPerCycle / 20.0 * animSpeed;
         double leftFootKeyframe = aggressive ? 0.25 : 0.5;
         boolean isLeftFoot = Math.abs(currentAnimTime - leftFootKeyframe) < 0.08;
         double footOffset = isLeftFoot ? -1.5 : 1.5;
         float yaw = this.getYaw();
         double yawRad = Math.toRadians(yaw);
         double footX = this.getX() + footOffset * Math.cos(yawRad);
         double footZ = this.getZ() + footOffset * Math.sin(yawRad);
         float baseVolume = 5.0F;
         float basePitch = 0.8F + this.random.nextFloat() * 0.4F;
         String pitchOwner = VillagerTransformTracker.getOwnerName(this);
         if (pitchOwner != null && VillagerTransformTracker.hasActiveTarget(pitchOwner) && !this.isCommandedStop()) {
            basePitch += 0.2F;
         }

         this.getWorld().playSound(null, footX, this.getY(), footZ, ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, baseVolume, basePitch);
         double stompDamageRadius = 3.0;
         double horseFlingRadius = 1.5;

         for (LivingEntity entity : this.getWorld()
            .getNonSpectatingEntities(
               LivingEntity.class,
               new Box(
                  footX - stompDamageRadius,
                  this.getY() - 1.0,
                  footZ - stompDamageRadius,
                  footX + stompDamageRadius,
                  this.getY() + 3.0,
                  footZ + stompDamageRadius
               )
            )) {
            if (entity != this && !ModNetworking.isFounder(entity)) {
               double distToFoot = Math.sqrt(Math.pow(entity.getX() - footX, 2.0) + Math.pow(entity.getZ() - footZ, 2.0));
               if (distToFoot < stompDamageRadius) {
                  float damage = (float)(10.0 * (1.0 - distToFoot / stompDamageRadius));
                  entity.damage(this.getDamageSources().mobAttack(this), damage);
                  if (distToFoot < horseFlingRadius) {
                     Entity vehicle = entity.getVehicle();
                     if (vehicle != null) {
                        entity.stopRiding();
                        double forwardX = -Math.sin(yawRad);
                        double forwardZ = Math.cos(yawRad);
                        Vec3d flingVelocity = new Vec3d(forwardX * 0.8, 0.6, forwardZ * 0.8);
                        entity.setVelocity(entity.getVelocity().add(flingVelocity));
                        vehicle.setVelocity(vehicle.getVelocity().add(flingVelocity));
                     }
                  }
               }
            }
         }

         if (this.getWorld() instanceof ServerWorld serverLevel) {
            BlockPos feetPos = new BlockPos((int)footX, (int)this.getY(), (int)footZ);
            BlockState groundState = this.getWorld().getBlockState(feetPos.down());
            if (!groundState.isAir()) {
               double particleRadius = 50.0;

               for (ServerPlayerEntity player : serverLevel.getPlayers()) {
                  if (player.squaredDistanceTo(footX, this.getY(), footZ) < particleRadius * particleRadius) {
                     for (int i = 0; i < 80; i++) {
                        double offsetX = (this.random.nextDouble() - 0.5) * 1.5;
                        double offsetZ = (this.random.nextDouble() - 0.5) * 1.5;
                        double spawnY = this.getY() + 0.1 + this.random.nextDouble() * 0.2;
                        double dx = offsetX;
                        double dz = offsetZ;
                        double dist = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                        if (dist > 0.001) {
                           dx = offsetX / dist;
                           dz = offsetZ / dist;
                        }

                        double velX = dx * (0.2 + this.random.nextDouble() * 0.4);
                        double velY = 0.2 + this.random.nextDouble() * 0.6;
                        double velZ = dz * (0.2 + this.random.nextDouble() * 0.4);
                        serverLevel.spawnParticles(
                           player,
                           new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                           true,
                           footX + offsetX,
                           spawnY,
                           footZ + offsetZ,
                           0,
                           velX,
                           velY,
                           velZ,
                           0.08
                        );
                     }

                     for (int i = 0; i < 50; i++) {
                        double offsetX = (this.random.nextDouble() - 0.5) * 1.2;
                        double offsetZ = (this.random.nextDouble() - 0.5) * 1.2;
                        double spawnY = this.getY() + this.random.nextDouble() * 0.3;
                        Vec3d movement = this.getVelocity();
                        double backX = -movement.x * 1.5;
                        double backZ = -movement.z * 1.5;
                        double velX = backX + (this.random.nextDouble() - 0.5) * 0.3;
                        double velY = 0.15 + this.random.nextDouble() * 0.3;
                        double velZ = backZ + (this.random.nextDouble() - 0.5) * 0.3;
                        serverLevel.spawnParticles(player, ParticleTypes.ASH, true, footX + offsetX, spawnY, footZ + offsetZ, 0, velX, velY, velZ, 0.06);
                     }

                     for (int i = 0; i < 20; i++) {
                        double offsetX = (this.random.nextDouble() - 0.5) * 1.0;
                        double offsetZ = (this.random.nextDouble() - 0.5) * 1.0;
                        serverLevel.spawnParticles(
                           player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, footX + offsetX, this.getY() + 0.2, footZ + offsetZ, 0, 0.0, 0.05, 0.0, 0.02
                        );
                     }
                  }
               }
            }
         }
      }
   }

   protected double measuredSpeedSqr() {
      if (this.age != this.animSpeedTick) {
         this.animSpeedTick = this.age;
         double dx = this.getX() - this.lastRenderX;
         double dz = this.getZ() - this.lastRenderZ;
         this.animSpeedSqr = this.animSpeedSqr * 0.7 + (dx * dx + dz * dz) * 0.3;
      }

      return this.animSpeedSqr;
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 5, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> state) {
      if (this.isDead()) {
         state.getController().setAnimation(DEATH_ANIM);
         state.getController().setAnimationSpeed(1.0);
         return PlayState.CONTINUE;
      } else if (this.isEyeHurt()) {
         state.getController().setAnimation(EYEHURT_ANIM);
         state.getController().setAnimationSpeed(1.0);
         return PlayState.CONTINUE;
      } else if (this.isAiDisabled() && this.getVehicle() == null && this.isOnGround()) {
         state.getController().setAnimation(NIGHT_SLEEP_ANIM);
         state.getController().setAnimationSpeed(1.0);
         return PlayState.CONTINUE;
      } else if (this.isEating()) {
         int phase = this.getSyncedEatPhase();
         if (phase == TitanEntity.EatPhase.SWOOP.ordinal()) {
            state.getController().transitionLength(5);
            state.getController().setAnimation(SWOOP_ANIM);
         } else if (phase != TitanEntity.EatPhase.PULL_TO_HOLD.ordinal() && phase != TitanEntity.EatPhase.HOLD.ordinal()) {
            state.getController().transitionLength(10);
            state.getController().setAnimation(EAT_ANIM);
         } else {
            state.getController().transitionLength(30);
            state.getController().setAnimation(HOLD_ANIM);
         }

         state.getController().setAnimationSpeed(this.isCommandedStop() ? 0.0 : 1.0);
         return PlayState.CONTINUE;
      } else {
         boolean actuallyMoving = this.measuredSpeedSqr() > 0.001;
         if (this.isAttacking() && actuallyMoving) {
            String animOwner = VillagerTransformTracker.getOwnerName(this);
            boolean pursuing = animOwner != null && VillagerTransformTracker.hasActiveTarget(animOwner) && !this.isCommandedStop();
            int runAnim = this.getRunAnimation();
            RawAnimation selectedAnim = runAnim == 2 ? RUN2_ANIM : RUN_ANIM;
            state.getController().setAnimation(selectedAnim);
            state.getController().setAnimationSpeed((pursuing ? 2.0 : 1.0) * this.speedMultiplier);
         } else if (actuallyMoving) {
            state.getController().setAnimation(WALK1_ANIM);
            state.getController().setAnimationSpeed(this.speedMultiplier);
         } else {
            int idleAnim = this.getIdleAnimation();
            RawAnimation selectedIdle = idleAnim == 2 ? IDLE2_ANIM : IDLE_ANIM;
            state.getController().setAnimation(selectedIdle);
            state.getController().setAnimationSpeed(1.0);
         }

         return PlayState.CONTINUE;
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public void travel(Vec3d movementInput) {
      if (!this.isTouchingWater() && !this.isInLava()) {
         super.travel(movementInput);
      } else {
         float speedFactor = this.isOnGround() ? 0.6F : 0.35F;
         this.updateVelocity(this.getMovementSpeed() * speedFactor, movementInput);
         this.move(MovementType.SELF, this.getVelocity());
         Vec3d vel = this.getVelocity();
         if (this.isOnGround()) {
            this.setVelocity(vel.x * 0.91, vel.y * 0.98, vel.z * 0.91);
         } else {
            double gravity = this.getAttributeValue(daot.compat.attributes.DaotEntityAttributes.GRAVITY);
            this.setVelocity(vel.x * 0.8, (vel.y - gravity) * 0.8, vel.z * 0.8);
         }
      }
   }

   private void tickContactDamage() {
      if (!this.isDead()) {
         Iterator var1 = this.getWorld()
            .getEntitiesByClass(
               LivingEntity.class,
               this.getBoundingBox().expand(1.0),
               e -> e != this
                  && (
                     e instanceof AttackTitanEntity
                        || e instanceof ArmoredTitanEntity
                        || e instanceof ColossalTitanEntity
                        || e instanceof FemaleTitanEntity
                        || e instanceof BeastTitanEntity
                        || e instanceof WarhammerTitanEntity
                  )
            )
            .iterator();
         if (var1.hasNext()) {
            LivingEntity target = (LivingEntity)var1.next();
            target.damage(this.getDamageSources().mobAttack(this), 10.0F);
            this.contactDamageCooldown = 40;
         }
      }
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void tickCramming() {
      super.tickCramming();
      TitanSlideHelper.titanPushEntities(this);
   }

   @Override
   protected void pushAway(Entity entity) {
   }

   @Override
   public void pushAwayFrom(Entity entity) {
   }

   @Override
   public boolean isCollidable() {
      return this.isDead() ? false : !this.getCommandTags().contains("dannysaot_regroup_no_collision");
   }

   @Override
   public boolean canHit() {
      if (this.isDead()) {
         return false;
      } else {
         for (PlayerEntity player : this.getWorld().getPlayers()) {
            if (player.distanceTo(this) <= 6.0
               && (player.getMainHandStack().getItem() instanceof EmptySyringeItem || player.getOffHandStack().getItem() instanceof EmptySyringeItem)) {
               return true;
            }
         }

         return false;
      }
   }

   private static enum EatPhase {
      SWOOP,
      PULL_TO_HOLD,
      HOLD,
      PULL_TO_EAT,
      EAT;
   }

   static class TitanChaseGoal extends Goal {
      private final TitanEntity titan;
      private int repathTimer = 0;
      private Path path = null;

      public TitanChaseGoal(TitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.MOVE));
      }

      @Override
      public boolean canStart() {
         LivingEntity target = this.titan.getTarget();
         return target != null
            && target.isAlive()
            && !this.titan.isEating()
            && !this.titan.isEyeHurt()
            && !this.titan.isDead()
            && !this.titan.isCommandedStop()
            && !VillagerTransformTracker.isRegrouping(this.titan);
      }

      @Override
      public boolean shouldContinue() {
         LivingEntity target = this.titan.getTarget();
         return target != null
            && target.isAlive()
            && !this.titan.isEating()
            && !this.titan.isEyeHurt()
            && !this.titan.isDead()
            && !this.titan.isCommandedStop()
            && !VillagerTransformTracker.isRegrouping(this.titan);
      }

      @Override
      public void start() {
         this.repathTimer = 0;
         this.path = null;
      }

      @Override
      public void stop() {
         this.path = null;
      }

      @Override
      public void tick() {
         if (!this.titan.isKnockedBack()) {
            LivingEntity target = this.titan.getTarget();
            if (target != null && target.isAlive()) {
               if (--this.repathTimer <= 0) {
                  this.repathTimer = 30;
                  this.path = this.titan.getNavigation().findPathTo(target, 0);
               }

               double moveX = target.getX();
               double moveZ = target.getZ();
               if (this.path != null && !this.path.isFinished()) {
                  double width = this.titan.getWidth();
                  double advanceDistSqr = Math.max(width * width, 2.25);

                  while (!this.path.isFinished()) {
                     PathNode node = this.path.getCurrentNode();
                     double nodeDx = node.x + 0.5 - this.titan.getX();
                     double nodeDz = node.z + 0.5 - this.titan.getZ();
                     if (!(nodeDx * nodeDx + nodeDz * nodeDz < advanceDistSqr)) {
                        break;
                     }

                     this.path.next();
                  }

                  if (!this.path.isFinished()) {
                     int currentIdx = this.path.getCurrentNodeIndex();
                     int lookaheadIdx = Math.min(currentIdx + 3, this.path.getLength() - 1);
                     PathNode aheadNode = this.path.getNode(lookaheadIdx);
                     moveX = aheadNode.x + 0.5;
                     moveZ = aheadNode.z + 0.5;
                  }
               }

               Vec3d currentPos = this.titan.getPos();
               double dx = moveX - currentPos.x;
               double dz = moveZ - currentPos.z;
               double horizontalDistSqr = dx * dx + dz * dz;
               if (horizontalDistSqr > 1.0E-4) {
                  float desiredYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                  this.titan.targetYRot = desiredYaw;
                  double horizontalDist = Math.sqrt(horizontalDistSqr);
                  double speed = this.titan.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                  Vec3d movement = new Vec3d(dx / horizontalDist * speed, this.titan.getVelocity().y, dz / horizontalDist * speed);
                  this.titan.setVelocity(movement);
               }
            }
         }
      }

      @Override
      public boolean shouldRunEveryTick() {
         return true;
      }
   }

   static class TitanFindTargetGoal extends Goal {
      private final TitanEntity titan;
      private int searchCooldown = 0;
      private int retargetCooldown = 0;

      public TitanFindTargetGoal(TitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.TARGET));
      }

      private LivingEntity findClosestTarget() {
         double range = this.titan.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
         Box searchBox = this.titan.getBoundingBox().expand(range);
         PlayerEntity nearestPlayer = null;
         double nearestPlayerDist = Double.MAX_VALUE;
         MerchantEntity nearestVillager = null;
         double nearestVillagerDist = Double.MAX_VALUE;
         PillagerEntity nearestPillager = null;
         double nearestPillagerDist = Double.MAX_VALUE;
         String ownerName = VillagerTransformTracker.getOwnerName(this.titan);

         for (LivingEntity entity : this.titan.getWorld().getNonSpectatingEntities(LivingEntity.class, searchBox)) {
            double yDiff = entity.getY() - this.titan.getY();
            if (!(yDiff > 30.0) && !(yDiff < -30.0)) {
               if (entity instanceof PlayerEntity player) {
                  if (!player.isCreative() && !player.isSpectator() && !this.titan.isSelfInjectionPassenger(player) && !this.titan.hasPassenger(player)) {
                     Entity vehicle = player.getVehicle();
                     if (!(vehicle instanceof SmallTitanEntity)
                        && !(vehicle instanceof SmallTitan2Entity)
                        && !(vehicle instanceof FritzTitanEntity)
                        && !(vehicle instanceof TitanEntity)
                        && (ownerName == null || !player.getName().getString().equals(ownerName))) {
                        double dist = this.titan.distanceTo(player);
                        double effectiveRange = range;
                        if (HoodTracker.isHoodUp(player)) {
                           effectiveRange = range * 0.6666666666666666;
                        }

                        if (!(dist > effectiveRange) && dist < nearestPlayerDist) {
                           nearestPlayer = player;
                           nearestPlayerDist = dist;
                        }
                     }
                  }
               } else if (entity instanceof MerchantEntity villager) {
                  double distx = this.titan.distanceTo(villager);
                  if (distx < nearestVillagerDist) {
                     nearestVillager = villager;
                     nearestVillagerDist = distx;
                  }
               } else if (entity instanceof PillagerEntity pillager) {
                  double distx = this.titan.distanceTo(pillager);
                  if (distx < nearestPillagerDist) {
                     nearestPillager = pillager;
                     nearestPillagerDist = distx;
                  }
               }
            }
         }

         LivingEntity target = null;
         double closestDist = Double.MAX_VALUE;
         if (nearestPlayer != null && nearestPlayerDist < closestDist) {
            target = nearestPlayer;
            closestDist = nearestPlayerDist;
         }

         if (nearestVillager != null && nearestVillagerDist < closestDist) {
            target = nearestVillager;
            closestDist = nearestVillagerDist;
         }

         if (nearestPillager != null && nearestPillagerDist < closestDist) {
            target = nearestPillager;
         }

         return target != null && target.isAlive() ? target : null;
      }

      @Override
      public boolean canStart() {
         if (!this.titan.isCommandedStop() && !VillagerTransformTracker.isRegrouping(this.titan)) {
            String ownerName = VillagerTransformTracker.getOwnerName(this.titan);
            if (VillagerTransformTracker.hasActiveTarget(ownerName)) {
               return false;
            } else if (this.searchCooldown > 0) {
               this.searchCooldown--;
               return false;
            } else {
               this.searchCooldown = 20;
               LivingEntity target = this.findClosestTarget();
               if (target != null) {
                  this.titan.setTarget(target);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }

      @Override
      public boolean shouldContinue() {
         if (!this.titan.isCommandedStop() && !VillagerTransformTracker.isRegrouping(this.titan)) {
            String ownerName = VillagerTransformTracker.getOwnerName(this.titan);
            if (VillagerTransformTracker.hasActiveTarget(ownerName)) {
               return false;
            } else {
               LivingEntity target = this.titan.getTarget();
               if (target != null && target.isAlive()) {
                  if (target instanceof PlayerEntity player) {
                     if (player.isCreative() || player.isSpectator()) {
                        this.titan.setTarget(null);
                        return false;
                     }

                     Entity vehicle = player.getVehicle();
                     if (vehicle instanceof SmallTitanEntity
                        || vehicle instanceof SmallTitan2Entity
                        || vehicle instanceof FritzTitanEntity
                        || vehicle instanceof TitanEntity) {
                        this.titan.setTarget(null);
                        return false;
                     }
                  }

                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }

      @Override
      public void tick() {
         if (this.retargetCooldown > 0) {
            this.retargetCooldown--;
         } else {
            this.retargetCooldown = 20;
            LivingEntity closest = this.findClosestTarget();
            if (closest != null && closest != this.titan.getTarget()) {
               this.titan.setTarget(closest);
            }
         }
      }

      @Override
      public void stop() {
         this.titan.setTarget(null);
      }
   }

   static class TitanWanderGoal extends Goal {
      private final TitanEntity titan;
      private Vec3d wanderTarget = null;
      private int wanderCooldown = 0;
      private static final double WANDER_RANGE = 30.0;
      private static final int MIN_WANDER_COOLDOWN = 40;
      private static final int MAX_WANDER_COOLDOWN = 160;
      private static final double ARRIVAL_THRESHOLD = 2.0;
      private int wanderTicks = 0;
      private static final int MAX_WANDER_TICKS = 100;
      private Vec3d lastPosition = null;
      private int stuckTicks = 0;
      private static final int STUCK_THRESHOLD = 30;
      private Path path = null;

      public TitanWanderGoal(TitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.MOVE));
      }

      @Override
      public boolean canStart() {
         if (this.titan.getTarget() != null
            || this.titan.isEating()
            || this.titan.isEyeHurt()
            || this.titan.isDead()
            || this.titan.isCommandedStop()
            || VillagerTransformTracker.isRegrouping(this.titan)) {
            return false;
         } else if (this.wanderCooldown > 0) {
            this.wanderCooldown--;
            return false;
         } else {
            Vec3d currentPos = this.titan.getPos();
            double angle = this.titan.random.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + this.titan.random.nextDouble() * 30.0;
            this.wanderTarget = currentPos.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
            return true;
         }
      }

      @Override
      public void start() {
         this.wanderTicks = 0;
         this.stuckTicks = 0;
         this.lastPosition = this.titan.getPos();
         if (this.wanderTarget != null) {
            this.path = this.titan.getNavigation().findPathTo(BlockPos.ofFloored(this.wanderTarget), 0);
         }
      }

      @Override
      public boolean shouldContinue() {
         if (this.titan.getTarget() != null
            || this.titan.isEating()
            || this.titan.isEyeHurt()
            || this.titan.isDead()
            || this.titan.isCommandedStop()
            || VillagerTransformTracker.isRegrouping(this.titan)) {
            return false;
         } else if (this.wanderTicks < 100 && this.stuckTicks < 30) {
            if (this.wanderTarget != null) {
               double distSqr = this.titan.getPos().squaredDistanceTo(this.wanderTarget);
               if (distSqr < 4.0) {
                  return false;
               }
            }

            return this.wanderTarget != null;
         } else {
            return false;
         }
      }

      @Override
      public void stop() {
         this.wanderTarget = null;
         this.path = null;
         this.wanderCooldown = 40 + this.titan.random.nextInt(120);
      }

      @Override
      public void tick() {
         if (!this.titan.isKnockedBack()) {
            if (this.wanderTarget != null) {
               this.wanderTicks++;
               if (this.wanderTicks < 100) {
                  if (this.wanderTicks % 10 == 0 && this.lastPosition != null) {
                     if (this.titan.getPos().squaredDistanceTo(this.lastPosition) < 1.0) {
                        this.stuckTicks += 10;
                     } else {
                        this.stuckTicks = 0;
                     }

                     this.lastPosition = this.titan.getPos();
                     if (this.stuckTicks >= 30) {
                        return;
                     }
                  }

                  double moveX = this.wanderTarget.x;
                  double moveZ = this.wanderTarget.z;
                  if (this.path != null && !this.path.isFinished()) {
                     double width = this.titan.getWidth();
                     double advanceDistSqr = Math.max(width * width, 2.25);

                     while (!this.path.isFinished()) {
                        PathNode node = this.path.getCurrentNode();
                        double nodeDx = node.x + 0.5 - this.titan.getX();
                        double nodeDz = node.z + 0.5 - this.titan.getZ();
                        if (!(nodeDx * nodeDx + nodeDz * nodeDz < advanceDistSqr)) {
                           break;
                        }

                        this.path.next();
                     }

                     if (!this.path.isFinished()) {
                        int currentIdx = this.path.getCurrentNodeIndex();
                        int lookaheadIdx = Math.min(currentIdx + 3, this.path.getLength() - 1);
                        PathNode aheadNode = this.path.getNode(lookaheadIdx);
                        moveX = aheadNode.x + 0.5;
                        moveZ = aheadNode.z + 0.5;
                     }
                  }

                  Vec3d currentPos = this.titan.getPos();
                  double dx = moveX - currentPos.x;
                  double dz = moveZ - currentPos.z;
                  double horizontalDistSqr = dx * dx + dz * dz;
                  if (horizontalDistSqr > 1.0E-4) {
                     float desiredYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                     this.titan.targetYRot = desiredYaw;
                     double horizontalDist = Math.sqrt(horizontalDistSqr);
                     double speed = TitanEntity.getWanderSpeed() * this.titan.speedMultiplier;
                     Vec3d movement = new Vec3d(dx / horizontalDist * speed, this.titan.getVelocity().y, dz / horizontalDist * speed);
                     this.titan.setVelocity(movement);
                  }
               }
            }
         }
      }

      @Override
      public boolean shouldRunEveryTick() {
         return true;
      }
   }
}
