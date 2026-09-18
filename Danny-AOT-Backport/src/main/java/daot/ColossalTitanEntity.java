package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.EffectPayload;
import daot.network.ModNetworking;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
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
import net.minecraft.particle.ParticleEffect;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ColossalTitanEntity extends HostileEntity implements GeoEntity, ShifterTitan {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static BiConsumer<AnimationController<ColossalTitanEntity>, ColossalTitanEntity> MOVEMENT_KEYFRAME_INSTALLER = null;
   private static final TrackedData<Boolean> DATA_IS_MOVING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      ColossalTitanEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_TRANSFORMATION_TICKS = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_DISMOUNTING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_LAST_STOMP_TICK = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_LAST_ATTACK_IMPACT_TICK = DataTracker.registerData(
      ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER
   );
   private static final TrackedData<Boolean> DATA_IS_ATTACKING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ATTACK_NUMBER = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_STEAMING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_INFERNAL_HEATING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_DEFEATED = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation SMASH_ANIM = RawAnimation.begin().thenPlayAndHold("smash");
   private static final RawAnimation STEAM_ANIM = RawAnimation.begin().thenPlayAndHold("steam");
   private static final RawAnimation KICK_ANIM = RawAnimation.begin().thenPlayAndHold("kick");
   private static final RawAnimation ARM_DRAG_ANIM = RawAnimation.begin().thenPlayAndHold("arm_drag");
   private static final RawAnimation SHIFT_ANIM = RawAnimation.begin().thenPlay("shift");
   private static final RawAnimation DISMOUNT_ANIM = RawAnimation.begin().thenLoop("dismount");
   private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");
   private static final double MOVEMENT_SPEED = 0.15;
   private static final double CONTROLLED_MOVEMENT_SPEED = 0.2;
   private long walkStartTick = 0L;
   private boolean wasMovingLastTick = false;
   private static final double STOMP_THRESHOLD = 0.15;
   private static final double[] STOMP_KEYFRAMES = new double[]{0.0, 2.0};
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
   private static final int DISMOUNT_VISIBILITY_DELAY_TICKS = 3;
   private long despawnAtGameTime = -1L;
   private static final int DISMOUNT_DESPAWN_TICKS = 1200;
   public static final int ATTACK1 = 1;
   public static final int ATTACK2 = 2;
   public static final int SMASH = 3;
   private int attackCooldown = 0;
   private static final int ATTACK1_TICKS = 130;
   private static final int ATTACK2_TICKS = 130;
   private static final int SMASH_TICKS = 140;
   private int attackAnimationTicks = 0;
   private int lastAttackNumber = 3;
   private static final int ATTACK1_EFFECT_TICK = 80;
   private static final int ATTACK2_EFFECT_TICK = 80;
   private static final int SMASH_EFFECT_TICK = 66;
   private int attackEffectTimer = 0;
   private boolean attackEffectTriggered = false;
   private static final int ATTACK_DESTRUCTION_TICKS = 10;
   private int attackDestructionTicksRemaining = 0;
   private static final int STEAM_TICKS = 300;
   private static final int STEAM_EFFECT_DELAY = 50;
   private static final double STEAM_PARTICLE_RADIUS = 512.0;
   private int steamAnimationTicks = 0;
   private int steamCooldown = 0;
   private static final int STEAM_COOLDOWN_TICKS = 400;
   private static final float INFERNAL_HEAT_DRAIN = 1.0F;
   private int infernalHeatTicksElapsed = 0;
   private ColossalTitanNapeEntity napeEntity = null;
   private ColossalTitanEyeEntity eyeEntity = null;
   private ColossalTitanHandEntity handEntity = null;
   private static final int BLINDNESS_DURATION_TICKS = 120;
   private int blindnessTicks = 0;
   private int hitSlowTicks = 0;
   private Vec3d slideVelocity = null;
   private int slideTicks = 0;
   private static final int SLIDE_DURATION = 8;
   private float lockedYaw = Float.NaN;
   private int lastFleshImpactIndex = -1;
   private int deathAnimTicks = -1;
   private static final int DEATH_IMPACT_TICK_1 = 30;
   private static final int DEATH_IMPACT_TICK_2 = 55;
   private static final float INCAP_HEALTH_FLOOR = 1.0F;
   private static final int INCAPACITATE_DURATION_TICKS = 200;
   private int incapacitateTicks = 0;
   private boolean configHealthApplied = false;
   private ServerBossBar bossBar = null;
   private int explosionPhase = 0;
   private boolean crouchShifted = false;
   private static final int NUKE_CRATER_RADIUS = 120;
   private static final int NUKE_CRATER_DEPTH = 28;
   private static final int NUKE_CONVERSION_RADIUS = 160;
   private static final int NUKE_FIRE_RADIUS = 200;
   private static final double NUKE_BLAST_RANGE = 400.0;
   private static final int NUKE_LEVEL_MAX_HEIGHT = 60;
   private static final int NUKE_DEBRIS_MAX = 500;
   private static final double NUKE_PARTICLE_RADIUS = 10000.0;
   private static final int EXPLOSION_MAX_PHASES = 20;
   private double explosionCenterX = 0.0;
   private double explosionCenterY = 0.0;
   private double explosionCenterZ = 0.0;
   private int nukeDebrisSpawned = 0;
   private static final int KICK_TICKS = 90;
   private static final int KICK_EFFECT_TICK = 60;
   private int kickAnimationTicks = 0;
   private int kickCooldown = 0;
   private boolean kickEffectTriggered = false;
   private static final TrackedData<Boolean> DATA_IS_KICKING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final int KICK_BREACH_LENGTH = 30;
   private static final double KICK_BREACH_HALF_WIDTH = 5.5;
   private static final double KICK_BREACH_HEIGHT = 20.0;
   private static final double KICK_BREACH_SHOULDER = 0.45;
   private static final double KICK_BREACH_FLOOR_DEPTH = 3.0;
   private static final double KICK_BREACH_RUGGEDNESS = 0.5;
   private static final int KICK_MAX_LAUNCHED_BLOCKS = 260;
   private static final int KICK_ROCK_COUNT = 14;
   private static final int KICK_DUST_TICKS = 50;
   private static final double KICK_PARTICLE_RADIUS = 400.0;
   private int kickDustTicks = 0;
   private double kickDustX;
   private double kickDustY;
   private double kickDustZ;
   private double kickDustDirX;
   private double kickDustDirZ;
   private static final int ARM_DRAG_TICKS = 160;
   private static final int ARM_DRAG_COOLDOWN_TICKS = 40;
   private static final int ARM_DRAG_TERRAIN_START = 116;
   private static final int ARM_DRAG_TERRAIN_END = 128;
   private static final int ARM_DRAG_SHAKE_LIGHT_START = 29;
   private static final int ARM_DRAG_SHAKE_LIGHT_END = 34;
   private static final int ARM_DRAG_SHAKE_HEAVY_START = 116;
   private static final int ARM_DRAG_SHAKE_HEAVY_END = 128;
   private static final int ARM_DRAG_TEAR_DEPTH = 3;
   private static final int ARM_DRAG_ROCKS_PER_TICK = 4;
   private int armDragAnimationTicks = 0;
   private int armDragCooldown = 0;
   private boolean armDragThrown = false;
   private final Set<Long> armDragTornColumns = new HashSet<>();
   private Vec3d prevArmDragHandPos = null;
   private static final TrackedData<Integer> DATA_LAST_HIT_TICK = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_SLIDING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_HIT_DIR_YAW = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_LOW_STAMINA = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_INCAPACITATED = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_ARM_DRAGGING = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_ARM_DRAG_SHAKE = DataTracker.registerData(ColossalTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final float ATTACK_DRAIN = 5.0F;
   private static final float ABILITY_DRAIN = 15.0F;
   private boolean wasTransforming = false;
   private boolean hasPlayedShiftAnim = false;

   public ColossalTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public ColossalTitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public ColossalTitanHandEntity getHandEntity() {
      return this.handEntity;
   }

   public ColossalTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_IS_MOVING, false);
      this.dataTracker.startTracking(DATA_SHIFTER_UUID, Optional.empty());
      this.dataTracker.startTracking(DATA_TRANSFORMATION_TICKS, 0);
      this.dataTracker.startTracking(DATA_IS_DISMOUNTING, false);
      this.dataTracker.startTracking(DATA_LAST_STOMP_TICK, 0);
      this.dataTracker.startTracking(DATA_LAST_ATTACK_IMPACT_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_ATTACKING, false);
      this.dataTracker.startTracking(DATA_ATTACK_NUMBER, 1);
      this.dataTracker.startTracking(DATA_IS_STEAMING, false);
      this.dataTracker.startTracking(DATA_IS_INFERNAL_HEATING, false);
      this.dataTracker.startTracking(DATA_IS_KICKING, false);
      this.dataTracker.startTracking(DATA_IS_DEFEATED, false);
      this.dataTracker.startTracking(DATA_IS_INCAPACITATED, false);
      this.dataTracker.startTracking(DATA_IS_ARM_DRAGGING, false);
      this.dataTracker.startTracking(DATA_ARM_DRAG_SHAKE, 0);
      this.dataTracker.startTracking(DATA_LAST_HIT_TICK, 0);
      this.dataTracker.startTracking(DATA_SLIDING, false);
      this.dataTracker.startTracking(DATA_HIT_DIR_YAW, 0.0F);
      this.dataTracker.startTracking(DATA_LOW_STAMINA, false);
   }

   public boolean isLowStamina() {
      return this.dataTracker.get(DATA_LOW_STAMINA);
   }

   public void spawnHitboxes() {
      if (!this.getWorld().isClient() && this.napeEntity == null && this.eyeEntity == null) {
         ColossalTitanNapeEntity nape = new ColossalTitanNapeEntity(DannysAot.COLOSSAL_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         ColossalTitanEyeEntity eye = new ColossalTitanEyeEntity(DannysAot.COLOSSAL_TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
         ColossalTitanHandEntity hand = new ColossalTitanHandEntity(DannysAot.COLOSSAL_TITAN_HAND, this.getWorld());
         hand.setParentTitan(this);
         hand.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(hand);
         this.handEntity = hand;
         DannysAot.LOGGER.info("Spawned Colossal Titan hitboxes");
      }
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

   public int getLastAttackImpactTick() {
      return this.dataTracker.get(DATA_LAST_ATTACK_IMPACT_TICK);
   }

   public void setLastAttackImpactTick(int tick) {
      this.dataTracker.set(DATA_LAST_ATTACK_IMPACT_TICK, tick);
   }

   public boolean isDismountAllowed() {
      return this.isIncapacitated() ? false : this.allowDismount || this.isDismounting();
   }

   public void setDismountAllowed(boolean allowed) {
      this.allowDismount = allowed;
   }

   public boolean isPlayerControlled() {
      return this.getShifterUUID() != null && this.getControllingPassenger() instanceof PlayerEntity;
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

   public boolean isSteaming() {
      return this.dataTracker.get(DATA_IS_STEAMING);
   }

   public void setSteaming(boolean steaming) {
      this.dataTracker.set(DATA_IS_STEAMING, steaming);
   }

   public boolean isInfernalHeating() {
      return this.dataTracker.get(DATA_IS_INFERNAL_HEATING);
   }

   public void setInfernalHeating(boolean active) {
      this.dataTracker.set(DATA_IS_INFERNAL_HEATING, active);
   }

   public boolean isVentingAny() {
      return this.isSteaming() || this.isInfernalHeating();
   }

   public boolean isKicking() {
      return this.dataTracker.get(DATA_IS_KICKING);
   }

   public void setKicking(boolean kicking) {
      this.dataTracker.set(DATA_IS_KICKING, kicking);
   }

   public boolean isArmDragging() {
      return this.dataTracker.get(DATA_IS_ARM_DRAGGING);
   }

   public void setArmDragging(boolean dragging) {
      this.dataTracker.set(DATA_IS_ARM_DRAGGING, dragging);
   }

   public int getArmDragShake() {
      return this.dataTracker.get(DATA_ARM_DRAG_SHAKE);
   }

   private void setArmDragShake(int level) {
      if (this.dataTracker.get(DATA_ARM_DRAG_SHAKE) != level) {
         this.dataTracker.set(DATA_ARM_DRAG_SHAKE, level);
      }
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

   public void triggerAttack() {
      if (!this.isDefeated()
         && this.attackCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isVentingAny()
         && !this.isKicking()
         && !this.isArmDragging()) {
         int nextAttack = switch (this.lastAttackNumber) {
            case 1 -> 2;
            case 2 -> 3;
            default -> 1;
         };
         this.lastAttackNumber = nextAttack;

         int attackTicks = switch (nextAttack) {
            case 1 -> 130;
            case 2 -> 130;
            case 3 -> 140;
            default -> 130;
         };
         this.setAttackNumber(nextAttack);
         this.setTitanAttacking(true);
         this.attackAnimationTicks = attackTicks;
         this.attackCooldown = attackTicks;
         this.attackEffectTimer = 0;
         this.attackEffectTriggered = false;
      }
   }

   public void triggerSteamAbility() {
      if (!this.isDefeated()
         && this.steamCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isTitanAttacking()
         && !this.isInfernalHeating()
         && !this.isKicking()
         && !this.isArmDragging()) {
         this.setSteaming(true);
         this.steamAnimationTicks = 300;
         this.steamCooldown = 700;
         this.playVentRoar();
      }
   }

   private void startInfernalHeat() {
      this.setInfernalHeating(true);
      this.infernalHeatTicksElapsed = 0;
      this.playVentRoar();
   }

   private void playVentRoar() {
      this.getWorld().playSound(null, this.getX(), this.getY() + 30.0, this.getZ(), ModSounds.ATTACK_TITAN_ROAR, SoundCategory.HOSTILE, 10.0F, 0.2F);
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double eyeOffsetForward = 8.0;
         double eyeHeightOffset = 56.0;
         double eyeX = this.getX() + -Math.sin(yawRad) * eyeOffsetForward;
         double eyeY = this.getY() + eyeHeightOffset;
         double eyeZ = this.getZ() + Math.cos(yawRad) * eyeOffsetForward;
         EffectPayload roarPayload = new EffectPayload("roar", eyeX, eyeY, eyeZ, 1.5F);

         for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
            ServerPlayNetworking.send(player, roarPayload);
         }
      }
   }

   public void triggerKickAbility() {
      if (!this.isDefeated()
         && this.kickCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isTitanAttacking()
         && !this.isVentingAny()
         && !this.isKicking()
         && !this.isArmDragging()) {
         this.setKicking(true);
         this.kickAnimationTicks = 90;
         this.kickCooldown = 130;
         this.kickEffectTriggered = false;
      }
   }

   public void triggerArmDragAbility() {
      if (!this.isDefeated()
         && this.armDragCooldown <= 0
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isTitanAttacking()
         && !this.isVentingAny()
         && !this.isKicking()
         && !this.isArmDragging()) {
         this.setArmDragging(true);
         this.armDragAnimationTicks = 160;
         this.armDragCooldown = 200;
         this.armDragThrown = false;
         this.armDragTornColumns.clear();
         this.prevArmDragHandPos = null;
         this.setArmDragShake(0);
      }
   }

   public void triggerInfernalHeatAbility() {
      if (this.isInfernalHeating()) {
         this.setInfernalHeating(false);
      } else if (!this.isDefeated()
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isTitanAttacking()
         && !this.isSteaming()
         && !this.isKicking()
         && !this.isArmDragging()) {
         this.startInfernalHeat();
      }
   }

   private void performKick() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double kickRadius = 70.0;
         double kickMaxHeight = 30.0;
         Box kickArea = new Box(
            this.getX() - kickRadius, this.getY(), this.getZ() - kickRadius, this.getX() + kickRadius, this.getY() + kickMaxHeight, this.getZ() + kickRadius
         );

         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, kickArea)) {
            if (target != this && target.getVehicle() != this) {
               UUID shifterUUID = this.getShifterUUID();
               if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
                  && !(target instanceof AttackTitanNapeEntity)
                  && !(target instanceof AttackTitanEyeEntity)
                  && !(target instanceof ArmoredTitanNapeEntity)
                  && !(target instanceof ArmoredTitanEyeEntity)
                  && !(target instanceof ColossalTitanNapeEntity)
                  && !(target instanceof ColossalTitanEyeEntity)) {
                  Vec3d toTarget = target.getPos().subtract(this.getPos());
                  double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
                  if (dot > 0.0 && toTarget.horizontalLength() < 70.0) {
                     if (target instanceof TitanNapeEntity napeEntity) {
                        TitanEntity parentTitan = napeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
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
                     } else if (target instanceof FritzTitanNapeEntity fritzNapeEntity) {
                        FritzTitanEntity parentTitan = fritzNapeEntity.getParentTitan();
                        if (parentTitan != null) {
                           parentTitan.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
                        }
                     } else if (target instanceof TitanEntity titanTarget) {
                        Vec3d knockback = toTarget.normalize().multiply(100.0, 60.0, 100.0);
                        titanTarget.applyKnockback(knockback);
                     } else if (isShifterTitan(target)) {
                        float kickDmg = 300.0F;
                        boolean armedBlock = this.checkArmedBlock(target);
                        if (armedBlock) {
                           kickDmg *= 0.5F;
                        }

                        target.damage(this.getDamageSources().mobAttack(this), kickDmg);
                        this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                        this.spawnShifterHitParticles(target);
                        Vec3d kickDir = new Vec3d(forwardX, 0.0, forwardZ).normalize();
                        Vec3d knockback = kickDir.multiply(armedBlock ? 75.0 : 150.0).add(0.0, armedBlock ? 37.5 : 75.0, 0.0);
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
                        float kickDmgx = 100.0F;
                        target.damage(this.getDamageSources().mobAttack(this), kickDmgx);
                        this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                        Vec3d kickDir = new Vec3d(forwardX, 0.5, forwardZ).normalize();
                        Vec3d knockback = kickDir.multiply(50.0, 25.0, 50.0);
                        target.setVelocity(knockback);
                        if (target instanceof ServerPlayerEntity sp) {
                           sp.velocityModified = true;
                        }
                     }
                  }
               }
            }
         }

         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            this.carveKickBreach(serverLevel, forwardX, forwardZ);
         }

         this.spawnKickRockVolley(serverLevel, forwardX, forwardZ);
         this.startKickDust(forwardX, forwardZ);
         this.getWorld()
            .playSound(
               null,
               this.getX() + forwardX * 30.0,
               this.getY(),
               this.getZ() + forwardZ * 30.0,
               SoundEvents.ENTITY_GENERIC_EXPLODE,
               SoundCategory.HOSTILE,
               12.0F,
               0.3F
            );
         this.getWorld()
            .playSound(
               null,
               this.getX() + forwardX * 8.0,
               this.getY(),
               this.getZ() + forwardZ * 8.0,
               SoundEvents.BLOCK_STONE_BREAK,
               SoundCategory.HOSTILE,
               14.0F,
               0.35F
            );
      }
   }

   private void carveKickBreach(ServerWorld serverLevel, double forwardX, double forwardZ) {
      double perpX = -forwardZ;
      double perpZ = forwardX;
      int startY = (int)Math.floor(this.getY());
      int launchedBlocks = 0;

      for (int dist = 5; dist <= 30; dist++) {
         double centerX = this.getX() + forwardX * dist;
         double centerZ = this.getZ() + forwardZ * dist;
         double t = (dist - 5) / 25.0;
         double taper = 0.75 + 0.25 * Math.sin(t * Math.PI);
         double halfWidth = 5.5 * taper;
         double height = 20.0 * (0.8 + 0.2 * Math.sin(t * Math.PI));
         double shoulder = height * 0.45;
         int scanHalf = (int)Math.ceil(halfWidth * 1.5) + 1;
         int scanTop = (int)Math.ceil(height * 1.5) + 1;
         int scanFloor = (int)Math.ceil(3.0) + 1;

         for (int side = -scanHalf; side <= scanHalf; side++) {
            int wx = (int)Math.floor(centerX + perpX * side);
            int wz = (int)Math.floor(centerZ + perpZ * side);
            double lateral = Math.abs(side) / halfWidth;
            if (!(lateral > 1.5)) {
               for (int dy = -scanFloor; dy <= scanTop; dy++) {
                  int wy = startY + dy;
                  double vertical;
                  if (dy < 0) {
                     vertical = -dy / 3.0;
                  } else if (dy <= shoulder) {
                     vertical = 0.0;
                  } else {
                     vertical = (dy - shoulder) / (height - shoulder);
                  }

                  double field = vertical == 0.0 ? lateral : Math.hypot(lateral, vertical);
                  double n = breachNoise(wx, wy, wz, 5.0) * 0.7 + breachNoise(wx, wy, wz, 1.8) * 0.3;
                  if (!(field > 1.0 + (n - 0.5) * 0.5)) {
                     BlockPos pos = new BlockPos(wx, wy, wz);
                     BlockState state = this.getWorld().getBlockState(pos);
                     if (!state.isAir()
                        && !(state.getHardness(this.getWorld(), pos) < 0.0F)
                        && !state.isIn(BlockTags.WITHER_IMMUNE)
                        && (!(state.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() >= 0.05F))) {
                        float launchChance = field > 0.6 ? 0.4F : 0.18F;
                        if (launchedBlocks < 260 && this.random.nextFloat() < launchChance) {
                           double velX = forwardX * 2.5 + (this.random.nextDouble() - 0.5) * 1.6;
                           double velY = 0.8 + this.random.nextDouble() * 1.4;
                           double velZ = forwardZ * 2.5 + (this.random.nextDouble() - 0.5) * 1.6;
                           FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                           ((FallingBlockEntityAccessor)fallingBlock).setBlockState(state);
                           fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                           fallingBlock.setFallingBlockPos(pos);
                           fallingBlock.setVelocity(velX, velY, velZ);
                           fallingBlock.timeFalling = state.getBlock() instanceof FluidBlock ? 590 : 1;
                           fallingBlock.dropItem = false;
                           serverLevel.spawnEntity(fallingBlock);
                           launchedBlocks++;
                        } else if (this.random.nextFloat() < 0.06F) {
                           serverLevel.spawnParticles(
                              new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                              pos.getX() + 0.5,
                              pos.getY() + 0.5,
                              pos.getZ() + 0.5,
                              6,
                              0.6,
                              0.6,
                              0.6,
                              0.2
                           );
                        }

                        this.getWorld().removeBlock(pos, false);
                     }
                  }
               }
            }
         }
      }
   }

   private static double breachNoise(int x, int y, int z, double scale) {
      double sx = x / scale;
      double sy = y / scale;
      double sz = z / scale;
      int x0 = (int)Math.floor(sx);
      int y0 = (int)Math.floor(sy);
      int z0 = (int)Math.floor(sz);
      double fx = smoothStep(sx - x0);
      double fy = smoothStep(sy - y0);
      double fz = smoothStep(sz - z0);
      double c00 = lerp(fx, latticeValue(x0, y0, z0), latticeValue(x0 + 1, y0, z0));
      double c10 = lerp(fx, latticeValue(x0, y0 + 1, z0), latticeValue(x0 + 1, y0 + 1, z0));
      double c01 = lerp(fx, latticeValue(x0, y0, z0 + 1), latticeValue(x0 + 1, y0, z0 + 1));
      double c11 = lerp(fx, latticeValue(x0, y0 + 1, z0 + 1), latticeValue(x0 + 1, y0 + 1, z0 + 1));
      return lerp(fz, lerp(fy, c00, c10), lerp(fy, c01, c11));
   }

   private static double smoothStep(double t) {
      return t * t * (3.0 - 2.0 * t);
   }

   private static double lerp(double t, double a, double b) {
      return a + t * (b - a);
   }

   private static double latticeValue(int x, int y, int z) {
      long h = x * 341873128712L + y * 132897987541L + z * 1911520717L;
      h ^= h >>> 33;
      h *= -49064778989728563L;
      h ^= h >>> 33;
      h *= -4265267296055464877L;
      h ^= h >>> 33;
      return (h >>> 11) / 9.007199E15F;
   }

   private void spawnKickRockVolley(ServerWorld serverLevel, double forwardX, double forwardZ) {
      double originX = this.getX() + forwardX * 8.0;
      double originZ = this.getZ() + forwardZ * 8.0;
      double originY = this.getY() + 2.0;

      for (int i = 0; i < 14; i++) {
         double spread = Math.toRadians((this.random.nextDouble() - 0.5) * 110.0);
         double dirX = forwardX * Math.cos(spread) - forwardZ * Math.sin(spread);
         double dirZ = forwardX * Math.sin(spread) + forwardZ * Math.cos(spread);
         RockProjectileEntity rock = new RockProjectileEntity(DannysAot.ROCK_PROJECTILE, serverLevel);
         rock.setRockScale(2.5F + this.random.nextFloat() * 4.0F);
         rock.setOwnerUUID(this.getShifterUUID());
         rock.setPosition(
            originX + dirX * 3.0 + (this.random.nextDouble() - 0.5) * 6.0,
            originY + this.random.nextDouble() * 4.0,
            originZ + dirZ * 3.0 + (this.random.nextDouble() - 0.5) * 6.0
         );
         double speed = 2.2 + this.random.nextDouble() * 1.6;
         double up = 0.6 + this.random.nextDouble() * 0.7;
         if (this.random.nextFloat() < 0.4F) {
            up *= 2.2 + this.random.nextDouble() * 0.9;
            speed *= 0.55;
         }

         rock.setVelocity(dirX * speed, up, dirZ * speed);
         rock.setCustomGravity(0.06);
         serverLevel.spawnEntity(rock);
      }
   }

   private void startKickDust(double forwardX, double forwardZ) {
      this.kickDustX = this.getX() + forwardX * 14.0;
      this.kickDustY = this.getY();
      this.kickDustZ = this.getZ() + forwardZ * 14.0;
      this.kickDustDirX = forwardX;
      this.kickDustDirZ = forwardZ;
      this.kickDustTicks = 50;
   }

   private void emitKickDust(ServerWorld serverLevel, int phase) {
      double cx = this.kickDustX;
      double cy = this.kickDustY;
      double cz = this.kickDustZ;
      if (phase <= 14) {
         double ringR = 4.0 + phase * 4.5;
         int pts = 16;

         for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2) * i / pts;
            this.sendKickParticles(
               serverLevel,
               DannysAot.NUKE_DUST_PARTICLE,
               cx + Math.cos(a) * ringR,
               cy + 1.5,
               cz + Math.sin(a) * ringR,
               0,
               Math.cos(a) * 0.55,
               0.1,
               Math.sin(a) * 0.55,
               1.0
            );
         }
      }

      if (phase <= 26) {
         double plumeY = cy + 2.0 + phase * 1.6;

         for (int i = 0; i < 5; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double rr = this.random.nextDouble() * (5.0 + phase * 0.7);
            this.sendKickParticles(
               serverLevel,
               DannysAot.NUKE_SMOKE_PARTICLE,
               cx + Math.cos(a) * rr + this.kickDustDirX * phase * 0.4,
               plumeY,
               cz + Math.sin(a) * rr + this.kickDustDirZ * phase * 0.4,
               0,
               (this.random.nextDouble() - 0.5) * 0.2,
               0.22,
               (this.random.nextDouble() - 0.5) * 0.2,
               1.0
            );
         }

         for (int i = 0; i < 4; i++) {
            double along = this.random.nextDouble() * 30.0;
            this.sendKickParticles(
               serverLevel,
               DannysAot.NUKE_DUST_PARTICLE,
               this.getX() + this.kickDustDirX * along + (this.random.nextDouble() - 0.5) * 10.0,
               cy + 1.0 + this.random.nextDouble() * 8.0,
               this.getZ() + this.kickDustDirZ * along + (this.random.nextDouble() - 0.5) * 10.0,
               3,
               3.0,
               2.5,
               3.0,
               0.06
            );
         }
      }

      if (phase >= 18) {
         for (int i = 0; i < 6; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double rr = this.random.nextDouble() * 40.0;
            this.sendKickParticles(
               serverLevel,
               DannysAot.NUKE_DUST_PARTICLE,
               cx + Math.cos(a) * rr,
               cy + 3.0 + this.random.nextDouble() * 20.0,
               cz + Math.sin(a) * rr,
               4,
               5.0,
               4.0,
               5.0,
               0.04
            );
         }
      }
   }

   private void flingBlockFromGroundsmash(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double forwardX, double forwardZ) {
      this.getWorld().removeBlock(pos, false);
      double velX = forwardX * (1.5 + this.random.nextDouble() * 1.0) + (this.random.nextDouble() - 0.5) * 1.0;
      double velY = 0.8 + this.random.nextDouble() * 1.2;
      double velZ = forwardZ * (1.5 + this.random.nextDouble() * 1.0) + (this.random.nextDouble() - 0.5) * 1.0;
      FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
      fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fallingBlock.setFallingBlockPos(pos);
      fallingBlock.setVelocity(velX, velY, velZ);
      fallingBlock.timeFalling = blockState.getBlock() instanceof FluidBlock ? 590 : 1;
      fallingBlock.dropItem = false;
      serverLevel.spawnEntity(fallingBlock);
   }

   private void destroyBlocksAlongSwing(float swingProgress) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            float yawRad = (float)Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double rightX = forwardZ;
            double rightZ = -forwardX;
            double sideOffset = (1.0 - swingProgress) * 12.0;
            double forwardDist = 6.0 + swingProgress * 14.0;
            double armHeight = 45.0 - swingProgress * 15.0;
            int baseY = (int)Math.floor(this.getY() + armHeight);
            int handRadius = 5;
            int forearmRadius = 10;
            double[][] armPoints = new double[][]{{forwardDist * 0.5, sideOffset * 0.5}, {forwardDist, sideOffset}};
            int[] radii = new int[]{forearmRadius, handRadius};

            for (int i = 0; i < armPoints.length; i++) {
               double[] point = armPoints[i];
               int radius = radii[i];
               double centerX = this.getX() + forwardX * point[0] + rightX * point[1];
               double centerZ = this.getZ() + forwardZ * point[0] + rightZ * point[1];

               for (int dx = -radius; dx <= radius; dx++) {
                  for (int dz = -radius; dz <= radius; dz++) {
                     if (dx * dx + dz * dz <= radius * radius) {
                        for (int dy = -3; dy <= 5; dy++) {
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
   }

   private void performArmDragTerrain(boolean throwPhase) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            Vec3d handPos = this.getArmDragHandPos();
            int tearRadius = 7;
            Vec3d from = this.prevArmDragHandPos != null ? this.prevArmDragHandPos : handPos;
            double dist = Math.hypot(handPos.x - from.x, handPos.z - from.z);
            int steps = Math.max(1, (int)Math.ceil(dist / 2.0));

            for (int s = 0; s <= steps; s++) {
               double t = (double)s / steps;
               double px = from.x + (handPos.x - from.x) * t;
               double pz = from.z + (handPos.z - from.z) * t;
               this.tearSurfaceAround(serverLevel, MathHelper.floor(px), MathHelper.floor(pz), tearRadius);
            }

            this.prevArmDragHandPos = handPos;
            if (this.age % 3 == 0) {
               this.getWorld().playSound(null, handPos.x, handPos.y, handPos.z, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 6.0F, 0.5F);
            }
         }
      }
   }

   private void tearSurfaceAround(ServerWorld serverLevel, int cx, int cz, int radius) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius) {
               int wx = cx + dx;
               int wz = cz + dz;
               long key = (long)wx << 32 ^ wz & 4294967295L;
               if (this.armDragTornColumns.add(key)) {
                  int surfaceY = serverLevel.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;

                  for (int dy = 0; dy < 3; dy++) {
                     BlockPos pos = new BlockPos(wx, surfaceY - dy, wz);
                     BlockState state = this.getWorld().getBlockState(pos);
                     if (!state.isAir()
                        && !(state.getHardness(this.getWorld(), pos) < 0.0F)
                        && !state.isIn(BlockTags.WITHER_IMMUNE)
                        && !(state.getBlock() instanceof FluidBlock)) {
                        if (dy == 0 && this.random.nextFloat() < 0.4F) {
                           FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                           ((FallingBlockEntityAccessor)fb).setBlockState(state);
                           fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                           fb.setFallingBlockPos(pos);
                           fb.setVelocity((this.random.nextDouble() - 0.5) * 0.6, 0.4 + this.random.nextDouble() * 0.6, (this.random.nextDouble() - 0.5) * 0.6);
                           fb.timeFalling = 1;
                           fb.dropItem = false;
                           serverLevel.spawnEntity(fb);
                        } else {
                           serverLevel.spawnParticles(
                              new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                              pos.getX() + 0.5,
                              pos.getY() + 0.7,
                              pos.getZ() + 0.5,
                              10,
                              0.5,
                              0.3,
                              0.5,
                              0.12
                           );
                        }

                        this.getWorld().removeBlock(pos, false);
                     }
                  }
               }
            }
         }
      }
   }

   private Vec3d getArmDragHandPos() {
      if (this.handEntity != null) {
         return this.handEntity.getHandWorldPos();
      } else {
         float yawR = (float)Math.toRadians(this.bodyYaw);
         return new Vec3d(this.getX() - Math.sin(yawR) * 6.0, this.getY() + 30.0, this.getZ() + Math.cos(yawR) * 6.0);
      }
   }

   private void spawnArmDragRock(float scale) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         Vec3d var26 = this.getArmDragHandPos();
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double spread = Math.toRadians((this.random.nextDouble() - 0.5) * 50.0);
         double dirX = forwardX * Math.cos(spread) - forwardZ * Math.sin(spread);
         double dirZ = forwardX * Math.sin(spread) + forwardZ * Math.cos(spread);
         RockProjectileEntity rock = new RockProjectileEntity(DannysAot.ROCK_PROJECTILE, serverLevel);
         rock.setRockScale(scale);
         rock.setFireMode(true);
         rock.setOwnerUUID(this.getShifterUUID());
         double sx = var26.x + dirX * 3.0 + (this.random.nextDouble() - 0.5) * 3.0;
         double sy = Math.max(var26.y, this.getY() + 2.0) + scale * 0.5;
         double sz = var26.z + dirZ * 3.0 + (this.random.nextDouble() - 0.5) * 3.0;
         rock.setPosition(sx, sy, sz);
         double speed = 2.6 + this.random.nextDouble() * 1.4;
         double up = 0.7 + this.random.nextDouble() * 0.6;
         if (this.random.nextFloat() < 0.35F) {
            up *= 2.2 + this.random.nextDouble() * 0.8;
            speed *= 0.55;
         }

         rock.setVelocity(dirX * speed, up, dirZ * speed);
         rock.setCustomGravity(0.06);
         serverLevel.spawnEntity(rock);
         this.getWorld().playSound(null, var26.x, var26.y, var26.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 6.0F, 0.5F);
      }
   }

   private void dealArmDragDamage(Vec3d center, double radius, float damage) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         Box area = new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
         UUID shifterUUID = this.getShifterUUID();

         for (LivingEntity target : serverLevel.getNonSpectatingEntities(LivingEntity.class, area)) {
            if (target != this
               && target.getVehicle() != this
               && (shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(target instanceof ColossalTitanHandEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                     )
               )) {
               double dh = Math.hypot(target.getX() - center.x, target.getZ() - center.z);
               if (!(dh > radius)) {
                  Vec3d away = new Vec3d(target.getX() - center.x, 0.0, target.getZ() - center.z);
                  Vec3d dir = away.lengthSquared() < 1.0E-4 ? new Vec3d(forwardX, 0.0, forwardZ) : away.normalize();
                  if (target instanceof TitanEntity titanTarget) {
                     titanTarget.applyKnockback(dir.multiply(30.0, 0.0, 30.0).add(0.0, 20.0, 0.0));
                  } else if (isShifterTitan(target)) {
                     float dmg = damage * 2.0F;
                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        dmg *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), dmg);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     Vec3d kb = dir.multiply(armedBlock ? 12.0 : 24.0).add(0.0, armedBlock ? 6.0 : 12.0, 0.0);
                     if (target instanceof AttackTitanEntity at) {
                        at.setPendingKnockback(kb);
                        at.applyHitSlow(20);
                        at.triggerHitReaction(this, false, false);
                     } else if (target instanceof FemaleTitanEntity ft) {
                        ft.setPendingKnockback(kb);
                        ft.applyHitSlow(20);
                        ft.triggerHitReaction(this, false, false);
                     } else if (target instanceof ArmoredTitanEntity art) {
                        art.setPendingKnockback(kb);
                        art.applyHitSlow(20);
                        art.triggerHitReaction(this, false, false);
                     } else if (target instanceof ColossalTitanEntity ct) {
                        ct.setPendingKnockback(kb);
                        ct.applyHitSlow(20);
                     }
                  } else {
                     target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, damage));
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     Vec3d kb = dir.multiply(14.0, 0.0, 14.0).add(0.0, 8.0, 0.0);
                     target.setVelocity(kb);
                     if (target instanceof ServerPlayerEntity sp) {
                        sp.velocityModified = true;
                     }
                  }
               }
            }
         }
      }
   }

   private void dealAttackDamage() {
      SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
      SoundEvent sound = impactSounds[this.random.nextInt(impactSounds.length)];
      float pitch = 0.9F + this.random.nextFloat() * 0.1F;
      float yaw = (float)Math.toRadians(this.getYaw());
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      this.getWorld().playSound(null, this.getX() + fx * 15.0, this.getY() + 30.0, this.getZ() + fz * 15.0, sound, SoundCategory.HOSTILE, 8.0F, pitch);
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      double attackRadius = ModConfig.get().colossalTitanAttackRange;
      double titanHeight = this.getHeight();
      Box attackArea = new Box(
         this.getX() - attackRadius, this.getY(), this.getZ() - attackRadius, this.getX() + attackRadius, this.getY() + titanHeight, this.getZ() + attackRadius
      );

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, attackArea)) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)
               && !(
                  target instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                     )
               )) {
               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
               if (dot > 0.1 && toTarget.horizontalLength() < ModConfig.get().colossalTitanAttackRange) {
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
                  } else if (target instanceof TitanEntity titanTarget) {
                     Vec3d knockback = toTarget.normalize().multiply(40.0, 30.0, 40.0);
                     titanTarget.applyKnockback(knockback);
                  } else if (isShifterTitan(target)) {
                     float atkDmg = (float)(ModConfig.get().colossalTitanAttackDamage * 2.5);
                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        atkDmg *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), atkDmg);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                     Vec3d knockback = horizontalDir.multiply(armedBlock ? 8.0 : 16.0).add(0.0, armedBlock ? 4.0 : 8.0, 0.0);
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
                     float atkDmgx = (float)ModConfig.get().colossalTitanAttackDamage;
                     target.damage(this.getDamageSources().mobAttack(this), ModConfig.capPlayerMelee(target, atkDmgx));
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     Vec3d knockback = toTarget.normalize().multiply(12.0, 6.0, 12.0);
                     target.setVelocity(knockback);
                     if (target instanceof ServerPlayerEntity sp) {
                        sp.velocityModified = true;
                     }
                  }
               }
            }
         }
      }

      this.getWorld()
         .playSound(null, this.getX(), this.getY() + 30.0, this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 5.0F, 0.5F);
      if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
         boolean isAbility = this.getAttackNumber() == 3 || this.getAttackNumber() == 4;
         ModNetworking.drainStamina(sp.getUuid(), isAbility ? 15.0F : 5.0F);
      }
   }

   private void performSmash() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      double smashRadius = ModConfig.get().colossalTitanSmashRange;
      double titanHeight = this.getHeight();
      Box smashArea = new Box(
         this.getX() - smashRadius, this.getY(), this.getZ() - smashRadius, this.getX() + smashRadius, this.getY() + titanHeight, this.getZ() + smashRadius
      );

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, smashArea)) {
         if (target != this && target.getVehicle() != this) {
            UUID shifterUUID = this.getShifterUUID();
            if ((shifterUUID == null || !target.getUuid().equals(shifterUUID))
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)) {
               Vec3d toTarget = target.getPos().subtract(this.getPos());
               double dot = toTarget.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ));
               if (dot > -0.1 && toTarget.horizontalLength() < ModConfig.get().colossalTitanSmashRange) {
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
                  } else if (target instanceof TitanEntity titanTarget) {
                     Vec3d knockback = toTarget.normalize().multiply(50.0, 40.0, 50.0);
                     titanTarget.applyKnockback(knockback);
                  } else if (isShifterTitan(target)) {
                     float smashDmg = (float)(ModConfig.get().colossalTitanSmashDamage * 3.0);
                     boolean armedBlock = this.checkArmedBlock(target);
                     if (armedBlock) {
                        smashDmg *= 0.5F;
                     }

                     target.damage(this.getDamageSources().mobAttack(this), smashDmg);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     this.spawnShifterHitParticles(target);
                     Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
                     Vec3d knockback = horizontalDir.multiply(armedBlock ? 12.0 : 24.0).add(0.0, armedBlock ? 6.0 : 12.0, 0.0);
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
                     float smashDmgx = (float)ModConfig.get().colossalTitanSmashDamage;
                     target.damage(this.getDamageSources().mobAttack(this), smashDmgx);
                     this.playFleshImpactSound(target.getX(), target.getY(), target.getZ());
                     Vec3d knockback = toTarget.normalize().multiply(18.0, 10.0, 18.0);
                     target.setVelocity(knockback);
                     if (target instanceof ServerPlayerEntity sp) {
                        sp.velocityModified = true;
                     }
                  }
               }
            }
         }
      }

      this.getWorld()
         .playSound(null, this.getX(), this.getY() + 30.0, this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 8.0F, 0.3F);
   }

   private void performSteamEffect() {
      this.performSteamEffect(false);
   }

   private void performSteamEffect(boolean withFire) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         for (int var26 = 0; var26 < 150; var26++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double spawnDist = this.random.nextDouble() * 5.0;
            double spawnX = this.getX() + Math.cos(angle) * spawnDist;
            double spawnZ = this.getZ() + Math.sin(angle) * spawnDist;
            double spawnY = this.getY() + 5.0 + this.random.nextDouble() * 55.0;
            double speed = 0.8 + this.random.nextDouble() * 1.5;
            double velX = Math.cos(angle) * speed;
            double velZ = Math.sin(angle) * speed;
            double velY = 0.3 + (this.random.nextDouble() - 0.3) * 0.5;
            ParticleEffect p1 = withFire && this.random.nextFloat() < 0.5F ? ParticleTypes.SMALL_FLAME : DannysAot.COLOSSAL_STEAM_PARTICLE;
            this.sendSteamParticle(serverLevel, p1, spawnX, spawnY, spawnZ, velX, velY, velZ);
         }

         for (int i = 0; i < 100; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double spawnDist = this.random.nextDouble() * 3.0;
            double spawnX = this.getX() + Math.cos(angle) * spawnDist;
            double spawnZ = this.getZ() + Math.sin(angle) * spawnDist;
            double spawnY = this.getY() + this.random.nextDouble() * 60.0;
            double speed = 1.0 + this.random.nextDouble() * 2.0;
            double velX = Math.cos(angle) * speed;
            double velZ = Math.sin(angle) * speed;
            double velY = 0.3 + (this.random.nextDouble() - 0.2) * 0.3;
            ParticleEffect p2 = withFire && this.random.nextFloat() < 0.5F ? ParticleTypes.FLAME : DannysAot.COLOSSAL_STEAM_PARTICLE;
            this.sendSteamParticle(serverLevel, p2, spawnX, spawnY, spawnZ, velX, velY, velZ);
         }

         for (int i = 0; i < 80; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double spawnDist = this.random.nextDouble() * 4.0;
            double spawnX = this.getX() + Math.cos(angle) * spawnDist;
            double spawnZ = this.getZ() + Math.sin(angle) * spawnDist;
            double spawnY = this.getY() + 10.0 + this.random.nextDouble() * 50.0;
            double speed = 1.2 + this.random.nextDouble() * 1.8;
            double velX = Math.cos(angle) * speed;
            double velZ = Math.sin(angle) * speed;
            double velY = 0.35 + (this.random.nextDouble() - 0.1) * 0.4;
            this.sendSteamParticle(serverLevel, DannysAot.COLOSSAL_STEAM_PARTICLE, spawnX, spawnY, spawnZ, velX, velY, velZ);
         }

         for (int i = 0; i < 60; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double spawnDist = 3.0 + this.random.nextDouble() * 5.0;
            double spawnX = this.getX() + Math.cos(angle) * spawnDist;
            double spawnZ = this.getZ() + Math.sin(angle) * spawnDist;
            double spawnY = this.getY() + 1.0 + this.random.nextDouble() * 3.0;
            double speed = 1.5 + this.random.nextDouble() * 2.0;
            double velX = Math.cos(angle) * speed;
            double velZ = Math.sin(angle) * speed;
            double velY = 0.1 + this.random.nextDouble() * 0.3;
            ParticleEffect p4 = withFire && this.random.nextFloat() < 0.5F ? ParticleTypes.FLAME : DannysAot.COLOSSAL_STEAM_PARTICLE;
            this.sendSteamParticle(serverLevel, p4, spawnX, spawnY, spawnZ, velX, velY, velZ);
         }

         if (this.age % 5 == 0) {
            this.getWorld().playSound(null, this.getX(), this.getY() + 30.0, this.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 8.0F, 0.2F);
         }

         double steamPushRadius = 100.0;
         Box steamArea = this.getBoundingBox().expand(steamPushRadius);
         UUID shifterUUID = this.getShifterUUID();

         for (Entity entity : serverLevel.getOtherEntities(this, steamArea)) {
            if (entity != this
               && (shifterUUID == null || !entity.getUuid().equals(shifterUUID))
               && !(entity instanceof ColossalTitanNapeEntity)
               && !(entity instanceof ColossalTitanEyeEntity)
               && !(entity instanceof AttackTitanNapeEntity)
               && !(entity instanceof AttackTitanEyeEntity)
               && !(entity instanceof ArmoredTitanNapeEntity)
               && !(entity instanceof ArmoredTitanEyeEntity)
               && !(entity instanceof FritzTitanNapeEntity)
               && !(entity instanceof FritzTitanEyeEntity)
               && !(entity instanceof TitanNapeEntity)
               && !(entity instanceof TitanEyeEntity)
               && !(entity instanceof SmallTitanNapeEntity)
               && !(entity instanceof SmallTitanEyeEntity)
               && !(entity instanceof SmallTitan2NapeEntity)
               && !(entity instanceof SmallTitan2EyeEntity)
               && !(
                  entity instanceof PlayerEntity p
                     && (
                        p.getVehicle() instanceof AttackTitanEntity
                           || p.getVehicle() instanceof ArmoredTitanEntity
                           || p.getVehicle() instanceof ColossalTitanEntity
                           || p.getVehicle() instanceof FemaleTitanEntity
                     )
               )) {
               if (withFire && !(entity instanceof ColossalTitanEntity) && entity.distanceTo(this) <= steamPushRadius) {
                  entity.setFireTicks(Math.max(entity.getFireTicks(), 80));
               }

               boolean isTitanOrShifter = entity instanceof TitanEntity
                  || entity instanceof FritzTitanEntity
                  || entity instanceof SmallTitanEntity
                  || entity instanceof SmallTitan2Entity
                  || entity instanceof AttackTitanEntity
                  || entity instanceof ArmoredTitanEntity
                  || entity instanceof ColossalTitanEntity
                  || entity instanceof FemaleTitanEntity;
               if (isTitanOrShifter) {
                  Vec3d toEntity = entity.getPos().subtract(this.getPos());
                  double distance = toEntity.horizontalLength();
                  if (!(distance < 5.0) && !(distance > steamPushRadius)) {
                     double intensity = 1.0 - distance / steamPushRadius;
                     Vec3d pushDir = new Vec3d(toEntity.x, 0.0, toEntity.z).normalize();
                     double pushStrength = 0.5 + 1.0 * intensity * intensity;
                     Vec3d knockback = pushDir.multiply(pushStrength).add(0.0, 0.05 * intensity, 0.0);
                     if (entity instanceof AttackTitanEntity at) {
                        at.setPendingKnockback(knockback);
                     } else if (entity instanceof FemaleTitanEntity ft) {
                        ft.setPendingKnockback(knockback);
                     } else if (entity instanceof ArmoredTitanEntity art) {
                        art.setPendingKnockback(knockback);
                     } else if (entity instanceof ColossalTitanEntity ct) {
                        ct.setPendingKnockback(knockback);
                     } else if (entity instanceof TitanEntity titanTarget) {
                        titanTarget.applyKnockback(knockback);
                     } else if (entity instanceof FritzTitanEntity fritzTarget) {
                        fritzTarget.applyKnockback(knockback);
                     } else {
                        entity.setVelocity(entity.getVelocity().add(knockback));
                        if (entity instanceof LivingEntity living) {
                           living.velocityModified = true;
                        }
                     }

                     if (isShifterTitan((LivingEntity)entity) && entity instanceof LivingEntity living) {
                        living.damage(this.getDamageSources().onFire(), 0.1F);
                     }
                  }
               }
            }
         }

         if (withFire) {
            for (int i = 0; i < 6; i++) {
               double angle = this.random.nextDouble() * Math.PI * 2.0;
               double dist = 6.0 + this.random.nextDouble() * (steamPushRadius - 6.0);
               int bx = MathHelper.floor(this.getX() + Math.cos(angle) * dist);
               int bz = MathHelper.floor(this.getZ() + Math.sin(angle) * dist);
               int by = serverLevel.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
               BlockPos firePos = new BlockPos(bx, by, bz);
               BlockPos belowPos = firePos.down();
               if (serverLevel.isInBuildLimit(firePos)
                  && serverLevel.getBlockState(firePos).isAir()
                  && serverLevel.getBlockState(belowPos).isSideSolidFullSquare(serverLevel, belowPos, Direction.UP)) {
                  BlockState fireState = AbstractFireBlock.getState(serverLevel, firePos);
                  if (!fireState.isAir()) {
                     serverLevel.setBlockState(firePos, fireState, 11);
                  }
               }
            }

            for (int ix = 0; ix < 60; ix++) {
               double angle = this.random.nextDouble() * Math.PI * 2.0;
               double spawnDist = this.random.nextDouble() * 4.0;
               double spawnX = this.getX() + Math.cos(angle) * spawnDist;
               double spawnZ = this.getZ() + Math.sin(angle) * spawnDist;
               double spawnY = this.getY() + 5.0 + this.random.nextDouble() * 55.0;
               double speed = 0.9 + this.random.nextDouble() * 1.6;
               double velX = Math.cos(angle) * speed;
               double velZ = Math.sin(angle) * speed;
               double velY = 0.3 + (this.random.nextDouble() - 0.2) * 0.3;
               this.sendSteamParticle(serverLevel, ParticleTypes.FLAME, spawnX, spawnY, spawnZ, velX, velY, velZ);
            }
         }
      }
   }

   public void triggerBlindness() {
      this.blindnessTicks = 120;
      DannysAot.LOGGER.info("Colossal Titan blinded for 6 seconds!");
      if (this.getControllingPassenger() instanceof PlayerEntity player) {
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0, false, false, true));
         player.sendMessage(Text.literal("You have been blinded!"), true);
      }
   }

   public void hurtFromNape(DamageSource source, float amount) {
      if (!this.getWorld().isClient() && !this.isDefeated()) {
         if (this.isDismounting()) {
            this.onDeath(source);
         } else {
            float chargeFraction = BladeAttackTracker.consumeNapeChargeFraction();
            if (!(chargeFraction <= 0.0F)) {
               float perHitDamage = this.getMaxHealth() / Math.max(1, ModConfig.get().colossalTitanNapeHits);
               float safeDamage = Math.max(0.0F, Math.min(perHitDamage * chargeFraction, this.getHealth() - 1.0F));
               super.damage(source, safeDamage);
               if (this.getHealth() <= 1.01F) {
                  this.incapacitate();
               }
            }
         }
      }
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 20.0)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.14)
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
   public void onDeath(DamageSource damageSource) {
      if (!this.getWorld().isClient() && !this.isDefeated()) {
         this.setDefeated(true);
         this.setIncapacitated(false);
         this.incapacitateTicks = 0;
         this.blindnessTicks = 0;
         this.deathAnimTicks = 0;
         this.setHealth(1.0F);
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setSteaming(false);
         this.setInfernalHeating(false);
         this.setKicking(false);
         this.setArmDragging(false);
         this.setArmDragShake(0);
         this.attackAnimationTicks = 0;
         this.attackCooldown = 0;
         this.steamAnimationTicks = 0;
         this.kickAnimationTicks = 0;
         this.armDragAnimationTicks = 0;
         this.attackDestructionTicksRemaining = 0;
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
                  DannysAot.LOGGER.info("Colossal Titan defeated with rider {} still attached", serverPlayer.getName().getString());
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
         this.setTitanAttacking(false);
         this.setAttackNumber(0);
         this.setSteaming(false);
         this.setInfernalHeating(false);
         this.setKicking(false);
         this.setArmDragging(false);
         this.setArmDragShake(0);
         this.attackAnimationTicks = 0;
         this.attackCooldown = 0;
         this.steamAnimationTicks = 0;
         this.kickAnimationTicks = 0;
         this.armDragAnimationTicks = 0;
         this.attackDestructionTicksRemaining = 0;
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
      if (this.getFirstPassenger() instanceof PlayerEntity player) {
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            return player;
         }
      }

      return null;
   }

   private Vec3d getRidePosition(Entity passenger) {
      double headHeight = 53.5;
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
            ColossalTitanNapeEntity nape = this.getWorld().isClient() ? ColossalTitanNapeEntity.getClientInstance(this.getId()) : this.napeEntity;
            if (nape != null) {
               double napeY = nape.getY() + nape.getHeight() / 2.0;
               positionUpdater.accept(passenger, nape.getX(), napeY, nape.getZ());
               return;
            }
         }

         Vec3d ridePos = this.getRidePosition(passenger);
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardOffset = 5.0;
         double offsetX = -Math.sin(yawRad) * forwardOffset;
         double offsetZ = Math.cos(yawRad) * forwardOffset;
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
            Vec3d v = this.getVelocity();
            this.setVelocity(0.0, v.y, 0.0);
         }
      } else {
         super.tickControlled(controllingPlayer, movementInput);
         if (!this.isTransforming() && !this.isVentingAny()) {
            if (this.isDismounting()) {
               if (!this.getWorld().isClient()) {
                  this.setMoving(false);
               }
            } else {
               if (this.getWorld().isClient() && !this.isTransforming() && !this.isVentingAny() && !this.isSliding()) {
                  Vec3d movement = this.getVelocity();
                  if (movement.horizontalLengthSquared() > 1.0E-4) {
                     float targetYaw = (float)(Math.atan2(-movement.x, movement.z) * (180.0 / Math.PI));
                     float currentYaw = this.getYaw();
                     float lerpFactor = 0.15F;
                     float newYaw = currentYaw + lerpFactor * wrapDegrees(targetYaw - currentYaw);
                     this.setYaw(newYaw);
                     this.bodyYaw = newYaw;
                     this.headYaw = newYaw;
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
                  this.setMoving(hasInput && !this.isTransforming() && !this.isDismounting() && !this.isVentingAny());
                  if (controllingPlayer instanceof ServerPlayerEntity sp) {
                     boolean lowStamina = ModNetworking.isLowStamina(sp.getUuid());
                     if (lowStamina != this.dataTracker.get(DATA_LOW_STAMINA)) {
                        this.dataTracker.set(DATA_LOW_STAMINA, lowStamina);
                     }
                  }
               }
            }
         } else {
            if (!controllingPlayer.isDead()) {
               controllingPlayer.setInvisible(true);
            }

            if (!this.getWorld().isClient()) {
               this.setMoving(false);
            }
         }
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
   protected float turnHead(float bodyRotation, float headRotation) {
      if (this.isPlayerControlled()) {
         this.bodyYaw = this.getYaw();
         this.headYaw = this.getYaw();
         return headRotation;
      } else {
         return super.turnHead(bodyRotation, headRotation);
      }
   }

   @Override
   protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
      if (!this.isDefeated()
         && !this.isTransforming()
         && !this.isDismounting()
         && !this.isVentingAny()
         && !this.isSliding()
         && !this.isKicking()
         && !this.isArmDragging()) {
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
   protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
      double speed = 0.2;
      if (this.hitSlowTicks > 0) {
         speed *= 0.5;
      }

      if (ModNetworking.isLowStamina(controllingPlayer.getUuid())) {
         speed *= 0.5;
      }

      return (float)speed;
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (passenger instanceof PlayerEntity player) {
         ShifterVisibilityHelper.cancelReentry(player);
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

            if (this.bossBar != null) {
               this.bossBar.clearPlayers();
               this.bossBar = null;
            }

            if (this.crouchShifted) {
               player.setInvisible(true);
               if (!this.getWorld().isClient()) {
                  ModNetworking.scheduleStealthInvisibility(player.getUuid(), 100);
               }

               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  double cx = this.getX();
                  double cy = this.getY() + this.getHeight() * 0.5;
                  double cz = this.getZ();
                  serverLevel.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 40, 4.0, 8.0, 4.0, 0.05);
                  serverLevel.spawnParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 60, 5.0, 10.0, 5.0, 0.05);
                  serverLevel.spawnParticles(ParticleTypes.CLOUD, cx, cy, cz, 30, 3.0, 6.0, 3.0, 0.05);
               }

               this.discard();
            } else {
               player.setInvisible(false);
               this.despawnAtGameTime = this.getWorld().getTime() + 1200L;
               NapeSmokeHelper.onFullDismount(this.getId());
            }
         } else {
            player.setInvisible(false);
         }
      }
   }

   public boolean isDismountToggleOnCooldown() {
      return this.dismountToggleCooldown > 0;
   }

   public void startDismounting(PlayerEntity player) {
      if (!this.isIncapacitated()) {
         if (!this.isDismountToggleOnCooldown()) {
            this.dismountVisibilityDelay = 3;
            this.setDismounting(true);
            this.allowDismount = true;
            this.dismountToggleCooldown = 20;
            NapeSmokeHelper.onDismountStart(this, this.napeEntity);
            float yawRad = (float)Math.toRadians(this.getYaw());
            double napeX = this.getX() + -Math.sin(yawRad) * 5.0;
            double napeY = this.getY() + 53.5;
            double napeZ = this.getZ() + Math.cos(yawRad) * 5.0;
            this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
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
               double napeX = this.getX() + -Math.sin(yawRad) * 5.0;
               double napeY = this.getY() + 53.5;
               double napeZ = this.getZ() + Math.cos(yawRad) * 5.0;
               this.getWorld().playSound(null, napeX, napeY, napeZ, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
         }
      }
   }

   public void onPlayerShift(PlayerEntity player) {
      this.setShifterUUID(player.getUuid());
      player.startRiding(this, true);
      player.setInvisible(true);
      if (player instanceof ServerPlayerEntity serverPlayer) {
         ShifterMarkTracker.markEntered(serverPlayer, this);
      }

      if (!this.getWorld().isClient()) {
         DismountSmokeHelper.onShifterSpawn(this);
         DismountSmokeHelper.cancelPendingDismount(player.getUuid());
      }

      if (!this.getWorld().isClient() && this.getWorld().getGameRules().getBoolean(DannysAot.RULE_SHIFT_BOSS_BARS)) {
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Colossal Titan" : "Colossal Titan - " + player.getName().getString();
         this.bossBar = new ServerBossBar(Text.literal(bossBarName), Color.RED, Style.PROGRESS);
         this.bossBar.setPercent(1.0F);
      }

      this.crouchShifted = player.getCommandTags().contains("titan_stealth");
      if (!this.getWorld().isClient()) {
         this.explosionCenterX = this.getX();
         this.explosionCenterY = this.getY();
         this.explosionCenterZ = this.getZ();
         this.explosionPhase = 1;
         this.nukeDebrisSpawned = 0;
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            double maxRange = this.crouchShifted ? 45.0 : 400.0;

            for (Entity entity : serverLevel.getOtherEntities(this, this.getBoundingBox().expand(maxRange))) {
               if (entity != player
                  && entity != this
                  && !(entity instanceof ColossalTitanNapeEntity)
                  && !(entity instanceof ColossalTitanEyeEntity)
                  && !(entity instanceof AttackTitanNapeEntity)
                  && !(entity instanceof AttackTitanEyeEntity)
                  && !(entity instanceof ArmoredTitanNapeEntity)
                  && !(entity instanceof ArmoredTitanEyeEntity)
                  && !(entity instanceof FritzTitanNapeEntity)
                  && !(entity instanceof FritzTitanEyeEntity)
                  && !(entity instanceof TitanNapeEntity)
                  && !(entity instanceof TitanEyeEntity)
                  && !(entity instanceof SmallTitanNapeEntity)
                  && !(entity instanceof SmallTitanEyeEntity)
                  && !(entity instanceof SmallTitan2NapeEntity)
                  && !(entity instanceof SmallTitan2EyeEntity)
                  && !(
                     entity instanceof PlayerEntity p
                        && (
                           p.getVehicle() instanceof AttackTitanEntity
                              || p.getVehicle() instanceof ArmoredTitanEntity
                              || p.getVehicle() instanceof ColossalTitanEntity
                              || p.getVehicle() instanceof FemaleTitanEntity
                        )
                  )) {
                  Vec3d toEntity = entity.getPos().subtract(this.getPos());
                  double distance = toEntity.length();
                  if (distance > 0.0 && distance < maxRange) {
                     double linearIntensity = (maxRange - distance) / maxRange;
                     Vec3d knockback;
                     if (this.crouchShifted) {
                        if (entity instanceof LivingEntity living) {
                           float damage = (float)(30.0 * linearIntensity * linearIntensity);
                           if (entity instanceof PlayerEntity) {
                              damage *= 0.3F;
                           }

                           living.damage(this.getDamageSources().explosion(null, player), damage);
                        }

                        knockback = toEntity.normalize().multiply(linearIntensity * 5.0).add(0.0, linearIntensity * 4.0, 0.0);
                     } else {
                        knockback = toEntity.normalize().multiply(linearIntensity * 15.0).add(0.0, linearIntensity * 8.0, 0.0);
                        if (entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)) {
                           living.setFireTicks(400);
                        }
                     }

                     if (entity instanceof AttackTitanEntity at) {
                        at.setPendingKnockback(knockback);
                        at.applyHitSlow(20);
                     } else if (entity instanceof FemaleTitanEntity ft) {
                        ft.setPendingKnockback(knockback);
                        ft.applyHitSlow(20);
                     } else if (entity instanceof ArmoredTitanEntity art) {
                        art.setPendingKnockback(knockback);
                        art.applyHitSlow(20);
                     } else if (entity instanceof ColossalTitanEntity ct) {
                        ct.setPendingKnockback(knockback);
                        ct.applyHitSlow(20);
                     } else if (entity instanceof TitanEntity titanTarget) {
                        titanTarget.applyKnockback(knockback);
                     } else if (entity instanceof FritzTitanEntity fritzTarget) {
                        fritzTarget.applyKnockback(knockback);
                     } else {
                        entity.setVelocity(entity.getVelocity().add(knockback));
                        if (entity instanceof LivingEntity living) {
                           living.velocityModified = true;
                        }
                     }
                  }
               }
            }

            this.getWorld()
               .playSound(
                  null,
                  this.explosionCenterX,
                  this.explosionCenterY + 30.0,
                  this.explosionCenterZ,
                  SoundEvents.ENTITY_GENERIC_EXPLODE,
                  SoundCategory.HOSTILE,
                  15.0F,
                  0.2F
               );
         }
      }
   }

   private void processExplosionPhase() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         double var12 = this.explosionCenterX;
         double cy = this.explosionCenterY;
         double cz = this.explosionCenterZ;
         boolean griefing = DannysAot.isTitanGriefingEnabled(this.getWorld())
            && DannysAot.isShifterExplosionDamageEnabled(this.getWorld())
            && !this.crouchShifted;
         if (!this.crouchShifted) {
            this.emitNukeCloud(serverLevel, var12, cy, cz, this.explosionPhase);
         }

         switch (this.explosionPhase) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
               if (griefing) {
                  int band = this.explosionPhase - 1;
                  int dxFrom = -120 + band * 24;
                  int dxTo = band < 9 ? dxFrom + 23 : 120;
                  this.blastClearBand(serverLevel, var12, cy, cz, dxFrom, dxTo);
               }
               break;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
               if (griefing) {
                  int band = this.explosionPhase - 11;
                  int dxFrom = -160 + band * 40;
                  int dxTo = band < 7 ? dxFrom + 39 : 160;
                  this.convertSurfaceBand(var12, cy, cz, dxFrom, dxTo);
               }
               break;
            case 19:
               if (griefing) {
                  this.spreadNukeFire(var12, cy, cz);
               }
            case 20:
         }

         if (this.explosionPhase >= 20) {
            this.explosionPhase = 0;
         } else {
            this.explosionPhase++;
         }
      }
   }

   private void blastClearBand(ServerWorld serverLevel, double cx, double cy, double cz, int dxFrom, int dxTo) {
      int r = 120;
      int maxDepth = 28;
      int baseY = (int)cy;
      double coreFrac = 0.45;

      for (int dx = dxFrom; dx <= dxTo; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            double dist = Math.sqrt((double)dx * dx + (double)dz * dz);
            double ang = Math.atan2(dz, dx);
            double effR = r + Math.sin(ang * 5.0) * 7.0 + Math.sin(ang * 11.0 + 1.7) * 4.0;
            if (!(dist > effR)) {
               int wx = (int)(cx + dx);
               int wz = (int)(cz + dz);
               double t = Math.min(1.0, dist / r);
               int depth = (int)(maxDepth * Math.pow(1.0 - t, 1.4));
               depth += (int)((this.random.nextDouble() - 0.4) * 3.0);
               if (depth < 0) {
                  depth = 0;
               }

               int floorY = baseY - depth;
               double shear;
               if (t <= coreFrac) {
                  shear = 1.0;
               } else {
                  double f = (t - coreFrac) / (1.0 - coreFrac);
                  shear = 1.0 - f * f;
               }

               int colTop = Math.min(serverLevel.getTopY(Type.MOTION_BLOCKING, wx, wz), baseY + 60);
               int removeBottom = (int)(colTop - (colTop - floorY) * shear);
               if (removeBottom < floorY) {
                  removeBottom = floorY;
               }

               for (int y = colTop; y >= removeBottom; y--) {
                  BlockPos pos = new BlockPos(wx, y, wz);
                  BlockState state = serverLevel.getBlockState(pos);
                  if (!state.isAir()
                     && !(state.getHardness(serverLevel, pos) < 0.0F)
                     && !state.isIn(BlockTags.WITHER_IMMUNE)
                     && !(state.getBlock() instanceof FluidBlock)
                     && (y > removeBottom + 1 || !(this.random.nextFloat() < 0.3F))) {
                     if (y >= baseY && dist > 6.0 && this.nukeDebrisSpawned < 500 && this.random.nextFloat() < 0.05F) {
                        this.spawnNukeDebris(serverLevel, pos, state, cx, cz);
                        this.nukeDebrisSpawned++;
                     }

                     serverLevel.removeBlock(pos, false);
                  }
               }
            }
         }
      }
   }

   private void spawnNukeDebris(ServerWorld serverLevel, BlockPos pos, BlockState state, double cx, double cz) {
      FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)fb).setBlockState(state);
      fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fb.setFallingBlockPos(pos);
      double ox = pos.getX() + 0.5 - cx;
      double oz = pos.getZ() + 0.5 - cz;
      double len = Math.sqrt(ox * ox + oz * oz);
      if (len < 0.01) {
         ox = this.random.nextDouble() - 0.5;
         oz = this.random.nextDouble() - 0.5;
         len = 1.0;
      }

      ox /= len;
      oz /= len;
      double outSpeed = 0.7 + this.random.nextDouble() * 1.1;
      double upSpeed = 0.8 + this.random.nextDouble() * 1.2;
      fb.setVelocity(ox * outSpeed, upSpeed, oz * outSpeed);
      fb.timeFalling = 1;
      fb.dropItem = false;
      serverLevel.spawnEntity(fb);
   }

   private void emitNukeCloud(ServerWorld serverLevel, double cx, double cy, double cz, int phase) {
      if (phase <= 2) {
         for (int i = 0; i < 8; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double rr = this.random.nextDouble() * 26.0;
            this.sendNukeParticles(
               serverLevel,
               ParticleTypes.EXPLOSION_EMITTER,
               cx + Math.cos(a) * rr,
               cy + 2.0 + this.random.nextDouble() * 12.0,
               cz + Math.sin(a) * rr,
               0,
               0.0,
               0.0,
               0.0,
               0.0
            );
         }
      }

      if (phase <= 5) {
         double coreY = cy + 6.0 + phase * 10.0;
         this.sendNukeParticles(serverLevel, DannysAot.NUKE_FIRE_PARTICLE, cx, coreY, cz, 26, 12.0, 8.0, 12.0, 0.5);
         this.sendNukeParticles(serverLevel, ParticleTypes.FLAME, cx, coreY, cz, 70, 13.0, 9.0, 13.0, 0.7);
         this.sendNukeParticles(serverLevel, ParticleTypes.LAVA, cx, coreY, cz, 16, 11.0, 7.0, 11.0, 0.3);
      }

      if (phase <= 9) {
         double topY = cy + 10.0 + phase * 16.0;
         int seg = 6;

         for (int s = 0; s < seg; s++) {
            double sy = cy + 8.0 + s * ((topY - cy - 8.0) / seg);
            this.sendNukeParticles(serverLevel, DannysAot.NUKE_SMOKE_PARTICLE, cx, sy, cz, 8, 7.0, 4.0, 7.0, 0.25);
         }
      }

      if (phase >= 3) {
         double capY = cy + 45.0 + Math.min(phase, 16) * 7.0;
         double capR = 14.0 + (phase - 3) * 9.0;
         int pts = 18;

         for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2) * i / pts + phase * 0.15;
            double px = cx + Math.cos(a) * capR;
            double pz = cz + Math.sin(a) * capR;
            this.sendNukeParticles(serverLevel, DannysAot.NUKE_SMOKE_PARTICLE, px, capY, pz, 0, Math.cos(a) * 0.5, 0.25, Math.sin(a) * 0.5, 1.0);
            this.sendNukeParticles(
               serverLevel,
               DannysAot.NUKE_SMOKE_PARTICLE,
               px * 0.6 + cx * 0.4,
               capY + 6.0,
               pz * 0.6 + cz * 0.4,
               0,
               Math.cos(a) * 0.2,
               0.3,
               Math.sin(a) * 0.2,
               1.0
            );
         }
      }

      if (phase <= 8) {
         double ringR = phase * 26.0;
         int pts = 24;

         for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2) * i / pts;
            double px = cx + Math.cos(a) * ringR;
            double pz = cz + Math.sin(a) * ringR;
            this.sendNukeParticles(serverLevel, DannysAot.NUKE_DUST_PARTICLE, px, cy + 2.0, pz, 0, Math.cos(a) * 0.8, 0.12, Math.sin(a) * 0.8, 1.0);
         }
      }

      if (phase >= 8) {
         for (int i = 0; i < 14; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double rr = this.random.nextDouble() * 160.0;
            this.sendNukeParticles(
               serverLevel,
               DannysAot.NUKE_DUST_PARTICLE,
               cx + Math.cos(a) * rr,
               cy + 6.0 + this.random.nextDouble() * 24.0,
               cz + Math.sin(a) * rr,
               6,
               8.0,
               6.0,
               8.0,
               0.05
            );
         }
      }
   }

   private void sendSteamParticle(ServerWorld serverLevel, ParticleEffect particle, double x, double y, double z, double velX, double velY, double velZ) {
      double rSqr = 262144.0;

      for (ServerPlayerEntity player : serverLevel.getPlayers()) {
         if (player.squaredDistanceTo(x, y, z) < rSqr) {
            serverLevel.spawnParticles(player, particle, true, x, y, z, 0, velX, velY, velZ, 1.0);
         }
      }
   }

   private void sendNukeParticles(
      ServerWorld serverLevel, ParticleEffect particle, double x, double y, double z, int count, double sx, double sy, double sz, double speed
   ) {
      this.sendForcedParticles(serverLevel, particle, x, y, z, count, sx, sy, sz, speed, 10000.0);
   }

   private void sendKickParticles(
      ServerWorld serverLevel, ParticleEffect particle, double x, double y, double z, int count, double sx, double sy, double sz, double speed
   ) {
      this.sendForcedParticles(serverLevel, particle, x, y, z, count, sx, sy, sz, speed, 400.0);
   }

   private void sendForcedParticles(
      ServerWorld serverLevel, ParticleEffect particle, double x, double y, double z, int count, double sx, double sy, double sz, double speed, double radius
   ) {
      double rSqr = radius * radius;

      for (ServerPlayerEntity player : serverLevel.getPlayers()) {
         if (player.squaredDistanceTo(x, y, z) < rSqr) {
            serverLevel.spawnParticles(player, particle, true, x, y, z, count, sx, sy, sz, speed);
         }
      }
   }

   private void convertSurfaceBand(double cx, double cy, double cz, int dxFrom, int dxTo) {
      for (int dx = dxFrom; dx <= dxTo; dx++) {
         for (int dz = -160; dz <= 160; dz++) {
            this.convertSurfaceBlock(cx, cy, cz, dx, dz, 160, 28);
         }
      }
   }

   private void spreadNukeFire(double cx, double cy, double cz) {
      int r = 200;
      int craterDepth = 28;

      for (int dx = -r; dx <= r; dx += 2) {
         for (int dz = -r; dz <= r; dz += 2) {
            double dist = Math.sqrt((double)dx * dx + (double)dz * dz);
            if (dist <= r && dist > 10.0 && this.random.nextFloat() < 0.25F) {
               BlockPos firePos = new BlockPos((int)(cx + dx), (int)cy, (int)(cz + dz));

               for (int y = -craterDepth; y <= 15; y++) {
                  BlockPos checkPos = firePos.up(y);
                  if (this.getWorld().getBlockState(checkPos).isAir() && this.getWorld().getBlockState(checkPos.down()).isSolid()) {
                     this.getWorld().setBlockState(checkPos, Blocks.FIRE.getDefaultState(), 3);
                     break;
                  }
               }
            }
         }
      }
   }

   private void convertSurfaceBlock(double cx, double cy, double cz, int dx, int dz, int conversionRadius, int craterDepth) {
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (!(dist > conversionRadius)) {
         for (int dy = 30; dy >= -craterDepth; dy--) {
            BlockPos pos = new BlockPos((int)(cx + dx), (int)(cy + dy), (int)(cz + dz));
            BlockState state = this.getWorld().getBlockState(pos);
            if (!state.isAir()) {
               if (state.isOf(Blocks.STONE)
                  || state.isOf(Blocks.COBBLESTONE)
                  || state.isOf(Blocks.MOSSY_COBBLESTONE)
                  || state.isOf(Blocks.STONE_BRICKS)
                  || state.isOf(Blocks.MOSSY_STONE_BRICKS)
                  || state.isOf(Blocks.CRACKED_STONE_BRICKS)
                  || state.isOf(Blocks.ANDESITE)
                  || state.isOf(Blocks.DIORITE)
                  || state.isOf(Blocks.GRANITE)
                  || state.isOf(Blocks.DEEPSLATE)
                  || state.isOf(Blocks.COBBLED_DEEPSLATE)) {
                  BlockState newState = this.random.nextBoolean() ? Blocks.BASALT.getDefaultState() : Blocks.SMOOTH_BASALT.getDefaultState();
                  this.getWorld().setBlockState(pos, newState, 3);
                  break;
               }

               if (state.isOf(Blocks.DIRT)
                  || state.isOf(Blocks.GRASS_BLOCK)
                  || state.isOf(Blocks.PODZOL)
                  || state.isOf(Blocks.MYCELIUM)
                  || state.isOf(Blocks.COARSE_DIRT)
                  || state.isOf(Blocks.ROOTED_DIRT)
                  || state.isOf(Blocks.SAND)
                  || state.isOf(Blocks.RED_SAND)
                  || state.isOf(Blocks.GRAVEL)) {
                  this.getWorld().setBlockState(pos, Blocks.BLACKSTONE.getDefaultState(), 3);
                  break;
               }

               if (state.isIn(BlockTags.LOGS)) {
                  this.getWorld().setBlockState(pos, Blocks.BLACKSTONE.getDefaultState(), 3);
               } else if (state.isIn(BlockTags.LEAVES)) {
                  this.getWorld().removeBlock(pos, false);
               } else if (state.isSolid()) {
                  break;
               }
            }
         }
      }
   }

   private static boolean isShifterTitan(LivingEntity target) {
      return target instanceof AttackTitanEntity
         || target instanceof ArmoredTitanEntity
         || target instanceof ColossalTitanEntity
         || target instanceof FemaleTitanEntity
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
            serverLevel.spawnParticles(bloodParticle, var10, impactY, impactZ, 60, 1.5, 1.5, 1.5, 0.3);
         }
      }
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

      if (this.handEntity != null && !this.handEntity.isRemoved()) {
         this.handEntity.discard();
      }

      super.remove(reason);
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient() && !this.configHealthApplied) {
         this.configHealthApplied = true;
         double configHealth = ModConfig.get().colossalTitanHealth;
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

      if (this.blindnessTicks > 0) {
         this.blindnessTicks--;
      }

      if (this.hitSlowTicks > 0) {
         this.hitSlowTicks--;
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

      if (!this.getWorld().isClient() && this.explosionPhase > 0) {
         this.processExplosionPhase();
      }

      if (this.dismountToggleCooldown > 0) {
         this.dismountToggleCooldown--;
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

         if (this.isTitanAttacking() && !this.attackEffectTriggered) {
            this.attackEffectTimer++;
            int attackNum = this.getAttackNumber();

            int effectTick = switch (attackNum) {
               case 1 -> 80;
               case 2 -> 80;
               case 3 -> 66;
               default -> 80;
            };
            if (this.attackEffectTimer >= effectTick) {
               if (attackNum == 3) {
                  this.performSmash();
               } else {
                  this.dealAttackDamage();
               }

               this.setLastAttackImpactTick(this.age);
               this.attackDestructionTicksRemaining = 10;
               this.attackEffectTriggered = true;
            }
         }

         if (this.attackDestructionTicksRemaining > 0) {
            int elapsed = 10 - this.attackDestructionTicksRemaining;
            float progress = elapsed / 10.0F;
            this.destroyBlocksAlongSwing(progress);
            this.attackDestructionTicksRemaining--;
         }

         if (this.steamCooldown > 0) {
            this.steamCooldown--;
         }

         if (this.steamAnimationTicks > 0) {
            this.steamAnimationTicks--;
            int ticksElapsed = 300 - this.steamAnimationTicks;
            if (ticksElapsed >= 50) {
               this.performSteamEffect();
            }

            if (this.steamAnimationTicks <= 0) {
               this.setSteaming(false);
            }
         }

         if (this.isInfernalHeating()) {
            if (!this.isTransforming() && !this.isDismounting() && !this.isDefeated()) {
               this.infernalHeatTicksElapsed++;
               if (this.infernalHeatTicksElapsed >= 50) {
                  this.performSteamEffect(true);
                  if (this.getFirstPassenger() instanceof ServerPlayerEntity sp) {
                     ModNetworking.drainStamina(sp.getUuid(), 1.0F);
                     if (ModNetworking.getStamina(sp.getUuid()) <= 0.0F) {
                        this.setInfernalHeating(false);
                     }
                  } else {
                     this.setInfernalHeating(false);
                  }
               }
            } else {
               this.setInfernalHeating(false);
               this.infernalHeatTicksElapsed = 0;
            }
         } else if (this.infernalHeatTicksElapsed != 0) {
            this.infernalHeatTicksElapsed = 0;
         }

         if (this.kickCooldown > 0) {
            this.kickCooldown--;
         }

         if (this.kickAnimationTicks > 0) {
            this.kickAnimationTicks--;
            int ticksElapsedx = 90 - this.kickAnimationTicks;
            if (!this.kickEffectTriggered && ticksElapsedx >= 60) {
               this.performKick();
               this.setLastAttackImpactTick(this.age);
               this.kickEffectTriggered = true;
            }

            if (this.kickAnimationTicks <= 0) {
               this.setKicking(false);
            }
         }

         if (this.kickDustTicks > 0 && this.getWorld() instanceof ServerWorld dustLevel) {
            this.emitKickDust(dustLevel, 50 - this.kickDustTicks);
            this.kickDustTicks--;
         }

         if (this.armDragCooldown > 0) {
            this.armDragCooldown--;
         }

         if (this.armDragAnimationTicks > 0) {
            this.armDragAnimationTicks--;
            int ticksElapsedxx = 160 - this.armDragAnimationTicks;
            int shakeLevel = 0;
            if (ticksElapsedxx >= 116 && ticksElapsedxx <= 128) {
               shakeLevel = 2;
            } else if (ticksElapsedxx >= 29 && ticksElapsedxx <= 34) {
               shakeLevel = 1;
            }

            this.setArmDragShake(shakeLevel);
            if (ticksElapsedxx >= 116 && ticksElapsedxx <= 128) {
               this.performArmDragTerrain(false);
               this.dealArmDragDamage(this.getArmDragHandPos(), 12.0, (float)ModConfig.get().colossalTitanSmashDamage);
               if (ticksElapsedxx < 128) {
                  for (int r = 0; r < 4; r++) {
                     this.spawnArmDragRock(3.0F + this.random.nextFloat() * 5.0F);
                  }
               } else if (!this.armDragThrown) {
                  for (int r = 0; r < 3; r++) {
                     this.spawnArmDragRock(9.0F + this.random.nextFloat());
                  }

                  this.armDragThrown = true;
                  this.setLastAttackImpactTick(this.age);
               }
            }

            if (this.armDragAnimationTicks <= 0) {
               this.setArmDragging(false);
               this.setArmDragShake(0);
            }
         }
      }

      if (!this.getWorld().isClient() && this.isDefeated() && this.deathAnimTicks >= 0) {
         this.deathAnimTicks++;
         if (this.deathAnimTicks == 30 || this.deathAnimTicks == 55) {
            this.triggerStompEffects(this.deathAnimTicks == 30 ? -1 : 1);
            this.setLastStompTick(this.age);
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

      if (!this.getWorld().isClient() && this.isIncapacitated() && !this.isDefeated() && this.incapacitateTicks > 0) {
         this.incapacitateTicks--;
         if (this.incapacitateTicks <= 0) {
            this.recover();
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

      if (!this.getWorld().isClient() && this.isAlive()) {
         this.breakBlocksInPath();
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
         boolean fullBodyLegLock = this.isKicking() || this.isVentingAny() || this.isArmDragging();
         if (isActuallyMoving && this.stompCooldown <= 0 && !fullBodyLegLock) {
            long walkingTicks = this.age - this.walkStartTick;
            if (walkingTicks >= 10L) {
               double ticksPerCycle = 64.0;
               double currentAnimTime = walkingTicks % ticksPerCycle / 20.0;
               int keyframeIndex = this.getStompKeyframeIndex(currentAnimTime);
               if (keyframeIndex >= 0 && keyframeIndex != this.lastStompKeyframeIndex) {
                  int footSide = keyframeIndex == 0 ? -1 : 1;
                  this.triggerStompEffects(footSide);
                  this.setLastStompTick(this.age);
                  this.lastStompKeyframeIndex = keyframeIndex;
                  this.stompCooldown = 15;
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
         if (this.getFirstPassenger() instanceof ServerPlayerEntity spx) {
            spx.velocityModified = true;
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
      double cycleTime = animTime % 3.2;

      for (int i = 0; i < STOMP_KEYFRAMES.length; i++) {
         double diff = Math.abs(cycleTime - STOMP_KEYFRAMES[i]);
         if (diff < 0.15) {
            return i;
         }
      }

      return -1;
   }

   private void triggerStompEffects(int footSide) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double rightX = -Math.cos(yawRad);
         double rightZ = -Math.sin(yawRad);
         double footDistance = 8.0;
         double footOffsetX = rightX * footSide * footDistance;
         double footOffsetZ = rightZ * footSide * footDistance;
         double footX = this.getX() + footOffsetX;
         double footZ = this.getZ() + footOffsetZ;
         double stompDamageRadius = 15.0;
         double stompKnockbackRadius = 32.0;
         double maxHeightAboveFeet = 10.0;
         Box stompArea = new Box(
            this.getX() - stompKnockbackRadius,
            this.getY(),
            this.getZ() - stompKnockbackRadius,
            this.getX() + stompKnockbackRadius,
            this.getY() + maxHeightAboveFeet,
            this.getZ() + stompKnockbackRadius
         );

         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, stompArea)) {
            if (target.getVehicle() != this
               && target != this
               && !(target instanceof AttackTitanNapeEntity)
               && !(target instanceof AttackTitanEyeEntity)
               && !(target instanceof ArmoredTitanNapeEntity)
               && !(target instanceof ArmoredTitanEyeEntity)
               && !(target instanceof ColossalTitanNapeEntity)
               && !(target instanceof ColossalTitanEyeEntity)) {
               UUID shifterUUID = this.getShifterUUID();
               if (shifterUUID == null || !target.getUuid().equals(shifterUUID)) {
                  double dx = target.getX() - footX;
                  double dz = target.getZ() - footZ;
                  double distance = Math.sqrt(dx * dx + dz * dz);
                  double knockbackIntensity = Math.max(0.0, 1.0 - distance / stompKnockbackRadius);
                  if (distance < stompDamageRadius) {
                     float damage = (float)(20.0 * (1.0 - distance / stompDamageRadius));
                     if (isShifterTitan(target)) {
                        damage = Math.min(damage, 3.0F);
                     }

                     target.damage(this.getDamageSources().mobAttack(this), damage);
                  }

                  if (knockbackIntensity > 0.0) {
                     Vec3d knockback = new Vec3d(dx, 0.0, dz)
                        .normalize()
                        .multiply(knockbackIntensity * 2.0, knockbackIntensity * 1.5, knockbackIntensity * 2.0);
                     target.setVelocity(target.getVelocity().add(knockback));
                     if (target instanceof ServerPlayerEntity sp) {
                        sp.velocityModified = true;
                     }
                  }
               }
            }
         }

         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            int footprintRadiusX = 4;
            int footprintRadiusZ = 5;

            for (int dxx = -footprintRadiusX; dxx <= footprintRadiusX; dxx++) {
               for (int dzx = -footprintRadiusZ; dzx <= footprintRadiusZ; dzx++) {
                  double ellipseVal = (double)(dxx * dxx) / (footprintRadiusX * footprintRadiusX) + (double)(dzx * dzx) / (footprintRadiusZ * footprintRadiusZ);
                  if (!(ellipseVal > 1.0)) {
                     double worldDx = dxx * Math.cos(-yawRad) - dzx * Math.sin(-yawRad);
                     double worldDz = dxx * Math.sin(-yawRad) + dzx * Math.cos(-yawRad);
                     double blockX = footX + worldDx;
                     double blockZ = footZ + worldDz;

                     for (int dy = 5; dy >= -5; dy--) {
                        BlockPos pos = new BlockPos((int)blockX, (int)(this.getY() + dy), (int)blockZ);
                        BlockState state = this.getWorld().getBlockState(pos);
                        if (state.isOf(Blocks.GRASS_BLOCK)) {
                           float rand = this.random.nextFloat();
                           if (rand < 0.4F) {
                              this.getWorld().setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState(), 3);
                           } else if (rand < 0.7F) {
                              this.getWorld().setBlockState(pos, Blocks.ROOTED_DIRT.getDefaultState(), 3);
                           }
                           break;
                        }

                        if (state.isSolid()) {
                           break;
                        }
                     }
                  }
               }
            }
         }

         for (int i = 0; i < 40; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * 8.0;
            double offsetZ = (this.random.nextDouble() - 0.5) * 8.0;
            BlockPos particlePos = new BlockPos((int)(footX + offsetX), (int)(this.getY() - 1.0), (int)(footZ + offsetZ));
            BlockState statex = this.getWorld().getBlockState(particlePos);
            if (!statex.isAir()) {
               serverLevel.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, statex), footX + offsetX, this.getY() + 0.1, footZ + offsetZ, 5, 0.2, 0.1, 0.2, 0.1
               );
            }
         }

         this.getWorld().playSound(null, footX, this.getY(), footZ, ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 15.0F, 0.6F);
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
      controllers.add(new AnimationController(this, "shift_controller", 0, this::shiftPredicate));
      AnimationController<ColossalTitanEntity> movementController = new AnimationController(this, "movement", 15, this::movementPredicate);
      if (MOVEMENT_KEYFRAME_INSTALLER != null) {
         MOVEMENT_KEYFRAME_INSTALLER.accept(movementController, this);
      }

      controllers.add(movementController);
      controllers.add(new AnimationController(this, "action", 15, this::actionPredicate));
   }

   private PlayState shiftPredicate(AnimationState<ColossalTitanEntity> state) {
      if (this.isTransforming()) {
         if (!this.hasPlayedShiftAnim) {
            state.getController().forceAnimationReset();
            this.hasPlayedShiftAnim = true;
         }

         state.getController().setAnimation(SHIFT_ANIM);
         return PlayState.CONTINUE;
      } else {
         this.hasPlayedShiftAnim = false;
         return PlayState.STOP;
      }
   }

   private PlayState movementPredicate(AnimationState<ColossalTitanEntity> state) {
      if (this.isTransforming()) {
         return PlayState.STOP;
      } else {
         AnimationController<?> controller = state.getController();
         controller.setAnimationSpeed(this.isLowStamina() ? 0.5 : 1.0);
         if (this.isDefeated()) {
            return state.setAndContinue(DEATH_ANIM);
         } else if (this.isVentingAny()) {
            return state.setAndContinue(STEAM_ANIM);
         } else if (this.isKicking()) {
            return state.setAndContinue(KICK_ANIM);
         } else if (this.isArmDragging()) {
            return state.setAndContinue(ARM_DRAG_ANIM);
         } else {
            return this.isDismounting() ? state.setAndContinue(DISMOUNT_ANIM) : state.setAndContinue(this.isMoving() ? WALK_ANIM : IDLE_ANIM);
         }
      }
   }

   private PlayState actionPredicate(AnimationState<ColossalTitanEntity> state) {
      if (this.isTransforming()) {
         return PlayState.STOP;
      } else {
         AnimationController<?> controller = state.getController();
         controller.setAnimationSpeed(this.isLowStamina() ? 0.5 : 1.0);
         if (this.isDefeated()) {
            return state.setAndContinue(DEATH_ANIM);
         } else if (this.isVentingAny()) {
            return state.setAndContinue(STEAM_ANIM);
         } else if (this.isKicking()) {
            return state.setAndContinue(KICK_ANIM);
         } else if (this.isArmDragging()) {
            return state.setAndContinue(ARM_DRAG_ANIM);
         } else if (this.isDismounting()) {
            return state.setAndContinue(DISMOUNT_ANIM);
         } else if (this.isTitanAttacking()) {
            return switch (this.getAttackNumber()) {
               case 1 -> state.setAndContinue(ATTACK1_ANIM);
               case 2 -> state.setAndContinue(ATTACK2_ANIM);
               case 3 -> state.setAndContinue(SMASH_ANIM);
               default -> state.setAndContinue(ATTACK1_ANIM);
            };
         } else {
            return state.setAndContinue(this.isMoving() ? WALK_UPPER_ANIM : IDLE_UPPER_ANIM);
         }
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
