package daot;

import daot.network.EffectPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class GibEntity extends Entity {
   private static final int LIFETIME_TICKS = 200;
   private static final int SHRINK_START_TICK = 150;
   private static final double GRAVITY = 0.045;
   private static final double AIR_DRAG = 0.985;
   private static final double GROUND_FRICTION = 0.6;
   private static final double BOUNCE_DAMPING = 0.3;
   private static final double STOP_SPEED_SQR = 0.002;
   private static final double LAND_SOUND_THRESHOLD = 0.05;
   private static final TrackedData<Integer> DATA_SPIN = DataTracker.registerData(GibEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> DATA_SCALE = DataTracker.registerData(GibEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private int age;
   public int getGibAge() { return age; }
   private boolean wasAirborne = true;
   private static final DustParticleEffect BLOOD_DUST = new DustParticleEffect(new Vector3f(0.55F, 0.05F, 0.05F), 1.5F);
   private static final DustParticleEffect BLOOD_DUST_DARK = new DustParticleEffect(new Vector3f(0.3F, 0.02F, 0.02F), 1.8F);

   public GibEntity(EntityType<?> type, World level) {
      super(type, level);
      this.noClip = false;
      this.intersectionChecked = false;
   }

   public GibEntity(World level, double x, double y, double z, Vec3d velocity, int spin, float scale) {
      this(DannysAot.GIB, level);
      this.setPosition(x, y, z);
      this.setVelocity(velocity);
      this.dataTracker.set(DATA_SPIN, spin);
      this.dataTracker.set(DATA_SCALE, scale);
   }

   @Override
   protected void initDataTracker() {
      this.dataTracker.startTracking(DATA_SPIN, 0);
      this.dataTracker.startTracking(DATA_SCALE, 1.0F);
   }

   public int getSpin() {
      return this.dataTracker.get(DATA_SPIN);
   }

   public float getGibScale() {
      return this.dataTracker.get(DATA_SCALE);
   }

   public float getVisualScale() {
      float fade;
      if (this.age <= 150) {
         fade = 1.0F;
      } else if (this.age >= 200) {
         fade = 0.0F;
      } else {
         fade = 1.0F - (this.age - 150) / 50.0F;
      }

      return this.getGibScale() * fade;
   }

   @Override
   public void tick() {
      super.tick();
      this.age++;
      if (!this.getWorld().isClient && this.age >= 200) {
         this.discard();
      } else {
         Vec3d cur = this.getVelocity();
         double impactY = cur.y;
         this.setVelocity(cur.x, cur.y - 0.045, cur.z);
         this.move(MovementType.SELF, this.getVelocity());
         Vec3d post = this.getVelocity();
         boolean nowOnGround = this.isOnGround();
         if (nowOnGround) {
            double bounceY = -impactY * 0.3;
            double newX = post.x * 0.6;
            double newZ = post.z * 0.6;
            if (Math.abs(bounceY) < 0.04 && newX * newX + newZ * newZ < 0.002) {
               this.setVelocity(Vec3d.ZERO);
            } else {
               this.setVelocity(newX, bounceY, newZ);
            }
         } else {
            this.setVelocity(post.multiply(0.985));
            if (this.getWorld() instanceof ServerWorld sl && (this.age & 1) == 0) {
               sl.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
            }
         }

         if (!this.getWorld().isClient && this.wasAirborne && nowOnGround && impactY < -0.05) {
            ServerWorld sl = (ServerWorld)this.getWorld();
            float pitch = 0.5F + sl.random.nextFloat() * 0.1F;
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_BEEHIVE_DRIP, SoundCategory.NEUTRAL, 1.0F, pitch);
         }

         this.wasAirborne = !nowOnGround;
      }
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean isCollidable() {
      return false;
   }

   @Override
   protected void readCustomDataFromNbt(NbtCompound nbt) {
      this.age = nbt.getInt("Age");
      if (nbt.contains("Spin")) {
         this.dataTracker.set(DATA_SPIN, nbt.getInt("Spin"));
      }

      if (nbt.contains("Scale")) {
         this.dataTracker.set(DATA_SCALE, nbt.getFloat("Scale"));
      }
   }

   @Override
   protected void writeCustomDataToNbt(NbtCompound nbt) {
      nbt.putInt("Age", this.age);
      nbt.putInt("Spin", this.getSpin());
      nbt.putFloat("Scale", this.getGibScale());
   }

   public static void spawnGibs(ServerWorld level, LivingEntity victim, Vec3d kickDir, float intensity) {
      int count = 45 + level.random.nextInt(25);
      double cx = victim.getX();
      double cy = victim.getY() + victim.getHeight() * 0.5;
      double cz = victim.getZ();
      double w = victim.getWidth();
      double h = victim.getHeight();
      Vec3d baseKick = kickDir != null && kickDir.lengthSquared() > 1.0E-4 ? kickDir.normalize() : new Vec3d(0.0, 1.0, 0.0);
      int directionalCount = (int)Math.round(count * 0.6);

      for (int i = 0; i < count; i++) {
         double x = cx + (level.random.nextDouble() - 0.5) * w * 0.6;
         double y = cy + (level.random.nextDouble() - 0.5) * h * 0.6;
         double z = cz + (level.random.nextDouble() - 0.5) * w * 0.6;
         double vx;
         double vy;
         double vz;
         if (i < directionalCount) {
            double rx = (level.random.nextDouble() - 0.5) * 1.6;
            double rz = (level.random.nextDouble() - 0.5) * 1.6;
            double ry = 0.25 + level.random.nextDouble() * 0.55;
            vx = (baseKick.x + rx) * 0.4 * intensity;
            vy = (baseKick.y * 0.5 + ry) * intensity;
            vz = (baseKick.z + rz) * 0.4 * intensity;
         } else {
            double theta = level.random.nextDouble() * Math.PI * 2.0;
            double cosPhi = level.random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            double mag = (0.4 + level.random.nextDouble() * 0.6) * intensity;
            vx = sinPhi * Math.cos(theta) * mag;
            vy = cosPhi * mag + 0.15 * intensity;
            vz = sinPhi * Math.sin(theta) * mag;
         }

         int spin = level.random.nextInt();
         float scale = MathHelper.lerp(level.random.nextFloat(), 0.33333334F, 1.0F);
         GibEntity gib = new GibEntity(level, x, y, z, new Vec3d(vx, vy, vz), spin, scale);
         level.spawnEntity(gib);
      }

      int dmgInd = 100 + level.random.nextInt(50);

      for (int i = 0; i < dmgInd; i++) {
         double sx = (level.random.nextDouble() - 0.5) * w;
         double sy = (level.random.nextDouble() - 0.5) * h;
         double sz = (level.random.nextDouble() - 0.5) * w;
         double svx = (baseKick.x + (level.random.nextDouble() - 0.5) * 2.0) * 0.35 * intensity;
         double svy = (level.random.nextDouble() * 0.6 + baseKick.y * 0.3) * 0.35 * intensity;
         double svz = (baseKick.z + (level.random.nextDouble() - 0.5) * 2.0) * 0.35 * intensity;
         level.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, cx + sx, cy + sy, cz + sz, 0, svx, svy, svz, 1.0);
      }

      int dust = 120 + level.random.nextInt(60);

      for (int i = 0; i < dust; i++) {
         double theta = level.random.nextDouble() * Math.PI * 2.0;
         double cosPhi = level.random.nextDouble() * 2.0 - 1.0;
         double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
         double mag = (0.2 + level.random.nextDouble() * 0.6) * intensity;
         double svx = sinPhi * Math.cos(theta) * mag;
         double svy = cosPhi * mag + 0.1;
         double svz = sinPhi * Math.sin(theta) * mag;
         DustParticleEffect opt = level.random.nextBoolean() ? BLOOD_DUST : BLOOD_DUST_DARK;
         level.spawnParticles(
            opt,
            cx + (level.random.nextDouble() - 0.5) * w * 0.8,
            cy + (level.random.nextDouble() - 0.5) * h * 0.8,
            cz + (level.random.nextDouble() - 0.5) * w * 0.8,
            0,
            svx,
            svy,
            svz,
            1.0
         );
      }

      BlockPos centerPos = new BlockPos((int)cx, (int)cy, (int)cz);
      EffectPayload bigBlood = new EffectPayload("blood", cx, cy, cz, 2.5F);

      for (ServerPlayerEntity p : PlayerLookup.tracking(level, centerPos)) {
         ServerPlayNetworking.send(p, bigBlood);
      }

      for (int i = 0; i < 4; i++) {
         double ox = (level.random.nextDouble() - 0.5) * w * 1.2;
         double oy = (level.random.nextDouble() - 0.5) * h * 0.8;
         double oz = (level.random.nextDouble() - 0.5) * w * 1.2;
         EffectPayload sideBlood = new EffectPayload("blood", cx + ox, cy + oy, cz + oz, 1.5F);
         BlockPos sidePos = new BlockPos((int)(cx + ox), (int)(cy + oy), (int)(cz + oz));

         for (ServerPlayerEntity p : PlayerLookup.tracking(level, sidePos)) {
            ServerPlayNetworking.send(p, sideBlood);
         }
      }
   }
}
