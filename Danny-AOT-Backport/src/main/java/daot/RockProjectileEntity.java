package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.APGFireShakePayload;
import daot.network.EffectPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RockProjectileEntity extends Entity implements GeoEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final RawAnimation SPAWN_ANIM = RawAnimation.begin().thenPlay("spawn");
   private static final RawAnimation AIR_ANIM = RawAnimation.begin().thenLoop("air");
   private static final RawAnimation DESPAWN_ANIM = RawAnimation.begin().thenPlayAndHold("despawn");
   private static final int MAX_LIFETIME_TICKS = 100;
   private static final float NAPE_EYE_DAMAGE_MULTIPLIER = 4.0F;
   private static final int BLOCK_DESTROY_RADIUS = 2;
   private static final double LONG_RANGE_PARTICLE_RADIUS = 1000.0;
   private double customGravity = 0.06;
   private boolean hasHit = false;
   private int despawnTicks = 0;
   private static final int DESPAWN_ANIM_TICKS = 5;
   private final Set<Long> forcedChunks = new HashSet<>();
   private UUID ownerUUID = null;
   private final Set<Integer> hitEntityIds = new HashSet<>();
   private boolean fireMode = false;
   private static final int FIRE_BURN_TICKS = 100;
   private static final float FIRE_BONUS_DAMAGE = 4.0F;
   private static final int FIRE_IGNITE_RADIUS = 4;
   private static final TrackedData<Boolean> DATA_BIG_MODE = DataTracker.registerData(RockProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> DATA_SCALE = DataTracker.registerData(RockProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final float BIG_SCALE = 3.0F;
   private static final int BIG_BLOCK_DESTROY_RADIUS = 12;
   private static final float BIG_DIRECT_DAMAGE_BONUS = 60.0F;
   private static final float BIG_AOE_DAMAGE = 50.0F;
   private static final int BIG_PATH_CARVE_RADIUS = 6;
   private static final float BIG_CARVE_HARDNESS_THRESHOLD = 1.0F;

   private static float getRockDamage() {
      return (float)ModConfig.get().beastRockDamage;
   }

   public void setOwnerUUID(UUID uuid) {
      this.ownerUUID = uuid;
   }

   public RockProjectileEntity(EntityType<?> entityType, World level) {
      super(entityType, level);
      this.noClip = false;
   }

   @Override
   protected void initDataTracker() {
      this.dataTracker.startTracking(DATA_BIG_MODE, false);
      this.dataTracker.startTracking(DATA_SCALE, 0.0F);
   }

   @Override
   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (data.equals(DATA_BIG_MODE) || data.equals(DATA_SCALE)) {
         this.calculateDimensions();
      }
   }

   @Override
   protected void readCustomDataFromNbt(NbtCompound nbt) {
   }

   @Override
   protected void writeCustomDataToNbt(NbtCompound nbt) {
   }

   public void setCustomGravity(double gravity) {
      this.customGravity = gravity;
   }

   public void setFireMode(boolean fire) {
      this.fireMode = fire;
   }

   public boolean isFireMode() {
      return this.fireMode;
   }

   public void setBigMode(boolean big) {
      this.dataTracker.set(DATA_BIG_MODE, big);
      this.calculateDimensions();
   }

   public void setRockScale(float scale) {
      this.dataTracker.set(DATA_SCALE, scale);
      this.calculateDimensions();
   }

   public boolean isBigMode() {
      return this.dataTracker.get(DATA_BIG_MODE);
   }

   public boolean isScaled() {
      return this.dataTracker.get(DATA_SCALE) > 0.0F;
   }

   public boolean isVisuallyScaled() {
      return this.isBigMode() || this.isScaled();
   }

   public float getRockScale() {
      float s = this.dataTracker.get(DATA_SCALE);
      if (s > 0.0F) {
         return s;
      } else {
         return this.isBigMode() ? 3.0F : 1.0F;
      }
   }

   private boolean isOwnEntity(Entity e) {
      if (this.ownerUUID == null) {
         return false;
      } else if (this.ownerUUID.equals(e.getUuid())) {
         return true;
      } else {
         ColossalTitanEntity colossal = null;
         if (e instanceof ColossalTitanEntity ct) {
            colossal = ct;
         } else if (e instanceof ColossalTitanNapeEntity n) {
            colossal = n.getParentTitan();
         } else if (e instanceof ColossalTitanEyeEntity ey) {
            colossal = ey.getParentTitan();
         } else if (e instanceof ColossalTitanHandEntity h) {
            colossal = h.getParentTitan();
         }

         if (colossal != null && this.ownerUUID.equals(colossal.getShifterUUID())) {
            return true;
         } else {
            BeastTitanEntity beast = null;
            if (e instanceof BeastTitanEntity bt) {
               beast = bt;
            } else if (e instanceof BeastTitanNapeEntity n) {
               beast = n.getParentTitan();
            } else if (e instanceof BeastTitanEyeEntity ey) {
               beast = ey.getParentTitan();
            }

            return beast != null && this.ownerUUID.equals(beast.getShifterUUID());
         }
      }
   }

   @Override
   public EntityDimensions getDimensions(EntityPose pose) {
      EntityDimensions base = this.getType().getDimensions();
      return this.isVisuallyScaled() ? base.scaled(this.getRockScale()) : base;
   }

   private <T extends ParticleEffect> void sendLongRangeParticles(
      ServerWorld level, T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed
   ) {
      for (ServerPlayerEntity player : level.getPlayers()) {
         if (player.squaredDistanceTo(x, y, z) < 1000000.0) {
            level.spawnParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
         }
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.age > 100) {
         if (this.isBigMode() && !this.hasHit) {
            this.detonateAtCurrentPosition();
         } else {
            this.discard();
         }
      } else if (this.hasHit) {
         this.despawnTicks++;
         if (this.despawnTicks >= 5) {
            this.discard();
         }
      } else {
         this.noClip = true;
         Vec3d vel = this.getVelocity();
         this.setVelocity(vel.x, vel.y - this.customGravity, vel.z);
         Vec3d movement = this.getVelocity();
         this.move(MovementType.SELF, movement);
         if (!this.hasHit && !this.getWorld().isClient() && this.getWorld() instanceof ServerWorld forceLevel) {
            ChunkPos cp = new ChunkPos(this.getBlockPos());
            forceLevel.setChunkForced(cp.x, cp.z, true);
            this.forcedChunks.add(cp.toLong());
         }

         if (!this.hasHit && this.age > 3) {
            if (this.age % 8 == 0) {
               this.getWorld()
                  .playSound(
                     null,
                     this.getX(),
                     this.getY(),
                     this.getZ(),
                     SoundEvents.ENTITY_ARROW_SHOOT,
                     SoundCategory.HOSTILE,
                     2.0F,
                     0.2F + this.random.nextFloat() * 0.2F
                  );
            }

            if (this.getWorld() instanceof ServerWorld serverLevel) {
               this.sendLongRangeParticles(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
               this.sendLongRangeParticles(serverLevel, ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.15, 0.15, 0.15, 0.02);
               if (this.age % 3 == 0) {
                  this.sendLongRangeParticles(serverLevel, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.005);
               }

               if (this.fireMode) {
                  this.sendLongRangeParticles(serverLevel, ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
                  this.sendLongRangeParticles(serverLevel, ParticleTypes.SMALL_FLAME, this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
                  this.sendLongRangeParticles(serverLevel, ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                  if (this.age % 3 == 0) {
                     this.sendLongRangeParticles(serverLevel, ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
                  }
               }
            }
         }

         if (!this.getWorld().isClient() && this.age > 3) {
            if (this.isBigMode()) {
               this.carveFlightPath();
            }

            Vec3d pos = this.getPos();
            Vec3d nextPos = pos.add(movement);
            BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(pos, nextPos, ShapeType.COLLIDER, FluidHandling.NONE, this));
            if (blockHit.getType() == Type.BLOCK) {
               if (!this.isBigMode()) {
                  this.onBlockHit(blockHit);
                  return;
               }

               BlockState hitState = this.getWorld().getBlockState(blockHit.getBlockPos());
               float hardness = hitState.getHardness(this.getWorld(), blockHit.getBlockPos());
               boolean unbreakable = hitState.isIn(BlockTags.WITHER_IMMUNE) || hardness < 0.0F;
               boolean tooHard = hardness >= 1.0F;
               if (unbreakable || tooHard) {
                  this.onBlockHit(blockHit);
                  return;
               }
            }

            double hitInflate = 4.0 * this.getBoundingBox().getXLength() + 4.5;
            Box searchBox = this.getBoundingBox().stretch(movement).expand(hitInflate);

            for (Entity hit : this.getWorld().getOtherEntities(this, searchBox, e -> {
               if (e instanceof RockProjectileEntity) {
                  return false;
               } else if (this.isOwnEntity(e)) {
                  return false;
               } else {
                  return this.hitEntityIds.contains(e.getId()) ? false : e instanceof LivingEntity && e.isAlive();
               }
            })) {
               this.onEntityHit(hit);
            }
         }
      }
   }

   private void onBlockHit(BlockHitResult result) {
      if (!this.getWorld().isClient() && !this.hasHit) {
         this.hasHit = true;
         BlockPos hitPos = result.getBlockPos();
         if (DannysAot.isTitanGriefingEnabled(this.getWorld()) && this.getWorld() instanceof ServerWorld serverLevel) {
            int destroyRadius = this.isBigMode() ? 12 : (this.isScaled() ? Math.max(5, Math.min(10, Math.round(this.getRockScale() * 1.0F))) : 2);
            float flingChance = this.isScaled() ? 0.3F : 0.6F;
            int maxFling = this.isScaled() ? 60 : Integer.MAX_VALUE;
            int flung = 0;
            Vec3d travel = this.getVelocity();
            double tlen = travel.length();
            boolean hasTravel = tlen > 0.05;
            double tvx = hasTravel ? travel.x / tlen : 0.0;
            double tvy = hasTravel ? travel.y / tlen : 0.0;
            double tvz = hasTravel ? travel.z / tlen : 0.0;
            double penLimit = destroyRadius * (this.isScaled() ? 0.8 : 0.6);
            double rimStart = destroyRadius - 2.0;

            for (int dx = -destroyRadius; dx <= destroyRadius; dx++) {
               for (int dy = -destroyRadius; dy <= destroyRadius; dy++) {
                  for (int dz = -destroyRadius; dz <= destroyRadius; dz++) {
                     double d2 = dx * dx + dy * dy + dz * dz;
                     if (!(d2 > destroyRadius * destroyRadius) && (!hasTravel || !(dx * tvx + dy * tvy + dz * tvz > penLimit))) {
                        double d = Math.sqrt(d2);
                        if ((!(d > rimStart) || !(this.random.nextFloat() < (d - rimStart) / 2.0)) && !(this.random.nextFloat() < 0.1F)) {
                           BlockPos pos = hitPos.add(dx, dy, dz);
                           BlockState blockState = this.getWorld().getBlockState(pos);
                           if (!blockState.isAir()
                              && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                              && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                              && !(blockState.getBlock() instanceof FluidBlock)) {
                              if (flung < maxFling && this.random.nextFloat() < flingChance) {
                                 flung++;
                                 this.getWorld().removeBlock(pos, false);
                                 double dist = Math.sqrt(dx * dx + dz * dz);
                                 if (dist < 0.1) {
                                    dist = 1.0;
                                 }

                                 double velX = dx / dist * (0.5 + this.random.nextDouble() * 0.5);
                                 double velY = 0.4 + this.random.nextDouble() * 0.6;
                                 double velZ = dz / dist * (0.5 + this.random.nextDouble() * 0.5);
                                 FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                                 ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                                 fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                                 fallingBlock.setFallingBlockPos(pos);
                                 fallingBlock.setVelocity(velX, velY, velZ);
                                 fallingBlock.timeFalling = 1;
                                 fallingBlock.dropItem = false;
                                 fallingBlock.setHurtEntities(5.0F, 20);
                                 serverLevel.spawnEntity(fallingBlock);
                              } else {
                                 this.sendLongRangeParticles(
                                    serverLevel,
                                    new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5,
                                    8,
                                    0.3,
                                    0.3,
                                    0.3,
                                    0.1
                                 );
                                 this.getWorld().removeBlock(pos, false);
                              }
                           }
                        }
                     }
                  }
               }
            }

            List<LivingEntity> nearbyEntities = serverLevel.getNonSpectatingEntities(LivingEntity.class, new Box(hitPos).expand(destroyRadius + 2));
            float aoeDamage = this.isBigMode() ? 50.0F : (this.isScaled() ? 10.0F + this.getRockScale() * 4.0F : 10.0F);
            double knockH = this.isVisuallyScaled() ? 3.0 : 1.5;
            double knockV = this.isVisuallyScaled() ? 1.0 : 0.5;

            for (LivingEntity entity : nearbyEntities) {
               if (!this.isOwnEntity(entity)) {
                  entity.damage(this.getDamageSources().mobAttack(entity), aoeDamage);
                  if (this.fireMode) {
                     entity.setFireTicks(100);
                  }

                  Vec3d knockDir = entity.getPos().subtract(Vec3d.ofCenter(hitPos)).normalize();
                  entity.setVelocity(entity.getVelocity().add(knockDir.x * knockH, knockV, knockDir.z * knockH));
                  if (entity instanceof PlayerEntity p) {
                     p.velocityModified = true;
                  } else {
                     entity.velocityModified = true;
                  }
               }
            }
         }

         if (this.getWorld() instanceof ServerWorld serverLevel) {
            BlockState hitBlock = this.getWorld().getBlockState(hitPos);
            int blockBurst = this.isVisuallyScaled() ? 120 : 30;
            int smokeBurst = this.isVisuallyScaled() ? 30 : 8;
            double burstSpread = this.isVisuallyScaled() ? 2.5 : 0.5;
            this.sendLongRangeParticles(
               serverLevel,
               new BlockStateParticleEffect(ParticleTypes.BLOCK, hitBlock.isAir() ? Blocks.STONE.getDefaultState() : hitBlock),
               this.getX(),
               this.getY(),
               this.getZ(),
               blockBurst,
               burstSpread,
               burstSpread,
               burstSpread,
               0.15
            );
            this.sendLongRangeParticles(
               serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY(), this.getZ(), smokeBurst, burstSpread, burstSpread, burstSpread, 0.05
            );
            if (this.fireMode) {
               this.sendLongRangeParticles(serverLevel, ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 60, 1.5, 0.8, 1.5, 0.15);
               this.sendLongRangeParticles(serverLevel, ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 10, 1.0, 0.5, 1.0, 0.0);
               this.sendLongRangeParticles(serverLevel, ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 30, 1.2, 0.6, 1.2, 0.4);
               this.igniteAreaAround(serverLevel, hitPos);
            }
         }

         SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_4, ModSounds.FLESH_IMPACT_5, ModSounds.FLESH_IMPACT_6, ModSounds.FLESH_IMPACT_7};
         float impactVol = this.isBigMode() ? 12.0F : 6.0F;
         float explodePitch = this.isBigMode() ? 0.4F : 0.8F;
         float explodeVol = this.isBigMode() ? 8.0F : 3.0F;
         this.getWorld()
            .playSound(
               null,
               this.getX(),
               this.getY(),
               this.getZ(),
               impactSounds[this.random.nextInt(impactSounds.length)],
               SoundCategory.HOSTILE,
               impactVol,
               0.6F + this.random.nextFloat() * 0.2F
            );
         this.getWorld()
            .playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, explodeVol, explodePitch);
         if (this.isBigMode() && this.getWorld() instanceof ServerWorld serverLevelx) {
            double shakeRadius = 80.0;

            for (ServerPlayerEntity p : serverLevelx.getPlayers()) {
               double d = p.squaredDistanceTo(this.getX(), this.getY(), this.getZ());
               if (!(d > shakeRadius * shakeRadius)) {
                  float dist = (float)Math.sqrt(d);
                  float fall = 1.0F - dist / (float)shakeRadius;
                  float intensity = 1.6F * fall * fall;
                  if (!(intensity < 0.05F)) {
                     ServerPlayNetworking.send(p, new APGFireShakePayload(intensity));
                  }
               }
            }
         }

         this.setVelocity(Vec3d.ZERO);
      }
   }

   private void onEntityHit(Entity hit) {
      if (!this.getWorld().isClient() && !this.hasHit) {
         this.hitEntityIds.add(hit.getId());
         if (hit instanceof LivingEntity living) {
            if (living instanceof TitanNapeEntity nape) {
               TitanEntity parent = nape.getParentTitan();
               if (parent != null) {
                  parent.damage(this.getDamageSources().generic(), Float.MAX_VALUE);
               }
            } else if (living instanceof SmallTitanNapeEntity napex) {
               SmallTitanEntity parent = napex.getParentTitan();
               if (parent != null) {
                  parent.damage(this.getDamageSources().generic(), Float.MAX_VALUE);
               }
            } else if (living instanceof SmallTitan2NapeEntity napexx) {
               SmallTitan2Entity parent = napexx.getParentTitan();
               if (parent != null) {
                  parent.damage(this.getDamageSources().generic(), Float.MAX_VALUE);
               }
            } else if (living instanceof AttackTitanNapeEntity napexxx) {
               AttackTitanEntity parent = napexxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof ArmoredTitanNapeEntity napexxxx) {
               ArmoredTitanEntity parent = napexxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof FemaleTitanNapeEntity napexxxxx) {
               FemaleTitanEntity parent = napexxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof ColossalTitanNapeEntity napexxxxxx) {
               ColossalTitanEntity parent = napexxxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof FritzTitanNapeEntity napexxxxxxx) {
               FritzTitanEntity parent = napexxxxxxx.getParentTitan();
               if (parent != null) {
                  parent.damage(this.getDamageSources().generic(), Float.MAX_VALUE);
               }
            } else if (living instanceof WarhammerTitanNapeEntity napexxxxxxxx) {
               WarhammerTitanEntity parent = napexxxxxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof BeastTitanNapeEntity napexxxxxxxxx) {
               BeastTitanEntity parent = napexxxxxxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.hurtFromNape(this.getDamageSources().generic(), getRockDamage() * 4.0F);
               }
            } else if (living instanceof AttackTitanEyeEntity eye) {
               AttackTitanEntity parent = eye.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else if (living instanceof ArmoredTitanEyeEntity eyex) {
               ArmoredTitanEntity parent = eyex.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else if (living instanceof FemaleTitanEyeEntity eyexx) {
               FemaleTitanEntity parent = eyexx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else if (living instanceof ColossalTitanEyeEntity eyexxx) {
               ColossalTitanEntity parent = eyexxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else if (living instanceof FritzTitanEyeEntity eyexxxx) {
               FritzTitanEntity parent = eyexxxx.getParentTitan();
               if (parent != null) {
                  parent.triggerEyeHurt();
               }
            } else if (living instanceof WarhammerTitanEyeEntity eyexxxxx) {
               WarhammerTitanEntity parent = eyexxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else if (living instanceof BeastTitanEyeEntity eyexxxxxx) {
               BeastTitanEntity parent = eyexxxxxx.getParentTitan();
               if (parent != null && !parent.isTransforming()) {
                  parent.triggerBlindness();
               }
            } else {
               float dmg = getRockDamage() + (this.fireMode ? 4.0F : 0.0F) + (this.isBigMode() ? 60.0F : (this.isScaled() ? this.getRockScale() * 6.0F : 0.0F));
               living.damage(this.getDamageSources().thrown(this, null), dmg);
            }

            if (this.fireMode) {
               living.setFireTicks(100);
            }

            Vec3d knockDir = this.getVelocity().normalize();
            living.setVelocity(living.getVelocity().add(knockDir.x * 2.0, 0.5, knockDir.z * 2.0));
            if (living instanceof PlayerEntity player) {
               player.velocityModified = true;
               player.velocityDirty = true;
            } else {
               living.velocityModified = true;
            }

            if (this.ownerUUID != null && this.getWorld().getPlayerByUuid(this.ownerUUID) instanceof ServerPlayerEntity owner) {
               owner.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
         }

         if (this.getWorld() instanceof ServerWorld serverLevel) {
            this.sendLongRangeParticles(
               serverLevel, ParticleTypes.DAMAGE_INDICATOR, hit.getX(), hit.getY() + hit.getHeight() * 0.5, hit.getZ(), 20, 0.3, 0.3, 0.3, 0.0
            );
            this.sendLongRangeParticles(
               serverLevel,
               new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
               hit.getX(),
               hit.getY() + hit.getHeight() * 0.5,
               hit.getZ(),
               15,
               0.3,
               0.5,
               0.3,
               0.1
            );
            EffectPayload bloodPayload = new EffectPayload("blood", hit.getX(), hit.getY() + hit.getHeight() * 0.5, hit.getZ(), 1.0F);

            for (ServerPlayerEntity player : PlayerLookup.tracking(serverLevel, new BlockPos((int)hit.getX(), (int)hit.getY(), (int)hit.getZ()))) {
               ServerPlayNetworking.send(player, bloodPayload);
            }
         }

         SoundEvent[] impactSounds = new SoundEvent[]{ModSounds.FLESH_IMPACT_1, ModSounds.FLESH_IMPACT_2, ModSounds.FLESH_IMPACT_3};
         this.getWorld()
            .playSound(
               null,
               this.getX(),
               this.getY(),
               this.getZ(),
               impactSounds[this.random.nextInt(impactSounds.length)],
               SoundCategory.HOSTILE,
               6.0F,
               0.7F + this.random.nextFloat() * 0.2F
            );
      }
   }

   private void carveFlightPath() {
      if (DannysAot.isTitanGriefingEnabled(this.getWorld())) {
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            byte var10 = 6;
            BlockPos center = this.getBlockPos();

            for (int dx = -var10; dx <= var10; dx++) {
               for (int dy = -var10; dy <= var10; dy++) {
                  for (int dz = -var10; dz <= var10; dz++) {
                     if (dx * dx + dy * dy + dz * dz <= var10 * var10) {
                        BlockPos pos = center.add(dx, dy, dz);
                        BlockState s = serverLevel.getBlockState(pos);
                        if (!s.isAir()) {
                           float hardness = s.getHardness(serverLevel, pos);
                           if (!(hardness < 0.0F) && !(hardness >= 1.0F) && !s.isIn(BlockTags.WITHER_IMMUNE) && !(s.getBlock() instanceof FluidBlock)) {
                              serverLevel.removeBlock(pos, false);
                              if (this.random.nextFloat() < 0.18F) {
                                 this.sendLongRangeParticles(
                                    serverLevel,
                                    new BlockStateParticleEffect(ParticleTypes.BLOCK, s),
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5,
                                    4,
                                    0.3,
                                    0.3,
                                    0.3,
                                    0.1
                                 );
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void detonateAtCurrentPosition() {
      BlockPos pos = this.getBlockPos();
      BlockHitResult fakeHit = new BlockHitResult(this.getPos(), Direction.UP, pos, false);
      this.onBlockHit(fakeHit);
   }

   private void igniteAreaAround(ServerWorld level, BlockPos center) {
      if (DannysAot.isTitanGriefingEnabled(level)) {
         int r = 4;

         for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= r * r) {
                     BlockPos pos = center.add(dx, dy, dz);
                     BlockState here = level.getBlockState(pos);
                     if (here.isAir()) {
                        BlockPos below = pos.down();
                        BlockState support = level.getBlockState(below);
                        if (!support.isAir() && !support.isIn(BlockTags.FIRE) && !(support.getBlock() instanceof FluidBlock) && this.random.nextFloat() < 0.35F
                           )
                         {
                           level.setBlockState(pos, Blocks.FIRE.getDefaultState(), 11);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         for (long key : this.forcedChunks) {
            ChunkPos cp = new ChunkPos(key);
            serverLevel.setChunkForced(cp.x, cp.z, false);
         }

         this.forcedChunks.clear();
      }

      super.remove(reason);
   }

   @Override
   public boolean canHit() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "rock_controller", 0, this::animPredicate));
   }

   private PlayState animPredicate(AnimationState<RockProjectileEntity> state) {
      if (this.hasHit) {
         return state.setAndContinue(DESPAWN_ANIM);
      } else {
         return this.age <= 5 ? state.setAndContinue(SPAWN_ANIM) : state.setAndContinue(AIR_ANIM);
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
