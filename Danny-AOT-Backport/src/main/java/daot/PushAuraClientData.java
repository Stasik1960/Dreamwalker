package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;

@Environment(EnvType.CLIENT)
public class PushAuraClientData {
   public static int pushAuraEntityId = -1;
   public static boolean clientAttractMode = false;
   public static boolean lastShiftState = false;
   public static long lastShiftPressTick = -100L;
   public static FadingLoopSound auraLoopSound = null;
   public static boolean roarEffectVisible = false;
   public static int roarTickCounter = 0;

   public static void clear() {
      pushAuraEntityId = -1;
      clientAttractMode = false;
      lastShiftState = false;
      lastShiftPressTick = -100L;
      roarEffectVisible = false;
      roarTickCounter = 0;
      if (auraLoopSound != null) {
         auraLoopSound.fadeOut();
         auraLoopSound = null;
      }

      ShiftParticleHelper.stopDannyModeRoarEffect();
   }

   public static double getAnimFreezeFactor(Entity entity) {
      int dannyId = pushAuraEntityId;
      if (dannyId == -1) {
         return 1.0;
      } else if (entity.getWorld() != null && entity.getWorld().isClient) {
         if (entity.getId() == dannyId) {
            return 1.0;
         } else {
            Entity holder = entity.getWorld().getEntityById(dannyId);
            if (holder == null) {
               return 1.0;
            } else {
               double dist = entity.distanceTo(holder);
               double radius = 7.5;
               double fullFreezeRadius = 1.25;
               boolean crouching = holder.isSneaking();
               if (!crouching) {
                  if (!FreezeVignetteClientData.isFrozen()) {
                     return dist > 2.5 ? 1.0 : Math.max(0.0, dist / 2.5);
                  } else if (dist > radius) {
                     return 1.0;
                  } else {
                     return dist <= fullFreezeRadius ? 0.0 : (dist - fullFreezeRadius) / (radius - fullFreezeRadius);
                  }
               } else if (dist > radius) {
                  return 1.0;
               } else {
                  return dist <= fullFreezeRadius ? 0.0 : (dist - fullFreezeRadius) / (radius - fullFreezeRadius);
               }
            }
         }
      } else {
         return 1.0;
      }
   }
}
