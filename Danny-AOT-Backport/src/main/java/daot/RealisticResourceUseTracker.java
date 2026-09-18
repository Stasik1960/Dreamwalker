package daot;

import net.minecraft.world.World;

public final class RealisticResourceUseTracker {
   private static volatile boolean clientEnabled = false;

   private RealisticResourceUseTracker() {
   }

   public static void setClientEnabled(boolean enabled) {
      clientEnabled = enabled;
   }

   public static boolean isEnabled(World level) {
      if (level == null) {
         return false;
      } else {
         return level.isClient() ? clientEnabled : level.getGameRules().getBoolean(DannysAot.RULE_REALISTIC_RESOURCE_USE);
      }
   }
}
