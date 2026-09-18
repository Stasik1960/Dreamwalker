package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.ModNetworking;
import daot.network.ODMJamPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
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
import net.minecraft.entity.mob.MobEntity;
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

public class FemaleTitanEntity extends HostileEntity implements GeoEntity, ShifterTitan, WireRestrainable, daot.compat.BaseDimensionsProvider {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Boolean> DATA_IS_MOVING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      FemaleTitanEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_TRANSFORMATION_TICKS = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_WIRE_RESTRAINT = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_DISMOUNTING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_STOMP_TICK = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_SPRINTING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ATTACKING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ATTACK_NUMBER = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_ARMED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_WAS_MOVING_ON_ATTACK = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_ATTACK_IMPACT_TICK = DataTracker.registerData(
      FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER
   );
   private static final TrackedData<Boolean> DATA_IS_CROUCHING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_HIT_REACTION = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_LOW_STAMINA = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_HIT_TICK = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_SLIDING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_HIT_DIR_YAW = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_IS_IMPALED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_FALLING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_LANDING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_LANDING_TICK = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> DATA_LANDING_INTENSITY = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_IS_JUMPING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_JUMP_LAUNCHED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_JUMP_VEL_X = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Y = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Z = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_IS_PROTECTING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ROARING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ROAR_START_TICK = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_WIRE_YANKING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_DEFEATED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_HARDENING_MODE = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_CLIMBING = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_GRABBED_ENTITY_ID = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_GRAB_PHASE = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_INCAPACITATED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_KNOCKED = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_KNOCKED_YAW = DataTracker.registerData(FemaleTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation RUN_UPPER_ANIM = RawAnimation.begin().thenLoop("run_upper");
   private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation UPPERCUT_L_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_l");
   private static final RawAnimation UPPERCUT_R_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_r");
   private static final RawAnimation JAB_L_ANIM = RawAnimation.begin().thenPlayAndHold("jab_l");
   private static final RawAnimation JAB_R_ANIM = RawAnimation.begin().thenPlayAndHold("jab_r");
   private static final RawAnimation WIRE_YANK_ANIM = RawAnimation.begin().thenPlayAndHold("wire_yank");
   private static final RawAnimation GROUNDSMASH_ANIM = RawAnimation.begin().thenPlayAndHold("graundsmash");
   private static final RawAnimation GROUNDSMASH_IDLE_ANIM = RawAnimation.begin().thenPlayAndHold("idlegraundsmash");
   private static final RawAnimation KICK_ANIM = RawAnimation.begin().thenPlayAndHold("kick");
   private static final RawAnimation SHIFT_ANIM = RawAnimation.begin().thenPlayAndHold("shift");
   private static final RawAnimation DISMOUNT_ANIM = RawAnimation.begin().thenLoop("dismount");
   private static final RawAnimation ARMED_IDLE_ANIM = RawAnimation.begin().thenLoop("armedidle");
   private static final RawAnimation CROUCH_IDLE_ANIM = RawAnimation.begin().thenLoop("crouchidle");
   private static final RawAnimation CROUCH_WALK_ANIM = RawAnimation.begin().thenLoop("crouchwalk");
   private static final RawAnimation CROUCH_WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("crouchwalk_upper");
   private static final RawAnimation CROUCH_IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("crouchidle_upper");
   private static final RawAnimation CROUCH_ARMED_ANIM = RawAnimation.begin().thenLoop("croucharmed");
   private static final RawAnimation HIT_FRONT_LEFT_ANIM = RawAnimation.begin().thenPlayAndHold("hitfrontleft");
   private static final RawAnimation HIT_FRONT_RIGHT_ANIM = RawAnimation.begin().thenPlayAndHold("hitfrontright");
   private static final RawAnimation HIT_BACK_LEFT_ANIM = RawAnimation.begin().thenPlayAndHold("hitbackleft");
   private static final RawAnimation HIT_BACK_RIGHT_ANIM = RawAnimation.begin().thenPlayAndHold("hitbackright");
   private static final RawAnimation IMPALED_ANIM = RawAnimation.begin().thenLoop("impaled");
   private static final RawAnimation KNOCKED_ANIM = RawAnimation.begin().thenPlayAndHold("knocked");
   private static final RawAnimation PROTECT_ANIM = RawAnimation.begin().thenPlayAndHold("protect");
   private static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenPlayAndHold("roar");
   private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");
   private static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("jump");
   private static final RawAnimation FALLING_ANIM = RawAnimation.begin().thenLoop("falling");
   private static final RawAnimation LAND_ANIM = RawAnimation.begin().thenPlayAndHold("land");
   private static final RawAnimation CLIMB_ANIM = RawAnimation.begin().thenLoop("climb");
   private static final RawAnimation HOLD_ANIM = RawAnimation.begin().thenLoop("hold");
   private static final RawAnimation THROW_CHARGE_ANIM = RawAnimation.begin().thenLoop("throw_charge");
   private static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlayAndHold("throw");
   private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenPlayAndHold("eat");
   private static final double MOVEMENT_SPEED = 0.375;
   private static final double CONTROLLED_WALK_SPEED = 0.3;
   private static final double CONTROLLED_RUN_SPEED = 1.4025;
   private static final double CONTROLLED_CROUCH_SPEED = 0.225;
   private double currentSpeed = 0.3;
   private static final double SPEED_LERP_RATE = 0.16666666666666666;
   private long walkStartTick = 0L;
   private boolean wasMovingLastTick = false;
   private static final double STOMP_THRESHOLD = 0.08;
   private static final double WALK_CYCLE_LENGTH = 1.4583;
   private static final double[] STOMP_KEYFRAMES = new double[]{0.46, 1.33};
   private static final int STOMP_START_DELAY = 5;
   private int lastStompKeyframeIndex = -1;
   private int stompCooldown = 0;
   private double smoothYOffset = 0.0;
   private double currentRideForwardOffset = 0.5;
   private static final double RIDE_POSITION_LERP = 0.15;
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
   private int attackCooldown = 0;
   private static final int ATTACK1_TICKS = 20;
   private static final int ATTACK2_TICKS = 18;
   private static final int UPPERCUT_TICKS = 16;
   private static final int JAB_TICKS = 20;
   private static final int GROUNDSMASH_TICKS = 35;
   private static final int KICK_TICKS = 35;
   private static final int WIRE_YANK_TICKS = 30;
   private static final int WIRE_YANK_SOUND_TICK = 14;
   private int attackAnimationTicks = 0;
   private int lastAttackNumber = 0;
   private int wireYankTicks = 0;
   private final Set<UUID> hookedPlayers = new HashSet<>();
   public static final int UPPERCUT_L = 6;
   public static final int UPPERCUT_R = 7;
   public static final int JAB_L = 10;
   public static final int JAB_R = 11;
   private boolean nextJabIsRight = false;
   private boolean protectActive = false;
   private static final int ROAR_DURATION_TICKS = 254;
   private static final int ROAR_SOUND_DELAY_TICKS = 10;
   private static final double ROAR_COMMAND_RADIUS = 200.0;
   private int roarTicks = 0;
   private boolean roarSoundPlayed = false;
   private boolean roarCommandActive = false;
   private static final int ATTACK1_EFFECT_TICK = 14;
   private static final int ATTACK2_EFFECT_TICK = 12;
   private static final int UPPERCUT_EFFECT_TICK = 8;
   private static final int JAB_EFFECT_TICK = 10;
   private static final int GROUNDSMASH_EFFECT_TICK = 14;
   private static final int KICK_EFFECT_TICK = 18;
   private int attackEffectTimer = 0;
   private boolean attackEffectTriggered = false;
   private static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.changing(3.0F, 14.0F);
   private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.changing(3.0F, 9.5F);
   private boolean wasCrouchingLastTick = false;
   private int crouchTransitionTicksRemaining = 0;
   private boolean wasSprintingBeforeCrouch = false;
   private int crouchHysteresisCounter = 0;
   private static final int CROUCH_HYSTERESIS_TICKS = 2;
   private FemaleTitanNapeEntity napeEntity = null;
   private FemaleTitanEyeEntity eyeEntity = null;
   private FemaleTitanGrabEntity grabHitboxEntity = null;
   private double syncedGrabX;
   private double syncedGrabY;
   private double syncedGrabZ;
   private int lastGrabSyncTick = -1;
   private int throwAnimTicks = 0;
   private int eatTicks = 0;
   private boolean eatDropDone = false;
   private boolean eatDamageDone = false;
   private static final int THROW_ANIM_DURATION = 20;
   private static final int THROW_LAUNCH_TICK = 12;
   private static final int EAT_TICKS_TOTAL = 60;
   private static final int EAT_DROP_TICK = 24;
   private static final int EAT_DAMAGE_TICK = 30;
   private static final float EAT_DAMAGE = 30.0F;
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
   private int deathAnimTicks = -1;
   private static final int DEATH_IMPACT_TICK_1 = 15;
   private static final int DEATH_IMPACT_TICK_2 = 36;
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
   private static final int FALLING_STATE_MIN_AIR_TICKS = 10;
   private int continuousAirborneTicks = 0;
   private int continuousAirborneTicksClient = 0;
   private int climbAnimTicks = 0;
   private float climbTargetYaw = Float.NaN;
   private int displayedHardeningMode = 0;
   private int previousHardeningMode = 0;
   private long hardeningTransitionStartTick = -1L;
   private boolean hardeningSyncInitialized = false;
   public static final int HARDENING_TRANSITION_TICKS = 20;
   private static final float HARDENING_TOGGLE_DRAIN = 10.0F;
   private static final float ATTACK_DRAIN = 5.0F;
   private static final float ABILITY_DRAIN = 15.0F;
   private static final double[] RUN_STOMP_KEYFRAMES = new double[]{0.0, 0.25};

   public FemaleTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public FemaleTitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public FemaleTitanGrabEntity getGrabHitboxEntity() {
      return this.grabHitboxEntity;
   }

   public void setGrabSyncedPosition(double x, double y, double z, int tick) {
      this.syncedGrabX = x;
      this.syncedGrabY = y;
      this.syncedGrabZ = z;
      this.lastGrabSyncTick = tick;
   }

   public int getGrabbedEntityId() {
      return this.dataTracker.get(DATA_GRABBED_ENTITY_ID);
   }

   public void setGrabbedEntityId(int id) {
      this.dataTracker.set(DATA_GRABBED_ENTITY_ID, id);
   }

   public int getGrabPhase() {
      return this.dataTracker.get(DATA_GRAB_PHASE);
   }

   public void setGrabPhase(int phase) {
      this.dataTracker.set(DATA_GRAB_PHASE, phase);
   }

   public boolean isGrabbing() {
      return this.getGrabbedEntityId() != -1 && this.getGrabPhase() != 0;
   }

   public boolean isThrowCharging() {
      return this.getGrabPhase() == 2;
   }

   public boolean isThrowing() {
      return this.getGrabPhase() == 3;
   }

   public boolean isEating() {
      return this.getGrabPhase() == 4;
   }

   public FemaleTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public void spawnHitboxes() {
      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         FemaleTitanNapeEntity nape = new FemaleTitanNapeEntity(DannysAot.FEMALE_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         FemaleTitanEyeEntity eye = new FemaleTitanEyeEntity(DannysAot.FEMALE_TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
         FemaleTitanGrabEntity grab = new FemaleTitanGrabEntity(DannysAot.FEMALE_TITAN_GRAB, this.getWorld());
         grab.setParentTitan(this);
         grab.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(grab);
         this.grabHitboxEntity = grab;
         DannysAot.LOGGER.info("Spawned Female Titan hitboxes");
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
      this.dataTracker.startTracking(DATA_HIT_REACTION, 0);
      this.dataTracker.startTracking(DATA_LAST_HIT_TICK, 0);
      this.dataTracker.startTracking(DATA_SLIDING, false);
      this.dataTracker.startTracking(DATA_HIT_DIR_YAW, 0.0F);
      this.dataTracker.startTracking(DATA_LOW_STAMINA, false);
      this.dataTracker.startTracking(DATA_IS_IMPALED, false);
      this.dataTracker.startTracking(DATA_IS_FALLING, false);
      this.dataTracker.startTracking(DATA_IS_LANDING, false);
      this.dataTracker.startTracking(DATA_LAST_LANDING_TICK, 0);
      this.dataTracker.startTracking(DATA_LANDING_INTENSITY, 0.0F);
      this.dataTracker.startTracking(DATA_IS_JUMPING, false);
      this.dataTracker.startTracking(DATA_JUMP_LAUNCHED, false);
      this.dataTracker.startTracking(DATA_JUMP_VEL_X, 0.0F);
      this.dataTracker.startTracking(DATA_JUMP_VEL_Y, 0.0F);
      this.dataTracker.startTracking(DATA_JUMP_VEL_Z, 0.0F);
      this.dataTracker.startTracking(DATA_IS_PROTECTING, false);
      this.dataTracker.startTracking(DATA_IS_ROARING, false);
      this.dataTracker.startTracking(DATA_ROAR_START_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_WIRE_YANKING, false);
      this.dataTracker.startTracking(DATA_IS_DEFEATED, false);
      this.dataTracker.startTracking(DATA_IS_INCAPACITATED, false);
      this.dataTracker.startTracking(DATA_HARDENING_MODE, 0);
      this.dataTracker.startTracking(DATA_IS_CLIMBING, false);
      this.dataTracker.startTracking(DATA_GRABBED_ENTITY_ID, -1);
      this.dataTracker.startTracking(DATA_GRAB_PHASE, 0);
      this.dataTracker.startTracking(DATA_IS_KNOCKED, false);
      this.dataTracker.startTracking(DATA_KNOCKED_YAW, 0.0F);
   }

   public boolean isWireYanking() {
      return this.dataTracker.get(DATA_IS_WIRE_YANKING);
   }

   public void setWireYanking(boolean v) {
      this.dataTracker.set(DATA_IS_WIRE_YANKING, v);
   }

   public void setPlayerHooked(UUID playerUUID, boolean hooked) {
      if (hooked) {
         this.hookedPlayers.add(playerUUID);
      } else {
         this.hookedPlayers.remove(playerUUID);
      }
   }

   public boolean isProtecting() {
      return this.dataTracker.get(DATA_IS_PROTECTING);
   }

   public void setProtecting(boolean protecting) {
      this.dataTracker.set(DATA_IS_PROTECTING, protecting);
   }

   public int getHardeningMode() {
      return this.dataTracker.get(DATA_HARDENING_MODE);
   }

   public void setHardeningMode(int mode) {
      this.dataTracker.set(DATA_HARDENING_MODE, mode);
   }

   public boolean isHardened() {
      return this.getHardeningMode() != 0;
   }

   public boolean isHardenedNape() {
      return this.getHardeningMode() == 1;
   }

   public boolean isHardenedFeet() {
      return this.getHardeningMode() == 2;
   }

   public boolean isHardenedArms() {
      return this.getHardeningMode() == 3;
   }

   public boolean isTitanClimbing() {
      return this.dataTracker.get(DATA_IS_CLIMBING);
   }

   public void setTitanClimbing(boolean climbing) {
      this.dataTracker.set(DATA_IS_CLIMBING, climbing);
   }

   public int getDisplayedHardeningMode() {
      return this.displayedHardeningMode;
   }

   public int getPreviousHardeningMode() {
      return this.previousHardeningMode;
   }

   public long getHardeningTransitionStartTick() {
      return this.hardeningTransitionStartTick;
   }

   public boolean isRoaring() {
      return this.dataTracker.get(DATA_IS_ROARING);
   }

   public void setRoaring(boolean roaring) {
      this.dataTracker.set(DATA_IS_ROARING, roaring);
   }

   public int getRoarStartTick() {
      return this.dataTracker.get(DATA_ROAR_START_TICK);
   }

   public void setRoarStartTick(int tick) {
      this.dataTracker.set(DATA_ROAR_START_TICK, tick);
   }

   public static int getRoarSoundDelayTicks() {
      return 10;
   }

   public static int getRoarDurationTicks() {
      return 254;
   }

   public boolean isImpaled() {
      return this.dataTracker.get(DATA_IS_IMPALED);
   }

   public void setImpaled(boolean impaled) {
      this.dataTracker.set(DATA_IS_IMPALED, impaled);
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
      this.setTitanAttacking(false);
      this.setAttackNumber(0);
      this.attackAnimationTicks = 0;
      this.attackCooldown = 0;
      this.attackEffectTriggered = true;
      this.setRoaring(false);
      this.roarTicks = 0;
      this.setWireYanking(false);
      this.wireYankTicks = 0;
      if (this.isGrabbing()) {
         this.releaseGrabbedEntity();
      }

      if (this.protectActive) {
         this.protectActive = false;
         this.setProtecting(false);
      }
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
      this.dataTracker.set(DATA_IS_ARMED, armed);
      if (!this.getWorld().isClient() && this.isGrabbing()) {
         int phase = this.getGrabPhase();
         if (armed && phase == 1) {
            this.startThrowCharge();
         } else if (!armed && phase == 2) {
            this.cancelThrowCharge();
         }
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
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.4025)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 4.0)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.5)
         .add(daot.compat.attributes.DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      if (this.despawnAtGameTime >= 0L) {
         nbt.putLong("DespawnAtGameTime", this.despawnAtGameTime);
      }

      nbt.putInt("HardeningMode", this.getHardeningMode());
      nbt.putBoolean("Incapacitated", this.isIncapacitated());
      nbt.putInt("IncapacitateTicks", this.incapacitateTicks);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("DespawnAtGameTime")) {
         this.despawnAtGameTime = nbt.getLong("DespawnAtGameTime");
      }

      if (nbt.contains("HardeningMode")) {
         this.setHardeningMode(nbt.getInt("HardeningMode"));
      }

      if (nbt.contains("Incapacitated")) {
         this.setIncapacitated(nbt.getBoolean("Incapacitated"));
      }

      if (nbt.contains("IncapacitateTicks")) {
         this.incapacitateTicks = nbt.getInt("IncapacitateTicks");
      }
   }

   @Override
   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (this.getWorld().isClient() && data.equals(DATA_HARDENING_MODE)) {
         int newMode = this.getHardeningMode();
         if (!this.hardeningSyncInitialized) {
            this.displayedHardeningMode = newMode;
            this.previousHardeningMode = newMode;
            this.hardeningSyncInitialized = true;
            return;
         }

         if (newMode != this.displayedHardeningMode) {
            this.previousHardeningMode = this.displayedHardeningMode;
            this.displayedHardeningMode = newMode;
            this.hardeningTransitionStartTick = this.getWorld().getTime();
         }
      }
   }

