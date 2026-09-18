package daot;

import daot.network.EffectPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class WarhammerTitanNapeEntity extends MobEntity {
   private static final TrackedData<Optional<UUID>> DATA_PARENT_UUID = DataTracker.registerData(
      WarhammerTitanNapeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private WarhammerTitanEntity cachedParent;
   private static final float THUNDER_SPEAR_WIRE_DAMAGE = 50.0F;
   private static final float EXPLOSION_WIRE_DAMAGE = 25.0F;
   private static final float TITAN_ATTACK_WIRE_DAMAGE = 30.0F;
   private static final int WIRE_HIT_COOLDOWN = 20;
   private final Map<UUID, Integer> wireHitCooldownMap = new HashMap<>();
   private int lastWireDamageTick = -1;
   private static final Map<Integer, WarhammerTitanNapeEntity> clientInstances = new ConcurrentHashMap<>();
   private boolean rendererPositionedThisTick = false;
   private double syncedX;
   private double syncedY;
   private double syncedZ;
   private long lastSyncTick = -1L;
   private static final int SYNC_TIMEOUT_TICKS = 10;

   public static WarhammerTitanNapeEntity getClientInstance(int parentEntityId) {
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

   private void positionFromCableMidpoint(WarhammerTitanEntity parent) {
      Vec3d crystalPos = parent.getCrystalShellPosition();
      if (crystalPos != null) {
         Vec3d footPos = new Vec3d(parent.getX(), parent.getY(), parent.getZ());
         double midX = (footPos.x + crystalPos.x) / 2.0;
         double midY = (footPos.y + crystalPos.y) / 2.0;
         double midZ = (footPos.z + crystalPos.z) / 2.0;
         this.setPosition(midX, midY, midZ);
      } else {
         float yawRad = (float)Math.toRadians(parent.bodyYaw);
         double offsetX = Math.sin(yawRad) * 1.5;
         double offsetZ = -Math.cos(yawRad) * 1.5;
         this.setPosition(parent.getX() + offsetX, parent.getY() + 11.0, parent.getZ() + offsetZ);
      }
   }

   public WarhammerTitanNapeEntity(EntityType<? extends MobEntity> entityType, World level) {
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

   public void setParentTitan(WarhammerTitanEntity titan) {
      this.dataTracker.set(DATA_PARENT_UUID, Optional.of(titan.getUuid()));
      this.cachedParent = titan;
   }

   public WarhammerTitanEntity getParentTitan() {
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
         if (this.getWorld() instanceof ServerWorld serverLevel && serverLevel.getEntity(uuid) instanceof WarhammerTitanEntity titan && titan.isAlive()) {
            this.cachedParent = titan;
            return titan;
         } else {
            if (this.getWorld().isClient()) {
               for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(50.0))) {
                  if (entity instanceof WarhammerTitanEntity titan && titan.getUuid().equals(uuid)) {
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
      if (!this.getWorld().isClient() && !this.wireHitCooldownMap.isEmpty()) {
         Iterator<Entry<UUID, Integer>> it = this.wireHitCooldownMap.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
               it.remove();
            } else {
               entry.setValue(remaining);
            }
         }
      }

      WarhammerTitanEntity parent = this.getParentTitan();
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
               this.positionFromCableMidpoint(parent);
            }
         } else if (this.hasFreshSync()) {
            this.setPosition(this.syncedX, this.syncedY - this.getHeight() / 2.0, this.syncedZ);
         } else {
            this.positionFromCableMidpoint(parent);
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

   private boolean hasCrystalShell() {
      WarhammerTitanEntity parent = this.getParentTitan();
      return parent != null && parent.getCrystalShellPosition() != null;
   }

   @Override
   public boolean isFireImmune() {
      return true;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      if (this.getWorld().isClient()) {
         return false;
      } else if (this.age == this.lastWireDamageTick) {
         return false;
      } else if (!this.hasCrystalShell()) {
         return false;
      } else if (source.isIn(DamageTypeTags.IS_FIRE)) {
         return false;
      } else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
         WarhammerTitanEntity parent = this.getParentTitan();
         if (parent != null && !parent.isTransforming()) {
            if (this.getWorld() instanceof ServerWorld serverLevel) {
               BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
               serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.25, this.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
               EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

               for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                  ServerPlayNetworking.send(player, bloodPayload);
               }
            }

            float damage = source.getSource() instanceof ThunderSpearEntity ? 50.0F : 25.0F;
            parent.hurtFromNape(source, damage);
            Entity attacker = source.getAttacker();
            if (attacker != null) {
               parent.triggerHitReaction(attacker, false, true);
            }

            this.lastWireDamageTick = this.age;
            return true;
         } else {
            return false;
         }
      } else {
         Entity attacker = source.getAttacker();
         if (attacker instanceof AttackTitanEntity
            || attacker instanceof ArmoredTitanEntity
            || attacker instanceof ColossalTitanEntity
            || attacker instanceof BeastTitanEntity
            || attacker instanceof FemaleTitanEntity
            || attacker instanceof FritzTitanEntity
            || attacker instanceof WarhammerTitanEntity) {
            WarhammerTitanEntity parent = this.getParentTitan();
            if (parent == null || parent.isTransforming()) {
               return false;
            } else if (attacker == parent) {
               return false;
            } else {
               if (this.getWorld() instanceof ServerWorld serverLevel) {
                  BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
                  serverLevel.spawnParticles(bloodParticle, this.getX(), this.getY() + 0.25, this.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
                  EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

                  for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                     ServerPlayNetworking.send(player, bloodPayload);
                  }
               }

               parent.hurtFromNape(source, 30.0F);
               parent.triggerHitReaction(attacker, false, true);
               this.lastWireDamageTick = this.age;
               return true;
            }
         } else if (!(attacker instanceof LivingEntity livingAttacker)) {
            return false;
         } else {
            UUID attackerUUID = livingAttacker.getUuid();
            if (this.wireHitCooldownMap.containsKey(attackerUUID)) {
               return false;
            } else {
               WarhammerTitanEntity parent = this.getParentTitan();
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

               if (parent != null && HomelanderNapeBypass.isHomelanderAttacker(livingAttacker, this.getWorld())) {
                  SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
                  SoundEvent slash = slashSounds[this.random.nextInt(3)];
                  float vPitch = 0.9F + this.random.nextFloat() * 0.1F;
                  this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), slash, SoundCategory.PLAYERS, 8.0F, vPitch);
                  if (this.getWorld() instanceof ServerWorld sl) {
                     EffectPayload bloodPayload = new EffectPayload("blood", this.getX(), this.getY(), this.getZ(), 1.0F);

                     for (ServerPlayerEntity p : PlayerLookup.tracking(sl, new BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()))) {
                        ServerPlayNetworking.send(p, bloodPayload);
                     }
                  }

                  parent.damage(source, Float.MAX_VALUE);
                  if (livingAttacker instanceof PlayerEntity player) {
                     player.sendMessage(Text.literal("Critical hit! Warhammer Titan's wire shattered!"), true);
                  }

                  this.lastWireDamageTick = this.age;
                  return true;
               } else {
                  ItemStack mainHandItem = livingAttacker.getMainHandStack();
                  boolean isUsingBlade = mainHandItem.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHandItem) != BladeItem.BladeState.EMPTY;
                  if (isUsingBlade && parent != null) {
                     float pitch = 0.5F + this.random.nextFloat() * 0.1F;
                     this.getWorld()
                        .playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 8.0F, pitch);
                     if (this.getWorld() instanceof ServerWorld serverLevel) {
                        BlockStateParticleEffect sandParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SANDSTONE.getDefaultState());
                        serverLevel.spawnParticles(sandParticle, this.getX(), this.getY(), this.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
                     }

                     BladeItem.damageBladeByAmount(mainHandItem, 10, livingAttacker);
                     this.wireHitCooldownMap.put(attackerUUID, 20);
                     if (livingAttacker instanceof PlayerEntity player) {
                        player.sendMessage(Text.literal("This wire is too hard to cut through!").formatted(Formatting.RED), true);
                     }

                     this.lastWireDamageTick = this.age;
                     return true;
                  } else {
                     if (livingAttacker instanceof PlayerEntity player) {
                        player.sendMessage(Text.literal("Blades can't cut this wire! Use explosives!").formatted(Formatting.RED), true);
                     }

                     return false;
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean isInvulnerableTo(DamageSource damageSource) {
      if (damageSource.isIn(DamageTypeTags.IS_EXPLOSION) && damageSource.getSource() instanceof ThunderSpearEntity) {
         return false;
      } else if (damageSource.isIn(DamageTypeTags.IS_EXPLOSION)) {
         return false;
      } else {
         Entity attacker = damageSource.getAttacker();
         if (attacker instanceof AttackTitanEntity
            || attacker instanceof ArmoredTitanEntity
            || attacker instanceof ColossalTitanEntity
            || attacker instanceof BeastTitanEntity
            || attacker instanceof FemaleTitanEntity
            || attacker instanceof FritzTitanEntity
            || attacker instanceof WarhammerTitanEntity) {
            return false;
         } else if (!(attacker instanceof LivingEntity livingAttacker)) {
            return true;
         } else {
            ItemStack mainHandItem = livingAttacker.getMainHandStack();
            boolean hasLoadedBlade = mainHandItem.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHandItem) != BladeItem.BladeState.EMPTY;
            return !hasLoadedBlade;
         }
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
      return other instanceof WarhammerTitanEntity titan ? titan != this.getParentTitan() : super.collidesWith(other);
   }

   @Override
   public boolean canHit() {
      return this.hasCrystalShell();
   }
}
