package daot;

import daot.network.HomelanderGrabRotationPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class HomelanderGrabClientHandler {
   private static final double GRAB_HOLD_DISTANCE = 1.1;
   private static final double GRAB_HOLD_Y_OFFSET = 0.15;
   private static final Map<UUID, Integer> grabPairs = new HashMap<>();
   private static final Map<Integer, UUID> victimToGrabber = new HashMap<>();
   private static volatile float prevGrabberYaw;
   private static volatile float currentGrabberYaw;
   private static volatile float prevGrabberPitch;
   private static volatile float currentGrabberPitch;
   private static volatile long grabberRotPayloadTimeNano = 0L;

   private HomelanderGrabClientHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderGrabClientHandler::tick);
   }

   public static void onSync(UUID grabberUuid, int victimEntityId) {
      if (victimEntityId == -1) {
         Integer prev = grabPairs.remove(grabberUuid);
         if (prev != null) {
            victimToGrabber.remove(prev);
         }
      } else {
         Integer prev = grabPairs.put(grabberUuid, victimEntityId);
         if (prev != null && prev != victimEntityId) {
            victimToGrabber.remove(prev);
         }

         victimToGrabber.put(victimEntityId, grabberUuid);
      }
   }

   public static UUID findGrabberOf(int victimEntityId) {
      return victimToGrabber.get(victimEntityId);
   }

   public static boolean isLocalPlayerGrabbing() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player == null ? false : grabPairs.containsKey(mc.player.getUuid());
   }

   public static void onGrabberRotationSync(float yaw, float pitch) {
      if (grabberRotPayloadTimeNano == 0L) {
         prevGrabberYaw = yaw;
         prevGrabberPitch = pitch;
      } else {
         prevGrabberYaw = currentGrabberYaw;
         prevGrabberPitch = currentGrabberPitch;
      }

      currentGrabberYaw = yaw;
      currentGrabberPitch = pitch;
      grabberRotPayloadTimeNano = System.nanoTime();
   }

   public static boolean hasGrabberRotation() {
      return grabberRotPayloadTimeNano != 0L;
   }

   public static float getGrabberYawInterpolated() {
      return currentGrabberYaw;
   }

   public static float getGrabberPitchInterpolated() {
      return currentGrabberPitch;
   }

   public static void clearGrabberRotation() {
      grabberRotPayloadTimeNano = 0L;
      currentGrabberYaw = 0.0F;
      prevGrabberYaw = 0.0F;
      currentGrabberPitch = 0.0F;
      prevGrabberPitch = 0.0F;
   }

   private static void tick(MinecraftClient mc) {
      if (mc.player != null && !victimToGrabber.containsKey(mc.player.getId()) && hasGrabberRotation()) {
         clearGrabberRotation();
      }

      if (mc.player != null && grabPairs.containsKey(mc.player.getUuid())) {
         try {
            ClientPlayNetworking.send(new HomelanderGrabRotationPayload(mc.player.getYaw(), mc.player.getPitch()));
         } catch (Exception var13) {
         }
      }

      if (!grabPairs.isEmpty()) {
         ClientWorld level = mc.world;
         if (level != null) {
            Iterator<Entry<UUID, Integer>> it = grabPairs.entrySet().iterator();

            while (it.hasNext()) {
               Entry<UUID, Integer> entry = it.next();
               PlayerEntity grabber = level.getPlayerByUuid(entry.getKey());
               Entity victim = level.getEntityById(entry.getValue());
               if (grabber != null && victim != null && !victim.isRemoved() && victim.isAlive()) {
                  Vec3d look = grabber.getRotationVec(1.0F);
                  double tx = grabber.getX() + look.x * 1.1;
                  double ty = grabber.getY() + 0.15 + look.y * 1.1;
                  double tz = grabber.getZ() + look.z * 1.1;
                  victim.setPosition(tx, ty, tz);
                  victim.updateTrackedPositionAndAngles(tx, ty, tz, victim.getYaw(), victim.getPitch(), 1, false);
                  victim.setVelocity(Vec3d.ZERO);
               } else {
                  victimToGrabber.remove(entry.getValue());
                  it.remove();
               }
            }
         }
      }
   }
}
