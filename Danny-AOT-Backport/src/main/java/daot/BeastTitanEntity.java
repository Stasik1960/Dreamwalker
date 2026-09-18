package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.EffectPayload;
import daot.network.ModNetworking;
import daot.network.ODMJamPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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

public class BeastTitanEntity extends HostileEntity implements GeoEntity, ShifterTitan, WireRestrainable, daot.compat.BaseDimensionsProvider {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Boolean> DATA_IS_MOVING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      BeastTitanEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_TRANSFORMATION_TICKS = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_WIRE_RESTRAINT = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_DISMOUNTING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_STOMP_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_SPRINTING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_CROUCHING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_LOW_STAMINA = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ROARING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ATTACKING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ATTACK_NUMBER = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_ROCK_THROW_PHASE = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_THROW_TURNING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_ROCK_BIG_MODE = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_BEAST_ROAR_ABILITY = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_DEFEATED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_GRABBED_ENTITY_ID = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_LAST_ATTACK_IMPACT_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_LAST_GRAB_IMPACT_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_GRAB_RUMBLING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_THROW_IMPACT_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_LAST_DISMOUNT_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_IMPALED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_FALLING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_LANDING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_LANDING_TICK = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> DATA_LANDING_INTENSITY = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_IS_JUMPING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_CLIMBING = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_JUMP_LAUNCHED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_JUMP_VEL_X = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Y = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_JUMP_VEL_Z = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_HOOK_PROTECTED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_INCAPACITATED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_KNOCKED = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_KNOCKED_YAW = DataTracker.registerData(BeastTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation CROUCH_ANIM = RawAnimation.begin().thenLoop("crouch");
   private static final RawAnimation CROUCH_WALK_ANIM = RawAnimation.begin().thenLoop("crouchwalk");
   private static final RawAnimation CLIMB_ANIM = RawAnimation.begin().thenLoop("climb");
   private static final RawAnimation IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation RUN_UPPER_ANIM = RawAnimation.begin().thenLoop("run_upper");
   private static final RawAnimation CROUCH_UPPER_ANIM = RawAnimation.begin().thenLoop("crouch_upper");
   private static final RawAnimation CROUCH_WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("crouchwalk_upper");
   private static final RawAnimation SHIFT_ANIM = RawAnimation.begin().thenPlayAndHold("shift");
   private static final RawAnimation DISMOUNT_ANIM = RawAnimation.begin().thenPlayAndHold("dismount");
   private static final RawAnimation UNDISMOUNT_ANIM = RawAnimation.begin().thenPlay("undismount");
   private static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenPlayAndHold("roar");
   private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");
   private static final RawAnimation ROCK_GRAB_ANIM = RawAnimation.begin().thenPlayAndHold("rockgrab");
   private static final RawAnimation ROCK_GRAB_BIG_ANIM = RawAnimation.begin().thenPlayAndHold("rockgrab_big");
   private static final RawAnimation THROW_IDLE_ANIM = RawAnimation.begin().thenLoop("throwidle");
   private static final RawAnimation THROW_IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("throwidle_upper");
   private static final RawAnimation THROW_IDLE_UPPER_BIG_ANIM = RawAnimation.begin().thenLoop("throwidle_upper_big");
   private static final RawAnimation THROW_IDLE_LOWER_ANIM = RawAnimation.begin().thenLoop("throwidle_lower");
   private static final RawAnimation THROW_WALK_ANIM = RawAnimation.begin().thenLoop("throw_walk");
   private static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlayAndHold("throw");
   private static final RawAnimation THROW_BIG_ANIM = RawAnimation.begin().thenPlayAndHold("throw_big");
   private static final RawAnimation IMPALED_ANIM = RawAnimation.begin().thenLoop("impaled");
   private static final RawAnimation KNOCKED_ANIM = RawAnimation.begin().thenPlayAndHold("knocked");
   private static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("jump");
   private static final RawAnimation FALLING_ANIM = RawAnimation.begin().thenLoop("falling");
   private static final RawAnimation LAND_ANIM = RawAnimation.begin().thenPlayAndHold("land");
   private static final double CONTROLLED_WALK_SPEED = 0.2;
   private static final double CONTROLLED_RUN_SPEED = 0.55;
   private static final double CONTROLLED_CROUCH_SPEED = 0.1;
   private static final double CONTROLLED_THROW_WALK_SPEED = 0.133;
   private double currentSpeed = 0.2;
   private static final double SPEED_LERP_RATE = 0.16666666666666666;
   private long walkStartTick = 0L;
   private boolean wasMovingLastTick = false;
   private static final double STOMP_THRESHOLD = 0.15;
   private static final double[] STOMP_KEYFRAMES = new double[]{0.75, 2.25};
   private static final double[] THROW_WALK_STOMP_KEYFRAMES = new double[]{1.125, 3.375};
   private static final int STOMP_START_DELAY = 10;
   private int lastStompKeyframeIndex = -1;
   private int stompCooldown = 0;
   private double smoothYOffset = 0.0;
   private int climbAnimTicks = 0;
   private float climbTargetYaw = Float.NaN;
   private double prevSmoothYOffset = 0.0;
   private static final double SMOOTH_Y_LERP = 0.25;
   private static final double STEP_SMOOTH_THRESHOLD = 0.1;
   private double currentRideForwardOffset = 2.0;
   private double currentRideExtraY = 0.0;
   private static final double RIDE_POSITION_LERP = 0.15;
   private boolean allowDismount = false;
   private int dismountToggleCooldown = 0;
   private static final int DISMOUNT_TOGGLE_COOLDOWN_TICKS = 20;
   private int dismountVisibilityDelay = 0;
   private boolean wasDismountingLastTick = false;
   private int dismountExitBlendTicks = 0;
   private static final int DISMOUNT_EXIT_BLEND_DURATION = 10;
   private long despawnAtGameTime = -1L;
   private static final int DISMOUNT_DESPAWN_TICKS = 1200;
   private static final int DISMOUNT_VISIBILITY_DELAY_TICKS = 3;
   private static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.changing(3.0F, 17.0F);
   private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.changing(3.0F, 12.0F);
   private boolean wasCrouchingLastTick = false;
   private int crouchTransitionTicksRemaining = 0;
   private boolean wasSprintingBeforeCrouch = false;
   private int crouchHysteresisCounter = 0;
   private static final int CROUCH_HYSTERESIS_TICKS = 2;
   private int roarTicks = 0;
   private static final int ROAR_DURATION_TICKS = 60;
   private static final int BEAST_ROAR_ABILITY_DURATION_TICKS = 120;
   private static final float BEAST_ROAR_STAMINA_COST = 60.0F;
   private int beastRoarCooldownTicks = 0;
   private static final int BEAST_ROAR_COOLDOWN_TICKS = 60;
   private int attackCooldown = 0;
   private int attackAnimationTicks = 0;
   private int lastAttackNumber = 2;
   private static final int ATTACK1_TICKS = 15;
   private static final int ATTACK2_TICKS = 15;
   private static final int ATTACK1_EFFECT_TICK = 13;
   private static final int ATTACK2_EFFECT_TICK = 12;
   private int attackEffectTimer = 0;
   private boolean attackEffectTriggered = false;
   private int rockGrabTicks = 0;
   private static final int ROCK_GRAB_DURATION_TICKS = 55;
   private int throwTicks = 0;
   private static final int THROW_DURATION_TICKS = 78;
   private static final float ROCK_GRAB_STAMINA_COST = 50.0F;
   private static final int THROW_COOLDOWN_TICKS = 15;
   private int rockProjectilesSpawned = 0;
   private static final int TOTAL_ROCK_PROJECTILES = 16;
   private static final int ROCK_SPAWN_INTERVAL = 3;
   private int throwExitBlendTicks = 0;
   private static final int THROW_EXIT_BLEND_DURATION = 10;
   private boolean rockFireMode = false;
   private static final float ROCK_GRAB_FIRE_STAMINA_MULT = 3.0F;
   private double syncedGrabX;
   private double syncedGrabY;
   private double syncedGrabZ;
   private int lastGrabSyncTick = -1;
   private int highlightedEntityId = -1;
   private BeastTitanNapeEntity napeEntity = null;
   private BeastTitanEyeEntity eyeEntity = null;
   private BeastTitanGrabEntity grabHitboxEntity = null;
   private static final int BLINDNESS_DURATION_TICKS = 120;
   private int blindnessTicks = 0;
   private int hitSlowTicks = 0;
   private int hitReactionTicks = 0;
   private static final int KNOCKED_DURATION_TICKS = 30;
   private static final double KNOCKED_SLIDE_SPEED = 1.575;
   private static final int KNOCKED_GETUP_COOLDOWN_TICKS = 60;
   private int knockedElapsedTicks = 0;
   private boolean wasKnocked = false;
   private float lockedYaw = Float.NaN;
   private boolean configHealthApplied = false;
   private static final float INCAP_HEALTH_FLOOR = 1.0F;
   private static final int INCAPACITATE_DURATION_TICKS = 200;
   private int incapacitateTicks = 0;
   private int deathAnimTicks = -1;
   private static final int DEATH_IMPACT_TICK_1 = 15;
   private static final int DEATH_IMPACT_TICK_2 = 36;
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
   private static final List<int[]> thrownEntities = new ArrayList<>();
   private static final Map<Integer, Vec3d> thrownVelocity = new HashMap<>();
   private static final int THROWN_COLLISION_TRACK_TICKS = 100;
   private static final float THROWN_COLLISION_DAMAGE = 15.0F;
   private static final double THROWN_COLLISION_KNOCKBACK = 2.5;
   private static final int THROWN_ODM_JAM_MS = 2000;
   private static final double THROWN_AIR_DRAG = 0.98;
   private static final double THROWN_GRAVITY = 0.06;

   public BeastTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public BeastTitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public BeastTitanGrabEntity getGrabHitboxEntity() {
      return this.grabHitboxEntity;
   }

   public BeastTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public void spawnHitboxes() {
      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         BeastTitanNapeEntity nape = new BeastTitanNapeEntity(DannysAot.BEAST_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         BeastTitanEyeEntity eye = new BeastTitanEyeEntity(DannysAot.BEAST_TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
         BeastTitanGrabEntity grab = new BeastTitanGrabEntity(DannysAot.BEAST_TITAN_GRAB, this.getWorld());
         grab.setParentTitan(this);
         grab.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(grab);
         this.grabHitboxEntity = grab;
         DannysAot.LOGGER.info("Spawned Beast Titan hitboxes");
      }
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_WIRE_RESTRAINT, 0);
      this.dataTracker.startTracking(DATA_IS_MOVING, false);
      this.dataTracker.startTracking(DATA_SHIFTER_UUID, Optional.empty());
      this.dataTracker.startTracking(DATA_TRANSFORMATION_TICKS, 0);
      this.dataTracker.startTracking(DATA_IS_DISMOUNTING, false);
      this.dataTracker.startTracking(DATA_LAST_STOMP_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_SPRINTING, false);
      this.dataTracker.startTracking(DATA_IS_CROUCHING, false);
      this.dataTracker.startTracking(DATA_LOW_STAMINA, false);
      this.dataTracker.startTracking(DATA_IS_ROARING, false);
      this.dataTracker.startTracking(DATA_IS_ATTACKING, false);
      this.dataTracker.startTracking(DATA_ATTACK_NUMBER, 1);
      this.dataTracker.startTracking(DATA_ROCK_THROW_PHASE, 0);
      this.dataTracker.startTracking(DATA_IS_THROW_TURNING, false);
      this.dataTracker.startTracking(DATA_ROCK_BIG_MODE, false);
      this.dataTracker.startTracking(DATA_BEAST_ROAR_ABILITY, false);
      this.dataTracker.startTracking(DATA_IS_DEFEATED, false);
      this.dataTracker.startTracking(DATA_IS_INCAPACITATED, false);
      this.dataTracker.startTracking(DATA_GRABBED_ENTITY_ID, -1);
      this.dataTracker.startTracking(DATA_LAST_ATTACK_IMPACT_TICK, 0);
      this.dataTracker.startTracking(DATA_LAST_GRAB_IMPACT_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_GRAB_RUMBLING, false);
      this.dataTracker.startTracking(DATA_LAST_THROW_IMPACT_TICK, 0);
      this.dataTracker.startTracking(DATA_LAST_DISMOUNT_TICK, 0);
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
      this.dataTracker.startTracking(DATA_IS_CLIMBING, false);
      this.dataTracker.startTracking(DATA_HOOK_PROTECTED, false);
      this.dataTracker.startTracking(DATA_IS_KNOCKED, false);
      this.dataTracker.startTracking(DATA_KNOCKED_YAW, 0.0F);
   }

   public boolean isHookProtected() {
      return this.dataTracker.get(DATA_HOOK_PROTECTED);
   }

   public void setHookProtected(boolean v) {
      this.dataTracker.set(DATA_HOOK_PROTECTED, v);
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
      this.setBeastRoarAbilityActive(false);
      this.roarTicks = 0;
      boolean wasGrabbing = this.getRockThrowPhase() == 1;
      this.setRockThrowPhase(0);
      this.setGrabRumbling(false);
      this.setThrowTurning(false);
      this.setRockBigMode(false);
      this.rockFireMode = false;
      this.rockGrabTicks = 0;
      this.throwTicks = 0;
      this.rockProjectilesSpawned = 0;
      if (wasGrabbing) {
         this.calculateDimensions();
      }

      this.releaseGrabbedEntity();
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

   public boolean isTitanClimbing() {
      return this.dataTracker.get(DATA_IS_CLIMBING);
   }

   public void setTitanClimbing(boolean climbing) {
      this.dataTracker.set(DATA_IS_CLIMBING, climbing);
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

   @Override
   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (data == DATA_IS_DISMOUNTING || data == DATA_IS_CROUCHING) {
         this.calculateDimensions();
      }
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

   @Override
   public boolean isInSneakingPose() {
      return this.dataTracker.get(DATA_IS_CROUCHING);
   }

   public void setCrouching(boolean crouching) {
      if (!crouching || !StrwsRestraintTracker.isFullyRestrained(this.getUuid())) {
         this.dataTracker.set(DATA_IS_CROUCHING, crouching);
      }
   }

   public boolean isRoaring() {
      return this.dataTracker.get(DATA_IS_ROARING);
   }

   public void setRoaring(boolean roaring) {
      this.dataTracker.set(DATA_IS_ROARING, roaring);
   }

   public boolean isBeastRoarAbilityActive() {
      return this.dataTracker.get(DATA_BEAST_ROAR_ABILITY);
   }

   public void setBeastRoarAbilityActive(boolean active) {
      this.dataTracker.set(DATA_BEAST_ROAR_ABILITY, active);
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

   public int getRockThrowPhase() {
      return this.dataTracker.get(DATA_ROCK_THROW_PHASE);
   }

   public void setRockThrowPhase(int phase) {
      this.dataTracker.set(DATA_ROCK_THROW_PHASE, phase);
   }

   public boolean isRockBigMode() {
      return this.dataTracker.get(DATA_ROCK_BIG_MODE);
   }

   public void setRockBigMode(boolean big) {
      this.dataTracker.set(DATA_ROCK_BIG_MODE, big);
   }

   public boolean isInRockThrowSequence() {
      return this.getRockThrowPhase() != 0;
   }

   public boolean isThrowTurning() {
      return this.dataTracker.get(DATA_IS_THROW_TURNING);
   }

   public void setThrowTurning(boolean turning) {
      this.dataTracker.set(DATA_IS_THROW_TURNING, turning);
   }

   public int getGrabbedEntityId() {
      return this.dataTracker.get(DATA_GRABBED_ENTITY_ID);
   }

   public void setGrabbedEntityId(int id) {
      this.dataTracker.set(DATA_GRABBED_ENTITY_ID, id);
   }

   public void setGrabSyncedPosition(double x, double y, double z, int tick) {
      this.syncedGrabX = x;
      this.syncedGrabY = y;
      this.syncedGrabZ = z;
      this.lastGrabSyncTick = tick;
   }

   public double getSyncedGrabX() {
      return this.syncedGrabX;
   }

   public double getSyncedGrabY() {
      return this.syncedGrabY;
   }

   public double getSyncedGrabZ() {
      return this.syncedGrabZ;
   }

   public int getLastGrabSyncTick() {
      return this.lastGrabSyncTick;
   }

   public int getHighlightedEntityId() {
      return this.highlightedEntityId;
   }

   public void setHighlightedEntityId(int id) {
      this.highlightedEntityId = id;
   }

   public int getLastAttackImpactTick() {
      return this.dataTracker.get(DATA_LAST_ATTACK_IMPACT_TICK);
   }

   public void setLastAttackImpactTick(int tick) {
      this.dataTracker.set(DATA_LAST_ATTACK_IMPACT_TICK, tick);
   }

   public int getLastGrabImpactTick() {
      return this.dataTracker.get(DATA_LAST_GRAB_IMPACT_TICK);
   }

   public void setLastGrabImpactTick(int tick) {
      this.dataTracker.set(DATA_LAST_GRAB_IMPACT_TICK, tick);
   }

   public boolean isGrabRumbling() {
      return this.dataTracker.get(DATA_IS_GRAB_RUMBLING);
   }

   public void setGrabRumbling(boolean rumbling) {
      this.dataTracker.set(DATA_IS_GRAB_RUMBLING, rumbling);
   }

   public int getLastThrowImpactTick() {
      return this.dataTracker.get(DATA_LAST_THROW_IMPACT_TICK);
   }

   public void setLastThrowImpactTick(int tick) {
      this.dataTracker.set(DATA_LAST_THROW_IMPACT_TICK, tick);
   }

   public int getLastDismountTick() {
      return this.dataTracker.get(DATA_LAST_DISMOUNT_TICK);
   }

   public void setLastDismountTick(int tick) {
      this.dataTracker.set(DATA_LAST_DISMOUNT_TICK, tick);
   }

   public boolean isPlayerControlled() {
      return this.getShifterUUID() != null && this.getControllingPassenger() instanceof PlayerEntity;
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.55)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 15.0)
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

      nbt.putBoolean("Incapacitated", this.isIncapacitated());
      nbt.putInt("IncapacitateTicks", this.incapacitateTicks);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("DespawnAtGameTime")) {
         this.despawnAtGameTime = nbt.getLong("DespawnAtGameTime");
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
      return !this.isInSneakingPose() && !this.isDismounting() && this.getRockThrowPhase() != 1 ? STANDING_DIMENSIONS : CROUCHING_DIMENSIONS;
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
         this.releaseGrabbedEntity();
         this.setRoaring(false);
         this.setBeastRoarAbilityActive(false);
         this.roarTicks = 0;
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setRockThrowPhase(0);
         this.setGrabRumbling(false);
         this.setThrowTurning(false);
         this.setRockBigMode(false);
         this.rockGrabTicks = 0;
         this.throwTicks = 0;
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
                  serverPlayer.setInvisible(false);
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 900, 4));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 900, 2));
                  serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 900, 4));
                  serverPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
                  DefeatedCarryTracker.markDefeated(serverPlayer.getUuid());
                  serverPlayer.sendMessage(Text.literal("Your titan has been defeated! Sneak to dismount.").formatted(Formatting.RED));
                  DannysAot.LOGGER.info("Beast Titan defeated with rider {} still attached", serverPlayer.getName().getString());
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
         this.releaseGrabbedEntity();
         this.setRoaring(false);
         this.setBeastRoarAbilityActive(false);
         this.roarTicks = 0;
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setRockThrowPhase(0);
         this.setGrabRumbling(false);
         this.setThrowTurning(false);
         this.setRockBigMode(false);
         this.rockGrabTicks = 0;
         this.throwTicks = 0;
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
         if (this.getFirstPassenger() instanceof PlayerEntity player) {
            UUID shifterUUID = this.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               return player;
            }
         }

         return null;
      }
   }

   private Vec3d getRidePosition(Entity passenger) {
      double headHeight = 13.5;
      return new Vec3d(0.0, headHeight, 0.0);
   }

   @Override
   public Vec3d updatePassengerForDismount(LivingEntity passenger) {
      if (this.isDefeated()) {
         return passenger.getPos();
      } else {
         Vec3d pos = super.updatePassengerForDismount(passenger);
         return new Vec3d(pos.x, pos.y + 1.0, pos.z);
      }
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         if (this.isDefeated()) {
            BeastTitanNapeEntity nape = this.getWorld().isClient() ? BeastTitanNapeEntity.getClientInstance(this.getId()) : this.napeEntity;
            if (nape != null) {
               double napeY = nape.getY() + nape.getHeight() / 2.0;
               positionUpdater.accept(passenger, nape.getX(), napeY, nape.getZ());
               return;
            }
         }

         Vec3d ridePos = this.getRidePosition(passenger);
         float yawRad = (float)Math.toRadians(this.getYaw());
         double targetForwardOffset;
         double targetExtraY;
         if (this.isDismounting()) {
            targetForwardOffset = -0.75;
            targetExtraY = -2.0;
         } else if (this.isTitanClimbing()) {
            targetForwardOffset = -3.0;
            targetExtraY = 0.0;
         } else {
            targetForwardOffset = 2.0;
            targetExtraY = 0.0;
         }

         this.currentRideForwardOffset = this.currentRideForwardOffset + (targetForwardOffset - this.currentRideForwardOffset) * 0.15;
         this.currentRideExtraY = this.currentRideExtraY + (targetExtraY - this.currentRideExtraY) * 0.15;
         if (Math.abs(this.currentRideForwardOffset - targetForwardOffset) < 0.01) {
            this.currentRideForwardOffset = targetForwardOffset;
         }

         if (Math.abs(this.currentRideExtraY - targetExtraY) < 0.01) {
            this.currentRideExtraY = targetExtraY;
         }

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
      if (this.isDefeated()) {
         super.tickControlled(controllingPlayer, Vec3d.ZERO);
         if (!this.getWorld().isClient()) {
            this.setMoving(false);
            this.setSprinting(false);
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

            if (this.getWorld().isClient() && !this.isTransforming() && !this.isTitanClimbing() && !this.isKnocked()) {
               if ((this.getRockThrowPhase() == 2 || this.getRockThrowPhase() == 3) && this.isThrowTurning()) {
                  float targetYaw = controllingPlayer.getYaw();
                  float currentYaw = this.getYaw();
                  float lerpFactor = 0.5F;
                  float newYaw = currentYaw + lerpFactor * wrapDegrees(targetYaw - currentYaw);
                  this.setYaw(newYaw);
                  this.bodyYaw = newYaw;
                  this.headYaw = newYaw;
               } else if (this.getRockThrowPhase() == 2) {
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
               } else if (this.getRockThrowPhase() != 1 && this.getRockThrowPhase() != 3) {
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

               boolean inLockedPhase = this.getRockThrowPhase() == 1 || this.getRockThrowPhase() == 3;
               boolean canShowMovement = !this.isTransforming() && !this.isDismounting() && !this.isRoaring() && !inLockedPhase;
               this.setMoving(hasInput && canShowMovement);
               boolean canMove = !this.isTransforming() && !this.isDismounting() && !this.isRoaring() && !inLockedPhase;
               if (!hasInput || !canMove) {
                  this.setSprinting(false);
               }

               boolean canCrouch = !this.isTransforming()
                  && !this.isDismounting()
                  && !this.isRoaring()
                  && !inLockedPhase
                  && this.getRockThrowPhase() != 2
                  && this.getGrabbedEntityId() == -1;
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
      if (!this.isDefeated()
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isRoaring()
         && this.getRockThrowPhase() != 1
         && this.getRockThrowPhase() != 3
         && !this.isKnocked()) {
         float forward = controllingPlayer.forwardSpeed;
         float strafe = controllingPlayer.sidewaysSpeed;
         if (forward == 0.0F && strafe == 0.0F) {
            return Vec3d.ZERO;
         } else {
            float cameraYaw = controllingPlayer.getYaw();
            float titanYaw = this.getYaw();
            float relativeRadians = (float)Math.toRadians(cameraYaw - titanYaw);
            float sin = (float)Math.sin(relativeRadians);
            float cos = (float)Math.cos(relativeRadians);
            return new Vec3d(strafe * cos - forward * sin, 0.0, strafe * sin + forward * cos);
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
      if (this.isDefeated()) {
         this.currentSpeed = 0.0;
         return 0.0F;
      } else if (!this.isLanding() && !this.isKnocked()) {
         double targetSpeed;
         if (this.getRockThrowPhase() == 2) {
            targetSpeed = 0.133;
         } else if (this.isInSneakingPose()) {
            targetSpeed = 0.1;
         } else if (this.isSprinting()) {
            targetSpeed = 0.55;
         } else {
            targetSpeed = 0.2;
         }

         if (this.currentSpeed < targetSpeed) {
            this.currentSpeed = Math.min(this.currentSpeed + 0.058333333333333334, targetSpeed);
         } else if (this.currentSpeed > targetSpeed) {
            this.currentSpeed = Math.max(this.currentSpeed - 0.058333333333333334, targetSpeed);
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
               if (!this.isInRockThrowSequence()) {
                  if (!this.isTitanClimbing()) {
                     this.dismountVisibilityDelay = 3;
                     this.setDismounting(true);
                     this.allowDismount = true;
                     this.dismountToggleCooldown = 20;
                     this.calculateDimensions();
                     this.setLastDismountTick(this.age);
                     NapeSmokeHelper.onDismountStart(this, this.napeEntity);
                     float yawRad = (float)Math.toRadians(this.getYaw());
                     double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
                     double napeY = this.getY() + 14.5;
                     double napeZ = this.getZ() + Math.cos(yawRad) * 1.5;
                     this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
                     this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 4.0F, 0.1F);
                     if (this.getWorld() instanceof ServerWorld serverLevel) {
                        BlockPos below = this.getBlockPos().down();
                        BlockState groundBlock = serverLevel.getBlockState(below);
                        if (!groundBlock.isAir()) {
                           serverLevel.spawnParticles(
                              new BlockStateParticleEffect(ParticleTypes.BLOCK, groundBlock), this.getX(), this.getY(), this.getZ(), 25, 1.5, 0.3, 1.5, 0.1
                           );
                        }
                     }
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
               this.calculateDimensions();
               float yawRad = (float)Math.toRadians(this.getYaw());
               double napeX = this.getX() + -Math.sin(yawRad) * 1.5;
               double napeY = this.getY() + 14.5;
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
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Beast Titan" : "Beast Titan - " + player.getName().getString();
         this.bossBar = new ServerBossBar(Text.literal(bossBarName), Color.RED, Style.PROGRESS);
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
               && !(entity instanceof BeastTitanNapeEntity)
               && !(entity instanceof BeastTitanEyeEntity)
               && !(entity instanceof AttackTitanEntity)
               && !(entity instanceof ArmoredTitanEntity)
               && !(entity instanceof ColossalTitanEntity)
               && !(entity instanceof FemaleTitanEntity)
               && !(entity instanceof BeastTitanEntity)
               && !(
                  entity instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
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

   public void triggerRoar() {
      if (!this.isKnocked()) {
         if (!this.isTransforming() && !this.isDismounting() && !this.isTitanClimbing()) {
            if (!this.isBeastRoarAbilityActive()) {
               this.setRoaring(true);
               this.roarTicks = 60;
            }
         }
      }
   }

   public void triggerBeastRoarAbility() {
      if (this.isKnocked()) {
         if (this.isKnockedHeld()) {
            this.releaseKnocked();
         }
      } else if (this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isRoaring()
         && !this.isTitanAttacking()
         && !this.isInRockThrowSequence()
         && !this.isTitanClimbing()) {
         if (this.beastRoarCooldownTicks <= 0) {
            this.setRoaring(true);
            this.setBeastRoarAbilityActive(true);
            this.roarTicks = 120;
            this.beastRoarCooldownTicks = 180;
            UUID shifter = this.getShifterUUID();
            if (shifter != null) {
               ModNetworking.drainStamina(shifter, 60.0F);
            }

            this.getWorld().playSound(null, this.getX(), this.getY() + 15.0, this.getZ(), ModSounds.ROYAL_SHOUT, SoundCategory.HOSTILE, 10.0F, 1.0F);
            if (this.getWorld() instanceof ServerWorld serverLevel) {
               double eyeY;
               double eyeZ;
               double eyeX;
               if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
                  eyeX = this.eyeEntity.getX();
                  eyeY = this.eyeEntity.getY();
                  eyeZ = this.eyeEntity.getZ();
               } else {
                  float yawRad = (float)Math.toRadians(this.bodyYaw);
                  eyeX = this.getX() + -Math.sin(yawRad) * 2.0;
                  eyeY = this.getY() + 15.5;
                  eyeZ = this.getZ() + Math.cos(yawRad) * 2.0;
               }

               EffectPayload roarPayload = new EffectPayload("roar", eyeX, eyeY, eyeZ, 1.5F);

               for (ServerPlayerEntity p : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                  ServerPlayNetworking.send(p, roarPayload);
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
      } else if (this.getRockThrowPhase() == 2) {
         this.triggerThrow();
      } else if (this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isRoaring()
         && !this.isTitanAttacking()
         && !this.isInRockThrowSequence()
         && !this.isTitanClimbing()) {
         this.lastAttackNumber = this.lastAttackNumber == 1 ? 2 : 1;
         this.setTitanAttacking(true);
         this.setAttackNumber(this.lastAttackNumber);
         int ticks = this.lastAttackNumber == 1 ? 15 : 15;
         this.attackAnimationTicks = ticks;
         this.attackCooldown = ticks;
         this.attackEffectTimer = 0;
         this.attackEffectTriggered = false;
      }
   }

   public void triggerRockGrab() {
      this.triggerRockGrabInternal(false, false);
   }

   public void triggerFireRockGrab() {
      this.triggerRockGrabInternal(true, false);
   }

   public void triggerBigRockGrab() {
      this.triggerRockGrabInternal(false, true);
   }

   private void triggerRockGrabInternal(boolean fire, boolean big) {
      if (this.isKnocked()) {
         if (this.isKnockedHeld()) {
            this.releaseKnocked();
         }
      } else if (this.getRockThrowPhase() == 2) {
         this.cancelRockThrow();
      } else if (this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isRoaring()
         && !this.isTitanAttacking()
         && !this.isInRockThrowSequence()
         && !this.isTitanClimbing()) {
         this.rockFireMode = fire;
         this.setRockBigMode(big);
         this.setRockThrowPhase(1);
         this.rockGrabTicks = 55;
         this.calculateDimensions();
         UUID shifter = this.getShifterUUID();
         if (shifter != null) {
            float cost = 50.0F * (fire ? 3.0F : 1.0F);
            ModNetworking.drainStamina(shifter, cost);
         }
      }
   }

   public void cancelRockThrow() {
      boolean wasGrabbing = this.getRockThrowPhase() == 1;
      boolean wasInSequence = this.getRockThrowPhase() != 0;
      this.setRockThrowPhase(0);
      this.setGrabRumbling(false);
      this.setThrowTurning(false);
      this.rockProjectilesSpawned = 0;
      this.rockGrabTicks = 0;
      this.throwTicks = 0;
      this.rockFireMode = false;
      this.setRockBigMode(false);
      if (wasGrabbing) {
         this.calculateDimensions();
      }

      if (wasInSequence) {
         this.throwExitBlendTicks = 10;
      }

      this.releaseGrabbedEntity();
   }

   public void triggerThrow() {
      if (this.getRockThrowPhase() == 2) {
         this.setRockThrowPhase(3);
         this.throwTicks = 78;
      }
   }

   public static boolean canBeGrabbed(Entity target) {
      if (!(target instanceof LivingEntity) || !target.isAlive()) {
         return false;
      } else if (target instanceof AttackTitanEntity
         || target instanceof ArmoredTitanEntity
         || target instanceof ColossalTitanEntity
         || target instanceof FemaleTitanEntity
         || target instanceof BeastTitanEntity) {
         return false;
      } else if (target instanceof FritzTitanEntity || target instanceof TitanEntity) {
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
      } else if (target instanceof BeastTitanGrabEntity) {
         return false;
      } else if (target instanceof FritzTitanNapeEntity || target instanceof FritzTitanEyeEntity) {
         return false;
      } else if (target instanceof SmallTitanNapeEntity || target instanceof SmallTitanEyeEntity) {
         return false;
      } else if (target instanceof SmallTitan2NapeEntity || target instanceof SmallTitan2EyeEntity) {
         return false;
      } else if (target instanceof ConnieFatherNapeEntity || target instanceof ConnieFatherEyeEntity) {
         return false;
      } else if (target instanceof SadTitanNapeEntity || target instanceof SadTitanEyeEntity) {
         return false;
      } else if (target instanceof YellowTitanNapeEntity || target instanceof YellowTitanEyeEntity) {
         return false;
      } else {
         return target instanceof OgreTitanNapeEntity || target instanceof OgreTitanEyeEntity
            ? false
            : !(target instanceof CrawlerTitanNapeEntity) && !(target instanceof CrawlerTitanEyeEntity);
      }
   }

   public void grabEntity(Entity target) {
      if (this.isKnocked()) {
         if (this.isKnockedHeld()) {
            this.releaseKnocked();
         }
      } else if (this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isRoaring()
         && !this.isTitanAttacking()
         && !this.isInRockThrowSequence()) {
         if (canBeGrabbed(target)) {
            if (this.grabHitboxEntity != null && !this.grabHitboxEntity.isRemoved()) {
               if (target != this.grabHitboxEntity && target != this) {
                  if (!this.grabHitboxEntity.hasPassenger(target) && !target.hasPassenger(this.grabHitboxEntity)) {
                     this.setGrabbedEntityId(target.getId());
                     if (target.getVehicle() != null) {
                        target.stopRiding();
                     }

                     target.startRiding(this.grabHitboxEntity, true);
                     target.setVelocity(Vec3d.ZERO);
                     if (target instanceof MobEntity mob) {
                        mob.setAiDisabled(true);
                     }

                     this.setRockThrowPhase(1);
                     this.rockGrabTicks = 55;
                     this.calculateDimensions();
                     UUID shifter = this.getShifterUUID();
                     if (shifter != null) {
                        ModNetworking.drainStamina(shifter, 50.0F);
                     }
                  }
               }
            }
         }
      }
   }

   public void releaseGrabbedEntity() {
      int grabbedId = this.getGrabbedEntityId();
      if (grabbedId != -1) {
         Entity grabbed = this.getWorld().getEntityById(grabbedId);
         this.setGrabbedEntityId(-1);
         if (grabbed != null) {
            if (grabbed.getVehicle() instanceof BeastTitanGrabEntity grabHitbox) {
               grabHitbox.setDismountAllowed(true);
               grabbed.stopRiding();
               grabHitbox.setDismountAllowed(false);
            }

            grabbed.setNoGravity(false);
            if (grabbed instanceof MobEntity mob) {
               mob.setAiDisabled(false);
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

            if (grabbed.getVehicle() instanceof BeastTitanGrabEntity grabHitbox) {
               grabHitbox.setDismountAllowed(true);
               grabbed.stopRiding();
               grabHitbox.setDismountAllowed(false);
            }

            this.setGrabbedEntityId(-1);
            double horizLen = Math.sqrt(aimX * aimX + aimZ * aimZ);
            double launchX;
            double launchZ;
            if (horizLen > 0.001) {
               launchX = this.getX() + aimX / horizLen * 8.0;
               launchZ = this.getZ() + aimZ / horizLen * 8.0;
            } else {
               float fwdYaw = (float)Math.toRadians(this.getYaw());
               launchX = this.getX() + -Math.sin(fwdYaw) * 8.0;
               launchZ = this.getZ() + Math.cos(fwdYaw) * 8.0;
            }

            double launchY = this.getY() + (this.isInSneakingPose() ? 10.0 : 15.0);
            grabbed.noClip = false;
            grabbed.setNoGravity(true);
            grabbed.setPosition(launchX, launchY, launchZ);
            double launchSpeed = 4.7;
            Vec3d launchVel = new Vec3d(aimX * launchSpeed, aimY * launchSpeed + 1.0, aimZ * launchSpeed);
            grabbed.setVelocity(launchVel);
            grabbed.velocityDirty = true;
            if (grabbed instanceof LivingEntity living) {
               living.velocityModified = true;
            }

            thrownEntities.add(new int[]{grabbed.getId(), this.getId(), 100});
            thrownVelocity.put(grabbed.getId(), launchVel);
            if (grabbed instanceof ServerPlayerEntity thrownPlayer) {
               ServerPlayNetworking.send(thrownPlayer, new ODMJamPayload(2000));
            }
         }
      }
   }

   public static boolean isEntityThrown(int entityId) {
      return thrownVelocity.containsKey(entityId);
   }

   public static void registerThrownEntity(Entity thrown, int throwerTitanId, Vec3d launchVel) {
      thrown.noClip = false;
      thrown.setNoGravity(true);
      thrown.setVelocity(launchVel);
      thrown.velocityDirty = true;
      if (thrown instanceof LivingEntity living) {
         living.velocityModified = true;
      }

      thrownEntities.add(new int[]{thrown.getId(), throwerTitanId, 100});
      thrownVelocity.put(thrown.getId(), launchVel);
      DannysAot.LOGGER
         .info(
            "[TitanToss] registered thrown id={} pos=({},{},{}) vel={} riding={}",
            new Object[]{thrown.getId(), (int)thrown.getX(), (int)thrown.getY(), (int)thrown.getZ(), launchVel, thrown.getVehicle() != null}
         );
   }

   private static void cleanupThrownEntity(Entity entity) {
      if (entity != null) {
         entity.noClip = false;
         entity.setNoGravity(false);
         entity.setVelocity(Vec3d.ZERO);
         if (entity instanceof MobEntity mob && mob.isAiDisabled()) {
            mob.setAiDisabled(false);
         }
      }
   }

   public static void tickThrownEntities(MinecraftServer server) {
      Iterator<int[]> iter = thrownEntities.iterator();

      while (iter.hasNext()) {
         int[] entry = iter.next();
         int entityId = entry[0];
         int titanId = entry[1];
         entry[2]--;
         Entity thrown = null;

         for (ServerWorld searchLevel : server.getWorlds()) {
            thrown = searchLevel.getEntityById(entityId);
            if (thrown != null) {
               break;
            }
         }

         if (thrown != null && thrown.isAlive() && entry[2] > 0) {
            World level = thrown.getWorld();
            Vec3d vel = thrownVelocity.get(entityId);
            if (vel != null && !(vel.lengthSquared() < 0.5)) {
               flingBlocksForThrownEntity(thrown, vel);
               thrown.setVelocity(vel);
               thrown.move(MovementType.SELF, vel);
               thrown.velocityDirty = true;
               if (thrown instanceof LivingEntity le) {
                  le.velocityModified = true;
               }

               new Vec3d(thrown.getX() - (thrown.getX() - vel.x), thrown.getY() - (thrown.getY() - vel.y), thrown.getZ() - (thrown.getZ() - vel.z));
               boolean hitWall = thrown.horizontalCollision;
               boolean hitFloor = thrown.verticalCollision;
               if (entry[2] % 5 == 0 || hitWall || hitFloor) {
                  DannysAot.LOGGER
                     .info(
                        "[TitanToss] tick id={} pos=({},{},{}) vel={} wall={} floor={} riding={}",
                        new Object[]{
                           entityId,
                           (int)thrown.getX(),
                           (int)thrown.getY(),
                           (int)thrown.getZ(),
                           String.format("%.2f", vel.length()),
                           hitWall,
                           hitFloor,
                           thrown.getVehicle() != null
                        }
                     );
               }

               if (!hitWall && !hitFloor) {
                  Vec3d nextVel = new Vec3d(vel.x * 0.98, (vel.y - 0.06) * 0.98, vel.z * 0.98);
                  thrownVelocity.put(entityId, nextVel);
                  List<LivingEntity> nearby = level.getEntitiesByClass(
                     LivingEntity.class,
                     thrown.getBoundingBox().expand(1.5),
                     e -> e.getId() != entityId
                        && e.isAlive()
                        && !isOwnedByTitan(e, titanId, level)
                        && (e.getVehicle() == null || e.getVehicle().getId() != titanId)
                        && !isHitboxChildEntity(e)
                  );
                  Iterator var13 = nearby.iterator();
                  if (var13.hasNext()) {
                     LivingEntity target = (LivingEntity)var13.next();
                     target.damage(thrown.getDamageSources().mobAttack(thrown instanceof LivingEntity le ? le : null), 15.0F);
                     double kbX = vel.x;
                     double kbZ = vel.z;
                     double kbLen = Math.sqrt(kbX * kbX + kbZ * kbZ);
                     if (kbLen > 0.001) {
                        kbX /= kbLen;
                        kbZ /= kbLen;
                     }

                     target.setVelocity(target.getVelocity().add(kbX * 2.5, 0.5, kbZ * 2.5));
                     if (target instanceof LivingEntity) {
                        target.velocityModified = true;
                        target.velocityDirty = true;
                     }

                     if (thrown instanceof LivingEntity thrownLiving) {
                        thrownLiving.damage(thrownLiving.getDamageSources().fall(), 7.5F);
                     }

                     cleanupThrownEntity(thrown);
                     thrownVelocity.remove(entityId);
                     iter.remove();
                  }
               } else {
                  if (thrown instanceof LivingEntity thrownLiving) {
                     float impactSpeed = (float)vel.length();
                     thrownLiving.damage(thrownLiving.getDamageSources().fall(), impactSpeed * 3.0F);
                  }

                  cleanupThrownEntity(thrown);
                  thrownVelocity.remove(entityId);
                  iter.remove();
               }
            } else {
               DannysAot.LOGGER.info("[TitanToss] cleanup id={} reason=slow vel={}", entityId, vel);
               cleanupThrownEntity(thrown);
               thrownVelocity.remove(entityId);
               iter.remove();
            }
         } else {
            DannysAot.LOGGER
               .info(
                  "[TitanToss] cleanup id={} reason={} ticksLeft={}",
                  new Object[]{entityId, thrown == null ? "notFound" : (!thrown.isAlive() ? "dead" : "timeout"), entry[2]}
               );
            cleanupThrownEntity(thrown);
            thrownVelocity.remove(entityId);
            iter.remove();
         }
      }
   }

   private static boolean isHitboxChildEntity(Entity e) {
      String n = e.getClass().getSimpleName();
      return n.contains("Nape") || n.contains("Eye") || n.contains("Grab") || n.contains("Leg") || n.contains("Dummy");
   }

   private static boolean isOwnedByTitan(Entity entity, int titanId, World level) {
      if (entity.getId() == titanId) {
         return true;
      } else {
         Entity titan = level.getEntityById(titanId);
         return titan == null
            ? false
            : entity.squaredDistanceTo(titan) < 25.0 && (entity instanceof BeastTitanEyeEntity || entity instanceof BeastTitanNapeEntity);
      }
   }

   private static void flingBlocksForThrownEntity(Entity thrown, Vec3d vel) {
      if (thrown.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(thrown.getWorld())) {
            double speed = vel.length();
            if (!(speed < 0.5)) {
               Vec3d dir = vel.normalize();
               float bbWidth = thrown.getWidth();
               float bbHeight = thrown.getHeight();
               int radius = Math.max(2, (int)Math.ceil(bbWidth / 2.0) + 1);
               int height = Math.max(3, (int)Math.ceil(bbHeight) + 1);
               int steps = Math.max(2, (int)Math.ceil(speed)) + 1;
               int flung = 0;
               int maxFlung = 25;
               Random random = serverLevel.random;

               for (int step = 0; step <= steps && flung < maxFlung; step++) {
                  double t = (double)step / steps * speed;
                  double checkX = thrown.getX() + dir.x * t;
                  double checkY = thrown.getY() + dir.y * t;
                  double checkZ = thrown.getZ() + dir.z * t;

                  for (int dx = -radius; dx <= radius && flung < maxFlung; dx++) {
                     for (int dz = -radius; dz <= radius && flung < maxFlung; dz++) {
                        for (int dy = 0; dy < height && flung < maxFlung; dy++) {
                           BlockPos pos = new BlockPos((int)Math.floor(checkX + dx), (int)Math.floor(checkY) + dy, (int)Math.floor(checkZ + dz));
                           BlockState blockState = serverLevel.getBlockState(pos);
                           if (!blockState.isAir()
                              && !(blockState.getHardness(serverLevel, pos) < 0.0F)
                              && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                              && !(blockState.getBlock() instanceof FluidBlock)) {
                              serverLevel.removeBlock(pos, false);
                              double flingVelX = dir.x * (0.8 + random.nextDouble() * 0.6) + (random.nextDouble() - 0.5) * 0.4;
                              double flingVelY = 0.4 + random.nextDouble() * 0.8;
                              double flingVelZ = dir.z * (0.8 + random.nextDouble() * 0.6) + (random.nextDouble() - 0.5) * 0.4;
                              FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                              ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                              fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                              fallingBlock.setFallingBlockPos(pos);
                              fallingBlock.setVelocity(flingVelX, flingVelY, flingVelZ);
                              fallingBlock.timeFalling = 1;
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
      }
   }

   private void spawnRockProjectiles() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float titanYawRad = (float)Math.toRadians(this.getYaw());
         double titanForwardX = -Math.sin(titanYawRad);
         double titanForwardZ = Math.cos(titanYawRad);
         double rightX = -titanForwardZ;
         double aimYawRad = titanYawRad;
         double aimPitchRad = 0.0;
         if (this.getControllingPassenger() instanceof PlayerEntity player && this.isThrowTurning()) {
            aimYawRad = Math.toRadians(player.getYaw());
            aimPitchRad = Math.toRadians(player.getPitch());
         }

         double aimX = -Math.sin(aimYawRad) * Math.cos(aimPitchRad);
         double aimY = -Math.sin(aimPitchRad);
         double aimZ = Math.cos(aimYawRad) * Math.cos(aimPitchRad);
         double spawnY = this.getY() + (this.isInSneakingPose() ? 10.0 : 15.0);
         double spawnX = this.getX() + rightX * 2.0;
         double spawnZ = this.getZ() + titanForwardX * 2.0;
         if (this.isRockBigMode()) {
            RockProjectileEntity rock = new RockProjectileEntity(DannysAot.ROCK_PROJECTILE, serverLevel);
            rock.setBigMode(true);
            rock.setOwnerUUID(this.getShifterUUID());
            rock.setPosition(spawnX, spawnY, spawnZ);
            double baseSpeed = 6.75;
            rock.setVelocity(aimX * baseSpeed, aimY * baseSpeed + 0.3, aimZ * baseSpeed);
            rock.setCustomGravity(0.025);
            serverLevel.spawnEntity(rock);
         } else {
            int rockCount = ModConfig.get().beastRockCount;
            double spreadDegrees = ModConfig.get().beastRockSpreadDegrees;

            for (int i = 0; i < rockCount; i++) {
               double offsetX = (this.random.nextDouble() - 0.5) * 3.0;
               double offsetZ = (this.random.nextDouble() - 0.5) * 3.0;
               double offsetY = (this.random.nextDouble() - 0.5) * 2.0;
               RockProjectileEntity rock = new RockProjectileEntity(DannysAot.ROCK_PROJECTILE, serverLevel);
               rock.setPosition(spawnX + offsetX, spawnY + offsetY, spawnZ + offsetZ);
               rock.setFireMode(this.rockFireMode);
               rock.setOwnerUUID(this.getShifterUUID());
               double baseSpeed = 6.0 + (this.random.nextDouble() - 0.5) * 1.5;
               double spreadYawAngle = (this.random.nextDouble() - 0.5) * Math.toRadians(spreadDegrees);
               double spreadPitchAngle = (this.random.nextDouble() - 0.5) * Math.toRadians(spreadDegrees * 0.625);
               double cosSpread = Math.cos(spreadYawAngle);
               double sinSpread = Math.sin(spreadYawAngle);
               double spreadAimX = aimX * cosSpread - aimZ * sinSpread;
               double spreadAimZ = aimX * sinSpread + aimZ * cosSpread;
               double spreadAimY = aimY + Math.sin(spreadPitchAngle) * 0.15;
               double len = Math.sqrt(spreadAimX * spreadAimX + spreadAimY * spreadAimY + spreadAimZ * spreadAimZ);
               if (len > 0.001) {
                  spreadAimX /= len;
                  spreadAimY /= len;
                  spreadAimZ /= len;
               }

               rock.setVelocity(spreadAimX * baseSpeed, spreadAimY * baseSpeed + 0.3, spreadAimZ * baseSpeed);
               rock.setCustomGravity(0.02 + this.random.nextDouble() * 0.015);
               serverLevel.spawnEntity(rock);
            }
         }
      }
   }

   private void dealAttackDamage() {
      if (this.getWorld() instanceof ServerWorld) {
         SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
         SoundEvent sound = impactSounds[this.random.nextInt(impactSounds.length)];
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         this.getWorld()
            .playSound(
               null,
               this.getX() + forwardX * 7.0,
               this.getY() + 4.0,
               this.getZ() + forwardZ * 7.0,
               sound,
               SoundCategory.HOSTILE,
               8.0F,
               0.85F + this.random.nextFloat() * 0.1F
            );

         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(16.0, 12.0, 16.0))) {
            if (target != this && target.getVehicle() != this) {
               UUID shifterUUID = this.getShifterUUID();
               if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
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
                  && !(
                     target instanceof PlayerEntity p
                        && (
                           p.getVehicle() instanceof AttackTitanEntity
                              || p.getVehicle() instanceof ArmoredTitanEntity
                              || p.getVehicle() instanceof ColossalTitanEntity
                              || p.getVehicle() instanceof FemaleTitanEntity
                              || p.getVehicle() instanceof BeastTitanEntity
                        )
                  )) {
                  Vec3d toTarget = target.getPos().subtract(this.getPos());
                  double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
                  if (dot > 0.2 && toTarget.horizontalLength() < 16.0) {
                     if (target instanceof TitanNapeEntity napeEntity) {
                        TitanEntity parentTitan = napeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else if (target instanceof SmallTitanNapeEntity nape) {
                        SmallTitanEntity parent = nape.getParentTitan();
                        if (parent != null) {
                           parent.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        }
                     } else if (target instanceof SmallTitan2NapeEntity napex) {
                        SmallTitan2Entity parent = napex.getParentTitan();
                        if (parent != null) {
                           parent.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        }
                     } else if (target instanceof FritzTitanNapeEntity fritzNape) {
                        FritzTitanEntity parent = fritzNape.getParentTitan();
                        if (parent != null) {
                           parent.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                           this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 8.0F, 0.8F);
                        }
                     } else {
                        float attackDamage = (float)ModConfig.get().beastTitanAttackDamage;
                        target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, attackDamage));
                        Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                        if (target instanceof TitanEntity) {
                           Vec3d knockback = horizontalDir.multiply(5.0).add(0.0, 2.0, 0.0);
                           target.setVelocity(target.getVelocity().add(knockback));
                        } else if (target instanceof PlayerEntity playerTarget) {
                           Vec3d knockback = horizontalDir.multiply(2.5).add(0.0, 1.0, 0.0);
                           playerTarget.setVelocity(knockback);
                           playerTarget.velocityModified = true;
                           playerTarget.velocityDirty = true;
                        } else {
                           Vec3d knockback = horizontalDir.multiply(3.0).add(0.0, 1.5, 0.0);
                           target.setVelocity(target.getVelocity().add(knockback));
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
   }

   private void destroyBlocksAlongSwing(float swingProgress) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            float yawRad = (float)Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double rightX = forwardZ;
            double rightZ = -forwardX;
            double sideOffset = (1.0 - swingProgress) * 6.0;
            double forwardDist = 3.0 + swingProgress * 8.0;
            double crouchOffset = this.isInSneakingPose() ? 5.0 : 0.0;
            double armHeight = 13.0 - crouchOffset - swingProgress * 4.0;
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
                              this.getWorld().removeBlock(pos, false);
                              double dist = Math.sqrt(dx * dx + dz * dz);
                              if (dist < 0.1) {
                                 dist = 1.0;
                              }

                              double velX = dx / dist * (0.6 + this.random.nextDouble() * 0.6) + (this.random.nextDouble() - 0.5) * 0.3;
                              double velY = 0.3 + this.random.nextDouble() * 0.7;
                              double velZ = dz / dist * (0.6 + this.random.nextDouble() * 0.6) + (this.random.nextDouble() - 0.5) * 0.3;
                              FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                              ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                              fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                              fallingBlock.setFallingBlockPos(pos);
                              fallingBlock.setVelocity(velX, velY, velZ);
                              fallingBlock.timeFalling = 1;
                              fallingBlock.dropItem = false;
                              serverLevel.spawnEntity(fallingBlock);
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
                           this.flingBlockFromKnockback(serverLevel, pos, blockState, forwardX, forwardZ);
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

   private void flingBlockFromKnockback(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
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

   public void triggerJump(PlayerEntity player) {
      if (!StrwsRestraintTracker.isFullyRestrained(this)) {
         if (!this.isKnocked()) {
            if (this.isOnGround()
               && !this.isTitanAttacking()
               && !this.isTransforming()
               && !this.isDismounting()
               && !this.isRoaring()
               && !this.isInRockThrowSequence()
               && !this.isFalling()
               && !this.isLanding()
               && this.jumpAnimTicks <= 0
               && this.attackCooldown <= 0
               && this.jumpCooldownTicks <= 0
               && !this.clientJumpActive
               && !this.clientFallActive) {
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
   public boolean isClimbing() {
      return this.isTitanClimbing() || super.isClimbing();
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
         || attacker instanceof ArmoredTitanEntity
         || attacker instanceof ColossalTitanEntity
         || attacker instanceof FemaleTitanEntity
         || attacker instanceof BeastTitanEntity
         || attacker instanceof WarhammerTitanEntity
         || attacker instanceof OgreTitanEntity) {
         float newHealth = this.getHealth() - amount;
         this.setHealth(Math.max(newHealth, 0.0F));
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
               float perHitDamage = this.getMaxHealth() / Math.max(1, ModConfig.get().beastTitanNapeHits);
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

   @Override
   public void remove(RemovalReason reason) {
      NapeSmokeHelper.cleanup(this.getId());
      this.releaseGrabbedEntity();
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

      super.remove(reason);
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient() && !this.configHealthApplied) {
         this.configHealthApplied = true;
         double configHealth = ModConfig.get().beastTitanHealth;
         this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(configHealth);
         this.setHealth((float)configHealth);
      }

      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         this.spawnHitboxes();
      }

      if (!this.getWorld().isClient()) {
         UUID shifter = this.getShifterUUID();
         boolean protect = shifter != null && ModCommands.isRoyalBeastEnabled(shifter);
         if (protect != this.isHookProtected()) {
            this.setHookProtected(protect);
         }
      }

      if (!this.getWorld().isClient()) {
         boolean canClimbNow = !this.isImpaled()
            && !this.isDismounting()
            && this.getTransformationTicks() <= 0
            && !this.isJumping()
            && this.jumpAnimTicks <= 0
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

      int transformTicks = this.getTransformationTicks();
      if (transformTicks > 0) {
         this.setTransformationTicks(transformTicks - 1);
         if (transformTicks - 1 <= 0 && this.hasNoGravity()) {
            this.setNoGravity(false);
         }
      }

      if (!this.getWorld().isClient()) {
         if (this.attackAnimationTicks > 0) {
            this.attackAnimationTicks--;
            if (this.attackAnimationTicks <= 0) {
               this.setTitanAttacking(false);
            }
         }

         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.isTitanAttacking() && !this.attackEffectTriggered) {
            this.attackEffectTimer++;
            int attackNum = this.getAttackNumber();
            int effectTick = attackNum == 1 ? 13 : 12;
            if (this.attackEffectTimer >= 10) {
               float swingProgress = (float)(this.attackEffectTimer - 10) / (effectTick - 10);
               this.destroyBlocksAlongSwing(Math.min(swingProgress, 1.0F));
            }

            if (this.attackEffectTimer >= effectTick) {
               this.dealAttackDamage();
               this.setLastAttackImpactTick(this.age);
               this.attackEffectTriggered = true;
            }
         }

         int phase = this.getRockThrowPhase();
         if (phase == 1) {
            this.rockGrabTicks--;
            int ticksIntoGrab = 55 - this.rockGrabTicks;
            float grabYawRad = (float)Math.toRadians(this.getYaw());
            double grabFwdX = -Math.sin(grabYawRad) * 5.0;
            double grabFwdZ = Math.cos(grabYawRad) * 5.0;
            boolean grabbingEntity = this.getGrabbedEntityId() != -1;
            boolean suppressCrush = grabbingEntity || this.isRockBigMode();
            if (ticksIntoGrab == 7 && !suppressCrush) {
               float scoopYawRad = (float)Math.toRadians(this.getYaw());
               double scoopFwdX = -Math.sin(scoopYawRad) * 2.5;
               double scoopFwdZ = Math.cos(scoopYawRad) * 2.5;
               double scoopX = this.getX() + scoopFwdX;
               double scoopZ = this.getZ() + scoopFwdZ;
               double scoopY = this.getY() + 1.5;
               this.getWorld().playSound(null, scoopX, scoopY, scoopZ, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 5.0F, 0.4F);
               this.getWorld().playSound(null, scoopX, scoopY, scoopZ, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 4.0F, 0.5F);
               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  BlockPos scoopBelow = new BlockPos((int)scoopX, (int)(scoopY - 1.0), (int)scoopZ);
                  BlockState scoopBlock = serverLevel.getBlockState(scoopBelow);
                  if (!scoopBlock.isAir()) {
                     serverLevel.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, scoopBlock), scoopX, scoopY, scoopZ, 30, 0.5, 0.3, 0.5, 0.4);
                  }
               }
            }

            if (ticksIntoGrab == 7) {
               this.setLastGrabImpactTick(this.age);
               if (!suppressCrush) {
                  double impactX = this.getX() + grabFwdX;
                  double impactZ = this.getZ() + grabFwdZ;
                  this.getWorld().playSound(null, impactX, this.getY(), impactZ, ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 6.0F, 0.5F);
                  if (this.getWorld() instanceof ServerWorld serverLevelx) {
                     BlockPos below = new BlockPos((int)impactX, (int)this.getY() - 1, (int)impactZ);
                     BlockState groundBlock = serverLevelx.getBlockState(below);
                     if (!groundBlock.isAir()) {
                        serverLevelx.spawnParticles(
                           new BlockStateParticleEffect(ParticleTypes.BLOCK, groundBlock), impactX, this.getY(), impactZ, 50, 2.0, 0.3, 2.0, 0.15
                        );
                     }
                  }
               }
            }

            boolean shouldRumble = ticksIntoGrab >= 25 && ticksIntoGrab <= 42;
            if (shouldRumble != this.isGrabRumbling()) {
               this.setGrabRumbling(shouldRumble);
            }

            if (shouldRumble && !suppressCrush && this.getWorld() instanceof ServerWorld serverLevelxx) {
               double rumbleFwdX = -Math.sin(grabYawRad) * 8.0;
               double rumbleFwdZ = Math.cos(grabYawRad) * 8.0;
               double rumbleX = this.getX() + rumbleFwdX;
               double rumbleZ = this.getZ() + rumbleFwdZ;
               double rumbleY = this.getY() + 8.5;
               serverLevelxx.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, rumbleX, rumbleY, rumbleZ, 3, 0.5, 0.3, 0.5, 0.02);
               serverLevelxx.spawnParticles(ParticleTypes.SMOKE, rumbleX, rumbleY, rumbleZ, 5, 0.8, 0.3, 0.8, 0.03);
               if (this.rockFireMode) {
                  serverLevelxx.spawnParticles(ParticleTypes.FLAME, rumbleX, rumbleY, rumbleZ, 4, 0.7, 0.4, 0.7, 0.04);
                  serverLevelxx.spawnParticles(ParticleTypes.SMALL_FLAME, rumbleX, rumbleY, rumbleZ, 3, 0.6, 0.4, 0.6, 0.03);
                  serverLevelxx.spawnParticles(ParticleTypes.CRIT, rumbleX, rumbleY, rumbleZ, 4, 0.8, 0.4, 0.8, 0.15);
                  if (ticksIntoGrab % 5 == 0) {
                     serverLevelxx.spawnParticles(ParticleTypes.LAVA, rumbleX, rumbleY, rumbleZ, 1, 0.5, 0.3, 0.5, 0.0);
                  }

                  if (ticksIntoGrab % 8 == 0) {
                     this.getWorld()
                        .playSound(
                           null, rumbleX, rumbleY, rumbleZ, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.5F, 0.6F + this.random.nextFloat() * 0.2F
                        );
                  }
               }

               if (ticksIntoGrab % 4 == 0) {
                  this.getWorld()
                     .playSound(
                        null, rumbleX, rumbleY, rumbleZ, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 3.0F, 0.4F + this.random.nextFloat() * 0.3F
                     );
               }

               if (ticksIntoGrab % 6 == 0) {
                  this.getWorld()
                     .playSound(
                        null, rumbleX, rumbleY, rumbleZ, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 2.5F, 0.5F + this.random.nextFloat() * 0.2F
                     );
               }
            }

            if (this.rockGrabTicks <= 0) {
               this.setRockThrowPhase(2);
               this.setGrabRumbling(false);
               this.calculateDimensions();
               this.throwExitBlendTicks = 10;
            }
         } else if (phase == 3) {
            this.throwTicks--;
            int ticksIntoThrow = 78 - this.throwTicks;
            if (ticksIntoThrow == 44) {
               this.setLastThrowImpactTick(this.age);
               if (this.getGrabbedEntityId() != -1) {
                  this.launchGrabbedEntity();
               } else if (this.rockProjectilesSpawned == 0) {
                  this.spawnRockProjectiles();
                  this.rockProjectilesSpawned = ModConfig.get().beastRockCount;
               }

               this.getWorld()
                  .playSound(null, this.getX(), this.getY() + 15.0, this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 8.0F, 0.3F);
               this.getWorld().playSound(null, this.getX(), this.getY() + 15.0, this.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.HOSTILE, 6.0F, 0.4F);
               this.getWorld().playSound(null, this.getX(), this.getY() + 15.0, this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 8.0F, 0.4F);
               if (this.getWorld() instanceof ServerWorld serverLevelxx) {
                  float yawRad = (float)Math.toRadians(this.getYaw());
                  double launchX = this.getX() + -Math.sin(yawRad) * 2.0;
                  double launchZ = this.getZ() + Math.cos(yawRad) * 2.0;
                  double launchY = this.getY() + (this.isInSneakingPose() ? 10.0 : 15.0);
                  serverLevelxx.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, launchX, launchY, launchZ, 15, 1.0, 0.5, 1.0, 0.05);
               }
            }

            if (this.throwTicks <= 0) {
               this.setRockThrowPhase(0);
               this.setThrowTurning(false);
               this.rockProjectilesSpawned = 0;
               this.rockFireMode = false;
               this.setRockBigMode(false);
               this.attackCooldown = 15;
               this.throwExitBlendTicks = 10;
               this.releaseGrabbedEntity();
            }
         }

         if (this.rockFireMode
            && this.getGrabbedEntityId() == -1
            && this.grabHitboxEntity != null
            && !this.grabHitboxEntity.isRemoved()
            && this.getWorld() instanceof ServerWorld handFireLevel) {
            boolean handFireActive = phase == 2 || phase == 3 && 78 - this.throwTicks < 44;
            if (handFireActive) {
               double hx = this.grabHitboxEntity.getX();
               double hy = this.grabHitboxEntity.getY() + this.grabHitboxEntity.getHeight() / 2.0;
               double hz = this.grabHitboxEntity.getZ();
               handFireLevel.spawnParticles(ParticleTypes.FLAME, hx, hy, hz, 5, 0.6, 0.5, 0.6, 0.02);
               handFireLevel.spawnParticles(ParticleTypes.SMALL_FLAME, hx, hy, hz, 4, 0.5, 0.4, 0.5, 0.02);
               handFireLevel.spawnParticles(ParticleTypes.CRIT, hx, hy, hz, 4, 0.7, 0.5, 0.7, 0.15);
               if (this.age % 4 == 0) {
                  handFireLevel.spawnParticles(ParticleTypes.LAVA, hx, hy, hz, 1, 0.4, 0.3, 0.4, 0.0);
               }

               if (this.age % 12 == 0) {
                  this.getWorld()
                     .playSound(null, hx, hy, hz, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.7F + this.random.nextFloat() * 0.2F);
               }
            }
         }
      }

      if (!this.getWorld().isClient()) {
         int grabbedId = this.getGrabbedEntityId();
         if (grabbedId != -1) {
            Entity grabbed = this.getWorld().getEntityById(grabbedId);
            if (grabbed != null && grabbed.isAlive()) {
               if (this.grabHitboxEntity != null && !this.grabHitboxEntity.isRemoved() && !(grabbed.getVehicle() instanceof BeastTitanGrabEntity)) {
                  grabbed.startRiding(this.grabHitboxEntity, true);
               }

               grabbed.setVelocity(Vec3d.ZERO);
               grabbed.fallDistance = 0.0F;
            } else {
               this.setGrabbedEntityId(-1);
            }
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

      boolean currentlyDismounting = this.isDismounting();
      if (this.wasDismountingLastTick && !currentlyDismounting) {
         this.dismountExitBlendTicks = 10;
      }

      this.wasDismountingLastTick = currentlyDismounting;
      if (this.dismountExitBlendTicks > 0) {
         this.dismountExitBlendTicks--;
      }

      if (this.throwExitBlendTicks > 0) {
         this.throwExitBlendTicks--;
      }

      if (this.blindnessTicks > 0) {
         this.blindnessTicks--;
      }

      if (this.hitSlowTicks > 0) {
         this.hitSlowTicks--;
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

      if (!this.getWorld().isClient() && this.roarTicks > 0) {
         this.roarTicks--;
         if (this.roarTicks <= 0) {
            this.setRoaring(false);
            this.setBeastRoarAbilityActive(false);
         }
      }

      if (!this.getWorld().isClient() && this.beastRoarCooldownTicks > 0) {
         this.beastRoarCooldownTicks--;
      }

      if (!this.getWorld().isClient() && this.bossBar != null) {
         if (!this.isDefeated() && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getHealth() + 0.05F, this.getMaxHealth()));
         }

         this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
         if (this.getWorld() instanceof ServerWorld serverLevelxx) {
            for (ServerPlayerEntity serverPlayer : serverLevelxx.getPlayers()) {
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

      if (!this.getWorld().isClient() && !this.isPlayerControlled()) {
         boolean isActuallyMoving = this.getVelocity().horizontalLengthSquared() > 0.001;
         this.setMoving(isActuallyMoving);
      }

      if (!this.getWorld().isClient() && this.isAlive() && !this.isDismounting() && this.getRockThrowPhase() != 1 && this.getRockThrowPhase() != 3) {
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
         if (isActuallyMoving && this.stompCooldown <= 0) {
            long walkingTicks = this.age - this.walkStartTick;
            if (walkingTicks >= 10L) {
               double ticksPerCycle;
               if (this.getRockThrowPhase() == 2) {
                  ticksPerCycle = 90.0;
               } else if (this.isSprinting()) {
                  ticksPerCycle = 40.0;
               } else {
                  ticksPerCycle = 60.0;
               }

               double currentAnimTime = walkingTicks % ticksPerCycle / 20.0;
               int keyframeIndex = this.getStompKeyframeIndex(currentAnimTime);
               if (keyframeIndex >= 0 && keyframeIndex != this.lastStompKeyframeIndex) {
                  this.triggerStompEffects();
                  this.setLastStompTick(this.age);
                  this.lastStompKeyframeIndex = keyframeIndex;
                  this.stompCooldown = this.getRockThrowPhase() == 2 ? 27 : (this.isSprinting() ? 12 : 18);
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
               float progress = 1.0F - (this.knockedElapsedTicks - 1) / 30.0F;
               float factor = (float)Math.sqrt(Math.max(0.0F, progress));
               double sp = 1.575 * factor;
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

         if (slidePhase && this.isOnGround() && this.getWorld() instanceof ServerWorld serverLevelxx) {
            BlockState groundBlock = this.getWorld().getBlockState(this.getBlockPos().down());
            if (!groundBlock.isAir()) {
               serverLevelxx.spawnParticles(
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

   private int getStompKeyframeIndex(double animTime) {
      double cycleLength;
      double[] keyframes;
      if (this.getRockThrowPhase() == 2) {
         cycleLength = 4.5;
         keyframes = THROW_WALK_STOMP_KEYFRAMES;
      } else if (this.isSprinting()) {
         cycleLength = 2.0;
         keyframes = new double[]{0.5, 1.5};
      } else {
         cycleLength = 3.0;
         keyframes = STOMP_KEYFRAMES;
      }

      double cycleTime = animTime % cycleLength;

      for (int i = 0; i < keyframes.length; i++) {
         double diff = Math.abs(cycleTime - keyframes[i]);
         if (diff < 0.15) {
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

         boolean isRunningForSound = this.isSprinting();
         float stompVolume = isRunningForSound ? 8.0F : 5.3F;
         if (this.isInSneakingPose()) {
            stompVolume *= 0.5F;
         }

         float randomPitch = 0.5F + this.random.nextFloat() * 0.1F;
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, stompVolume, randomPitch);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement_controller", 6, this::movementPredicate));
      controllers.add(new AnimationController(this, "action_controller", 10, this::actionPredicate));
   }

   private PlayState movementPredicate(AnimationState<BeastTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      state.getController().transitionLength(6);
      if (this.isDefeated()) {
         return PlayState.STOP;
      } else if (this.isTransforming()) {
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.isKnocked()) {
         return PlayState.STOP;
      } else if (this.getRockThrowPhase() == 1 || this.getRockThrowPhase() == 3) {
         return PlayState.STOP;
      } else if (this.isDismounting()) {
         state.getController().setAnimationSpeed(0.0);
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.dismountExitBlendTicks > 0) {
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.isJumpingForAnim()) {
         return state.setAndContinue(JUMP_ANIM);
      } else if (this.isFallingForAnim()) {
         return state.setAndContinue(FALLING_ANIM);
      } else if (this.isLanding()) {
         return state.setAndContinue(LAND_ANIM);
      } else if (this.throwExitBlendTicks > 0) {
         state.getController().transitionLength(10);
         return state.setAndContinue(IDLE_ANIM);
      } else if (this.getRockThrowPhase() == 2) {
         return this.isMoving() ? state.setAndContinue(THROW_WALK_ANIM) : state.setAndContinue(THROW_IDLE_LOWER_ANIM);
      } else if (this.isTitanClimbing()) {
         return state.setAndContinue(CLIMB_ANIM);
      } else if (this.isInSneakingPose()) {
         return this.isMoving() ? state.setAndContinue(CROUCH_WALK_ANIM) : state.setAndContinue(CROUCH_ANIM);
      } else if (!this.isMoving()) {
         return state.setAndContinue(IDLE_ANIM);
      } else {
         return this.isSprinting() ? state.setAndContinue(RUN_ANIM) : state.setAndContinue(WALK_ANIM);
      }
   }

   private PlayState actionPredicate(AnimationState<BeastTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      state.getController().transitionLength(10);
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
      } else {
         int rockPhase = this.getRockThrowPhase();
         if (rockPhase == 1) {
            state.getController().transitionLength(2);
            return state.setAndContinue(this.isRockBigMode() ? ROCK_GRAB_BIG_ANIM : ROCK_GRAB_ANIM);
         } else if (rockPhase == 2) {
            return state.setAndContinue(this.isRockBigMode() ? THROW_IDLE_UPPER_BIG_ANIM : THROW_IDLE_UPPER_ANIM);
         } else if (rockPhase == 3) {
            return state.setAndContinue(this.isRockBigMode() ? THROW_BIG_ANIM : THROW_ANIM);
         } else if (this.isTitanAttacking()) {
            return switch (this.getAttackNumber()) {
               case 1 -> state.setAndContinue(ATTACK1_ANIM);
               case 2 -> state.setAndContinue(ATTACK2_ANIM);
               default -> state.setAndContinue(ATTACK1_ANIM);
            };
         } else if (this.isDismounting()) {
            return state.setAndContinue(DISMOUNT_ANIM);
         } else if (this.dismountExitBlendTicks > 0) {
            return state.setAndContinue(UNDISMOUNT_ANIM);
         } else {
            if (this.throwExitBlendTicks > 0) {
               state.getController().transitionLength(10);
            }

            if (this.isRoaring()) {
               return state.setAndContinue(ROAR_ANIM);
            } else if (this.isInSneakingPose()) {
               return this.isMoving() ? state.setAndContinue(CROUCH_WALK_UPPER_ANIM) : state.setAndContinue(CROUCH_UPPER_ANIM);
            } else if (this.isMoving()) {
               return this.isSprinting() ? state.setAndContinue(RUN_UPPER_ANIM) : state.setAndContinue(WALK_UPPER_ANIM);
            } else {
               return state.setAndContinue(IDLE_UPPER_ANIM);
            }
         }
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
