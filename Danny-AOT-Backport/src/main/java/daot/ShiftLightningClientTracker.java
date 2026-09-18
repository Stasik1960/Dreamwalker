package daot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ShiftLightningClientTracker {
   private static final long TTL_MS = 3000L;
   private static final Map<Integer, Long> YELLOW = new ConcurrentHashMap<>();

   private ShiftLightningClientTracker() {
   }

   public static void mark(int entityId) {
      YELLOW.put(entityId, System.currentTimeMillis() + 3000L);
   }

   public static boolean isYellow(int entityId) {
      Long expiry = YELLOW.get(entityId);
      if (expiry == null) {
         return false;
      } else if (System.currentTimeMillis() > expiry) {
         YELLOW.remove(entityId);
         return false;
      } else {
         return true;
      }
   }
}
