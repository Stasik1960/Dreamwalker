package daot;

import daot.network.ODMJamPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;

public class OgreShifterTitanEntity extends AttackTitanEntity implements daot.compat.BaseDimensionsProvider {
   private static final TrackedData<Boolean> DATA_OGRE_PROTECTING = DataTracker.registerData(OgreShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_OGRE_WIRE_YANKING = DataTracker.registerData(OgreShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final RawAnimation OGRE_IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation OGRE_WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation OGRE_RUN_ANIM = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation OGRE_IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("idle_upper");
   private static final RawAnimation OGRE_WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("walk_upper");
   private static final RawAnimation OGRE_RUN_UPPER_ANIM = RawAnimation.begin().thenLoop("run_upper");
   private static final RawAnimation OGRE_SHIFT_ANIM = RawAnimation.begin().thenPlayAndHold("shift");
   private static final RawAnimation OGRE_JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("jump");
   private static final RawAnimation OGRE_FALLING_ANIM = RawAnimation.begin().thenLoop("falling");
   private static final RawAnimation OGRE_LAND_ANIM = RawAnimation.begin().thenPlayAndHold("land");
   private static final RawAnimation OGRE_DISMOUNT_ANIM = RawAnimation.begin().thenLoop("dismount");
   private static final RawAnimation OGRE_CROUCH_IDLE_ANIM = RawAnimation.begin().thenLoop("crouchidle");
   private static final RawAnimation OGRE_CROUCH_WALK_ANIM = RawAnimation.begin().thenLoop("crouchwalk");
   private static final RawAnimation OGRE_CROUCH_IDLE_UPPER_ANIM = RawAnimation.begin().thenLoop("crouchidle_upper");
   private static final RawAnimation OGRE_CROUCH_WALK_UPPER_ANIM = RawAnimation.begin().thenLoop("crouchwalk_upper");
   private static final RawAnimation OGRE_CROUCH_ARMED_ANIM = RawAnimation.begin().thenLoop("croucharmed");
   private static final RawAnimation OGRE_ARMED_IDLE_ANIM = RawAnimation.begin().thenLoop("armedidle");
   private static final RawAnimation OGRE_ATTACK1_ANIM = RawAnimation.begin().thenPlayAndHold("attack1");
   private static final RawAnimation OGRE_ATTACK2_ANIM = RawAnimation.begin().thenPlayAndHold("attack2");
   private static final RawAnimation OGRE_UPPERCUT_R_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_r");
   private static final RawAnimation OGRE_UPPERCUT_L_ANIM = RawAnimation.begin().thenPlayAndHold("uppercut_l");
   private static final RawAnimation OGRE_GROUND_SMASH1_ANIM = RawAnimation.begin().thenPlayAndHold("ground_smash1");
   private static final RawAnimation OGRE_GROUND_SMASH2_ANIM = RawAnimation.begin().thenPlayAndHold("ground_smash2");
   private static final RawAnimation OGRE_KICK_ANIM = RawAnimation.begin().thenPlayAndHold("kick_attack");
   private static final RawAnimation OGRE_PROTECT_ANIM = RawAnimation.begin().thenPlayAndHold("protect");
   private static final RawAnimation OGRE_WIRE_YANK_ANIM = RawAnimation.begin().thenPlayAndHold("wire_yank");
   private static final RawAnimation OGRE_DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");
   private static final RawAnimation OGRE_KNOCKED_ANIM = RawAnimation.begin().thenPlayAndHold("knocked");
   private static final RawAnimation OGRE_IMPALED_ANIM = RawAnimation.begin().thenLoop("impaled");
   private static final RawAnimation OGRE_CLIMB_ANIM = RawAnimation.begin().thenLoop("climb");
   private static final RawAnimation OGRE_THROW_CHARGE_ANIM = RawAnimation.begin().thenLoop("throw_charge");
   private static final RawAnimation OGRE_THROW_ANIM = RawAnimation.begin().thenPlayAndHold("throw");
   private static final RawAnimation OGRE_EAT_ANIM = RawAnimation.begin().thenPlayAndHold("eat");
   private static final int OGRE_UPPERCUT_R = 5;
   private static final int OGRE_UPPERCUT_L = 6;
   public static final int OGRE_GROUND_SMASH2 = 7;
   private static final float OGRE_DAMAGE_MULT = 1.5F;
   private static final int OGRE_PUNCH_TICKS = 25;
   private static final int OGRE_PUNCH_EFFECT = 10;
   private static final int OGRE_UPPERCUT_TICKS = 30;
   private static final int OGRE_UPPERCUT_EFFECT = 12;
   private static final int OGRE_SMASH_TICKS = 35;
   private static final int OGRE_SMASH_EFFECT = 14;
   private static final int OGRE_KICK_TICKS = 35;
   private static final int OGRE_KICK_EFFECT = 18;
   private static final int OGRE_WIRE_YANK_TICKS = 30;
   private int ogreAttackCooldown = 0;
   private int ogreAttackAnimTicks = 0;
   private int ogreAttackEffectTimer = 0;
   private boolean ogreAttackEffectTriggered = false;
   private int ogreAttackNum = 0;
   private int wireYankTicks = 0;
   private final Set<UUID> hookedPlayers = new HashSet<>();
   private static final EntityDimensions OGRE_STANDING = EntityDimensions.changing(2.5F, 10.0F);
   private static final EntityDimensions OGRE_CROUCHING = EntityDimensions.changing(2.5F, 7.0F);

   public OgreShifterTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public static Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.85)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 20.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 10.0)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.5)
         .add(daot.compat.attributes.DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
   }

   @Override
   protected double getHealthMultiplier() {
      return 1.5;
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_OGRE_PROTECTING, false);
      this.dataTracker.startTracking(DATA_OGRE_WIRE_YANKING, false);
   }

   public boolean isOgreProtecting() {
      return this.dataTracker.get(DATA_OGRE_PROTECTING);
   }

   public void setOgreProtecting(boolean v) {
      this.dataTracker.set(DATA_OGRE_PROTECTING, v);
   }

   public boolean isOgreWireYanking() {
      return this.dataTracker.get(DATA_OGRE_WIRE_YANKING);
   }

   public void setOgreWireYanking(boolean v) {
      this.dataTracker.set(DATA_OGRE_WIRE_YANKING, v);
   }

   public void setPlayerHooked(UUID playerUUID, boolean hooked) {
      if (hooked) {
         this.hookedPlayers.add(playerUUID);
      } else {
         this.hookedPlayers.remove(playerUUID);
      }
   }

   @Override
   public EntityDimensions getBaseDimensions(EntityPose pose) {
      return this.isInSneakingPose() ? OGRE_CROUCHING : OGRE_STANDING;
   }

   @Override
   public void onPlayerShift(PlayerEntity player) {
      super.onPlayerShift(player);
      if (this.bossBar != null) {
         this.bossBar.clearPlayers();
         this.bossBar = null;
      }
   }

   @Override
   public void spawnHitboxes() {
      if (!this.getWorld().isClient() && this.napeEntity == null) {
         AttackTitanNapeEntity nape = new AttackTitanNapeEntity(DannysAot.ATTACK_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
      }

      if (this.bossBar != null) {
         this.bossBar.clearPlayers();
         this.bossBar = null;
      }
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardOffset = 0.8;
         double offsetX = -Math.sin(yawRad) * forwardOffset;
         double offsetZ = Math.cos(yawRad) * forwardOffset;
         double x = this.getX() + offsetX;
         double y = this.getY() + 7.5;
         double z = this.getZ() + offsetZ;
         positionUpdater.accept(passenger, x, y, z);
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      return this.getPos().add(0.0, 7.5, 0.0);
   }

   @Override
   public void triggerAttack() {
      if (this.ogreAttackCooldown <= 0 && !this.isOgreProtecting() && !this.isOgreWireYanking() && !this.isTransforming() && !this.isDismounting()) {
         int[] punches = new int[]{1, 2, 5, 6};
         this.ogreAttackNum = punches[this.random.nextInt(punches.length)];
         this.setWasMovingOnAttackStart(this.isMoving());
         int ticks = this.ogreAttackNum != 5 && this.ogreAttackNum != 6 ? 25 : 30;
         this.setAttackNumber(this.ogreAttackNum);
         this.setTitanAttacking(true);
         this.ogreAttackAnimTicks = ticks;
         this.ogreAttackCooldown = ticks;
         this.ogreAttackEffectTimer = 0;
         this.ogreAttackEffectTriggered = false;
      }
   }

   @Override
   public void triggerAbility(int abilityNumber) {
      if (!this.isTransforming() && !this.isDismounting()) {
         switch (abilityNumber) {
            case 1:
               if (this.ogreAttackCooldown > 0 || this.isOgreProtecting() || this.isOgreWireYanking()) {
                  return;
               }

               this.ogreAttackNum = 4;
               this.setWasMovingOnAttackStart(this.isMoving());
               this.setAttackNumber(4);
               this.setTitanAttacking(true);
               this.ogreAttackAnimTicks = 35;
               this.ogreAttackCooldown = 35;
               this.ogreAttackEffectTimer = 0;
               this.ogreAttackEffectTriggered = false;
               break;
            case 2:
               if (this.ogreAttackCooldown > 0 || this.isOgreProtecting() || this.isOgreWireYanking()) {
                  return;
               }

               boolean smash2 = this.random.nextBoolean();
               this.ogreAttackNum = smash2 ? 7 : 3;
               this.setWasMovingOnAttackStart(this.isMoving());
               this.setAttackNumber(this.ogreAttackNum);
               this.setTitanAttacking(true);
               this.ogreAttackAnimTicks = 35;
               this.ogreAttackCooldown = 35;
               this.ogreAttackEffectTimer = 0;
               this.ogreAttackEffectTriggered = false;
               break;
            case 3:
               if (this.isOgreProtecting()) {
                  this.setOgreProtecting(false);
               } else {
                  if (this.ogreAttackCooldown > 0 || this.isOgreWireYanking()) {
                     return;
                  }

                  this.setOgreProtecting(true);
                  this.setTitanAttacking(false);
                  this.setAttackNumber(0);
                  this.ogreAttackAnimTicks = 0;
               }
               break;
            case 4:
               if (this.ogreAttackCooldown > 0 || this.isOgreProtecting()) {
                  return;
               }

               this.setOgreWireYanking(true);
               this.wireYankTicks = 30;
               this.ogreAttackCooldown = 40;
               this.performShifterWireYank();
               break;
            case 5:
               if (this.getShifterUUID() != null
                  && this.getFirstPassenger() instanceof ServerPlayerEntity rider
                  && rider.getUuid().equals(this.getShifterUUID())) {
                  OgreHealAbility.tryHeal(rider, this);
               }
         }
      }
   }

   @Override
   public void tick() {
      if (!this.getWorld().isClient()) {
         if (this.ogreAttackAnimTicks > 0) {
            this.ogreAttackAnimTicks--;
            if (this.ogreAttackAnimTicks <= 0) {
               this.setTitanAttacking(false);
               this.setAttackNumber(0);
               this.ogreAttackNum = 0;
            }
         }

         if (this.ogreAttackCooldown > 0) {
            this.ogreAttackCooldown--;
         }

         if (this.isTitanAttacking() && !this.ogreAttackEffectTriggered && this.ogreAttackNum > 0) {
            this.ogreAttackEffectTimer++;

            int effectTick = switch (this.ogreAttackNum) {
               case 1, 2 -> 10;
               case 3, 7 -> 14;
               case 4 -> 18;
               case 5, 6 -> 12;
               default -> 10;
            };
            if (this.ogreAttackEffectTimer >= effectTick) {
               switch (this.ogreAttackNum) {
                  case 1:
                  case 2:
                  case 5:
                  case 6:
                     this.dealOgrePunchDamage();
                     break;
                  case 3:
                  case 7:
                     this.performOgreGroundSmash();
                     break;
                  case 4:
                     this.performOgreKick();
               }

               this.ogreAttackEffectTriggered = true;
            }
         }

         if (this.isOgreWireYanking()) {
            this.wireYankTicks--;
            if (this.wireYankTicks % 5 == 0) {
               this.performShifterWireYank();
            }

            if (this.wireYankTicks <= 0) {
               this.setOgreWireYanking(false);
            }
         }

         if (this.isOgreProtecting()) {
            this.setVelocity(0.0, this.getVelocity().y, 0.0);
         }
      }

      super.tick();
   }

   @Override
   public void startDismounting(PlayerEntity player) {
      if (!this.isOgreProtecting()) {
         super.startDismounting(player);
      }
   }

   private void dealOgrePunchDamage() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      float damage = (float)ModConfig.get().attackTitanAttackDamage * 1.5F;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(14.0, 20.0, 14.0))) {
         if (target != this && !this.isOwnEntity(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean close = hDist < this.getWidth();
            Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
            if (close || dot > 0.3 && hDist < 14.0) {
               target.damage(this.getDamageSources().mobAttack(this), damage);
               this.applyOgreKnockback(target, forwardX, forwardZ, 2.5, 1.0);
            }
         }
      }

      SoundEvent[] sounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
      this.getWorld()
         .playSound(
            null,
            this.getX() + forwardX * 5.0,
            this.getY() + 5.0,
            this.getZ() + forwardZ * 5.0,
            sounds[this.random.nextInt(sounds.length)],
            SoundCategory.HOSTILE,
            8.0F,
            0.8F
         );
   }

   private void performOgreGroundSmash() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      float damage = (float)ModConfig.get().attackTitanKickDamage * 1.5F;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(12.0, 15.0, 12.0))) {
         if (target != this && !this.isOwnEntity(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean close = hDist < this.getWidth();
            Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
            if (close || dot > 0.2 && hDist < 10.0) {
               target.damage(this.getDamageSources().mobAttack(this), damage);
               this.applyOgreKnockback(target, forwardX, forwardZ, 3.0, 1.5);
            }
         }
      }

      this.getWorld()
         .playSound(
            null,
            this.getX() + forwardX * 6.0,
            this.getY(),
            this.getZ() + forwardZ * 6.0,
            SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.HOSTILE,
            2.5F,
            0.4F
         );
   }

   private void performOgreKick() {
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad);
      double forwardZ = Math.cos(yawRad);
      float damage = (float)ModConfig.get().attackTitanKickDamage * 1.5F;

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, this.getBoundingBox().expand(14.0, 15.0, 14.0))) {
         if (target != this && !this.isOwnEntity(target)) {
            Vec3d toTarget = target.getPos().subtract(this.getPos());
            double hDist = toTarget.horizontalLength();
            boolean close = hDist < this.getWidth();
            Vec3d toTargetH = new Vec3d(toTarget.x, 0.0, toTarget.z);
            double dot = hDist > 0.5 ? toTargetH.normalize().dotProduct(new Vec3d(forwardX, 0.0, forwardZ)) : 1.0;
            if (close || dot > 0.4 && hDist < 12.0) {
               target.damage(this.getDamageSources().mobAttack(this), damage);
               this.applyOgreKnockback(target, forwardX, forwardZ, 5.0, 3.0);
            }
         }
      }

      this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.5F, 0.5F);
   }

   private void applyOgreKnockback(LivingEntity target, double forwardX, double forwardZ, double knockbackH, double knockbackV) {
      Vec3d toTarget = target.getPos().subtract(this.getPos());
      Vec3d horizontalDir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
      if (target instanceof PlayerEntity p) {
         Vec3d kb = horizontalDir.multiply(knockbackH).add(0.0, knockbackV, 0.0);
         p.setVelocity(kb);
         p.velocityModified = true;
         p.velocityDirty = true;
      } else if (target instanceof AttackTitanEntity at) {
         at.setPendingKnockback(horizontalDir.multiply(knockbackH).add(0.0, knockbackV, 0.0));
         at.applyHitSlow(20);
      } else {
         target.setVelocity(horizontalDir.multiply(knockbackH).add(0.0, knockbackV, 0.0));
         target.velocityModified = true;
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

         this.getWorld().playSound(null, this.getX(), this.getY() + 5.0, this.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0F, 0.5F);
      }
   }

   private boolean isOwnEntity(Entity entity) {
      UUID shifterUUID = this.getShifterUUID();
      if (shifterUUID != null && entity.getUuid().equals(shifterUUID)) {
         return true;
      } else if (entity.getVehicle() == this) {
         return true;
      } else if (entity instanceof AttackTitanNapeEntity nape && nape.getParentTitan() == this) {
         return true;
      } else {
         return entity instanceof AttackTitanEyeEntity eye && eye.getParentTitan() == this ? true : entity instanceof PlayerEntity p && p.getVehicle() == this;
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement_controller", 6, this::ogreMovementPredicate));
      controllers.add(new AnimationController(this, "action_controller", 6, this::ogreActionPredicate));
   }

   private PlayState ogreMovementPredicate(AnimationState<OgreShifterTitanEntity> state) {
      if (this.isDefeated() || this.isKnocked()) {
         return PlayState.STOP;
      } else if (this.isTransforming() || this.isDismounting()) {
         return PlayState.STOP;
      } else if (this.isJumping()) {
         return state.setAndContinue(OGRE_JUMP_ANIM);
      } else if (this.isFalling()) {
         return state.setAndContinue(OGRE_FALLING_ANIM);
      } else if (this.isLanding()) {
         return state.setAndContinue(OGRE_LAND_ANIM);
      } else if (this.isTitanClimbing()) {
         return state.setAndContinue(OGRE_CLIMB_ANIM);
      } else if (!this.isInSneakingPose()) {
         int atkNum = this.getAttackNumber();
         if (!this.isTitanAttacking() || atkNum != 3 && atkNum != 4 && atkNum != 7) {
            if (this.isOgreProtecting() || this.isOgreWireYanking()) {
               return state.setAndContinue(OGRE_IDLE_ANIM);
            } else if (!this.isMoving()) {
               return state.setAndContinue(OGRE_IDLE_ANIM);
            } else {
               return this.isSprinting() && !this.isArmed() ? state.setAndContinue(OGRE_RUN_ANIM) : state.setAndContinue(OGRE_WALK_ANIM);
            }
         } else {
            return state.setAndContinue(OGRE_IDLE_ANIM);
         }
      } else {
         return this.isMoving() ? state.setAndContinue(OGRE_CROUCH_WALK_ANIM) : state.setAndContinue(OGRE_CROUCH_IDLE_ANIM);
      }
   }

   private PlayState ogreActionPredicate(AnimationState<OgreShifterTitanEntity> state) {
      if (this.isDefeated()) {
         return state.setAndContinue(OGRE_DEATH_ANIM);
      } else if (this.isTransforming()) {
         return state.setAndContinue(OGRE_SHIFT_ANIM);
      } else if (this.isKnocked()) {
         return state.setAndContinue(OGRE_KNOCKED_ANIM);
      } else if (this.isTitanClimbing()) {
         return PlayState.STOP;
      } else if (this.isLanding()) {
         return state.setAndContinue(OGRE_LAND_ANIM);
      } else if (this.isJumping()) {
         return state.setAndContinue(OGRE_JUMP_ANIM);
      } else if (this.isFalling()) {
         return state.setAndContinue(OGRE_FALLING_ANIM);
      } else if (this.isOgreWireYanking()) {
         return state.setAndContinue(OGRE_WIRE_YANK_ANIM);
      } else if (this.isOgreProtecting()) {
         return state.setAndContinue(OGRE_PROTECT_ANIM);
      } else if (this.isImpaled() && !this.isDismounting() && !this.isTitanAttacking()) {
         return state.setAndContinue(OGRE_IMPALED_ANIM);
      } else if (this.isThrowCharging()) {
         return state.setAndContinue(OGRE_THROW_CHARGE_ANIM);
      } else if (this.isThrowing()) {
         return state.setAndContinue(OGRE_THROW_ANIM);
      } else if (this.isEating()) {
         return state.setAndContinue(OGRE_EAT_ANIM);
      } else if (this.isTitanAttacking()) {
         RawAnimation anim = switch (this.getAttackNumber()) {
            case 1 -> OGRE_ATTACK1_ANIM;
            case 2 -> OGRE_ATTACK2_ANIM;
            case 3 -> OGRE_GROUND_SMASH1_ANIM;
            case 4 -> OGRE_KICK_ANIM;
            case 5 -> OGRE_UPPERCUT_R_ANIM;
            case 6 -> OGRE_UPPERCUT_L_ANIM;
            case 7 -> OGRE_GROUND_SMASH2_ANIM;
            default -> OGRE_IDLE_UPPER_ANIM;
         };
         return state.setAndContinue(anim);
      } else if (this.isDismounting()) {
         return state.setAndContinue(OGRE_DISMOUNT_ANIM);
      } else if (this.isArmed()) {
         return this.isInSneakingPose() ? state.setAndContinue(OGRE_CROUCH_ARMED_ANIM) : state.setAndContinue(OGRE_ARMED_IDLE_ANIM);
      } else if (this.isInSneakingPose()) {
         return this.isMoving() ? state.setAndContinue(OGRE_CROUCH_WALK_UPPER_ANIM) : state.setAndContinue(OGRE_CROUCH_IDLE_UPPER_ANIM);
      } else if (this.isMoving()) {
         return this.isSprinting() ? state.setAndContinue(OGRE_RUN_UPPER_ANIM) : state.setAndContinue(OGRE_WALK_UPPER_ANIM);
      } else {
         return state.setAndContinue(OGRE_IDLE_UPPER_ANIM);
      }
   }
}
