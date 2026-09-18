package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class APGProjectileEntity extends PersistentProjectileEntity implements daot.compat.ProjectileGravityProvider {
   private static final int LIFETIME_TICKS = 60;
   private static final float DAMAGE = 13.65F;
   private static final Map<UUID, Long> LAST_IMPACT_TICK = new ConcurrentHashMap<>();
   private static final long IMPACT_DEDUP_WINDOW = 3L;

   public APGProjectileEntity(EntityType<?> entityType, World level) {
      super((EntityType<? extends PersistentProjectileEntity>)entityType, level);
      this.pickupType = PickupPermission.DISALLOWED;
      this.setDamage(13.65F);
   }

   public APGProjectileEntity(World level, LivingEntity shooter) {
      super(DannysAot.APG_PROJECTILE, level);
      this.pickupType = PickupPermission.DISALLOWED;
      this.setDamage(13.65F);
      if (shooter != null) {
         this.setOwner(shooter);
         this.setPosition(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
      }
   }

   @Override
   public double daotGravity() {
      return 0.0;
   }

   @Override
   public void tick() {
      Vec3d velBefore = this.getVelocity();
      double hSpeedBefore = Math.sqrt(velBefore.x * velBefore.x + velBefore.z * velBefore.z);
      super.tick();
      if (hSpeedBefore > 0.001 && !this.isTouchingWater()) {
         Vec3d velAfter = this.getVelocity();
         double hSpeedAfter = Math.sqrt(velAfter.x * velAfter.x + velAfter.z * velAfter.z);
         if (hSpeedAfter > 1.0E-4 && hSpeedAfter < hSpeedBefore) {
            double restore = hSpeedBefore / hSpeedAfter;
            this.setVelocity(velAfter.x * restore, velAfter.y, velAfter.z * restore);
         }
      }

      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         for (ServerPlayerEntity p : serverLevel.getPlayers()) {
            if (p.squaredDistanceTo(this.getX(), this.getY(), this.getZ()) < 16384.0) {
               serverLevel.spawnParticles(p, ParticleTypes.SMOKE, true, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
            }
         }
      }

      if (this.age > 60) {
         this.discard();
      }
   }

   @Override
   protected void onBlockHit(BlockHitResult blockHitResult) {
      World level = this.getWorld();
      if (!level.isClient()) {
         BlockState state = level.getBlockState(blockHitResult.getBlockPos());
         if (isGlassLike(state)) {
            level.breakBlock(blockHitResult.getBlockPos(), false, this.getOwner());
            if (level instanceof ServerWorld serverLevel) {
               serverLevel.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            }

            return;
         }
      }

      if (!level.isClient() && level instanceof ServerWorld serverLevel) {
         boolean playSfx = this.shouldPlayImpactSfx(serverLevel);
         BlockState hitState = level.getBlockState(blockHitResult.getBlockPos());
         BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, hitState);
         int debrisCount = playSfx ? 22 : 10;
         int critCount = playSfx ? 10 : 4;
         serverLevel.spawnParticles(debris, this.getX(), this.getY(), this.getZ(), debrisCount, 0.18, 0.18, 0.18, 0.25);
         serverLevel.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), critCount, 0.12, 0.12, 0.12, 0.08);
         if (playSfx) {
            float impactPitch = 1.2F + this.random.nextFloat() * 0.1F;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SHOT_IMPACT, SoundCategory.PLAYERS, 1.0F, impactPitch);
            float ramPitch = 0.5F + this.random.nextFloat() * 0.1F;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.PLAYERS, 0.9F, ramPitch);
         }
      }

      this.discard();
   }

   private boolean shouldPlayImpactSfx(ServerWorld level) {
      return shouldPlayImpactSfx(level, this.getOwner());
   }

   public static boolean shouldPlayImpactSfx(ServerWorld level, Entity owner) {
      if (owner == null) {
         return true;
      } else {
         long now = level.getTime();
         Long last = LAST_IMPACT_TICK.get(owner.getUuid());
         if (last != null && now - last < 3L) {
            return false;
         } else {
            LAST_IMPACT_TICK.put(owner.getUuid(), now);
            return true;
         }
      }
   }

   public static boolean isGlassLike(BlockState state) {
      return state.isIn(BlockTags.IMPERMEABLE) || state.getBlock() instanceof PaneBlock;
   }

   public static float damage() {
      return 13.65F;
   }

   public static void spawnTracer(ServerWorld level, Vec3d from, Vec3d to) {
      Vec3d delta = to.subtract(from);
      double dist = delta.length();
      if (!(dist < 0.05)) {
         Vec3d step = delta.multiply(1.0 / dist);
         int points = (int)Math.min(dist, 160.0);

         for (int i = 1; i < points; i++) {
            level.spawnParticles(ParticleTypes.SMOKE, from.x + step.x * i, from.y + step.y * i, from.z + step.z * i, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   public static void spawnBlockImpact(ServerWorld level, Vec3d pos, BlockState hitState, Entity owner) {
      boolean playSfx = shouldPlayImpactSfx(level, owner);
      BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, hitState);
      int debrisCount = playSfx ? 22 : 10;
      int critCount = playSfx ? 10 : 4;
      level.spawnParticles(debris, pos.x, pos.y, pos.z, debrisCount, 0.18, 0.18, 0.18, 0.25);
      level.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, critCount, 0.12, 0.12, 0.12, 0.08);
      if (playSfx) {
         float impactPitch = 1.2F + level.random.nextFloat() * 0.1F;
         level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SHOT_IMPACT, SoundCategory.PLAYERS, 1.0F, impactPitch);
         float ramPitch = 0.5F + level.random.nextFloat() * 0.1F;
         level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.PLAYERS, 0.9F, ramPitch);
      }
   }

   public static void spawnEntityImpact(ServerWorld level, Vec3d pos, Entity owner) {
      boolean playSfx = shouldPlayImpactSfx(level, owner);
      level.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, playSfx ? 8 : 3, 0.1, 0.1, 0.1, 0.05);
      if (playSfx) {
         float impactPitch = 1.2F + level.random.nextFloat() * 0.1F;
         level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SHOT_IMPACT, SoundCategory.PLAYERS, 1.0F, impactPitch);
      }
   }

   @Override
   protected void onEntityHit(EntityHitResult entityHitResult) {
      Entity target = entityHitResult.getEntity();
      Entity owner = this.getOwner();
      DamageSource source = this.getDamageSources().arrow(this, (Entity)(owner != null ? owner : this));
      target.damage(source, 13.65F);
      this.discard();
   }

   @Override
   protected ItemStack asItemStack() {
      return new ItemStack(Items.ARROW);
   }

   @Override
   protected float getDragInWater() {
      return 0.9F;
   }
}
