package daot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ArmoredTitanGrabEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      ArmoredTitanGrabEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private ArmoredTitanEntity cachedParent;
   private boolean allowDismount = false;
   private static final double GRAB_HEIGHT_OFFSET = 9.0;
   private static final Map<Integer, ArmoredTitanGrabEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static ArmoredTitanGrabEntity getClientInstance(int parentEntityId) {
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

   private void positionFromStaticOffset(ArmoredTitanEntity parent) {
      float yawRad = (float)Math.toRadians(parent.bodyYaw);
      double fwdX = -Math.sin(yawRad) * 4.0;
      double fwdZ = Math.cos(yawRad) * 4.0;
      this.setPosition(parent.getX() + fwdX, parent.getY() + 9.0, parent.getZ() + fwdZ);
   }

   public ArmoredTitanGrabEntity(EntityType<? extends MobEntity> entityType, World level) {
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

   public boolean isDismountAllowed() {
      return this.allowDismount;
   }

   public void setDismountAllowed(boolean allowed) {
      this.allowDismount = allowed;
   }

   public void setParentTitan(ArmoredTitanEntity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   public ArmoredTitanEntity getParentTitan() {
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
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof ArmoredTitanEntity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(50.0))) {
                  if (entity instanceof ArmoredTitanEntity titan && titan.getUuid().equals(uuid)) {
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
   protected boolean canAddPassenger(Entity passenger) {
      return true;
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      return new Vec3d(this.getX(), this.getY() + this.getHeight() / 2.0 - passenger.getHeight() / 2.0, this.getZ());
   }

   @Override
   public boolean isInvulnerable() {
      return true;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      if (!this.getWorld().isClient() && source.getAttacker() instanceof LivingEntity attacker) {
         ItemStack main = attacker.getMainHandStack();
         boolean loadedBlade = main.getItem() instanceof BladeItem && BladeItem.getBladeState(main) != BladeItem.BladeState.EMPTY;
         ArmoredTitanEntity parent = this.getParentTitan();
         if (loadedBlade && parent != null && parent.isGrabbing()) {
            parent.releaseGrabbedEntity();
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SLASH1, SoundCategory.PLAYERS, 6.0F, 1.1F);
         }
      }

      return false;
   }

   @Override
   public void tick() {
      this.baseTick();
      ArmoredTitanEntity parent = this.getParentTitan();
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
      }
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      Optional<UUID> parentUUID = this.dataTracker.get(DATA_PARENT_UUID);
      parentUUID.ifPresent(uuid -> nbt.putUuid("ParentTitan", uuid));
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.containsUuid("ParentTitan")) {
         this.dataTracker.set(DATA_PARENT_UUID, Optional.of(nbt.getUuid("ParentTitan")));
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      this.removeAllPassengers();
      if (this.getWorld().isClient()) {
         ArmoredTitanEntity parent = this.getParentTitan();
         if (parent != null) {
            clientInstances.remove(parent.getId());
         }
      }

      super.remove(reason);
   }

   @Override protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (!this.hasPassenger(passenger)) return;
      Vec3d bpRidingPos = this.getPassengerRidingPos(passenger);
      positionUpdater.accept(passenger, bpRidingPos.x, bpRidingPos.y, bpRidingPos.z);
   }
}
