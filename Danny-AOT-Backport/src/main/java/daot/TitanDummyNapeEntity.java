package daot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Blocks;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class TitanDummyNapeEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      TitanDummyNapeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private TitanDummyEntity cachedParent;
   private static final Map<Integer, TitanDummyNapeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;
   private static final double NAPE_HEIGHT_OFFSET = 6.9;
   private static final double NAPE_OFFSET_BACK = 0.4;

   public TitanDummyNapeEntity(EntityType<? extends MobEntity> entityType, World level) {
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

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
   }

   public void setParentDummy(TitanDummyEntity dummy) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(dummy.getUuid()));
      this.cachedParent = dummy;
   }

   public TitanDummyEntity getParentDummy() {
      if (this.cachedParent != null && !this.cachedParent.isRemoved()) {
         return this.cachedParent;
      } else {
         Optional<UUID> parentUUID = this.dataTracker.get(DATA_PARENT_UUID);
         if (parentUUID.isEmpty()) {
            return null;
         } else {
            if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
               if (serverLevel.getEntity(parentUUID.get()) instanceof TitanDummyEntity dummy) {
                  this.cachedParent = dummy;
                  return dummy;
               }
            } else {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(50.0))) {
                  if (entity instanceof TitanDummyEntity dummy && dummy.getUuid().equals(parentUUID.get())) {
                     this.cachedParent = dummy;
                     return dummy;
                  }
               }
            }

            return null;
         }
      }
   }

   public static TitanDummyNapeEntity getClientInstance(int parentEntityId) {
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

   @Override
   public void tick() {
      super.tick();
      if (this.getWorld().isClient()) {
         TitanDummyEntity parent = this.getParentDummy();
         if (parent != null) {
            clientInstances.put(parent.getId(), this);
         }

         if (!this.rendererPositionedThisTick && this.lastSyncTick >= 0L && this.getWorld().getTime() - this.lastSyncTick < 10L) {
            double cy = this.syncedY - this.getHeight() / 2.0;
            this.setPosition(this.syncedX, cy, this.syncedZ);
         }

         this.rendererPositionedThisTick = false;
      } else {
         TitanDummyEntity parentx = this.getParentDummy();
         if (parentx != null && !parentx.isRemoved()) {
            if (this.lastSyncTick >= 0L && this.getWorld().getTime() - this.lastSyncTick < 10L) {
               double cy = this.syncedY - this.getHeight() / 2.0;
               this.setPosition(this.syncedX, cy, this.syncedZ);
            } else {
               float yawRad = (float)Math.toRadians(parentx.bodyYaw);
               double offsetX = Math.sin(yawRad) * 0.4;
               double offsetZ = -Math.cos(yawRad) * 0.4;
               this.setPosition(parentx.getX() + offsetX, parentx.getY() + 6.9, parentx.getZ() + offsetZ);
            }
         } else {
            this.discard();
         }
      }
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      if (this.getWorld().isClient()) {
         return false;
      } else if (!(source.getAttacker() instanceof LivingEntity livingAttacker)) {
         return false;
      } else {
         ItemStack mainHand = livingAttacker.getMainHandStack();
         boolean isUsingBlade = mainHand.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
         if (!isUsingBlade) {
            if (livingAttacker instanceof PlayerEntity player) {
               player.sendMessage(Text.literal("Use a blade to cut the nape!"), true);
            }

            return false;
         } else {
            TitanDummyEntity parent = this.getParentDummy();
            if (parent != null) {
               parent.triggerNapeHit();
               if (livingAttacker instanceof PlayerEntity player) {
                  player.sendMessage(Text.literal("Nape hit!"), true);
               }

               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  serverLevel.spawnParticles(
                     new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.getDefaultState()),
                     this.getX(),
                     this.getY() + this.getHeight() / 2.0,
                     this.getZ(),
                     15,
                     0.3,
                     0.3,
                     0.3,
                     0.05
                  );
               }
            }

            return false;
         }
      }
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void remove(RemovalReason reason) {
      if (this.getWorld().isClient()) {
         clientInstances.values().remove(this);
      }

      super.remove(reason);
   }
}
