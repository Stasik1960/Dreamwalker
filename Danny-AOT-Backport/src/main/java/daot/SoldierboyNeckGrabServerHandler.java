package daot;

import daot.network.SoldierboyNeckGrabStatePayload;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class SoldierboyNeckGrabServerHandler {
   public static final double GRAB_REACH = 2.0;
   private static final int MAX_GRAB_TICKS = 100;
   private static final int COOLDOWN_TICKS = 200;
   private static final float PUNCH_DAMAGE = 6.0F;
   public static final double HOLD_FORWARD = 1.0;
   private static final Map<UUID, SoldierboyNeckGrabServerHandler.GrabPair> ACTIVE_GRABS = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

   private SoldierboyNeckGrabServerHandler() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(SoldierboyNeckGrabServerHandler::tick);
   }

   public static boolean isGrabbing(UUID grabberUuid) {
      return ACTIVE_GRABS.containsKey(grabberUuid);
   }

   public static SoldierboyNeckGrabServerHandler.AttemptResult handleXPress(ServerPlayerEntity grabber) {
      UUID grabberId = grabber.getUuid();
      long now = grabber.getServerWorld().getTime();
      if (ACTIVE_GRABS.containsKey(grabberId)) {
         ACTIVE_GRABS.remove(grabberId);
         COOLDOWN_UNTIL.put(grabberId, now + 200L);
         broadcastGrabState(grabber.getServer(), grabberId, -1, false);
         return SoldierboyNeckGrabServerHandler.AttemptResult.RELEASED;
      } else {
         Long cdEnd = COOLDOWN_UNTIL.get(grabberId);
         if (cdEnd != null && now < cdEnd) {
            grabber.getServerWorld()
               .playSound(null, grabber.getX(), grabber.getY() + 1.2, grabber.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.6F);
            return SoldierboyNeckGrabServerHandler.AttemptResult.COOLDOWN_DENIED;
         } else {
            Vec3d look = grabber.getRotationVector().normalize();
            Vec3d chest = grabber.getPos().add(0.0, grabber.getHeight() * 0.5, 0.0);
            Vec3d reachEnd = chest.add(look.multiply(2.0));
            Box search = new Box(chest, reachEnd).expand(0.6);
            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Entity e : grabber.getServerWorld().getOtherEntities(grabber, search)) {
               if (e instanceof LivingEntity living) {
                  Vec3d toEntity = e.getPos().subtract(chest);
                  double along = toEntity.dotProduct(look);
                  if (!(along < -0.2) && !(along > 2.5)) {
                     double d = e.getPos().squaredDistanceTo(chest);
                     if (d < closestDist) {
                        closestDist = d;
                        closest = living;
                     }
                  }
               }
            }

            if (closest == null) {
               return SoldierboyNeckGrabServerHandler.AttemptResult.NO_TARGET;
            } else {
               ACTIVE_GRABS.put(grabberId, new SoldierboyNeckGrabServerHandler.GrabPair(closest.getUuid(), closest.getId(), now));
               broadcastGrabState(grabber.getServer(), grabberId, closest.getId(), true);
               return SoldierboyNeckGrabServerHandler.AttemptResult.STARTED;
            }
         }
      }
   }

   public static void punchGrabbed(ServerPlayerEntity grabber) {
      UUID grabberId = grabber.getUuid();
      SoldierboyNeckGrabServerHandler.GrabPair pair = ACTIVE_GRABS.get(grabberId);
      if (pair != null) {
         if (grabber.getServerWorld().getEntity(pair.victimUuid) instanceof LivingEntity living) {
            DamageSource source = grabber.getServerWorld().getDamageSources().playerAttack(grabber);
            PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);

            try {
               living.damage(source, 6.0F);
            } finally {
               PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
            }
         }
      }
   }

   public static void onPlayerLeave(UUID id) {
      ACTIVE_GRABS.remove(id);
      COOLDOWN_UNTIL.remove(id);
   }

   private static void tick(MinecraftServer server) {
      if (!ACTIVE_GRABS.isEmpty()) {
         Iterator<Entry<UUID, SoldierboyNeckGrabServerHandler.GrabPair>> it = ACTIVE_GRABS.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, SoldierboyNeckGrabServerHandler.GrabPair> entry = it.next();
            UUID grabberId = entry.getKey();
            SoldierboyNeckGrabServerHandler.GrabPair pair = entry.getValue();
            ServerPlayerEntity grabber = server.getPlayerManager().getPlayer(grabberId);
            if (grabber == null) {
               it.remove();
            } else {
               ServerWorld level = grabber.getServerWorld();
               long now = level.getTime();
               if (now - pair.startTick >= 100L) {
                  it.remove();
                  COOLDOWN_UNTIL.put(grabberId, now + 200L);
                  broadcastGrabState(server, grabberId, -1, false);
               } else if (level.getEntity(pair.victimUuid) instanceof LivingEntity victim) {
                  Vec3d look = grabber.getRotationVector().normalize();
                  double targetX = grabber.getX() + look.x * 1.0;
                  double targetY = grabber.getY() + grabber.getHeight() * 0.5 - victim.getHeight() * 0.5;
                  double targetZ = grabber.getZ() + look.z * 1.0;
                  victim.setPosition(targetX, targetY, targetZ);
                  victim.setVelocity(Vec3d.ZERO);
                  if (victim instanceof ServerPlayerEntity sp) {
                     sp.networkHandler.syncWithPlayerPosition();
                  }
               } else {
                  it.remove();
                  COOLDOWN_UNTIL.put(grabberId, now + 200L);
                  broadcastGrabState(server, grabberId, -1, false);
               }
            }
         }
      }
   }

   private static void broadcastGrabState(MinecraftServer server, UUID grabberId, int victimEntityId, boolean active) {
      SoldierboyNeckGrabStatePayload payload = new SoldierboyNeckGrabStatePayload(grabberId, victimEntityId, active);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   public static enum AttemptResult {
      STARTED,
      RELEASED,
      COOLDOWN_DENIED,
      NO_TARGET;
   }

   private record GrabPair(UUID victimUuid, int victimEntityId, long startTick) {
   }
}
