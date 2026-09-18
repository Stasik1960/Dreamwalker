package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;

public final class ShifterVisibilityHelper {
   public static final int REENTRY_INVISIBILITY_DELAY_TICKS = 10;
   private static final Map<UUID, Integer> reentryTimers = new ConcurrentHashMap<>();

   private ShifterVisibilityHelper() {
   }

   public static void scheduleReentryInvisibility(PlayerEntity player) {
      reentryTimers.put(player.getUuid(), 10);
   }

   public static boolean isInReentryWindow(PlayerEntity player) {
      return reentryTimers.containsKey(player.getUuid());
   }

   public static void cancelReentry(PlayerEntity player) {
      if (player != null) {
         reentryTimers.remove(player.getUuid());
      }
   }

   public static void cancelReentry(UUID playerUuid) {
      reentryTimers.remove(playerUuid);
   }

   public static void tick(PlayerEntity player) {
      Integer remaining = reentryTimers.get(player.getUuid());
      if (remaining != null) {
         remaining = remaining - 1;
         if (remaining <= 0) {
            reentryTimers.remove(player.getUuid());
            player.setInvisible(true);
         } else {
            reentryTimers.put(player.getUuid(), remaining);
         }
      }
   }
}
