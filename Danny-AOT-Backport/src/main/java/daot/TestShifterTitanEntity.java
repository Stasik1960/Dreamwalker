package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.ModNetworking;
import daot.network.TitanImpactShakePayload;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.boss.BossBar.Style;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;

public class TestShifterTitanEntity extends AttackTitanEntity implements daot.compat.BaseDimensionsProvider {
   private static final EntityDimensions TEST_DIMENSIONS = EntityDimensions.changing(1.2F, 5.5F);
   private static final RawAnimation TEST_IDLE = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation TEST_WALK = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation TEST_RUN = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation TEST_JUMP = RawAnimation.begin().thenPlayAndHold("jump");
   private static final RawAnimation TEST_FALL = RawAnimation.begin().thenLoop("fall");
   private static final RawAnimation TEST_HANG = RawAnimation.begin().thenLoop("hang");
   private static final RawAnimation TEST_LEFT_HANG = RawAnimation.begin().thenLoop("left_hang");
   private static final RawAnimation TEST_RIGHT_HANG = RawAnimation.begin().thenLoop("right_hang");
   private static final RawAnimation TEST_DISMOUNT = RawAnimation.begin().thenLoop("dismount");
   private static final RawAnimation TEST_HANG_JUMP = RawAnimation.begin().thenPlayAndHold("hang_jump");
   private static final RawAnimation TEST_ATTACK1 = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation TEST_ATTACK2 = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation TEST_ATTACK3 = RawAnimation.begin().thenPlayAndHold("attack3");
   private static final RawAnimation TEST_SHIFT = RawAnimation.begin().thenPlayAndHold("Transform");
   private static final RawAnimation TEST_DEATH = RawAnimation.begin().thenPlayAndHold("Death");
   private static final RawAnimation TEST_IDLE_UPPER = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation TEST_WALK_UPPER = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation TEST_RUN_UPPER = RawAnimation.begin().thenLoop("run_upper");
   private static final TrackedData<Boolean> DATA_LATCHED = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_LATCH_NORMAL_X = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_LATCH_NORMAL_Z = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> DATA_HANG_JUMPING = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_POUNCE_COOLDOWN = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_KICKBACK_COOLDOWN = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_BARRAGE_COOLDOWN = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_CHOMP_COOLDOWN = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_JAW_CHOMPING = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_NAPE_HARDENED = DataTracker.registerData(TestShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private double anchorX;
   private double anchorY;
   private double anchorZ;
   private boolean latchRequested = false;
   private int hangJumpRelatchCooldown = 0;
   private static final int HANG_JUMP_MAX_TICKS = 30;
   private int hangJumpTicks = 0;
   private boolean hangJumpBoostActive = false;
   private static final double TEST_WALK_SPEED = 0.25875;
   private static final double TEST_RUN_SPEED = 1.46625;
   private double testCurrentSpeed = 0.25875;
   private static final double TEST_SPEED_LERP_STEP = 0.20125;
   private static final double LATCH_SCAN_DISTANCE = 1.2;
   public static final int POUNCE_1 = 11;
   public static final int POUNCE_2 = 12;
   private static final RawAnimation TEST_POUNCE1 = RawAnimation.begin().thenPlayAndHold("pounce1");
   private static final RawAnimation TEST_POUNCE2 = RawAnimation.begin().thenPlayAndHold("pounce2");
   private static final int POUNCE_TICKS = 25;
   private static final int POUNCE_IMPACT_TICK = Math.round(22.5F);
   private static final int POUNCE_MIN_AIR_TICKS = 4;
   private static final int POUNCE_COOLDOWN_TICKS = 40;
   private static final float POUNCE_DAMAGE_MULT = 6.0F;
   private static final double POUNCE_RADIUS = 7.0;
   private static final float POUNCE_LEAP_BOOST = 2.6F;
   private static final float POUNCE_LEAP_VERTICAL = 1.3F;
   private static final double POUNCE_MAX_HORIZONTAL_SPEED = 10.0;
   private static final double POUNCE_MAX_VERTICAL_SPEED = 6.0;
   private static final int POUNCE_MIN_FLIGHT_TICKS = 5;
   private static final int POUNCE_MAX_FLIGHT_TICKS = 40;
   private static final double JUMP_HORIZONTAL_DRAG = 0.91;
   private static final double JUMP_VERTICAL_DRAG = 0.98;
   private static final double POUNCE_ARRIVAL_SLACK = 2.5;
   private double pounceSolvedHorizontal = 0.0;
   private double pounceSolvedVertical = 0.0;
   private int pounceClientBoostTicks = 0;
   private int pounceFlightTargetId = -1;
   private static final int POUNCE_CRATER_RADIUS = 4;
   private static final int POUNCE_CRATER_DEPTH = 2;
   private static final double POUNCE_CRATER_FORWARD = 3.0;
   private int pounceAnimTicks = 0;
   private boolean pounceImpactDone = false;
   private boolean pounceBoostActive = false;
   private boolean pounceWasAirborne = false;
   private boolean pounceUseSecondVariant = false;
   public static final int KICKBACK = 13;
   private static final RawAnimation TEST_KICKBACK = RawAnimation.begin().thenPlayAndHold("kickback");
   private static final int KICKBACK_TICKS = 14;
   private static final int KICKBACK_IMPACT_TICK = 10;
   private static final int KICKBACK_COOLDOWN_TICKS = 40;
   private static final float KICKBACK_DAMAGE_MULT = 4.0F;
   private static final double KICKBACK_RADIUS = 6.0;
   private static final int KICKBACK_CRATER_RADIUS = 3;
   private static final int KICKBACK_CRATER_DEPTH = 2;
   private static final double KICKBACK_CRATER_OFFSET = -3.0;
   private static final double KICKBACK_KNOCK_H = 2.2;
   private static final double KICKBACK_KNOCK_V = 0.8;
   public static final int BARRAGE = 14;
   private static final RawAnimation TEST_BARRAGE = RawAnimation.begin().thenLoop("barrage");
   private static final int BARRAGE_TICKS = 60;
   private static final int BARRAGE_CYCLE_TICKS = 10;
   private static final int BARRAGE_IMPACT_A = 3;
   private static final int BARRAGE_IMPACT_B = 6;
   private static final int BARRAGE_BORE_RADIUS = 3;
   private static final int BARRAGE_BORE_HEIGHT = 7;
   private static final double BARRAGE_BORE_FORWARD = 2.5;
   private static final int BARRAGE_COOLDOWN_TICKS = 140;
   private static final float BARRAGE_DIG_DOWN_PITCH = 15.0F;
   private static final float BARRAGE_SHAKE_BASE = 0.35F;
   private static final float BARRAGE_SHAKE_TEARING = 0.6F;
   private static final int BARRAGE_CRASH_INTERVAL = 8;
   private int barrageTicksLeft = 0;
   private int barrageCycleTick = 0;
   private int barrageCrashCooldown = 0;
   public static final int CHOMP = 15;
   private static final RawAnimation TEST_CHOMP = RawAnimation.begin().thenPlayAndHold("chomp");
   private static final int CHOMP_TICKS = 18;
   private static final int CHOMP_IMPACT_TICK = 9;
   private static final int CHOMP_COOLDOWN_TICKS = 30;
   private static final double CHOMP_REACH = 6.0;
   private static final double CHOMP_HALF_WIDTH = 3.0;
   private static final float CHOMP_DAMAGE = 30.0F;
   private int chompAnimTicks = 0;
   private boolean chompBitten = false;
   private int kickbackAnimTicks = 0;
   private boolean kickbackImpactDone = false;
   private static final int POUNCE_TARGET_VALID_TICKS = 10;
   private static final double POUNCE_TARGET_MAX_DIST = 80.0;
   private int pendingPounceTargetId = -1;
   private int pendingPounceTargetTick = 0;
   private static final int TEST_ATTACK3_TICKS = 20;
   private final Set<UUID> attackHitTargets = new HashSet<>();
   private int prevAttackNum = 0;
   private boolean prevIsAttacking = false;
   private static final double RUN_ANIM_SPEED_MULT = 1.15;
   private static final double WALK_ANIM_SPEED_MULT = 1.15;

   public TestShifterTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public static Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.46625)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 5.0)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.65)
         .add(daot.compat.attributes.DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_LATCHED, false);
      this.dataTracker.startTracking(DATA_LATCH_NORMAL_X, 0.0F);
      this.dataTracker.startTracking(DATA_LATCH_NORMAL_Z, 0.0F);
      this.dataTracker.startTracking(DATA_HANG_JUMPING, false);
      this.dataTracker.startTracking(DATA_POUNCE_COOLDOWN, 0);
      this.dataTracker.startTracking(DATA_KICKBACK_COOLDOWN, 0);
      this.dataTracker.startTracking(DATA_BARRAGE_COOLDOWN, 0);
      this.dataTracker.startTracking(DATA_CHOMP_COOLDOWN, 0);
      this.dataTracker.startTracking(DATA_JAW_CHOMPING, false);
      this.dataTracker.startTracking(DATA_NAPE_HARDENED, false);
   }

   @Override
   public EntityDimensions getBaseDimensions(EntityPose pose) {
      return TEST_DIMENSIONS;
   }

   @Override
   public void setCrouching(boolean crouching) {
      super.setCrouching(false);
   }

   public static boolean isBarrageAttack(int attackNumber) {
      return attackNumber == 14;
   }

   public boolean isBarraging() {
      return this.isTitanAttacking() && isBarrageAttack(this.getAttackNumber());
   }

   public boolean isJawChomping() {
      return this.dataTracker.get(DATA_JAW_CHOMPING);
   }

   public static boolean isChompAttack(int attackNumber) {
      return attackNumber == 15;
   }

   public int getChompCooldownTicks() {
      return this.dataTracker.get(DATA_CHOMP_COOLDOWN);
   }

   public static int chompCooldownTotal() {
      return 30;
   }

   public static boolean isPounceAttack(int attackNumber) {
      return attackNumber == 11 || attackNumber == 12;
   }

   public static boolean isKickbackAttack(int attackNumber) {
      return attackNumber == 13;
   }

   private static boolean isJawAbilityAttack(int attackNumber) {
      return isPounceAttack(attackNumber) || isKickbackAttack(attackNumber);
   }

   public int getKickbackCooldownTicks() {
      return this.dataTracker.get(DATA_KICKBACK_COOLDOWN);
   }

   public static int kickbackCooldownTotal() {
      return 40;
   }

   public int getPounceCooldownTicks() {
      return this.dataTracker.get(DATA_POUNCE_COOLDOWN);
   }

   public static int pounceCooldownTotal() {
      return 40;
   }

   @Override
   public void triggerAbility(int abilityNumber) {
      if (!this.getWorld().isClient()) {
         switch (abilityNumber) {
            case 1:
               this.startPounce();
               break;
            case 2:
               this.startKickback();
               break;
            case 3:
               this.toggleBarrage();
               break;
            case 4:
               this.toggleNapeHardening();
               break;
            case 5:
               this.chompOrRelease();
         }
      }
   }

   private void toggleBarrage() {
      if (!this.isTransforming() && !this.isDismounting() && !this.isDefeated()) {
         if (this.isBarraging()) {
            this.endBarrage();
         } else if (!this.isTitanAttacking()) {
            if (this.getBarrageCooldownTicks() <= 0) {
               this.setWasMovingOnAttackStart(this.isMoving());
               this.setAttackNumber(14);
               this.setTitanAttacking(true);
               this.barrageTicksLeft = 60;
               this.barrageCycleTick = 0;
               this.attackHitTargets.clear();
            }
         }
      }
   }

   private boolean barrageBore() {
      if (!(this.getWorld() instanceof ServerWorld serverLevel)) {
         return false;
      } else if (!DannysAot.isTitanGriefingEnabled(this.getWorld())) {
         return false;
      } else {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double centerX = this.getX() + forwardX * 2.5;
         double centerY = this.getY();
         double centerZ = this.getZ() + forwardZ * 2.5;
         boolean playedBreakSound = false;
         boolean brokeAnything = false;
         int r = 3;
         int baseY = (int)Math.floor(centerY);
         LivingEntity rider = this.getControllingPassenger();
         if (rider == null && this.getFirstPassenger() instanceof LivingEntity passenger) {
            rider = passenger;
         }

         boolean diggingDown = rider == null || rider.getPitch() >= 15.0F;
         int lowestOffset = diggingDown ? -1 : 0;

         for (int dx = -r; dx <= r; dx++) {
            for (int dy = lowestOffset; dy <= 7; dy++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (dx * dx + dz * dz <= r * r) {
                     BlockPos pos = new BlockPos((int)Math.floor(centerX) + dx, baseY + dy, (int)Math.floor(centerZ) + dz);
                     BlockState state = serverLevel.getBlockState(pos);
                     if (!state.isAir()
                        && !(state.getHardness(serverLevel, pos) < 0.0F)
                        && !state.isIn(BlockTags.WITHER_IMMUNE)
                        && (!(state.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                        if (!playedBreakSound) {
                           serverLevel.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 2.0F, 0.5F);
                           playedBreakSound = true;
                        }

                        brokeAnything = true;
                        if (this.random.nextFloat() < 0.2F) {
                           this.flingCraterDebris(serverLevel, pos, state, forwardX, forwardZ);
                        } else {
                           serverLevel.removeBlock(pos, false);
                        }
                     }
                  }
               }
            }
         }

         if (brokeAnything && this.barrageCrashCooldown <= 0) {
            this.playImpactCrashSound();
            this.barrageCrashCooldown = 8;
         }

         return brokeAnything;
      }
   }

   private void endBarrage() {
      this.barrageTicksLeft = 0;
      this.barrageCycleTick = 0;
      this.setTitanAttacking(false);
      this.setAttackNumber(0);
      this.attackHitTargets.clear();
      this.dataTracker.set(DATA_BARRAGE_COOLDOWN, 140);
   }

   public int getBarrageCooldownTicks() {
      return this.dataTracker.get(DATA_BARRAGE_COOLDOWN);
   }

   public static int barrageCooldownTotal() {
      return 140;
   }

   private void chompOrRelease() {
      if (!this.isTransforming() && !this.isDismounting() && !this.isDefeated()) {
         if (this.isGrabbing()) {
            this.releaseGrabbedEntity();
         } else if (!this.isJawChomping() && !this.isTitanAttacking()) {
            if (this.getChompCooldownTicks() <= 0) {
               this.dataTracker.set(DATA_JAW_CHOMPING, true);
               this.chompAnimTicks = 18;
               this.chompBitten = false;
               this.dataTracker.set(DATA_CHOMP_COOLDOWN, 30);
            }
         }
      }
   }

   private void chompBite() {
      if (!this.isGrabbing()) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double fwdX = -Math.sin(yawRad);
         double fwdZ = Math.cos(yawRad);
         LivingEntity controller = this.getControllingPassenger();
         Entity best = null;
         double bestForward = Double.MAX_VALUE;

         for (Entity e : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(6.0, 3.0, 6.0))) {
            if (e != controller) {
               boolean grabbable = FemaleTitanEntity.canBeGrabbedByFemale(e) || this.isGrabbableCrystal(e);
               boolean titan = this.isChompTitanTarget(e);
               if (grabbable || titan) {
                  double dx = e.getX() - this.getX();
                  double dz = e.getZ() - this.getZ();
                  double forwardDist = dx * fwdX + dz * fwdZ;
                  if (!(forwardDist < 0.0) && !(forwardDist > 6.0)) {
                     double lateral = Math.abs(dx * fwdZ - dz * fwdX);
                     if (!(lateral > 3.0) && forwardDist < bestForward) {
                        best = e;
                        bestForward = forwardDist;
                     }
                  }
               }
            }
         }

         if (best != null) {
            if (this.isChompTitanTarget(best)) {
               float dmg = best instanceof ShifterTitan ? 15.0F : 30.0F;
               best.damage(this.getDamageSources().mobAttack(this), dmg);
            } else {
               this.grabEntity(best);
            }
         }
      }
   }

   private boolean isGrabbableCrystal(Entity e) {
      return e instanceof FemaleCrystalShellEntity || e instanceof CrystalShellWarhammerEntity;
   }

   private boolean isChompTitanTarget(Entity e) {
      return e != this && e instanceof LivingEntity && e.isAlive() ? e instanceof GrabbingTitan || e instanceof ShifterTitan : false;
   }

   public boolean isNapeHardened() {
      return this.dataTracker.get(DATA_NAPE_HARDENED);
   }

   private void toggleNapeHardening() {
      if (!this.isTransforming() && !this.isDismounting() && !this.isDefeated()) {
         if (this.getFirstPassenger() instanceof ServerPlayerEntity rider) {
            boolean turningOn = !this.isNapeHardened();
            if (turningOn && !rider.getCommandTags().contains("has_hardening")) {
               rider.sendMessage(Text.literal("This ability requires consumption of the Armor Potion to use.").formatted(Formatting.RED), false);
            } else {
               this.dataTracker.set(DATA_NAPE_HARDENED, turningOn);
               this.getWorld()
                  .playSound(null, this.getX(), this.getY() + 3.0, this.getZ(), SoundEvents.ENTITY_TURTLE_EGG_BREAK, SoundCategory.HOSTILE, 4.0F, 0.5F);
               ModNetworking.drainStamina(rider.getUuid(), 5.0F);
            }
         }
      }
   }

   private void startPounce() {
      if (this.canStartPounce()) {
         this.pounceUseSecondVariant = !this.pounceUseSecondVariant;
         int variant = this.pounceUseSecondVariant ? 12 : 11;
         this.setWasMovingOnAttackStart(this.isMoving());
         this.setAttackNumber(variant);
         this.setTitanAttacking(true);
         this.pounceAnimTicks = 25;
         this.pounceImpactDone = false;
         this.pounceWasAirborne = false;
         this.dataTracker.set(DATA_POUNCE_COOLDOWN, pounceCooldownTotal());
         Vec3d aim = this.consumePounceAimDirection();
         this.pounceBoostActive = true;
         this.forceStartJumpArc(aim.x, aim.z);
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.5F, 1.5F);
      }
   }

   private boolean pounceConnectedMidair() {
      if (25 - this.pounceAnimTicks < 4) {
         return false;
      } else if (!this.pounceWasAirborne) {
         return false;
      } else if (this.horizontalCollision) {
         return true;
      } else if (this.isOnGround()) {
         return true;
      } else {
         for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(1.5))) {
            if (target != this && !this.isOwnPounceEntity(target)) {
               return true;
            }
         }

         return false;
      }
   }

   private void startKickback() {
      if (!this.isTransforming() && !this.isDismounting() && !this.isDefeated()) {
         if (!this.isTitanAttacking() && this.getKickbackCooldownTicks() <= 0 && !this.isLatched()) {
            this.setWasMovingOnAttackStart(this.isMoving());
            this.setAttackNumber(13);
            this.setTitanAttacking(true);
            this.kickbackAnimTicks = 14;
            this.kickbackImpactDone = false;
            this.dataTracker.set(DATA_KICKBACK_COOLDOWN, kickbackCooldownTotal());
         }
      }
   }

   private void performKickbackImpact() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double rearX = Math.sin(yawRad);
      double rearZ = -Math.cos(yawRad);
      float damage = (float)ModConfig.get().attackTitanKickDamage * 4.0F;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(6.0))) {
         if (target != this && !this.isOwnPounceEntity(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean close = hDist < this.getWidth();
            double dot = hDist > 0.5 ? new Vec3d(toTarget.x, 0.0, toTarget.z).normalize().dotProduct(new Vec3d(rearX, 0.0, rearZ)) : 1.0;
            if (close || !(dot <= 0.2)) {
               target.damage(this.getDamageSources().mobAttack(this), damage);
               this.applyKickbackKnockback(target, rearX, rearZ);
            }
         }
      }

      this.carveCrater(-3.0, 3, 2);
      this.playImpactCrashSound();
      this.broadcastImpactShake(1.1F);
      this.drainAttackStamina();
   }

   private void applyKickbackKnockback(LivingEntity target, double rearX, double rearZ) {
      Vec3d toTarget = target.getPos().subtract(this.getPos());
      Vec3d dir = new Vec3d(toTarget.x, 0.0, toTarget.z);
      dir = dir.lengthSquared() < 0.01 ? new Vec3d(rearX, 0.0, rearZ) : dir.normalize();
      Vec3d knock = dir.multiply(2.2).add(0.0, 0.8, 0.0);
      if (target instanceof AttackTitanEntity shifter) {
         shifter.setPendingKnockback(knock);
         shifter.applyHitSlow(20);
      } else {
         target.setVelocity(knock);
         target.velocityModified = true;
         if (target instanceof PlayerEntity player) {
            player.velocityDirty = true;
         } else if (target instanceof GrabbingTitan pure) {
            pure.triggerEyeHurt();
            if (target instanceof MobEntity mob) {
               mob.getNavigation().stop();
            }
         }
      }
   }

   public void setPendingPounceTarget(int entityId) {
      this.pendingPounceTargetId = entityId;
      this.pendingPounceTargetTick = this.age;
   }

   private Vec3d consumePounceAimDirection() {
      int id = this.pendingPounceTargetId;
      int stamped = this.pendingPounceTargetTick;
      this.pendingPounceTargetId = -1;
      this.pounceSolvedHorizontal = 0.0;
      this.pounceSolvedVertical = 0.0;
      this.pounceFlightTargetId = -1;
      float yawRad = (float)Math.toRadians(this.getYaw());
      Vec3d facing = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      if (id != -1 && this.age - stamped <= 10) {
         Entity target = this.getWorld().getEntityById(id);
         if (target == null || !target.isAlive() || target == this) {
            return facing;
         } else if (target.getVehicle() == this) {
            return facing;
         } else if (target.distanceTo(this) > 80.0) {
            return facing;
         } else {
            Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0).subtract(this.getPos().add(0.0, this.getHeight() * 0.5, 0.0));
            Vec3d flat = new Vec3d(to.x, 0.0, to.z);
            if (flat.lengthSquared() < 0.01) {
               return facing;
            } else {
               flat = flat.normalize();
               float aimYaw = (float)(Math.atan2(flat.z, flat.x) * (180.0 / Math.PI)) - 90.0F;
               this.setYaw(aimYaw);
               this.bodyYaw = aimYaw;
               this.setHeadYaw(aimYaw);
               this.solvePounceArc(to.horizontalLength(), to.y);
               this.pounceFlightTargetId = id;
               return flat;
            }
         }
      } else {
         return facing;
      }
   }

   private void solvePounceArc(double horizontalDist, double verticalDelta) {
      double gravity = this.getAttributeValue(daot.compat.attributes.DaotEntityAttributes.GRAVITY);
      if (!(gravity <= 1.0E-4) && !(horizontalDist < 0.5)) {
         int airtimeBudget = (int)Math.min(40L, Math.max(8L, Math.round(8.0 + horizontalDist * 0.35)));

         for (double vy = 6.0; vy >= 0.8; vy -= 0.1) {
            int flightTicks = this.simulateArcTicks(vy, gravity, verticalDelta);
            if (flightTicks >= 5 && flightTicks <= airtimeBudget) {
               double reachPerSpeed = horizontalDragReach(flightTicks);
               if (!(reachPerSpeed < 1.0E-4)) {
                  double vx = horizontalDist / reachPerSpeed;
                  if (vx <= 10.0) {
                     this.pounceSolvedHorizontal = vx;
                     this.pounceSolvedVertical = vy;
                     return;
                  }
               }
            }
         }

         this.pounceSolvedHorizontal = 10.0;
         this.pounceSolvedVertical = 6.0;
      }
   }

   private int simulateArcTicks(double vy, double gravity, double targetY) {
      double y = 0.0;
      double v = vy;

      for (int t = 1; t <= 40; t++) {
         v = (v - gravity) * 0.98;
         y += v;
         if (v < 0.0 && y <= targetY) {
            return t;
         }
      }

      return -1;
   }

   private static double horizontalDragReach(int ticks) {
      double sum = 0.0;
      double factor = 1.0;

      for (int t = 1; t <= ticks; t++) {
         factor *= 0.91;
         sum += factor;
      }

      return sum;
   }

   private boolean pounceReachedTarget() {
      if (this.pounceFlightTargetId == -1) {
         return false;
      } else {
         Entity target = this.getWorld().getEntityById(this.pounceFlightTargetId);
         if (target != null && target.isAlive()) {
            Box targetBox = target.getBoundingBox().expand(this.getWidth() * 0.5);
            Vec3d now = this.getPos();
            if (targetBox.contains(now)) {
               return true;
            } else {
               Vec3d previous = new Vec3d(this.prevX, this.prevY, this.prevZ);
               if (targetBox.raycast(previous, now).isPresent()) {
                  return true;
               } else {
                  double reach = 2.5 + this.getWidth() * 0.5 + target.getWidth() * 0.5;
                  return this.squaredDistanceTo(target) <= reach * reach;
               }
            }
         } else {
            this.pounceFlightTargetId = -1;
            return false;
         }
      }
   }

   private void oneTapPureTitanNapesInReach() {
      Set<Integer> killed = new HashSet<>();

      for (Entity nape : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(7.0), e -> e.getClass().getSimpleName().endsWith("NapeEntity"))) {
         LivingEntity owner = null;
         double bestDistSq = Double.MAX_VALUE;

         for (Entity body : this.getWorld().getOtherEntities(this, nape.getBoundingBox().expand(8.0), e -> e instanceof GrabbingTitan && e.isAlive())) {
            double distSq = body.squaredDistanceTo(nape);
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               owner = (LivingEntity)body;
            }
         }

         if (owner != null && killed.add(owner.getId())) {
            owner.damage(this.getDamageSources().mobAttack(this), Float.MAX_VALUE);
            this.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.SLASH1, SoundCategory.HOSTILE, 3.0F, 0.7F);
         }
      }
   }

   private boolean canStartPounce() {
      return !this.isTransforming() && !this.isDismounting() && !this.isDefeated()
         ? !this.isTitanAttacking() && this.getPounceCooldownTicks() <= 0 && !this.isLatched()
         : false;
   }

   public void startPounceLeapClient(double dirX, double dirZ, int lockedTargetId) {
      if (this.getWorld().isClient() && this.canStartPounce()) {
         this.pounceSolvedHorizontal = 0.0;
         this.pounceSolvedVertical = 0.0;
         if (lockedTargetId != -1) {
            Entity target = this.getWorld().getEntityById(lockedTargetId);
            if (target != null && target.isAlive() && target.distanceTo(this) <= 80.0) {
               Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0).subtract(this.getPos().add(0.0, this.getHeight() * 0.5, 0.0));
               this.solvePounceArc(to.horizontalLength(), to.y);
            }
         }

         if (dirX * dirX + dirZ * dirZ > 1.0E-4) {
            float aimYaw = (float)(Math.atan2(dirZ, dirX) * (180.0 / Math.PI)) - 90.0F;
            this.setYaw(aimYaw);
            this.bodyYaw = aimYaw;
            this.setHeadYaw(aimYaw);
         }

         this.pounceBoostActive = true;
         this.pounceClientBoostTicks = 35;
         this.forceStartJumpArc(dirX, dirZ);
      }
   }

   private void performPounceImpact() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      float damage = (float)ModConfig.get().attackTitanKickDamage * 6.0F;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(7.0))) {
         if (target != this && !this.isOwnPounceEntity(target)) {
            target.damage(this.getDamageSources().mobAttack(this), damage);
            Vec3d away = target.getPos().subtract(this.getPos());
            double len = away.horizontalLength();
            double kx = len > 0.01 ? away.x / len : forwardX;
            double kz = len > 0.01 ? away.z / len : forwardZ;
            target.setVelocity(target.getVelocity().add(kx * 1.1, 0.55, kz * 1.1));
            target.velocityModified = true;
         }
      }

      this.oneTapPureTitanNapesInReach();
      this.carveCrater(3.0, 4, 2);
      this.playImpactCrashSound();
      this.broadcastImpactShake(1.3F);
      this.drainAttackStamina();
   }

   private void broadcastImpactShake(float intensity) {
      if (this.getWorld() instanceof ServerWorld) {
         TitanImpactShakePayload shake = new TitanImpactShakePayload(this.getX(), this.getY(), this.getZ(), intensity);
         Collection<ServerPlayerEntity> tracking = PlayerLookup.tracking(this);

         for (ServerPlayerEntity nearby : tracking) {
            ServerPlayNetworking.send(nearby, shake);
         }

         if (this.getControllingPassenger() instanceof ServerPlayerEntity rider && !tracking.contains(rider)) {
            ServerPlayNetworking.send(rider, shake);
         }
      }
   }

   private void carveCrater(double offsetAlongFacing, int radius, int depth) {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            float yawRad = (float)Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double centerX = this.getX() + forwardX * offsetAlongFacing;
            double centerZ = this.getZ() + forwardZ * offsetAlongFacing;
            int groundY = (int)Math.floor(this.getY());
            boolean playedBreakSound = false;

            for (int dx = -radius; dx <= radius; dx++) {
               for (int dz = -radius; dz <= radius; dz++) {
                  if (dx * dx + dz * dz <= radius * radius) {
                     for (int dy = 0; dy >= -depth; dy--) {
                        BlockPos pos = new BlockPos((int)Math.floor(centerX) + dx, groundY + dy, (int)Math.floor(centerZ) + dz);
                        BlockState state = serverLevel.getBlockState(pos);
                        if (!state.isAir()
                           && !(state.getHardness(serverLevel, pos) < 0.0F)
                           && !state.isIn(BlockTags.WITHER_IMMUNE)
                           && (!(state.getBlock() instanceof FluidBlock) || !(this.random.nextFloat() > 0.05F))) {
                           if (!playedBreakSound) {
                              serverLevel.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 2.0F, 0.5F);
                              playedBreakSound = true;
                           }

                           if (this.random.nextFloat() < 0.25F) {
                              this.flingCraterDebris(serverLevel, pos, state, forwardX, forwardZ);
                           } else {
                              serverLevel.removeBlock(pos, false);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void playImpactCrashSound() {
      SoundEvent[] sounds = new SoundEvent[]{ModSounds.IMPACT_1, ModSounds.IMPACT_2, ModSounds.IMPACT_3, ModSounds.IMPACT_4, ModSounds.IMPACT_5};
      SoundEvent sound = sounds[this.random.nextInt(sounds.length)];
      float pitch = 0.85F + this.random.nextFloat() * 0.2F;
      this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundCategory.HOSTILE, 3.0F, pitch);
   }

   private void flingCraterDebris(ServerWorld serverLevel, BlockPos pos, BlockState state, double forwardX, double forwardZ) {
      serverLevel.removeBlock(pos, false);
      double vx = forwardX * (0.4 + this.random.nextDouble() * 0.6) + (this.random.nextDouble() - 0.5) * 0.5;
      double vy = 0.45 + this.random.nextDouble() * 0.7;
      double vz = forwardZ * (0.4 + this.random.nextDouble() * 0.6) + (this.random.nextDouble() - 0.5) * 0.5;
      FallingBlockEntity debris = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)debris).setBlockState(state);
      debris.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      debris.setFallingBlockPos(pos);
      debris.setVelocity(vx, vy, vz);
      debris.timeFalling = 1;
      debris.dropItem = false;
      serverLevel.spawnEntity(debris);
   }

   private boolean isOwnPounceEntity(Entity entity) {
      UUID shifterUUID = this.getShifterUUID();
      if (shifterUUID != null && entity.getUuid().equals(shifterUUID)) {
         return true;
      } else if (entity.getVehicle() == this) {
         return true;
      } else {
         return entity instanceof AttackTitanNapeEntity nape && nape.getParentTitan() == this
            ? true
            : entity instanceof AttackTitanEyeEntity eye && eye.getParentTitan() == this;
      }
   }

   @Override
   protected float getAttackDamageMultiplier() {
      return 2.0F;
   }

   @Override
   protected double getAttackReachScale() {
      return 0.36666666666666664;
   }

   @Override
   protected int getAttackEffectTick(int attackNum) {
      return attackNum == 1 ? 7 : 6;
   }

   @Override
   protected int comboAttackCount() {
      return 3;
   }

   @Override
   protected boolean isAbilityAttackNumber(int attackNumber) {
      return isJawAbilityAttack(attackNumber);
   }

   @Override
   protected int attackAnimTicks(int attackNumber) {
      if (isPounceAttack(attackNumber)) {
         return 25;
      } else if (isKickbackAttack(attackNumber)) {
         return 14;
      } else {
         return attackNumber == 3 ? 20 : super.attackAnimTicks(attackNumber);
      }
   }

   @Override
   protected boolean usesKneelDismount() {
      return false;
   }

   @Override
   public boolean shouldJamHookedOdm() {
      return this.isTitanAttacking();
   }

   @Override
   protected boolean isJumpBlockedByAttack() {
      return false;
   }

   @Override
   protected void dealAttackDamage() {
      int current = this.getAttackNumber();
      if (!isJawAbilityAttack(current) && !isBarrageAttack(current)) {
         this.playAttackImpactSound();
         this.drainAttackStamina();
      }
   }

   @Override
   protected float getJumpVerticalPower() {
      if (this.pounceBoostActive && this.pounceSolvedVertical > 0.0) {
         return (float)this.pounceSolvedVertical;
      } else if (this.pounceBoostActive) {
         return super.getJumpVerticalPower() * 1.3F;
      } else {
         return this.hangJumpBoostActive ? super.getJumpVerticalPower() * 1.5F : super.getJumpVerticalPower();
      }
   }

   @Override
   protected float getJumpHorizontalPower() {
      if (this.pounceBoostActive && this.pounceSolvedHorizontal > 0.0) {
         return (float)this.pounceSolvedHorizontal;
      } else if (this.pounceBoostActive) {
         return super.getJumpHorizontalPower() * 2.6F;
      } else {
         return this.hangJumpBoostActive ? super.getJumpHorizontalPower() * 1.5F : super.getJumpHorizontalPower();
      }
   }

   @Override
   public void triggerJump(PlayerEntity player) {
      if (this.isLatched()) {
         this.doHangJump(player);
      } else {
         super.triggerJump(player);
      }
   }

   public void snapYawToRider(float yaw) {
      this.setYaw(yaw);
      this.bodyYaw = yaw;
      this.setHeadYaw(yaw);
   }

   @Override
   public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
      if (!this.getWorld().isClient() && fallDistance > 3.0F) {
         this.setFalling(false);
         this.applyJumpLandingCooldown();
         this.setLandingIntensity(0.0F);
      }

      return false;
   }

   private void doHangJump(PlayerEntity player) {
      this.unlatch();
      this.hangJumpRelatchCooldown = 10;
      float yawRad = (float)Math.toRadians(player.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      this.hangJumpBoostActive = true;
      this.forceStartJumpArc(forwardX, forwardZ);
      this.dataTracker.set(DATA_HANG_JUMPING, true);
      this.hangJumpTicks = 30;
      this.fallDistance = 0.0F;
   }

   public boolean isHangJumping() {
      return this.dataTracker.get(DATA_HANG_JUMPING);
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardOffset = 0.4;
         double offsetX = -Math.sin(yawRad) * forwardOffset;
         double offsetZ = Math.cos(yawRad) * forwardOffset;
         double riderY = this.isDismounting() ? 2.15 : 4.4;
         positionUpdater.accept(passenger, this.getX() + offsetX, this.getY() + riderY, this.getZ() + offsetZ);
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      double y = this.isDismounting() ? 2.15 : 4.4;
      return this.getPos().add(0.0, y, 0.0);
   }

   @Override
   public void onPlayerShift(PlayerEntity player) {
      super.onPlayerShift(player);
      if (this.bossBar != null) {
         this.bossBar.clearPlayers();
         this.bossBar = null;
      }

      if (!this.getWorld().isClient() && this.getWorld().getGameRules().getBoolean(DannysAot.RULE_SHIFT_BOSS_BARS)) {
         this.bossBar = new ServerBossBar(Text.literal("Jaw Titan"), Color.YELLOW, Style.PROGRESS);
         this.bossBar.setPercent(1.0F);
      }
   }

   @Override
   public void spawnHitboxes() {
      if (!this.getWorld().isClient()) {
         if (this.napeEntity == null) {
            AttackTitanNapeEntity nape = new AttackTitanNapeEntity(DannysAot.ATTACK_TITAN_NAPE, this.getWorld());
            nape.setParentTitan(this);
            nape.setPosition(this.getX(), this.getY(), this.getZ());
            this.getWorld().spawnEntity(nape);
            this.napeEntity = nape;
         }

         if (this.eyeEntity == null) {
            AttackTitanEyeEntity eye = new AttackTitanEyeEntity(DannysAot.ATTACK_TITAN_EYE, this.getWorld());
            eye.setParentTitan(this);
            eye.setPosition(this.getX(), this.getY(), this.getZ());
            this.getWorld().spawnEntity(eye);
            this.eyeEntity = eye;
         }

         if (this.grabHitboxEntity == null) {
            AttackTitanGrabEntity grab = new AttackTitanGrabEntity(DannysAot.ATTACK_TITAN_GRAB, this.getWorld());
            grab.setParentTitan(this);
            grab.setPosition(this.getX(), this.getY(), this.getZ());
            this.getWorld().spawnEntity(grab);
            this.grabHitboxEntity = grab;
         }
      }
   }

   @Override
   protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
      int atkNum = this.getAttackNumber();
      boolean isFullBodyAttack = this.isTitanAttacking() && this.isAbilityAttackNumber(atkNum);
      boolean canRun = this.isSprinting() && !isFullBodyAttack;
      double target = canRun ? 1.46625 : 0.25875;
      if (this.testCurrentSpeed < target) {
         this.testCurrentSpeed = Math.min(this.testCurrentSpeed + 0.20125, target);
      } else if (this.testCurrentSpeed > target) {
         this.testCurrentSpeed = Math.max(this.testCurrentSpeed - 0.20125, target);
      }

      return (float)this.testCurrentSpeed;
   }

   public boolean isLatched() {
      return this.dataTracker.get(DATA_LATCHED);
   }

   public float getLatchNormalX() {
      return this.dataTracker.get(DATA_LATCH_NORMAL_X);
   }

   public float getLatchNormalZ() {
      return this.dataTracker.get(DATA_LATCH_NORMAL_Z);
   }

   public void setLatchRequested(boolean requested) {
      this.latchRequested = requested;
      if (!requested) {
         this.unlatch();
      }
   }

   private void unlatch() {
      if (this.dataTracker.get(DATA_LATCHED)) {
         this.dataTracker.set(DATA_LATCHED, false);
         this.setNoGravity(false);
      }
   }

   private void tryLatch() {
      if (!this.isOnGround()) {
         World level = this.getWorld();
         Box bb = this.getBoundingBox();
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
         int bestIdx = -1;
         double bestScore = -2.0;

         for (int i = 0; i < 4; i++) {
            int dx = dirs[i][0];
            int dz = dirs[i][1];
            if (this.hasWallInDirection(level, bb, dx, dz)) {
               double alignment = dx * forwardX + dz * forwardZ;
               if (alignment > bestScore) {
                  bestScore = alignment;
                  bestIdx = i;
               }
            }
         }

         if (bestIdx >= 0) {
            int dx = dirs[bestIdx][0];
            int dz = dirs[bestIdx][1];
            this.dataTracker.set(DATA_LATCH_NORMAL_X, (float)(-dx));
            this.dataTracker.set(DATA_LATCH_NORMAL_Z, (float)(-dz));
            this.dataTracker.set(DATA_LATCHED, true);
            this.anchorX = this.getX();
            this.anchorY = this.getY();
            this.anchorZ = this.getZ();
            this.setVelocity(0.0, 0.0, 0.0);
            this.setFalling(false);
            this.fallDistance = 0.0F;
            this.setNoGravity(true);
         }
      }
   }

   private boolean hasWallInDirection(World level, Box bb, int dx, int dz) {
      double testMinX = bb.minX;
      double testMaxX = bb.maxX;
      double testMinZ = bb.minZ;
      double testMaxZ = bb.maxZ;
      if (dx > 0) {
         testMinX = bb.maxX;
         testMaxX = bb.maxX + 1.2;
      } else if (dx < 0) {
         testMinX = bb.minX - 1.2;
         testMaxX = bb.minX;
      }

      if (dz > 0) {
         testMinZ = bb.maxZ;
         testMaxZ = bb.maxZ + 1.2;
      } else if (dz < 0) {
         testMinZ = bb.minZ - 1.2;
         testMaxZ = bb.minZ;
      }

      double yBot = bb.minY + 2.0;
      double yTop = bb.minY + 4.5;

      for (int bx = (int)Math.floor(testMinX); bx < (int)Math.ceil(testMaxX); bx++) {
         for (int bz = (int)Math.floor(testMinZ); bz < (int)Math.ceil(testMaxZ); bz++) {
            for (int by = (int)Math.floor(yBot); by < (int)Math.ceil(yTop); by++) {
               BlockPos pos = new BlockPos(bx, by, bz);
               BlockState state = level.getBlockState(pos);
               if (state.isOpaqueFullCube(level, pos)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public void travel(Vec3d movementInput) {
      if (this.isLatched()) {
         LivingEntity controller = this.getControllingPassenger();
         if (controller != null) {
            this.setYaw(controller.getYaw());
            this.bodyYaw = controller.getYaw();
            this.setHeadYaw(controller.getHeadYaw());
         }

         this.setVelocity(0.0, 0.0, 0.0);
      } else {
         super.travel(movementInput);
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient()) {
         boolean currentlyAttacking = this.isTitanAttacking();
         int currentAttackNum = this.getAttackNumber();
         boolean newAttackStarted = currentlyAttacking && (!this.prevIsAttacking || currentAttackNum != this.prevAttackNum);
         if (newAttackStarted) {
            this.attackHitTargets.clear();
         }

         if (currentlyAttacking && (currentAttackNum == 1 || currentAttackNum == 2 || currentAttackNum == 3)) {
            this.applyAttackDamage(this.attackHitTargets);
         }

         if (!currentlyAttacking && this.prevIsAttacking) {
            this.attackHitTargets.clear();
         }

         this.prevIsAttacking = currentlyAttacking;
         this.prevAttackNum = currentAttackNum;
         int pounceCd = this.getPounceCooldownTicks();
         if (pounceCd > 0) {
            this.dataTracker.set(DATA_POUNCE_COOLDOWN, pounceCd - 1);
         }

         if (currentlyAttacking && isPounceAttack(currentAttackNum)) {
            this.pounceAnimTicks--;
            if (!this.isOnGround()) {
               this.pounceWasAirborne = true;
            }

            boolean reachedKeyframe = 25 - this.pounceAnimTicks >= POUNCE_IMPACT_TICK;
            if (!this.pounceImpactDone && (this.pounceReachedTarget() || reachedKeyframe || this.pounceConnectedMidair())) {
               this.pounceImpactDone = true;
               this.performPounceImpact();
            }

            if (this.pounceAnimTicks <= 0) {
               this.setTitanAttacking(false);
               this.setAttackNumber(0);
               this.pounceBoostActive = false;
               this.pounceSolvedHorizontal = 0.0;
               this.pounceSolvedVertical = 0.0;
               this.pounceFlightTargetId = -1;
            }
         }

         int chompCd = this.getChompCooldownTicks();
         if (chompCd > 0) {
            this.dataTracker.set(DATA_CHOMP_COOLDOWN, chompCd - 1);
         }

         if (this.isJawChomping()) {
            this.chompAnimTicks--;
            if (!this.chompBitten && 18 - this.chompAnimTicks >= 9) {
               this.chompBitten = true;
               this.chompBite();
            }

            if (this.chompAnimTicks <= 0) {
               this.dataTracker.set(DATA_JAW_CHOMPING, false);
               this.chompAnimTicks = 0;
            }
         }

         int barrageCd = this.getBarrageCooldownTicks();
         if (barrageCd > 0) {
            this.dataTracker.set(DATA_BARRAGE_COOLDOWN, barrageCd - 1);
         }

         if (currentlyAttacking && isBarrageAttack(currentAttackNum)) {
            this.barrageTicksLeft--;
            if (this.barrageCrashCooldown > 0) {
               this.barrageCrashCooldown--;
            }

            int cycleTick = this.barrageCycleTick % 10;
            if (cycleTick == 3 || cycleTick == 6) {
               this.attackHitTargets.clear();
               this.applyAttackDamage(this.attackHitTargets);
               this.playAttackImpactSound();
               boolean tore = this.barrageBore();
               this.broadcastImpactShake(tore ? 0.6F : 0.35F);
            }

            this.barrageCycleTick++;
            if (this.barrageTicksLeft <= 0) {
               this.endBarrage();
            }
         }

         int kickbackCd = this.getKickbackCooldownTicks();
         if (kickbackCd > 0) {
            this.dataTracker.set(DATA_KICKBACK_COOLDOWN, kickbackCd - 1);
         }

         if (currentlyAttacking && isKickbackAttack(currentAttackNum)) {
            this.kickbackAnimTicks--;
            if (!this.kickbackImpactDone && 14 - this.kickbackAnimTicks >= 10) {
               this.kickbackImpactDone = true;
               this.performKickbackImpact();
            }

            if (this.kickbackAnimTicks <= 0) {
               this.setTitanAttacking(false);
               this.setAttackNumber(0);
            }
         }

         if (this.pounceBoostActive && !currentlyAttacking) {
            this.pounceBoostActive = false;
         }

         if (this.hangJumpRelatchCooldown > 0) {
            this.hangJumpRelatchCooldown--;
         }

         if (this.isLatched() && this.isDismounting()) {
            this.unlatch();
            this.latchRequested = false;
         }

         if (this.latchRequested && !this.isLatched() && !this.isOnGround() && this.hangJumpRelatchCooldown == 0) {
            this.tryLatch();
         }

         if (this.isLatched()) {
            this.setPosition(this.anchorX, this.anchorY, this.anchorZ);
            this.setVelocity(0.0, 0.0, 0.0);
            this.fallDistance = 0.0F;
            if (this.getControllingPassenger() == null) {
               this.unlatch();
               this.latchRequested = false;
            }
         }

         if (this.isHangJumping()) {
            this.hangJumpTicks--;
            if (this.hangJumpTicks <= 0 || this.isOnGround() || this.isLatched()) {
               this.dataTracker.set(DATA_HANG_JUMPING, false);
               this.hangJumpTicks = 0;
               this.hangJumpBoostActive = false;
            }
         }
      } else {
         if (!this.isHangJumping()) {
            this.hangJumpBoostActive = false;
         }

         if (this.pounceClientBoostTicks > 0) {
            this.pounceClientBoostTicks--;
            if (this.pounceClientBoostTicks == 0) {
               this.pounceBoostActive = false;
               this.pounceSolvedHorizontal = 0.0;
               this.pounceSolvedVertical = 0.0;
            }
         } else {
            this.pounceBoostActive = false;
         }
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement", 6, this::testMovementPredicate));
      controllers.add(new AnimationController(this, "action", 6, this::testActionPredicate));
   }

   private RawAnimation resolveHangAnimation() {
      if (!this.isLatched()) {
         return null;
      } else {
         float nx = this.getLatchNormalX();
         float nz = this.getLatchNormalZ();
         if (nx == 0.0F && nz == 0.0F) {
            return null;
         } else {
            double faceX = -nx;
            double faceZ = -nz;
            double yawRad = Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double dot = forwardX * faceX + forwardZ * faceZ;
            if (dot > Math.cos(Math.toRadians(45.0))) {
               return TEST_HANG;
            } else {
               double cross = forwardX * faceZ - forwardZ * faceX;
               return cross > 0.0 ? TEST_RIGHT_HANG : TEST_LEFT_HANG;
            }
         }
      }
   }

   private RawAnimation resolveAbilityAnimation() {
      if (!this.isTitanAttacking()) {
         return null;
      } else {
         int atk = this.getAttackNumber();
         if (atk == 11) {
            return TEST_POUNCE1;
         } else if (atk == 12) {
            return TEST_POUNCE2;
         } else {
            return atk == 13 ? TEST_KICKBACK : null;
         }
      }
   }

   private PlayState testMovementPredicate(AnimationState<TestShifterTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      if (this.isDefeated()) {
         return state.setAndContinue(TEST_DEATH);
      } else if (this.isTransforming()) {
         return state.setAndContinue(TEST_SHIFT);
      } else if (this.isDismounting()) {
         return state.setAndContinue(TEST_DISMOUNT);
      } else if (this.isHangJumping()) {
         return state.setAndContinue(TEST_HANG_JUMP);
      } else {
         RawAnimation hang = this.resolveHangAnimation();
         if (hang != null) {
            return state.setAndContinue(hang);
         } else {
            RawAnimation ability = this.resolveAbilityAnimation();
            if (ability != null) {
               return state.setAndContinue(ability);
            } else if (this.isJumpingForAnim()) {
               return state.setAndContinue(TEST_JUMP);
            } else if (this.isFallingForAnim()) {
               return state.setAndContinue(TEST_FALL);
            } else if (this.isMoving()) {
               if (this.isSprinting()) {
                  state.getController().setAnimationSpeed(1.15);
                  return state.setAndContinue(TEST_RUN);
               } else {
                  state.getController().setAnimationSpeed(1.15);
                  return state.setAndContinue(TEST_WALK);
               }
            } else {
               return state.setAndContinue(TEST_IDLE);
            }
         }
      }
   }

   private PlayState testActionPredicate(AnimationState<TestShifterTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      if (this.isDefeated()) {
         return state.setAndContinue(TEST_DEATH);
      } else if (this.isTransforming()) {
         return state.setAndContinue(TEST_SHIFT);
      } else if (this.isDismounting()) {
         return state.setAndContinue(TEST_DISMOUNT);
      } else if (this.isHangJumping()) {
         return state.setAndContinue(TEST_HANG_JUMP);
      } else {
         RawAnimation hang = this.resolveHangAnimation();
         if (hang != null) {
            return state.setAndContinue(hang);
         } else {
            RawAnimation ability = this.resolveAbilityAnimation();
            if (ability != null) {
               return state.setAndContinue(ability);
            } else {
               if (this.isTitanAttacking()) {
                  int atk = this.getAttackNumber();
                  if (atk == 14) {
                     return state.setAndContinue(TEST_BARRAGE);
                  }
               }

               if (this.isJawChomping()) {
                  return state.setAndContinue(TEST_CHOMP);
               } else {
                  if (this.isTitanAttacking()) {
                     int atk = this.getAttackNumber();
                     if (atk == 1) {
                        return state.setAndContinue(TEST_ATTACK1);
                     }

                     if (atk == 2) {
                        return state.setAndContinue(TEST_ATTACK2);
                     }

                     if (atk == 3) {
                        return state.setAndContinue(TEST_ATTACK3);
                     }
                  }

                  if (this.isJumpingForAnim()) {
                     return state.setAndContinue(TEST_JUMP);
                  } else if (this.isFallingForAnim()) {
                     return state.setAndContinue(TEST_FALL);
                  } else if (this.isMoving()) {
                     if (this.isSprinting()) {
                        state.getController().setAnimationSpeed(1.15);
                        return state.setAndContinue(TEST_RUN_UPPER);
                     } else {
                        state.getController().setAnimationSpeed(1.15);
                        return state.setAndContinue(TEST_WALK_UPPER);
                     }
                  } else {
                     return state.setAndContinue(TEST_IDLE_UPPER);
                  }
               }
            }
         }
      }
   }
}
