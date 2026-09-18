package daot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AwakenedPowerClientData {
   private static float charge = 0.0F;
   private static float maxCharge = 300.0F;
   private static boolean active = false;
   private static boolean isAckerman = false;
   private static final Set<UUID> activeAwakened = ConcurrentHashMap.newKeySet();

   public static void update(float newCharge, float newMaxCharge, boolean newActive) {
      charge = newCharge;
      maxCharge = newMaxCharge;
      active = newActive;
      isAckerman = true;
   }

   public static boolean isActive() {
      return active;
   }

   public static boolean isActiveFor(UUID playerUuid) {
      return activeAwakened.contains(playerUuid);
   }

   public static void setActiveFor(UUID playerUuid, boolean awakened) {
      if (awakened) {
         activeAwakened.add(playerUuid);
      } else {
         activeAwakened.remove(playerUuid);
      }
   }

   public static float getCharge() {
      return charge;
   }

   public static float getMaxCharge() {
      return maxCharge;
   }

   public static boolean isAckerman() {
      return isAckerman;
   }

   public static void clear() {
      active = false;
      charge = 0.0F;
      maxCharge = 300.0F;
      isAckerman = false;
   }

   public static void clearAll() {
      clear();
      activeAwakened.clear();
   }
}
