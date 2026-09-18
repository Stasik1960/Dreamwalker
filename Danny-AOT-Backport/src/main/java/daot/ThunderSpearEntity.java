package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.EffectPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.World.ExplosionSourceType;
import org.jetbrains.annotations.Nullable;

public class ThunderSpearEntity extends PersistentProjectileEntity {
   private static final float EXPLOSION_POWER = 4.0F;
   private static final int FUSE_TICKS = 10;
   private static final double TRIGGER_STRETCH = 1.5;
   private static final double MIN_LODGE_WIRE = 3.5;
   private static final int LODGE_GRACE_TICKS = 10;
   private static final double MAX_RANGE = 50.0;
   private static final int FLIGHT_SOUND_INTERVAL = 5;
   public static final float FLIGHT_SPEED = 1.8F;
   private boolean lodged = false;
   private double lodgeWireLength = -1.0;
   private int fuseTicks = -1;
   private boolean fuseStarted = false;
   private int lodgeTick = 0;
   private Entity lodgeEntity = null;
   private Vec3d lodgeEntityOffset = Vec3d.ZERO;
   private Vec3d spawnPos = null;
   private boolean freezeLodged = false;
   private boolean repelled = false;
   private boolean repelledEnteredOwnerRange = false;
   public boolean inPushZone = false;

   public ThunderSpearEntity(EntityType<?> entityType, World level) {
      super((EntityType<? extends PersistentProjectileEntity>)entityType, level);
      this.setNoGravity(true);
      this.pickupType = PickupPermission.CREATIVE_ONLY;
   }

   public ThunderSpearEntity(World level, LivingEntity shooter) {
      super(DannysAot.THUNDER_SPEAR_ENTITY, shooter, level);
      this.setNoGravity(true);
      this.pickupType = PickupPermission.CREATIVE_ONLY;
   }

   @Override
   protected boolean canHit(Entity entity) {
      if (entity.isAlive() && !entity.isSpectator()) {
         Entity owner = this.getOwner();
         return entity != owner;
      } else {
         return false;
      }
   }

   @Nullable
   @Override
   protected EntityHitResult getEntityCollision(Vec3d currentPosition, Vec3d nextPosition) {
      Box searchBox = this.getBoundingBox().stretch(this.getVelocity()).expand(1.0);
      double bestDist = Double.MAX_VALUE;
      Entity bestEntity = null;
      Vec3d bestHitLoc = null;

      for (Entity entity : this.getWorld().getOtherEntities(this, searchBox, this::canHit)) {
         Box entityBox = entity.getBoundingBox().expand(0.3);
         Optional<Vec3d> clip = entityBox.raycast(currentPosition, nextPosition);
         if (clip.isPresent()) {
            double dist = currentPosition.squaredDistanceTo(clip.get());
            if (dist < bestDist) {
               bestDist = dist;
               bestEntity = entity;
               bestHitLoc = clip.get();
            }
         } else if (entityBox.contains(currentPosition)) {
            double dist = currentPosition.squaredDistanceTo(entity.getPos());
            if (dist < bestDist) {
               bestDist = dist;
               bestEntity = entity;
               bestHitLoc = currentPosition;
            }
         }
      }

      return bestEntity == null ? null : new EntityHitResult(bestEntity, bestHitLoc);
   }

