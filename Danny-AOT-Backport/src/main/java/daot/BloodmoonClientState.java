package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BloodmoonClientState {
   private static volatile boolean active = false;

   public static boolean isActive() {
      return active;
   }

   public static void setActive(boolean value) {
      active = value;
   }
}
