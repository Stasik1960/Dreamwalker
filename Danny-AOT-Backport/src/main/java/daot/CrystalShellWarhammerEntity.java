package daot;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CrystalShellWarhammerEntity extends Entity implements GeoEntity {
   private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      CrystalShellWarhammerEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_PARENT_TITAN_ID = DataTracker.registerData(
      CrystalShellWarhammerEntity.class, TrackedDataHandlerRegistry.INTEGER
   );
   private int parentMissingTicks = 0;
   private static final int PARENT_MISSING_GRACE_TICKS = 20;

   public CrystalShellWarhammerEntity(EntityType<?> entityType, World level) {
      super(entityType, level);
      this.noClip = false;
      this.setNoGravity(false);
   }

   @Override
   protected void initDataTracker() {
      this.dataTracker.startTracking(DATA_SHIFTER_UUID, Optional.empty());
      this.dataTracker.startTracking(DATA_PARENT_TITAN_ID, -1);
   }

   public UUID getShifterUUID() {
      return this.dataTracker.get(DATA_SHIFTER_UUID).orElse(null);
   }

   public void setShifterUUID(UUID uuid) {
      this.dataTracker.set(DATA_SHIFTER_UUID, Optional.ofNullable(uuid));
   }

   public int getParentTitanId() {
      return this.dataTracker.get(DATA_PARENT_TITAN_ID);
   }

   public void setParentTitanId(int id) {
      this.dataTracker.set(DATA_PARENT_TITAN_ID, id);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.hasNoGravity()) {
         this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0));
      }

      this.move(MovementType.SELF, this.getVelocity());
      if (this.isOnGround()) {
         this.setVelocity(this.getVelocity().multiply(0.5, 0.0, 0.5));
      } else {
         this.setVelocity(this.getVelocity().multiply(0.98, 0.98, 0.98));
      }

      if (!this.getWorld().isClient()) {
         int titanId = this.getParentTitanId();
         if (titanId == -1) {
            return;
         }

         if (this.getWorld().getEntityById(titanId) instanceof WarhammerTitanEntity titan && titan.isAlive() && !titan.isRemoved()) {
            this.parentMissingTicks = 0;
         } else {
            this.parentMissingTicks++;
            if (this.parentMissingTicks >= 20) {
               this.discard();
            }
         }
      }
   }

   @Override
   public boolean canHit() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      return false;
   }

   @Override
   public boolean isInvulnerable() {
      return true;
   }

   @Override
   public Box getVisibilityBoundingBox() {
      return super.getVisibilityBoundingBox().expand(200.0);
   }

   @Override
   public boolean shouldRender(double distance) {
      return distance < 65536.0;
   }

   @Override
   protected void readCustomDataFromNbt(NbtCompound nbt) {
      if (nbt.contains("ShifterUUID")) {
         this.setShifterUUID(nbt.getUuid("ShifterUUID"));
      }

      if (nbt.contains("ParentTitanId")) {
         this.dataTracker.set(DATA_PARENT_TITAN_ID, nbt.getInt("ParentTitanId"));
      }
   }

   @Override
   protected void writeCustomDataToNbt(NbtCompound nbt) {
      UUID shifter = this.getShifterUUID();
      if (shifter != null) {
         nbt.putUuid("ShifterUUID", shifter);
      }

      nbt.putInt("ParentTitanId", this.getParentTitanId());
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
