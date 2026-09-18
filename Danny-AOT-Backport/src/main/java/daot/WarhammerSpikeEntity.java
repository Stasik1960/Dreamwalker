package daot;

import daot.mixin.FallingBlockEntityAccessor;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class WarhammerSpikeEntity extends Entity implements GeoEntity {
   private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Float> DATA_TILT_X = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_TILT_Z = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_SPIKE_HEIGHT = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> DATA_SPIKE_WIDTH = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Integer> DATA_SPAWN_DELAY = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_SPIKE_FIELD = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_IS_IMPALE = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_IMPALE_TARGET_ID = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> DATA_ROTATION = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Integer> DATA_CUSTOM_LIFETIME = DataTracker.registerData(WarhammerSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final int MAX_AGE = 320;
   public static final int GROW_TICKS = 12;
   public static final int SHRINK_START = 280;
   private static final int DAMAGE_INTERVAL = 10;
   private static final float PASSIVE_DAMAGE = 6.0F;
   private static final float SHIFTER_PASSIVE_DAMAGE = 12.0F;
   private static final int SOUND_INTERVAL = 50;
   public static final int IMPALE_MAX_AGE = 270;
   public static final int IMPALE_SHRINK_START = 230;
   public static final int IMPALE_GROW_TICKS = 35;
   private static final float EMERGE_VOLUME = 6.0F;
   private static final float RETRACT_VOLUME = 5.0F;
   private int totalAge = 0;
   private boolean playedEmergeSound = false;
   private boolean playedRetractSound = false;
   private UUID ownerUUID = null;
   private double lastFlingDist = -1.0;
   private double spikeDirX;
   private double spikeDirY;
   private double spikeDirZ;
   private boolean dirComputed = false;

   public WarhammerSpikeEntity(EntityType<?> entityType, World level) {
      super(entityType, level);
      this.noClip = true;
      this.setNoGravity(true);
   }

   @Override
   protected void initDataTracker() {
      this.dataTracker.startTracking(DATA_TILT_X, 0.0F);
      this.dataTracker.startTracking(DATA_TILT_Z, 0.0F);
      this.dataTracker.startTracking(DATA_SPIKE_HEIGHT, 5.0F);
      this.dataTracker.startTracking(DATA_SPIKE_WIDTH, 0.5F);
      this.dataTracker.startTracking(DATA_SPAWN_DELAY, 0);
      this.dataTracker.startTracking(DATA_IS_SPIKE_FIELD, false);
      this.dataTracker.startTracking(DATA_IS_IMPALE, false);
      this.dataTracker.startTracking(DATA_IMPALE_TARGET_ID, -1);
      this.dataTracker.startTracking(DATA_ROTATION, 0.0F);
      this.dataTracker.startTracking(DATA_CUSTOM_LIFETIME, 0);
   }

   public void setSpikeProperties(float tiltX, float tiltZ, float height, float width) {
      this.dataTracker.set(DATA_TILT_X, tiltX);
      this.dataTracker.set(DATA_TILT_Z, tiltZ);
      this.dataTracker.set(DATA_SPIKE_HEIGHT, height);
      this.dataTracker.set(DATA_SPIKE_WIDTH, width);
      this.calculateDimensions();
   }

   @Override
   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (data == DATA_SPIKE_HEIGHT || data == DATA_SPIKE_WIDTH) {
         this.calculateDimensions();
      }
   }

   public void setSpawnDelay(int delay) {
      this.dataTracker.set(DATA_SPAWN_DELAY, delay);
   }

   public void setOwnerUUID(UUID uuid) {
      this.ownerUUID = uuid;
   }

   public void setSpikeField(boolean spikeField) {
      this.dataTracker.set(DATA_IS_SPIKE_FIELD, spikeField);
   }

   public float getTiltX() {
      return this.dataTracker.get(DATA_TILT_X);
   }

   public float getTiltZ() {
      return this.dataTracker.get(DATA_TILT_Z);
   }

   public float getSpikeHeight() {
      return this.dataTracker.get(DATA_SPIKE_HEIGHT);
   }

   public float getSpikeWidth() {
      return this.dataTracker.get(DATA_SPIKE_WIDTH);
   }

   public int getSpawnDelay() {
      return this.dataTracker.get(DATA_SPAWN_DELAY);
   }

   public boolean isSpikeField() {
      return this.dataTracker.get(DATA_IS_SPIKE_FIELD);
   }

   public void setImpaleSpike(boolean impale) {
      this.dataTracker.set(DATA_IS_IMPALE, impale);
   }

   public boolean isImpaleSpike() {
      return this.dataTracker.get(DATA_IS_IMPALE);
   }

   public void setImpaleTargetId(int id) {
      this.dataTracker.set(DATA_IMPALE_TARGET_ID, id);
   }

   public int getImpaleTargetId() {
      return this.dataTracker.get(DATA_IMPALE_TARGET_ID);
   }

   public float getRotation() {
      return this.dataTracker.get(DATA_ROTATION);
   }

   public void setCustomLifetime(int ticks) {
      this.dataTracker.set(DATA_CUSTOM_LIFETIME, ticks);
   }

   public int getCustomLifetime() {
      return this.dataTracker.get(DATA_CUSTOM_LIFETIME);
   }

   public int getSpikeAge() {
      int delay = this.getSpawnDelay();
      return this.totalAge < delay ? -1 : this.totalAge - delay;
   }

   private void computeSpikeDirection() {
      if (!this.dirComputed) {
         this.dirComputed = true;
         double ax = Math.toRadians(this.getTiltX());
         double az = Math.toRadians(this.getTiltZ());
         double cosAx = Math.cos(ax);
         double sinAx = Math.sin(ax);
         double cosAz = Math.cos(az);
         double sinAz = Math.sin(az);
         this.spikeDirX = -cosAx * sinAz;
         this.spikeDirY = cosAx * cosAz;
         this.spikeDirZ = sinAx;
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.totalAge++;
      int spikeAge = this.getSpikeAge();
      boolean isImpale = this.isImpaleSpike();
      int customLife = this.getCustomLifetime();
      int maxAge = customLife > 0 ? customLife : (isImpale ? 270 : 320);
      int shrinkStart = customLife > 0 ? (int)(maxAge * 0.875) : (isImpale ? 230 : 280);
      int growTicks = isImpale ? 35 : 12;
      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         this.computeSpikeDirection();
         if (spikeAge == 0 && !this.playedEmergeSound) {
            this.playedEmergeSound = true;
            this.getWorld()
               .playSound(
                  null,
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  SoundEvents.BLOCK_STONE_BREAK,
                  SoundCategory.HOSTILE,
                  6.0F,
                  0.7F + this.random.nextFloat() * 0.4F
               );
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 6.0F, 0.5F);
            this.getWorld()
               .playSound(
                  null, this.getX(), this.getY(), this.getZ(), ModSounds.FLESH_IMPACT_4, SoundCategory.HOSTILE, 6.0F, 0.5F + this.random.nextFloat() * 0.2F
               );
            this.getWorld()
               .playSound(
                  null, this.getX(), this.getY(), this.getZ(), ModSounds.FLESH_IMPACT_5, SoundCategory.HOSTILE, 6.0F, 0.5F + this.random.nextFloat() * 0.2F
               );
            this.spawnEmergeParticles(serverLevel);
         }

         if (spikeAge >= 0 && spikeAge <= growTicks && DannysAot.isTitanGriefingEnabled(this.getWorld())) {
            this.flingBlocksDuringGrowth(serverLevel, spikeAge);
         }

         if (spikeAge == shrinkStart && !this.playedRetractSound) {
            this.playedRetractSound = true;
            this.getWorld()
               .playSound(
                  null,
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  SoundEvents.BLOCK_STONE_BREAK,
                  SoundCategory.HOSTILE,
                  5.0F,
                  0.9F + this.random.nextFloat() * 0.3F
               );
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 5.0F, 0.5F);
         }

         if (spikeAge > 0 && spikeAge < shrinkStart && (spikeAge + this.getId()) % 10 == 0) {
            this.damageAndSlowNearbyEntities(serverLevel);
         }

         if (isImpale && spikeAge >= 0) {
            if (spikeAge <= 35) {
               this.dataTracker.set(DATA_ROTATION, this.getRotation() + 0.5F);
            }

            int targetId = this.getImpaleTargetId();
            if (targetId >= 0 && this.getWorld().getEntityById(targetId) instanceof LivingEntity target) {
               if (spikeAge > shrinkStart) {
                  float shrinkT = (float)(spikeAge - shrinkStart) / (maxAge - shrinkStart);
                  if (!(shrinkT >= 0.7F)) {
                     float currentHeight = this.getSpikeHeight() * (1.0F - shrinkT);
                     double backX = this.getX() + Math.sin(Math.toRadians(target.getYaw())) * 2.5;
                     double backZ = this.getZ() - Math.cos(Math.toRadians(target.getYaw())) * 2.5;
                     double targetY = this.getY() + currentHeight - 19.0;
                     target.setPosition(backX, targetY, backZ);
                     target.setVelocity(0.0, 0.0, 0.0);
                     target.velocityModified = true;
                     target.fallDistance = 0.0F;
                  } else {
                     if (target instanceof AttackTitanEntity at && at.isImpaled()) {
                        at.setImpaled(false);
                     } else if (target instanceof ArmoredTitanEntity ar && ar.isImpaled()) {
                        ar.setImpaled(false);
                     } else if (target instanceof FemaleTitanEntity ft && ft.isImpaled()) {
                        ft.setImpaled(false);
                     } else if (target instanceof BeastTitanEntity bt && bt.isImpaled()) {
                        bt.setImpaled(false);
                     }

                     this.dataTracker.set(DATA_IMPALE_TARGET_ID, -1);
                     target.setVelocity(0.0, 0.1, 0.0);
                     target.velocityModified = true;
                  }
               } else {
                  float growProgress = Math.min(1.0F, spikeAge / 35.0F);
                  float hScale = (float)(1.0 - Math.exp(-4.0 * growProgress) * Math.cos(growProgress * Math.PI * 1.5));
                  hScale = Math.max(0.0F, Math.min(1.3F, hScale));
                  float currentHeight = this.getSpikeHeight() * hScale;
                  double backX = this.getX() + Math.sin(Math.toRadians(target.getYaw())) * 2.5;
                  double backZ = this.getZ() - Math.cos(Math.toRadians(target.getYaw())) * 2.5;
                  double targetY = this.getY() + currentHeight - 19.0;
                  target.setPosition(backX, targetY, backZ);
                  target.setVelocity(0.0, 0.0, 0.0);
                  target.velocityModified = true;
                  target.fallDistance = 0.0F;
               }

               if (this.getImpaleTargetId() >= 0) {
                  if (target instanceof AttackTitanEntity at && !at.isImpaled()) {
                     at.setImpaled(true);
                  } else if (target instanceof ArmoredTitanEntity ar && !ar.isImpaled()) {
                     ar.setImpaled(true);
                  } else if (target instanceof FemaleTitanEntity ft && !ft.isImpaled()) {
                     ft.setImpaled(true);
                  } else if (target instanceof BeastTitanEntity bt && !bt.isImpaled()) {
                     bt.setImpaled(true);
                  }
               }

               target.setVelocity(0.0, 0.0, 0.0);
               target.velocityModified = true;
               target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 255, false, false));
               if (target instanceof AttackTitanEntity at2) {
                  at2.setDismounting(false);
                  at2.setMoving(false);
                  at2.setSprinting(false);
                  at2.setTitanAttacking(false);
                  at2.setCrouching(false);
               } else if (target instanceof ArmoredTitanEntity ar) {
                  ar.setDismounting(false);
                  ar.setMoving(false);
                  ar.setSprinting(false);
                  ar.setTitanAttacking(false);
                  ar.setCrouching(false);
               } else if (target instanceof FemaleTitanEntity ft) {
                  ft.setDismounting(false);
                  ft.setMoving(false);
                  ft.setSprinting(false);
                  ft.setTitanAttacking(false);
                  ft.setCrouching(false);
               } else if (target instanceof ColossalTitanEntity ct) {
                  ct.setDismounting(false);
                  ct.setMoving(false);
                  ct.setTitanAttacking(false);
               } else if (target instanceof BeastTitanEntity bt) {
                  bt.setDismounting(false);
                  bt.setMoving(false);
                  bt.setSprinting(false);
                  bt.setTitanAttacking(false);
               } else if (target instanceof WarhammerTitanEntity wh2) {
                  wh2.setDismounting(false);
                  wh2.setMoving(false);
                  wh2.setSprinting(false);
                  wh2.setTitanAttacking(false);
                  wh2.setCrouching(false);
               }

               if (target.getFirstPassenger() instanceof PlayerEntity p) {
                  p.setInvisible(true);
                  p.setVelocity(0.0, 0.0, 0.0);
                  p.velocityModified = true;
               }

               if (spikeAge % 5 == 0) {
                  BlockStateParticleEffect blood = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
                  serverLevel.spawnParticles(blood, target.getX(), target.getY(), target.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
               }
            }

            if (spikeAge > 0 && spikeAge <= 35) {
               float mainRotation = (float)Math.toRadians(this.getRotation());
               float baseRadius = this.getSpikeWidth() / 2.0F;
               float topRadius = baseRadius * 0.06F;
               float totalHeight = this.getSpikeHeight();
               float heightFraction = spikeAge / 35.0F;
               heightFraction = Math.min(heightFraction, 0.9F);
               double sy = this.getY() + totalHeight * heightFraction;
               float spiralAngle = mainRotation + heightFraction * (float) (Math.PI * 8);
               float radiusAtHeight = baseRadius + (topRadius - baseRadius) * heightFraction;
               double sx = this.getX() + Math.cos(spiralAngle) * radiusAtHeight;
               double sz = this.getZ() + Math.sin(spiralAngle) * radiusAtHeight;
               WarhammerSpikeEntity miniSpike = new WarhammerSpikeEntity(DannysAot.WARHAMMER_SPIKE, serverLevel);
               miniSpike.setPosition(sx, sy, sz);
               float outwardAngle = 60.0F + this.random.nextFloat() * 20.0F;
               float tiltX = (float)(Math.sin(spiralAngle) * outwardAngle);
               float tiltZ = (float)(-Math.cos(spiralAngle) * outwardAngle);
               float miniHeight = 2.0F + (1.0F - heightFraction) * 2.0F;
               float miniWidth = 0.4F + (1.0F - heightFraction) * 0.3F;
               miniSpike.setSpikeProperties(tiltX, tiltZ, miniHeight, miniWidth);
               miniSpike.setSpawnDelay(0);
               miniSpike.setOwnerUUID(this.ownerUUID);
               miniSpike.setCustomLifetime(270 - spikeAge);
               serverLevel.spawnEntity(miniSpike);
            }
         }

         if (spikeAge >= maxAge) {
            if (isImpale) {
               int targetIdx = this.getImpaleTargetId();
               if (targetIdx >= 0) {
                  Entity targetEntity = this.getWorld().getEntityById(targetIdx);
                  if (targetEntity instanceof AttackTitanEntity at) {
                     at.setImpaled(false);
                  } else if (targetEntity instanceof ArmoredTitanEntity ar) {
                     ar.setImpaled(false);
                  } else if (targetEntity instanceof FemaleTitanEntity ft) {
                     ft.setImpaled(false);
                  } else if (targetEntity instanceof BeastTitanEntity bt) {
                     bt.setImpaled(false);
                  }
               }
            }

            this.discard();
         }
      }
   }

   private void spawnEmergeParticles(ServerWorld serverLevel) {
      BlockPos groundPos = new BlockPos((int)Math.floor(this.getX()), (int)Math.floor(this.getY()) - 1, (int)Math.floor(this.getZ()));
      BlockState groundState = this.getWorld().getBlockState(groundPos);
      if (groundState.isAir()) {
         groundState = this.getWorld().getBlockState(groundPos.down());
      }

      if (!groundState.isAir()) {
         serverLevel.spawnParticles(
            new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState), this.getX(), this.getY(), this.getZ(), 40, 0.8, 0.5, 0.8, 0.15
         );
      }

      serverLevel.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.5, 0.3, 0.5, 0.02);
   }

   private void flingBlocksDuringGrowth(ServerWorld serverLevel, int spikeAge) {
      int gTicks = this.isImpaleSpike() ? 35 : 12;
      float growthProgress = Math.min(1.0F, (float)spikeAge / gTicks);
      float heightScale = (float)(1.0 - Math.exp(-4.0 * growthProgress) * Math.cos(growthProgress * Math.PI * 1.5));
      heightScale = Math.max(0.0F, Math.min(1.3F, heightScale));
      double currentTipDist = this.getSpikeHeight() * heightScale;
      if (this.lastFlingDist < 0.0) {
         this.lastFlingDist = 0.0;
      }

      int destroyRadius = Math.max(1, (int)Math.ceil(this.getSpikeWidth() / 2.0F) + 1);
      boolean isImpale = this.isImpaleSpike();

      for (double step = this.lastFlingDist + 1.0; step <= currentTipDist; step++) {
         double wx = this.getX() + this.spikeDirX * step;
         double wy = this.getY() + this.spikeDirY * step;
         double wz = this.getZ() + this.spikeDirZ * step;
         int bx = (int)Math.floor(wx);
         int by = (int)Math.floor(wy);
         int bz = (int)Math.floor(wz);

         for (int dx = -destroyRadius; dx <= destroyRadius; dx++) {
            for (int dz = -destroyRadius; dz <= destroyRadius; dz++) {
               if (dx * dx + dz * dz <= destroyRadius * destroyRadius) {
                  BlockPos pos = new BlockPos(bx + dx, by, bz + dz);
                  BlockState state = this.getWorld().getBlockState(pos);
                  if (!state.isAir()
                     && !(state.getHardness(this.getWorld(), pos) < 0.0F)
                     && !state.isIn(BlockTags.WITHER_IMMUNE)
                     && !(state.getBlock() instanceof FluidBlock)) {
                     double dirX = dx == 0 ? (this.random.nextDouble() - 0.5) * 2.0 : dx;
                     double dirZ = dz == 0 ? (this.random.nextDouble() - 0.5) * 2.0 : dz;
                     if (isImpale && this.random.nextBoolean()) {
                        this.getWorld().removeBlock(pos, false);
                     } else {
                        double flingForce = isImpale ? 1.5 : 0.5;
                        this.flingBlock(serverLevel, pos, state, dirX * flingForce, dirZ * flingForce);
                     }
                  }
               }
            }
         }
      }

      this.lastFlingDist = currentTipDist;
   }

   private void flingBlock(ServerWorld serverLevel, BlockPos pos, BlockState blockState, double dirX, double dirZ) {
      this.getWorld().removeBlock(pos, false);
      double velX = dirX * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.3;
      double velY = 0.5 + this.random.nextDouble() * 0.8;
      double velZ = dirZ * (1.0 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.3;
      FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
      ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
      fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fallingBlock.setFallingBlockPos(pos);
      fallingBlock.setVelocity(velX, velY, velZ);
      fallingBlock.timeFalling = 1;
      fallingBlock.dropItem = false;
      serverLevel.spawnEntity(fallingBlock);
   }

   private boolean isOwnerOrOwnedWarhammer(Entity target) {
      if (this.ownerUUID == null) {
         return false;
      } else if (target.getUuid().equals(this.ownerUUID)) {
         return true;
      } else {
         return target instanceof WarhammerTitanEntity whx && whx.getShifterUUID() != null && whx.getShifterUUID().equals(this.ownerUUID)
            ? true
            : target.getVehicle() instanceof WarhammerTitanEntity wh && wh.getShifterUUID() != null && wh.getShifterUUID().equals(this.ownerUUID);
      }
   }

   private static boolean isShifterTitan(LivingEntity target) {
      return target instanceof AttackTitanEntity
         || target instanceof FemaleTitanEntity
         || target instanceof ArmoredTitanEntity
         || target instanceof ColossalTitanEntity
         || target instanceof BeastTitanEntity
         || target instanceof WarhammerTitanEntity;
   }

   private static boolean isPureTitan(LivingEntity target) {
      return target instanceof TitanEntity || target instanceof SmallTitanEntity || target instanceof SmallTitan2Entity || target instanceof FritzTitanEntity;
   }

   private void damageAndSlowNearbyEntities(ServerWorld serverLevel) {
      float halfW = this.getSpikeWidth() / 2.0F;
      float h = this.getSpikeHeight();
      boolean isSF = this.isSpikeField();
      int spikeAge = this.getSpikeAge();
      boolean isImpale = this.isImpaleSpike();
      int growTicks = isImpale ? 35 : 12;
      int customLife = this.getCustomLifetime();
      int maxAge = customLife > 0 ? customLife : (isImpale ? 270 : 320);
      int shrinkStart = customLife > 0 ? (int)(maxAge * 0.875) : (isImpale ? 230 : 280);
      if (spikeAge < growTicks) {
         h *= Math.min(1.0F, (float)spikeAge / growTicks);
      } else if (spikeAge > shrinkStart) {
         float t = (float)(spikeAge - shrinkStart) / (maxAge - shrinkStart);
         h *= Math.max(0.0F, 1.0F - t);
      }

      Box spikeBox = new Box(this.getX() - halfW, this.getY() - 1.0, this.getZ() - halfW, this.getX() + halfW, this.getY() + h, this.getZ() + halfW);

      for (LivingEntity target : this.getWorld().getNonSpectatingEntities(LivingEntity.class, spikeBox)) {
         if (!this.isOwnerOrOwnedWarhammer(target)
            && !(target instanceof AttackTitanEntity atx && atx.isImpaled())
            && !(target.getVehicle() instanceof AttackTitanEntity at && at.isImpaled())) {
            boolean isShifter = isShifterTitan(target);
            boolean isPure = isPureTitan(target);
            if (isSF) {
               boolean isTargetMoving = false;
               if (target instanceof AttackTitanEntity at3) {
                  isTargetMoving = at3.isMoving();
               } else if (target instanceof ArmoredTitanEntity ar2) {
                  isTargetMoving = ar2.isMoving();
               } else if (target instanceof FemaleTitanEntity ft2) {
                  isTargetMoving = ft2.isMoving();
               } else if (target instanceof ColossalTitanEntity ct2) {
                  isTargetMoving = ct2.isMoving();
               } else if (target instanceof BeastTitanEntity bt2) {
                  isTargetMoving = bt2.isMoving();
               } else if (target instanceof WarhammerTitanEntity wh3) {
                  isTargetMoving = wh3.isMoving();
               } else {
                  isTargetMoving = target.getVelocity().horizontalLengthSquared() > 0.001;
               }

               if (!isTargetMoving) {
                  continue;
               }
            }

            float damage = isShifter ? 12.0F : 6.0F;
            target.damage(this.getDamageSources().generic(), damage);
            int slowTicks;
            int slowLevel;
            if (isSF) {
               slowTicks = 20;
               slowLevel = 9;
            } else {
               slowTicks = isShifter ? 60 : 40;
               slowLevel = 3;
            }

            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, slowLevel, false, false));
            if (!isSF && (target instanceof PlayerEntity || isPure)) {
               target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 2, 255, false, false));
            }

            BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
            serverLevel.spawnParticles(bloodParticle, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            if (!this.isImpaleSpike() && spikeAge > 0 && (spikeAge + this.getId()) % 50 < 10) {
               SoundEvent[] sounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_1, ModSounds.FLESH_IMPACT_2, ModSounds.FLESH_IMPACT_3};
               SoundEvent sound = sounds[this.random.nextInt(sounds.length)];
               float pitch = 1.1F + this.random.nextFloat() * 0.3F;
               this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundCategory.HOSTILE, 4.0F, pitch);
            }
         }
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }

   @Override
   public boolean shouldRender(double distance) {
      return this.isImpaleSpike() ? distance < 262144.0 : distance < 16384.0;
   }

   @Override
   public Box getVisibilityBoundingBox() {
      if (this.isImpaleSpike()) {
         return new Box(this.getX() - 50.0, this.getY() - 10.0, this.getZ() - 50.0, this.getX() + 50.0, this.getY() + 120.0, this.getZ() + 50.0);
      } else {
         float halfW = this.getSpikeWidth() / 2.0F + 1.0F;
         float h = this.getSpikeHeight() + 2.0F;
         return new Box(this.getX() - halfW, this.getY() - 1.0, this.getZ() - halfW, this.getX() + halfW, this.getY() + h, this.getZ() + halfW);
      }
   }

   @Override
   public EntityDimensions getDimensions(EntityPose pose) {
      float w = this.getSpikeWidth();
      float h = this.getSpikeHeight();
      return w > 0.01F && h > 0.01F ? EntityDimensions.fixed(w, h) : super.getDimensions(pose);
   }

   @Override
   public boolean isCollidable() {
      return !this.isSpikeField();
   }

   @Override
   protected void readCustomDataFromNbt(NbtCompound nbt) {
      this.totalAge = nbt.getInt("SpikeAge");
      this.playedEmergeSound = nbt.getBoolean("PlayedEmerge");
      this.playedRetractSound = nbt.getBoolean("PlayedRetract");
      if (nbt.contains("OwnerUUID")) {
         this.ownerUUID = nbt.getUuid("OwnerUUID");
      }
   }

   @Override
   protected void writeCustomDataToNbt(NbtCompound nbt) {
      nbt.putInt("SpikeAge", this.totalAge);
      nbt.putBoolean("PlayedEmerge", this.playedEmergeSound);
      nbt.putBoolean("PlayedRetract", this.playedRetractSound);
      if (this.ownerUUID != null) {
         nbt.putUuid("OwnerUUID", this.ownerUUID);
      }
   }

   @Override
   public boolean canHit() {
      return true;
   }

   @Override
   public boolean isPushable() {
      return false;
   }
}
