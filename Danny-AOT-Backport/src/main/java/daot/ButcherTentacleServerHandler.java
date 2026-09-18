package daot;

import daot.network.ButcherTentacleBroadcastPayload;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ButcherTentacleServerHandler {
   public static final double TENTACLE_RANGE = 11.0;
   public static final double TENTACLE_RADIUS = 1.2;
   private static final float TENTACLE_DAMAGE = 20.0F;
   private static final double TENTACLE_KNOCKBACK = 0.5;
   public static final int COOLDOWN_TICKS = 40;
   private static final int RETRACT_PHASE_OFFSET_TICKS = 14;
   private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
   private static final Map<UUID, long[]> pendingRetractTick = new ConcurrentHashMap<>();
   private static final Map<UUID, Vec3d> pendingRetractPos = new ConcurrentHashMap<>();
   private static final Map<UUID, ServerWorld> pendingRetractLevel = new ConcurrentHashMap<>();

   private ButcherTentacleServerHandler() {
   }

   public static boolean fire(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      long now = level.getTime();
      UUID id = player.getUuid();
      Long until = cooldownUntil.get(id);
      if (until != null && now < until) {
         return false;
      } else {
         cooldownUntil.put(id, now + 40L);
         Vec3d look = player.getRotationVec(1.0F);
         if (look.lengthSquared() < 1.0E-4) {
            return false;
         } else {
            look = look.normalize();
            Vec3d chest = player.getPos().add(0.0, player.getStandingEyeHeight() * 0.7, 0.0);
            Vec3d endpoint = chest.add(look.multiply(11.0));
            Box box = new Box(chest, endpoint).expand(1.7);

            for (Entity e : level.getOtherEntities(player, box)) {
               if (e instanceof LivingEntity living && living != player) {
                  Vec3d toEntity = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(chest);
                  double along = toEntity.dotProduct(look);
                  if (!(along < 0.0) && !(along > 11.0)) {
                     Vec3d perp = toEntity.subtract(look.multiply(along));
                     if (!(perp.length() > 1.2 + e.getWidth() * 0.5)) {
                        boolean wasAlive = living.isAlive();
                        PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);

                        try {
                           living.damage(level.getDamageSources().playerAttack(player), 20.0F);
                        } finally {
                           PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
                        }

                        living.setVelocity(living.getVelocity().add(look.multiply(0.5)));
                        living.velocityModified = true;
                        if (wasAlive && living.isDead()) {
                           GibEntity.spawnGibs(level, living, look, 1.0F);
                        }
                     }
                  }
               }
            }

            ButcherTentacleBroadcastPayload broadcast = new ButcherTentacleBroadcastPayload(id, now, look.x, look.y, look.z);

            for (ServerPlayerEntity p : level.getServer().getPlayerManager().getPlayerList()) {
               ServerPlayNetworking.send(p, broadcast);
            }

            level.playSound(null, chest.x, chest.y, chest.z, SoundEvents.BLOCK_SLIME_BLOCK_PLACE, SoundCategory.PLAYERS, 1.4F, 0.55F);
            pendingRetractTick.put(id, new long[]{now + 14L});
            pendingRetractPos.put(id, chest);
            pendingRetractLevel.put(id, level);
            return true;
         }
      }
   }

   public static void tick(MinecraftServer server) {
      if (!pendingRetractTick.isEmpty()) {
         Iterator<Entry<UUID, long[]>> it = pendingRetractTick.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, long[]> entry = it.next();
            UUID id = entry.getKey();
            ServerWorld level = pendingRetractLevel.get(id);
            if (level == null) {
               it.remove();
               pendingRetractPos.remove(id);
            } else if (level.getTime() >= entry.getValue()[0]) {
               Vec3d pos = pendingRetractPos.get(id);
               if (pos != null) {
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 1.1F, 0.45F);
               }

               it.remove();
               pendingRetractLevel.remove(id);
               pendingRetractPos.remove(id);
            }
         }
      }
   }

   public static void onPlayerDisconnect(UUID id) {
      cooldownUntil.remove(id);
   }
}
