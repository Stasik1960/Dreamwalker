package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class FounderAbilityBarState {
   private static boolean abilityMode = false;
   private static boolean localMode = false;

   private FounderAbilityBarState() {
   }

   public static void toggle() {
      abilityMode = !abilityMode;
   }

   public static void toggleScope() {
      localMode = !localMode;
   }

   public static boolean isLocalMode() {
      return localMode;
   }

   public static void setAbilityMode(boolean value) {
      abilityMode = value;
   }

   public static boolean isActive() {
      return abilityMode && ShifterStaminaHUD.isFounding();
   }
}