   @Override
   public EntityDimensions getBaseDimensions(EntityPose pose) {
      return this.isInSneakingPose() ? CROUCHING_DIMENSIONS : STANDING_DIMENSIONS;
   }

   @Override
   public void onDeath(DamageSource damageSource) {
      if (!this.getWorld().isClient() && !this.isDefeated()) {
         this.setDefeated(true);
         this.setIncapacitated(false);
         this.incapacitateTicks = 0;
         this.blindnessTicks = 0;
         this.deathAnimTicks = 0;
         this.setHealth(1.0F);
         this.clearRoarCommand();
         this.setRoaring(false);
         this.roarTicks = 0;
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setWireYanking(false);
         this.releaseGrabbedEntity();
         if (this.protectActive) {
            this.protectActive = false;
            this.setProtecting(false);
         }

         this.attackAnimationTicks = 0;
         this.attackCooldown = 0;
         if (this.bossBar != null) {
            this.bossBar.clearPlayers();
            this.bossBar = null;
         }

         this.setDismounting(true);
         this.allowDismount = true;
         this.dismountVisibilityDelay = 3;
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            for (Entity passenger : this.getPassengerList()) {
               if (passenger instanceof ServerPlayerEntity serverPlayer && serverPlayer.getUuid().equals(shifterUUID)) {
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 900, 4));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 900, 2));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 900, 4));
                  serverPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
                  DefeatedCarryTracker.markDefeated(serverPlayer.getUuid());
                  serverPlayer.sendMessage(Text.literal("Your titan has been defeated! Sneak to dismount.").formatted(Formatting.RED));
                  DannysAot.LOGGER.info("Female Titan defeated with rider {} still attached", serverPlayer.getName().getString());
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
         this.clearRoarCommand();
         this.setRoaring(false);
         this.roarTicks = 0;
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setWireYanking(false);
         this.releaseGrabbedEntity();
         if (this.protectActive) {
            this.protectActive = false;
            this.setProtecting(false);
         }

         this.attackAnimationTicks = 0;
         this.attackCooldown = 0;
         this.blindnessTicks = 0;
         this.setDismounting(true);
         this.allowDismount = false;
         this.dismountVisibilityDelay = 3;
         this.calculateDimensions();
         NapeSmokeHelper.onDismountStart(this, this.napeEntity);
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
      return this.isDefeated() ? true : super.isInvulnerableTo(damageSource);
   }

   public boolean isDefeated() {
      return this.dataTracker.get(DATA_IS_DEFEATED);
   }

   public void setDefeated(boolean v) {
      this.dataTracker.set(DATA_IS_DEFEATED, v);
   }

   @Override
   public boolean isIncapacitated() {
      return this.dataTracker.get(DATA_IS_INCAPACITATED);
   }

   public void setIncapacitated(boolean v) {
      this.dataTracker.set(DATA_IS_INCAPACITATED, v);
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
      if (this.isImpaled()) {
         return null;
      } else {
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            for (Entity p : this.getPassengerList()) {
               if (p instanceof PlayerEntity player && player.getUuid().equals(shifterUUID)) {
                  return player;
               }
            }
         }

         return null;
      }
   }

   @Override
   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengerList().size() < 2;
   }

   private Vec3d getRidePosition(Entity passenger) {
      double headHeight = 11.0;
      return new Vec3d(0.0, headHeight, 0.0);
   }

   @Override
   public Vec3d updatePassengerForDismount(LivingEntity passenger) {
      return this.isDefeated() ? passenger.getPos() : super.updatePassengerForDismount(passenger);
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         if (this.isDefeated()) {
            FemaleTitanNapeEntity nape = this.getWorld().isClient() ? FemaleTitanNapeEntity.getClientInstance(this.getId()) : this.napeEntity;
            if (nape != null) {
               double napeY = nape.getY() + nape.getHeight() / 2.0;
               positionUpdater.accept(passenger, nape.getX(), napeY, nape.getZ());
               return;
            }
         }

         Vec3d ridePos = this.getRidePosition(passenger);
         double targetForwardOffset = this.isTitanClimbing() ? -3.0 : (this.isDismounting() ? 0.0 : 0.5);
         this.currentRideForwardOffset = this.currentRideForwardOffset + (targetForwardOffset - this.currentRideForwardOffset) * 0.15;
         if (Math.abs(this.currentRideForwardOffset - targetForwardOffset) < 0.01) {
            this.currentRideForwardOffset = targetForwardOffset;
         }

         float yawRad = (float)Math.toRadians(this.getYaw());
         double offsetX = -Math.sin(yawRad) * this.currentRideForwardOffset;
         double offsetZ = Math.cos(yawRad) * this.currentRideForwardOffset;
         double x = this.getX() + ridePos.x + offsetX;
         double y = this.getY() + ridePos.y + this.smoothYOffset;
         double z = this.getZ() + ridePos.z + offsetZ;
         positionUpdater.accept(passenger, x, y, z);
      }
   }

   @Override
   protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
      if (this.isDefeated()) {
         super.tickControlled(controllingPlayer, Vec3d.ZERO);
         if (!this.getWorld().isClient()) {
            this.setMoving(false);
            this.setSprinting(false);
            this.setArmed(false);
            this.setCrouching(false);
            Vec3d v = this.getVelocity();
            this.setVelocity(0.0, v.y, 0.0);
         }
      } else {
         super.tickControlled(controllingPlayer, movementInput);
         if (this.isTitanClimbing()) {
            if (Float.isNaN(this.climbTargetYaw)) {
               this.climbTargetYaw = Math.round(controllingPlayer.getYaw() / 90.0F) * 90.0F;
            }

            float currentYaw = this.getYaw();
            float delta = MathHelper.wrapDegrees(this.climbTargetYaw - currentYaw);
            float lerped = currentYaw + delta * 0.18F;
            this.setYaw(lerped);
            this.prevYaw = lerped;
            this.bodyYaw = lerped;
            this.prevBodyYaw = lerped;
            this.headYaw = lerped;
            this.prevHeadYaw = lerped;
         }

         if (!this.isDismounting() && !this.isImpaled()) {
            if (this.getWorld().isClient() && this.isKnocked()) {
               float ky = this.getKnockedYaw();
               this.setYaw(ky);
               this.prevYaw = ky;
               this.bodyYaw = ky;
               this.prevBodyYaw = ky;
               this.headYaw = ky;
               this.prevHeadYaw = ky;
            }

            if (this.getWorld().isClient() && !this.isTransforming() && !this.isSliding() && !this.isTitanClimbing() && !this.isKnocked()) {
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

               boolean canShowMovement = !this.isTransforming() && !this.isDismounting();
               this.setMoving(hasInput && canShowMovement);
               boolean canMove = !this.isTransforming() && !this.isDismounting();
               if (!hasInput || !canMove) {
                  this.setSprinting(false);
               }

               int attackNum = this.getAttackNumber();
               boolean isFullBodyAttack = this.isTitanAttacking() && (attackNum == 3 || attackNum == 4 || attackNum == 5);
               boolean canCrouch = !this.isTransforming()
                  && !this.isDismounting()
                  && !isFullBodyAttack
                  && !this.isGrabbing()
                  && !this.isThrowing()
                  && !this.isThrowCharging();
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
         } else {
            if (!this.getWorld().isClient()) {
               this.setMoving(false);
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
      if (!this.isTransforming() && !this.isDismounting() && !this.isSliding() && !this.isProtecting() && !this.isDefeated() && !this.isKnocked()) {
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
      int attackNum = this.getAttackNumber();
      boolean isKicking = this.isTitanAttacking() && attackNum == 4;
      boolean isGroundsmash = this.isTitanAttacking() && attackNum == 3;
      if (!this.isLanding()
         && !isKicking
         && !isGroundsmash
         && !this.isRoaring()
         && !this.isDefeated()
         && !this.isKnocked()
         && !this.isThrowCharging()
         && !this.isThrowing()
         && !this.isEating()) {
         boolean canRun = this.isSprinting() && !this.isArmed() && !this.isInSneakingPose() && !this.isWireYanking();
         double targetSpeed;
         if (this.isInSneakingPose()) {
            targetSpeed = 0.225;
         } else if (canRun) {
            targetSpeed = 1.4025;
         } else {
            targetSpeed = 0.3;
         }

         if (this.currentSpeed < targetSpeed) {
            this.currentSpeed = Math.min(this.currentSpeed + 0.18375, targetSpeed);
         } else if (this.currentSpeed > targetSpeed) {
            this.currentSpeed = Math.max(this.currentSpeed - 0.18375, targetSpeed);
         }

         if (this.isBlinded()) {
            this.currentSpeed *= 0.6666666666666666;
         }

         if (this.hitSlowTicks > 0) {
            this.currentSpeed *= 0.5;
         }

         double finalSpeed = this.currentSpeed * StrwsRestraintTracker.movementFactor(this, this.getWireRestraintCount());
         if (this.isHardenedFeet()) {
            finalSpeed *= 1.2;
         }

         return (float)finalSpeed;
      } else {
         this.currentSpeed = 0.0;
         return 0.0F;
      }
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (passenger instanceof PlayerEntity player) {
         ShifterVisibilityHelper.cancelReentry(player);
         player.setInvisible(false);
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            this.setShifterUUID(null);
            if (!this.getWorld().isClient()) {
               ServerHookTracker.grantDismountFallImmunity(player.getUuid());
               if (player instanceof ServerPlayerEntity serverPlayer) {
                  ShifterMarkTracker.markFullyDismounted(serverPlayer, this);
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
   }

   public boolean isDismountToggleOnCooldown() {
      return this.dismountToggleCooldown > 0;
   }

   public void startDismounting(PlayerEntity player) {
      if (!this.isDismountToggleOnCooldown()) {
         if (!this.isKnocked()) {
            if (!this.isIncapacitated()) {
               if (!this.isProtecting()) {
                  if (!this.isTitanClimbing()) {
                     this.dismountVisibilityDelay = 3;
                     this.setDismounting(true);
                     this.allowDismount = true;
                     this.dismountToggleCooldown = 20;
                     this.releaseGrabbedEntity();
                     NapeSmokeHelper.onDismountStart(this, this.napeEntity);
                     float yawRad = (float)Math.toRadians(this.getYaw());
                     double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
                     double napeY = this.getY() + 12.5;
                     double napeZ = this.getZ() + Math.cos(yawRad) * 1.5;
                     this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                  }
               }
            }
         }
      }
   }

   public void cancelDismounting(PlayerEntity player) {
      if (!this.isDefeated()) {
         if (!this.isIncapacitated()) {
            if (!this.isDismountToggleOnCooldown()) {
               player.setInvisible(true);
               this.dismountVisibilityDelay = 0;
               this.setDismounting(false);
               this.allowDismount = false;
               this.dismountToggleCooldown = 20;
               float yawRad = (float)Math.toRadians(this.getYaw());
               double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
               double napeY = this.getY() + 12.5;
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
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Female Titan" : "Female Titan - " + player.getName().getString();
         this.bossBar = new ServerBossBar(Text.literal(bossBarName), Color.PINK, Style.PROGRESS);
         this.bossBar.setPercent(1.0F);
      }

      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         double centerX = this.getX();
         double centerY = this.getY() + 3.0;
         double centerZ = this.getZ();
         boolean stealthShift = player.getCommandTags().contains("titan_stealth");
         boolean griefing = DannysAot.isTitanGriefingEnabled(this.getWorld()) && DannysAot.isShifterExplosionDamageEnabled(this.getWorld()) && !stealthShift;
         if (!stealthShift) {
            if (griefing) {
               this.flingBlocksFromShiftExplosion(serverLevel, centerX, centerY, centerZ);
            }

            ExplosionSourceType interaction = griefing ? ExplosionSourceType.BLOCK : ExplosionSourceType.NONE;
            serverLevel.createExplosion(this, centerX, centerY, centerZ, 24.0F, false, interaction);
            serverLevel.createExplosion(this, centerX, centerY + 8.0, centerZ, 16.0F, false, interaction);
            serverLevel.createExplosion(this, centerX, centerY + 15.0, centerZ, 12.0F, false, interaction);
         }

         if (griefing) {
            double fireRadius = 16.0;

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

         double maxRange = stealthShift ? 15.0 : 20.0;

         for (Entity entity : serverLevel.getOtherEntities(this, this.getBoundingBox().expand(maxRange))) {
            if (entity != player
               && entity != this
               && !(entity instanceof FemaleTitanNapeEntity)
               && !(entity instanceof FemaleTitanEyeEntity)
               && !(entity instanceof ArmoredTitanNapeEntity)
               && !(entity instanceof ArmoredTitanEyeEntity)
               && !(entity instanceof ColossalTitanNapeEntity)
               && !(entity instanceof ColossalTitanEyeEntity)
               && !(entity instanceof BeastTitanNapeEntity)
               && !(entity instanceof BeastTitanEyeEntity)
               && !(entity instanceof AttackTitanEntity)
               && !(entity instanceof FemaleTitanEntity)
               && !(entity instanceof ArmoredTitanEntity)
               && !(entity instanceof ColossalTitanEntity)
               && !(entity instanceof BeastTitanEntity)
               && !(
                  entity instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof BeastTitanEntity
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

                  if (!stealthShift && distance < 12.0 && entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)) {
                     living.setFireTicks(100);
                  }
               }
            }
         }
      }
   }

   public void triggerAttack() {
      if (this.protectActive) {
         this.cancelProtect();
      } else if (!this.isDefeated()) {
         if (this.isKnocked()) {
            if (this.isKnockedHeld()) {
               this.releaseKnocked();
            }
         } else if (!this.isGrabbing() && !this.isThrowing() && !this.isEating()) {
            if (this.attackCooldown <= 0
               && !this.isTransforming()
               && !this.isDismounting()
               && !this.isRoaring()
               && !this.isWireYanking()
               && !this.isTitanClimbing()) {
               this.setWasMovingOnAttackStart(this.isMoving());
               int nextAttack;
               if (this.isArmed()) {
                  nextAttack = this.nextJabIsRight ? 11 : 10;
                  this.nextJabIsRight = !this.nextJabIsRight;
               } else {
                  nextAttack = switch (this.lastAttackNumber) {
                     case 1 -> 6;
                     case 2 -> 7;
                     case 6 -> 2;
                     default -> 1;
                  };
                  this.lastAttackNumber = nextAttack;
               }
               int attackTicks = switch (nextAttack) {
                  case 1 -> 20;
                  case 2 -> 18;
                  case 10, 11 -> 20;
                  default -> 16;
               };
               this.setAttackNumber(nextAttack);
               this.setTitanAttacking(true);
               this.attackAnimationTicks = attackTicks;
               this.attackCooldown = attackTicks;
               this.attackEffectTimer = 0;
               this.attackEffectTriggered = false;
            }
         } else if (!this.isThrowing() && !this.isEating()) {
            if (this.isInSneakingPose()) {
               this.startEat();
            } else if (this.getGrabPhase() == 2) {
               this.startThrow();
            }
         }
      }
   }

   public static boolean canBeGrabbedByFemale(Entity target) {
      if (!(target instanceof LivingEntity) || !target.isAlive()) {
         return false;
      } else if (target instanceof AttackTitanEntity
         || target instanceof ArmoredTitanEntity
         || target instanceof ColossalTitanEntity
         || target instanceof FemaleTitanEntity
         || target instanceof BeastTitanEntity
         || target instanceof WarhammerTitanEntity
         || target instanceof OgreShifterTitanEntity
         || target instanceof TripleTTitanEntity
         || target instanceof TestShifterTitanEntity) {
         return false;
      } else if (target instanceof TitanEntity
         || target instanceof SmallTitanEntity
         || target instanceof SmallTitan2Entity
         || target instanceof FritzTitanEntity
         || target instanceof YellowTitanEntity
         || target instanceof SadTitanEntity
         || target instanceof CrawlerTitanEntity
         || target instanceof OgreTitanEntity
         || target instanceof AbnormalTitanEntity) {
         return false;
      } else if (target instanceof TitanNapeEntity || target instanceof TitanEyeEntity) {
         return false;
      } else if (target instanceof AttackTitanNapeEntity || target instanceof AttackTitanEyeEntity) {
         return false;
      } else if (target instanceof ArmoredTitanNapeEntity || target instanceof ArmoredTitanEyeEntity) {
         return false;
      } else if (target instanceof ColossalTitanNapeEntity || target instanceof ColossalTitanEyeEntity) {
         return false;
      } else if (target instanceof FemaleTitanNapeEntity || target instanceof FemaleTitanEyeEntity) {
         return false;
      } else if (target instanceof BeastTitanNapeEntity || target instanceof BeastTitanEyeEntity) {
         return false;
      } else if (target instanceof BeastTitanGrabEntity || target instanceof FemaleTitanGrabEntity || target instanceof AttackTitanGrabEntity) {
         return false;
      } else if (target instanceof FritzTitanNapeEntity || target instanceof FritzTitanEyeEntity) {
         return false;
      } else if (target instanceof SmallTitanNapeEntity || target instanceof SmallTitanEyeEntity) {
         return false;
      } else if (target instanceof SmallTitan2NapeEntity || target instanceof SmallTitan2EyeEntity) {
         return false;
      } else if (target instanceof OgreTitanNapeEntity || target instanceof OgreTitanEyeEntity) {
         return false;
      } else if (target instanceof SadTitanNapeEntity || target instanceof SadTitanEyeEntity) {
         return false;
      } else if (target instanceof YellowTitanNapeEntity || target instanceof YellowTitanEyeEntity) {
         return false;
      } else if (target instanceof CrawlerTitanNapeEntity || target instanceof CrawlerTitanEyeEntity) {
         return false;
      } else if (target instanceof WarhammerTitanNapeEntity || target instanceof WarhammerTitanEyeEntity) {
         return false;
      } else if (!(target instanceof ConnieFatherNapeEntity) && !(target instanceof ConnieFatherEyeEntity)) {
         Entity vehicle = target.getVehicle();
         return !(vehicle instanceof AttackTitanEntity)
            && !(vehicle instanceof ArmoredTitanEntity)
            && !(vehicle instanceof ColossalTitanEntity)
            && !(vehicle instanceof FemaleTitanEntity)
            && !(vehicle instanceof BeastTitanEntity)
            && !(vehicle instanceof WarhammerTitanEntity)
            && !(vehicle instanceof OgreShifterTitanEntity)
            && !(vehicle instanceof TripleTTitanEntity)
            && !(vehicle instanceof TestShifterTitanEntity)
            && !(vehicle instanceof CartShifterTitanEntity);
      } else {
         return false;
      }
   }

   private void tryGrabNearest() {
      if (!this.getWorld().isClient()) {
         if (this.grabHitboxEntity != null && !this.grabHitboxEntity.isRemoved()) {
            if (!this.isGrabbing() && !this.isThrowing() && !this.isEating()) {
               if (!this.isTransforming() && !this.isDismounting() && !this.isRoaring() && !this.isWireYanking()) {
                  LivingEntity controller = this.getControllingPassenger();
                  float yawDeg = controller != null ? controller.getYaw() : this.getYaw();
                  float yawRad = (float)Math.toRadians(yawDeg);
                  double fwdX = -Math.sin(yawRad);
                  double fwdZ = Math.cos(yawRad);
                  double searchRadius = 10.0;
                  Box searchBox = this.getBoundingBox().expand(searchRadius, 6.0, searchRadius).offset(fwdX * 4.0, 0.0, fwdZ * 4.0);
                  Entity bestTarget = null;
                  double bestDot = -1.0;
                  double bestDistSq = Double.MAX_VALUE;

                  for (Entity e : this.getWorld().getOtherEntities(this, searchBox)) {
                     if (canBeGrabbedByFemale(e) && e != controller) {
                        double dx = e.getX() - this.getX();
                        double dz = e.getZ() - this.getZ();
                        double horizDist = Math.sqrt(dx * dx + dz * dz);
                        if (!(horizDist < 0.001) && !(horizDist > searchRadius + 5.0)) {
                           double dot = (dx * fwdX + dz * fwdZ) / horizDist;
                           if (!(dot < 0.5)) {
                              double distSq = dx * dx + dz * dz;
                              if (dot > bestDot - 0.05 && distSq < bestDistSq) {
                                 bestTarget = e;
                                 bestDot = dot;
                                 bestDistSq = distSq;
                              }
                           }
                        }
                     }
                  }

                  if (bestTarget != null) {
                     this.grabEntity(bestTarget);
                  }
               }
            }
         }
      }
   }

   private void grabEntity(Entity target) {
      if (this.grabHitboxEntity != null && !this.grabHitboxEntity.isRemoved()) {
         if (target != this.grabHitboxEntity && target != this) {
            if (!this.grabHitboxEntity.hasPassenger(target) && !target.hasPassenger(this.grabHitboxEntity)) {
               this.setGrabbedEntityId(target.getId());
               this.setGrabPhase(1);
               double tx = this.grabHitboxEntity.getX();
               double ty = this.grabHitboxEntity.getY();
               double tz = this.grabHitboxEntity.getZ();
               if (target.getVehicle() != null) {
                  target.stopRiding();
               }

               target.setPosition(tx, ty, tz);
               target.prevX = tx;
               target.prevY = ty;
               target.prevZ = tz;
               target.startRiding(this.grabHitboxEntity, true);
               target.setVelocity(Vec3d.ZERO);
               if (target instanceof MobEntity mob) {
                  mob.setAiDisabled(true);
               }

               target.addCommandTag("dannys-aot:being_grabbed");
            }
         }
      }
   }

   public void releaseGrabbedEntity() {
      int grabbedId = this.getGrabbedEntityId();
      this.setGrabbedEntityId(-1);
      this.setGrabPhase(0);
      if (grabbedId != -1) {
         Entity grabbed = this.getWorld().getEntityById(grabbedId);
         if (grabbed != null) {
            if (grabbed.getVehicle() instanceof FemaleTitanGrabEntity grabHitbox) {
               grabHitbox.setDismountAllowed(true);
               grabbed.stopRiding();
               grabHitbox.setDismountAllowed(false);
            } else if (grabbed.getVehicle() == this) {
               grabbed.stopRiding();
            }

            grabbed.setNoGravity(false);
            if (grabbed instanceof MobEntity mob) {
               mob.setAiDisabled(false);
            }

            grabbed.removeScoreboardTag("dannys-aot:being_grabbed");
         }
      }
   }

   public void startThrowCharge() {
      if (!this.getWorld().isClient()) {
         if (this.isGrabbing()) {
            if (this.getGrabPhase() == 1) {
               this.setGrabPhase(2);
            }
         }
      }
   }

   public void cancelThrowCharge() {
      if (!this.getWorld().isClient()) {
         if (this.getGrabPhase() == 2) {
            this.setGrabPhase(1);
         }
      }
   }

   public void startThrow() {
      if (!this.getWorld().isClient()) {
         if (this.isGrabbing()) {
            if (this.getGrabPhase() == 2) {
               this.setGrabPhase(3);
               this.throwAnimTicks = 0;
            }
         }
      }
   }

   public void startEat() {
      if (!this.getWorld().isClient()) {
         if (this.isGrabbing()) {
            int phase = this.getGrabPhase();
            if (phase == 1 || phase == 2) {
               this.setGrabPhase(4);
               this.eatTicks = 0;
               this.eatDropDone = false;
               this.eatDamageDone = false;
               this.setVelocity(Vec3d.ZERO);
            }
         }
      }
   }

   private void launchGrabbedEntity() {
      int grabbedId = this.getGrabbedEntityId();
      if (grabbedId != -1) {
         Entity grabbed = this.getWorld().getEntityById(grabbedId);
         if (grabbed == null) {
            this.setGrabbedEntityId(-1);
         } else {
            float aimYawRad = (float)Math.toRadians(this.getYaw());
            double aimPitchRad = 0.0;
            if (this.getControllingPassenger() instanceof PlayerEntity player) {
               aimYawRad = (float)Math.toRadians(player.getYaw());
               aimPitchRad = Math.toRadians(player.getPitch());
            }

            double aimX = -Math.sin(aimYawRad) * Math.cos(aimPitchRad);
            double aimY = -Math.sin(aimPitchRad);
            double aimZ = Math.cos(aimYawRad) * Math.cos(aimPitchRad);
            double len = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
            if (len > 0.001) {
               aimX /= len;
               aimY /= len;
               aimZ /= len;
            }

            if (grabbed.getVehicle() instanceof FemaleTitanGrabEntity grabHitbox) {
               grabHitbox.setDismountAllowed(true);
               grabbed.stopRiding();
               grabHitbox.setDismountAllowed(false);
            } else if (grabbed.getVehicle() == this) {
               grabbed.stopRiding();
            }

            this.setGrabbedEntityId(-1);
            grabbed.removeScoreboardTag("dannys-aot:being_grabbed");
            if (grabbed instanceof MobEntity mob) {
               mob.setAiDisabled(false);
            }

            double horizLen = Math.sqrt(aimX * aimX + aimZ * aimZ);
            double launchX;
            double launchZ;
            if (horizLen > 0.001) {
               launchX = this.getX() + aimX / horizLen * 7.0;
               launchZ = this.getZ() + aimZ / horizLen * 7.0;
            } else {
               float fwdYaw = (float)Math.toRadians(this.getYaw());
               launchX = this.getX() + -Math.sin(fwdYaw) * 7.0;
               launchZ = this.getZ() + Math.cos(fwdYaw) * 7.0;
            }

            double launchY = this.getY() + 12.0;
            grabbed.noClip = false;
            grabbed.setNoGravity(false);
            grabbed.setPosition(launchX, launchY, launchZ);
            double launchSpeed = 4.2;
            Vec3d launchVel = new Vec3d(aimX * launchSpeed, aimY * launchSpeed + 0.8, aimZ * launchSpeed);
            grabbed.setVelocity(launchVel);
            grabbed.velocityDirty = true;
            if (grabbed instanceof LivingEntity living) {
               living.velocityModified = true;
            }

            if (grabbed instanceof ServerPlayerEntity thrownPlayer) {
               ServerPlayNetworking.send(thrownPlayer, new ODMJamPayload(2000));
            }
         }
      }
   }

   private void tickThrow() {
      if (this.getGrabPhase() == 3) {
         this.throwAnimTicks++;
         if (this.throwAnimTicks == 12) {
            this.launchGrabbedEntity();
         }

         if (this.throwAnimTicks >= 20) {
            this.setGrabPhase(0);
            this.throwAnimTicks = 0;
         }
      }
   }

   private void tickEat() {
      if (this.getGrabPhase() == 4) {
         this.eatTicks++;
         int grabbedId = this.getGrabbedEntityId();
         Entity grabbed = grabbedId == -1 ? null : this.getWorld().getEntityById(grabbedId);
         if (grabbed != null && grabbed.isAlive()) {
            if (this.eatTicks >= 24 && !this.eatDropDone) {
               this.eatDropDone = true;
               if (grabbed.getVehicle() instanceof FemaleTitanGrabEntity gh) {
                  gh.setDismountAllowed(true);
                  grabbed.stopRiding();
                  gh.setDismountAllowed(false);
               }
            }

            if (this.eatTicks >= 30 && !this.eatDamageDone) {
               this.eatDamageDone = true;
               if (grabbed instanceof LivingEntity living) {
                  living.damage(this.getDamageSources().mobAttack(this), 30.0F);
               }

               this.getWorld()
                  .playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 4.0F, 0.4F);
            }

            if (this.eatTicks >= 60) {
               grabbed.removeScoreboardTag("dannys-aot:being_grabbed");
               if (grabbed instanceof MobEntity mob) {
                  mob.setAiDisabled(false);
               }

               this.setGrabbedEntityId(-1);
               this.setGrabPhase(0);
               this.eatTicks = 0;
            }
         } else {
            this.setGrabbedEntityId(-1);
            this.setGrabPhase(0);
            this.eatTicks = 0;
         }
      }
   }

   public void triggerAbility(int abilityNumber) {
      if (!this.isDefeated()) {
         if (this.isKnocked()) {
            if (this.isKnockedHeld()) {
               this.releaseKnocked();
            }
         } else if (!this.isTransforming() && !this.isDismounting()) {
            if (abilityNumber == 6) {
               this.cycleHardeningMode();
            } else if (!this.isTitanClimbing()) {
               if (abilityNumber == 7) {
                  if (!this.isThrowing() && !this.isEating()) {
                     if (this.isGrabbing()) {
                        this.releaseGrabbedEntity();
                     } else {
                        this.tryGrabNearest();
                     }
                  }
               } else if (abilityNumber == 5 && this.protectActive) {
                  this.cancelProtect();
               } else if (abilityNumber == 3) {
                  this.triggerRoar();
               } else if (!this.isRoaring() && !this.isWireYanking()) {
                  if (this.protectActive) {
                     this.cancelProtect();
                  }

                  if (this.attackCooldown <= 0) {
                     this.setWasMovingOnAttackStart(this.isMoving());
                     switch (abilityNumber) {
                        case 1:
                           this.setAttackNumber(3);
                           this.setTitanAttacking(true);
                           this.attackAnimationTicks = 35;
                           this.attackCooldown = 35;
                           this.attackEffectTimer = 0;
                           this.attackEffectTriggered = false;
                           break;
                        case 2:
                           this.setAttackNumber(4);
                           this.setTitanAttacking(true);
                           this.attackAnimationTicks = 35;
                           this.attackCooldown = 35;
                           this.attackEffectTimer = 0;
                           this.attackEffectTriggered = false;
                        case 3:
                        case 6:
                        case 7:
                        case 8:
                        default:
                           break;
                        case 4:
                           this.setWireYanking(true);
                           this.wireYankTicks = 30;
                           this.attackCooldown = 40;
                           this.performShifterWireYank();
                           break;
                        case 5:
                           this.protectActive = true;
                           this.setProtecting(true);
                           this.setAttackNumber(5);
                           this.setTitanAttacking(true);
                           this.attackAnimationTicks = Integer.MAX_VALUE;
                           this.attackCooldown = Integer.MAX_VALUE;
                           this.attackEffectTimer = 0;
                           this.attackEffectTriggered = false;
                           break;
                        case 9:
                           this.triggerCrystalShell();
                     }
                  }
               }
            }
         }
      }
   }

   private void triggerRoar() {
      if (!this.isRoaring() && this.roarTicks <= 0) {
         if (this.getWorld().isClient()) {
            this.setRoaring(true);
         } else {
            this.setRoaring(true);
            this.setRoarStartTick(this.age);
            this.roarTicks = 254;
            this.roarSoundPlayed = false;
            this.roarCommandActive = true;
            VillagerTransformTracker.applyFemaleTitanRoar(this, 200.0);
            if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
               float current = ModNetworking.getStamina(sp.getUuid());
               ModNetworking.drainStamina(sp.getUuid(), current * 0.9F);
            }
         }
      }
   }

   private void clearRoarCommand() {
      if (this.roarCommandActive) {
         this.roarCommandActive = false;
         if (!this.getWorld().isClient()) {
            VillagerTransformTracker.clearFemaleTitanRoar(this);
         }
      }
   }

   private void cycleHardeningMode() {
      if (!this.getWorld().isClient()) {
         int next = (this.getHardeningMode() + 1) % 4;
         this.setHardeningMode(next);
         this.getWorld().playSound(null, this.getX(), this.getY() + 5.0, this.getZ(), SoundEvents.ENTITY_TURTLE_EGG_BREAK, SoundCategory.HOSTILE, 4.0F, 0.5F);
         if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
            ModNetworking.drainStamina(sp.getUuid(), 10.0F);
         }
      }
   }

   private void cancelProtect() {
      if (this.protectActive) {
         this.protectActive = false;
         this.setProtecting(false);
         this.setTitanAttacking(false);
         this.attackAnimationTicks = 0;
         this.attackCooldown = 6;
      }
   }

   private void performShifterWireYank() {
      if (this.getWorld() instanceof ServerWorld) {
         for (UUID playerUUID : new ArrayList<>(this.hookedPlayers)) {
            ServerPlayerEntity player = this.getWorld().getServer().getPlayerManager().getPlayer(playerUUID);
            if (player != null) {
               ServerPlayNetworking.send(player, new ODMJamPayload(5000, true));
            }
         }
      }
   }

   private void triggerCrystalShell() {
      if (!this.getWorld().isClient()) {
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            if (this.getFirstPassenger() instanceof ServerPlayerEntity player) {
               UUID shifterUUID = this.getShifterUUID();
               if (shifterUUID != null) {
                  float yawRad = (float)Math.toRadians(this.getYaw());
                  double napeX = this.getX() + Math.sin(yawRad) * 1.5;
                  double napeY = this.getY() + 12.0;
                  double napeZ = this.getZ() + -Math.cos(yawRad) * 1.5;
                  player.stopRiding();
                  player.setInvisible(false);
                  this.allowDismount = true;
                  this.setDismounting(true);
                  this.despawnAtGameTime = this.getWorld().getTime() + 1200L;
                  FemaleCrystalShellEntity crystal = new FemaleCrystalShellEntity(DannysAot.CRYSTAL_SHELL_FEMALE, this.getWorld());
                  crystal.setPosition(napeX, napeY, napeZ);
                  crystal.setShifterUUID(shifterUUID);
                  crystal.setRemainingTicks(ModConfig.get().femaleCrystalDurationSeconds * 20);
                  this.getWorld().spawnEntity(crystal);
                  player.startRiding(crystal, true);
                  player.setInvulnerable(true);
                  this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 3.0F, 0.4F);
                  this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 4.0F, 0.6F);
                  serverLevel.spawnParticles(ParticleTypes.SNOWFLAKE, napeX, napeY + 1.0, napeZ, 40, 1.0, 1.5, 1.0, 0.02);
                  serverLevel.spawnParticles(ParticleTypes.END_ROD, napeX, napeY + 0.5, napeZ, 20, 0.8, 1.2, 0.8, 0.03);
                  DannysAot.LOGGER.info("Female Titan crystal shell spawned at ({}, {}, {})", new Object[]{napeX, napeY, napeZ});
               }
            }
         }
      }
   }

   private void performGroundsmash() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         byte smashRadius = 3;
         byte smashDepth = 2;
         byte smashDistance = 5;
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
                           if (this.random.nextFloat() < 0.3F) {
                              this.flingBlockFromGroundsmash(serverLevel, pos, blockState, forwardX, forwardZ);
                           } else {
                              this.getWorld().removeBlock(pos, false);
                           }
                        }
                     }
                  }
               }
            }
         }

         this.getWorld().playSound(null, centerX, (double)groundY, centerZ, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.5F);
      }
   }

   private void flingBlockFromGroundsmash(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
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
                           this.flingBlockFromGroundsmash(serverLevel, pos, blockState, forwardX, forwardZ);
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

   private void flingBlocksFromShiftExplosion(ServerWorld serverLevel, double cx, double cy, double cz) {
      int radius = 10;
      int flung = 0;
      int maxFlung = 35;

      for (int dx = -radius; dx <= radius && flung < maxFlung; dx++) {
         for (int dz = -radius; dz <= radius && flung < maxFlung; dz++) {
            for (int dy = -2; dy <= 12 && flung < maxFlung; dy++) {
               if (!(this.random.nextFloat() > 0.15F)) {
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  if (!(dist > radius) && !(dist < 3.0)) {
                     BlockPos pos = new BlockPos((int)Math.floor(cx + dx), (int)Math.floor(cy + dy), (int)Math.floor(cz + dz));
                     BlockState blockState = this.getWorld().getBlockState(pos);
                     if (!blockState.isAir()
                        && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                        && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                        && (!(blockState.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                        this.getWorld().removeBlock(pos, false);
                        double dirX = dx / dist;
                        double dirZ = dz / dist;
                        double velX = dirX * (0.8 + this.random.nextDouble() * 0.8) + (this.random.nextDouble() - 0.5) * 0.3;
                        double velY = 0.4 + this.random.nextDouble() * 0.9;
                        double velZ = dirZ * (0.8 + this.random.nextDouble() * 0.8) + (this.random.nextDouble() - 0.5) * 0.3;
                        FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                        ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                        fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        fallingBlock.setFallingBlockPos(pos);
                        fallingBlock.setVelocity(velX, velY, velZ);
                        fallingBlock.timeFalling = blockState.getBlock() instanceof FluidBlock ? 590 : 1;
                        fallingBlock.dropItem = false;
                        serverLevel.spawnEntity(fallingBlock);
                        flung++;
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
            double sideOffset = (1.0 - swingProgress) * 5.0;
            double forwardDist = 3.0 + swingProgress * 7.0;
            double crouchOffset = this.isInSneakingPose() ? 5.0 : 0.0;
            double armHeight = 11.0 - crouchOffset - swingProgress * 4.0;
            int baseY = (int)Math.floor(this.getY() + armHeight);
            int handRadius = 2;
            int forearmRadius = 4;
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
                              this.flingBlockFromGroundsmash(serverLevel, pos, blockState, forwardX, forwardZ);
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

   private void performKick() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      Vec3d kickCenter = this.getPos().add(forwardX * 5.0, 3.0, forwardZ * 5.0);

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(10.0, 8.0, 10.0))) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof FemaleTitanNapeEntity)
               && !(target instanceof FemaleTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                     )
               )) {
               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
               if (dot > 0.5 && toTarget.horizontalLength() < 10.0) {
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
                     float kickDamage = isShifterTitan(target)
                        ? (float)(ModConfig.get().femaleTitanKickDamage / 2.0)
                        : (float)ModConfig.get().femaleTitanKickDamage;
                     if (isShifterTitan(target) || isPureTitan(target)) {
                        kickDamage *= 3.0F;
                     }

                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        kickDamage *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), kickDamage);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                     if (target instanceof TitanEntity titanTarget) {
                        Vec3d knockback = horizontalDir.multiply(40.0).add(0.0, 30.0, 0.0);
                        titanTarget.applyKnockback(knockback);
                     } else if (isShifterTitan(target)) {
                        Vec3d knockback = horizontalDir.multiply(armedBlock ? 3.0 : 6.0).add(0.0, armedBlock ? 1.5 : 3.0, 0.0);
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
                        }
                     } else if (target instanceof PlayerEntity playerTarget) {
                        Vec3d knockback = horizontalDir.multiply(4.0).add(0.0, 2.5, 0.0);
                        playerTarget.setVelocity(knockback);
                        playerTarget.velocityModified = true;
                        playerTarget.velocityDirty = true;
                     } else {
                        Vec3d knockback = horizontalDir.multiply(4.0).add(0.0, 2.5, 0.0);
                        target.setVelocity(knockback);
                        target.velocityModified = true;
                     }
                  }
               }
            }
         }
      }

      this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0F, 0.6F);
      if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
         ModNetworking.drainStamina(sp.getUuid(), 45.0F);
      }
   }

   private void dealAttackDamage() {
      SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
      SoundEvent sound = impactSounds[this.random.nextInt(impactSounds.length)];
      float pitch = 0.9F + this.random.nextFloat() * 0.1F;
      float yaw = (float)Math.toRadians(this.getYaw());
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      this.getWorld().playSound(null, this.getX() + fx * 6.0, this.getY() + 3.0, this.getZ() + fz * 6.0, sound, SoundCategory.HOSTILE, 8.0F, pitch);
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      Vec3d attackCenter = this.getPos().add(forwardX * 6.0, 5.0, forwardZ * 6.0);

      for (LivingEntity target : this.getWorld()
         .getNonSpectatingEntities(
            LivingEntity.class, this.getBoundingBox().expand((int)ModConfig.get().femaleTitanAttackRange, 10.0, (int)ModConfig.get().femaleTitanAttackRange)
         )) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof FemaleTitanNapeEntity)
               && !(target instanceof FemaleTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                     )
               )) {
               int curAttackNum = this.getAttackNumber();
               if (curAttackNum == 10 || curAttackNum == 11) {
                  double minTargetTopY = this.getY() + (this.isInSneakingPose() ? 0.0 : 4.0);
                  if (target.getY() + target.getHeight() < minTargetTopY) {
                     continue;
                  }
               }

               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
               if (dot > 0.2 && toTarget.horizontalLength() < ModConfig.get().femaleTitanAttackRange) {
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
                     float attackDamage = isShifterTitan(target)
                        ? (
                           this.getAttackNumber() == 3
                              ? (float)(ModConfig.get().femaleTitanAttackDamage * 2.0 / 3.0)
                              : (float)(ModConfig.get().femaleTitanAttackDamage / 3.0)
                        )
                        : (float)ModConfig.get().femaleTitanAttackDamage;
                     if ((curAttackNum == 10 || curAttackNum == 11) && (isShifterTitan(target) || isPureTitan(target))) {
                        attackDamage *= 2.0F;
                     }

                     if (this.isHardenedFeet() && curAttackNum == 4) {
                        attackDamage *= 2.0F;
                     }

                     if (this.isHardenedArms()
                        && (curAttackNum == 1 || curAttackNum == 2 || curAttackNum == 6 || curAttackNum == 7 || curAttackNum == 10 || curAttackNum == 11)) {
                        attackDamage *= 2.0F;
                     }

                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        attackDamage *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, attackDamage));
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     if (target instanceof TitanEntity titanTarget) {
                        Vec3d knockback = toTarget.normalize().multiply(5.0, 2.0, 5.0);
                        titanTarget.applyKnockback(knockback);
                     } else if (target instanceof FritzTitanEntity fritzTarget) {
                        Vec3d knockback = toTarget.normalize().multiply(5.0, 2.0, 5.0);
                        fritzTarget.applyKnockback(knockback);
                     } else if (target instanceof SmallTitanEntity || target instanceof SmallTitan2Entity) {
                        Vec3d knockback = toTarget.normalize().multiply(3.0, 1.5, 3.0);
                        target.setVelocity(knockback);
                        target.velocityModified = true;
                     } else if (isShifterTitan(target)) {
                        Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                        Vec3d knockback = horizontalDir.multiply(armedBlock ? 1.5 : 3.0).add(0.0, armedBlock ? 0.5 : 1.0, 0.0);
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
                        }
                     } else {
                        Vec3d knockback = toTarget.normalize().multiply(1.5, 0.5, 1.5);
                        target.setVelocity(target.getVelocity().add(knockback));
                     }
                  }
               }
            }
         }
      }

      if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
         boolean isAbility = this.getAttackNumber() == 3 || this.getAttackNumber() == 4;
         float drainAmount = isAbility ? 15.0F : 5.0F;
         if (this.isHardened()) {
            drainAmount *= 2.0F;
         }

         ModNetworking.drainStamina(sp.getUuid(), drainAmount);
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

   private static boolean isPureTitan(LivingEntity target) {
      return target instanceof TitanEntity || target instanceof SmallTitanEntity || target instanceof SmallTitan2Entity || target instanceof FritzTitanEntity;
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
               && !this.isProtecting()) {
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

   @Override
   public void travel(Vec3d movementInput) {
      if (!this.isImpaled()) {
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
         } else {
            this.jumpVelocityApplied = false;
            if (!this.isTouchingWater() && !this.isInLava()) {
               super.travel(movementInput);
               if (this.isTitanClimbing() && this.isLogicalSideForUpdatingMovement()) {
                  double t = this.climbAnimTicks % 60 / 20.0;
                  boolean inPause = t >= 0.75 && t < 1.5 || t >= 2.25 && t < 3.0;
                  double targetY = inPause ? 0.0 : 0.375;
                  float directionYaw = Float.isNaN(this.climbTargetYaw) ? this.getYaw() : this.climbTargetYaw;
                  float yawRad = (float)Math.toRadians(directionYaw);
                  double pushX = -MathHelper.sin(yawRad) * 0.35;
                  double pushZ = MathHelper.cos(yawRad) * 0.35;
                  this.setVelocity(pushX, targetY, pushZ);
                  this.fallDistance = 0.0F;
               }
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
      }
   }

   @Override
   public boolean isClimbing() {
      return this.isTitanClimbing() || super.isClimbing();
   }

   private boolean detectWallInFront() {
      if (this.getControllingPassenger() instanceof PlayerEntity rider) {
         if (rider.forwardSpeed <= 0.01F) {
            return false;
         } else {
            float yawRad = (float)Math.toRadians(rider.getYaw());
            double dirX = -MathHelper.sin(yawRad);
            double dirZ = MathHelper.cos(yawRad);
            double probeX = this.getX() + dirX * 2.0;
            double probeZ = this.getZ() + dirZ * 2.0;
            double[] probeYs = new double[]{0.5, 2.5, 4.5, 6.5};

            for (double dy : probeYs) {
               BlockPos pos = BlockPos.ofFloored(probeX, this.getY() + dy, probeZ);
               if (this.getWorld().getBlockState(pos).isSolid()) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
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
      if (!this.getWorld().isClient() && fallDistance > 6.0F) {
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
                     target.velocityModified = true;
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
                           BlockPos pos = new BlockPos((int)Math.floor(this.getX()) + dx, groundY + dy, (int)Math.floor(this.getZ()) + dz);
                           BlockState blockState = this.getWorld().getBlockState(pos);
                           if (!blockState.isAir()
                              && !blockState.isIn(BlockTags.FEATURES_CANNOT_REPLACE)
                              && blockState.getHardness(this.getWorld(), pos) >= 0.0F
                              && blockState.getHardness(this.getWorld(), pos) <= 50.0F) {
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

         int particleCount = (int)(70.0F + impactStrength * 30.0F);

         for (int i = 0; i < particleCount; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double speed = 0.3 + this.random.nextDouble() * (0.4 + impactStrength * 0.2);
            double px = this.getX() + (this.random.nextDouble() - 0.5) * 3.0;
            double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 3.0;
            BlockPos below = new BlockPos((int)Math.floor(px), (int)Math.floor(this.getY()) - 1, (int)Math.floor(pz));
            BlockState belowState = this.getWorld().getBlockState(below);
            if (!belowState.isAir()) {
               serverLevel.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, belowState),
                  px,
                  this.getY() + 0.1,
                  pz,
                  0,
                  Math.cos(angle) * speed,
                  0.2 + this.random.nextDouble() * 0.3,
                  Math.sin(angle) * speed,
                  1.0
               );
            }
         }
      }
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
         || attacker instanceof FemaleTitanEntity
         || attacker instanceof ArmoredTitanEntity
         || attacker instanceof ColossalTitanEntity
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
         if (this.isGrabbing()) {
            this.releaseGrabbedEntity();
         }

         if (this.isDismounting()) {
            this.onDeath(source);
         } else {
            float chargeFraction = BladeAttackTracker.consumeNapeChargeFraction();
            if (!(chargeFraction <= 0.0F)) {
               float perHitDamage = this.getMaxHealth() / Math.max(1, ModConfig.get().femaleTitanNapeHits);
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
      if (!this.getWorld().isClient() && this.isGrabbing()) {
         this.releaseGrabbedEntity();
      }

      this.blindnessTicks = 120;
      DannysAot.LOGGER.info("Female Titan blinded for 6 seconds!");
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
         boolean isFront;
         if (forceFront) {
            isFront = true;
         } else if (forceBack) {
            isFront = false;
         } else {
            isFront = dotForward >= 0.0;
         }

         boolean isRight = dotRight < 0.0;
         int reaction;
         if (isFront && !isRight) {
            reaction = 1;
         } else if (isFront && isRight) {
            reaction = 2;
         } else if (!isFront && !isRight) {
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
      this.clearRoarCommand();
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

      if (this.grabHitboxEntity != null && !this.grabHitboxEntity.isRemoved()) {
         this.grabHitboxEntity.discard();
      }

      this.releaseGrabbedEntity();
      super.remove(reason);
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient() && !this.configHealthApplied) {
         this.configHealthApplied = true;
         double configHealth = ModConfig.get().femaleTitanHealth;
         this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(configHealth);
         this.setHealth((float)configHealth);
      }

      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         this.spawnHitboxes();
      }

      if (!this.getWorld().isClient()) {
         boolean canClimbNow = this.isHardenedArms()
            && !this.isImpaled()
            && !this.isDismounting()
            && !this.isTransforming()
            && !this.isJumping()
            && this.jumpAnimTicks <= 0
            && !this.isGrabbing()
            && !this.isThrowing()
            && !this.isThrowCharging()
            && !this.isEating()
            && this.detectWallInFront();
         if (canClimbNow != this.isTitanClimbing()) {
            this.setTitanClimbing(canClimbNow);
         }
      }

      if (this.isTitanClimbing()) {
         this.climbAnimTicks++;
      } else {
         this.climbAnimTicks = 0;
         this.climbTargetYaw = Float.NaN;
      }

      if (!this.getWorld().isClient()) {
         int grabbedId = this.getGrabbedEntityId();
         if (grabbedId != -1) {
            Entity grabbed = this.getWorld().getEntityById(grabbedId);
            if (grabbed == null || !grabbed.isAlive()) {
               this.setGrabbedEntityId(-1);
               this.setGrabPhase(0);
            }
         }

         this.tickThrow();
         this.tickEat();
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

      if (this.dismountToggleCooldown > 0) {
         this.dismountToggleCooldown--;
      }

      if (this.blindnessTicks > 0) {
         this.blindnessTicks--;
      }

      if (!this.getWorld().isClient() && this.roarTicks > 0) {
         this.roarTicks--;
         int elapsed = 254 - this.roarTicks;
         if (!this.roarSoundPlayed && elapsed >= 10) {
            this.roarSoundPlayed = true;
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.FEMALE_TITAN_ROAR, SoundCategory.HOSTILE, 10.0F, 1.0F);
         }

         if (this.roarTicks <= 0) {
            this.setRoaring(false);
         }
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

      if (this.jumpAnimTicks > 0) {
         this.jumpAnimTicks--;
         int ticksElapsed = 10 - this.jumpAnimTicks;
         if (ticksElapsed >= 4 && !this.jumpLaunched) {
            this.jumpLaunched = true;
            float jumpMult = this.isHardenedFeet() ? 1.2F : 1.0F;
            this.jumpVelX = this.jumpDirX * 3.6F * jumpMult;
            this.jumpVelY = 4.5F * jumpMult;
            this.jumpVelZ = this.jumpDirZ * 3.6F * jumpMult;
            if (!this.getWorld().isClient()) {
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 6.0F, 0.7F);
               this.setLastStompTick(this.age);
            }
         }
      }

      if (this.isAlive() && !this.isTransforming()) {
         if (this.isOnGround()) {
            this.continuousAirborneTicks = 0;
            this.continuousAirborneTicksClient = 0;
         } else {
            this.continuousAirborneTicks++;
            this.continuousAirborneTicksClient++;
         }

         if (this.getWorld().isClient()) {
            if (this.clientJumpActive && this.jumpLaunched && this.jumpAnimTicks <= 0 && this.getVelocity().y < -0.1) {
               this.clientJumpActive = false;
               this.clientFallActive = true;
               this.jumpLaunched = false;
               this.jumpVelocityApplied = false;
            }

            if (!this.clientJumpActive
               && !this.clientFallActive
               && !this.isOnGround()
               && !this.isLanding()
               && this.continuousAirborneTicksClient >= 10
               && this.fallDistance >= 6.0F) {
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

            if (!this.isOnGround()
               && !this.isLanding()
               && !this.isJumping()
               && !this.isFalling()
               && this.continuousAirborneTicks >= 10
               && this.fallDistance >= 6.0F) {
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

      if (!this.getWorld().isClient() && this.bossBar != null) {
         if (!this.isDefeated() && this.getHealth() < this.getMaxHealth()) {
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

      if (!this.getWorld().isClient() && this.isDefeated() && this.deathAnimTicks >= 0) {
         this.deathAnimTicks++;
         if (this.deathAnimTicks == 15 || this.deathAnimTicks == 36) {
            this.triggerStompEffects();
            this.setLastStompTick(this.age);
         }
      }

      if (!this.getWorld().isClient() && this.isIncapacitated() && !this.isDefeated() && this.incapacitateTicks > 0) {
         this.incapacitateTicks--;
         if (this.incapacitateTicks <= 0) {
            this.recover();
         }
      }

      if (!this.getWorld().isClient() && this.isDefeated() && this.despawnAtGameTime < 0L) {
         this.despawnAtGameTime = this.getWorld().getTime() + 1200L;
      }

      if (!this.getWorld().isClient() && this.despawnAtGameTime >= 0L) {
         if (this.getShifterUUID() != null) {
            this.despawnAtGameTime = -1L;
         } else if (this.getWorld().getTime() >= this.despawnAtGameTime) {
            this.discard();
            return;
         }
      }

      if (!this.getWorld().isClient() && this.dismountVisibilityDelay > 0) {
         this.dismountVisibilityDelay--;
         if (this.dismountVisibilityDelay <= 0 && this.isDismounting() && this.getControllingPassenger() instanceof PlayerEntity player) {
            player.setInvisible(false);
         }
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
         this.setShifterUUID(null);
      }

      if (!this.getWorld().isClient()) {
         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
            if (this.attackAnimationTicks <= 0) {
               this.setTitanAttacking(false);
            }
         }

         if (this.isWireYanking()) {
            this.wireYankTicks--;
            int elapsedx = 30 - this.wireYankTicks;
            if (elapsedx == 14) {
               this.getWorld().playSound(null, this.getX(), this.getY() + 5.0, this.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0F, 0.5F);
            }

            if (this.wireYankTicks % 5 == 0) {
               this.performShifterWireYank();
            }

            if (this.wireYankTicks <= 0) {
               this.setWireYanking(false);
            }
         }

         if (this.isTitanAttacking() && !this.attackEffectTriggered && this.getAttackNumber() != 5) {
            this.attackEffectTimer++;
            int attackNum = this.getAttackNumber();

            int effectTick = switch (attackNum) {
               case 1 -> 14;
               case 2 -> 12;
               case 3 -> 14;
               case 4 -> 18;
               default -> 10;
               case 6, 7 -> 8;
               case 10, 11 -> 10;
            };
            if ((attackNum == 1 || attackNum == 2) && this.attackEffectTimer >= 10) {
               float swingProgress = (float)(this.attackEffectTimer - 10) / (effectTick - 10);
               this.destroyBlocksAlongSwing(Math.min(swingProgress, 1.0F));
            }

            if (this.attackEffectTimer >= effectTick) {
               if (attackNum == 4) {
                  this.performKick();
               } else {
                  this.dealAttackDamage();
                  if (attackNum == 3) {
                     this.performGroundsmash();
                  }
               }

               this.setLastAttackImpactTick(this.age);
               this.attackEffectTriggered = true;
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

         boolean isActuallyMoving = this.getVelocity().horizontalLengthSquared() > 0.001;
         isActuallyMoving = isActuallyMoving || this.isMoving();
         if (this.getControllingPassenger() instanceof PlayerEntity controllingPlayer) {
            boolean hasInput = Math.abs(controllingPlayer.forwardSpeed) > 0.01 || Math.abs(controllingPlayer.sidewaysSpeed) > 0.01;
            isActuallyMoving = isActuallyMoving || hasInput;
         }

         isActuallyMoving = isActuallyMoving && !this.isTransforming();
         if (isActuallyMoving && !this.wasMovingLastTick) {
            this.walkStartTick = this.age;
            this.lastStompKeyframeIndex = -1;
         }

         this.wasMovingLastTick = isActuallyMoving;
         int stompAttackNum = this.getAttackNumber();
         boolean isFullBodyAttack = this.isTitanAttacking() && (stompAttackNum == 3 || stompAttackNum == 4 || stompAttackNum == 5);
         if (isActuallyMoving && this.stompCooldown <= 0 && !isFullBodyAttack) {
            long walkingTicks = this.age - this.walkStartTick;
            if (walkingTicks >= 5L) {
               boolean isRunning = this.isSprinting() && !this.isArmed();
               double ticksPerCycle = isRunning ? 10.0 : 29.165999999999997;
               double currentAnimTime = walkingTicks % ticksPerCycle / 20.0;
               int keyframeIndex = this.getStompKeyframeIndex(currentAnimTime, isRunning);
               if (keyframeIndex >= 0 && keyframeIndex != this.lastStompKeyframeIndex) {
                  this.triggerStompEffects();
                  this.setLastStompTick(this.age);
                  this.lastStompKeyframeIndex = keyframeIndex;
                  this.stompCooldown = isRunning ? 3 : 10;
               }

               if (keyframeIndex < 0) {
                  this.lastStompKeyframeIndex = -1;
               }
            }
         }

         if (this.isSprinting() && !this.isArmed() && this.random.nextFloat() < 0.15F) {
            this.spawnKickedBlock();
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

         if (this.isOnGround() && this.getWorld() instanceof ServerWorld serverLevel) {
            BlockState groundBlock = this.getWorld().getBlockState(this.getBlockPos().down());
            if (!groundBlock.isAir()) {
               serverLevel.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, groundBlock), this.getX(), this.getY() + 0.2, this.getZ(), 8, 1.5, 0.3, 1.5, 0.1
               );
            }
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

         if (slidePhase && this.isOnGround() && this.getWorld() instanceof ServerWorld serverLevelx) {
            BlockState groundBlock = this.getWorld().getBlockState(this.getBlockPos().down());
            if (!groundBlock.isAir()) {
               serverLevelx.spawnParticles(
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
   }

   private void spawnKickedBlock() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            double offsetX = (this.random.nextDouble() - 0.5) * 2.0;
            double offsetZ = (this.random.nextDouble() - 0.5) * 2.0;
            BlockPos blockPos = new BlockPos((int)Math.floor(this.getX() + offsetX), (int)Math.floor(this.getY() - 1.0), (int)Math.floor(this.getZ() + offsetZ));
            BlockState blockState = this.getWorld().getBlockState(blockPos);
            if (!blockState.isAir() && blockState.isSolid()) {
               double spawnX = blockPos.getX() + 0.5;
               double spawnY = blockPos.getY() + 0.5;
               double spawnZ = blockPos.getZ() + 0.5;
               Vec3d movement = this.getVelocity();
               double backX = -movement.x * 3.0;
               double backZ = -movement.z * 3.0;
               double velX = backX + (this.random.nextDouble() - 0.5) * 0.8;
               double velY = 0.8 + this.random.nextDouble() * 0.4;
               double velZ = backZ + (this.random.nextDouble() - 0.5) * 0.8;
               FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
               ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
               fallingBlock.setPosition(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
               fallingBlock.setFallingBlockPos(blockPos);
               fallingBlock.setVelocity(velX, velY, velZ);
               fallingBlock.timeFalling = 1;
               fallingBlock.dropItem = false;
               serverLevel.spawnEntity(fallingBlock);
            }
         }
      }
   }

   private int getStompKeyframeIndex(double animTime) {
      return this.getStompKeyframeIndex(animTime, false);
   }

   private int getStompKeyframeIndex(double animTime, boolean isRunning) {
      double[] keyframes = isRunning ? RUN_STOMP_KEYFRAMES : STOMP_KEYFRAMES;
      double cycleLength = isRunning ? 0.5 : 1.4583;
      double cycleTime = animTime % cycleLength;

      for (int i = 0; i < keyframes.length; i++) {
         double diff = Math.abs(cycleTime - keyframes[i]);
         if (diff < 0.08) {
            return i;
         }
      }

      return -1;
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

         BlockPos feetPos = this.getBlockPos();
         BlockPos groundPos = feetPos.down();
         BlockState groundState = this.getWorld().getBlockState(groundPos);
         double footX = this.getX();
         double footZ = this.getZ();
         if (!groundState.isAir()) {
            double particleRadius = 50.0;

            for (ServerPlayerEntity playerx : serverLevel.getPlayers()) {
               if (playerx.squaredDistanceTo(footX, this.getY(), footZ) < particleRadius * particleRadius) {
                  for (int i = 0; i < 50; i++) {
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
                        playerx,
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

                  for (int i = 0; i < 30; i++) {
                     double offsetX = (this.random.nextDouble() - 0.5) * 1.2;
                     double offsetZ = (this.random.nextDouble() - 0.5) * 1.2;
                     double spawnY = this.getY() + this.random.nextDouble() * 0.3;
                     Vec3d movement = this.getVelocity();
                     double backX = -movement.x * 1.5;
                     double backZ = -movement.z * 1.5;
                     double velX = backX + (this.random.nextDouble() - 0.5) * 0.3;
                     double velY = 0.15 + this.random.nextDouble() * 0.3;
                     double velZ = backZ + (this.random.nextDouble() - 0.5) * 0.3;
                     serverLevel.spawnParticles(playerx, ParticleTypes.ASH, true, footX + offsetX, spawnY, footZ + offsetZ, 0, velX, velY, velZ, 0.06);
                  }

                  for (int i = 0; i < 15; i++) {
                     double offsetX = (this.random.nextDouble() - 0.5) * 1.0;
                     double offsetZ = (this.random.nextDouble() - 0.5) * 1.0;
                     serverLevel.spawnParticles(
                        playerx, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, footX + offsetX, this.getY() + 0.2, footZ + offsetZ, 0, 0.0, 0.05, 0.0, 0.02
                     );
                  }
               }
            }
         }

         boolean isRunningForSound = this.isSprinting() && !this.isArmed();
         float stompVolume = isRunningForSound ? 8.0F : 5.3F;
         if (this.isInSneakingPose()) {
            stompVolume *= 0.5F;
         }

         float randomPitch = 0.5F + this.random.nextFloat() * 0.1F;
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, stompVolume, randomPitch);
      }
   }

   private void breakBlocksInPath() {
      BlockPos feetPos = this.getBlockPos();
      int height = (int)Math.ceil(this.getHeight());

      for (int y = 0; y < height; y++) {
         BlockPos checkPos = feetPos.up(y);
         BlockState state = this.getWorld().getBlockState(checkPos);
         if (!state.isAir() && state.getHardness(this.getWorld(), checkPos) >= 0.0F && !state.isIn(BlockTags.WITHER_IMMUNE)) {
            this.getWorld().breakBlock(checkPos, true);
         }
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement_controller", 6, this::movementPredicate));
      controllers.add(new AnimationController(this, "action_controller", 6, this::actionPredicate));
      controllers.add(new AnimationController(this, "grab_arm_controller", 4, this::grabArmPredicate));
   }

   private PlayState grabArmPredicate(AnimationState<FemaleTitanEntity> state) {
      if (this.isTitanClimbing()) {
         return PlayState.STOP;
      } else {
         int phase = this.getGrabPhase();
         return phase == 1 ? state.setAndContinue(HOLD_ANIM) : PlayState.STOP;
      }
   }

   private PlayState movementPredicate(AnimationState<FemaleTitanEntity> state) {
      state.getController().transitionLength(this.crouchTransitionTicksRemaining > 0 ? 4 : 7);
      state.getController().setAnimationSpeed(1.0);
      if (this.isDefeated()) {
         return PlayState.STOP;
      } else if (this.isTransforming() || this.isDismounting()) {
         return PlayState.STOP;
      } else if (this.isKnocked()) {
         return PlayState.STOP;
      } else if (this.isJumpingForAnim()) {
         return state.setAndContinue(JUMP_ANIM);
      } else if (this.isFallingForAnim()) {
         return state.setAndContinue(FALLING_ANIM);
      } else if (this.isLanding()) {
         return state.setAndContinue(LAND_ANIM);
      } else if (this.isTitanClimbing()) {
         return state.setAndContinue(CLIMB_ANIM);
      } else if (!this.isInSneakingPose()) {
         int attackNum = this.getAttackNumber();
         if (!this.isTitanAttacking() || attackNum != 3 && attackNum != 4 && attackNum != 5) {
            if (this.isThrowCharging() || this.isThrowing() || this.isEating()) {
               return state.setAndContinue(IDLE_ANIM);
            } else if (this.isWireYanking()) {
               return state.setAndContinue(this.isMoving() ? WALK_ANIM : IDLE_ANIM);
            } else if (!this.isMoving()) {
               return state.setAndContinue(IDLE_ANIM);
            } else {
               return this.isSprinting() && !this.isArmed() ? state.setAndContinue(RUN_ANIM) : state.setAndContinue(WALK_ANIM);
            }
         } else {
            return state.setAndContinue(IDLE_ANIM);
         }
      } else {
         return this.isMoving() ? state.setAndContinue(CROUCH_WALK_ANIM) : state.setAndContinue(CROUCH_IDLE_ANIM);
      }
   }

   private PlayState actionPredicate(AnimationState<FemaleTitanEntity> state) {
      state.getController().transitionLength(this.crouchTransitionTicksRemaining > 0 ? 4 : 7);
      if (this.isDefeated()) {
         return state.setAndContinue(DEATH_ANIM);
      } else if (this.isTransforming()) {
         return state.setAndContinue(SHIFT_ANIM);
      } else if (this.isKnocked()) {
         return state.setAndContinue(KNOCKED_ANIM);
      } else if (this.isTitanClimbing()) {
         return PlayState.STOP;
      } else if (this.isImpaled() && !this.isDismounting() && !this.isTitanAttacking()) {
         return state.setAndContinue(IMPALED_ANIM);
      } else if (this.isLanding()) {
         state.getController().transitionLength(2);
         return state.setAndContinue(LAND_ANIM);
      } else if (this.isJumpingForAnim()) {
         state.getController().transitionLength(2);
         return state.setAndContinue(JUMP_ANIM);
      } else if (this.isFallingForAnim()) {
         state.getController().transitionLength(4);
         return state.setAndContinue(FALLING_ANIM);
      } else if (this.isRoaring()) {
         return state.setAndContinue(ROAR_ANIM);
      } else if (this.isWireYanking()) {
         return state.setAndContinue(WIRE_YANK_ANIM);
      } else if (this.isThrowCharging()) {
         return state.setAndContinue(THROW_CHARGE_ANIM);
      } else if (this.isThrowing()) {
         return state.setAndContinue(THROW_ANIM);
      } else if (this.isEating()) {
         return state.setAndContinue(EAT_ANIM);
      } else if (this.isTitanAttacking()) {
         int currentAttackNumber = this.getAttackNumber();
         boolean wasMoving = this.wasMovingOnAttackStart();

         return switch (currentAttackNumber) {
            case 1 -> state.setAndContinue(ATTACK1_ANIM);
            case 2 -> state.setAndContinue(ATTACK2_ANIM);
            case 3 -> state.setAndContinue(wasMoving ? GROUNDSMASH_ANIM : GROUNDSMASH_IDLE_ANIM);
            case 4 -> state.setAndContinue(KICK_ANIM);
            case 5 -> state.setAndContinue(PROTECT_ANIM);
            case 6 -> state.setAndContinue(UPPERCUT_L_ANIM);
            case 7 -> state.setAndContinue(UPPERCUT_R_ANIM);
            default -> state.setAndContinue(ATTACK1_ANIM);
            case 10 -> state.setAndContinue(JAB_L_ANIM);
            case 11 -> state.setAndContinue(JAB_R_ANIM);
         };
      } else {
         int hitReaction = this.dataTracker.get(DATA_HIT_REACTION);
         if (hitReaction > 0) {
            return switch (hitReaction) {
               case 1 -> state.setAndContinue(HIT_FRONT_LEFT_ANIM);
               case 2 -> state.setAndContinue(HIT_FRONT_RIGHT_ANIM);
               case 3 -> state.setAndContinue(HIT_BACK_LEFT_ANIM);
               case 4 -> state.setAndContinue(HIT_BACK_RIGHT_ANIM);
               default -> state.setAndContinue(HIT_FRONT_LEFT_ANIM);
            };
         } else if (this.isDismounting()) {
            return state.setAndContinue(DISMOUNT_ANIM);
         } else if (this.isArmed()) {
            return this.isInSneakingPose() ? state.setAndContinue(CROUCH_ARMED_ANIM) : state.setAndContinue(ARMED_IDLE_ANIM);
         } else if (this.isInSneakingPose()) {
            return this.isMoving() ? state.setAndContinue(CROUCH_WALK_UPPER_ANIM) : state.setAndContinue(CROUCH_IDLE_UPPER_ANIM);
         } else if (this.isMoving()) {
            return this.isSprinting() ? state.setAndContinue(RUN_UPPER_ANIM) : state.setAndContinue(WALK_UPPER_ANIM);
         } else {
            return state.setAndContinue(IDLE_UPPER_ANIM);
         }
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
