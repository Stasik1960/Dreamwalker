package daot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SoldierboyClientHandler {
   private static final Map<UUID, SoldierboyClientHandler.RemoteCharge> REMOTE_CHARGES = new ConcurrentHashMap<>();
   private static final Set<UUID> LASER_FIRERS = ConcurrentHashMap.newKeySet();

   private SoldierboyClientHandler() {
   }

   public static void setRemoteCharging(UUID uuid, int ability, boolean charging, long gameTime) {
      if (charging) {
         REMOTE_CHARGES.put(uuid, new SoldierboyClientHandler.RemoteCharge(ability, gameTime));
      } else {
         REMOTE_CHARGES.remove(uuid);
      }
   }

   public static SoldierboyClientHandler.RemoteCharge getRemoteCharge(UUID uuid) {
      return REMOTE_CHARGES.get(uuid);
   }

   public static Map<UUID, SoldierboyClientHandler.RemoteCharge> snapshot() {
      return new HashMap<>(REMOTE_CHARGES);
   }

   public static void setLaserFiring(UUID uuid, boolean firing) {
      if (firing) {
         LASER_FIRERS.add(uuid);
      } else {
         LASER_FIRERS.remove(uuid);
      }
   }

   public static Set<UUID> laserFirers() {
      return Collections.unmodifiableSet(LASER_FIRERS);
   }

   public static void clearAll() {
      REMOTE_CHARGES.clear();
      LASER_FIRERS.clear();
   }

   @Environment(EnvType.CLIENT)
   public record RemoteCharge(int ability, long startTick) {
   }
}
