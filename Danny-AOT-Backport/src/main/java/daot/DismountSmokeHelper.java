package daot;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

public class DismountSmokeHelper {
   public static final int DURATION_TICKS = 200;
   private static final int SOUND_INTERVAL_TICKS = 10;
   private static final double DISMOUNT_TRIGGER_DISTANCE_SQ = 25.0;
   private static final int MAX_DISMOUNT_PENDING_TICKS = 600;
   private static final int SHIFTER_PARTICLES_PER_TICK = 6;
   private static final int COLOSSAL_PARTICLES_PER_TICK = 12;
   private static final double COLOSSAL_SPREAD_MUL = 2.0;
   private static final double COLOSSAL_STEAM_RANGE = 512.0;
   private static final int COLOSSAL_STEAM_DURATION = 600;
   private static final double COLOSSAL_BACK_DRIFT = 0.08;
   private static final Map<UUID, DismountSmokeHelper.PendingDismount> pendingDismounts = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> playerSmokeRemaining = new ConcurrentHashMap<>();
   private static final Map<Integer, Integer> shifterSmokeRemaining = new ConcurrentHashMap<>();

   public static void armFullDismount(UUID playerUuid, double anchorX, double anchorY, double anchorZ, int currentTick) {
      pendingDismounts.put(playerUuid, new DismountSmokeHelper.PendingDismount(anchorX, anchorY, anchorZ, currentTick));
   }

   public static void cancelPendingDismount(UUID playerUuid) {
      pendingDismounts.remove(playerUuid);
   }

   public static void onShifterSpawn(LivingEntity shifter) {
      if (shifter != null && !shifter.getWorld().isClient()) {
         int duration = shifter instanceof ColossalTitanEntity ? 600 : 200;
         shifterSmokeRemaining.put(shifter.getId(), duration);
      }
   }

   public static void registerServerTick() {
      ServerTickEvents.END_SERVER_TICK.register(DismountSmokeHelper::tick);
   }

   private static void tick(MinecraftServer server) {
      int serverTick = server.getTicks();
      tickPendingDismounts(server, serverTick);
      tickPlayerSmoke(server, serverTick);
      tickShifterSmoke(server, serverTick);
   }

