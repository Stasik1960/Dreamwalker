package daot;

public class FogEventState {
   public static final double ABNORMAL_SPEED_MULTIPLIER = 1.75;
   private static volatile boolean active = false;

   public static boolean isActive() {
      return active;
   }

   public static void setActive(boolean value) {
      active = value;
   }
}
