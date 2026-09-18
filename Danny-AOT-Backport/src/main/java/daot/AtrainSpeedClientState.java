package daot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AtrainSpeedClientState {
   private static final Set<UUID> active = ConcurrentHashMap.newKeySet();

   private AtrainSpeedClientState() {
   }

   public static void set(UUID uuid, boolean isActive) {
      if (isActive) {
         active.add(uuid);
      } else {
         active.remove(uuid);
      }
   }

   public static boolean isActive(UUID uuid) {
      return active.contains(uuid);
   }

   public static void clearAll() {
      active.clear();
   }
}
