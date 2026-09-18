package daot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class RemoteAPGAimTracker {
   private static final float TRANSITION_SPEED = 0.2F;
   private static final Map<Integer, RemoteAPGAimTracker.AimData> entries = new ConcurrentHashMap<>();

   public static void updateState(int playerId, boolean mainAiming, boolean offAiming) {
      RemoteAPGAimTracker.AimData d = entries.computeIfAbsent(playerId, k -> new RemoteAPGAimTracker.AimData());
      d.targetMain = mainAiming;
      d.targetOff = offAiming;
   }

   public static void tick() {
      for (RemoteAPGAimTracker.AimData d : entries.values()) {
         d.prevAnimMain = d.animMain;
         d.prevAnimOff = d.animOff;
         float tMain = d.targetMain ? 1.0F : 0.0F;
         float tOff = d.targetOff ? 1.0F : 0.0F;
         d.animMain = d.animMain + (tMain - d.animMain) * 0.2F;
         d.animOff = d.animOff + (tOff - d.animOff) * 0.2F;
      }
   }

   public static float getAimAnimation(int playerId, boolean mainHand, float partialTick) {
      RemoteAPGAimTracker.AimData d = entries.get(playerId);
      if (d == null) {
         return 0.0F;
      } else {
         return mainHand ? d.prevAnimMain + (d.animMain - d.prevAnimMain) * partialTick : d.prevAnimOff + (d.animOff - d.prevAnimOff) * partialTick;
      }
   }

   public static void removePlayer(int playerId) {
      entries.remove(playerId);
   }

   public static void clear() {
      entries.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class AimData {
      public boolean targetMain;
      public boolean targetOff;
      public float animMain;
      public float animOff;
      public float prevAnimMain;
      public float prevAnimOff;
   }
}
