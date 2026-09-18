package daot;

import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class SoldierboyNeckGrabClientHandler {
   private static final Map<UUID, Integer> GRABS = new ConcurrentHashMap<>();

   private SoldierboyNeckGrabClientHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyNeckGrabClientHandler::tick);
   }

   public static void setActive(UUID grabberUuid, int victimEntityId, boolean active) {
      if (active && victimEntityId >= 0) {
         GRABS.put(grabberUuid, victimEntityId);
      } else {
         GRABS.remove(grabberUuid);
      }
   }

   public static void clearAll() {
      GRABS.clear();
   }

   private static void tick(MinecraftClient mc) {
      if (!GRABS.isEmpty()) {
         ClientWorld level = mc.world;
         if (level != null) {
            for (Entry<UUID, Integer> entry : GRABS.entrySet()) {
               PlayerEntity grabber = level.getPlayerByUuid(entry.getKey());
               if (grabber != null) {
                  Entity victim = level.getEntityById(entry.getValue());
                  if (victim instanceof LivingEntity) {
                     Vec3d look = grabber.getRotationVector().normalize();
                     double targetX = grabber.getX() + look.x * 1.0;
                     double targetY = grabber.getY() + grabber.getHeight() * 0.5 - victim.getHeight() * 0.5;
                     double targetZ = grabber.getZ() + look.z * 1.0;
                     victim.setPosition(targetX, targetY, targetZ);
                     victim.updateTrackedPositionAndAngles(targetX, targetY, targetZ, victim.getYaw(), victim.getPitch(), 1, false);
                     victim.setVelocity(Vec3d.ZERO);
                  }
               }
            }
         }
      }
   }
}
