package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class TitanBloodlineClientData {
   private static volatile boolean active = false;

   public static void setActive(boolean value) {
      active = value;
   }

   public static boolean isActive() {
      return active;
   }
}
