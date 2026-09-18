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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class OgreTitanNapeEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      OgreTitanNapeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private OgreTitanEntity cachedParent;
   private static final double NAPE_OFFSET_BACK = 1.0;
   private static final double NAPE_HEIGHT_OFFSET = 7.0;
   private static final Map<Integer, OgreTitanNapeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static OgreTitanNapeEntity getClientInstance(int parentEntityId) {
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

   private void positionFromStaticOffset(OgreTitanEntity parent, double scale) {
      float yawRad = (float)Math.toRadians(parent.bodyYaw);
      double scaledBackOffset = 1.0 * scale;
      double offsetX = Math.sin(yawRad) * scaledBackOffset;
      double offsetZ = -Math.cos(yawRad) * scaledBackOffset;
      this.setPosition(parent.getX() + offsetX, parent.getY() + 7.0 * scale, parent.getZ() + offsetZ);
   }

   public OgreTitanNapeEntity(EntityType<? extends MobEntity> entityType, World level) {
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
      return MobEntity.createMobAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
         .add(daot.compat.attributes.DaotEntityAttributes.SCALE, 1.0);
   }

   public void setParentTitan(OgreTitanEntity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   public OgreTitanEntity getParentTitan() {
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
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof OgreTitanEntity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(20.0))) {
                  if (entity instanceof OgreTitanEntity titan && titan.getUuid().equals(uuid)) {
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
      OgreTitanEntity parent = this.getParentTitan();
      if (parent == null) {
         if (!this.getWorld().isClient() && this.age > 20) {
            this.discard();
         }
      } else {
         double scale = daot.compat.attributes.LivingEntityAttributeCompat.getScale(parent);
         if (!this.getWorld().isClient() && this.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE) != null) {
            this.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE).setBaseValue(scale);
         }

         if (this.getWorld().isClient()) {
            clientInstances.put(parent.getId(), this);
            if (this.rendererPositionedThisTick) {
               this.rendererPositionedThisTick = false;
            } else {
               this.positionFromStaticOffset(parent, scale);
            }
         } else if (this.hasFreshSync()) {
            this.setPosition(this.syncedX, this.syncedY - this.getHeight() / 2.0, this.syncedZ);
         } else {
            this.positionFromStaticOffset(parent, scale);
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
   public boolean damage(DamageSource source, float amount) {
      if (this.getWorld().isClient()) {
         return false;
      } else {
         OgreTitanEntity parent = this.getParentTitan();
         if (parent != null && parent.isProtecting()) {
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 2.0F, 0.6F);
            return false;
         } else {
            Entity attacker = source.getAttacker();
            if (!(attacker instanceof AttackTitanEntity)
               && !(attacker instanceof ArmoredTitanEntity)
               && !(attacker instanceof ColossalTitanEntity)
               && !(attacker instanceof BeastTitanEntity)
               && !(attacker instanceof WarhammerTitanEntity)
               && !(attacker instanceof FemaleTitanEntity)) {
               if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                  if (parent != null) {
                     this.spawnBloodParticles();
                     parent.onNapeHit();
                     return true;
                  } else {
                     return false;
                  }
               } else {
                  if (attacker instanceof LivingEntity livingAttacker) {
                     if (parent != null && parent.hasPassenger(attacker)) {
                        return false;
                     }

                     if (parent != null) {
                        float yawRad = (float)Math.toRadians(parent.bodyYaw);
                        double forwardX = -Math.sin(yawRad);
                        double forwardZ = Math.cos(yawRad);
                        double toAttackerX = livingAttacker.getX() - parent.getX();
                        double toAttackerZ = livingAttacker.getZ() - parent.getZ();
                        double len = Math.sqrt(toAttackerX * toAttackerX + toAttackerZ * toAttackerZ);
                        if (len > 0.01) {
                           double dot = (forwardX * toAttackerX + forwardZ * toAttackerZ) / len;
                           if (dot > 0.5) {
                              return false;
                           }
                        }
                     }

                     ItemStack mainHandItem = livingAttacker.getMainHandStack();
                     boolean isUsingBlade = mainHandItem.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHandItem) != BladeItem.BladeState.EMPTY;
                     boolean isHomelander = HomelanderNapeBypass.isHomelanderAttacker(livingAttacker, this.getWorld());
                     double distanceToAttacker = this.distanceTo(livingAttacker);
                     boolean isMeleeRange = distanceToAttacker <= 6.0;
                     if ((isUsingBlade || isHomelander) && isMeleeRange) {
                        if (parent != null && parent.isInParryWindow()) {
                           parent.performParry(livingAttacker);
                           return false;
                        }

                        this.spawnBloodParticles();
                        if (parent != null) {
                           parent.onNapeHit();
                        }

                        return true;
                     }
                  }

                  return false;
               }
            } else {
               if (parent != null) {
                  this.spawnBloodParticles();
                  if (parent.isInParryWindow()) {
                     parent.performParry(attacker instanceof LivingEntity le ? le : null);
                  } else {
                     parent.onNapeHit();
                  }
               }

               return true;
            }
         }
      }
   }

   private void spawnBloodParticles() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
         serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.25, this.getZ(), 30, 0.3, 0.4, 0.3, 0.1);
         serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.1, 0.1, 0.1, 0.15);
         EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

         for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
            ServerPlayNetworking.send(player, bloodPayload);
         }
      }
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      this.dataTracker.get(DATA_PARENT_UUID).ifPresent(uuid -> nbt.putUuid("ParentUUID", uuid));
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.containsUuid("ParentUUID")) {
         this.dataTracker.set(DATA_PARENT_UUID, Optional.of(nbt.getUuid("ParentUUID")));
      }
   }
}
