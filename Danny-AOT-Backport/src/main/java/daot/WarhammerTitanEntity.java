package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.ModNetworking;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.boss.BossBar.Style;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.World.ExplosionSourceType;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class WarhammerTitanEntity extends HostileEntity implements GeoEntity, ShifterTitan, WireRestrainable, daot.compat.BaseDimensionsProvider {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Boolean> DATA_IS_MOVING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      WarhammerTitanEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_TRANSFORMATION_TICKS = DataTracker.registerData(
      WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER
   );
   private static final TrackedData<Integer> DATA_WIRE_RESTRAINT = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_DISMOUNTING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_STOMP_TICK = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_SPRINTING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ATTACKING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ATTACK_NUMBER = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_ARMED = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_WAS_MOVING_ON_ATTACK = DataTracker.registerData(
      WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN
   );
   private static final TrackedData<Integer> DATA_LAST_ATTACK_IMPACT_TICK = DataTracker.registerData(
      WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER
   );
   private static final TrackedData<Boolean> DATA_IS_CROUCHING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_HIT_REACTION = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_LOW_STAMINA = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_HIT_TICK = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_SLIDING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_HIT_DIR_YAW = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_HAS_HAMMER = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_FALLING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_LANDING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_LANDING_TICK = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> DATA_LANDING_INTENSITY = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_IS_JUMPING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_JUMP_LAUNCHED = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_JUMP_VEL_X = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Y = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Z = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Integer> DATA_CRYSTAL_SHELL_ID = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_CRYSTAL_PEEKING = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_INCAPACITATED = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_KNOCKED = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_KNOCKED_YAW = DataTracker.registerData(WarhammerTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private CrystalShellWarhammerEntity crystalShell = null;
   private Vec3d crystalShellPos = null;
   private UUID crystalShellUUID = null;
   private Vec3d savedDismountPos = null;
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation SHIFT_ANIM = RawAnimation.begin().thenPlayAndHold("shift");
   private static final RawAnimation PUNCH_LEFT_ANIM = RawAnimation.begin().thenPlayAndHold("punch_left");
   private static final RawAnimation PUNCH_RIGHT_ANIM = RawAnimation.begin().thenPlayAndHold("punch_right");
   private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation GROUND_SLAM_ANIM = RawAnimation.begin().thenPlayAndHold("ground_slam");
   private static final RawAnimation SPIN_ANIM = RawAnimation.begin().thenPlayAndHold("spin");
   private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenPlayAndHold("charge");
   private static final RawAnimation CHARGE2_ANIM = RawAnimation.begin().thenPlayAndHold("charge2");
   private static final RawAnimation CROUCH_ANIM = RawAnimation.begin().thenLoop("crouch");
   private static final RawAnimation CROUCH_UPPER_ANIM = RawAnimation.begin().thenLoop("crouch_upper");
   private static final RawAnimation CROUCH_HAMMER_ANIM = RawAnimation.begin().thenLoop("crouch_hammer");
   private static final RawAnimation CROUCH_HAMMER_UPPER_ANIM = RawAnimation.begin().thenLoop("crouch_hammer_upper");
   private static final RawAnimation DISMOUNT_ANIM = RawAnimation.begin().thenPlayAndHold("dismount");
   private static final RawAnimation IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation RUN_UPPER_ANIM = RawAnimation.begin().thenLoop("run_upper");
   private static final RawAnimation SPIN_UPPER_ANIM = RawAnimation.begin().thenLoop("spin_upper");
   private static final RawAnimation IDLE_NOHAMMER_ANIM = RawAnimation.begin().thenLoop("idlenohammer");
   private static final RawAnimation IDLE_NOHAMMER_UPPER_ANIM = RawAnimation.begin().thenLoop("idlenohammer_upper");
   private static final RawAnimation WALK_NOHAMMER_ANIM = RawAnimation.begin().thenLoop("walknohammer");
   private static final RawAnimation WALK_NOHAMMER_UPPER_ANIM = RawAnimation.begin().thenLoop("walknohammer_upper");
   private static final RawAnimation RUN_HAMMER_ANIM = RawAnimation.begin().thenLoop("runhammer");
   private static final RawAnimation RUN_HAMMER_UPPER_ANIM = RawAnimation.begin().thenLoop("runhammer_upper");
   private static final RawAnimation CHARGE_KRON_ANIM = RawAnimation.begin().thenPlayAndHold("chargekron");
   private static final RawAnimation CHARGE_KRON2_ANIM = RawAnimation.begin().thenLoop("chargekron2");
   private static final RawAnimation CHARGE_KRON3_ANIM = RawAnimation.begin().thenPlayAndHold("chargekron3");
   private static final RawAnimation SUMMON_HAMMER_ANIM = RawAnimation.begin().thenPlayAndHold("summonhammer");
   private static final RawAnimation UNSUMMON_HAMMER_ANIM = RawAnimation.begin().thenPlayAndHold("unsummonhammer");
   private static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlayAndHold("stomp");
   private static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("jump");
   private static final RawAnimation FALLING_ANIM = RawAnimation.begin().thenLoop("falling");
   private static final RawAnimation LAND_ANIM = RawAnimation.begin().thenPlayAndHold("land");
   private static final RawAnimation ARMED_IDLE_ANIM = RawAnimation.begin().thenLoop("armedidle");
   private static final RawAnimation CROUCH_ARMED_ANIM = RawAnimation.begin().thenLoop("croucharmed");
   private static final RawAnimation JAB_L_ANIM = RawAnimation.begin().thenPlayAndHold("jab_l");
   private static final RawAnimation JAB_R_ANIM = RawAnimation.begin().thenPlayAndHold("jab_r");
   private static final RawAnimation KNOCKED_ANIM = RawAnimation.begin().thenPlayAndHold("knocked");
   private static final double MOVEMENT_SPEED = 0.25;
   private static final double CONTROLLED_WALK_SPEED = 0.3;
   private static final double CONTROLLED_RUN_SPEED = 1.02;
   private static final double CONTROLLED_CROUCH_SPEED = 0.15;
   private double currentSpeed = 0.3;
   private static final double SPEED_LERP_RATE = 0.16666666666666666;
   private long walkStartTick = 0L;
   private boolean wasMovingLastTick = false;
   private static final double STOMP_THRESHOLD = 0.15;
   private static final double[] STOMP_KEYFRAMES = new double[]{0.0, 1.0};
   private static final int STOMP_START_DELAY = 10;
   private int lastStompKeyframeIndex = -1;
   private int stompCooldown = 0;
   private double smoothYOffset = 0.0;
   private double prevSmoothYOffset = 0.0;
   private static final double SMOOTH_Y_LERP = 0.25;
   private static final double STEP_SMOOTH_THRESHOLD = 0.1;
   private boolean allowDismount = false;
   private int dismountToggleCooldown = 0;
   private static final int DISMOUNT_TOGGLE_COOLDOWN_TICKS = 20;
   private int dismountVisibilityDelay = 0;
   private long despawnAtGameTime = -1L;
   private static final int DISMOUNT_DESPAWN_TICKS = 1200;
   private static final int DISMOUNT_VISIBILITY_DELAY_TICKS = 3;
   private static final double MAX_CRYSTAL_TETHER_DISTANCE = 150.0;
   private static final double MAX_CRYSTAL_TETHER_DISTANCE_SQ = 22500.0;
   private int attackCooldown = 0;
   private int attackAnimationTicks = 0;
   private int lastAttackNumber = 0;
   private static final int PUNCH_LEFT_TICKS = 16;
   private static final int PUNCH_RIGHT_TICKS = 16;
   private static final int ATTACK1_TICKS = 20;
   private static final int GROUND_SLAM_TICKS = 35;
   private static final int SPIN_TICKS = 40;
   private static final int CHARGE2_TICKS = 56;
   private static final int SPIKE_FIELD_TICKS = 35;
   private static final int SPIKE_FIELD_EFFECT_TICK = 14;
   private static final int STOMP_TICKS = 25;
   private static final int STOMP_EFFECT_TICK = 7;
   private static final float STOMP_DRAIN = 15.0F;
   private static final double PIERCING_THORNS_RADIUS = 25.0;
   private static final int PIERCING_THORNS_COUNT = 10;
   private static final double SPIKE_FIELD_RADIUS = 30.0;
   private static final int SPIKE_FIELD_COUNT = 90;
   private static final int JAB_TICKS = 20;
   private int attackCycleIndex = 0;
   private boolean nextJabIsRight = false;
   private boolean nextHammerAttackIs2 = false;
   private boolean wasPlayingSpin = false;
   private static final int ATTACK1_EFFECT_TICK = 12;
   private static final int GROUND_SLAM_EFFECT_TICK = 14;
   private static final int CHARGE2_EFFECT_TICK = 26;
   private int attackEffectTimer = 0;
   private boolean attackEffectTriggered = false;
   private boolean spinActive = false;
   private int spinDamageTimer = 0;
   private static final int SPIN_DAMAGE_INTERVAL = 5;
   private static final int SPIN_FIRST_DAMAGE_TICK = 8;
   private static final float ATTACK_DRAIN = 5.0F;
   private static final float ABILITY_DRAIN = 15.0F;
   private static final float PIERCING_THORNS_DRAIN = 350.0F;
   private static final float SPIKE_FIELD_DRAIN = 300.0F;
   private static final int IMPALE_TICKS = 45;
   private static final int IMPALE_EFFECT_TICK = 14;
   private static final float IMPALE_DRAIN = 500.0F;
   private static final int PIERCING_THORNS_COOLDOWN = 240;
   private static final int SPIKE_FIELD_COOLDOWN = 240;
   private static final int IMPALE_COOLDOWN = 600;
   private int piercingThornsCooldownTicks = 0;
   private int spikeFieldCooldownTicks = 0;
   private int impaleCooldownTicks = 0;
   private static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.changing(3.0F, 13.0F);
   private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.changing(3.0F, 7.0F);
   private boolean wasCrouchingLastTick = false;
   private boolean wasDismountingLastTick = false;
   private boolean wasSprintingBeforeAttack = false;
   private int crouchTransitionTicksRemaining = 0;
   private boolean wasSprintingBeforeCrouch = false;
   private int crouchHysteresisCounter = 0;
   private static final int CROUCH_HYSTERESIS_TICKS = 2;
   private WarhammerTitanNapeEntity napeEntity = null;
   private WarhammerTitanEyeEntity eyeEntity = null;
   private double hammerBoneX;
   private double hammerBoneY;
   private double hammerBoneZ;
   private double prevHammerBoneX;
   private double prevHammerBoneY;
   private double prevHammerBoneZ;
   private boolean hammerBoneSynced = false;
   private static final int HAMMER_BONE_SYNC_TIMEOUT = 10;
   private long lastHammerBoneSyncTick = -1L;
   private static final int BLINDNESS_DURATION_TICKS = 120;
   private int blindnessTicks = 0;
   private int hitSlowTicks = 0;
   private int hitReactionTicks = 0;
   private Vec3d slideVelocity = null;
   private int slideTicks = 0;
   private static final int SLIDE_DURATION = 8;
   private float lockedYaw = Float.NaN;
   private static final int KNOCKED_DURATION_TICKS = 30;
   private static final double KNOCKED_SLIDE_SPEED = 1.575;
   private static final int KNOCKED_GETUP_COOLDOWN_TICKS = 60;
   private int knockedElapsedTicks = 0;
   private boolean wasKnocked = false;
   private int lastFleshImpactIndex = -1;
   private static final float INCAP_HEALTH_FLOOR = 1.0F;
   private static final int INCAPACITATE_DURATION_TICKS = 200;
   private int incapacitateTicks = 0;
   private boolean defeated = false;
   private boolean configHealthApplied = false;
   private ServerBossBar bossBar = null;
   private static final int JUMP_ANIM_TICKS = 10;
   private static final int JUMP_LAUNCH_TICK = 4;
   private static final float JUMP_VERTICAL_POWER = 4.5F;
   private static final float JUMP_HORIZONTAL_POWER = 3.6F;
   private static final int JUMP_TO_FALL_DELAY = 12;
   private static final int JUMP_LANDING_COOLDOWN = 10;
   private int jumpAnimTicks = 0;
   private boolean jumpLaunched = false;
   private double jumpDirX = 0.0;
   private double jumpDirZ = 0.0;
   private int jumpCooldownTicks = 0;
   private double jumpVelX = 0.0;
   private double jumpVelY = 0.0;
   private double jumpVelZ = 0.0;
   private boolean jumpVelocityApplied = false;
   private boolean jumpWasAirborne = false;
   private int jumpToFallTicks = 0;
   private boolean clientJumpActive = false;
   private boolean clientFallActive = false;
   private boolean clientInJumpArc = false;
   private int jumpAnimVisibleUntilTick = 0;
   private int landingAnimTicks = 0;
   private static final int LAND_ANIM_TICKS = 14;
   private static final float LANDING_STOMP_MIN_FALL = 20.0F;
   private static final float RIDE_POSITION_LERP = 0.15F;
   private double currentRideForwardOffset = 1.0;
   private double currentRideExtraY = 0.0;

   public WarhammerTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public WarhammerTitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public void setHammerBonePosition(double x, double y, double z, long tick) {
      this.prevHammerBoneX = this.hammerBoneX;
      this.prevHammerBoneY = this.hammerBoneY;
      this.prevHammerBoneZ = this.hammerBoneZ;
      this.hammerBoneX = x;
      this.hammerBoneY = y;
      this.hammerBoneZ = z;
      this.hammerBoneSynced = true;
      this.lastHammerBoneSyncTick = tick;
   }

   public boolean hasHammerBoneSync() {
      if (!this.hammerBoneSynced) {
         return false;
      } else {
         long currentServerTick = this.getWorld().getServer() != null ? this.getWorld().getServer().getTicks() : this.age;
         return currentServerTick - this.lastHammerBoneSyncTick < 10L;
      }
   }

   public WarhammerTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public void spawnHitboxes() {
      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         WarhammerTitanNapeEntity nape = new WarhammerTitanNapeEntity(DannysAot.WARHAMMER_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         WarhammerTitanEyeEntity eye = new WarhammerTitanEyeEntity(DannysAot.WARHAMMER_TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
         DannysAot.LOGGER.info("Spawned Warhammer Titan hitboxes");
      }
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_IS_MOVING, false);
      this.dataTracker.startTracking(DATA_SHIFTER_UUID, Optional.empty());
      this.dataTracker.startTracking(DATA_TRANSFORMATION_TICKS, 0);
      this.dataTracker.startTracking(DATA_WIRE_RESTRAINT, 0);
      this.dataTracker.startTracking(DATA_IS_DISMOUNTING, false);
      this.dataTracker.startTracking(DATA_LAST_STOMP_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_SPRINTING, false);
      this.dataTracker.startTracking(DATA_IS_ATTACKING, false);
      this.dataTracker.startTracking(DATA_ATTACK_NUMBER, 1);
      this.dataTracker.startTracking(DATA_IS_ARMED, false);
      this.dataTracker.startTracking(DATA_WAS_MOVING_ON_ATTACK, false);
      this.dataTracker.startTracking(DATA_LAST_ATTACK_IMPACT_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_CROUCHING, false);
      this.dataTracker.startTracking(DATA_IS_INCAPACITATED, false);
      this.dataTracker.startTracking(DATA_HIT_REACTION, 0);
      this.dataTracker.startTracking(DATA_LAST_HIT_TICK, 0);
      this.dataTracker.startTracking(DATA_SLIDING, false);
      this.dataTracker.startTracking(DATA_HIT_DIR_YAW, 0.0F);
      this.dataTracker.startTracking(DATA_LOW_STAMINA, false);
      this.dataTracker.startTracking(DATA_HAS_HAMMER, false);
      this.dataTracker.startTracking(DATA_IS_FALLING, false);
      this.dataTracker.startTracking(DATA_IS_LANDING, false);
      this.dataTracker.startTracking(DATA_LAST_LANDING_TICK, 0);
      this.dataTracker.startTracking(DATA_LANDING_INTENSITY, 0.0F);
      this.dataTracker.startTracking(DATA_IS_JUMPING, false);
      this.dataTracker.startTracking(DATA_JUMP_LAUNCHED, false);
      this.dataTracker.startTracking(DATA_JUMP_VEL_X, 0.0F);
      this.dataTracker.startTracking(DATA_JUMP_VEL_Y, 0.0F);
      this.dataTracker.startTracking(DATA_JUMP_VEL_Z, 0.0F);
      this.dataTracker.startTracking(DATA_CRYSTAL_SHELL_ID, -1);
      this.dataTracker.startTracking(DATA_IS_CRYSTAL_PEEKING, false);
      this.dataTracker.startTracking(DATA_IS_KNOCKED, false);
      this.dataTracker.startTracking(DATA_KNOCKED_YAW, 0.0F);
   }

   public boolean isLowStamina() {
      return this.dataTracker.get(DATA_LOW_STAMINA);
   }

   public boolean isMoving() {
      return this.dataTracker.get(DATA_IS_MOVING);
   }

   public void setMoving(boolean moving) {
      this.dataTracker.set(DATA_IS_MOVING, moving);
   }

   @Nullable
   public UUID getShifterUUID() {
      return this.dataTracker.get(DATA_SHIFTER_UUID).orElse(null);
   }

   public void setShifterUUID(@Nullable UUID uuid) {
      this.dataTracker.set(DATA_SHIFTER_UUID, Optional.ofNullable(uuid));
   }

   public int getTransformationTicks() {
      return this.dataTracker.get(DATA_TRANSFORMATION_TICKS);
   }

   public void setTransformationTicks(int ticks) {
      this.dataTracker.set(DATA_TRANSFORMATION_TICKS, ticks);
   }

   public boolean isTransforming() {
      return this.getTransformationTicks() > 0;
   }

   @Override
   public boolean isDismounting() {
      return this.dataTracker.get(DATA_IS_DISMOUNTING);
   }

   public void setDismounting(boolean dismounting) {
      this.dataTracker.set(DATA_IS_DISMOUNTING, dismounting);
   }

   public int getLastStompTick() {
      return this.dataTracker.get(DATA_LAST_STOMP_TICK);
   }

   public void setLastStompTick(int tick) {
      this.dataTracker.set(DATA_LAST_STOMP_TICK, tick);
   }

   public boolean isDismountAllowed() {
      return this.isIncapacitated() ? false : this.allowDismount || this.isDismounting();
   }

   public void setDismountAllowed(boolean allowed) {
      this.allowDismount = allowed;
   }

   public int getCrystalShellId() {
      return this.dataTracker.get(DATA_CRYSTAL_SHELL_ID);
   }

   public void setCrystalShellId(int id) {
      this.dataTracker.set(DATA_CRYSTAL_SHELL_ID, id);
   }

   public boolean isCrystalPeeking() {
      return this.dataTracker.get(DATA_IS_CRYSTAL_PEEKING);
   }

   public void setCrystalPeeking(boolean peeking) {
      this.dataTracker.set(DATA_IS_CRYSTAL_PEEKING, peeking);
   }

   public void spawnCrystalShell(double x, double y, double z, UUID shifterUUID) {
      if (!this.getWorld().isClient()) {
         CrystalShellWarhammerEntity crystal = new CrystalShellWarhammerEntity(DannysAot.CRYSTAL_SHELL_WARHAMMER, this.getWorld());
         crystal.setPosition(x, y, z);
         crystal.setShifterUUID(shifterUUID);
         crystal.setParentTitanId(this.getId());
         this.getWorld().spawnEntity(crystal);
         this.crystalShell = crystal;
         this.crystalShellPos = new Vec3d(x, y, z);
         this.crystalShellUUID = crystal.getUuid();
         this.setCrystalShellId(crystal.getId());
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(x, y, z));
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
         }

         DannysAot.LOGGER.info("Spawned crystal shell for Warhammer Titan at ({}, {}, {})", new Object[]{x, y, z});
      }
   }

   public void removeCrystalShell() {
      if (this.crystalShellPos != null && !this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(this.crystalShellPos.x, this.crystalShellPos.y, this.crystalShellPos.z));
         serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
      }

      if (this.crystalShell != null && !this.crystalShell.isRemoved()) {
         this.crystalShell.discard();
      }

      this.crystalShell = null;
      this.crystalShellPos = null;
      this.crystalShellUUID = null;
      this.setCrystalShellId(-1);
   }

   @Nullable
   private CrystalShellWarhammerEntity getCrystalShellEntity() {
      if (this.crystalShell != null && !this.crystalShell.isRemoved()) {
         return this.crystalShell;
      } else {
         int id = this.getCrystalShellId();
         if (id != -1 && this.getWorld().getEntityById(id) instanceof CrystalShellWarhammerEntity crystal) {
            this.crystalShell = crystal;
            return crystal;
         } else if (this.crystalShellUUID != null
            && this.getWorld() instanceof ServerWorld serverLevel
            && serverLevel.getEntity(this.crystalShellUUID) instanceof CrystalShellWarhammerEntity crystal) {
            this.crystalShell = crystal;
            this.setCrystalShellId(crystal.getId());
            return crystal;
         } else {
            return null;
         }
      }
   }

   @Nullable
   public Vec3d getCrystalShellPosition() {
      CrystalShellWarhammerEntity crystal = this.getCrystalShellEntity();
      if (crystal != null) {
         this.crystalShellPos = crystal.getPos();
         return crystal.getPos();
      } else {
         return this.crystalShellPos;
      }
   }

   @Override
   public boolean isSprinting() {
      return this.dataTracker.get(DATA_IS_SPRINTING);
   }

   @Override
   public void setSprinting(boolean sprinting) {
      this.dataTracker.set(DATA_IS_SPRINTING, sprinting);
   }

   public boolean isTitanAttacking() {
      return this.dataTracker.get(DATA_IS_ATTACKING);
   }

   public void setTitanAttacking(boolean attacking) {
      this.dataTracker.set(DATA_IS_ATTACKING, attacking);
   }

   public int getAttackNumber() {
      return this.dataTracker.get(DATA_ATTACK_NUMBER);
   }

   public void setAttackNumber(int number) {
      this.dataTracker.set(DATA_ATTACK_NUMBER, number);
   }

   public boolean isArmed() {
      return this.dataTracker.get(DATA_IS_ARMED);
   }

   public void setArmed(boolean armed) {
      boolean wasArmed = this.dataTracker.get(DATA_IS_ARMED);
      this.dataTracker.set(DATA_IS_ARMED, armed);
      if (wasArmed
         && !armed
         && this.hasHammer()
         && !this.getWorld().isClient()
         && this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isKnocked()) {
         this.setWasMovingOnAttackStart(this.isMoving());
         this.setAttackNumber(5);
         this.setTitanAttacking(true);
         this.attackAnimationTicks = 56;
         this.attackCooldown = 56;
         this.attackEffectTimer = 0;
         this.attackEffectTriggered = false;
      }
   }

   public boolean wasMovingOnAttackStart() {
      return this.dataTracker.get(DATA_WAS_MOVING_ON_ATTACK);
   }

   public void setWasMovingOnAttackStart(boolean moving) {
      this.dataTracker.set(DATA_WAS_MOVING_ON_ATTACK, moving);
   }

   public int getLastAttackImpactTick() {
      return this.dataTracker.get(DATA_LAST_ATTACK_IMPACT_TICK);
   }

   public void setLastAttackImpactTick(int tick) {
      this.dataTracker.set(DATA_LAST_ATTACK_IMPACT_TICK, tick);
   }

   public int getLastHitTick() {
      return this.dataTracker.get(DATA_LAST_HIT_TICK);
   }

   public void setLastHitTick(int tick) {
      this.dataTracker.set(DATA_LAST_HIT_TICK, tick);
   }

   public float getHitDirYaw() {
      return this.dataTracker.get(DATA_HIT_DIR_YAW);
   }

   public boolean hasHammer() {
      return this.dataTracker.get(DATA_HAS_HAMMER);
   }

   public void setHasHammer(boolean has) {
      this.dataTracker.set(DATA_HAS_HAMMER, has);
   }

   public boolean isFalling() {
      return this.dataTracker.get(DATA_IS_FALLING);
   }

   public void setFalling(boolean falling) {
      this.dataTracker.set(DATA_IS_FALLING, falling);
   }

   public boolean isLanding() {
      return this.dataTracker.get(DATA_IS_LANDING);
   }

   public void setLanding(boolean landing) {
      this.dataTracker.set(DATA_IS_LANDING, landing);
   }

   public int getLastLandingTick() {
      return this.dataTracker.get(DATA_LAST_LANDING_TICK);
   }

   public void setLastLandingTick(int tick) {
      this.dataTracker.set(DATA_LAST_LANDING_TICK, tick);
   }

   public float getLandingIntensity() {
      return this.dataTracker.get(DATA_LANDING_INTENSITY);
   }

   public void setLandingIntensity(float intensity) {
      this.dataTracker.set(DATA_LANDING_INTENSITY, intensity);
   }

   public boolean isJumping() {
      return this.dataTracker.get(DATA_IS_JUMPING);
   }

   @Override
   public void setJumping(boolean jumping) {
      this.dataTracker.set(DATA_IS_JUMPING, jumping);
   }

   @Override
   public boolean isInSneakingPose() {
      return this.dataTracker.get(DATA_IS_CROUCHING);
   }

   public void setCrouching(boolean crouching) {
      if (!crouching || !StrwsRestraintTracker.isFullyRestrained(this.getUuid())) {
         this.dataTracker.set(DATA_IS_CROUCHING, crouching);
      }
   }

   public boolean isPlayerControlled() {
      return this.getShifterUUID() != null && this.getControllingPassenger() instanceof PlayerEntity;
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.02)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 15.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 4.5)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.5)
         .add(daot.compat.attributes.DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      if (this.despawnAtGameTime >= 0L) {
         nbt.putLong("DespawnAtGameTime", this.despawnAtGameTime);
      }

      if (this.crystalShellUUID != null) {
         nbt.putUuid("CrystalShellUUID", this.crystalShellUUID);
      }

      if (this.crystalShellPos != null) {
         nbt.putDouble("CrystalShellX", this.crystalShellPos.x);
         nbt.putDouble("CrystalShellY", this.crystalShellPos.y);
         nbt.putDouble("CrystalShellZ", this.crystalShellPos.z);
      }

      nbt.putBoolean("Incapacitated", this.isIncapacitated());
      nbt.putInt("IncapacitateTicks", this.incapacitateTicks);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("DespawnAtGameTime")) {
         this.despawnAtGameTime = nbt.getLong("DespawnAtGameTime");
      }

      if (nbt.contains("CrystalShellUUID")) {
         this.crystalShellUUID = nbt.getUuid("CrystalShellUUID");
      }

      if (nbt.contains("CrystalShellX")) {
         this.crystalShellPos = new Vec3d(nbt.getDouble("CrystalShellX"), nbt.getDouble("CrystalShellY"), nbt.getDouble("CrystalShellZ"));
         if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(this.crystalShellPos.x, this.crystalShellPos.y, this.crystalShellPos.z));
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
         }
      }

      if (nbt.contains("Incapacitated")) {
         this.setIncapacitated(nbt.getBoolean("Incapacitated"));
      }

      if (nbt.contains("IncapacitateTicks")) {
         this.incapacitateTicks = nbt.getInt("IncapacitateTicks");
      }
   }

   @Override
   public EntityDimensions getBaseDimensions(EntityPose pose) {
      return !this.isInSneakingPose() && !this.isDismounting() ? STANDING_DIMENSIONS : CROUCHING_DIMENSIONS;
   }

   @Override
   public void onDeath(DamageSource damageSource) {
      if (!this.getWorld().isClient() && !this.defeated) {
         this.defeated = true;
         this.setIncapacitated(false);
         this.incapacitateTicks = 0;
         this.blindnessTicks = 0;
         this.setHealth(1.0F);
         this.setCrystalPeeking(false);
         this.removeCrystalShell();
         if (this.bossBar != null) {
            this.bossBar.clearPlayers();
            this.bossBar = null;
         }

         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            for (Entity passenger : this.getPassengerList()) {
               if (passenger instanceof ServerPlayerEntity serverPlayer && serverPlayer.getUuid().equals(shifterUUID)) {
                  this.startDismounting(serverPlayer);
                  serverPlayer.setInvisible(false);
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 900, 4));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 900, 2));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 900, 4));
                  serverPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
                  DefeatedCarryTracker.markDefeated(serverPlayer.getUuid());
                  serverPlayer.sendMessage(Text.literal("Your titan has been defeated! Leaving will have consequences!").formatted(Formatting.RED));
                  DannysAot.LOGGER.info("Player {} ejected from defeated Warhammer Titan", serverPlayer.getName().getString());
                  break;
               }
            }
         }
      }
   }

   public void incapacitate() {
      if (!this.getWorld().isClient() && !this.isDefeated() && !this.isIncapacitated()) {
         this.setIncapacitated(true);
         this.incapacitateTicks = 200;
         this.cancelSpin();
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.attackAnimationTicks = 0;
         this.attackCooldown = 0;
         this.blindnessTicks = 0;
         this.setDismounting(true);
         this.allowDismount = false;
         this.dismountVisibilityDelay = 3;
         this.calculateDimensions();
         NapeSmokeHelper.onDismountStart(this, this.napeEntity);
         if (this.getControllingPassenger() instanceof PlayerEntity player) {
            player.setInvisible(false);
         }

         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            for (Entity passenger : this.getPassengerList()) {
               if (passenger instanceof ServerPlayerEntity serverPlayer && serverPlayer.getUuid().equals(shifterUUID)) {
                  serverPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
                  serverPlayer.sendMessage(Text.literal("Your titan has been incapacitated! Hold out for 10 seconds...").formatted(Formatting.GOLD), true);
                  break;
               }
            }
         }
      }
   }

   private void recover() {
      if (!this.getWorld().isClient() && !this.isDefeated()) {
         this.setIncapacitated(false);
         this.incapacitateTicks = 0;
         if (this.getControllingPassenger() instanceof PlayerEntity player) {
            player.setInvisible(true);
         }

         this.setDismounting(false);
         this.allowDismount = false;
         this.dismountVisibilityDelay = 0;
         this.calculateDimensions();
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            for (Entity passenger : this.getPassengerList()) {
               if (passenger instanceof ServerPlayerEntity serverPlayer && serverPlayer.getUuid().equals(shifterUUID)) {
                  serverPlayer.sendMessage(Text.literal("Your titan is recovering!").formatted(Formatting.GREEN), true);
                  break;
               }
            }
         }
      }
   }

   @Override
   public boolean isInvulnerableTo(DamageSource damageSource) {
      return this.defeated ? true : super.isInvulnerableTo(damageSource);
   }

   public boolean isDefeated() {
      return this.defeated;
   }

   @Override
   public boolean isIncapacitated() {
      return this.dataTracker.get(DATA_IS_INCAPACITATED);
   }

   public void setIncapacitated(boolean v) {
      this.dataTracker.set(DATA_IS_INCAPACITATED, v);
   }

   public boolean isKnocked() {
      return this.dataTracker.get(DATA_IS_KNOCKED);
   }

   public float getKnockedYaw() {
      return this.dataTracker.get(DATA_KNOCKED_YAW);
   }

   public void triggerKnockback(Entity source) {
      if (!this.getWorld().isClient() && !this.isDefeated() && source != null) {
         this.knockedElapsedTicks = 0;
         this.dataTracker.set(DATA_IS_KNOCKED, true);
         double toSourceX = source.getX() - this.getX();
         double toSourceZ = source.getZ() - this.getZ();
         if (toSourceX * toSourceX + toSourceZ * toSourceZ < 1.0E-8) {
            float yr = (float)Math.toRadians(source.getYaw());
            toSourceX = -Math.sin(yr);
            toSourceZ = Math.cos(yr);
         }

         this.lockedYaw = (float)Math.toDegrees(Math.atan2(-toSourceX, toSourceZ));
         this.dataTracker.set(DATA_KNOCKED_YAW, this.lockedYaw);
         this.setYaw(this.lockedYaw);
         this.bodyYaw = this.lockedYaw;
         this.headYaw = this.lockedYaw;
         this.cancelActiveStatesForKnockback();
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 4.0F, 0.5F);
      }
   }

   private boolean isKnockedHeld() {
      return this.isKnocked() && this.knockedElapsedTicks >= 60;
   }

   private void releaseKnocked() {
      this.knockedElapsedTicks = 0;
      this.lockedYaw = Float.NaN;
      this.dataTracker.set(DATA_IS_KNOCKED, false);
   }

   private void cancelActiveStatesForKnockback() {
      this.spinActive = false;
      this.spinDamageTimer = 0;
      this.setTitanAttacking(false);
      this.setAttackNumber(0);
      this.attackAnimationTicks = 0;
      this.attackCooldown = 0;
      this.attackEffectTriggered = true;
   }

   @Override
   protected void initGoals() {
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   public boolean isPersistent() {
      return true;
   }

   @Nullable
   @Override
   public LivingEntity getControllingPassenger() {
      if (this.getFirstPassenger() instanceof PlayerEntity player) {
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            return player;
         }
      }

      return null;
   }

   private Vec3d getRidePosition(Entity passenger) {
      double headHeight = 10.0;
      return new Vec3d(0.0, headHeight, 0.0);
   }

   @Override
   public Vec3d updatePassengerForDismount(LivingEntity passenger) {
      if (this.isCrystalPeeking()) {
         Vec3d crystalPos = this.getCrystalShellPosition();
         if (crystalPos != null) {
            return new Vec3d(crystalPos.x, crystalPos.y + 0.5, crystalPos.z);
         }
      }

      if (this.savedDismountPos != null) {
         Vec3d pos = this.savedDismountPos;
         this.savedDismountPos = null;
         return pos;
      } else {
         return super.updatePassengerForDismount(passenger);
      }
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         if (this.isCrystalPeeking()) {
            Vec3d crystalPos = this.getCrystalShellPosition();
            if (crystalPos != null) {
               positionUpdater.accept(passenger, crystalPos.x, crystalPos.y - 0.5, crystalPos.z);
               return;
            }
         }

         Vec3d ridePos = this.getRidePosition(passenger);
         double targetForwardOffset;
         double targetExtraY;
         if (this.isDismounting()) {
            targetForwardOffset = 2.5;
            targetExtraY = -3.5;
         } else {
            targetForwardOffset = 1.0;
            targetExtraY = 0.0;
         }

         this.currentRideForwardOffset = this.currentRideForwardOffset + (targetForwardOffset - this.currentRideForwardOffset) * 0.15F;
         this.currentRideExtraY = this.currentRideExtraY + (targetExtraY - this.currentRideExtraY) * 0.15F;
         if (Math.abs(this.currentRideExtraY - targetExtraY) < 0.01) {
            this.currentRideExtraY = targetExtraY;
         }

         float yawRad = (float)Math.toRadians(this.getYaw());
         double offsetX = -Math.sin(yawRad) * this.currentRideForwardOffset;
         double offsetZ = Math.cos(yawRad) * this.currentRideForwardOffset;
         double x = this.getX() + ridePos.x + offsetX;
         double y = this.getY() + ridePos.y + this.currentRideExtraY + this.smoothYOffset;
         double z = this.getZ() + ridePos.z + offsetZ;
         positionUpdater.accept(passenger, x, y, z);
      }
   }

   @Override
   protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
      super.tickControlled(controllingPlayer, movementInput);
      if (this.isDismounting()) {
         if (!this.getWorld().isClient()) {
            this.setMoving(false);
         }
      } else {
         if (this.getWorld().isClient() && this.isKnocked()) {
            float ky = this.getKnockedYaw();
            this.setYaw(ky);
            this.prevYaw = ky;
            this.bodyYaw = ky;
            this.prevBodyYaw = ky;
            this.headYaw = ky;
            this.prevHeadYaw = ky;
         }

         if (this.getWorld().isClient() && !this.isTransforming() && !this.isSliding() && !this.isKnocked()) {
            if (this.isArmed()) {
               float targetYaw = controllingPlayer.getYaw();
               float currentYaw = this.getYaw();
               float lerpFactor = 0.5F;
               float newYaw = currentYaw + lerpFactor * wrapDegrees(targetYaw - currentYaw);
               this.setYaw(newYaw);
               this.bodyYaw = newYaw;
               this.headYaw = newYaw;
            } else {
               Vec3d movement = this.getVelocity();
               if (movement.horizontalLengthSquared() > 1.0E-4) {
                  float targetYaw = (float)(Math.atan2(-movement.x, movement.z) * (180.0 / Math.PI));
                  float currentYaw = this.getYaw();
                  float lerpFactor = 0.25F;
                  float newYaw = currentYaw + lerpFactor * wrapDegrees(targetYaw - currentYaw);
                  this.setYaw(newYaw);
                  this.bodyYaw = newYaw;
                  this.headYaw = newYaw;
               }
            }
         } else if (!this.getWorld().isClient()) {
            this.bodyYaw = this.getYaw();
            this.headYaw = this.getYaw();
         }

         if (!this.isDismounting() && !controllingPlayer.isDead() && !ShifterVisibilityHelper.isInReentryWindow(controllingPlayer)) {
            controllingPlayer.setInvisible(true);
         }

         ShifterVisibilityHelper.tick(controllingPlayer);
         if (!this.getWorld().isClient()) {
            boolean hasInput = Math.abs(controllingPlayer.forwardSpeed) > 0.01 || Math.abs(controllingPlayer.sidewaysSpeed) > 0.01;
            if (hasInput && this.isKnockedHeld()) {
               this.releaseKnocked();
            }

            int atkN = this.getAttackNumber();
            boolean isMovementBlockingAttack = this.isTitanAttacking() && (atkN == 3 || atkN == 5 || atkN == 8 || atkN == 9 || atkN == 10 || atkN == 12);
            boolean canShowMovement = !this.isTransforming() && !this.isDismounting() && !isMovementBlockingAttack && (!this.isArmed() || !this.hasHammer());
            this.setMoving(hasInput && canShowMovement);
            boolean canMove = !this.isTransforming() && !this.isDismounting();
            boolean isInHeavyAttack = this.isTitanAttacking()
               && (atkN == 3 || atkN == 4 || atkN == 5 || atkN == 8 || atkN == 9 || atkN == 10 || atkN == 11 || atkN == 12);
            if (!isInHeavyAttack && !this.isArmed()) {
               if (this.wasSprintingBeforeAttack && hasInput && controllingPlayer.isSprinting()) {
                  this.setSprinting(true);
                  this.wasSprintingBeforeAttack = false;
               } else {
                  this.wasSprintingBeforeAttack = false;
               }
            } else {
               if (this.isSprinting() || hasInput && controllingPlayer.isSprinting()) {
                  this.wasSprintingBeforeAttack = true;
               }

               this.setSprinting(false);
            }

            if (!hasInput || !canMove) {
               this.setSprinting(false);
               this.wasSprintingBeforeAttack = false;
            }

            boolean canCrouch = !this.isTransforming() && !this.isDismounting();
            boolean shiftPressed = controllingPlayer.isSneaking() && canCrouch;
            boolean currentlyCrouching = this.isInSneakingPose();
            if (shiftPressed != currentlyCrouching && this.crouchTransitionTicksRemaining <= 0) {
               this.crouchHysteresisCounter++;
               if (this.crouchHysteresisCounter >= 2) {
                  if (shiftPressed && !currentlyCrouching) {
                     this.wasSprintingBeforeCrouch = this.isSprinting();
                  }

                  this.setCrouching(shiftPressed);
                  if (currentlyCrouching && !shiftPressed && hasInput) {
                     if (this.wasSprintingBeforeCrouch || this.isSprinting()) {
                        this.setSprinting(true);
                     }

                     this.wasSprintingBeforeCrouch = false;
                  }

                  this.crouchHysteresisCounter = 0;
               }
            } else {
               this.crouchHysteresisCounter = 0;
            }

            if (controllingPlayer instanceof ServerPlayerEntity sp) {
               boolean lowStamina = ModNetworking.isLowStamina(sp.getUuid());
               if (lowStamina != this.dataTracker.get(DATA_LOW_STAMINA)) {
                  this.dataTracker.set(DATA_LOW_STAMINA, lowStamina);
               }
            }
         }
      }
   }

   @Override
   protected float turnHead(float bodyRotation, float headRotation) {
      if (this.isPlayerControlled()) {
         this.bodyYaw = this.getYaw();
         this.headYaw = this.getYaw();
         return headRotation;
      } else {
         return super.turnHead(bodyRotation, headRotation);
      }
   }

   private static float wrapDegrees(float degrees) {
      float wrapped = degrees % 360.0F;
      if (wrapped >= 180.0F) {
         wrapped -= 360.0F;
      }

      if (wrapped < -180.0F) {
         wrapped += 360.0F;
      }

      return wrapped;
   }

   @Override
   protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
      if (!this.isTransforming() && !this.isDismounting() && !this.isSliding() && !this.isKnocked()) {
         int atkNum = this.getAttackNumber();
         if (!this.isTitanAttacking() || atkNum != 3 && atkNum != 5 && atkNum != 8 && atkNum != 9 && atkNum != 10 && atkNum != 12) {
            if (this.isArmed() && this.hasHammer()) {
               return Vec3d.ZERO;
            } else if (this.jumpAnimTicks > 0 && !this.jumpLaunched) {
               return Vec3d.ZERO;
            } else {
               float forward = controllingPlayer.forwardSpeed;
               float strafe = controllingPlayer.sidewaysSpeed;
               if (forward == 0.0F && strafe == 0.0F) {
                  return Vec3d.ZERO;
               } else {
                  float cameraYaw = controllingPlayer.getYaw();
                  float titanYaw = this.getYaw();
                  float relativeAngle = cameraYaw - titanYaw;
                  float relativeRadians = (float)Math.toRadians(relativeAngle);
                  float sin = (float)Math.sin(relativeRadians);
                  float cos = (float)Math.cos(relativeRadians);
                  float localX = strafe * cos - forward * sin;
                  float localZ = strafe * sin + forward * cos;
                  return new Vec3d(localX, 0.0, localZ);
               }
            }
         } else {
            return Vec3d.ZERO;
         }
      } else {
         return Vec3d.ZERO;
      }
   }

   @Override
   public void setWireRestraintCount(int count) {
      this.dataTracker.set(DATA_WIRE_RESTRAINT, count);
   }

   @Override
   public int getWireRestraintCount() {
      return this.dataTracker.get(DATA_WIRE_RESTRAINT);
   }

   @Override
   protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
      if (!this.isLanding() && !this.isKnocked()) {
         int attackNum = this.getAttackNumber();
         boolean isHeavyAttack = this.isTitanAttacking()
            && (attackNum == 3 || attackNum == 4 || attackNum == 5 || attackNum == 8 || attackNum == 9 || attackNum == 10 || attackNum == 11 || attackNum == 12);
         boolean canRun = this.isSprinting() && !isHeavyAttack && !this.isArmed() && !this.isInSneakingPose();
         double targetSpeed;
         if (this.isInSneakingPose()) {
            targetSpeed = 0.15;
         } else if (canRun) {
            targetSpeed = this.hasHammer() ? 0.9107142857142857 : 1.02;
         } else {
            targetSpeed = 0.3;
         }

         if (this.currentSpeed < targetSpeed) {
            this.currentSpeed = Math.min(this.currentSpeed + 0.12, targetSpeed);
         } else if (this.currentSpeed > targetSpeed) {
            this.currentSpeed = Math.max(this.currentSpeed - 0.12, targetSpeed);
         }

         if (this.isBlinded()) {
            this.currentSpeed *= 0.6666666666666666;
         }

         if (this.hitSlowTicks > 0) {
            this.currentSpeed *= 0.5;
         }

         return (float)(this.currentSpeed * StrwsRestraintTracker.movementFactor(this, this.getWireRestraintCount()));
      } else {
         this.currentSpeed = 0.0;
         return 0.0F;
      }
   }

   @Override
   protected void removePassenger(Entity passenger) {
      if (!this.getWorld().isClient() && passenger instanceof ServerPlayerEntity serverPlayer) {
         Vec3d crystalPos = this.getCrystalShellPosition();
         if (crystalPos != null) {
            serverPlayer.requestTeleport(crystalPos.x, crystalPos.y + 0.5, crystalPos.z);
         }
      }

      super.removePassenger(passenger);
      if (passenger instanceof PlayerEntity player) {
         ShifterVisibilityHelper.cancelReentry(player);
         player.setInvisible(false);
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            this.setShifterUUID(null);
            if (!this.getWorld().isClient()) {
               ServerHookTracker.grantDismountFallImmunity(player.getUuid());
               if (player instanceof ServerPlayerEntity sp) {
                  ShifterMarkTracker.markFullyDismounted(sp, this);
               }

               DismountSmokeHelper.armFullDismount(player.getUuid(), player.getX(), player.getY(), player.getZ(), this.getWorld().getServer().getTicks());
            }

            this.despawnAtGameTime = this.getWorld().getTime() + 1200L;
            NapeSmokeHelper.onFullDismount(this.getId());
            if (this.bossBar != null) {
               this.bossBar.clearPlayers();
               this.bossBar = null;
            }
         }
      }

      this.setCrystalPeeking(false);
      this.removeCrystalShell();
   }

   public boolean isDismountToggleOnCooldown() {
      return this.dismountToggleCooldown > 0;
   }

   public void startDismounting(PlayerEntity player) {
      if (!this.isIncapacitated()) {
         if (!this.isDismountToggleOnCooldown()) {
            if (!this.isKnocked()) {
               this.cancelSpin();
               this.setJumping(false);
               this.jumpAnimTicks = 0;
               this.jumpLaunched = false;
               this.jumpWasAirborne = false;
               this.jumpVelocityApplied = false;
               this.dismountVisibilityDelay = 0;
               this.setCrystalPeeking(true);
               this.setDismounting(true);
               this.allowDismount = true;
               Vec3d crystalPos = this.getCrystalShellPosition();
               this.savedDismountPos = crystalPos != null ? new Vec3d(crystalPos.x, crystalPos.y + 0.5, crystalPos.z) : null;
               this.dismountToggleCooldown = 20;
               this.calculateDimensions();
               NapeSmokeHelper.onDismountStart(this, this.napeEntity);
               float yawRad = (float)Math.toRadians(this.getYaw());
               double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
               double napeY = this.getY() + 10.5;
               double napeZ = this.getZ() + Math.cos(yawRad) * 1.5;
               this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 4.0F, 0.8F);
               if (this.getWorld() instanceof ServerWorld sl) {
                  BlockPos groundPos = new BlockPos((int)Math.floor(this.getX()), (int)Math.floor(this.getY()) - 1, (int)Math.floor(this.getZ()));
                  BlockState groundState = this.getWorld().getBlockState(groundPos);
                  if (!groundState.isAir()) {
                     sl.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState), this.getX(), this.getY(), this.getZ(), 30, 1.5, 0.3, 1.5, 0.1
                     );
                  }
               }
            }
         }
      }
   }

   public void cancelDismounting(PlayerEntity player) {
      if (!this.defeated) {
         if (!this.isIncapacitated()) {
            if (!this.isDismountToggleOnCooldown()) {
               player.setInvisible(true);
               this.dismountVisibilityDelay = 0;
               this.setCrystalPeeking(false);
               this.setDismounting(false);
               this.allowDismount = false;
               this.dismountToggleCooldown = 20;
               this.calculateDimensions();
               float yawRad = (float)Math.toRadians(this.getYaw());
               double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
               double napeY = this.getY() + 10.5;
               double napeZ = this.getZ() + Math.cos(yawRad) * 1.5;
               this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
         }
      }
   }

   public void onPlayerShift(PlayerEntity player) {
      this.setShifterUUID(player.getUuid());
      player.startRiding(this, true);
      if (player instanceof ServerPlayerEntity serverPlayer) {
         ShifterMarkTracker.markEntered(serverPlayer, this);
      }

      if (!this.getWorld().isClient()) {
         DismountSmokeHelper.onShifterSpawn(this);
         DismountSmokeHelper.cancelPendingDismount(player.getUuid());
      }

      if (!this.getWorld().isClient() && this.getWorld().getGameRules().getBoolean(DannysAot.RULE_SHIFT_BOSS_BARS)) {
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Warhammer Titan" : "Warhammer Titan - " + player.getName().getString();
         this.bossBar = new ServerBossBar(Text.literal(bossBarName), Color.PURPLE, Style.PROGRESS);
         this.bossBar.setPercent(1.0F);
      }

      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         double centerX = this.getX();
         double centerY = this.getY() + 3.0;
         double centerZ = this.getZ();
         boolean stealthShift = player.getCommandTags().contains("titan_stealth");
         boolean griefing = DannysAot.isTitanGriefingEnabled(this.getWorld()) && DannysAot.isShifterExplosionDamageEnabled(this.getWorld()) && !stealthShift;
         if (!stealthShift) {
            ExplosionSourceType interaction = griefing ? ExplosionSourceType.BLOCK : ExplosionSourceType.NONE;
            serverLevel.createExplosion(this, centerX, centerY, centerZ, 20.0F, false, interaction);
            serverLevel.createExplosion(this, centerX, centerY + 7.0, centerZ, 14.0F, false, interaction);
            serverLevel.createExplosion(this, centerX, centerY + 13.0, centerZ, 10.0F, false, interaction);
         }

         if (griefing) {
            double fireRadius = 14.0;

            for (int dx = (int)(-fireRadius); dx <= fireRadius; dx++) {
               for (int dz = (int)(-fireRadius); dz <= fireRadius; dz++) {
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  if (dist <= fireRadius && dist > 2.0 && this.random.nextFloat() < 0.3F) {
                     BlockPos firePos = new BlockPos((int)Math.floor(centerX + dx), (int)Math.floor(this.getY()), (int)Math.floor(centerZ + dz));

                     for (int y = 0; y <= 5; y++) {
                        BlockPos checkPos = firePos.up(y);
                        BlockPos belowPos = checkPos.down();
                        if (this.getWorld().getBlockState(checkPos).isAir() && this.getWorld().getBlockState(belowPos).isSolid()) {
                           this.getWorld().setBlockState(checkPos, Blocks.FIRE.getDefaultState(), 3);
                           break;
                        }
                     }
                  }
               }
            }
         }

         double maxRange = stealthShift ? 15.0 : 18.0;

         for (Entity entity : serverLevel.getOtherEntities(this, this.getBoundingBox().expand(maxRange))) {
            if (entity != player
               && entity != this
               && !(entity instanceof WarhammerTitanNapeEntity)
               && !(entity instanceof WarhammerTitanEyeEntity)
               && !(entity instanceof AttackTitanNapeEntity)
               && !(entity instanceof AttackTitanEyeEntity)
               && !(entity instanceof ArmoredTitanNapeEntity)
               && !(entity instanceof ArmoredTitanEyeEntity)
               && !(entity instanceof ColossalTitanNapeEntity)
               && !(entity instanceof ColossalTitanEyeEntity)
               && !(entity instanceof BeastTitanNapeEntity)
               && !(entity instanceof BeastTitanEyeEntity)
               && !(entity instanceof FemaleTitanNapeEntity)
               && !(entity instanceof FemaleTitanEyeEntity)
               && !(entity instanceof AttackTitanEntity)
               && !(entity instanceof ArmoredTitanEntity)
               && !(entity instanceof ColossalTitanEntity)
               && !(entity instanceof FemaleTitanEntity)
               && !(entity instanceof BeastTitanEntity)
               && !(entity instanceof WarhammerTitanEntity)
               && !(
                  entity instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof BeastTitanEntity
                           || p.getVehicle() instanceof WarhammerTitanEntity
                     )
               )) {
               Vec3d toEntity = entity.getPos().subtract(this.getPos());
               double distance = toEntity.length();
               if (distance > 0.0 && distance < maxRange) {
                  double linearIntensity = (maxRange - distance) / maxRange;
                  if (entity instanceof LivingEntity living) {
                     float damage;
                     if (stealthShift) {
                        damage = (float)(30.0 * linearIntensity * linearIntensity);
                     } else {
                        damage = (float)(15.0 * linearIntensity);
                     }

                     if (entity instanceof PlayerEntity) {
                        damage *= 0.3F;
                     }

                     living.damage(this.getDamageSources().explosion(null, player), damage);
                  }

                  double knockbackStrength = stealthShift ? linearIntensity * 3.0 : linearIntensity * 4.0;
                  double upwardStrength = stealthShift ? linearIntensity * 2.5 : linearIntensity * 1.5;
                  Vec3d knockback = toEntity.normalize().multiply(knockbackStrength).add(0.0, upwardStrength, 0.0);
                  entity.setVelocity(entity.getVelocity().add(knockback));
                  if (entity instanceof LivingEntity living) {
                     living.velocityModified = true;
                  }

                  if (!stealthShift && distance < 10.0 && entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)) {
                     living.setFireTicks(100);
                  }
               }
            }
         }
      }
   }

   public void triggerAttack() {
      if (this.isKnocked()) {
         if (this.isKnockedHeld()) {
            this.releaseKnocked();
         }
      } else if (this.spinActive) {
         this.cancelSpin();
      } else if (this.attackCooldown <= 0 && !this.isTransforming() && !this.isDismounting() && this.isOnGround()) {
         this.setWasMovingOnAttackStart(this.isMoving());
         int attackNum;
         int ticks;
         if (this.isArmed() && !this.hasHammer()) {
            attackNum = this.nextJabIsRight ? 15 : 14;
            ticks = 20;
            this.nextJabIsRight = !this.nextJabIsRight;
         } else if (this.hasHammer()) {
            attackNum = this.nextHammerAttackIs2 ? 16 : 1;
            ticks = 20;
            this.nextHammerAttackIs2 = !this.nextHammerAttackIs2;
         } else {
            switch (this.attackCycleIndex) {
               case 0:
                  attackNum = 6;
                  ticks = 16;
                  break;
               default:
                  attackNum = 7;
                  ticks = 16;
            }

            this.attackCycleIndex = (this.attackCycleIndex + 1) % 2;
         }

         this.setAttackNumber(attackNum);
         this.setTitanAttacking(true);
         this.attackAnimationTicks = ticks;
         this.attackCooldown = ticks;
         this.attackEffectTimer = 0;
         this.attackEffectTriggered = false;
      }
   }

   private void cancelSpin() {
      if (this.spinActive) {
         this.spinActive = false;
         this.spinDamageTimer = 0;
         this.setTitanAttacking(false);
         this.attackAnimationTicks = 0;
         this.attackCooldown = 6;
      }
   }

   public void triggerAbility(int abilityNumber) {
      if (this.isKnocked()) {
         if (this.isKnockedHeld()) {
            this.releaseKnocked();
         }
      } else if (!this.isTransforming() && !this.isDismounting() && this.isOnGround()) {
         if (abilityNumber == 3 && this.spinActive) {
            this.cancelSpin();
         } else {
            if (this.spinActive) {
               this.cancelSpin();
            }

            if (this.attackCooldown <= 0) {
               this.setWasMovingOnAttackStart(this.isMoving());
               switch (abilityNumber) {
                  case 1:
                     if (!this.hasHammer()) {
                        return;
                     }

                     this.setAttackNumber(3);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = 35;
                     this.attackCooldown = 35;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     break;
                  case 2:
                     this.setAttackNumber(12);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = 25;
                     this.attackCooldown = 25;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     break;
                  case 3:
                     if (!this.hasHammer()) {
                        return;
                     }

                     this.spinActive = true;
                     this.setAttackNumber(4);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = Integer.MAX_VALUE;
                     this.attackCooldown = Integer.MAX_VALUE;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     this.spinDamageTimer = 0;
                     break;
                  case 4:
                     if (this.piercingThornsCooldownTicks > 0) {
                        return;
                     }

                     this.setAttackNumber(8);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = 25;
                     this.attackCooldown = 25;
                     this.piercingThornsCooldownTicks = 240;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     break;
                  case 5:
                     if (this.spikeFieldCooldownTicks > 0) {
                        return;
                     }

                     this.setAttackNumber(9);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = 25;
                     this.attackCooldown = 25;
                     this.spikeFieldCooldownTicks = 240;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     break;
                  case 6:
                     DannysAot.LOGGER.info("Ability 6 (Hardened Block) not yet implemented for Warhammer Titan");
                     break;
                  case 7:
                     DannysAot.LOGGER.info("Ability 7 (Control Center) not yet implemented for Warhammer Titan");
                     break;
                  case 8:
                     if (this.impaleCooldownTicks > 0) {
                        return;
                     }

                     this.setAttackNumber(10);
                     this.setTitanAttacking(true);
                     this.attackAnimationTicks = 25;
                     this.attackCooldown = 25;
                     this.impaleCooldownTicks = 600;
                     this.attackEffectTimer = 0;
                     this.attackEffectTriggered = false;
                     break;
                  case 9:
                     if (this.hasHammer()) {
                        this.setAttackNumber(13);
                        this.setTitanAttacking(true);
                        this.attackAnimationTicks = 60;
                        this.attackCooldown = 60;
                        this.attackEffectTimer = 0;
                        this.attackEffectTriggered = false;
                     } else {
                        this.setAttackNumber(11);
                        this.setTitanAttacking(true);
                        this.attackAnimationTicks = 100;
                        this.attackCooldown = 100;
                        this.attackEffectTimer = 0;
                        this.attackEffectTriggered = false;
                     }
               }
            }
         }
      }
   }

   public void triggerJump(PlayerEntity player) {
      if (!StrwsRestraintTracker.isFullyRestrained(this)) {
         if (!this.isKnocked()) {
            if (this.isOnGround()
               && !this.isTitanAttacking()
               && !this.isTransforming()
               && !this.isDismounting()
               && !this.isArmed()
               && !this.isFalling()
               && !this.isLanding()
               && this.jumpAnimTicks <= 0
               && this.attackCooldown <= 0
               && this.jumpCooldownTicks <= 0
               && !this.clientJumpActive
               && !this.clientFallActive
               && !this.hasHammer()) {
               float forward = player.forwardSpeed;
               float strafe = player.sidewaysSpeed;
               this.jumpDirX = 0.0;
               this.jumpDirZ = 0.0;
               if (Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F) {
                  float cameraYaw = player.getYaw();
                  float sinYaw = MathHelper.sin(cameraYaw * (float) (Math.PI / 180.0));
                  float cosYaw = MathHelper.cos(cameraYaw * (float) (Math.PI / 180.0));
                  double hx = strafe * cosYaw - forward * sinYaw;
                  double hz = forward * cosYaw + strafe * sinYaw;
                  double len = Math.sqrt(hx * hx + hz * hz);
                  if (len > 0.001) {
                     this.jumpDirX = hx / len;
                     this.jumpDirZ = hz / len;
                  }
               }

               this.jumpAnimTicks = 10;
               this.jumpLaunched = false;
               this.jumpWasAirborne = false;
               this.jumpVelocityApplied = false;
               this.jumpToFallTicks = 12;
               this.dataTracker.set(DATA_JUMP_VEL_X, 0.0F);
               this.dataTracker.set(DATA_JUMP_VEL_Y, 0.0F);
               this.dataTracker.set(DATA_JUMP_VEL_Z, 0.0F);
               this.setJumping(true);
               if (this.getWorld().isClient()) {
                  this.clientJumpActive = true;
                  this.clientFallActive = false;
                  this.clientInJumpArc = true;
               }
            }
         }
      }
   }

   private float hammerMeleeMultiplier() {
      return this.hasHammer() ? 1.5F : 1.0F;
   }

   private void dealAttackDamage() {
      SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
      SoundEvent sound = impactSounds[this.random.nextInt(impactSounds.length)];
      float pitch = 0.9F + this.random.nextFloat() * 0.1F;
      float yaw = (float)Math.toRadians(this.getYaw());
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      double soundDist = this.getAttackNumber() == 5 ? 15.0 : 5.0;
      this.getWorld().playSound(null, this.getX() + fx * soundDist, this.getY() + 3.0, this.getZ() + fz * soundDist, sound, SoundCategory.HOSTILE, 8.0F, pitch);
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      double attackRange = this.getAttackNumber() == 5 ? 20.0 : 10.0;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(attackRange, 10.0, attackRange))) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof WarhammerTitanNapeEntity)
               && !(target instanceof WarhammerTitanEyeEntity)
               && !(target instanceof FemaleTitanNapeEntity)
               && !(target instanceof FemaleTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof BeastTitanNapeEntity)
               && !(target instanceof BeastTitanEyeEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof BeastTitanEntity
                           || p.getVehicle() instanceof WarhammerTitanEntity
                     )
               )) {
               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
               if (dot > 0.2 && toTarget.horizontalLength() < attackRange) {
                  if (target instanceof TitanNapeEntity napeEntity) {
                     TitanEntity parentTitan = napeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof SmallTitanNapeEntity smallNapeEntity) {
                     SmallTitanEntity parentTitan = smallNapeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof SmallTitan2NapeEntity smallNape2Entity) {
                     SmallTitan2Entity parentTitan = smallNape2Entity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof FritzTitanNapeEntity fritzNapeEntity) {
                     FritzTitanEntity parentTitan = fritzNapeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof WarhammerTitanNapeEntity whNapeEntity) {
                     WarhammerTitanEntity parentTitan = whNapeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else {
                     int attackNum = this.getAttackNumber();
                     float baseDamage = (float)ModConfig.get().attackTitanAttackDamage;
                     baseDamage *= this.hammerMeleeMultiplier();
                     if (attackNum == 1) {
                        baseDamage *= 1.5F;
                     }

                     if (attackNum == 5) {
                        baseDamage *= 2.5F;
                     }

                     float attackDamage = isShifterTitan(target)
                        ? (attackNum != 3 && attackNum != 5 ? baseDamage / 3.0F : baseDamage * 2.0F / 3.0F)
                        : baseDamage;
                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        attackDamage *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, attackDamage));
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     double knockScale = attackNum == 5 ? 3.0 : 1.0;
                     if (target instanceof TitanEntity titanTarget) {
                        Vec3d knockback = toTarget.normalize().multiply(5.0 * knockScale, 2.0 * knockScale, 5.0 * knockScale);
                        titanTarget.applyKnockback(knockback);
                     } else if (target instanceof FritzTitanEntity fritzTarget) {
                        Vec3d knockback = toTarget.normalize().multiply(5.0 * knockScale, 2.0 * knockScale, 5.0 * knockScale);
                        fritzTarget.applyKnockback(knockback);
                     } else if (target instanceof SmallTitanEntity || target instanceof SmallTitan2Entity) {
                        Vec3d knockback = toTarget.normalize().multiply(3.0 * knockScale, 1.5 * knockScale, 3.0 * knockScale);
                        target.setVelocity(knockback);
                        target.velocityModified = true;
                     } else if (isShifterTitan(target)) {
                        Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                        Vec3d knockback = horizontalDir.multiply((armedBlock ? 1.5 : 3.0) * knockScale).add(0.0, (armedBlock ? 0.5 : 1.0) * knockScale, 0.0);
                        if (target instanceof AttackTitanEntity at) {
                           at.setPendingKnockback(knockback);
                           at.applyHitSlow(20);
                           at.triggerHitReaction(this, false, false);
                        } else if (target instanceof FemaleTitanEntity ft) {
                           ft.setPendingKnockback(knockback);
                           ft.applyHitSlow(20);
                           ft.triggerHitReaction(this, false, false);
                        } else if (target instanceof ArmoredTitanEntity art) {
                           art.setPendingKnockback(knockback);
                           art.applyHitSlow(20);
                           art.triggerHitReaction(this, false, false);
                        } else if (target instanceof ColossalTitanEntity ct) {
                           ct.setPendingKnockback(knockback);
                           ct.applyHitSlow(20);
                        } else if (target instanceof WarhammerTitanEntity wh) {
                           wh.setPendingKnockback(knockback);
                           wh.applyHitSlow(20);
                           wh.triggerHitReaction(this, false, false);
                        }
                     } else {
                        Vec3d knockback = toTarget.normalize().multiply(1.5 * knockScale, 0.5 * knockScale, 1.5 * knockScale);
                        target.setVelocity(target.getVelocity().add(knockback));
                     }
                  }
               }
            }
         }
      }

      if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
         boolean isAbility = this.getAttackNumber() == 3
            || this.getAttackNumber() == 4
            || this.getAttackNumber() == 5
            || this.getAttackNumber() == 8
            || this.getAttackNumber() == 9
            || this.getAttackNumber() == 10
            || this.getAttackNumber() == 11;
         ModNetworking.drainStamina(sp.getUuid(), isAbility ? 15.0F : 5.0F);
      }
   }

   private void performSpinDamage() {
      double spinRadius = 8.0;
      float baseDamage = (float)ModConfig.get().attackTitanAttackDamage * 0.5F;
      baseDamage *= this.hammerMeleeMultiplier();

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(spinRadius, 6.0, spinRadius))) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof WarhammerTitanNapeEntity)
               && !(target instanceof WarhammerTitanEyeEntity)
               && !(target instanceof FemaleTitanNapeEntity)
               && !(target instanceof FemaleTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof BeastTitanNapeEntity)
               && !(target instanceof BeastTitanEyeEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof BeastTitanEntity
                           || p.getVehicle() instanceof WarhammerTitanEntity
                     )
               )) {
               double dist = this.distanceTo(target);
               if (!(dist > spinRadius)) {
                  if (target instanceof TitanNapeEntity napeEntity) {
                     TitanEntity parentTitan = napeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof SmallTitanNapeEntity smallNapeEntity) {
                     SmallTitanEntity parentTitan = smallNapeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof SmallTitan2NapeEntity smallNape2Entity) {
                     SmallTitan2Entity parentTitan = smallNape2Entity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else if (target instanceof FritzTitanNapeEntity fritzNapeEntity) {
                     FritzTitanEntity parentTitan = fritzNapeEntity.getParentTitan();
                     if (parentTitan != null) {
                        parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                     }
                  } else {
                     float damage = isShifterTitan(target) ? baseDamage / 3.0F : baseDamage;
                     target.damage(this.getDamageSources().mobAttack(this), damage);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     Vec3d toTarget = target.getPos().subtract(this.getPos());
                     Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                     if (target instanceof TitanEntity titanTarget) {
                        titanTarget.applyKnockback(horizontalDir.multiply(3.0).add(0.0, 1.0, 0.0));
                     } else if (isShifterTitan(target)) {
                        Vec3d knockback = horizontalDir.multiply(2.0).add(0.0, 0.5, 0.0);
                        if (target instanceof AttackTitanEntity at) {
                           at.setPendingKnockback(knockback);
                           at.applyHitSlow(10);
                        } else if (target instanceof FemaleTitanEntity ft) {
                           ft.setPendingKnockback(knockback);
                           ft.applyHitSlow(10);
                        } else if (target instanceof ArmoredTitanEntity art) {
                           art.setPendingKnockback(knockback);
                           art.applyHitSlow(10);
                        } else if (target instanceof ColossalTitanEntity ct) {
                           ct.setPendingKnockback(knockback);
                           ct.applyHitSlow(10);
                        } else if (target instanceof WarhammerTitanEntity wh) {
                           wh.setPendingKnockback(knockback);
                           wh.applyHitSlow(10);
                        }
                     } else {
                        target.setVelocity(target.getVelocity().add(horizontalDir.multiply(1.5).add(0.0, 0.3, 0.0)));
                        if (target instanceof LivingEntity) {
                           target.velocityModified = true;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void performGroundSlam() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         boolean isCharge2 = this.getAttackNumber() == 5;
         int smashRadius = isCharge2 ? 7 : 3;
         int smashDepth = isCharge2 ? 4 : 2;
         int smashDistance = isCharge2 ? 14 : 4;
         float flingChance = isCharge2 ? 0.6F : 0.3F;
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
                        if (!blockState.isAir()
                           && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                           && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                           && (!(blockState.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                           if (this.random.nextFloat() < flingChance) {
                              this.flingBlockFromSlam(serverLevel, pos, blockState, forwardX, forwardZ);
                           } else {
                              this.getWorld().removeBlock(pos, false);
                           }
                        }
                     }
                  }
               }
            }
         }

         if (isCharge2) {
            this.getWorld().playSound(null, centerX, (double)groundY, centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 5.0F, 0.3F);
            this.getWorld().playSound(null, centerX, (double)groundY, centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4.0F, 0.6F);
            if (this.getWorld() instanceof ServerWorld sl) {
               sl.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, centerX, groundY + 1, centerZ, 3, 2.0, 1.0, 2.0, 0.0);
               sl.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, centerX, groundY + 1, centerZ, 40, 4.0, 2.0, 4.0, 0.05);
            }
         } else {
            this.getWorld().playSound(null, centerX, (double)groundY, centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.5F);
         }
      }
   }

   private void flingBlockFromSlam(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
      this.getWorld().removeBlock(pos, false);
      double flingMult = this.getAttackNumber() == 5 ? 2.5 : 1.0;
      double velX = (forwardX * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.5) * flingMult;
      double velY = (0.5 + this.random.nextDouble() * 0.8) * flingMult;
      double velZ = (forwardZ * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.5) * flingMult;
      FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
      fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fallingBlock.setFallingBlockPos(pos);
      fallingBlock.setVelocity(velX, velY, velZ);
      fallingBlock.timeFalling = 1;
      fallingBlock.dropItem = false;
      serverLevel.spawnEntity(fallingBlock);
   }

   private void breakBlocksAlongKnockback(double forwardX, double forwardZ) {
      if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
         boolean server = this.getWorld() instanceof ServerWorld;
         ServerWorld serverLevel = server ? (ServerWorld)this.getWorld() : null;
         double rightX = forwardZ;
         double rightZ = -forwardX;
         double halfWidth = this.getWidth() / 2.0 + 3.0;
         int height = (int)Math.ceil(this.getHeight());
         int baseY = (int)Math.floor(this.getY());
         boolean playedSound = false;

         for (int depth = 1; depth <= 6; depth++) {
            double centerX = this.getX() + forwardX * depth;
            double centerZ = this.getZ() + forwardZ * depth;

            for (double w = -halfWidth; w <= halfWidth; w++) {
               int wx = (int)Math.floor(centerX + rightX * w);
               int wz = (int)Math.floor(centerZ + rightZ * w);

               for (int dy = 0; dy < height; dy++) {
                  BlockPos pos = new BlockPos(wx, baseY + dy, wz);
                  BlockState blockState = this.getWorld().getBlockState(pos);
                  if (!blockState.isAir()
                     && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                     && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                     && (!(blockState.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))
                     && !blockState.isIn(BlockTags.REPLACEABLE)) {
                     if (server) {
                        if (!playedSound && dy >= 1) {
                           serverLevel.playSound(null, pos, blockState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 2.0F, 0.5F);
                           playedSound = true;
                        }

                        if (this.random.nextFloat() < 0.18F) {
                           this.flingBlockFromSlam(serverLevel, pos, blockState, forwardX, forwardZ);
                        } else {
                           this.getWorld().removeBlock(pos, false);
                        }
                     } else {
                        this.getWorld().removeBlock(pos, false);
                     }
                  }
               }
            }
         }
      }
   }

   private void destroyBlocksAlongSwing(float swingProgress) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            float yawRad = (float)Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double rightX = forwardZ;
            double rightZ = -forwardX;
            double sideOffset = (1.0 - swingProgress) * 4.0;
            double forwardDist = 2.5 + swingProgress * 6.0;
            double crouchOffset = this.isInSneakingPose() ? 4.5 : 0.0;
            double armHeight = 9.0 - crouchOffset - swingProgress * 3.5;
            int baseY = (int)Math.floor(this.getY() + armHeight);
            int handRadius = 2;
            int forearmRadius = 3;
            double[][] armPoints = new double[][]{{forwardDist * 0.5, sideOffset * 0.5}, {forwardDist, sideOffset}};
            int[] radii = new int[]{forearmRadius, handRadius};

            for (int i = 0; i < armPoints.length; i++) {
               double[] point = armPoints[i];
               int radius = radii[i];
               double centerX = this.getX() + forwardX * point[0] + rightX * point[1];
               double centerZ = this.getZ() + forwardZ * point[0] + rightZ * point[1];

               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     for (int dy = -1; dy <= 3; dy++) {
                        BlockPos pos = new BlockPos((int)Math.floor(centerX + dx), baseY + dy, (int)Math.floor(centerZ + dz));
                        BlockState blockState = this.getWorld().getBlockState(pos);
                        if (!blockState.isAir()
                           && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                           && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                           && (!(blockState.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                           if (this.random.nextFloat() < 0.7F) {
                              this.flingBlockFromSwing(serverLevel, pos, blockState, forwardX, forwardZ);
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
      }
   }

   private void flingBlockFromSwing(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
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

   private void performHammerBoneDamage() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (this.hasHammerBoneSync()) {
            double hx = this.hammerBoneX;
            double hy = this.hammerBoneY;
            double hz = this.hammerBoneZ;
            double hammerDamageRadius = 3.0;
            float baseDamage = (float)ModConfig.get().attackTitanAttackDamage;
            baseDamage *= this.hammerMeleeMultiplier();
            if (this.getAttackNumber() == 1 || this.getAttackNumber() == 16) {
               baseDamage *= 1.5F;
            }

            UUID shifterUUID = this.getShifterUUID();

            for (LivingEntity target : this.getWorld()
               .getNonSpectatingEntities(
                  LivingEntity.class,
                  new Box(
                     hx - hammerDamageRadius,
                     hy - hammerDamageRadius,
                     hz - hammerDamageRadius,
                     hx + hammerDamageRadius,
                     hy + hammerDamageRadius,
                     hz + hammerDamageRadius
                  )
               )) {
               if (target != this
                  && target.getVehicle() != this
                  && (shifterUUID == null || !target.getUuid().equals(shifterUUID))
                  && !(target instanceof WarhammerTitanNapeEntity)
                  && !(target instanceof WarhammerTitanEyeEntity)
                  && !(target instanceof FemaleTitanNapeEntity)
                  && !(target instanceof FemaleTitanEyeEntity)
                  && !(target instanceof ArmoredTitanNapeEntity)
                  && !(target instanceof ArmoredTitanEyeEntity)
                  && !(target instanceof ColossalTitanNapeEntity)
                  && !(target instanceof ColossalTitanEyeEntity)
                  && !(target instanceof AttackTitanNapeEntity)
                  && !(target instanceof AttackTitanEyeEntity)
                  && !(target instanceof BeastTitanNapeEntity)
                  && !(target instanceof BeastTitanEyeEntity)
                  && !(
                     target instanceof PlayerEntity p
                        && (
                           p.getVehicle() instanceof AttackTitanEntity
                              || p.getVehicle() instanceof FemaleTitanEntity
                              || p.getVehicle() instanceof ArmoredTitanEntity
                              || p.getVehicle() instanceof ColossalTitanEntity
                              || p.getVehicle() instanceof BeastTitanEntity
                              || p.getVehicle() instanceof WarhammerTitanEntity
                        )
                  )) {
                  double dist = target.getPos().distanceTo(new Vec3d(hx, hy, hz));
                  if (!(dist > hammerDamageRadius) && target.timeUntilRegen <= 0) {
                     if (target instanceof TitanNapeEntity napeEntity) {
                        TitanEntity parentTitan = napeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else if (target instanceof SmallTitanNapeEntity smallNapeEntity) {
                        SmallTitanEntity parentTitan = smallNapeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        }
                     } else if (target instanceof SmallTitan2NapeEntity smallNape2Entity) {
                        SmallTitan2Entity parentTitan = smallNape2Entity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        }
                     } else {
                        float attackDamage = isShifterTitan(target) ? baseDamage / 3.0F : baseDamage;
                        target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, attackDamage));
                        this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                        this.spawnShifterHitParticles(target);
                        Vec3d knockDir = target.getPos().subtract(hx, hy, hz).normalize();
                        if (target instanceof TitanEntity titanTarget) {
                           titanTarget.applyKnockback(knockDir.multiply(4.0, 1.5, 4.0));
                        } else if (!(target instanceof SmallTitanEntity) && !(target instanceof SmallTitan2Entity)) {
                           target.setVelocity(target.getVelocity().add(knockDir.multiply(1.5, 0.5, 1.5)));
                        } else {
                           target.setVelocity(knockDir.multiply(2.5, 1.0, 2.5));
                           target.velocityModified = true;
                        }
                     }
                  }
               }
            }

            if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
               int radius = 2;
               int baseX = (int)Math.floor(hx);
               int baseY = (int)Math.floor(hy);
               int baseZ = (int)Math.floor(hz);
               float yawRad = (float)Math.toRadians(this.getYaw());
               double forwardX = -Math.sin(yawRad);
               double forwardZ = Math.cos(yawRad);

               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dy = -radius; dy <= radius; dy++) {
                     for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        BlockState blockState = this.getWorld().getBlockState(pos);
                        if (!blockState.isAir()
                           && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                           && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                           && (!(blockState.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                           if (this.random.nextFloat() < 0.6F) {
                              this.flingBlockFromSwing(serverLevel, pos, blockState, forwardX, forwardZ);
                           } else {
                              serverLevel.spawnParticles(
                                 new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                                 pos.getX() + 0.5,
                                 pos.getY() + 0.5,
                                 pos.getZ() + 0.5,
                                 8,
                                 0.4,
                                 0.4,
                                 0.4,
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
      }
   }

   private void performPiercingThorns() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         int var27 = (int)Math.floor(this.getY());
         double centerX = this.getX();
         double centerZ = this.getZ();
         float baseDamage = (float)ModConfig.get().attackTitanAttackDamage;

         for (int rider = 0; rider < 10; rider++) {
            double angle = (Math.PI * 2) * rider / 10.0 + (this.random.nextDouble() - 0.5) * 0.6;
            double radius = 5.0 + this.random.nextDouble() * 20.0;
            double spikeX = centerX + Math.cos(angle) * radius;
            double spikeZ = centerZ + Math.sin(angle) * radius;
            int surfaceY = var27;
            boolean foundSurface = false;

            for (int dy = 10; dy >= -10; dy--) {
               BlockPos check = new BlockPos((int)Math.floor(spikeX), var27 + dy, (int)Math.floor(spikeZ));
               BlockState checkState = this.getWorld().getBlockState(check);
               BlockState aboveState = this.getWorld().getBlockState(check.up());
               if (checkState.isSolid() && !aboveState.isSolid()) {
                  surfaceY = var27 + dy + 1;
                  foundSurface = true;
                  break;
               }
            }

            if (foundSurface) {
               float tiltX = (this.random.nextFloat() - 0.5F) * 30.0F;
               float tiltZ = (this.random.nextFloat() - 0.5F) * 30.0F;
               float height = 9.0F + this.random.nextFloat() * 12.0F;
               float width = 1.8F + this.random.nextFloat() * 2.4F;
               double minRadius = 5.0;
               int spawnDelay = (int)((radius - minRadius) / (25.0 - minRadius) * 15.0) + this.random.nextInt(4);
               WarhammerSpikeEntity spike = new WarhammerSpikeEntity(DannysAot.WARHAMMER_SPIKE, serverLevel);
               spike.setPosition(spikeX, surfaceY, spikeZ);
               spike.setSpikeProperties(tiltX, tiltZ, height, width);
               spike.setSpawnDelay(spawnDelay);
               spike.setOwnerUUID(this.getShifterUUID());
               serverLevel.spawnEntity(spike);
            }
         }

         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(25.0, 6.0, 25.0))) {
            if (target != this && target.getVehicle() != this) {
               UUID shifterUUID = this.getShifterUUID();
               if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
                  && !(target instanceof WarhammerTitanNapeEntity)
                  && !(target instanceof WarhammerTitanEyeEntity)
                  && !(target instanceof FemaleTitanNapeEntity)
                  && !(target instanceof FemaleTitanEyeEntity)
                  && !(target instanceof ArmoredTitanNapeEntity)
                  && !(target instanceof ArmoredTitanEyeEntity)
                  && !(target instanceof ColossalTitanNapeEntity)
                  && !(target instanceof ColossalTitanEyeEntity)
                  && !(target instanceof AttackTitanNapeEntity)
                  && !(target instanceof AttackTitanEyeEntity)
                  && !(target instanceof BeastTitanNapeEntity)
                  && !(target instanceof BeastTitanEyeEntity)
                  && !(
                     target instanceof PlayerEntity p
                        && (
                           p.getVehicle() instanceof AttackTitanEntity
                              || p.getVehicle() instanceof FemaleTitanEntity
                              || p.getVehicle() instanceof ArmoredTitanEntity
                              || p.getVehicle() instanceof ColossalTitanEntity
                              || p.getVehicle() instanceof BeastTitanEntity
                              || p.getVehicle() instanceof WarhammerTitanEntity
                        )
                  )) {
                  double dist = this.distanceTo(target);
                  if (!(dist > 25.0)) {
                     if (target instanceof TitanNapeEntity napeEntity) {
                        TitanEntity parentTitan = napeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else if (target instanceof SmallTitanNapeEntity smallNapeEntity) {
                        SmallTitanEntity parentTitan = smallNapeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else if (target instanceof SmallTitan2NapeEntity smallNape2Entity) {
                        SmallTitan2Entity parentTitan = smallNape2Entity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else if (target instanceof FritzTitanNapeEntity fritzNapeEntity) {
                        FritzTitanEntity parentTitan = fritzNapeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else {
                        float damage = isShifterTitan(target) ? baseDamage * 2.0F / 3.0F : baseDamage;
                        target.damage(this.getDamageSources().mobAttack(this), damage);
                        this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                        this.spawnShifterHitParticles(target);
                        Vec3d toTarget = target.getPos().subtract(this.getPos());
                        Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                        if (target instanceof TitanEntity titanTarget) {
                           titanTarget.applyKnockback(horizontalDir.multiply(2.0).add(0.0, 3.0, 0.0));
                        } else if (isShifterTitan(target)) {
                           Vec3d knockback = horizontalDir.multiply(1.5).add(0.0, 2.0, 0.0);
                           if (target instanceof AttackTitanEntity at) {
                              at.setPendingKnockback(knockback);
                              at.applyHitSlow(20);
                           } else if (target instanceof FemaleTitanEntity ft) {
                              ft.setPendingKnockback(knockback);
                              ft.applyHitSlow(20);
                           } else if (target instanceof ArmoredTitanEntity art) {
                              art.setPendingKnockback(knockback);
                              art.applyHitSlow(20);
                           } else if (target instanceof ColossalTitanEntity ct) {
                              ct.setPendingKnockback(knockback);
                              ct.applyHitSlow(20);
                           } else if (target instanceof WarhammerTitanEntity wh) {
                              wh.setPendingKnockback(knockback);
                              wh.applyHitSlow(20);
                           }
                        } else {
                           target.setVelocity(target.getVelocity().add(horizontalDir.multiply(1.0).add(0.0, 0.8, 0.0)));
                           if (target instanceof LivingEntity) {
                              target.velocityModified = true;
                           }
                        }
                     }
                  }
               }
            }
         }

         if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
            ModNetworking.drainStamina(sp.getUuid(), 350.0F);
         }
      }
   }

   private void performSpikeField() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         int var27 = (int)Math.floor(this.getY());
         double centerX = this.getX();
         double centerZ = this.getZ();

         for (int rider = 0; rider < 90; rider++) {
            double angle = (Math.PI * 2) * rider / 90.0;
            double ringFraction = (rider % 5 + 1) / 5.0;
            double radius = 4.0 + ringFraction * 26.0;
            double spikeX = centerX + Math.cos(angle) * radius;
            double spikeZ = centerZ + Math.sin(angle) * radius;
            int searchY = var27;
            int surfaceY = var27;
            boolean foundSurface = false;

            for (int dy = 10; dy >= -10; dy--) {
               BlockPos check = new BlockPos((int)Math.floor(spikeX), searchY + dy, (int)Math.floor(spikeZ));
               BlockState checkState = this.getWorld().getBlockState(check);
               BlockState aboveState = this.getWorld().getBlockState(check.up());
               if (checkState.isSolid() && !aboveState.isSolid()) {
                  surfaceY = searchY + dy + 1;
                  foundSurface = true;
                  break;
               }
            }

            if (foundSurface) {
               float height = 1.0F;
               float width = 0.32F;
               double minRadius = 4.0;
               int spawnDelay = (int)((radius - minRadius) / (30.0 - minRadius) * 15.0) + this.random.nextInt(3);
               WarhammerSpikeEntity spike = new WarhammerSpikeEntity(DannysAot.WARHAMMER_SPIKE, serverLevel);
               spike.setPosition(spikeX, surfaceY + 1, spikeZ);
               spike.setSpikeProperties(0.0F, 0.0F, height, width);
               spike.setSpawnDelay(spawnDelay);
               spike.setOwnerUUID(this.getShifterUUID());
               spike.setSpikeField(true);
               spike.setCustomLifetime(960);
               serverLevel.spawnEntity(spike);
            }
         }

         if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
            ModNetworking.drainStamina(sp.getUuid(), 300.0F);
         }
      }
   }

   private void performImpale() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double spikeX = this.getX() + forwardX * 10.0;
         double spikeZ = this.getZ() + forwardZ * 10.0;
         int spikeY = (int)Math.floor(this.getY()) - 1;
         Box impaleBox = new Box(spikeX - 6.0, spikeY, spikeZ - 6.0, spikeX + 6.0, spikeY + 53, spikeZ + 6.0);
         LivingEntity impaleTarget = null;
         double closestDist = Double.MAX_VALUE;

         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, impaleBox)) {
            if (target != this && target.getVehicle() != this) {
               UUID shifterUUID = this.getShifterUUID();
               if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
                  && !(target instanceof WarhammerTitanNapeEntity)
                  && !(target instanceof WarhammerTitanEyeEntity)
                  && !(target instanceof AttackTitanNapeEntity)
                  && !(target instanceof AttackTitanEyeEntity)
                  && !(target instanceof ArmoredTitanNapeEntity)
                  && !(target instanceof ArmoredTitanEyeEntity)
                  && !(target instanceof ColossalTitanNapeEntity)
                  && !(target instanceof ColossalTitanEyeEntity)
                  && !(target instanceof FemaleTitanNapeEntity)
                  && !(target instanceof FemaleTitanEyeEntity)
                  && !(target instanceof BeastTitanNapeEntity)
                  && !(target instanceof BeastTitanEyeEntity)
                  && !(target instanceof TitanNapeEntity)
                  && !(target instanceof TitanEyeEntity)
                  && !(target instanceof SmallTitanNapeEntity)
                  && !(target instanceof SmallTitanEyeEntity)
                  && !(target instanceof SmallTitan2NapeEntity)
                  && !(target instanceof SmallTitan2EyeEntity)
                  && !(target instanceof FritzTitanNapeEntity)
                  && !(target instanceof FritzTitanEyeEntity)
                  && !(target instanceof ArmoredTitanEntity ctAr && ctAr.isConsciousnessTransferActive())) {
                  double dist = target.squaredDistanceTo(spikeX, spikeY, spikeZ);
                  if (dist < closestDist) {
                     closestDist = dist;
                     impaleTarget = target;
                  }
               }
            }
         }

         WarhammerSpikeEntity spike = new WarhammerSpikeEntity(DannysAot.WARHAMMER_SPIKE, serverLevel);
         spike.setPosition(spikeX, spikeY, spikeZ);
         spike.setSpikeProperties(0.0F, 0.0F, 53.0F, 12.0F);
         spike.setSpawnDelay(0);
         spike.setOwnerUUID(this.getShifterUUID());
         spike.setImpaleSpike(true);
         if (impaleTarget != null) {
            spike.setImpaleTargetId(impaleTarget.getId());
            if (impaleTarget instanceof AttackTitanEntity at) {
               at.setImpaled(true);
               at.setDismounting(false);
            } else if (impaleTarget instanceof ArmoredTitanEntity ar) {
               ar.setImpaled(true);
               ar.setDismounting(false);
            } else if (impaleTarget instanceof FemaleTitanEntity ft) {
               ft.setImpaled(true);
               ft.setDismounting(false);
            } else if (impaleTarget instanceof BeastTitanEntity bt) {
               bt.setImpaled(true);
               bt.setDismounting(false);
            } else if (impaleTarget instanceof ColossalTitanEntity ct) {
               ct.setDismounting(false);
            } else if (impaleTarget instanceof WarhammerTitanEntity wh) {
               wh.setDismounting(false);
            }

            impaleTarget.setVelocity(0.0, 0.0, 0.0);
            impaleTarget.velocityModified = true;
         }

         serverLevel.spawnEntity(spike);
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            int craterRadius = 7;
            int craterDepth = 3;

            for (int dx = -craterRadius; dx <= craterRadius; dx++) {
               for (int dz = -craterRadius; dz <= craterRadius; dz++) {
                  if (dx * dx + dz * dz <= craterRadius * craterRadius) {
                     for (int dy = 0; dy >= -craterDepth; dy--) {
                        BlockPos pos = new BlockPos((int)Math.floor(spikeX) + dx, spikeY + dy, (int)Math.floor(spikeZ) + dz);
                        BlockState state = this.getWorld().getBlockState(pos);
                        if (!state.isAir()
                           && !(state.getHardness(this.getWorld(), pos) < 0.0F)
                           && !state.isIn(BlockTags.WITHER_IMMUNE)
                           && !(state.getBlock() instanceof FluidBlock)) {
                           double dirX = dx == 0 ? (this.random.nextDouble() - 0.5) * 2.0 : dx;
                           double dirZ = dz == 0 ? (this.random.nextDouble() - 0.5) * 2.0 : dz;
                           this.getWorld().removeBlock(pos, false);
                           if (this.random.nextFloat() < 0.4F) {
                              double velX = dirX * 0.8 + (this.random.nextDouble() - 0.5) * 0.3;
                              double velY = 0.8 + this.random.nextDouble() * 1.2;
                              double velZ = dirZ * 0.8 + (this.random.nextDouble() - 0.5) * 0.3;
                              FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                              ((FallingBlockEntityAccessor)fb).setBlockState(state);
                              fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                              fb.setFallingBlockPos(pos);
                              fb.setVelocity(velX, velY, velZ);
                              fb.timeFalling = 1;
                              fb.dropItem = false;
                              serverLevel.spawnEntity(fb);
                           }
                        }
                     }
                  }
               }
            }
         }

         this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 8.0F, 0.4F);
         this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 10.0F, 0.5F);
         this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 10.0F, 0.3F);
         this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, ModSounds.FLESH_IMPACT_4, SoundCategory.HOSTILE, 10.0F, 0.4F);
         this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, ModSounds.FLESH_IMPACT_5, SoundCategory.HOSTILE, 10.0F, 0.5F);
         if (impaleTarget != null) {
            float impaleDamage;
            if (isShifterTitan(impaleTarget)) {
               impaleDamage = impaleTarget.getMaxHealth() * 0.167F;
            } else {
               impaleDamage = impaleTarget.getMaxHealth() * 5.0F;
            }

            impaleTarget.damage(this.getDamageSources().mobAttack(this), impaleDamage);
            BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
            serverLevel.spawnParticles(
               bloodParticle, impaleTarget.getX(), impaleTarget.getY() + impaleTarget.getHeight() * 0.5, impaleTarget.getZ(), 60, 1.0, 1.0, 1.0, 0.2
            );
            this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, ModSounds.FLESH_IMPACT_4, SoundCategory.HOSTILE, 8.0F, 0.5F);
            this.getWorld().playSound(null, spikeX, (double)spikeY, spikeZ, ModSounds.FLESH_IMPACT_5, SoundCategory.HOSTILE, 8.0F, 0.6F);
         }

         if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
            ModNetworking.drainStamina(sp.getUuid(), 500.0F);
         }
      }
   }

   private static boolean isShifterTitan(LivingEntity target) {
      return target instanceof AttackTitanEntity
         || target instanceof FemaleTitanEntity
         || target instanceof ArmoredTitanEntity
         || target instanceof ColossalTitanEntity
         || target instanceof BeastTitanEntity
         || target instanceof WarhammerTitanEntity;
   }

   private boolean checkArmedBlock(LivingEntity target) {
      if (target instanceof AttackTitanEntity at && at.isArmed()) {
         float targetYaw = (float)Math.toRadians(at.getYaw());
         Vec3d targetFwd = new Vec3d(-Math.sin(targetYaw), 0.0, Math.cos(targetYaw));
         Vec3d attackDir = new Vec3d(this.getX() - at.getX(), 0.0, this.getZ() - at.getZ()).normalize();
         return attackDir.dotProduct(targetFwd) > 0.0;
      } else if (target instanceof FemaleTitanEntity ft && ft.isArmed()) {
         float targetYaw = (float)Math.toRadians(ft.getYaw());
         Vec3d targetFwd = new Vec3d(-Math.sin(targetYaw), 0.0, Math.cos(targetYaw));
         Vec3d attackDir = new Vec3d(this.getX() - ft.getX(), 0.0, this.getZ() - ft.getZ()).normalize();
         return attackDir.dotProduct(targetFwd) > 0.0;
      } else if (target instanceof ArmoredTitanEntity art && art.isArmed()) {
         float targetYaw = (float)Math.toRadians(art.getYaw());
         Vec3d targetFwd = new Vec3d(-Math.sin(targetYaw), 0.0, Math.cos(targetYaw));
         Vec3d attackDir = new Vec3d(this.getX() - art.getX(), 0.0, this.getZ() - art.getZ()).normalize();
         return attackDir.dotProduct(targetFwd) > 0.0;
      } else if (target instanceof WarhammerTitanEntity wh && wh.isArmed()) {
         float targetYaw = (float)Math.toRadians(wh.getYaw());
         Vec3d targetFwd = new Vec3d(-Math.sin(targetYaw), 0.0, Math.cos(targetYaw));
         Vec3d attackDir = new Vec3d(this.getX() - wh.getX(), 0.0, this.getZ() - wh.getZ()).normalize();
         return attackDir.dotProduct(targetFwd) > 0.0;
      } else {
         return false;
      }
   }

   private void spawnShifterHitParticles(LivingEntity target) {
      if (isShifterTitan(target)) {
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            double var10 = target.getX() + (this.getX() - target.getX()) * 0.2;
            double impactY = target.getY() + target.getHeight() * 0.5;
            double impactZ = target.getZ() + (this.getZ() - target.getZ()) * 0.2;
            BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
            serverLevel.spawnParticles(bloodParticle, var10, impactY, impactZ, 30, 0.5, 0.5, 0.5, 0.1);
         }
      }
   }

   private void playFleshImpactSound(double x, double y, double z) {
      if (!this.getWorld().isClient()) {
         SoundEvent[] sounds = new SoundEvent[]{
            ModSounds.FLESH_IMPACT_1,
            ModSounds.FLESH_IMPACT_2,
            ModSounds.FLESH_IMPACT_3,
            ModSounds.FLESH_IMPACT_4,
            ModSounds.FLESH_IMPACT_5,
            ModSounds.FLESH_IMPACT_6,
            ModSounds.FLESH_IMPACT_7
         };

         int index;
         do {
            index = this.random.nextInt(7);
         } while (index == this.lastFleshImpactIndex);

         this.lastFleshImpactIndex = index;
         float pitch = 0.8F + this.random.nextFloat() * 0.1F;
         this.getWorld().playSound(null, x, y, z, sounds[index], SoundCategory.HOSTILE, 8.0F, pitch);
      }
   }

   @Override
   public void travel(Vec3d movementInput) {
      boolean activeJump = this.jumpAnimTicks > 0 || (this.getWorld().isClient() ? this.clientInJumpArc : this.isJumping()) && !this.isOnGround();
      if (activeJump) {
         if (this.jumpLaunched && !this.jumpVelocityApplied) {
            this.setVelocity(this.jumpVelX, this.jumpVelY, this.jumpVelZ);
            this.jumpVelocityApplied = true;
            this.jumpVelX = 0.0;
            this.jumpVelY = 0.0;
            this.jumpVelZ = 0.0;
         }

         if (this.getControllingPassenger() instanceof PlayerEntity ridingPlayer && this.jumpLaunched) {
            float forward = ridingPlayer.forwardSpeed;
            float strafe = ridingPlayer.sidewaysSpeed;
            if (Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F) {
               float cameraYaw = ridingPlayer.getYaw();
               float sinYaw = MathHelper.sin(cameraYaw * (float) (Math.PI / 180.0));
               float cosYaw = MathHelper.cos(cameraYaw * (float) (Math.PI / 180.0));
               double airX = strafe * cosYaw - forward * sinYaw;
               double airZ = forward * cosYaw + strafe * sinYaw;
               double airLen = Math.sqrt(airX * airX + airZ * airZ);
               if (airLen > 0.001) {
                  double airControl = 0.04;
                  Vec3d current = this.getVelocity();
                  this.setVelocity(current.x + airX / airLen * airControl, current.y, current.z + airZ / airLen * airControl);
               }
            }
         }

         double gravity = this.getAttributeValue(daot.compat.attributes.DaotEntityAttributes.GRAVITY);
         Vec3d vel = this.getVelocity();
         this.setVelocity(vel.x * 0.91, (vel.y - gravity) * 0.98, vel.z * 0.91);
         this.move(MovementType.SELF, this.getVelocity());
         this.enforceCrystalTether();
      } else {
         this.jumpVelocityApplied = false;
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

         this.enforceCrystalTether();
      }
   }

   private void enforceCrystalTether() {
      Vec3d crystalPos = this.getCrystalShellPosition();
      if (crystalPos != null) {
         double dx = this.getX() - crystalPos.x;
         double dz = this.getZ() - crystalPos.z;
         double distSq = dx * dx + dz * dz;
         if (distSq > 22500.0) {
            double dist = Math.sqrt(distSq);
            double scale = 150.0 / dist;
            double clampedX = crystalPos.x + dx * scale;
            double clampedZ = crystalPos.z + dz * scale;
            this.setPosition(clampedX, this.getY(), clampedZ);
            Vec3d vel = this.getVelocity();
            double velDot = vel.x * dx + vel.z * dz;
            if (velDot > 0.0) {
               this.setVelocity(vel.x - velDot / distSq * dx, vel.y, vel.z - velDot / distSq * dz);
            }
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
   }

   @Override
   protected void pushAway(Entity entity) {
   }

   @Override
   public void pushAwayFrom(Entity entity) {
   }

   @Override
   public void addVelocity(double deltaX, double deltaY, double deltaZ) {
   }

   @Override
   public boolean isCollidable() {
      return true;
   }

   @Override
   public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
      if (!this.getWorld().isClient() && fallDistance > 3.0F) {
         this.setFalling(false);
         this.setLanding(true);
         this.landingAnimTicks = 14;
         this.jumpCooldownTicks = 10;
         float impactIntensity = Math.min(Math.max(fallDistance - 20.0F, 0.0F) / 10.0F, 5.0F);
         if (fallDistance >= 20.0F) {
            this.performLandingShockwave(fallDistance);
         }

         this.setLandingIntensity(impactIntensity);
         this.setLastLandingTick(this.age);
         this.setLastAttackImpactTick(this.age);
      }

      return false;
   }

   @Override
   public boolean isFireImmune() {
      return true;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      Entity attacker = source.getAttacker();
      if (attacker != null) {
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null && attacker.getUuid().equals(shifterUUID)) {
            return false;
         }

         if (attacker.getVehicle() == this) {
            return false;
         }
      }

      if (source.isIn(DamageTypeTags.IS_FIRE)) {
         return false;
      } else if (source.isIn(DamageTypeTags.IS_EXPLOSION) && !(source.getSource() instanceof ThunderSpearEntity)) {
         return false;
      } else if (attacker instanceof LivingEntity && ShifterDodgeManager.isTitanInDodgeIFrames(this)) {
         return false;
      } else if (attacker instanceof AttackTitanEntity
         || attacker instanceof ArmoredTitanEntity
         || attacker instanceof ColossalTitanEntity
         || attacker instanceof FemaleTitanEntity
         || attacker instanceof BeastTitanEntity
         || attacker instanceof WarhammerTitanEntity
         || attacker instanceof OgreTitanEntity) {
         float newHealth = this.getHealth() - amount;
         this.setHealth(Math.max(newHealth, 0.0F));
         this.setLastHitTick(this.age);
         float hitYaw = (float)Math.toDegrees(Math.atan2(this.getZ() - attacker.getZ(), this.getX() - attacker.getX()));
         this.dataTracker.set(DATA_HIT_DIR_YAW, hitYaw);
         if (this.getHealth() <= 0.0F) {
            this.onDeath(source);
         }

         return false;
      } else if (attacker instanceof FritzTitanEntity
         || attacker instanceof TitanEntity
         || attacker instanceof SmallTitanEntity
         || attacker instanceof SmallTitan2Entity
         || attacker instanceof YellowTitanEntity
         || attacker instanceof SadTitanEntity
         || attacker instanceof ConnieFatherEntity
         || attacker instanceof CrawlerTitanEntity) {
         float pureDamage = amount * 0.5F;
         float newHealth = this.getHealth() - pureDamage;
         this.setHealth(Math.max(newHealth, 0.0F));
         this.setLastHitTick(this.age);
         float hitYaw = (float)Math.toDegrees(Math.atan2(this.getZ() - attacker.getZ(), this.getX() - attacker.getX()));
         this.dataTracker.set(DATA_HIT_DIR_YAW, hitYaw);
         if (this.getHealth() <= 0.0F) {
            this.onDeath(source);
         }

         return false;
      } else {
         return attacker instanceof LivingEntity ? false : super.damage(source, amount);
      }
   }

   public void hurtFromNape(DamageSource source, float amount) {
      if (!this.getWorld().isClient() && !this.isDefeated()) {
         if (this.isDismounting()) {
            this.onDeath(source);
         } else {
            float chargeFraction = BladeAttackTracker.consumeNapeChargeFraction();
            if (!(chargeFraction <= 0.0F)) {
               float perHitDamage = this.getMaxHealth() / Math.max(1, ModConfig.get().warhammerTitanNapeHits);
               float safeDamage = Math.max(0.0F, Math.min(perHitDamage * chargeFraction, this.getHealth() - 1.0F));
               super.damage(source, safeDamage);
               if (this.getHealth() <= 1.01F) {
                  this.incapacitate();
               }
            }
         }
      }
   }

   public void triggerBlindness() {
      this.blindnessTicks = 120;
      DannysAot.LOGGER.info("Warhammer Titan blinded for 6 seconds!");
      if (this.getControllingPassenger() instanceof PlayerEntity player) {
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0, false, false, true));
         player.sendMessage(Text.literal("You have been blinded!"), true);
      }
   }

   public boolean isBlinded() {
      return this.blindnessTicks > 0;
   }

   public void applyHitSlow(int ticks) {
      this.hitSlowTicks = ticks;
   }

   public void setPendingKnockback(Vec3d knockback) {
      this.slideVelocity = knockback;
      this.slideTicks = 8;
      this.lockedYaw = this.getYaw();
      this.dataTracker.set(DATA_SLIDING, true);
   }

   public boolean isSliding() {
      return this.dataTracker.get(DATA_SLIDING);
   }

   public void triggerHitReaction(Entity attacker, boolean forceFront, boolean forceBack) {
      if (attacker != null) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double rightX = Math.cos(yawRad);
         double rightZ = Math.sin(yawRad);
         double dx = attacker.getX() - this.getX();
         double dz = attacker.getZ() - this.getZ();
         double dotForward = dx * forwardX + dz * forwardZ;
         double dotRight = dx * rightX + dz * rightZ;
         boolean isFront = forceFront || !forceBack && dotForward >= 0.0;
         boolean isRight = dotRight < 0.0;
         int reaction;
         if (isFront && !isRight) {
            reaction = 1;
         } else if (isFront) {
            reaction = 2;
         } else if (!isRight) {
            reaction = 3;
         } else {
            reaction = 4;
         }

         this.dataTracker.set(DATA_HIT_REACTION, reaction);
         this.hitReactionTicks = 10;
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      NapeSmokeHelper.cleanup(this.getId());
      if (this.bossBar != null) {
         this.bossBar.clearPlayers();
         this.bossBar = null;
      }

      if (this.napeEntity != null && !this.napeEntity.isRemoved()) {
         this.napeEntity.discard();
      }

      if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
         this.eyeEntity.discard();
      }

      super.remove(reason);
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient() && !this.configHealthApplied) {
         this.configHealthApplied = true;
         double configHealth = ModConfig.get().attackTitanHealth;
         this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(configHealth);
         this.setHealth((float)configHealth);
      }

      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         this.spawnHitboxes();
      }

      int transformTicks = this.getTransformationTicks();
      if (transformTicks > 0) {
         this.setTransformationTicks(transformTicks - 1);
         if (transformTicks - 1 <= 0 && this.hasNoGravity()) {
            this.setNoGravity(false);
         }
      }

      if (this.crouchTransitionTicksRemaining > 0) {
         this.crouchTransitionTicksRemaining--;
      }

      boolean currentlyCrouching = this.isInSneakingPose();
      if (currentlyCrouching != this.wasCrouchingLastTick) {
         this.calculateDimensions();
         if (!this.getWorld().isClient()) {
            this.getWorld()
               .playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.HOSTILE, 3.0F, 0.5F);
         }

         this.wasCrouchingLastTick = currentlyCrouching;
         this.crouchTransitionTicksRemaining = 6;
      }

      boolean currentlyDismounting = this.isDismounting();
      if (currentlyDismounting != this.wasDismountingLastTick) {
         this.calculateDimensions();
         this.wasDismountingLastTick = currentlyDismounting;
      }

      if (this.dismountToggleCooldown > 0) {
         this.dismountToggleCooldown--;
      }

      if (this.blindnessTicks > 0) {
         this.blindnessTicks--;
      }

      if (this.hitSlowTicks > 0) {
         this.hitSlowTicks--;
      }

      if (this.hitReactionTicks > 0) {
         this.hitReactionTicks--;
         if (this.hitReactionTicks == 0) {
            this.dataTracker.set(DATA_HIT_REACTION, 0);
         }
      }

      if (!this.getWorld().isClient() && this.bossBar != null) {
         if (!this.defeated && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getHealth() + 0.05F, this.getMaxHealth()));
         }

         this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            for (ServerPlayerEntity serverPlayer : serverLevel.getPlayers()) {
               if (serverPlayer.squaredDistanceTo(this) < 16384.0) {
                  this.bossBar.addPlayer(serverPlayer);
               } else {
                  this.bossBar.removePlayer(serverPlayer);
               }
            }
         }
      }

      if (!this.getWorld().isClient() && this.defeated && this.despawnAtGameTime < 0L) {
         this.despawnAtGameTime = this.getWorld().getTime() + 1200L;
      }

      if (!this.getWorld().isClient() && this.despawnAtGameTime >= 0L) {
         if (this.getShifterUUID() != null) {
            this.despawnAtGameTime = -1L;
         } else if (this.getWorld().getTime() >= this.despawnAtGameTime) {
            this.removeCrystalShell();
            this.discard();
            return;
         }
      }

      if (!this.getWorld().isClient() && this.isIncapacitated() && !this.isDefeated() && this.incapacitateTicks > 0) {
         this.incapacitateTicks--;
         if (this.incapacitateTicks <= 0) {
            this.recover();
         }
      }

      if (!this.getWorld().isClient() && this.dismountVisibilityDelay > 0) {
         this.dismountVisibilityDelay--;
      }

      NapeSmokeHelper.tick(this, this.napeEntity, this.isDismounting());
      if (!this.getWorld().isClient() && this.getControllingPassenger() instanceof PlayerEntity player && player.isDead()) {
         player.setInvisible(false);
         this.allowDismount = true;
         player.stopRiding();
         this.allowDismount = false;
         this.setDismounting(false);
         this.setIncapacitated(false);
         this.incapacitateTicks = 0;
         this.setCrystalPeeking(false);
         this.removeCrystalShell();
         this.setShifterUUID(null);
      }

      if (this.jumpAnimTicks > 0) {
         this.jumpAnimTicks--;
         int ticksElapsed = 10 - this.jumpAnimTicks;
         if (ticksElapsed >= 4 && !this.jumpLaunched) {
            this.jumpLaunched = true;
            this.jumpVelX = this.jumpDirX * 3.6F;
            this.jumpVelY = 4.5;
            this.jumpVelZ = this.jumpDirZ * 3.6F;
            if (!this.getWorld().isClient()) {
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 6.0F, 0.7F);
               this.setLastStompTick(this.age);
            }
         }
      }

      if (!this.getWorld().isClient()) {
         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.piercingThornsCooldownTicks > 0) {
            this.piercingThornsCooldownTicks--;
         }

         if (this.spikeFieldCooldownTicks > 0) {
            this.spikeFieldCooldownTicks--;
         }

         if (this.impaleCooldownTicks > 0) {
            this.impaleCooldownTicks--;
         }

         if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
            if (this.attackAnimationTicks <= 0) {
               if (this.getAttackNumber() == 11) {
                  this.setHasHammer(true);
               } else if (this.getAttackNumber() == 13) {
                  this.setHasHammer(false);
               }

               this.setTitanAttacking(false);
            }
         }

         if (this.isTitanAttacking()) {
            int hammerAttackNum = this.getAttackNumber();
            if (hammerAttackNum == 4 && this.spinDamageTimer >= 8) {
               this.performHammerBoneDamage();
            }

            if ((hammerAttackNum == 1 || hammerAttackNum == 3) && this.attackEffectTimer >= 4) {
               this.performHammerBoneDamage();
            }
         }

         if (this.isTitanAttacking() && !this.attackEffectTriggered) {
            this.attackEffectTimer++;
            int attackNum = this.getAttackNumber();
            if (attackNum != 11 && attackNum != 13) {
               if (attackNum == 4) {
                  this.spinDamageTimer++;
                  if (this.spinDamageTimer >= 8 && (this.spinDamageTimer - 8) % 5 == 0) {
                     this.performSpinDamage();
                     this.setLastAttackImpactTick(this.age);
                  }

                  if (this.spinDamageTimer > 0 && this.spinDamageTimer % 5 == 0) {
                     this.getWorld()
                        .playSound(
                           null, this.getX(), this.getY(), this.getZ(), daot.compat.BackportEffects.WIND_BURST, SoundCategory.HOSTILE, 10.0F, 2.0F
                        );
                  }
               } else {
                  int effectTick = switch (attackNum) {
                     case 1 -> 12;
                     default -> 10;
                     case 3 -> 14;
                     case 5 -> 26;
                     case 6 -> 8;
                     case 7 -> 8;
                     case 8, 9, 10, 12 -> 7;
                  };
                  if ((attackNum == 6 || attackNum == 7) && this.attackEffectTimer >= 4) {
                     float swingProgress = (float)(this.attackEffectTimer - 4) / (effectTick - 4);
                     this.destroyBlocksAlongSwing(Math.min(swingProgress, 1.0F));
                  }

                  if (this.attackEffectTimer >= effectTick) {
                     if (attackNum == 8) {
                        this.performPiercingThorns();
                     } else if (attackNum == 9) {
                        this.performSpikeField();
                     } else if (attackNum == 10) {
                        this.performImpale();
                     } else if (attackNum == 12) {
                        this.dealAttackDamage();
                        this.performGroundSlam();
                        if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
                           ModNetworking.drainStamina(sp.getUuid(), 15.0F);
                        }
                     } else {
                        this.dealAttackDamage();
                     }

                     if (attackNum == 3 || attackNum == 5) {
                        this.performGroundSlam();
                     }

                     this.setLastAttackImpactTick(this.age);
                     this.attackEffectTriggered = true;
                  }
               }
            }
         }
      }

      if (!this.getWorld().isClient() && !this.isPlayerControlled()) {
         boolean isActuallyMoving = this.getVelocity().horizontalLengthSquared() > 0.001;
         this.setMoving(isActuallyMoving);
      }

      if (!this.getWorld().isClient() && this.isAlive() && !this.isDismounting()) {
         if (this.stompCooldown > 0) {
            this.stompCooldown--;
         }

         int stompAtkNum = this.getAttackNumber();
         boolean isMovementBlocked = this.isTitanAttacking()
            && (stompAtkNum == 3 || stompAtkNum == 5 || stompAtkNum == 8 || stompAtkNum == 9 || stompAtkNum == 10 || stompAtkNum == 12);
         isMovementBlocked = isMovementBlocked || this.isArmed() && this.hasHammer();
         isMovementBlocked = isMovementBlocked || this.isTransforming();
         boolean isActuallyMoving = !isMovementBlocked && (this.getVelocity().horizontalLengthSquared() > 0.001 || this.isMoving());
         if (!isMovementBlocked && this.getControllingPassenger() instanceof PlayerEntity controllingPlayer) {
            boolean hasInput = Math.abs(controllingPlayer.forwardSpeed) > 0.01 || Math.abs(controllingPlayer.sidewaysSpeed) > 0.01;
            isActuallyMoving = isActuallyMoving || hasInput;
         }

         if (isActuallyMoving && !this.wasMovingLastTick) {
            this.walkStartTick = this.age;
            this.lastStompKeyframeIndex = -1;
         }

         this.wasMovingLastTick = isActuallyMoving;
         if (isActuallyMoving && this.stompCooldown <= 0) {
            long walkingTicks = this.age - this.walkStartTick;
            if (walkingTicks >= 10L) {
               boolean isRunning = this.isSprinting() && (!this.isArmed() || !this.hasHammer());
               boolean hammerRun = isRunning && this.hasHammer();
               double ticksPerCycle;
               double[] keyframes;
               double cycleLength;
               int cooldown;
               if (isRunning && !hammerRun) {
                  ticksPerCycle = 14.584;
                  keyframes = new double[]{0.23, 0.665};
                  cycleLength = 0.7292;
                  cooldown = 5;
               } else if (hammerRun) {
                  ticksPerCycle = 20.0;
                  keyframes = new double[]{0.0, 0.5};
                  cycleLength = 1.0;
                  cooldown = 8;
               } else {
                  ticksPerCycle = 40.0;
                  keyframes = STOMP_KEYFRAMES;
                  cycleLength = 2.0;
                  cooldown = 12;
               }

               double currentAnimTime = walkingTicks % ticksPerCycle / 20.0;
               double cycleTime = currentAnimTime % cycleLength;
               int keyframeIndex = -1;

               for (int i = 0; i < keyframes.length; i++) {
                  if (Math.abs(cycleTime - keyframes[i]) < 0.15) {
                     keyframeIndex = i;
                     break;
                  }
               }

               if (keyframeIndex >= 0 && keyframeIndex != this.lastStompKeyframeIndex) {
                  this.triggerStompEffects();
                  this.setLastStompTick(this.age);
                  this.lastStompKeyframeIndex = keyframeIndex;
                  this.stompCooldown = cooldown;
               }

               if (keyframeIndex < 0) {
                  this.lastStompKeyframeIndex = -1;
               }
            }
         }
      }

      this.prevSmoothYOffset = this.smoothYOffset;
      double yBefore = this.getY();
      Vec3d velocityBefore = this.getVelocity();
      super.tick();
      if (this.slideTicks > 0 && this.slideVelocity != null && !this.getWorld().isClient()) {
         float progress = this.slideTicks / 8.0F;
         float factor = progress * progress;
         Vec3d frameVelocity = this.slideVelocity.multiply(factor * 0.35);
         this.setVelocity(frameVelocity.x, this.getVelocity().y, frameVelocity.z);
         this.velocityModified = true;
         if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
            sp.velocityModified = true;
         }

         if (!Float.isNaN(this.lockedYaw)) {
            this.setYaw(this.lockedYaw);
            this.bodyYaw = this.lockedYaw;
            this.headYaw = this.lockedYaw;
         }

         this.slideTicks--;
         if (this.slideTicks <= 0) {
            this.setVelocity(0.0, this.getVelocity().y, 0.0);
            this.velocityModified = true;
            this.slideVelocity = null;
            this.lockedYaw = Float.NaN;
            this.dataTracker.set(DATA_SLIDING, false);
         }
      }

      if (this.isKnocked()) {
         if (!this.wasKnocked) {
            this.knockedElapsedTicks = 0;
         }

         this.wasKnocked = true;
         this.knockedElapsedTicks++;
         boolean slidePhase = this.knockedElapsedTicks <= 30;
         float ky = this.getKnockedYaw();
         double kyRad = Math.toRadians(ky);
         double slideX = Math.sin(kyRad);
         double slideZ = -Math.cos(kyRad);
         if (slidePhase) {
            this.breakBlocksAlongKnockback(slideX, slideZ);
         }

         if (this.isLogicalSideForUpdatingMovement()) {
            this.setYaw(ky);
            this.prevYaw = ky;
            this.bodyYaw = ky;
            this.prevBodyYaw = ky;
            this.headYaw = ky;
            this.prevHeadYaw = ky;
            if (slidePhase) {
               float progressx = 1.0F - (this.knockedElapsedTicks - 1) / 30.0F;
               float factorx = (float)Math.sqrt(Math.max(0.0F, progressx));
               double sp = 1.575 * factorx;
               this.setVelocity(slideX * sp, this.getVelocity().y, slideZ * sp);
            } else {
               Vec3d v = this.getVelocity();
               if (v.x != 0.0 || v.z != 0.0) {
                  this.setVelocity(0.0, v.y, 0.0);
               }
            }

            this.velocityModified = true;
            if (this.getFirstPassenger() instanceof ServerPlayerEntity sp2) {
               sp2.velocityModified = true;
            }
         }

         if (slidePhase && this.isOnGround() && this.getWorld() instanceof ServerWorld serverLevel) {
            BlockState groundBlock = this.getWorld().getBlockState(this.getBlockPos().down());
            if (!groundBlock.isAir()) {
               serverLevel.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, groundBlock), this.getX(), this.getY() + 0.15, this.getZ(), 12, 1.4, 0.25, 1.4, 0.18
               );
            }
         }
      } else {
         this.wasKnocked = false;
      }

      double yDelta = this.getY() - yBefore;
      if (Math.abs(yDelta) > 0.1 && this.isOnGround()) {
         this.smoothYOffset -= yDelta;
      }

      Vec3d smoothVel = this.getVelocity();
      double horizSpeed = Math.sqrt(smoothVel.x * smoothVel.x + smoothVel.z * smoothVel.z);
      double lerpRate = horizSpeed > 0.15 ? 0.35 : 0.25;
      this.smoothYOffset *= 1.0 - lerpRate;
      if (Math.abs(this.smoothYOffset) < 0.02) {
         this.smoothYOffset = 0.0;
      }

      if (this.isPlayerControlled() && Math.abs(yDelta) > 0.1 && this.isOnGround()) {
         Vec3d currentVel = this.getVelocity();
         double prevHorizSpeed = Math.sqrt(velocityBefore.x * velocityBefore.x + velocityBefore.z * velocityBefore.z);
         double currHorizSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
         if (prevHorizSpeed > 0.01 && currHorizSpeed < prevHorizSpeed * 0.5) {
            this.setVelocity(velocityBefore.x, currentVel.y, velocityBefore.z);
         }
      }

      if (this.isAlive() && !this.isTransforming()) {
         if (this.getWorld().isClient()) {
            if (this.clientJumpActive && this.jumpLaunched && this.jumpAnimTicks <= 0 && this.getVelocity().y < -0.1) {
               this.clientJumpActive = false;
               this.clientFallActive = true;
               this.jumpLaunched = false;
               this.jumpVelocityApplied = false;
            }

            if (!this.clientJumpActive && !this.clientFallActive && !this.isOnGround() && !this.isLanding() && this.fallDistance >= 6.0F) {
               this.clientFallActive = true;
            }

            if (this.clientFallActive && this.isOnGround()) {
               this.clientFallActive = false;
               this.clientInJumpArc = false;
            }
         }

         if (!this.getWorld().isClient()) {
            if (this.isJumping() && this.jumpLaunched && this.jumpAnimTicks <= 0) {
               if (this.jumpToFallTicks > 0) {
                  this.jumpToFallTicks--;
               } else {
                  this.setJumping(false);
                  this.setFalling(true);
                  this.jumpLaunched = false;
                  this.jumpVelocityApplied = false;
                  this.jumpWasAirborne = false;
               }
            }

            if (this.isOnGround() && this.isFalling()) {
               this.setFalling(false);
            }

            if (!this.isOnGround() && !this.isLanding() && !this.isJumping() && !this.isFalling() && this.fallDistance >= 6.0F) {
               this.setFalling(true);
            }
         }

         if (this.landingAnimTicks > 0) {
            this.landingAnimTicks--;
            if (this.landingAnimTicks <= 0) {
               this.setLanding(false);
            }
         }

         if (this.jumpCooldownTicks > 0) {
            this.jumpCooldownTicks--;
         }
      }
   }

   private void triggerStompEffects() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         for (PlayerEntity player : this.getWorld().getNonSpectatingEntities(PlayerEntity.class, this.getBoundingBox().expand(12.0))) {
            if (player.getVehicle() != this) {
               double distance = player.distanceTo(this);
               double intensity = Math.max(0.0, 1.0 - distance / 12.0);
               if (intensity > 0.0) {
                  Vec3d knockback = player.getPos().subtract(this.getPos()).normalize().multiply(intensity * 0.2, 0.0, intensity * 0.2);
                  player.setVelocity(player.getVelocity().add(knockback));
               }
            }
         }

         BlockPos groundPos = this.getBlockPos().down();
         BlockState groundState = this.getWorld().getBlockState(groundPos);
         if (!groundState.isAir()) {
            for (ServerPlayerEntity playerx : serverLevel.getPlayers()) {
               if (playerx.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) < 2500.0) {
                  for (int i = 0; i < 50; i++) {
                     double offsetX = (this.random.nextDouble() - 0.5) * 1.5;
                     double offsetZ = (this.random.nextDouble() - 0.5) * 1.5;
                     double dx = offsetX;
                     double dz = offsetZ;
                     double dist = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
                     if (dist > 0.001) {
                        dx = offsetX / dist;
                        dz = offsetZ / dist;
                     }

                     serverLevel.spawnParticles(
                        playerx,
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                        true,
                        this.getX() + offsetX,
                        this.getY() + 0.1,
                        this.getZ() + offsetZ,
                        0,
                        dx * 0.3,
                        0.4,
                        dz * 0.3,
                        0.08
                     );
                  }
               }
            }
         }

         boolean isRunningForSound = this.isSprinting() && (!this.isArmed() || !this.hasHammer());
         float stompVolume = isRunningForSound ? 8.0F : 5.3F;
         if (this.isInSneakingPose()) {
            stompVolume *= 0.5F;
         }

         float randomPitch = 0.5F + this.random.nextFloat() * 0.1F;
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, stompVolume, randomPitch);
      }
   }

   private void performLandingShockwave(float fallDistance) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         int impactTier = 1 + (int)((fallDistance - 20.0F) / 10.0F);
         float impactStrength = Math.min(impactTier, 5);
         int shockwaveRadius = (int)(3.0F + impactStrength * 2.0F);
         int destroyDepth = (int)(1.0F + impactStrength);
         float knockbackRadius = 8.0F + impactStrength * 4.0F;
         float knockbackStrength = 0.3F + impactStrength * 0.15F;
         float volume = 4.0F + impactStrength * 3.0F;
         float pitch = 0.6F - impactStrength * 0.06F;
         this.getWorld()
            .playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, volume, pitch);
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, volume, pitch + 0.1F);

         for (LivingEntity target : this.getWorld()
            .getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(knockbackRadius, 4.0, knockbackRadius))) {
            if (target != this && target.getVehicle() != this) {
               UUID shifterUUID = this.getShifterUUID();
               if (shifterUUID == null || !target.getUuid().equals(shifterUUID)) {
                  double dist = target.distanceTo(this);
                  if (!(dist > knockbackRadius) && !(dist < 0.5)) {
                     double intensity = Math.max(0.0, 1.0 - dist / knockbackRadius);
                     Vec3d direction = target.getPos().subtract(this.getPos()).normalize();
                     double kbHorizontal = knockbackStrength * intensity;
                     double kbVertical = 0.2 + impactStrength * 0.1 * intensity;
                     target.setVelocity(target.getVelocity().add(direction.x * kbHorizontal, kbVertical, direction.z * kbHorizontal));
                     if (target instanceof LivingEntity) {
                        target.velocityModified = true;
                     }
                  }
               }
            }
         }

         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            int groundY = (int)Math.floor(this.getY());

            for (int dx = -shockwaveRadius; dx <= shockwaveRadius; dx++) {
               for (int dz = -shockwaveRadius; dz <= shockwaveRadius; dz++) {
                  double distSq = dx * dx + dz * dz;
                  if (!(distSq > shockwaveRadius * shockwaveRadius)) {
                     double dist = Math.sqrt(distSq);
                     if (!(this.random.nextFloat() > 0.4F + dist / shockwaveRadius * 0.4F)) {
                        for (int dy = 0; dy >= -destroyDepth; dy--) {
                           BlockPos pos = new BlockPos((int)Math.floor(this.getX() + dx), groundY + dy, (int)Math.floor(this.getZ() + dz));
                           BlockState blockState = this.getWorld().getBlockState(pos);
                           if (!blockState.isAir() && !(blockState.getHardness(this.getWorld(), pos) < 0.0F) && !blockState.isIn(BlockTags.WITHER_IMMUNE)) {
                              this.getWorld().removeBlock(pos, false);
                              if (this.random.nextFloat() < 0.25F + impactStrength * 0.05F) {
                                 double dirX = dist > 0.001 ? dx / dist : this.random.nextDouble() - 0.5;
                                 double dirZ = dist > 0.001 ? dz / dist : this.random.nextDouble() - 0.5;
                                 double velX = dirX * (0.5 + impactStrength * 0.2) + (this.random.nextDouble() - 0.5) * 0.3;
                                 double velY = 0.4 + impactStrength * 0.15 + this.random.nextDouble() * 0.5;
                                 double velZ = dirZ * (0.5 + impactStrength * 0.2) + (this.random.nextDouble() - 0.5) * 0.3;
                                 FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                                 ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                                 fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                 fallingBlock.setFallingBlockPos(pos);
                                 fallingBlock.setVelocity(velX, velY, velZ);
                                 fallingBlock.timeFalling = 1;
                                 fallingBlock.dropItem = false;
                                 serverLevel.spawnEntity(fallingBlock);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         BlockPos groundPos = this.getBlockPos().down();
         BlockState groundState = this.getWorld().getBlockState(groundPos);
         if (!groundState.isAir()) {
            int particleCount = (int)(40.0F + impactStrength * 30.0F);

            for (ServerPlayerEntity sp : serverLevel.getPlayers()) {
               if (sp.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) < 6400.0) {
                  for (int i = 0; i < particleCount; i++) {
                     double angle = this.random.nextDouble() * 2.0 * Math.PI;
                     double r = this.random.nextDouble() * shockwaveRadius * 0.5;
                     double offsetX = Math.cos(angle) * r;
                     double offsetZ = Math.sin(angle) * r;
                     double dirX = Math.cos(angle) * (0.3 + impactStrength * 0.1);
                     double dirZ = Math.sin(angle) * (0.3 + impactStrength * 0.1);
                     serverLevel.spawnParticles(
                        sp,
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                        true,
                        this.getX() + offsetX,
                        this.getY() + 0.1,
                        this.getZ() + offsetZ,
                        0,
                        dirX,
                        0.5 + impactStrength * 0.1,
                        dirZ,
                        0.1
                     );
                  }

                  if (impactStrength >= 2.0F) {
                     int smokeCount = (int)(impactStrength * 8.0F);

                     for (int i = 0; i < smokeCount; i++) {
                        double angle = this.random.nextDouble() * 2.0 * Math.PI;
                        double r = this.random.nextDouble() * shockwaveRadius * 0.3;
                        serverLevel.spawnParticles(
                           sp,
                           ParticleTypes.CAMPFIRE_COSY_SMOKE,
                           true,
                           this.getX() + Math.cos(angle) * r,
                           this.getY() + 0.5,
                           this.getZ() + Math.sin(angle) * r,
                           0,
                           Math.cos(angle) * 0.1,
                           0.15,
                           Math.sin(angle) * 0.1,
                           0.05
                        );
                     }
                  }
               }
            }
         }

         this.setLastStompTick(this.age);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement_controller", 6, this::movementPredicate));
      controllers.add(new AnimationController(this, "action_controller", 6, this::actionPredicate));
   }

   private boolean isJumpingForAnim() {
      if (this.isLogicalSideForUpdatingMovement()) {
         return this.clientJumpActive;
      } else {
         if (this.isJumping()) {
            this.jumpAnimVisibleUntilTick = this.age + 15;
         }

         return this.age < this.jumpAnimVisibleUntilTick;
      }
   }

   private boolean isFallingForAnim() {
      return this.isLogicalSideForUpdatingMovement() ? this.clientFallActive : this.isFalling();
   }

   private PlayState movementPredicate(AnimationState<WarhammerTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      if (this.isTransforming() || this.isDismounting()) {
         return PlayState.STOP;
      } else if (this.isKnocked()) {
         return PlayState.STOP;
      } else if (this.isJumpingForAnim()) {
         return state.setAndContinue(JUMP_ANIM);
      } else if (this.isFallingForAnim()) {
         return state.setAndContinue(FALLING_ANIM);
      } else if (this.isLanding()) {
         return state.setAndContinue(LAND_ANIM);
      } else {
         boolean hammer = this.hasHammer();
         if (this.isInSneakingPose()) {
            return state.setAndContinue(hammer ? CROUCH_HAMMER_ANIM : CROUCH_ANIM);
         } else {
            int attackNum = this.getAttackNumber();
            if (!this.isTitanAttacking() || attackNum != 3 && attackNum != 8 && attackNum != 9 && attackNum != 10 && attackNum != 12) {
               if (!this.isMoving()) {
                  return state.setAndContinue(hammer ? IDLE_ANIM : IDLE_NOHAMMER_ANIM);
               } else {
                  return this.isSprinting() && !this.isArmed()
                     ? state.setAndContinue(hammer ? RUN_HAMMER_ANIM : RUN_ANIM)
                     : state.setAndContinue(hammer ? WALK_ANIM : WALK_NOHAMMER_ANIM);
               }
            } else {
               return state.setAndContinue(hammer ? IDLE_ANIM : IDLE_NOHAMMER_ANIM);
            }
         }
      }
   }

   private PlayState actionPredicate(AnimationState<WarhammerTitanEntity> state) {
      state.getController().transitionLength(6);
      boolean hammer = this.hasHammer();
      if (this.isTransforming()) {
         return state.setAndContinue(SHIFT_ANIM);
      } else if (this.isKnocked()) {
         return state.setAndContinue(KNOCKED_ANIM);
      } else if (this.isLanding()) {
         state.getController().transitionLength(2);
         return state.setAndContinue(LAND_ANIM);
      } else if (this.isJumpingForAnim()) {
         state.getController().transitionLength(2);
         return state.setAndContinue(JUMP_ANIM);
      } else if (this.isFallingForAnim()) {
         state.getController().transitionLength(4);
         return state.setAndContinue(FALLING_ANIM);
      } else if (this.isTitanAttacking()) {
         int attackNum = this.getAttackNumber();
         boolean isSpin = attackNum == 4;
         boolean isStomp = attackNum == 8 || attackNum == 9 || attackNum == 10 || attackNum == 12;
         boolean isCharge2 = attackNum == 5;
         if (isSpin || this.wasPlayingSpin) {
            state.getController().transitionLength(0);
         } else if (!isStomp && !isCharge2) {
            state.getController().transitionLength(6);
         } else {
            state.getController().transitionLength(0);
         }

         this.wasPlayingSpin = isSpin;

         return switch (attackNum) {
            case 1 -> state.setAndContinue(ATTACK1_ANIM);
            default -> state.setAndContinue(ATTACK1_ANIM);
            case 3 -> state.setAndContinue(GROUND_SLAM_ANIM);
            case 4 -> state.setAndContinue(SPIN_UPPER_ANIM);
            case 5 -> state.setAndContinue(CHARGE_KRON3_ANIM);
            case 6 -> state.setAndContinue(PUNCH_LEFT_ANIM);
            case 7 -> state.setAndContinue(PUNCH_RIGHT_ANIM);
            case 8, 9, 10, 12 -> state.setAndContinue(STOMP_ANIM);
            case 11 -> state.setAndContinue(SUMMON_HAMMER_ANIM);
            case 13 -> state.setAndContinue(UNSUMMON_HAMMER_ANIM);
            case 14 -> state.setAndContinue(JAB_L_ANIM);
            case 15 -> state.setAndContinue(JAB_R_ANIM);
            case 16 -> state.setAndContinue(ATTACK2_ANIM);
         };
      } else {
         if (this.wasPlayingSpin) {
            state.getController().transitionLength(0);
            this.wasPlayingSpin = false;
         } else {
            state.getController().transitionLength(6);
         }

         if (this.isDismounting()) {
            return state.setAndContinue(DISMOUNT_ANIM);
         } else if (this.isArmed()) {
            if (hammer) {
               return state.setAndContinue(CHARGE_KRON_ANIM);
            } else {
               return this.isInSneakingPose() ? state.setAndContinue(CROUCH_ARMED_ANIM) : state.setAndContinue(ARMED_IDLE_ANIM);
            }
         } else if (this.isInSneakingPose()) {
            return state.setAndContinue(hammer ? CROUCH_HAMMER_UPPER_ANIM : CROUCH_UPPER_ANIM);
         } else if (this.isMoving()) {
            return this.isSprinting() && !this.isArmed()
               ? state.setAndContinue(hammer ? RUN_HAMMER_UPPER_ANIM : RUN_UPPER_ANIM)
               : state.setAndContinue(hammer ? WALK_UPPER_ANIM : WALK_NOHAMMER_UPPER_ANIM);
         } else {
            return state.setAndContinue(hammer ? IDLE_UPPER_ANIM : IDLE_NOHAMMER_UPPER_ANIM);
         }
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
