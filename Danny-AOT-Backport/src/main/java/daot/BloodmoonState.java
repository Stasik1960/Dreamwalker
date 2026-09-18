package daot;

public class BloodmoonState {
   public static final long[] TRACK_DURATIONS_TICKS = new long[]{1800L, 1800L, 1800L, 1800L};
   private static volatile boolean active = false;
   private static volatile long startTick = 0L;

   public static boolean isActive() {
      return active;
   }

   public static long getStartTick() {
      return startTick;
   }

   public static void setActive(boolean value, long currentServerTick) {
      active = value;
      if (value) {
         startTick = currentServerTick;
      }
   }

   public static int getCurrentTrackIndex(long currentServerTick) {
      long elapsed = Math.max(0L, currentServerTick - startTick);
      long cycleTotal = 0L;

      for (long d : TRACK_DURATIONS_TICKS) {
         cycleTotal += d;
      }

      if (cycleTotal <= 0L) {
         return 0;
      } else {
         long withinCycle = elapsed % cycleTotal;
         long acc = 0L;

         for (int i = 0; i < TRACK_DURATIONS_TICKS.length; i++) {
            acc += TRACK_DURATIONS_TICKS[i];
            if (withinCycle < acc) {
               return i;
            }
         }

         return 0;
      }
   }
}
