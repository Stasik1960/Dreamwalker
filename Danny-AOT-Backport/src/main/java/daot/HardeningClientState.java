package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HardeningClientState {
   private static volatile boolean hasHardening = false;

   private HardeningClientState() {
   }

   public static void set(boolean value) {
      hasHardening = value;
   }

   public static boolean hasHardening() {
      return hasHardening;
   }
}
