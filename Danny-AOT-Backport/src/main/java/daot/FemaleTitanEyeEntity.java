package daot;

import daot.network.EffectPayload;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FemaleTitanEyeEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      FemaleTitanEyeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private FemaleTitanEntity cachedParent;
   private static final double EYE_OFFSET_FORWARD = 2.0;
   private static final double EYE_HEIGHT_OFFSET = 12.5;
   private static final Map<Integer, FemaleTitanEyeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static FemaleTitanEyeEntity getClientInstance(int parentEntityId) {
      return clientInstances.get(parentEntityId);
   }

   public void setRendererPosition(double x, double y, double z) {
      double cy = y - this.getHeight() / 2.0;
      this.setPosition(x, cy, z);
      this.prevX = x;
      this.prevY = cy;
      this.prevZ = z;
      this.rendererPositionedThisTick = true;
   }

   public void setSyncedPosition(double x, double y, double z) {
      this.syncedX = x;
      this.syncedY = y;
      this.syncedZ = z;
      this.lastSyncTick = this.getWorld().getTime();
   }

   private boolean hasFreshSync() {
      return this.lastSyncTick >= 0L && this.getWorld().getTime() - this.lastSyncTick < 10L;
   }

   private void positionFromStaticOffset(FemaleTitanEntity parent) {
      float yawRad = (float)Math.toRadians(parent.bodyYaw);
      double offsetX = -Math.sin(yawRad) * 2.0;
      double offsetZ = Math.cos(yawRad) * 2.0;
      this.setPosition(parent.getX() + offsetX, parent.getY() + 12.5, parent.getZ() + offsetZ);
   }

   public FemaleTitanEyeEntity(EntityType<? extends MobEntity> entityType, World level) {
      super(entityType, level);
      this.noClip = true;
      this.setNoGravity(true);
      this.setInvisible(true);
      this.setPersistent();
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_PARENT_UUID, Optional.empty());
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
   }

   public void setParentTitan(FemaleTitanEntity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   public FemaleTitanEntity getParentTitan() {
      if (this.cachedParent != null) {
         if (this.cachedParent.isAlive() && !this.cachedParent.isRemoved()) {
            return this.cachedParent;
         }

         this.cachedParent = null;
      }

      Optional<UUID> parentUUID = this.dataTracker.get(DATA_PARENT_UUID);
      if (parentUUID.isEmpty()) {
         return null;
      } else {
         UUID uuid = parentUUID.get();
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof FemaleTitanEntity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(50.0))) {
                  if (entity instanceof FemaleTitanEntity titan && titan.getUuid().equals(uuid)) {
                     this.cachedParent = titan;
                     return titan;
                  }
               }
            }

            return null;
         }
      }
   }

   @Override
   protected void initGoals() {
   }

   @Override
   public void tickMovement() {
   }

   @Override
   protected void mobTick() {
   }

   @Override
   public int getMaxLookPitchChange() {
      return 0;
   }

   @Override
   public int getMaxHeadRotation() {
      return 0;
   }

   @Override
   public int getMaxLookYawChange() {
      return 0;
   }

   @Override
   public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
   }

   @Override
   public void tick() {
      super.tick();
      FemaleTitanEntity parent = this.getParentTitan();
      if (parent == null) {
         if (!this.getWorld().isClient() && this.age > 20) {
            this.discard();
         }
      } else {
         if (this.getWorld().isClient()) {
            clientInstances.put(parent.getId(), this);
            if (this.rendererPositionedThisTick) {
               this.rendererPositionedThisTick = false;
            } else {
               this.positionFromStaticOffset(parent);
            }
         } else if (this.hasFreshSync()) {
            this.setPosition(this.syncedX, this.syncedY - this.getHeight() / 2.0, this.syncedZ);
         } else {
            this.positionFromStaticOffset(parent);
         }

         super.setYaw(parent.bodyYaw);
         this.prevYaw = parent.prevBodyYaw;
         this.bodyYaw = parent.bodyYaw;
         this.prevBodyYaw = parent.prevBodyYaw;
         super.setPitch(0.0F);
         this.prevPitch = 0.0F;
         this.setHeadYaw(parent.bodyYaw);
         this.prevHeadYaw = parent.prevBodyYaw;
      }
   }

   @Override
   public boolean isFireImmune() {
      return true;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      if (this.getWorld().isClient()) {
         return false;
      } else if (source.isIn(DamageTypeTags.IS_FIRE)) {
         return false;
      } else if (VillagerTransformTracker.isFromShifter(source)) {
         return false;
      } else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
         FemaleTitanEntity parent = this.getParentTitan();
         if (parent != null && !parent.isTransforming() && !parent.isBlinded()) {
            if (this.getWorld() instanceof ServerWorld serverLevel) {
               BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
               serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY(), this.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
               EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

               for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                  ServerPlayNetworking.send(player, bloodPayload);
               }
            }

            parent.triggerBlindness();
            return true;
         } else {
            return false;
         }
      } else {
         Entity attacker = source.getAttacker();
         if (attacker instanceof LivingEntity livingAttacker) {
            FemaleTitanEntity parent = this.getParentTitan();
            if (parent != null) {
               if (ShifterDodgeManager.isTitanInDodgeIFrames(parent)) {
                  return false;
               }

               if (parent.isTransforming()) {
                  return false;
               }

               UUID shifterUUID = parent.getShifterUUID();
               if (shifterUUID != null && livingAttacker.getUuid().equals(shifterUUID)) {
                  return false;
               }

               if (parent.hasPassenger(attacker)) {
                  return false;
               }

               if (attacker.getVehicle() == parent) {
                  return false;
               }

               if (parent.getControllingPassenger() == attacker) {
                  return false;
               }
            }

            if (parent != null && !parent.isBlinded()) {
               boolean isArrow = source.getSource() instanceof PersistentProjectileEntity;
               if (!isArrow && amount < 6.0F) {
                  if (livingAttacker instanceof PlayerEntity player) {
                     player.sendMessage(Text.literal("You need a stronger weapon to hit the eyes!"), true);
                  }

                  return false;
               }

               SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
               SoundEvent slash = slashSounds[this.random.nextInt(3)];
               float pitch = 0.9F + this.random.nextFloat() * 0.1F;
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), slash, SoundCategory.PLAYERS, 8.0F, pitch);
               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

                  for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                     ServerPlayNetworking.send(player, bloodPayload);
                  }
               }

               parent.triggerBlindness();
               parent.triggerHitReaction(attacker, true, false);
               if (livingAttacker instanceof PlayerEntity player) {
                  player.sendMessage(Text.literal("Critical hit! Female Titan blinded for 6 seconds!"), true);
               }

               return true;
            }
         }

         return false;
      }
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      Optional<UUID> parentUUID = this.dataTracker.get(DATA_PARENT_UUID);
      if (parentUUID.isPresent()) {
         nbt.putUuid("ParentTitan", parentUUID.get());
      }
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.containsUuid("ParentTitan")) {
         this.dataTracker.set(DATA_PARENT_UUID, Optional.of(nbt.getUuid("ParentTitan")));
      }
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   protected void pushAway(Entity entity) {
   }

   @Override
   public boolean isCollidable() {
      return false;
   }

   @Override
   public boolean collidesWith(Entity other) {
      return other instanceof FemaleTitanEntity titan ? titan != this.getParentTitan() : super.collidesWith(other);
   }

   @Override
   public boolean canHit() {
      return true;
   }
}