   private static void tickPendingDismounts(MinecraftServer server, int serverTick) {
      if (!pendingDismounts.isEmpty()) {
         Iterator<Entry<UUID, DismountSmokeHelper.PendingDismount>> it = pendingDismounts.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, DismountSmokeHelper.PendingDismount> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) {
               DismountSmokeHelper.PendingDismount pd = entry.getValue();
               if (serverTick - pd.armedAtTick() > 600) {
                  it.remove();
               } else {
                  double dx = player.getX() - pd.anchorX();
                  double dy = player.getY() - pd.anchorY();
                  double dz = player.getZ() - pd.anchorZ();
                  if (dx * dx + dy * dy + dz * dz >= 25.0) {
                     playerSmokeRemaining.put(entry.getKey(), 200);
                     it.remove();
                  }
               }
            } else {
               it.remove();
            }
         }
      }
   }

   private static void tickPlayerSmoke(MinecraftServer server, int serverTick) {
      if (!playerSmokeRemaining.isEmpty()) {
         Iterator<Entry<UUID, Integer>> it = playerSmokeRemaining.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) {
               spawnPlayerEffect(player, serverTick);
               int next = entry.getValue() - 1;
               if (next <= 0) {
                  it.remove();
               } else {
                  entry.setValue(next);
               }
            } else {
               it.remove();
            }
         }
      }
   }

   private static void tickShifterSmoke(MinecraftServer server, int serverTick) {
      if (!shifterSmokeRemaining.isEmpty()) {
         Iterator<Entry<Integer, Integer>> it = shifterSmokeRemaining.entrySet().iterator();

         while (it.hasNext()) {
            Entry<Integer, Integer> entry = it.next();
            Entity entity = findEntity(server, entry.getKey());
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
               int remaining = entry.getValue();
               float intensity = entity instanceof ColossalTitanEntity ? remaining / 600.0F : 1.0F;
               spawnShifterEffect(entity, serverTick, intensity);
               int next = remaining - 1;
               if (next <= 0) {
                  it.remove();
               } else {
                  entry.setValue(next);
               }
            } else {
               it.remove();
            }
         }
      }
   }

   private static Entity findEntity(MinecraftServer server, int entityId) {
      for (ServerWorld level : server.getWorlds()) {
         Entity e = level.getEntityById(entityId);
         if (e != null) {
            return e;
         }
      }

      return null;
   }

   private static void spawnPlayerEffect(ServerPlayerEntity player, int serverTick) {
      if (player.getWorld() instanceof ServerWorld serverLevel) {
         double var15 = player.getX();
         double py = player.getY() + player.getHeight() * 0.5;
         double pz = player.getZ();
         double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.8;
         double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.6;
         double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.8;
         serverLevel.spawnParticles(DannysAot.PLAYER_DISMOUNT_PARTICLE, var15 + offsetX, py + offsetY, pz + offsetZ, 1, 0.0, 0.02, 0.0, 0.01);
         if (serverTick % 10 == 0) {
            serverLevel.playSound(null, var15, py, pz, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.15F, 1.0F);
         }
      }
   }

   private static void emitColossalLongRange(
      ServerWorld level, ParticleEffect particle, double x, double y, double z, int count, double sx, double sy, double sz, double speed
   ) {
      double rSqr = 262144.0;

      for (ServerPlayerEntity player : level.getPlayers()) {
         if (player.squaredDistanceTo(x, y, z) < rSqr) {
            level.spawnParticles(player, particle, true, x, y, z, count, sx, sy, sz, speed);
         }
      }
   }

   private static void spawnShifterEffect(Entity entity, int serverTick, float intensity) {
      if (entity.getWorld() instanceof ServerWorld serverLevel) {
         Box bb = entity.getBoundingBox();
         double cx = (bb.minX + bb.maxX) * 0.5;
         double cz = (bb.minZ + bb.maxZ) * 0.5;
         double halfX = (bb.maxX - bb.minX) * 0.5;
         double halfZ = (bb.maxZ - bb.minZ) * 0.5;
         double height = bb.maxY - bb.minY;
         Random rand = entity.getWorld().random;
         boolean isColossal = entity instanceof ColossalTitanEntity;
         int count = isColossal ? Math.round(12.0F * intensity) : 6;
         double spreadMul = isColossal ? 2.0 : 1.0;
         double yaw = Math.toRadians(entity.getYaw());
         double backX = Math.sin(yaw);
         double backZ = -Math.cos(yaw);

         for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() * 2.0 - 1.0) * halfX * spreadMul;
            double oz = (rand.nextDouble() * 2.0 - 1.0) * halfZ * spreadMul;
            double oy = rand.nextDouble() * height;
            if (isColossal) {
               double upSpeed = 0.3 + rand.nextDouble() * 0.35;
               double drift = 0.08 * (0.6 + rand.nextDouble() * 0.8);
               emitColossalLongRange(
                  serverLevel, DannysAot.COLOSSAL_BODY_STEAM_PARTICLE, cx + ox, bb.minY + oy, cz + oz, 0, backX * drift, upSpeed, backZ * drift, 1.0
               );
            } else {
               serverLevel.spawnParticles(DannysAot.SHIFTER_TRAIL_PARTICLE, cx + ox, bb.minY + oy, cz + oz, 1, 0.05, 0.02, 0.05, 0.01);
            }
         }

         if (serverTick % 10 == 0) {
            serverLevel.playSound(null, cx, bb.minY + height * 0.5, cz, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.6F, 1.0F);
         }
      }
   }

   private record PendingDismount(double anchorX, double anchorY, double anchorZ, int armedAtTick) {
   }
}
