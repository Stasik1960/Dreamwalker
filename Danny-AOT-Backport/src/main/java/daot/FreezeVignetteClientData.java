package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class FreezeVignetteClientData {
   private static boolean frozen = false;
   private static int frozenTimeout = 0;

   public static void setFrozen(boolean active) {
      if (active) {
         frozen = true;
         frozenTimeout = 15;
      } else {
         frozenTimeout = 0;
         frozen = false;
      }

      PushAuraTracker.clientLocalFrozenVignette = frozen;
   }

   public static void tick() {
      if (frozen && frozenTimeout > 0) {
         frozenTimeout--;
         if (frozenTimeout <= 0) {
            frozen = false;
            PushAuraTracker.clientLocalFrozenVignette = false;
         }
      }
   }

   public static boolean isFrozen() {
      return frozen;
   }
}
