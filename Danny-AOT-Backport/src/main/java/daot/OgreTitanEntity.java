package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.ModNetworking;
import daot.network.ODMJamPayload;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
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
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class OgreTitanEntity extends HostileEntity implements GeoEntity, GrabbingTitan {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final Set<UUID> selfInjectionPassengers = new HashSet<>();
   private final Map<UUID, Integer> sneakDeathTimers = new HashMap<>();
   private final Map<UUID, Long> lastBlockedDismountTick = new HashMap<>();
   private static final int SNEAK_DEATH_TICKS = 60;
   private static final int SNEAK_GRACE_TICKS = 5;
   private boolean dismountAllowed = false;
   private static final TrackedData<Boolean> DATA_IS_AGGRESSIVE = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ATTACKING = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ATTACK_NUMBER = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_PROTECTING = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_EATING = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_DEAD = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_RUNNING = DataTracker.registerData(OgreTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation RUN_UPPER_ANIM = RawAnimation.begin().thenLoop("run_upper");
   private static final RawAnimation GROUND_SMASH1_ANIM = RawAnimation.begin().thenPlayAndHold("ground_smash1");
   private static final RawAnimation GROUND_SMASH2_ANIM = RawAnimation.begin().thenPlayAndHold("ground_smash2");
   private static final RawAnimation UPPERCUT_R_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_r");
   private static final RawAnimation UPPERCUT_L_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_l");
   private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation KICK_ATTACK_ANIM = RawAnimation.begin().thenPlayAndHold("kick_attack");
   private static final RawAnimation PROTECT_ANIM = RawAnimation.begin().thenPlayAndHold("protect");
   private static final RawAnimation WIRE_YANK_ANIM = RawAnimation.begin().thenPlayAndHold("wire_yank");
   private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenPlayAndHold("eat");
   private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlay("death");
   private static final RawAnimation NIGHT_SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
   public static final int ATTACK_GROUND_SMASH1 = 1;
   public static final int ATTACK_GROUND_SMASH2 = 2;
   public static final int ATTACK_UPPERCUT_R = 3;
   public static final int ATTACK_UPPERCUT_L = 4;
   public static final int ATTACK_PUNCH1 = 5;
   public static final int ATTACK_PUNCH2 = 6;
   public static final int ATTACK_KICK = 7;
   public static final int ATTACK_WIRE_YANK = 8;
   public static final int ATTACK_EAT = 9;
   private static final int GROUND_SMASH_TICKS = 40;
   private static final int GROUND_SMASH_EFFECT_TICK = 16;
   private static final int UPPERCUT_TICKS = 35;
   private static final int UPPERCUT_EFFECT_TICK = 12;
   private static final int PUNCH_TICKS = 30;
   private static final int PUNCH_EFFECT_TICK = 10;
   private static final int KICK_TICKS = 35;
   private static final int KICK_EFFECT_TICK = 18;
   private static final int WIRE_YANK_TICKS = 30;
   private static final int WIRE_YANK_JAM_TICK = 13;
   private static final int WIRE_YANK_JAM_DURATION_MS = 5000;
   private static final int EAT_TICKS = 60;
   private static final int EAT_DROP_TICK = 24;
   private static final int EAT_DAMAGE_TICK = 30;
   private static final float EAT_DAMAGE = 30.0F;
   private static final int PROTECT_TICKS = 100;
   private static final int PROTECT_COOLDOWN_TICKS = 400;
   private static final int NAPE_MAX_HITS = 5;
   private static final int NAPE_REGEN_DELAY = 200;
   private static final int NAPE_REGEN_RATE = 40;
   private static final float MAX_TURN_SPEED = 12.0F;
   private static final double ATTACK_RANGE = 14.0;
   private static final double CLOSE_ATTACK_RANGE = 7.0;
   private static final float ATTACK_DAMAGE = 14.0F;
   private static final float GROUND_SMASH_DAMAGE = 20.0F;
   private static final float KICK_DAMAGE = 18.0F;
   private int attackAnimationTicks = 0;
   private int attackCooldown = 0;
   private int attackEffectTimer = 0;
   private boolean attackEffectTriggered = false;
   private int protectTicks = 0;
   private int protectCooldown = 0;
   private int hitboxRespawnCooldown = 0;
   int napeHitsRemaining = 5;
   private int napeSinceLastHitTicks = 0;
   private int napeRegenTimer = 0;
   private static final int PARRY_WINDOW_TICKS = 200;
   private static final int EAT_COOLDOWN_TICKS = 140;
   private LivingEntity eatingTarget = null;
   private int eatTicks = 0;
   private boolean eatDropDone = false;
   private boolean eatDamageDone = false;
   private int eatCooldown = 0;
   private LivingEntity guardRetaliationTarget = null;
   private int guardRetaliationTicks = 0;
   private int wireYankCooldown = 0;
   private int parryWindowTicks = 0;
   private final Set<UUID> hookedPlayers = new HashSet<>();
   OgreTitanNapeEntity napeEntity;
   private float targetYRot;
   private int nightSleepStart;
   private int nightSleepEnd;
   private boolean isNightSleeping = false;
   private double animSpeedSqr = 0.0;
   private int animSpeedTick = -1;

   public void addSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.add(passenger.getUuid());
   }

   public void removeSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.remove(passenger.getUuid());
   }

   public boolean isSelfInjectionPassenger(Entity passenger) {
      return this.selfInjectionPassengers.contains(passenger.getUuid());
   }

   public boolean isDismountAllowed() {
      return this.dismountAllowed;
   }

   public void setDismountAllowed(boolean allowed) {
      this.dismountAllowed = allowed;
   }

   public void notifyDismountBlocked(Entity passenger) {
      this.lastBlockedDismountTick.put(passenger.getUuid(), this.getWorld().getTime());
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

   private void randomizeNightSleepTimes() {
      this.nightSleepStart = 13000 + this.random.nextInt(2001);
      this.nightSleepEnd = 22000 + this.random.nextInt(1001);
   }

   public boolean isNightSleeping() {
      return this.isNightSleeping;
   }

   public OgreTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
      this.experiencePoints = 50;
      this.randomizeNightSleepTimes();
   }

   public static Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 25.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 120.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 10.0)
         .add(daot.compat.attributes.DaotEntityAttributes.SCALE, 1.0);
   }

   public static boolean checkOgreTitanSpawnRules(
      EntityType<OgreTitanEntity> entityType, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random
   ) {
      if (!DannysAot.areOgreSpawnsAllowed(level.toServerWorld())) {
         return false;
      } else {
         int playerCount = level.toServerWorld().getPlayers().size();
         int maxTitans = TitanEntity.getEffectiveMaxTitans(playerCount);
         if (TitanEntity.getLoadedTitanCount() >= maxTitans) {
            return false;
         } else if (!BreachManager.isNaturalSpawnAllowed(entityType, level, pos.getX(), pos.getZ())) {
            return false;
         } else {
            return random.nextFloat() >= 0.01F ? false : TitanEntity.tryClaimSpawnTick(level);
         }
      }
   }

   @Override
   public boolean canSpawn(WorldAccess world, SpawnReason spawnReason) {
      return true;
   }

   @Override
   public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, net.minecraft.nbt.NbtCompound entityNbt) {
      if (!this.getWorld().isClient()) {
         int capPlayerCount = this.getWorld() instanceof ServerWorld sl ? sl.getPlayers().size() : 1;
         int capMaxTitans = TitanEntity.getEffectiveMaxTitans(capPlayerCount);
         if (TitanEntity.getLoadedTitanCount() >= capMaxTitans
            && spawnReason != SpawnReason.SPAWN_EGG
            && spawnReason != SpawnReason.COMMAND
            && spawnReason != SpawnReason.CONVERSION) {
            this.discard();
            return entityData;
         }
      }

      this.spawnHitboxes();
      return super.initialize(world, difficulty, spawnReason, entityData, null);
   }

   private void spawnHitboxes() {
      if (!this.getWorld().isClient()) {
         OgreTitanNapeEntity nape = new OgreTitanNapeEntity(DannysAot.OGRE_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
      }
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_IS_AGGRESSIVE, false);
      this.dataTracker.startTracking(DATA_IS_ATTACKING, false);
      this.dataTracker.startTracking(DATA_ATTACK_NUMBER, 0);
      this.dataTracker.startTracking(DATA_IS_PROTECTING, false);
      this.dataTracker.startTracking(DATA_IS_EATING, false);
      this.dataTracker.startTracking(DATA_IS_DEAD, false);
      this.dataTracker.startTracking(DATA_IS_RUNNING, false);
   }

   public boolean isRunning() {
      return this.dataTracker.get(DATA_IS_RUNNING);
   }

   public void setRunning(boolean v) {
      if (this.dataTracker.get(DATA_IS_RUNNING) != v) {
         this.dataTracker.set(DATA_IS_RUNNING, v);
      }
   }

   @Override
   public boolean isAttacking() {
      return this.dataTracker.get(DATA_IS_AGGRESSIVE);
   }

   @Override
   public void setAttacking(boolean attacking) {
      this.dataTracker.set(DATA_IS_AGGRESSIVE, attacking);
   }

   public boolean isTitanAttacking() {
      return this.dataTracker.get(DATA_IS_ATTACKING);
   }

   public void setTitanAttacking(boolean v) {
      this.dataTracker.set(DATA_IS_ATTACKING, v);
   }

   public int getAttackNumber() {
      return this.dataTracker.get(DATA_ATTACK_NUMBER);
   }

   public void setAttackNumber(int v) {
      this.dataTracker.set(DATA_ATTACK_NUMBER, v);
   }

   public boolean isProtecting() {
      return this.dataTracker.get(DATA_IS_PROTECTING);
   }

   public void setProtecting(boolean v) {
      this.dataTracker.set(DATA_IS_PROTECTING, v);
   }

   @Override
   public boolean isEating() {
      return this.dataTracker.get(DATA_IS_EATING);
   }

   @Override
   public int getEatingTargetId() {
      return this.eatingTarget != null ? this.eatingTarget.getId() : -1;
   }

   @Override
   public int getMaxHeadRotation() {
      return 60;
   }

   @Override
   public int getMaxLookYawChange() {
      return 12;
   }

   public void setEating(boolean v) {
      this.dataTracker.set(DATA_IS_EATING, v);
   }

   @Override
   public void setTarget(@Nullable LivingEntity target) {
      if (target instanceof PlayerEntity p && (p.isCreative() || p.isSpectator() || VanishManager.isVanished(p.getUuid()))) {
         super.setTarget(null);
      } else if (ModNetworking.isFounder(target)) {
         super.setTarget(null);
      } else {
         super.setTarget(target);
      }
   }

   @Override
   public boolean isDead() {
      return this.dataTracker.get(DATA_IS_DEAD);
   }

   public void setDead(boolean v) {
      this.dataTracker.set(DATA_IS_DEAD, v);
   }

   public OgreTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public void setPlayerHooked(UUID playerUUID, boolean hooked) {
      if (hooked) {
         this.hookedPlayers.add(playerUUID);
      } else {
         this.hookedPlayers.remove(playerUUID);
      }
   }

   public boolean isFullBodyAttacking() {
      if (!this.isTitanAttacking()) {
         return false;
      } else {
         int n = this.getAttackNumber();
         return n == 1 || n == 2 || n == 7 || n == 8;
      }
   }

   public boolean isInParryWindow() {
      return this.parryWindowTicks > 0 && !this.isProtecting() && !this.isDead();
   }

   public void performParry(LivingEntity attacker) {
      this.parryWindowTicks = 0;
      this.stopEating();
      this.setTitanAttacking(false);
      this.setAttackNumber(0);
      this.attackAnimationTicks = 0;
      double dx = attacker.getX() - this.getX();
      double dz = attacker.getZ() - this.getZ();
      if (dx * dx + dz * dz > 0.001) {
         this.targetYRot = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
         this.setYaw(this.targetYRot);
         this.bodyYaw = this.targetYRot;
         this.headYaw = this.targetYRot;
      }

      boolean rightSide = this.random.nextBoolean();
      this.setAttackNumber(rightSide ? 3 : 4);
      this.setTitanAttacking(true);
      this.attackAnimationTicks = 35;
      this.attackCooldown = 35;
      this.attackEffectTimer = 12;
      this.attackEffectTriggered = false;
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      this.applyAttackDamage(attacker, 28.0F, forwardX, forwardZ, 5.0, 2.0);
      this.performWireYank();
      this.getWorld().playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0F, 0.8F);
   }

   public void onNapeHit() {
      this.napeHitsRemaining--;
      this.napeSinceLastHitTicks = 0;
      this.napeRegenTimer = 0;
      if (this.napeHitsRemaining <= 0) {
         this.damage(this.getDamageSources().generic(), Float.MAX_VALUE);
      } else if (!this.isProtecting() && !this.isDead() && this.parryWindowTicks <= 0) {
         this.setProtecting(true);
         this.protectTicks = 100;
         this.stopEating();
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.attackAnimationTicks = 0;
         this.protectCooldown = 0;
      }
   }

   @Override
   protected void initGoals() {
      this.goalSelector.add(0, new SwimGoal(this));
      this.goalSelector.add(1, new OgreTitanEntity.OgreChaseGoal(this));
      this.goalSelector.add(2, new OgreTitanEntity.OgreWanderGoal(this));
      this.goalSelector.add(3, new TitanLookAtPlayerGoal(this, 40.0));
      this.goalSelector.add(4, new LookAroundGoal(this) {
         @Override
         public boolean canStart() {
            return this.notHeld() && super.canStart();
         }

         @Override
         public boolean shouldContinue() {
            return this.notHeld() && super.shouldContinue();
         }

         private boolean notHeld() {
            return !OgreTitanEntity.this.isCommandedStop() && !VillagerTransformTracker.isRegrouping(OgreTitanEntity.this);
         }
      });
      this.targetSelector.add(1, new OgreTitanEntity.OgreHurtByTargetGoal(this));
      this.targetSelector.add(2, new OgreTitanEntity.OgreFindTargetGoal(this));
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient()) {
         if (this.age > 20 && !this.isDead()) {
            if (this.hitboxRespawnCooldown > 0) {
               this.hitboxRespawnCooldown--;
            } else if (this.napeEntity == null || this.napeEntity.isRemoved()) {
               OgreTitanNapeEntity nape = new OgreTitanNapeEntity(DannysAot.OGRE_TITAN_NAPE, this.getWorld());
               nape.setParentTitan(this);
               nape.setPosition(this.getX(), this.getY(), this.getZ());
               this.getWorld().spawnEntity(nape);
               this.napeEntity = nape;
               this.hitboxRespawnCooldown = 100;
            }
         }

         boolean commandedStop = this.isCommandedStop();
         boolean regrouping = VillagerTransformTracker.isRegrouping(this);
         if (commandedStop) {
            this.setVelocity(0.0, this.getVelocity().y, 0.0);
         } else if (regrouping) {
            if (this.isEating()) {
               this.cancelEating();
            }

            this.tickRegroupMovement();
         }

         if (commandedStop || !regrouping) {
            this.setRunning(false);
         }

         this.setAttacking(this.getTarget() != null && this.getTarget().isAlive());
         this.napeSinceLastHitTicks++;
         if (this.napeHitsRemaining < 5 && this.napeSinceLastHitTicks >= 200) {
            this.napeRegenTimer++;
            if (this.napeRegenTimer >= 40) {
               this.napeRegenTimer = 0;
               this.napeHitsRemaining++;
            }
         }

         if (this.isProtecting()) {
            this.protectTicks--;
            if (this.protectTicks <= 0) {
               this.setProtecting(false);
               this.protectCooldown = 400;
               this.parryWindowTicks = 200;
            }
         }

         if (this.protectCooldown > 0) {
            this.protectCooldown--;
         }

         if (this.parryWindowTicks > 0) {
            this.parryWindowTicks--;
         }

         if (this.wireYankCooldown > 0) {
            this.wireYankCooldown--;
         }

         if (!this.hookedPlayers.isEmpty()
            && !this.isTitanAttacking()
            && !this.isProtecting()
            && !this.isDead()
            && this.wireYankCooldown <= 0
            && !commandedStop
            && !regrouping
            && this.random.nextFloat() < 0.08F) {
            this.setAttackNumber(8);
            this.setTitanAttacking(true);
            this.attackAnimationTicks = 30;
            this.attackCooldown = 30;
            this.attackEffectTimer = 0;
            this.attackEffectTriggered = false;
            this.wireYankCooldown = 100;
         }

         if (!this.isProtecting()
            && !this.isTitanAttacking()
            && !this.isEating()
            && !this.isDead()
            && this.protectCooldown <= 0
            && !commandedStop
            && !regrouping) {
            this.checkNapeProximity();
         }

         if (this.eatCooldown > 0) {
            this.eatCooldown--;
         }

         if (this.guardRetaliationTarget != null) {
            if (this.guardRetaliationTicks > 0) {
               this.guardRetaliationTicks--;
            }

            if (this.guardRetaliationTicks <= 0 || !this.guardRetaliationTarget.isAlive()) {
               if (this.getTarget() == this.guardRetaliationTarget) {
                  this.setTarget(null);
               }

               this.guardRetaliationTarget = null;
               this.guardRetaliationTicks = 0;
            }
         }

         if (!this.isNightSleeping
            && !this.isEating()
            && !this.isTitanAttacking()
            && !this.isProtecting()
            && !this.isDead()
            && this.eatCooldown <= 0
            && !commandedStop
            && !regrouping) {
            this.tryGrabTarget();
         }

         if (this.isEating() && !commandedStop) {
            this.tickEating();
         }

         if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
            if (this.attackAnimationTicks <= 0) {
               this.setTitanAttacking(false);
               this.setAttackNumber(0);
            }
         }

         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.isTitanAttacking() && !this.attackEffectTriggered) {
            this.attackEffectTimer++;
            int attackNum = this.getAttackNumber();

            int effectTick = switch (attackNum) {
               case 1, 2 -> 16;
               case 3, 4 -> 12;
               case 5, 6 -> 10;
               case 7 -> 18;
               case 8 -> 13;
               default -> 10;
            };
            if (this.attackEffectTimer >= effectTick) {
               switch (attackNum) {
                  case 1:
                  case 2:
                     this.performGroundSmash();
                     break;
                  case 3:
                  case 4:
                  case 5:
                  case 6:
                     this.dealPunchDamage();
                     break;
                  case 7:
                     this.performKick();
                     break;
                  case 8:
                     this.performWireYank();
               }

               this.attackEffectTriggered = true;
            }
         }

         if (this.isTitanAttacking() && this.getAttackNumber() == 8 && this.attackEffectTimer % 5 == 0) {
            this.performWireYank();
         }

         if (this.getVelocity().horizontalLengthSquared() > 0.005) {
            int stompInterval = this.isAttacking() ? 8 : 14;
            if (this.age % stompInterval == 0) {
               float pitch = 0.6F + this.random.nextFloat() * 0.2F;
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 10.0F, pitch);
               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  BlockPos below = this.getBlockPos().down();
                  BlockState groundState = this.getWorld().getBlockState(below);
                  if (!groundState.isAir()) {
                     serverLevel.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState), this.getX(), this.getY() + 0.1, this.getZ(), 15, 1.0, 0.1, 1.0, 0.05
                     );
                  }
               }
            }
         }
      }

      if (!this.getWorld().isClient()) {
         if (!this.isProtecting() && !this.isEating()) {
            float deltaRot = this.targetYRot - this.prevYaw;

            while (deltaRot > 180.0F) {
               deltaRot -= 360.0F;
            }

            while (deltaRot < -180.0F) {
               deltaRot += 360.0F;
            }

            float clampedDelta = Math.max(-12.0F, Math.min(12.0F, deltaRot));
            this.setYaw(this.prevYaw + clampedDelta);
            this.bodyYaw = this.getYaw();
            this.headYaw = MathHelper.clampAngle(this.headYaw, this.bodyYaw, this.getMaxHeadRotation());
         } else {
            this.setYaw(this.prevYaw);
            this.bodyYaw = this.prevYaw;
            this.headYaw = this.prevYaw;
         }
      }

      if (!this.getWorld().isClient()) {
         this.tickSelfInjectionPassengers();
      }

      super.tick();
   }

   private void tickSelfInjectionPassengers() {
      long currentTick = this.getWorld().getTime();

      for (Entity passenger : this.getPassengerList()) {
         if (passenger instanceof ServerPlayerEntity sp && this.isSelfInjectionPassenger(sp)) {
            if (!sp.isDead()) {
               sp.setInvisible(true);
            }

            UUID playerUUID = sp.getUuid();
            Long lastBlocked = this.lastBlockedDismountTick.get(playerUUID);
            boolean isSneaking = sp.isSneaking() || lastBlocked != null && currentTick - lastBlocked <= 5L;
            if (isSneaking) {
               int sneakTicks = this.sneakDeathTimers.getOrDefault(playerUUID, 0) + 1;
               this.sneakDeathTimers.put(playerUUID, sneakTicks);
               if (sneakTicks >= 60) {
                  sp.setInvisible(false);
                  this.setDismountAllowed(true);
                  sp.stopRiding();
                  this.setDismountAllowed(false);
                  RegistryKey<DamageType> becomeTitanKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "become_titan"));
                  RegistryEntry<DamageType> becomeTitanHolder = this.getWorld()
                     .getRegistryManager()
                     .getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE)
                     .getOrThrow(becomeTitanKey);
                  DamageSource becomeTitanDamage = new DamageSource(becomeTitanHolder);
                  if (sp.isCreative()) {
                     sp.kill();
                  } else {
                     sp.damage(becomeTitanDamage, Float.MAX_VALUE);
                  }
               } else {
                  double remainingSeconds = (60 - sneakTicks) / 20.0;
                  String countdown = String.format("%.1f", remainingSeconds);
                  sp.sendMessage(Text.literal("Accept your fate? (" + countdown + ")"), true);
               }
            } else if (this.sneakDeathTimers.containsKey(playerUUID)) {
               this.sneakDeathTimers.remove(playerUUID);
               this.lastBlockedDismountTick.remove(playerUUID);
               sp.sendMessage(Text.empty(), true);
            }
         }
      }
   }

   private void checkNapeProximity() {
      if (this.napeEntity != null) {
         List<PlayerEntity> nearbyPlayers = this.getWorld()
            .getEntitiesByClass(
               PlayerEntity.class,
               this.napeEntity.getBoundingBox().expand(5.0),
               p -> !this.isSelfInjectionPassenger(p) && !p.isSpectator() && !p.isCreative() && !VanishManager.isVanished(p.getUuid())
            );
         if (!nearbyPlayers.isEmpty() && this.random.nextFloat() < 0.02F) {
            this.setProtecting(true);
            this.protectTicks = 100;
            this.setTitanAttacking(false);
            this.setAttackNumber(0);
            this.attackAnimationTicks = 0;
         }
      }
   }

   public void startAttack(LivingEntity target) {
      if (this.attackCooldown <= 0 && !this.isTitanAttacking() && !this.isProtecting() && !this.isDead()) {
         double heightDiff = target.getY() - this.getY();
         double hDist = this.distanceTo(target);
         boolean closeEnoughForGround = hDist < 9.0;
         int attack;
         if (heightDiff < 2.0) {
            if (closeEnoughForGround) {
               float roll = this.random.nextFloat();
               if (roll < 0.3F) {
                  attack = 1;
               } else if (roll < 0.55F) {
                  attack = 2;
               } else if (roll < 0.75F) {
                  attack = 7;
               } else if (roll < 0.88F) {
                  attack = 5;
               } else {
                  attack = 6;
               }
            } else {
               attack = this.random.nextBoolean() ? 5 : 6;
            }
         } else if (heightDiff < 6.0) {
            float roll = this.random.nextFloat();
            if (roll < 0.25F) {
               attack = 3;
            } else if (roll < 0.5F) {
               attack = 4;
            } else if (roll < 0.7F) {
               attack = 5;
            } else if (roll < 0.85F) {
               attack = 6;
            } else {
               attack = closeEnoughForGround ? 7 : 3;
            }
         } else {
            float roll = this.random.nextFloat();
            if (roll < 0.35F) {
               attack = 3;
            } else if (roll < 0.7F) {
               attack = 4;
            } else if (roll < 0.85F) {
               attack = 5;
            } else {
               attack = 6;
            }
         }

         this.setAttackNumber(attack);
         this.setTitanAttacking(true);

         int ticks = switch (attack) {
            case 1, 2 -> 40;
            case 3, 4 -> 35;
            case 5, 6 -> 30;
            case 7 -> 35;
            default -> 30;
         };
         this.attackAnimationTicks = ticks;
         this.attackCooldown = ticks + 10;
         this.attackEffectTimer = 0;
         this.attackEffectTriggered = false;
      }
   }

   private void performGroundSmash() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         byte smashRadius = 4;
         byte smashDepth = 2;
         byte smashDistance = 6;
         double centerX = this.getX() + forwardX * smashDistance;
         double centerZ = this.getZ() + forwardZ * smashDistance;
         int groundY = (int)Math.floor(this.getY());
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            for (int dx = -smashRadius; dx <= smashRadius; dx++) {
               for (int dz = -smashRadius; dz <= smashRadius; dz++) {
                  double localForward = dx * forwardX + dz * forwardZ;
                  if (!(localForward < -1.0) && dx * dx + dz * dz <= smashRadius * smashRadius) {
                     for (int dy = 0; dy >= -smashDepth; dy--) {
                        BlockPos pos = new BlockPos((int)Math.floor(centerX + dx), groundY + dy, (int)Math.floor(centerZ + dz));
                        BlockState blockState = this.getWorld().getBlockState(pos);
                        if (!blockState.isAir() && !(blockState.getHardness(this.getWorld(), pos) < 0.0F) && !blockState.isIn(BlockTags.WITHER_IMMUNE)) {
                           if (this.random.nextFloat() < 0.3F) {
                              this.flingBlock(serverLevel, pos, blockState, forwardX, forwardZ);
                           } else {
                              this.getWorld().removeBlock(pos, false);
                           }
                        }
                     }
                  }
               }
            }
         }

         for (LivingEntity target : this.getWorld()
            .getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(smashRadius + smashDistance, 15.0, smashRadius + smashDistance))) {
            if (target != this && !this.isOwnHitbox(target)) {
               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double hDist = toTarget.horizontalLength();
               boolean directlyBelow = hDist < this.getWidth();
               Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
               double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
               if (directlyBelow || dot > 0.2 && hDist < smashRadius + smashDistance) {
                  this.applyAttackDamage(target, 20.0F, forwardX, forwardZ, 3.0, 1.5);
               }
            }
         }

         this.getWorld().playSound(null, centerX, (double)groundY, centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5F, 0.4F);
         serverLevel.spawnParticles(ParticleTypes.EXPLOSION, centerX, groundY + 1, centerZ, 3, 1.5, 0.5, 1.5, 0.0);
      }
   }

   private void dealPunchDamage() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      if (this.getWorld() instanceof ServerWorld && DannysAot.isTitanGriefingEnabled(this.getWorld())) {
         this.destroyBlocksAlongPunch(forwardX, forwardZ);
      }

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(16.0, 20.0, 16.0))) {
         if (target != this && !this.isOwnHitbox(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean directlyBelow = hDist < this.getWidth();
            Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
            if (directlyBelow || dot > 0.3 && hDist < 14.0) {
               this.applyAttackDamage(target, 14.0F, forwardX, forwardZ, 2.5, 1.0);
            }
         }
      }

      SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
      SoundEvent sound = impactSounds[this.random.nextInt(impactSounds.length)];
      this.getWorld().playSound(null, this.getX() + forwardX * 5.0, this.getY() + 5.0, this.getZ() + forwardZ * 5.0, sound, SoundCategory.HOSTILE, 8.0F, 0.8F);
   }

   private void performKick() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(16.0, 15.0, 16.0))) {
         if (target != this && !this.isOwnHitbox(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean directlyBelow = hDist < this.getWidth();
            Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
            if (directlyBelow || dot > 0.4 && hDist < 14.0) {
               this.applyAttackDamage(target, 18.0F, forwardX, forwardZ, 5.0, 3.0);
            }
         }
      }

      this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.5F, 0.5F);
   }

   private void performWireYank() {
      if (this.getWorld() instanceof ServerWorld) {
         for (UUID playerUUID : new ArrayList<>(this.hookedPlayers)) {
            ServerPlayerEntity player = this.getWorld().getServer().getPlayerManager().getPlayer(playerUUID);
            if (player != null) {
               ServerPlayNetworking.send(player, new ODMJamPayload(5000, true));
            }
         }

         this.getWorld().playSound(null, this.getX(), this.getY() + 5.0, this.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0F, 0.5F);
      }
   }

   private void tryGrabTarget() {
      LivingEntity commanded = this.getTarget();
      if (commanded != null && this.isCommandedTarget(commanded)) {
         if (this.isValidCommandedEatTarget(commanded)
            && commanded.isOnGround()
            && this.getBoundingBox().expand(4.0, 3.0, 4.0).intersects(commanded.getBoundingBox())
            && this.random.nextFloat() < 0.05F) {
            this.startEating(commanded);
         }
      } else {
         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(4.0, 3.0, 4.0))) {
            if (this.isValidEatTarget(target) && target.isOnGround() && this.random.nextFloat() < 0.05F) {
               this.startEating(target);
               return;
            }
         }

         if (this.napeEntity != null) {
            for (LivingEntity targetx : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.napeEntity.getBoundingBox().expand(4.0))) {
               if (this.isValidEatTarget(targetx) && this.random.nextFloat() < 0.01F) {
                  this.startEating(targetx);
                  return;
               }
            }
         }
      }
   }

   private boolean isValidEatTarget(LivingEntity target) {
      if (target == this || target == this.napeEntity) {
         return false;
      } else if (target.isAlive() && !target.isSpectator()) {
         if (target instanceof PlayerEntity p && (p.isCreative() || VanishManager.isVanished(p.getUuid()))) {
            return false;
         } else if (ModNetworking.isFounder(target)) {
            return false;
         } else if (target.getCommandTags().contains("dannys-aot:being_grabbed")) {
            return false;
         } else if (TitanGrabImmunity.isImmune(target)) {
            return false;
         } else {
            return target.getVehicle() != null ? false : target instanceof PlayerEntity || target instanceof MerchantEntity;
         }
      } else {
         return false;
      }
   }

   private boolean isValidCommandedEatTarget(LivingEntity target) {
      if (target == this || target == this.napeEntity) {
         return false;
      } else if (target.isAlive() && !target.isSpectator()) {
         if (target instanceof PlayerEntity p && (p.isCreative() || VanishManager.isVanished(p.getUuid()))) {
            return false;
         } else if (ModNetworking.isFounder(target)) {
            return false;
         } else if (target.getCommandTags().contains("dannys-aot:being_grabbed")) {
            return false;
         } else {
            return TitanGrabImmunity.isImmune(target) ? false : target.getVehicle() == null;
         }
      } else {
         return false;
      }
   }

   boolean isCommandedTarget(LivingEntity target) {
      if (target == null) {
         return false;
      } else {
         String ownerName = VillagerTransformTracker.getOwnerName(this);
         if (ownerName != null && VillagerTransformTracker.hasActiveTarget(ownerName)) {
            VillagerTransformTracker.ActiveTarget at = VillagerTransformTracker.getActiveTarget(ownerName);
            return at != null && target.getUuid().equals(at.targetUUID());
         } else {
            return false;
         }
      }
   }

   boolean hasFounderCommand() {
      if (!this.isCommandedStop() && !VillagerTransformTracker.isRegrouping(this)) {
         String ownerName = VillagerTransformTracker.getOwnerName(this);
         return ownerName != null && VillagerTransformTracker.hasActiveTarget(ownerName);
      } else {
         return false;
      }
   }

   private void startEating(LivingEntity target) {
      target.addCommandTag("dannys-aot:being_grabbed");
      this.eatingTarget = target;
      this.setEating(true);
      this.eatTicks = 0;
      this.eatDropDone = false;
      this.eatDamageDone = false;
      this.setTitanAttacking(false);
      this.setAttackNumber(0);
      this.attackAnimationTicks = 0;
      this.setVelocity(Vec3d.ZERO);
      double dx = target.getX() - this.getX();
      double dz = target.getZ() - this.getZ();
      if (dx * dx + dz * dz > 0.001) {
         this.targetYRot = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      }

      if (target.getVehicle() != null) {
         target.stopRiding();
      }

      target.startRiding(this, true);
   }

   private void tickEating() {
      this.eatTicks++;
      if (this.eatingTarget == null || !this.eatingTarget.isAlive() || this.eatingTarget.isRemoved()) {
         this.stopEating();
      } else if (!this.hasPassenger(this.eatingTarget) && !this.eatDropDone && this.eatTicks > 2) {
         this.stopEating();
      } else {
         if (!this.eatingTarget.hasVehicle() && !this.eatDropDone) {
            this.eatingTarget.startRiding(this, true);
         }

         if (this.eatTicks >= 24 && !this.eatDropDone) {
            this.eatDropDone = true;
            this.setDismountAllowed(true);
            this.eatingTarget.stopRiding();
            this.setDismountAllowed(false);
         }

         if (this.eatTicks >= 30 && !this.eatDamageDone) {
            this.eatDamageDone = true;
            this.eatingTarget.damage(this.getDamageSources().mobAttack(this), 30.0F);
            this.getWorld().playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), ModSounds.FLESH_IMPACT_6, SoundCategory.HOSTILE, 8.0F, 0.6F);
            this.getWorld().playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), ModSounds.FLESH_IMPACT_7, SoundCategory.HOSTILE, 6.0F, 0.5F);
            this.getWorld()
               .playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 3.0F, 0.4F);
         }

         if (this.eatTicks >= 60) {
            this.stopEating();
         }
      }
   }

   private void stopEating() {
      if (this.eatingTarget != null) {
         this.eatingTarget.removeScoreboardTag("dannys-aot:being_grabbed");
         if (this.hasPassenger(this.eatingTarget)) {
            this.setDismountAllowed(true);
            this.eatingTarget.stopRiding();
            this.setDismountAllowed(false);
         }

         this.eatingTarget = null;
      }

      this.setEating(false);
      this.eatTicks = 0;
      this.eatDropDone = false;
      this.eatDamageDone = false;
      this.eatCooldown = 140;
   }

   @Override
   public void cancelEating() {
      if (this.isEating()) {
         this.stopEating();
      }
   }

   public boolean isCommandedStop() {
      return this.getCommandTags().contains("dannysaot_commanded_stop");
   }

   public void setCommandFacing(float yaw) {
      this.targetYRot = yaw;
   }

   public void setGuardRetaliationTarget(LivingEntity attacker, int ticks) {
      this.guardRetaliationTarget = attacker;
      this.guardRetaliationTicks = ticks;
   }

   public boolean hasGuardRetaliation() {
      return this.guardRetaliationTarget != null && this.guardRetaliationTarget.isAlive() && this.guardRetaliationTicks > 0;
   }

   public LivingEntity getGuardRetaliationTarget() {
      return this.guardRetaliationTarget;
   }

   private void tickRegroupMovement() {
      VillagerTransformTracker.RegroupTarget regTarget = VillagerTransformTracker.getRegroupTarget(this);
      if (regTarget != null) {
         double dx = regTarget.x() - this.getX();
         double dz = regTarget.z() - this.getZ();
         double distSqr = dx * dx + dz * dz;
         if (distSqr < 9.0) {
            VillagerTransformTracker.clearRegroup(this);
            this.addCommandTag("dannysaot_commanded_stop");
            this.setVelocity(0.0, this.getVelocity().y, 0.0);
            this.targetYRot = regTarget.facingYaw();
            this.setRunning(false);
         } else {
            double dist = Math.sqrt(distSqr);
            this.targetYRot = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            double speed = this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 3.0;
            boolean sprinting = dist > 24.0;
            this.setRunning(sprinting);
            if (sprinting) {
               speed *= 2.0;
            }

            this.setVelocity(new Vec3d(dx / dist * speed, this.getVelocity().y, dz / dist * speed));
         }
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      if (this.isSelfInjectionPassenger(passenger)) {
         return this.getPos().add(0.0, 5.0, 0.0);
      } else {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double backX = Math.sin(yawRad) * 1.5;
         double backZ = -Math.cos(yawRad) * 1.5;
         return this.getPos().add(backX, this.getHeight() + 0.5, backZ);
      }
   }

   private void applyAttackDamage(LivingEntity target, float damage, double forwardX, double forwardZ, double knockbackH, double knockbackV) {
      if (!ModNetworking.isFounder(target)) {
         if (target instanceof TitanNapeEntity napeEntity) {
            TitanEntity parentTitan = napeEntity.getParentTitan();
            if (parentTitan != null) {
               parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
               this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
            }
         } else if (target instanceof SmallTitanNapeEntity smallNape) {
            SmallTitanEntity parentTitan = smallNape.getParentTitan();
            if (parentTitan != null) {
               parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
               this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
            }
         } else if (target instanceof SmallTitan2NapeEntity small2Nape) {
            SmallTitan2Entity parentTitan = small2Nape.getParentTitan();
            if (parentTitan != null) {
               parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
               this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
            }
         } else if (!(target instanceof AttackTitanNapeEntity) && !(target instanceof AttackTitanEyeEntity)) {
            if (!(target instanceof ArmoredTitanNapeEntity) && !(target instanceof ArmoredTitanEyeEntity)) {
               if (!(target instanceof ColossalTitanNapeEntity) && !(target instanceof ColossalTitanEyeEntity)) {
                  if (!(
                     target instanceof PlayerEntity p
                        && (
                           p.getVehicle() instanceof AttackTitanEntity
                              || p.getVehicle() instanceof ArmoredTitanEntity
                              || p.getVehicle() instanceof ColossalTitanEntity
                              || p.getVehicle() instanceof FemaleTitanEntity
                        )
                  )) {
                     float finalDamage = isShifterTitan(target) ? damage * 0.5F : damage;
                     target.damage(this.getDamageSources().mobAttack(this), finalDamage);
                     Vec3d toTarget = target.getPos().subtract(this.getPos());
                     Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                     if (target instanceof TitanEntity titanTarget) {
                        Vec3d knockback = horizontalDir.multiply(knockbackH * 8.0).add(0.0, knockbackV * 6.0, 0.0);
                        titanTarget.applyKnockback(knockback);
                     } else if (target instanceof PlayerEntity playerTarget) {
                        Vec3d knockback = horizontalDir.multiply(knockbackH).add(0.0, knockbackV, 0.0);
                        playerTarget.setVelocity(knockback);
                        playerTarget.velocityModified = true;
                        playerTarget.velocityDirty = true;
                     } else {
                        Vec3d knockback = horizontalDir.multiply(knockbackH).add(0.0, knockbackV, 0.0);
                        target.setVelocity(knockback);
                        target.velocityModified = true;
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isOwnHitbox(Entity entity) {
      return entity == this.napeEntity;
   }

   private static boolean isShifterTitan(Entity entity) {
      return entity instanceof AttackTitanEntity
         || entity instanceof ArmoredTitanEntity
         || entity instanceof ColossalTitanEntity
         || entity instanceof FemaleTitanEntity
         || entity instanceof WarhammerTitanEntity
         || entity instanceof BeastTitanEntity;
   }

   private void destroyBlocksAlongPunch(double forwardX, double forwardZ) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         double var20 = forwardZ;
         double rightZ = -forwardX;
         int armHeight = (int)Math.floor(this.getY() + 7.0);

         for (int dist = 3; dist <= 8; dist++) {
            double cx = this.getX() + forwardX * dist;
            double cz = this.getZ() + forwardZ * dist;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 2; dy++) {
                  BlockPos pos = new BlockPos((int)Math.floor(cx + var20 * dx), armHeight + dy, (int)Math.floor(cz + rightZ * dx));
                  BlockState blockState = this.getWorld().getBlockState(pos);
                  if (!blockState.isAir() && !(blockState.getHardness(this.getWorld(), pos) < 0.0F) && !blockState.isIn(BlockTags.WITHER_IMMUNE)) {
                     if (this.random.nextFloat() < 0.5F) {
                        this.flingBlock(serverLevel, pos, blockState, forwardX, forwardZ);
                     } else {
                        serverLevel.spawnParticles(
                           new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                           pos.getX() + 0.5,
                           pos.getY() + 0.5,
                           pos.getZ() + 0.5,
                           10,
                           0.5,
                           0.5,
                           0.5,
                           0.1
                        );
                        this.getWorld().removeBlock(pos, false);
                     }
                  }
               }
            }
         }
      }
   }

   private void flingBlock(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
      this.getWorld().removeBlock(pos, false);
      double velX = forwardX * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.5;
      double velY = 0.5 + this.random.nextDouble() * 0.8;
      double velZ = forwardZ * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.5;
      FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
      fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fallingBlock.setFallingBlockPos(pos);
      fallingBlock.setVelocity(velX, velY, velZ);
      fallingBlock.timeFalling = 1;
      fallingBlock.dropItem = false;
      serverLevel.spawnEntity(fallingBlock);
   }

   @Override
   public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
      return false;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      Entity attacker = source.getAttacker();
      return attacker instanceof PlayerEntity ? false : super.damage(source, amount);
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (!this.getWorld().isClient()) {
         this.selfInjectionPassengers.remove(passenger.getUuid());
         this.sneakDeathTimers.remove(passenger.getUuid());
         if (passenger instanceof PlayerEntity player) {
            player.setInvisible(false);
         }
      }
   }

   @Override
   protected void updatePostDeath() {
      if (!this.isDead()) {
         this.setDead(true);
         this.stopEating();
         this.setDismountAllowed(true);

         for (Entity passenger : this.getPassengerList()) {
            if (passenger instanceof ServerPlayerEntity sp && this.isSelfInjectionPassenger(sp)) {
               sp.setInvisible(false);
               sp.stopRiding();
               sp.kill();
            }
         }
      }

      this.deathTime++;
      if (this.deathTime >= 60 && !this.getWorld().isClient() && !this.isRemoved()) {
         if (this.napeEntity != null) {
            this.napeEntity.discard();
         }

         this.getWorld().sendEntityStatus(this, (byte)60);
         this.remove(RemovalReason.KILLED);
      }
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
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
      return !this.isDead();
   }

   @Override
   public boolean canHit() {
      return !this.isDead();
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement_controller", 6, this::movementPredicate));
      controllers.add(new AnimationController(this, "action_controller", 6, this::actionPredicate));
   }

   private PlayState movementPredicate(AnimationState<OgreTitanEntity> state) {
      if (this.isDead()) {
         return PlayState.STOP;
      } else if (this.isAiDisabled()) {
         state.getController().setAnimation(NIGHT_SLEEP_ANIM);
         return PlayState.CONTINUE;
      } else if (this.isEating()) {
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.isFullBodyAttacking()) {
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.isProtecting()) {
         return state.setAndContinue(IDLE_ANIM);
      } else if ((this.isAttacking() || this.isRunning()) && this.measuredSpeedSqr() > 0.001) {
         state.getController().setAnimationSpeed(1.2);
         return state.setAndContinue(RUN_ANIM);
      } else {
         return this.measuredSpeedSqr() > 5.0E-4 ? state.setAndContinue(WALK_ANIM) : state.setAndContinue(IDLE_ANIM);
      }
   }

   private PlayState actionPredicate(AnimationState<OgreTitanEntity> state) {
      if (this.isDead()) {
         state.getController().setAnimation(DEATH_ANIM);
         return PlayState.CONTINUE;
      } else if (this.isAiDisabled()) {
         state.getController().setAnimation(NIGHT_SLEEP_ANIM);
         return PlayState.CONTINUE;
      } else if (this.isEating()) {
         return state.setAndContinue(EAT_ANIM);
      } else if (this.isProtecting()) {
         return state.setAndContinue(PROTECT_ANIM);
      } else if (this.isTitanAttacking()) {
         RawAnimation attackAnim = switch (this.getAttackNumber()) {
            case 1 -> GROUND_SMASH1_ANIM;
            case 2 -> GROUND_SMASH2_ANIM;
            case 3 -> UPPERCUT_R_ANIM;
            case 4 -> UPPERCUT_L_ANIM;
            case 5 -> ATTACK1_ANIM;
            case 6 -> ATTACK2_ANIM;
            case 7 -> KICK_ATTACK_ANIM;
            case 8 -> WIRE_YANK_ANIM;
            default -> IDLE_UPPER_ANIM;
         };
         return state.setAndContinue(attackAnim);
      } else if ((this.isAttacking() || this.isRunning()) && this.measuredSpeedSqr() > 0.001) {
         return state.setAndContinue(RUN_UPPER_ANIM);
      } else {
         return this.measuredSpeedSqr() > 5.0E-4 ? state.setAndContinue(WALK_UPPER_ANIM) : state.setAndContinue(IDLE_UPPER_ANIM);
      }
   }

   private double measuredSpeedSqr() {
      if (this.age != this.animSpeedTick) {
         this.animSpeedTick = this.age;
         double dx = this.getX() - this.lastRenderX;
         double dz = this.getZ() - this.lastRenderZ;
         double inst = dx * dx + dz * dz;
         if (inst < this.animSpeedSqr) {
            this.animSpeedSqr = this.animSpeedSqr * 0.35 + inst * 0.65;
         } else {
            this.animSpeedSqr = this.animSpeedSqr * 0.7 + inst * 0.3;
         }
      }

      return this.animSpeedSqr;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putBoolean("OgreDead", this.isDead());
      nbt.putInt("NapeHits", this.napeHitsRemaining);
      nbt.putInt("NightSleepStart", this.nightSleepStart);
      nbt.putInt("NightSleepEnd", this.nightSleepEnd);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.getBoolean("OgreDead")) {
         this.setDead(true);
      }

      if (nbt.contains("NapeHits")) {
         this.napeHitsRemaining = nbt.getInt("NapeHits");
      }

      if (nbt.contains("NightSleepStart")) {
         this.nightSleepStart = nbt.getInt("NightSleepStart");
      }

      if (nbt.contains("NightSleepEnd")) {
         this.nightSleepEnd = nbt.getInt("NightSleepEnd");
      }
   }

   static class OgreChaseGoal extends Goal {
      private final OgreTitanEntity titan;
      private int repathTimer = 0;
      private Path path = null;

      public OgreChaseGoal(OgreTitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.MOVE));
      }

      @Override
      public boolean canStart() {
         LivingEntity target = this.titan.getTarget();
         return target != null
            && target.isAlive()
            && !this.titan.isFullBodyAttacking()
            && !this.titan.isProtecting()
            && !this.titan.isEating()
            && !this.titan.isDead()
            && !this.titan.isCommandedStop()
            && !VillagerTransformTracker.isRegrouping(this.titan);
      }

      @Override
      public boolean shouldContinue() {
         return this.canStart();
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
         LivingEntity target = this.titan.getTarget();
         if (target != null && target.isAlive()) {
            if ((target instanceof PlayerEntity || this.titan.hasGuardRetaliation() || this.titan.hasFounderCommand())
               && this.titan.distanceTo(target) < 16.0
               && this.titan.attackCooldown <= 0) {
               this.titan.startAttack(target);
            } else {
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
                  double speed = this.titan.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 5.0;
                  double dirX = dx / horizontalDist;
                  double dirZ = dz / horizontalDist;
                  double sepX = 0.0;
                  double sepZ = 0.0;
                  double sepRadius = 6.0;

                  for (OgreTitanEntity other : this.titan
                     .getWorld()
                     .getNonSpectatingEntities(OgreTitanEntity.class, this.titan.getBoundingBox().expand(6.0, 4.0, 6.0))) {
                     if (other != this.titan) {
                        double ox = this.titan.getX() - other.getX();
                        double oz = this.titan.getZ() - other.getZ();
                        double dsq = ox * ox + oz * oz;
                        if (dsq > 1.0E-4 && dsq < 36.0) {
                           double d = Math.sqrt(dsq);
                           double strength = (6.0 - d) / 6.0;
                           sepX += ox / d * strength;
                           sepZ += oz / d * strength;
                        }
                     }
                  }

                  dirX += sepX * 1.5;
                  dirZ += sepZ * 1.5;
                  double blendLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
                  if (blendLen > 1.0E-4) {
                     dirX /= blendLen;
                     dirZ /= blendLen;
                  }

                  Vec3d movement = new Vec3d(dirX * speed, this.titan.getVelocity().y, dirZ * speed);
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

   static class OgreFindTargetGoal extends Goal {
      private final OgreTitanEntity titan;
      private int recheckTimer = 0;

      public OgreFindTargetGoal(OgreTitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.TARGET));
      }

      @Override
      public boolean canStart() {
         return !this.titan.isDead();
      }

      @Override
      public boolean shouldContinue() {
         return !this.titan.isDead();
      }

      @Override
      public void tick() {
         if (!this.titan.isCommandedStop() && !VillagerTransformTracker.isRegrouping(this.titan)) {
            String ownerName = VillagerTransformTracker.getOwnerName(this.titan);
            if (ownerName != null && VillagerTransformTracker.hasActiveTarget(ownerName)) {
               VillagerTransformTracker.ActiveTarget at = VillagerTransformTracker.getActiveTarget(ownerName);
               if (((ServerWorld)this.titan.getWorld()).getEntity(at.targetUUID()) instanceof LivingEntity living && living.isAlive() && living != this.titan) {
                  this.titan.setTarget(living);
               } else {
                  this.titan.setTarget(null);
               }
            } else if (this.titan.hasGuardRetaliation()) {
               this.titan.setTarget(this.titan.getGuardRetaliationTarget());
            } else if (--this.recheckTimer <= 0) {
               this.recheckTimer = 20;
               double range = this.titan.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
               Box searchBox = this.titan.getBoundingBox().expand(range);
               List<PlayerEntity> players = this.titan
                  .getWorld()
                  .getEntitiesByClass(
                     PlayerEntity.class, searchBox, p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && !VanishManager.isVanished(p.getUuid())
                  );
               List<MerchantEntity> villagers = this.titan.getWorld().getEntitiesByClass(MerchantEntity.class, searchBox, vx -> vx.isAlive());
               LivingEntity closest = null;
               double closestDist = Double.MAX_VALUE;

               for (PlayerEntity p : players) {
                  double d = this.titan.squaredDistanceTo(p);
                  if (d < closestDist) {
                     closestDist = d;
                     closest = p;
                  }
               }

               if (closest == null) {
                  for (MerchantEntity v : villagers) {
                     double d = this.titan.squaredDistanceTo(v);
                     if (d < closestDist) {
                        closestDist = d;
                        closest = v;
                     }
                  }
               }

               if (closest != null) {
                  this.titan.setTarget(closest);
               } else {
                  this.titan.setTarget(null);
               }
            }
         } else {
            this.titan.setTarget(null);
         }
      }
   }

   static class OgreHurtByTargetGoal extends RevengeGoal {
      private final OgreTitanEntity titan;

      public OgreHurtByTargetGoal(OgreTitanEntity titan) {
         super(titan);
         this.titan = titan;
      }

      @Override
      public boolean canStart() {
         return !this.titan.hasFounderCommand() && super.canStart();
      }

      @Override
      public boolean shouldContinue() {
         return !this.titan.hasFounderCommand() && super.shouldContinue();
      }
   }

   static class OgreWanderGoal extends Goal {
      private final OgreTitanEntity titan;
      private double targetX;
      private double targetZ;
      private int wanderTimer = 0;

      public OgreWanderGoal(OgreTitanEntity titan) {
         this.titan = titan;
         this.setControls(EnumSet.of(Control.MOVE));
      }

      @Override
      public boolean canStart() {
         return this.titan.getTarget() == null
            && !this.titan.isTitanAttacking()
            && !this.titan.isProtecting()
            && !this.titan.isEating()
            && !this.titan.isDead()
            && !this.titan.isCommandedStop()
            && !VillagerTransformTracker.isRegrouping(this.titan);
      }

      @Override
      public boolean shouldContinue() {
         return this.canStart() && this.wanderTimer > 0;
      }

      @Override
      public void start() {
         this.wanderTimer = 200 + this.titan.random.nextInt(200);
         Vec3d pos = this.titan.getPos();
         double angle = this.titan.random.nextDouble() * Math.PI * 2.0;
         double dist = 10.0 + this.titan.random.nextDouble() * 15.0;
         this.targetX = pos.x + Math.cos(angle) * dist;
         this.targetZ = pos.z + Math.sin(angle) * dist;
      }

      @Override
      public void tick() {
         this.wanderTimer--;
         Vec3d currentPos = this.titan.getPos();
         double dx = this.targetX - currentPos.x;
         double dz = this.targetZ - currentPos.z;
         double distSqr = dx * dx + dz * dz;
         if (distSqr < 4.0) {
            this.wanderTimer = 0;
         } else {
            float desiredYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.titan.targetYRot = desiredYaw;
            double dist = Math.sqrt(distSqr);
            double speed = this.titan.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            this.titan.setVelocity(new Vec3d(dx / dist * speed, this.titan.getVelocity().y, dz / dist * speed));
         }
      }

      @Override
      public boolean shouldRunEveryTick() {
         return true;
      }
   }

   @Override protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (!this.hasPassenger(passenger)) return;
      Vec3d bpRidingPos = this.getPassengerRidingPos(passenger);
      positionUpdater.accept(passenger, bpRidingPos.x, bpRidingPos.y, bpRidingPos.z);
   }
}
