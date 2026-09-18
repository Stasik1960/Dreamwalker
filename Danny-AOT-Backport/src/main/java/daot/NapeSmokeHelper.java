package daot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

public final class NapeSmokeHelper {
   private static final int POST_DISMOUNT_TICKS = 1000;
   private static final int SOUND_INTERVAL = 30;
   private static final int SIDE_STREAM_DURATION_TICKS = 100;
   private static final Map<Integer, Integer> postDismountTimers = new ConcurrentHashMap<>();
   private static final Map<Integer, Integer> activeTicksElapsed = new ConcurrentHashMap<>();
   private static final double COLOSSAL_STEAM_RANGE = 512.0;

   private NapeSmokeHelper() {
   }

   private static void emit(
      ServerWorld level, ParticleEffect particle, boolean longRange, double x, double y, double z, int count, double sx, double sy, double sz, double speed
   ) {
      if (longRange) {
         double rSqr = 262144.0;

         for (ServerPlayerEntity player : level.getPlayers()) {
            if (player.squaredDistanceTo(x, y, z) < rSqr) {
               level.spawnParticles(player, particle, true, x, y, z, count, sx, sy, sz, speed);
            }
         }
      } else {
         level.spawnParticles(particle, x, y, z, count, sx, sy, sz, speed);
      }
   }

   public static void tick(LivingEntity titan, MobEntity napeEntity, boolean isDismounting) {
      if (!titan.getWorld().isClient()) {
         int id = titan.getId();
         boolean postDismount = postDismountTimers.containsKey(id);
         if (!isDismounting && !postDismount) {
            activeTicksElapsed.remove(id);
         } else {
            int elapsed = activeTicksElapsed.getOrDefault(id, 0);
            activeTicksElapsed.put(id, elapsed + 1);
            boolean sideStreamsActive = elapsed < 100;
            if (postDismount) {
               int remaining = postDismountTimers.get(id);
               if (remaining <= 0) {
                  postDismountTimers.remove(id);
                  return;
               }

               postDismountTimers.put(id, remaining - 1);
            }

            ServerWorld level = (ServerWorld)titan.getWorld();
            boolean isColossal = titan instanceof ColossalTitanEntity;
            ParticleEffect steamParticle = isColossal ? DannysAot.COLOSSAL_NAPE_STEAM_PARTICLE : DannysAot.NAPE_STEAM_PARTICLE;
            ParticleEffect trailParticle = isColossal ? DannysAot.COLOSSAL_NAPE_TRAIL_PARTICLE : DannysAot.SHIFTER_TRAIL_PARTICLE;
            double radiusMul = isColossal ? 2.0 : 1.0;
            double x;
            double y;
            double z;
            if (napeEntity != null && napeEntity.isAlive()) {
               x = napeEntity.getX();
               y = napeEntity.getY();
               z = napeEntity.getZ();
            } else {
               float yawRad = (float)Math.toRadians(titan.getYaw());
               x = titan.getX() + -Math.sin(yawRad) * 1.5;
               y = titan.getY() + titan.getHeight() * 0.8;
               z = titan.getZ() + Math.cos(yawRad) * 1.5;
            }

            float yawRad = (float)Math.toRadians(titan.getYaw());
            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double sideOffset = 0.9 * radiusMul;
            double exitSpeed = 0.18;
            double depthOffset = 0.0;
            if (titan instanceof BeastTitanEntity) {
               depthOffset = -0.5;
            } else if (titan instanceof ColossalTitanEntity) {
               depthOffset = 0.5;
            }

            double depthX = forwardX * depthOffset;
            double depthZ = forwardZ * depthOffset;
            if (sideStreamsActive) {
               emit(
                  level,
                  steamParticle,
                  isColossal,
                  x + rightX * sideOffset + depthX,
                  y,
                  z + rightZ * sideOffset + depthZ,
                  0,
                  rightX * exitSpeed,
                  0.0,
                  rightZ * exitSpeed,
                  1.0
               );
               emit(
                  level,
                  steamParticle,
                  isColossal,
                  x - rightX * sideOffset + depthX,
                  y,
                  z - rightZ * sideOffset + depthZ,
                  0,
                  -rightX * exitSpeed,
                  0.0,
                  -rightZ * exitSpeed,
                  1.0
               );
            }

            double backX = -forwardX;
            double backZ = -forwardZ;
            double behindCenterX = x + backX * 0.5;
            double behindCenterZ = z + backZ * 0.5;
            double behindRadius = 1.5 * radiusMul;

            for (int i = 0; i < 3; i++) {
               double r = Math.sqrt(titan.getRandom().nextDouble()) * behindRadius;
               double a = titan.getRandom().nextDouble() * Math.PI * 2.0;
               double dx = Math.cos(a) * r;
               double dz = Math.sin(a) * r;
               emit(level, trailParticle, isColossal, behindCenterX + dx, y, behindCenterZ + dz, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (titan.age % 30 == 0) {
               level.playSound(null, x, y, z, ModSounds.GAS_BOOST, SoundCategory.HOSTILE, 0.6F, 1.2F);
            }
         }
      }
   }

   public static void onDismountStart(LivingEntity titan, MobEntity napeEntity) {
      if (!titan.getWorld().isClient()) {
         ServerWorld level = (ServerWorld)titan.getWorld();
         double x;
         double y;
         double z;
         if (napeEntity != null && napeEntity.isAlive()) {
            x = napeEntity.getX();
            y = napeEntity.getY();
            z = napeEntity.getZ();
         } else {
            float yawRad = (float)Math.toRadians(titan.getYaw());
            x = titan.getX() + -Math.sin(yawRad) * 1.5;
            y = titan.getY() + titan.getHeight() * 0.8;
            z = titan.getZ() + Math.cos(yawRad) * 1.5;
         }

         boolean isColossal = titan instanceof ColossalTitanEntity;
         ParticleEffect steamParticle = isColossal ? DannysAot.COLOSSAL_NAPE_STEAM_PARTICLE : DannysAot.NAPE_STEAM_PARTICLE;
         double spreadMul = isColossal ? 2.0 : 1.0;
         emit(level, steamParticle, isColossal, x, y, z, 120, 1.2 * spreadMul, 0.8 * spreadMul, 1.2 * spreadMul, 0.04);
         level.playSound(null, x, y, z, ModSounds.STEAM_POOF, SoundCategory.HOSTILE, 1.5F, 1.0F);
      }
   }

   public static void onFullDismount(int entityId) {
      postDismountTimers.put(entityId, 1000);
   }

   public static void cleanup(int entityId) {
      postDismountTimers.remove(entityId);
      activeTicksElapsed.remove(entityId);
   }
}
