package daot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public class CrawlerTitanEyeEntity extends MobEntity implements PureTitanHitbox {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      CrawlerTitanEyeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private CrawlerTitanEntity cachedParent;
   private static final double EYE_OFFSET_FORWARD = 1.0;
   private static final double EYE_HEIGHT_OFFSET = 2.0;
   private static final Map<Integer, CrawlerTitanEyeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static CrawlerTitanEyeEntity getClientInstance(int parentEntityId) {
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

   private void positionFromStaticOffset(CrawlerTitanEntity parent, double scale) {
      float yawRad = (float)Math.toRadians(parent.bodyYaw);
      double scaledForwardOffset = 1.0 * scale;
      double offsetX = -Math.sin(yawRad) * scaledForwardOffset;
      double offsetZ = Math.cos(yawRad) * scaledForwardOffset;
      this.setPosition(parent.getX() + offsetX, parent.getY() + 2.0 * scale, parent.getZ() + offsetZ);
   }

   public CrawlerTitanEyeEntity(EntityType<? extends MobEntity> entityType, World level) {
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
         .add(daot.compat.attributes.DaotEntityAttributes.SCALE, 0.7);
   }

   public void setParentTitan(CrawlerTitanEntity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   @Override
   public LivingEntity pureTitanOwner() {
      return this.getParentTitan();
   }

   public CrawlerTitanEntity getParentTitan() {
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
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof CrawlerTitanEntity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(20.0))) {
                  if (entity instanceof CrawlerTitanEntity titan && titan.getUuid().equals(uuid)) {
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
      CrawlerTitanEntity parent = this.getParentTitan();
      if (parent == null) {
         if (!this.getWorld().isClient() && this.age > 20) {
            this.discard();
         }
      } else {
         double scale = daot.compat.attributes.LivingEntityAttributeCompat.getScale(parent);
         if (!this.getWorld().isClient() && this.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE) != null) {
            this.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE).setBaseValue(scale * 0.7);
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
      return false;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource damageSource) {
      return true;
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
      return other instanceof CrawlerTitanEntity titan ? titan != this.getParentTitan() : super.collidesWith(other);
   }

   @Override
   public boolean canHit() {
      return true;
   }
}
