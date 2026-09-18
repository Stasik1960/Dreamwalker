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
import net.minecraft.item.ItemStack;
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

public class SmallTitan2NapeEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      SmallTitan2NapeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private SmallTitan2Entity cachedParent;
   private static final double NAPE_OFFSET_BACK = 0.3;
   private static final double NAPE_HEIGHT_OFFSET = 4.5;
   private static final Map<Integer, SmallTitan2NapeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static SmallTitan2NapeEntity getClientInstance(int parentEntityId) {
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

   private void positionFromStaticOffset(SmallTitan2Entity parent, double scale) {
      float yawRad = (float)Math.toRadians(parent.bodyYaw);
      double scaledBackOffset = 0.3 * scale;
      double offsetX = Math.sin(yawRad) * scaledBackOffset;
      double offsetZ = -Math.cos(yawRad) * scaledBackOffset;
      this.setPosition(parent.getX() + offsetX, parent.getY() + 4.5 * scale, parent.getZ() + offsetZ);
   }

   public SmallTitan2NapeEntity(EntityType<? extends MobEntity> entityType, World level) {
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

   public void setParentTitan(SmallTitan2Entity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   public SmallTitan2Entity getParentTitan() {
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
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof SmallTitan2Entity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(20.0))) {
                  if (entity instanceof SmallTitan2Entity titan && titan.getUuid().equals(uuid)) {
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
      SmallTitan2Entity parent = this.getParentTitan();
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
         Entity attacker = source.getAttacker();
         if (!(attacker instanceof AttackTitanEntity)
            && !(attacker instanceof ArmoredTitanEntity)
            && !(attacker instanceof ColossalTitanEntity)
            && !(attacker instanceof BeastTitanEntity)
            && !(attacker instanceof WarhammerTitanEntity)) {
            SmallTitan2Entity unfairParent = this.getParentTitan();
            if (unfairParent != null
               && DannysAot.isUnfairPureTitans(this.getWorld())
               && unfairParent.getEatingTarget() != null
               && unfairParent.getEatingTarget() == attacker) {
               return false;
            } else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
               SmallTitan2Entity parent = this.getParentTitan();
               if (parent == null) {
                  return false;
               } else {
                  if (this.getWorld() instanceof ServerWorld serverLevel) {
                     BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
                     serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.25, this.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
                     serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.5, this.getZ(), 30, 0.15, 0.15, 0.15, 0.15);
                     EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

                     for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                        ServerPlayNetworking.send(player, bloodPayload);
                     }
                  }

                  parent.damage(source, Float.MAX_VALUE);
                  return true;
               }
            } else if (!(attacker instanceof LivingEntity livingAttacker)) {
               return false;
            } else {
               SmallTitan2Entity parent = this.getParentTitan();
               if (parent != null && parent.hasPassenger(attacker)) {
                  return false;
               } else {
                  if (parent != null) {
                     float yawRad2 = (float)Math.toRadians(parent.bodyYaw);
                     double forwardX = -Math.sin(yawRad2);
                     double forwardZ = Math.cos(yawRad2);
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
                  boolean isMeleeRange = distanceToAttacker <= 8.0;
                  if ((isUsingBlade || isHomelander) && isMeleeRange && parent != null) {
                     if (this.getWorld() instanceof ServerWorld serverLevel) {
                        BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
                        serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.25, this.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
                        serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.5, this.getZ(), 30, 0.15, 0.15, 0.15, 0.15);
                        EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

                        for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                           ServerPlayNetworking.send(player, bloodPayload);
                        }
                     }

                     SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
                     SoundEvent slash = slashSounds[this.random.nextInt(3)];
                     float pitch = 0.9F + this.random.nextFloat() * 0.1F;
                     this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), slash, SoundCategory.PLAYERS, 8.0F, pitch);
                     parent.damage(source, Float.MAX_VALUE);
                     if (livingAttacker instanceof PlayerEntity player) {
                        player.sendMessage(Text.literal("Critical hit! Small Titan 2's nape destroyed!"), true);
                     }

                     return true;
                  } else {
                     if (livingAttacker instanceof PlayerEntity player) {
                        player.sendMessage(Text.literal("Use an ultrahard steel blade to cut the nape!"), true);
                     }

                     return false;
                  }
               }
            }
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean isInvulnerableTo(DamageSource damageSource) {
      if (damageSource.isIn(DamageTypeTags.IS_EXPLOSION)) {
         return false;
      } else if (!(damageSource.getAttacker() instanceof LivingEntity attacker)) {
         return true;
      } else {
         ItemStack mainHandItem = attacker.getMainHandStack();
         boolean hasLoadedBlade = mainHandItem.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHandItem) != BladeItem.BladeState.EMPTY;
         return !hasLoadedBlade;
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
      return other instanceof SmallTitan2Entity titan ? titan != this.getParentTitan() : super.collidesWith(other);
   }

   @Override
   public boolean canHit() {
      return true;
   }
}