   @Override
   protected void onEntityHit(EntityHitResult entityHitResult) {
      if (!this.getWorld().isClient()) {
         Vec3d hitLoc = entityHitResult.getPos();
         this.setPosition(hitLoc.x, hitLoc.y, hitLoc.z);
         this.setVelocity(Vec3d.ZERO);
         this.setNoGravity(true);
         this.lodgeEntity = entityHitResult.getEntity();
         this.lodgeEntityOffset = hitLoc.subtract(this.lodgeEntity.getPos());
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            Vec3d hitPos = entityHitResult.getPos();

            for (int i = 0; i < 30; i++) {
               double ox = (this.random.nextDouble() - 0.5) * 1.0;
               double oy = (this.random.nextDouble() - 0.5) * 1.0;
               double oz = (this.random.nextDouble() - 0.5) * 1.0;
               serverLevel.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, hitPos.x + ox, hitPos.y + oy, hitPos.z + oz, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }

         this.lodge();
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HANDBITE, SoundCategory.PLAYERS, 15.0F, 0.6F);
      }
   }

   @Override
   protected void onBlockHit(BlockHitResult blockHitResult) {
      super.onBlockHit(blockHitResult);
      if (!this.getWorld().isClient()) {
         if (this.getWorld() instanceof ServerWorld serverLevel) {
            BlockPos hitBlock = blockHitResult.getBlockPos();
            BlockState blockState = this.getWorld().getBlockState(hitBlock);
            Vec3d hitPos = blockHitResult.getPos();

            for (int i = 0; i < 30; i++) {
               double ox = (this.random.nextDouble() - 0.5) * 1.0;
               double oy = (this.random.nextDouble() - 0.5) * 1.0;
               double oz = (this.random.nextDouble() - 0.5) * 1.0;
               serverLevel.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState), hitPos.x + ox, hitPos.y + oy, hitPos.z + oz, 1, 0.0, 0.0, 0.0, 0.0
               );
            }
         }

         this.lodge();
      }
   }

   @Override
   public void playSound(SoundEvent sound, float volume, float pitch) {
      if (!sound.equals(SoundEvents.ENTITY_ARROW_HIT)) {
         super.playSound(sound, volume, pitch);
      }
   }

   public boolean isLodged() {
      return this.lodged;
   }

   public void freezeLodge() {
      if (!this.lodged) {
         this.freezeLodged = true;
         this.lodge();
         this.setVelocity(Vec3d.ZERO);
      }
   }

   public boolean isFreezeLodged() {
      return this.freezeLodged;
   }

   public void unfreezeLodge() {
      if (this.freezeLodged) {
         this.freezeLodged = false;
         this.lodged = false;
         this.lodgeTick = 0;
         this.lodgeWireLength = -1.0;
      }
   }

   public boolean isRepelled() {
      return this.repelled;
   }

   public void setRepelled(boolean val) {
      this.repelled = val;
      if (val) {
         this.repelledEnteredOwnerRange = false;
      }
   }

   private void lodge() {
      if (!this.lodged) {
         this.lodged = true;
         this.lodgeTick = 0;
         Entity owner = this.getOwner();
         if (owner != null) {
            this.lodgeWireLength = Math.max((double)this.distanceTo(owner), 3.5);
         }

         if (!this.getWorld().isClient()) {
            float fleshPitch = 1.5F + this.random.nextFloat() * 0.1F;
            float hookPitch = 0.8F + this.random.nextFloat() * 0.1F;
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.FLESH_IMPACT_6, SoundCategory.PLAYERS, 15.0F, fleshPitch);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.HOOK_IMPACT, SoundCategory.PLAYERS, 15.0F, hookPitch);
            this.velocityDirty = true;
            if (this.getWorld() instanceof ServerWorld serverLevel) {
               float entityIdFloat = this.lodgeEntity != null ? this.lodgeEntity.getId() : -1.0F;
               EffectPayload payload = new EffectPayload("thunder_spear_lodge", this.getX(), this.getY(), this.getZ(), entityIdFloat);

               for (ServerPlayerEntity nearby : PlayerLookup.tracking(serverLevel, this.getBlockPos())) {
                  ServerPlayNetworking.send(nearby, payload);
               }

               if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
                  ServerPlayNetworking.send(ownerPlayer, payload);
               }
            }
         }
      }
   }

   private void explode() {
      boolean canDestroyBlocks = this.getWorld().getGameRules().getBoolean(DannysAot.RULE_TITAN_GRIEFING)
         && DannysAot.isThunderSpearGriefingEnabled(this.getWorld());
      ExplosionSourceType interaction = canDestroyBlocks ? ExplosionSourceType.TNT : ExplosionSourceType.NONE;
      this.getWorld().createExplosion(this, this.getX(), this.getY(), this.getZ(), 4.0F, true, interaction);
      double knockbackRadius = 10.0;

      for (Entity entity : this.getWorld()
         .getOtherEntities(
            this,
            new Box(
               this.getX() - knockbackRadius,
               this.getY() - knockbackRadius,
               this.getZ() - knockbackRadius,
               this.getX() + knockbackRadius,
               this.getY() + knockbackRadius,
               this.getZ() + knockbackRadius
            )
         )) {
         if (entity instanceof LivingEntity living) {
            double dist = this.getPos().distanceTo(entity.getPos());
            if (dist < knockbackRadius && dist > 0.1) {
               double factor = 1.0 - dist / knockbackRadius;
               Vec3d dir = entity.getPos().subtract(this.getPos()).normalize();
               double strength = 2.5 * factor;
               living.setVelocity(living.getVelocity().add(dir.x * strength, 0.6 * factor + 0.3, dir.z * strength));
               living.velocityModified = true;
            }
         }
      }

      if (canDestroyBlocks && this.getWorld() instanceof ServerWorld serverLevel) {
         this.flingBlocks(serverLevel);
      }

      if (this.getWorld() instanceof ServerWorld serverLevel) {
         EffectPayload payload = new EffectPayload("thunder_spear_explode", this.getX(), this.getY(), this.getZ(), 4.0F);

         for (ServerPlayerEntity nearby : PlayerLookup.tracking(serverLevel, this.getBlockPos())) {
            ServerPlayNetworking.send(nearby, payload);
         }

         if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
            ServerPlayNetworking.send(ownerPlayer, payload);
         }
      }

      this.discard();
   }

   private void flingBlocks(ServerWorld serverLevel) {
      double cx = this.getX();
      double cy = this.getY();
      double cz = this.getZ();
      int radius = 4;
      int maxFlung = 15;
      int flung = 0;

      for (int dx = -radius; dx <= radius && flung < maxFlung; dx++) {
         for (int dz = -radius; dz <= radius && flung < maxFlung; dz++) {
            for (int dy = -2; dy <= 4 && flung < maxFlung; dy++) {
               if (!(this.random.nextFloat() > 0.15F)) {
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  if (!(dist > radius) && !(dist < 1.0)) {
                     BlockPos pos = new BlockPos((int)Math.floor(cx + dx), (int)Math.floor(cy + dy), (int)Math.floor(cz + dz));
                     BlockState blockState = this.getWorld().getBlockState(pos);
                     if (!blockState.isAir()
                        && !(blockState.getHardness(this.getWorld(), pos) < 0.0F)
                        && !blockState.isIn(BlockTags.WITHER_IMMUNE)
                        && !(blockState.getBlock() instanceof FluidBlock)) {
                        this.getWorld().removeBlock(pos, false);
                        double dirX = dx / dist;
                        double dirZ = dz / dist;
                        double velX = dirX * (0.5 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.2;
                        double velY = 0.3 + this.random.nextDouble() * 0.6;
                        double velZ = dirZ * (0.5 + this.random.nextDouble() * 0.5) + (this.random.nextDouble() - 0.5) * 0.2;
                        FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, serverLevel);
                        ((FallingBlockEntityAccessor)fallingBlock).setBlockState(blockState);
                        fallingBlock.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        fallingBlock.setFallingBlockPos(pos);
                        fallingBlock.setVelocity(velX, velY, velZ);
                        fallingBlock.timeFalling = 1;
                        fallingBlock.dropItem = false;
                        serverLevel.spawnEntity(fallingBlock);
                        flung++;
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   protected ItemStack asItemStack() {
      return new ItemStack(DannysAot.THUNDER_SPEAR);
   }

   @Override
   public void tick() {
      if (this.spawnPos == null) {
         this.spawnPos = this.getPos();
      }

      this.inPushZone = false;
      if (this.freezeLodged) {
         this.lastRenderX = this.getX();
         this.lastRenderY = this.getY();
         this.lastRenderZ = this.getZ();
         this.age++;
         this.setVelocity(Vec3d.ZERO);
         if (this.lodged) {
            this.lodgeTick++;
         }
      } else {
         if (this.lodged && this.lodgeEntity != null) {
            this.lastRenderX = this.getX();
            this.lastRenderY = this.getY();
            this.lastRenderZ = this.getZ();
            this.age++;
            if (!this.lodgeEntity.isAlive()) {
               this.explode();
               return;
            }

            Vec3d target = this.lodgeEntity.getPos().add(this.lodgeEntityOffset);
            this.setPosition(target.x, target.y, target.z);
            this.setVelocity(Vec3d.ZERO);
         } else {
            boolean wasLodged = this.lodged;
            super.tick();
            if (!wasLodged && this.lodged && this.lodgeEntity != null && this.lodgeEntity.isAlive()) {
               Vec3d target = this.lodgeEntity.getPos().add(this.lodgeEntityOffset);
               this.setPosition(target.x, target.y, target.z);
               this.setVelocity(Vec3d.ZERO);
            }

            if (!this.lodged && !this.fuseStarted && !this.inPushZone) {
               Vec3d vel = this.getVelocity();
               if (vel.lengthSquared() > 0.001) {
                  this.setVelocity(vel.normalize().multiply(1.8F));
               }
            }
         }

         if (this.lodged) {
            this.lodgeTick++;
         }

         if (!this.getWorld().isClient() && !this.lodged && this.age % 5 == 0) {
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GAS_BOOST, SoundCategory.PLAYERS, 2.0F, 2.0F);
         }

         if (!this.getWorld().isClient() && !this.lodged && !this.fuseStarted && this.spawnPos != null) {
            double flightDist = this.getPos().distanceTo(this.spawnPos);
            if (flightDist >= 50.0) {
               this.fuseStarted = true;
               this.fuseTicks = 0;
               Entity owner = this.getOwner();
               if (owner != null) {
                  this.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.HOOK_IMPACT, SoundCategory.PLAYERS, 1.0F, 2.0F);
               }

               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GAS_BOOST, SoundCategory.AMBIENT, 1.0F, 2.0F);
            }
         }

         if (!this.getWorld().isClient() && this.lodged && this.lodgeWireLength >= 0.0) {
            Entity owner = this.getOwner();
            if (owner != null && this.lodgeTick > 10) {
               double currentDist = this.distanceTo(owner);
               if (!this.fuseStarted && currentDist > this.lodgeWireLength + 1.5) {
                  this.fuseStarted = true;
                  this.fuseTicks = 0;
                  this.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.HOOK_IMPACT, SoundCategory.PLAYERS, 1.0F, 2.0F);
                  this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GAS_BOOST, SoundCategory.AMBIENT, 1.0F, 2.0F);
               }
            }
         }

         if (!this.getWorld().isClient() && this.fuseStarted) {
            this.fuseTicks++;
            if (this.fuseTicks >= 10) {
               this.explode();
               return;
            }
         }

         if (!this.lodged && !this.fuseStarted && this.age > 200) {
            this.discard();
         } else if (this.lodged && !this.fuseStarted && this.age > 600) {
            this.discard();
         }
      }
   }
}
