package daot;

import java.util.HashSet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FlareProjectileEntity extends PersistentProjectileEntity implements daot.compat.ProjectileGravityProvider {
   private int flareColorOrdinal = -1;
   private final HashSet<ChunkPos> forcedChunks = new HashSet<>();
   private static final double FOG_FLARE_RANGE = 80.0;
   private static final double NORMAL_FLARE_RANGE = 1000.0;

   public FlareProjectileEntity(EntityType<?> entityType, World level) {
      super((EntityType<? extends PersistentProjectileEntity>)entityType, level);
      this.pickupType = PickupPermission.DISALLOWED;
   }

   public FlareProjectileEntity(World level, LivingEntity shooter, int colorOrdinal) {
      super(DannysAot.FLARE_PROJECTILE, level);
      this.flareColorOrdinal = colorOrdinal;
      this.pickupType = PickupPermission.DISALLOWED;
      if (shooter != null) {
         this.setOwner(shooter);
         this.setPosition(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
      }
   }

   public int getFlareColorOrdinal() {
      return this.flareColorOrdinal;
   }

   public void setFlareColorOrdinal(int ord) {
      this.flareColorOrdinal = ord;
   }

   @Override
   public double daotGravity() {
      return 7.0E-4;
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
         if (this.flareColorOrdinal >= 0 && this.flareColorOrdinal < FlareCartridgeItem.FlareColor.values().length) {
            FlareCartridgeItem.FlareColor color = FlareCartridgeItem.FlareColor.values()[this.flareColorOrdinal];
            DefaultParticleType particle = getParticleForColor(color);
            this.sendLongRangeParticles(serverLevel, particle, this.getX(), this.getY(), this.getZ(), 3, 0.2, 0.2, 0.2, 0.015);
         }

         ChunkPos cp = new ChunkPos(this.getBlockPos());

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               ChunkPos toForce = new ChunkPos(cp.x + dx, cp.z + dz);
               if (this.forcedChunks.add(toForce)) {
                  serverLevel.setChunkForced(toForce.x, toForce.z, true);
               }
            }
         }
      }

      if (this.age > 600) {
         this.discard();
      }
   }

   private static DefaultParticleType getParticleForColor(FlareCartridgeItem.FlareColor color) {
      return switch (color) {
         case RED -> DannysAot.FLARE_RED_PARTICLE;
         case BLACK -> DannysAot.FLARE_BLACK_PARTICLE;
         case PURPLE -> DannysAot.FLARE_PURPLE_PARTICLE;
         case BLUE -> DannysAot.FLARE_BLUE_PARTICLE;
         case GREEN -> DannysAot.FLARE_GREEN_PARTICLE;
         case YELLOW -> DannysAot.FLARE_YELLOW_PARTICLE;
      };
   }

   private <T extends ParticleEffect> void sendLongRangeParticles(
      ServerWorld level, T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed
   ) {
      double range = 1000.0;
      if (BreachManager.isFogZone(level.getServer(), (int)Math.floor(x), (int)Math.floor(z))) {
         range = 80.0;
      }

      double rangeSq = range * range;

      for (ServerPlayerEntity player : level.getPlayers()) {
         if (player.squaredDistanceTo(x, y, z) < rangeSq) {
            level.spawnParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
         }
      }
   }

   @Override
   protected void onBlockHit(BlockHitResult blockHitResult) {
      this.discard();
   }

   @Override
   protected void onEntityHit(EntityHitResult entityHitResult) {
   }

   @Override
   protected ItemStack asItemStack() {
      return new ItemStack(Items.ARROW);
   }

   @Override
   protected float getDragInWater() {
      return 0.6F;
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld serverLevel) {
         for (ChunkPos cp : this.forcedChunks) {
            serverLevel.setChunkForced(cp.x, cp.z, false);
         }

         this.forcedChunks.clear();
      }

      super.remove(reason);
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putInt("FlareColor", this.flareColorOrdinal);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      this.flareColorOrdinal = nbt.getInt("FlareColor");
   }
}
